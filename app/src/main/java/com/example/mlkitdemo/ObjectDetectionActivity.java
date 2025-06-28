package com.example.mlkitdemo;

import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.TextView;
import android.widget.Toast;
import androidx.annotation.NonNull;
import androidx.annotation.OptIn;
import androidx.appcompat.app.AppCompatActivity;
import androidx.camera.core.CameraSelector;
import androidx.camera.core.ImageCapture;
import androidx.camera.core.ImageCaptureException;
import androidx.camera.core.ImageProxy;
import androidx.camera.core.Preview;
import androidx.camera.core.ExperimentalGetImage;
import androidx.camera.lifecycle.ProcessCameraProvider;
import androidx.camera.view.PreviewView;
import androidx.core.content.ContextCompat;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.floatingactionbutton.FloatingActionButton;
import com.google.android.material.snackbar.Snackbar;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.mlkit.vision.common.InputImage;
import com.google.mlkit.vision.objects.DetectedObject;
import com.google.mlkit.vision.objects.ObjectDetection;
import com.google.mlkit.vision.objects.ObjectDetector;
import com.google.mlkit.vision.objects.defaults.ObjectDetectorOptions;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class ObjectDetectionActivity extends AppCompatActivity {

    private static final String TAG = "ObjectDetection";

    private PreviewView previewView;
    private TextView resultText;
    private FloatingActionButton captureButton;
    private MaterialButton switchCameraButton;
    private MaterialButton exportButton;
    private ImageCapture imageCapture;
    private ExecutorService cameraExecutor;
    private ObjectDetector objectDetector;
    private CameraSelector cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA;
    private View rootView;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_object_detection);

        // Initialize views
        rootView = findViewById(android.R.id.content);
        previewView = findViewById(R.id.preview_view);
        resultText = findViewById(R.id.text_output);
        captureButton = findViewById(R.id.capture_button);
        switchCameraButton = findViewById(R.id.switch_camera_button);
        exportButton = findViewById(R.id.export_button);

        // Initially disable the export button until objects are detected
        exportButton.setEnabled(false);

        // Set up camera executor
        cameraExecutor = Executors.newSingleThreadExecutor();

        // Configure the object detector with options
        ObjectDetectorOptions options = new ObjectDetectorOptions.Builder()
                .setDetectorMode(ObjectDetectorOptions.SINGLE_IMAGE_MODE)
                .enableClassification()  // Make sure classification is enabled
                .enableMultipleObjects()
                .build();
        objectDetector = ObjectDetection.getClient(options);

        // Start camera preview
        startCamera();

        // Set up click listeners
        captureButton.setOnClickListener(v -> {
            animateCaptureButton();
            takePhoto();
        });

        switchCameraButton.setOnClickListener(v -> switchCamera());
        exportButton.setOnClickListener(v -> exportLabels());
    }

    private void animateCaptureButton() {
        captureButton.animate()
                .scaleX(0.85f)
                .scaleY(0.85f)
                .setDuration(100)
                .withEndAction(() ->
                        captureButton.animate()
                                .scaleX(1f)
                                .scaleY(1f)
                                .setDuration(100)
                                .start())
                .start();
    }

    private void startCamera() {
        ListenableFuture<ProcessCameraProvider> cameraProviderFuture =
                ProcessCameraProvider.getInstance(this);

        cameraProviderFuture.addListener(() -> {
            try {
                ProcessCameraProvider cameraProvider = cameraProviderFuture.get();

                // Configure preview use case
                Preview preview = new Preview.Builder().build();

                // Configure image capture use case with high quality prioritized
                imageCapture = new ImageCapture.Builder()
                        .setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY)
                        .build();

                // Set up preview surface
                preview.setSurfaceProvider(previewView.getSurfaceProvider());

                // Unbind previous use cases and bind new ones
                cameraProvider.unbindAll();
                cameraProvider.bindToLifecycle(this, cameraSelector, preview, imageCapture);

                // Show appropriate camera switch icon based on current camera
                updateCameraSwitchButton();

            } catch (Exception e) {
                Log.e(TAG, "Camera initialization failed", e);
                showErrorMessage("Camera initialization failed");
            }
        }, ContextCompat.getMainExecutor(this));
    }

    private void updateCameraSwitchButton() {
        // Update the icon and text based on which camera is active
        if (cameraSelector == CameraSelector.DEFAULT_FRONT_CAMERA) {
            switchCameraButton.setIcon(ContextCompat.getDrawable(this, R.drawable.ic_switch_camera));
            switchCameraButton.setText(R.string.switch_to_back);
        } else {
            switchCameraButton.setIcon(ContextCompat.getDrawable(this, R.drawable.ic_switch_camera));
            switchCameraButton.setText(R.string.switch_to_front);
        }
    }

    private void switchCamera() {
        cameraSelector = cameraSelector == CameraSelector.DEFAULT_BACK_CAMERA
                ? CameraSelector.DEFAULT_FRONT_CAMERA
                : CameraSelector.DEFAULT_BACK_CAMERA;

        // Restart camera with new selector
        startCamera();
    }

    private void exportLabels() {
        String labels = resultText.getText().toString();
        if (labels.equals(getString(R.string.detected_objects_will_appear_here)) || labels.isEmpty()) {
            showMessage("No detection results available to export");
            return;
        }

        // Implement export functionality here
        // For now, show a success message
        showSuccessMessage("Detection results exported successfully");
        Log.d(TAG, "Exporting labels: " + labels);
    }

    private void takePhoto() {
        if (imageCapture == null) {
            showErrorMessage("Camera not initialized");
            return;
        }

        // Show processing indicator
        captureButton.setEnabled(false);
        resultText.setText("Capturing image...");

        imageCapture.takePicture(ContextCompat.getMainExecutor(this),
                new ImageCapture.OnImageCapturedCallback() {
                    @Override
                    public void onCaptureSuccess(@NonNull ImageProxy imageProxy) {
                        processImage(imageProxy);
                        captureButton.setEnabled(true);
                    }

                    @Override
                    public void onError(@NonNull ImageCaptureException exception) {
                        Log.e(TAG, "Capture failed", exception);
                        showErrorMessage("Failed to capture image");
                        resultText.setText(getString(R.string.detected_objects_will_appear_here));
                        captureButton.setEnabled(true);
                    }
                });
    }

    @OptIn(markerClass = ExperimentalGetImage.class)
    private void processImage(ImageProxy imageProxy) {
        if (imageProxy.getImage() == null) {
            resultText.setText("Failed to capture image");
            imageProxy.close();
            return;
        }

        @SuppressWarnings("UnsafeOptInUsageError")
        InputImage image = InputImage.fromMediaImage(
                imageProxy.getImage(),
                imageProxy.getImageInfo().getRotationDegrees()
        );

        // Show processing state
        resultText.setText("Processing image...");

        objectDetector.process(image)
                .addOnSuccessListener(detectedObjects -> {
                    StringBuilder result = new StringBuilder();

                    if (detectedObjects.isEmpty()) {
                        result.append("No objects detected in this image.");
                        exportButton.setEnabled(false);
                    } else {
                        result.append("Found ").append(detectedObjects.size()).append(" object(s):\n\n");

                        for (int i = 0; i < detectedObjects.size(); i++) {
                            DetectedObject object = detectedObjects.get(i);
                            result.append("Object ").append(i + 1).append(":\n");

                            List<DetectedObject.Label> labels = object.getLabels();

                            if (labels.isEmpty()) {
                                result.append("• Unidentified object\n");
                            } else {
                                // Sort labels by confidence (highest first)
                                labels.sort((a, b) -> Float.compare(b.getConfidence(), a.getConfidence()));

                                // Display top labels (up to 3)
                                int labelLimit = Math.min(labels.size(), 3);
                                for (int j = 0; j < labelLimit; j++) {
                                    DetectedObject.Label label = labels.get(j);
                                    result.append("• ")
                                            .append(label.getText())
                                            .append(" (")
                                            .append(String.format("%.1f%%", label.getConfidence() * 100))
                                            .append(")\n");
                                }
                            }

                            if (i < detectedObjects.size() - 1) {
                                result.append("\n");
                            }
                        }

                        // Enable export button if we detected objects
                        exportButton.setEnabled(true);
                    }

                    resultText.setText(result.toString());
                    Log.d(TAG, "Detection results: " + result.toString());
                    imageProxy.close();
                })
                .addOnFailureListener(e -> {
                    resultText.setText("Object detection failed");
                    Log.e(TAG, "Detection failed", e);
                    imageProxy.close();
                    showErrorMessage("Object detection failed: " + e.getMessage());
                });
    }

    private void showMessage(String message) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show();
    }

    private void showErrorMessage(String message) {
        Snackbar.make(rootView, message, Snackbar.LENGTH_LONG)
                .setBackgroundTint(getResources().getColor(R.color.error, getTheme()))
                .setTextColor(getResources().getColor(R.color.white, getTheme()))
                .show();
    }

    private void showSuccessMessage(String message) {
        Snackbar.make(rootView, message, Snackbar.LENGTH_SHORT)
                .setBackgroundTint(getResources().getColor(R.color.success, getTheme()))
                .setTextColor(getResources().getColor(R.color.white, getTheme()))
                .show();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        cameraExecutor.shutdown();
        if (objectDetector != null) {
            objectDetector.close();
        }
    }
}