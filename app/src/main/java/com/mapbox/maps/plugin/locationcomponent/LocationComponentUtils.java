package com.mapbox.maps.plugin.locationcomponent;

import com.mapbox.maps.MapView;

public final class LocationComponentUtils {
    private LocationComponentUtils() {
    }

    public static LocationComponentPlugin getLocationComponent(MapView mapView) {
        return mapView.getLocationComponentPlugin();
    }
}