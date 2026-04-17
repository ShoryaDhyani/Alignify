package com.mapbox.maps;

public final class EdgeInsets {
    private final double top;
    private final double left;
    private final double bottom;
    private final double right;

    public EdgeInsets(double top, double left, double bottom, double right) {
        this.top = top;
        this.left = left;
        this.bottom = bottom;
        this.right = right;
    }

    public double top() {
        return top;
    }

    public double left() {
        return left;
    }

    public double bottom() {
        return bottom;
    }

    public double right() {
        return right;
    }
}