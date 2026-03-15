package com.alignify.exercises;

import android.content.Context;
import android.util.Log;

import com.google.mediapipe.tasks.vision.poselandmarker.PoseLandmarkerResult;
import com.alignify.utils.LandmarkUtils;

import java.util.ArrayList;
import java.util.List;

/**
 * Lunge exercise detector.
 * Detects:
 * - Rep counting based on knee angle
 * - Knee over toe error
 * - Front knee angle error
 */
public class LungeDetector extends ExerciseDetector {

    // Knee angle thresholds
    private static final float LUNGE_UP_ANGLE = 160f;
    private static final float LUNGE_DOWN_ANGLE = 100f;

    // Knee over toe threshold
    private static final float KNEE_TOE_THRESHOLD = 0.05f; // Knee x should not pass ankle x by much

    private String previousStage = "up";
    private String currentStage = "up";
    private String leadLeg = "left"; // Track which leg is in front

    public LungeDetector(Context context) {
        super(context, "lunge_model.tflite");
    }

    @Override
    public String getExerciseName() {
        return "Lunge";
    }

    @Override
    public DetectionResult detect(PoseLandmarkerResult result) {
        List<String> errors = new ArrayList<>();
        boolean isCorrect = true;

        // Determine lead leg based on hip positions
        LandmarkUtils.Point2D leftAnkle = LandmarkUtils.getPoint2D(result, LandmarkUtils.Landmarks.LEFT_ANKLE);
        LandmarkUtils.Point2D rightAnkle = LandmarkUtils.getPoint2D(result, LandmarkUtils.Landmarks.RIGHT_ANKLE);

        if (leftAnkle != null && rightAnkle != null) {
            leadLeg = (leftAnkle.y > rightAnkle.y) ? "left" : "right";
        }

        // Calculate knee angle of lead leg
        boolean isLeft = leadLeg.equals("left");
        Float kneeAngle = LandmarkUtils.calculateKneeAngle(result, isLeft);

        if (kneeAngle == null) {
            return new DetectionResult(
                    true,
                    1.0f,
                    "Position yourself sideways",
                    getRepCount(),
                    currentStage);
        }

        // Determine stage
        previousStage = currentStage;
        if (kneeAngle > LUNGE_UP_ANGLE) {
            currentStage = "up";
        } else if (kneeAngle < LUNGE_DOWN_ANGLE) {
            currentStage = "down";
        }
        // else keep current stage

        // Count rep when going from down to up
        if (previousStage.equals("down") && currentStage.equals("up")) {
            _repCount++;
        }

        // Check knee over toe
        if (currentStage.equals("down")) {
            String kneeError = checkKneeOverToe(result, isLeft);
            if (kneeError != null) {
                errors.add(kneeError);
                isCorrect = false;
            }
        }

        // Use ML model if available for additional error detection
        float confidence = 1.0f;
        if (tfliteInterpreter != null) {
            try {
                float[] features = LandmarkUtils.extractLungeFeatures(result);
                if (features != null) {
                    int prediction = tfliteInterpreter.predictClass(features);
                    confidence = tfliteInterpreter.predictConfidence(features);
                    if (prediction == 1) {
                        errors.add("Torso leaning - keep upright");
                        isCorrect = false;
                    } else if (prediction == 2) {
                        errors.add("Back knee too high - lower it");
                        isCorrect = false;
                    }
                }
            } catch (Exception e) {
                Log.w("LungeDetector", "ML inference failed", e);
            }
        }

        String feedback;
        if (!errors.isEmpty()) {
            feedback = String.join("\n", errors);
        } else if (currentStage.equals("down")) {
            feedback = "Good lunge! Push back up";
        } else {
            feedback = "Step forward and lunge down";
        }

        return new DetectionResult(
                isCorrect,
                confidence,
                feedback,
                getRepCount(),
                currentStage,
                errors);
    }

    private String checkKneeOverToe(PoseLandmarkerResult result, boolean isLeft) {
        int kneeIdx = isLeft ? LandmarkUtils.Landmarks.LEFT_KNEE : LandmarkUtils.Landmarks.RIGHT_KNEE;
        int ankleIdx = isLeft ? LandmarkUtils.Landmarks.LEFT_ANKLE : LandmarkUtils.Landmarks.RIGHT_ANKLE;
        int hipIdx = isLeft ? LandmarkUtils.Landmarks.LEFT_HIP : LandmarkUtils.Landmarks.RIGHT_HIP;

        LandmarkUtils.Point2D knee = LandmarkUtils.getPoint2D(result, kneeIdx);
        LandmarkUtils.Point2D ankle = LandmarkUtils.getPoint2D(result, ankleIdx);
        LandmarkUtils.Point2D hip = LandmarkUtils.getPoint2D(result, hipIdx);

        if (knee != null && ankle != null && hip != null) {
            // Determine forward direction from hip-to-ankle vector
            float forwardDir = ankle.x - hip.x;
            // Knee extension past ankle in the forward direction
            float kneeExtension = (knee.x - ankle.x) * Math.signum(forwardDir);
            if (kneeExtension > KNEE_TOE_THRESHOLD) {
                return "Keep knee behind toes";
            }
        }

        return null;
    }

    @Override
    public void reset() {
        super.reset();
        previousStage = "up";
        currentStage = "up";
        leadLeg = "left";
    }
}
