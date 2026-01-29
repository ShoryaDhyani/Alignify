package com.alignify;

import android.graphics.Color;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

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
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.Query;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Activity displaying progress statistics with charts.
 */
public class StatsActivity extends AppCompatActivity {

    private static final String TAG = "StatsActivity";

    private TextView totalWorkoutsValue;
    private TextView totalRepsValue;
    private TextView avgAccuracyValue;
    private LineChart accuracyChart;
    private BarChart frequencyChart;
    private ProgressBar loadingIndicator;

    private FirebaseFirestore firestore;
    private FirebaseUser currentUser;

    // Data for charts
    private Map<String, Integer> dailyWorkoutCounts = new HashMap<>();
    private Map<String, Integer> dailyAccuracySums = new HashMap<>();
    private Map<String, Integer> dailyAccuracyCounts = new HashMap<>();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_stats);

        firestore = FirebaseFirestore.getInstance();
        currentUser = FirebaseAuth.getInstance().getCurrentUser();

        initViews();
        loadStats();
    }

    private void initViews() {
        findViewById(R.id.btnBack).setOnClickListener(v -> finish());

        totalWorkoutsValue = findViewById(R.id.totalWorkoutsValue);
        totalRepsValue = findViewById(R.id.totalRepsValue);
        avgAccuracyValue = findViewById(R.id.avgAccuracyValue);
        accuracyChart = findViewById(R.id.accuracyChart);
        frequencyChart = findViewById(R.id.frequencyChart);
        loadingIndicator = findViewById(R.id.loadingIndicator);

        setupCharts();
    }

    private void setupCharts() {
        // Setup LineChart (Accuracy)
        accuracyChart.getDescription().setEnabled(false);
        accuracyChart.setTouchEnabled(true);
        accuracyChart.setDragEnabled(true);
        accuracyChart.setScaleEnabled(false);
        accuracyChart.setPinchZoom(false);
        accuracyChart.getLegend().setEnabled(false);
        accuracyChart.getAxisLeft().setTextColor(Color.WHITE);
        accuracyChart.getAxisRight().setEnabled(false);
        accuracyChart.getXAxis().setTextColor(Color.WHITE);
        accuracyChart.getXAxis().setPosition(XAxis.XAxisPosition.BOTTOM);
        accuracyChart.setNoDataTextColor(Color.WHITE);

        // Setup BarChart (Frequency)
        frequencyChart.getDescription().setEnabled(false);
        frequencyChart.setTouchEnabled(true);
        frequencyChart.setDragEnabled(true);
        frequencyChart.setScaleEnabled(false);
        frequencyChart.setPinchZoom(false);
        frequencyChart.getLegend().setEnabled(false);
        frequencyChart.getAxisLeft().setTextColor(Color.WHITE);
        frequencyChart.getAxisRight().setEnabled(false);
        frequencyChart.getXAxis().setTextColor(Color.WHITE);
        frequencyChart.getXAxis().setPosition(XAxis.XAxisPosition.BOTTOM);
        frequencyChart.setNoDataTextColor(Color.WHITE);
    }

    private void loadStats() {
        if (currentUser == null) {
            hideLoading();
            return;
        }

        showLoading();

        // Load last 30 days of workouts
        long thirtyDaysAgo = System.currentTimeMillis() - (30L * 24 * 60 * 60 * 1000);

        firestore.collection("users")
                .document(currentUser.getUid())
                .collection("workouts")
                .whereGreaterThan("timestamp", thirtyDaysAgo)
                .orderBy("timestamp", Query.Direction.DESCENDING)
                .get()
                .addOnSuccessListener(querySnapshot -> {
                    hideLoading();

                    if (querySnapshot.isEmpty()) {
                        updateSummaryStats(0, 0, 0);
                        return;
                    }

                    int totalWorkouts = querySnapshot.size();
                    int totalReps = 0;
                    int totalAccuracy = 0;

                    for (DocumentSnapshot doc : querySnapshot.getDocuments()) {
                        Long reps = doc.getLong("reps");
                        Long accuracy = doc.getLong("accuracyScore");
                        Long timestamp = doc.getLong("timestamp");

                        if (reps != null) {
                            totalReps += reps.intValue();
                        }
                        if (accuracy != null) {
                            totalAccuracy += accuracy.intValue();
                        }

                        // Aggregate by day
                        if (timestamp != null) {
                            String dateKey = getDateKey(timestamp);
                            dailyWorkoutCounts.put(dateKey,
                                    dailyWorkoutCounts.getOrDefault(dateKey, 0) + 1);

                            if (accuracy != null) {
                                dailyAccuracySums.put(dateKey,
                                        dailyAccuracySums.getOrDefault(dateKey, 0) + accuracy.intValue());
                                dailyAccuracyCounts.put(dateKey,
                                        dailyAccuracyCounts.getOrDefault(dateKey, 0) + 1);
                            }
                        }
                    }

                    int avgAccuracy = totalWorkouts > 0 ? totalAccuracy / totalWorkouts : 0;
                    updateSummaryStats(totalWorkouts, totalReps, avgAccuracy);
                    updateCharts();
                })
                .addOnFailureListener(e -> {
                    hideLoading();
                    Log.e(TAG, "Error loading stats", e);
                    updateSummaryStats(0, 0, 0);
                });
    }

    private void updateSummaryStats(int workouts, int reps, int accuracy) {
        totalWorkoutsValue.setText(String.valueOf(workouts));
        totalRepsValue.setText(String.valueOf(reps));
        avgAccuracyValue.setText(accuracy + "%");
    }

    private void updateCharts() {
        // Get last 7 days
        List<String> last7Days = getLast7Days();
        List<String> dayLabels = new ArrayList<>();

        // Accuracy Line Chart
        List<Entry> accuracyEntries = new ArrayList<>();
        for (int i = 0; i < last7Days.size(); i++) {
            String dateKey = last7Days.get(i);
            int sum = dailyAccuracySums.getOrDefault(dateKey, 0);
            int count = dailyAccuracyCounts.getOrDefault(dateKey, 0);
            float avgAccuracy = count > 0 ? (float) sum / count : 0;
            accuracyEntries.add(new Entry(i, avgAccuracy));
            dayLabels.add(getDayLabel(dateKey));
        }

        LineDataSet accuracyDataSet = new LineDataSet(accuracyEntries, "Accuracy");
        accuracyDataSet.setColor(getColor(R.color.accent));
        accuracyDataSet.setValueTextColor(Color.WHITE);
        accuracyDataSet.setLineWidth(2f);
        accuracyDataSet.setCircleColor(getColor(R.color.accent));
        accuracyDataSet.setCircleRadius(4f);
        accuracyDataSet.setDrawValues(false);
        accuracyDataSet.setMode(LineDataSet.Mode.CUBIC_BEZIER);

        LineData lineData = new LineData(accuracyDataSet);
        accuracyChart.setData(lineData);
        accuracyChart.getXAxis().setValueFormatter(new IndexAxisValueFormatter(dayLabels));
        accuracyChart.invalidate();

        // Frequency Bar Chart
        List<BarEntry> frequencyEntries = new ArrayList<>();
        for (int i = 0; i < last7Days.size(); i++) {
            String dateKey = last7Days.get(i);
            int count = dailyWorkoutCounts.getOrDefault(dateKey, 0);
            frequencyEntries.add(new BarEntry(i, count));
        }

        BarDataSet frequencyDataSet = new BarDataSet(frequencyEntries, "Workouts");
        frequencyDataSet.setColor(getColor(R.color.correct_green));
        frequencyDataSet.setValueTextColor(Color.WHITE);

        BarData barData = new BarData(frequencyDataSet);
        barData.setBarWidth(0.6f);
        frequencyChart.setData(barData);
        frequencyChart.getXAxis().setValueFormatter(new IndexAxisValueFormatter(dayLabels));
        frequencyChart.invalidate();
    }

    private List<String> getLast7Days() {
        List<String> days = new ArrayList<>();
        Calendar cal = Calendar.getInstance();

        for (int i = 6; i >= 0; i--) {
            cal.setTimeInMillis(System.currentTimeMillis());
            cal.add(Calendar.DAY_OF_YEAR, -i);
            SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd", Locale.US);
            days.add(sdf.format(cal.getTime()));
        }

        return days;
    }

    private String getDateKey(long timestamp) {
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd", Locale.US);
        return sdf.format(new Date(timestamp));
    }

    private String getDayLabel(String dateKey) {
        try {
            SimpleDateFormat inputFormat = new SimpleDateFormat("yyyy-MM-dd", Locale.US);
            SimpleDateFormat outputFormat = new SimpleDateFormat("EEE", Locale.US);
            Date date = inputFormat.parse(dateKey);
            return outputFormat.format(date);
        } catch (Exception e) {
            return "";
        }
    }

    private void showLoading() {
        loadingIndicator.setVisibility(View.VISIBLE);
    }

    private void hideLoading() {
        loadingIndicator.setVisibility(View.GONE);
    }
}
