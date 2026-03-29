package com.manus.instacam;

import android.annotation.SuppressLint;
import android.content.Context;
import android.graphics.ImageFormat;
import android.graphics.Rect;
import android.graphics.SurfaceTexture;
import android.hardware.camera2.CameraAccessException;
import android.hardware.camera2.CameraCaptureSession;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CameraDevice;
import android.hardware.camera2.CameraManager;
import android.hardware.camera2.CaptureRequest;
import android.hardware.camera2.params.StreamConfigurationMap;
import android.media.Image;
import android.media.ImageReader;
import android.media.MediaRecorder;
import android.os.Handler;
import android.os.HandlerThread;
import android.util.Log;
import android.util.Size;
import android.view.Surface;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

public class CameraEngine {
    private static final String TAG = "CameraEngine";
    private final Context context;
    private CameraDevice cameraDevice;
    private CameraCaptureSession captureSession;
    private CaptureRequest.Builder previewRequestBuilder;
    private Size previewSize;
    private String cameraId = "0";
    private HandlerThread backgroundThread;
    private Handler backgroundHandler;
    private ImageReader imageReader;
    private MediaRecorder mediaRecorder;
    private boolean isRecording = false;
    private boolean isFlashOn = false;
    private float currentZoom = 1.0f;
    private float maxZoom = 1.0f;
    private Rect sensorArraySize;

    public interface CaptureCallback {
        void onCaptureSuccess(String path);
        void onCaptureFailed(Exception e);
    }

    public CameraEngine(Context context) {
        this.context = context;
        startBackgroundThread();
    }

    private void startBackgroundThread() {
        backgroundThread = new HandlerThread("CameraBackground");
        backgroundThread.start();
        backgroundHandler = new Handler(backgroundThread.getLooper());
    }

    public void stopBackgroundThread() {
        if (backgroundThread != null) {
            backgroundThread.quitSafely();
            try {
                backgroundThread.join();
                backgroundThread = null;
                backgroundHandler = null;
            } catch (InterruptedException e) {
                Log.e(TAG, "Interrupted while stopping background thread", e);
            }
        }
    }

