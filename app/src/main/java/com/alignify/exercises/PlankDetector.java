package com.alignify.exercises;

import android.content.Context;
import android.util.Log;

import com.google.mediapipe.tasks.vision.poselandmarker.PoseLandmarkerResult;
import com.alignify.utils.LandmarkUtils;

import java.util.ArrayList;
import java.util.List;

/**
 * Plank exercise detector.
 * Detects:
 * - Hold time tracking
 * - Hip too high error
 * - Hip too low (sagging) error
 * - Shoulder alignment error
 */
public class PlankDetector extends ExerciseDetector {

    // Hip angle thresholds (shoulder-hip-ankle alignment)
    // calculateAngle() returns 0-180, so thresholds must be in that range
    private static final float HIP_ANGLE_IDEAL = 170f; // Nearly straight
    private static final float HIP_ANGLE_LOW_THRESHOLD = 160f; // Hips sagging (angle at hip decreases)

    // Shoulder-wrist alignment threshold
    private static final float SHOULDER_WRIST_THRESHOLD = 0.1f;

    private long holdStartTime = 0;
    private long totalHoldTime = 0;
    private boolean isHolding = false;

    public PlankDetector(Context context) {
        super(context, "plank_model.tflite");
    }

    @Override
    public String getExerciseName() {
        return "Plank";
    }

    @Override
    public DetectionResult detect(PoseLandmarkerResult result) {
        List<String> errors = new ArrayList<>();
        boolean isCorrect = true;

        // Check if person is in plank position
        boolean isInPlankPosition = checkPlankPosition(result);

        if (!isInPlankPosition) {
            if (isHolding) {
                // End hold
                totalHoldTime += System.currentTimeMillis() - holdStartTime;
                isHolding = false;
            }

            _repCount = (int) (totalHoldTime / 1000);

            return new DetectionResult(
                    true,
                    1.0f,
                    "Get into plank position",
                    getRepCount(),
                    "rest");
        }

        // Start or continue hold
        if (!isHolding) {
            holdStartTime = System.currentTimeMillis();
            isHolding = true;
        }

        long currentHoldTime = (System.currentTimeMillis() - holdStartTime + totalHoldTime) / 1000;
        _repCount = (int) currentHoldTime;

        // Check hip alignment
        String hipError = checkHipAlignment(result);
        if (hipError != null) {
            errors.add(hipError);
            isCorrect = false;
        }

        // Check shoulder alignment
        String shoulderError = checkShoulderAlignment(result);
        if (shoulderError != null) {
            errors.add(shoulderError);
            isCorrect = false;
        }

        // Use ML model if available
        float confidence = 1.0f;
        if (tfliteInterpreter != null) {
            try {
                float[] features = LandmarkUtils.extractPlankFeatures(result);
                if (features != null) {
                    int prediction = tfliteInterpreter.predictClass(features);
                    confidence = tfliteInterpreter.predictConfidence(features);
                    switch (prediction) {
                        case 1:
                            if (!errors.contains("Lower your hips")) {
                                errors.add("Adjust form");
                            }
                            isCorrect = false;
                            break;
                        case 2:
                            if (!errors.contains("Raise your hips")) {
                                errors.add("Adjust form");
                            }
                            isCorrect = false;
                            break;
                    }
                }
            } catch (Exception e) {
                Log.w("PlankDetector", "ML inference failed", e);
            }
        }

        String feedback;
        if (!errors.isEmpty()) {
            feedback = String.join("\n", errors);
        } else {
            feedback = "Good form! Hold: " + currentHoldTime + "s";
        }

        return new DetectionResult(
                isCorrect,
                confidence,
                feedback,
                getRepCount(),
                "holding",
                errors);
    }

