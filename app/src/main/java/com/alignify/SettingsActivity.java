package com.alignify;

import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;

import com.alignify.engine.CaloriesEngine;
import com.google.android.gms.auth.api.signin.GoogleSignIn;
import com.google.android.gms.auth.api.signin.GoogleSignInClient;
import com.google.android.gms.auth.api.signin.GoogleSignInOptions;
import com.google.firebase.auth.FirebaseAuth;

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

    // Views
    private TextView tvStepGoal;
    private TextView tvCalorieGoal;
    private TextView tvDistanceUnit;

    // Data
    private int stepGoal = 10000;
    private int calorieGoal = 500;
    private boolean useKilometers = true;

    private FirebaseAuth firebaseAuth;
    private GoogleSignInClient googleSignInClient;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_settings);

        firebaseAuth = FirebaseAuth.getInstance();
        GoogleSignInOptions gso = new GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
                .requestEmail()
                .build();
        googleSignInClient = GoogleSignIn.getClient(this, gso);

        initViews();
        loadSettings();
        setupListeners();
    }

    private void initViews() {
        tvStepGoal = findViewById(R.id.tvStepGoal);
        tvCalorieGoal = findViewById(R.id.tvCalorieGoal);
        tvDistanceUnit = findViewById(R.id.tvDistanceUnit);
    }

    private void loadSettings() {
        SharedPreferences prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
        stepGoal = prefs.getInt(KEY_STEP_GOAL, 10000);
        calorieGoal = prefs.getInt(KEY_CALORIE_GOAL, 500);
        useKilometers = prefs.getBoolean(KEY_DISTANCE_UNIT, true);

        updateUI();
    }

    private void updateUI() {
        NumberFormat nf = NumberFormat.getNumberInstance(Locale.US);
        tvStepGoal.setText(nf.format(stepGoal) + " steps");
        tvCalorieGoal.setText(nf.format(calorieGoal) + " kcal");
        tvDistanceUnit.setText(useKilometers ? "Kilometers (km)" : "Miles (mi)");
    }

    private void setupListeners() {
        findViewById(R.id.btnBack).setOnClickListener(v -> finish());

        findViewById(R.id.settingStepGoal).setOnClickListener(v -> showStepGoalDialog());
        findViewById(R.id.settingCalorieGoal).setOnClickListener(v -> showCalorieGoalDialog());
        findViewById(R.id.settingDistanceUnit).setOnClickListener(v -> showDistanceUnitDialog());
        findViewById(R.id.settingEditProfile).setOnClickListener(v -> {
            Intent intent = new Intent(this, ProfileSetupActivity.class);
            intent.putExtra("edit_mode", true);
            startActivity(intent);
        });
        findViewById(R.id.settingLogout).setOnClickListener(v -> showLogoutConfirmation());
    }

    private void showStepGoalDialog() {
        final String[] goals = { "5,000", "7,500", "10,000", "12,500", "15,000" };
        final int[] values = { 5000, 7500, 10000, 12500, 15000 };

        new AlertDialog.Builder(this)
                .setTitle("Daily Step Goal")
                .setItems(goals, (dialog, which) -> {
                    stepGoal = values[which];
                    saveSettings();
                    updateUI();
                })
                .show();
    }

    private void showCalorieGoalDialog() {
        final String[] goals = { "300", "500", "750", "1,000" };
        final int[] values = { 300, 500, 750, 1000 };

        new AlertDialog.Builder(this)
                .setTitle("Daily Calorie Goal")
                .setItems(goals, (dialog, which) -> {
                    calorieGoal = values[which];
                    saveSettings();
                    updateUI();
                })
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
}
