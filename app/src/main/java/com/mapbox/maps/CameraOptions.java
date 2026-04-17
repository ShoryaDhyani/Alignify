package com.mapbox.maps;

import com.mapbox.geojson.Point;

public final class CameraOptions {
    private final Point center;
    private final Double zoom;
    private final Double pitch;

    private CameraOptions(Point center, Double zoom, Double pitch) {
        this.center = center;
        this.zoom = zoom;
        this.pitch = pitch;
    }

    public Point center() {
        return center;
    }

    public Double zoom() {
        return zoom;
    }

    public Double pitch() {
        return pitch;
    }

    public static final class Builder {
        private Point center;
        private Double zoom;
        private Double pitch;

        public Builder center(Point center) {
            this.center = center;
            return this;
        }

        public Builder zoom(double zoom) {
            this.zoom = zoom;
            return this;
        }

        public Builder pitch(double pitch) {
            this.pitch = pitch;
            return this;
        }

        public CameraOptions build() {
            return new CameraOptions(center, zoom, pitch);
        }
    }
}