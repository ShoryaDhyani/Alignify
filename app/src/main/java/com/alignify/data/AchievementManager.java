package com.alignify.data;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Log;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.SetOptions;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Manages achievements/badges for the fitness app.
 */
public class AchievementManager {

    private static final String TAG = "AchievementManager";
    private static final String PREFS_NAME = "alignify_achievements";

    private final FirebaseFirestore db;
    private final FirebaseAuth auth;
    private final Context context;

    private static AchievementManager instance;

    /**
     * Achievement definition
     */
    public static class Achievement {
        public final String id;
        public final String title;
        public final String description;
        public final String emoji;
        public final int requiredValue;
        public final AchievementType type;

        public Achievement(String id, String title, String description, String emoji,
                int requiredValue, AchievementType type) {
            this.id = id;
            this.title = title;
            this.description = description;
            this.emoji = emoji;
            this.requiredValue = requiredValue;
            this.type = type;
        }
    }

    public enum AchievementType {
        WORKOUT_COUNT, // Total workouts completed
        STREAK_DAYS, // Consecutive workout days
        REP_COUNT, // Total reps performed
        ACCURACY_AVERAGE, // Average accuracy percentage
        STEP_COUNT, // Total steps walked
        EXERCISE_VARIETY // Different exercises performed
    }

    // All available achievements
    public static final Achievement[] ALL_ACHIEVEMENTS = {
            // Workout count achievements
            new Achievement("first_workout", "First Steps", "Complete your first workout", "🎯", 1,
                    AchievementType.WORKOUT_COUNT),
            new Achievement("workout_10", "Getting Started", "Complete 10 workouts", "💪", 10,
                    AchievementType.WORKOUT_COUNT),
            new Achievement("workout_50", "Dedicated", "Complete 50 workouts", "🏆", 50, AchievementType.WORKOUT_COUNT),
            new Achievement("workout_100", "Century Club", "Complete 100 workouts", "🌟", 100,
                    AchievementType.WORKOUT_COUNT),

            // Streak achievements
            new Achievement("streak_3", "Hat Trick", "3-day workout streak", "🔥", 3, AchievementType.STREAK_DAYS),
            new Achievement("streak_7", "Week Warrior", "7-day workout streak", "⚡", 7, AchievementType.STREAK_DAYS),
            new Achievement("streak_30", "Monthly Master", "30-day workout streak", "👑", 30,
                    AchievementType.STREAK_DAYS),

            // Rep count achievements
            new Achievement("reps_100", "Rep Rookie", "Perform 100 total reps", "🏋️", 100, AchievementType.REP_COUNT),
            new Achievement("reps_500", "Rep Machine", "Perform 500 total reps", "💥", 500, AchievementType.REP_COUNT),
            new Achievement("reps_1000", "Rep Master", "Perform 1000 total reps", "🎖️", 1000,
                    AchievementType.REP_COUNT),

            // Accuracy achievements
            new Achievement("accuracy_80", "Form Focus", "Average 80% accuracy", "🎯", 80,
                    AchievementType.ACCURACY_AVERAGE),
            new Achievement("accuracy_90", "Perfect Form", "Average 90% accuracy", "⭐", 90,
                    AchievementType.ACCURACY_AVERAGE),
            new Achievement("accuracy_95", "Master Form", "Average 95% accuracy", "🏅", 95,
                    AchievementType.ACCURACY_AVERAGE),

            // Step achievements
            new Achievement("steps_10k", "Walking Starter", "Walk 10,000 steps", "👟", 10000,
                    AchievementType.STEP_COUNT),
            new Achievement("steps_50k", "Walking Pro", "Walk 50,000 steps", "🚶", 50000, AchievementType.STEP_COUNT),
            new Achievement("steps_100k", "Walking Champion", "Walk 100,000 steps", "🏃", 100000,
                    AchievementType.STEP_COUNT)
    };

    public static synchronized AchievementManager getInstance(Context context) {
        if (instance == null) {
            instance = new AchievementManager(context.getApplicationContext());
        }
        return instance;
    }

    private AchievementManager(Context context) {
        this.context = context;
        this.db = FirebaseFirestore.getInstance();
        this.auth = FirebaseAuth.getInstance();
    }

