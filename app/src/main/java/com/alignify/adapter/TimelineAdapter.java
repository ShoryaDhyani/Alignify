package com.alignify.adapter;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.alignify.R;
import com.alignify.data.TimelineItem;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;

/**
 * Adapter for timeline activity list.
 */
public class TimelineAdapter extends RecyclerView.Adapter<TimelineAdapter.ViewHolder> {

    private final List<TimelineItem> items;
    private final SimpleDateFormat timeFormat = new SimpleDateFormat("h:mm a", Locale.US);
    private final SimpleDateFormat dateFormat = new SimpleDateFormat("MMM d", Locale.US);

    public TimelineAdapter(List<TimelineItem> items) {
        this.items = items;
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_timeline, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        TimelineItem item = items.get(position);

        holder.activityIcon.setText(item.getIcon());
        holder.activityType.setText(item.getTitle());
        holder.activityValue.setText(item.getValue());
        holder.activityCalories.setText(item.getCalories() + " kcal");

        // Format time - show "Today" or date
        long now = System.currentTimeMillis();
        long itemTime = item.getTimestamp();
        long dayDiff = (now - itemTime) / (24 * 60 * 60 * 1000);

        String timeStr = timeFormat.format(new Date(itemTime));
        if (dayDiff == 0) {
            holder.activityTime.setText(timeStr);
        } else if (dayDiff == 1) {
            holder.activityTime.setText("Yesterday, " + timeStr);
        } else {
            holder.activityTime.setText(dateFormat.format(new Date(itemTime)) + ", " + timeStr);
        }
    }

    @Override
    public int getItemCount() {
        return items.size();
    }

    static class ViewHolder extends RecyclerView.ViewHolder {
        TextView activityIcon;
        TextView activityType;
        TextView activityTime;
        TextView activityValue;
        TextView activityCalories;

        ViewHolder(View itemView) {
            super(itemView);
            activityIcon = itemView.findViewById(R.id.activityIcon);
            activityType = itemView.findViewById(R.id.activityType);
            activityTime = itemView.findViewById(R.id.activityTime);
            activityValue = itemView.findViewById(R.id.activityValue);
            activityCalories = itemView.findViewById(R.id.activityCalories);
        }
    }
}
