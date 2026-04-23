package com.mapbox.maps.extension.style.layers.generated;

import com.mapbox.maps.extension.style.layers.LayerUtils.NamedLayer;
import com.mapbox.maps.extension.style.layers.properties.generated.LineCap;
import com.mapbox.maps.extension.style.layers.properties.generated.LineJoin;

public class LineLayer implements NamedLayer {
    private final String layerId;
    private final String sourceId;
    private Integer lineColor;
    private Double lineWidth;
    private LineCap lineCap;
    private LineJoin lineJoin;

    public LineLayer(String layerId, String sourceId) {
        this.layerId = layerId;
        this.sourceId = sourceId;
    }

    public LineLayer lineColor(int color) {
        this.lineColor = color;
        return this;
    }

    public LineLayer lineWidth(double width) {
        this.lineWidth = width;
        return this;
    }

    public LineLayer lineCap(LineCap lineCap) {
        this.lineCap = lineCap;
        return this;
    }

    public LineLayer lineJoin(LineJoin lineJoin) {
        this.lineJoin = lineJoin;
        return this;
    }

    public String sourceId() {
        return sourceId;
    }

    @Override
    public String layerId() {
        return layerId;
    }
}