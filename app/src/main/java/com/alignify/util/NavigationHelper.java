package com.alignify.util;

import android.app.Activity;
import android.content.Intent;
import android.view.View;
import android.widget.ImageView;

import com.alignify.ActivityActivity;
import com.alignify.DashboardActivity;
import com.alignify.MainActivity;
import com.alignify.R;
import com.alignify.RunActivity;
import com.alignify.SettingsActivity;
import com.alignify.StepActivity;

/**
 * Centralized helper for bottom navigation setup across activities.
 * Reduces code duplication for nav bar handling.
 */
public class NavigationHelper {

    public static final int NAV_HOME = 0;
    public static final int NAV_EXERCISES = 1;
    public static final int NAV_RUN = 2;
    public static final int NAV_ANALYTICS = 3;
    public static final int NAV_PROFILE = 4;

    /**
     * Sets up bottom navigation click listeners for an activity (5 tabs).
     *
     * @param activity      The current activity
     * @param currentNavTab The current tab index (use NAV_HOME, NAV_EXERCISES,
     *                      etc.)
     * @param navHome       The home navigation view
     * @param navExercises  The exercises navigation view
     * @param navRun        The run navigation view
     * @param navAnalytics  The analytics navigation view
     * @param navProfile    The profile navigation view
     */
    public static void setupBottomNavigation(Activity activity, int currentNavTab,
            View navHome, View navExercises, View navRun, View navAnalytics, View navProfile) {

        if (navHome != null) {
            navHome.setOnClickListener(v -> {
                if (currentNavTab != NAV_HOME) {
                    activity.startActivity(new Intent(activity, DashboardActivity.class));
                    applyTransition(activity);
                    if (currentNavTab != NAV_PROFILE) {
                        activity.finish();
                    }
                }
            });
        }

        if (navExercises != null) {
            navExercises.setOnClickListener(v -> {
                if (currentNavTab != NAV_EXERCISES) {
                    activity.startActivity(new Intent(activity, MainActivity.class));
                    applyTransition(activity);
                    if (currentNavTab != NAV_PROFILE) {
                        activity.finish();
                    }
                }
            });
        }

        if (navRun != null) {
            navRun.setOnClickListener(v -> {
                if (currentNavTab != NAV_RUN) {
                    activity.startActivity(new Intent(activity, RunActivity.class));
                    applyTransition(activity);
                    if (currentNavTab != NAV_PROFILE) {
                        activity.finish();
                    }
                }
            });
        }

        if (navAnalytics != null) {
            navAnalytics.setOnClickListener(v -> {
                if (currentNavTab != NAV_ANALYTICS) {
                    activity.startActivity(new Intent(activity, ActivityActivity.class));
                    applyTransition(activity);
                    if (currentNavTab != NAV_PROFILE) {
                        activity.finish();
                    }
                }
            });
        }

        if (navProfile != null) {
            navProfile.setOnClickListener(v -> {
                if (currentNavTab != NAV_PROFILE) {
                    activity.startActivity(new Intent(activity, SettingsActivity.class));
                    applyTransition(activity);
                }
            });
        }

        // Highlight current tab
        highlightNavItem(activity, currentNavTab);
    }

    /**
     * Legacy 4-tab setup for backward compatibility.
     * Wraps the new 5-tab method with null for the Run tab.
     */
    public static void setupBottomNavigation(Activity activity, int currentNavTab,
            View navHome, View navExercises, View navAnalytics, View navProfile) {
        // Map old indices to new indices
        int mappedTab = currentNavTab;
        if (currentNavTab >= NAV_RUN) {
            mappedTab = currentNavTab + 1; // shift Analytics and Profile by 1
        }

        View navRun = activity.findViewById(R.id.navRun);
        setupBottomNavigation(activity, mappedTab, navHome, navExercises, navRun, navAnalytics, navProfile);
    }

    /**
     * Highlights the active navigation item.
     * 
     * @param activity The current activity
     * @param index    0=Home, 1=Exercises, 2=Run, 3=Analytics, 4=Profile
     */
    public static void highlightNavItem(Activity activity, int index) {
        ImageView navHomeIcon = activity.findViewById(R.id.navHomeIcon);
        ImageView navExercisesIcon = activity.findViewById(R.id.navExercisesIcon);
        ImageView navRunIcon = activity.findViewById(R.id.navRunIcon);
        ImageView navAnalyticsIcon = activity.findViewById(R.id.navAnalyticsIcon);
        ImageView navProfileIcon = activity.findViewById(R.id.navProfileIcon);

        int activeColor = activity.getColor(R.color.accent);
        int inactiveColor = activity.getColor(R.color.text_secondary_dark);

        if (navHomeIcon != null)
            navHomeIcon.setColorFilter(index == NAV_HOME ? activeColor : inactiveColor);
        if (navExercisesIcon != null)
            navExercisesIcon.setColorFilter(index == NAV_EXERCISES ? activeColor : inactiveColor);
        if (navRunIcon != null)
            navRunIcon.setColorFilter(index == NAV_RUN ? activeColor : inactiveColor);
        if (navAnalyticsIcon != null)
            navAnalyticsIcon.setColorFilter(index == NAV_ANALYTICS ? activeColor : inactiveColor);
        if (navProfileIcon != null)
            navProfileIcon.setColorFilter(index == NAV_PROFILE ? activeColor : inactiveColor);
    }

    /**
     * Applies fade transition animation.
     */
    @SuppressWarnings("deprecation")
    private static void applyTransition(Activity activity) {
        activity.overridePendingTransition(R.anim.slide_in_right, R.anim.slide_out_left);
    }
}
