package com.alignify;

import android.graphics.Color;
import android.os.Bundle;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

import com.alignify.data.DailyActivity;
import com.alignify.data.FitnessDataManager;
import com.alignify.data.UserRepository;
import com.alignify.service.WaterReminderService;
import com.alignify.util.NavigationHelper;
import com.alignify.util.WaterTrackingHelper;
import com.github.mikephil.charting.charts.BarChart;
import com.github.mikephil.charting.charts.LineChart;
import com.github.mikephil.charting.components.XAxis;
import com.github.mikephil.charting.data.BarData;
import com.github.mikephil.charting.data.BarDataSet;
import com.github.mikephil.charting.data.BarEntry;
import com.github.mikephil.charting.data.Entry;
import com.github.mikephil.charting.data.LineData;
import com.github.mikephil.charting.data.LineDataSet;
import com.github.mikephil.charting.formatter.IndexAxisValueFormatter;
import com.google.android.material.bottomsheet.BottomSheetDialog;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.Locale;

/**
 * Activity/Analytics screen with redesigned UI.
 * Shows week calendar, today's stats, and water tracking.
 */
public class ActivityActivity extends AppCompatActivity {

    private static final String TAG = "ActivityActivity";

    // Views
    private TextView tvMonthYear;
    private LinearLayout weekCalendar;
    private TextView tvCalories;
    private TextView tvTrainingPercent;
    private ProgressBar progressTraining;
    private TextView tvHeartRate;
    private TextView tvSteps;
    private TextView tvSleep;
    private TextView tvWaterCups;
    private ProgressBar progressWater;
    private ImageButton btnAddWater;
    private ImageButton btnRemoveWater;

    // Navigation
    private LinearLayout navHome;
    private LinearLayout navExercises;
    private LinearLayout navAnalytics;
    private LinearLayout navProfile;

