package com.alignify.service;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
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

import androidx.annotation.Nullable;
import androidx.core.app.NotificationCompat;

import com.alignify.R;
import com.alignify.data.sleep.AppDatabase;
import com.alignify.data.sleep.SleepSession;
import com.alignify.data.sleep.SleepSessionDao;

import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.Locale;
import java.util.concurrent.Executors;

/**
 * ForegroundService that detects sleep/wake transitions using the accelerometer.
 *
 * Detection logic:
 * - Tracks rolling average of accelerometer magnitude over 5-minute windows
 * - Stillness < 0.3 m/s² for > 15 minutes → mark as "asleep"
 * - Movement > 1.5 m/s² for > 5 minutes → mark as "awake"
 * - Only active between 8 PM and 12 PM (noon) next day
 * - Quality score based on duration vs goal and interruption count
 *
 * Follows the same ForegroundService pattern as StepCounterService.
 */
public class SleepTrackingService extends Service implements SensorEventListener {

    private static final String TAG = "SleepTrackingService";

    // Notification
    private static final String CHANNEL_ID = "sleep_tracking";
    private static final String CHANNEL_NAME = "Sleep Tracking";
    private static final int NOTIFICATION_ID = 2002;

    // Detection thresholds
    private static final float STILLNESS_THRESHOLD = 0.3f;  // m/s² deviation from gravity
    private static final float WAKE_THRESHOLD = 1.5f;       // m/s² deviation from gravity
    private static final long SLEEP_CONFIRM_MS = 15 * 60 * 1000;  // 15 minutes
    private static final long WAKE_CONFIRM_MS = 5 * 60 * 1000;    // 5 minutes
    private static final long WINDOW_MS = 5 * 60 * 1000;          // 5-minute averaging window

    // Sleep time window (8 PM to 12 PM noon)
    private static final int SLEEP_WINDOW_START_HOUR = 20;  // 8 PM
    private static final int SLEEP_WINDOW_END_HOUR = 12;    // 12 PM noon

    // SharedPreferences
    private static final String PREFS_NAME = "SleepTrackingPrefs";
    private static final String KEY_IS_TRACKING = "is_tracking";
    private static final String KEY_SLEEP_START = "sleep_start_millis";
    private static final String KEY_LAST_MOVEMENT = "last_movement_time";
    private static final String KEY_INTERRUPTIONS = "interruption_count";
    private static final String KEY_IS_ASLEEP = "is_asleep";
    private static final String KEY_STILLNESS_START = "stillness_start_millis";

    // State
    private SensorManager sensorManager;
    private Sensor accelerometer;
    private SharedPreferences prefs;

    // Movement tracking
    private float movementAccumulator = 0f;
    private int sampleCount = 0;
    private long windowStartTime = 0;

    // Sleep state
    private boolean isAsleep = false;
    private long sleepStartMillis = 0;
    private long stillnessStartMillis = 0;
    private long lastMovementTime = 0;
    private int interruptionCount = 0;

    @Override
    public void onCreate() {
        super.onCreate();
        Log.d(TAG, "SleepTrackingService created");

        prefs = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        sensorManager = (SensorManager) getSystemService(Context.SENSOR_SERVICE);

        if (sensorManager != null) {
            accelerometer = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);
        }

        createNotificationChannel();
        restoreState();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        Log.d(TAG, "SleepTrackingService started");

        Notification notification = buildNotification("Sleep tracking active");
        startForeground(NOTIFICATION_ID, notification);

        if (accelerometer != null) {
            // Use SENSOR_DELAY_NORMAL (~200ms) to conserve battery overnight
            sensorManager.registerListener(this, accelerometer, SensorManager.SENSOR_DELAY_NORMAL);
        } else {
            Log.e(TAG, "No accelerometer available — cannot track sleep");
            stopSelf();
            return START_NOT_STICKY;
        }

        prefs.edit().putBoolean(KEY_IS_TRACKING, true).apply();
        windowStartTime = System.currentTimeMillis();

