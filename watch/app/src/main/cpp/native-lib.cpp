#include <jni.h>

extern "C"
JNIEXPORT jboolean JNICALL
Java_com_example_pulseguard_processing_AnomalyProcessor_nativeProcessSample(
        JNIEnv* env,
        jobject /* this */,
        jint bpm
        ){
    // Sprint 0 stub:
    // Always return false (no anomaly)
    return JNI_FALSE;
}