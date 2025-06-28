package com.example.mlkitdemo;

import android.Manifest;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.util.Log;
import android.util.Size;
import android.widget.Button;
import android.widget.Toast;
import androidx.annotation.NonNull;
import androidx.annotation.OptIn;
import androidx.appcompat.app.AppCompatActivity;
import androidx.camera.core.Camera;
import androidx.camera.core.CameraSelector;
import androidx.camera.core.ExperimentalGetImage;
import androidx.camera.core.ImageAnalysis;
import androidx.camera.core.ImageProxy;
import androidx.camera.core.Preview;
import androidx.camera.lifecycle.ProcessCameraProvider;
import androidx.camera.view.PreviewView;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.mlkit.vision.common.InputImage;
import com.google.mlkit.vision.face.Face;
import com.google.mlkit.vision.face.FaceDetection;
import com.google.mlkit.vision.face.FaceDetector;
import com.google.mlkit.vision.face.FaceDetectorOptions;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class FaceDetectionActivity extends AppCompatActivity {

    private static final String TAG = "FaceDetectionActivity";
    private static final int REQUEST_CODE_PERMISSIONS = 100;
    private PreviewView previewView;
    private GraphicOverlay overlay;
    private Button captureFrameButton;
    private Button switchCameraButton;
    private ExecutorService cameraExecutor;
    private FaceDetector faceDetector;
    private boolean freeze = false;
    private int currentLensFacing = CameraSelector.LENS_FACING_FRONT; // Default to front camera
    private ProcessCameraProvider cameraProvider;

    // Fixed resolution for analysis to ensure consistent coordinates
    private static final Size ANALYSIS_RESOLUTION = new Size(640, 480);

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_face_detection);

        // Initialize views
        previewView = findViewById(R.id.preview_view);
        overlay = findViewById(R.id.graphic_overlay);
        captureFrameButton = findViewById(R.id.capture_frame);
        switchCameraButton = findViewById(R.id.switch_camera_button);

        cameraExecutor = Executors.newSingleThreadExecutor();

        // Configure the face detector with better options for detection
        FaceDetectorOptions options = new FaceDetectorOptions.Builder()
                .setPerformanceMode(FaceDetectorOptions.PERFORMANCE_MODE_ACCURATE)
                .setLandmarkMode(FaceDetectorOptions.LANDMARK_MODE_ALL)
                .setClassificationMode(FaceDetectorOptions.CLASSIFICATION_MODE_ALL)
                .setMinFaceSize(0.15f) // Increase detection sensitivity
                .enableTracking() // Enable face tracking for smoother detection
                .build();

        faceDetector = FaceDetection.getClient(options);

        captureFrameButton.setOnClickListener(v -> {
            freeze = !freeze;
            captureFrameButton.setText(freeze ? "Resume" : "Freeze Frame");
        });

        switchCameraButton.setOnClickListener(v -> switchCamera());

        // Check camera permission and start camera
        if (allPermissionsGranted()) {
            startCamera();
        } else {
            ActivityCompat.requestPermissions(
                    this,
                    new String[]{Manifest.permission.CAMERA},
                    REQUEST_CODE_PERMISSIONS
            );
        }
    }

    private boolean allPermissionsGranted() {
        return ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.CAMERA
        ) == PackageManager.PERMISSION_GRANTED;
    }

    @Override
    public void onRequestPermissionsResult(
            int requestCode,
            @NonNull String[] permissions,
            @NonNull int[] grantResults
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == REQUEST_CODE_PERMISSIONS) {
            if (allPermissionsGranted()) {
                startCamera();
            } else {
                Toast.makeText(
                        this,
                        "Camera permission is required for face detection.",
                        Toast.LENGTH_LONG
                ).show();
                finish();
            }
        }
    }

    private void startCamera() {
        ListenableFuture<ProcessCameraProvider> cameraProviderFuture =
                ProcessCameraProvider.getInstance(this);

        cameraProviderFuture.addListener(() -> {
            try {
                cameraProvider = cameraProviderFuture.get();
                bindCameraUseCases();
            } catch (ExecutionException | InterruptedException e) {
                Log.e(TAG, "Error getting camera provider", e);
            }
        }, ContextCompat.getMainExecutor(this));
    }

    private void bindCameraUseCases() {
        if (cameraProvider == null) {
            return;
        }

        // Unbind all use cases before rebinding
        cameraProvider.unbindAll();

        // Set up the camera selector
        CameraSelector cameraSelector = new CameraSelector.Builder()
                .requireLensFacing(currentLensFacing)
                .build();

        // Set up the preview use case
        Preview preview = new Preview.Builder()
                .build();
        preview.setSurfaceProvider(previewView.getSurfaceProvider());

        // Set up image analysis use case with a fixed resolution
        ImageAnalysis imageAnalysis = new ImageAnalysis.Builder()
                .setTargetResolution(ANALYSIS_RESOLUTION)  // Use consistent resolution
                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                .build();

        imageAnalysis.setAnalyzer(cameraExecutor, this::processImageProxy);

        try {
            // Bind all use cases to the lifecycle
            Camera camera = cameraProvider.bindToLifecycle(
                    this,
                    cameraSelector,
                    preview,
                    imageAnalysis
            );

            // Instead of waiting for previewView layout, we set the overlay dimensions immediately
            // using our fixed analysis resolution
            overlay.setCameraInfo(
                    ANALYSIS_RESOLUTION.getWidth(),
                    ANALYSIS_RESOLUTION.getHeight(),
                    currentLensFacing
            );

            Log.d(TAG, "Camera bound successfully with analysis resolution: " +
                    ANALYSIS_RESOLUTION.getWidth() + "x" + ANALYSIS_RESOLUTION.getHeight());

        } catch (Exception e) {
            Log.e(TAG, "Use case binding failed", e);
            Toast.makeText(
                    this,
                    "Failed to initialize camera: " + e.getMessage(),
                    Toast.LENGTH_SHORT
            ).show();
        }
    }

    @OptIn(markerClass = ExperimentalGetImage.class)
    private void processImageProxy(ImageProxy imageProxy) {
        if (freeze) {
            imageProxy.close();
            return;
        }

        try {
            if (imageProxy.getImage() == null) {
                imageProxy.close();
                return;
            }

            int rotationDegrees = imageProxy.getImageInfo().getRotationDegrees();
            Log.d(TAG, "Processing image with rotation: " + rotationDegrees);

            @SuppressWarnings("UnsafeOptInUsageError")
            InputImage inputImage = InputImage.fromMediaImage(
                    imageProxy.getImage(),
                    rotationDegrees
            );

            // Get actual dimensions of the image being processed
            int actualWidth = imageProxy.getWidth();
            int actualHeight = imageProxy.getHeight();
            Log.d(TAG, "Image dimensions: " + actualWidth + "x" + actualHeight);

            // Update overlay dimensions if they differ from what was set initially
            if (actualWidth != overlay.getImageWidth() || actualHeight != overlay.getImageHeight()) {
                Log.d(TAG, "Updating overlay dimensions to match image");
                overlay.setCameraInfo(actualWidth, actualHeight, currentLensFacing);
            }

            faceDetector.process(inputImage)
                    .addOnSuccessListener(faces -> {
                        Log.d(TAG, "Faces detected: " + faces.size());
                        processFaceDetectionResults(faces, imageProxy);
                    })
                    .addOnFailureListener(e -> {
                        Log.e(TAG, "Face detection failed", e);
                    })
                    .addOnCompleteListener(task -> {
                        imageProxy.close();
                    });
        } catch (Exception e) {
            Log.e(TAG, "Error processing image", e);
            imageProxy.close();
        }
    }

    private void processFaceDetectionResults(List<Face> faces, ImageProxy imageProxy) {
        int imageWidth = imageProxy.getWidth();
        int imageHeight = imageProxy.getHeight();
        int rotationDegrees = imageProxy.getImageInfo().getRotationDegrees();

        // Clear previous detections
        overlay.clear();

        // Draw faces
        for (Face face : faces) {
            FaceGraphic faceGraphic = new FaceGraphic(
                    overlay,
                    face,
                    imageWidth,
                    imageHeight,
                    rotationDegrees,
                    currentLensFacing
            );
            overlay.add(faceGraphic);
        }

        // Force redraw
        overlay.postInvalidate();
    }

    private void switchCamera() {
        currentLensFacing = (currentLensFacing == CameraSelector.LENS_FACING_FRONT)
                ? CameraSelector.LENS_FACING_BACK
                : CameraSelector.LENS_FACING_FRONT;

        Log.d(TAG, "Switching camera to: " +
                (currentLensFacing == CameraSelector.LENS_FACING_FRONT ? "FRONT" : "BACK"));

        if (cameraProvider != null) {
            bindCameraUseCases();
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (allPermissionsGranted()) {
            startCamera();
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        if (cameraProvider != null) {
            cameraProvider.unbindAll();
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        cameraExecutor.shutdown();
        if (faceDetector != null) {
            faceDetector.close();
        }
    }
}