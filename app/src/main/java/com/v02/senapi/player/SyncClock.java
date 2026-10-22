package com.v02.senapi.player;

public class SyncClock {

    private long baseTimeMs = 0;
    private long basePtsMs = 0;
    private boolean isPaused = true;
    private float playbackSpeed = 1.0f;

    public synchronized void start(long startPtsMs) {
        this.basePtsMs = startPtsMs;
        this.baseTimeMs = System.currentTimeMillis();
        this.isPaused = false;
    }

    public synchronized void pause() {
        if (!isPaused) {
            this.basePtsMs = getCurrentPtsMs();
            this.isPaused = true;
        }
    }

    public synchronized void resume() {
        if (isPaused) {
            this.baseTimeMs = System.currentTimeMillis();
            this.isPaused = false;
        }
    }

    public synchronized void seekTo(long targetPtsMs) {
        this.basePtsMs = targetPtsMs;
        this.baseTimeMs = System.currentTimeMillis();
    }

    public synchronized long getCurrentPtsMs() {
        if (isPaused) {
            return basePtsMs;
        }
        long elapsedWallTime = System.currentTimeMillis() - baseTimeMs;
        return basePtsMs + (long) (elapsedWallTime * playbackSpeed);
    }

    public synchronized void setSpeed(float speed) {
        if (speed <= 0) return;
        this.basePtsMs = getCurrentPtsMs();
        this.baseTimeMs = System.currentTimeMillis();
        this.playbackSpeed = speed;
    }

    public synchronized boolean isPaused() {
        return isPaused;
    }

    public synchronized void reset() {
        this.basePtsMs = 0;
        this.baseTimeMs = 0;
        this.isPaused = true;
        this.playbackSpeed = 1.0f;
    }
}
