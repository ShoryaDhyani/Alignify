package com.alignify;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.drawerlayout.widget.DrawerLayout;
import androidx.fragment.app.Fragment;
import androidx.localbroadcastmanager.content.LocalBroadcastManager;

import com.alignify.chatbot.ChatbotActivity;
import com.alignify.data.DailyActivity;
import com.alignify.data.FitnessDataManager;
import com.alignify.data.UserRepository;
import com.alignify.ml.ModelManager;
import com.alignify.service.StepCounterService;
import com.alignify.util.NavigationHelper;
import com.alignify.util.ProfileImageHelper;
import com.alignify.util.StepCounterHelper;
import com.bumptech.glide.Glide;
import com.google.android.gms.auth.api.signin.GoogleSignIn;
import com.google.android.gms.auth.api.signin.GoogleSignInAccount;
import com.google.android.gms.auth.api.signin.GoogleSignInClient;
import com.google.android.gms.auth.api.signin.GoogleSignInOptions;
import com.google.android.material.floatingactionbutton.FloatingActionButton;
import com.google.android.material.navigation.NavigationView;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;

/**
 * Dashboard/Home fragment showing user profile and system status.
 * Converted from DashboardActivity for ViewPager2-based navigation.
 */
public class DashboardFragment extends Fragment {

    private static final String PREFS_NAME = "AlignifyPrefs";
    private static final String KEY_LOGGED_IN = "logged_in";
    private static final String KEY_USER_EMAIL = "user_email";
    private static final String KEY_USER_NAME = "user_name";
    private static final String KEY_USER_BMI = "user_bmi";
    private static final String KEY_USER_BMI_CATEGORY = "user_bmi_category";
    private static final String KEY_USER_ACTIVITY = "user_activity";
    private static final String KEY_PROFILE_IMAGE_URL = "profile_image_url";

    private TextView userName;
    private TextView bmiValue;
    private TextView fitnessLevel;
    private ImageView ivProfileImage;
    private View btnStartCorrection;

    // Navigation drawer
    private DrawerLayout drawerLayout;
    private NavigationView navigationView;

    private FirebaseAuth firebaseAuth;
    private GoogleSignInClient googleSignInClient;

    // Step counter
    private static final String TAG = "DashboardFragment";
    private FitnessDataManager fitnessDataManager;
    private TextView stepsValue;
    private TextView stepGoalText;
    private ImageButton btnResetSteps;
    private ProgressBar stepProgressBar;
    private BroadcastReceiver stepUpdateReceiver;
    private TextView tvCalories;
    private TextView tvDistance;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
            @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.activity_dashboard_new, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        // Hide the bottom nav bar from the inflated layout (HomeActivity provides the
        // nav)
        View bottomNav = view.findViewById(R.id.bottomNavContainer);
        if (bottomNav != null) {
            bottomNav.setVisibility(View.GONE);
        }

        // Initialize Firebase Auth
        firebaseAuth = FirebaseAuth.getInstance();

        // Initialize FitnessDataManager
        fitnessDataManager = FitnessDataManager.getInstance(requireContext());

        // Initialize Google Sign-In client
        GoogleSignInOptions gso = new GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
                .requestEmail()
                .build();
        googleSignInClient = GoogleSignIn.getClient(requireActivity(), gso);

        initViews(view);
        loadUserProfile();
        setupListeners(view);
        setupStepCounter(view);

        // Load data from Firestore
        fitnessDataManager.loadFromFirestore(null);

