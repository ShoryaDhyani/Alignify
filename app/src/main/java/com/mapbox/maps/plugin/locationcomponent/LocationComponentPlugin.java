package com.mapbox.maps.plugin.locationcomponent;

import com.mapbox.maps.plugin.LocationPuck2D;
import com.mapbox.maps.plugin.PuckBearing;

import java.util.ArrayList;
import java.util.List;

public class LocationComponentPlugin {
    private boolean enabled;
    private boolean puckBearingEnabled;
    private PuckBearing puckBearing = PuckBearing.HEADING;
    private LocationPuck2D locationPuck;
    private final List<OnIndicatorPositionChangedListener> positionListeners = new ArrayList<>();
    private final List<OnIndicatorBearingChangedListener> bearingListeners = new ArrayList<>();

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public void setPuckBearingEnabled(boolean enabled) {
        this.puckBearingEnabled = enabled;
    }

    public void setPuckBearing(PuckBearing puckBearing) {
        this.puckBearing = puckBearing;
    }

    public void setLocationPuck(LocationPuck2D locationPuck) {
        this.locationPuck = locationPuck;
    }

    public void addOnIndicatorPositionChangedListener(OnIndicatorPositionChangedListener listener) {
        if (listener != null && !positionListeners.contains(listener)) {
            positionListeners.add(listener);
        }
    }

    public void removeOnIndicatorPositionChangedListener(OnIndicatorPositionChangedListener listener) {
        positionListeners.remove(listener);
    }

    public void addOnIndicatorBearingChangedListener(OnIndicatorBearingChangedListener listener) {
        if (listener != null && !bearingListeners.contains(listener)) {
            bearingListeners.add(listener);
        }
    }

    public void removeOnIndicatorBearingChangedListener(OnIndicatorBearingChangedListener listener) {
        bearingListeners.remove(listener);
    }

    public boolean isEnabled() {
        return enabled;
    }

    public boolean isPuckBearingEnabled() {
        return puckBearingEnabled;
    }

    public PuckBearing getPuckBearing() {
        return puckBearing;
    }

    public LocationPuck2D getLocationPuck() {
        return locationPuck;
    }
}