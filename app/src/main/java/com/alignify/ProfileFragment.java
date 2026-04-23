package com.alignify;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatDelegate;
import androidx.fragment.app.Fragment;

import com.alignify.data.FitnessDataManager;
import com.alignify.engine.CaloriesEngine;
import com.alignify.service.WaterReminderService;
import com.bumptech.glide.Glide;
import com.google.android.gms.auth.api.signin.GoogleSignIn;
import com.google.android.gms.auth.api.signin.GoogleSignInAccount;
import com.google.android.gms.auth.api.signin.GoogleSignInClient;
import com.google.android.gms.auth.api.signin.GoogleSignInOptions;
import com.google.android.material.switchmaterial.SwitchMaterial;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;

import java.text.NumberFormat;
import java.util.Locale;

/**
 * Profile/Settings fragment.
 * Converted from SettingsActivity for ViewPager2-based navigation.
 */
public class ProfileFragment extends Fragment {

    private static final String PREFS_NAME = "AlignifyPrefs";
    private static final String KEY_DISTANCE_UNIT = "distance_unit";
    private static final String KEY_WATER_REMINDERS = "water_reminders_enabled";

    // Views
    private TextView tvStepGoal;
    private TextView tvCalorieGoal;
    private TextView tvDistanceUnit;
    private TextView tvWaterGoal;
    private TextView tvUserName;
    private TextView tvUserEmail;
    private ImageView ivProfileImage;
    private SwitchMaterial switchWaterReminders;
    private TextView tvThemeMode;

    private boolean isUpdatingUI = false;

    // Data
    private FitnessDataManager fitnessDataManager;
    private int stepGoal = 10000;
    private int calorieGoal = 500;
    private int waterGoal = 8;
    private boolean useKilometers = true;

