package com.alignify;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.util.Log;
import android.view.GestureDetector;
import android.view.MotionEvent;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import android.view.MenuItem;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.appcompat.app.ActionBarDrawerToggle;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.drawerlayout.widget.DrawerLayout;
import androidx.localbroadcastmanager.content.LocalBroadcastManager;

import com.google.android.material.navigation.NavigationView;
import android.widget.ImageButton;

import com.alignify.ml.ModelManager;
import com.alignify.service.StepCounterService;
import com.alignify.util.NavigationHelper;
import com.alignify.util.StepCounterHelper;
import com.alignify.data.DailyActivity;
import com.alignify.data.FitnessDataManager;
import com.alignify.data.UserRepository;
import com.alignify.util.ProfileImageHelper;
import com.bumptech.glide.Glide;
import com.google.android.material.floatingactionbutton.FloatingActionButton;

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
    private static final String KEY_PROFILE_IMAGE_URL = "profile_image_url";

    private TextView userName;
    private TextView bmiValue;
    private TextView fitnessLevel;
    private ImageView ivProfileImage;

    private View btnStartCorrection;

    // Navigation drawer
    private DrawerLayout drawerLayout;
    private NavigationView navigationView;

    // Bottom navigation
    private View navHome;
    private View navExercises;
    private View navAnalytics;
    private View navProfile;

    // Step counter
    private static final String TAG = "DashboardActivity";
    private FitnessDataManager fitnessDataManager;
    private TextView stepsValue;
    private TextView stepGoalText;
    private ImageButton btnResetSteps;
    private android.widget.ProgressBar stepProgressBar;
    private BroadcastReceiver stepUpdateReceiver;
    private TextView tvCalories;
    private TextView tvDistance;

    // Swipe navigation
    private GestureDetector swipeDetector;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_dashboard_new);

        // Initialize FitnessDataManager (single source of truth for fitness data)
        fitnessDataManager = FitnessDataManager.getInstance(this);

        initViews();
        loadUserProfile();

        // Setup step counter
        setupStepCounter();

        // Load data from local SQLite
        fitnessDataManager.loadFromLocal(null);

        // Check for model updates
        checkForModelUpdates();

        // Setup swipe navigation
        swipeDetector = NavigationHelper.createSwipeDetector(this, NavigationHelper.NAV_HOME);

        // Setup overscroll navigation on main ScrollView
        View contentView = findViewById(android.R.id.content);
        if (contentView instanceof android.view.ViewGroup) {
            ScrollView mainScroll = findScrollView((android.view.ViewGroup) contentView);
            if (mainScroll != null) {
                NavigationHelper.enableOverscrollNavigation(this, mainScroll, NavigationHelper.NAV_HOME);
            }
        }
    }

    private ScrollView findScrollView(android.view.ViewGroup parent) {
        for (int i = 0; i < parent.getChildCount(); i++) {
            View child = parent.getChildAt(i);
            if (child instanceof ScrollView)
                return (ScrollView) child;
            if (child instanceof android.view.ViewGroup) {
                ScrollView found = findScrollView((android.view.ViewGroup) child);
                if (found != null)
                    return found;
            }
        }
        return null;
    }

    @Override
    public boolean dispatchTouchEvent(MotionEvent ev) {
        if (swipeDetector != null) {
            swipeDetector.onTouchEvent(ev);
        }
        return super.dispatchTouchEvent(ev);
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

        // Load today's activity from local SQLite
        loadTodayActivityFromLocal();
        
        // Force a sync with connected wearables when viewing the dashboard
        fitnessDataManager.syncWithWearable();
    }

    @Override
    protected void onPause() {
        super.onPause();
        // Unregister step update receiver
        if (stepUpdateReceiver != null) {
            LocalBroadcastManager.getInstance(this).unregisterReceiver(stepUpdateReceiver);
        }
        // Sync current steps to FitnessDataManager (which handles Firestore sync)
        syncStepsToManager();
    }

    /**
     * Loads today's activity data from FitnessDataManager.
     */
    private void loadTodayActivityFromLocal() {
        int steps = fitnessDataManager.getStepsToday();
        updateStepUI(steps);

        fitnessDataManager.loadFromLocal(() -> {
            runOnUiThread(() -> {
                int mergedSteps = fitnessDataManager.getStepsToday();
                updateStepUI(mergedSteps);
                Log.d(TAG, "Loaded from FitnessDataManager: steps=" + mergedSteps +
                        ", calories=" + fitnessDataManager.getCaloriesToday() +
                        ", activeMinutes=" + fitnessDataManager.getActiveMinutesToday());
            });
        });
    }

    /**
     * Syncs current step count to FitnessDataManager.
     */
    private void syncStepsToManager() {
        if (!StepCounterHelper.isStepTrackingEnabled(this))
            return;

        int steps = StepCounterHelper.getStepsToday(this);
        if (steps > 0) {
            // FitnessDataManager auto-calculates calories and distance
            fitnessDataManager.setStepsToday(steps);
            Log.d(TAG, "Synced steps to FitnessDataManager: " + steps);
        }
    }

    private void initViews() {
        userName = findViewById(R.id.userName);
        bmiValue = findViewById(R.id.bmiValue);
        fitnessLevel = findViewById(R.id.fitnessLevel);
        ivProfileImage = findViewById(R.id.ivProfileImage);

        btnStartCorrection = findViewById(R.id.btnStartCorrection);

        // Bottom navigation
        navHome = findViewById(R.id.navHome);
        navExercises = findViewById(R.id.navExercises);
        navAnalytics = findViewById(R.id.navAnalytics);
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

        // Setup bottom navigation with highlighting
        NavigationHelper.setupBottomNavigation(this, NavigationHelper.NAV_HOME,
                navHome, navExercises, navAnalytics, navProfile);
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

        SharedPreferences prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
        String storedName = prefs.getString(KEY_USER_NAME, "");
        String displayName = prefs.getString("display_name", storedName);
        String storedEmail = prefs.getString(KEY_USER_EMAIL, "");

        navUserName.setText(displayName.isEmpty() ? "Guest User" : displayName);
        navUserEmail.setText(storedEmail.isEmpty() ? "Guest" : storedEmail);

        if (ProfileImageHelper.hasProfileImage(this)) {
            String localPath = ProfileImageHelper.getProfileImagePath(this);
            Glide.with(this)
                    .load(new java.io.File(localPath))
                    .placeholder(R.drawable.ic_profile)
                    .error(R.drawable.ic_profile)
                    .circleCrop()
                    .into(navAvatar);
        } else {
            String cachedProfileImageUrl = prefs.getString(KEY_PROFILE_IMAGE_URL, null);
            if (cachedProfileImageUrl != null && !cachedProfileImageUrl.isEmpty()) {
                Glide.with(this)
                        .load(cachedProfileImageUrl)
                        .placeholder(R.drawable.ic_profile)
                        .error(R.drawable.ic_profile)
                        .circleCrop()
                        .into(navAvatar);
            } else {
                navAvatar.setImageResource(R.drawable.ic_profile);
            }
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

        // Load profile image
        loadProfileImage();
    }

    private void loadProfileImage() {
        // First, try to load from local storage (ProfileImageHelper)
        if (ProfileImageHelper.hasProfileImage(this) && ivProfileImage != null) {
            String localPath = ProfileImageHelper.getProfileImagePath(this);
            Glide.with(this)
                    .load(new java.io.File(localPath))
                    .placeholder(R.drawable.ic_profile)
                    .error(R.drawable.ic_profile)
                    .circleCrop()
                    .into(ivProfileImage);
            return;
        }

        // Fallback to SharedPreferences cached URL (for backwards compatibility)
        SharedPreferences prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
        String cachedImageUrl = prefs.getString(KEY_PROFILE_IMAGE_URL, null);

        if (cachedImageUrl != null && !cachedImageUrl.isEmpty() && ivProfileImage != null) {
            Glide.with(this)
                    .load(cachedImageUrl)
                    .placeholder(R.drawable.ic_profile)
                    .error(R.drawable.ic_profile)
                    .circleCrop()
                    .into(ivProfileImage);
        } else {
            // No custom profile image, try Google/Firebase account photos
            loadDefaultAccountPhoto();
        }
    }

    private void loadDefaultAccountPhoto() {
        // No Firebase/Google account photos in offline mode
        if (ivProfileImage != null) {
            ivProfileImage.setImageResource(R.drawable.ic_profile);
        }
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
            startActivity(new Intent(this, ActivityActivity.class));
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
                .setTitle("Reset App")
                .setMessage("This will clear all your local data and return to initial setup. Continue?")
                .setPositiveButton("Reset", (dialog, which) -> performLogout())
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void performLogout() {
        SharedPreferences prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
        prefs.edit().clear().apply();

        Toast.makeText(this, "Data cleared successfully", Toast.LENGTH_SHORT).show();

        Intent intent = new Intent(this, LoginActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(intent);
        finish();
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
        // Initialize views
        stepsValue = findViewById(R.id.stepsValue);
        stepGoalText = findViewById(R.id.stepGoalText);
        btnResetSteps = findViewById(R.id.btnResetSteps);
        stepProgressBar = findViewById(R.id.stepProgressBar);
        tvCalories = findViewById(R.id.tvCalories);
        tvDistance = findViewById(R.id.tvDistance);

        // Observe LiveData for reactive updates to calories and distance
        fitnessDataManager.getCaloriesLiveData().observe(this, calories -> {
            if (tvCalories != null) {
                tvCalories.setText(String.valueOf(calories));
            }
        });
        fitnessDataManager.getDistanceLiveData().observe(this, distance -> {
            if (tvDistance != null) {
                tvDistance.setText(String.format(java.util.Locale.US, "%.1f km", distance));
            }
        });

        // Check if step counter is available
        if (!StepCounterHelper.isStepCounterAvailable(this)) {
            Log.w(TAG, "Step counter sensor not available on this device");
            if (stepsValue != null) {
                stepsValue.setText("N/A");
            }
            if (stepGoalText != null) {
                stepGoalText.setText("Step counter not available");
            }
            if (btnResetSteps != null) {
                btnResetSteps.setEnabled(false);
                btnResetSteps.setAlpha(0.3f);
            }
            return;
        }

        // Setup reset button with confirmation
        if (btnResetSteps != null) {
            btnResetSteps.setOnClickListener(v -> showResetStepsConfirmation());
        }

        // Always start step tracking if permissions are granted
        startStepTracking();
    }

    /**
     * Shows confirmation dialog before resetting steps.
     */
    private void showResetStepsConfirmation() {
        new androidx.appcompat.app.AlertDialog.Builder(this)
                .setTitle("Reset Today's Steps")
                .setMessage("This will reset your step count to zero. This action cannot be undone.")
                .setPositiveButton("Reset", (dialog, which) -> resetSteps())
                .setNegativeButton("Cancel", null)
                .show();
    }

    /**
     * Resets steps both locally and in Firestore.
     */
    private void resetSteps() {
        // Reset local step counter first
        StepCounterService.resetStepCounter(this);

        // Update UI immediately to show reset
        if (stepsValue != null) {
            stepsValue.setText("0");
        }
        if (stepProgressBar != null) {
            stepProgressBar.setProgress(0);
        }

        // Sync to local SQLite
        UserRepository.getInstance(this).resetTodaySteps(new UserRepository.OnCompleteListener() {
            @Override
            public void onSuccess() {
                runOnUiThread(() -> {
                    Log.d("DashboardActivity", "Steps synced successfully");
                    updateStepCountDisplay();
                    Toast.makeText(DashboardActivity.this, "Steps reset and synced", Toast.LENGTH_SHORT).show();
                });
            }

            @Override
            public void onError(String error) {
                runOnUiThread(() -> {
                    Log.e("DashboardActivity", "Sync error: " + error);
                    // Steps are still reset locally, but sync failed
                    String errorMsg = "Steps reset locally but sync failed: " + error;
                    Toast.makeText(DashboardActivity.this, errorMsg, Toast.LENGTH_LONG).show();
                });
            }
        });
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

            // Also sync to FitnessDataManager
            fitnessDataManager.setStepsToday(steps);
        }
    }

    /**
     * Updates the step counter UI elements.
     */
    private void updateStepUI(int steps) {
        int stepGoal = fitnessDataManager.getStepGoal();
        if (stepsValue != null) {
            stepsValue.setText(String.valueOf(steps));
        }
        if (stepGoalText != null) {
            stepGoalText.setText(steps + " / " + stepGoal + " steps");
        }
        if (stepProgressBar != null) {
            stepProgressBar.setMax(stepGoal);
            stepProgressBar.setProgress(Math.min(steps, stepGoal));
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
            }
        }
    }

    /**
     * Check for AI model updates and show dialog if available.
     */
    private void checkForModelUpdates() {
        ModelManager.getInstance(this).checkForUpdates(this,
                new ModelManager.UpdateCheckCallback() {
                    @Override
                    public void onUpdatesAvailable(java.util.List<ModelManager.ModelInfo> updates) {
                        // User chose "Later" - updates are pending
                        Log.d(TAG, updates.size() + " model updates available");
                    }

                    @Override
                    public void onNoUpdates() {
                        Log.d(TAG, "All models are up to date");
                    }

                    @Override
                    public void onError(String error) {
                        Log.e(TAG, "Error checking for model updates: " + error);
                    }
                });
    }
}
