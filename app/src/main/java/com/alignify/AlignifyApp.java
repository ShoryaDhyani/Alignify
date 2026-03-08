package com.alignify;

import android.app.Application;
import android.content.SharedPreferences;

import androidx.appcompat.app.AppCompatDelegate;

public class AlignifyApp extends Application {

    public static final String PREFS_NAME = "AlignifyPrefs";
    public static final String KEY_DARK_MODE = "dark_mode_enabled";
    public static final String KEY_THEME_MODE = "theme_mode";

    @Override
    public void onCreate() {
        super.onCreate();
        SharedPreferences prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);

        // Support new theme_mode key ("light", "dark", "system")
        // with fallback to legacy dark_mode_enabled boolean
        String themeMode = prefs.getString(KEY_THEME_MODE, null);
        if (themeMode == null) {
            // Migrate from legacy boolean preference
            boolean darkMode = prefs.getBoolean(KEY_DARK_MODE, false);
            themeMode = darkMode ? "dark" : "light";
            prefs.edit().putString(KEY_THEME_MODE, themeMode).apply();
        }

        switch (themeMode) {
            case "dark":
                AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_YES);
                break;
            case "light":
                AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO);
                break;
            default:
                AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM);
                break;
        }
    }
}