    /**
     * Check if an achievement is unlocked.
     */
    public boolean isUnlocked(String achievementId) {
        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        return prefs.getBoolean(achievementId, false);
    }

    /**
     * Get list of all unlocked achievements.
     */
    public List<Achievement> getUnlockedAchievements() {
        List<Achievement> unlocked = new ArrayList<>();
        for (Achievement achievement : ALL_ACHIEVEMENTS) {
            if (isUnlocked(achievement.id)) {
                unlocked.add(achievement);
            }
        }
        return unlocked;
    }

    /**
     * Get count of unlocked achievements.
     */
    public int getUnlockedCount() {
        int count = 0;
        for (Achievement achievement : ALL_ACHIEVEMENTS) {
            if (isUnlocked(achievement.id)) {
                count++;
            }
        }
        return count;
    }

    /**
     * Check all achievements and return newly unlocked ones.
     */
    public List<Achievement> checkAchievements(int totalWorkouts, int currentStreak,
            int totalReps, int averageAccuracy, int totalSteps) {
        List<Achievement> newlyUnlocked = new ArrayList<>();

        for (Achievement achievement : ALL_ACHIEVEMENTS) {
            if (isUnlocked(achievement.id)) {
                continue; // Already unlocked
            }

            boolean shouldUnlock = false;

            switch (achievement.type) {
                case WORKOUT_COUNT:
                    shouldUnlock = totalWorkouts >= achievement.requiredValue;
                    break;
                case STREAK_DAYS:
                    shouldUnlock = currentStreak >= achievement.requiredValue;
                    break;
                case REP_COUNT:
                    shouldUnlock = totalReps >= achievement.requiredValue;
                    break;
                case ACCURACY_AVERAGE:
                    shouldUnlock = averageAccuracy >= achievement.requiredValue;
                    break;
                case STEP_COUNT:
                    shouldUnlock = totalSteps >= achievement.requiredValue;
                    break;
                case EXERCISE_VARIETY:
                    // Not checked here - requires separate tracking
                    break;
            }

            if (shouldUnlock) {
                unlockAchievement(achievement);
                newlyUnlocked.add(achievement);
            }
        }

        return newlyUnlocked;
    }

    /**
     * Unlock an achievement.
     */
    private void unlockAchievement(Achievement achievement) {
        // Save locally
        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        prefs.edit().putBoolean(achievement.id, true).apply();

        // Sync to Firestore
        syncAchievementToFirestore(achievement.id);

        Log.d(TAG, "Achievement unlocked: " + achievement.title);
    }

    private void syncAchievementToFirestore(String achievementId) {
        FirebaseUser user = auth.getCurrentUser();
        if (user == null)
            return;

        Map<String, Object> data = new HashMap<>();
        data.put("achievements." + achievementId, true);
        data.put("achievementsUpdatedAt", System.currentTimeMillis());

        db.collection("users")
                .document(user.getUid())
                .set(data, SetOptions.merge())
                .addOnSuccessListener(aVoid -> Log.d(TAG, "Achievement synced: " + achievementId))
                .addOnFailureListener(e -> Log.e(TAG, "Error syncing achievement", e));
    }

    /**
     * Load achievements from Firestore.
     */
    public void loadAchievementsFromFirestore(OnLoadListener listener) {
        FirebaseUser user = auth.getCurrentUser();
        if (user == null) {
            if (listener != null)
                listener.onLoaded();
            return;
        }

        db.collection("users")
                .document(user.getUid())
                .get()
                .addOnSuccessListener(doc -> {
                    if (doc.exists() && doc.contains("achievements")) {
                        Map<String, Object> achievements = (Map<String, Object>) doc.get("achievements");
                        if (achievements != null) {
                            SharedPreferences.Editor editor = context
                                    .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                                    .edit();

                            for (Map.Entry<String, Object> entry : achievements.entrySet()) {
                                if (entry.getValue() instanceof Boolean) {
                                    editor.putBoolean(entry.getKey(), (Boolean) entry.getValue());
                                }
                            }
                            editor.apply();
                        }
                    }
                    if (listener != null)
                        listener.onLoaded();
                })
                .addOnFailureListener(e -> {
                    Log.e(TAG, "Error loading achievements", e);
                    if (listener != null)
                        listener.onLoaded();
                });
    }

    public interface OnLoadListener {
        void onLoaded();
    }
}
