package app.voicekb.tinyasr;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import org.junit.Assume;
import org.junit.Test;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.util.Locale;

/**
 * The JNI bridge and the real model, on this machine: transcribes a recording the way the keyboard feeds the
 * recognizer. Needs scripts/prepare.sh and scripts/host-bridge.sh to have run; skipped otherwise, except in CI,
 * where a missing library is a failure.
 */
public class BridgeHostTest {
    private static final File LIB_DIR = new File(System.getProperty("tinyasr.hostlib", "build/tinyasr/host"));
    private static final File ASSETS = new File(System.getProperty("tinyasr.assets", "build/tinyasr/assets"));

    private static void requireBridge() {
        boolean ready = new File(LIB_DIR, System.mapLibraryName("tinyasr_jni")).exists()
                && new File(ASSETS, "en-ru-uk.tzf").exists();
        if (System.getenv("CI") != null) {
            assertTrue("host bridge not built: run scripts/prepare.sh and scripts/host-bridge.sh", ready);
        }
        Assume.assumeTrue("host bridge not built", ready);
    }

    private static ByteBuffer map(File f) throws IOException {
        try (FileChannel c = FileChannel.open(f.toPath())) {
            return c.map(FileChannel.MapMode.READ_ONLY, 0, c.size());
        }
    }

    /** 16-bit mono 16 kHz WAV from the test resources, as floats. */
    private static float[] recording() throws IOException {
        byte[] wav;
        try (InputStream in = BridgeHostTest.class.getResourceAsStream("/hello_en.wav")) {
            wav = in.readAllBytes();
        }
        ByteBuffer b = ByteBuffer.wrap(wav).order(ByteOrder.LITTLE_ENDIAN);
        int pos = 12;
        while (true) {
            int id = b.getInt(pos), size = b.getInt(pos + 4);
            if (id == 0x61746164) { // "data"
                size = Math.min(size, wav.length - pos - 8);
                float[] pcm = new float[size / 2];
                for (int i = 0; i < pcm.length; i++) pcm[i] = b.getShort(pos + 8 + 2 * i) / 32768f;
                return pcm;
            }
            pos += 8 + size + (size & 1);
        }
    }

    @Test
    public void brokenModelIsRejectedWithAReason() {
        requireBridge();
        try {
            TinyAsr.nativeLoad(ByteBuffer.allocateDirect(100), ByteBuffer.allocateDirect(100), 1);
            fail("a broken model file was accepted");
        } catch (IllegalStateException e) {
            assertTrue(e.getMessage(), e.getMessage().length() > 3);
        }
    }

    @Test
    public void heapBuffersAreRefused() {
        requireBridge();
        try {
            TinyAsr.nativeLoad(ByteBuffer.allocate(100), ByteBuffer.allocate(100), 1);
            fail("heap buffers were accepted");
        } catch (IllegalArgumentException expected) {
        }
    }

    @Test
    public void nullSessionIsHarmless() {
        requireBridge();
        assertNull(TinyAsr.nativeSessionPush(0, new float[160], 160));
        assertNull(TinyAsr.nativeSessionFinish(0));
        assertNull(TinyAsr.nativeSessionLang(0));
        TinyAsr.nativeSessionFree(0);
        TinyAsr.nativeFree(0);
    }

    @Test
    public void transcribesARecordingIntoAnEditor() throws Exception {
        requireBridge();
        assertEquals(2, TinyAsr.nativeVersion());
        long models = TinyAsr.nativeLoad(map(new File(ASSETS, "en-ru-uk.tzf")),
                map(new File(ASSETS, "silero_vad.onnx")), 4);
        assertTrue(TinyAsr.nativeChunkMs(models) > 0);
        long session = TinyAsr.nativeSessionNew(models, "en", "en", 0);
        assertTrue(session != 0);

        // The same path as the keyboard: events -> Transcript -> TextWriter -> editor.
        FakeEditor editor = new FakeEditor("");
        TextWriter writer = new TextWriter();
        int[] partials = {0};
        Transcript.Sink sink = new Transcript.Sink() {
            @Override public void commit(String text) { writer.commit(editor.connection(), text); }
            @Override public void partial(String text) {
                writer.partial(editor.connection(), text);
                if (!text.isEmpty()) partials[0]++;
            }
            @Override public void language(String lang) {}
        };

        float[] pcm = recording();
        float[] buf = new float[1600]; // 100 ms, as VoiceEngine reads
        for (int i = 0; i < pcm.length; i += buf.length) {
            int n = Math.min(buf.length, pcm.length - i);
            System.arraycopy(pcm, i, buf, 0, n);
            Transcript.dispatch(new String(TinyAsr.nativeSessionPush(session, buf, n), StandardCharsets.UTF_8), sink);
        }
        Transcript.dispatch(new String(TinyAsr.nativeSessionFinish(session), StandardCharsets.UTF_8), sink);
        writer.settle(editor.connection());
        assertEquals("en", TinyAsr.nativeSessionLang(session));
        TinyAsr.nativeSessionFree(session);
        TinyAsr.nativeFree(models);

        String text = editor.text.toString();
        System.out.println("recognized: " + text);
        String lower = text.toLowerCase(Locale.ROOT);
        assertTrue("live text was shown while speaking", partials[0] > 3);
        assertTrue(text, lower.contains("this is a test of the voice keyboard"));
        assertTrue(text, lower.contains("how are you doing today"));
        assertTrue(text, lower.contains("pizzas"));
        assertTrue("no doubled spaces: " + text, !text.contains("  "));
        assertTrue("nothing left composing", editor.compStart < 0);
    }
}
