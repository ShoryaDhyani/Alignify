package com.alignify.util;

import android.content.Context;
import android.content.SharedPreferences;

import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.Locale;

/**
 * Helper class for tracking daily water intake using SharedPreferences.
 * Automatically resets at midnight for each new day.
 */
public class WaterTrackingHelper {

    private static final String PREFS_NAME = "water_tracking_prefs";
    private static final String KEY_WATER_CUPS = "water_cups";
    private static final String KEY_WATER_GOAL = "water_goal";
    private static final String KEY_LAST_TRACKED_DATE = "last_tracked_date";
    private static final int DEFAULT_WATER_GOAL = 8;

    private final SharedPreferences prefs;

    public WaterTrackingHelper(Context context) {
        prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        checkAndResetForNewDay();
    }

    /**
     * Check if date has changed and reset water cups if new day.
     */
    private void checkAndResetForNewDay() {
        String today = getTodayDateString();
        String lastTrackedDate = prefs.getString(KEY_LAST_TRACKED_DATE, "");

        if (!today.equals(lastTrackedDate)) {
            // New day - reset water cups
            prefs.edit()
                    .putInt(KEY_WATER_CUPS, 0)
                    .putString(KEY_LAST_TRACKED_DATE, today)
                    .apply();
        }
    }

    /**
     * Get current water cups count for today.
     */
    public int getWaterCups() {
        checkAndResetForNewDay();
        return prefs.getInt(KEY_WATER_CUPS, 0);
    }

    /**
     * Set water cups count.
     */
    public void setWaterCups(int cups) {
        prefs.edit()
                .putInt(KEY_WATER_CUPS, Math.max(0, cups))
                .putString(KEY_LAST_TRACKED_DATE, getTodayDateString())
                .apply();
    }

    /**
     * Add one cup of water.
     */
    public int addWaterCup() {
        int current = getWaterCups();
        int newValue = current + 1;
        setWaterCups(newValue);
        return newValue;
    }

    /**
     * Remove one cup of water (minimum 0).
     */
    public int removeWaterCup() {
        int current = getWaterCups();
        int newValue = Math.max(0, current - 1);
        setWaterCups(newValue);
        return newValue;
    }

    /**
     * Get daily water goal.
     */
    public int getWaterGoal() {
        return prefs.getInt(KEY_WATER_GOAL, DEFAULT_WATER_GOAL);
    }

    /**
     * Set daily water goal.
     */
    public void setWaterGoal(int goal) {
        prefs.edit()
                .putInt(KEY_WATER_GOAL, Math.max(1, goal))
                .apply();
    }

    /**
     * Get progress as a percentage (0-100).
     */
    public int getProgressPercent() {
        int cups = getWaterCups();
        int goal = getWaterGoal();
        return Math.min(100, (cups * 100) / goal);
    }

    /**
     * Check if water goal has been reached.
     */
    public boolean isGoalReached() {
        return getWaterCups() >= getWaterGoal();
    }

    /**
     * Get remaining cups to reach goal.
     */
    public int getRemainingCups() {
        return Math.max(0, getWaterGoal() - getWaterCups());
    }

    /**
     * Get formatted progress string (e.g., "6/8 Cups").
     */
    public String getProgressString() {
        return getWaterCups() + "/" + getWaterGoal() + " Cups";
    }

    /**
     * Get today's date as string in yyyy-MM-dd format.
     */
    private String getTodayDateString() {
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd", Locale.US);
        return sdf.format(new Date());
    }

    /**
     * Get the next reminder time based on schedule (8AM-10PM, every 2 hours).
     */
    public static long getNextReminderTime() {
        Calendar now = Calendar.getInstance();
        Calendar next = Calendar.getInstance();

        int currentHour = now.get(Calendar.HOUR_OF_DAY);

        // Reminder hours: 8, 10, 12, 14, 16, 18, 20, 22 (8AM to 10PM every 2 hours)
        int[] reminderHours = { 8, 10, 12, 14, 16, 18, 20, 22 };

        // Find next reminder hour
        int nextHour = -1;
        for (int hour : reminderHours) {
            if (hour > currentHour || (hour == currentHour && now.get(Calendar.MINUTE) < 0)) {
                nextHour = hour;
                break;
            }
        }

        if (nextHour == -1) {
            // Schedule for next day at 8AM
            next.add(Calendar.DAY_OF_YEAR, 1);
            nextHour = 8;
        }

        next.set(Calendar.HOUR_OF_DAY, nextHour);
        next.set(Calendar.MINUTE, 0);
        next.set(Calendar.SECOND, 0);
        next.set(Calendar.MILLISECOND, 0);

        return next.getTimeInMillis();
    }
}
