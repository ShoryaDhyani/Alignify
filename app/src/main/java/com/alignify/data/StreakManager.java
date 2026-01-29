package com.alignify.data;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Log;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.SetOptions;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

/**
 * Manages user streaks, achievements, and daily statistics.
 */
public class StreakManager {

    private static final String TAG = "StreakManager";
    private static final String PREFS_NAME = "alignify_streak";
    private static final String KEY_LAST_WORKOUT_DATE = "last_workout_date";
    private static final String KEY_CURRENT_STREAK = "current_streak";
    private static final String KEY_LONGEST_STREAK = "longest_streak";

    private final FirebaseFirestore db;
    private final FirebaseAuth auth;
    private final Context context;

    private static StreakManager instance;

    public static synchronized StreakManager getInstance(Context context) {
        if (instance == null) {
            instance = new StreakManager(context.getApplicationContext());
        }
        return instance;
    }

    private StreakManager(Context context) {
        this.context = context;
        this.db = FirebaseFirestore.getInstance();
        this.auth = FirebaseAuth.getInstance();
    }

    /**
     * Get current streak count from local cache.
     */
    public int getCurrentStreak() {
        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        return prefs.getInt(KEY_CURRENT_STREAK, 0);
    }

    /**
     * Get longest streak ever achieved.
     */
    public int getLongestStreak() {
        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        return prefs.getInt(KEY_LONGEST_STREAK, 0);
    }

    /**
     * Record a workout completion and update streak.
     */
    public void recordWorkoutCompletion(OnStreakUpdateListener listener) {
        String today = getTodayDateString();
        String lastWorkoutDate = getLastWorkoutDate();

        int currentStreak = getCurrentStreak();
        int longestStreak = getLongestStreak();

        if (today.equals(lastWorkoutDate)) {
            // Already worked out today, streak unchanged
            if (listener != null) {
                listener.onStreakUpdated(currentStreak, false);
            }
            return;
        }

        String yesterday = getYesterdayDateString();

        if (lastWorkoutDate.equals(yesterday)) {
            // Consecutive day! Increment streak
            currentStreak++;
        } else if (lastWorkoutDate.isEmpty()) {
            // First workout ever
            currentStreak = 1;
        } else {
            // Streak broken, start fresh
            currentStreak = 1;
        }

        // Update longest streak if needed
        if (currentStreak > longestStreak) {
            longestStreak = currentStreak;
        }

        // Save locally
        saveStreakData(today, currentStreak, longestStreak);

        // Sync to Firestore
        syncStreakToFirestore(currentStreak, longestStreak);

        if (listener != null) {
            listener.onStreakUpdated(currentStreak, currentStreak > 1);
        }
    }

    /**
     * Check and update streak status (call on app start).
     * This handles streak reset if user missed a day.
     */
    public void checkStreakStatus(OnStreakUpdateListener listener) {
        String today = getTodayDateString();
        String lastWorkoutDate = getLastWorkoutDate();
        String yesterday = getYesterdayDateString();

        int currentStreak = getCurrentStreak();

        // If last workout was not today or yesterday, streak is broken
        if (!lastWorkoutDate.isEmpty() &&
                !lastWorkoutDate.equals(today) &&
                !lastWorkoutDate.equals(yesterday)) {

            // Reset streak
            SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
            prefs.edit().putInt(KEY_CURRENT_STREAK, 0).apply();
            currentStreak = 0;
        }

        if (listener != null) {
            listener.onStreakUpdated(currentStreak, false);
        }
    }

    /**
     * Load streak data from Firestore (for sync across devices).
     */
    public void loadStreakFromFirestore(OnStreakUpdateListener listener) {
        FirebaseUser user = auth.getCurrentUser();
        if (user == null) {
            if (listener != null) {
                listener.onStreakUpdated(0, false);
            }
            return;
        }

        db.collection("users")
                .document(user.getUid())
                .get()
                .addOnSuccessListener(doc -> {
                    if (doc.exists()) {
                        Long streak = doc.getLong("currentStreak");
                        Long longest = doc.getLong("longestStreak");
                        String lastDate = doc.getString("lastWorkoutDate");

                        int currentStreak = streak != null ? streak.intValue() : 0;
                        int longestStreak = longest != null ? longest.intValue() : 0;
                        String lastWorkout = lastDate != null ? lastDate : "";

                        // Save locally
                        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
                        prefs.edit()
                                .putInt(KEY_CURRENT_STREAK, currentStreak)
                                .putInt(KEY_LONGEST_STREAK, longestStreak)
                                .putString(KEY_LAST_WORKOUT_DATE, lastWorkout)
                                .apply();

                        // Check if streak needs reset
                        checkStreakStatus(listener);
                    } else {
                        if (listener != null) {
                            listener.onStreakUpdated(0, false);
                        }
                    }
                })
                .addOnFailureListener(e -> {
                    Log.e(TAG, "Error loading streak", e);
                    // Use local data
                    if (listener != null) {
                        listener.onStreakUpdated(getCurrentStreak(), false);
                    }
                });
    }

    private void saveStreakData(String date, int currentStreak, int longestStreak) {
        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        prefs.edit()
                .putString(KEY_LAST_WORKOUT_DATE, date)
                .putInt(KEY_CURRENT_STREAK, currentStreak)
                .putInt(KEY_LONGEST_STREAK, longestStreak)
                .apply();
    }

    private void syncStreakToFirestore(int currentStreak, int longestStreak) {
        FirebaseUser user = auth.getCurrentUser();
        if (user == null)
            return;

        Map<String, Object> streakData = new HashMap<>();
        streakData.put("currentStreak", currentStreak);
        streakData.put("longestStreak", longestStreak);
        streakData.put("lastWorkoutDate", getTodayDateString());
        streakData.put("streakUpdatedAt", System.currentTimeMillis());

        db.collection("users")
                .document(user.getUid())
                .set(streakData, SetOptions.merge())
                .addOnSuccessListener(aVoid -> Log.d(TAG, "Streak synced to Firestore"))
                .addOnFailureListener(e -> Log.e(TAG, "Error syncing streak", e));
    }

    private String getLastWorkoutDate() {
        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        return prefs.getString(KEY_LAST_WORKOUT_DATE, "");
    }

    private String getTodayDateString() {
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd", Locale.US);
        return sdf.format(new Date());
    }

    private String getYesterdayDateString() {
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd", Locale.US);
        long yesterday = System.currentTimeMillis() - (24 * 60 * 60 * 1000L);
        return sdf.format(new Date(yesterday));
    }

    public interface OnStreakUpdateListener {
        void onStreakUpdated(int currentStreak, boolean isNewStreak);
    }
}
