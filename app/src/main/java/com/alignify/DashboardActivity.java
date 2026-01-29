package com.alignify;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.drawerlayout.widget.DrawerLayout;
import androidx.localbroadcastmanager.content.LocalBroadcastManager;

import com.alignify.data.AchievementManager;
import com.alignify.data.DailyActivity;
import com.alignify.data.StreakManager;
import com.alignify.data.UserRepository;
import com.alignify.engine.CaloriesEngine;
import com.alignify.engine.StepEngine;
import com.alignify.service.StepCounterService;
import com.alignify.util.StepCounterHelper;
import com.google.android.gms.auth.api.signin.GoogleSignIn;
import com.google.android.gms.auth.api.signin.GoogleSignInClient;
import com.google.android.gms.auth.api.signin.GoogleSignInOptions;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;

/**
 * Dashboard/Home screen with navigation drawer for profile and settings.
 */
public class DashboardActivity extends AppCompatActivity {

    private static final String TAG = "DashboardActivity";
    private static final String PREFS_NAME = "AlignifyPrefs";
    private static final String KEY_LOGGED_IN = "logged_in";
    private static final String KEY_USER_EMAIL = "user_email";
    private static final String KEY_USER_NAME = "user_name";
    private static final String KEY_USER_BMI = "user_bmi";
    private static final String KEY_USER_BMI_CATEGORY = "user_bmi_category";
    private static final String KEY_VOICE_FEEDBACK = "voice_feedback";
    private static final String KEY_TEXT_FEEDBACK = "text_feedback";
    private static final int STEP_GOAL = 10000;

    // Drawer
    private DrawerLayout drawerLayout;

    // Header views
    private TextView headerGreeting;

    // Drawer views
    private TextView drawerUserName;
    private TextView drawerUserEmail;
    private TextView drawerAvatarInitial;
    private TextView drawerBmiValue;
    private TextView drawerBmiCategory;

    // Quick stats
    private TextView stepsValue;
    private TextView caloriesValue;
    private TextView activeMinutesValue;
    private android.widget.ProgressBar stepProgressBar;

    // Streak and achievements
    private TextView streakValue;
    private TextView achievementsValue;

    // Feedback toggles
    private androidx.appcompat.widget.SwitchCompat voiceToggle;
    private androidx.appcompat.widget.SwitchCompat textToggle;
    private androidx.appcompat.widget.SwitchCompat stepTrackingToggle;
    private TextView voiceStatus;
    private TextView textStatus;

    // Managers
    private FirebaseAuth firebaseAuth;
    private GoogleSignInClient googleSignInClient;
    private StreakManager streakManager;
    private AchievementManager achievementManager;
    private CaloriesEngine caloriesEngine;
    private StepEngine stepEngine;
    private UserRepository userRepository;

    // Step counter broadcast receiver
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

        // Initialize managers
        streakManager = StreakManager.getInstance(this);
        achievementManager = AchievementManager.getInstance(this);
        caloriesEngine = CaloriesEngine.getInstance(this);
        stepEngine = StepEngine.getInstance(this);
        userRepository = UserRepository.getInstance();

