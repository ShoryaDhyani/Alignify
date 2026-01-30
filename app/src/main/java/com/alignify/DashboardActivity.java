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
import android.widget.TextView;
import android.widget.Toast;

import com.google.android.material.switchmaterial.SwitchMaterial;

import android.view.MenuItem;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.appcompat.app.ActionBarDrawerToggle;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.drawerlayout.widget.DrawerLayout;
import androidx.localbroadcastmanager.content.LocalBroadcastManager;

import com.google.android.material.navigation.NavigationView;

import com.alignify.service.StepCounterService;
import com.alignify.util.StepCounterHelper;
import com.alignify.data.DailyActivity;
import com.alignify.data.UserRepository;
import com.bumptech.glide.Glide;
import com.google.android.gms.auth.api.signin.GoogleSignIn;
import com.google.android.gms.auth.api.signin.GoogleSignInAccount;
import com.google.android.gms.auth.api.signin.GoogleSignInClient;
import com.google.android.gms.auth.api.signin.GoogleSignInOptions;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;

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

    private SwitchMaterial voiceToggle;
    private SwitchMaterial textToggle;
    private TextView voiceStatus;
    private TextView textStatus;

    private Button btnStartCorrection;
    private ImageView navExercises;
    private ImageView navProfile;

    // Navigation drawer
    private DrawerLayout drawerLayout;
    private NavigationView navigationView;

    private FirebaseAuth firebaseAuth;
    private GoogleSignInClient googleSignInClient;

    // Step counter
    private static final String TAG = "DashboardActivity";
    private static final int STEP_GOAL = 10000; // Daily step goal
    private TextView stepsValue;
    private SwitchMaterial stepTrackingToggle;
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

        // Load today's activity from Firestore
        loadTodayActivityFromFirestore();
    }

    @Override
    protected void onPause() {
        super.onPause();
        // Unregister step update receiver
        if (stepUpdateReceiver != null) {
            LocalBroadcastManager.getInstance(this).unregisterReceiver(stepUpdateReceiver);
        }
        // Sync current steps to Firestore
        saveTodayStepsToFirestore();
    }

    /**
     * Loads today's activity data from Firestore to display.
     */
    private void loadTodayActivityFromFirestore() {
        UserRepository.getInstance().getTodayActivity(activity -> {
            if (activity != null) {
                runOnUiThread(() -> {
                    // Update UI with Firestore data if higher than local
                    int localSteps = StepCounterHelper.getStepsToday(this);
                    int firestoreSteps = activity.getSteps();
                    if (firestoreSteps > localSteps) {
                        updateStepUI(firestoreSteps);
                    }
                    Log.d(TAG, "Loaded from Firestore: steps=" + firestoreSteps +
                            ", calories=" + activity.getCalories() +
                            ", activeMinutes=" + activity.getActiveMinutes());
                });
            }
        });
    }

    /**
     * Saves current step count to Firestore.
     */
    private void saveTodayStepsToFirestore() {
        if (!StepCounterHelper.isStepTrackingEnabled(this))
            return;

        int steps = StepCounterHelper.getStepsToday(this);
        if (steps > 0) {
            int calories = (int) (steps * 0.04);
            float distance = steps * 0.0007f;
            UserRepository.getInstance().updateTodaySteps(steps, calories, distance);
            Log.d(TAG, "Saved steps to Firestore: " + steps);
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

        // Navigation drawer
        drawerLayout = findViewById(R.id.drawerLayout);
        navigationView = findViewById(R.id.navigationView);

        // Setup hamburger menu
        ImageView btnMenu = findViewById(R.id.btnMenu);
        btnMenu.setOnClickListener(v -> drawerLayout.openDrawer(navigationView));

        // Setup navigation item selection
        navigationView.setNavigationItemSelectedListener(this::onNavigationItemSelected);

        // Populate nav header with user data
        populateNavHeader();
    }

    /**
     * Populates the navigation drawer header with user profile data from
     * Google/Firebase
     */
    private void populateNavHeader() {
        View headerView = navigationView.getHeaderView(0);
        ImageView navAvatar = headerView.findViewById(R.id.navAvatar);
        TextView navUserName = headerView.findViewById(R.id.navUserName);
        TextView navUserEmail = headerView.findViewById(R.id.navUserEmail);

        // Get current Firebase user
        FirebaseUser firebaseUser = firebaseAuth.getCurrentUser();

        // Get Google Sign-In account for photo URL
        GoogleSignInAccount googleAccount = GoogleSignIn.getLastSignedInAccount(this);

        // Get user info from SharedPreferences
        SharedPreferences prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
        String storedName = prefs.getString(KEY_USER_NAME, "");
        String storedEmail = prefs.getString(KEY_USER_EMAIL, "");

        // Set user name - prioritize Firebase, then Google, then stored prefs
        String displayName = "";
        if (firebaseUser != null && firebaseUser.getDisplayName() != null && !firebaseUser.getDisplayName().isEmpty()) {
            displayName = firebaseUser.getDisplayName();
        } else if (googleAccount != null && googleAccount.getDisplayName() != null) {
            displayName = googleAccount.getDisplayName();
        } else if (!storedName.isEmpty()) {
            displayName = storedName;
        }
        navUserName.setText(displayName.isEmpty() ? "User" : displayName);

        // Set email - prioritize Firebase, then stored
        String email = "";
        if (firebaseUser != null && firebaseUser.getEmail() != null) {
            email = firebaseUser.getEmail();
        } else if (!storedEmail.isEmpty()) {
            email = storedEmail;
        }
        navUserEmail.setText(email);

        // Load profile photo - prioritize Google account, then Firebase
        android.net.Uri photoUrl = null;
        if (googleAccount != null && googleAccount.getPhotoUrl() != null) {
            photoUrl = googleAccount.getPhotoUrl();
        } else if (firebaseUser != null && firebaseUser.getPhotoUrl() != null) {
            photoUrl = firebaseUser.getPhotoUrl();
        }

        if (photoUrl != null) {
            Glide.with(this)
                    .load(photoUrl)
                    .placeholder(R.drawable.ic_profile)
                    .error(R.drawable.ic_profile)
                    .into(navAvatar);
        } else {
            navAvatar.setImageResource(R.drawable.ic_profile);
        }
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

        // Profile nav - navigate to profile edit
        navProfile.setOnClickListener(v -> navigateToEditProfile());
    }

    private boolean onNavigationItemSelected(MenuItem item) {
        drawerLayout.closeDrawers();
        int id = item.getItemId();

        if (id == R.id.nav_dashboard) {
            // Already on dashboard
            return true;
        } else if (id == R.id.nav_steps) {
            startActivity(new Intent(this, StepActivity.class));
            return true;
        } else if (id == R.id.nav_exercises) {
            navigateToExerciseSelection();
            return true;
        } else if (id == R.id.nav_history) {
            startActivity(new Intent(this, HistoryActivity.class));
            return true;
        } else if (id == R.id.nav_settings) {
            startActivity(new Intent(this, SettingsActivity.class));
            return true;
        } else if (id == R.id.nav_profile) {
            navigateToEditProfile();
            return true;
        } else if (id == R.id.nav_logout) {
            showLogoutConfirmation();
            return true;
        }
        return false;
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
