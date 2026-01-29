package com.alignify.data;

import android.util.Log;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.SetOptions;

import java.util.HashMap;
import java.util.Map;

/**
 * Repository class for handling user data operations with Firebase Firestore.
 */
public class UserRepository {

    private static final String TAG = "UserRepository";
    private static final String COLLECTION_USERS = "users";

    private final FirebaseFirestore db;
    private final FirebaseAuth auth;

    private static UserRepository instance;

    public static synchronized UserRepository getInstance() {
        if (instance == null) {
            instance = new UserRepository();
        }
        return instance;
    }

    private UserRepository() {
        db = FirebaseFirestore.getInstance();
        auth = FirebaseAuth.getInstance();
    }

    /**
     * Get the current user's document reference.
     */
    private DocumentReference getUserDocument() {
        FirebaseUser user = auth.getCurrentUser();
        if (user != null) {
            return db.collection(COLLECTION_USERS).document(user.getUid());
        }
        return null;
    }

    /**
     * Save user profile data to Firestore.
     */
    public void saveUserProfile(String email, String name, float bmi, String bmiCategory,
            String activityLevel, int height, int weight, int age,
            String gender, OnCompleteListener listener) {
        DocumentReference userDoc = getUserDocument();
        if (userDoc == null) {
            if (listener != null)
                listener.onError("User not authenticated");
            return;
        }

        Map<String, Object> profile = new HashMap<>();
        profile.put("email", email);
        profile.put("name", name);
        profile.put("bmi", bmi);
        profile.put("bmiCategory", bmiCategory);
        profile.put("activityLevel", activityLevel);
        profile.put("height", height);
        profile.put("weight", weight);
        profile.put("age", age);
        profile.put("gender", gender);
        profile.put("profileComplete", true);
        profile.put("updatedAt", System.currentTimeMillis());

        userDoc.set(profile, SetOptions.merge())
                .addOnSuccessListener(aVoid -> {
                    Log.d(TAG, "Profile saved successfully");
                    if (listener != null)
                        listener.onSuccess();
                })
                .addOnFailureListener(e -> {
                    Log.e(TAG, "Error saving profile", e);
                    if (listener != null)
                        listener.onError(e.getMessage());
                });
    }

    /**
     * Save feedback settings to Firestore.
     */
    public void saveFeedbackSettings(boolean voiceFeedback, boolean textFeedback) {
        DocumentReference userDoc = getUserDocument();
        if (userDoc == null)
            return;

        Map<String, Object> settings = new HashMap<>();
        settings.put("voiceFeedback", voiceFeedback);
        settings.put("textFeedback", textFeedback);

        userDoc.update(settings)
                .addOnSuccessListener(aVoid -> Log.d(TAG, "Settings saved"))
                .addOnFailureListener(e -> Log.e(TAG, "Error saving settings", e));
    }

    /**
     * Load user profile from Firestore.
     */
    public void loadUserProfile(OnProfileLoadedListener listener) {
        DocumentReference userDoc = getUserDocument();
        if (userDoc == null) {
            if (listener != null)
                listener.onError("User not authenticated");
            return;
        }

        userDoc.get()
                .addOnSuccessListener(document -> {
                    if (document.exists()) {
                        Map<String, Object> data = document.getData();
                        if (listener != null)
                            listener.onProfileLoaded(data);
                    } else {
                        if (listener != null)
                            listener.onProfileLoaded(null);
                    }
                })
                .addOnFailureListener(e -> {
                    Log.e(TAG, "Error loading profile", e);
                    if (listener != null)
                        listener.onError(e.getMessage());
                });
    }

