package com.alignify.service;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.os.Build;
import android.os.IBinder;
import android.util.Log;

import androidx.core.app.NotificationCompat;
import androidx.localbroadcastmanager.content.LocalBroadcastManager;

import com.alignify.DashboardActivity;
import com.alignify.R;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/**
 * Foreground Service that tracks steps using the device's TYPE_STEP_COUNTER
 * sensor.
 * 
 * The TYPE_STEP_COUNTER sensor returns the total number of steps since the last
 * device reboot.
 * This service handles:
 * - Daily step tracking (resets at midnight)
 * - Device reboot handling (restores step count baseline)
 * - Real-time step updates via LocalBroadcast
 * - Persistent notification (required for Android 10+)
 * 
 * Battery optimization: Uses hardware sensor hub which is extremely
 * power-efficient.
 * The sensor is always-on and batches events to minimize CPU wake-ups.
 */
public class StepCounterService extends Service implements SensorEventListener {

    private static final String TAG = "StepCounterService";

    // Notification constants
    private static final String CHANNEL_ID = "step_counter_channel";
    private static final int NOTIFICATION_ID = 1001;

    // SharedPreferences constants
    public static final String PREFS_NAME = "StepCounterPrefs";
    public static final String KEY_DAILY_BASELINE = "daily_baseline"; // Steps at start of day
    public static final String KEY_LAST_KNOWN_STEPS = "last_known_steps"; // Last sensor value
    public static final String KEY_LAST_DATE = "last_date"; // Date for daily reset
    public static final String KEY_STEPS_TODAY = "steps_today"; // Calculated daily steps
    public static final String KEY_TOTAL_STEPS_BEFORE_REBOOT = "total_steps_before_reboot"; // Steps before last reboot

    // Broadcast action for step updates
    public static final String ACTION_STEP_UPDATE = "com.alignify.ACTION_STEP_UPDATE";
    public static final String EXTRA_STEPS_TODAY = "extra_steps_today";

    private SensorManager sensorManager;
    private Sensor stepCounterSensor;
    private SharedPreferences prefs;

    private boolean isSensorAvailable = false;
    private int stepsToday = 0;

    @Override
    public void onCreate() {
        super.onCreate();
        Log.d(TAG, "StepCounterService onCreate");

        prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);

        // Initialize sensor
        sensorManager = (SensorManager) getSystemService(Context.SENSOR_SERVICE);
        stepCounterSensor = sensorManager.getDefaultSensor(Sensor.TYPE_STEP_COUNTER);

        if (stepCounterSensor != null) {
            isSensorAvailable = true;
            Log.d(TAG, "Step counter sensor available");
        } else {
            Log.w(TAG, "Step counter sensor NOT available on this device");
        }

        // Create notification channel
        createNotificationChannel();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        Log.d(TAG, "StepCounterService onStartCommand");

        // Load saved steps
        stepsToday = prefs.getInt(KEY_STEPS_TODAY, 0);

        // Start as foreground service with notification
        startForeground(NOTIFICATION_ID, createNotification(stepsToday));

        // Register sensor listener
        if (isSensorAvailable && stepCounterSensor != null) {
            // Use SensorManager.SENSOR_DELAY_NORMAL for battery efficiency
            // The sensor hub batches events, so we don't need frequent updates
            boolean registered = sensorManager.registerListener(
                    this,
                    stepCounterSensor,
                    SensorManager.SENSOR_DELAY_NORMAL);
            Log.d(TAG, "Sensor listener registered: " + registered);
        }