    @SuppressLint("MissingPermission")
    public void openCamera(final SurfaceTexture surfaceTexture) {
        CameraManager manager = (CameraManager) context.getSystemService(Context.CAMERA_SERVICE);
        try {
            CameraCharacteristics characteristics = manager.getCameraCharacteristics(cameraId);
            maxZoom = characteristics.get(CameraCharacteristics.SCALER_AVAILABLE_MAX_DIGITAL_ZOOM);
            sensorArraySize = characteristics.get(CameraCharacteristics.SENSOR_INFO_ACTIVE_ARRAY_SIZE);
            StreamConfigurationMap map = characteristics.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP);
            if (map == null) return;
            previewSize = chooseOptimalSize(map.getOutputSizes(SurfaceTexture.class), 1080, 1920);

            manager.openCamera(cameraId, new CameraDevice.StateCallback() {
                @Override
                public void onOpened(CameraDevice camera) {
                    cameraDevice = camera;
                    createCameraPreviewSession(surfaceTexture);
                }

                @Override
                public void onDisconnected(CameraDevice camera) {
                    camera.close();
                    cameraDevice = null;
                }

                @Override
                public void onError(CameraDevice camera, int error) {
                    camera.close();
                    cameraDevice = null;
                }
            }, backgroundHandler);
        } catch (CameraAccessException e) {
            Log.e(TAG, "Camera access exception", e);
        }
    }

    private void createCameraPreviewSession(SurfaceTexture surfaceTexture) {
        try {
            if (cameraDevice == null || surfaceTexture == null) return;
            surfaceTexture.setDefaultBufferSize(previewSize.getWidth(), previewSize.getHeight());
            Surface surface = new Surface(surfaceTexture);
            previewRequestBuilder = cameraDevice.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW);
            previewRequestBuilder.addTarget(surface);

            setupImageReader();

            cameraDevice.createCaptureSession(Arrays.asList(surface, imageReader.getSurface()), new CameraCaptureSession.StateCallback() {
                @Override
                public void onConfigured(CameraCaptureSession session) {
                    if (cameraDevice == null) return;
                    captureSession = session;
                    try {
                        previewRequestBuilder.set(CaptureRequest.CONTROL_AF_MODE, CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_PICTURE);
                        updatePreview();
                    } catch (Exception e) {
                        Log.e(TAG, "Failed to start preview", e);
                    }
                }

                @Override
                public void onConfigureFailed(CameraCaptureSession session) {
                    Log.e(TAG, "Configuration failed");
                }
            }, backgroundHandler);
        } catch (CameraAccessException e) {
            Log.e(TAG, "Failed to create preview session", e);
        }
    }

    private void setupImageReader() {
        imageReader = ImageReader.newInstance(previewSize.getWidth(), previewSize.getHeight(), ImageFormat.JPEG, 2);
    }

    public void capturePhoto(final String path, final CaptureCallback callback) {
        if (cameraDevice == null || captureSession == null) return;
        try {
            final CaptureRequest.Builder captureBuilder = cameraDevice.createCaptureRequest(CameraDevice.TEMPLATE_STILL_CAPTURE);
            captureBuilder.addTarget(imageReader.getSurface());
            captureBuilder.set(CaptureRequest.CONTROL_AF_MODE, CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_PICTURE);
            applyFlash(captureBuilder);
            applyZoom(captureBuilder);

            imageReader.setOnImageAvailableListener(new ImageReader.OnImageAvailableListener() {
                @Override
                public void onImageAvailable(ImageReader reader) {
                    Image image = null;
                    try {
                        image = reader.acquireLatestImage();
                        if (image != null) {
                            ByteBuffer buffer = image.getPlanes()[0].getBuffer();
                            byte[] bytes = new byte[buffer.remaining()];
                            buffer.get(bytes);
                            save(bytes, path);
                            callback.onCaptureSuccess(path);
                        }
                    } catch (IOException e) {
                        callback.onCaptureFailed(e);
                    } finally {
                        if (image != null) image.close();
                    }
                }
            }, backgroundHandler);

            captureSession.capture(captureBuilder.build(), null, backgroundHandler);
        } catch (CameraAccessException e) {
            callback.onCaptureFailed(e);
        }
    }

    private void save(byte[] bytes, String path) throws IOException {
        FileOutputStream output = null;
        try {
            output = new FileOutputStream(new File(path));
            output.write(bytes);
        } finally {
            if (output != null) output.close();
        }
    }

    public void startRecording(String path, SurfaceTexture surfaceTexture) {
        if (isRecording || cameraDevice == null) return;
        try {
            setupMediaRecorder(path);
            Surface recorderSurface = mediaRecorder.getSurface();
            Surface previewSurface = new Surface(surfaceTexture);
            
            previewRequestBuilder = cameraDevice.createCaptureRequest(CameraDevice.TEMPLATE_RECORD);
            previewRequestBuilder.addTarget(previewSurface);
            previewRequestBuilder.addTarget(recorderSurface);

            cameraDevice.createCaptureSession(Arrays.asList(previewSurface, recorderSurface), new CameraCaptureSession.StateCallback() {
                @Override
                public void onConfigured(CameraCaptureSession session) {
                    captureSession = session;
                    try {
                        updatePreview();
                        mediaRecorder.start();
                        isRecording = true;
                    } catch (Exception e) {
                        Log.e(TAG, "Failed to start recording", e);
                    }
                }

                @Override
                public void onConfigureFailed(CameraCaptureSession session) {
                    Log.e(TAG, "Recording configuration failed");
                }
            }, backgroundHandler);
        } catch (IOException | CameraAccessException e) {
            Log.e(TAG, "Failed to start recording", e);
        }
    }

    private void setupMediaRecorder(String path) throws IOException {
        mediaRecorder = new MediaRecorder();
        mediaRecorder.setAudioSource(MediaRecorder.AudioSource.MIC);
        mediaRecorder.setVideoSource(MediaRecorder.VideoSource.SURFACE);
        mediaRecorder.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4);
        mediaRecorder.setOutputFile(path);
        mediaRecorder.setVideoEncodingBitRate(10000000);
        mediaRecorder.setVideoFrameRate(30);
        mediaRecorder.setVideoSize(previewSize.getWidth(), previewSize.getHeight());
        mediaRecorder.setVideoEncoder(MediaRecorder.VideoEncoder.H264);
        mediaRecorder.setAudioEncoder(MediaRecorder.AudioEncoder.AAC);
        mediaRecorder.prepare();
    }

    public void stopRecording(SurfaceTexture surfaceTexture) {
        if (!isRecording) return;
        try {
            mediaRecorder.stop();
        } catch (RuntimeException e) {
            Log.e(TAG, "MediaRecorder stop failed", e);
        }
        mediaRecorder.reset();
        isRecording = false;
        createCameraPreviewSession(surfaceTexture);
    }

    public void toggleFlash(boolean on) {
        isFlashOn = on;
        if (previewRequestBuilder != null) {
            applyFlash(previewRequestBuilder);
            updatePreview();
        }
    }

    private void applyFlash(CaptureRequest.Builder builder) {
        if (isFlashOn) {
            builder.set(CaptureRequest.FLASH_MODE, CaptureRequest.FLASH_MODE_TORCH);
        } else {
            builder.set(CaptureRequest.FLASH_MODE, CaptureRequest.FLASH_MODE_OFF);
        }
    }

    public void setZoom(float zoom) {
        if (zoom < 1.0f || zoom > maxZoom) return;
        currentZoom = zoom;
        if (previewRequestBuilder != null) {
            applyZoom(previewRequestBuilder);
            updatePreview();
        }
    }

    private void applyZoom(CaptureRequest.Builder builder) {
        if (sensorArraySize == null) return;
        int centerX = sensorArraySize.width() / 2;
        int centerY = sensorArraySize.height() / 2;
        int deltaX = (int) (0.5f * sensorArraySize.width() / currentZoom);
        int deltaY = (int) (0.5f * sensorArraySize.height() / currentZoom);
        Rect zoomRect = new Rect(centerX - deltaX, centerY - deltaY, centerX + deltaX, centerY + deltaY);
        builder.set(CaptureRequest.SCALER_CROP_REGION, zoomRect);
    }

    private void updatePreview() {
        if (captureSession == null || previewRequestBuilder == null) return;
        try {
            captureSession.setRepeatingRequest(previewRequestBuilder.build(), null, backgroundHandler);
        } catch (CameraAccessException e) {
            Log.e(TAG, "Update preview failed", e);
        }
    }

    public void switchCamera(SurfaceTexture surfaceTexture) {
        cameraId = cameraId.equals("0") ? "1" : "0";
        closeCamera();
        openCamera(surfaceTexture);
    }

    public void closeCamera() {
        if (captureSession != null) {
            captureSession.close();
            captureSession = null;
        }
        if (cameraDevice != null) {
            cameraDevice.close();
            cameraDevice = null;
        }
        if (imageReader != null) {
            imageReader.close();
            imageReader = null;
        }
    }

    public void setPreviewSize(int width, int height) {
        // Handled during openCamera
    }

    private static Size chooseOptimalSize(Size[] choices, int width, int height) {
        List<Size> bigEnough = new ArrayList<>();
        for (Size option : choices) {
            if (option.getWidth() >= width && option.getHeight() >= height) {
                bigEnough.add(option);
            }
        }
        if (bigEnough.size() > 0) {
            return Collections.min(bigEnough, new Comparator<Size>() {
                @Override
                public int compare(Size lhs, Size rhs) {
                    return Long.signum((long) lhs.getWidth() * lhs.getHeight() - (long) rhs.getWidth() * rhs.getHeight());
                }
            });
        } else {
            return choices[0];
        }
    }
}
