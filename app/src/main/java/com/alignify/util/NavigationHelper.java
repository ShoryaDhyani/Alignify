package com.alignify.util;

import android.app.Activity;
import android.content.Intent;
import android.view.GestureDetector;
import android.view.MotionEvent;
import android.view.View;
import android.view.animation.AccelerateInterpolator;
import android.view.animation.DecelerateInterpolator;
import android.widget.ImageView;
import android.widget.ScrollView;

import com.alignify.ActivityActivity;
import com.alignify.DashboardActivity;
import com.alignify.MainActivity;
import com.alignify.R;
import com.alignify.RunActivity;
import com.alignify.SettingsActivity;
import com.alignify.StepActivity;

/**
 * Centralized helper for bottom navigation setup across activities.
 * Handles nav bar setup, directional transitions, swipe/scroll navigation,
 * and nav bar show/hide animations.
 */
public class NavigationHelper {

    public static final int NAV_HOME = 0;
    public static final int NAV_EXERCISES = 1;
    public static final int NAV_RUN = 2;
    public static final int NAV_ANALYTICS = 3;
    public static final int NAV_PROFILE = 4;

    // Swipe navigation screen order (all 5 tabs participate in swipe nav)
    private static final int[] SWIPE_ORDER = { NAV_HOME, NAV_EXERCISES, NAV_RUN, NAV_ANALYTICS, NAV_PROFILE };
    private static final Class<?>[] SWIPE_ACTIVITIES = { DashboardActivity.class, MainActivity.class,
            RunActivity.class, ActivityActivity.class, SettingsActivity.class };

    private static final int SWIPE_THRESHOLD = 100;
    private static final int SWIPE_VELOCITY_THRESHOLD = 200;
    private static final int OVERSCROLL_THRESHOLD = 150;

    /**
     * Sets up bottom navigation click listeners for an activity (5 tabs).
     */
    public static void setupBottomNavigation(Activity activity, int currentNavTab,
            View navHome, View navExercises, View navRun, View navAnalytics, View navProfile) {

        if (navHome != null) {
            navHome.setOnClickListener(v -> {
                if (currentNavTab != NAV_HOME) {
                    activity.startActivity(new Intent(activity, DashboardActivity.class));
                    applyDirectionalTransition(activity, currentNavTab, NAV_HOME);
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
                    applyDirectionalTransition(activity, currentNavTab, NAV_EXERCISES);
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
                    applyDirectionalTransition(activity, currentNavTab, NAV_RUN);
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
                    applyDirectionalTransition(activity, currentNavTab, NAV_ANALYTICS);
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
                    applyDirectionalTransition(activity, currentNavTab, NAV_PROFILE);
                }
            });
        }

        highlightNavItem(activity, currentNavTab);
    }

    /**
     * 4-tab setup for activities that don't explicitly reference navRun.
     * Finds navRun from the layout and delegates to the 5-tab method.
     */
    public static void setupBottomNavigation(Activity activity, int currentNavTab,
            View navHome, View navExercises, View navAnalytics, View navProfile) {
        View navRun = activity.findViewById(R.id.navRun);
        setupBottomNavigation(activity, currentNavTab, navHome, navExercises, navRun, navAnalytics, navProfile);
    }

    /**
     * Highlights the active navigation item.
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
        // Center Record button: always white icon on accent bg, no tint change
        if (navRunIcon != null && index == NAV_RUN) {
            navRunIcon.setColorFilter(activity.getColor(android.R.color.white));
        }
        if (navAnalyticsIcon != null)
            navAnalyticsIcon.setColorFilter(index == NAV_ANALYTICS ? activeColor : inactiveColor);
        if (navProfileIcon != null)
            navProfileIcon.setColorFilter(index == NAV_PROFILE ? activeColor : inactiveColor);
    }

    // ============ Directional Transitions ============

    /**
     * Applies directional slide transition based on navigation direction.
     */
    @SuppressWarnings("deprecation")
    private static void applyDirectionalTransition(Activity activity, int fromTab, int toTab) {
        if (toTab > fromTab) {
            activity.overridePendingTransition(R.anim.slide_in_right, R.anim.slide_out_left);
        } else {
            activity.overridePendingTransition(R.anim.slide_in_left, R.anim.slide_out_right);
        }
    }

    // ============ Swipe Navigation ============

