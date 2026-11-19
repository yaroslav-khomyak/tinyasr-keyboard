/* JNI bridge between app.voicekb.tinyasr.TinyAsr and the tinyasr C API (include/tinyasr.h).
 * All calls for one models object must come from the same thread (the recognizer thread). */
#define _GNU_SOURCE
#include <jni.h>
#include <sched.h>
#include <stdint.h>
#include <stdio.h>
#include <string.h>
#include <time.h>

#include "tinyasr.h"

#define JNI_FN(name) Java_app_voicekb_tinyasr_TinyAsr_##name

static double mono_clock(void) {
    struct timespec ts;
    clock_gettime(CLOCK_MONOTONIC, &ts);
    return (double)ts.tv_sec + (double)ts.tv_nsec * 1e-9;
}

/* Pins the calling thread to the big cores; returns their count (0: unknown, nothing pinned). From tinyasr.h. */
static int pin_to_big_cores(void) {
#ifdef __linux__
    static int cap[CPU_SETSIZE];
    int n = 0, max = 0, big = 0;
    for (; n < CPU_SETSIZE; n++) {
        char path[64];
        snprintf(path, sizeof path, "/sys/devices/system/cpu/cpu%d/cpu_capacity", n);
        FILE *f = fopen(path, "r");
        if (f == NULL) break;
        if (fscanf(f, "%d", &cap[n]) != 1) cap[n] = 0;
        fclose(f);
        if (cap[n] > max) max = cap[n];
    }
    cpu_set_t set;
    CPU_ZERO(&set);
    for (int i = 0; i < n; i++)
        if (max > 0 && cap[i] * 10 >= max * 9) {
            CPU_SET(i, &set);
            big++;
        }
    if (big > 0 && sched_setaffinity(0, sizeof set, &set) != 0) big = 0;
    return big;
#else
    return 0;
#endif
}

static void throw_msg(JNIEnv *env, const char *cls, const char *msg) {
    jclass c = (*env)->FindClass(env, cls);
    if (c != NULL) (*env)->ThrowNew(env, c, msg);
}

static jbyteArray to_bytes(JNIEnv *env, const char *s) {
    if (s == NULL) return NULL;
    jsize n = (jsize)strlen(s);
    jbyteArray a = (*env)->NewByteArray(env, n);
    if (a != NULL) (*env)->SetByteArrayRegion(env, a, 0, n, (const jbyte *)s);
    return a;
}

JNIEXPORT jint JNICALL JNI_FN(nativeVersion)(JNIEnv *env, jclass cls) {
    (void)env; (void)cls;
    return (jint)tinyasr_version();
}

/* asr, vad: direct ByteBuffers (e.g. mapped files); copied by the library. maxThreads <= 0: no limit. */
JNIEXPORT jlong JNICALL JNI_FN(nativeLoad)(JNIEnv *env, jclass cls, jobject asr, jobject vad, jint maxThreads) {
    (void)cls;
    const uint8_t *a = asr ? (*env)->GetDirectBufferAddress(env, asr) : NULL;
    const uint8_t *v = vad ? (*env)->GetDirectBufferAddress(env, vad) : NULL;
    if (a == NULL || v == NULL) {
        throw_msg(env, "java/lang/IllegalArgumentException", "model buffers must be direct ByteBuffers");
        return 0;
    }
    char err[256] = "unknown error";
    tinyasr_models *m = tinyasr_load(a, (size_t)(*env)->GetDirectBufferCapacity(env, asr), v,
                                     (size_t)(*env)->GetDirectBufferCapacity(env, vad), err, sizeof err);
    if (m == NULL) {
        throw_msg(env, "java/lang/IllegalStateException", err);
        return 0;
    }
    int threads = pin_to_big_cores();
    if (maxThreads > 0 && threads > maxThreads) threads = maxThreads;
    tinyasr_set_threads(m, threads > 0 ? (uint32_t)threads : 1);
    return (jlong)(intptr_t)m;
}

JNIEXPORT void JNICALL JNI_FN(nativeFree)(JNIEnv *env, jclass cls, jlong m) {
    (void)env; (void)cls;
    tinyasr_free((tinyasr_models *)(intptr_t)m);
}

JNIEXPORT jint JNICALL JNI_FN(nativeChunkMs)(JNIEnv *env, jclass cls, jlong m) {
    (void)env; (void)cls;
    return (jint)tinyasr_chunk_ms((const tinyasr_models *)(intptr_t)m);
}

JNIEXPORT jlong JNICALL JNI_FN(nativeSessionNew)(JNIEnv *env, jclass cls, jlong m, jstring langs, jstring startLang,
                                                 jint beam) {
    (void)cls;
    const char *l = langs ? (*env)->GetStringUTFChars(env, langs, NULL) : NULL;
    const char *s = startLang ? (*env)->GetStringUTFChars(env, startLang, NULL) : NULL;
    tinyasr_session *ses =
        tinyasr_session_new((const tinyasr_models *)(intptr_t)m, l, s, mono_clock, beam > 0 ? (uint32_t)beam : 0);
    if (l) (*env)->ReleaseStringUTFChars(env, langs, l);
    if (s) (*env)->ReleaseStringUTFChars(env, startLang, s);
    return (jlong)(intptr_t)ses;
}

/* Returns the events as UTF-8 JSON bytes (null for a null session). */
JNIEXPORT jbyteArray JNICALL JNI_FN(nativeSessionPush)(JNIEnv *env, jclass cls, jlong s, jfloatArray pcm, jint n) {
    (void)cls;
    if (pcm == NULL || n < 0 || n > (*env)->GetArrayLength(env, pcm)) {
        throw_msg(env, "java/lang/IllegalArgumentException", "bad pcm length");
        return NULL;
    }
    jfloat *p = (*env)->GetFloatArrayElements(env, pcm, NULL);
    if (p == NULL) return NULL;
    const char *json = tinyasr_session_push((tinyasr_session *)(intptr_t)s, p, (size_t)n);
    (*env)->ReleaseFloatArrayElements(env, pcm, p, JNI_ABORT);
    return to_bytes(env, json);
}

JNIEXPORT jbyteArray JNICALL JNI_FN(nativeSessionFinish)(JNIEnv *env, jclass cls, jlong s) {
    (void)cls;
    return to_bytes(env, tinyasr_session_finish((tinyasr_session *)(intptr_t)s));
}

JNIEXPORT jstring JNICALL JNI_FN(nativeSessionLang)(JNIEnv *env, jclass cls, jlong s) {
    (void)cls;
    const char *l = tinyasr_session_lang((const tinyasr_session *)(intptr_t)s);
    return l ? (*env)->NewStringUTF(env, l) : NULL;
}

JNIEXPORT void JNICALL JNI_FN(nativeSessionFree)(JNIEnv *env, jclass cls, jlong s) {
    (void)env; (void)cls;
    tinyasr_session_free((tinyasr_session *)(intptr_t)s);
}
