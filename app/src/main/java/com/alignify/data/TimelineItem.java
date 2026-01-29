package com.alignify.data;

/**
 * Data model for timeline items (steps, activities, workouts).
 */
public class TimelineItem {

    public enum Type {
        STEPS,
        WALKING,
        RUNNING,
        WORKOUT,
        MANUAL
    }

    private Type type;
    private String title;
    private long timestamp;
    private String value;
    private int calories;
    private String source; // "auto", "manual", "ai"

    public TimelineItem() {
        // Required for Firestore
    }

    public TimelineItem(Type type, String title, long timestamp, String value, int calories, String source) {
        this.type = type;
        this.title = title;
        this.timestamp = timestamp;
        this.value = value;
        this.calories = calories;
        this.source = source;
    }

    public Type getType() {
        return type;
    }

    public void setType(Type type) {
        this.type = type;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public long getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(long timestamp) {
        this.timestamp = timestamp;
    }

    public String getValue() {
        return value;
    }

    public void setValue(String value) {
        this.value = value;
    }

    public int getCalories() {
        return calories;
    }

    public void setCalories(int calories) {
        this.calories = calories;
    }

    public String getSource() {
        return source;
    }

    public void setSource(String source) {
        this.source = source;
    }

    /**
     * Get emoji icon based on type.
     */
    public String getIcon() {
        switch (type) {
            case STEPS:
            case WALKING:
                return "👟";
            case RUNNING:
                return "🏃";
            case WORKOUT:
                return "💪";
            case MANUAL:
                return "📝";
            default:
                return "⏱️";
        }
    }
}
