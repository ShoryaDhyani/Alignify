package com.alignify.exercises;

import android.content.Context;

import com.google.mediapipe.tasks.vision.poselandmarker.PoseLandmarkerResult;
import com.alignify.utils.LandmarkUtils;

import java.util.ArrayList;
import java.util.List;

/**
 * Squat exercise detector.
 * Detects:
 * - Rep counting based on knee angle (up/down stages)
 * - Feet placement error
 * - Knee placement error
 */
public class SquatDetector extends ExerciseDetector {

    // Knee angle thresholds for stage detection
    private static final float SQUAT_UP_ANGLE = 160f; // Standing
    private static final float SQUAT_DOWN_ANGLE = 90f; // Squatting

    // Feet placement ratio (feet distance / shoulder distance)
    private static final float FEET_RATIO_MIN = 0.8f;
    private static final float FEET_RATIO_MAX = 1.3f;

    // Knee placement ratio (knee distance / feet distance)
    private static final float KNEE_RATIO_MIN = 0.9f; // Knees should be wider than feet

    private String previousStage = "up";
    private String currentStage = "up";

    public SquatDetector(Context context) {
        super(context, "squat_model.tflite");
    }

    @Override
    public String getExerciseName() {
        return "Squat";
    }

    @Override
    public DetectionResult detect(PoseLandmarkerResult result) {
        List<String> errors = new ArrayList<>();
        boolean isCorrect = true;

        // Calculate knee angles
        Float leftKneeAngle = LandmarkUtils.calculateKneeAngle(result, true);
        Float rightKneeAngle = LandmarkUtils.calculateKneeAngle(result, false);

        if (leftKneeAngle == null && rightKneeAngle == null) {
            return new DetectionResult(
                    true,
                    "Position yourself in frame",
                    getRepCount(),
                    currentStage);
        }

        // Average knee angle
        float kneeAngle;
        if (leftKneeAngle != null && rightKneeAngle != null) {
            kneeAngle = (leftKneeAngle + rightKneeAngle) / 2;
        } else if (leftKneeAngle != null) {
            kneeAngle = leftKneeAngle;
        } else {
            kneeAngle = rightKneeAngle;
        }

        // Determine stage
        previousStage = currentStage;
        if (kneeAngle > SQUAT_UP_ANGLE) {
            currentStage = "up";
        } else if (kneeAngle < SQUAT_DOWN_ANGLE) {
            currentStage = "down";
        }
        // else keep current stage

        // Count rep when going from down to up
        if (previousStage.equals("down") && currentStage.equals("up")) {
            _repCount++;
        }

        // Check feet placement
        String feetError = checkFeetPlacement(result);
        if (feetError != null) {
            errors.add(feetError);
            isCorrect = false;
        }

        // Check knee placement (only during down stage)
        if (currentStage.equals("down")) {
            String kneeError = checkKneePlacement(result);
            if (kneeError != null) {
                errors.add(kneeError);
                isCorrect = false;
            }
        }

        String feedback;
        if (!errors.isEmpty()) {
            feedback = String.join("\n", errors);
        } else if (currentStage.equals("down")) {
            feedback = "Good depth! Push through heels";
        } else {
            feedback = "Squat down with control";
        }

        return new DetectionResult(
                isCorrect,
                feedback,
                getRepCount(),
                currentStage,
                errors);
    }

    private String checkFeetPlacement(PoseLandmarkerResult result) {
        LandmarkUtils.Point2D leftShoulder = LandmarkUtils.getPoint2D(result, LandmarkUtils.Landmarks.LEFT_SHOULDER);
        LandmarkUtils.Point2D rightShoulder = LandmarkUtils.getPoint2D(result, LandmarkUtils.Landmarks.RIGHT_SHOULDER);
        LandmarkUtils.Point2D leftAnkle = LandmarkUtils.getPoint2D(result, LandmarkUtils.Landmarks.LEFT_ANKLE);
        LandmarkUtils.Point2D rightAnkle = LandmarkUtils.getPoint2D(result, LandmarkUtils.Landmarks.RIGHT_ANKLE);

        if (leftShoulder != null && rightShoulder != null &&
                leftAnkle != null && rightAnkle != null) {

            float shoulderDistance = LandmarkUtils.calculateDistance(leftShoulder, rightShoulder);
            float feetDistance = LandmarkUtils.calculateDistance(leftAnkle, rightAnkle);

            float ratio = feetDistance / shoulderDistance;

            if (ratio < FEET_RATIO_MIN) {
                return "Widen your stance";
            } else if (ratio > FEET_RATIO_MAX) {
                return "Narrow your stance";
            }
        }

        return null;
    }

    private String checkKneePlacement(PoseLandmarkerResult result) {
        LandmarkUtils.Point2D leftKnee = LandmarkUtils.getPoint2D(result, LandmarkUtils.Landmarks.LEFT_KNEE);
        LandmarkUtils.Point2D rightKnee = LandmarkUtils.getPoint2D(result, LandmarkUtils.Landmarks.RIGHT_KNEE);
        LandmarkUtils.Point2D leftAnkle = LandmarkUtils.getPoint2D(result, LandmarkUtils.Landmarks.LEFT_ANKLE);
        LandmarkUtils.Point2D rightAnkle = LandmarkUtils.getPoint2D(result, LandmarkUtils.Landmarks.RIGHT_ANKLE);

        if (leftKnee != null && rightKnee != null &&
                leftAnkle != null && rightAnkle != null) {

            float kneeDistance = LandmarkUtils.calculateDistance(leftKnee, rightKnee);
            float feetDistance = LandmarkUtils.calculateDistance(leftAnkle, rightAnkle);

            float ratio = kneeDistance / feetDistance;

            if (ratio < KNEE_RATIO_MIN) {
                return "Push knees outward";
            }
        }

        return null;
    }

    @Override
    public void reset() {
        super.reset();
        previousStage = "up";
        currentStage = "up";
    }
}
