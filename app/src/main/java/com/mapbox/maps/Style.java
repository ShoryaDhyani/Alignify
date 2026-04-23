package com.mapbox.maps;

import java.util.LinkedHashMap;
import java.util.Map;

public class Style {
    public static final String MAPBOX_STREETS = "mapbox://styles/mapbox/streets-v12";

    private final Map<String, Object> sources = new LinkedHashMap<>();
    private final Map<String, Object> layers = new LinkedHashMap<>();

    public void addSource(String sourceId, Object source) {
        sources.put(sourceId, source);
    }

    public Object getSource(String sourceId) {
        return sources.get(sourceId);
    }

    public void addLayer(String layerId, Object layer) {
        layers.put(layerId, layer);
    }

    public Object getLayer(String layerId) {
        return layers.get(layerId);
    }

    public void removeStyleSource(String sourceId) {
        sources.remove(sourceId);
    }

    public void removeStyleLayer(String layerId) {
        layers.remove(layerId);
    }
}