package com.alignify.adapter;

import android.graphics.Color;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.alignify.R;
import com.alignify.data.AchievementManager;

import java.util.HashSet;
import java.util.Set;

/**
 * RecyclerView adapter for displaying achievements.
 */
public class AchievementAdapter extends RecyclerView.Adapter<AchievementAdapter.AchievementViewHolder> {

    private final AchievementManager.Achievement[] achievements;
    private final Set<String> unlockedIds = new HashSet<>();

    public AchievementAdapter() {
        this.achievements = AchievementManager.ALL_ACHIEVEMENTS;
    }

    public void setUnlockedAchievements(Set<String> unlockedIds) {
        this.unlockedIds.clear();
        this.unlockedIds.addAll(unlockedIds);
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public AchievementViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_achievement, parent, false);
        return new AchievementViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull AchievementViewHolder holder, int position) {
        AchievementManager.Achievement achievement = achievements[position];
        boolean isUnlocked = unlockedIds.contains(achievement.id);
        holder.bind(achievement, isUnlocked);
    }

    @Override
    public int getItemCount() {
        return achievements.length;
    }

    static class AchievementViewHolder extends RecyclerView.ViewHolder {
        private final LinearLayout container;
        private final TextView emoji;
        private final TextView title;
        private final TextView description;
        private final ImageView statusIcon;

        public AchievementViewHolder(@NonNull View itemView) {
            super(itemView);
            container = itemView.findViewById(R.id.achievementContainer);
            emoji = itemView.findViewById(R.id.achievementEmoji);
            title = itemView.findViewById(R.id.achievementTitle);
            description = itemView.findViewById(R.id.achievementDescription);
            statusIcon = itemView.findViewById(R.id.statusIcon);
        }

        public void bind(AchievementManager.Achievement achievement, boolean isUnlocked) {
            emoji.setText(achievement.emoji);
            title.setText(achievement.title);
            description.setText(achievement.description);

            if (isUnlocked) {
                // Unlocked state
                container.setAlpha(1.0f);
                title.setTextColor(itemView.getContext().getColor(R.color.text_primary));
                statusIcon.setImageResource(android.R.drawable.checkbox_on_background);
                statusIcon.setColorFilter(itemView.getContext().getColor(R.color.correct_green));
                statusIcon.setVisibility(View.VISIBLE);
            } else {
                // Locked state
                container.setAlpha(0.5f);
                title.setTextColor(itemView.getContext().getColor(R.color.text_secondary));
                statusIcon.setImageResource(android.R.drawable.ic_lock_lock);
                statusIcon.setColorFilter(itemView.getContext().getColor(R.color.text_secondary));
                statusIcon.setVisibility(View.VISIBLE);
            }
        }
    }
}
