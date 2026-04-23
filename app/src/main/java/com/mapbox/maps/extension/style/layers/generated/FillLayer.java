package com.mapbox.maps.extension.style.layers.generated;

import com.mapbox.maps.extension.style.layers.LayerUtils.NamedLayer;

public class FillLayer implements NamedLayer {
    private final String layerId;
    private final String sourceId;
    private Integer fillColor;
    private Double fillOpacity;
    private Integer fillOutlineColor;

    public FillLayer(String layerId, String sourceId) {
        this.layerId = layerId;
        this.sourceId = sourceId;
    }

    public FillLayer fillColor(int color) {
        this.fillColor = color;
        return this;
    }

    public FillLayer fillOpacity(double opacity) {
        this.fillOpacity = opacity;
        return this;
    }

    public FillLayer fillOutlineColor(int color) {
        this.fillOutlineColor = color;
        return this;
    }

    public String sourceId() {
        return sourceId;
    }

    public Integer fillColor() {
        return fillColor;
    }

    public Double fillOpacityValue() {
        return fillOpacity;
    }

    public Integer fillOutlineColorValue() {
        return fillOutlineColor;
    }

    @Override
    public String layerId() {
        return layerId;
    }
}