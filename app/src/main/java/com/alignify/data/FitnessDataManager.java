package com.alignify.data;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Log;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/**
 * Centralized singleton manager for all fitness data.
 * 
 * This class serves as the single source of truth for:
 * - Steps, calories, distance, active time
 * - Water intake tracking
 * - Daily goals (step goal, water goal, etc.)
 * - Sleep tracking
 * - Exercise reps
 * 
 * Data is cached locally in SharedPreferences for speed and synced to Firebase Firestore
 * for persistence across devices.
 * 
 * All activities should use this manager instead of directly accessing SharedPreferences
 * or Firestore to ensure data consistency.
 */
public class FitnessDataManager {

    private static final String TAG = "FitnessDataManager";

    // SharedPreferences keys
    private static final String PREFS_NAME = "fitness_data_prefs";
    private static final String KEY_LAST_SYNC_DATE = "last_sync_date";

    // Steps
    private static final String KEY_STEPS_TODAY = "steps_today";
    private static final String KEY_STEP_GOAL = "step_goal";
    private static final int DEFAULT_STEP_GOAL = 10000;

    // Calories
    private static final String KEY_CALORIES_TODAY = "calories_today";
    private static final String KEY_CALORIES_GOAL = "calories_goal";
    private static final int DEFAULT_CALORIES_GOAL = 500;

    // Distance
    private static final String KEY_DISTANCE_TODAY = "distance_today";

    // Active time
    private static final String KEY_ACTIVE_MINUTES_TODAY = "active_minutes_today";
    private static final String KEY_ACTIVE_TIME_GOAL = "active_time_goal";
    private static final int DEFAULT_ACTIVE_TIME_GOAL = 60; // minutes

    // Water
    private static final String KEY_WATER_CUPS = "water_cups";
    private static final String KEY_WATER_GOAL = "water_goal";
    private static final int DEFAULT_WATER_GOAL = 8;

    // Sleep
    private static final String KEY_SLEEP_HOURS = "sleep_hours";
    private static final String KEY_SLEEP_GOAL = "sleep_goal";
    private static final float DEFAULT_SLEEP_GOAL = 8.0f;

    // Exercise tracking
    private static final String KEY_SQUAT_REPS = "squat_reps";
    private static final String KEY_BICEP_CURL_REPS = "bicep_curl_reps";
    private static final String KEY_LUNGE_REPS = "lunge_reps";
    private static final String KEY_PLANK_SECONDS = "plank_seconds";
    private static final String KEY_WORKOUTS_COUNT = "workouts_count";
    private static final String KEY_TOTAL_WORKOUT_DURATION = "total_workout_duration";

    // Singleton instance
    private static volatile FitnessDataManager instance;

    private final Context context;
    private final SharedPreferences prefs;

    // LiveData for reactive UI updates
    private final MutableLiveData<Integer> stepsLiveData = new MutableLiveData<>(0);
    private final MutableLiveData<Integer> caloriesLiveData = new MutableLiveData<>(0);
    private final MutableLiveData<Integer> waterCupsLiveData = new MutableLiveData<>(0);
    private final MutableLiveData<Integer> activeMinutesLiveData = new MutableLiveData<>(0);
    private final MutableLiveData<Float> distanceLiveData = new MutableLiveData<>(0f);

    // Sync state
    private boolean isSyncing = false;
    private long lastSyncTime = 0;
    private static final long SYNC_INTERVAL_MS = 60000; // 1 minute

    /**
     * Get the singleton instance of FitnessDataManager.
     */
    public static FitnessDataManager getInstance(Context context) {
        if (instance == null) {
            synchronized (FitnessDataManager.class) {
                if (instance == null) {
                    instance = new FitnessDataManager(context.getApplicationContext());
                }
            }
        }
        return instance;
    }

    private FitnessDataManager(Context context) {
        this.context = context;
        this.prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        checkAndResetForNewDay();
        loadInitialData();
    }

