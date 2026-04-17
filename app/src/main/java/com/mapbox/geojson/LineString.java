package com.mapbox.geojson;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public final class LineString implements Geometry {
    private final List<Point> coordinates;

    private LineString(List<Point> coordinates) {
        this.coordinates = Collections.unmodifiableList(new ArrayList<>(coordinates));
    }

    public static LineString fromLngLats(List<Point> coordinates) {
        return new LineString(coordinates);
    }

    public List<Point> coordinates() {
        return coordinates;
    }
}