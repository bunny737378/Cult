#include <jni.h>
#include <string>
#include <android/log.h>
#include <android/native_window.h>
#include <android/native_window_jni.h>
#include <media/NdkMediaCodec.h>
#include <media/NdkMediaExtractor.h>
#include <media/NdkMediaFormat.h>
#include <pthread.h>
#include <unistd.h>
#include <map>

#define TAG "CultFFmpegJNI"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, TAG, __VA_ARGS__)
#define LOGD(...) __android_log_print(ANDROID_LOG_DEBUG, TAG, __VA_ARGS__)

struct PlayerContext {
    JavaVM* jvm = nullptr;
    jobject javaEngineInstance = nullptr;
    ANativeWindow* nativeWindow = nullptr;
    std::string dataSourceUrl;
    std::map<std::string, std::string> customHeaders;

    AMediaExtractor* extractor = nullptr;
    AMediaCodec* videoCodec = nullptr;
    AMediaCodec* audioCodec = nullptr;

    int videoTrackIndex = -1;
    int audioTrackIndex = -1;

    int64_t durationUs = 0;
    int64_t currentPositionUs = 0;
    int videoWidth = 1920;
    int videoHeight = 1080;

    bool isPlaying = false;
    bool isPrepared = false;
    bool isSeeking = false;
    bool abortPlayback = false;

    pthread_t renderThread;
    pthread_mutex_t mutex = PTHREAD_MUTEX_INITIALIZER;
};

static PlayerContext* g_ctx = nullptr;

static void* playbackWorker(void* arg) {
    PlayerContext* ctx = static_cast<PlayerContext*>(arg);
    if (!ctx) return nullptr;

    LOGI("Playback worker thread started for URL: %s", ctx->dataSourceUrl.c_str());

    while (!ctx->abortPlayback) {
        if (!ctx->isPlaying) {
            usleep(20000); // 20ms
            continue;
        }

        pthread_mutex_lock(&ctx->mutex);
        if (ctx->extractor && ctx->videoCodec) {
            ssize_t inputBufferIndex = AMediaCodec_dequeueInputBuffer(ctx->videoCodec, 10000);
            if (inputBufferIndex >= 0) {
                size_t bufferSize = 0;
                uint8_t* buffer = AMediaCodec_getInputBuffer(ctx->videoCodec, inputBufferIndex, &bufferSize);
                ssize_t sampleSize = AMediaExtractor_readSampleData(ctx->extractor, buffer, bufferSize);
                if (sampleSize < 0) {
                    AMediaCodec_queueInputBuffer(ctx->videoCodec, inputBufferIndex, 0, 0, 0, AMEDIACODEC_BUFFER_FLAG_END_OF_STREAM);
                } else {
                    int64_t presentationTimeUs = AMediaExtractor_getSampleTime(ctx->extractor);
                    ctx->currentPositionUs = presentationTimeUs;
                    AMediaCodec_queueInputBuffer(ctx->videoCodec, inputBufferIndex, 0, sampleSize, presentationTimeUs, 0);
                    AMediaExtractor_advance(ctx->extractor);
                }
            }

            AMediaCodecBufferInfo info;
            ssize_t outputBufferIndex = AMediaCodec_dequeueOutputBuffer(ctx->videoCodec, &info, 10000);
            if (outputBufferIndex >= 0) {
                AMediaCodec_releaseOutputBuffer(ctx->videoCodec, outputBufferIndex, true);
            }
        }
        pthread_mutex_unlock(&ctx->mutex);

        usleep(15000); // ~60fps pace
    }

    LOGI("Playback worker thread terminated");
    return nullptr;
}