    /**
     * Save workout session data.
     */
    public void saveWorkoutSession(String exercise, int reps, int duration,
            int errorsCount, OnCompleteListener listener) {
        DocumentReference userDoc = getUserDocument();
        if (userDoc == null) {
            if (listener != null)
                listener.onError("User not authenticated");
            return;
        }

        Map<String, Object> workout = new HashMap<>();
        workout.put("exercise", exercise);
        workout.put("reps", reps);
        workout.put("duration", duration);
        workout.put("errorsCount", errorsCount);
        workout.put("timestamp", System.currentTimeMillis());

        userDoc.collection("workouts")
                .add(workout)
                .addOnSuccessListener(docRef -> {
                    Log.d(TAG, "Workout saved: " + docRef.getId());
                    if (listener != null)
                        listener.onSuccess();
                })
                .addOnFailureListener(e -> {
                    Log.e(TAG, "Error saving workout", e);
                    if (listener != null)
                        listener.onError(e.getMessage());
                });
    }

    // ============ Daily Activity Methods ============

    private static final String COLLECTION_DAILY_ACTIVITY = "dailyActivity";

    /**
     * Save or update daily activity data (upsert).
     */
    public void saveDailyActivity(DailyActivity activity, OnCompleteListener listener) {
        DocumentReference userDoc = getUserDocument();
        if (userDoc == null) {
            if (listener != null)
                listener.onError("User not authenticated");
            return;
        }

        userDoc.collection(COLLECTION_DAILY_ACTIVITY)
                .document(activity.getDate())
                .set(activity.toMap(), SetOptions.merge())
                .addOnSuccessListener(aVoid -> {
                    Log.d(TAG, "Daily activity saved: " + activity.getDate());
                    if (listener != null)
                        listener.onSuccess();
                })
                .addOnFailureListener(e -> {
                    Log.e(TAG, "Error saving daily activity", e);
                    if (listener != null)
                        listener.onError(e.getMessage());
                });
    }

    /**
     * Get today's activity (cache-first for speed).
     */
    public void getTodayActivity(OnDailyActivityListener listener) {
        getDailyActivity(DailyActivity.todayKey(), listener);
    }

    /**
     * Get activity for a specific date.
     */
    public void getDailyActivity(String date, OnDailyActivityListener listener) {
        DocumentReference userDoc = getUserDocument();
        if (userDoc == null) {
            if (listener != null)
                listener.onActivityLoaded(null);
            return;
        }

        // Try cache first for speed
        userDoc.collection(COLLECTION_DAILY_ACTIVITY)
                .document(date)
                .get(com.google.firebase.firestore.Source.CACHE)
                .addOnSuccessListener(document -> {
                    if (document.exists() && document.getData() != null) {
                        DailyActivity activity = DailyActivity.fromMap(document.getData());
                        if (listener != null)
                            listener.onActivityLoaded(activity);
                    } else {
                        // Fallback to server
                        fetchFromServer(userDoc, date, listener);
                    }
                })
                .addOnFailureListener(e -> fetchFromServer(userDoc, date, listener));
    }

    private void fetchFromServer(DocumentReference userDoc, String date, OnDailyActivityListener listener) {
        userDoc.collection(COLLECTION_DAILY_ACTIVITY)
                .document(date)
                .get()
                .addOnSuccessListener(document -> {
                    if (document.exists() && document.getData() != null) {
                        DailyActivity activity = DailyActivity.fromMap(document.getData());
                        if (listener != null)
                            listener.onActivityLoaded(activity);
                    } else {
                        if (listener != null)
                            listener.onActivityLoaded(null);
                    }
                })
                .addOnFailureListener(e -> {
                    Log.e(TAG, "Error loading daily activity", e);
                    if (listener != null)
                        listener.onActivityLoaded(null);
                });
    }

