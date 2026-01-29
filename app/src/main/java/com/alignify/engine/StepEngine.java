package com.alignify.engine;

import android.content.Context;
import android.content.SharedPreferences;

/**
 * Engine for step-related calculations: distance, active minutes, etc.
 * Singleton pattern for app-wide access.
 */
public class StepEngine {

    private static final String PREFS_NAME = "StepEnginePrefs";

    // Average stride length in meters (can be customized per user)
    private static final double DEFAULT_STRIDE_LENGTH_M = 0.762;

    // Steps per minute thresholds
    private static final int WALKING_MIN_SPM = 60;
    private static final int RUNNING_MIN_SPM = 120;

    private static StepEngine instance;
    private final Context context;
    private final SharedPreferences prefs;

    private double strideLengthM = DEFAULT_STRIDE_LENGTH_M;

    private StepEngine(Context context) {
        this.context = context.getApplicationContext();
        this.prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        loadUserSettings();
    }

    public static synchronized StepEngine getInstance(Context context) {
        if (instance == null) {
            instance = new StepEngine(context);
        }
        return instance;
    }

    private void loadUserSettings() {
        // Load user's height to estimate stride length
        SharedPreferences userPrefs = context.getSharedPreferences("AlignifyPrefs", Context.MODE_PRIVATE);
        float heightCm = userPrefs.getFloat("user_height", 170f);

        // Stride length estimate: height * 0.415 for walking
        strideLengthM = (heightCm / 100.0) * 0.415;
    }

    /**
     * Calculate distance in kilometers from step count.
     */
    public double calculateDistance(int steps) {
        double distanceM = steps * strideLengthM;
        return distanceM / 1000.0;
    }

    /**
     * Calculate distance in miles from step count.
     */
    public double calculateDistanceMiles(int steps) {
        return calculateDistance(steps) * 0.621371;
    }

    /**
     * Estimate active minutes from step count.
     * Assumes average walking pace of ~100 steps/minute.
     */
    public int calculateActiveMinutes(int steps) {
        // Conservative estimate: only count if walking at decent pace
        // Assume 100 steps/min average walking pace
        return steps / 100;
    }

    /**
     * Detect activity type based on steps per minute.
     */
    public ActivityType detectActivityType(int stepsPerMinute) {
        if (stepsPerMinute >= RUNNING_MIN_SPM) {
            return ActivityType.RUNNING;
        } else if (stepsPerMinute >= WALKING_MIN_SPM) {
            return ActivityType.WALKING;
        } else {
            return ActivityType.IDLE;
        }
    }

    /**
     * Get step goal from user preferences.
     */
    public int getStepGoal() {
        SharedPreferences userPrefs = context.getSharedPreferences("AlignifyPrefs", Context.MODE_PRIVATE);
        return userPrefs.getInt("step_goal", 10000);
    }

    /**
     * Check if step goal is reached.
     */
    public boolean isGoalReached(int steps) {
        return steps >= getStepGoal();
    }

    /**
     * Get progress percentage toward goal.
     */
    public float getGoalProgress(int steps) {
        int goal = getStepGoal();
        return Math.min(100f, (float) steps / goal * 100f);
    }

    /**
     * Update stride length based on user height.
     */
    public void updateStrideLength(float heightCm) {
        strideLengthM = (heightCm / 100.0) * 0.415;
        prefs.edit().putFloat("stride_length", (float) strideLengthM).apply();
    }

    public enum ActivityType {
        IDLE,
        WALKING,
        RUNNING
    }
}
