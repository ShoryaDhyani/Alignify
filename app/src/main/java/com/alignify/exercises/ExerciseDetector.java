package com.alignify.exercises;

import android.content.Context;

import com.google.mediapipe.tasks.vision.poselandmarker.PoseLandmarkerResult;
import com.alignify.utils.TFLiteInterpreter;

import java.util.ArrayList;
import java.util.List;

/**
 * Base class for exercise detectors.
 * Provides common functionality for pose analysis and rep counting.
 */
public abstract class ExerciseDetector {

    protected final Context context;
    protected final String modelPath;
    protected TFLiteInterpreter tfliteInterpreter;
    protected boolean isInExercise = false;
    protected String lastPrediction = "";
    protected int _repCount = 0;

    public ExerciseDetector(Context context, String modelPath) {
        this.context = context;
        this.modelPath = modelPath;

        if (modelPath != null) {
            try {
                tfliteInterpreter = new TFLiteInterpreter(context, modelPath);
            } catch (Exception e) {
                // Model not available, will use rule-based detection
                e.printStackTrace();
            }
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

        public DetectionResult(boolean isCorrect, String feedback, int repCount, String stage, List<String> errors) {
            this.isCorrect = isCorrect;
            this.feedback = feedback;
            this.repCount = repCount;
            this.stage = stage;
            this.errors = errors;
        }

        public DetectionResult(boolean isCorrect, String feedback, int repCount, String stage) {
            this(isCorrect, feedback, repCount, stage, new ArrayList<>());
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
