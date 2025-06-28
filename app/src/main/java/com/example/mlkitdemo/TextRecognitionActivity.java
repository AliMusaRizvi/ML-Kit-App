package com.example.mlkitdemo;

import android.Manifest;
import android.annotation.SuppressLint;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;
import android.app.SearchManager;

import androidx.annotation.NonNull;
import androidx.annotation.OptIn;
import androidx.appcompat.app.AppCompatActivity;
import androidx.camera.core.Camera;
import androidx.camera.core.CameraSelector;
import androidx.camera.core.ExperimentalGetImage;
import androidx.camera.core.ImageAnalysis;
import androidx.camera.core.ImageCapture;
import androidx.camera.core.ImageProxy;
import androidx.camera.core.Preview;
import androidx.camera.lifecycle.ProcessCameraProvider;
import androidx.camera.view.PreviewView;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import com.google.android.material.floatingactionbutton.FloatingActionButton;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.mlkit.vision.common.InputImage;
import com.google.mlkit.vision.text.TextRecognition;
import com.google.mlkit.vision.text.TextRecognizer;
import com.google.mlkit.vision.text.latin.TextRecognizerOptions;

import java.util.Objects;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Consumer; // For callback

public class TextRecognitionActivity extends AppCompatActivity {

    private static final String TAG = "TextRecognitionActivity";
    private static final int CAMERA_PERMISSION_CODE = 100;

    private PreviewView previewView;
    private TextView resultText;
    private FloatingActionButton captureButton;
    private ProgressBar progressBar; // Still present in layout, but less used for real-time
    private ImageCapture imageCapture; // Still present for potential future snapshot use, but not for real-time processing
    private ExecutorService cameraExecutor;
    private Camera camera;
    private boolean flashEnabled = false;
    private FloatingActionButton flashButton;
    private Button copyTextButton;
    private Button searchTextButton;

    // Callback to update UI from TextAnalyzer
    private Consumer<String> textResultCallback;

