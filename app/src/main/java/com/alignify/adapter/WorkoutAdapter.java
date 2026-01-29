package com.alignify.adapter;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.alignify.R;
import com.alignify.data.WorkoutSession;

import java.util.ArrayList;
import java.util.List;

/**
 * RecyclerView adapter for displaying workout history.
 */
public class WorkoutAdapter extends RecyclerView.Adapter<WorkoutAdapter.WorkoutViewHolder> {

    private List<WorkoutSession> workouts = new ArrayList<>();
    private OnWorkoutClickListener listener;

    public interface OnWorkoutClickListener {
        void onWorkoutClick(WorkoutSession workout);
    }

    public void setOnWorkoutClickListener(OnWorkoutClickListener listener) {
        this.listener = listener;
    }

    public void setWorkouts(List<WorkoutSession> workouts) {
        this.workouts = workouts != null ? workouts : new ArrayList<>();
        notifyDataSetChanged();
    }

    public void addWorkout(WorkoutSession workout) {
        workouts.add(0, workout);
        notifyItemInserted(0);
    }

    @NonNull
    @Override
    public WorkoutViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_workout, parent, false);
        return new WorkoutViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull WorkoutViewHolder holder, int position) {
        WorkoutSession workout = workouts.get(position);
        holder.bind(workout);

        holder.itemView.setOnClickListener(v -> {
            if (listener != null) {
                listener.onWorkoutClick(workout);
            }
        });
    }

    @Override
    public int getItemCount() {
        return workouts.size();
    }

    static class WorkoutViewHolder extends RecyclerView.ViewHolder {

        private final TextView exerciseIcon;
        private final TextView exerciseName;
        private final TextView repsCount;
        private final TextView duration;
        private final TextView timestamp;
        private final TextView accuracyScore;

        public WorkoutViewHolder(@NonNull View itemView) {
            super(itemView);
            exerciseIcon = itemView.findViewById(R.id.exerciseIcon);
            exerciseName = itemView.findViewById(R.id.exerciseName);
            repsCount = itemView.findViewById(R.id.repsCount);
            duration = itemView.findViewById(R.id.duration);
            timestamp = itemView.findViewById(R.id.timestamp);
            accuracyScore = itemView.findViewById(R.id.accuracyScore);
        }

        public void bind(WorkoutSession workout) {
            exerciseIcon.setText(workout.getExerciseIcon());
            exerciseName.setText(workout.getExerciseDisplayName());
            repsCount.setText(workout.getReps() + " reps");
            duration.setText(workout.getFormattedDuration());
            timestamp.setText(workout.getFormattedTimestamp());

            int accuracy = workout.getAccuracyScore();
            accuracyScore.setText(accuracy + "%");

            // Color based on accuracy
            int colorRes;
            if (accuracy >= 80) {
                colorRes = R.color.correct_green;
            } else if (accuracy >= 60) {
                colorRes = R.color.accent;
            } else {
                colorRes = R.color.error_red;
            }
            accuracyScore.setTextColor(itemView.getContext().getColor(colorRes));
        }
    }
}
