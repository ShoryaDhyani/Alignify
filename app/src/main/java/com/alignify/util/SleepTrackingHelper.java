package com.alignify.util;

import android.content.Context;
import android.content.Intent;
import android.hardware.Sensor;
import android.hardware.SensorManager;
import android.os.Build;

import com.alignify.service.SleepTrackingService;

/**
 * Helper class for managing SleepTrackingService lifecycle.
 * Provides start/stop/enable/check methods so Activities and Fragments
 * don't need to manage service intents directly.
 *
 * Modeled after StepCounterHelper.
 */
public class SleepTrackingHelper {

    private static final String PREFS_NAME = "SleepTrackingPrefs";
    private static final String KEY_ENABLED = "sleep_tracking_enabled";

    /**
     * Check if sleep tracking is currently enabled.
     */
    public static boolean isSleepTrackingEnabled(Context context) {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .getBoolean(KEY_ENABLED, false);
    }

    /**
     * Set the sleep tracking enabled preference.
     */
    public static void setSleepTrackingEnabled(Context context, boolean enabled) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .edit().putBoolean(KEY_ENABLED, enabled).apply();
    }

    /**
     * Start the sleep tracking foreground service.
     */
    public static void startSleepTracking(Context context) {
        Intent intent = new Intent(context, SleepTrackingService.class);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            context.startForegroundService(intent);
        } else {
            context.startService(intent);
        }
        setSleepTrackingEnabled(context, true);
    }

    /**
     * Stop the sleep tracking foreground service.
     */
    public static void stopSleepTracking(Context context) {
        context.stopService(new Intent(context, SleepTrackingService.class));
        setSleepTrackingEnabled(context, false);
    }

    /**
     * Check if the device has an accelerometer sensor available.
     */
    public static boolean isAccelerometerAvailable(Context context) {
        SensorManager sm = (SensorManager) context.getSystemService(Context.SENSOR_SERVICE);
        return sm != null && sm.getDefaultSensor(Sensor.TYPE_ACCELEROMETER) != null;
    }
}