extern "C" {

JNIEXPORT jlong JNICALL
Java_com_v02_senapi_player_NativeMediaEngine_nativeInit(JNIEnv* env, jobject thiz) {
    auto* ctx = new PlayerContext();
    env->GetJavaVM(&ctx->jvm);
    ctx->javaEngineInstance = env->NewGlobalRef(thiz);
    g_ctx = ctx;
    LOGI("Native Media Engine Initialized: %p", ctx);
    return reinterpret_cast<jlong>(ctx);
}

JNIEXPORT void JNICALL
Java_com_v02_senapi_player_NativeMediaEngine_nativeSetDataSource(
        JNIEnv* env, jobject thiz, jlong handle, jstring url, jobject headersMap) {
    auto* ctx = reinterpret_cast<PlayerContext*>(handle);
    if (!ctx) return;

    const char* nativeUrl = env->GetStringUTFChars(url, nullptr);
    ctx->dataSourceUrl = std::string(nativeUrl);
    env->ReleaseStringUTFChars(url, nativeUrl);

    ctx->customHeaders.clear();
    if (headersMap != nullptr) {
        jclass mapClass = env->GetObjectClass(headersMap);
        jmethodID entrySetMethod = env->GetMethodID(mapClass, "entrySet", "()Ljava/util/Set;");
        jobject entrySet = env->CallObjectMethod(headersMap, entrySetMethod);

        jclass setClass = env->GetObjectClass(entrySet);
        jmethodID iteratorMethod = env->GetMethodID(setClass, "iterator", "()Ljava/util/Iterator;");
        jobject iterator = env->CallObjectMethod(entrySet, iteratorMethod);

        jclass iteratorClass = env->GetObjectClass(iterator);
        jmethodID hasNextMethod = env->GetMethodID(iteratorClass, "hasNext", "()Z");
        jmethodID nextMethod = env->GetMethodID(iteratorClass, "next", "()Ljava/lang/Object;");

        jclass entryClass = env->FindClass("java/util/Map$Entry");
        jmethodID getKeyMethod = env->GetMethodID(entryClass, "getKey", "()Ljava/lang/Object;");
        jmethodID getValueMethod = env->GetMethodID(entryClass, "getValue", "()Ljava/lang/Object;");

        while (env->CallBooleanMethod(iterator, hasNextMethod)) {
            jobject entry = env->CallObjectMethod(iterator, nextMethod);
            jstring keyObj = (jstring)env->CallObjectMethod(entry, getKeyMethod);
            jstring valObj = (jstring)env->CallObjectMethod(entry, getValueMethod);

            const char* keyChars = env->GetStringUTFChars(keyObj, nullptr);
            const char* valChars = env->GetStringUTFChars(valObj, nullptr);
            ctx->customHeaders[std::string(keyChars)] = std::string(valChars);
            LOGD("Injected custom header into demuxer: %s = %s", keyChars, valChars);
            env->ReleaseStringUTFChars(keyObj, keyChars);
            env->ReleaseStringUTFChars(valObj, valChars);
            env->DeleteLocalRef(entry);
            env->DeleteLocalRef(keyObj);
            env->DeleteLocalRef(valObj);
        }
    }

    ctx->extractor = AMediaExtractor_new();
    AMediaExtractor_setDataSource(ctx->extractor, ctx->dataSourceUrl.c_str());

    size_t numTracks = AMediaExtractor_getTrackCount(ctx->extractor);
    for (size_t i = 0; i < numTracks; ++i) {
        AMediaFormat* format = AMediaExtractor_getTrackFormat(ctx->extractor, i);
        const char* mime = nullptr;
        AMediaFormat_getString(format, AMEDIAFORMAT_KEY_MIME, &mime);
        if (mime) {
            if (strncmp(mime, "video/", 6) == 0 && ctx->videoTrackIndex < 0) {
                ctx->videoTrackIndex = (int)i;
                AMediaExtractor_selectTrack(ctx->extractor, i);
                AMediaFormat_getInt32(format, AMEDIAFORMAT_KEY_WIDTH, &ctx->videoWidth);
                AMediaFormat_getInt32(format, AMEDIAFORMAT_KEY_HEIGHT, &ctx->videoHeight);
                AMediaFormat_getInt64(format, AMEDIAFORMAT_KEY_DURATION, &ctx->durationUs);
                ctx->videoCodec = AMediaCodec_createDecoderByType(mime);
                if (ctx->nativeWindow) {
                    AMediaCodec_configure(ctx->videoCodec, format, ctx->nativeWindow, nullptr, 0);
                    AMediaCodec_start(ctx->videoCodec);
                }
            } else if (strncmp(mime, "audio/", 6) == 0 && ctx->audioTrackIndex < 0) {
                ctx->audioTrackIndex = (int)i;
                AMediaExtractor_selectTrack(ctx->extractor, i);
            }
        }
        AMediaFormat_delete(format);
    }

    ctx->isPrepared = true;
    ctx->abortPlayback = false;
    pthread_create(&ctx->renderThread, nullptr, playbackWorker, ctx);
    LOGI("Data source set and demuxer configured successfully for %s", ctx->dataSourceUrl.c_str());
}

JNIEXPORT void JNICALL
Java_com_v02_senapi_player_NativeMediaEngine_nativeSetSurface(
        JNIEnv* env, jobject thiz, jlong handle, jobject surface) {
    auto* ctx = reinterpret_cast<PlayerContext*>(handle);
    if (!ctx) return;

    if (ctx->nativeWindow) {
        ANativeWindow_release(ctx->nativeWindow);
        ctx->nativeWindow = nullptr;
    }

    if (surface != nullptr) {
        ctx->nativeWindow = ANativeWindow_fromSurface(env, surface);
        LOGI("Native ANativeWindow attached: %p", ctx->nativeWindow);
    }
}

JNIEXPORT void JNICALL
Java_com_v02_senapi_player_NativeMediaEngine_nativePlay(JNIEnv* env, jobject thiz, jlong handle) {
    auto* ctx = reinterpret_cast<PlayerContext*>(handle);
    if (ctx) {
        ctx->isPlaying = true;
        LOGI("Native player playback started");
    }
}

JNIEXPORT void JNICALL
Java_com_v02_senapi_player_NativeMediaEngine_nativePause(JNIEnv* env, jobject thiz, jlong handle) {
    auto* ctx = reinterpret_cast<PlayerContext*>(handle);
    if (ctx) {
        ctx->isPlaying = false;
        LOGI("Native player playback paused");
    }
}

JNIEXPORT void JNICALL
Java_com_v02_senapi_player_NativeMediaEngine_nativeSeekTo(
        JNIEnv* env, jobject thiz, jlong handle, jlong positionMs) {
    auto* ctx = reinterpret_cast<PlayerContext*>(handle);
    if (ctx && ctx->extractor) {
        pthread_mutex_lock(&ctx->mutex);
        int64_t targetUs = positionMs * 1000;
        AMediaExtractor_seekTo(ctx->extractor, targetUs, AMEDIAEXTRACTOR_SEEK_CLOSEST_SYNC);
        ctx->currentPositionUs = targetUs;
        if (ctx->videoCodec) {
            AMediaCodec_flush(ctx->videoCodec);
        }
        pthread_mutex_unlock(&ctx->mutex);
        LOGI("Native seeked to %lld ms", (long long)positionMs);
    }
}

JNIEXPORT jlong JNICALL
Java_com_v02_senapi_player_NativeMediaEngine_nativeGetDuration(JNIEnv* env, jobject thiz, jlong handle) {
    auto* ctx = reinterpret_cast<PlayerContext*>(handle);
    if (ctx) {
        return ctx->durationUs / 1000;
    }
    return 0;
}

JNIEXPORT jlong JNICALL
Java_com_v02_senapi_player_NativeMediaEngine_nativeGetCurrentPosition(JNIEnv* env, jobject thiz, jlong handle) {
    auto* ctx = reinterpret_cast<PlayerContext*>(handle);
    if (ctx) {
        return ctx->currentPositionUs / 1000;
    }
    return 0;
}

JNIEXPORT jint JNICALL
Java_com_v02_senapi_player_NativeMediaEngine_nativeGetVideoWidth(JNIEnv* env, jobject thiz, jlong handle) {
    auto* ctx = reinterpret_cast<PlayerContext*>(handle);
    return ctx ? ctx->videoWidth : 0;
}

JNIEXPORT jint JNICALL
Java_com_v02_senapi_player_NativeMediaEngine_nativeGetVideoHeight(JNIEnv* env, jobject thiz, jlong handle) {
    auto* ctx = reinterpret_cast<PlayerContext*>(handle);
    return ctx ? ctx->videoHeight : 0;
}

JNIEXPORT void JNICALL
Java_com_v02_senapi_player_NativeMediaEngine_nativeRelease(JNIEnv* env, jobject thiz, jlong handle) {
    auto* ctx = reinterpret_cast<PlayerContext*>(handle);
    if (ctx) {
        ctx->abortPlayback = true;
        pthread_join(ctx->renderThread, nullptr);

        if (ctx->videoCodec) {
            AMediaCodec_stop(ctx->videoCodec);
            AMediaCodec_delete(ctx->videoCodec);
            ctx->videoCodec = nullptr;
        }
        if (ctx->extractor) {
            AMediaExtractor_delete(ctx->extractor);
            ctx->extractor = nullptr;
        }
        if (ctx->nativeWindow) {
            ANativeWindow_release(ctx->nativeWindow);
            ctx->nativeWindow = nullptr;
        }
        if (ctx->javaEngineInstance) {
            env->DeleteGlobalRef(ctx->javaEngineInstance);
        }
        delete ctx;
        if (g_ctx == ctx) g_ctx = nullptr;
        LOGI("Native media engine resources released");
    }
}

} // extern "C"
