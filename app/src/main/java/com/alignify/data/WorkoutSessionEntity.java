package com.alignify.data;

import androidx.room.Entity;
import androidx.room.PrimaryKey;

/**
 * Room entity for individual workout sessions.
 * Replaces Firestore workouts sub-collection.
 */
@Entity(tableName = "workout_sessions")
public class WorkoutSessionEntity {

    @PrimaryKey(autoGenerate = true)
    public long id;

    public String exercise;
    public int reps;
    public int duration; // seconds
    public int errorsCount;
    public long timestamp;
}
