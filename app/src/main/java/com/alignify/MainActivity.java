package com.alignify;

import android.Manifest;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;
import androidx.cardview.widget.CardView;
import androidx.core.content.ContextCompat;

import com.alignify.util.NavigationHelper;
import com.google.android.material.switchmaterial.SwitchMaterial;

/**
 * Main activity for exercise selection.
 */
public class MainActivity extends AppCompatActivity {

    private static final String PREFS_NAME = "AlignifyPrefs";
    private static final String KEY_VOICE_FEEDBACK = "voice_feedback";
    private static final String KEY_TEXT_FEEDBACK = "text_feedback";

    private String pendingExercise = null;

    // Exercise cards
    private CardView cardSquat;
    private CardView cardBicepCurl;
    private CardView cardLunge;
    private CardView cardPlank;

    // Feedback toggles
    private SwitchMaterial voiceToggle;
    private SwitchMaterial textToggle;
    private TextView voiceStatus;
    private TextView textStatus;

    // Bottom navigation
    private LinearLayout navHome;
    private LinearLayout navExercises;
    private LinearLayout navAnalytics;
    private LinearLayout navProfile;

    private final ActivityResultLauncher<String> cameraPermissionLauncher = registerForActivityResult(
            new ActivityResultContracts.RequestPermission(), isGranted -> {
                if (isGranted) {
                    if (pendingExercise != null) {
                        startExercise(pendingExercise);
                    }
                } else {
                    Toast.makeText(
                            this,
                            R.string.permission_camera_rationale,
                            Toast.LENGTH_LONG).show();
                }
                pendingExercise = null;
            });

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_select_exercise);

        initViews();
        setupExerciseCards();
        setupFeedbackToggles();
        setupBottomNavigation();
    }

    private void initViews() {
        // Exercise cards
        cardSquat = findViewById(R.id.cardSquat);
        cardBicepCurl = findViewById(R.id.cardBicepCurl);
        cardLunge = findViewById(R.id.cardLunge);
        cardPlank = findViewById(R.id.cardPlank);

        // Feedback toggles
        voiceToggle = findViewById(R.id.voiceToggle);
        textToggle = findViewById(R.id.textToggle);
        voiceStatus = findViewById(R.id.voiceStatus);
        textStatus = findViewById(R.id.textStatus);

        // Bottom navigation
        navHome = findViewById(R.id.navHome);
        navExercises = findViewById(R.id.navExercises);
        navAnalytics = findViewById(R.id.navAnalytics);
        navProfile = findViewById(R.id.navProfile);
    }

    private void setupFeedbackToggles() {
        SharedPreferences prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
        boolean voiceEnabled = prefs.getBoolean(KEY_VOICE_FEEDBACK, false);
        boolean textEnabled = prefs.getBoolean(KEY_TEXT_FEEDBACK, false);

        voiceToggle.setChecked(voiceEnabled);
        textToggle.setChecked(textEnabled);
        updateVoiceStatus(voiceEnabled);
        updateTextStatus(textEnabled);

        voiceToggle.setOnCheckedChangeListener((buttonView, isChecked) -> {
            updateVoiceStatus(isChecked);
            saveFeedbackPreference(KEY_VOICE_FEEDBACK, isChecked);
        });

        textToggle.setOnCheckedChangeListener((buttonView, isChecked) -> {
            updateTextStatus(isChecked);
            saveFeedbackPreference(KEY_TEXT_FEEDBACK, isChecked);
        });
    }

    private void updateVoiceStatus(boolean enabled) {
        if (voiceStatus != null) {
            voiceStatus.setText(enabled ? "ON" : "OFF");
            voiceStatus.setTextColor(getColor(enabled ? R.color.accent : R.color.text_secondary_dark));
        }
    }

    private void updateTextStatus(boolean enabled) {
        if (textStatus != null) {
            textStatus.setText(enabled ? "ON" : "OFF");
            textStatus.setTextColor(getColor(enabled ? R.color.accent : R.color.text_secondary_dark));
        }
    }

    private void saveFeedbackPreference(String key, boolean value) {
        getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
                .edit()
                .putBoolean(key, value)
                .apply();
    }

    private void setupExerciseCards() {
        cardSquat.setOnClickListener(v -> checkPermissionAndStart("squat"));
        cardBicepCurl.setOnClickListener(v -> checkPermissionAndStart("bicep_curl"));
        cardLunge.setOnClickListener(v -> checkPermissionAndStart("lunge"));
        cardPlank.setOnClickListener(v -> checkPermissionAndStart("plank"));
    }

    private void setupBottomNavigation() {
        NavigationHelper.setupBottomNavigation(this, NavigationHelper.NAV_EXERCISES,
                navHome, navExercises, navAnalytics, navProfile);
    }

    private void checkPermissionAndStart(String exerciseType) {
        if (ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) {
            startExercise(exerciseType);
        } else {
            pendingExercise = exerciseType;
            cameraPermissionLauncher.launch(Manifest.permission.CAMERA);
        }
    }

    private void startExercise(String exerciseType) {
        Intent intent = new Intent(this, ExerciseActivity.class);
        intent.putExtra(ExerciseActivity.EXTRA_EXERCISE_TYPE, exerciseType);
        startActivity(intent);
    }
}
