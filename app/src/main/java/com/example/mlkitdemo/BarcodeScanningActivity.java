package com.example.mlkitdemo;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.media.MediaPlayer;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.Vibrator;
import android.util.Log;
import android.util.Patterns;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;
import android.app.SearchManager; // Added for web search intent

import androidx.appcompat.app.AppCompatActivity;
import androidx.camera.core.CameraSelector;
import androidx.camera.core.ImageAnalysis;
import androidx.camera.core.ImageProxy;
import androidx.camera.core.Preview;
import androidx.camera.lifecycle.ProcessCameraProvider;
import androidx.camera.view.PreviewView;
import androidx.core.content.ContextCompat;

import com.google.common.util.concurrent.ListenableFuture;
import com.google.mlkit.vision.barcode.common.Barcode;
import com.google.mlkit.vision.barcode.BarcodeScanning;
import com.google.mlkit.vision.barcode.BarcodeScanner;
import com.google.mlkit.vision.common.InputImage;


import java.util.Objects;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class BarcodeScanningActivity extends AppCompatActivity {

    private static final String TAG = "BarcodeScanningActivity"; // Tag for logging

    private PreviewView previewView;
    private TextView resultText;
    private Button copyButton;
    private Button openLinkButton;
    private ExecutorService cameraExecutor;
    private boolean scanned = false; // Flag to prevent multiple scans immediately
    private final Handler handler = new Handler(Looper.getMainLooper()); // Use main looper for UI updates

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_barcode_scanning);

        // Initialize UI elements
        previewView = findViewById(R.id.preview_view);
        resultText = findViewById(R.id.result_text);
        copyButton = findViewById(R.id.copy_button);
        openLinkButton = findViewById(R.id.open_link_button);

        // Initially disable open link button
        openLinkButton.setEnabled(false);
        openLinkButton.setAlpha(0.5f); // Visually indicate disabled state

        // Set up Copy Button click listener
        copyButton.setOnClickListener(v -> {
            String scannedData = resultText.getText().toString();
            if (!scannedData.isEmpty() && !scannedData.equals(getString(R.string.scan_prompt))) {
                ClipboardManager clipboard = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
                ClipData clip = ClipData.newPlainText("barcode_data", scannedData);
                clipboard.setPrimaryClip(clip);
                Toast.makeText(this, R.string.copied_to_clipboard_message, Toast.LENGTH_SHORT).show();
            } else {
                Toast.makeText(this, R.string.nothing_to_copy_message, Toast.LENGTH_SHORT).show();
            }
        });

        // Set up Open Link Button click listener to perform a Google search
        openLinkButton.setOnClickListener(v -> {
            // Trim whitespace from the scanned data before processing
            String data = resultText.getText().toString().trim();
            Log.d(TAG, "Attempting to search for: " + data);

            if (!data.isEmpty() && !data.equals(getString(R.string.scan_prompt))) {
                try {
                    // Create an Intent for web search
                    Intent intent = new Intent(Intent.ACTION_WEB_SEARCH);
                    intent.putExtra(SearchManager.QUERY, data); // Set the search query

                    // Verify that there's an app to handle this intent (e.g., a web browser)
                    if (intent.resolveActivity(getPackageManager()) != null) {
                        startActivity(intent);
                        Toast.makeText(this," R.string.searching_google_message", Toast.LENGTH_SHORT).show();
                        Log.d(TAG, "Successfully launched search intent for: " + data);
                    } else {
                        Toast.makeText(this, "R.string.no_app_to_handle_search_message", Toast.LENGTH_LONG).show();
                        Log.w(TAG, "No app found to handle web search for: " + data);
                    }
                } catch (Exception e) {
                    Log.e(TAG, "Failed to perform search: " + data, e);
                    Toast.makeText(this," R.string.failed_to_search_message", Toast.LENGTH_LONG).show();
                }
            } else {
                Toast.makeText(this, "R.string.nothing_to_search_message", Toast.LENGTH_SHORT).show();
                openLinkButton.setEnabled(false); // Just in case, disable it again
                openLinkButton.setAlpha(0.5f);
                Log.d(TAG, "No data to search.");
            }
        });

        // Initialize camera executor for background processing
        cameraExecutor = Executors.newSingleThreadExecutor();
        // Start camera preview and barcode analysis
        startCamera();
    }

    /**
     * Initializes and starts the CameraX preview and image analysis for barcode scanning.
     */
    private void startCamera() {
        ListenableFuture<ProcessCameraProvider> cameraProviderFuture = ProcessCameraProvider.getInstance(this);

        cameraProviderFuture.addListener(() -> {
            try {
                ProcessCameraProvider cameraProvider = cameraProviderFuture.get();

                // Select the back camera as default
                CameraSelector cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA;

                // Set up the preview use case to display camera feed
                Preview preview = new Preview.Builder().build();
                preview.setSurfaceProvider(previewView.getSurfaceProvider());

                // Set up the image analysis use case for barcode detection
                ImageAnalysis imageAnalysis = new ImageAnalysis.Builder()
                        // Using STRATEGY_DROP_AND_QUEUE as an alternative to STRATEGY_KEEP_LATEST
                        // This strategy drops the oldest frame if the queue is full and adds the new frame.
                        .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                        .build();

                // Get an instance of BarcodeScanner
                BarcodeScanner scanner = BarcodeScanning.getClient();

                // Set the analyzer for image analysis
                imageAnalysis.setAnalyzer(cameraExecutor, image -> {
                    // Only process if not already scanned to prevent rapid re-scans
                    if (!scanned) {
                        // Suppress lint warning for unsafe usage of ImageProxy.getImage()
                        @SuppressWarnings("UnsafeOptInUsageError")
                        InputImage inputImage = InputImage.fromMediaImage(
                                Objects.requireNonNull(image.getImage()), image.getImageInfo().getRotationDegrees());

                        // Process the image for barcodes
                        scanner.process(inputImage)
                                .addOnSuccessListener(barcodes -> {
                                    if (!barcodes.isEmpty()) {
                                        // If barcodes are detected, get the first one
                                        Barcode barcode = barcodes.get(0);
                                        scanned = true; // Set flag to true to pause further scanning
                                        handleResult(barcode.getRawValue()); // Handle the scanned data

                                        // Reset scan status after a delay to allow re-scanning
                                        handler.postDelayed(() -> {
                                            runOnUiThread(() -> {
                                                resultText.setText(R.string.scan_prompt); // Reset text
                                                openLinkButton.setEnabled(false); // Disable link button
                                                openLinkButton.setAlpha(0.5f);
                                            });
                                            scanned = false; // Allow new scans
                                        }, 3000); // Reset after 3 seconds
                                    }
                                    image.close(); // Close the image proxy to release resources
                                })
                                .addOnFailureListener(e -> {
                                    Log.e(TAG, "Barcode detection failed", e);
                                    image.close(); // Close the image proxy even on failure
                                });
                    } else {
                        image.close(); // If already scanned, just close the image
                    }
                });

                // Unbind any previously bound use cases
                cameraProvider.unbindAll();
                // Bind both Preview and ImageAnalysis to the lifecycle of the activity
                cameraProvider.bindToLifecycle(this, cameraSelector, preview, imageAnalysis);

            } catch (Exception e) {
                Log.e(TAG, "Error initializing camera: " + e.getMessage(), e);
                Toast.makeText(this, R.string.camera_init_error_message, Toast.LENGTH_LONG).show();
            }
        }, ContextCompat.getMainExecutor(this)); // Ensure listener runs on main thread
    }

    /**
     * Handles the result of a successful barcode scan.
     * Updates the UI, plays a sound, and vibrates the device.
     * @param data The raw value of the scanned barcode.
     */
    private void handleResult(String data) {
        runOnUiThread(() -> {
            resultText.setText(data); // Display scanned data

            // Trim data before validation
            String trimmedData = data.trim();
            Log.d(TAG, "Handling result: " + trimmedData);

            // Enable the search button if there is any non-empty scanned data
            if (!trimmedData.isEmpty() && !trimmedData.equals(getString(R.string.scan_prompt))) {
                openLinkButton.setEnabled(true);
                openLinkButton.setAlpha(1.0f);
                Log.d(TAG, "Result is not empty, enabling search button.");
            } else {
                openLinkButton.setEnabled(false);
                openLinkButton.setAlpha(0.5f);
                Log.d(TAG, "Result is empty or prompt, disabling search button.");
            }
        });

        // Play notification sound
        try {
            MediaPlayer mediaPlayer = MediaPlayer.create(this, R.raw.notification);
            if (mediaPlayer != null) {
                mediaPlayer.start();
                mediaPlayer.setOnCompletionListener(MediaPlayer::release); // Release media player after completion
            }
        } catch (Exception e) {
            Log.e(TAG, "Error playing notification sound", e);
        }

        // Vibrate the device
        Vibrator vibrator = (Vibrator) getSystemService(Context.VIBRATOR_SERVICE);
        if (vibrator != null && vibrator.hasVibrator()) {
            vibrator.vibrate(300); // Vibrate for 300 milliseconds
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        // Shut down the camera executor to prevent memory leaks
        if (cameraExecutor != null) {
            cameraExecutor.shutdown();
        }
        // Remove any pending callbacks from the handler
        handler.removeCallbacksAndMessages(null);
    }
}
