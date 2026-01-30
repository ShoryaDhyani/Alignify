package com.alignify;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.localbroadcastmanager.content.LocalBroadcastManager;

import com.alignify.data.DailyActivity;
import com.alignify.data.UserRepository;
import com.alignify.engine.ActivityEngine;
import com.alignify.engine.CaloriesEngine;
import com.alignify.service.StepCounterService;
import com.alignify.util.StepCounterHelper;

import java.text.NumberFormat;
import java.util.Locale;

/**
 * Dedicated step tracking screen with live metrics.
 * Displays steps, distance, calories, and activity status.
 */
public class StepActivity extends AppCompatActivity {

    private static final String TAG = "StepActivity";
    private static final String PREFS_NAME = "AlignifyPrefs";
    private static final String KEY_STEP_GOAL = "step_goal";
    private static final int DEFAULT_STEP_GOAL = 10000;

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

    // State
    private int stepGoal = DEFAULT_STEP_GOAL;
    private BroadcastReceiver stepUpdateReceiver;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_step);

        initViews();
        initEngines();
        setupListeners();
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

        // Set max for progress circle
        stepProgressCircle.setMax(DEFAULT_STEP_GOAL);
    }

    private void initEngines() {
        caloriesEngine = CaloriesEngine.getInstance(this);
        activityEngine = ActivityEngine.getInstance(this);
    }

    private void setupListeners() {
        // Back button
        findViewById(R.id.btnBack).setOnClickListener(v -> finish());

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

    private void loadGoal() {
        SharedPreferences prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
        stepGoal = prefs.getInt(KEY_STEP_GOAL, DEFAULT_STEP_GOAL);
        stepProgressCircle.setMax(stepGoal);
        tvGoalValue.setText(NumberFormat.getNumberInstance(Locale.US).format(stepGoal) + " steps");
    }

    private void showGoalDialog() {
        final String[] goals = { "5,000", "7,500", "10,000", "12,500", "15,000" };
        final int[] goalValues = { 5000, 7500, 10000, 12500, 15000 };

        new androidx.appcompat.app.AlertDialog.Builder(this)
                .setTitle("Set Daily Step Goal")
                .setItems(goals, (dialog, which) -> {
                    stepGoal = goalValues[which];
                    SharedPreferences prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
                    prefs.edit().putInt(KEY_STEP_GOAL, stepGoal).apply();
                    stepProgressCircle.setMax(stepGoal);
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

        // Then load from Firestore (might have higher count from other devices)
        UserRepository.getInstance().getTodayActivity(activity -> {
            if (activity != null) {
                runOnUiThread(() -> {
                    int firestoreSteps = activity.getSteps();
                    if (firestoreSteps > localSteps) {
                        updateUI(firestoreSteps);
                    }
                    // Update active minutes from Firestore
                    tvActiveMinutes.setText(String.valueOf(activity.getActiveMinutes()));
                });
            }
        });
    }

    private void updateUI(int steps) {
        // Step count with formatting
        tvStepCount.setText(NumberFormat.getNumberInstance(Locale.US).format(steps));

        // Progress
        stepProgressCircle.setProgress(Math.min(steps, stepGoal));
        int percentage = stepGoal > 0 ? (steps * 100 / stepGoal) : 0;
        tvGoalProgress.setText(percentage + "% of goal");

        // Distance (estimated: average stride ~0.7m)
        float distanceKm = (steps * 0.7f) / 1000f;
        tvDistance.setText(String.format(Locale.US, "%.1f", distanceKm));

        // Calories from CaloriesEngine
        int calories = caloriesEngine.getCaloriesFromSteps(steps);
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
