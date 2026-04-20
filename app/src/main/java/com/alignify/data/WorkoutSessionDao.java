package com.alignify.data;

import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.Query;

import java.util.List;

/**
 * Data Access Object for workout session operations.
 */
@Dao
public interface WorkoutSessionDao {

    @Insert
    long insert(WorkoutSessionEntity session);

    @Query("SELECT * FROM workout_sessions WHERE timestamp >= :startTime ORDER BY timestamp DESC")
    List<WorkoutSessionEntity> getSessionsSince(long startTime);

    @Query("DELETE FROM workout_sessions")
    void deleteAll();
}
