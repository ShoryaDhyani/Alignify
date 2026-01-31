package com.alignify.data;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

/**
 * Data model for daily activity aggregates.
 * Stores steps, calories, distance, active minutes for a single day.
 */
public class DailyActivity {

    private String date; // Format: "yyyy-MM-dd"
    private int steps;
    private float distance; // in kilometers
    private int calories;
    private int activeMinutes;
    private int workoutsCount;
    private int totalWorkoutDuration; // in seconds
    private long timestamp;

    // Water tracking
    private int waterCups;
    private int waterGoal;

    // Sleep tracking
    private float sleepHours;

    // Exercise-specific tracking
    private int squatReps;
    private int bicepCurlReps;
    private int lungeReps;
    private int plankSeconds;

    public DailyActivity() {
        // Required for Firestore
    }

    public DailyActivity(String date) {
        this.date = date;
        this.steps = 0;
        this.distance = 0f;
        this.calories = 0;
        this.activeMinutes = 0;
        this.workoutsCount = 0;
        this.totalWorkoutDuration = 0;
        this.timestamp = System.currentTimeMillis();
        this.waterCups = 0;
        this.waterGoal = 8; // Default 8 cups
        this.sleepHours = 0f;
        this.squatReps = 0;
        this.bicepCurlReps = 0;
        this.lungeReps = 0;
        this.plankSeconds = 0;
    }

    // ============ Getters ============

    public String getDate() {
        return date;
    }

    public int getSteps() {
        return steps;
    }

    public float getDistance() {
        return distance;
    }

    public int getCalories() {
        return calories;
    }

    public int getActiveMinutes() {
        return activeMinutes;
    }

    public int getWorkoutsCount() {
        return workoutsCount;
    }

    public int getTotalWorkoutDuration() {
        return totalWorkoutDuration;
    }

    public long getTimestamp() {
        return timestamp;
    }

    public int getWaterCups() {
        return waterCups;
    }

    public int getWaterGoal() {
        return waterGoal;
    }

    public float getSleepHours() {
        return sleepHours;
    }

    public int getSquatReps() {
        return squatReps;
    }

    public int getBicepCurlReps() {
        return bicepCurlReps;
    }

    public int getLungeReps() {
        return lungeReps;
    }

    public int getPlankSeconds() {
        return plankSeconds;
    }

    // ============ Setters ============

    public void setDate(String date) {
        this.date = date;
    }

    public void setSteps(int steps) {
        this.steps = steps;
    }

    public void setDistance(float distance) {
        this.distance = distance;
    }

    public void setCalories(int calories) {
        this.calories = calories;
    }

    public void setActiveMinutes(int activeMinutes) {
        this.activeMinutes = activeMinutes;
    }

    public void setWorkoutsCount(int workoutsCount) {
        this.workoutsCount = workoutsCount;
    }

    public void setTotalWorkoutDuration(int totalWorkoutDuration) {
        this.totalWorkoutDuration = totalWorkoutDuration;
    }

    public void setTimestamp(long timestamp) {
        this.timestamp = timestamp;
    }

    public void setWaterCups(int waterCups) {
        this.waterCups = waterCups;
    }

    public void setWaterGoal(int waterGoal) {
        this.waterGoal = waterGoal;
    }

    public void setSleepHours(float sleepHours) {
        this.sleepHours = sleepHours;
    }

    public void setSquatReps(int squatReps) {
        this.squatReps = squatReps;
    }

    public void setBicepCurlReps(int bicepCurlReps) {
        this.bicepCurlReps = bicepCurlReps;
    }

    public void setLungeReps(int lungeReps) {
        this.lungeReps = lungeReps;
    }

    public void setPlankSeconds(int plankSeconds) {
        this.plankSeconds = plankSeconds;
    }

    public void addSquatReps(int reps) {
        this.squatReps += reps;
    }

    public void addBicepCurlReps(int reps) {
        this.bicepCurlReps += reps;
    }

    public void addLungeReps(int reps) {
        this.lungeReps += reps;
    }

    public void addPlankSeconds(int seconds) {
        this.plankSeconds += seconds;
    }

    // ============ Increment Methods ============

    public void addSteps(int steps) {
        this.steps += steps;
    }

    public void addCalories(int calories) {
        this.calories += calories;
    }

    public void addActiveMinutes(int minutes) {
        this.activeMinutes += minutes;
    }

    public void addWorkout(int durationSeconds) {
        this.workoutsCount++;
        this.totalWorkoutDuration += durationSeconds;
    }

    public void addWaterCup() {
        this.waterCups++;
    }

    public void removeWaterCup() {
        if (this.waterCups > 0) {
            this.waterCups--;
        }
    }

    // ============ Firestore Conversion ============

    public Map<String, Object> toMap() {
        Map<String, Object> map = new HashMap<>();
        map.put("date", date);
        map.put("steps", steps);
        map.put("distance", distance);
        map.put("calories", calories);
        map.put("activeMinutes", activeMinutes);
        map.put("workoutsCount", workoutsCount);
        map.put("totalWorkoutDuration", totalWorkoutDuration);
        map.put("timestamp", timestamp);
        map.put("waterCups", waterCups);
        map.put("waterGoal", waterGoal);
        map.put("sleepHours", sleepHours);
        map.put("squatReps", squatReps);
        map.put("bicepCurlReps", bicepCurlReps);
        map.put("lungeReps", lungeReps);
        map.put("plankSeconds", plankSeconds);
        return map;
    }

    public static DailyActivity fromMap(Map<String, Object> map) {
        DailyActivity activity = new DailyActivity();
        activity.date = (String) map.get("date");
        activity.steps = ((Number) map.getOrDefault("steps", 0)).intValue();
        activity.distance = ((Number) map.getOrDefault("distance", 0f)).floatValue();
        activity.calories = ((Number) map.getOrDefault("calories", 0)).intValue();
        activity.activeMinutes = ((Number) map.getOrDefault("activeMinutes", 0)).intValue();
        activity.workoutsCount = ((Number) map.getOrDefault("workoutsCount", 0)).intValue();
        activity.totalWorkoutDuration = ((Number) map.getOrDefault("totalWorkoutDuration", 0)).intValue();
        activity.timestamp = ((Number) map.getOrDefault("timestamp", 0L)).longValue();
        activity.waterCups = ((Number) map.getOrDefault("waterCups", 0)).intValue();
        activity.waterGoal = ((Number) map.getOrDefault("waterGoal", 8)).intValue();
        activity.sleepHours = ((Number) map.getOrDefault("sleepHours", 0f)).floatValue();
        activity.squatReps = ((Number) map.getOrDefault("squatReps", 0)).intValue();
        activity.bicepCurlReps = ((Number) map.getOrDefault("bicepCurlReps", 0)).intValue();
        activity.lungeReps = ((Number) map.getOrDefault("lungeReps", 0)).intValue();
        activity.plankSeconds = ((Number) map.getOrDefault("plankSeconds", 0)).intValue();
        return activity;
    }

    // ============ Helper Methods ============

    /**
     * Get today's date key in yyyy-MM-dd format.
     */
    public static String todayKey() {
        return dateKey(System.currentTimeMillis());
    }

    /**
     * Get date key for a timestamp.
     */
    public static String dateKey(long timestamp) {
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd", Locale.US);
        return sdf.format(new Date(timestamp));
    }
}
