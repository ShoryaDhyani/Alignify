package com.alignify.exercises;

import android.content.Context;

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
    private static final float HIP_ANGLE_IDEAL = 170f; // Nearly straight
    private static final float HIP_ANGLE_HIGH_THRESHOLD = 155f;
    private static final float HIP_ANGLE_LOW_THRESHOLD = 185f;

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

            return new DetectionResult(
                    true,
                    "Get into plank position",
                    (int) (totalHoldTime / 1000),
                    "rest");
        }

        // Start or continue hold
        if (!isHolding) {
            holdStartTime = System.currentTimeMillis();
            isHolding = true;
        }

        long currentHoldTime = (System.currentTimeMillis() - holdStartTime + totalHoldTime) / 1000;

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
        if (tfliteInterpreter != null) {
            float[] features = LandmarkUtils.extractPlankFeatures(result);
            if (features != null) {
                int prediction = tfliteInterpreter.predictClass(features);
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
        }

        String feedback;
        if (!errors.isEmpty()) {
            feedback = String.join("\n", errors);
        } else {
            feedback = "Good form! Hold: " + currentHoldTime + "s";
        }

        return new DetectionResult(
                isCorrect,
                feedback,
                (int) currentHoldTime,
                "holding",
                errors);
    }

    private boolean checkPlankPosition(PoseLandmarkerResult result) {
        // Check if key landmarks are visible
        LandmarkUtils.Point2D leftShoulder = LandmarkUtils.getPoint2D(result, LandmarkUtils.Landmarks.LEFT_SHOULDER);
        LandmarkUtils.Point2D leftHip = LandmarkUtils.getPoint2D(result, LandmarkUtils.Landmarks.LEFT_HIP);
        LandmarkUtils.Point2D leftAnkle = LandmarkUtils.getPoint2D(result, LandmarkUtils.Landmarks.LEFT_ANKLE);

        if (leftShoulder == null || leftHip == null || leftAnkle == null) {
            return false;
        }

        // Check if body is roughly horizontal (plank position)
        float shoulderHipDiff = Math.abs(leftShoulder.y - leftHip.y);
        float hipAnkleDiff = Math.abs(leftHip.y - leftAnkle.y);

        // In plank, these should be relatively small
        return shoulderHipDiff < 0.3f && hipAnkleDiff < 0.3f;
    }

    private String checkHipAlignment(PoseLandmarkerResult result) {
        // Calculate hip angle using shoulder, hip, and ankle
        Float leftHipAngle = LandmarkUtils.calculateHipAngle(result, true);
        Float rightHipAngle = LandmarkUtils.calculateHipAngle(result, false);

        Float hipAngle;
        if (leftHipAngle != null && rightHipAngle != null) {
            hipAngle = (leftHipAngle + rightHipAngle) / 2;
        } else if (leftHipAngle != null) {
            hipAngle = leftHipAngle;
        } else if (rightHipAngle != null) {
            hipAngle = rightHipAngle;
        } else {
            return null;
        }

        if (hipAngle < HIP_ANGLE_HIGH_THRESHOLD) {
            return "Lower your hips";
        } else if (hipAngle > HIP_ANGLE_LOW_THRESHOLD) {
            return "Raise your hips";
        }

        return null;
    }

    private String checkShoulderAlignment(PoseLandmarkerResult result) {
        LandmarkUtils.Point2D shoulder = LandmarkUtils.getPoint2D(result, LandmarkUtils.Landmarks.LEFT_SHOULDER);
        LandmarkUtils.Point2D wrist = LandmarkUtils.getPoint2D(result, LandmarkUtils.Landmarks.LEFT_WRIST);

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
