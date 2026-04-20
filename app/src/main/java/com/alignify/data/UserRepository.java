package com.alignify.data;

import android.content.Context;
import android.util.Log;

import com.alignify.data.sleep.AppDatabase;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Repository class for handling user data operations with local Room SQLite database.
 * Replaces the previous Firebase Firestore implementation.
 */
public class UserRepository {

    private static final String TAG = "UserRepository";

    private final AppDatabase db;
    private final ExecutorService executor;

    private static UserRepository instance;

    public static synchronized UserRepository getInstance(Context context) {
        if (instance == null) {
            instance = new UserRepository(context.getApplicationContext());
        }
        return instance;
    }

    /**
     * @deprecated Use getInstance(Context) instead.
     */
    @Deprecated
    public static synchronized UserRepository getInstance() {
        if (instance == null) {
            throw new IllegalStateException("UserRepository not initialized. Call getInstance(Context) first.");
        }
        return instance;
    }

    private UserRepository(Context context) {
        db = AppDatabase.getInstance(context);
        executor = Executors.newSingleThreadExecutor();
    }

    /**
     * Save user profile data to local SQLite.
     */
    public void saveUserProfile(String email, String name, float bmi, String bmiCategory,
            String activityLevel, int height, int weight, int age,
            String gender, OnCompleteListener listener) {
        executor.execute(() -> {
            try {
                UserProfileEntity profile = db.userProfileDao().getProfile();
                if (profile == null) {
                    profile = new UserProfileEntity();
                    profile.createdAt = System.currentTimeMillis();
                }
                profile.email = email;
                profile.name = name;
                profile.bmi = bmi;
                profile.bmiCategory = bmiCategory;
                profile.activityLevel = activityLevel;
                profile.height = height;
                profile.weight = weight;
                profile.age = age;
                profile.gender = gender;
                profile.profileComplete = true;
                profile.updatedAt = System.currentTimeMillis();

                db.userProfileDao().insertOrUpdate(profile);
                Log.d(TAG, "Profile saved successfully");
                if (listener != null) listener.onSuccess();
            } catch (Exception e) {
                Log.e(TAG, "Error saving profile", e);
                if (listener != null) listener.onError(e.getMessage());
            }
        });
    }

    /**
     * Save feedback settings.
     */
    public void saveFeedbackSettings(boolean voiceFeedback, boolean textFeedback) {
        executor.execute(() -> {
            try {
                db.userProfileDao().updateFeedbackSettings(voiceFeedback, textFeedback);
                Log.d(TAG, "Settings saved");
            } catch (Exception e) {
                Log.e(TAG, "Error saving settings", e);
            }
        });
    }

    /**
     * Update user's profile image URL.
     */
    public void updateProfileImageUrl(String imageUrl, OnCompleteListener listener) {
        executor.execute(() -> {
            try {
                db.userProfileDao().updateProfileImageUrl(imageUrl, System.currentTimeMillis());
                Log.d(TAG, "Profile image URL updated");
                if (listener != null) listener.onSuccess();
            } catch (Exception e) {
                Log.e(TAG, "Error updating profile image URL", e);
                if (listener != null) listener.onError(e.getMessage());
            }
        });
    }

    /**
     * Load user profile.
     */
    public void loadUserProfile(OnProfileLoadedListener listener) {
        executor.execute(() -> {
            try {
                UserProfileEntity profile = db.userProfileDao().getProfile();
                if (profile != null) {
                    Map<String, Object> data = new HashMap<>();
                    data.put("email", profile.email);
                    data.put("name", profile.name);
                    data.put("bmi", (double) profile.bmi);
                    data.put("bmiCategory", profile.bmiCategory);
                    data.put("activityLevel", profile.activityLevel);
                    data.put("height", profile.height);
                    data.put("weight", profile.weight);
                    data.put("age", profile.age);
                    data.put("gender", profile.gender);
                    data.put("profileImageUrl", profile.profileImageUrl);
                    data.put("profileComplete", profile.profileComplete);
                    if (listener != null) listener.onProfileLoaded(data);
                } else {
                    if (listener != null) listener.onProfileLoaded(null);
                }
            } catch (Exception e) {
                Log.e(TAG, "Error loading profile", e);
                if (listener != null) listener.onError(e.getMessage());
            }
        });
    }

