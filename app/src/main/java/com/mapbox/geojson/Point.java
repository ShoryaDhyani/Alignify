package com.mapbox.geojson;

public final class Point implements Geometry {
    private final double longitude;
    private final double latitude;

    private Point(double longitude, double latitude) {
        this.longitude = longitude;
        this.latitude = latitude;
    }

    public static Point fromLngLat(double longitude, double latitude) {
        return new Point(longitude, latitude);
    }

    public double longitude() {
        return longitude;
    }

    public double latitude() {
        return latitude;
    }
}