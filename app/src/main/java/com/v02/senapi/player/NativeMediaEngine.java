package com.v02.senapi.player;

import android.content.Context;
import android.media.MediaCodec;
import android.media.MediaExtractor;
import android.media.MediaFormat;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.Surface;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.HashMap;
import java.util.Map;

public class NativeMediaEngine {
    private static final String TAG = "NativeMediaEngine";
    private static boolean sNativeLibraryLoaded = false;

    static {
        try {
            System.loadLibrary("cult_ffmpeg");
            sNativeLibraryLoaded = true;
            Log.i(TAG, "Native library 'cult_ffmpeg' loaded successfully");
        } catch (Throwable t) {
            sNativeLibraryLoaded = false;
            Log.w(TAG, "Native library 'cult_ffmpeg' not found; using hybrid Java MediaCodec engine");
        }
    }

    public interface EngineCallback {
        void onPrepared(long durationMs, int width, int height);
        void onPositionUpdate(long currentMs, long durationMs);
        void onBuffering(int percent);
        void onCompletion();
        void onError(String message);
    }

    private final Context context;
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private EngineCallback callback;

    private long nativeHandle = 0;
    private Surface surface;
    private String currentUrl;
    private Map<String, String> currentHeaders = new HashMap<>();

    // Hybrid Java MediaCodec & MediaExtractor fallback pipeline
    private MediaExtractor extractor;
    private MediaCodec videoCodec;
    private MediaCodec audioCodec;
    private final AudioTrackSink audioSink = new AudioTrackSink();
    private final SyncClock syncClock = new SyncClock();

    private Thread decodeThread;
    private volatile boolean isRunning = false;
    private volatile boolean isPlaying = false;
    private long durationMs = 0;
    private int videoWidth = 1920;
    private int videoHeight = 1080;

    public NativeMediaEngine(Context context) {
        this.context = context.getApplicationContext();
        if (sNativeLibraryLoaded) {
            try {
                this.nativeHandle = nativeInit();
            } catch (Throwable t) {
                Log.e(TAG, "Failed to initialize native engine", t);
                this.nativeHandle = 0;
            }
        }
    }

    public void setCallback(EngineCallback cb) {
        this.callback = cb;
    }

    public void setSurface(Surface surf) {
        this.surface = surf;
        if (sNativeLibraryLoaded && nativeHandle != 0) {
            try {
                nativeSetSurface(nativeHandle, surf);
            } catch (Throwable t) {
                Log.e(TAG, "Error setting native surface", t);
            }
        }
    }

    public void setDataSource(String url, Map<String, String> headers) {
        this.currentUrl = url;
        this.currentHeaders = headers != null ? new HashMap<>(headers) : new HashMap<>();

        if (sNativeLibraryLoaded && nativeHandle != 0) {
            try {
                nativeSetDataSource(nativeHandle, url, this.currentHeaders);
                durationMs = nativeGetDuration(nativeHandle);
                videoWidth = nativeGetVideoWidth(nativeHandle);
                videoHeight = nativeGetVideoHeight(nativeHandle);
                if (callback != null) {
                    mainHandler.post(() -> callback.onPrepared(durationMs, videoWidth, videoHeight));
                }
                return;
            } catch (Throwable t) {
                Log.e(TAG, "Native setDataSource failed, falling back to hybrid pipeline", t);
            }
        }

        // Hybrid MediaCodec + MediaExtractor pipeline
        stopPlaybackThread();
        new Thread(() -> initJavaPipeline(url, currentHeaders)).start();
    }

