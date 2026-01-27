package com.alignify.utils;

import com.google.mediapipe.tasks.vision.poselandmarker.PoseLandmarkerResult;
import com.google.mediapipe.tasks.components.containers.NormalizedLandmark;

import java.util.List;

/**
 * Utility functions for working with MediaPipe pose landmarks.
 * Includes angle calculations, distance measurements, and feature extraction.
 */
public class LandmarkUtils {

    // MediaPipe Pose Landmark indices
    public static class Landmarks {
        public static final int NOSE = 0;
        public static final int LEFT_EYE_INNER = 1;
        public static final int LEFT_EYE = 2;
        public static final int LEFT_EYE_OUTER = 3;
        public static final int RIGHT_EYE_INNER = 4;
        public static final int RIGHT_EYE = 5;
        public static final int RIGHT_EYE_OUTER = 6;
        public static final int LEFT_EAR = 7;
        public static final int RIGHT_EAR = 8;
        public static final int MOUTH_LEFT = 9;
        public static final int MOUTH_RIGHT = 10;
        public static final int LEFT_SHOULDER = 11;
        public static final int RIGHT_SHOULDER = 12;
        public static final int LEFT_ELBOW = 13;
        public static final int RIGHT_ELBOW = 14;
        public static final int LEFT_WRIST = 15;
        public static final int RIGHT_WRIST = 16;
        public static final int LEFT_PINKY = 17;
        public static final int RIGHT_PINKY = 18;
        public static final int LEFT_INDEX = 19;
        public static final int RIGHT_INDEX = 20;
        public static final int LEFT_THUMB = 21;
        public static final int RIGHT_THUMB = 22;
        public static final int LEFT_HIP = 23;
        public static final int RIGHT_HIP = 24;
        public static final int LEFT_KNEE = 25;
        public static final int RIGHT_KNEE = 26;
        public static final int LEFT_ANKLE = 27;
        public static final int RIGHT_ANKLE = 28;
        public static final int LEFT_HEEL = 29;
        public static final int RIGHT_HEEL = 30;
        public static final int LEFT_FOOT_INDEX = 31;
        public static final int RIGHT_FOOT_INDEX = 32;
    }

    public static class Point2D {
        public final float x;
        public final float y;

        public Point2D(float x, float y) {
            this.x = x;
            this.y = y;
        }
    }

    public static class Point3D {
        public final float x;
        public final float y;
        public final float z;

        public Point3D(float x, float y, float z) {
            this.x = x;
            this.y = y;
            this.z = z;
        }
    }

    /**
     * Calculate the angle between three points (in degrees).
     * The angle is measured at point2 (the vertex).
     */
    public static float calculateAngle(Point2D point1, Point2D point2, Point2D point3) {
        double radians = Math.atan2(point3.y - point2.y, point3.x - point2.x) -
                Math.atan2(point1.y - point2.y, point1.x - point2.x);
        float angle = (float) Math.toDegrees(radians);

        // Normalize to 0-360 range
        if (angle < 0)
            angle += 360f;

        // Return the smaller angle
        return angle > 180f ? 360f - angle : angle;
    }

    /**
     * Calculate Euclidean distance between two points.
     */
    public static float calculateDistance(Point2D point1, Point2D point2) {
        float dx = point1.x - point2.x;
        float dy = point1.y - point2.y;
        return (float) Math.sqrt(dx * dx + dy * dy);
    }

    /**
     * Extract 2D point from PoseLandmarkerResult.
     */
    public static Point2D getPoint2D(PoseLandmarkerResult result, int landmarkIndex) {
        if (result.landmarks().isEmpty())
            return null;
        List<NormalizedLandmark> landmarks = result.landmarks().get(0);
        if (landmarkIndex >= landmarks.size())
            return null;

        NormalizedLandmark landmark = landmarks.get(landmarkIndex);
        return new Point2D(landmark.x(), landmark.y());
    }

    /**
     * Extract 3D point from PoseLandmarkerResult (world coordinates).
     */
    public static Point3D getPoint3D(PoseLandmarkerResult result, int landmarkIndex) {
        if (result.worldLandmarks().isEmpty())
            return null;
        List<com.google.mediapipe.tasks.components.containers.Landmark> landmarks = result.worldLandmarks().get(0);
        if (landmarkIndex >= landmarks.size())
            return null;

        com.google.mediapipe.tasks.components.containers.Landmark landmark = landmarks.get(landmarkIndex);
        return new Point3D(landmark.x(), landmark.y(), landmark.z());
    }

    /**
     * Extract features for bicep curl detection.
     * Returns coordinates for: nose, shoulders, elbows, wrists, hips
     */
    public static float[] extractBicepFeatures(PoseLandmarkerResult result) {
        int[] indices = {
                Landmarks.NOSE,
                Landmarks.LEFT_SHOULDER, Landmarks.RIGHT_SHOULDER,
                Landmarks.LEFT_ELBOW, Landmarks.RIGHT_ELBOW,
                Landmarks.LEFT_WRIST, Landmarks.RIGHT_WRIST,
                Landmarks.LEFT_HIP, Landmarks.RIGHT_HIP
        };

        return extractFeatures(result, indices);
    }