        // Check for model updates
        checkForModelUpdates();
    }

    @Override
    public void onResume() {
        super.onResume();
        if (!isAdded())
            return;
        loadUserProfile();
        updateStepCountDisplay();
        registerStepUpdateReceiver();
        loadTodayActivityFromFirestore();
    }

    @Override
    public void onPause() {
        super.onPause();
        if (stepUpdateReceiver != null) {
            LocalBroadcastManager.getInstance(requireContext()).unregisterReceiver(stepUpdateReceiver);
        }
        syncStepsToManager();
    }

    private void loadTodayActivityFromFirestore() {
        int steps = fitnessDataManager.getStepsToday();
        updateStepUI(steps);

        fitnessDataManager.loadFromFirestore(() -> {
            if (isAdded()) {
                requireActivity().runOnUiThread(() -> {
                    int mergedSteps = fitnessDataManager.getStepsToday();
                    updateStepUI(mergedSteps);
                    Log.d(TAG, "Loaded from FitnessDataManager: steps=" + mergedSteps);
                });
            }
        });
    }

    private void syncStepsToManager() {
        if (!isAdded())
            return;
        if (!StepCounterHelper.isStepTrackingEnabled(requireContext()))
            return;

        int steps = StepCounterHelper.getStepsToday(requireContext());
        if (steps > 0) {
            fitnessDataManager.setStepsToday(steps);
        }
    }

    private void initViews(View view) {
        userName = view.findViewById(R.id.userName);
        bmiValue = view.findViewById(R.id.bmiValue);
        fitnessLevel = view.findViewById(R.id.fitnessLevel);
        ivProfileImage = view.findViewById(R.id.ivProfileImage);
        btnStartCorrection = view.findViewById(R.id.btnStartCorrection);

        // Navigation drawer
        drawerLayout = view.findViewById(R.id.drawerLayout);
        navigationView = view.findViewById(R.id.navigationView);

        // Setup hamburger menu
        ImageView btnMenu = view.findViewById(R.id.btnMenu);
        if (btnMenu != null) {
            btnMenu.setOnClickListener(v -> {
                if (drawerLayout != null) {
                    drawerLayout.openDrawer(navigationView);
                }
            });
        }

        // Setup navigation item selection
        if (navigationView != null) {
            navigationView.setNavigationItemSelectedListener(this::onNavigationItemSelected);
            populateNavHeader();
        }
    }

    private void populateNavHeader() {
        if (navigationView == null)
            return;
        View headerView = navigationView.getHeaderView(0);
        if (headerView == null)
            return;

        ImageView navAvatar = headerView.findViewById(R.id.navAvatar);
        TextView navUserName = headerView.findViewById(R.id.navUserName);
        TextView navUserEmail = headerView.findViewById(R.id.navUserEmail);

        FirebaseUser firebaseUser = firebaseAuth.getCurrentUser();
        GoogleSignInAccount googleAccount = GoogleSignIn.getLastSignedInAccount(requireContext());
        SharedPreferences prefs = requireContext().getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        String storedName = prefs.getString(KEY_USER_NAME, "");
        String storedEmail = prefs.getString(KEY_USER_EMAIL, "");
        String cachedProfileImageUrl = prefs.getString(KEY_PROFILE_IMAGE_URL, null);

        String displayName = "";
        if (firebaseUser != null && firebaseUser.getDisplayName() != null && !firebaseUser.getDisplayName().isEmpty()) {
            displayName = firebaseUser.getDisplayName();
        } else if (googleAccount != null && googleAccount.getDisplayName() != null) {
            displayName = googleAccount.getDisplayName();
        } else if (!storedName.isEmpty()) {
            displayName = storedName;
        }
        navUserName.setText(displayName.isEmpty() ? "User" : displayName);

        String email = "";
        if (firebaseUser != null && firebaseUser.getEmail() != null) {
            email = firebaseUser.getEmail();
        } else if (!storedEmail.isEmpty()) {
            email = storedEmail;
        }
        navUserEmail.setText(email);

        if (ProfileImageHelper.hasProfileImage(requireContext())) {
            String localPath = ProfileImageHelper.getProfileImagePath(requireContext());
            Glide.with(this)
                    .load(new java.io.File(localPath))
                    .placeholder(R.drawable.ic_profile)
                    .error(R.drawable.ic_profile)
                    .circleCrop()
                    .into(navAvatar);
        } else if (cachedProfileImageUrl != null && !cachedProfileImageUrl.isEmpty()) {
            Glide.with(this)
                    .load(cachedProfileImageUrl)
                    .placeholder(R.drawable.ic_profile)
                    .error(R.drawable.ic_profile)
                    .circleCrop()
                    .into(navAvatar);
        } else {
            android.net.Uri photoUrl = null;
            if (googleAccount != null && googleAccount.getPhotoUrl() != null) {
                photoUrl = googleAccount.getPhotoUrl();
            } else if (firebaseUser != null && firebaseUser.getPhotoUrl() != null) {
                photoUrl = firebaseUser.getPhotoUrl();
            }
            if (photoUrl != null) {
                Glide.with(this)
                        .load(photoUrl)
                        .placeholder(R.drawable.ic_profile)
                        .error(R.drawable.ic_profile)
                        .circleCrop()
                        .into(navAvatar);
            } else {
                navAvatar.setImageResource(R.drawable.ic_profile);
            }
        }
    }

    private void loadUserProfile() {
        if (!isAdded())
            return;
        SharedPreferences prefs = requireContext().getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);

        String email = prefs.getString(KEY_USER_EMAIL, "User");
        String googleName = prefs.getString(KEY_USER_NAME, "");
        float bmi = prefs.getFloat(KEY_USER_BMI, 0f);
        String bmiCategory = prefs.getString(KEY_USER_BMI_CATEGORY, "Normal");
        String activity = prefs.getString(KEY_USER_ACTIVITY, "Active");

        String name;
        if (!googleName.isEmpty()) {
            name = googleName;
        } else if (email.contains("@")) {
            name = email.split("@")[0];
            if (!name.isEmpty()) {
                name = name.substring(0, 1).toUpperCase() + name.substring(1);
            }
        } else {
            name = email;
        }
        if (userName != null)
            userName.setText(name);

        if (bmi > 0) {
            if (bmiValue != null)
                bmiValue.setText(String.format("%.1f", bmi));
        } else {
            if (bmiValue != null)
                bmiValue.setText("--");
        }

        if (fitnessLevel != null)
            fitnessLevel.setText("Fitness Level: " + activity);
        loadProfileImage();
    }

    private void loadProfileImage() {
        if (!isAdded())
            return;
        if (ProfileImageHelper.hasProfileImage(requireContext()) && ivProfileImage != null) {
            String localPath = ProfileImageHelper.getProfileImagePath(requireContext());
            Glide.with(this)
                    .load(new java.io.File(localPath))
                    .placeholder(R.drawable.ic_profile)
                    .error(R.drawable.ic_profile)
                    .circleCrop()
                    .into(ivProfileImage);
            return;
        }

        SharedPreferences prefs = requireContext().getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        String cachedImageUrl = prefs.getString(KEY_PROFILE_IMAGE_URL, null);

        if (cachedImageUrl != null && !cachedImageUrl.isEmpty() && ivProfileImage != null) {
            Glide.with(this)
                    .load(cachedImageUrl)
                    .placeholder(R.drawable.ic_profile)
                    .error(R.drawable.ic_profile)
                    .circleCrop()
                    .into(ivProfileImage);
        } else {
            loadDefaultAccountPhoto();
        }
    }

    private void loadDefaultAccountPhoto() {
        if (!isAdded())
            return;
        GoogleSignInAccount googleAccount = GoogleSignIn.getLastSignedInAccount(requireContext());
        FirebaseUser firebaseUser = firebaseAuth.getCurrentUser();

        android.net.Uri photoUrl = null;
        if (googleAccount != null && googleAccount.getPhotoUrl() != null) {
            photoUrl = googleAccount.getPhotoUrl();
        } else if (firebaseUser != null && firebaseUser.getPhotoUrl() != null) {
            photoUrl = firebaseUser.getPhotoUrl();
        }

        if (photoUrl != null && ivProfileImage != null) {
            Glide.with(this)
                    .load(photoUrl)
                    .placeholder(R.drawable.ic_profile)
                    .error(R.drawable.ic_profile)
                    .circleCrop()
                    .into(ivProfileImage);
        } else if (ivProfileImage != null) {
            ivProfileImage.setImageResource(R.drawable.ic_profile);
        }
    }

    private void setupListeners(View view) {
        if (btnStartCorrection != null) {
            btnStartCorrection.setOnClickListener(v -> navigateToTab(NavigationHelper.NAV_EXERCISES));
        }

        View btnStartRunning = view.findViewById(R.id.btnStartRunning);
        if (btnStartRunning != null) {
            btnStartRunning.setOnClickListener(v -> navigateToTab(NavigationHelper.NAV_RUN));
        }

        View btnTalkCoach = view.findViewById(R.id.btnTalkCoach);
        if (btnTalkCoach != null) {
            btnTalkCoach.setOnClickListener(v -> startActivity(new Intent(requireContext(), ChatbotActivity.class)));
        }

        FloatingActionButton fabChatbot = view.findViewById(R.id.fabChatbot);
        if (fabChatbot != null) {
            fabChatbot.setOnClickListener(v -> startActivity(new Intent(requireContext(), ChatbotActivity.class)));
        }
    }

    private boolean onNavigationItemSelected(MenuItem item) {
        if (drawerLayout != null)
            drawerLayout.closeDrawers();
        int id = item.getItemId();

        if (id == R.id.nav_dashboard) {
            return true;
        } else if (id == R.id.nav_steps) {
            startActivity(new Intent(requireContext(), StepActivity.class));
            return true;
        } else if (id == R.id.nav_exercises) {
            navigateToTab(NavigationHelper.NAV_EXERCISES);
            return true;
        } else if (id == R.id.nav_history) {
            navigateToTab(NavigationHelper.NAV_ANALYTICS);
            return true;
        } else if (id == R.id.nav_settings) {
            navigateToTab(NavigationHelper.NAV_PROFILE);
            return true;
        } else if (id == R.id.nav_profile) {
            Intent intent = new Intent(requireContext(), ProfileSetupActivity.class);
            intent.putExtra("edit_mode", true);
            startActivity(intent);
            return true;
        } else if (id == R.id.nav_logout) {
            showLogoutConfirmation();
            return true;
        }
        return false;
    }

    private void navigateToTab(int tabIndex) {
        if (getActivity() instanceof HomeActivity) {
            ((HomeActivity) getActivity()).navigateToTab(tabIndex);
        }
    }

    private void showLogoutConfirmation() {
        if (!isAdded())
            return;
        new AlertDialog.Builder(requireContext())
                .setTitle("Logout")
                .setMessage("Are you sure you want to logout?")
                .setPositiveButton("Logout", (dialog, which) -> performLogout())
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void performLogout() {
        if (!isAdded())
            return;
        if (firebaseAuth != null) {
            firebaseAuth.signOut();
        }

        googleSignInClient.signOut().addOnCompleteListener(requireActivity(), task -> {
            SharedPreferences prefs = requireContext().getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
            prefs.edit().clear().apply();

            Toast.makeText(requireContext(), "Logged out successfully", Toast.LENGTH_SHORT).show();

            Intent intent = new Intent(requireContext(), LoginActivity.class);
            intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
            startActivity(intent);
            requireActivity().finish();
        });
    }

    // ==================== Step Counter ====================

    private void setupStepCounter(View view) {
        stepsValue = view.findViewById(R.id.stepsValue);
        stepGoalText = view.findViewById(R.id.stepGoalText);
        btnResetSteps = view.findViewById(R.id.btnResetSteps);
        stepProgressBar = view.findViewById(R.id.stepProgressBar);
        tvCalories = view.findViewById(R.id.tvCalories);
        tvDistance = view.findViewById(R.id.tvDistance);

        fitnessDataManager.getCaloriesLiveData().observe(getViewLifecycleOwner(), calories -> {
            if (tvCalories != null) {
                tvCalories.setText(String.valueOf(calories));
            }
        });
        fitnessDataManager.getDistanceLiveData().observe(getViewLifecycleOwner(), distance -> {
            if (tvDistance != null) {
                tvDistance.setText(String.format(java.util.Locale.US, "%.1f km", distance));
            }
        });

        if (!StepCounterHelper.isStepCounterAvailable(requireContext())) {
            Log.w(TAG, "Step counter sensor not available");
            if (stepsValue != null)
                stepsValue.setText("N/A");
            if (stepGoalText != null)
                stepGoalText.setText("Step counter not available");
            if (btnResetSteps != null) {
                btnResetSteps.setEnabled(false);
                btnResetSteps.setAlpha(0.3f);
            }
            return;
        }

        if (btnResetSteps != null) {
            btnResetSteps.setOnClickListener(v -> showResetStepsConfirmation());
        }

        startStepTracking();
    }

    private void showResetStepsConfirmation() {
        if (!isAdded())
            return;
        new AlertDialog.Builder(requireContext())
                .setTitle("Reset Today's Steps")
                .setMessage("This will reset your step count to zero. This action cannot be undone.")
                .setPositiveButton("Reset", (dialog, which) -> resetSteps())
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void resetSteps() {
        if (!isAdded())
            return;
        StepCounterService.resetStepCounter(requireContext());

        UserRepository.getInstance().resetTodaySteps(new UserRepository.OnCompleteListener() {
            @Override
            public void onSuccess() {
                if (isAdded()) {
                    requireActivity().runOnUiThread(() -> {
                        updateStepCountDisplay();
                        Toast.makeText(requireContext(), "Steps reset successfully", Toast.LENGTH_SHORT).show();
                    });
                }
            }

            @Override
            public void onError(String error) {
                if (isAdded()) {
                    requireActivity().runOnUiThread(() -> {
                        Toast.makeText(requireContext(), "Failed to sync reset: " + error, Toast.LENGTH_SHORT).show();
                    });
                }
            }
        });

        if (stepsValue != null)
            stepsValue.setText("0");
        if (stepProgressBar != null)
            stepProgressBar.setProgress(0);
    }

    private void startStepTracking() {
        if (!isAdded())
            return;
        if (!StepCounterHelper.hasAllPermissions(requireContext())) {
            StepCounterHelper.requestPermissions(requireActivity());
        } else {
            StepCounterHelper.startStepTracking(requireContext(), true);
            updateStepCountDisplay();
        }
    }

    private void registerStepUpdateReceiver() {
        if (!isAdded())
            return;
        stepUpdateReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                if (StepCounterService.ACTION_STEP_UPDATE.equals(intent.getAction())) {
                    int steps = intent.getIntExtra(StepCounterService.EXTRA_STEPS_TODAY, 0);
                    updateStepUI(steps);
                }
            }
        };
        IntentFilter filter = new IntentFilter(StepCounterService.ACTION_STEP_UPDATE);
        LocalBroadcastManager.getInstance(requireContext()).registerReceiver(stepUpdateReceiver, filter);
    }

    private void updateStepCountDisplay() {
        if (!isAdded())
            return;
        if (StepCounterHelper.isStepTrackingEnabled(requireContext())) {
            int steps = StepCounterHelper.getStepsToday(requireContext());
            updateStepUI(steps);
            fitnessDataManager.setStepsToday(steps);
        }
    }

    private void updateStepUI(int steps) {
        if (!isAdded())
            return;
        int stepGoal = fitnessDataManager.getStepGoal();
        if (stepsValue != null)
            stepsValue.setText(String.valueOf(steps));
        if (stepGoalText != null)
            stepGoalText.setText(steps + " / " + stepGoal + " steps");
        if (stepProgressBar != null) {
            stepProgressBar.setMax(stepGoal);
            stepProgressBar.setProgress(Math.min(steps, stepGoal));
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions,
            @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);

        if (requestCode == StepCounterHelper.PERMISSION_REQUEST_CODE) {
            boolean allGranted = true;
            for (int result : grantResults) {
                if (result != PackageManager.PERMISSION_GRANTED) {
                    allGranted = false;
                    break;
                }
            }

            if (allGranted) {
                Log.d(TAG, "All step counter permissions granted");
                StepCounterHelper.startStepTracking(requireContext(), true);
                updateStepCountDisplay();
            } else {
                Log.w(TAG, "Step counter permissions denied");
                Toast.makeText(requireContext(), "Step tracking requires permissions", Toast.LENGTH_SHORT).show();
            }
        }
    }

    private void checkForModelUpdates() {
        if (!isAdded())
            return;
        ModelManager.getInstance(requireContext()).checkForUpdates(requireActivity(),
                new ModelManager.UpdateCheckCallback() {
                    @Override
                    public void onUpdatesAvailable(java.util.List<ModelManager.ModelInfo> updates) {
                        Log.d(TAG, updates.size() + " model updates available");
                    }

                    @Override
                    public void onNoUpdates() {
                        Log.d(TAG, "All models are up to date");
                    }

                    @Override
                    public void onError(String error) {
                        Log.e(TAG, "Error checking for model updates: " + error);
                    }
                });
    }
}
