package com.alignify;

import android.graphics.Color;
import android.os.Bundle;
import android.view.View;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.alignify.adapter.TimelineAdapter;
import com.alignify.data.TimelineItem;
import com.alignify.engine.CaloriesEngine;
import com.alignify.util.StepCounterHelper;
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
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.Query;
import com.google.firebase.firestore.QueryDocumentSnapshot;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Unified activity timeline showing steps, activities, and workouts.
 */
public class TimelineActivity extends AppCompatActivity {

    private RecyclerView timelineList;
    private View chartsContainer;
    private TextView tabTimeline, tabCharts;
    private View emptyState;
    private ProgressBar loadingIndicator;

    private BarChart stepsChart, activeMinutesChart;
    private LineChart caloriesChart;

    private TimelineAdapter adapter;
    private List<TimelineItem> timelineItems;
    private CaloriesEngine caloriesEngine;

    private boolean showingCharts = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_timeline);

        caloriesEngine = CaloriesEngine.getInstance(this);

        initViews();
        setupTabs();
        setupRecyclerView();
        loadTimelineData();
    }

    private void initViews() {
        findViewById(R.id.btnBack).setOnClickListener(v -> finish());

        timelineList = findViewById(R.id.timelineList);
        chartsContainer = findViewById(R.id.chartsContainer);
        tabTimeline = findViewById(R.id.tabTimeline);
        tabCharts = findViewById(R.id.tabCharts);
        emptyState = findViewById(R.id.emptyState);
        loadingIndicator = findViewById(R.id.loadingIndicator);

        stepsChart = findViewById(R.id.stepsChart);
        caloriesChart = findViewById(R.id.caloriesChart);
        activeMinutesChart = findViewById(R.id.activeMinutesChart);
    }

    private void setupTabs() {
        tabTimeline.setOnClickListener(v -> {
            showingCharts = false;
            updateTabUI();
        });

        tabCharts.setOnClickListener(v -> {
            showingCharts = true;
            updateTabUI();
            loadChartData();
        });
    }

    private void updateTabUI() {
        if (showingCharts) {
            tabCharts.setBackgroundResource(R.drawable.bg_tab_active);
            tabCharts.setTextColor(getResources().getColor(R.color.text_primary));
            tabTimeline.setBackground(null);
            tabTimeline.setTextColor(getResources().getColor(R.color.text_secondary));

            timelineList.setVisibility(View.GONE);
            chartsContainer.setVisibility(View.VISIBLE);
            emptyState.setVisibility(View.GONE);
        } else {
            tabTimeline.setBackgroundResource(R.drawable.bg_tab_active);
            tabTimeline.setTextColor(getResources().getColor(R.color.text_primary));
            tabCharts.setBackground(null);
            tabCharts.setTextColor(getResources().getColor(R.color.text_secondary));

            chartsContainer.setVisibility(View.GONE);
            timelineList.setVisibility(timelineItems.isEmpty() ? View.GONE : View.VISIBLE);
            emptyState.setVisibility(timelineItems.isEmpty() ? View.VISIBLE : View.GONE);
        }
    }

    private void setupRecyclerView() {
        timelineItems = new ArrayList<>();
        adapter = new TimelineAdapter(timelineItems);
        timelineList.setLayoutManager(new LinearLayoutManager(this));
        timelineList.setAdapter(adapter);
    }

    private void loadTimelineData() {
        loadingIndicator.setVisibility(View.VISIBLE);
        timelineItems.clear();

        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        if (user == null) {
            loadingIndicator.setVisibility(View.GONE);
            return;
        }

        FirebaseFirestore db = FirebaseFirestore.getInstance();

        // Add today's steps as first item
        int todaySteps = StepCounterHelper.getStepsToday(this);
        if (todaySteps > 0) {
            int calories = caloriesEngine.calculateStepCalories(todaySteps);
            timelineItems.add(new TimelineItem(
                    TimelineItem.Type.STEPS,
                    "Walking",
                    System.currentTimeMillis(),
                    todaySteps + " steps",
                    calories,
                    "auto"));
        }

        // Load workouts
        db.collection("users").document(user.getUid())
                .collection("workouts")
                .orderBy("timestamp", Query.Direction.DESCENDING)
                .limit(20)
                .get()
                .addOnSuccessListener(querySnapshot -> {
                    for (QueryDocumentSnapshot doc : querySnapshot) {
                        String exercise = doc.getString("exercise");
                        Long timestamp = doc.getLong("timestamp");
                        Long reps = doc.getLong("reps");
                        Long duration = doc.getLong("duration");
                        Double accuracy = doc.getDouble("accuracyScore");

                        if (exercise != null && timestamp != null) {
                            int durationMin = duration != null ? (int) (duration / 60) : 0;
                            int calories = caloriesEngine.calculateActivityCalories(
                                    CaloriesEngine.ActivityType.WORKOUT, durationMin);

                            String value = reps != null ? reps + " reps" : durationMin + " min";

                            timelineItems.add(new TimelineItem(
                                    TimelineItem.Type.WORKOUT,
                                    exercise,
                                    timestamp,
                                    value,
                                    calories,
                                    "ai"));
                        }
                    }

                    // Sort by timestamp descending
                    timelineItems.sort((a, b) -> Long.compare(b.getTimestamp(), a.getTimestamp()));

                    adapter.notifyDataSetChanged();
                    loadingIndicator.setVisibility(View.GONE);
                    emptyState.setVisibility(timelineItems.isEmpty() ? View.VISIBLE : View.GONE);
                    timelineList.setVisibility(timelineItems.isEmpty() ? View.GONE : View.VISIBLE);
                })
                .addOnFailureListener(e -> {
                    loadingIndicator.setVisibility(View.GONE);
                    emptyState.setVisibility(View.VISIBLE);
                });
    }

    private void loadChartData() {
        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        if (user == null)
            return;

        loadingIndicator.setVisibility(View.VISIBLE);

        // Prepare 7-day data structures
        String[] dayLabels = new String[7];
        int[] stepsData = new int[7];
        int[] caloriesData = new int[7];
        int[] activeMinutesData = new int[7];

        Calendar cal = Calendar.getInstance();
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd", Locale.US);
        SimpleDateFormat dayFormat = new SimpleDateFormat("EEE", Locale.US);

        // Fill labels
        for (int i = 6; i >= 0; i--) {
            cal.setTimeInMillis(System.currentTimeMillis());
            cal.add(Calendar.DAY_OF_YEAR, -(6 - i));
            dayLabels[i] = dayFormat.format(cal.getTime());
        }

        // Today's data
        stepsData[6] = StepCounterHelper.getStepsToday(this);
        caloriesData[6] = caloriesEngine.calculateStepCalories(stepsData[6]);
        activeMinutesData[6] = stepsData[6] / 100;

        // Load historical data from Firestore
        FirebaseFirestore db = FirebaseFirestore.getInstance();

        for (int i = 0; i < 6; i++) {
            cal.setTimeInMillis(System.currentTimeMillis());
            cal.add(Calendar.DAY_OF_YEAR, -(6 - i));
            String dateKey = sdf.format(cal.getTime());

            final int dayIndex = i;
            db.collection("daily_activity")
                    .document(user.getUid())
                    .collection("days")
                    .document(dateKey)
                    .get()
                    .addOnSuccessListener(doc -> {
                        if (doc.exists()) {
                            Long steps = doc.getLong("steps");
                            Long cals = doc.getLong("calories_active");
                            Long activeMin = doc.getLong("active_minutes");

                            if (steps != null)
                                stepsData[dayIndex] = steps.intValue();
                            if (cals != null)
                                caloriesData[dayIndex] = cals.intValue();
                            if (activeMin != null)
                                activeMinutesData[dayIndex] = activeMin.intValue();
                        }

                        // Update charts on last iteration
                        if (dayIndex == 5) {
                            updateCharts(dayLabels, stepsData, caloriesData, activeMinutesData);
                            loadingIndicator.setVisibility(View.GONE);
                        }
                    });
        }
    }

    private void updateCharts(String[] labels, int[] steps, int[] calories, int[] activeMinutes) {
        // Steps Bar Chart
        setupBarChart(stepsChart, labels, steps, Color.parseColor("#6C63FF"));

        // Calories Line Chart
        setupLineChart(caloriesChart, labels, calories, Color.parseColor("#FFC107"));

        // Active Minutes Bar Chart
        setupBarChart(activeMinutesChart, labels, activeMinutes, Color.parseColor("#4CAF50"));
    }

    private void setupBarChart(BarChart chart, String[] labels, int[] values, int color) {
        List<BarEntry> entries = new ArrayList<>();
        for (int i = 0; i < values.length; i++) {
            entries.add(new BarEntry(i, values[i]));
        }

        BarDataSet dataSet = new BarDataSet(entries, "");
        dataSet.setColor(color);
        dataSet.setDrawValues(false);

        BarData data = new BarData(dataSet);
        data.setBarWidth(0.7f);

        chart.setData(data);
        chart.getDescription().setEnabled(false);
        chart.getLegend().setEnabled(false);
        chart.setDrawGridBackground(false);
        chart.setDrawBorders(false);
        chart.setTouchEnabled(false);

        chart.getXAxis().setValueFormatter(new IndexAxisValueFormatter(labels));
        chart.getXAxis().setPosition(XAxis.XAxisPosition.BOTTOM);
        chart.getXAxis().setTextColor(Color.parseColor("#B3B3B3"));
        chart.getXAxis().setDrawGridLines(false);
        chart.getXAxis().setGranularity(1f);

        chart.getAxisLeft().setTextColor(Color.parseColor("#B3B3B3"));
        chart.getAxisLeft().setDrawGridLines(false);
        chart.getAxisRight().setEnabled(false);

        chart.invalidate();
    }

    private void setupLineChart(LineChart chart, String[] labels, int[] values, int color) {
        List<Entry> entries = new ArrayList<>();
        for (int i = 0; i < values.length; i++) {
            entries.add(new Entry(i, values[i]));
        }

        LineDataSet dataSet = new LineDataSet(entries, "");
        dataSet.setColor(color);
        dataSet.setCircleColor(color);
        dataSet.setLineWidth(2f);
        dataSet.setCircleRadius(4f);
        dataSet.setDrawValues(false);
        dataSet.setMode(LineDataSet.Mode.CUBIC_BEZIER);

        LineData data = new LineData(dataSet);

        chart.setData(data);
        chart.getDescription().setEnabled(false);
        chart.getLegend().setEnabled(false);
        chart.setDrawGridBackground(false);
        chart.setDrawBorders(false);
        chart.setTouchEnabled(false);

        chart.getXAxis().setValueFormatter(new IndexAxisValueFormatter(labels));
        chart.getXAxis().setPosition(XAxis.XAxisPosition.BOTTOM);
        chart.getXAxis().setTextColor(Color.parseColor("#B3B3B3"));
        chart.getXAxis().setDrawGridLines(false);
        chart.getXAxis().setGranularity(1f);

        chart.getAxisLeft().setTextColor(Color.parseColor("#B3B3B3"));
        chart.getAxisLeft().setDrawGridLines(false);
        chart.getAxisRight().setEnabled(false);

        chart.invalidate();
    }
}
