package com.alignify.data;

import androidx.annotation.NonNull;
import androidx.room.Entity;
import androidx.room.PrimaryKey;

/**
 * Room entity for storing user profile data locally.
 */
@Entity(tableName = "user_profile")
public class UserProfileEntity {

    @PrimaryKey
    @NonNull
    public String id; // Always "guest" for single-user local mode

    public String email;
    public String name;
    public float bmi;
    public String bmiCategory;
    public String activityLevel;
    public int height; // cm
    public int weight; // kg
    public int age;
    public String gender;
    public String profileImageUrl;
    public boolean profileComplete;
    public boolean voiceFeedback;
    public boolean textFeedback;

    // Goals
    public int stepGoal;
    public int caloriesGoal;
    public int activeTimeGoal;
    public int waterGoal;
    public float sleepGoal;

    public long createdAt;
    public long updatedAt;

    public UserProfileEntity() {
        this.id = "guest";
    }
}
