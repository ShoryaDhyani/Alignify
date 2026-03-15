package com.alignify.data.sleep;

import androidx.room.Entity;
import androidx.room.PrimaryKey;

/**
 * Room entity representing a single sleep session.
 * Stores bedtime, wake time, duration, quality score, and date.
 */
@Entity(tableName = "sleep_sessions")
public class SleepSession {

    @PrimaryKey(autoGenerate = true)
    public long id;

    /** Bedtime timestamp in millis */
    public long startTimeMillis;

    /** Wake-up timestamp in millis */
    public long endTimeMillis;

    /** Total sleep duration in minutes */
    public int durationMinutes;

    /** Sleep quality score (0-100 scale) */
    public int qualityScore;

    /** Date string "yyyy-MM-dd" for easy lookup */
    public String date;

    public SleepSession() {}

    public SleepSession(long startTimeMillis, long endTimeMillis, int durationMinutes,
                        int qualityScore, String date) {
        this.startTimeMillis = startTimeMillis;
        this.endTimeMillis = endTimeMillis;
        this.durationMinutes = durationMinutes;
        this.qualityScore = qualityScore;
        this.date = date;
    }

    /** Get duration formatted as "Xh Ym" */
    public String getFormattedDuration() {
        int hours = durationMinutes / 60;
        int mins = durationMinutes % 60;
        return hours + "h " + mins + "m";
    }

    /** Get duration as float hours (e.g. 7.5) */
    public float getDurationHours() {
        return durationMinutes / 60f;
    }
}