    /**
     * Extract features for squat detection.
     * Returns coordinates for: shoulders, hips, knees, ankles
     */
    public static float[] extractSquatFeatures(PoseLandmarkerResult result) {
        int[] indices = {
                Landmarks.LEFT_SHOULDER, Landmarks.RIGHT_SHOULDER,
                Landmarks.LEFT_HIP, Landmarks.RIGHT_HIP,
                Landmarks.LEFT_KNEE, Landmarks.RIGHT_KNEE,
                Landmarks.LEFT_ANKLE, Landmarks.RIGHT_ANKLE
        };

        return extractFeatures(result, indices);
    }

    /**
     * Extract features for lunge detection.
     * Returns coordinates for: shoulders, hips, knees, ankles
     */
    public static float[] extractLungeFeatures(PoseLandmarkerResult result) {
        int[] indices = {
                Landmarks.LEFT_SHOULDER, Landmarks.RIGHT_SHOULDER,
                Landmarks.LEFT_HIP, Landmarks.RIGHT_HIP,
                Landmarks.LEFT_KNEE, Landmarks.RIGHT_KNEE,
                Landmarks.LEFT_ANKLE, Landmarks.RIGHT_ANKLE
        };

        return extractFeatures(result, indices);
    }

    /**
     * Extract features for plank detection.
     * Returns coordinates for: shoulders, elbows, wrists, hips, knees, ankles
     */
    public static float[] extractPlankFeatures(PoseLandmarkerResult result) {
        int[] indices = {
                Landmarks.LEFT_SHOULDER, Landmarks.RIGHT_SHOULDER,
                Landmarks.LEFT_ELBOW, Landmarks.RIGHT_ELBOW,
                Landmarks.LEFT_WRIST, Landmarks.RIGHT_WRIST,
                Landmarks.LEFT_HIP, Landmarks.RIGHT_HIP,
                Landmarks.LEFT_KNEE, Landmarks.RIGHT_KNEE,
                Landmarks.LEFT_ANKLE, Landmarks.RIGHT_ANKLE
        };

        return extractFeatures(result, indices);
    }

    /**
     * Generic feature extraction for given landmark indices.
     */
    private static float[] extractFeatures(PoseLandmarkerResult result, int[] indices) {
        if (result.landmarks().isEmpty())
            return null;

        float[] features = new float[indices.length * 2]; // x, y for each landmark

        for (int i = 0; i < indices.length; i++) {
            Point2D point = getPoint2D(result, indices[i]);
            if (point == null)
                return null;
            features[i * 2] = point.x;
            features[i * 2 + 1] = point.y;
        }

        return features;
    }

    /**
     * Calculate elbow angle for bicep curl.
     */
    public static Float calculateElbowAngle(PoseLandmarkerResult result, boolean isLeft) {
        int shoulderIdx = isLeft ? Landmarks.LEFT_SHOULDER : Landmarks.RIGHT_SHOULDER;
        int elbowIdx = isLeft ? Landmarks.LEFT_ELBOW : Landmarks.RIGHT_ELBOW;
        int wristIdx = isLeft ? Landmarks.LEFT_WRIST : Landmarks.RIGHT_WRIST;

        Point2D shoulder = getPoint2D(result, shoulderIdx);
        Point2D elbow = getPoint2D(result, elbowIdx);
        Point2D wrist = getPoint2D(result, wristIdx);

        if (shoulder == null || elbow == null || wrist == null)
            return null;

        return calculateAngle(shoulder, elbow, wrist);
    }

    /**
     * Calculate knee angle for squat/lunge.
     */
    public static Float calculateKneeAngle(PoseLandmarkerResult result, boolean isLeft) {
        int hipIdx = isLeft ? Landmarks.LEFT_HIP : Landmarks.RIGHT_HIP;
        int kneeIdx = isLeft ? Landmarks.LEFT_KNEE : Landmarks.RIGHT_KNEE;
        int ankleIdx = isLeft ? Landmarks.LEFT_ANKLE : Landmarks.RIGHT_ANKLE;

        Point2D hip = getPoint2D(result, hipIdx);
        Point2D knee = getPoint2D(result, kneeIdx);
        Point2D ankle = getPoint2D(result, ankleIdx);

        if (hip == null || knee == null || ankle == null)
            return null;

        return calculateAngle(hip, knee, ankle);
    }

    /**
     * Calculate hip angle for plank.
     */
    public static Float calculateHipAngle(PoseLandmarkerResult result, boolean isLeft) {
        int shoulderIdx = isLeft ? Landmarks.LEFT_SHOULDER : Landmarks.RIGHT_SHOULDER;
        int hipIdx = isLeft ? Landmarks.LEFT_HIP : Landmarks.RIGHT_HIP;
        int kneeIdx = isLeft ? Landmarks.LEFT_KNEE : Landmarks.RIGHT_KNEE;

        Point2D shoulder = getPoint2D(result, shoulderIdx);
        Point2D hip = getPoint2D(result, hipIdx);
        Point2D knee = getPoint2D(result, kneeIdx);

        if (shoulder == null || hip == null || knee == null)
            return null;

        return calculateAngle(shoulder, hip, knee);
    }
}
