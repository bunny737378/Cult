package com.v02.senapi.player;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.Surface;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class CultPlayer {
    private static final String TAG = "CultPlayer";

    public interface OnBufferingUpdateListener {
        void onBufferingUpdate(CultPlayer player, int percent);
    }

    public interface OnPreparedListener {
        void onPrepared(CultPlayer player, long durationMs, int width, int height);
    }

    public interface OnVideoSizeChangedListener {
        void onVideoSizeChanged(CultPlayer player, int width, int height);
    }

    public interface OnPlaybackPositionListener {
        void onPlaybackPosition(CultPlayer player, long positionMs, long durationMs);
    }

    public interface OnSubtitleCueListener {
        void onSubtitleCue(CultPlayer player, String text);
    }

    public interface OnErrorListener {
        void onError(CultPlayer player, String message);
    }

    public interface OnCompletionListener {
        void onCompletion(CultPlayer player);
    }

    private final Context context;
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private final NativeMediaEngine mediaEngine;

    private OnBufferingUpdateListener bufferingUpdateListener;
    private OnPreparedListener preparedListener;
    private OnVideoSizeChangedListener videoSizeChangedListener;
    private OnPlaybackPositionListener playbackPositionListener;
    private OnSubtitleCueListener subtitleCueListener;
    private OnErrorListener errorListener;
    private OnCompletionListener completionListener;

    private final List<SubtitleTrack> subtitleTracks = new ArrayList<>();
    private int selectedSubtitleIndex = -1;
    private String selectedQuality = "auto";
    private boolean isPlaying = false;
    private long currentDuration = 0;
    private long lastReportedPosition = 0;

    public CultPlayer(Context context) {
        this.context = context.getApplicationContext();
        this.mediaEngine = new NativeMediaEngine(this.context);
        setupEngineCallbacks();
    }

    private void setupEngineCallbacks() {
        mediaEngine.setCallback(new NativeMediaEngine.EngineCallback() {
            @Override
            public void onPrepared(long durationMs, int width, int height) {
                currentDuration = durationMs;
                if (videoSizeChangedListener != null) {
                    videoSizeChangedListener.onVideoSizeChanged(CultPlayer.this, width, height);
                }
                if (preparedListener != null) {
                    preparedListener.onPrepared(CultPlayer.this, durationMs, width, height);
                }
            }

            @Override
            public void onPositionUpdate(long currentMs, long durationMs) {
                lastReportedPosition = currentMs;
                currentDuration = durationMs;
                if (playbackPositionListener != null) {
                    playbackPositionListener.onPlaybackPosition(CultPlayer.this, currentMs, durationMs);
                }
                updateSubtitleCue(currentMs);
            }

            @Override
            public void onBuffering(int percent) {
                if (bufferingUpdateListener != null) {
                    bufferingUpdateListener.onBufferingUpdate(CultPlayer.this, percent);
                }
            }

            @Override
            public void onCompletion() {
                isPlaying = false;
                if (completionListener != null) {
                    completionListener.onCompletion(CultPlayer.this);
                }
            }

            @Override
            public void onError(String message) {
                if (errorListener != null) {
                    errorListener.onError(CultPlayer.this, message);
                }
            }
        });
    }

    public void setSurface(Surface surface) {
        mediaEngine.setSurface(surface);
    }

    public void attachSurfaceView(CultSurfaceView surfaceView) {
        if (surfaceView != null) {
            surfaceView.setSurfaceListener(new CultSurfaceView.SurfaceListener() {
                @Override
                public void onSurfaceCreated(Surface surface) {
                    setSurface(surface);
                }

                @Override
                public void onSurfaceChanged(Surface surface, int format, int width, int height) {
                    setSurface(surface);
                }

                @Override
                public void onSurfaceDestroyed(Surface surface) {
                    setSurface(null);
                }
            });
        }
    }

    public void setDataSource(String url, Map<String, String> headers) {
        Log.i(TAG, "Setting data source: " + url + " with " + (headers != null ? headers.size() : 0) + " headers");
        mediaEngine.setDataSource(url, headers);
    }

    public void setSubtitles(List<SubtitleTrack> tracks) {
        this.subtitleTracks.clear();
        if (tracks != null) {
            this.subtitleTracks.addAll(tracks);
            for (SubtitleTrack track : tracks) {
                loadSubtitleTrackContent(track);
            }
        }
    }

    private void loadSubtitleTrackContent(SubtitleTrack track) {
        if (track == null || track.getUrl() == null || track.getUrl().isEmpty()) return;
        new Thread(() -> {
            try {
                URL u = new URL(track.getUrl());
                HttpURLConnection conn = (HttpURLConnection) u.openConnection();
                conn.setConnectTimeout(8000);
                conn.setReadTimeout(8000);
                conn.setRequestProperty("User-Agent", "Cult/1.0 SubtitleLoader");
                conn.connect();

                if (conn.getResponseCode() == 200) {
                    try (BufferedReader reader = new BufferedReader(new InputStreamReader(conn.getInputStream()))) {
                        StringBuilder sb = new StringBuilder();
                        String line;
                        while ((line = reader.readLine()) != null) {
                            sb.append(line).append("\n");
                        }
                        List<SubtitleTrack.Cue> parsed = VttParser.parse(sb.toString());
                        track.setCues(parsed);
                        Log.i(TAG, "Loaded " + parsed.size() + " subtitle cues for " + track.getLabel());
                    }
                }
            } catch (Exception e) {
                Log.w(TAG, "Failed to fetch subtitle track: " + track.getUrl(), e);
            }
        }).start();
    }

    public void selectSubtitle(int index) {
        this.selectedSubtitleIndex = index;
        if (index < 0 || index >= subtitleTracks.size()) {
            if (subtitleCueListener != null) {
                subtitleCueListener.onSubtitleCue(this, null);
            }
        } else {
            updateSubtitleCue(getCurrentPosition());
        }
    }

    private void updateSubtitleCue(long currentMs) {
        if (subtitleCueListener == null) return;
        if (selectedSubtitleIndex >= 0 && selectedSubtitleIndex < subtitleTracks.size()) {
            SubtitleTrack track = subtitleTracks.get(selectedSubtitleIndex);
            String cueText = track.getActiveCueText(currentMs);
            subtitleCueListener.onSubtitleCue(this, cueText);
        } else {
            subtitleCueListener.onSubtitleCue(this, null);
        }
    }

    public void selectQuality(String quality) {
        this.selectedQuality = quality != null ? quality : "auto";
        Log.i(TAG, "Quality switched to: " + this.selectedQuality);
    }

    public void play() {
        isPlaying = true;
        mediaEngine.play();
    }

    public void pause() {
        isPlaying = false;
        mediaEngine.pause();
    }

    public void seekTo(long positionMs) {
        mediaEngine.seekTo(positionMs);
        lastReportedPosition = positionMs;
        updateSubtitleCue(positionMs);
    }

    public boolean isPlaying() {
        return isPlaying;
    }

    public long getDuration() {
        return mediaEngine.getDuration();
    }

    public long getCurrentPosition() {
        return mediaEngine.getCurrentPosition();
    }

    public int getVideoWidth() {
        return mediaEngine.getVideoWidth();
    }

    public int getVideoHeight() {
        return mediaEngine.getVideoHeight();
    }

    public void release() {
        isPlaying = false;
        mediaEngine.release();
        subtitleTracks.clear();
        selectedSubtitleIndex = -1;
    }

    // Listeners
    public void setOnBufferingUpdateListener(OnBufferingUpdateListener l) { this.bufferingUpdateListener = l; }
    public void setOnPreparedListener(OnPreparedListener l) { this.preparedListener = l; }
    public void setOnVideoSizeChangedListener(OnVideoSizeChangedListener l) { this.videoSizeChangedListener = l; }
    public void setOnPlaybackPositionListener(OnPlaybackPositionListener l) { this.playbackPositionListener = l; }
    public void setOnSubtitleCueListener(OnSubtitleCueListener l) { this.subtitleCueListener = l; }
    public void setOnErrorListener(OnErrorListener l) { this.errorListener = l; }
    public void setOnCompletionListener(OnCompletionListener l) { this.completionListener = l; }
}