        return START_STICKY;
    }

    @Override
    public void onDestroy() {
        Log.d(TAG, "SleepTrackingService destroyed");

        if (sensorManager != null) {
            sensorManager.unregisterListener(this);
        }

        // If currently asleep, finalize the session
        if (isAsleep && sleepStartMillis > 0) {
            finalizeSleepSession(System.currentTimeMillis());
        }

        prefs.edit().putBoolean(KEY_IS_TRACKING, false).apply();
        super.onDestroy();
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    // ============ Sensor Callback ============

    @Override
    public void onSensorChanged(SensorEvent event) {
        if (event.sensor.getType() != Sensor.TYPE_ACCELEROMETER) return;

        // Check if we're in the valid sleep detection window
        if (!isInSleepWindow()) return;

        // Calculate movement magnitude (deviation from gravity ~9.81)
        float x = event.values[0];
        float y = event.values[1];
        float z = event.values[2];
        float magnitude = (float) Math.sqrt(x * x + y * y + z * z);
        float deviation = Math.abs(magnitude - SensorManager.GRAVITY_EARTH);

        movementAccumulator += deviation;
        sampleCount++;

        long now = System.currentTimeMillis();

        // Process in 5-minute windows to save battery
        if (now - windowStartTime >= WINDOW_MS && sampleCount > 0) {
            float avgMovement = movementAccumulator / sampleCount;
            processMovementWindow(avgMovement, now);

            // Reset window
            movementAccumulator = 0f;
            sampleCount = 0;
            windowStartTime = now;
        }
    }

    @Override
    public void onAccuracyChanged(Sensor sensor, int accuracy) {
        // Not needed
    }

    // ============ Sleep Detection Logic ============

    private void processMovementWindow(float avgMovement, long now) {
        if (!isAsleep) {
            // Looking for sleep onset
            if (avgMovement < STILLNESS_THRESHOLD) {
                if (stillnessStartMillis == 0) {
                    stillnessStartMillis = now;
                    saveState();
                }

                long stillnessDuration = now - stillnessStartMillis;
                if (stillnessDuration >= SLEEP_CONFIRM_MS) {
                    // Confirmed: user fell asleep
                    isAsleep = true;
                    sleepStartMillis = stillnessStartMillis; // sleep started when stillness began
                    interruptionCount = 0;
                    lastMovementTime = 0;

                    updateNotification("Sleeping... 😴");
                    saveState();
                    Log.d(TAG, "Sleep detected at " + new Date(sleepStartMillis));
                }
            } else {
                // Movement detected — reset stillness counter
                stillnessStartMillis = 0;
                saveState();
            }
        } else {
            // Currently asleep — looking for wake
            if (avgMovement > WAKE_THRESHOLD) {
                if (lastMovementTime == 0) {
                    lastMovementTime = now;
                }

                long movementDuration = now - lastMovementTime;
                if (movementDuration >= WAKE_CONFIRM_MS) {
                    // Confirmed: user woke up
                    finalizeSleepSession(lastMovementTime);
                    return;
                }
            } else if (avgMovement > STILLNESS_THRESHOLD) {
                // Brief movement — count as interruption
                if (lastMovementTime == 0) {
                    interruptionCount++;
                    saveState();
                }
                lastMovementTime = 0;
            } else {
                // Still sleeping peacefully
                lastMovementTime = 0;
            }
        }
    }

    private void finalizeSleepSession(long wakeTimeMillis) {
        if (sleepStartMillis <= 0) return;

        int durationMinutes = (int) ((wakeTimeMillis - sleepStartMillis) / 60000);

        // Skip very short "sessions" (< 30 minutes)
        if (durationMinutes < 30) {
            Log.d(TAG, "Sleep session too short (" + durationMinutes + " min), discarding");
            resetSleepState();
            return;
        }

        // Calculate quality score
        float sleepGoalHours = prefs.getFloat("sleep_goal", 8.0f);
        float durationHours = durationMinutes / 60f;
        int qualityScore = calculateQualityScore(durationHours, sleepGoalHours, interruptionCount);

        // Determine the date (use sleep start date)
        String date = new SimpleDateFormat("yyyy-MM-dd", Locale.US).format(new Date(sleepStartMillis));

        SleepSession session = new SleepSession(
                sleepStartMillis, wakeTimeMillis, durationMinutes, qualityScore, date
        );

        // Save to Room DB on background thread
        Executors.newSingleThreadExecutor().execute(() -> {
            try {
                SleepSessionDao dao = AppDatabase.getInstance(this).sleepSessionDao();

                // Check if a session already exists for this date
                SleepSession existing = dao.getByDate(date);
                if (existing != null) {
                    // Update if new session is longer
                    if (durationMinutes > existing.durationMinutes) {
                        session.id = existing.id;
                        dao.update(session);
                        Log.d(TAG, "Updated sleep session for " + date);
                    }
                } else {
                    dao.insert(session);
                    Log.d(TAG, "Saved sleep session: " + date + " — " +
                            durationMinutes + " min, quality=" + qualityScore);
                }
            } catch (Exception e) {
                Log.e(TAG, "Error saving sleep session", e);
            }
        });

        updateNotification("Sleep recorded: " + session.getFormattedDuration());
        resetSleepState();
    }

    private int calculateQualityScore(float durationHours, float goalHours, int interruptions) {
        // Base score from duration (0-80 points)
        float durationRatio = Math.min(1.0f, durationHours / goalHours);
        int durationScore = (int) (durationRatio * 80);

        // Penalty for interruptions (max -20 points)
        int interruptionPenalty = Math.min(20, interruptions * 5);

        return Math.max(0, Math.min(100, durationScore + 20 - interruptionPenalty));
    }

    // ============ Time Window ============

    private boolean isInSleepWindow() {
        Calendar cal = Calendar.getInstance();
        int hour = cal.get(Calendar.HOUR_OF_DAY);
        // Valid window: 8 PM (20) to 12 PM noon (12) next day
        return hour >= SLEEP_WINDOW_START_HOUR || hour < SLEEP_WINDOW_END_HOUR;
    }

    // ============ State Persistence ============

    private void saveState() {
        prefs.edit()
                .putBoolean(KEY_IS_ASLEEP, isAsleep)
                .putLong(KEY_SLEEP_START, sleepStartMillis)
                .putLong(KEY_LAST_MOVEMENT, lastMovementTime)
                .putInt(KEY_INTERRUPTIONS, interruptionCount)
                .putLong(KEY_STILLNESS_START, stillnessStartMillis)
                .apply();
    }

    private void restoreState() {
        isAsleep = prefs.getBoolean(KEY_IS_ASLEEP, false);
        sleepStartMillis = prefs.getLong(KEY_SLEEP_START, 0);
        lastMovementTime = prefs.getLong(KEY_LAST_MOVEMENT, 0);
        interruptionCount = prefs.getInt(KEY_INTERRUPTIONS, 0);
        stillnessStartMillis = prefs.getLong(KEY_STILLNESS_START, 0);
    }

    private void resetSleepState() {
        isAsleep = false;
        sleepStartMillis = 0;
        stillnessStartMillis = 0;
        lastMovementTime = 0;
        interruptionCount = 0;
        saveState();
    }

    // ============ Notification ============

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                    CHANNEL_ID, CHANNEL_NAME, NotificationManager.IMPORTANCE_LOW);
            channel.setDescription("Tracks sleep using accelerometer");
            channel.setSound(null, null);
            channel.enableVibration(false);

            NotificationManager nm = getSystemService(NotificationManager.class);
            if (nm != null) {
                nm.createNotificationChannel(channel);
            }
        }
    }

    private Notification buildNotification(String text) {
        return new NotificationCompat.Builder(this, CHANNEL_ID)
                .setContentTitle("Alignify Sleep Tracker")
                .setContentText(text)
                .setSmallIcon(R.drawable.ic_sleep)
                .setOngoing(true)
                .setSilent(true)
                .setPriority(NotificationCompat.PRIORITY_LOW)
                .build();
    }

    private void updateNotification(String text) {
        NotificationManager nm = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        if (nm != null) {
            nm.notify(NOTIFICATION_ID, buildNotification(text));
        }
    }
}
