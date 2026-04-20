package com.alignify.data.sleep;

import android.content.Context;

import androidx.room.Database;
import androidx.room.Room;
import androidx.room.RoomDatabase;

import com.alignify.data.DailyActivityDao;
import com.alignify.data.DailyActivityEntity;
import com.alignify.data.FitnessActivityDao;
import com.alignify.data.FitnessActivityEntity;
import com.alignify.data.UserProfileDao;
import com.alignify.data.UserProfileEntity;
import com.alignify.data.WorkoutSessionDao;
import com.alignify.data.WorkoutSessionEntity;

/**
 * Room database for Alignify.
 * Contains all local data tables: sleep sessions, user profile,
 * daily activity, workout sessions, and fitness activities.
 */
@Database(
    entities = {
        SleepSession.class,
        UserProfileEntity.class,
        DailyActivityEntity.class,
        WorkoutSessionEntity.class,
        FitnessActivityEntity.class
    },
    version = 2,
    exportSchema = false
)
public abstract class AppDatabase extends RoomDatabase {

    private static volatile AppDatabase INSTANCE;

    public abstract SleepSessionDao sleepSessionDao();
    public abstract UserProfileDao userProfileDao();
    public abstract DailyActivityDao dailyActivityDao();
    public abstract WorkoutSessionDao workoutSessionDao();
    public abstract FitnessActivityDao fitnessActivityDao();

    public static AppDatabase getInstance(Context context) {
        if (INSTANCE == null) {
            synchronized (AppDatabase.class) {
                if (INSTANCE == null) {
                    INSTANCE = Room.databaseBuilder(
                            context.getApplicationContext(),
                            AppDatabase.class,
                            "alignify_database"
                    ).fallbackToDestructiveMigration().build();
                }
            }
        }
        return INSTANCE;
    }
}
