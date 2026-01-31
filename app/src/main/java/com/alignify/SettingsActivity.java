package com.alignify;

import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Bundle;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;

import com.alignify.engine.CaloriesEngine;
import com.alignify.service.WaterReminderService;
import com.alignify.util.NavigationHelper;
import com.alignify.util.WaterTrackingHelper;
import com.bumptech.glide.Glide;
import com.google.android.gms.auth.api.signin.GoogleSignIn;
import com.google.android.gms.auth.api.signin.GoogleSignInAccount;
import com.google.android.gms.auth.api.signin.GoogleSignInClient;
import com.google.android.gms.auth.api.signin.GoogleSignInOptions;
import com.google.android.material.switchmaterial.SwitchMaterial;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;

import java.text.NumberFormat;
import java.util.Locale;

/**
 * Settings screen for configuring goals, units, and profile.
 */
public class SettingsActivity extends AppCompatActivity {

    private static final String PREFS_NAME = "AlignifyPrefs";
    private static final String KEY_STEP_GOAL = "step_goal";
    private static final String KEY_CALORIE_GOAL = "calorie_goal";
    private static final String KEY_DISTANCE_UNIT = "distance_unit";
    private static final String KEY_WATER_GOAL = "water_goal";
    private static final String KEY_WATER_REMINDERS = "water_reminders_enabled";

    // Views
    private TextView tvStepGoal;
    private TextView tvCalorieGoal;
    private TextView tvDistanceUnit;
    private TextView tvWaterGoal;
    private TextView tvUserName;
    private TextView tvUserEmail;
    private ImageView ivProfileImage;
    private SwitchMaterial switchWaterReminders;

    // Data
    private int stepGoal = 10000;
    private int calorieGoal = 500;
    private int waterGoal = 8;
    private boolean useKilometers = true;

