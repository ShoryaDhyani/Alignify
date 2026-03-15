package com.alignify;

import android.Manifest;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.util.Log;
import android.view.GestureDetector;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowManager;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
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
 * Strava-style activity for recording Run and Walk sessions.
 * Tracks GPS route on a live map, duration, distance, pace, calories, steps,
 * and avg speed.
 * Saves completed activities to Firestore via UserRepository.saveActivity().
 */
public class RunActivity extends AppCompatActivity {

    private static final String TAG = "RunActivity";
    private static final int LOCATION_PERMISSION_REQUEST = 2001;
    private static final String ROUTE_SOURCE_ID = "route-source";
    private static final String ROUTE_LAYER_ID = "route-layer";

    // Activity types
    private static final int TYPE_RUN = 0;
    private static final int TYPE_WALK = 1;

    // Calories per step
    private static final float RUN_CAL_PER_STEP = 0.06f;
    private static final float WALK_CAL_PER_STEP = 0.04f;

    // Timer states
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
    private boolean isMapTouched = false;

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

    // Session timestamps for Firestore save
    private long sessionStartTime = 0;

    // Swipe navigation
    private GestureDetector swipeDetector;

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

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_run);

        locationManager = (LocationManager) getSystemService(Context.LOCATION_SERVICE);
        initLocationListener();
        initViews();
        setupMap();
        setupTabs();
        setupControls();
        setupNavigation();
        ensureStepService();
        requestLocationPermission();

        // Setup swipe navigation
        swipeDetector = NavigationHelper.createSwipeDetector(this, NavigationHelper.NAV_RUN);
    }

    @Override
    public boolean dispatchTouchEvent(MotionEvent ev) {
        // Only allow swipe navigation when not recording and not touching the map
        if (swipeDetector != null && timerState == STATE_IDLE && !isMapTouched) {
            swipeDetector.onTouchEvent(ev);
        }
        return super.dispatchTouchEvent(ev);
    }

    // ============ Init ============

    private void initViews() {
        tvHeaderTitle = findViewById(R.id.tvHeaderTitle);
        tvRunStatus = findViewById(R.id.tvRunStatus);
        tvRunTimer = findViewById(R.id.tvRunTimer);
        tvRunDistance = findViewById(R.id.tvRunDistance);
        tvRunPace = findViewById(R.id.tvRunPace);
        tvRunCalories = findViewById(R.id.tvRunCalories);
        tvRunSteps = findViewById(R.id.tvRunSteps);
        tvRunAvgSpeed = findViewById(R.id.tvRunAvgSpeed);
        tvRunHint = findViewById(R.id.tvRunHint);

        tabRun = findViewById(R.id.tabRun);
        tabWalk = findViewById(R.id.tabWalk);

        btnStartPause = findViewById(R.id.btnRunStartPause);
        btnStop = findViewById(R.id.btnRunStop);
        btnLock = findViewById(R.id.btnRunLock);

        findViewById(R.id.btnBack).setOnClickListener(v -> finish());
    }

    private void setupMap() {
        mapView = findViewById(R.id.mapView);
        if (mapView == null) {
            Log.e(TAG, "MapView not found in layout!");
            return;
        }
        mapboxMap = mapView.getMapboxMap();
        String selectedStyleUri = AlignifyApp.resolveMapStyleUri(this);
        try {
            mapboxMap.loadStyle(selectedStyleUri, style -> {
                mapStyleLoaded = true;
                Log.d(TAG, "Mapbox style loaded: " + selectedStyleUri);

                if (hasLocationPermission()) {
                    enableLocationPuck();
                    zoomToCurrentLocation();
                }
            });
        } catch (Exception e) {
            Log.w(TAG, "Failed to load selected map style. Falling back to Streets.", e);
            mapboxMap.loadStyle(Style.MAPBOX_STREETS, style -> {
                mapStyleLoaded = true;
                Log.d(TAG, "Fallback map style loaded: streets");
                if (hasLocationPermission()) {
                    enableLocationPuck();
                    zoomToCurrentLocation();
                }
            });
        }

        // Feature 3: Intercept touch on MapView to prevent swipe navigation
        mapView.setOnTouchListener((v, event) -> {
            int action = event.getActionMasked();
            if (action == MotionEvent.ACTION_DOWN || action == MotionEvent.ACTION_MOVE) {
                isMapTouched = true;
                v.getParent().requestDisallowInterceptTouchEvent(true);
            } else if (action == MotionEvent.ACTION_UP || action == MotionEvent.ACTION_CANCEL) {
                isMapTouched = false;
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
        return ContextCompat.checkSelfPermission(this,
                Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED;
    }

    private void requestLocationPermission() {
        if (!hasLocationPermission()) {
            ActivityCompat.requestPermissions(this,
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
            } else {
                Toast.makeText(this, "Location permission needed for route tracking",
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
        tabRun.setTextColor(getColor(isRun ? android.R.color.white : R.color.text_secondary_dark));
        tabWalk.setBackgroundResource(isRun ? R.drawable.bg_tab_unselected : R.drawable.bg_tab_selected);
        tabWalk.setTextColor(getColor(isRun ? R.color.text_secondary_dark : android.R.color.white));

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
        stepsAtStart = StepCounterService.getStepsToday(this);
        sessionSteps = 0;
        elapsedMs = 0;
        gpsDistanceMetres = 0f;
        lastLocation = null;
        routePoints.clear();
        clearRoute();

        sessionStartTime = System.currentTimeMillis();
        timerStartMs = SystemClock.elapsedRealtime();
        timerState = STATE_RUNNING;
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

        startLocationUpdates();
        timerHandler.post(timerRunnable);
        updateUI();

        // Hide nav bar during recording
        NavigationHelper.hideNavBar(this);
    }

    private void pauseSession() {
        elapsedMs = SystemClock.elapsedRealtime() - timerStartMs;
        timerState = STATE_PAUSED;
        timerHandler.removeCallbacks(timerRunnable);
        stopLocationUpdates();
        getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        updateUI();
    }

    private void resumeSession() {
        timerStartMs = SystemClock.elapsedRealtime() - elapsedMs;
        timerState = STATE_RUNNING;
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        lastLocation = null; // don't count gap distance
        startLocationUpdates();
        timerHandler.post(timerRunnable);
        updateUI();
    }

    private void stopAndSave() {
        if (timerState == STATE_RUNNING) {
            elapsedMs = SystemClock.elapsedRealtime() - timerStartMs;
        }
        timerHandler.removeCallbacks(timerRunnable);
        stopLocationUpdates();
        timerState = STATE_IDLE;
        getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

        int durationSec = (int) (elapsedMs / 1000);
        float distanceKm = gpsDistanceMetres / 1000f;
        int calories = calculateCalories();
        long endTime = System.currentTimeMillis();

        // Save to FitnessDataManager (daily totals)
        if (durationSec > 0) {
            FitnessDataManager.getInstance(this).recordWorkout(durationSec, calories);
        }

        // Save activity to Firestore
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

        // Fit entire route on map
        fitRouteOnMap();

        tvRunStatus.setText("Saved!");
        tvRunHint.setText(formatSummary());
        updateUI();

        // Show nav bar after recording ends
        NavigationHelper.showNavBar(this);

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
                btnStartPause.setBackgroundTintList(
                        android.content.res.ColorStateList.valueOf(0xFF4CAF50));
                btnStop.setVisibility(View.GONE);
                btnLock.setVisibility(View.GONE);
                tabRun.setAlpha(1.0f);
                tabWalk.setAlpha(1.0f);
                break;

            case STATE_RUNNING:
                tvRunStatus.setText(activityType == TYPE_RUN ? "Running..." : "Walking...");
                btnStartPause.setImageResource(R.drawable.ic_pause);
                btnStartPause.setBackgroundTintList(
                        android.content.res.ColorStateList.valueOf(0xFFFFA000));
                btnStop.setVisibility(View.VISIBLE);
                btnLock.setVisibility(View.VISIBLE);
                tabRun.setAlpha(0.5f);
                tabWalk.setAlpha(0.5f);
                break;

            case STATE_PAUSED:
                tvRunStatus.setText("Paused");
                btnStartPause.setImageResource(R.drawable.ic_play);
                btnStartPause.setBackgroundTintList(
                        android.content.res.ColorStateList.valueOf(0xFF4CAF50));
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
        // Use GPS distance (accurate) instead of step-based estimation
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
        tvRunDistance.setText(String.format("%.2f km", distanceKm));
        tvRunSteps.setText(steps + " steps");
        tvRunCalories.setText(calories + " cal");
        tvRunAvgSpeed.setText(String.format("%.1f km/h", avgSpeedKmh));
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
            btnLock.setBackgroundTintList(
                    android.content.res.ColorStateList.valueOf(0xFFF44336));
            tvRunHint.setText("Screen locked — tap lock to unlock");
            btnStartPause.setAlpha(0.3f);
            btnStop.setAlpha(0.3f);
        } else {
            btnLock.setBackgroundTintList(
                    android.content.res.ColorStateList.valueOf(0xFF757575));
            tvRunHint.setText("");
            btnStartPause.setAlpha(1.0f);
            btnStop.setAlpha(1.0f);
        }
    }

    // ============ Navigation & Lifecycle ============

    private void setupNavigation() {
        NavigationHelper.setupBottomNavigation(this, NavigationHelper.NAV_RUN,
                findViewById(R.id.navHome), findViewById(R.id.navExercises),
                findViewById(R.id.navRun), findViewById(R.id.navAnalytics),
                findViewById(R.id.navProfile));
    }

    private void ensureStepService() {
        if (StepCounterHelper.hasAllPermissions(this)
                && StepCounterHelper.isStepCounterAvailable(this)) {
            StepCounterHelper.startStepTracking(this, false);
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        LocalBroadcastManager.getInstance(this).registerReceiver(
                stepReceiver, new IntentFilter(StepCounterService.ACTION_STEP_UPDATE));
    }

    @Override
    protected void onPause() {
        super.onPause();
        LocalBroadcastManager.getInstance(this).unregisterReceiver(stepReceiver);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        timerHandler.removeCallbacks(timerRunnable);
        stopLocationUpdates();
        getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
    }
}
