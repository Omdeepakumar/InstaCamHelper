package com.manus.instacam.sample;

import android.Manifest;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.view.View;
import android.widget.Toast;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import com.manus.instacam.CameraEngine;
import com.manus.instacam.GalleryHelper;
import com.manus.instacam.InstaCamView;
import java.io.File;

public class MainActivity extends AppCompatActivity {

    private static final int REQUEST_PERMISSIONS = 100;
    private InstaCamView cameraView;
    private boolean isRecording = false;
    private boolean isFlashOn = false;
    private int currentFilter = 0;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        cameraView = findViewById(R.id.camera_view);

        if (allPermissionsGranted()) {
            setupCamera();
        } else {
            ActivityCompat.requestPermissions(this, new String[]{
                    Manifest.permission.CAMERA,
                    Manifest.permission.RECORD_AUDIO,
                    Manifest.permission.WRITE_EXTERNAL_STORAGE
            }, REQUEST_PERMISSIONS);
        }
    }

    private void setupCamera() {
        findViewById(R.id.btn_capture).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                final String path = getExternalFilesDir(null) + "/photo_" + System.currentTimeMillis() + ".jpg";
                cameraView.capturePhoto(path, new CameraEngine.CaptureCallback() {
                    @Override
                    public void onCaptureSuccess(final String path) {
                        runOnUiThread(new Runnable() {
                            @Override
                            public void run() {
                                GalleryHelper.saveToGallery(MainActivity.this, path, false);
                                Toast.makeText(MainActivity.this, "Photo saved to gallery!", Toast.LENGTH_SHORT).show();
                            }
                        });
                    }

                    @Override
                    public void onCaptureFailed(Exception e) {
                        runOnUiThread(new Runnable() {
                            @Override
                            public void run() {
                                Toast.makeText(MainActivity.this, "Capture failed", Toast.LENGTH_SHORT).show();
                            }
                        });
                    }
                });
            }
        });

        findViewById(R.id.btn_record).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (!isRecording) {
                    String path = getExternalFilesDir(null) + "/video_" + System.currentTimeMillis() + ".mp4";
                    cameraView.startRecording(path);
                    isRecording = true;
                    Toast.makeText(MainActivity.this, "Recording started", Toast.LENGTH_SHORT).show();
                } else {
                    cameraView.stopRecording();
                    isRecording = false;
                    Toast.makeText(MainActivity.this, "Recording stopped", Toast.LENGTH_SHORT).show();
                }
            }
        });

        findViewById(R.id.btn_filter).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                currentFilter = (currentFilter + 1) % 3;
                cameraView.setFilter(currentFilter);
            }
        });

        findViewById(R.id.btn_flash).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                isFlashOn = !isFlashOn;
                cameraView.toggleFlash(isFlashOn);
            }
        });
    }

    private boolean allPermissionsGranted() {
        return ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED &&
                ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED;
    }

    @Override
    protected void onPause() {
        super.onPause();
        cameraView.onPause();
    }

    @Override
    protected void onResume() {
        super.onResume();
        cameraView.onResume();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        cameraView.onDestroy();
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == REQUEST_PERMISSIONS) {
            if (allPermissionsGranted()) {
                setupCamera();
            } else {
                Toast.makeText(this, "Permissions not granted", Toast.LENGTH_SHORT).show();
            }
        }
    }
}
