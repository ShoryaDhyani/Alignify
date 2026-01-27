package com.alignify;

import android.content.Context;
import android.util.Log;

import com.google.mediapipe.framework.image.MPImage;
import com.google.mediapipe.tasks.core.BaseOptions;
import com.google.mediapipe.tasks.core.Delegate;
import com.google.mediapipe.tasks.vision.core.RunningMode;
import com.google.mediapipe.tasks.vision.poselandmarker.PoseLandmarker;
import com.google.mediapipe.tasks.vision.poselandmarker.PoseLandmarkerResult;

/**
 * Helper class for MediaPipe Pose Landmarker.
 * Handles initialization, configuration, and inference for pose detection.
 */
public class PoseLandmarkerHelper {

    private static final String TAG = "PoseLandmarkerHelper";
    private static final String MP_POSE_LANDMARKER_TASK = "pose_landmarker_lite.task";

    private final Context context;
    private final RunningMode runningMode;
    private final float minPoseDetectionConfidence;
    private final float minPoseTrackingConfidence;
    private final float minPosePresenceConfidence;
    private final Delegate delegate;
    private final LandmarkerListener poseLandmarkerListener;

    private PoseLandmarker poseLandmarker;

    public PoseLandmarkerHelper(
            Context context,
            RunningMode runningMode,
            float minPoseDetectionConfidence,
            float minPoseTrackingConfidence,
            float minPosePresenceConfidence,
            Delegate delegate,
            LandmarkerListener poseLandmarkerListener) {
        this.context = context;
        this.runningMode = runningMode;
        this.minPoseDetectionConfidence = minPoseDetectionConfidence;
        this.minPoseTrackingConfidence = minPoseTrackingConfidence;
        this.minPosePresenceConfidence = minPosePresenceConfidence;
        this.delegate = delegate;
        this.poseLandmarkerListener = poseLandmarkerListener;

        setupPoseLandmarker();
    }

    private void setupPoseLandmarker() {
        // Try GPU first for better performance, fallback to CPU if GPU fails
        Delegate[] delegatesToTry;
        if (delegate == Delegate.GPU) {
            delegatesToTry = new Delegate[] { Delegate.GPU, Delegate.CPU };
        } else {
            delegatesToTry = new Delegate[] { delegate };
        }

        Exception lastError = null;

        for (Delegate currentDelegate : delegatesToTry) {
            try {
                BaseOptions.Builder baseOptionsBuilder = BaseOptions.builder()
                        .setDelegate(currentDelegate)
                        .setModelAssetPath(MP_POSE_LANDMARKER_TASK);

                PoseLandmarker.PoseLandmarkerOptions.Builder optionsBuilder = PoseLandmarker.PoseLandmarkerOptions
                        .builder()
                        .setBaseOptions(baseOptionsBuilder.build())
                        .setMinPoseDetectionConfidence(minPoseDetectionConfidence)
                        .setMinTrackingConfidence(minPoseTrackingConfidence)
                        .setMinPosePresenceConfidence(minPosePresenceConfidence)
                        .setRunningMode(runningMode);

                if (runningMode == RunningMode.LIVE_STREAM) {
                    optionsBuilder
                            .setResultListener((result, input) -> {
                                if (poseLandmarkerListener != null) {
                                    poseLandmarkerListener.onResults(result, input);
                                }
                            })
                            .setErrorListener(error -> {
                                if (poseLandmarkerListener != null) {
                                    String errorMessage = error.getMessage() != null ? error.getMessage()
                                            : "Unknown error";
                                    poseLandmarkerListener.onError(errorMessage);
                                }
                            });
                }

                poseLandmarker = PoseLandmarker.createFromOptions(context, optionsBuilder.build());

                Log.i(TAG, "PoseLandmarker initialized successfully with " + currentDelegate.name() + " delegate");
                return; // Success, exit

            } catch (Exception e) {
                lastError = e;
                Log.w(TAG, "Failed to setup PoseLandmarker with " + currentDelegate.name() + ": " + e.getMessage());
                // Continue to next delegate
            }
        }

        // All delegates failed
        Log.e(TAG, "Failed to setup PoseLandmarker with any delegate: "
                + (lastError != null ? lastError.getMessage() : ""));
        if (poseLandmarkerListener != null) {
            poseLandmarkerListener
                    .onError("Failed to setup PoseLandmarker: " + (lastError != null ? lastError.getMessage() : ""));
        }
    }

    /**
     * Detect poses in live stream (from camera).
     * Results are delivered via the listener callback.
     */
    public void detectLiveStream(MPImage imageProxy, long frameTime) {
        if (poseLandmarker != null) {
            poseLandmarker.detectAsync(imageProxy, frameTime);
        }
    }

    /**
     * Detect poses in a single image.
     * Returns the result directly.
     */
    public PoseLandmarkerResult detectImage(MPImage image) {
        if (runningMode != RunningMode.IMAGE) {
            Log.e(TAG, "detectImage requires IMAGE running mode");
            return null;
        }
        return poseLandmarker != null ? poseLandmarker.detect(image) : null;
    }

    /**
     * Detect poses in video frame.
     * Returns the result directly.
     */
    public PoseLandmarkerResult detectVideoFrame(MPImage image, long frameTimeMs) {
        if (runningMode != RunningMode.VIDEO) {
            Log.e(TAG, "detectVideoFrame requires VIDEO running mode");
            return null;
        }
        return poseLandmarker != null ? poseLandmarker.detectForVideo(image, frameTimeMs) : null;
    }

    /**
     * Check if the pose landmarker is ready.
     */
    public boolean isReady() {
        return poseLandmarker != null;
    }

    /**
     * Clear and release resources.
     */
    public void clearPoseLandmarker() {
        if (poseLandmarker != null) {
            poseLandmarker.close();
            poseLandmarker = null;
        }
    }

    /**
     * Listener interface for pose detection results.
     */
    public interface LandmarkerListener {
        void onResults(PoseLandmarkerResult result, MPImage input);

        void onError(String error);
    }
}
