package com.mapbox.geojson;

public final class Feature {
    private final Geometry geometry;

    private Feature(Geometry geometry) {
        this.geometry = geometry;
    }

    public static Feature fromGeometry(Geometry geometry) {
        return new Feature(geometry);
    }

    public Geometry geometry() {
        return geometry;
    }
}