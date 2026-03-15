package com.alignify;

import android.app.Application;
import android.content.Context;
import android.content.SharedPreferences;
import android.util.Log;

import androidx.appcompat.app.AppCompatDelegate;

import com.mapbox.common.MapboxOptions;
import com.mapbox.maps.Style;

public class AlignifyApp extends Application {
    private static final String TAG = "AlignifyApp";

    public static final String PREFS_NAME = "AlignifyPrefs";
    public static final String KEY_DARK_MODE = "dark_mode_enabled";
    public static final String KEY_THEME_MODE = "theme_mode";
    public static final String KEY_MAP_STYLE_URI = "map_style_uri";
    public static final String DEFAULT_MAPBOX_STYLE_URI = "mapbox://styles/shoryadhyani/cmms6pbia008y01sge7vqb3r6";
    public static final String MAP_STYLE_STREETS = "mapbox://styles/mapbox/streets-v12";
    public static final String MAP_STYLE_OUTDOORS = "mapbox://styles/mapbox/outdoors-v12";
    public static final String MAP_STYLE_LIGHT = "mapbox://styles/mapbox/light-v11";
    public static final String MAP_STYLE_DARK = "mapbox://styles/mapbox/dark-v11";
    public static final String MAP_STYLE_SATELLITE = "mapbox://styles/mapbox/satellite-v9";

    @Override
    public void onCreate() {
        super.onCreate();

        // Initialize Mapbox access token from BuildConfig (set via local.properties or
        // CI env var)
        String mapboxToken = BuildConfig.MAPBOX_ACCESS_TOKEN;
        if (isValidMapboxPublicToken(mapboxToken)) {
            MapboxOptions.setAccessToken(mapboxToken);
        } else {
            Log.w(TAG, "Mapbox access token missing/invalid. Set a real pk.* token in local.properties as MAPBOX_ACCESS_TOKEN.");
        }

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

    private static boolean isValidMapboxPublicToken(String token) {
        if (token == null) {
            return false;
        }
        String value = token.trim();
        if (value.isEmpty()) {
            return false;
        }
        if (value.equals("YOUR_MAPBOX_ACCESS_TOKEN") || value.equals("YOUR_MAPBOX_PUBLIC_TOKEN")) {
            return false;
        }
        return value.startsWith("pk.");
    }

    public static boolean isValidMapStyleUri(String styleUri) {
        return styleUri != null && styleUri.startsWith("mapbox://styles/");
    }

    public static String normalizeMapStyleInput(String rawInput) {
        if (rawInput == null) {
            return null;
        }

        String value = rawInput.trim();
        if (isValidMapStyleUri(value)) {
            return value;
        }

        String marker = "console.mapbox.com/studio/styles/";
        int markerIndex = value.indexOf(marker);
        if (markerIndex < 0) {
            return null;
        }

        String tail = value.substring(markerIndex + marker.length());
        String[] parts = tail.split("/");
        if (parts.length < 2) {
            return null;
        }

        String username = parts[0].trim();
        String styleId = parts[1].trim();

        int hashIndex = styleId.indexOf('#');
        if (hashIndex >= 0) {
            styleId = styleId.substring(0, hashIndex);
        }
        int queryIndex = styleId.indexOf('?');
        if (queryIndex >= 0) {
            styleId = styleId.substring(0, queryIndex);
        }

        if (username.isEmpty() || styleId.isEmpty()) {
            return null;
        }

        return "mapbox://styles/" + username + "/" + styleId;
    }

    public static String resolveMapStyleUri(Context context) {
        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
        String savedStyle = normalizeMapStyleInput(prefs.getString(KEY_MAP_STYLE_URI, null));
        if (savedStyle != null) {
            return savedStyle;
        }

        String buildStyle = normalizeMapStyleInput(BuildConfig.MAPBOX_STYLE_URI);
        if (buildStyle != null) {
            return buildStyle;
        }

        if (normalizeMapStyleInput(DEFAULT_MAPBOX_STYLE_URI) != null) {
            return DEFAULT_MAPBOX_STYLE_URI;
        }

        return MAP_STYLE_STREETS;
    }

    public static void saveMapStyleUri(Context context, String styleUri) {
        String normalized = normalizeMapStyleInput(styleUri);
        if (normalized == null) {
            return;
        }
        context.getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
                .edit()
                .putString(KEY_MAP_STYLE_URI, normalized)
                .apply();
    }

    public static String getMapStyleLabel(String styleUri) {
        if (MAP_STYLE_STREETS.equals(styleUri) || Style.MAPBOX_STREETS.equals(styleUri))
            return "Streets";
        if (MAP_STYLE_OUTDOORS.equals(styleUri))
            return "Outdoors";
        if (MAP_STYLE_LIGHT.equals(styleUri))
            return "Light";
        if (MAP_STYLE_DARK.equals(styleUri))
            return "Dark";
        if (MAP_STYLE_SATELLITE.equals(styleUri))
            return "Satellite";
        if (DEFAULT_MAPBOX_STYLE_URI.equals(styleUri))
            return "Custom (Shorya)";
        return "Custom";
    }
}
