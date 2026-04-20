package com.alignify.data;

import androidx.annotation.NonNull;
import androidx.room.Entity;
import androidx.room.PrimaryKey;

/**
 * Room entity for daily activity aggregates.
 * Replaces Firestore dailyActivity sub-collection.
 */
@Entity(tableName = "daily_activity")
public class DailyActivityEntity {

    @PrimaryKey
    @NonNull
    public String date; // Format: "yyyy-MM-dd"

    public int steps;
    public float distance; // km
    public int calories;
    public int activeMinutes;
    public int workoutsCount;
    public int totalWorkoutDuration; // seconds
    public long timestamp;

    // Water tracking
    public int waterCups;
    public int waterGoal;

    // Sleep tracking
    public float sleepHours;

    // Exercise-specific
    public int squatReps;
    public int bicepCurlReps;
    public int lungeReps;
    public int plankSeconds;

    public DailyActivityEntity() {
        this.date = "";
    }

    /** Convert from the existing DailyActivity model. */
    public static DailyActivityEntity fromDailyActivity(DailyActivity da) {
        DailyActivityEntity e = new DailyActivityEntity();
        e.date = da.getDate();
        e.steps = da.getSteps();
        e.distance = da.getDistance();
        e.calories = da.getCalories();
        e.activeMinutes = da.getActiveMinutes();
        e.workoutsCount = da.getWorkoutsCount();
        e.totalWorkoutDuration = da.getTotalWorkoutDuration();
        e.timestamp = da.getTimestamp();
        e.waterCups = da.getWaterCups();
        e.waterGoal = da.getWaterGoal();
        e.sleepHours = da.getSleepHours();
        e.squatReps = da.getSquatReps();
        e.bicepCurlReps = da.getBicepCurlReps();
        e.lungeReps = da.getLungeReps();
        e.plankSeconds = da.getPlankSeconds();
        return e;
    }

    /** Convert to the existing DailyActivity model. */
    public DailyActivity toDailyActivity() {
        DailyActivity da = new DailyActivity(date);
        da.setSteps(steps);
        da.setDistance(distance);
        da.setCalories(calories);
        da.setActiveMinutes(activeMinutes);
        da.setWorkoutsCount(workoutsCount);
        da.setTotalWorkoutDuration(totalWorkoutDuration);
        da.setTimestamp(timestamp);
        da.setWaterCups(waterCups);
        da.setWaterGoal(waterGoal);
        da.setSleepHours(sleepHours);
        da.setSquatReps(squatReps);
        da.setBicepCurlReps(bicepCurlReps);
        da.setLungeReps(lungeReps);
        da.setPlankSeconds(plankSeconds);
        return da;
    }
}
