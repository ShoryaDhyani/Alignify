package com.alignify;

import android.os.Bundle;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.alignify.data.DailyActivity;
import com.alignify.data.UserRepository;
import com.google.android.material.chip.Chip;

import java.text.NumberFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * History and Analytics screen.
 * Shows weekly/monthly summaries, charts, and activity timeline.
 */
public class HistoryActivity extends AppCompatActivity {

    private static final String TAG = "HistoryActivity";

    // Views
    private TextView tvTotalSteps;
    private TextView tvTotalCalories;
    private TextView tvTotalWorkouts;
    private LinearLayout chartSteps;
    private RecyclerView recyclerTimeline;
    private LinearLayout emptyState;
    private Chip chipWeek;
    private Chip chipMonth;

    // Data
    private List<DailyActivity> weeklyData = new ArrayList<>();
    private List<Map<String, Object>> activities = new ArrayList<>();
    private boolean isWeekView = true;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_history);

        initViews();
        setupListeners();
        loadData();
    }

    private void initViews() {
        tvTotalSteps = findViewById(R.id.tvTotalSteps);
        tvTotalCalories = findViewById(R.id.tvTotalCalories);
        tvTotalWorkouts = findViewById(R.id.tvTotalWorkouts);
        chartSteps = findViewById(R.id.chartSteps);
        recyclerTimeline = findViewById(R.id.recyclerTimeline);
        emptyState = findViewById(R.id.emptyState);
        chipWeek = findViewById(R.id.chipWeek);
        chipMonth = findViewById(R.id.chipMonth);

        recyclerTimeline.setLayoutManager(new LinearLayoutManager(this));
    }

    private void setupListeners() {
        findViewById(R.id.btnBack).setOnClickListener(v -> finish());

        chipWeek.setOnClickListener(v -> {
            isWeekView = true;
            loadData();
        });

        chipMonth.setOnClickListener(v -> {
            isWeekView = false;
            loadData();
        });
    }

    private void loadData() {
        int days = isWeekView ? 7 : 30;

        UserRepository.getInstance().getWeeklyActivities(days, activities -> {
            runOnUiThread(() -> {
                if (activities != null && !activities.isEmpty()) {
                    weeklyData = activities;
                    updateUI();
                    emptyState.setVisibility(View.GONE);
                } else {
                    emptyState.setVisibility(View.VISIBLE);
                }
            });
        });

        // Load activity timeline
        loadActivityTimeline();
    }

    private void loadActivityTimeline() {
        long startTime = System.currentTimeMillis() - (isWeekView ? 7 : 30) * 24 * 60 * 60 * 1000L;
        long endTime = System.currentTimeMillis();

        UserRepository.getInstance().getActivities(startTime, endTime, activityList -> {
            runOnUiThread(() -> {
                this.activities = activityList;
                // TODO: Set adapter for recyclerTimeline
            });
        });
    }

    private void updateUI() {
        // Calculate totals
        int totalSteps = 0;
        int totalCalories = 0;
        int totalWorkouts = 0;
        int maxSteps = 1; // Avoid division by zero

        for (DailyActivity day : weeklyData) {
            totalSteps += day.getSteps();
            totalCalories += day.getCalories();
            totalWorkouts += day.getWorkoutsCount();
            if (day.getSteps() > maxSteps) {
                maxSteps = day.getSteps();
            }
        }

        // Update summary
        NumberFormat nf = NumberFormat.getNumberInstance(Locale.US);
        tvTotalSteps.setText(nf.format(totalSteps));
        tvTotalCalories.setText(nf.format(totalCalories));
        tvTotalWorkouts.setText(String.valueOf(totalWorkouts));

        // Draw bar chart
        drawBarChart(maxSteps);
    }

    private void drawBarChart(int maxSteps) {
        chartSteps.removeAllViews();

        int barCount = Math.min(weeklyData.size(), 7);

        for (int i = 0; i < barCount; i++) {
            DailyActivity day = weeklyData.get(weeklyData.size() - barCount + i);

            LinearLayout barContainer = new LinearLayout(this);
            barContainer.setLayoutParams(new LinearLayout.LayoutParams(
                    0, LinearLayout.LayoutParams.MATCH_PARENT, 1f));
            barContainer.setOrientation(LinearLayout.VERTICAL);
            barContainer.setGravity(android.view.Gravity.BOTTOM | android.view.Gravity.CENTER_HORIZONTAL);
            barContainer.setPadding(8, 0, 8, 0);

            // Bar
            View bar = new View(this);
            int heightPercent = (int) ((day.getSteps() / (float) maxSteps) * 100);
            int barHeight = (heightPercent * 120) / 100; // Max height 120dp
            LinearLayout.LayoutParams barParams = new LinearLayout.LayoutParams(
                    dpToPx(24), dpToPx(Math.max(4, barHeight)));
            bar.setLayoutParams(barParams);
            bar.setBackgroundResource(R.drawable.rounded_bar);

            // Day label
            TextView label = new TextView(this);
            label.setText(getDayLabel(day.getDate()));
            label.setTextSize(10);
            label.setTextColor(getColor(R.color.text_secondary));
            label.setGravity(android.view.Gravity.CENTER);

            barContainer.addView(bar);
            barContainer.addView(label);
            chartSteps.addView(barContainer);
        }
    }

    private String getDayLabel(String date) {
        // Extract day from yyyy-MM-dd
        if (date != null && date.length() >= 10) {
            String day = date.substring(8, 10);
            return day;
        }
        return "";
    }

    private int dpToPx(int dp) {
        return (int) (dp * getResources().getDisplayMetrics().density);
    }
}
