package com.mapbox.maps.plugin;

import com.mapbox.maps.ImageHolder;

public class LocationPuck2D {
    private final ImageHolder topImage;
    private final ImageHolder shadowImage;

    public LocationPuck2D(ImageHolder topImage, Object unused, ImageHolder shadowImage) {
        this.topImage = topImage;
        this.shadowImage = shadowImage;
    }

    public ImageHolder topImage() {
        return topImage;
    }

    public ImageHolder shadowImage() {
        return shadowImage;
    }
}