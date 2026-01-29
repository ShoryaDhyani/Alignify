package com.alignify;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.view.View;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;
import androidx.localbroadcastmanager.content.LocalBroadcastManager;

import com.alignify.data.DailyActivity;
import com.alignify.data.UserRepository;
import com.alignify.engine.CaloriesEngine;
import com.alignify.engine.StepEngine;
import com.alignify.service.StepCounterService;
import com.alignify.util.StepCounterHelper;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;

import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.Locale;

/**
 * Dedicated step tracking screen with live metrics and circular progress.
 */
public class StepActivity extends AppCompatActivity {

    private static final String TAG = "StepActivity";
    private static final String PREFS_NAME = "AlignifyPrefs";
    private static final int DEFAULT_STEP_GOAL = 10000;

    // UI Elements
    private TextView stepCount;
    private TextView stepGoalLabel;
    private ProgressBar stepProgress;
    private TextView distanceValue;
    private TextView caloriesValue;
    private TextView activeMinutesValue;
    private TextView weeklyStepsTotal;
    private View[] weekBars;

    // Data
    private int stepGoal = DEFAULT_STEP_GOAL;
    private BroadcastReceiver stepUpdateReceiver;
    private StepEngine stepEngine;
    private CaloriesEngine caloriesEngine;
    private UserRepository userRepository;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_step);

        stepEngine = StepEngine.getInstance(this);
        caloriesEngine = CaloriesEngine.getInstance(this);
        userRepository = UserRepository.getInstance();

        initViews();
        loadStepGoal();
        setupStepReceiver();
    }

    private void initViews() {
        findViewById(R.id.btnBack).setOnClickListener(v -> finish());

        stepCount = findViewById(R.id.stepCount);
        stepGoalLabel = findViewById(R.id.stepGoalLabel);
        stepProgress = findViewById(R.id.stepProgress);
        distanceValue = findViewById(R.id.distanceValue);
        caloriesValue = findViewById(R.id.caloriesValue);
        activeMinutesValue = findViewById(R.id.activeMinutesValue);
        weeklyStepsTotal = findViewById(R.id.weeklyStepsTotal);

        // Week bars
        weekBars = new View[] {
                findViewById(R.id.barMon),
                findViewById(R.id.barTue),
                findViewById(R.id.barWed),
                findViewById(R.id.barThu),
                findViewById(R.id.barFri),
                findViewById(R.id.barSat),
                findViewById(R.id.barSun)
        };

        // Set step goal
        stepProgress.setMax(stepGoal);
        stepGoalLabel.setText("/ " + formatNumber(stepGoal) + " steps");
    }

    private void loadStepGoal() {
        SharedPreferences prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
        stepGoal = prefs.getInt("step_goal", DEFAULT_STEP_GOAL);
        stepProgress.setMax(stepGoal);
        stepGoalLabel.setText("/ " + formatNumber(stepGoal) + " steps");
    }

    private void setupStepReceiver() {
        stepUpdateReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                int steps = intent.getIntExtra(StepCounterService.EXTRA_STEPS_TODAY, 0);
                updateStepDisplay(steps);
            }
        };
    }

    @Override
    protected void onResume() {
        super.onResume();

        // Register for step updates
        LocalBroadcastManager.getInstance(this).registerReceiver(
                stepUpdateReceiver,
                new IntentFilter(StepCounterService.ACTION_STEP_UPDATE));

        // Get current steps and update display
        int currentSteps = StepCounterHelper.getStepsToday(this);
        updateStepDisplay(currentSteps);

        // Load weekly data
        loadWeeklyData();
    }

    @Override
    protected void onPause() {
        super.onPause();
        LocalBroadcastManager.getInstance(this).unregisterReceiver(stepUpdateReceiver);

        // Save current steps to Firestore
        saveStepsToFirestore();
    }

    private void saveStepsToFirestore() {
        int steps = StepCounterHelper.getStepsToday(this);
        int calories = caloriesEngine.calculateStepCalories(steps);
        float distance = (float) stepEngine.calculateDistance(steps);
        userRepository.updateTodaySteps(steps, calories, distance);
    }

    private void updateStepDisplay(int steps) {
        // Update step count
        stepCount.setText(formatNumber(steps));
        stepProgress.setProgress(Math.min(steps, stepGoal));

        // Calculate distance (average stride = 0.762m)
        double distanceKm = stepEngine.calculateDistance(steps);
        distanceValue.setText(String.format(Locale.US, "%.1f", distanceKm));

        // Calculate calories from steps
        int calories = caloriesEngine.calculateStepCalories(steps);
        caloriesValue.setText(String.valueOf(calories));

        // Calculate active minutes (approx 100 steps/min walking)
        int activeMinutes = stepEngine.calculateActiveMinutes(steps);
        activeMinutesValue.setText(String.valueOf(activeMinutes));
    }

    private void loadWeeklyData() {
        // Get current steps for today
        int todaySteps = StepCounterHelper.getStepsToday(this);

        // Calculate today's index (Mon=0, Sun=6)
        Calendar cal = Calendar.getInstance();
        int dayOfWeek = cal.get(Calendar.DAY_OF_WEEK);
        int todayIndex = (dayOfWeek + 5) % 7;

        // Update today's bar immediately
        updateWeekBar(todayIndex, todaySteps);
        weeklyStepsTotal.setText("Total: " + formatNumber(todaySteps) + " steps");

        // Load weekly data from Firestore using UserRepository
        userRepository.getWeeklyActivities(7, activities -> {
            runOnUiThread(() -> {
                int totalSteps = todaySteps;

                for (DailyActivity activity : activities) {
                    String dateStr = activity.getDate();
                    int barIndex = getBarIndexForDate(dateStr);

                    if (barIndex != todayIndex) {
                        updateWeekBar(barIndex, activity.getSteps());
                        totalSteps += activity.getSteps();
                    }
                }

                weeklyStepsTotal.setText("Total: " + formatNumber(totalSteps) + " steps");
            });
        });
    }

    private int getBarIndexForDate(String dateStr) {
        try {
            SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd", Locale.US);
            Calendar cal = Calendar.getInstance();
            cal.setTime(sdf.parse(dateStr));
            int dayOfWeek = cal.get(Calendar.DAY_OF_WEEK);
            return (dayOfWeek + 5) % 7; // Mon=0, Sun=6
        } catch (Exception e) {
            return -1;
        }
    }

    private void updateWeekBar(int index, int steps) {
        if (index < 0 || index >= weekBars.length)
            return;

        View bar = weekBars[index];
        float progress = Math.min(1.0f, (float) steps / stepGoal);

        // Scale bar height based on progress
        int maxHeight = 60; // dp
        int minHeight = 8; // dp
        float density = getResources().getDisplayMetrics().density;
        int heightPx = (int) ((minHeight + (maxHeight - minHeight) * progress) * density);

        bar.getLayoutParams().height = heightPx;
        bar.setBackgroundResource(steps > 0 ? R.drawable.rounded_bar_active : R.drawable.rounded_bar_inactive);
        bar.requestLayout();
    }

    private String formatNumber(int number) {
        if (number >= 1000) {
            return String.format(Locale.US, "%,d", number);
        }
        return String.valueOf(number);
    }
}
