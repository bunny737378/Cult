package com.v02.senapi.player;

import android.content.Context;
import android.graphics.Color;
import android.graphics.PixelFormat;
import android.util.AttributeSet;
import android.view.Surface;
import android.view.SurfaceHolder;
import android.view.SurfaceView;

public class CultSurfaceView extends SurfaceView implements SurfaceHolder.Callback {

    public interface SurfaceListener {
        void onSurfaceCreated(Surface surface);
        void onSurfaceChanged(Surface surface, int format, int width, int height);
        void onSurfaceDestroyed(Surface surface);
    }

    public enum ScaleMode {
        FIT_PARENT,
        CROP_ZOOM,
        STRETCH
    }

    private SurfaceListener surfaceListener;
    private int videoWidth = 0;
    private int videoHeight = 0;
    private ScaleMode scaleMode = ScaleMode.FIT_PARENT;

    public CultSurfaceView(Context context) {
        super(context);
        init();
    }

    public CultSurfaceView(Context context, AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    public CultSurfaceView(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init();
    }

    private void init() {
        getHolder().addCallback(this);
        getHolder().setFormat(PixelFormat.RGBX_8888);
        setBackgroundColor(Color.TRANSPARENT);
    }

    public void setSurfaceListener(SurfaceListener listener) {
        this.surfaceListener = listener;
        Surface surface = getHolder().getSurface();
        if (surface != null && surface.isValid() && listener != null) {
            listener.onSurfaceCreated(surface);
        }
    }

    public void setVideoDimensions(int width, int height) {
        if (width > 0 && height > 0 && (this.videoWidth != width || this.videoHeight != height)) {
            this.videoWidth = width;
            this.videoHeight = height;
            post(this::requestLayout);
        }
    }

    public void setScaleMode(ScaleMode mode) {
        this.scaleMode = mode;
        post(this::requestLayout);
    }

    public ScaleMode getScaleMode() {
        return scaleMode;
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        int width = getDefaultSize(videoWidth, widthMeasureSpec);
        int height = getDefaultSize(videoHeight, heightMeasureSpec);

        if (videoWidth > 0 && videoHeight > 0 && scaleMode != ScaleMode.STRETCH) {
            int widthSpecMode = MeasureSpec.getMode(widthMeasureSpec);
            int widthSpecSize = MeasureSpec.getSize(widthMeasureSpec);
            int heightSpecMode = MeasureSpec.getMode(heightMeasureSpec);
            int heightSpecSize = MeasureSpec.getSize(heightMeasureSpec);

            if (widthSpecMode == MeasureSpec.EXACTLY && heightSpecMode == MeasureSpec.EXACTLY) {
                width = widthSpecSize;
                height = heightSpecSize;

                if (scaleMode == ScaleMode.FIT_PARENT) {
                    if (videoWidth * height < width * videoHeight) {
                        width = height * videoWidth / videoHeight;
                    } else if (videoWidth * height > width * videoHeight) {
                        height = width * videoHeight / videoWidth;
                    }
                } else if (scaleMode == ScaleMode.CROP_ZOOM) {
                    if (videoWidth * height < width * videoHeight) {
                        height = width * videoHeight / videoWidth;
                    } else if (videoWidth * height > width * videoHeight) {
                        width = height * videoWidth / videoHeight;
                    }
                }
            } else if (widthSpecMode == MeasureSpec.EXACTLY) {
                width = widthSpecSize;
                height = width * videoHeight / videoWidth;
                if (heightSpecMode == MeasureSpec.AT_MOST && height > heightSpecSize) {
                    height = heightSpecSize;
                }
            } else if (heightSpecMode == MeasureSpec.EXACTLY) {
                height = heightSpecSize;
                width = height * videoWidth / videoHeight;
                if (widthSpecMode == MeasureSpec.AT_MOST && width > widthSpecSize) {
                    width = widthSpecSize;
                }
            } else {
                width = videoWidth;
                height = videoHeight;
                if (heightSpecMode == MeasureSpec.AT_MOST && height > heightSpecSize) {
                    height = heightSpecSize;
                    width = height * videoWidth / videoHeight;
                }
                if (widthSpecMode == MeasureSpec.AT_MOST && width > widthSpecSize) {
                    width = widthSpecSize;
                    height = width * videoHeight / videoWidth;
                }
            }
        }

        setMeasuredDimension(width, height);
    }

    @Override
    public void surfaceCreated(SurfaceHolder holder) {
        if (surfaceListener != null) {
            surfaceListener.onSurfaceCreated(holder.getSurface());
        }
    }

    @Override
    public void surfaceChanged(SurfaceHolder holder, int format, int width, int height) {
        if (surfaceListener != null) {
            surfaceListener.onSurfaceChanged(holder.getSurface(), format, width, height);
        }
    }

    @Override
    public void surfaceDestroyed(SurfaceHolder holder) {
        if (surfaceListener != null) {
            surfaceListener.onSurfaceDestroyed(holder.getSurface());
        }
    }
}
