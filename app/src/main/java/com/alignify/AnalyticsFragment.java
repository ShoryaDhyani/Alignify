package com.alignify;

import android.graphics.Color;
import android.os.Bundle;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import com.alignify.data.DailyActivity;
import com.alignify.data.FitnessDataManager;
import com.alignify.data.UserRepository;
import com.alignify.service.WaterReminderService;
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
import java.util.List;
import java.util.Locale;

/**
 * Analytics/Activity fragment with charts and water tracking.
 * Converted from ActivityActivity for ViewPager2-based navigation.
 */
public class AnalyticsFragment extends Fragment {

    private static final String TAG = "AnalyticsFragment";

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

    // Data
    private FitnessDataManager fitnessDataManager;
    private WaterTrackingHelper waterHelper;
    private Calendar selectedDate;
    private List<DayItem> weekDays = new ArrayList<>();
    private DailyActivity todayActivity;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
            @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.activity_activity, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        // Hide the bottom nav bar from the inflated layout
        View bottomNav = view.findViewById(R.id.bottomNavContainer);
        if (bottomNav != null) {
            bottomNav.setVisibility(View.GONE);
        }

        fitnessDataManager = FitnessDataManager.getInstance(requireContext());
        waterHelper = new WaterTrackingHelper(requireContext());
        selectedDate = Calendar.getInstance();