    // Data
    private FitnessDataManager fitnessDataManager;
    private WaterTrackingHelper waterHelper;
    private Calendar selectedDate;
    private List<DayItem> weekDays = new ArrayList<>();
    private DailyActivity todayActivity;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_activity);

        // Initialize FitnessDataManager (single source of truth for fitness data)
        fitnessDataManager = FitnessDataManager.getInstance(this);
        waterHelper = new WaterTrackingHelper(this);
        selectedDate = Calendar.getInstance();

        initViews();
        setupListeners();
        setupWeekCalendar();
        loadData();
        scheduleWaterReminders();
    }

    @Override
    protected void onResume() {
        super.onResume();
        updateWaterUI();
        loadData();
    }

    private void initViews() {
        tvMonthYear = findViewById(R.id.tvMonthYear);
        weekCalendar = findViewById(R.id.weekCalendar);
        tvCalories = findViewById(R.id.tvCalories);
        tvTrainingPercent = findViewById(R.id.tvTrainingPercent);
        progressTraining = findViewById(R.id.progressTraining);
        tvHeartRate = findViewById(R.id.tvHeartRate);
        tvSteps = findViewById(R.id.tvSteps);
        tvSleep = findViewById(R.id.tvSleep);
        tvWaterCups = findViewById(R.id.tvWaterCups);
        progressWater = findViewById(R.id.progressWater);
        btnAddWater = findViewById(R.id.btnAddWater);
        btnRemoveWater = findViewById(R.id.btnRemoveWater);

        // Navigation
        navHome = findViewById(R.id.navHome);
        navExercises = findViewById(R.id.navExercises);
        navAnalytics = findViewById(R.id.navAnalytics);
        navProfile = findViewById(R.id.navProfile);
    }

    private void setupListeners() {
        // Water tracking - use FitnessDataManager for consistent data
        btnAddWater.setOnClickListener(v -> {
            fitnessDataManager.addWaterCup();
            updateWaterUI();
        });

        btnRemoveWater.setOnClickListener(v -> {
            fitnessDataManager.removeWaterCup();
            updateWaterUI();
        });

        // Steps card click - open step tracking activity
        View stepsCard = findViewById(R.id.stepsCard);
        if (stepsCard != null) {
            stepsCard.setOnClickListener(v -> showStepsBottomSheet());
        }

        // Bottom navigation
        NavigationHelper.setupBottomNavigation(this, NavigationHelper.NAV_ANALYTICS,
                navHome, navExercises, navAnalytics, navProfile);
    }

    private void setupWeekCalendar() {
        weekCalendar.removeAllViews();
        weekDays.clear();

        // Get current week (Sunday to Saturday)
        Calendar cal = Calendar.getInstance();
        cal.set(Calendar.DAY_OF_WEEK, Calendar.SUNDAY);

        String[] dayLabels = { "S", "M", "T", "W", "T", "F", "S" };
        Calendar today = Calendar.getInstance();

        for (int i = 0; i < 7; i++) {
            DayItem dayItem = new DayItem();
            dayItem.date = (Calendar) cal.clone();
            dayItem.dayLabel = dayLabels[i];
            dayItem.dayNumber = cal.get(Calendar.DAY_OF_MONTH);
            dayItem.isToday = isSameDay(cal, today);
            dayItem.isSelected = isSameDay(cal, selectedDate);

            weekDays.add(dayItem);
            weekCalendar.addView(createDayView(dayItem, i));

            cal.add(Calendar.DAY_OF_MONTH, 1);
        }

        // Update month/year text
        SimpleDateFormat monthFormat = new SimpleDateFormat("MMMM yyyy", Locale.US);
        tvMonthYear.setText(monthFormat.format(selectedDate.getTime()));
    }

    private View createDayView(DayItem dayItem, int index) {
        LinearLayout container = new LinearLayout(this);
        container.setLayoutParams(new LinearLayout.LayoutParams(
                dpToPx(48), LinearLayout.LayoutParams.WRAP_CONTENT));
        container.setOrientation(LinearLayout.VERTICAL);
        container.setGravity(Gravity.CENTER);
        container.setPadding(dpToPx(4), dpToPx(8), dpToPx(4), dpToPx(8));

        // Day label (S, M, T, etc.)
        TextView labelView = new TextView(this);
        labelView.setLayoutParams(new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT));
        labelView.setText(dayItem.dayLabel);
        labelView.setTextSize(12);
        labelView.setTextColor(getColor(R.color.text_secondary_dark));
        labelView.setGravity(Gravity.CENTER);

        // Day number in circle
        TextView numberView = new TextView(this);
        LinearLayout.LayoutParams numberParams = new LinearLayout.LayoutParams(dpToPx(36), dpToPx(36));
        numberParams.topMargin = dpToPx(4);
        numberView.setLayoutParams(numberParams);
        numberView.setText(String.valueOf(dayItem.dayNumber));
        numberView.setTextSize(14);
        numberView.setGravity(Gravity.CENTER);

        if (dayItem.isSelected || dayItem.isToday) {
            numberView.setBackgroundResource(R.drawable.bg_day_selected);
            numberView.setTextColor(getColor(R.color.text_primary_dark));
        } else {
            numberView.setBackgroundResource(R.drawable.bg_day_unselected);
            numberView.setTextColor(getColor(R.color.text_secondary_dark));
        }

        container.addView(labelView);
        container.addView(numberView);

        // Click listener - show activity history bottom sheet
        container.setOnClickListener(v -> {
            selectedDate = (Calendar) dayItem.date.clone();
            setupWeekCalendar();
            loadDataForDate(dayItem.date);
            showActivityHistoryBottomSheet(dayItem.date);
        });

        return container;
    }

    private void showActivityHistoryBottomSheet(Calendar date) {
        BottomSheetDialog bottomSheetDialog = new BottomSheetDialog(this);
        View sheetView = LayoutInflater.from(this).inflate(R.layout.bottom_sheet_activity_history, null);
        bottomSheetDialog.setContentView(sheetView);

        // Set date header
        SimpleDateFormat dateFormat = new SimpleDateFormat("EEEE, MMMM d", Locale.US);
        TextView tvHistoryDate = sheetView.findViewById(R.id.tvHistoryDate);
        tvHistoryDate.setText(dateFormat.format(date.getTime()));

        // Get views
        TextView tvHistorySteps = sheetView.findViewById(R.id.tvHistorySteps);
        TextView tvHistoryCalories = sheetView.findViewById(R.id.tvHistoryCalories);
        TextView tvHistoryWater = sheetView.findViewById(R.id.tvHistoryWater);
        BarChart chartWeeklyActivity = sheetView.findViewById(R.id.chartWeeklyActivity);
        LineChart chartExerciseMinutes = sheetView.findViewById(R.id.chartExerciseMinutes);
        LinearLayout exerciseBreakdownContainer = sheetView.findViewById(R.id.exerciseBreakdownContainer);

        // Load data for selected date
        String dateKey = DailyActivity.dateKey(date.getTimeInMillis());
        UserRepository.getInstance().getDailyActivity(dateKey, activity -> {
            runOnUiThread(() -> {
                if (activity != null) {
                    tvHistorySteps.setText(String.valueOf(activity.getSteps()));
                    tvHistoryCalories.setText(String.valueOf(activity.getCalories()));
                    tvHistoryWater.setText(String.valueOf(waterHelper.getWaterCups()));

                    // Populate exercise breakdown
                    populateExerciseBreakdown(exerciseBreakdownContainer, activity);
                } else {
                    tvHistorySteps.setText("0");
                    tvHistoryCalories.setText("0");
                    tvHistoryWater.setText("0");
                }
            });
        });

        // Setup charts
        setupWeeklyActivityChart(chartWeeklyActivity, date);
        setupExerciseMinutesChart(chartExerciseMinutes, date);

        // Close button
        sheetView.findViewById(R.id.btnCloseHistory).setOnClickListener(v -> bottomSheetDialog.dismiss());

        bottomSheetDialog.show();
    }

    private void setupWeeklyActivityChart(BarChart chart, Calendar selectedDate) {
        // Get week data from Firebase
        Calendar cal = (Calendar) selectedDate.clone();
        cal.set(Calendar.DAY_OF_WEEK, Calendar.SUNDAY);

        String[] dayLabels = { "Sun", "Mon", "Tue", "Wed", "Thu", "Fri", "Sat" };
        int selectedDayIndex = selectedDate.get(Calendar.DAY_OF_WEEK) - 1;

        // Store date keys for the week
        String[] dateKeys = new String[7];
        for (int i = 0; i < 7; i++) {
            dateKeys[i] = DailyActivity.dateKey(cal.getTimeInMillis());
            cal.add(Calendar.DAY_OF_MONTH, 1);
        }

        // Load data for all 7 days
        float[] stepsData = new float[7];
        int[] loadedCount = { 0 };

        for (int i = 0; i < 7; i++) {
            final int index = i;
            UserRepository.getInstance().getDailyActivity(dateKeys[i], activity -> {
                if (activity != null) {
                    stepsData[index] = activity.getSteps();
                } else {
                    stepsData[index] = 0;
                }
                loadedCount[0]++;

                // When all data is loaded, update the chart
                if (loadedCount[0] == 7) {
                    runOnUiThread(() -> {
                        List<BarEntry> stepsEntries = new ArrayList<>();
                        for (int j = 0; j < 7; j++) {
                            stepsEntries.add(new BarEntry(j, stepsData[j]));
                        }

                        BarDataSet dataSet = new BarDataSet(stepsEntries, "Steps");

                        // Color bars - highlight selected day
                        int[] colors = new int[7];
                        int accentColor = getColor(R.color.accent);
                        int lightColor = getColor(R.color.card_steps);
                        for (int j = 0; j < 7; j++) {
                            colors[j] = (j == selectedDayIndex) ? accentColor : lightColor;
                        }
                        dataSet.setColors(colors);
                        dataSet.setDrawValues(false);

                        BarData barData = new BarData(dataSet);
                        barData.setBarWidth(0.6f);

                        chart.setData(barData);
                        chart.setFitBars(true);
                        chart.getDescription().setEnabled(false);
                        chart.getLegend().setEnabled(false);
                        chart.setDrawGridBackground(false);
                        chart.setDrawBorders(false);
                        chart.setTouchEnabled(false);

                        // X axis
                        XAxis xAxis = chart.getXAxis();
                        xAxis.setValueFormatter(new IndexAxisValueFormatter(dayLabels));
                        xAxis.setPosition(XAxis.XAxisPosition.BOTTOM);
                        xAxis.setDrawGridLines(false);
                        xAxis.setGranularity(1f);
                        xAxis.setTextColor(getColor(R.color.text_secondary_dark));

                        // Y axis
                        chart.getAxisLeft().setDrawGridLines(true);
                        chart.getAxisLeft().setGridColor(Color.parseColor("#EEEEEE"));
                        chart.getAxisLeft().setTextColor(getColor(R.color.text_secondary_dark));
                        chart.getAxisLeft().setAxisMinimum(0f);
                        chart.getAxisRight().setEnabled(false);

                        chart.animateY(500);
                        chart.invalidate();
                    });
                }
            });
        }
    }

    private void setupExerciseMinutesChart(LineChart chart, Calendar selectedDate) {
        Calendar cal = (Calendar) selectedDate.clone();
        cal.set(Calendar.DAY_OF_WEEK, Calendar.SUNDAY);

        String[] dayLabels = { "Sun", "Mon", "Tue", "Wed", "Thu", "Fri", "Sat" };
        int selectedDayIndex = selectedDate.get(Calendar.DAY_OF_WEEK) - 1;

        // Store date keys for the week
        String[] dateKeys = new String[7];
        for (int i = 0; i < 7; i++) {
            dateKeys[i] = DailyActivity.dateKey(cal.getTimeInMillis());
            cal.add(Calendar.DAY_OF_MONTH, 1);
        }

        // Load data for all 7 days
        float[] minutesData = new float[7];
        int[] loadedCount = { 0 };

        for (int i = 0; i < 7; i++) {
            final int index = i;
            UserRepository.getInstance().getDailyActivity(dateKeys[i], activity -> {
                if (activity != null) {
                    minutesData[index] = activity.getActiveMinutes();
                } else {
                    minutesData[index] = 0;
                }
                loadedCount[0]++;

                // When all data is loaded, update the chart
                if (loadedCount[0] == 7) {
                    runOnUiThread(() -> {
                        List<Entry> entries = new ArrayList<>();
                        for (int j = 0; j < 7; j++) {
                            entries.add(new Entry(j, minutesData[j]));
                        }

                        LineDataSet dataSet = new LineDataSet(entries, "Exercise Minutes");
                        dataSet.setColor(getColor(R.color.accent));
                        dataSet.setCircleColor(getColor(R.color.accent));
                        dataSet.setLineWidth(2f);
                        dataSet.setCircleRadius(4f);
                        dataSet.setDrawCircleHole(true);
                        dataSet.setCircleHoleRadius(2f);
                        dataSet.setDrawFilled(true);
                        dataSet.setFillColor(getColor(R.color.accent));
                        dataSet.setFillAlpha(50);
                        dataSet.setDrawValues(false);
                        dataSet.setMode(LineDataSet.Mode.CUBIC_BEZIER);

                        LineData lineData = new LineData(dataSet);

                        chart.setData(lineData);
                        chart.getDescription().setEnabled(false);
                        chart.getLegend().setEnabled(false);
                        chart.setDrawGridBackground(false);
                        chart.setTouchEnabled(false);

                        // X axis
                        XAxis xAxis = chart.getXAxis();
                        xAxis.setValueFormatter(new IndexAxisValueFormatter(dayLabels));
                        xAxis.setPosition(XAxis.XAxisPosition.BOTTOM);
                        xAxis.setDrawGridLines(false);
                        xAxis.setGranularity(1f);
                        xAxis.setTextColor(getColor(R.color.text_secondary_dark));

                        // Y axis
                        chart.getAxisLeft().setDrawGridLines(true);
                        chart.getAxisLeft().setGridColor(Color.parseColor("#EEEEEE"));
                        chart.getAxisLeft().setTextColor(getColor(R.color.text_secondary_dark));
                        chart.getAxisLeft().setAxisMinimum(0f);
                        chart.getAxisRight().setEnabled(false);

                        chart.animateX(500);
                        chart.invalidate();
                    });
                }
            });
        }
    }

    private void populateExerciseBreakdown(LinearLayout container, DailyActivity activity) {
        container.removeAllViews();

        // Exercise types and their icons/colors
        String[][] exercises = {
                { "Squats", String.valueOf(activity.getSquatReps()), "#4CAF50" },
                { "Bicep Curls", String.valueOf(activity.getBicepCurlReps()), "#2196F3" },
                { "Lunges", String.valueOf(activity.getLungeReps()), "#FF9800" },
                { "Planks", String.valueOf(activity.getPlankSeconds()) + "s", "#9C27B0" }
        };

        for (String[] exercise : exercises) {
            if (Integer.parseInt(exercise[1].replace("s", "")) > 0) {
                LinearLayout row = new LinearLayout(this);
                row.setLayoutParams(new LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT));
                row.setOrientation(LinearLayout.HORIZONTAL);
                row.setPadding(0, dpToPx(8), 0, dpToPx(8));
                row.setGravity(Gravity.CENTER_VERTICAL);

                // Color indicator
                View colorDot = new View(this);
                LinearLayout.LayoutParams dotParams = new LinearLayout.LayoutParams(dpToPx(8), dpToPx(8));
                dotParams.setMarginEnd(dpToPx(12));
                colorDot.setLayoutParams(dotParams);
                colorDot.setBackgroundColor(Color.parseColor(exercise[2]));

                // Exercise name
                TextView tvName = new TextView(this);
                LinearLayout.LayoutParams nameParams = new LinearLayout.LayoutParams(
                        0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
                tvName.setLayoutParams(nameParams);
                tvName.setText(exercise[0]);
                tvName.setTextColor(getColor(R.color.text_primary_dark));
                tvName.setTextSize(14);

                // Reps/time
                TextView tvValue = new TextView(this);
                tvValue.setLayoutParams(new LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.WRAP_CONTENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT));
                tvValue.setText(exercise[1] + (exercise[0].equals("Planks") ? "" : " reps"));
                tvValue.setTextColor(getColor(R.color.text_secondary_dark));
                tvValue.setTextSize(14);

                row.addView(colorDot);
                row.addView(tvName);
                row.addView(tvValue);
                container.addView(row);
            }
        }

        // If no exercises, show placeholder
        if (container.getChildCount() == 0) {
            TextView placeholder = new TextView(this);
            placeholder.setLayoutParams(new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT));
            placeholder.setText("No exercises recorded for this day");
            placeholder.setTextColor(getColor(R.color.text_secondary_dark));
            placeholder.setTextSize(14);
            placeholder.setGravity(Gravity.CENTER);
            placeholder.setPadding(0, dpToPx(16), 0, dpToPx(16));
            container.addView(placeholder);
        }
    }

    private void loadData() {
        loadDataForDate(Calendar.getInstance());
    }

    private void loadDataForDate(Calendar date) {
        String dateKey = DailyActivity.dateKey(date.getTimeInMillis());

        UserRepository.getInstance().getDailyActivity(dateKey, activity -> {
            runOnUiThread(() -> {
                if (activity != null) {
                    todayActivity = activity;
                    updateStatsUI(activity);
                } else {
                    // No data for this date, show defaults
                    todayActivity = new DailyActivity(dateKey);
                    updateStatsUI(todayActivity);
                }
            });
        });

        // Update water UI (always uses today's data from SharedPreferences)
        updateWaterUI();
    }

    private void updateStatsUI(DailyActivity activity) {
        // Calories
        tvCalories.setText(activity.getCalories() + " Cal");

        // Steps - use FitnessDataManager for consistent step goal
        int stepGoal = fitnessDataManager.getStepGoal();
        tvSteps.setText(activity.getSteps() + "/" + stepGoal);

        // Training time (calculate percentage of goal from FitnessDataManager)
        int trainingGoal = fitnessDataManager.getActiveTimeGoal();
        int activeMinutes = activity.getActiveMinutes();
        int trainingPercent = trainingGoal > 0 ? Math.min(100, (activeMinutes * 100) / trainingGoal) : 0;
        tvTrainingPercent.setText(trainingPercent + "%");
        progressTraining.setProgress(trainingPercent);

        // Sleep
        float sleepHours = activity.getSleepHours();
        if (sleepHours > 0) {
            tvSleep.setText(String.format(Locale.US, "%.1f hrs", sleepHours));
        } else {
            tvSleep.setText("-- hrs");
        }

        // Heart rate (placeholder - would need health connect integration)
        tvHeartRate.setText("-- Bpm");
    }

    private void updateWaterUI() {
        // Use FitnessDataManager for water tracking (consistent with other activities)
        int cups = fitnessDataManager.getWaterCupsToday();
        int goal = fitnessDataManager.getWaterGoal();
        int progress = fitnessDataManager.getWaterProgressPercent();

        tvWaterCups.setText(cups + "/" + goal + " Cups");
        progressWater.setProgress(progress);
    }

    private void scheduleWaterReminders() {
        try {
            WaterReminderService.scheduleReminders(this);
        } catch (SecurityException e) {
            // Exact alarm permission not granted - reminders will use inexact timing
            e.printStackTrace();
        }
    }

    private boolean isSameDay(Calendar cal1, Calendar cal2) {
        return cal1.get(Calendar.YEAR) == cal2.get(Calendar.YEAR)
                && cal1.get(Calendar.DAY_OF_YEAR) == cal2.get(Calendar.DAY_OF_YEAR);
    }

    private int dpToPx(int dp) {
        return (int) (dp * getResources().getDisplayMetrics().density);
    }

    private void showStepsBottomSheet() {
        BottomSheetDialog bottomSheetDialog = new BottomSheetDialog(this);
        View sheetView = LayoutInflater.from(this).inflate(R.layout.bottom_sheet_steps, null);
        bottomSheetDialog.setContentView(sheetView);

        // Get views
        ProgressBar progressSteps = sheetView.findViewById(R.id.progressSteps);
        TextView tvCurrentSteps = sheetView.findViewById(R.id.tvCurrentSteps);
        TextView tvGoalSteps = sheetView.findViewById(R.id.tvGoalSteps);
        TextView tvStepMotivation = sheetView.findViewById(R.id.tvStepMotivation);
        TextView tvDistance = sheetView.findViewById(R.id.tvDistance);
        TextView tvStepCalories = sheetView.findViewById(R.id.tvStepCalories);
        TextView tvActiveTime = sheetView.findViewById(R.id.tvActiveTime);

        // Use FitnessDataManager for consistent step goal across all activities
        int currentSteps = fitnessDataManager.getStepsToday();
        int goalSteps = fitnessDataManager.getStepGoal();

        if (todayActivity != null && todayActivity.getSteps() > currentSteps) {
            currentSteps = todayActivity.getSteps();
        }

        int progressPercent = goalSteps > 0 ? (int) ((currentSteps * 100.0f) / goalSteps) : 0;
        progressPercent = Math.min(progressPercent, 100);

        // Update UI
        tvCurrentSteps.setText(String.valueOf(currentSteps));
        tvGoalSteps.setText("of " + goalSteps);
        progressSteps.setMax(100);
        progressSteps.setProgress(progressPercent);

        // Use FitnessDataManager calculated values for consistency
        float distanceKm = fitnessDataManager.getDistanceToday();
        int calories = fitnessDataManager.getCaloriesToday();
        int activeMinutes = fitnessDataManager.getActiveMinutesToday();

        tvDistance.setText(String.format(Locale.US, "%.1f km", distanceKm));
        tvStepCalories.setText(String.valueOf(calories));
        tvActiveTime.setText(activeMinutes + " min");

        // Motivation text
        if (progressPercent >= 100) {
            tvStepMotivation.setText("🎉 Goal achieved! You're a champion!");
        } else if (progressPercent >= 75) {
            tvStepMotivation.setText("💪 Almost there! " + (goalSteps - currentSteps) + " steps to go!");
        } else if (progressPercent >= 50) {
            tvStepMotivation.setText("🚶 Halfway there! Keep moving!");
        } else if (progressPercent >= 25) {
            tvStepMotivation.setText("👟 Great start! Keep it up!");
        } else {
            tvStepMotivation.setText("🌅 Start your day with a walk!");
        }

        // Set goal button
        sheetView.findViewById(R.id.btnSetStepGoal).setOnClickListener(v -> {
            // TODO: Show goal setting dialog
            bottomSheetDialog.dismiss();
        });

        // Close button
        sheetView.findViewById(R.id.btnCloseSteps).setOnClickListener(v -> bottomSheetDialog.dismiss());

        bottomSheetDialog.show();
    }

    /**
     * Helper class to hold day item data.
     */
    private static class DayItem {
        Calendar date;
        String dayLabel;
        int dayNumber;
        boolean isToday;
        boolean isSelected;
    }
}