    private void initJavaPipeline(String url, Map<String, String> headers) {
        try {
            if (extractor != null) {
                extractor.release();
                extractor = null;
            }
            extractor = new MediaExtractor();

            Map<String, String> httpHeaders = new HashMap<>();
            httpHeaders.put("User-Agent", "Cult/1.0 (Linux; Android; Mobile; ModularStreamer)");
            if (headers != null) {
                httpHeaders.putAll(headers);
            }

            Log.i(TAG, "Connecting to media stream with headers: " + httpHeaders);
            extractor.setDataSource(context, Uri.parse(url), httpHeaders);

            int numTracks = extractor.getTrackCount();
            int videoTrack = -1;
            int audioTrack = -1;

            for (int i = 0; i < numTracks; i++) {
                MediaFormat format = extractor.getTrackFormat(i);
                String mime = format.getString(MediaFormat.KEY_MIME);
                if (mime != null) {
                    if (mime.startsWith("video/") && videoTrack < 0) {
                        videoTrack = i;
                        if (format.containsKey(MediaFormat.KEY_WIDTH)) {
                            videoWidth = format.getInteger(MediaFormat.KEY_WIDTH);
                        }
                        if (format.containsKey(MediaFormat.KEY_HEIGHT)) {
                            videoHeight = format.getInteger(MediaFormat.KEY_HEIGHT);
                        }
                        if (format.containsKey(MediaFormat.KEY_DURATION)) {
                            durationMs = format.getLong(MediaFormat.KEY_DURATION) / 1000L;
                        }
                    } else if (mime.startsWith("audio/") && audioTrack < 0) {
                        audioTrack = i;
                        int sampleRate = format.containsKey(MediaFormat.KEY_SAMPLE_RATE) ?
                                format.getInteger(MediaFormat.KEY_SAMPLE_RATE) : 48000;
                        int channels = format.containsKey(MediaFormat.KEY_CHANNEL_COUNT) ?
                                format.getInteger(MediaFormat.KEY_CHANNEL_COUNT) : 2;
                        audioSink.initialize(sampleRate, channels);
                    }
                }
            }

            if (videoTrack >= 0) {
                extractor.selectTrack(videoTrack);
                MediaFormat videoFormat = extractor.getTrackFormat(videoTrack);
                String videoMime = videoFormat.getString(MediaFormat.KEY_MIME);
                videoCodec = MediaCodec.createDecoderByType(videoMime);
                videoCodec.configure(videoFormat, surface, null, 0);
                videoCodec.start();
            }

            if (durationMs <= 0) {
                durationMs = 7200000L; // Default fallback duration 2hr if live/hls
            }

            mainHandler.post(() -> {
                if (callback != null) {
                    callback.onPrepared(durationMs, videoWidth, videoHeight);
                }
            });

            startPlaybackThread(videoTrack, audioTrack);

        } catch (Exception e) {
            Log.e(TAG, "Error in Java MediaCodec initialization", e);
            mainHandler.post(() -> {
                if (callback != null) {
                    callback.onError("Media decoding error: " + e.getMessage());
                }
            });
        }
    }

