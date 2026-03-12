package com.alignify;

import android.os.Bundle;
import android.view.View;
import android.view.animation.AccelerateInterpolator;
import android.view.animation.DecelerateInterpolator;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.viewpager2.widget.ViewPager2;

/**
 * Single host activity for all 5 main screens via ViewPager2.
 * Provides Instagram-style smooth horizontal swipe navigation.
 */
public class HomeActivity extends AppCompatActivity {

    public static final int TAB_HOME = 0;
    public static final int TAB_EXERCISES = 1;
    public static final int TAB_RUN = 2;
    public static final int TAB_ANALYTICS = 3;
    public static final int TAB_PROFILE = 4;

    private ViewPager2 viewPager;

    // Bottom nav views
    private View navHome;
    private View navExercises;
    private View navRun;
    private View navAnalytics;
    private View navProfile;

    // Nav icons for highlight
    private ImageView navHomeIcon;
    private ImageView navExercisesIcon;
    private ImageView navRunIcon;
    private ImageView navAnalyticsIcon;
    private ImageView navProfileIcon;

    // Nav labels for highlight
    private TextView navHomeLabel;
    private TextView navExercisesLabel;
    private TextView navAnalyticsLabel;
    private TextView navProfileLabel;

    private View bottomNavContainer;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_home);

        initViews();
        setupViewPager();
        setupBottomNav();

        // Default to Home tab (or restore from intent)
        int startTab = getIntent().getIntExtra("start_tab", TAB_HOME);
        if (startTab >= 0 && startTab < MainPagerAdapter.PAGE_COUNT) {
            viewPager.setCurrentItem(startTab, false);
            highlightTab(startTab);
        }
    }

    private void initViews() {
        viewPager = findViewById(R.id.viewPager);

        // Use the unique include ID to scope nav lookups, avoiding ID conflicts
        // with the fragment layouts that also include layout_bottom_navbar
        View homeNav = findViewById(R.id.homeBottomNav);
        bottomNavContainer = homeNav;

        navHome = homeNav.findViewById(R.id.navHome);
        navExercises = homeNav.findViewById(R.id.navExercises);
        navRun = homeNav.findViewById(R.id.navRun);
        navAnalytics = homeNav.findViewById(R.id.navAnalytics);
        navProfile = homeNav.findViewById(R.id.navProfile);

        navHomeIcon = homeNav.findViewById(R.id.navHomeIcon);
        navExercisesIcon = homeNav.findViewById(R.id.navExercisesIcon);
        navRunIcon = homeNav.findViewById(R.id.navRunIcon);
        navAnalyticsIcon = homeNav.findViewById(R.id.navAnalyticsIcon);
        navProfileIcon = homeNav.findViewById(R.id.navProfileIcon);

        navHomeLabel = homeNav.findViewById(R.id.navHomeLabel);
        navExercisesLabel = homeNav.findViewById(R.id.navExercisesLabel);
        navAnalyticsLabel = homeNav.findViewById(R.id.navAnalyticsLabel);
        navProfileLabel = homeNav.findViewById(R.id.navProfileLabel);
    }

    private void setupViewPager() {
        MainPagerAdapter adapter = new MainPagerAdapter(this);
        viewPager.setAdapter(adapter);

        // Keep all 5 pages alive to preserve state (timers, maps, etc.)
        viewPager.setOffscreenPageLimit(4);

        viewPager.registerOnPageChangeCallback(new ViewPager2.OnPageChangeCallback() {
            @Override
            public void onPageSelected(int position) {
                highlightTab(position);
            }
        });
    }

    private void setupBottomNav() {
        if (navHome != null) {
            navHome.setOnClickListener(v -> viewPager.setCurrentItem(TAB_HOME, true));
        }
        if (navExercises != null) {
            navExercises.setOnClickListener(v -> viewPager.setCurrentItem(TAB_EXERCISES, true));
        }
        if (navRun != null) {
            navRun.setOnClickListener(v -> viewPager.setCurrentItem(TAB_RUN, true));
        }
        if (navAnalytics != null) {
            navAnalytics.setOnClickListener(v -> viewPager.setCurrentItem(TAB_ANALYTICS, true));
        }
        if (navProfile != null) {
            navProfile.setOnClickListener(v -> viewPager.setCurrentItem(TAB_PROFILE, true));
        }

        // Set initial highlight
        highlightTab(TAB_HOME);
    }

    private void highlightTab(int index) {
        int activeColor = getColor(R.color.accent);
        int inactiveColor = getColor(R.color.text_secondary_dark);

        if (navHomeIcon != null)
            navHomeIcon.setColorFilter(index == TAB_HOME ? activeColor : inactiveColor);
        if (navExercisesIcon != null)
            navExercisesIcon.setColorFilter(index == TAB_EXERCISES ? activeColor : inactiveColor);
        // Center Record button: always white icon on accent background
        if (navRunIcon != null)
            navRunIcon.setColorFilter(getColor(android.R.color.white));
        if (navAnalyticsIcon != null)
            navAnalyticsIcon.setColorFilter(index == TAB_ANALYTICS ? activeColor : inactiveColor);
        if (navProfileIcon != null)
            navProfileIcon.setColorFilter(index == TAB_PROFILE ? activeColor : inactiveColor);

        // Highlight labels too
        if (navHomeLabel != null)
            navHomeLabel.setTextColor(index == TAB_HOME ? activeColor : inactiveColor);
        if (navExercisesLabel != null)
            navExercisesLabel.setTextColor(index == TAB_EXERCISES ? activeColor : inactiveColor);
        if (navAnalyticsLabel != null)
            navAnalyticsLabel.setTextColor(index == TAB_ANALYTICS ? activeColor : inactiveColor);
        if (navProfileLabel != null)
            navProfileLabel.setTextColor(index == TAB_PROFILE ? activeColor : inactiveColor);
    }

    // ========== Public methods for fragment communication ==========

    /**
     * Navigate to a specific tab. Called by fragments (e.g. Dashboard quick
     * actions).
     */
    public void navigateToTab(int tabIndex) {
        if (viewPager != null && tabIndex >= 0 && tabIndex < MainPagerAdapter.PAGE_COUNT) {
            viewPager.setCurrentItem(tabIndex, true);
        }
    }

    /**
     * Enable or disable ViewPager2 swiping. Called by RunFragment during recording.
     */
    public void setSwipeEnabled(boolean enabled) {
        if (viewPager != null) {
            viewPager.setUserInputEnabled(enabled);
        }
    }

    /**
     * Hides the bottom nav bar with a slide-down animation.
     */
    public void hideNavBar() {
        if (bottomNavContainer != null && bottomNavContainer.getTranslationY() == 0) {
            bottomNavContainer.animate()
                    .translationY(bottomNavContainer.getHeight())
                    .setDuration(300)
                    .setInterpolator(new AccelerateInterpolator())
                    .start();
        }
    }

    /**
     * Shows the bottom nav bar with a slide-up animation.
     */
    public void showNavBar() {
        if (bottomNavContainer != null && bottomNavContainer.getTranslationY() != 0) {
            bottomNavContainer.animate()
                    .translationY(0)
                    .setDuration(300)
                    .setInterpolator(new DecelerateInterpolator())
                    .start();
        }
    }
}
