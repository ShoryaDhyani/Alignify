package com.mapbox.maps.extension.style.sources;

import com.mapbox.maps.Style;

public final class SourceUtils {
    private SourceUtils() {
    }

    public static Object getSource(Style style, String sourceId) {
        return style == null ? null : style.getSource(sourceId);
    }

    public static void addSource(Style style, Object source) {
        if (style == null || source == null) {
            return;
        }
        if (source instanceof NamedSource) {
            NamedSource namedSource = (NamedSource) source;
            style.addSource(namedSource.sourceId(), source);
        }
    }

    public interface NamedSource {
        String sourceId();
    }
}