    /**
     * Check if it's a new day and reset daily counters.
     */
    private void checkAndResetForNewDay() {
        String today = getTodayDateString();
        String lastDate = prefs.getString(KEY_LAST_SYNC_DATE, "");

        if (!today.equals(lastDate)) {
            Log.d(TAG, "New day detected, resetting daily counters");
            // Save yesterday's data to Firestore before resetting
            if (!lastDate.isEmpty()) {
                syncToFirestore(lastDate);
            }
            
            // Reset daily counters
            prefs.edit()
                    .putInt(KEY_STEPS_TODAY, 0)
                    .putInt(KEY_CALORIES_TODAY, 0)
                    .putFloat(KEY_DISTANCE_TODAY, 0f)
                    .putInt(KEY_ACTIVE_MINUTES_TODAY, 0)
                    .putInt(KEY_WATER_CUPS, 0)
                    .putFloat(KEY_SLEEP_HOURS, 0f)
                    .putInt(KEY_SQUAT_REPS, 0)
                    .putInt(KEY_BICEP_CURL_REPS, 0)
                    .putInt(KEY_LUNGE_REPS, 0)
                    .putInt(KEY_PLANK_SECONDS, 0)
                    .putInt(KEY_WORKOUTS_COUNT, 0)
                    .putInt(KEY_TOTAL_WORKOUT_DURATION, 0)
                    .putString(KEY_LAST_SYNC_DATE, today)
                    .apply();
        }
    }

    /**
     * Load initial data from SharedPreferences into LiveData.
     */
    private void loadInitialData() {
        stepsLiveData.setValue(prefs.getInt(KEY_STEPS_TODAY, 0));
        caloriesLiveData.setValue(prefs.getInt(KEY_CALORIES_TODAY, 0));
        waterCupsLiveData.setValue(prefs.getInt(KEY_WATER_CUPS, 0));
        activeMinutesLiveData.setValue(prefs.getInt(KEY_ACTIVE_MINUTES_TODAY, 0));
        distanceLiveData.setValue(prefs.getFloat(KEY_DISTANCE_TODAY, 0f));
    }

    // ============ Steps ============

    /**
     * Get current step count for today.
     */
    public int getStepsToday() {
        checkAndResetForNewDay();
        return prefs.getInt(KEY_STEPS_TODAY, 0);
    }

    /**
     * Set step count for today.
     */
    public void setStepsToday(int steps) {
        prefs.edit().putInt(KEY_STEPS_TODAY, steps).apply();
        stepsLiveData.postValue(steps);
        
        // Auto-calculate calories and distance
        int calories = calculateCaloriesFromSteps(steps);
        float distance = calculateDistanceFromSteps(steps);
        
        prefs.edit()
                .putInt(KEY_CALORIES_TODAY, calories)
                .putFloat(KEY_DISTANCE_TODAY, distance)
                .apply();
        caloriesLiveData.postValue(calories);
        distanceLiveData.postValue(distance);
        
        // Trigger sync if interval passed
        scheduleSyncIfNeeded();
    }

    /**
     * Add steps to today's count.
     */
    public void addSteps(int steps) {
        int current = getStepsToday();
        setStepsToday(current + steps);
    }

    /**
     * Get step goal.
     */
    public int getStepGoal() {
        return prefs.getInt(KEY_STEP_GOAL, DEFAULT_STEP_GOAL);
    }

    /**
     * Set step goal.
     */
    public void setStepGoal(int goal) {
        prefs.edit().putInt(KEY_STEP_GOAL, goal).apply();
        syncGoalsToFirestore();
    }

    /**
     * Get step progress as percentage (0-100).
     */
    public int getStepProgressPercent() {
        int steps = getStepsToday();
        int goal = getStepGoal();
        return goal > 0 ? Math.min(100, (steps * 100) / goal) : 0;
    }

    /**
     * Get LiveData for steps to observe in UI.
     */
    public LiveData<Integer> getStepsLiveData() {
        return stepsLiveData;
    }

    // ============ Calories ============

    /**
     * Get calories burned today.
     */
    public int getCaloriesToday() {
        checkAndResetForNewDay();
        return prefs.getInt(KEY_CALORIES_TODAY, 0);
    }

