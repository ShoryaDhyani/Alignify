package com.alignify;

import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;

import androidx.appcompat.app.AppCompatActivity;

import com.alignify.data.UserRepository;

/**
 * Entry point for the app. Auto-logs in as guest and navigates to
 * ProfileSetupActivity (first time) or HomeActivity (returning user).
 */
public class LoginActivity extends AppCompatActivity {

    private static final String PREFS_NAME = "AlignifyPrefs";
    private static final String KEY_LOGGED_IN = "logged_in";
    private static final String KEY_USER_NAME = "user_name";
    private static final String KEY_PROFILE_COMPLETE = "profile_complete";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // Initialize UserRepository (needs context on first call)
        UserRepository.getInstance(this);

        SharedPreferences prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);

        if (prefs.getBoolean(KEY_LOGGED_IN, false)) {
            // Returning user — go straight to next screen
            navigateToNextScreen();
        } else {
            // First launch — set up as guest
            prefs.edit()
                    .putBoolean(KEY_LOGGED_IN, true)
                    .putString(KEY_USER_NAME, "Guest User")
                    .putBoolean(KEY_PROFILE_COMPLETE, false)
                    .apply();

            navigateToNextScreen();
        }
    }

    private void navigateToNextScreen() {
        SharedPreferences prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
        boolean profileComplete = prefs.getBoolean(KEY_PROFILE_COMPLETE, false);

        Class<?> target = profileComplete ? HomeActivity.class : ProfileSetupActivity.class;

        Intent intent = new Intent(this, target);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(intent);
        finish();
    }
}
