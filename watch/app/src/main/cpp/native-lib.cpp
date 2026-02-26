#include <jni.h>
#include <android/log.h>
#include <cstdint>

namespace {
    // --- Filter config ---
    static constexpr int kWindowSize = 5;
    static int ring[kWindowSize] = {0};
    static int ringCount = 0;
    static int ringIndex = 0;

    // --- Detection config (tweak later) ---
    static constexpr int kTachyThreshold = 120; // To force Tachy use 80
    static constexpr int kBradyThreshold = 45;  // To force Brady use 80
    static constexpr int64_t kPersistMillis = 15000;

    // --- State ---
    static int gLastFiltered = 0;

    static bool tachyActive = false;
    static bool bradyActive = false;
    static int64_t tachyStartTs = 0;
    static int64_t bradyStartTs = 0;

    static int movingAverage(int bpm) {
        ring[ringIndex] = bpm;
        ringIndex = (ringIndex + 1) % kWindowSize;
        if (ringCount < kWindowSize) ringCount ++;

        long sum = 0;
        for (int i = 0; i < ringCount; i++) sum += ring[i];
        return (ringCount > 0) ? static_cast<int>(sum / ringCount) : bpm;
    }
    // return 0 none, 1 tachy, 2 brady
    static int detectEvent(int filteredBpm, int64_t ts) {
        // Tachy logic
        if (filteredBpm >= kTachyThreshold) {
            if (!tachyActive) {
                tachyActive = true;
                tachyStartTs = ts;
            }
        } else {
            tachyActive = false;
            tachyStartTs = 0;
        }

        // Brady logic
        if (filteredBpm <= kBradyThreshold) {
            if(!bradyActive) {
                bradyActive = true;
                bradyStartTs = ts;
            }
        } else {
            bradyActive = false;
            bradyStartTs = 0;
        }

        // Persistence check
        if (tachyActive && tachyStartTs > 0 && (ts - tachyStartTs) >= kPersistMillis){
            return 1;
        }
        if (bradyActive && bradyStartTs > 0 && (ts - bradyStartTs) >= kPersistMillis){
            return 2;
        }
        return 0;
    }
}



extern "C"
JNIEXPORT jint JNICALL
Java_com_example_pulseguard_processing_AnomalyProcessor_nativeProcessSample(
        JNIEnv* /* env */,
        jobject /* this */,
        jint bpm,
        jlong timestampMillis
        ){
    // Basic sanity
    if (bpm <= 0 || bpm > 250) {
        // ignore obvious junk
        return 0;
    }

    const int filtered = movingAverage(static_cast<int>(bpm));
    gLastFiltered = filtered;

    const int eventCode = detectEvent(filtered, static_cast<int64_t>(timestampMillis));

    return eventCode;
}

extern "C"
JNIEXPORT jint JNICALL
Java_com_example_pulseguard_processing_AnomalyProcessor_nativeGetLastFilteredBpm(
        JNIEnv* /*env */,
        jobject /* this*/
        ){
    return static_cast<jint>(gLastFiltered);
}