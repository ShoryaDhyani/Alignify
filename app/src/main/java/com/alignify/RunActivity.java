package com.alignify;

import android.Manifest;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.location.Location;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.util.Log;
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
import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.LocationCallback;
import com.google.android.gms.location.LocationRequest;
import com.google.android.gms.location.LocationResult;
import com.google.android.gms.location.LocationServices;
import com.google.android.gms.location.Priority;
import com.google.android.gms.maps.CameraUpdateFactory;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.OnMapReadyCallback;
import com.google.android.gms.maps.SupportMapFragment;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.LatLngBounds;
import com.google.android.gms.maps.model.PolylineOptions;
import com.google.android.material.floatingactionbutton.FloatingActionButton;

import java.util.ArrayList;
import java.util.List;

/**
 * Strava-style activity for recording Run and Walk sessions.
 * Tracks GPS route on a live map, duration, distance, pace, calories, steps,
 * and avg speed.
 * Saves completed activities to Firestore via UserRepository.saveActivity().
 */
public class RunActivity extends AppCompatActivity implements OnMapReadyCallback {

    private static final String TAG = "RunActivity";
    private static final int LOCATION_PERMISSION_REQUEST = 2001;

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
    private GoogleMap googleMap;
    private FusedLocationProviderClient fusedLocationClient;
    private LocationCallback locationCallback;
    private final List<LatLng> routePoints = new ArrayList<>();
    private PolylineOptions polylineOptions;

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

    // Session timestamps for Firestore save
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

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_run);

        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this);
        initLocationCallback();
        initViews();
        setupMap();
        setupTabs();
        setupControls();
        setupNavigation();
        ensureStepService();
        requestLocationPermission();
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
        SupportMapFragment mapFragment = (SupportMapFragment) getSupportFragmentManager()
                .findFragmentById(R.id.mapFragment);
        if (mapFragment != null) {
            mapFragment.getMapAsync(this);
        }
    }

    @Override
    public void onMapReady(@NonNull GoogleMap map) {
        googleMap = map;
        googleMap.getUiSettings().setZoomControlsEnabled(false);
        googleMap.getUiSettings().setMyLocationButtonEnabled(false);

        if (hasLocationPermission()) {
            try {
                googleMap.setMyLocationEnabled(true);
            } catch (SecurityException ignored) {
            }
            zoomToCurrentLocation();
        }
    }

    private void initLocationCallback() {
        locationCallback = new LocationCallback() {
            @Override
            public void onLocationResult(@NonNull LocationResult locationResult) {
                if (timerState != STATE_RUNNING)
                    return;
                for (Location location : locationResult.getLocations()) {
                    if (location.getAccuracy() > 30)
                        continue; // skip inaccurate fixes

                    LatLng point = new LatLng(location.getLatitude(), location.getLongitude());

                    // Accumulate GPS distance
                    if (lastLocation != null) {
                        gpsDistanceMetres += lastLocation.distanceTo(location);
                    }
                    lastLocation = location;

                    // Draw route on map
                    routePoints.add(point);
                    if (googleMap != null) {
                        drawRoute();
                        googleMap.animateCamera(CameraUpdateFactory.newLatLngZoom(point, 17f));
                    }

                    updateStats();
                }
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
                if (googleMap != null) {
                    try {
                        googleMap.setMyLocationEnabled(true);
                    } catch (SecurityException ignored) {
                    }
                    zoomToCurrentLocation();
                }
            } else {
                Toast.makeText(this, "Location permission needed for route tracking",
                        Toast.LENGTH_SHORT).show();
            }
        }
    }

    private void zoomToCurrentLocation() {
        if (!hasLocationPermission())
            return;
        try {
            fusedLocationClient.getLastLocation().addOnSuccessListener(location -> {
                if (location != null && googleMap != null) {
                    LatLng pos = new LatLng(location.getLatitude(), location.getLongitude());
                    googleMap.moveCamera(CameraUpdateFactory.newLatLngZoom(pos, 16f));
                }
            });
        } catch (SecurityException ignored) {
        }
    }

    // ============ GPS Tracking ============

    private void startLocationUpdates() {
        if (!hasLocationPermission())
            return;
        try {
            LocationRequest request = new LocationRequest.Builder(
                    Priority.PRIORITY_HIGH_ACCURACY, 3000)
                    .setMinUpdateDistanceMeters(5f)
                    .setMinUpdateIntervalMillis(2000)
                    .build();
            fusedLocationClient.requestLocationUpdates(request, locationCallback,
                    Looper.getMainLooper());
        } catch (SecurityException e) {
            Log.e(TAG, "Location permission lost", e);
        }
    }

    private void stopLocationUpdates() {
        fusedLocationClient.removeLocationUpdates(locationCallback);
    }

    private void drawRoute() {
        if (googleMap == null || routePoints.size() < 2)
            return;
        googleMap.clear();
        polylineOptions = new PolylineOptions()
                .addAll(routePoints)
                .width(8f)
                .color(activityType == TYPE_RUN ? Color.parseColor("#4CAF50")
                        : Color.parseColor("#FF9800"))
                .geodesic(true);
        googleMap.addPolyline(polylineOptions);
    }

    private void fitRouteOnMap() {
        if (googleMap == null || routePoints.size() < 2)
            return;
        LatLngBounds.Builder builder = new LatLngBounds.Builder();
        for (LatLng pt : routePoints)
            builder.include(pt);
        try {
            googleMap.animateCamera(CameraUpdateFactory.newLatLngBounds(builder.build(), 80));
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
        if (googleMap != null)
            googleMap.clear();

        sessionStartTime = System.currentTimeMillis();
        timerStartMs = SystemClock.elapsedRealtime();
        timerState = STATE_RUNNING;
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

        startLocationUpdates();
        timerHandler.post(timerRunnable);
        updateUI();
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

        timerHandler.postDelayed(this::resetSession, 4000);
    }

    private void resetSession() {
        elapsedMs = 0;
        sessionSteps = 0;
        gpsDistanceMetres = 0f;
        lastLocation = null;
        routePoints.clear();
        if (googleMap != null)
            googleMap.clear();

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
        tvRunSteps.setText(String.valueOf(steps));
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
