package com.mapbox.geojson;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public final class Polygon implements Geometry {
    private final List<List<Point>> coordinates;

    private Polygon(List<List<Point>> coordinates) {
        List<List<Point>> copy = new ArrayList<>();
        for (List<Point> ring : coordinates) {
            copy.add(Collections.unmodifiableList(new ArrayList<>(ring)));
        }
        this.coordinates = Collections.unmodifiableList(copy);
    }

    public static Polygon fromLngLats(List<List<Point>> coordinates) {
        return new Polygon(coordinates);
    }

    public List<List<Point>> coordinates() {
        return coordinates;
    }
}