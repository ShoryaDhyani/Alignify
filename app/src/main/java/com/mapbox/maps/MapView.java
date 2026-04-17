package com.mapbox.maps;

import android.content.Context;
import android.util.AttributeSet;
import android.widget.FrameLayout;

import com.mapbox.maps.plugin.locationcomponent.LocationComponentPlugin;

public class MapView extends FrameLayout {
    private final MapboxMap mapboxMap = new MapboxMap();
    private final LocationComponentPlugin locationComponentPlugin = new LocationComponentPlugin();

    public MapView(Context context) {
        super(context);
    }

    public MapView(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    public MapView(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
    }

    public MapboxMap getMapboxMap() {
        return mapboxMap;
    }

    public LocationComponentPlugin getLocationComponentPlugin() {
        return locationComponentPlugin;
    }
}