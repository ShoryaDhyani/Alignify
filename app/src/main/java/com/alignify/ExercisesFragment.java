package com.alignify;

import android.Manifest;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.cardview.widget.CardView;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;

import com.google.android.material.switchmaterial.SwitchMaterial;

/**
 * Exercise selection fragment.
 * Converted from MainActivity for ViewPager2-based navigation.
 */
public class ExercisesFragment extends Fragment {

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

    private final ActivityResultLauncher<String> cameraPermissionLauncher = registerForActivityResult(
            new ActivityResultContracts.RequestPermission(), isGranted -> {
                if (isGranted) {
                    if (pendingExercise != null) {
                        startExercise(pendingExercise);
                    }
                } else {
                    if (isAdded()) {
                        Toast.makeText(requireContext(),
                                R.string.permission_camera_rationale,
                                Toast.LENGTH_LONG).show();
                    }
                }
                pendingExercise = null;
            });

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
            @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.activity_select_exercise, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        // Hide the bottom nav bar from the inflated layout
        View bottomNav = view.findViewById(R.id.bottomNavContainer);
        if (bottomNav != null) {
            bottomNav.setVisibility(View.GONE);
        }

        initViews(view);
        setupExerciseCards();
        setupFeedbackToggles();
    }

    private void initViews(View view) {
        cardSquat = view.findViewById(R.id.cardSquat);
        cardBicepCurl = view.findViewById(R.id.cardBicepCurl);
        cardLunge = view.findViewById(R.id.cardLunge);
        cardPlank = view.findViewById(R.id.cardPlank);

        voiceToggle = view.findViewById(R.id.voiceToggle);
        textToggle = view.findViewById(R.id.textToggle);
        voiceStatus = view.findViewById(R.id.voiceStatus);
        textStatus = view.findViewById(R.id.textStatus);
    }

    private void setupFeedbackToggles() {
        SharedPreferences prefs = requireContext().getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
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
            voiceStatus.setTextColor(requireContext().getColor(enabled ? R.color.accent : R.color.text_secondary_dark));
        }
    }

    private void updateTextStatus(boolean enabled) {
        if (textStatus != null) {
            textStatus.setText(enabled ? "ON" : "OFF");
            textStatus.setTextColor(requireContext().getColor(enabled ? R.color.accent : R.color.text_secondary_dark));
        }
    }

    private void saveFeedbackPreference(String key, boolean value) {
        requireContext().getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .edit()
                .putBoolean(key, value)
                .apply();
    }

    private void setupExerciseCards() {
        if (cardSquat != null)
            cardSquat.setOnClickListener(v -> checkPermissionAndStart("squat"));
        if (cardBicepCurl != null)
            cardBicepCurl.setOnClickListener(v -> checkPermissionAndStart("bicep_curl"));
        if (cardLunge != null)
            cardLunge.setOnClickListener(v -> checkPermissionAndStart("lunge"));
        if (cardPlank != null)
            cardPlank.setOnClickListener(v -> checkPermissionAndStart("plank"));
    }

    private void checkPermissionAndStart(String exerciseType) {
        if (ContextCompat.checkSelfPermission(requireContext(),
                Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) {
            startExercise(exerciseType);
        } else {
            pendingExercise = exerciseType;
            cameraPermissionLauncher.launch(Manifest.permission.CAMERA);
        }
    }

    private void startExercise(String exerciseType) {
        Intent intent = new Intent(requireContext(), ExerciseActivity.class);
        intent.putExtra(ExerciseActivity.EXTRA_EXERCISE_TYPE, exerciseType);
        startActivity(intent);
    }
}
