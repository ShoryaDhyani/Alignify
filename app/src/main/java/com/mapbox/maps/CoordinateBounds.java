package com.mapbox.maps;

import com.mapbox.geojson.Point;

public final class CoordinateBounds {
    private final Point southwest;
    private final Point northeast;

    public CoordinateBounds(Point southwest, Point northeast) {
        this.southwest = southwest;
        this.northeast = northeast;
    }

    public Point southwest() {
        return southwest;
    }

    public Point northeast() {
        return northeast;
    }
}