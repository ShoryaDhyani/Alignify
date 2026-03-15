package com.alignify.ml;

import com.google.mediapipe.tasks.vision.poselandmarker.PoseLandmarkerResult;
import com.alignify.utils.LandmarkUtils;

/**
 * Global Posture Classifier.
 * Runs independently of specific exercises to monitor bad posture (e.g. forward head posture or severe slouching)
 * using simple robust heuristics.
 */
public class PostureClassifier {

    public enum PostureState {
        GOOD,
        SLOUCHING,
        CRITICAL
    }

    // Thresholds: The angle forms between the ear-to-shoulder vector and the vertical.
    // Near vertical is good posture. As the head creeps forward, the angle increases.
    // Angles are just approximations depending on normalized Landmark coordinates.
    // Using horizontal distance ratio between ear and shoulder relative to torso height as a proxy.
    private static final float SLOUCHING_THRESHOLD = 0.25f;
    private static final float CRITICAL_THRESHOLD = 0.40f;

    public PostureState classify(PoseLandmarkerResult result) {
        if (result == null || result.landmarks().isEmpty()) {
            return PostureState.GOOD;
        }

        // Try left side
        LandmarkUtils.Point2D leftEar = LandmarkUtils.getPoint2D(result, LandmarkUtils.Landmarks.LEFT_EAR);
        LandmarkUtils.Point2D leftShoulder = LandmarkUtils.getPoint2D(result, LandmarkUtils.Landmarks.LEFT_SHOULDER);
        LandmarkUtils.Point2D leftHip = LandmarkUtils.getPoint2D(result, LandmarkUtils.Landmarks.LEFT_HIP);

        if (leftEar != null && leftShoulder != null && leftHip != null) {
            float stateRatio = calculateFHP(leftEar, leftShoulder, leftHip);
            return determineState(stateRatio);
        }

        // Fallback to right side
        LandmarkUtils.Point2D rightEar = LandmarkUtils.getPoint2D(result, LandmarkUtils.Landmarks.RIGHT_EAR);
        LandmarkUtils.Point2D rightShoulder = LandmarkUtils.getPoint2D(result, LandmarkUtils.Landmarks.RIGHT_SHOULDER);
        LandmarkUtils.Point2D rightHip = LandmarkUtils.getPoint2D(result, LandmarkUtils.Landmarks.RIGHT_HIP);

        if (rightEar != null && rightShoulder != null && rightHip != null) {
            float stateRatio = calculateFHP(rightEar, rightShoulder, rightHip);
            return determineState(stateRatio);
        }

        return PostureState.GOOD;
    }

    /**
     * Calculates Forward Head Posture (FHP) severity by looking at horizontal displacement
     * of the ear relative to the shoulder, normalized by torso height.
     */
    private float calculateFHP(LandmarkUtils.Point2D ear, LandmarkUtils.Point2D shoulder, LandmarkUtils.Point2D hip) {
        float torsoHeight = Math.abs(shoulder.y - hip.y);
        if (torsoHeight < 0.01f) return 0f; // Avoid division by zero if completely weird pose

        // horizontal distance from shoulder to ear
        float headForwardDistance = Math.abs(ear.x - shoulder.x);
        return headForwardDistance / torsoHeight;
    }

    private PostureState determineState(float ratio) {
        if (ratio > CRITICAL_THRESHOLD) {
            return PostureState.CRITICAL;
        } else if (ratio > SLOUCHING_THRESHOLD) {
            return PostureState.SLOUCHING;
        } else {
            return PostureState.GOOD;
        }
    }
}
