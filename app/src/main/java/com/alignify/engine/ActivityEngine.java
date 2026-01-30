package com.alignify.engine;

import android.content.Context;
import android.util.Log;

import com.alignify.data.UserRepository;

import java.util.HashMap;
import java.util.Map;

/**
 * Activity detection and tracking engine.
 * Auto-detects walking/running from step cadence.
 * Groups steps into activity sessions.
 */
public class ActivityEngine {

    private static final String TAG = "ActivityEngine";

    // Activity detection thresholds
    public static final int CADENCE_IDLE = 30;
    public static final int CADENCE_WALKING_MIN = 60;
    public static final int CADENCE_WALKING_MAX = 120;
    public static final int CADENCE_RUNNING_MIN = 120;

    // Session grouping
    private static final long SESSION_TIMEOUT_MS = 2 * 60 * 1000; // 2 minutes inactivity ends session
    private static final int MIN_SESSION_DURATION_SEC = 60; // Minimum 1 minute for valid session

    private static ActivityEngine instance;
    private final Context context;
    private final CaloriesEngine caloriesEngine;

    // Current session tracking
    private boolean isSessionActive = false;
    private long sessionStartTime = 0;
    private long lastStepTime = 0;
    private int sessionSteps = 0;
    private int currentCadence = 0;
    private String currentActivityType = "idle";

    // Cadence calculation
    private int[] recentCadences = new int[10];
    private int cadenceIndex = 0;

    public static synchronized ActivityEngine getInstance(Context context) {
        if (instance == null) {
            instance = new ActivityEngine(context.getApplicationContext());
        }
        return instance;
    }

    private ActivityEngine(Context context) {
        this.context = context;
        this.caloriesEngine = CaloriesEngine.getInstance(context);
    }

    /**
     * Activity types enum.
     */
    public enum ActivityType {
        IDLE("idle", 0),
        LIGHT_ACTIVITY("light_activity", 1),
        WALKING("walking", 2),
        RUNNING("running", 3);

        public final String name;
        public final int level;

        ActivityType(String name, int level) {
            this.name = name;
            this.level = level;
        }

        public static ActivityType fromCadence(int stepsPerMinute) {
            if (stepsPerMinute < CADENCE_IDLE)
                return IDLE;
            if (stepsPerMinute < CADENCE_WALKING_MIN)
                return LIGHT_ACTIVITY;
            if (stepsPerMinute < CADENCE_RUNNING_MIN)
                return WALKING;
            return RUNNING;
        }
    }

    /**
     * Called on each step update to track activity sessions.
     * 
     * @param totalSteps      Current step count
     * @param stepsThisMinute Steps in the last minute (for cadence)
     */
    public void onStepUpdate(int totalSteps, int stepsThisMinute) {
        long now = System.currentTimeMillis();
        currentCadence = stepsThisMinute;
        updateCadenceHistory(stepsThisMinute);

        ActivityType activity = ActivityType.fromCadence(stepsThisMinute);
        currentActivityType = activity.name;

        // Session management
        if (activity.level >= ActivityType.WALKING.level) {
            if (!isSessionActive) {
                startSession(now);
            }
            lastStepTime = now;
            sessionSteps++;
        } else if (isSessionActive && (now - lastStepTime) > SESSION_TIMEOUT_MS) {
            endSession();
        }
    }

    private void updateCadenceHistory(int cadence) {
        recentCadences[cadenceIndex] = cadence;
        cadenceIndex = (cadenceIndex + 1) % recentCadences.length;
    }

    /**
     * Get smoothed average cadence.
     */
    public int getAverageCadence() {
        int sum = 0;
        int count = 0;
        for (int c : recentCadences) {
            if (c > 0) {
                sum += c;
                count++;
            }
        }
        return count > 0 ? sum / count : 0;
    }

    private void startSession(long timestamp) {
        isSessionActive = true;
        sessionStartTime = timestamp;
        sessionSteps = 0;
        Log.d(TAG, "Activity session started");
    }

    private void endSession() {
        if (!isSessionActive)
            return;

        long duration = (lastStepTime - sessionStartTime) / 1000;
        if (duration >= MIN_SESSION_DURATION_SEC && sessionSteps > 0) {
            saveSession(duration);
        }

        isSessionActive = false;
        sessionSteps = 0;
        Log.d(TAG, "Activity session ended");
    }

    private void saveSession(long durationSeconds) {
        int avgCadence = getAverageCadence();
        ActivityType type = ActivityType.fromCadence(avgCadence);
        int calories;

        if (type == ActivityType.RUNNING) {
            calories = caloriesEngine.getCaloriesFromRunning((int) (durationSeconds / 60), avgCadence);
        } else {
            calories = caloriesEngine.getCaloriesFromWalking((int) (durationSeconds / 60), avgCadence);
        }

        // Save to Firestore
        UserRepository.getInstance().saveActivity(
                type.name,
                "auto",
                sessionStartTime,
                lastStepTime,
                (int) durationSeconds,
                estimateDistance(sessionSteps),
                calories,
                null);

        Log.d(TAG, "Saved activity: " + type.name + ", duration=" + durationSeconds + "s, calories=" + calories);
    }

    /**
     * Estimate distance from steps (in km).
     * Uses average stride length based on height.
     */
    private float estimateDistance(int steps) {
        // Average stride = 0.415 * height in cm (in meters)
        // For 170cm person: stride = 0.7m
        float strideMeters = 0.7f;
        return (steps * strideMeters) / 1000f; // Convert to km
    }

    /**
     * Manually log an activity.
     */
    public void logManualActivity(String type, long startTime, long endTime, float distanceKm) {
        int durationSeconds = (int) ((endTime - startTime) / 1000);
        int calories = caloriesEngine.getCaloriesFromWalking(durationSeconds / 60, 90); // Assume moderate pace

        UserRepository.getInstance().saveActivity(
                type,
                "manual",
                startTime,
                endTime,
                durationSeconds,
                distanceKm,
                calories,
                null);
    }

    /**
     * Force end current session (e.g., when app closes).
     */
    public void forceEndSession() {
        if (isSessionActive) {
            lastStepTime = System.currentTimeMillis();
            endSession();
        }
    }

    // Getters
    public boolean isSessionActive() {
        return isSessionActive;
    }

    public String getCurrentActivityType() {
        return currentActivityType;
    }

    public int getCurrentCadence() {
        return currentCadence;
    }

    public int getSessionSteps() {
        return sessionSteps;
    }

    public long getSessionDuration() {
        if (!isSessionActive)
            return 0;
        return (System.currentTimeMillis() - sessionStartTime) / 1000;
    }
}
