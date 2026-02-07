package com.alignify.util;

import android.content.Context;

import com.alignify.data.FitnessDataManager;

import java.util.Calendar;

/**
 * Helper class for tracking daily water intake.
 * 
 * This class now delegates to FitnessDataManager for consistent data management
 * across all activities. FitnessDataManager handles:
 * - Local caching in SharedPreferences
 * - Automatic midnight reset
 * - Firebase Firestore sync
 * 
 * @deprecated Use FitnessDataManager directly for new code. This class is kept
 *             for backward compatibility and to avoid breaking existing code.
 */
public class WaterTrackingHelper {

    private final FitnessDataManager fitnessDataManager;

    public WaterTrackingHelper(Context context) {
        fitnessDataManager = FitnessDataManager.getInstance(context);
    }

    /**
     * Get current water cups count for today.
     */
    public int getWaterCups() {
        return fitnessDataManager.getWaterCupsToday();
    }

    /**
     * Set water cups count.
     */
    public void setWaterCups(int cups) {
        fitnessDataManager.setWaterCupsToday(cups);
    }

    /**
     * Add one cup of water.
     */
    public int addWaterCup() {
        return fitnessDataManager.addWaterCup();
    }

    /**
     * Remove one cup of water (minimum 0).
     */
    public int removeWaterCup() {
        return fitnessDataManager.removeWaterCup();
    }

    /**
     * Get daily water goal.
     */
    public int getWaterGoal() {
        return fitnessDataManager.getWaterGoal();
    }

    /**
     * Set daily water goal.
     */
    public void setWaterGoal(int goal) {
        fitnessDataManager.setWaterGoal(goal);
    }

    /**
     * Get progress as a percentage (0-100).
     */
    public int getProgressPercent() {
        return fitnessDataManager.getWaterProgressPercent();
    }

    /**
     * Check if water goal has been reached.
     */
    public boolean isGoalReached() {
        return fitnessDataManager.isWaterGoalReached();
    }

    /**
     * Get remaining cups to reach goal.
     */
    public int getRemainingCups() {
        int goal = fitnessDataManager.getWaterGoal();
        int cups = fitnessDataManager.getWaterCupsToday();
        return Math.max(0, goal - cups);
    }

    /**
     * Get formatted progress string (e.g., "6/8 Cups").
     */
    public String getProgressString() {
        return fitnessDataManager.getWaterProgressString();
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
