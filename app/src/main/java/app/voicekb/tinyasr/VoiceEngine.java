package app.voicekb.tinyasr;

import android.content.Context;
import android.content.res.AssetFileDescriptor;
import android.media.AudioFormat;
import android.media.AudioRecord;
import android.media.MediaRecorder;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Looper;
import android.os.Process;
import android.os.SystemClock;
import android.util.Log;

import java.io.FileInputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;

/**
 * Owns the models and the recognizer thread. One instance per process; all public methods are called on the main
 * thread and all Listener callbacks arrive there.
 */
final class VoiceEngine {
    private static final String TAG = "TinyAsrEngine";
    static final String ASR_ASSET = "en-ru-uk.tzf";
    static final String VAD_ASSET = "silero_vad.onnx";
    private static final int SAMPLE_RATE = 16000;
    private static final int READ_SAMPLES = SAMPLE_RATE / 10; // 100 ms
    private static final long SILENCE_TIMEOUT_MS = 30_000;
    private static final int MAX_THREADS = 4;

    enum State { IDLE, LOADING, LISTENING, FINISHING }

    interface Listener {
        void onState(State state);

        void onCommit(String text);

        void onPartial(String text);

        void onLanguage(String lang);

        /** 0..1, roughly how loud the last 100 ms were. */
        void onLevel(float level);

        void onError(String message);

        /** The run is over (after stop: all text has been delivered). */
        void onDone();
    }

    /** One recording, from start() to its end. */
    private static final class Run {
        final String langs;
        final String startLang;
        final Listener listener;
        volatile boolean stop;
        volatile boolean discard;

        Run(String langs, String startLang, Listener listener) {
            this.langs = langs;
            this.startLang = startLang;
            this.listener = listener;
        }
    }

    private static VoiceEngine instance;

    static synchronized VoiceEngine get(Context context) {
        if (instance == null) instance = new VoiceEngine(context.getApplicationContext());
        return instance;
    }

    private final Context app;
    private final Handler main = new Handler(Looper.getMainLooper());
    private final Handler worker;
    private long models; // worker thread only
    private Run current; // main thread only

    private VoiceEngine(Context app) {
        this.app = app;
        HandlerThread t = new HandlerThread("tinyasr", Process.THREAD_PRIORITY_URGENT_AUDIO);
        t.start();
        worker = new Handler(t.getLooper());
    }

    /** Loads the models in the background so that the first start() is quick. */
    void preload() {
        worker.post(() -> {
            try {
                ensureModels();
            } catch (Throwable e) {
                Log.e(TAG, "preload failed", e);
            }
        });
    }

    boolean isRunning() {
        return current != null;
    }

    /** langs: "uk", "en" or "uk,en"; startLang: the language to assume first when there are several. */
    void start(String langs, String startLang, Listener listener) {
        if (current != null) cancel();
        Run run = new Run(langs, startLang, listener);
        current = run;
        worker.post(() -> record(run));
    }

    /** Ends the recording; the remaining text is still delivered, then onDone. */
    void stop() {
        if (current != null) current.stop = true;
    }

    /** Ends the recording and drops everything not delivered yet. */
    void cancel() {
        Run run = current;
        if (run != null) {
            run.discard = true;
            run.stop = true;
            current = null;
        }
    }

    // ---- worker thread ----

    private void ensureModels() throws IOException {
        if (models != 0) return;
        long t0 = SystemClock.elapsedRealtime();
        ByteBuffer asr = mapAsset(ASR_ASSET);
        ByteBuffer vad = mapAsset(VAD_ASSET);
        models = TinyAsr.nativeLoad(asr, vad, MAX_THREADS);
        Log.i(TAG, "models loaded in " + (SystemClock.elapsedRealtime() - t0) + " ms, chunk "
                + TinyAsr.nativeChunkMs(models) + " ms");
    }

    /** Assets are stored uncompressed (see noCompress in app/build.gradle), so they can be mapped. */
    private ByteBuffer mapAsset(String name) throws IOException {
        try (AssetFileDescriptor fd = app.getAssets().openFd(name);
             FileInputStream in = new FileInputStream(fd.getFileDescriptor());
             FileChannel ch = in.getChannel()) {
            return ch.map(FileChannel.MapMode.READ_ONLY, fd.getStartOffset(), fd.getLength());
        }
    }

