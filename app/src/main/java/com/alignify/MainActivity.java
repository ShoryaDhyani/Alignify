package com.alignify;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;

import com.alignify.databinding.ActivityMainBinding;

/**
 * Main activity for exercise selection.
 */
public class MainActivity extends AppCompatActivity {

    private ActivityMainBinding binding;
    private String pendingExercise = null;

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
        binding = ActivityMainBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        setupButtons();
    }

    private void setupButtons() {
        // Back button to return to Dashboard
        binding.btnBack.setOnClickListener(v -> finish());

        binding.btnBicepCurl.setOnClickListener(v -> checkPermissionAndStart("bicep_curl"));

        binding.btnSquat.setOnClickListener(v -> checkPermissionAndStart("squat"));

        binding.btnLunge.setOnClickListener(v -> checkPermissionAndStart("lunge"));

        binding.btnPlank.setOnClickListener(v -> checkPermissionAndStart("plank"));
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