        initViews();
        setupDrawer();
        setupListeners();
        loadUserProfile();
        setupStepCounter();
    }

    @Override
    protected void onResume() {
        super.onResume();
        loadUserProfile();
        updateStepCountDisplay();
        registerStepUpdateReceiver();
        updateStreakAndAchievements();
        updateCaloriesAndActiveMinutes();
        loadTodayActivityFromFirestore();
    }

    @Override
    protected void onPause() {
        super.onPause();
        if (stepUpdateReceiver != null) {
            LocalBroadcastManager.getInstance(this).unregisterReceiver(stepUpdateReceiver);
        }
    }

    private void initViews() {
        // Drawer
        drawerLayout = findViewById(R.id.drawerLayout);

        // Header
        headerGreeting = findViewById(R.id.headerGreeting);

        // Drawer header views
        drawerUserName = findViewById(R.id.drawerUserName);
        drawerUserEmail = findViewById(R.id.drawerUserEmail);
        drawerAvatarInitial = findViewById(R.id.drawerAvatarInitial);
        drawerBmiValue = findViewById(R.id.drawerBmiValue);
        drawerBmiCategory = findViewById(R.id.drawerBmiCategory);

        // Quick stats
        stepsValue = findViewById(R.id.stepsValue);
        caloriesValue = findViewById(R.id.caloriesValue);
        activeMinutesValue = findViewById(R.id.activeMinutesValue);
        stepProgressBar = findViewById(R.id.stepProgressBar);

        // Streak and achievements
        streakValue = findViewById(R.id.streakValue);
        achievementsValue = findViewById(R.id.achievementsValue);

        // Feedback toggles
        voiceToggle = findViewById(R.id.voiceToggle);
        textToggle = findViewById(R.id.textToggle);
        stepTrackingToggle = findViewById(R.id.stepTrackingToggle);
        voiceStatus = findViewById(R.id.voiceStatus);
        textStatus = findViewById(R.id.textStatus);
    }

    private void setupDrawer() {
        // Menu button opens drawer
        findViewById(R.id.btnMenu).setOnClickListener(v -> {
            drawerLayout.openDrawer(android.view.Gravity.START);
        });

        // Drawer navigation items
        findViewById(R.id.navDrawerEditProfile).setOnClickListener(v -> {
            closeDrawerAndNavigate(() -> navigateToEditProfile());
        });

        findViewById(R.id.navDrawerSettings).setOnClickListener(v -> {
            closeDrawerAndNavigate(() -> navigateToSettings());
        });

        findViewById(R.id.navDrawerAchievements).setOnClickListener(v -> {
            closeDrawerAndNavigate(() -> navigateToAchievements());
        });

        findViewById(R.id.navDrawerHistory).setOnClickListener(v -> {
            closeDrawerAndNavigate(() -> navigateToTimeline());
        });

        findViewById(R.id.navDrawerLogout).setOnClickListener(v -> {
            drawerLayout.closeDrawers();
            showLogoutConfirmation();
        });
    }

    private void closeDrawerAndNavigate(Runnable action) {
        drawerLayout.closeDrawers();
        drawerLayout.postDelayed(action, 250);
    }

    private void setupListeners() {
        // Feedback toggles
        voiceToggle.setOnCheckedChangeListener((buttonView, isChecked) -> {
            updateVoiceStatus(isChecked);
            saveFeedbackPreference(KEY_VOICE_FEEDBACK, isChecked);
        });

        textToggle.setOnCheckedChangeListener((buttonView, isChecked) -> {
            updateTextStatus(isChecked);
            saveFeedbackPreference(KEY_TEXT_FEEDBACK, isChecked);
        });

        // Start AI Workout button
        findViewById(R.id.btnStartCorrection).setOnClickListener(v -> navigateToExerciseSelection());

        // Card clicks
        findViewById(R.id.stepCounterCard).setOnClickListener(v -> navigateToSteps());
        findViewById(R.id.caloriesCard).setOnClickListener(v -> navigateToTimeline());
        findViewById(R.id.activeMinutesCard).setOnClickListener(v -> navigateToTimeline());
        findViewById(R.id.streakCard).setOnClickListener(v -> navigateToAchievements());

        // Bottom navigation
        findViewById(R.id.navHome).setOnClickListener(v -> {
            /* Already on home */ });
        findViewById(R.id.navExercises).setOnClickListener(v -> navigateToExerciseSelection());
        findViewById(R.id.navTimeline).setOnClickListener(v -> navigateToTimeline());
        findViewById(R.id.navSteps).setOnClickListener(v -> navigateToSteps());
    }

    private void loadUserProfile() {
        SharedPreferences prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);

        String email = prefs.getString(KEY_USER_EMAIL, "user@example.com");
        String googleName = prefs.getString(KEY_USER_NAME, "");
        float bmi = prefs.getFloat(KEY_USER_BMI, 0f);
        String bmiCategory = prefs.getString(KEY_USER_BMI_CATEGORY, "Normal");

        // Determine display name
        String name;
        if (!googleName.isEmpty()) {
            name = googleName;
        } else if (email.contains("@")) {
            name = email.split("@")[0];
            if (!name.isEmpty()) {
                name = name.substring(0, 1).toUpperCase() + name.substring(1);
            }
        } else {
            name = "User";
        }

        // Update header greeting
        if (headerGreeting != null) {
            headerGreeting.setText("Hi, " + name + " 👋");
        }

        // Update drawer header
        if (drawerUserName != null) {
            drawerUserName.setText(name);
        }
        if (drawerUserEmail != null) {
            drawerUserEmail.setText(email);
        }
        if (drawerAvatarInitial != null && !name.isEmpty()) {
            drawerAvatarInitial.setText(String.valueOf(name.charAt(0)).toUpperCase());
        }
        if (drawerBmiValue != null) {
            drawerBmiValue.setText(bmi > 0 ? String.format("%.1f", bmi) : "--");
        }
        if (drawerBmiCategory != null) {
            drawerBmiCategory.setText(bmiCategory);
            // Color based on BMI category
            int color = R.color.correct_green; // Normal
            if (bmiCategory.equalsIgnoreCase("Underweight") || bmiCategory.equalsIgnoreCase("Overweight")) {
                color = R.color.warning_yellow;
            } else if (bmiCategory.equalsIgnoreCase("Obese")) {
                color = R.color.error_red;
            }
            drawerBmiCategory.setTextColor(getColor(color));
        }

        // Load feedback preferences
        boolean voiceEnabled = prefs.getBoolean(KEY_VOICE_FEEDBACK, true);
        boolean textEnabled = prefs.getBoolean(KEY_TEXT_FEEDBACK, true);

        if (voiceToggle != null)
            voiceToggle.setChecked(voiceEnabled);
        if (textToggle != null)
            textToggle.setChecked(textEnabled);
        updateVoiceStatus(voiceEnabled);
        updateTextStatus(textEnabled);
    }

    // ==================== Navigation ====================

    private void navigateToExerciseSelection() {
        startActivity(new Intent(this, MainActivity.class));
    }

    private void navigateToSteps() {
        startActivity(new Intent(this, StepActivity.class));
    }

    private void navigateToTimeline() {
        startActivity(new Intent(this, TimelineActivity.class));
    }

    private void navigateToSettings() {
        startActivity(new Intent(this, SettingsActivity.class));
    }

    private void navigateToAchievements() {
        startActivity(new Intent(this, AchievementsActivity.class));
    }

    private void navigateToEditProfile() {
        Intent intent = new Intent(this, ProfileSetupActivity.class);
        intent.putExtra("edit_mode", true);
        startActivity(intent);
    }

    // ==================== Logout ====================

    private void showLogoutConfirmation() {
        new AlertDialog.Builder(this)
                .setTitle("Sign Out")
                .setMessage("Are you sure you want to sign out?")
                .setPositiveButton("Sign Out", (dialog, which) -> performLogout())
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

            Toast.makeText(this, "Signed out successfully", Toast.LENGTH_SHORT).show();

            // Navigate to login
            Intent intent = new Intent(this, LoginActivity.class);
            intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
            startActivity(intent);
            finish();
        });
    }

    // ==================== Feedback Toggles ====================

    private void updateVoiceStatus(boolean enabled) {
        if (voiceStatus != null) {
            voiceStatus.setText(enabled ? "ON" : "OFF");
            voiceStatus.setTextColor(getColor(enabled ? R.color.accent : R.color.text_secondary));
        }
    }

    private void updateTextStatus(boolean enabled) {
        if (textStatus != null) {
            textStatus.setText(enabled ? "ON" : "OFF");
            textStatus.setTextColor(getColor(enabled ? R.color.accent : R.color.text_secondary));
        }
    }

    private void saveFeedbackPreference(String key, boolean value) {
        SharedPreferences prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
        prefs.edit().putBoolean(key, value).apply();
    }

    // ==================== Step Counter ====================

    private void setupStepCounter() {
        if (!StepCounterHelper.isStepCounterAvailable(this)) {
            Log.w(TAG, "Step counter sensor not available on this device");
            if (stepsValue != null)
                stepsValue.setText("N/A");
            if (stepTrackingToggle != null)
                stepTrackingToggle.setEnabled(false);
            return;
        }

        boolean stepTrackingEnabled = StepCounterHelper.isStepTrackingEnabled(this);

        if (stepTrackingToggle != null) {
            stepTrackingToggle.setChecked(stepTrackingEnabled);
            stepTrackingToggle.setOnCheckedChangeListener((buttonView, isChecked) -> {
                if (isChecked) {
                    startStepTracking();
                } else {
                    StepCounterHelper.stopStepTracking(this);
                    if (stepsValue != null)
                        stepsValue.setText("0");
                    if (stepProgressBar != null)
                        stepProgressBar.setProgress(0);
                }
            });
        }

        if (stepTrackingEnabled) {
            startStepTracking();
        }
    }

    private void startStepTracking() {
        if (!StepCounterHelper.hasAllPermissions(this)) {
            StepCounterHelper.requestPermissions(this);
        } else {
            StepCounterHelper.startStepTracking(this, true);
            updateStepCountDisplay();
        }
    }

    private void registerStepUpdateReceiver() {
        stepUpdateReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                if (StepCounterService.ACTION_STEP_UPDATE.equals(intent.getAction())) {
                    int steps = intent.getIntExtra(StepCounterService.EXTRA_STEPS_TODAY, 0);
                    updateStepUI(steps);
                    updateCaloriesAndActiveMinutes();
                }
            }
        };

        IntentFilter filter = new IntentFilter(StepCounterService.ACTION_STEP_UPDATE);
        LocalBroadcastManager.getInstance(this).registerReceiver(stepUpdateReceiver, filter);
    }

    private void updateStepCountDisplay() {
        if (StepCounterHelper.isStepTrackingEnabled(this)) {
            int steps = StepCounterHelper.getStepsToday(this);
            updateStepUI(steps);
        }
    }

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

    // ==================== Streak & Achievements ====================

    private void updateStreakAndAchievements() {
        int currentStreak = streakManager.getCurrentStreak();
        if (streakValue != null) {
            streakValue.setText("🔥 " + currentStreak);
        }

        int unlockedCount = achievementManager.getUnlockedCount();
        if (achievementsValue != null) {
            achievementsValue.setText("🏆 " + unlockedCount);
        }

        // Load from Firestore for sync
        streakManager.loadStreakFromFirestore(new StreakManager.OnStreakUpdateListener() {
            @Override
            public void onStreakUpdated(int streak, boolean isNewStreak) {
                runOnUiThread(() -> {
                    if (streakValue != null) {
                        streakValue.setText("🔥 " + streak);
                    }
                });
            }
        });
    }

    // ==================== Calories & Active Minutes ====================

    private void updateCaloriesAndActiveMinutes() {
        int steps = StepCounterHelper.getStepsToday(this);
        int calories = caloriesEngine.calculateStepCalories(steps);
        int activeMinutes = stepEngine.calculateActiveMinutes(steps);

        if (caloriesValue != null) {
            caloriesValue.setText(String.valueOf(calories));
        }
        if (activeMinutesValue != null) {
            activeMinutesValue.setText(String.valueOf(activeMinutes));
        }
    }

    // ==================== Firestore Activity Data ====================

    private void loadTodayActivityFromFirestore() {
        userRepository.getTodayActivity(activity -> {
            runOnUiThread(() -> {
                if (activity != null) {
                    // Update UI with Firestore data
                    if (stepsValue != null && activity.getSteps() > 0) {
                        stepsValue.setText(String.valueOf(activity.getSteps()));
                        if (stepProgressBar != null) {
                            int progress = Math.min(100, (activity.getSteps() * 100) / STEP_GOAL);
                            stepProgressBar.setProgress(progress);
                        }
                    }
                    if (caloriesValue != null && activity.getCalories() > 0) {
                        caloriesValue.setText(String.valueOf(activity.getCalories()));
                    }
                    if (activeMinutesValue != null && activity.getActiveMinutes() > 0) {
                        activeMinutesValue.setText(String.valueOf(activity.getActiveMinutes()));
                    }
                    Log.d(TAG, "Loaded activity from Firestore: " + activity.getSteps() + " steps");
                } else {
                    Log.d(TAG, "No Firestore activity data for today");
                }
            });
        });
    }

    /**
     * Save current step data to Firestore (called periodically or on pause).
     */
    private void saveStepsToFirestore() {
        int steps = StepCounterHelper.getStepsToday(this);
        int calories = caloriesEngine.calculateStepCalories(steps);
        float distance = (float) stepEngine.calculateDistance(steps);

        userRepository.updateTodaySteps(steps, calories, distance);
    }
}
