package com.mapbox.maps;

import com.mapbox.geojson.Point;

import java.util.function.Consumer;

public class MapboxMap {
    private Style style;

    public void loadStyle(String styleUri, Consumer<Style> onStyleLoaded) {
        style = new Style();
        if (onStyleLoaded != null) {
            onStyleLoaded.accept(style);
        }
    }

    public Style getStyle() {
        return style;
    }

    public void setCamera(CameraOptions cameraOptions) {
    }

    public CameraOptions cameraForCoordinateBounds(CoordinateBounds bounds, EdgeInsets padding,
            double bearing, double pitch) {
        Point center = null;
        if (bounds != null && bounds.southwest() != null && bounds.northeast() != null) {
            center = Point.fromLngLat(
                    (bounds.southwest().longitude() + bounds.northeast().longitude()) / 2.0,
                    (bounds.southwest().latitude() + bounds.northeast().latitude()) / 2.0);
        }
        return new CameraOptions.Builder().center(center).zoom(15.0).pitch(pitch).build();
    }

    public CameraOptions cameraForCoordinateBounds(CoordinateBounds bounds, EdgeInsets padding,
            Object bearing, Object pitch, Object anchor, Object animationDuration) {
        return cameraForCoordinateBounds(bounds, padding, 0.0, 0.0);
    }
}