    /**
     * Get last N days of activity for charts (optimized batch fetch).
     */
    public void getWeeklyActivities(int days, OnWeeklyActivityListener listener) {
        DocumentReference userDoc = getUserDocument();
        if (userDoc == null) {
            if (listener != null)
                listener.onActivitiesLoaded(new java.util.ArrayList<>());
            return;
        }

        // Calculate start date
        java.util.Calendar cal = java.util.Calendar.getInstance();
        cal.add(java.util.Calendar.DAY_OF_YEAR, -(days - 1));
        String startDate = DailyActivity.dateKey(cal.getTimeInMillis());

        userDoc.collection(COLLECTION_DAILY_ACTIVITY)
                .whereGreaterThanOrEqualTo("date", startDate)
                .orderBy("date", com.google.firebase.firestore.Query.Direction.ASCENDING)
                .limit(days)
                .get()
                .addOnSuccessListener(querySnapshot -> {
                    java.util.List<DailyActivity> activities = new java.util.ArrayList<>();
                    for (com.google.firebase.firestore.QueryDocumentSnapshot doc : querySnapshot) {
                        if (doc.getData() != null) {
                            activities.add(DailyActivity.fromMap(doc.getData()));
                        }
                    }
                    if (listener != null)
                        listener.onActivitiesLoaded(activities);
                })
                .addOnFailureListener(e -> {
                    Log.e(TAG, "Error loading weekly activities", e);
                    if (listener != null)
                        listener.onActivitiesLoaded(new java.util.ArrayList<>());
                });
    }

    /**
     * Update today's step count (incremental or replace).
     */
    public void updateTodaySteps(int steps, int calories, float distance) {
        DocumentReference userDoc = getUserDocument();
        if (userDoc == null)
            return;

        String today = DailyActivity.todayKey();
        Map<String, Object> updates = new HashMap<>();
        updates.put("date", today);
        updates.put("steps", steps);
        updates.put("calories", calories);
        updates.put("distance", distance);
        updates.put("timestamp", System.currentTimeMillis());

        userDoc.collection(COLLECTION_DAILY_ACTIVITY)
                .document(today)
                .set(updates, SetOptions.merge())
                .addOnSuccessListener(aVoid -> Log.d(TAG, "Steps updated: " + steps))
                .addOnFailureListener(e -> Log.e(TAG, "Error updating steps", e));
    }

    /**
     * Increment active minutes for today.
     */
    public void addActiveMinutes(int minutes) {
        DocumentReference userDoc = getUserDocument();
        if (userDoc == null)
            return;

        String today = DailyActivity.todayKey();
        userDoc.collection(COLLECTION_DAILY_ACTIVITY)
                .document(today)
                .update("activeMinutes", com.google.firebase.firestore.FieldValue.increment(minutes),
                        "timestamp", System.currentTimeMillis())
                .addOnFailureListener(e -> {
                    // Document might not exist, create it
                    Map<String, Object> data = new HashMap<>();
                    data.put("date", today);
                    data.put("activeMinutes", minutes);
                    data.put("timestamp", System.currentTimeMillis());
                    userDoc.collection(COLLECTION_DAILY_ACTIVITY)
                            .document(today)
                            .set(data, SetOptions.merge());
                });
    }

    /**
     * Record a completed workout to daily activity.
     */
    public void recordWorkoutToDaily(int durationSeconds, int caloriesBurned) {
        DocumentReference userDoc = getUserDocument();
        if (userDoc == null)
            return;

        String today = DailyActivity.todayKey();
        userDoc.collection(COLLECTION_DAILY_ACTIVITY)
                .document(today)
                .update(
                        "workoutsCount", com.google.firebase.firestore.FieldValue.increment(1),
                        "totalWorkoutDuration", com.google.firebase.firestore.FieldValue.increment(durationSeconds),
                        "calories", com.google.firebase.firestore.FieldValue.increment(caloriesBurned),
                        "activeMinutes", com.google.firebase.firestore.FieldValue.increment(durationSeconds / 60),
                        "timestamp", System.currentTimeMillis())
                .addOnFailureListener(e -> {
                    // Document might not exist, create it
                    DailyActivity activity = new DailyActivity(today);
                    activity.addWorkout(durationSeconds);
                    activity.addCalories(caloriesBurned);
                    activity.addActiveMinutes(durationSeconds / 60);
                    saveDailyActivity(activity, null);
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
}
