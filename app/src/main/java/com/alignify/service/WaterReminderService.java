package com.alignify.service;

import android.app.AlarmManager;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.Build;

import androidx.core.app.NotificationCompat;

import com.alignify.ActivityActivity;
import com.alignify.R;
import com.alignify.util.WaterTrackingHelper;

/**
 * Service for scheduling and handling water reminder notifications.
 * Sends reminders every 2 hours from 8AM to 10PM.
 */
public class WaterReminderService extends BroadcastReceiver {

    private static final String TAG = "WaterReminderService";
    private static final String CHANNEL_ID = "water_reminder_channel";
    private static final String CHANNEL_NAME = "Water Reminders";
    private static final int NOTIFICATION_ID = 2001;

    public static final String ACTION_WATER_REMINDER = "com.alignify.ACTION_WATER_REMINDER";
    public static final String ACTION_LOG_WATER = "com.alignify.ACTION_LOG_WATER";

    @Override
    public void onReceive(Context context, Intent intent) {
        String action = intent.getAction();

        if (ACTION_WATER_REMINDER.equals(action)) {
            // Show water reminder notification
            showWaterReminderNotification(context);
            // Schedule next reminder
            scheduleNextReminder(context);
        } else if (ACTION_LOG_WATER.equals(action)) {
            // User tapped "Log Water" action
            logWaterFromNotification(context);
        }
    }

    /**
     * Schedule water reminders to start.
     */
    public static void scheduleReminders(Context context) {
        createNotificationChannel(context);
        scheduleNextReminder(context);
    }

    /**
     * Cancel all water reminders.
     */
    public static void cancelReminders(Context context) {
        AlarmManager alarmManager = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
        PendingIntent pendingIntent = getReminderPendingIntent(context);

        if (alarmManager != null) {
            alarmManager.cancel(pendingIntent);
        }
    }

    /**
     * Schedule the next water reminder based on the schedule.
     */
    private static void scheduleNextReminder(Context context) {
        AlarmManager alarmManager = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
        if (alarmManager == null)
            return;

        long nextReminderTime = WaterTrackingHelper.getNextReminderTime();
        PendingIntent pendingIntent = getReminderPendingIntent(context);

        // Cancel any existing reminder
        alarmManager.cancel(pendingIntent);

        // Check for exact alarm permission on Android 12+
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            if (!alarmManager.canScheduleExactAlarms()) {
                // Use inexact alarm as fallback
                alarmManager.setAndAllowWhileIdle(
                        AlarmManager.RTC_WAKEUP,
                        nextReminderTime,
                        pendingIntent);
                return;
            }
        }

        // Schedule new reminder with exact timing
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            alarmManager.setExactAndAllowWhileIdle(
                    AlarmManager.RTC_WAKEUP,
                    nextReminderTime,
                    pendingIntent);
        } else {
            alarmManager.setExact(
                    AlarmManager.RTC_WAKEUP,
                    nextReminderTime,
                    pendingIntent);
        }
    }

    private static PendingIntent getReminderPendingIntent(Context context) {
        Intent intent = new Intent(context, WaterReminderService.class);
        intent.setAction(ACTION_WATER_REMINDER);

        int flags = PendingIntent.FLAG_UPDATE_CURRENT;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            flags |= PendingIntent.FLAG_IMMUTABLE;
        }

        return PendingIntent.getBroadcast(context, 0, intent, flags);
    }

    /**
     * Show water reminder notification with action button.
     */
    private void showWaterReminderNotification(Context context) {
        WaterTrackingHelper waterHelper = new WaterTrackingHelper(context);

        // Don't show notification if goal already reached
        if (waterHelper.isGoalReached()) {
            return;
        }

        NotificationManager notificationManager = (NotificationManager) context
                .getSystemService(Context.NOTIFICATION_SERVICE);
        if (notificationManager == null)
            return;

        // Intent to open Activity screen
        Intent openIntent = new Intent(context, ActivityActivity.class);
        openIntent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        PendingIntent openPendingIntent = PendingIntent.getActivity(
                context, 0, openIntent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        // Intent to log water directly from notification
        Intent logWaterIntent = new Intent(context, WaterReminderService.class);
        logWaterIntent.setAction(ACTION_LOG_WATER);
        PendingIntent logWaterPendingIntent = PendingIntent.getBroadcast(
                context, 1, logWaterIntent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        int remainingCups = waterHelper.getRemainingCups();
        String message = remainingCups + " more cup" + (remainingCups > 1 ? "s" : "") + " to reach your goal!";

        NotificationCompat.Builder builder = new NotificationCompat.Builder(context, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_water)
                .setContentTitle("💧 Time to hydrate!")
                .setContentText(message)
                .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                .setContentIntent(openPendingIntent)
                .setAutoCancel(true)
                .addAction(R.drawable.ic_water, "Log Water", logWaterPendingIntent);

        notificationManager.notify(NOTIFICATION_ID, builder.build());
    }

    /**
     * Log water when user taps notification action.
     */
    private void logWaterFromNotification(Context context) {
        WaterTrackingHelper waterHelper = new WaterTrackingHelper(context);
        waterHelper.addWaterCup();

        // Dismiss the notification
        NotificationManager notificationManager = (NotificationManager) context
                .getSystemService(Context.NOTIFICATION_SERVICE);
        if (notificationManager != null) {
            notificationManager.cancel(NOTIFICATION_ID);
        }

        // Show confirmation notification
        if (!waterHelper.isGoalReached()) {
            showConfirmationNotification(context, waterHelper);
        } else {
            showGoalReachedNotification(context);
        }
    }

    private void showConfirmationNotification(Context context, WaterTrackingHelper waterHelper) {
        NotificationManager notificationManager = (NotificationManager) context
                .getSystemService(Context.NOTIFICATION_SERVICE);
        if (notificationManager == null)
            return;

        NotificationCompat.Builder builder = new NotificationCompat.Builder(context, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_water)
                .setContentTitle("💧 Water logged!")
                .setContentText("Progress: " + waterHelper.getProgressString())
                .setPriority(NotificationCompat.PRIORITY_LOW)
                .setAutoCancel(true);

        notificationManager.notify(NOTIFICATION_ID + 1, builder.build());
    }

    private void showGoalReachedNotification(Context context) {
        NotificationManager notificationManager = (NotificationManager) context
                .getSystemService(Context.NOTIFICATION_SERVICE);
        if (notificationManager == null)
            return;

        NotificationCompat.Builder builder = new NotificationCompat.Builder(context, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_water)
                .setContentTitle("🎉 Goal Reached!")
                .setContentText("Great job staying hydrated today!")
                .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                .setAutoCancel(true);

        notificationManager.notify(NOTIFICATION_ID + 2, builder.build());
    }

    /**
     * Create notification channel for Android O+.
     */
    private static void createNotificationChannel(Context context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                    CHANNEL_ID,
                    CHANNEL_NAME,
                    NotificationManager.IMPORTANCE_DEFAULT);
            channel.setDescription("Reminders to drink water throughout the day");

            NotificationManager notificationManager = context.getSystemService(NotificationManager.class);
            if (notificationManager != null) {
                notificationManager.createNotificationChannel(channel);
            }
        }
    }
}