    /**
     * Save workout session data.
     */
    public void saveWorkoutSession(String exercise, int reps, int duration,
            int errorsCount, OnCompleteListener listener) {
        executor.execute(() -> {
            try {
                WorkoutSessionEntity session = new WorkoutSessionEntity();
                session.exercise = exercise;
                session.reps = reps;
                session.duration = duration;
                session.errorsCount = errorsCount;
                session.timestamp = System.currentTimeMillis();

                long id = db.workoutSessionDao().insert(session);
                Log.d(TAG, "Workout saved: " + id);
                if (listener != null) listener.onSuccess();
            } catch (Exception e) {
                Log.e(TAG, "Error saving workout", e);
                if (listener != null) listener.onError(e.getMessage());
            }
        });
    }

    // ============ Goals Methods ============

    /**
     * Save user's fitness goals.
     */
    public void saveGoals(int stepGoal, int caloriesGoal, int activeTimeGoal,
            int waterGoal, float sleepGoal, OnCompleteListener listener) {
        executor.execute(() -> {
            try {
                // Ensure profile exists first
                UserProfileEntity profile = db.userProfileDao().getProfile();
                if (profile == null) {
                    profile = new UserProfileEntity();
                    profile.createdAt = System.currentTimeMillis();
                }
                profile.stepGoal = stepGoal;
                profile.caloriesGoal = caloriesGoal;
                profile.activeTimeGoal = activeTimeGoal;
                profile.waterGoal = waterGoal;
                profile.sleepGoal = sleepGoal;
                profile.updatedAt = System.currentTimeMillis();
                db.userProfileDao().insertOrUpdate(profile);

                Log.d(TAG, "Goals saved successfully");
                if (listener != null) listener.onSuccess();
            } catch (Exception e) {
                Log.e(TAG, "Error saving goals", e);
                if (listener != null) listener.onError(e.getMessage());
            }
        });
    }

    /**
     * Load user's fitness goals.
     */
    public void loadGoals(OnGoalsLoadedListener listener) {
        executor.execute(() -> {
            try {
                UserProfileEntity profile = db.userProfileDao().getProfile();
                if (profile != null) {
                    Map<String, Object> goals = new HashMap<>();
                    if (profile.stepGoal > 0) goals.put("stepGoal", profile.stepGoal);
                    if (profile.caloriesGoal > 0) goals.put("caloriesGoal", profile.caloriesGoal);
                    if (profile.activeTimeGoal > 0) goals.put("activeTimeGoal", profile.activeTimeGoal);
                    if (profile.waterGoal > 0) goals.put("waterGoal", profile.waterGoal);
                    if (profile.sleepGoal > 0) goals.put("sleepGoal", profile.sleepGoal);
                    if (listener != null) listener.onGoalsLoaded(goals.isEmpty() ? null : goals);
                } else {
                    if (listener != null) listener.onGoalsLoaded(null);
                }
            } catch (Exception e) {
                Log.e(TAG, "Error loading goals", e);
                if (listener != null) listener.onGoalsLoaded(null);
            }
        });
    }

    // ============ Daily Activity Methods ============

    /**
     * Save or update daily activity data (upsert).
     */
    public void saveDailyActivity(DailyActivity activity, OnCompleteListener listener) {
        executor.execute(() -> {
            try {
                DailyActivityEntity entity = DailyActivityEntity.fromDailyActivity(activity);
                db.dailyActivityDao().insertOrUpdate(entity);
                Log.d(TAG, "Daily activity saved: " + activity.getDate());
                if (listener != null) listener.onSuccess();
            } catch (Exception e) {
                Log.e(TAG, "Error saving daily activity", e);
                if (listener != null) listener.onError(e.getMessage());
            }
        });
    }

    /**
     * Get today's activity.
     */
    public void getTodayActivity(OnDailyActivityListener listener) {
        getDailyActivity(DailyActivity.todayKey(), listener);
    }

    /**
     * Get activity for a specific date.
     */
    public void getDailyActivity(String date, OnDailyActivityListener listener) {
        executor.execute(() -> {
            try {
                DailyActivityEntity entity = db.dailyActivityDao().getByDate(date);
                if (entity != null) {
                    if (listener != null) listener.onActivityLoaded(entity.toDailyActivity());
                } else {
                    if (listener != null) listener.onActivityLoaded(null);
                }
            } catch (Exception e) {
                Log.e(TAG, "Error loading daily activity", e);
                if (listener != null) listener.onActivityLoaded(null);
            }
        });
    }

