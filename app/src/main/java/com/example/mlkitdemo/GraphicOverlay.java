package com.example.mlkitdemo;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Matrix;
import android.util.AttributeSet;
import android.util.Log;
import android.view.View;
import androidx.camera.core.CameraSelector;

import java.util.ArrayList;
import java.util.List;

/**
 * A view which renders a series of custom graphics to be overlaid on top of an associated preview
 * (i.e., the camera preview). The creator can add graphics objects, update the objects, and remove
 * them, triggering the appropriate drawing and invalidation within the view.
 */
public class GraphicOverlay extends View {
    private static final String TAG = "GraphicOverlay";

    private final Object lock = new Object();
    private final List<Graphic> graphics = new ArrayList<>();

    // Camera and image properties
    private int imageWidth;
    private int imageHeight;
    private int lensFacing = CameraSelector.LENS_FACING_FRONT;

    // Transformation properties
    private float scaleFactor = 1.0f;
    private float postScaleWidthOffset = 0f;
    private float postScaleHeightOffset = 0f;
    private Matrix transformationMatrix = new Matrix();
    private boolean needUpdateTransformation = true;

    public GraphicOverlay(Context context, AttributeSet attrs) {
        super(context, attrs);
        // Listen for layout changes to update transformation
        addOnLayoutChangeListener(
                (view, left, top, right, bottom, oldLeft, oldTop, oldRight, oldBottom) -> {
                    needUpdateTransformation = true;
                });
    }

    /**
     * Removes all graphics from the overlay.
     */
    public void clear() {
        synchronized (lock) {
            graphics.clear();
        }
        postInvalidate();
    }

    /**
     * Adds a graphic to the overlay.
     */
    public void add(Graphic graphic) {
        synchronized (lock) {
            graphics.add(graphic);
        }
        postInvalidate();
    }

    /**
     * Removes a graphic from the overlay.
     */
    public void remove(Graphic graphic) {
        synchronized (lock) {
            graphics.remove(graphic);
        }
        postInvalidate();
    }

    /**
     * Sets the camera attributes for the overlay.
     */
    public void setCameraInfo(int imageWidth, int imageHeight, int lensFacing) {
        synchronized (lock) {
            this.imageWidth = imageWidth;
            this.imageHeight = imageHeight;
            this.lensFacing = lensFacing;
            needUpdateTransformation = true;
        }
        postInvalidate();
    }

    /**
     * Returns the scale factor from the image dimensions to the overlay dimensions.
     */
    public float getScaleFactor() {
        return scaleFactor;
    }

    /**
     * Returns whether the image is flipped horizontally based on camera lens facing.
     */
    public boolean isImageFlipped() {
        return lensFacing == CameraSelector.LENS_FACING_FRONT;
    }

    /**
     * Adjusts the supplied x coordinate from the image's coordinate system to the view's
     * coordinate system.
     */
    public float translateX(float x) {
        if (needUpdateTransformation) {
            updateTransformationIfNeeded();
        }

        if (isImageFlipped()) {
            x = imageWidth - x;
        }

        return x * scaleFactor + postScaleWidthOffset;
    }

    /**
     * Adjusts the supplied y coordinate from the image's coordinate system to the view's
     * coordinate system.
     */
    public float translateY(float y) {
        if (needUpdateTransformation) {
            updateTransformationIfNeeded();
        }

        return y * scaleFactor + postScaleHeightOffset;
    }
    /*
    getter functions
     */
    public int getImageWidth() {
        return imageWidth;
    }

    public int getImageHeight() {
        return imageHeight;
    }
    /**
     * Updates the transformation matrix for accurate coordinate mapping.
     */
    private void updateTransformationIfNeeded() {
        if (!needUpdateTransformation || imageWidth <= 0 || imageHeight <= 0) {
            return;
        }

        // Get view dimensions
        float viewWidth = getWidth();
        float viewHeight = getHeight();

        // Reset transformation matrix
        transformationMatrix.reset();

        // Calculate scaling factors
        float scaleX = viewWidth / (float) imageWidth;
        float scaleY = viewHeight / (float) imageHeight;

        // For front camera, we need to handle the mirrored preview
        if (isImageFlipped()) {
            transformationMatrix.postScale(-1f, 1f, viewWidth / 2f, viewHeight / 2f);
        }

        // Fix: Account for different aspect ratios between camera and view
        // Calculate the aspect ratios
        float viewAspectRatio = viewWidth / viewHeight;
        float imageAspectRatio = (float) imageWidth / imageHeight;

        if (viewAspectRatio > imageAspectRatio) {
            // View is wider than the image
            scaleFactor = viewHeight / (float) imageHeight;
            postScaleWidthOffset = (viewWidth - imageWidth * scaleFactor) / 2;
            postScaleHeightOffset = 0;
        } else {
            // View is taller than the image
            scaleFactor = viewWidth / (float) imageWidth;
            postScaleWidthOffset = 0;
            postScaleHeightOffset = (viewHeight - imageHeight * scaleFactor) / 2;
        }

        // Apply scaling and translation
        transformationMatrix.postScale(scaleFactor, scaleFactor);
        transformationMatrix.postTranslate(postScaleWidthOffset, postScaleHeightOffset);

        Log.d(TAG, String.format(
                "Updated transformation - Image: %dx%d, View: %.0fx%.0f, Scale: %.2f, Mirrored: %b",
                imageWidth, imageHeight, viewWidth, viewHeight, scaleFactor, isImageFlipped()));

        needUpdateTransformation = false;
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);

        synchronized (lock) {
            updateTransformationIfNeeded();

            // Apply transformation to canvas if needed
            canvas.save();
            // No need to apply transformation to canvas since we're
            // transforming individual coordinates in translateX/Y methods
            canvas.restore();

            // Draw all graphics
            for (Graphic graphic : graphics) {
                graphic.draw(canvas);
            }
        }
    }

    /**
     * Base class for a custom graphics object to be rendered within the graphic overlay.
     */
    public abstract static class Graphic {
        private final GraphicOverlay overlay;

        public Graphic(GraphicOverlay overlay) {
            this.overlay = overlay;
        }

        /**
         * Draw the graphic on the supplied canvas.
         */
        public abstract void draw(Canvas canvas);

        /**
         * Adjusts the supplied value from the image scale to the view scale.
         */
        public float scale(float imagePixel) {
            return imagePixel * overlay.getScaleFactor();
        }

        /**
         * Adjusts the x coordinate from the image's coordinate system to the view coordinate system.
         */
        public float translateX(float x) {
            return overlay.translateX(x);
        }

        /**
         * Adjusts the y coordinate from the image's coordinate system to the view coordinate system.
         */
        public float translateY(float y) {
            return overlay.translateY(y);
        }

        /**
         * Returns the application context for the overlay.
         */
        public Context getApplicationContext() {
            return overlay.getContext().getApplicationContext();
        }

        /**
         * Returns whether the display is currently mirrored (front-facing camera).
         */
        public boolean isImageFlipped() {
            return overlay.isImageFlipped();
        }

        /**
         * Gets the width of the overlay relative to the source image.
         */
        public int getImageWidth() {
            return overlay.imageWidth;
        }

        /**
         * Gets the height of the overlay relative to the source image.
         */
        public int getImageHeight() {
            return overlay.imageHeight;
        }
    }
}