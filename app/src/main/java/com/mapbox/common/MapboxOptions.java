package com.mapbox.common;

public final class MapboxOptions {
    private static String accessToken;

    private MapboxOptions() {
    }

    public static void setAccessToken(String token) {
        accessToken = token;
    }

    public static String getAccessToken() {
        return accessToken;
    }
}