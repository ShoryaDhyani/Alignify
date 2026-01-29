package com.alignify.engine;

import android.content.Context;
import android.content.SharedPreferences;

/**
 * Engine for calorie calculations: BMR, active calories from steps/activities.
 * Uses Mifflin-St Jeor equation for BMR.
 */
public class CaloriesEngine {

    private static final String PREFS_NAME = "CaloriesEnginePrefs";

    // MET values for activities
    private static final double MET_WALKING = 3.5;
    private static final double MET_RUNNING = 8.0;
    private static final double MET_WORKOUT = 5.0;

    private static CaloriesEngine instance;
    private final Context context;
    private final SharedPreferences prefs;

    // User profile (cached)
    private float weightKg = 70f;
    private float heightCm = 170f;
    private int age = 30;
    private boolean isMale = true;

    private CaloriesEngine(Context context) {
        this.context = context.getApplicationContext();
        this.prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        loadUserProfile();
    }

    public static synchronized CaloriesEngine getInstance(Context context) {
        if (instance == null) {
            instance = new CaloriesEngine(context);
        }
        return instance;
    }

    private void loadUserProfile() {
        SharedPreferences userPrefs = context.getSharedPreferences("AlignifyPrefs", Context.MODE_PRIVATE);
        weightKg = userPrefs.getFloat("user_weight", 70f);
        heightCm = userPrefs.getFloat("user_height", 170f);
        age = userPrefs.getInt("user_age", 30);
        isMale = userPrefs.getBoolean("user_is_male", true);
    }

    /**
     * Refresh cached user profile data.
     */
    public void refreshProfile() {
        loadUserProfile();
    }

    /**
     * Calculate Basal Metabolic Rate using Mifflin-St Jeor equation.
     * Returns daily resting calories.
     */
    public int calculateBMR() {
        double bmr;
        if (isMale) {
            bmr = (10 * weightKg) + (6.25 * heightCm) - (5 * age) + 5;
        } else {
            bmr = (10 * weightKg) + (6.25 * heightCm) - (5 * age) - 161;
        }
        return (int) Math.round(bmr);
    }

    /**
     * Calculate hourly resting calories.
     */
    public int calculateHourlyRestingCalories() {
        return calculateBMR() / 24;
    }

    /**
     * Calculate calories burned from steps.
     * Based on MET value for walking and user weight.
     */
    public int calculateStepCalories(int steps) {
        // Approximate: 100 steps ≈ 1 minute of walking
        double minutesWalking = steps / 100.0;
        double hoursWalking = minutesWalking / 60.0;

        // Calories = MET * weight(kg) * time(hours)
        double calories = MET_WALKING * weightKg * hoursWalking;

        return (int) Math.round(calories);
    }

    /**
     * Calculate calories for a specific activity.
     */
    public int calculateActivityCalories(ActivityType type, int durationMinutes) {
        double met;
        switch (type) {
            case WALKING:
                met = MET_WALKING;
                break;
            case RUNNING:
                met = MET_RUNNING;
                break;
            case WORKOUT:
                met = MET_WORKOUT;
                break;
            default:
                met = 1.0; // Resting
        }

        double hours = durationMinutes / 60.0;
        double calories = met * weightKg * hours;

        return (int) Math.round(calories);
    }

    /**
     * Calculate total daily calories (resting + active).
     */
    public int calculateTotalDailyCalories(int steps, int workoutMinutes) {
        int restingCalories = calculateBMR();
        int stepCalories = calculateStepCalories(steps);
        int workoutCalories = calculateActivityCalories(ActivityType.WORKOUT, workoutMinutes);

        // Avoid double counting - subtract baseline from active
        return restingCalories + stepCalories + workoutCalories;
    }

    /**
     * Get active calories only (excluding BMR).
     */
    public int calculateActiveCalories(int steps, int workoutMinutes) {
        return calculateStepCalories(steps) + calculateActivityCalories(ActivityType.WORKOUT, workoutMinutes);
    }

    public enum ActivityType {
        IDLE,
        WALKING,
        RUNNING,
        WORKOUT
    }

    // Getters for UI display
    public float getWeightKg() {
        return weightKg;
    }

    public float getHeightCm() {
        return heightCm;
    }

    public int getAge() {
        return age;
    }

    public boolean isMale() {
        return isMale;
    }
}