        initViews(view);
        setupListeners(view);
        setupWeekCalendar();
        loadData();
        scheduleWaterReminders();
    }

    @Override
    public void onResume() {
        super.onResume();
        if (!isAdded())
            return;
        updateWaterUI();
        loadData();
    }

    private void initViews(View view) {
        tvMonthYear = view.findViewById(R.id.tvMonthYear);
        weekCalendar = view.findViewById(R.id.weekCalendar);
        tvCalories = view.findViewById(R.id.tvCalories);
        tvTrainingPercent = view.findViewById(R.id.tvTrainingPercent);
        progressTraining = view.findViewById(R.id.progressTraining);
        tvHeartRate = view.findViewById(R.id.tvHeartRate);
        tvSteps = view.findViewById(R.id.tvSteps);
        tvSleep = view.findViewById(R.id.tvSleep);
        tvWaterCups = view.findViewById(R.id.tvWaterCups);
        progressWater = view.findViewById(R.id.progressWater);
        btnAddWater = view.findViewById(R.id.btnAddWater);
        btnRemoveWater = view.findViewById(R.id.btnRemoveWater);
    }

    private void setupListeners(View view) {
        btnAddWater.setOnClickListener(v -> {
            fitnessDataManager.addWaterCup();
            updateWaterUI();
        });

        btnRemoveWater.setOnClickListener(v -> {
            fitnessDataManager.removeWaterCup();
            updateWaterUI();
        });

        View stepsCard = view.findViewById(R.id.stepsCard);
        if (stepsCard != null) {
            stepsCard.setOnClickListener(v -> showStepsBottomSheet());
        }
    }

    private void setupWeekCalendar() {
        weekCalendar.removeAllViews();
        weekDays.clear();

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

        SimpleDateFormat monthFormat = new SimpleDateFormat("MMMM yyyy", Locale.US);
        tvMonthYear.setText(monthFormat.format(selectedDate.getTime()));
    }

    private View createDayView(DayItem dayItem, int index) {
        LinearLayout container = new LinearLayout(requireContext());
        container.setLayoutParams(new LinearLayout.LayoutParams(dpToPx(48), LinearLayout.LayoutParams.WRAP_CONTENT));
        container.setOrientation(LinearLayout.VERTICAL);
        container.setGravity(Gravity.CENTER);
        container.setPadding(dpToPx(4), dpToPx(8), dpToPx(4), dpToPx(8));

        TextView labelView = new TextView(requireContext());
        labelView.setLayoutParams(new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT));
        labelView.setText(dayItem.dayLabel);
        labelView.setTextSize(12);
        labelView.setTextColor(requireContext().getColor(R.color.text_secondary_dark));
        labelView.setGravity(Gravity.CENTER);

        TextView numberView = new TextView(requireContext());
        LinearLayout.LayoutParams numberParams = new LinearLayout.LayoutParams(dpToPx(36), dpToPx(36));
        numberParams.topMargin = dpToPx(4);
        numberView.setLayoutParams(numberParams);
        numberView.setText(String.valueOf(dayItem.dayNumber));
        numberView.setTextSize(14);
        numberView.setGravity(Gravity.CENTER);

        if (dayItem.isSelected || dayItem.isToday) {
            numberView.setBackgroundResource(R.drawable.bg_day_selected);
            numberView.setTextColor(requireContext().getColor(R.color.text_primary_dark));
        } else {
            numberView.setBackgroundResource(R.drawable.bg_day_unselected);
            numberView.setTextColor(requireContext().getColor(R.color.text_secondary_dark));
        }

        container.addView(labelView);
        container.addView(numberView);

        container.setOnClickListener(v -> {
            selectedDate = (Calendar) dayItem.date.clone();
            setupWeekCalendar();
            loadDataForDate(dayItem.date);
            showActivityHistoryBottomSheet(dayItem.date);
        });

        return container;
    }

    private void showActivityHistoryBottomSheet(Calendar date) {
        if (!isAdded())
            return;
        BottomSheetDialog bottomSheetDialog = new BottomSheetDialog(requireContext());
        View sheetView = LayoutInflater.from(requireContext()).inflate(R.layout.bottom_sheet_activity_history, null);
        bottomSheetDialog.setContentView(sheetView);

        SimpleDateFormat dateFormat = new SimpleDateFormat("EEEE, MMMM d", Locale.US);
        TextView tvHistoryDate = sheetView.findViewById(R.id.tvHistoryDate);
        tvHistoryDate.setText(dateFormat.format(date.getTime()));

        TextView tvHistorySteps = sheetView.findViewById(R.id.tvHistorySteps);
        TextView tvHistoryCalories = sheetView.findViewById(R.id.tvHistoryCalories);
        TextView tvHistoryWater = sheetView.findViewById(R.id.tvHistoryWater);
        BarChart chartWeeklyActivity = sheetView.findViewById(R.id.chartWeeklyActivity);
        LineChart chartExerciseMinutes = sheetView.findViewById(R.id.chartExerciseMinutes);
        LinearLayout exerciseBreakdownContainer = sheetView.findViewById(R.id.exerciseBreakdownContainer);

        String dateKey = DailyActivity.dateKey(date.getTimeInMillis());
        UserRepository.getInstance().getDailyActivity(dateKey, activity -> {
            if (!isAdded())
                return;
            requireActivity().runOnUiThread(() -> {
                if (activity != null) {
                    tvHistorySteps.setText(String.valueOf(activity.getSteps()));
                    tvHistoryCalories.setText(String.valueOf(activity.getCalories()));
                    tvHistoryWater.setText(String.valueOf(waterHelper.getWaterCups()));
                    populateExerciseBreakdown(exerciseBreakdownContainer, activity);
                } else {
                    tvHistorySteps.setText("0");
                    tvHistoryCalories.setText("0");
                    tvHistoryWater.setText("0");
                }
            });
        });

        setupWeeklyActivityChart(chartWeeklyActivity, date);
        setupExerciseMinutesChart(chartExerciseMinutes, date);

        sheetView.findViewById(R.id.btnCloseHistory).setOnClickListener(v -> bottomSheetDialog.dismiss());
        bottomSheetDialog.show();
    }

    private void setupWeeklyActivityChart(BarChart chart, Calendar selectedDate) {
        Calendar cal = (Calendar) selectedDate.clone();
        cal.set(Calendar.DAY_OF_WEEK, Calendar.SUNDAY);

        String[] dayLabels = { "Sun", "Mon", "Tue", "Wed", "Thu", "Fri", "Sat" };
        int selectedDayIndex = selectedDate.get(Calendar.DAY_OF_WEEK) - 1;

        String[] dateKeys = new String[7];
        for (int i = 0; i < 7; i++) {
            dateKeys[i] = DailyActivity.dateKey(cal.getTimeInMillis());
            cal.add(Calendar.DAY_OF_MONTH, 1);
        }

        float[] stepsData = new float[7];
        int[] loadedCount = { 0 };

        for (int i = 0; i < 7; i++) {
            final int index = i;
            UserRepository.getInstance().getDailyActivity(dateKeys[i], activity -> {
                stepsData[index] = activity != null ? activity.getSteps() : 0;
                loadedCount[0]++;

                if (loadedCount[0] == 7 && isAdded()) {
                    requireActivity().runOnUiThread(() -> {
                        List<BarEntry> stepsEntries = new ArrayList<>();
                        for (int j = 0; j < 7; j++) {
                            stepsEntries.add(new BarEntry(j, stepsData[j]));
                        }

                        BarDataSet dataSet = new BarDataSet(stepsEntries, "Steps");
                        int[] colors = new int[7];
                        int accentColor = requireContext().getColor(R.color.accent);
                        int lightColor = requireContext().getColor(R.color.card_steps);
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

                        XAxis xAxis = chart.getXAxis();
                        xAxis.setValueFormatter(new IndexAxisValueFormatter(dayLabels));
                        xAxis.setPosition(XAxis.XAxisPosition.BOTTOM);
                        xAxis.setDrawGridLines(false);
                        xAxis.setGranularity(1f);
                        xAxis.setTextColor(requireContext().getColor(R.color.text_secondary_dark));

                        chart.getAxisLeft().setDrawGridLines(true);
                        chart.getAxisLeft().setGridColor(requireContext().getColor(R.color.divider_light));
                        chart.getAxisLeft().setTextColor(requireContext().getColor(R.color.text_secondary_dark));
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

        String[] dateKeys = new String[7];
        for (int i = 0; i < 7; i++) {
            dateKeys[i] = DailyActivity.dateKey(cal.getTimeInMillis());
            cal.add(Calendar.DAY_OF_MONTH, 1);
        }

        float[] minutesData = new float[7];
        int[] loadedCount = { 0 };

        for (int i = 0; i < 7; i++) {
            final int index = i;
            UserRepository.getInstance().getDailyActivity(dateKeys[i], activity -> {
                minutesData[index] = activity != null ? activity.getActiveMinutes() : 0;
                loadedCount[0]++;

                if (loadedCount[0] == 7 && isAdded()) {
                    requireActivity().runOnUiThread(() -> {
                        List<Entry> entries = new ArrayList<>();
                        for (int j = 0; j < 7; j++) {
                            entries.add(new Entry(j, minutesData[j]));
                        }

                        LineDataSet dataSet = new LineDataSet(entries, "Exercise Minutes");
                        dataSet.setColor(requireContext().getColor(R.color.accent));
                        dataSet.setCircleColor(requireContext().getColor(R.color.accent));
                        dataSet.setLineWidth(2f);
                        dataSet.setCircleRadius(4f);
                        dataSet.setDrawCircleHole(true);
                        dataSet.setCircleHoleRadius(2f);
                        dataSet.setDrawFilled(true);
                        dataSet.setFillColor(requireContext().getColor(R.color.accent));
                        dataSet.setFillAlpha(50);
                        dataSet.setDrawValues(false);
                        dataSet.setMode(LineDataSet.Mode.CUBIC_BEZIER);

                        LineData lineData = new LineData(dataSet);

                        chart.setData(lineData);
                        chart.getDescription().setEnabled(false);
                        chart.getLegend().setEnabled(false);
                        chart.setDrawGridBackground(false);
                        chart.setTouchEnabled(false);

                        XAxis xAxis = chart.getXAxis();
                        xAxis.setValueFormatter(new IndexAxisValueFormatter(dayLabels));
                        xAxis.setPosition(XAxis.XAxisPosition.BOTTOM);
                        xAxis.setDrawGridLines(false);
                        xAxis.setGranularity(1f);
                        xAxis.setTextColor(requireContext().getColor(R.color.text_secondary_dark));

                        chart.getAxisLeft().setDrawGridLines(true);
                        chart.getAxisLeft().setGridColor(requireContext().getColor(R.color.divider_light));
                        chart.getAxisLeft().setTextColor(requireContext().getColor(R.color.text_secondary_dark));
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

        String[][] exercises = {
                { "Squats", String.valueOf(activity.getSquatReps()), "#4CAF50" },
                { "Bicep Curls", String.valueOf(activity.getBicepCurlReps()), "#2196F3" },
                { "Lunges", String.valueOf(activity.getLungeReps()), "#FF9800" },
                { "Planks", String.valueOf(activity.getPlankSeconds()) + "s", "#9C27B0" }
        };

        for (String[] exercise : exercises) {
            if (Integer.parseInt(exercise[1].replace("s", "")) > 0) {
                LinearLayout row = new LinearLayout(requireContext());
                row.setLayoutParams(new LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));
                row.setOrientation(LinearLayout.HORIZONTAL);
                row.setPadding(0, dpToPx(8), 0, dpToPx(8));
                row.setGravity(Gravity.CENTER_VERTICAL);

                View colorDot = new View(requireContext());
                LinearLayout.LayoutParams dotParams = new LinearLayout.LayoutParams(dpToPx(8), dpToPx(8));
                dotParams.setMarginEnd(dpToPx(12));
                colorDot.setLayoutParams(dotParams);
                colorDot.setBackgroundColor(Color.parseColor(exercise[2]));

                TextView tvName = new TextView(requireContext());
                LinearLayout.LayoutParams nameParams = new LinearLayout.LayoutParams(
                        0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
                tvName.setLayoutParams(nameParams);
                tvName.setText(exercise[0]);
                tvName.setTextColor(requireContext().getColor(R.color.text_primary_dark));
                tvName.setTextSize(14);

                TextView tvValue = new TextView(requireContext());
                tvValue.setLayoutParams(new LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT));
                tvValue.setText(exercise[1] + (exercise[0].equals("Planks") ? "" : " reps"));
                tvValue.setTextColor(requireContext().getColor(R.color.text_secondary_dark));
                tvValue.setTextSize(14);

                row.addView(colorDot);
                row.addView(tvName);
                row.addView(tvValue);
                container.addView(row);
            }
        }

        if (container.getChildCount() == 0) {
            TextView placeholder = new TextView(requireContext());
            placeholder.setLayoutParams(new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));
            placeholder.setText("No exercises recorded for this day");
            placeholder.setTextColor(requireContext().getColor(R.color.text_secondary_dark));
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
            if (!isAdded())
                return;
            requireActivity().runOnUiThread(() -> {
                if (activity != null) {
                    todayActivity = activity;
                    updateStatsUI(activity);
                } else {
                    todayActivity = new DailyActivity(dateKey);
                    updateStatsUI(todayActivity);
                }
            });
        });

        updateWaterUI();
    }

    private void updateStatsUI(DailyActivity activity) {
        if (!isAdded())
            return;

        tvCalories.setText(activity.getCalories() + " Cal");

        int stepGoal = fitnessDataManager.getStepGoal();
        tvSteps.setText(activity.getSteps() + "/" + stepGoal);

        int trainingGoal = fitnessDataManager.getActiveTimeGoal();
        int activeMinutes = activity.getActiveMinutes();
        int trainingPercent = trainingGoal > 0 ? Math.min(100, (activeMinutes * 100) / trainingGoal) : 0;
        tvTrainingPercent.setText(trainingPercent + "%");
        progressTraining.setProgress(trainingPercent);

        float sleepHours = activity.getSleepHours();
        if (sleepHours > 0) {
            tvSleep.setText(String.format(Locale.US, "%.1f hrs", sleepHours));
        } else {
            tvSleep.setText("-- hrs");
        }

        tvHeartRate.setText("-- Bpm");
    }

    private void updateWaterUI() {
        if (!isAdded())
            return;
        int cups = fitnessDataManager.getWaterCupsToday();
        int goal = fitnessDataManager.getWaterGoal();
        int progress = fitnessDataManager.getWaterProgressPercent();

        tvWaterCups.setText(cups + "/" + goal + " Cups");
        progressWater.setProgress(progress);
    }

    private void scheduleWaterReminders() {
        if (!isAdded())
            return;
        try {
            WaterReminderService.scheduleReminders(requireContext());
        } catch (SecurityException e) {
            e.printStackTrace();
        }
    }

    private void showStepsBottomSheet() {
        if (!isAdded())
            return;
        BottomSheetDialog bottomSheetDialog = new BottomSheetDialog(requireContext());
        View sheetView = LayoutInflater.from(requireContext()).inflate(R.layout.bottom_sheet_steps, null);
        bottomSheetDialog.setContentView(sheetView);

        ProgressBar progressSteps = sheetView.findViewById(R.id.progressSteps);
        TextView tvCurrentSteps = sheetView.findViewById(R.id.tvCurrentSteps);
        TextView tvGoalSteps = sheetView.findViewById(R.id.tvGoalSteps);
        TextView tvStepMotivation = sheetView.findViewById(R.id.tvStepMotivation);
        TextView tvDistance = sheetView.findViewById(R.id.tvDistance);
        TextView tvStepCalories = sheetView.findViewById(R.id.tvStepCalories);
        TextView tvActiveTime = sheetView.findViewById(R.id.tvActiveTime);

        int currentSteps = fitnessDataManager.getStepsToday();
        int goalSteps = fitnessDataManager.getStepGoal();

        if (todayActivity != null && todayActivity.getSteps() > currentSteps) {
            currentSteps = todayActivity.getSteps();
        }

        int progressPercent = goalSteps > 0 ? (int) ((currentSteps * 100.0f) / goalSteps) : 0;
        progressPercent = Math.min(progressPercent, 100);

        tvCurrentSteps.setText(String.valueOf(currentSteps));
        tvGoalSteps.setText("of " + goalSteps);
        progressSteps.setMax(100);
        progressSteps.setProgress(progressPercent);

        float distanceKm = fitnessDataManager.getDistanceToday();
        int calories = fitnessDataManager.getCaloriesToday();
        int activeMinutes = fitnessDataManager.getActiveMinutesToday();

        tvDistance.setText(String.format(Locale.US, "%.1f km", distanceKm));
        tvStepCalories.setText(String.valueOf(calories));
        tvActiveTime.setText(activeMinutes + " min");

        if (progressPercent >= 100) {
            tvStepMotivation.setText("Goal achieved! You're a champion!");
        } else if (progressPercent >= 75) {
            tvStepMotivation.setText("Almost there! " + (goalSteps - currentSteps) + " steps to go!");
        } else if (progressPercent >= 50) {
            tvStepMotivation.setText("Halfway there! Keep moving!");
        } else if (progressPercent >= 25) {
            tvStepMotivation.setText("Great start! Keep it up!");
        } else {
            tvStepMotivation.setText("Start your day with a walk!");
        }

        sheetView.findViewById(R.id.btnSetStepGoal).setOnClickListener(v -> bottomSheetDialog.dismiss());
        sheetView.findViewById(R.id.btnCloseSteps).setOnClickListener(v -> bottomSheetDialog.dismiss());

        bottomSheetDialog.show();
    }

    private boolean isSameDay(Calendar cal1, Calendar cal2) {
        return cal1.get(Calendar.YEAR) == cal2.get(Calendar.YEAR)
                && cal1.get(Calendar.DAY_OF_YEAR) == cal2.get(Calendar.DAY_OF_YEAR);
    }

    private int dpToPx(int dp) {
        return (int) (dp * getResources().getDisplayMetrics().density);
    }

    private static class DayItem {
        Calendar date;
        String dayLabel;
        int dayNumber;
        boolean isToday;
        boolean isSelected;
    }
}
