package com.alignify;

import android.Manifest;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;
import androidx.localbroadcastmanager.content.LocalBroadcastManager;

import com.alignify.data.FitnessDataManager;
import com.alignify.data.UserRepository;
import com.alignify.service.StepCounterService;
import com.alignify.util.NavigationHelper;
import com.alignify.util.StepCounterHelper;
import com.mapbox.geojson.Feature;
import com.mapbox.geojson.LineString;
import com.mapbox.geojson.Point;
import com.mapbox.maps.CameraOptions;
import com.mapbox.maps.CoordinateBounds;
import com.mapbox.maps.EdgeInsets;
import com.mapbox.maps.MapView;
import com.mapbox.maps.MapboxMap;
import com.mapbox.maps.Style;
import com.mapbox.maps.extension.style.layers.LayerUtils;
import com.mapbox.maps.extension.style.layers.generated.LineLayer;
import com.mapbox.maps.extension.style.layers.properties.generated.LineCap;
import com.mapbox.maps.extension.style.layers.properties.generated.LineJoin;
import com.mapbox.maps.extension.style.sources.SourceUtils;
import com.mapbox.maps.extension.style.sources.generated.GeoJsonSource;
import com.mapbox.maps.plugin.LocationPuck2D;
import com.mapbox.maps.plugin.PuckBearing;
import com.mapbox.maps.plugin.locationcomponent.LocationComponentPlugin;
import com.mapbox.maps.plugin.locationcomponent.LocationComponentUtils;
import com.mapbox.maps.ImageHolder;
import com.google.android.material.floatingactionbutton.FloatingActionButton;

import java.util.ArrayList;
import java.util.List;

/**
 * Run/Walk tracking fragment with Mapbox Maps SDK v11.
 * Converted from RunActivity for ViewPager2-based navigation.
 * Disables ViewPager2 swiping during active recording.
 */
public class RunFragment extends Fragment {

    private static final String TAG = "RunFragment";
    private static final int LOCATION_PERMISSION_REQUEST = 2001;
    private static final String ROUTE_SOURCE_ID = "route-source";
    private static final String ROUTE_LAYER_ID = "route-layer";

    private static final int TYPE_RUN = 0;
    private static final int TYPE_WALK = 1;

    private static final float RUN_CAL_PER_STEP = 0.06f;
    private static final float WALK_CAL_PER_STEP = 0.04f;

    private static final int STATE_IDLE = 0;
    private static final int STATE_RUNNING = 1;
    private static final int STATE_PAUSED = 2;

    // UI
    private TextView tvHeaderTitle, tvRunStatus, tvRunTimer, tvRunDistance;
    private TextView tvRunPace, tvRunCalories, tvRunSteps, tvRunAvgSpeed, tvRunHint;
    private TextView tabRun, tabWalk;
    private FloatingActionButton btnStartPause, btnStop, btnLock;

    // Map
    private MapView mapView;
    private MapboxMap mapboxMap;
    private LocationManager locationManager;
    private LocationListener locationListener;
    private final List<Point> routePoints = new ArrayList<>();
    private boolean mapStyleLoaded = false;

    // State
    private int activityType = TYPE_RUN;
    private int timerState = STATE_IDLE;
    private boolean isLocked = false;

    // Timer
    private final Handler timerHandler = new Handler(Looper.getMainLooper());
    private long elapsedMs = 0;
    private long timerStartMs = 0;

    // GPS distance (metres)
    private float gpsDistanceMetres = 0f;
    private Location lastLocation = null;

    // Steps
    private int stepsAtStart = 0;
    private int sessionSteps = 0;

    // Session timestamps
    private long sessionStartTime = 0;

