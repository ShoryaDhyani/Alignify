package com.alignify;

import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;

import com.alignify.engine.CaloriesEngine;
import com.google.android.gms.auth.api.signin.GoogleSignIn;
import com.google.android.gms.auth.api.signin.GoogleSignInClient;
import com.google.android.gms.auth.api.signin.GoogleSignInOptions;
import com.google.firebase.auth.FirebaseAuth;

import java.util.Locale;

/**
 * Settings activity for personal data, goals, and preferences.
 */
public class SettingsActivity extends AppCompatActivity {

    private static final String PREFS_NAME = "AlignifyPrefs";

    private TextView genderValue, weightValue, heightValue, ageValue;
    private TextView stepGoalValue, calorieGoalValue, activeMinutesGoalValue;
    private TextView distanceUnitValue, weightUnitValue;
    private Switch voiceFeedbackToggle, textFeedbackToggle;

    private SharedPreferences prefs;
    private CaloriesEngine caloriesEngine;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_settings);

        prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
        caloriesEngine = CaloriesEngine.getInstance(this);

        initViews();
        loadSettings();
        setupListeners();
    }

    private void initViews() {
        findViewById(R.id.btnBack).setOnClickListener(v -> finish());

        genderValue = findViewById(R.id.genderValue);
        weightValue = findViewById(R.id.weightValue);
        heightValue = findViewById(R.id.heightValue);
        ageValue = findViewById(R.id.ageValue);
        stepGoalValue = findViewById(R.id.stepGoalValue);
        calorieGoalValue = findViewById(R.id.calorieGoalValue);
        activeMinutesGoalValue = findViewById(R.id.activeMinutesGoalValue);
        distanceUnitValue = findViewById(R.id.distanceUnitValue);
        weightUnitValue = findViewById(R.id.weightUnitValue);
        voiceFeedbackToggle = findViewById(R.id.voiceFeedbackToggle);
        textFeedbackToggle = findViewById(R.id.textFeedbackToggle);
    }

    private void loadSettings() {
        // Personal data
        boolean isMale = prefs.getBoolean("user_is_male", true);
        genderValue.setText(isMale ? "Male" : "Female");

        float weight = prefs.getFloat("user_weight", 70f);
        weightValue.setText(String.format(Locale.US, "%.0f kg", weight));

        float height = prefs.getFloat("user_height", 170f);
        heightValue.setText(String.format(Locale.US, "%.0f cm", height));

        int age = prefs.getInt("user_age", 30);
        ageValue.setText(age + " years");

        // Goals
        int stepGoal = prefs.getInt("step_goal", 10000);
        stepGoalValue.setText(String.format(Locale.US, "%,d", stepGoal));

        int calorieGoal = prefs.getInt("calorie_goal", 500);
        calorieGoalValue.setText(String.valueOf(calorieGoal));

        int activeMinutesGoal = prefs.getInt("active_minutes_goal", 30);
        activeMinutesGoalValue.setText(String.valueOf(activeMinutesGoal));

        // Units
        boolean useKm = prefs.getBoolean("use_km", true);
        distanceUnitValue.setText(useKm ? "Kilometers" : "Miles");

        boolean useKg = prefs.getBoolean("use_kg", true);
        weightUnitValue.setText(useKg ? "Kilograms" : "Pounds");

        // Feedback
        voiceFeedbackToggle.setChecked(prefs.getBoolean("voice_feedback", true));
        textFeedbackToggle.setChecked(prefs.getBoolean("text_feedback", true));
    }

    private void setupListeners() {
        // Gender
        findViewById(R.id.genderRow).setOnClickListener(v -> showGenderDialog());

        // Weight
        findViewById(R.id.weightRow)
                .setOnClickListener(v -> showNumberDialog("Weight (kg)", "user_weight", 30, 200, weightValue, "kg"));

        // Height
        findViewById(R.id.heightRow)
                .setOnClickListener(v -> showNumberDialog("Height (cm)", "user_height", 100, 250, heightValue, "cm"));

        // Age
        findViewById(R.id.ageRow)
                .setOnClickListener(v -> showNumberDialog("Age", "user_age", 10, 100, ageValue, "years"));

        // Step Goal
        findViewById(R.id.stepGoalRow).setOnClickListener(v -> showStepGoalDialog());

        // Calorie Goal
        findViewById(R.id.calorieGoalRow).setOnClickListener(
                v -> showNumberDialog("Calorie Goal", "calorie_goal", 100, 2000, calorieGoalValue, ""));

        // Active Minutes Goal
        findViewById(R.id.activeMinutesGoalRow).setOnClickListener(v -> showNumberDialog("Active Minutes Goal",
                "active_minutes_goal", 10, 120, activeMinutesGoalValue, ""));

        // Distance Unit
        findViewById(R.id.distanceUnitRow).setOnClickListener(v -> showDistanceUnitDialog());

        // Weight Unit
        findViewById(R.id.weightUnitRow).setOnClickListener(v -> showWeightUnitDialog());

        // Feedback toggles
        voiceFeedbackToggle.setOnCheckedChangeListener(
                (buttonView, isChecked) -> prefs.edit().putBoolean("voice_feedback", isChecked).apply());

        textFeedbackToggle.setOnCheckedChangeListener(
                (buttonView, isChecked) -> prefs.edit().putBoolean("text_feedback", isChecked).apply());

        // Logout
        findViewById(R.id.logoutRow).setOnClickListener(v -> showLogoutDialog());
    }

    private void showGenderDialog() {
        String[] options = { "Male", "Female" };
        boolean isMale = prefs.getBoolean("user_is_male", true);

        new AlertDialog.Builder(this)
                .setTitle("Gender")
                .setSingleChoiceItems(options, isMale ? 0 : 1, (dialog, which) -> {
                    prefs.edit().putBoolean("user_is_male", which == 0).apply();
                    genderValue.setText(options[which]);
                    caloriesEngine.refreshProfile();
                    dialog.dismiss();
                })
                .show();
    }

    private void showNumberDialog(String title, String key, int min, int max, TextView display, String suffix) {
        // Create number picker values
        String[] values = new String[max - min + 1];
        for (int i = 0; i <= max - min; i++) {
            values[i] = String.valueOf(min + i);
        }

        int currentValue = key.equals("user_weight") || key.equals("user_height")
                ? (int) prefs.getFloat(key, (min + max) / 2f)
                : prefs.getInt(key, (min + max) / 2);
        int selectedIndex = Math.max(0, Math.min(currentValue - min, values.length - 1));

        new AlertDialog.Builder(this)
                .setTitle(title)
                .setSingleChoiceItems(values, selectedIndex, (dialog, which) -> {
                    int value = min + which;
                    if (key.equals("user_weight") || key.equals("user_height")) {
                        prefs.edit().putFloat(key, (float) value).apply();
                        display.setText(value + " " + suffix);
                    } else {
                        prefs.edit().putInt(key, value).apply();
                        display.setText(suffix.isEmpty() ? String.valueOf(value) : value + " " + suffix);
                    }
                    caloriesEngine.refreshProfile();
                    dialog.dismiss();
                })
                .show();
    }

    private void showStepGoalDialog() {
        String[] options = { "5,000", "7,500", "10,000", "12,500", "15,000", "20,000" };
        int[] values = { 5000, 7500, 10000, 12500, 15000, 20000 };
        int current = prefs.getInt("step_goal", 10000);
        int selectedIndex = 2;
        for (int i = 0; i < values.length; i++) {
            if (values[i] == current) {
                selectedIndex = i;
                break;
            }
        }

        new AlertDialog.Builder(this)
                .setTitle("Daily Step Goal")
                .setSingleChoiceItems(options, selectedIndex, (dialog, which) -> {
                    prefs.edit().putInt("step_goal", values[which]).apply();
                    stepGoalValue.setText(options[which]);
                    dialog.dismiss();
                })
                .show();
    }

    private void showDistanceUnitDialog() {
        String[] options = { "Kilometers", "Miles" };
        boolean useKm = prefs.getBoolean("use_km", true);

        new AlertDialog.Builder(this)
                .setTitle("Distance Unit")
                .setSingleChoiceItems(options, useKm ? 0 : 1, (dialog, which) -> {
                    prefs.edit().putBoolean("use_km", which == 0).apply();
                    distanceUnitValue.setText(options[which]);
                    dialog.dismiss();
                })
                .show();
    }

    private void showWeightUnitDialog() {
        String[] options = { "Kilograms", "Pounds" };
        boolean useKg = prefs.getBoolean("use_kg", true);

        new AlertDialog.Builder(this)
                .setTitle("Weight Unit")
                .setSingleChoiceItems(options, useKg ? 0 : 1, (dialog, which) -> {
                    prefs.edit().putBoolean("use_kg", which == 0).apply();
                    weightUnitValue.setText(options[which]);
                    dialog.dismiss();
                })
                .show();
    }

    private void showLogoutDialog() {
        new AlertDialog.Builder(this)
                .setTitle("Sign Out")
                .setMessage("Are you sure you want to sign out?")
                .setPositiveButton("Sign Out", (dialog, which) -> logout())
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void logout() {
        FirebaseAuth.getInstance().signOut();

        GoogleSignInOptions gso = new GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
                .requestEmail()
                .build();
        GoogleSignInClient googleSignInClient = GoogleSignIn.getClient(this, gso);
        googleSignInClient.signOut();

        prefs.edit().putBoolean("logged_in", false).apply();

        Intent intent = new Intent(this, LoginActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(intent);
        finish();
    }
}
