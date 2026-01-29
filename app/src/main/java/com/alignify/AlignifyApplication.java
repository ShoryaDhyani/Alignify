package com.alignify;

import android.app.Application;
import android.util.Log;

import com.google.firebase.FirebaseApp;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.FirebaseFirestoreSettings;

/**
 * Application class for Alignify app initialization.
 * Enables Firestore offline persistence and other app-wide configurations.
 */
public class AlignifyApplication extends Application {

    private static final String TAG = "AlignifyApplication";

    @Override
    public void onCreate() {
        super.onCreate();

        // Initialize Firebase
        FirebaseApp.initializeApp(this);

        // Enable Firestore offline persistence
        enableFirestoreOfflinePersistence();

        Log.d(TAG, "Alignify application initialized");
    }

    private void enableFirestoreOfflinePersistence() {
        try {
            FirebaseFirestore db = FirebaseFirestore.getInstance();

            FirebaseFirestoreSettings settings = new FirebaseFirestoreSettings.Builder()
                    .setPersistenceEnabled(true)
                    .setCacheSizeBytes(FirebaseFirestoreSettings.CACHE_SIZE_UNLIMITED)
                    .build();

            db.setFirestoreSettings(settings);

            Log.d(TAG, "Firestore offline persistence enabled");
        } catch (Exception e) {
            Log.e(TAG, "Error enabling Firestore persistence", e);
        }
    }
}
