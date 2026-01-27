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

    // Callback interfaces
    public interface OnCompleteListener {
        void onSuccess();

        void onError(String error);
    }

    public interface OnProfileLoadedListener {
        void onProfileLoaded(Map<String, Object> profile);

        void onError(String error);
    }
}
