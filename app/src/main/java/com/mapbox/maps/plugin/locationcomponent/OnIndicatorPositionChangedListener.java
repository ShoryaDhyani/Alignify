package com.mapbox.maps.plugin.locationcomponent;

import com.mapbox.geojson.Point;

public interface OnIndicatorPositionChangedListener {
    void onIndicatorPositionChanged(Point point);
}