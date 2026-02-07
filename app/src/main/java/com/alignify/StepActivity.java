package com.alignify;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Bundle;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.localbroadcastmanager.content.LocalBroadcastManager;

import com.alignify.data.FitnessDataManager;
import com.alignify.engine.ActivityEngine;
import com.alignify.engine.CaloriesEngine;
import com.alignify.service.StepCounterService;
import com.alignify.util.NavigationHelper;
import com.alignify.util.StepCounterHelper;

import java.text.NumberFormat;
import java.util.Locale;

/**
 * Dedicated step tracking screen with live metrics.
 * Displays steps, distance, calories, and activity status.
 */
public class StepActivity extends AppCompatActivity {

    private static final String TAG = "StepActivity";

    // Views
    private TextView tvStepCount;
    private TextView tvGoalProgress;
    private TextView tvDistance;
    private TextView tvCalories;
    private TextView tvActiveMinutes;
    private TextView tvActivityStatus;
    private TextView tvCadence;
    private TextView tvGoalValue;
    private ProgressBar stepProgressCircle;
    private ImageView ivActivityIcon;
    private Switch switchTracking;

    // Engines
    private CaloriesEngine caloriesEngine;
    private ActivityEngine activityEngine;

    // Data manager (single source of truth)
    private FitnessDataManager fitnessDataManager;
    private BroadcastReceiver stepUpdateReceiver;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_step_new);

        // Initialize FitnessDataManager (single source of truth for fitness data)
        fitnessDataManager = FitnessDataManager.getInstance(this);

        initViews();
        initEngines();
        setupListeners();
        setupBottomNavigation();
        loadGoal();
    }

    private void initViews() {
        tvStepCount = findViewById(R.id.tvStepCount);
        tvGoalProgress = findViewById(R.id.tvGoalProgress);
        tvDistance = findViewById(R.id.tvDistance);
        tvCalories = findViewById(R.id.tvCalories);
        tvActiveMinutes = findViewById(R.id.tvActiveMinutes);
        tvActivityStatus = findViewById(R.id.tvActivityStatus);
        tvCadence = findViewById(R.id.tvCadence);
        tvGoalValue = findViewById(R.id.tvGoalValue);
        stepProgressCircle = findViewById(R.id.stepProgressCircle);
        ivActivityIcon = findViewById(R.id.ivActivityIcon);
        switchTracking = findViewById(R.id.switchTracking);

        // Set max for progress circle from FitnessDataManager
        stepProgressCircle.setMax(fitnessDataManager.getStepGoal());
    }

    private void initEngines() {
        caloriesEngine = CaloriesEngine.getInstance(this);
        activityEngine = ActivityEngine.getInstance(this);
    }

    private void setupListeners() {
        // Tracking toggle
        switchTracking.setOnCheckedChangeListener((buttonView, isChecked) -> {
            if (isChecked) {
                if (StepCounterHelper.hasAllPermissions(this)) {
                    StepCounterHelper.startStepTracking(this, true);
                    Toast.makeText(this, "Step tracking enabled", Toast.LENGTH_SHORT).show();
                } else {
                    StepCounterHelper.requestPermissions(this);
                }
            } else {
                StepCounterHelper.stopStepTracking(this);
                Toast.makeText(this, "Step tracking paused", Toast.LENGTH_SHORT).show();
            }
        });

        // Goal setting
        findViewById(R.id.cardGoalSetting).setOnClickListener(v -> showGoalDialog());
    }

    private void setupBottomNavigation() {
        // Use NAV_HOME since StepActivity is accessed from Dashboard
        NavigationHelper.setupBottomNavigation(this, NavigationHelper.NAV_HOME,
                findViewById(R.id.navHome), findViewById(R.id.navExercises),
                findViewById(R.id.navAnalytics), findViewById(R.id.navProfile));
    }

    private void loadGoal() {
        // Use FitnessDataManager for centralized goal management
        int stepGoal = fitnessDataManager.getStepGoal();
        stepProgressCircle.setMax(stepGoal);
        tvGoalValue.setText(NumberFormat.getNumberInstance(Locale.US).format(stepGoal) + " steps");
    }

    private void showGoalDialog() {
        final String[] goals = { "5,000", "7,500", "10,000", "12,500", "15,000" };
        final int[] goalValues = { 5000, 7500, 10000, 12500, 15000 };

        new androidx.appcompat.app.AlertDialog.Builder(this)
                .setTitle("Set Daily Step Goal")
                .setItems(goals, (dialog, which) -> {
                    // Use FitnessDataManager to set goal (syncs to Firestore automatically)
                    int newGoal = goalValues[which];
                    fitnessDataManager.setStepGoal(newGoal);
                    stepProgressCircle.setMax(newGoal);
                    tvGoalValue.setText(goals[which] + " steps");
                    updateUI(StepCounterHelper.getStepsToday(this));
                })
                .show();
    }

    @Override
    protected void onResume() {
        super.onResume();
        registerStepUpdateReceiver();
        updateFromLocalAndFirestore();
        switchTracking.setChecked(StepCounterHelper.isStepTrackingEnabled(this));
    }

    @Override
    protected void onPause() {
        super.onPause();
        if (stepUpdateReceiver != null) {
            LocalBroadcastManager.getInstance(this).unregisterReceiver(stepUpdateReceiver);
        }
    }

    private void registerStepUpdateReceiver() {
        stepUpdateReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                if (StepCounterService.ACTION_STEP_UPDATE.equals(intent.getAction())) {
                    int steps = intent.getIntExtra(StepCounterService.EXTRA_STEPS_TODAY, 0);
                    updateUI(steps);
                }
            }
        };
        IntentFilter filter = new IntentFilter(StepCounterService.ACTION_STEP_UPDATE);
        LocalBroadcastManager.getInstance(this).registerReceiver(stepUpdateReceiver, filter);
    }

    private void updateFromLocalAndFirestore() {
        // First show local data
        int localSteps = StepCounterHelper.getStepsToday(this);
        updateUI(localSteps);

        // Sync to FitnessDataManager
        fitnessDataManager.setStepsToday(localSteps);

        // Then load from Firestore (might have higher count from other devices)
        fitnessDataManager.loadFromFirestore(() -> {
            runOnUiThread(() -> {
                int mergedSteps = fitnessDataManager.getStepsToday();
                if (mergedSteps > localSteps) {
                    updateUI(mergedSteps);
                }
                // Update active minutes from FitnessDataManager
                tvActiveMinutes.setText(String.valueOf(fitnessDataManager.getActiveMinutesToday()));
            });
        });
    }

    private void updateUI(int steps) {
        // Get step goal from FitnessDataManager
        int stepGoal = fitnessDataManager.getStepGoal();

        // Step count with formatting
        tvStepCount.setText(NumberFormat.getNumberInstance(Locale.US).format(steps));

        // Progress
        stepProgressCircle.setMax(stepGoal);
        stepProgressCircle.setProgress(Math.min(steps, stepGoal));
        int percentage = stepGoal > 0 ? (steps * 100 / stepGoal) : 0;
        tvGoalProgress.setText(percentage + "% of goal");

        // Distance from FitnessDataManager (consistent calculation)
        float distanceKm = fitnessDataManager.getDistanceToday();
        if (distanceKm == 0 && steps > 0) {
            // Fallback calculation if not yet synced
            distanceKm = (steps * 0.7f) / 1000f;
        }
        tvDistance.setText(String.format(Locale.US, "%.1f", distanceKm));

        // Calories from FitnessDataManager
        int calories = fitnessDataManager.getCaloriesToday();
        if (calories == 0 && steps > 0) {
            // Fallback to CaloriesEngine
            calories = caloriesEngine.getCaloriesFromSteps(steps);
        }
        tvCalories.setText(String.valueOf(calories));

        // Activity status
        updateActivityStatus();
    }

    private void updateActivityStatus() {
        String activityType = activityEngine.getCurrentActivityType();
        int cadence = activityEngine.getCurrentCadence();

        tvCadence.setText(cadence + " steps/min");

        switch (activityType) {
            case "running":
                tvActivityStatus.setText("Running");
                ivActivityIcon.setImageResource(R.drawable.ic_walking);
                ivActivityIcon.setColorFilter(getColor(R.color.error_red));
                break;
            case "walking":
                tvActivityStatus.setText("Walking");
                ivActivityIcon.setImageResource(R.drawable.ic_walking);
                ivActivityIcon.setColorFilter(getColor(R.color.accent));
                break;
            case "light_activity":
                tvActivityStatus.setText("Light Activity");
                ivActivityIcon.setImageResource(R.drawable.ic_walking);
                ivActivityIcon.setColorFilter(getColor(R.color.accent_secondary));
                break;
            default:
                tvActivityStatus.setText("Idle");
                ivActivityIcon.setImageResource(R.drawable.ic_walking);
                ivActivityIcon.setColorFilter(getColor(R.color.text_secondary));
                break;
        }
    }
}
