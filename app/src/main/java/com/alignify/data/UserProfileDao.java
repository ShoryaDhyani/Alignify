package com.alignify.data;

import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;
import androidx.room.Update;

/**
 * Data Access Object for user profile operations.
 */
@Dao
public interface UserProfileDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void insertOrUpdate(UserProfileEntity profile);

    @Query("SELECT * FROM user_profile WHERE id = 'guest' LIMIT 1")
    UserProfileEntity getProfile();

    @Query("UPDATE user_profile SET profileImageUrl = :url, updatedAt = :timestamp WHERE id = 'guest'")
    void updateProfileImageUrl(String url, long timestamp);

    @Query("UPDATE user_profile SET voiceFeedback = :voice, textFeedback = :text WHERE id = 'guest'")
    void updateFeedbackSettings(boolean voice, boolean text);

    @Query("UPDATE user_profile SET stepGoal = :stepGoal, caloriesGoal = :caloriesGoal, " +
            "activeTimeGoal = :activeTimeGoal, waterGoal = :waterGoal, sleepGoal = :sleepGoal, " +
            "updatedAt = :timestamp WHERE id = 'guest'")
    void updateGoals(int stepGoal, int caloriesGoal, int activeTimeGoal, int waterGoal,
                     float sleepGoal, long timestamp);

    @Query("DELETE FROM user_profile")
    void deleteAll();
}
