#include <jni.h>
#include <android/log.h>
#include <projectM-4/projectM.h>
#include <cstring>

#define LOG_TAG "ProjectMJNI"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, LOG_TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)

extern "C" {

JNIEXPORT jlong JNICALL
Java_com_vibrdrome_app_visualizer_ProjectMBridge_nativeCreate(
        JNIEnv *env, jobject thiz, jint width, jint height) {
    projectm_handle handle = projectm_create();
    if (!handle) {
        LOGE("Failed to create projectM instance");
        return 0;
    }
    projectm_set_window_size(handle, width, height);
    LOGI("projectM created: %dx%d", width, height);
    return reinterpret_cast<jlong>(handle);
}

JNIEXPORT void JNICALL
Java_com_vibrdrome_app_visualizer_ProjectMBridge_nativeDestroy(
        JNIEnv *env, jobject thiz, jlong handle) {
    if (handle) {
        projectm_destroy(reinterpret_cast<projectm_handle>(handle));
        LOGI("projectM destroyed");
    }
}

JNIEXPORT void JNICALL
Java_com_vibrdrome_app_visualizer_ProjectMBridge_nativeRenderFrame(
        JNIEnv *env, jobject thiz, jlong handle) {
    if (handle) {
        projectm_opengl_render_frame(reinterpret_cast<projectm_handle>(handle));
    }
}

JNIEXPORT void JNICALL
Java_com_vibrdrome_app_visualizer_ProjectMBridge_nativeResize(
        JNIEnv *env, jobject thiz, jlong handle, jint width, jint height) {
    if (handle) {
        projectm_set_window_size(reinterpret_cast<projectm_handle>(handle), width, height);
    }
}

JNIEXPORT void JNICALL
Java_com_vibrdrome_app_visualizer_ProjectMBridge_nativeAddAudioData(
        JNIEnv *env, jobject thiz, jlong handle, jbyteArray data) {
    if (!handle) return;
    jsize len = env->GetArrayLength(data);
    jbyte *bytes = env->GetByteArrayElements(data, nullptr);
    if (bytes) {
        projectm_pcm_add_uint8(
                reinterpret_cast<projectm_handle>(handle),
                reinterpret_cast<const uint8_t *>(bytes),
                len,
                PROJECTM_MONO
        );
        env->ReleaseByteArrayElements(data, bytes, JNI_ABORT);
    }
}

JNIEXPORT void JNICALL
Java_com_vibrdrome_app_visualizer_ProjectMBridge_nativeLoadPreset(
        JNIEnv *env, jobject thiz, jlong handle, jstring path, jboolean smooth) {
    if (!handle) return;
    const char *cpath = env->GetStringUTFChars(path, nullptr);
    projectm_load_preset_file(reinterpret_cast<projectm_handle>(handle), cpath, smooth);
    LOGI("Loaded preset: %s", cpath);
    env->ReleaseStringUTFChars(path, cpath);
}

JNIEXPORT void JNICALL
Java_com_vibrdrome_app_visualizer_ProjectMBridge_nativeLoadPresetData(
        JNIEnv *env, jobject thiz, jlong handle, jstring data, jboolean smooth) {
    if (!handle) return;
    const char *cdata = env->GetStringUTFChars(data, nullptr);
    projectm_load_preset_data(reinterpret_cast<projectm_handle>(handle), cdata, smooth);
    env->ReleaseStringUTFChars(data, cdata);
}

JNIEXPORT jdouble JNICALL
Java_com_vibrdrome_app_visualizer_ProjectMBridge_nativeGetPresetDuration(
        JNIEnv *env, jobject thiz, jlong handle) {
    if (!handle) return 0.0;
    return projectm_get_preset_duration(reinterpret_cast<projectm_handle>(handle));
}

JNIEXPORT void JNICALL
Java_com_vibrdrome_app_visualizer_ProjectMBridge_nativeSetPresetDuration(
        JNIEnv *env, jobject thiz, jlong handle, jdouble seconds) {
    if (!handle) return;
    projectm_set_preset_duration(reinterpret_cast<projectm_handle>(handle), seconds);
}

JNIEXPORT void JNICALL
Java_com_vibrdrome_app_visualizer_ProjectMBridge_nativeSelectRandomPreset(
        JNIEnv *env, jobject thiz, jlong handle, jboolean smooth) {
    if (!handle) return;
    // Load idle preset as fallback — random preset requires playlist API
    projectm_load_preset_file(reinterpret_cast<projectm_handle>(handle), "idle://", smooth);
}

JNIEXPORT void JNICALL
Java_com_vibrdrome_app_visualizer_ProjectMBridge_nativeSetPresetPath(
        JNIEnv *env, jobject thiz, jlong handle, jstring path) {
    // Store path for preset loading — handled at Kotlin level
}

JNIEXPORT void JNICALL
Java_com_vibrdrome_app_visualizer_ProjectMBridge_nativeSetPresetLocked(
        JNIEnv *env, jobject thiz, jlong handle, jboolean locked) {
    // Preset lock — handled at Kotlin level
}

} // extern "C"