    @SuppressLint("QueryPermissionsNeeded")
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_text_recognition);

        // Initialize UI components
        previewView = findViewById(R.id.camera_preview);
        resultText = findViewById(R.id.text_output);
        captureButton = findViewById(R.id.capture_button);
        progressBar = findViewById(R.id.progress_bar);
        flashButton = findViewById(R.id.flash_button);
        copyTextButton = findViewById(R.id.copy_text_button);
        searchTextButton = findViewById(R.id.search_text_button);

        // Initially disable copy and search buttons
        setButtonsEnabled(false);

        // Set up camera executor
        cameraExecutor = Executors.newSingleThreadExecutor();

        // Define the callback for text recognition results
        textResultCallback = detectedText -> runOnUiThread(() -> {
            if (detectedText != null && !detectedText.isEmpty()) {
                resultText.setText(detectedText);
                setButtonsEnabled(true);
            } else {
                resultText.setText(R.string.no_text_detected);
                setButtonsEnabled(false);
            }
        });

        // Check camera permission
        checkCameraPermission();

        // Set up button click listeners
        // Capture button now "freezes" the current real-time text
        captureButton.setOnClickListener(v -> {
            String currentText = resultText.getText().toString();
            if (!currentText.isEmpty() && !currentText.equals(getString(R.string.text_recognition_prompt)) && !currentText.equals(getString(R.string.no_text_detected))) {
                Toast.makeText(this, R.string.text_frozen_message, Toast.LENGTH_SHORT).show();
                // No need to re-enable buttons here, they are already enabled by real-time updates
            } else {
                Toast.makeText(this, R.string.no_text_to_freeze_message, Toast.LENGTH_SHORT).show();
            }
        });

        flashButton.setOnClickListener(v -> toggleFlash());

        // Set up Copy Text Button click listener
        copyTextButton.setOnClickListener(v -> {
            String recognizedData = resultText.getText().toString();
            if (!recognizedData.isEmpty() && !recognizedData.equals(getString(R.string.text_recognition_prompt)) && !recognizedData.equals(getString(R.string.no_text_detected))) {
                ClipboardManager clipboard = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
                ClipData clip = ClipData.newPlainText("recognized_text", recognizedData);
                clipboard.setPrimaryClip(clip);
                Toast.makeText(this, R.string.copied_text_message, Toast.LENGTH_SHORT).show();
            } else {
                Toast.makeText(this, R.string.nothing_to_copy_text_message, Toast.LENGTH_SHORT).show();
            }
        });

        // Set up Search Text Button click listener to perform a Google search
        searchTextButton.setOnClickListener(v -> {
            String dataToSearch = resultText.getText().toString().trim();
            Log.d(TAG, "Attempting to search for text: " + dataToSearch);

            if (!dataToSearch.isEmpty() && !dataToSearch.equals(getString(R.string.text_recognition_prompt)) && !dataToSearch.equals(getString(R.string.no_text_detected))) {
                try {
                    Intent intent = new Intent(Intent.ACTION_WEB_SEARCH);
                    intent.putExtra(SearchManager.QUERY, dataToSearch);

                    if (intent.resolveActivity(getPackageManager()) != null) {
                        startActivity(intent);
                        Toast.makeText(this, R.string.searching_text_message, Toast.LENGTH_SHORT).show();
                        Log.d(TAG, "Successfully launched search intent for: " + dataToSearch);
                    } else {
                        Toast.makeText(this, R.string.no_app_to_handle_search_text_message, Toast.LENGTH_LONG).show();
                        Log.w(TAG, "No app found to handle web search for: " + dataToSearch);
                    }
                } catch (Exception e) {
                    Log.e(TAG, "Failed to perform search for text: " + dataToSearch, e);
                    Toast.makeText(this, R.string.failed_to_search_text_message, Toast.LENGTH_LONG).show();
                }
            } else {
                Toast.makeText(this, R.string.nothing_to_search_text_message, Toast.LENGTH_SHORT).show();
                setButtonsEnabled(false); // Disable if no text
                Log.d(TAG, "No text to search.");
            }
        });
    }

    /**
     * Helper method to enable/disable copy and search buttons.
     * @param enabled True to enable, false to disable.
     */
    private void setButtonsEnabled(boolean enabled) {
        copyTextButton.setEnabled(enabled);
        copyTextButton.setAlpha(enabled ? 1.0f : 0.5f);
        searchTextButton.setEnabled(enabled);
        searchTextButton.setAlpha(enabled ? 1.0f : 0.5f);
    }

    private void toggleFlash() {
        if (camera != null && camera.getCameraInfo().hasFlashUnit()) {
            flashEnabled = !flashEnabled;
            camera.getCameraControl().enableTorch(flashEnabled);

            // Update flash button icon and content description using custom drawables
            int iconResource = flashEnabled ?
                    R.drawable.ic_flash_on :
                    R.drawable.ic_flash_off;
            flashButton.setImageResource(iconResource);
            flashButton.setContentDescription(getString(flashEnabled ? R.string.flash_on_desc : R.string.flash_off_desc));
        } else {
            Toast.makeText(this, R.string.flash_not_available_message, Toast.LENGTH_SHORT).show();
        }
    }

    private void checkCameraPermission() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA)
                != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this,
                    new String[]{Manifest.permission.CAMERA}, CAMERA_PERMISSION_CODE);
        } else {
            startCamera();
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions,
                                           @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == CAMERA_PERMISSION_CODE) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                startCamera();
            } else {
                Log.e(TAG, "Camera permission denied");
                resultText.setText(R.string.camera_permission_required);
                Toast.makeText(this, R.string.camera_permission_denied_toast, Toast.LENGTH_SHORT).show();
                setButtonsEnabled(false); // Disable buttons if permission denied
            }
        }
    }

    private void startCamera() {
        ListenableFuture<ProcessCameraProvider> cameraProviderFuture =
                ProcessCameraProvider.getInstance(this);

        cameraProviderFuture.addListener(() -> {
            try {
                ProcessCameraProvider cameraProvider = cameraProviderFuture.get();

                Preview preview = new Preview.Builder().build();
                preview.setSurfaceProvider(previewView.getSurfaceProvider());

                CameraSelector cameraSelector = new CameraSelector.Builder()
                        .requireLensFacing(CameraSelector.LENS_FACING_BACK)
                        .build();

                // ImageCapture is still bound but not used for real-time processing
                imageCapture = new ImageCapture.Builder()
                        .setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY)
                        .build();

                // Setup ImageAnalysis for real-time text detection
                ImageAnalysis imageAnalysis = new ImageAnalysis.Builder()
                        .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST) // Process only the latest frame
                        .build();

                // Set the analyzer with our custom TextAnalyzer and the callback
                imageAnalysis.setAnalyzer(cameraExecutor, new TextAnalyzer(textResultCallback));

                cameraProvider.unbindAll(); // Unbind all use cases before rebinding

                // Bind all use cases to camera
                camera = cameraProvider.bindToLifecycle(
                        this,
                        cameraSelector,
                        preview,
                        imageCapture, // Keep for potential future snapshot feature, though not used in current real-time flow
                        imageAnalysis // Bind the real-time image analysis
                );

                if (camera.getCameraInfo().hasFlashUnit()) {
                    flashButton.setVisibility(View.VISIBLE);
                    flashButton.setImageResource(R.drawable.ic_flash_off);
                    flashButton.setContentDescription(getString(R.string.flash_off_desc));
                } else {
                    flashButton.setVisibility(View.GONE);
                }

            } catch (ExecutionException | InterruptedException e) {
                Log.e(TAG, "Camera initialization failed", e);
                Toast.makeText(this, R.string.failed_to_initialize_camera, Toast.LENGTH_SHORT).show();
                setButtonsEnabled(false); // Disable buttons on camera init failure
            }
        }, ContextCompat.getMainExecutor(this));
    }

    // This method is now effectively replaced by real-time analysis
    // The capture button now triggers a "freeze" of the current real-time text.
    private void takePhoto() {
        // This method's logic is now handled by the captureButton's OnClickListener directly
        // and primarily serves to 'freeze' the currently displayed real-time text.
        // No explicit image capture or processing happens here anymore.
    }

    // Inner class for real-time text analysis
    @OptIn(markerClass = ExperimentalGetImage.class)
    private static class TextAnalyzer implements ImageAnalysis.Analyzer {
        private final TextRecognizer recognizer;
        private final Consumer<String> resultCallback;

        TextAnalyzer(Consumer<String> resultCallback) {
            this.recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS);
            this.resultCallback = resultCallback;
        }

        @Override
        public void analyze(@NonNull ImageProxy imageProxy) {
            // Ensure the image is not null
            if (imageProxy.getImage() == null) {
                imageProxy.close();
                resultCallback.accept(null); // Indicate no text or error
                return;
            }

            // Create InputImage from ImageProxy
            @SuppressWarnings("UnsafeOptInUsageError")
            InputImage inputImage = InputImage.fromMediaImage(
                    Objects.requireNonNull(imageProxy.getImage()),
                    imageProxy.getImageInfo().getRotationDegrees()
            );

            // Process image for text recognition
            recognizer.process(inputImage)
                    .addOnSuccessListener(visionText -> {
                        String detectedText = visionText.getText();
                        resultCallback.accept(detectedText.isEmpty() ? null : detectedText); // Pass null if no text
                        imageProxy.close(); // Important: Close the image proxy
                    })
                    .addOnFailureListener(e -> {
                        Log.e(TAG, "Real-time text recognition failed: " + e.getMessage(), e);
                        resultCallback.accept(null); // Indicate error
                        imageProxy.close(); // Important: Close the image proxy
                    });
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (cameraExecutor != null) {
            cameraExecutor.shutdown();
        }
    }
}