    private void record(Run run) {
        if (run.discard) return;
        AudioRecord rec = null;
        long session = 0;
        try {
            if (models == 0) {
                post(run, () -> run.listener.onState(State.LOADING));
                ensureModels();
            }
            if (run.stop) return;

            AudioFormat format = new AudioFormat.Builder()
                    .setEncoding(AudioFormat.ENCODING_PCM_FLOAT)
                    .setSampleRate(SAMPLE_RATE)
                    .setChannelMask(AudioFormat.CHANNEL_IN_MONO)
                    .build();
            int min = AudioRecord.getMinBufferSize(SAMPLE_RATE, AudioFormat.CHANNEL_IN_MONO,
                    AudioFormat.ENCODING_PCM_FLOAT);
            // Room for 4 s, so that a slow re-decode does not lose audio.
            int size = Math.max(min > 0 ? min : 0, SAMPLE_RATE * 4 * 4);
            rec = new AudioRecord.Builder()
                    .setAudioSource(MediaRecorder.AudioSource.VOICE_RECOGNITION)
                    .setAudioFormat(format)
                    .setBufferSizeInBytes(size)
                    .build();
            if (rec.getState() != AudioRecord.STATE_INITIALIZED) throw new IOException("microphone unavailable");
            rec.startRecording();
            if (rec.getRecordingState() != AudioRecord.RECORDSTATE_RECORDING) {
                throw new IOException("microphone is busy");
            }

            session = TinyAsr.nativeSessionNew(models, run.langs, run.startLang, 0);
            if (session == 0) throw new IOException("cannot start the recognizer");
            post(run, () -> run.listener.onState(State.LISTENING));

            float[] buf = new float[READ_SAMPLES];
            long lastActivity = SystemClock.elapsedRealtime();
            while (!run.stop) {
                int n = rec.read(buf, 0, buf.length, AudioRecord.READ_BLOCKING);
                if (n < 0) throw new IOException("microphone read failed (" + n + ")");
                if (n == 0) continue;
                float level = level(buf, n);
                post(run, () -> run.listener.onLevel(level));
                String json = utf8(TinyAsr.nativeSessionPush(session, buf, n));
                long now = SystemClock.elapsedRealtime();
                if (json != null && json.length() > 2) {
                    if (json.contains("\"text\"")) lastActivity = now;
                    post(run, () -> deliver(run, json));
                }
                if (now - lastActivity > SILENCE_TIMEOUT_MS) break;
            }
            rec.stop();
            if (!run.discard) {
                post(run, () -> run.listener.onState(State.FINISHING));
                String json = utf8(TinyAsr.nativeSessionFinish(session));
                post(run, () -> deliver(run, json));
            }
        } catch (SecurityException e) {
            post(run, () -> run.listener.onError("no microphone permission"));
        } catch (Throwable e) {
            Log.e(TAG, "recording failed", e);
            String msg = e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName();
            post(run, () -> run.listener.onError(msg));
        } finally {
            if (rec != null) {
                try {
                    rec.release();
                } catch (RuntimeException ignored) {
                }
            }
            if (session != 0) TinyAsr.nativeSessionFree(session);
            main.post(() -> {
                if (current == run) current = null;
                if (!run.discard) {
                    run.listener.onState(State.IDLE);
                    run.listener.onDone();
                }
            });
        }
    }

    private void post(Run run, Runnable r) {
        main.post(() -> {
            if (!run.discard) r.run();
        });
    }

    private void deliver(Run run, String json) {
        if (json == null) return;
        try {
            Transcript.dispatch(json, new Transcript.Sink() {
                @Override public void commit(String text) { run.listener.onCommit(text); }
                @Override public void partial(String text) { run.listener.onPartial(text); }
                @Override public void language(String lang) { run.listener.onLanguage(lang); }
            });
        } catch (Exception e) {
            Log.e(TAG, "bad events: " + json, e);
        }
    }

    private static String utf8(byte[] b) {
        return b == null ? null : new String(b, StandardCharsets.UTF_8);
    }

    private static float level(float[] x, int n) {
        double s = 0;
        for (int i = 0; i < n; i++) s += x[i] * x[i];
        double rms = Math.sqrt(s / n);
        // -50 dB .. -10 dB -> 0 .. 1
        double db = 20 * Math.log10(rms + 1e-7);
        return (float) Math.max(0, Math.min(1, (db + 50) / 40));
    }
}
