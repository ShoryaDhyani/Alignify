package com.alignify.data;

import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;

import java.util.List;

/**
 * Data Access Object for daily activity operations.
 */
@Dao
public interface DailyActivityDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void insertOrUpdate(DailyActivityEntity activity);

    @Query("SELECT * FROM daily_activity WHERE date = :date LIMIT 1")
    DailyActivityEntity getByDate(String date);

    @Query("SELECT * FROM daily_activity WHERE date >= :startDate ORDER BY date ASC LIMIT :limit")
    List<DailyActivityEntity> getActivitiesSince(String startDate, int limit);

    @Query("DELETE FROM daily_activity WHERE date < :dateThreshold")
    int deleteOlderThan(String dateThreshold);

    @Query("DELETE FROM daily_activity")
    void deleteAll();
}
