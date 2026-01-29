package com.alignify;

import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.alignify.adapter.WorkoutAdapter;
import com.alignify.data.WorkoutSession;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.Query;

import java.util.ArrayList;
import java.util.List;

/**
 * Activity displaying workout history and statistics.
 */
public class HistoryActivity extends AppCompatActivity {

    private static final String TAG = "HistoryActivity";
    private static final int PAGE_SIZE = 20;

    // Views
    private TextView totalWorkouts;
    private TextView totalReps;
    private TextView avgAccuracy;
    private RecyclerView workoutRecyclerView;
    private LinearLayout emptyState;
    private ProgressBar loadingIndicator;

    // Data
    private WorkoutAdapter adapter;
    private FirebaseFirestore firestore;
    private FirebaseUser currentUser;

    // Stats
    private int totalWorkoutCount = 0;
    private int totalRepCount = 0;
    private int totalAccuracySum = 0;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_history);

        // Initialize Firebase
        firestore = FirebaseFirestore.getInstance();
        currentUser = FirebaseAuth.getInstance().getCurrentUser();

        initViews();
        setupRecyclerView();
        loadWorkoutHistory();
    }

    private void initViews() {
        // Header back button
        findViewById(R.id.btnBack).setOnClickListener(v -> finish());

        // Stats views
        totalWorkouts = findViewById(R.id.totalWorkouts);
        totalReps = findViewById(R.id.totalReps);
        avgAccuracy = findViewById(R.id.avgAccuracy);

        // RecyclerView
        workoutRecyclerView = findViewById(R.id.workoutRecyclerView);
        emptyState = findViewById(R.id.emptyState);
        loadingIndicator = findViewById(R.id.loadingIndicator);
    }

    private void setupRecyclerView() {
        adapter = new WorkoutAdapter();
        workoutRecyclerView.setLayoutManager(new LinearLayoutManager(this));
        workoutRecyclerView.setAdapter(adapter);

        adapter.setOnWorkoutClickListener(workout -> {
            // Show workout details (could expand to detail view later)
            Toast.makeText(this,
                    workout.getExerciseDisplayName() + ": " + workout.getReps() + " reps, "
                            + workout.getAccuracyScore() + "% accuracy",
                    Toast.LENGTH_SHORT).show();
        });
    }

    private void loadWorkoutHistory() {
        if (currentUser == null) {
            showEmptyState();
            return;
        }

        showLoading();

        firestore.collection("users")
                .document(currentUser.getUid())
                .collection("workouts")
                .orderBy("timestamp", Query.Direction.DESCENDING)
                .limit(PAGE_SIZE)
                .get()
                .addOnSuccessListener(querySnapshot -> {
                    hideLoading();

                    if (querySnapshot.isEmpty()) {
                        showEmptyState();
                        return;
                    }

                    List<WorkoutSession> workouts = new ArrayList<>();

                    for (DocumentSnapshot doc : querySnapshot.getDocuments()) {
                        WorkoutSession session = new WorkoutSession();
                        session.setId(doc.getId());
                        session.setExercise(doc.getString("exercise"));

                        Long reps = doc.getLong("reps");
                        session.setReps(reps != null ? reps.intValue() : 0);

                        Long duration = doc.getLong("duration");
                        session.setDuration(duration != null ? duration.intValue() : 0);

                        Long errors = doc.getLong("errorsCount");
                        session.setErrorsCount(errors != null ? errors.intValue() : 0);

                        Long timestamp = doc.getLong("timestamp");
                        session.setTimestamp(timestamp != null ? timestamp : System.currentTimeMillis());

                        // Check if accuracyScore is already stored
                        Long accuracy = doc.getLong("accuracyScore");
                        if (accuracy != null) {
                            session.setAccuracyScore(accuracy.intValue());
                        }

                        workouts.add(session);

                        // Aggregate stats
                        totalWorkoutCount++;
                        totalRepCount += session.getReps();
                        totalAccuracySum += session.getAccuracyScore();
                    }

                    adapter.setWorkouts(workouts);
                    updateStats();
                    showContent();
                })
                .addOnFailureListener(e -> {
                    hideLoading();
                    Log.e(TAG, "Error loading workouts", e);
                    Toast.makeText(this, "Failed to load history", Toast.LENGTH_SHORT).show();
                    showEmptyState();
                });
    }

    private void updateStats() {
        totalWorkouts.setText(String.valueOf(totalWorkoutCount));
        totalReps.setText(String.valueOf(totalRepCount));

        if (totalWorkoutCount > 0) {
            int avg = totalAccuracySum / totalWorkoutCount;
            avgAccuracy.setText(avg + "%");
        } else {
            avgAccuracy.setText("--");
        }
    }

    private void showLoading() {
        loadingIndicator.setVisibility(View.VISIBLE);
        workoutRecyclerView.setVisibility(View.GONE);
        emptyState.setVisibility(View.GONE);
    }

    private void hideLoading() {
        loadingIndicator.setVisibility(View.GONE);
    }

    private void showContent() {
        workoutRecyclerView.setVisibility(View.VISIBLE);
        emptyState.setVisibility(View.GONE);
    }

    private void showEmptyState() {
        workoutRecyclerView.setVisibility(View.GONE);
        emptyState.setVisibility(View.VISIBLE);
    }
}
