package com.alignify.util;

import android.Manifest;
import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.hardware.Sensor;
import android.hardware.SensorManager;
import android.os.Build;
import android.util.Log;

import androidx.activity.result.ActivityResultLauncher;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import com.alignify.service.StepCounterService;

import java.util.ArrayList;
import java.util.List;

/**
 * Utility class for handling step counter permissions and service management.
 * 
 * Required permissions for step tracking:
 * - ACTIVITY_RECOGNITION (Android 10+): Required to access step counter sensor
 * - POST_NOTIFICATIONS (Android 13+): Required to show foreground service
 * notification
 * - FOREGROUND_SERVICE_HEALTH (Android 14+): Required for health-related
 * foreground services
 */
public class StepCounterHelper {

    private static final String TAG = "StepCounterHelper";

    public static final int PERMISSION_REQUEST_CODE = 1001;

    /**
     * Checks if the device has a step counter sensor.
     */
    public static boolean isStepCounterAvailable(Context context) {
        SensorManager sensorManager = (SensorManager) context.getSystemService(Context.SENSOR_SERVICE);
        Sensor stepSensor = sensorManager.getDefaultSensor(Sensor.TYPE_STEP_COUNTER);
        return stepSensor != null;
    }

    /**
     * Checks if all required permissions are granted.
     */
    public static boolean hasAllPermissions(Context context) {
        // Check ACTIVITY_RECOGNITION for Android 10+
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            if (ContextCompat.checkSelfPermission(context,
                    Manifest.permission.ACTIVITY_RECOGNITION) != PackageManager.PERMISSION_GRANTED) {
                return false;
            }
        }

        // Check POST_NOTIFICATIONS for Android 13+
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(context,
                    Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                return false;
            }
        }

        return true;
    }

    /**
     * Returns the list of permissions that need to be requested.
     */
    public static String[] getRequiredPermissions() {
        List<String> permissions = new ArrayList<>();

        // ACTIVITY_RECOGNITION is required for Android 10+
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            permissions.add(Manifest.permission.ACTIVITY_RECOGNITION);
        }

        // POST_NOTIFICATIONS is required for Android 13+
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            permissions.add(Manifest.permission.POST_NOTIFICATIONS);
        }

        return permissions.toArray(new String[0]);
    }

    /**
     * Requests the required permissions for step tracking.
     * 
     * @param activity The activity requesting permissions
     * @return true if permissions were requested, false if already granted
     */
    public static boolean requestPermissions(Activity activity) {
        String[] requiredPermissions = getRequiredPermissions();
        List<String> permissionsToRequest = new ArrayList<>();

        for (String permission : requiredPermissions) {
            if (ContextCompat.checkSelfPermission(activity, permission) != PackageManager.PERMISSION_GRANTED) {
                permissionsToRequest.add(permission);
            }
        }

        if (permissionsToRequest.isEmpty()) {
            Log.d(TAG, "All permissions already granted");
            return false;
        }

        Log.d(TAG, "Requesting permissions: " + permissionsToRequest);
        ActivityCompat.requestPermissions(
                activity,
                permissionsToRequest.toArray(new String[0]),
                PERMISSION_REQUEST_CODE);
        return true;
    }

    /**
     * Requests permissions using ActivityResultLauncher (recommended for new code).
     */
    public static void requestPermissions(ActivityResultLauncher<String[]> launcher) {
        String[] permissions = getRequiredPermissions();
        if (permissions.length > 0) {
            launcher.launch(permissions);
        }
    }

    /**
     * Starts the step counter service.
     * 
     * @param context        The context
     * @param savePreference If true, saves the enabled state to SharedPreferences
     *                       for boot receiver
     */
    public static void startStepTracking(Context context, boolean savePreference) {
        if (!hasAllPermissions(context)) {
            Log.w(TAG, "Cannot start step tracking: permissions not granted");
            return;
        }

        if (!isStepCounterAvailable(context)) {
            Log.w(TAG, "Cannot start step tracking: sensor not available");
            return;
        }

        // Save preference for boot receiver
        if (savePreference) {
            context.getSharedPreferences("AlignifyPrefs", Context.MODE_PRIVATE)
                    .edit()
                    .putBoolean("step_tracking_enabled", true)
                    .apply();
        }

        // Start the foreground service
        Intent serviceIntent = new Intent(context, StepCounterService.class);

        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(serviceIntent);
            } else {
                context.startService(serviceIntent);
            }
            Log.d(TAG, "Step tracking service started");
        } catch (Exception e) {
            Log.e(TAG, "Failed to start step tracking service", e);
        }
    }

    /**
     * Stops the step counter service.
     */
    public static void stopStepTracking(Context context) {
        // Update preference
        context.getSharedPreferences("AlignifyPrefs", Context.MODE_PRIVATE)
                .edit()
                .putBoolean("step_tracking_enabled", false)
                .apply();

        // Stop the service
        Intent serviceIntent = new Intent(context, StepCounterService.class);
        context.stopService(serviceIntent);
        Log.d(TAG, "Step tracking service stopped");
    }

    /**
     * Gets the current step count for today.
     */
    public static int getStepsToday(Context context) {
        return StepCounterService.getStepsToday(context);
    }

    /**
     * Checks if the step tracking service is currently running.
     */
    public static boolean isStepTrackingEnabled(Context context) {
        return context.getSharedPreferences("AlignifyPrefs", Context.MODE_PRIVATE)
                .getBoolean("step_tracking_enabled", false);
    }
}
