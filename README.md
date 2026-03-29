# InstaCamHelper: Instagram-style Camera Library for Android

InstaCamHelper is an Android library designed to provide an Instagram-style camera experience within your applications. It offers functionalities such as real-time camera preview with filters, photo capture, video recording, flashlight control, and saving media to the device gallery.

## Features

-   **Real-time Camera Preview**: Utilizes Android's Camera2 API for efficient camera management.
-   **Photo Capture**: Capture high-resolution photos.
-   **Video Recording**: Record videos with audio.
-   **Image Filters**: Apply real-time filters (Normal, Grayscale, Sepia) using OpenGL ES shaders.
-   **Flashlight Control**: Toggle the camera flashlight (torch mode).
-   **Camera Switching**: Seamlessly switch between front and rear cameras.
-   **Gallery Integration**: Automatically save captured photos and videos to the device's media gallery.

## Installation

To integrate InstaCamHelper into your Android project, follow these steps:

1.  **Add JitPack to your root `build.gradle`** (if you plan to publish/use via JitPack, otherwise you can include the module directly):

    ```gradle
    allprojects {
        repositories {
            google()
            mavenCentral()
            // If using JitPack
            // maven { url 'https://jitpack.io' }
        }
    }
    ```

2.  **Add the library dependency to your app's `build.gradle`**:

    ```gradle
    dependencies {
        implementation project(':instacam')
        // If using JitPack
        // implementation 'com.github.YOUR_USERNAME:InstaCamHelper:Tag'
    }
    ```

    *Note: For this project, we are including the `instacam` module directly as a local project dependency.* 

## Permissions

Ensure you have the necessary permissions declared in your `AndroidManifest.xml`:

```xml
<uses-permission android:name="android.permission.CAMERA" />
<uses-permission android:name="android.permission.RECORD_AUDIO" />
<uses-permission android:name="android.permission.WRITE_EXTERNAL_STORAGE" android:maxSdkVersion="32" />
<uses-permission android:name="android.permission.READ_EXTERNAL_STORAGE" android:maxSdkVersion="32" />
<uses-permission android:name="android.permission.READ_MEDIA_IMAGES" />
<uses-permission android:name="android.permission.READ_MEDIA_VIDEO" />

<uses-feature android:name="android.hardware.camera" />
<uses-feature android:name="android.hardware.camera.autofocus" />
```

And request them at runtime in your Activity:

```java
private static final int REQUEST_PERMISSIONS = 100;

private boolean allPermissionsGranted() {
    return ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED;
}

@Override
public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
    super.onRequestPermissionsResult(requestCode, permissions, grantResults);
    if (requestCode == REQUEST_PERMISSIONS) {
        if (allPermissionsGranted()) {
            // Permissions granted, proceed with camera setup
        } else {
            Toast.makeText(this, "Permissions not granted", Toast.LENGTH_SHORT).show();
            finish();
        }
    }
}

// Call this where appropriate, e.g., in onCreate
if (!allPermissionsGranted()) {
    ActivityCompat.requestPermissions(this, new String[]{
            Manifest.permission.CAMERA,
            Manifest.permission.RECORD_AUDIO,
            Manifest.permission.WRITE_EXTERNAL_STORAGE
    }, REQUEST_PERMISSIONS);
}
```

## Usage

1.  **Add `InstaCamView` to your layout XML** (e.g., `activity_main.xml`):

    ```xml
    <com.manus.instacam.InstaCamView
        android:id="@+id/camera_view"
        android:layout_width="match_parent"
        android:layout_height="match_parent" />
    ```

2.  **Initialize and control `InstaCamView` in your Activity** (e.g., `MainActivity.java`):

    ```java
    public class MainActivity extends AppCompatActivity {

        private InstaCamView cameraView;
        private boolean isRecording = false;
        private boolean isFlashOn = false;
        private int currentFilter = 0; // 0: Normal, 1: Grayscale, 2: Sepia

        @Override
        protected void onCreate(Bundle savedInstanceState) {
            super.onCreate(savedInstanceState);
            setContentView(R.layout.activity_main);

            cameraView = findViewById(R.id.camera_view);

            // Ensure permissions are granted before setting up camera
            // ... (permission handling as shown above)

            setupCameraControls();
        }

        private void setupCameraControls() {
            // Photo Capture
            findViewById(R.id.btn_capture).setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    final String path = getExternalFilesDir(null) + "/photo_" + System.currentTimeMillis() + ".jpg";
                    cameraView.capturePhoto(path, new CameraEngine.CaptureCallback() {
                        @Override
                        public void onCaptureSuccess(String path) {
                            GalleryHelper.saveToGallery(MainActivity.this, path, false);
                            Toast.makeText(MainActivity.this, "Photo saved!", Toast.LENGTH_SHORT).show();
                        }

                        @Override
                        public void onCaptureFailed(Exception e) {
                            Toast.makeText(MainActivity.this, "Capture failed: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                        }
                    });
                }
            });

            // Video Recording
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

            // Apply Filters
            findViewById(R.id.btn_filter).setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    currentFilter = (currentFilter + 1) % 3; // Cycle through 0, 1, 2
                    cameraView.setFilter(currentFilter);
                    String filterName = "Normal";
                    if (currentFilter == 1) filterName = "Grayscale";
                    else if (currentFilter == 2) filterName = "Sepia";
                    Toast.makeText(MainActivity.this, "Filter: " + filterName, Toast.LENGTH_SHORT).show();
                }
            });

            // Toggle Flashlight
            findViewById(R.id.btn_flash).setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    isFlashOn = !isFlashOn;
                    cameraView.toggleFlash(isFlashOn);
                    Toast.makeText(MainActivity.this, "Flash: " + (isFlashOn ? "On" : "Off"), Toast.LENGTH_SHORT).show();
                }
            });

            // You can also add buttons for switchCamera() and setZoom() if needed
        }
    }
    ```

## Contributing

Feel free to fork the repository, make improvements, and submit pull requests.

## License

This project is licensed under the MIT License - see the LICENSE file for details. (License file will be added later)
