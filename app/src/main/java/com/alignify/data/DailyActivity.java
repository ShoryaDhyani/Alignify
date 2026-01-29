package com.alignify.data;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

/**
 * Data model for daily activity aggregates.
 * Stored in Firestore: users/{userId}/dailyActivity/{date}
 */
public class DailyActivity {

    private String date; // Format: YYYY-MM-DD
    private int steps;
    private float distance; // in km
    private int calories;
    private int activeMinutes;
    private int workoutsCount;
    private int totalWorkoutDuration; // seconds
    private long timestamp; // last updated

    // Required for Firestore
    public DailyActivity() {
    }

    public DailyActivity(String date) {
        this.date = date;
        this.timestamp = System.currentTimeMillis();
    }

    // Static helper to get today's date key
    public static String todayKey() {
        return new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(new Date());
    }

    // Static helper to get date key for any date
    public static String dateKey(long timestamp) {
        return new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(new Date(timestamp));
    }

    // Convert to Firestore map
    public Map<String, Object> toMap() {
        Map<String, Object> map = new HashMap<>();
        map.put("date", date);
        map.put("steps", steps);
        map.put("distance", distance);
        map.put("calories", calories);
        map.put("activeMinutes", activeMinutes);
        map.put("workoutsCount", workoutsCount);
        map.put("totalWorkoutDuration", totalWorkoutDuration);
        map.put("timestamp", System.currentTimeMillis());
        return map;
    }

    // Create from Firestore document
    public static DailyActivity fromMap(Map<String, Object> map) {
        DailyActivity activity = new DailyActivity();
        activity.date = (String) map.get("date");
        activity.steps = getInt(map, "steps");
        activity.distance = getFloat(map, "distance");
        activity.calories = getInt(map, "calories");
        activity.activeMinutes = getInt(map, "activeMinutes");
        activity.workoutsCount = getInt(map, "workoutsCount");
        activity.totalWorkoutDuration = getInt(map, "totalWorkoutDuration");
        activity.timestamp = getLong(map, "timestamp");
        return activity;
    }

    private static int getInt(Map<String, Object> map, String key) {
        Object val = map.get(key);
        if (val instanceof Number)
            return ((Number) val).intValue();
        return 0;
    }

    private static float getFloat(Map<String, Object> map, String key) {
        Object val = map.get(key);
        if (val instanceof Number)
            return ((Number) val).floatValue();
        return 0f;
    }

    private static long getLong(Map<String, Object> map, String key) {
        Object val = map.get(key);
        if (val instanceof Number)
            return ((Number) val).longValue();
        return 0L;
    }

    // Getters and Setters
    public String getDate() {
        return date;
    }

    public void setDate(String date) {
        this.date = date;
    }

    public int getSteps() {
        return steps;
    }

    public void setSteps(int steps) {
        this.steps = steps;
    }

    public float getDistance() {
        return distance;
    }

    public void setDistance(float distance) {
        this.distance = distance;
    }

    public int getCalories() {
        return calories;
    }

    public void setCalories(int calories) {
        this.calories = calories;
    }

    public int getActiveMinutes() {
        return activeMinutes;
    }

    public void setActiveMinutes(int activeMinutes) {
        this.activeMinutes = activeMinutes;
    }

    public int getWorkoutsCount() {
        return workoutsCount;
    }

    public void setWorkoutsCount(int workoutsCount) {
        this.workoutsCount = workoutsCount;
    }

    public int getTotalWorkoutDuration() {
        return totalWorkoutDuration;
    }

    public void setTotalWorkoutDuration(int totalWorkoutDuration) {
        this.totalWorkoutDuration = totalWorkoutDuration;
    }

    public long getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(long timestamp) {
        this.timestamp = timestamp;
    }

    // Increment methods for atomic updates
    public void addSteps(int stepsToAdd) {
        this.steps += stepsToAdd;
    }

    public void addCalories(int caloriesToAdd) {
        this.calories += caloriesToAdd;
    }

    public void addActiveMinutes(int minutesToAdd) {
        this.activeMinutes += minutesToAdd;
    }

    public void addWorkout(int durationSeconds) {
        this.workoutsCount++;
        this.totalWorkoutDuration += durationSeconds;
    }
}
