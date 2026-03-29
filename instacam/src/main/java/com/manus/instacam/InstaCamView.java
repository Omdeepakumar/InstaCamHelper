package com.manus.instacam;

import android.content.Context;
import android.graphics.SurfaceTexture;
import android.opengl.GLES20;
import android.opengl.GLSurfaceView;
import android.util.AttributeSet;
import javax.microedition.khronos.egl.EGLConfig;
import javax.microedition.khronos.opengles.GL10;

public class InstaCamView extends GLSurfaceView implements GLSurfaceView.Renderer, SurfaceTexture.OnFrameAvailableListener {

    private CameraEngine cameraEngine;
    private SurfaceTexture surfaceTexture;
    private int textureId;
    private boolean updateSurface = false;
    private FilterManager filterManager;

    public InstaCamView(Context context) {
        super(context);
        init();
    }

    public InstaCamView(Context context, AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    private void init() {
        setEGLContextClientVersion(2);
        setRenderer(this);
        setRenderMode(RENDERMODE_WHEN_DIRTY);
        cameraEngine = new CameraEngine(getContext());
        filterManager = new FilterManager();
    }

    @Override
    public void onSurfaceCreated(GL10 gl, EGLConfig config) {
        textureId = filterManager.createTexture();
        surfaceTexture = new SurfaceTexture(textureId);
        surfaceTexture.setOnFrameAvailableListener(this);
        filterManager.init();
        cameraEngine.openCamera(surfaceTexture);
    }

    @Override
    public void onSurfaceChanged(GL10 gl, int width, int height) {
        GLES20.glViewport(0, 0, width, height);
        cameraEngine.setPreviewSize(width, height);
    }

    @Override
    public void onDrawFrame(GL10 gl) {
        synchronized (this) {
            if (updateSurface) {
                surfaceTexture.updateTexImage();
                updateSurface = false;
            }
        }
        filterManager.draw(textureId);
    }

    @Override
    public void onFrameAvailable(SurfaceTexture surfaceTexture) {
        synchronized (this) {
            updateSurface = true;
        }
        requestRender();
    }

    public void setFilter(int filterType) {
        filterManager.setFilter(filterType);
    }

    public void capturePhoto(String path, CameraEngine.CaptureCallback callback) {
        cameraEngine.capturePhoto(path, callback);
    }

    public void startRecording(String path) {
        cameraEngine.startRecording(path, surfaceTexture);
    }

    public void stopRecording() {
        cameraEngine.stopRecording(surfaceTexture);
    }

    public void toggleFlash(boolean on) {
        cameraEngine.toggleFlash(on);
    }

    public void switchCamera() {
        cameraEngine.switchCamera(surfaceTexture);
    }

    public void onPause() {
        super.onPause();
        cameraEngine.closeCamera();
    }

    public void onResume() {
        super.onResume();
        if (surfaceTexture != null) {
            cameraEngine.openCamera(surfaceTexture);
        }
    }

    public void onDestroy() {
        cameraEngine.stopBackgroundThread();
    }
}