    /**
     * Set calories burned today.
     */
    public void setCaloriesToday(int calories) {
        prefs.edit().putInt(KEY_CALORIES_TODAY, calories).apply();
        caloriesLiveData.postValue(calories);
    }

    /**
     * Add calories to today's count.
     */
    public void addCalories(int calories) {
        int current = getCaloriesToday();
        setCaloriesToday(current + calories);
    }

    /**
     * Get calories goal.
     */
    public int getCaloriesGoal() {
        return prefs.getInt(KEY_CALORIES_GOAL, DEFAULT_CALORIES_GOAL);
    }

    /**
     * Set calories goal.
     */
    public void setCaloriesGoal(int goal) {
        prefs.edit().putInt(KEY_CALORIES_GOAL, goal).apply();
        syncGoalsToFirestore();
    }

    /**
     * Get LiveData for calories to observe in UI.
     */
    public LiveData<Integer> getCaloriesLiveData() {
        return caloriesLiveData;
    }

    // ============ Distance ============

    /**
     * Get distance traveled today in kilometers.
     */
    public float getDistanceToday() {
        checkAndResetForNewDay();
        return prefs.getFloat(KEY_DISTANCE_TODAY, 0f);
    }

    /**
     * Set distance traveled today.
     */
    public void setDistanceToday(float distance) {
        prefs.edit().putFloat(KEY_DISTANCE_TODAY, distance).apply();
        distanceLiveData.postValue(distance);
    }

    /**
     * Get LiveData for distance to observe in UI.
     */
    public LiveData<Float> getDistanceLiveData() {
        return distanceLiveData;
    }

    // ============ Active Time ============

    /**
     * Get active minutes today.
     */
    public int getActiveMinutesToday() {
        checkAndResetForNewDay();
        return prefs.getInt(KEY_ACTIVE_MINUTES_TODAY, 0);
    }

    /**
     * Set active minutes today.
     */
    public void setActiveMinutesToday(int minutes) {
        prefs.edit().putInt(KEY_ACTIVE_MINUTES_TODAY, minutes).apply();
        activeMinutesLiveData.postValue(minutes);
    }

    /**
     * Add active minutes to today's count.
     */
    public void addActiveMinutes(int minutes) {
        int current = getActiveMinutesToday();
        setActiveMinutesToday(current + minutes);
        scheduleSyncIfNeeded();
    }

    /**
     * Get active time goal in minutes.
     */
    public int getActiveTimeGoal() {
        return prefs.getInt(KEY_ACTIVE_TIME_GOAL, DEFAULT_ACTIVE_TIME_GOAL);
    }

    /**
     * Set active time goal in minutes.
     */
    public void setActiveTimeGoal(int goal) {
        prefs.edit().putInt(KEY_ACTIVE_TIME_GOAL, goal).apply();
        syncGoalsToFirestore();
    }

    /**
     * Get active time progress as percentage (0-100).
     */
    public int getActiveTimeProgressPercent() {
        int minutes = getActiveMinutesToday();
        int goal = getActiveTimeGoal();
        return goal > 0 ? Math.min(100, (minutes * 100) / goal) : 0;
    }

    /**
     * Get LiveData for active minutes to observe in UI.
     */
    public LiveData<Integer> getActiveMinutesLiveData() {
        return activeMinutesLiveData;
    }

    // ============ Water Tracking ============

    /**
     * Get water cups consumed today.
     */
    public int getWaterCupsToday() {
        checkAndResetForNewDay();
        return prefs.getInt(KEY_WATER_CUPS, 0);
    }

    /**
     * Set water cups consumed today.
     */
    public void setWaterCupsToday(int cups) {
        prefs.edit().putInt(KEY_WATER_CUPS, Math.max(0, cups)).apply();
        waterCupsLiveData.postValue(cups);
        scheduleSyncIfNeeded();
    }

    /**
     * Add one cup of water.
     */
    public int addWaterCup() {
        int current = getWaterCupsToday();
        int newValue = current + 1;
        setWaterCupsToday(newValue);
        return newValue;
    }

