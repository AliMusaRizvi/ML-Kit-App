package com.example.mlkitdemo;

import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.PointF;
import android.graphics.Rect;
import android.graphics.RectF;
import android.util.Log;

import androidx.camera.core.CameraSelector;

import com.google.mlkit.vision.face.Face;
import com.google.mlkit.vision.face.FaceLandmark;

/**
 * Graphic instance for rendering face position, orientation, landmarks, etc.
 */
public class FaceGraphic extends GraphicOverlay.Graphic {
    private static final String TAG = "FaceGraphic";
    private static final float BOX_STROKE_WIDTH = 8.0f;
    private static final float ID_TEXT_SIZE = 40.0f;
    private static final float LANDMARK_RADIUS = 6.0f;

    // Colors for different face analysis metrics
    private static final int BOX_COLOR = Color.parseColor("#4CAF50"); // Material Green
    private static final int TEXT_COLOR = Color.parseColor("#FFFFFF"); // White
    private static final int LANDMARK_COLOR = Color.parseColor("#FF5722"); // Material Deep Orange

    private final Paint faceBoxPaint;
    private final Paint faceTextPaint;
    private final Paint landmarkPaint;

    private final Face face;
    private final int imageWidth;
    private final int imageHeight;
    private final int rotationDegrees;
    private final int lensFacing;

    public FaceGraphic(
            GraphicOverlay overlay,
            Face face,
            int imageWidth,
            int imageHeight,
            int rotationDegrees,
            int lensFacing) {
        super(overlay);

        this.face = face;
        this.imageWidth = imageWidth;
        this.imageHeight = imageHeight;
        this.rotationDegrees = rotationDegrees;
        this.lensFacing = lensFacing;

        // Initialize paints for face annotations
        faceBoxPaint = new Paint();
        faceBoxPaint.setColor(BOX_COLOR);
        faceBoxPaint.setStyle(Paint.Style.STROKE);
        faceBoxPaint.setStrokeWidth(BOX_STROKE_WIDTH);
        faceBoxPaint.setAntiAlias(true);

        faceTextPaint = new Paint();
        faceTextPaint.setColor(TEXT_COLOR);
        faceTextPaint.setTextSize(ID_TEXT_SIZE);
        faceTextPaint.setShadowLayer(5.0f, 0, 0, Color.BLACK); // Add shadow for better visibility
        faceTextPaint.setAntiAlias(true);

        landmarkPaint = new Paint();
        landmarkPaint.setColor(LANDMARK_COLOR);
        landmarkPaint.setStyle(Paint.Style.FILL);
        landmarkPaint.setAntiAlias(true);

        // Log the face bounding box for debugging
        Log.d(TAG, "Face bounding box: " + face.getBoundingBox().toString());
        Log.d(TAG, "Image dimensions: " + imageWidth + "x" + imageHeight);
        Log.d(TAG, "Rotation: " + rotationDegrees);
    }

    @Override
    public void draw(Canvas canvas) {
        if (face == null) {
            return;
        }

        // Get face bounds
        Rect boundingBox = face.getBoundingBox();

        // Adjust for rotation if needed
        float left = boundingBox.left;
        float top = boundingBox.top;
        float right = boundingBox.right;
        float bottom = boundingBox.bottom;

        // Transform it to overlay coordinates
        RectF rectF = new RectF(
                translateX(left),
                translateY(top),
                translateX(right),
                translateY(bottom)
        );

        // Log transformed coordinates for debugging
        Log.d(TAG, "Original box: " + boundingBox.toString());
        Log.d(TAG, "Transformed box: " + rectF.toString());

        // Draw bounding box
        canvas.drawRect(rectF, faceBoxPaint);

        // Calculate face attributes probabilities
        float smileProb = face.getSmilingProbability() != null ? face.getSmilingProbability() : 0;
        float leftEyeOpenProb = face.getLeftEyeOpenProbability() != null ? face.getLeftEyeOpenProbability() : 0;
        float rightEyeOpenProb = face.getRightEyeOpenProbability() != null ? face.getRightEyeOpenProbability() : 0;

        // Format text with emojis for better visual representation
        String smileText = String.format("😊 %.0f%%", smileProb * 100);
        String eyesText = String.format("👁️ L:%.0f%% R:%.0f%%", leftEyeOpenProb * 100, rightEyeOpenProb * 100);

        // Draw face attributes
        canvas.drawText(smileText, rectF.left, rectF.top - 30, faceTextPaint);
        canvas.drawText(eyesText, rectF.left, rectF.top - 80, faceTextPaint);

        // Draw key landmarks
        drawLandmarkPoint(canvas, FaceLandmark.LEFT_EYE);
        drawLandmarkPoint(canvas, FaceLandmark.RIGHT_EYE);
        drawLandmarkPoint(canvas, FaceLandmark.NOSE_BASE);
        drawLandmarkPoint(canvas, FaceLandmark.LEFT_CHEEK);
        drawLandmarkPoint(canvas, FaceLandmark.RIGHT_CHEEK);
        drawLandmarkPoint(canvas, FaceLandmark.MOUTH_LEFT);
        drawLandmarkPoint(canvas, FaceLandmark.MOUTH_RIGHT);
        drawLandmarkPoint(canvas, FaceLandmark.MOUTH_BOTTOM);
    }

    private void drawLandmarkPoint(Canvas canvas, int landmarkType) {
        FaceLandmark landmark = face.getLandmark(landmarkType);
        if (landmark != null) {
            PointF point = landmark.getPosition();

            float x = translateX(point.x);
            float y = translateY(point.y);

            // Log landmark positions for debugging
            Log.d(TAG, "Landmark " + landmarkType + " original: " + point.x + "," + point.y);
            Log.d(TAG, "Landmark " + landmarkType + " transformed: " + x + "," + y);

            canvas.drawCircle(
                    x,
                    y,
                    scale(LANDMARK_RADIUS),
                    landmarkPaint);
        }
    }
}