    private FirebaseAuth firebaseAuth;
    private GoogleSignInClient googleSignInClient;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_settings_new);

        firebaseAuth = FirebaseAuth.getInstance();
        GoogleSignInOptions gso = new GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
                .requestEmail()
                .build();
        googleSignInClient = GoogleSignIn.getClient(this, gso);

        initViews();
        loadSettings();
        loadUserProfile();
        setupListeners();
        setupBottomNavigation();
    }

    @Override
    protected void onResume() {
        super.onResume();
        // Refresh profile data when returning from EditProfileActivity
        loadUserProfile();
        loadSettings();
    }

    private void initViews() {
        tvStepGoal = findViewById(R.id.tvStepGoal);
        tvCalorieGoal = findViewById(R.id.tvCalorieGoal);
        tvDistanceUnit = findViewById(R.id.tvDistanceUnit);
        tvWaterGoal = findViewById(R.id.tvWaterGoal);
        tvUserName = findViewById(R.id.tvUserName);
        tvUserEmail = findViewById(R.id.tvUserEmail);
        ivProfileImage = findViewById(R.id.ivProfileImage);
        switchWaterReminders = findViewById(R.id.switchWaterReminders);
    }

    private void loadSettings() {
        SharedPreferences prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
        stepGoal = prefs.getInt(KEY_STEP_GOAL, 10000);
        calorieGoal = prefs.getInt(KEY_CALORIE_GOAL, 500);
        waterGoal = prefs.getInt(KEY_WATER_GOAL, 8);
        useKilometers = prefs.getBoolean(KEY_DISTANCE_UNIT, true);
        boolean waterRemindersEnabled = prefs.getBoolean(KEY_WATER_REMINDERS, true);
        switchWaterReminders.setChecked(waterRemindersEnabled);

        updateUI();
    }

    private void loadUserProfile() {
        SharedPreferences prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);

        // First check for custom display name saved in EditProfileActivity
        String savedDisplayName = prefs.getString("display_name", null);
        String savedPhotoUri = prefs.getString("profile_photo_uri", null);

        if (savedDisplayName != null && !savedDisplayName.isEmpty()) {
            tvUserName.setText(savedDisplayName);
        } else {
            // Fall back to Firebase/Google account
            FirebaseUser user = firebaseAuth.getCurrentUser();
            if (user != null && user.getDisplayName() != null && !user.getDisplayName().isEmpty()) {
                tvUserName.setText(user.getDisplayName());
            } else {
                GoogleSignInAccount account = GoogleSignIn.getLastSignedInAccount(this);
                if (account != null && account.getDisplayName() != null) {
                    tvUserName.setText(account.getDisplayName());
                } else {
                    tvUserName.setText("User");
                }
            }
        }

        // Load email
        FirebaseUser user = firebaseAuth.getCurrentUser();
        if (user != null && user.getEmail() != null) {
            tvUserEmail.setText(user.getEmail());
        } else {
            GoogleSignInAccount account = GoogleSignIn.getLastSignedInAccount(this);
            if (account != null && account.getEmail() != null) {
                tvUserEmail.setText(account.getEmail());
            }
        }

        // Load profile photo - prefer custom saved photo
        if (savedPhotoUri != null) {
            Glide.with(this)
                    .load(Uri.parse(savedPhotoUri))
                    .placeholder(R.drawable.default_profile)
                    .error(R.drawable.default_profile)
                    .circleCrop()
                    .into(ivProfileImage);
        } else if (user != null && user.getPhotoUrl() != null) {
            Glide.with(this)
                    .load(user.getPhotoUrl())
                    .placeholder(R.drawable.default_profile)
                    .error(R.drawable.default_profile)
                    .circleCrop()
                    .into(ivProfileImage);
        } else {
            GoogleSignInAccount account = GoogleSignIn.getLastSignedInAccount(this);
            if (account != null && account.getPhotoUrl() != null) {
                Glide.with(this)
                        .load(account.getPhotoUrl())
                        .placeholder(R.drawable.default_profile)
                        .error(R.drawable.default_profile)
                        .circleCrop()
                        .into(ivProfileImage);
            }
        }
    }

    private void updateUI() {
        NumberFormat nf = NumberFormat.getNumberInstance(Locale.US);
        tvStepGoal.setText(nf.format(stepGoal) + " steps");
        tvCalorieGoal.setText(nf.format(calorieGoal) + " kcal");
        tvDistanceUnit.setText(useKilometers ? "Kilometers (km)" : "Miles (mi)");
        tvWaterGoal.setText(waterGoal + " cups");
    }

    private void setupListeners() {
        findViewById(R.id.cardProfile).setOnClickListener(v -> {
            Intent intent = new Intent(this, EditProfileActivity.class);
            startActivity(intent);
            overridePendingTransition(R.anim.fade_in, R.anim.fade_out);
        });

        findViewById(R.id.settingStepGoal).setOnClickListener(v -> showStepGoalDialog());
        findViewById(R.id.settingCalorieGoal).setOnClickListener(v -> showCalorieGoalDialog());
        findViewById(R.id.settingWaterGoal).setOnClickListener(v -> showWaterGoalDialog());
        findViewById(R.id.settingDistanceUnit).setOnClickListener(v -> showDistanceUnitDialog());
        findViewById(R.id.settingEditProfile).setOnClickListener(v -> {
            Intent intent = new Intent(this, EditProfileActivity.class);
            startActivity(intent);
            overridePendingTransition(R.anim.fade_in, R.anim.fade_out);
        });
        findViewById(R.id.settingLogout).setOnClickListener(v -> showLogoutConfirmation());

        switchWaterReminders.setOnCheckedChangeListener((buttonView, isChecked) -> {
            SharedPreferences prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
            prefs.edit().putBoolean(KEY_WATER_REMINDERS, isChecked).apply();

            if (isChecked) {
                WaterReminderService.scheduleReminders(this);
                Toast.makeText(this, "Water reminders enabled", Toast.LENGTH_SHORT).show();
            } else {
                WaterReminderService.cancelReminders(this);
                Toast.makeText(this, "Water reminders disabled", Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void setupBottomNavigation() {
        NavigationHelper.setupBottomNavigation(this, NavigationHelper.NAV_PROFILE,
                findViewById(R.id.navHome), findViewById(R.id.navExercises),
                findViewById(R.id.navAnalytics), findViewById(R.id.navProfile));
    }

    private void showStepGoalDialog() {
        android.widget.EditText input = new android.widget.EditText(this);
        input.setInputType(android.text.InputType.TYPE_CLASS_NUMBER);
        input.setHint("Enter daily step goal");
        input.setText(String.valueOf(stepGoal));
        input.setSelection(input.getText().length());

        android.widget.FrameLayout container = new android.widget.FrameLayout(this);
        android.widget.FrameLayout.LayoutParams params = new android.widget.FrameLayout.LayoutParams(
                android.widget.FrameLayout.LayoutParams.MATCH_PARENT,
                android.widget.FrameLayout.LayoutParams.WRAP_CONTENT);
        params.leftMargin = dpToPx(20);
        params.rightMargin = dpToPx(20);
        input.setLayoutParams(params);
        container.addView(input);

        new AlertDialog.Builder(this)
                .setTitle("Daily Step Goal")
                .setView(container)
                .setPositiveButton("Save", (dialog, which) -> {
                    String value = input.getText().toString().trim();
                    if (!value.isEmpty()) {
                        try {
                            int newGoal = Integer.parseInt(value);
                            if (newGoal > 0 && newGoal <= 100000) {
                                stepGoal = newGoal;
                                saveSettings();
                                updateUI();
                            } else {
                                Toast.makeText(this, "Enter a value between 1 and 100,000", Toast.LENGTH_SHORT).show();
                            }
                        } catch (NumberFormatException e) {
                            Toast.makeText(this, "Invalid number", Toast.LENGTH_SHORT).show();
                        }
                    }
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void showCalorieGoalDialog() {
        android.widget.EditText input = new android.widget.EditText(this);
        input.setInputType(android.text.InputType.TYPE_CLASS_NUMBER);
        input.setHint("Enter daily calorie goal");
        input.setText(String.valueOf(calorieGoal));
        input.setSelection(input.getText().length());

        android.widget.FrameLayout container = new android.widget.FrameLayout(this);
        android.widget.FrameLayout.LayoutParams params = new android.widget.FrameLayout.LayoutParams(
                android.widget.FrameLayout.LayoutParams.MATCH_PARENT,
                android.widget.FrameLayout.LayoutParams.WRAP_CONTENT);
        params.leftMargin = dpToPx(20);
        params.rightMargin = dpToPx(20);
        input.setLayoutParams(params);
        container.addView(input);

        new AlertDialog.Builder(this)
                .setTitle("Daily Calorie Goal")
                .setView(container)
                .setPositiveButton("Save", (dialog, which) -> {
                    String value = input.getText().toString().trim();
                    if (!value.isEmpty()) {
                        try {
                            int newGoal = Integer.parseInt(value);
                            if (newGoal > 0 && newGoal <= 10000) {
                                calorieGoal = newGoal;
                                saveSettings();
                                updateUI();
                            } else {
                                Toast.makeText(this, "Enter a value between 1 and 10,000", Toast.LENGTH_SHORT).show();
                            }
                        } catch (NumberFormatException e) {
                            Toast.makeText(this, "Invalid number", Toast.LENGTH_SHORT).show();
                        }
                    }
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void showWaterGoalDialog() {
        android.widget.EditText input = new android.widget.EditText(this);
        input.setInputType(android.text.InputType.TYPE_CLASS_NUMBER);
        input.setHint("Enter daily water cups goal");
        input.setText(String.valueOf(waterGoal));
        input.setSelection(input.getText().length());

        android.widget.FrameLayout container = new android.widget.FrameLayout(this);
        android.widget.FrameLayout.LayoutParams params = new android.widget.FrameLayout.LayoutParams(
                android.widget.FrameLayout.LayoutParams.MATCH_PARENT,
                android.widget.FrameLayout.LayoutParams.WRAP_CONTENT);
        params.leftMargin = dpToPx(20);
        params.rightMargin = dpToPx(20);
        input.setLayoutParams(params);
        container.addView(input);

        new AlertDialog.Builder(this)
                .setTitle("Daily Water Goal")
                .setView(container)
                .setPositiveButton("Save", (dialog, which) -> {
                    String value = input.getText().toString().trim();
                    if (!value.isEmpty()) {
                        try {
                            int newGoal = Integer.parseInt(value);
                            if (newGoal > 0 && newGoal <= 20) {
                                waterGoal = newGoal;
                                saveSettings();
                                updateUI();
                                // Update WaterTrackingHelper goal
                                WaterTrackingHelper helper = new WaterTrackingHelper(this);
                                helper.setWaterGoal(waterGoal);
                            } else {
                                Toast.makeText(this, "Enter a value between 1 and 20", Toast.LENGTH_SHORT).show();
                            }
                        } catch (NumberFormatException e) {
                            Toast.makeText(this, "Invalid number", Toast.LENGTH_SHORT).show();
                        }
                    }
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void showDistanceUnitDialog() {
        final String[] units = { "Kilometers (km)", "Miles (mi)" };

        new AlertDialog.Builder(this)
                .setTitle("Distance Unit")
                .setItems(units, (dialog, which) -> {
                    useKilometers = (which == 0);
                    saveSettings();
                    updateUI();
                })
                .show();
    }

    private void saveSettings() {
        SharedPreferences prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
        prefs.edit()
                .putInt(KEY_STEP_GOAL, stepGoal)
                .putInt(KEY_CALORIE_GOAL, calorieGoal)
                .putInt(KEY_WATER_GOAL, waterGoal)
                .putBoolean(KEY_DISTANCE_UNIT, useKilometers)
                .apply();

        // Reload CaloriesEngine if profile changed
        CaloriesEngine.getInstance(this).loadUserProfile();
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
        if (firebaseAuth != null) {
            firebaseAuth.signOut();
        }

        googleSignInClient.signOut().addOnCompleteListener(this, task -> {
            SharedPreferences prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
            prefs.edit().clear().apply();

            Toast.makeText(this, "Logged out successfully", Toast.LENGTH_SHORT).show();

            Intent intent = new Intent(this, LoginActivity.class);
            intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
            startActivity(intent);
            finish();
        });
    }

    private int dpToPx(int dp) {
        return (int) (dp * getResources().getDisplayMetrics().density);
    }
}