    /**
     * Remove one cup of water.
     */
    public int removeWaterCup() {
        int current = getWaterCupsToday();
        int newValue = Math.max(0, current - 1);
        setWaterCupsToday(newValue);
        return newValue;
    }

    /**
     * Get water goal in cups.
     */
    public int getWaterGoal() {
        return prefs.getInt(KEY_WATER_GOAL, DEFAULT_WATER_GOAL);
    }

    /**
     * Set water goal in cups.
     */
    public void setWaterGoal(int goal) {
        prefs.edit().putInt(KEY_WATER_GOAL, Math.max(1, goal)).apply();
        syncGoalsToFirestore();
    }

    /**
     * Get water progress as percentage (0-100).
     */
    public int getWaterProgressPercent() {
        int cups = getWaterCupsToday();
        int goal = getWaterGoal();
        return goal > 0 ? Math.min(100, (cups * 100) / goal) : 0;
    }

    /**
     * Get formatted water progress string (e.g., "6/8 Cups").
     */
    public String getWaterProgressString() {
        return getWaterCupsToday() + "/" + getWaterGoal() + " Cups";
    }

    /**
     * Check if water goal is reached.
     */
    public boolean isWaterGoalReached() {
        return getWaterCupsToday() >= getWaterGoal();
    }

    /**
     * Get LiveData for water cups to observe in UI.
     */
    public LiveData<Integer> getWaterCupsLiveData() {
        return waterCupsLiveData;
    }

    // ============ Sleep Tracking ============

    /**
     * Get sleep hours for today.
     */
    public float getSleepHours() {
        checkAndResetForNewDay();
        return prefs.getFloat(KEY_SLEEP_HOURS, 0f);
    }

    /**
     * Set sleep hours for today.
     */
    public void setSleepHours(float hours) {
        prefs.edit().putFloat(KEY_SLEEP_HOURS, hours).apply();
        scheduleSyncIfNeeded();
    }

    /**
     * Get sleep goal in hours.
     */
    public float getSleepGoal() {
        return prefs.getFloat(KEY_SLEEP_GOAL, DEFAULT_SLEEP_GOAL);
    }

    /**
     * Set sleep goal in hours.
     */
    public void setSleepGoal(float goal) {
        prefs.edit().putFloat(KEY_SLEEP_GOAL, goal).apply();
        syncGoalsToFirestore();
    }

    // ============ Exercise Tracking ============

    /**
     * Get squat reps for today.
     */
    public int getSquatRepsToday() {
        checkAndResetForNewDay();
        return prefs.getInt(KEY_SQUAT_REPS, 0);
    }

    /**
     * Add squat reps to today's count.
     */
    public void addSquatReps(int reps) {
        int current = getSquatRepsToday();
        prefs.edit().putInt(KEY_SQUAT_REPS, current + reps).apply();
    }

    /**
     * Get bicep curl reps for today.
     */
    public int getBicepCurlRepsToday() {
        checkAndResetForNewDay();
        return prefs.getInt(KEY_BICEP_CURL_REPS, 0);
    }

    /**
     * Add bicep curl reps to today's count.
     */
    public void addBicepCurlReps(int reps) {
        int current = getBicepCurlRepsToday();
        prefs.edit().putInt(KEY_BICEP_CURL_REPS, current + reps).apply();
    }

    /**
     * Get lunge reps for today.
     */
    public int getLungeRepsToday() {
        checkAndResetForNewDay();
        return prefs.getInt(KEY_LUNGE_REPS, 0);
    }

    /**
     * Add lunge reps to today's count.
     */
    public void addLungeReps(int reps) {
        int current = getLungeRepsToday();
        prefs.edit().putInt(KEY_LUNGE_REPS, current + reps).apply();
    }

    /**
     * Get plank seconds for today.
     */
    public int getPlankSecondsToday() {
        checkAndResetForNewDay();
        return prefs.getInt(KEY_PLANK_SECONDS, 0);
    }

