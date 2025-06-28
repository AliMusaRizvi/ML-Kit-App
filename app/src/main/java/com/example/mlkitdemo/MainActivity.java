package com.example.mlkitdemo;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.cardview.widget.CardView;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.card.MaterialCardView;
import com.google.android.material.transition.platform.MaterialFadeThrough;

public class MainActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        // Set up transitions
        getWindow().setEnterTransition(new MaterialFadeThrough());
        getWindow().setExitTransition(new MaterialFadeThrough());

        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        // Set up toolbar
        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);

        // Set up feature cards
        MaterialCardView textRecognitionCard = findViewById(R.id.card_text_recognition);
        MaterialCardView faceDetectionCard = findViewById(R.id.card_face_detection);
        MaterialCardView barcodeScanningCard = findViewById(R.id.card_barcode_scanning);
        MaterialCardView objectDetectionCard = findViewById(R.id.card_object_detection);

        // Set up feature buttons
        Button textRecognitionBtn = findViewById(R.id.button_text_recognition);
        Button faceDetectionBtn = findViewById(R.id.button_face_detection);
        Button barcodeScanningBtn = findViewById(R.id.button_barcode_scanning);
        Button objectDetectionBtn = findViewById(R.id.button_object_detection);
        MaterialButton learnMoreBtn = findViewById(R.id.button_learn_more);

        // Set up click listeners for cards
        setFeatureCardClickListener(textRecognitionCard, TextRecognitionActivity.class);
        setFeatureCardClickListener(faceDetectionCard, FaceDetectionActivity.class);
        setFeatureCardClickListener(barcodeScanningCard, BarcodeScanningActivity.class);
        setFeatureCardClickListener(objectDetectionCard, ObjectDetectionActivity.class);

        // Set up click listeners for buttons
        textRecognitionBtn.setOnClickListener(v -> startFeatureActivity(TextRecognitionActivity.class));
        faceDetectionBtn.setOnClickListener(v -> startFeatureActivity(FaceDetectionActivity.class));
        barcodeScanningBtn.setOnClickListener(v -> startFeatureActivity(BarcodeScanningActivity.class));
        objectDetectionBtn.setOnClickListener(v -> startFeatureActivity(ObjectDetectionActivity.class));

        // Learn more button opens ML Kit documentation
        learnMoreBtn.setOnClickListener(v -> {
            Intent browserIntent = new Intent(Intent.ACTION_VIEW,
                    Uri.parse("https://developers.google.com/ml-kit"));
            startActivity(browserIntent);
        });
    }

    /**
     * Helper method to set click listener on a feature card
     */
    private void setFeatureCardClickListener(View card, Class<?> activityClass) {
        card.setOnClickListener(v -> startFeatureActivity(activityClass));
    }

    /**
     * Starts the selected feature activity with shared element transition
     */
    private void startFeatureActivity(Class<?> activityClass) {
        Intent intent = new Intent(MainActivity.this, activityClass);
        startActivity(intent);
    }
}