package com.alignify.data;

import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.Query;

import java.util.List;

/**
 * Data Access Object for fitness activity operations.
 */
@Dao
public interface FitnessActivityDao {

    @Insert
    long insert(FitnessActivityEntity activity);

    @Query("SELECT * FROM fitness_activities WHERE startTime >= :startTime ORDER BY startTime DESC")
    List<FitnessActivityEntity> getActivitiesSince(long startTime);

    @Query("SELECT * FROM fitness_activities WHERE startTime >= :startTime AND startTime <= :endTime ORDER BY startTime DESC")
    List<FitnessActivityEntity> getActivitiesBetween(long startTime, long endTime);

    @Query("DELETE FROM fitness_activities")
    void deleteAll();
}