    /**
     * Add plank seconds to today's count.
     */
    public void addPlankSeconds(int seconds) {
        int current = getPlankSecondsToday();
        prefs.edit().putInt(KEY_PLANK_SECONDS, current + seconds).apply();
    }

    /**
     * Get workouts count for today.
     */
    public int getWorkoutsCountToday() {
        checkAndResetForNewDay();
        return prefs.getInt(KEY_WORKOUTS_COUNT, 0);
    }

    /**
     * Get total workout duration in seconds for today.
     */
    public int getTotalWorkoutDurationToday() {
        checkAndResetForNewDay();
        return prefs.getInt(KEY_TOTAL_WORKOUT_DURATION, 0);
    }

    /**
     * Record a completed workout.
     */
    public void recordWorkout(int durationSeconds, int caloriesBurned) {
        int currentCount = getWorkoutsCountToday();
        int currentDuration = getTotalWorkoutDurationToday();
        
        prefs.edit()
                .putInt(KEY_WORKOUTS_COUNT, currentCount + 1)
                .putInt(KEY_TOTAL_WORKOUT_DURATION, currentDuration + durationSeconds)
                .apply();
        
        // Add active minutes
        addActiveMinutes(durationSeconds / 60);
        
        // Add calories
        addCalories(caloriesBurned);
        
        // Sync to Firestore
        syncToFirestore();
    }

    /**
     * Record exercise reps by type.
     */
    public void recordExercise(String exerciseType, int reps, int durationSeconds, int caloriesBurned) {
        switch (exerciseType.toLowerCase()) {
            case "squat":
            case "squats":
                addSquatReps(reps);
                break;
            case "bicep":
            case "bicep curl":
            case "bicep curls":
                addBicepCurlReps(reps);
                break;
            case "lunge":
            case "lunges":
                addLungeReps(reps);
                break;
            case "plank":
            case "planks":
                addPlankSeconds(durationSeconds);
                break;
        }
        
        recordWorkout(durationSeconds, caloriesBurned);
    }

    // ============ Calculation Helpers ============

    /**
     * Calculate calories from steps.
     * Uses formula: calories = steps * 0.04 (average for moderate walking)
     */
    private int calculateCaloriesFromSteps(int steps) {
        return (int) (steps * 0.04);
    }

    /**
     * Calculate distance from steps in kilometers.
     * Uses formula: distance = steps * 0.0007 km (average stride length ~70cm)
     */
    private float calculateDistanceFromSteps(int steps) {
        return steps * 0.0007f;
    }

    // ============ Firestore Sync ============

    /**
     * Schedule sync if enough time has passed since last sync.
     */
    private void scheduleSyncIfNeeded() {
        long now = System.currentTimeMillis();
        if (now - lastSyncTime > SYNC_INTERVAL_MS) {
            syncToFirestore();
        }
    }

    /**
     * Sync current day's data to Firestore.
     */
    public void syncToFirestore() {
        syncToFirestore(getTodayDateString());
    }

    /**
     * Sync data for a specific date to Firestore.
     */
    private void syncToFirestore(String dateKey) {
        if (isSyncing) return;
        
        isSyncing = true;
        lastSyncTime = System.currentTimeMillis();

        DailyActivity activity = new DailyActivity(dateKey);
        activity.setSteps(getStepsToday());
        activity.setCalories(getCaloriesToday());
        activity.setDistance(getDistanceToday());
        activity.setActiveMinutes(getActiveMinutesToday());
        activity.setWaterCups(getWaterCupsToday());
        activity.setWaterGoal(getWaterGoal());
        activity.setSleepHours(getSleepHours());
        activity.setSquatReps(getSquatRepsToday());
        activity.setBicepCurlReps(getBicepCurlRepsToday());
        activity.setLungeReps(getLungeRepsToday());
        activity.setPlankSeconds(getPlankSecondsToday());
        activity.setWorkoutsCount(getWorkoutsCountToday());
        activity.setTotalWorkoutDuration(getTotalWorkoutDurationToday());

        UserRepository.getInstance().saveDailyActivity(activity, new UserRepository.OnCompleteListener() {
            @Override
            public void onSuccess() {
                Log.d(TAG, "Data synced to Firestore: " + dateKey);
                isSyncing = false;
            }

            @Override
            public void onError(String error) {
                Log.e(TAG, "Failed to sync to Firestore: " + error);
                isSyncing = false;
            }
        });
    }