    // Step broadcast receiver
    private final BroadcastReceiver stepReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (timerState == STATE_RUNNING) {
                int totalSteps = intent.getIntExtra(StepCounterService.EXTRA_STEPS_TODAY, 0);
                sessionSteps = totalSteps - stepsAtStart;
                if (sessionSteps < 0)
                    sessionSteps = 0;
                updateStats();
            }
        }
    };

    private final Runnable timerRunnable = new Runnable() {
        @Override
        public void run() {
            if (timerState == STATE_RUNNING) {
                elapsedMs = SystemClock.elapsedRealtime() - timerStartMs;
                updateTimerDisplay();
                updateStats();
                timerHandler.postDelayed(this, 1000);
            }
        }
    };

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
            @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.activity_run, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        // Hide the bottom nav bar from the inflated layout
        View bottomNav = view.findViewById(R.id.bottomNavContainer);
        if (bottomNav != null) {
            bottomNav.setVisibility(View.GONE);
        }

        // Hide the back button (not needed in ViewPager2)
        View btnBack = view.findViewById(R.id.btnBack);
        if (btnBack != null) {
            btnBack.setVisibility(View.GONE);
        }

        locationManager = (LocationManager) requireContext().getSystemService(Context.LOCATION_SERVICE);
        initLocationListener();
        initViews(view);
        setupMap(view);
        setupTabs();
        setupControls();
        ensureStepService();
        requestLocationPermission();
    }

    @Override
    public void onResume() {
        super.onResume();
        if (!isAdded())
            return;
        LocalBroadcastManager.getInstance(requireContext()).registerReceiver(
                stepReceiver, new IntentFilter(StepCounterService.ACTION_STEP_UPDATE));
    }

    @Override
    public void onPause() {
        super.onPause();
        if (isAdded()) {
            LocalBroadcastManager.getInstance(requireContext()).unregisterReceiver(stepReceiver);
        }
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        timerHandler.removeCallbacks(timerRunnable);
        stopLocationUpdates();
        if (getActivity() != null) {
            getActivity().getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        }
    }

    // ============ Init ============

    private void initViews(View view) {
        tvHeaderTitle = view.findViewById(R.id.tvHeaderTitle);
        tvRunStatus = view.findViewById(R.id.tvRunStatus);
        tvRunTimer = view.findViewById(R.id.tvRunTimer);
        tvRunDistance = view.findViewById(R.id.tvRunDistance);
        tvRunPace = view.findViewById(R.id.tvRunPace);
        tvRunCalories = view.findViewById(R.id.tvRunCalories);
        tvRunSteps = view.findViewById(R.id.tvRunSteps);
        tvRunAvgSpeed = view.findViewById(R.id.tvRunAvgSpeed);
        tvRunHint = view.findViewById(R.id.tvRunHint);

        tabRun = view.findViewById(R.id.tabRun);
        tabWalk = view.findViewById(R.id.tabWalk);

        btnStartPause = view.findViewById(R.id.btnRunStartPause);
        btnStop = view.findViewById(R.id.btnRunStop);
        btnLock = view.findViewById(R.id.btnRunLock);
    }

    private void setupMap(View view) {
        mapView = view.findViewById(R.id.mapView);
        if (mapView == null) {
            Log.e(TAG, "MapView not found in layout!");
            return;
        }
        mapboxMap = mapView.getMapboxMap();
        mapboxMap.loadStyle(Style.MAPBOX_STREETS, style -> {
            mapStyleLoaded = true;
            Log.d(TAG, "Mapbox style loaded");

            // Enable location puck (blue dot)
            if (hasLocationPermission()) {
                enableLocationPuck();
                zoomToCurrentLocation();
            }
        });

        // Feature 3: Intercept touch on MapView to prevent ViewPager2 swipe
        mapView.setOnTouchListener((v, event) -> {
            int action = event.getActionMasked();
            if (action == MotionEvent.ACTION_DOWN || action == MotionEvent.ACTION_MOVE) {
                v.getParent().requestDisallowInterceptTouchEvent(true);
            } else if (action == MotionEvent.ACTION_UP || action == MotionEvent.ACTION_CANCEL) {
                v.getParent().requestDisallowInterceptTouchEvent(false);
            }
            return false; // Don't consume — let MapView handle its own gestures
        });
    }

    private void enableLocationPuck() {
        LocationComponentPlugin locationComponent = LocationComponentUtils.getLocationComponent(mapView);
        locationComponent.setEnabled(true);
        locationComponent.setPuckBearingEnabled(true);
        locationComponent.setPuckBearing(PuckBearing.HEADING);
        locationComponent.setLocationPuck(
                new LocationPuck2D(
                        ImageHolder.Companion.from(R.drawable.ic_location_dot_cyan),
                        ImageHolder.Companion.from(R.drawable.ic_location_heading_arrow),
                        null));
    }

    private void initLocationListener() {
        locationListener = new LocationListener() {
            @Override
            public void onLocationChanged(@NonNull Location location) {
                if (timerState != STATE_RUNNING)
                    return;
                if (location.getAccuracy() > 30)
                    return;

                Point point = Point.fromLngLat(location.getLongitude(), location.getLatitude());

                if (lastLocation != null) {
                    gpsDistanceMetres += lastLocation.distanceTo(location);
                }
                lastLocation = location;

                routePoints.add(point);
                if (mapboxMap != null && mapStyleLoaded) {
                    drawRoute();
                    mapboxMap.setCamera(
                            new CameraOptions.Builder()
                                    .center(point)
                                    .zoom(17.0)
                                    .build());
                }
                updateStats();
            }

            @Override
            public void onProviderEnabled(@NonNull String provider) {
            }

            @Override
            public void onProviderDisabled(@NonNull String provider) {
            }
        };
    }

    // ============ Location Permission ============

    private boolean hasLocationPermission() {
        return isAdded() && ContextCompat.checkSelfPermission(requireContext(),
                Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED;
    }

    private void requestLocationPermission() {
        if (!hasLocationPermission() && isAdded()) {
            ActivityCompat.requestPermissions(requireActivity(),
                    new String[] { Manifest.permission.ACCESS_FINE_LOCATION },
                    LOCATION_PERMISSION_REQUEST);
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions,
            @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == LOCATION_PERMISSION_REQUEST) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                if (mapboxMap != null && mapStyleLoaded) {
                    enableLocationPuck();
                    zoomToCurrentLocation();
                }
            } else if (isAdded()) {
                Toast.makeText(requireContext(), "Location permission needed for route tracking",
                        Toast.LENGTH_SHORT).show();
            }
        }
    }

    private void zoomToCurrentLocation() {
        if (!hasLocationPermission() || locationManager == null)
            return;
        try {
            Location location = locationManager.getLastKnownLocation(LocationManager.GPS_PROVIDER);
            if (location == null) {
                location = locationManager.getLastKnownLocation(LocationManager.NETWORK_PROVIDER);
            }
            if (location != null && mapboxMap != null) {
                Point pos = Point.fromLngLat(location.getLongitude(), location.getLatitude());
                mapboxMap.setCamera(
                        new CameraOptions.Builder()
                                .center(pos)
                                .zoom(16.0)
                                .build());
            } else if (mapboxMap != null) {
                // No cached location — request a one-shot update to center camera on first fix
                requestInitialLocationForCamera();
            }
        } catch (SecurityException e) {
            Log.w(TAG, "Location permission not granted", e);
        }
    }

    private void requestInitialLocationForCamera() {
        if (!hasLocationPermission() || locationManager == null)
            return;
        try {
            LocationListener oneShot = new LocationListener() {
                @Override
                public void onLocationChanged(@NonNull Location loc) {
                    if (mapboxMap != null) {
                        Point pos = Point.fromLngLat(loc.getLongitude(), loc.getLatitude());
                        mapboxMap.setCamera(
                                new CameraOptions.Builder()
                                        .center(pos)
                                        .zoom(16.0)
                                        .build());
                    }
                    if (locationManager != null)
                        locationManager.removeUpdates(this);
                }

                @Override
                public void onProviderEnabled(@NonNull String p) {
                }

                @Override
                public void onProviderDisabled(@NonNull String p) {
                }
            };
            locationManager.requestLocationUpdates(
                    LocationManager.GPS_PROVIDER, 0, 0, oneShot, Looper.getMainLooper());
        } catch (SecurityException e) {
            Log.w(TAG, "Cannot request initial location", e);
        }
    }

    // ============ GPS Tracking ============

    private void startLocationUpdates() {
        if (!hasLocationPermission() || locationManager == null)
            return;
        try {
            locationManager.requestLocationUpdates(
                    LocationManager.GPS_PROVIDER, 3000, 5f, locationListener, Looper.getMainLooper());
        } catch (SecurityException e) {
            Log.e(TAG, "Location permission lost", e);
        }
    }

    private void stopLocationUpdates() {
        if (locationManager != null && locationListener != null) {
            locationManager.removeUpdates(locationListener);
        }
    }

    private void drawRoute() {
        if (mapboxMap == null || !mapStyleLoaded || routePoints.size() < 2)
            return;

        Style style = mapboxMap.getStyle();
        if (style == null)
            return;

        LineString lineString = LineString.fromLngLats(routePoints);
        GeoJsonSource existingSource = (GeoJsonSource) SourceUtils.getSource(style, ROUTE_SOURCE_ID);

        if (existingSource != null) {
            existingSource.feature(Feature.fromGeometry(lineString));
        } else {
            GeoJsonSource source = new GeoJsonSource.Builder(ROUTE_SOURCE_ID)
                    .feature(Feature.fromGeometry(lineString))
                    .build();
            SourceUtils.addSource(style, source);

            String lineColor = activityType == TYPE_RUN ? "#4CAF50" : "#FF9800";
            LineLayer lineLayer = new LineLayer(ROUTE_LAYER_ID, ROUTE_SOURCE_ID);
            lineLayer.lineColor(Color.parseColor(lineColor));
            lineLayer.lineWidth(8.0);
            lineLayer.lineCap(LineCap.ROUND);
            lineLayer.lineJoin(LineJoin.ROUND);
            LayerUtils.addLayer(style, lineLayer);
        }
    }

    private void clearRoute() {
        if (mapboxMap == null || !mapStyleLoaded)
            return;
        Style style = mapboxMap.getStyle();
        if (style == null)
            return;
        try {
            style.removeStyleLayer(ROUTE_LAYER_ID);
            style.removeStyleSource(ROUTE_SOURCE_ID);
        } catch (Exception e) {
            // Layer/source may not exist yet
        }
    }

    private void fitRouteOnMap() {
        if (mapboxMap == null || routePoints.size() < 2)
            return;

        double minLng = Double.MAX_VALUE, maxLng = -Double.MAX_VALUE;
        double minLat = Double.MAX_VALUE, maxLat = -Double.MAX_VALUE;
        for (Point pt : routePoints) {
            minLng = Math.min(minLng, pt.longitude());
            maxLng = Math.max(maxLng, pt.longitude());
            minLat = Math.min(minLat, pt.latitude());
            maxLat = Math.max(maxLat, pt.latitude());
        }

        try {
            CoordinateBounds bounds = new CoordinateBounds(
                    Point.fromLngLat(minLng, minLat),
                    Point.fromLngLat(maxLng, maxLat));
            CameraOptions camera = mapboxMap.cameraForCoordinateBounds(
                    bounds, new EdgeInsets(80, 80, 80, 80), null, null, null, null);
            mapboxMap.setCamera(camera);
        } catch (Exception e) {
            Log.e(TAG, "Error fitting route bounds", e);
        }
    }

    // ============ Tabs ============

    private void setupTabs() {
        tabRun.setOnClickListener(v -> {
            if (timerState == STATE_IDLE)
                setActivityType(TYPE_RUN);
        });
        tabWalk.setOnClickListener(v -> {
            if (timerState == STATE_IDLE)
                setActivityType(TYPE_WALK);
        });
    }

    private void setActivityType(int type) {
        activityType = type;
        boolean isRun = (type == TYPE_RUN);

        tabRun.setBackgroundResource(isRun ? R.drawable.bg_tab_selected : R.drawable.bg_tab_unselected);
        tabRun.setTextColor(requireContext().getColor(isRun ? android.R.color.white : R.color.text_secondary_dark));
        tabWalk.setBackgroundResource(isRun ? R.drawable.bg_tab_unselected : R.drawable.bg_tab_selected);
        tabWalk.setTextColor(requireContext().getColor(isRun ? R.color.text_secondary_dark : android.R.color.white));

        tvHeaderTitle.setText(isRun ? "Running" : "Walking");
        tvRunHint.setText(isRun ? "Tap Start to begin your run" : "Tap Start to begin your walk");
    }

    // ============ Controls ============

    private void setupControls() {
        btnStartPause.setOnClickListener(v -> {
            if (isLocked)
                return;
            switch (timerState) {
                case STATE_IDLE:
                    startSession();
                    break;
                case STATE_RUNNING:
                    pauseSession();
                    break;
                case STATE_PAUSED:
                    resumeSession();
                    break;
            }
        });
        btnStop.setOnClickListener(v -> {
            if (isLocked)
                return;
            stopAndSave();
        });
        btnLock.setOnClickListener(v -> toggleLock());
    }

    // ============ Session Lifecycle ============

    private void startSession() {
        if (!isAdded())
            return;
        stepsAtStart = StepCounterService.getStepsToday(requireContext());
        sessionSteps = 0;
        elapsedMs = 0;
        gpsDistanceMetres = 0f;
        lastLocation = null;
        routePoints.clear();
        clearRoute();

        sessionStartTime = System.currentTimeMillis();
        timerStartMs = SystemClock.elapsedRealtime();
        timerState = STATE_RUNNING;
        requireActivity().getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

        startLocationUpdates();
        timerHandler.post(timerRunnable);
        updateUI();

        // Disable ViewPager2 swiping during recording
        if (getActivity() instanceof HomeActivity) {
            ((HomeActivity) getActivity()).setSwipeEnabled(false);
        }
    }

    private void pauseSession() {
        elapsedMs = SystemClock.elapsedRealtime() - timerStartMs;
        timerState = STATE_PAUSED;
        timerHandler.removeCallbacks(timerRunnable);
        stopLocationUpdates();
        if (getActivity() != null) {
            getActivity().getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        }
        updateUI();
    }

    private void resumeSession() {
        if (!isAdded())
            return;
        timerStartMs = SystemClock.elapsedRealtime() - elapsedMs;
        timerState = STATE_RUNNING;
        requireActivity().getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        lastLocation = null;
        startLocationUpdates();
        timerHandler.post(timerRunnable);
        updateUI();
    }

    private void stopAndSave() {
        if (!isAdded())
            return;
        if (timerState == STATE_RUNNING) {
            elapsedMs = SystemClock.elapsedRealtime() - timerStartMs;
        }
        timerHandler.removeCallbacks(timerRunnable);
        stopLocationUpdates();
        timerState = STATE_IDLE;
        requireActivity().getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

        int durationSec = (int) (elapsedMs / 1000);
        float distanceKm = gpsDistanceMetres / 1000f;
        int calories = calculateCalories();
        long endTime = System.currentTimeMillis();

        if (durationSec > 0) {
            FitnessDataManager.getInstance(requireContext()).recordWorkout(durationSec, calories);
        }

        String type = activityType == TYPE_RUN ? "run" : "walk";
        UserRepository.getInstance().saveActivity(
                type, "manual", sessionStartTime, endTime,
                durationSec, distanceKm, calories,
                new UserRepository.OnCompleteListener() {
                    @Override
                    public void onSuccess() {
                        Log.d(TAG, "Activity saved to Firestore");
                    }

                    @Override
                    public void onError(String error) {
                        Log.e(TAG, "Failed to save activity: " + error);
                    }
                });

        fitRouteOnMap();
        tvRunStatus.setText("Saved!");
        tvRunHint.setText(formatSummary());
        updateUI();

        // Re-enable ViewPager2 swiping after recording ends
        if (getActivity() instanceof HomeActivity) {
            ((HomeActivity) getActivity()).setSwipeEnabled(true);
        }

        timerHandler.postDelayed(this::resetSession, 4000);
    }

    private void resetSession() {
        elapsedMs = 0;
        sessionSteps = 0;
        gpsDistanceMetres = 0f;
        lastLocation = null;
        routePoints.clear();
        clearRoute();

        timerState = STATE_IDLE;
        updateTimerDisplay();
        updateStatsDisplay(0f, 0, 0, 0f, "0'00\"/km");
        tvRunStatus.setText("Ready");
        tvRunHint.setText(activityType == TYPE_RUN
                ? "Tap Start to begin your run"
                : "Tap Start to begin your walk");
        updateUI();
        zoomToCurrentLocation();
    }

    // ============ UI Updates ============

    private void updateUI() {
        switch (timerState) {
            case STATE_IDLE:
                tvRunStatus.setText("Ready");
                btnStartPause.setImageResource(R.drawable.ic_play);
                btnStartPause.setBackgroundTintList(ColorStateList.valueOf(0xFF4CAF50));
                btnStop.setVisibility(View.GONE);
                btnLock.setVisibility(View.GONE);
                tabRun.setAlpha(1.0f);
                tabWalk.setAlpha(1.0f);
                break;

            case STATE_RUNNING:
                tvRunStatus.setText(activityType == TYPE_RUN ? "Running..." : "Walking...");
                btnStartPause.setImageResource(R.drawable.ic_pause);
                btnStartPause.setBackgroundTintList(ColorStateList.valueOf(0xFFFFA000));
                btnStop.setVisibility(View.VISIBLE);
                btnLock.setVisibility(View.VISIBLE);
                tabRun.setAlpha(0.5f);
                tabWalk.setAlpha(0.5f);
                break;

            case STATE_PAUSED:
                tvRunStatus.setText("Paused");
                btnStartPause.setImageResource(R.drawable.ic_play);
                btnStartPause.setBackgroundTintList(ColorStateList.valueOf(0xFF4CAF50));
                btnStop.setVisibility(View.VISIBLE);
                btnLock.setVisibility(View.GONE);
                tabRun.setAlpha(0.5f);
                tabWalk.setAlpha(0.5f);
                break;
        }
    }

    private void updateTimerDisplay() {
        long totalSec = elapsedMs / 1000;
        int hours = (int) (totalSec / 3600);
        int minutes = (int) ((totalSec % 3600) / 60);
        int seconds = (int) (totalSec % 60);
        tvRunTimer.setText(String.format("%02d:%02d:%02d", hours, minutes, seconds));
    }

    private void updateStats() {
        float distanceKm = gpsDistanceMetres / 1000f;
        int calories = calculateCalories();
        float elapsedHours = elapsedMs / 3_600_000f;
        float avgSpeedKmh = elapsedHours > 0 ? distanceKm / elapsedHours : 0f;

        String paceStr;
        if (distanceKm > 0.01f) {
            float paceMinPerKm = (elapsedMs / 60_000f) / distanceKm;
            int paceMin = (int) paceMinPerKm;
            int paceSec = (int) ((paceMinPerKm - paceMin) * 60);
            paceStr = String.format("%d'%02d\"/km", paceMin, paceSec);
        } else {
            paceStr = "0'00\"/km";
        }

        updateStatsDisplay(distanceKm, sessionSteps, calories, avgSpeedKmh, paceStr);
    }

    private void updateStatsDisplay(float distanceKm, int steps, int calories,
            float avgSpeedKmh, String paceStr) {
        if (tvRunDistance != null)
            tvRunDistance.setText(String.format("%.2f km", distanceKm));
        if (tvRunSteps != null)
            tvRunSteps.setText(steps + " steps");
        if (tvRunCalories != null)
            tvRunCalories.setText(calories + " cal");
        if (tvRunAvgSpeed != null)
            tvRunAvgSpeed.setText(String.format("%.1f km/h", avgSpeedKmh));
        if (tvRunPace != null)
            tvRunPace.setText(paceStr);
    }

    private int calculateCalories() {
        float calPerStep = activityType == TYPE_RUN ? RUN_CAL_PER_STEP : WALK_CAL_PER_STEP;
        return (int) (sessionSteps * calPerStep);
    }

    private String formatSummary() {
        float distanceKm = gpsDistanceMetres / 1000f;
        long totalSec = elapsedMs / 1000;
        String typeName = activityType == TYPE_RUN ? "Run" : "Walk";
        return String.format("%s: %.2f km in %02d:%02d:%02d • %d steps • %d cal",
                typeName, distanceKm,
                (int) (totalSec / 3600), (int) ((totalSec % 3600) / 60), (int) (totalSec % 60),
                sessionSteps, calculateCalories());
    }

    // ============ Lock Screen ============

    private void toggleLock() {
        isLocked = !isLocked;
        if (isLocked) {
            btnLock.setBackgroundTintList(ColorStateList.valueOf(0xFFF44336));
            tvRunHint.setText("Screen locked — tap lock to unlock");
            btnStartPause.setAlpha(0.3f);
            btnStop.setAlpha(0.3f);
        } else {
            btnLock.setBackgroundTintList(ColorStateList.valueOf(0xFF757575));
            tvRunHint.setText("");
            btnStartPause.setAlpha(1.0f);
            btnStop.setAlpha(1.0f);
        }
    }

    // ============ Step Service ============

    private void ensureStepService() {
        if (!isAdded())
            return;
        if (StepCounterHelper.hasAllPermissions(requireContext())
                && StepCounterHelper.isStepCounterAvailable(requireContext())) {
            StepCounterHelper.startStepTracking(requireContext(), false);
        }
    }
}