    private boolean checkPlankPosition(PoseLandmarkerResult result) {
        // Check left side first, fall back to right side
        LandmarkUtils.Point2D shoulder = LandmarkUtils.getPoint2D(result, LandmarkUtils.Landmarks.LEFT_SHOULDER);
        LandmarkUtils.Point2D hip = LandmarkUtils.getPoint2D(result, LandmarkUtils.Landmarks.LEFT_HIP);
        LandmarkUtils.Point2D ankle = LandmarkUtils.getPoint2D(result, LandmarkUtils.Landmarks.LEFT_ANKLE);

        if (shoulder == null || hip == null || ankle == null) {
            shoulder = LandmarkUtils.getPoint2D(result, LandmarkUtils.Landmarks.RIGHT_SHOULDER);
            hip = LandmarkUtils.getPoint2D(result, LandmarkUtils.Landmarks.RIGHT_HIP);
            ankle = LandmarkUtils.getPoint2D(result, LandmarkUtils.Landmarks.RIGHT_ANKLE);
        }

        if (shoulder == null || hip == null || ankle == null) {
            return false;
        }

        // Check if body is roughly horizontal (plank position)
        float shoulderHipDiff = Math.abs(shoulder.y - hip.y);
        float hipAnkleDiff = Math.abs(hip.y - ankle.y);

        // In plank, these should be relatively small
        return shoulderHipDiff < 0.3f && hipAnkleDiff < 0.3f;
    }

    private String checkHipAlignment(PoseLandmarkerResult result) {
        // Use shoulder-hip-ankle positions to detect alignment
        // The angle alone can't distinguish hips-too-high from hips-sagging
        // since both cause the angle to decrease from 180
        LandmarkUtils.Point2D shoulder = LandmarkUtils.getPoint2D(result, LandmarkUtils.Landmarks.LEFT_SHOULDER);
        LandmarkUtils.Point2D hip = LandmarkUtils.getPoint2D(result, LandmarkUtils.Landmarks.LEFT_HIP);
        LandmarkUtils.Point2D ankle = LandmarkUtils.getPoint2D(result, LandmarkUtils.Landmarks.LEFT_ANKLE);

        if (shoulder == null || hip == null || ankle == null) {
            shoulder = LandmarkUtils.getPoint2D(result, LandmarkUtils.Landmarks.RIGHT_SHOULDER);
            hip = LandmarkUtils.getPoint2D(result, LandmarkUtils.Landmarks.RIGHT_HIP);
            ankle = LandmarkUtils.getPoint2D(result, LandmarkUtils.Landmarks.RIGHT_ANKLE);
        }

        if (shoulder == null || hip == null || ankle == null) {
            return null;
        }

        // Check hip angle for overall form quality
        float hipAngle = LandmarkUtils.calculateAngle(shoulder, hip, ankle);
        if (hipAngle > HIP_ANGLE_LOW_THRESHOLD) {
            // Form is acceptable
            return null;
        }

        // Form is off - determine direction using vertical position
        // In normalized coords, y increases downward
        // Interpolate expected hip y on the shoulder-ankle line
        float dx = ankle.x - shoulder.x;
        float t = (Math.abs(dx) > 0.001f) ? (hip.x - shoulder.x) / dx : 0.5f;
        float expectedY = shoulder.y + t * (ankle.y - shoulder.y);
        float deviation = hip.y - expectedY;

        // deviation > 0 means hip is below the line (sagging)
        // deviation < 0 means hip is above the line (piked up)
        if (deviation < -0.03f) {
            return "Lower your hips";
        } else if (deviation > 0.03f) {
            return "Raise your hips";
        }

        return null;
    }

    private String checkShoulderAlignment(PoseLandmarkerResult result) {
        LandmarkUtils.Point2D shoulder = LandmarkUtils.getPoint2D(result, LandmarkUtils.Landmarks.LEFT_SHOULDER);
        LandmarkUtils.Point2D wrist = LandmarkUtils.getPoint2D(result, LandmarkUtils.Landmarks.LEFT_WRIST);

        if (shoulder == null || wrist == null) {
            shoulder = LandmarkUtils.getPoint2D(result, LandmarkUtils.Landmarks.RIGHT_SHOULDER);
            wrist = LandmarkUtils.getPoint2D(result, LandmarkUtils.Landmarks.RIGHT_WRIST);
        }

        if (shoulder != null && wrist != null) {
            // Check if shoulders are stacked over wrists
            float xDiff = Math.abs(shoulder.x - wrist.x);
            if (xDiff > SHOULDER_WRIST_THRESHOLD) {
                return "Stack shoulders over wrists";
            }
        }

        return null;
    }

    @Override
    public void reset() {
        super.reset();
        holdStartTime = 0;
        totalHoldTime = 0;
        isHolding = false;
    }
}
