package com.alignify;

import androidx.annotation.NonNull;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentActivity;
import androidx.viewpager2.adapter.FragmentStateAdapter;

/**
 * ViewPager2 adapter that manages the 5 main navigation fragments.
 */
public class MainPagerAdapter extends FragmentStateAdapter {

    public static final int PAGE_COUNT = 5;

    public MainPagerAdapter(@NonNull FragmentActivity activity) {
        super(activity);
    }

    @NonNull
    @Override
    public Fragment createFragment(int position) {
        switch (position) {
            case 0:
                return new DashboardFragment();
            case 1:
                return new ExercisesFragment();
            case 2:
                return new RunFragment();
            case 3:
                return new AnalyticsFragment();
            case 4:
                return new ProfileFragment();
            default:
                return new DashboardFragment();
        }
    }

    @Override
    public int getItemCount() {
        return PAGE_COUNT;
    }
}
