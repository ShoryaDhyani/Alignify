package com.alignify.receiver;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Build;
import android.os.SystemClock;
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
    private static final String ACTION_RETRY_SERVICE_START = "com.alignify.action.RETRY_SERVICE_START";
    private static final long RETRY_DELAY_MS = 10_000;

    @Override
    public void onReceive(Context context, Intent intent) {
        if (intent == null)
            return;

        String action = intent.getAction();

        if (ACTION_RETRY_SERVICE_START.equals(action)) {
            Log.d(TAG, "Retrying StepCounterService start");
            startStepCounterService(context, false);
            return;
        }

        if (Intent.ACTION_BOOT_COMPLETED.equals(action) ||
                Intent.ACTION_LOCKED_BOOT_COMPLETED.equals(action) ||
                "android.intent.action.QUICKBOOT_POWERON".equals(action)) {

            // Deduplicate: only process once per boot cycle
            SharedPreferences prefs = context.getSharedPreferences("AlignifyPrefs", Context.MODE_PRIVATE);
            long lastBootHandled = prefs.getLong("last_boot_handled", 0);
            long bootTime = System.currentTimeMillis() - SystemClock.elapsedRealtime();
            if (Math.abs(bootTime - lastBootHandled) < 60_000) {
                Log.d(TAG, "Boot event already handled, skipping duplicate");
                return;
            }
            prefs.edit().putLong("last_boot_handled", bootTime).apply();

            Log.d(TAG, "Device boot completed, starting StepCounterService");

            boolean stepTrackingEnabled = prefs.getBoolean("step_tracking_enabled", false);

            if (stepTrackingEnabled) {
                startStepCounterService(context, true);
            } else {
                Log.d(TAG, "Step tracking not enabled, skipping service start");
            }
        }
    }

    private void startStepCounterService(Context context, boolean allowRetry) {
        Intent serviceIntent = new Intent(context, StepCounterService.class);

        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(serviceIntent);
            } else {
                context.startService(serviceIntent);
            }
            Log.d(TAG, "StepCounterService started successfully");
        } catch (Exception e) {
            Log.e(TAG, "Failed to start StepCounterService", e);

            // On Android 12+, background FG service start may be blocked; schedule a retry
            if (allowRetry && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                scheduleRetry(context);
            }
        }
    }

    private void scheduleRetry(Context context) {
        Log.d(TAG, "Scheduling retry for StepCounterService start in " + RETRY_DELAY_MS + "ms");
        Intent retryIntent = new Intent(context, BootReceiver.class);
        retryIntent.setAction(ACTION_RETRY_SERVICE_START);

        PendingIntent pendingIntent = PendingIntent.getBroadcast(
                context, 0, retryIntent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        AlarmManager alarmManager = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
        if (alarmManager != null) {
            alarmManager.set(AlarmManager.ELAPSED_REALTIME_WAKEUP,
                    SystemClock.elapsedRealtime() + RETRY_DELAY_MS, pendingIntent);
        }
    }
}
