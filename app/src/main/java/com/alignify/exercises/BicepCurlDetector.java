package com.alignify.exercises;

import android.content.Context;

import com.google.mediapipe.tasks.vision.poselandmarker.PoseLandmarkerResult;
import com.alignify.utils.LandmarkUtils;

import java.util.ArrayList;
import java.util.List;

/**
 * Bicep Curl exercise detector.
 * Detects:
 * - Rep counting based on elbow angle
 * - Loose upper arm error
 * - Weak peak contraction error
 * - Lean too far back error (using ML model if available)
 */
public class BicepCurlDetector extends ExerciseDetector {

    // Elbow angle thresholds
    private static final float CURL_DOWN_ANGLE = 160f; // Arm extended
    private static final float CURL_UP_ANGLE = 40f; // Arm curled

    // Error thresholds
    private static final float LOOSE_ARM_ANGLE_THRESHOLD = 40f; // Shoulder-elbow angle
    private static final float WEAK_CONTRACTION_THRESHOLD = 60f; // Min curl angle

    private String previousStage = "down";
    private String currentStage = "down";
    private float minAngleReached = 180f;

    public BicepCurlDetector(Context context) {
        super(context, "bicep_model.tflite");
    }

    @Override
    public String getExerciseName() {
        return "Bicep Curl";
    }

    @Override
    public DetectionResult detect(PoseLandmarkerResult result) {
        List<String> errors = new ArrayList<>();
        boolean isCorrect = true;

        // Calculate elbow angles for both arms
        Float leftElbowAngle = LandmarkUtils.calculateElbowAngle(result, true);
        Float rightElbowAngle = LandmarkUtils.calculateElbowAngle(result, false);

        if (leftElbowAngle == null && rightElbowAngle == null) {
            return new DetectionResult(
                    true,
                    "Position yourself in frame",
                    getRepCount(),
                    currentStage);
        }

        // Use the arm with better visibility
        float elbowAngle;
        if (leftElbowAngle != null && rightElbowAngle != null) {
            elbowAngle = Math.min(leftElbowAngle, rightElbowAngle);
        } else if (leftElbowAngle != null) {
            elbowAngle = leftElbowAngle;
        } else {
            elbowAngle = rightElbowAngle;
        }

        // Track minimum angle for peak contraction check
        if (elbowAngle < minAngleReached) {
            minAngleReached = elbowAngle;
        }

        // Determine stage
        previousStage = currentStage;
        if (elbowAngle > CURL_DOWN_ANGLE) {
            currentStage = "down";
        } else if (elbowAngle < CURL_UP_ANGLE) {
            currentStage = "up";
        }
        // else keep current stage during transition

        // Count rep when going from up to down
        if (previousStage.equals("up") && currentStage.equals("down")) {
            // Check for weak peak contraction
            if (minAngleReached > WEAK_CONTRACTION_THRESHOLD) {
                errors.add("Weak contraction - curl higher");
                isCorrect = false;
            }

            _repCount++;
            minAngleReached = 180f;
        }

        // Check for loose upper arm
        String looseArmError = checkLooseUpperArm(result);
        if (looseArmError != null) {
            errors.add(looseArmError);
            isCorrect = false;
        }

        // Check for lean back using ML model if available
        if (tfliteInterpreter != null) {
            float[] features = LandmarkUtils.extractBicepFeatures(result);
            if (features != null) {
                int prediction = tfliteInterpreter.predictClass(features);
                if (prediction == 1) { // Assuming 1 = lean back error
                    errors.add("Leaning back - keep torso straight");
                    isCorrect = false;
                }
            }
        }

        String feedback;
        if (!errors.isEmpty()) {
            feedback = String.join("\n", errors);
        } else if (currentStage.equals("up")) {
            feedback = "Good curl! Now lower slowly";
        } else {
            feedback = "Curl up with control";
        }

        return new DetectionResult(
                isCorrect,
                feedback,
                getRepCount(),
                currentStage,
                errors);
    }

    private String checkLooseUpperArm(PoseLandmarkerResult result) {
        // Check if upper arm stays close to torso
        LandmarkUtils.Point2D leftShoulder = LandmarkUtils.getPoint2D(result, LandmarkUtils.Landmarks.LEFT_SHOULDER);
        LandmarkUtils.Point2D leftElbow = LandmarkUtils.getPoint2D(result, LandmarkUtils.Landmarks.LEFT_ELBOW);
        LandmarkUtils.Point2D leftHip = LandmarkUtils.getPoint2D(result, LandmarkUtils.Landmarks.LEFT_HIP);

        if (leftShoulder != null && leftElbow != null && leftHip != null) {
            float angle = LandmarkUtils.calculateAngle(leftHip, leftShoulder, leftElbow);
            if (angle > LOOSE_ARM_ANGLE_THRESHOLD) {
                return "Keep upper arm still";
            }
        }

        return null;
    }

    @Override
    public void reset() {
        super.reset();
        previousStage = "down";
        currentStage = "down";
        minAngleReached = 180f;
    }
}