        // Return START_STICKY to restart service if killed
        return START_STICKY;
    }

    @Override
    public void onSensorChanged(SensorEvent event) {
        if (event.sensor.getType() == Sensor.TYPE_STEP_COUNTER) {
            // This is the total steps since last device reboot
            int totalStepsSinceReboot = (int) event.values[0];

            Log.d(TAG, "Sensor event: totalStepsSinceReboot = " + totalStepsSinceReboot);

            // Handle daily reset and calculate steps for today
            handleStepCount(totalStepsSinceReboot);
        }
    }

    /**
     * Handles the step count logic including:
     * - Daily baseline tracking
     * - Device reboot detection
     * - Daily reset at midnight
     */
    private void handleStepCount(int totalStepsSinceReboot) {
        String currentDate = getCurrentDate();
        String lastDate = prefs.getString(KEY_LAST_DATE, "");
        int dailyBaseline = prefs.getInt(KEY_DAILY_BASELINE, -1);
        int lastKnownSteps = prefs.getInt(KEY_LAST_KNOWN_STEPS, -1);
        int totalStepsBeforeReboot = prefs.getInt(KEY_TOTAL_STEPS_BEFORE_REBOOT, 0);

        SharedPreferences.Editor editor = prefs.edit();

        // Check if it's a new day - reset daily baseline
        if (!currentDate.equals(lastDate)) {
            Log.d(TAG, "New day detected, resetting daily baseline");
            dailyBaseline = totalStepsSinceReboot;
            editor.putString(KEY_LAST_DATE, currentDate);
            editor.putInt(KEY_DAILY_BASELINE, dailyBaseline);
            editor.putInt(KEY_STEPS_TODAY, 0); // Reset today's steps
            stepsToday = 0;
        }

        // Detect device reboot: if current steps < last known steps, device was
        // rebooted
        if (lastKnownSteps > 0 && totalStepsSinceReboot < lastKnownSteps) {
            Log.d(TAG, "Device reboot detected. Adjusting baseline.");
            // Save the steps accumulated before reboot
            int stepsBeforeReboot = lastKnownSteps - dailyBaseline;
            totalStepsBeforeReboot += stepsBeforeReboot;
            editor.putInt(KEY_TOTAL_STEPS_BEFORE_REBOOT, totalStepsBeforeReboot);

            // Set new baseline after reboot
            dailyBaseline = totalStepsSinceReboot;
            editor.putInt(KEY_DAILY_BASELINE, dailyBaseline);
        }

        // First run initialization
        if (dailyBaseline == -1) {
            Log.d(TAG, "First run, setting initial baseline");
            dailyBaseline = totalStepsSinceReboot;
            editor.putString(KEY_LAST_DATE, currentDate);
            editor.putInt(KEY_DAILY_BASELINE, dailyBaseline);
        }

        // Calculate today's steps
        int stepsSinceBaseline = totalStepsSinceReboot - dailyBaseline;
        stepsToday = stepsSinceBaseline + (currentDate.equals(lastDate) ? 0 : totalStepsBeforeReboot);

        // Ensure steps are never negative
        if (stepsToday < 0)
            stepsToday = 0;

        // Save current state
        editor.putInt(KEY_LAST_KNOWN_STEPS, totalStepsSinceReboot);
        editor.putInt(KEY_STEPS_TODAY, stepsToday);
        editor.apply();

        Log.d(TAG, "Steps today: " + stepsToday);

        // Update notification
        updateNotification(stepsToday);

        // Broadcast step update to UI
        broadcastStepUpdate(stepsToday);
    }

    /**
     * Broadcasts the current step count to any listening activities.
     */
    private void broadcastStepUpdate(int steps) {
        Intent intent = new Intent(ACTION_STEP_UPDATE);
        intent.putExtra(EXTRA_STEPS_TODAY, steps);
        LocalBroadcastManager.getInstance(this).sendBroadcast(intent);
    }

    /**
     * Creates the notification channel required for Android O+.
     */
    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                    CHANNEL_ID,
                    "Step Counter",
                    NotificationManager.IMPORTANCE_LOW // Low importance = no sound
            );
            channel.setDescription("Tracks your daily steps");
            channel.setShowBadge(false);

            NotificationManager manager = getSystemService(NotificationManager.class);
            if (manager != null) {
                manager.createNotificationChannel(channel);
            }
        }
    }

    /**
     * Creates the persistent notification for the foreground service.
     */
    private Notification createNotification(int steps) {
        Intent notificationIntent = new Intent(this, DashboardActivity.class);
        PendingIntent pendingIntent = PendingIntent.getActivity(
                this,
                0,
                notificationIntent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        return new NotificationCompat.Builder(this, CHANNEL_ID)
                .setContentTitle("Step Counter Active")
                .setContentText(steps + " steps today")
                .setSmallIcon(R.drawable.ic_launcher_foreground) // Use your step icon
                .setContentIntent(pendingIntent)
                .setOngoing(true)
                .setSilent(true)
                .setPriority(NotificationCompat.PRIORITY_LOW)
                .setCategory(NotificationCompat.CATEGORY_SERVICE)
                .build();
    }

    /**
     * Updates the notification with the current step count.
     */
    private void updateNotification(int steps) {
        NotificationManager manager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        if (manager != null) {
            manager.notify(NOTIFICATION_ID, createNotification(steps));
        }
    }

    /**
     * Returns the current date in yyyy-MM-dd format for daily reset tracking.
     */
    private String getCurrentDate() {
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault());
        return sdf.format(new Date());
    }

    @Override
    public void onAccuracyChanged(Sensor sensor, int accuracy) {
        // Not needed for step counter
        Log.d(TAG, "Sensor accuracy changed: " + accuracy);
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        Log.d(TAG, "StepCounterService onDestroy");

        // Unregister sensor listener
        if (sensorManager != null) {
            sensorManager.unregisterListener(this);
        }
    }

    @Override
    public IBinder onBind(Intent intent) {
        // We don't provide binding
        return null;
    }

    /**
     * Helper method to get today's step count from SharedPreferences.
     * Can be called from activities to get the current step count without waiting
     * for broadcast.
     */
    public static int getStepsToday(Context context) {
        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
        return prefs.getInt(KEY_STEPS_TODAY, 0);
    }

    /**
     * Resets the step counter for debugging/testing purposes.
     */
    public static void resetStepCounter(Context context) {
        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
        prefs.edit()
                .putInt(KEY_DAILY_BASELINE, -1)
                .putInt(KEY_LAST_KNOWN_STEPS, -1)
                .putInt(KEY_STEPS_TODAY, 0)
                .putInt(KEY_TOTAL_STEPS_BEFORE_REBOOT, 0)
                .remove(KEY_LAST_DATE)
                .apply();
    }
}
