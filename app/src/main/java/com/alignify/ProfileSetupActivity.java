package com.alignify;

import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.cardview.widget.CardView;
import androidx.core.content.ContextCompat;

import com.alignify.data.UserRepository;

/**
 * Profile setup screen with BMI calculator.
 * Supports both initial setup and edit mode.
 */
public class ProfileSetupActivity extends AppCompatActivity {

    private static final String PREFS_NAME = "AlignifyPrefs";
    private static final String KEY_PROFILE_COMPLETE = "profile_complete";
    private static final String KEY_USER_HEIGHT = "user_height";
    private static final String KEY_USER_WEIGHT = "user_weight";
    private static final String KEY_USER_AGE = "user_age";
    private static final String KEY_USER_GENDER = "user_gender";
    private static final String KEY_USER_ACTIVITY = "user_activity";
    private static final String KEY_USER_BMI = "user_bmi";
    private static final String KEY_USER_BMI_CATEGORY = "user_bmi_category";

    private EditText heightInput;
    private EditText weightInput;
    private EditText ageInput;
    private Spinner genderSpinner;
    private Spinner activitySpinner;

    private CardView bmiCard;
    private TextView bmiValue;
    private TextView bmiCategory;

    private CardView summaryCard;
    private TextView summaryText;

    private Button btnGenerate;
    private Button btnContinue;
    private TextView pageTitle;
    private ImageView btnBack;

    private boolean isEditMode = false;

