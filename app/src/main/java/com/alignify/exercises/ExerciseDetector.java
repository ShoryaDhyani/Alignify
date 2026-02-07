package com.alignify.exercises;

import android.content.Context;
import android.util.Log;

import com.google.mediapipe.tasks.vision.poselandmarker.PoseLandmarkerResult;
import com.alignify.ml.ModelManager;
import com.alignify.utils.TFLiteInterpreter;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

/**
 * Base class for exercise detectors.
 * Provides common functionality for pose analysis and rep counting.
 */
public abstract class ExerciseDetector {
    private static final String TAG = "ExerciseDetector";

    protected final Context context;
    protected final String modelPath;
    protected final String modelName;
    protected TFLiteInterpreter tfliteInterpreter;
    protected boolean isInExercise = false;
    protected String lastPrediction = "";
    protected int _repCount = 0;

    public ExerciseDetector(Context context, String modelPath) {
        this.context = context;
        this.modelPath = modelPath;
        this.modelName = modelPath != null ? modelPath.replace(".tflite", "") : null;

        if (modelPath != null) {
            loadModel();
        }
    }

    /**
     * Load model from cache (downloaded) or assets (bundled).
     */
    private void loadModel() {
        try {
            ModelManager modelManager = ModelManager.getInstance(context);
            File cachedModel = modelManager.getModelFileSync(modelName);

            if (cachedModel != null) {
                // Use downloaded/cached model
                tfliteInterpreter = new TFLiteInterpreter(cachedModel);
                Log.d(TAG, "Loaded cached model: " + modelName);
            } else {
                // Fallback to bundled asset
                tfliteInterpreter = new TFLiteInterpreter(context, modelPath);
                Log.d(TAG, "Loaded bundled model: " + modelPath);
            }
        } catch (Exception e) {
            // Model not available, will use rule-based detection
            Log.e(TAG, "Failed to load model: " + modelPath, e);
        }
    }

    /**
     * Result of exercise detection.
     */
    public static class DetectionResult {
        private final boolean isCorrect;
        private final String feedback;
        private final int repCount;
        private final String stage;
        private final List<String> errors;
        private final String correctionTip;

        public DetectionResult(boolean isCorrect, String feedback, int repCount, String stage, List<String> errors,
                String correctionTip) {
            this.isCorrect = isCorrect;
            this.feedback = feedback;
            this.repCount = repCount;
            this.stage = stage;
            this.errors = errors;
            this.correctionTip = correctionTip;
        }

        public DetectionResult(boolean isCorrect, String feedback, int repCount, String stage, List<String> errors) {
            this(isCorrect, feedback, repCount, stage, errors, errors.isEmpty() ? "" : errors.get(0));
        }

        public DetectionResult(boolean isCorrect, String feedback, int repCount, String stage) {
            this(isCorrect, feedback, repCount, stage, new ArrayList<>(), "");
        }

        public boolean isCorrect() {
            return isCorrect;
        }

        public String getFeedback() {
            return feedback;
        }

        public int getRepCount() {
            return repCount;
        }

        public String getStage() {
            return stage;
        }

        public List<String> getErrors() {
            return errors;
        }

        public String getCorrectionTip() {
            return correctionTip;
        }
    }

    /**
     * Process pose landmarks and detect exercise form.
     */
    public abstract DetectionResult detect(PoseLandmarkerResult result);

    /**
     * Get exercise name.
     */
    public abstract String getExerciseName();

    /**
     * Reset detector state.
     */
    public void reset() {
        _repCount = 0;
        isInExercise = false;
        lastPrediction = "";
    }

    /**
     * Current rep count (read-only from outside).
     */
    public int getRepCount() {
        return _repCount;
    }

    /**
     * Release resources.
     */
    public void close() {
        if (tfliteInterpreter != null) {
            tfliteInterpreter.close();
        }
    }
}
