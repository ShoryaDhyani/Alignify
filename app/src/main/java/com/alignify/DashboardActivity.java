package com.alignify;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.util.Log;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.localbroadcastmanager.content.LocalBroadcastManager;

import com.alignify.service.StepCounterService;
import com.alignify.util.StepCounterHelper;
import com.google.android.gms.auth.api.signin.GoogleSignIn;
import com.google.android.gms.auth.api.signin.GoogleSignInClient;
import com.google.android.gms.auth.api.signin.GoogleSignInOptions;
import com.google.firebase.auth.FirebaseAuth;

/**
 * Dashboard/Home screen showing user profile and system status.
 */
public class DashboardActivity extends AppCompatActivity {

    private static final String PREFS_NAME = "AlignifyPrefs";
    private static final String KEY_LOGGED_IN = "logged_in";
    private static final String KEY_USER_EMAIL = "user_email";
    private static final String KEY_USER_NAME = "user_name";
    private static final String KEY_USER_BMI = "user_bmi";
    private static final String KEY_USER_BMI_CATEGORY = "user_bmi_category";
    private static final String KEY_USER_ACTIVITY = "user_activity";
    private static final String KEY_VOICE_FEEDBACK = "voice_feedback";
    private static final String KEY_TEXT_FEEDBACK = "text_feedback";

    private TextView userName;
    private TextView bmiValue;
    private TextView fitnessLevel;

    private Switch voiceToggle;
    private Switch textToggle;
    private TextView voiceStatus;
    private TextView textStatus;

    private Button btnStartCorrection;
    private ImageView navExercises;
    private ImageView navProfile;

    private FirebaseAuth firebaseAuth;
    private GoogleSignInClient googleSignInClient;

