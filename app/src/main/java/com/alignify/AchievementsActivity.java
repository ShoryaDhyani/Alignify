package com.alignify;

import android.os.Bundle;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.alignify.adapter.AchievementAdapter;
import com.alignify.data.AchievementManager;
import com.alignify.data.StreakManager;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Activity displaying user achievements and badges.
 */
public class AchievementsActivity extends AppCompatActivity {

    private TextView unlockedCount;
    private TextView totalCount;
    private TextView currentStreak;
    private RecyclerView achievementRecyclerView;

    private AchievementAdapter adapter;
    private AchievementManager achievementManager;
    private StreakManager streakManager;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_achievements);

        achievementManager = AchievementManager.getInstance(this);
        streakManager = StreakManager.getInstance(this);

        initViews();
        setupRecyclerView();
        loadData();
    }

    private void initViews() {
        findViewById(R.id.btnBack).setOnClickListener(v -> finish());

        unlockedCount = findViewById(R.id.unlockedCount);
        totalCount = findViewById(R.id.totalCount);
        currentStreak = findViewById(R.id.currentStreak);
        achievementRecyclerView = findViewById(R.id.achievementRecyclerView);
    }

    private void setupRecyclerView() {
        adapter = new AchievementAdapter();
        achievementRecyclerView.setLayoutManager(new LinearLayoutManager(this));
        achievementRecyclerView.setAdapter(adapter);
    }

    private void loadData() {
        // Load achievements
        List<AchievementManager.Achievement> unlocked = achievementManager.getUnlockedAchievements();
        Set<String> unlockedIds = new HashSet<>();
        for (AchievementManager.Achievement a : unlocked) {
            unlockedIds.add(a.id);
        }
        adapter.setUnlockedAchievements(unlockedIds);

        // Update stats
        int total = AchievementManager.ALL_ACHIEVEMENTS.length;
        unlockedCount.setText(String.valueOf(unlocked.size()));
        totalCount.setText("/ " + total);

        // Load streak
        streakManager.loadStreakFromFirestore(new StreakManager.OnStreakUpdateListener() {
            @Override
            public void onStreakUpdated(int streak, boolean isNewStreak) {
                currentStreak.setText("🔥 " + streak);
            }
        });
    }
}
