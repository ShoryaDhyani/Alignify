package com.alignify.data.sleep;

import androidx.room.Dao;
import androidx.room.Delete;
import androidx.room.Insert;
import androidx.room.Query;
import androidx.room.Update;

import java.util.List;

/**
 * Data Access Object for SleepSession.
 * Provides CRUD operations and date-based queries.
 */
@Dao
public interface SleepSessionDao {

    @Insert
    long insert(SleepSession session);

    @Query("SELECT * FROM sleep_sessions WHERE date = :date LIMIT 1")
    SleepSession getByDate(String date);

    @Query("SELECT * FROM sleep_sessions ORDER BY startTimeMillis DESC LIMIT :limit")
    List<SleepSession> getRecent(int limit);

    @Query("SELECT * FROM sleep_sessions WHERE date BETWEEN :startDate AND :endDate ORDER BY date ASC")
    List<SleepSession> getRange(String startDate, String endDate);

    @Update
    void update(SleepSession session);

    @Delete
    void delete(SleepSession session);
}
