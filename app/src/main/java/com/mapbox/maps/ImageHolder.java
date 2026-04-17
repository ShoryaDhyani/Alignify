package com.mapbox.maps;

public final class ImageHolder {
    private final Object value;

    private ImageHolder(Object value) {
        this.value = value;
    }

    public Object value() {
        return value;
    }

    public static final class Companion {
        private Companion() {
        }

        public static ImageHolder from(int resourceId) {
            return new ImageHolder(resourceId);
        }
    }
}