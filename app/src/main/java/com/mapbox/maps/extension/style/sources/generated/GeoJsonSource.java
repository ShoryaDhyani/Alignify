package com.mapbox.maps.extension.style.sources.generated;

import com.mapbox.geojson.Feature;
import com.mapbox.maps.extension.style.sources.SourceUtils.NamedSource;

public class GeoJsonSource implements NamedSource {
    private final String sourceId;
    private Feature feature;

    private GeoJsonSource(String sourceId, Feature feature) {
        this.sourceId = sourceId;
        this.feature = feature;
    }

    public GeoJsonSource feature(Feature feature) {
        this.feature = feature;
        return this;
    }

    public Feature feature() {
        return feature;
    }

    @Override
    public String sourceId() {
        return sourceId;
    }

    public static final class Builder {
        private final String sourceId;
        private Feature feature;

        public Builder(String sourceId) {
            this.sourceId = sourceId;
        }

        public Builder feature(Feature feature) {
            this.feature = feature;
            return this;
        }

        public GeoJsonSource build() {
            return new GeoJsonSource(sourceId, feature);
        }
    }
}