    private FirebaseAuth firebaseAuth;
    private GoogleSignInClient googleSignInClient;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
            @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.activity_settings_new, container, false);
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
        View btnBack = view.findViewById(R.id.btnBackSettings);
        if (btnBack != null) {
            btnBack.setVisibility(View.GONE);
        }

        fitnessDataManager = FitnessDataManager.getInstance(requireContext());

        firebaseAuth = FirebaseAuth.getInstance();
        GoogleSignInOptions gso = new GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
                .requestEmail()
                .build();
        googleSignInClient = GoogleSignIn.getClient(requireActivity(), gso);

        initViews(view);
        loadSettings();
        loadUserProfile();
        setupListeners(view);
    }

    @Override
    public void onResume() {
        super.onResume();
        if (!isAdded())
            return;
        loadUserProfile();
        loadSettings();
    }

    private void initViews(View view) {
        tvStepGoal = view.findViewById(R.id.tvStepGoal);
        tvCalorieGoal = view.findViewById(R.id.tvCalorieGoal);
        tvDistanceUnit = view.findViewById(R.id.tvDistanceUnit);
        tvWaterGoal = view.findViewById(R.id.tvWaterGoal);
        tvUserName = view.findViewById(R.id.tvUserName);
        tvUserEmail = view.findViewById(R.id.tvUserEmail);
        ivProfileImage = view.findViewById(R.id.ivProfileImage);
        switchWaterReminders = view.findViewById(R.id.switchWaterReminders);
        tvThemeMode = view.findViewById(R.id.tvThemeMode);

        TextView tvAppVersion = view.findViewById(R.id.tvAppVersion);
        if (tvAppVersion != null) {
            try {
                PackageInfo pInfo = requireContext().getPackageManager().getPackageInfo(
                        requireContext().getPackageName(), 0);
                tvAppVersion.setText("Alignify v" + pInfo.versionName);
            } catch (PackageManager.NameNotFoundException e) {
                tvAppVersion.setText("Alignify");
            }
        }
    }

    private void loadSettings() {
        if (!isAdded())
            return;
        stepGoal = fitnessDataManager.getStepGoal();
        calorieGoal = fitnessDataManager.getCaloriesGoal();
        waterGoal = fitnessDataManager.getWaterGoal();

        SharedPreferences prefs = requireContext().getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        useKilometers = prefs.getBoolean(KEY_DISTANCE_UNIT, true);

        isUpdatingUI = true;
        switchWaterReminders.setChecked(prefs.getBoolean(KEY_WATER_REMINDERS, true));
        isUpdatingUI = false;

        String themeMode = prefs.getString(AlignifyApp.KEY_THEME_MODE, "light");
        updateThemeModeDisplay(themeMode);

        updateUI();
    }

    private void loadUserProfile() {
        if (!isAdded())
            return;
        SharedPreferences prefs = requireContext().getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);

        String savedDisplayName = prefs.getString("display_name", null);
        String savedPhotoUri = prefs.getString("profile_photo_uri", null);

        if (savedDisplayName != null && !savedDisplayName.isEmpty()) {
            tvUserName.setText(savedDisplayName);
        } else {
            FirebaseUser user = firebaseAuth.getCurrentUser();
            if (user != null && user.getDisplayName() != null && !user.getDisplayName().isEmpty()) {
                tvUserName.setText(user.getDisplayName());
            } else {
                GoogleSignInAccount account = GoogleSignIn.getLastSignedInAccount(requireContext());
                if (account != null && account.getDisplayName() != null) {
                    tvUserName.setText(account.getDisplayName());
                } else {
                    tvUserName.setText("User");
                }
            }
        }

        FirebaseUser user = firebaseAuth.getCurrentUser();
        if (user != null && user.getEmail() != null) {
            tvUserEmail.setText(user.getEmail());
        } else {
            GoogleSignInAccount account = GoogleSignIn.getLastSignedInAccount(requireContext());
            if (account != null && account.getEmail() != null) {
                tvUserEmail.setText(account.getEmail());
            }
        }

        if (savedPhotoUri != null) {
            Glide.with(this)
                    .load(Uri.parse(savedPhotoUri))
                    .placeholder(R.drawable.default_profile)
                    .error(R.drawable.default_profile)
                    .circleCrop()
                    .into(ivProfileImage);
        } else if (user != null && user.getPhotoUrl() != null) {
            Glide.with(this)
                    .load(user.getPhotoUrl())
                    .placeholder(R.drawable.default_profile)
                    .error(R.drawable.default_profile)
                    .circleCrop()
                    .into(ivProfileImage);
        } else {
            GoogleSignInAccount account = GoogleSignIn.getLastSignedInAccount(requireContext());
            if (account != null && account.getPhotoUrl() != null) {
                Glide.with(this)
                        .load(account.getPhotoUrl())
                        .placeholder(R.drawable.default_profile)
                        .error(R.drawable.default_profile)
                        .circleCrop()
                        .into(ivProfileImage);
            }
        }
    }

    private void updateUI() {
        NumberFormat nf = NumberFormat.getNumberInstance(Locale.US);
        tvStepGoal.setText(nf.format(stepGoal) + " steps");
        tvCalorieGoal.setText(nf.format(calorieGoal) + " kcal");
        tvDistanceUnit.setText(useKilometers ? "Kilometers (km)" : "Miles (mi)");
        tvWaterGoal.setText(waterGoal + " cups");
    }

    private void setupListeners(View view) {
        view.findViewById(R.id.cardProfile).setOnClickListener(v -> {
            Intent intent = new Intent(requireContext(), EditProfileActivity.class);
            startActivity(intent);
        });

        view.findViewById(R.id.settingStepGoal).setOnClickListener(v -> showStepGoalDialog());
        view.findViewById(R.id.settingCalorieGoal).setOnClickListener(v -> showCalorieGoalDialog());
        view.findViewById(R.id.settingWaterGoal).setOnClickListener(v -> showWaterGoalDialog());
        view.findViewById(R.id.settingDistanceUnit).setOnClickListener(v -> showDistanceUnitDialog());
        view.findViewById(R.id.settingEditProfile).setOnClickListener(v -> {
            Intent intent = new Intent(requireContext(), EditProfileActivity.class);
            startActivity(intent);
        });
        view.findViewById(R.id.settingLogout).setOnClickListener(v -> showLogoutConfirmation());

        switchWaterReminders.setOnCheckedChangeListener((buttonView, isChecked) -> {
            if (isUpdatingUI)
                return;
            if (!isAdded())
                return;

            SharedPreferences prefs = requireContext().getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
            prefs.edit().putBoolean(KEY_WATER_REMINDERS, isChecked).apply();

            if (isChecked) {
                WaterReminderService.scheduleReminders(requireContext());
                Toast.makeText(requireContext(), "Water reminders enabled", Toast.LENGTH_SHORT).show();
            } else {
                WaterReminderService.cancelReminders(requireContext());
                Toast.makeText(requireContext(), "Water reminders disabled", Toast.LENGTH_SHORT).show();
            }
        });

        View settingTheme = view.findViewById(R.id.settingDarkMode);
        if (settingTheme != null) {
            settingTheme.setOnClickListener(v -> showThemeModeDialog());
        }
    }

    private void updateThemeModeDisplay(String themeMode) {
        if (tvThemeMode == null)
            return;
        switch (themeMode) {
            case "dark":
                tvThemeMode.setText("Dark");
                break;
            case "light":
                tvThemeMode.setText("Light");
                break;
            default:
                tvThemeMode.setText("System Default");
                break;
        }
    }

    private void showThemeModeDialog() {
        if (!isAdded())
            return;
        final String[] options = { "Light", "Dark", "System Default" };
        SharedPreferences prefs = requireContext().getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        String current = prefs.getString(AlignifyApp.KEY_THEME_MODE, "light");
        int checkedItem = current.equals("dark") ? 1 : current.equals("system") ? 2 : 0;

        new AlertDialog.Builder(requireContext())
                .setTitle("Theme")
                .setSingleChoiceItems(options, checkedItem, (dialog, which) -> {
                    String selected;
                    int nightMode;
                    switch (which) {
                        case 1:
                            selected = "dark";
                            nightMode = AppCompatDelegate.MODE_NIGHT_YES;
                            break;
                        case 2:
                            selected = "system";
                            nightMode = AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM;
                            break;
                        default:
                            selected = "light";
                            nightMode = AppCompatDelegate.MODE_NIGHT_NO;
                            break;
                    }
                    prefs.edit()
                            .putString(AlignifyApp.KEY_THEME_MODE, selected)
                            .putBoolean(AlignifyApp.KEY_DARK_MODE, "dark".equals(selected))
                            .apply();
                    updateThemeModeDisplay(selected);
                    dialog.dismiss();
                    AppCompatDelegate.setDefaultNightMode(nightMode);
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void showStepGoalDialog() {
        if (!isAdded())
            return;
        android.widget.EditText input = new android.widget.EditText(requireContext());
        input.setInputType(android.text.InputType.TYPE_CLASS_NUMBER);
        input.setHint("Enter daily step goal");
        input.setText(String.valueOf(stepGoal));
        input.setSelection(input.getText().length());

        android.widget.FrameLayout container = new android.widget.FrameLayout(requireContext());
        android.widget.FrameLayout.LayoutParams params = new android.widget.FrameLayout.LayoutParams(
                android.widget.FrameLayout.LayoutParams.MATCH_PARENT,
                android.widget.FrameLayout.LayoutParams.WRAP_CONTENT);
        params.leftMargin = dpToPx(20);
        params.rightMargin = dpToPx(20);
        input.setLayoutParams(params);
        container.addView(input);

        new AlertDialog.Builder(requireContext())
                .setTitle("Daily Step Goal")
                .setView(container)
                .setPositiveButton("Save", (dialog, which) -> {
                    String value = input.getText().toString().trim();
                    if (!value.isEmpty()) {
                        try {
                            int newGoal = Integer.parseInt(value);
                            if (newGoal > 0 && newGoal <= 100000) {
                                stepGoal = newGoal;
                                saveSettings();
                                updateUI();
                            } else {
                                Toast.makeText(requireContext(), "Enter a value between 1 and 100,000",
                                        Toast.LENGTH_SHORT).show();
                            }
                        } catch (NumberFormatException e) {
                            Toast.makeText(requireContext(), "Invalid number", Toast.LENGTH_SHORT).show();
                        }
                    }
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void showCalorieGoalDialog() {
        if (!isAdded())
            return;
        android.widget.EditText input = new android.widget.EditText(requireContext());
        input.setInputType(android.text.InputType.TYPE_CLASS_NUMBER);
        input.setHint("Enter daily calorie goal");
        input.setText(String.valueOf(calorieGoal));
        input.setSelection(input.getText().length());

        android.widget.FrameLayout container = new android.widget.FrameLayout(requireContext());
        android.widget.FrameLayout.LayoutParams params = new android.widget.FrameLayout.LayoutParams(
                android.widget.FrameLayout.LayoutParams.MATCH_PARENT,
                android.widget.FrameLayout.LayoutParams.WRAP_CONTENT);
        params.leftMargin = dpToPx(20);
        params.rightMargin = dpToPx(20);
        input.setLayoutParams(params);
        container.addView(input);

        new AlertDialog.Builder(requireContext())
                .setTitle("Daily Calorie Goal")
                .setView(container)
                .setPositiveButton("Save", (dialog, which) -> {
                    String value = input.getText().toString().trim();
                    if (!value.isEmpty()) {
                        try {
                            int newGoal = Integer.parseInt(value);
                            if (newGoal > 0 && newGoal <= 10000) {
                                calorieGoal = newGoal;
                                saveSettings();
                                updateUI();
                            } else {
                                Toast.makeText(requireContext(), "Enter a value between 1 and 10,000",
                                        Toast.LENGTH_SHORT).show();
                            }
                        } catch (NumberFormatException e) {
                            Toast.makeText(requireContext(), "Invalid number", Toast.LENGTH_SHORT).show();
                        }
                    }
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void showWaterGoalDialog() {
        if (!isAdded())
            return;
        android.widget.EditText input = new android.widget.EditText(requireContext());
        input.setInputType(android.text.InputType.TYPE_CLASS_NUMBER);
        input.setHint("Enter daily water cups goal");
        input.setText(String.valueOf(waterGoal));
        input.setSelection(input.getText().length());

        android.widget.FrameLayout container = new android.widget.FrameLayout(requireContext());
        android.widget.FrameLayout.LayoutParams params = new android.widget.FrameLayout.LayoutParams(
                android.widget.FrameLayout.LayoutParams.MATCH_PARENT,
                android.widget.FrameLayout.LayoutParams.WRAP_CONTENT);
        params.leftMargin = dpToPx(20);
        params.rightMargin = dpToPx(20);
        input.setLayoutParams(params);
        container.addView(input);

        new AlertDialog.Builder(requireContext())
                .setTitle("Daily Water Goal")
                .setView(container)
                .setPositiveButton("Save", (dialog, which) -> {
                    String value = input.getText().toString().trim();
                    if (!value.isEmpty()) {
                        try {
                            int newGoal = Integer.parseInt(value);
                            if (newGoal > 0 && newGoal <= 20) {
                                waterGoal = newGoal;
                                saveSettings();
                                updateUI();
                            } else {
                                Toast.makeText(requireContext(), "Enter a value between 1 and 20",
                                        Toast.LENGTH_SHORT).show();
                            }
                        } catch (NumberFormatException e) {
                            Toast.makeText(requireContext(), "Invalid number", Toast.LENGTH_SHORT).show();
                        }
                    }
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void showDistanceUnitDialog() {
        if (!isAdded())
            return;
        final String[] units = { "Kilometers (km)", "Miles (mi)" };

        new AlertDialog.Builder(requireContext())
                .setTitle("Distance Unit")
                .setItems(units, (dialog, which) -> {
                    useKilometers = (which == 0);
                    saveSettings();
                    updateUI();
                })
                .show();
    }

    private void saveSettings() {
        if (!isAdded())
            return;
        fitnessDataManager.setStepGoal(stepGoal);
        fitnessDataManager.setCaloriesGoal(calorieGoal);
        fitnessDataManager.setWaterGoal(waterGoal);

        SharedPreferences prefs = requireContext().getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        prefs.edit()
                .putBoolean(KEY_DISTANCE_UNIT, useKilometers)
                .apply();

        CaloriesEngine.getInstance(requireContext()).loadUserProfile();
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
            if (!isAdded())
                return;
            SharedPreferences prefs = requireContext().getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
            prefs.edit().clear().apply();

            Toast.makeText(requireContext(), "Logged out successfully", Toast.LENGTH_SHORT).show();

            Intent intent = new Intent(requireContext(), LoginActivity.class);
            intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
            startActivity(intent);
            requireActivity().finish();
        });
    }

    private int dpToPx(int dp) {
        return (int) (dp * getResources().getDisplayMetrics().density);
    }
}