    /**
     * Get last N days of activity for charts.
     */
    public void getWeeklyActivities(int days, OnWeeklyActivityListener listener) {
        executor.execute(() -> {
            try {
                Calendar cal = Calendar.getInstance();
                cal.add(Calendar.DAY_OF_YEAR, -(days - 1));
                String startDate = DailyActivity.dateKey(cal.getTimeInMillis());

                List<DailyActivityEntity> entities = db.dailyActivityDao().getActivitiesSince(startDate, days);
                List<DailyActivity> activities = new ArrayList<>();
                for (DailyActivityEntity entity : entities) {
                    activities.add(entity.toDailyActivity());
                }
                if (listener != null) listener.onActivitiesLoaded(activities);
            } catch (Exception e) {
                Log.e(TAG, "Error loading weekly activities", e);
                if (listener != null) listener.onActivitiesLoaded(new ArrayList<>());
            }
        });
    }

    /**
     * Update today's step count.
     */
    public void updateTodaySteps(int steps, int calories, float distance) {
        executor.execute(() -> {
            try {
                String today = DailyActivity.todayKey();
                DailyActivityEntity entity = db.dailyActivityDao().getByDate(today);
                if (entity == null) {
                    entity = new DailyActivityEntity();
                    entity.date = today;
                }
                entity.steps = steps;
                entity.calories = calories;
                entity.distance = distance;
                entity.timestamp = System.currentTimeMillis();
                db.dailyActivityDao().insertOrUpdate(entity);
                Log.d(TAG, "Steps updated: " + steps);
            } catch (Exception e) {
                Log.e(TAG, "Error updating steps", e);
            }
        });
    }

    /**
     * Reset today's step count to zero.
     */
    public void resetTodaySteps(OnCompleteListener listener) {
        executor.execute(() -> {
            try {
                String today = DailyActivity.todayKey();
                DailyActivityEntity entity = db.dailyActivityDao().getByDate(today);
                if (entity == null) {
                    entity = new DailyActivityEntity();
                    entity.date = today;
                }
                entity.steps = 0;
                entity.calories = 0;
                entity.distance = 0f;
                entity.timestamp = System.currentTimeMillis();
                db.dailyActivityDao().insertOrUpdate(entity);
                Log.d(TAG, "Steps reset successfully for date: " + today);
                if (listener != null) listener.onSuccess();
            } catch (Exception e) {
                Log.e(TAG, "Error resetting steps", e);
                if (listener != null) listener.onError(e.getMessage());
            }
        });
    }

    /**
     * Increment active minutes for today.
     */
    public void addActiveMinutes(int minutes) {
        executor.execute(() -> {
            try {
                String today = DailyActivity.todayKey();
                DailyActivityEntity entity = db.dailyActivityDao().getByDate(today);
                if (entity == null) {
                    entity = new DailyActivityEntity();
                    entity.date = today;
                }
                entity.activeMinutes += minutes;
                entity.timestamp = System.currentTimeMillis();
                db.dailyActivityDao().insertOrUpdate(entity);
            } catch (Exception e) {
                Log.e(TAG, "Error adding active minutes", e);
            }
        });
    }

    /**
     * Record a completed workout to daily activity.
     */
    public void recordWorkoutToDaily(int durationSeconds, int caloriesBurned) {
        executor.execute(() -> {
            try {
                String today = DailyActivity.todayKey();
                DailyActivityEntity entity = db.dailyActivityDao().getByDate(today);
                if (entity == null) {
                    entity = new DailyActivityEntity();
                    entity.date = today;
                }
                entity.workoutsCount += 1;
                entity.totalWorkoutDuration += durationSeconds;
                entity.calories += caloriesBurned;
                entity.activeMinutes += durationSeconds / 60;
                entity.timestamp = System.currentTimeMillis();
                db.dailyActivityDao().insertOrUpdate(entity);
            } catch (Exception e) {
                Log.e(TAG, "Error recording workout to daily", e);
            }
        });
    }

    // ============ Activity Collection Methods ============

    /**
     * Save an auto-detected or manual activity.
     */
    public void saveActivity(String type, String source, long startTime, long endTime,
            int durationSeconds, float distanceKm, int calories, OnCompleteListener listener) {
        executor.execute(() -> {
            try {
                FitnessActivityEntity entity = new FitnessActivityEntity();
                entity.type = type;
                entity.source = source;
                entity.startTime = startTime;
                entity.endTime = endTime;
                entity.duration = durationSeconds;
                entity.distance = distanceKm;
                entity.calories = calories;
                entity.timestamp = System.currentTimeMillis();

                long id = db.fitnessActivityDao().insert(entity);
                Log.d(TAG, "Activity saved: " + type + " (" + id + ")");
                if (listener != null) listener.onSuccess();
            } catch (Exception e) {
                Log.e(TAG, "Error saving activity", e);
                if (listener != null) listener.onError(e.getMessage());
            }
        });
    }

