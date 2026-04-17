package com.mapbox.maps.extension.style.layers;

import com.mapbox.maps.Style;

public final class LayerUtils {
    private LayerUtils() {
    }

    public static void addLayer(Style style, Object layer) {
        if (style == null || layer == null) {
            return;
        }
        if (layer instanceof NamedLayer) {
            NamedLayer namedLayer = (NamedLayer) layer;
            style.addLayer(namedLayer.layerId(), layer);
        }
    }

    public interface NamedLayer {
        String layerId();
    }
}