    /**
     * Sync goals to Firestore.
     */
    private void syncGoalsToFirestore() {
        UserRepository.getInstance().saveGoals(
                getStepGoal(),
                getCaloriesGoal(),
                getActiveTimeGoal(),
                getWaterGoal(),
                getSleepGoal(),
                null
        );
    }

    /**
     * Load data from Firestore (useful when app starts or user logs in).
     */
    public void loadFromFirestore(OnDataLoadedListener listener) {
        UserRepository.getInstance().getTodayActivity(activity -> {
            if (activity != null) {
                // Merge with local data - take the higher values
                int localSteps = getStepsToday();
                int firestoreSteps = activity.getSteps();
                
                if (firestoreSteps > localSteps) {
                    prefs.edit()
                            .putInt(KEY_STEPS_TODAY, firestoreSteps)
                            .putInt(KEY_CALORIES_TODAY, activity.getCalories())
                            .putFloat(KEY_DISTANCE_TODAY, activity.getDistance())
                            .apply();
                    stepsLiveData.postValue(firestoreSteps);
                    caloriesLiveData.postValue(activity.getCalories());
                    distanceLiveData.postValue(activity.getDistance());
                }
                
                // For other metrics, take Firestore values if local is 0
                if (getActiveMinutesToday() == 0 && activity.getActiveMinutes() > 0) {
                    setActiveMinutesToday(activity.getActiveMinutes());
                }
                if (getWaterCupsToday() == 0 && activity.getWaterCups() > 0) {
                    setWaterCupsToday(activity.getWaterCups());
                }
                
                Log.d(TAG, "Loaded and merged data from Firestore");
            }
            
            if (listener != null) {
                listener.onDataLoaded();
            }
        });

        // Also load goals
        UserRepository.getInstance().loadGoals(goals -> {
            if (goals != null) {
                if (goals.containsKey("stepGoal")) {
                    int stepGoal = ((Number) goals.get("stepGoal")).intValue();
                    if (stepGoal > 0) {
                        prefs.edit().putInt(KEY_STEP_GOAL, stepGoal).apply();
                    }
                }
                if (goals.containsKey("caloriesGoal")) {
                    int caloriesGoal = ((Number) goals.get("caloriesGoal")).intValue();
                    if (caloriesGoal > 0) {
                        prefs.edit().putInt(KEY_CALORIES_GOAL, caloriesGoal).apply();
                    }
                }
                if (goals.containsKey("activeTimeGoal")) {
                    int activeTimeGoal = ((Number) goals.get("activeTimeGoal")).intValue();
                    if (activeTimeGoal > 0) {
                        prefs.edit().putInt(KEY_ACTIVE_TIME_GOAL, activeTimeGoal).apply();
                    }
                }
                if (goals.containsKey("waterGoal")) {
                    int waterGoal = ((Number) goals.get("waterGoal")).intValue();
                    if (waterGoal > 0) {
                        prefs.edit().putInt(KEY_WATER_GOAL, waterGoal).apply();
                    }
                }
                if (goals.containsKey("sleepGoal")) {
                    float sleepGoal = ((Number) goals.get("sleepGoal")).floatValue();
                    if (sleepGoal > 0) {
                        prefs.edit().putFloat(KEY_SLEEP_GOAL, sleepGoal).apply();
                    }
                }
                Log.d(TAG, "Loaded goals from Firestore");
            }
        });
    }

    /**
     * Get today's date as string in yyyy-MM-dd format.
     */
    private String getTodayDateString() {
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd", Locale.US);
        return sdf.format(new Date());
    }

    /**
     * Callback interface for data loaded events.
     */
    public interface OnDataLoadedListener {
        void onDataLoaded();
    }
}
