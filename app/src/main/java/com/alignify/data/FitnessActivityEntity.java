package com.alignify.data;

import androidx.room.Entity;
import androidx.room.PrimaryKey;

/**
 * Room entity for auto-detected or manual fitness activities.
 * Replaces Firestore activities sub-collection.
 */
@Entity(tableName = "fitness_activities")
public class FitnessActivityEntity {

    @PrimaryKey(autoGenerate = true)
    public long id;

    public String type;      // e.g., "walking", "running"
    public String source;    // "auto", "manual", "ai"
    public long startTime;
    public long endTime;
    public int duration;     // seconds
    public float distance;   // km
    public int calories;
    public long timestamp;
}