    // Step counter
    private static final String TAG = "DashboardActivity";
    private static final int STEP_GOAL = 10000; // Daily step goal
    private TextView stepsValue;
    private Switch stepTrackingToggle;
    private android.widget.ProgressBar stepProgressBar;
    private BroadcastReceiver stepUpdateReceiver;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_dashboard);

        // Initialize Firebase Auth
        firebaseAuth = FirebaseAuth.getInstance();

        // Initialize Google Sign-In client for logout
        GoogleSignInOptions gso = new GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
                .requestEmail()
                .build();
        googleSignInClient = GoogleSignIn.getClient(this, gso);

        initViews();
        loadUserProfile();
        setupListeners();

        // Setup step counter
        setupStepCounter();
    }

    @Override
    protected void onResume() {
        super.onResume();
        // Reload profile when returning from profile edit
        loadUserProfile();

        // Update step count
        updateStepCountDisplay();

        // Register step update receiver
        registerStepUpdateReceiver();
    }

    @Override
    protected void onPause() {
        super.onPause();
        // Unregister step update receiver
        if (stepUpdateReceiver != null) {
            LocalBroadcastManager.getInstance(this).unregisterReceiver(stepUpdateReceiver);
        }
    }

    private void initViews() {
        userName = findViewById(R.id.userName);
        bmiValue = findViewById(R.id.bmiValue);
        fitnessLevel = findViewById(R.id.fitnessLevel);

        voiceToggle = findViewById(R.id.voiceToggle);
        textToggle = findViewById(R.id.textToggle);
        voiceStatus = findViewById(R.id.voiceStatus);
        textStatus = findViewById(R.id.textStatus);

        btnStartCorrection = findViewById(R.id.btnStartCorrection);
        navExercises = findViewById(R.id.navExercises);
        navProfile = findViewById(R.id.navProfile);
    }

    private void loadUserProfile() {
        SharedPreferences prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);

        // Load user data
        String email = prefs.getString(KEY_USER_EMAIL, "User");
        String googleName = prefs.getString(KEY_USER_NAME, "");
        float bmi = prefs.getFloat(KEY_USER_BMI, 0f);
        String bmiCategory = prefs.getString(KEY_USER_BMI_CATEGORY, "Normal");
        String activity = prefs.getString(KEY_USER_ACTIVITY, "Active");

        // Use Google display name if available, otherwise extract from email
        String name;
        if (!googleName.isEmpty()) {
            name = googleName;
        } else if (email.contains("@")) {
            name = email.split("@")[0];
            if (!name.isEmpty()) {
                name = name.substring(0, 1).toUpperCase() + name.substring(1);
            }
        } else {
            name = email;
        }
        userName.setText(name);

        // Set BMI
        if (bmi > 0) {
            bmiValue.setText(String.format("%.1f", bmi));
        } else {
            bmiValue.setText("--");
        }

        // Set fitness level
        fitnessLevel.setText("Fitness Level: " + activity);

        // Load feedback preferences
        boolean voiceEnabled = prefs.getBoolean(KEY_VOICE_FEEDBACK, true);
        boolean textEnabled = prefs.getBoolean(KEY_TEXT_FEEDBACK, true);

        voiceToggle.setChecked(voiceEnabled);
        textToggle.setChecked(textEnabled);
        updateVoiceStatus(voiceEnabled);
        updateTextStatus(textEnabled);
    }

    private void setupListeners() {
        voiceToggle.setOnCheckedChangeListener((buttonView, isChecked) -> {
            updateVoiceStatus(isChecked);
            saveFeedbackPreference(KEY_VOICE_FEEDBACK, isChecked);
        });

        textToggle.setOnCheckedChangeListener((buttonView, isChecked) -> {
            updateTextStatus(isChecked);
            saveFeedbackPreference(KEY_TEXT_FEEDBACK, isChecked);
        });

        btnStartCorrection.setOnClickListener(v -> navigateToExerciseSelection());

        navExercises.setOnClickListener(v -> navigateToExerciseSelection());

        // Settings button - navigate to profile edit
        findViewById(R.id.btnSettings).setOnClickListener(v -> navigateToEditProfile());

        // Profile nav - navigate to profile edit
        navProfile.setOnClickListener(v -> navigateToEditProfile());

        // Logout button
        findViewById(R.id.btnLogout).setOnClickListener(v -> showLogoutConfirmation());

        // Profile card tap - edit profile
        findViewById(R.id.profileCard).setOnClickListener(v -> navigateToEditProfile());
    }

    private void navigateToEditProfile() {
        Intent intent = new Intent(this, ProfileSetupActivity.class);
        intent.putExtra("edit_mode", true);
        startActivity(intent);
    }

    private void showLogoutConfirmation() {
        new AlertDialog.Builder(this)
                .setTitle("Logout")
                .setMessage("Are you sure you want to logout?")
                .setPositiveButton("Logout", (dialog, which) -> performLogout())
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void performLogout() {
        // Sign out from Firebase Auth
        if (firebaseAuth != null) {
            firebaseAuth.signOut();
        }

        // Sign out from Google
        googleSignInClient.signOut().addOnCompleteListener(this, task -> {
            // Clear SharedPreferences
            SharedPreferences prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
            prefs.edit().clear().apply();

            Toast.makeText(this, "Logged out successfully", Toast.LENGTH_SHORT).show();

            // Navigate to login
            Intent intent = new Intent(this, LoginActivity.class);
            intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
            startActivity(intent);
            finish();
        });
    }

    private void updateVoiceStatus(boolean enabled) {
        voiceStatus.setText(enabled ? "ON" : "OFF");
        voiceStatus.setTextColor(getColor(enabled ? R.color.accent : R.color.text_secondary));
    }

    private void updateTextStatus(boolean enabled) {
        textStatus.setText(enabled ? "ON" : "OFF");
        textStatus.setTextColor(getColor(enabled ? R.color.accent : R.color.text_secondary));
    }

    private void saveFeedbackPreference(String key, boolean value) {
        SharedPreferences prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
        prefs.edit().putBoolean(key, value).apply();
    }

    private void navigateToExerciseSelection() {
        Intent intent = new Intent(this, MainActivity.class);
        startActivity(intent);
    }

    // ==================== Step Counter Integration ====================

    /**
     * Sets up the step counter feature.
     */
    private void setupStepCounter() {
        // Initialize views (these need to be added to your layout)
        stepsValue = findViewById(R.id.stepsValue);
        stepTrackingToggle = findViewById(R.id.stepTrackingToggle);
        stepProgressBar = findViewById(R.id.stepProgressBar);

        // Check if step counter is available
        if (!StepCounterHelper.isStepCounterAvailable(this)) {
            Log.w(TAG, "Step counter sensor not available on this device");
            if (stepsValue != null) {
                stepsValue.setText("N/A");
            }
            if (stepTrackingToggle != null) {
                stepTrackingToggle.setEnabled(false);
            }
            return;
        }

        // Load saved step tracking state
        boolean stepTrackingEnabled = StepCounterHelper.isStepTrackingEnabled(this);

        if (stepTrackingToggle != null) {
            stepTrackingToggle.setChecked(stepTrackingEnabled);
            stepTrackingToggle.setOnCheckedChangeListener((buttonView, isChecked) -> {
                if (isChecked) {
                    startStepTracking();
                } else {
                    StepCounterHelper.stopStepTracking(this);
                    if (stepsValue != null) {
                        stepsValue.setText("0");
                    }
                }
            });
        }

        // If step tracking was enabled, start it
        if (stepTrackingEnabled) {
            startStepTracking();
        }
    }

    /**
     * Starts step tracking after checking/requesting permissions.
     */
    private void startStepTracking() {
        // Check if we have permissions
        if (!StepCounterHelper.hasAllPermissions(this)) {
            // Request permissions
            StepCounterHelper.requestPermissions(this);
        } else {
            // Start step tracking
            StepCounterHelper.startStepTracking(this, true);
            updateStepCountDisplay();
        }
    }

    /**
     * Registers the broadcast receiver for step updates.
     */
    private void registerStepUpdateReceiver() {
        stepUpdateReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                if (StepCounterService.ACTION_STEP_UPDATE.equals(intent.getAction())) {
                    int steps = intent.getIntExtra(StepCounterService.EXTRA_STEPS_TODAY, 0);
                    updateStepUI(steps);
                }
            }
        };

        IntentFilter filter = new IntentFilter(StepCounterService.ACTION_STEP_UPDATE);
        LocalBroadcastManager.getInstance(this).registerReceiver(stepUpdateReceiver, filter);
    }

    /**
     * Updates the step count display from SharedPreferences.
     */
    private void updateStepCountDisplay() {
        if (StepCounterHelper.isStepTrackingEnabled(this)) {
            int steps = StepCounterHelper.getStepsToday(this);
            updateStepUI(steps);
        }
    }

    /**
     * Updates the step counter UI elements.
     */
    private void updateStepUI(int steps) {
        if (stepsValue != null) {
            stepsValue.setText(String.valueOf(steps));
        }
        if (stepProgressBar != null) {
            stepProgressBar.setProgress(Math.min(steps, STEP_GOAL));
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions,
            @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);

        if (requestCode == StepCounterHelper.PERMISSION_REQUEST_CODE) {
            boolean allGranted = true;
            for (int result : grantResults) {
                if (result != PackageManager.PERMISSION_GRANTED) {
                    allGranted = false;
                    break;
                }
            }

            if (allGranted) {
                Log.d(TAG, "All step counter permissions granted");
                StepCounterHelper.startStepTracking(this, true);
                updateStepCountDisplay();
            } else {
                Log.w(TAG, "Step counter permissions denied");
                Toast.makeText(this, "Step tracking requires permissions", Toast.LENGTH_SHORT).show();
                if (stepTrackingToggle != null) {
                    stepTrackingToggle.setChecked(false);
                }
            }
        }
    }
}
