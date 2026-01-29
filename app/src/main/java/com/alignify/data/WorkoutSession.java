package com.alignify.data;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/**
 * Data model for a workout session.
 */
public class WorkoutSession {

    private String id;
    private String exercise;
    private int reps;
    private int duration; // seconds
    private int errorsCount;
    private int accuracyScore; // 0-100
    private long timestamp;

    public WorkoutSession() {
        // Required for Firestore
    }

    public WorkoutSession(String exercise, int reps, int duration, int errorsCount, long timestamp) {
        this.exercise = exercise;
        this.reps = reps;
        this.duration = duration;
        this.errorsCount = errorsCount;
        this.timestamp = timestamp;
        this.accuracyScore = calculateAccuracy(reps, errorsCount);
    }

    /**
     * Calculate accuracy score based on reps and errors.
     * Simple formula: accuracy = max(0, 100 - (errors/reps * 100))
     */
    private int calculateAccuracy(int reps, int errors) {
        if (reps <= 0)
            return 100;
        float errorRate = (float) errors / reps;
        return Math.max(0, Math.min(100, (int) ((1 - errorRate) * 100)));
    }

    // Getters and Setters

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getExercise() {
        return exercise;
    }

    public void setExercise(String exercise) {
        this.exercise = exercise;
    }

    public int getReps() {
        return reps;
    }

    public void setReps(int reps) {
        this.reps = reps;
    }

    public int getDuration() {
        return duration;
    }

    public void setDuration(int duration) {
        this.duration = duration;
    }

    public int getErrorsCount() {
        return errorsCount;
    }

    public void setErrorsCount(int errorsCount) {
        this.errorsCount = errorsCount;
    }

    public int getAccuracyScore() {
        if (accuracyScore == 0 && reps > 0) {
            accuracyScore = calculateAccuracy(reps, errorsCount);
        }
        return accuracyScore;
    }

    public void setAccuracyScore(int accuracyScore) {
        this.accuracyScore = accuracyScore;
    }

    public long getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(long timestamp) {
        this.timestamp = timestamp;
    }

    // Helper methods

    /**
     * Get formatted duration string (e.g., "2m 30s")
     */
    public String getFormattedDuration() {
        int minutes = duration / 60;
        int seconds = duration % 60;
        if (minutes > 0) {
            return minutes + "m " + seconds + "s";
        }
        return seconds + "s";
    }

    /**
     * Get formatted timestamp (e.g., "Today, 3:30 PM" or "Jan 27, 3:30 PM")
     */
    public String getFormattedTimestamp() {
        Date date = new Date(timestamp);
        Date now = new Date();

        SimpleDateFormat timeFormat = new SimpleDateFormat("h:mm a", Locale.getDefault());
        SimpleDateFormat dateFormat = new SimpleDateFormat("MMM d", Locale.getDefault());

        // Check if today
        SimpleDateFormat dayFormat = new SimpleDateFormat("yyyyMMdd", Locale.getDefault());
        if (dayFormat.format(date).equals(dayFormat.format(now))) {
            return "Today, " + timeFormat.format(date);
        }

        // Check if yesterday
        long diff = now.getTime() - date.getTime();
        if (diff < 2 * 24 * 60 * 60 * 1000L && diff > 24 * 60 * 60 * 1000L) {
            return "Yesterday, " + timeFormat.format(date);
        }

        return dateFormat.format(date) + ", " + timeFormat.format(date);
    }

    /**
     * Get exercise display name (capitalize and format)
     */
    public String getExerciseDisplayName() {
        if (exercise == null || exercise.isEmpty())
            return "Unknown";

        String displayName = exercise.replace("_", " ");
        String[] words = displayName.split(" ");
        StringBuilder result = new StringBuilder();

        for (String word : words) {
            if (word.length() > 0) {
                result.append(Character.toUpperCase(word.charAt(0)));
                if (word.length() > 1) {
                    result.append(word.substring(1).toLowerCase());
                }
                result.append(" ");
            }
        }

        return result.toString().trim();
    }

    /**
     * Get exercise emoji icon
     */
    public String getExerciseIcon() {
        if (exercise == null)
            return "🏋️";

        switch (exercise.toLowerCase()) {
            case "squat":
                return "🦵";
            case "bicep_curl":
            case "bicep curl":
                return "💪";
            case "lunge":
                return "🏃";
            case "plank":
                return "🧘";
            default:
                return "🏋️";
        }
    }
}
