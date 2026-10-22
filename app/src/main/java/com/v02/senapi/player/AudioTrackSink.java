package com.v02.senapi.player;

import android.media.AudioAttributes;
import android.media.AudioFormat;
import android.media.AudioManager;
import android.media.AudioTrack;
import android.os.Build;
import android.util.Log;

public class AudioTrackSink {
    private static final String TAG = "CultAudioSink";

    private AudioTrack audioTrack;
    private int sampleRate = 48000;
    private int channelConfig = AudioFormat.CHANNEL_OUT_STEREO;
    private int audioFormat = AudioFormat.ENCODING_PCM_16BIT;
    private int minBufferSize = 0;
    private float volume = 1.0f;

    public synchronized void initialize(int sampleRateInHz, int channels) {
        release();

        this.sampleRate = sampleRateInHz > 0 ? sampleRateInHz : 48000;
        this.channelConfig = channels == 1 ? AudioFormat.CHANNEL_OUT_MONO : AudioFormat.CHANNEL_OUT_STEREO;

        minBufferSize = AudioTrack.getMinBufferSize(sampleRate, channelConfig, audioFormat);
        int bufferSize = Math.max(minBufferSize * 4, 8192);

        AudioAttributes attributes = new AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_MEDIA)
                .setContentType(AudioAttributes.CONTENT_TYPE_MOVIE)
                .build();

        AudioFormat format = new AudioFormat.Builder()
                .setSampleRate(sampleRate)
                .setChannelMask(channelConfig)
                .setEncoding(audioFormat)
                .build();

        audioTrack = new AudioTrack(
                attributes,
                format,
                bufferSize,
                AudioTrack.MODE_STREAM,
                AudioManager.AUDIO_SESSION_ID_GENERATE
        );

        setVolume(volume);
        Log.i(TAG, "AudioTrack initialized with rate: " + sampleRate + " bufferSize: " + bufferSize);
    }

    public synchronized void play() {
        if (audioTrack != null && audioTrack.getState() == AudioTrack.STATE_INITIALIZED) {
            try {
                audioTrack.play();
            } catch (IllegalStateException e) {
                Log.e(TAG, "Error starting AudioTrack playback", e);
            }
        }
    }

    public synchronized void pause() {
        if (audioTrack != null && audioTrack.getState() == AudioTrack.STATE_INITIALIZED) {
            try {
                audioTrack.pause();
            } catch (IllegalStateException e) {
                Log.e(TAG, "Error pausing AudioTrack", e);
            }
        }
    }

    public synchronized void write(byte[] data, int offset, int length) {
        if (audioTrack != null && audioTrack.getState() == AudioTrack.STATE_INITIALIZED) {
            audioTrack.write(data, offset, length);
        }
    }

    public synchronized void flush() {
        if (audioTrack != null && audioTrack.getState() == AudioTrack.STATE_INITIALIZED) {
            try {
                audioTrack.flush();
            } catch (IllegalStateException e) {
                Log.e(TAG, "Error flushing AudioTrack", e);
            }
        }
    }

    public synchronized void setVolume(float vol) {
        this.volume = Math.max(0.0f, Math.min(1.0f, vol));
        if (audioTrack != null) {
            audioTrack.setVolume(this.volume);
        }
    }

    public synchronized void release() {
        if (audioTrack != null) {
            try {
                if (audioTrack.getState() == AudioTrack.STATE_INITIALIZED) {
                    audioTrack.stop();
                }
                audioTrack.release();
            } catch (Exception e) {
                Log.e(TAG, "Error releasing AudioTrack", e);
            } finally {
                audioTrack = null;
            }
        }
    }
}
