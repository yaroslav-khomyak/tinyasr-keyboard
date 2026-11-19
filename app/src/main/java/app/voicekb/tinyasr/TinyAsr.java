package app.voicekb.tinyasr;

import java.nio.ByteBuffer;

/** JNI bindings of the tinyasr C API. Every call for one models object must come from the same thread. */
final class TinyAsr {
    private TinyAsr() {}

    static {
        System.loadLibrary("tinyasr_jni");
    }

    static native int nativeVersion();

    /** Both buffers must be direct; they are copied. Throws IllegalStateException with the library's reason. */
    static native long nativeLoad(ByteBuffer asr, ByteBuffer vad, int maxThreads);

    static native void nativeFree(long models);

    static native int nativeChunkMs(long models);

    static native long nativeSessionNew(long models, String langs, String startLang, int beam);

    /** Events as a UTF-8 JSON array. */
    static native byte[] nativeSessionPush(long session, float[] pcm, int n);

    static native byte[] nativeSessionFinish(long session);

    static native String nativeSessionLang(long session);

    static native void nativeSessionFree(long session);
}
