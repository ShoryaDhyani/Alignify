package com.alignify.receiver;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Build;
import android.util.Log;

import com.alignify.service.StepCounterService;

/**
 * BroadcastReceiver that starts the StepCounterService after device boot.
 * 
 * This ensures step tracking continues after the device restarts.
 * The step count baseline is preserved in SharedPreferences and the service
 * handles the reboot detection logic.
 * 
 * Required permissions:
 * - android.permission.RECEIVE_BOOT_COMPLETED
 */
public class BootReceiver extends BroadcastReceiver {

    private static final String TAG = "BootReceiver";

    @Override
    public void onReceive(Context context, Intent intent) {
        if (intent == null)
            return;

        String action = intent.getAction();

        if (Intent.ACTION_BOOT_COMPLETED.equals(action) ||
                Intent.ACTION_LOCKED_BOOT_COMPLETED.equals(action) ||
                "android.intent.action.QUICKBOOT_POWERON".equals(action)) { // For HTC devices

            Log.d(TAG, "Device boot completed, starting StepCounterService");

            // Check if step tracking was enabled before boot
            SharedPreferences prefs = context.getSharedPreferences("AlignifyPrefs", Context.MODE_PRIVATE);
            boolean stepTrackingEnabled = prefs.getBoolean("step_tracking_enabled", false);

            if (stepTrackingEnabled) {
                startStepCounterService(context);
            } else {
                Log.d(TAG, "Step tracking not enabled, skipping service start");
            }
        }
    }

    /**
     * Starts the StepCounterService as a foreground service.
     */
    private void startStepCounterService(Context context) {
        Intent serviceIntent = new Intent(context, StepCounterService.class);

        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                // For Android O+, must use startForegroundService
                context.startForegroundService(serviceIntent);
            } else {
                context.startService(serviceIntent);
            }
            Log.d(TAG, "StepCounterService started successfully");
        } catch (Exception e) {
            Log.e(TAG, "Failed to start StepCounterService", e);
        }
    }
}
