package com.alignify.engine;

import android.content.Context;
import android.content.SharedPreferences;

/**
 * Centralized calorie calculation engine.
 * Uses MET (Metabolic Equivalent of Task) values for activity-based
 * calculations.
 * Profile-driven for accurate personalized estimates.
 */
public class CaloriesEngine {

    private static final String TAG = "CaloriesEngine";
    private static final String PREFS_NAME = "AlignifyPrefs";

    // MET Values (Metabolic Equivalent of Task)
    private static final float MET_RESTING = 1.0f;
    private static final float MET_WALKING_SLOW = 2.5f; // < 3 mph
    private static final float MET_WALKING_NORMAL = 3.5f; // 3-4 mph
    private static final float MET_WALKING_BRISK = 4.5f; // > 4 mph
    private static final float MET_RUNNING_LIGHT = 7.0f; // 5-6 mph
    private static final float MET_RUNNING_MODERATE = 9.0f; // 6-7 mph
    private static final float MET_RUNNING_FAST = 11.0f; // > 7 mph

    // Exercise METs
    private static final float MET_BICEP_CURL = 3.5f;
    private static final float MET_SQUAT = 5.5f;
    private static final float MET_LUNGE = 5.0f;
    private static final float MET_PLANK = 3.0f;

    // Calories per step approximation (average)
    private static final float CALORIES_PER_STEP_BASE = 0.04f;

    private static CaloriesEngine instance;
    private final Context context;

    // User profile data (cached)
    private int userWeight = 70; // kg
    private int userHeight = 170; // cm
    private int userAge = 30;
    private String userGender = "male";
    private float userBMR = 0;

    public static synchronized CaloriesEngine getInstance(Context context) {
        if (instance == null) {
            instance = new CaloriesEngine(context.getApplicationContext());
        }
        return instance;
    }

    private CaloriesEngine(Context context) {
        this.context = context;
        loadUserProfile();
        calculateBMR();
    }

    /**
     * Reload user profile from SharedPreferences.
     * Note: ProfileSetupActivity stores weight/height as float, so we read as float
     */
    public void loadUserProfile() {
        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);

        // Weight and height are stored as float in ProfileSetupActivity
        // Use safe reading to handle both int and float storage
        try {
            userWeight = (int) prefs.getFloat("user_weight", 70f);
        } catch (ClassCastException e) {
            userWeight = prefs.getInt("user_weight", 70);
        }

        try {
            userHeight = (int) prefs.getFloat("user_height", 170f);
        } catch (ClassCastException e) {
            userHeight = prefs.getInt("user_height", 170);
        }

        userAge = prefs.getInt("user_age", 30);
        userGender = prefs.getString("user_gender", "male");
        calculateBMR();
    }

    /**
     * Calculate BMR using Mifflin-St Jeor equation.
     * Male: BMR = (10 × weight in kg) + (6.25 × height in cm) − (5 × age) + 5
     * Female: BMR = (10 × weight in kg) + (6.25 × height in cm) − (5 × age) − 161
     */
    private void calculateBMR() {
        float baseBMR = (10 * userWeight) + (6.25f * userHeight) - (5 * userAge);
        if ("female".equalsIgnoreCase(userGender)) {
            userBMR = baseBMR - 161;
        } else {
            userBMR = baseBMR + 5;
        }
    }

    /**
     * Get daily resting calories (BMR).
     */
    public float getDailyRestingCalories() {
        return userBMR;
    }

    /**
     * Get resting calories for a given duration in minutes.
     */
    public float getRestingCalories(int minutes) {
        return (userBMR / 1440f) * minutes; // 1440 = minutes in a day
    }

    /**
     * Calculate calories burned from steps.
     * Uses weight-adjusted formula.
     */
    public int getCaloriesFromSteps(int steps) {
        // Calories = steps × weight factor × base rate
        float weightFactor = userWeight / 70f; // Normalized to 70kg average
        return (int) (steps * CALORIES_PER_STEP_BASE * weightFactor);
    }

    /**
     * Calculate calories burned from walking.
     * 
     * @param durationMinutes Duration in minutes
     * @param stepsPerMinute  Cadence for intensity estimation
     */
    public int getCaloriesFromWalking(int durationMinutes, int stepsPerMinute) {
        float met;
        if (stepsPerMinute < 80) {
            met = MET_WALKING_SLOW;
        } else if (stepsPerMinute < 110) {
            met = MET_WALKING_NORMAL;
        } else {
            met = MET_WALKING_BRISK;
        }
        return calculateMETCalories(met, durationMinutes);
    }

    /**
     * Calculate calories burned from running.
     * 
     * @param durationMinutes Duration in minutes
     * @param stepsPerMinute  Cadence for intensity estimation
     */
    public int getCaloriesFromRunning(int durationMinutes, int stepsPerMinute) {
        float met;
        if (stepsPerMinute < 140) {
            met = MET_RUNNING_LIGHT;
        } else if (stepsPerMinute < 160) {
            met = MET_RUNNING_MODERATE;
        } else {
            met = MET_RUNNING_FAST;
        }
        return calculateMETCalories(met, durationMinutes);
    }

    /**
     * Calculate calories burned from AI exercise session.
     * 
     * @param exerciseType    Type of exercise
     * @param durationSeconds Duration in seconds
     */
    public int getCaloriesFromExercise(String exerciseType, int durationSeconds) {
        float met;
        switch (exerciseType.toLowerCase()) {
            case "bicep_curl":
                met = MET_BICEP_CURL;
                break;
            case "squat":
                met = MET_SQUAT;
                break;
            case "lunge":
                met = MET_LUNGE;
                break;
            case "plank":
                met = MET_PLANK;
                break;
            default:
                met = 4.0f; // General moderate exercise
                break;
        }
        return calculateMETCalories(met, durationSeconds / 60f);
    }

    /**
     * Calculate calories using MET formula.
     * Calories = MET × weight (kg) × duration (hours)
     */
    private int calculateMETCalories(float met, float durationMinutes) {
        float hours = durationMinutes / 60f;
        return (int) (met * userWeight * hours);
    }

    /**
     * Get activity type from steps per minute.
     */
    public String getActivityType(int stepsPerMinute) {
        if (stepsPerMinute < 30) {
            return "idle";
        } else if (stepsPerMinute < 60) {
            return "light_activity";
        } else if (stepsPerMinute < 120) {
            return "walking";
        } else {
            return "running";
        }
    }

    /**
     * Calculate Move Minutes (active minutes) from activity.
     * Walking and above counts as active.
     */
    public boolean isActiveMinute(int stepsPerMinute) {
        return stepsPerMinute >= 60;
    }

    /**
     * Get user's weight in kg.
     */
    public int getUserWeight() {
        return userWeight;
    }

    /**
     * Get user's BMR.
     */
    public float getUserBMR() {
        return userBMR;
    }
}