    /**
     * Creates a GestureDetector for horizontal swipe navigation between screens.
     * Call from Activity.dispatchTouchEvent() to enable swipe nav.
     */
    public static GestureDetector createSwipeDetector(Activity activity, int currentNavTab) {
        return new GestureDetector(activity, new GestureDetector.SimpleOnGestureListener() {
            @Override
            public boolean onFling(MotionEvent e1, MotionEvent e2, float velocityX, float velocityY) {
                if (e1 == null || e2 == null)
                    return false;
                float dx = e2.getX() - e1.getX();
                float dy = e2.getY() - e1.getY();
                if (Math.abs(dx) > Math.abs(dy) * 1.5f
                        && Math.abs(dx) > SWIPE_THRESHOLD
                        && Math.abs(velocityX) > SWIPE_VELOCITY_THRESHOLD) {
                    if (dx < 0) {
                        navigateToNext(activity, currentNavTab);
                    } else {
                        navigateToPrevious(activity, currentNavTab);
                    }
                    return true;
                }
                return false;
            }
        });
    }

    /**
     * Enables vertical overscroll navigation on a ScrollView.
     * Pulling down at top → previous screen, pulling up at bottom → next screen.
     */
    public static void enableOverscrollNavigation(Activity activity, ScrollView scrollView, int currentNavTab) {
        if (scrollView == null)
            return;
        scrollView.setOnTouchListener(new View.OnTouchListener() {
            private float startY;
            private boolean wasAtTop, wasAtBottom;

            @Override
            public boolean onTouch(View v, MotionEvent event) {
                switch (event.getActionMasked()) {
                    case MotionEvent.ACTION_DOWN:
                        startY = event.getY();
                        wasAtTop = scrollView.getScrollY() == 0;
                        View child = scrollView.getChildAt(0);
                        wasAtBottom = child != null &&
                                scrollView.getScrollY() + scrollView.getHeight() >= child.getHeight();
                        break;
                    case MotionEvent.ACTION_UP:
                        float dy = event.getY() - startY;
                        if (wasAtTop && dy > OVERSCROLL_THRESHOLD) {
                            navigateToPrevious(activity, currentNavTab);
                        } else if (wasAtBottom && dy < -OVERSCROLL_THRESHOLD) {
                            navigateToNext(activity, currentNavTab);
                        }
                        break;
                }
                return false;
            }
        });
    }

    private static void navigateToNext(Activity activity, int currentNavTab) {
        int swipeIndex = getSwipeIndex(currentNavTab);
        if (swipeIndex >= 0 && swipeIndex < SWIPE_ORDER.length - 1) {
            int nextNav = SWIPE_ORDER[swipeIndex + 1];
            activity.startActivity(new Intent(activity, SWIPE_ACTIVITIES[swipeIndex + 1]));
            applyDirectionalTransition(activity, currentNavTab, nextNav);
            activity.finish();
        }
    }

    private static void navigateToPrevious(Activity activity, int currentNavTab) {
        int swipeIndex = getSwipeIndex(currentNavTab);
        if (swipeIndex > 0) {
            int prevNav = SWIPE_ORDER[swipeIndex - 1];
            activity.startActivity(new Intent(activity, SWIPE_ACTIVITIES[swipeIndex - 1]));
            applyDirectionalTransition(activity, currentNavTab, prevNav);
            activity.finish();
        }
    }

    private static int getSwipeIndex(int navTab) {
        for (int i = 0; i < SWIPE_ORDER.length; i++) {
            if (SWIPE_ORDER[i] == navTab)
                return i;
        }
        return -1;
    }

    // ============ Nav Bar Show/Hide Animation ============

    /**
     * Hides the bottom nav bar with a slide-down animation.
     */
    public static void hideNavBar(Activity activity) {
        View navBar = activity.findViewById(R.id.bottomNavContainer);
        if (navBar != null && navBar.getTranslationY() == 0) {
            navBar.animate()
                    .translationY(navBar.getHeight())
                    .setDuration(300)
                    .setInterpolator(new AccelerateInterpolator())
                    .start();
        }
    }

    /**
     * Shows the bottom nav bar with a slide-up animation.
     */
    public static void showNavBar(Activity activity) {
        View navBar = activity.findViewById(R.id.bottomNavContainer);
        if (navBar != null && navBar.getTranslationY() != 0) {
            navBar.animate()
                    .translationY(0)
                    .setDuration(300)
                    .setInterpolator(new DecelerateInterpolator())
                    .start();
        }
    }
}