    /**
     * Get activities for today.
     */
    public void getTodayActivities(OnActivitiesListener listener) {
        executor.execute(() -> {
            try {
                long todayStart = getTodayStartMillis();
                List<FitnessActivityEntity> entities = db.fitnessActivityDao().getActivitiesSince(todayStart);
                List<Map<String, Object>> activities = new ArrayList<>();
                for (FitnessActivityEntity entity : entities) {
                    Map<String, Object> data = new HashMap<>();
                    data.put("id", String.valueOf(entity.id));
                    data.put("type", entity.type);
                    data.put("source", entity.source);
                    data.put("startTime", entity.startTime);
                    data.put("endTime", entity.endTime);
                    data.put("duration", entity.duration);
                    data.put("distance", entity.distance);
                    data.put("calories", entity.calories);
                    data.put("timestamp", entity.timestamp);
                    activities.add(data);
                }
                if (listener != null) listener.onActivitiesLoaded(activities);
            } catch (Exception e) {
                Log.e(TAG, "Error loading today's activities", e);
                if (listener != null) listener.onActivitiesLoaded(new ArrayList<>());
            }
        });
    }

    /**
     * Get activities for a date range.
     */
    public void getActivities(long startTime, long endTime, OnActivitiesListener listener) {
        executor.execute(() -> {
            try {
                List<FitnessActivityEntity> entities = db.fitnessActivityDao().getActivitiesBetween(startTime, endTime);
                List<Map<String, Object>> activities = new ArrayList<>();
                for (FitnessActivityEntity entity : entities) {
                    Map<String, Object> data = new HashMap<>();
                    data.put("id", String.valueOf(entity.id));
                    data.put("type", entity.type);
                    data.put("source", entity.source);
                    data.put("startTime", entity.startTime);
                    data.put("endTime", entity.endTime);
                    data.put("duration", entity.duration);
                    data.put("distance", entity.distance);
                    data.put("calories", entity.calories);
                    data.put("timestamp", entity.timestamp);
                    activities.add(data);
                }
                if (listener != null) listener.onActivitiesLoaded(activities);
            } catch (Exception e) {
                Log.e(TAG, "Error loading activities", e);
                if (listener != null) listener.onActivitiesLoaded(new ArrayList<>());
            }
        });
    }

    private long getTodayStartMillis() {
        Calendar cal = Calendar.getInstance();
        cal.set(Calendar.HOUR_OF_DAY, 0);
        cal.set(Calendar.MINUTE, 0);
        cal.set(Calendar.SECOND, 0);
        cal.set(Calendar.MILLISECOND, 0);
        return cal.getTimeInMillis();
    }

    /**
     * Delete daily activities older than the specified number of days.
     */
    public void deleteOldActivities(int daysToKeep, OnDeleteCompleteListener listener) {
        executor.execute(() -> {
            try {
                Calendar cal = Calendar.getInstance();
                cal.add(Calendar.DAY_OF_YEAR, -daysToKeep);
                String dateThreshold = new SimpleDateFormat("yyyy-MM-dd", Locale.US)
                        .format(new Date(cal.getTimeInMillis()));

                Log.d(TAG, "Deleting activities older than: " + dateThreshold);
                int deleted = db.dailyActivityDao().deleteOlderThan(dateThreshold);
                Log.d(TAG, "Deleted " + deleted + " old activity records");
                if (listener != null) listener.onResult(true);
            } catch (Exception e) {
                Log.e(TAG, "Error deleting old activities", e);
                if (listener != null) listener.onResult(false);
            }
        });
    }

    // ============ Callback Interfaces ============

    public interface OnCompleteListener {
        void onSuccess();
        void onError(String error);
    }

    public interface OnProfileLoadedListener {
        void onProfileLoaded(Map<String, Object> profile);
        void onError(String error);
    }

    public interface OnDailyActivityListener {
        void onActivityLoaded(DailyActivity activity);
    }

    public interface OnWeeklyActivityListener {
        void onActivitiesLoaded(java.util.List<DailyActivity> activities);
    }

    public interface OnActivitiesListener {
        void onActivitiesLoaded(java.util.List<Map<String, Object>> activities);
    }

    public interface OnGoalsLoadedListener {
        void onGoalsLoaded(Map<String, Object> goals);
    }

    public interface OnDeleteCompleteListener {
        void onResult(boolean success);
    }
}