    // Spinner arrays for finding indices
    private String[] genders = { "Select Gender", "Male", "Female", "Other", "Prefer not to say" };
    private String[] activities = { "Select Activity Level", "Sedentary", "Lightly Active", "Moderately Active",
            "Very Active" };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_profile_setup);

        // Check if edit mode
        isEditMode = getIntent().getBooleanExtra("edit_mode", false);

        initViews();
        setupSpinners();
        setupListeners();

        // If edit mode, load existing data
        if (isEditMode) {
            loadExistingProfile();
            setupEditModeUI();
        }
    }

    private void initViews() {
        heightInput = findViewById(R.id.heightInput);
        weightInput = findViewById(R.id.weightInput);
        ageInput = findViewById(R.id.ageInput);
        genderSpinner = findViewById(R.id.genderSpinner);
        activitySpinner = findViewById(R.id.activitySpinner);

        bmiCard = findViewById(R.id.bmiCard);
        bmiValue = findViewById(R.id.bmiValue);
        bmiCategory = findViewById(R.id.bmiCategory);

        summaryCard = findViewById(R.id.summaryCard);
        summaryText = findViewById(R.id.summaryText);

        btnGenerate = findViewById(R.id.btnGenerate);
        btnContinue = findViewById(R.id.btnContinue);

        // Page title (may not exist in all layouts)
        pageTitle = findViewById(R.id.pageTitle);
        btnBack = findViewById(R.id.btnBack);
    }

    private void setupSpinners() {
        ArrayAdapter<String> genderAdapter = new ArrayAdapter<>(
                this, android.R.layout.simple_spinner_dropdown_item, genders);
        genderSpinner.setAdapter(genderAdapter);

        ArrayAdapter<String> activityAdapter = new ArrayAdapter<>(
                this, android.R.layout.simple_spinner_dropdown_item, activities);
        activitySpinner.setAdapter(activityAdapter);
    }

    private void setupListeners() {
        btnGenerate.setOnClickListener(v -> generateProfile());
        btnContinue.setOnClickListener(v -> navigateToDashboard());

        // Back button for edit mode
        if (btnBack != null) {
            btnBack.setOnClickListener(v -> onBackPressed());
        }
    }

    private void setupEditModeUI() {
        // Update title if exists
        if (pageTitle != null) {
            pageTitle.setText("Edit Profile");
        }

        // Show back button if exists
        if (btnBack != null) {
            btnBack.setVisibility(View.VISIBLE);
        }

        // Change button text
        btnGenerate.setText("Update Profile");
        btnContinue.setText("Save & Go Back");
    }

    private void loadExistingProfile() {
        SharedPreferences prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);

        // Load saved values
        float height = prefs.getFloat(KEY_USER_HEIGHT, 0f);
        float weight = prefs.getFloat(KEY_USER_WEIGHT, 0f);
        int age = prefs.getInt(KEY_USER_AGE, 0);
        String gender = prefs.getString(KEY_USER_GENDER, "");
        String activity = prefs.getString(KEY_USER_ACTIVITY, "");
        float bmi = prefs.getFloat(KEY_USER_BMI, 0f);
        String category = prefs.getString(KEY_USER_BMI_CATEGORY, "");

        // Populate fields
        if (height > 0) {
            heightInput.setText(String.valueOf((int) height));
        }
        if (weight > 0) {
            weightInput.setText(String.valueOf((int) weight));
        }
        if (age > 0) {
            ageInput.setText(String.valueOf(age));
        }

        // Set spinner selections
        setSpinnerSelection(genderSpinner, genders, gender);
        setSpinnerSelection(activitySpinner, activities, activity);

        // Show BMI if already calculated
        if (bmi > 0) {
            bmiValue.setText(String.format("%.1f", bmi));
            bmiCategory.setText(category);

            // Set category color
            int categoryColor = getCategoryColor(category);
            bmiCategory.setTextColor(ContextCompat.getColor(this, categoryColor));

            // Show existing data
            bmiCard.setVisibility(View.VISIBLE);

            String summary = String.format("%d cm, %d kg, %d years, %s, %s",
                    (int) height, (int) weight, age, gender, activity);
            summaryText.setText(summary);
            summaryCard.setVisibility(View.VISIBLE);
            btnContinue.setVisibility(View.VISIBLE);
        }
    }

    private void setSpinnerSelection(Spinner spinner, String[] options, String value) {
        for (int i = 0; i < options.length; i++) {
            if (options[i].equals(value)) {
                spinner.setSelection(i);
                return;
            }
        }
    }

    private int getCategoryColor(String category) {
        switch (category) {
            case "Underweight":
            case "Overweight":
                return R.color.warning_yellow;
            case "Normal":
                return R.color.correct_green;
            case "Obese":
                return R.color.error_red;
            default:
                return R.color.text_primary;
        }
    }

    private void generateProfile() {
        String heightStr = heightInput.getText().toString().trim();
        String weightStr = weightInput.getText().toString().trim();
        String ageStr = ageInput.getText().toString().trim();

        // Validation
        if (TextUtils.isEmpty(heightStr)) {
            heightInput.setError("Height is required");
            return;
        }
        if (TextUtils.isEmpty(weightStr)) {
            weightInput.setError("Weight is required");
            return;
        }
        if (TextUtils.isEmpty(ageStr)) {
            ageInput.setError("Age is required");
            return;
        }

        float height = Float.parseFloat(heightStr);
        float weight = Float.parseFloat(weightStr);
        int age = Integer.parseInt(ageStr);

        // Calculate BMI
        float heightM = height / 100f;
        float bmi = weight / (heightM * heightM);

        // Determine category
        String category;
        int categoryColor;

        if (bmi < 18.5f) {
            category = "Underweight";
            categoryColor = R.color.warning_yellow;
        } else if (bmi < 25f) {
            category = "Normal";
            categoryColor = R.color.correct_green;
        } else if (bmi < 30f) {
            category = "Overweight";
            categoryColor = R.color.warning_yellow;
        } else {
            category = "Obese";
            categoryColor = R.color.error_red;
        }

        // Update UI
        bmiValue.setText(String.format("%.1f", bmi));
        bmiCategory.setText(category);
        bmiCategory.setTextColor(ContextCompat.getColor(this, categoryColor));

        String gender = genderSpinner.getSelectedItemPosition() > 0
                ? genderSpinner.getSelectedItem().toString()
                : "Not specified";
        String activity = activitySpinner.getSelectedItemPosition() > 0
                ? activitySpinner.getSelectedItem().toString()
                : "Not specified";

        String summary = String.format("%s cm, %s kg, %d years, %s, %s",
                heightStr, weightStr, age, gender, activity);
        summaryText.setText(summary);

        // Show cards
        bmiCard.setVisibility(View.VISIBLE);
        summaryCard.setVisibility(View.VISIBLE);
        btnContinue.setVisibility(View.VISIBLE);

        // Save to preferences
        SharedPreferences prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
        String email = prefs.getString("user_email", "");
        String name = prefs.getString("user_name", "");

        prefs.edit()
                .putFloat(KEY_USER_HEIGHT, height)
                .putFloat(KEY_USER_WEIGHT, weight)
                .putInt(KEY_USER_AGE, age)
                .putString(KEY_USER_GENDER, gender)
                .putString(KEY_USER_ACTIVITY, activity)
                .putFloat(KEY_USER_BMI, bmi)
                .putString(KEY_USER_BMI_CATEGORY, category)
                .apply();

        // Save to Firestore
        UserRepository.getInstance().saveUserProfile(
                email, name, bmi, category, activity,
                (int) height, (int) weight, age, gender,
                new UserRepository.OnCompleteListener() {
                    @Override
                    public void onSuccess() {
                        String msg = isEditMode ? "Profile updated!" : "Profile saved to cloud!";
                        Toast.makeText(ProfileSetupActivity.this, msg, Toast.LENGTH_SHORT).show();
                    }

                    @Override
                    public void onError(String error) {
                        android.util.Log.e("ProfileSetup", "Cloud save failed: " + error);
                    }
                });

        String msg = isEditMode ? "Profile updated!" : "Profile generated!";
        Toast.makeText(this, msg, Toast.LENGTH_SHORT).show();
    }

    private void navigateToDashboard() {
        SharedPreferences prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
        prefs.edit().putBoolean(KEY_PROFILE_COMPLETE, true).apply();

        if (isEditMode) {
            // Just go back to dashboard
            finish();
        } else {
            // First time setup - navigate to dashboard
            Intent intent = new Intent(this, DashboardActivity.class);
            intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
            startActivity(intent);
            finish();
        }
    }

    @Override
    public void onBackPressed() {
        if (isEditMode) {
            finish();
        } else {
            super.onBackPressed();
        }
    }
}