    private void startPlaybackThread(int videoTrack, int audioTrack) {
        isRunning = true;
        decodeThread = new Thread(() -> {
            MediaCodec.BufferInfo bufferInfo = new MediaCodec.BufferInfo();
            syncClock.start(0);

            long lastProgressReportMs = 0;

            while (isRunning) {
                if (!isPlaying) {
                    try {
                        Thread.sleep(30);
                    } catch (InterruptedException ignored) {}
                    continue;
                }

                try {
                    if (videoCodec != null && extractor != null) {
                        int inIndex = videoCodec.dequeueInputBuffer(10000);
                        if (inIndex >= 0) {
                            ByteBuffer buffer = videoCodec.getInputBuffer(inIndex);
                            if (buffer != null) {
                                int sampleSize = extractor.readSampleData(buffer, 0);
                                if (sampleSize < 0) {
                                    videoCodec.queueInputBuffer(inIndex, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM);
                                } else {
                                    long sampleTime = extractor.getSampleTime();
                                    videoCodec.queueInputBuffer(inIndex, 0, sampleSize, sampleTime, 0);
                                    extractor.advance();
                                }
                            }
                        }

                        int outIndex = videoCodec.dequeueOutputBuffer(bufferInfo, 10000);
                        if (outIndex >= 0) {
                            if ((bufferInfo.flags & MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) {
                                mainHandler.post(() -> {
                                    if (callback != null) callback.onCompletion();
                                });
                                isPlaying = false;
                            } else {
                                long presentationMs = bufferInfo.presentationTimeUs / 1000L;
                                syncClock.seekTo(presentationMs);

                                videoCodec.releaseOutputBuffer(outIndex, surface != null);

                                long now = System.currentTimeMillis();
                                if (now - lastProgressReportMs >= 250) {
                                    lastProgressReportMs = now;
                                    mainHandler.post(() -> {
                                        if (callback != null) {
                                            callback.onPositionUpdate(presentationMs, durationMs);
                                        }
                                    });
                                }
                            }
                        }
                    }

                    Thread.sleep(12); // Paced ~60fps
                } catch (Exception e) {
                    Log.w(TAG, "Playback loop warning: " + e.getMessage());
                }
            }
        });
        decodeThread.start();
    }

    private void stopPlaybackThread() {
        isRunning = false;
        if (decodeThread != null) {
            try {
                decodeThread.join(500);
            } catch (InterruptedException ignored) {}
            decodeThread = null;
        }
        if (videoCodec != null) {
            try {
                videoCodec.stop();
                videoCodec.release();
            } catch (Exception ignored) {}
            videoCodec = null;
        }
        if (audioCodec != null) {
            try {
                audioCodec.stop();
                audioCodec.release();
            } catch (Exception ignored) {}
            audioCodec = null;
        }
        audioSink.release();
    }

    public void play() {
        isPlaying = true;
        syncClock.resume();
        audioSink.play();

        if (sNativeLibraryLoaded && nativeHandle != 0) {
            try {
                nativePlay(nativeHandle);
            } catch (Throwable ignored) {}
        }
    }

    public void pause() {
        isPlaying = false;
        syncClock.pause();
        audioSink.pause();

        if (sNativeLibraryLoaded && nativeHandle != 0) {
            try {
                nativePause(nativeHandle);
            } catch (Throwable ignored) {}
        }
    }

    public void seekTo(long positionMs) {
        syncClock.seekTo(positionMs);
        if (extractor != null) {
            try {
                extractor.seekTo(positionMs * 1000L, MediaExtractor.SEEK_TO_CLOSEST_SYNC);
                if (videoCodec != null) {
                    videoCodec.flush();
                }
                audioSink.flush();
            } catch (Exception ignored) {}
        }

        if (sNativeLibraryLoaded && nativeHandle != 0) {
            try {
                nativeSeekTo(nativeHandle, positionMs);
            } catch (Throwable ignored) {}
        }
    }

    public long getDuration() {
        if (sNativeLibraryLoaded && nativeHandle != 0) {
            try {
                return nativeGetDuration(nativeHandle);
            } catch (Throwable ignored) {}
        }
        return durationMs;
    }

    public long getCurrentPosition() {
        if (sNativeLibraryLoaded && nativeHandle != 0) {
            try {
                return nativeGetCurrentPosition(nativeHandle);
            } catch (Throwable ignored) {}
        }
        return syncClock.getCurrentPtsMs();
    }

    public int getVideoWidth() {
        return videoWidth;
    }

    public int getVideoHeight() {
        return videoHeight;
    }

    public void release() {
        stopPlaybackThread();
        if (extractor != null) {
            extractor.release();
            extractor = null;
        }

        if (sNativeLibraryLoaded && nativeHandle != 0) {
            try {
                nativeRelease(nativeHandle);
                nativeHandle = 0;
            } catch (Throwable ignored) {}
        }
    }

    // Native JNI Declarations
    private native long nativeInit();
    private native void nativeSetDataSource(long handle, String url, Map<String, String> headers);
    private native void nativeSetSurface(long handle, Surface surface);
    private native void nativePlay(long handle);
    private native void nativePause(long handle);
    private native void nativeSeekTo(long handle, long positionMs);
    private native long nativeGetDuration(long handle);
    private native long nativeGetCurrentPosition(long handle);
    private native int nativeGetVideoWidth(long handle);
    private native int nativeGetVideoHeight(long handle);
    private native void nativeRelease(long handle);
}
