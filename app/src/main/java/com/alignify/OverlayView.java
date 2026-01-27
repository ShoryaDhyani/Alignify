package com.alignify;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.util.AttributeSet;
import android.view.View;

import androidx.annotation.Nullable;

import com.google.mediapipe.tasks.components.containers.NormalizedLandmark;
import com.google.mediapipe.tasks.vision.poselandmarker.PoseLandmarkerResult;

import java.util.List;

/**
 * Custom view for drawing pose landmarks and connections on top of camera
 * preview.
 */
public class OverlayView extends View {

    private PoseLandmarkerResult results;
    private int imageWidth = 1;
    private int imageHeight = 1;
    private boolean isFrontCamera = true;

    private final Paint landmarkPaint;
    private final Paint connectionPaint;

    // Pose connections for drawing skeleton
    private final int[][] connections = {
            // Torso
            { 11, 12 }, { 11, 23 }, { 12, 24 }, { 23, 24 },
            // Left arm
            { 11, 13 }, { 13, 15 },
            // Right arm
            { 12, 14 }, { 14, 16 },
            // Left leg
            { 23, 25 }, { 25, 27 },
            // Right leg
            { 24, 26 }, { 26, 28 },
            // Face
            { 0, 1 }, { 1, 2 }, { 2, 3 }, { 3, 7 },
            { 0, 4 }, { 4, 5 }, { 5, 6 }, { 6, 8 }
    };

    public OverlayView(Context context) {
        this(context, null);
    }

    public OverlayView(Context context, @Nullable AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public OverlayView(Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);

        landmarkPaint = new Paint();
        landmarkPaint.setColor(Color.parseColor("#FF5722"));
        landmarkPaint.setStyle(Paint.Style.FILL);
        landmarkPaint.setStrokeWidth(8f);
        landmarkPaint.setAntiAlias(true);

        connectionPaint = new Paint();
        connectionPaint.setColor(Color.parseColor("#00BCD4"));
        connectionPaint.setStyle(Paint.Style.STROKE);
        connectionPaint.setStrokeWidth(4f);
        connectionPaint.setAntiAlias(true);
    }

    public void setResults(
            PoseLandmarkerResult poseLandmarkerResult,
            int imageWidth,
            int imageHeight,
            boolean isFrontCamera) {
        this.results = poseLandmarkerResult;
        this.imageWidth = imageWidth;
        this.imageHeight = imageHeight;
        this.isFrontCamera = isFrontCamera;

        invalidate();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);

        if (results != null && !results.landmarks().isEmpty()) {
            List<NormalizedLandmark> landmarks = results.landmarks().get(0);

            // Draw connections
            for (int[] connection : connections) {
                int start = connection[0];
                int end = connection[1];

                if (start < landmarks.size() && end < landmarks.size()) {
                    NormalizedLandmark startLandmark = landmarks.get(start);
                    NormalizedLandmark endLandmark = landmarks.get(end);

                    // Mirror x-coordinate for front camera
                    float startX = isFrontCamera ? (1f - startLandmark.x()) * getWidth()
                            : startLandmark.x() * getWidth();
                    float endX = isFrontCamera ? (1f - endLandmark.x()) * getWidth() : endLandmark.x() * getWidth();

                    canvas.drawLine(
                            startX,
                            startLandmark.y() * getHeight(),
                            endX,
                            endLandmark.y() * getHeight(),
                            connectionPaint);
                }
            }

            // Draw landmarks
            for (NormalizedLandmark landmark : landmarks) {
                // Mirror x-coordinate for front camera
                float x = isFrontCamera ? (1f - landmark.x()) * getWidth() : landmark.x() * getWidth();

                canvas.drawCircle(
                        x,
                        landmark.y() * getHeight(),
                        10f,
                        landmarkPaint);
            }
        }
    }

    /**
     * Set the color of landmarks based on detection result.
     */
    public void setFeedbackColor(boolean isCorrect) {
        landmarkPaint.setColor(isCorrect ? Color.GREEN : Color.RED);
        connectionPaint.setColor(isCorrect ? Color.parseColor("#4CAF50") : Color.parseColor("#F44336"));
        invalidate();
    }

    public void clear() {
        results = null;
        invalidate();
    }
}
