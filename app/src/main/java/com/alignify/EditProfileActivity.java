package com.alignify;

import android.Manifest;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Bundle;
import android.provider.MediaStore;
import android.text.Editable;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;

import com.alignify.data.UserRepository;
import com.bumptech.glide.Glide;
import com.google.android.gms.auth.api.signin.GoogleSignIn;
import com.google.android.gms.auth.api.signin.GoogleSignInAccount;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.auth.UserProfileChangeRequest;

/**
 * Enhanced profile editing screen with photo upload,
 * body measurements, and health insights.
 */
public class EditProfileActivity extends AppCompatActivity {

    private static final String PREFS_NAME = "AlignifyPrefs";
    private static final String KEY_USER_HEIGHT = "user_height";
    private static final String KEY_USER_WEIGHT = "user_weight";
    private static final String KEY_USER_AGE = "user_age";
    private static final String KEY_USER_GENDER = "user_gender";
    private static final String KEY_USER_ACTIVITY = "user_activity";
    private static final String KEY_USER_BMI = "user_bmi";
    private static final String KEY_USER_BMI_CATEGORY = "user_bmi_category";
    private static final String KEY_TARGET_WEIGHT = "target_weight";
    private static final String KEY_FITNESS_GOAL = "fitness_goal";
    private static final String KEY_DISPLAY_NAME = "display_name";
    private static final String KEY_PROFILE_PHOTO_URI = "profile_photo_uri";

    // Views
    private ImageView ivProfilePhoto;
    private EditText etDisplayName;
    private EditText etAge;
    private TextView tvGender;
    private EditText etHeight;
    private EditText etWeight;
    private EditText etTargetWeight;
    private TextView tvActivityLevel;
    private TextView tvFitnessGoal;
    private TextView tvBmiValue;
    private TextView tvBmiCategory;
    private TextView tvBmiAdvice;
    private TextView tvCurrentWeightValue;
    private TextView tvTargetWeightValue;
    private TextView tvWeightDifference;

    // Data
    private String selectedGender = "";
    private String selectedActivityLevel = "";
    private String selectedFitnessGoal = "";
    private Uri selectedPhotoUri = null;

    private FirebaseAuth firebaseAuth;

    // Gender options
    private final String[] genderOptions = { "Male", "Female", "Other", "Prefer not to say" };

    // Activity level options
    private final String[] activityOptions = { "Sedentary", "Lightly Active", "Moderately Active", "Very Active" };

    // Fitness goal options
    private final String[] fitnessGoalOptions = { "Lose Weight", "Maintain Weight", "Build Muscle", "Improve Fitness",
            "Increase Flexibility" };

    // Photo picker launcher
    private final ActivityResultLauncher<Intent> photoPickerLauncher = registerForActivityResult(
            new ActivityResultContracts.StartActivityForResult(),
            result -> {
                if (result.getResultCode() == RESULT_OK && result.getData() != null) {
                    selectedPhotoUri = result.getData().getData();
                    if (selectedPhotoUri != null) {
                        // Take persistent permission
                        try {
                            getContentResolver().takePersistableUriPermission(
                                    selectedPhotoUri,
                                    Intent.FLAG_GRANT_READ_URI_PERMISSION);
                        } catch (SecurityException e) {
                            // Ignore - some URIs don't support persistent permissions
                        }
                        loadProfilePhoto(selectedPhotoUri);
                    }
                }
            });

    // Permission launcher for gallery
    private final ActivityResultLauncher<String> permissionLauncher = registerForActivityResult(
            new ActivityResultContracts.RequestPermission(),
            isGranted -> {
                if (isGranted) {
                    openPhotoPicker();
                } else {
                    Toast.makeText(this, "Permission required to select photo", Toast.LENGTH_SHORT).show();
                }
            });

    // Permission launcher for camera
    private final ActivityResultLauncher<String> cameraPermissionLauncher = registerForActivityResult(
            new ActivityResultContracts.RequestPermission(),
            isGranted -> {
                if (isGranted) {
                    openCamera();
                } else {
                    Toast.makeText(this, "Camera permission required to take photo", Toast.LENGTH_SHORT).show();
                }
            });

    // Camera launcher
    private final ActivityResultLauncher<Intent> cameraLauncher = registerForActivityResult(
            new ActivityResultContracts.StartActivityForResult(),
            result -> {
                if (result.getResultCode() == RESULT_OK && result.getData() != null) {
                    // Get the thumbnail bitmap from camera
                    android.graphics.Bitmap bitmap = (android.graphics.Bitmap) result.getData().getExtras().get("data");
                    if (bitmap != null) {
                        ivProfilePhoto.setImageBitmap(bitmap);
                        // Save to internal storage and get URI
                        saveCameraPhoto(bitmap);
                    }
                }
            });

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_edit_profile);

        firebaseAuth = FirebaseAuth.getInstance();

        initViews();
        loadExistingProfile();
        setupListeners();
        updateBmiDisplay();
    }

    private void initViews() {
        ivProfilePhoto = findViewById(R.id.ivProfilePhoto);
        etDisplayName = findViewById(R.id.etDisplayName);
        etAge = findViewById(R.id.etAge);
        tvGender = findViewById(R.id.tvGender);
        etHeight = findViewById(R.id.etHeight);
        etWeight = findViewById(R.id.etWeight);
        etTargetWeight = findViewById(R.id.etTargetWeight);
        tvActivityLevel = findViewById(R.id.tvActivityLevel);
        tvFitnessGoal = findViewById(R.id.tvFitnessGoal);
        tvBmiValue = findViewById(R.id.tvBmiValue);
        tvBmiCategory = findViewById(R.id.tvBmiCategory);
        tvBmiAdvice = findViewById(R.id.tvBmiAdvice);
        tvCurrentWeightValue = findViewById(R.id.tvCurrentWeightValue);
        tvTargetWeightValue = findViewById(R.id.tvTargetWeightValue);
        tvWeightDifference = findViewById(R.id.tvWeightDifference);
    }

    private void loadExistingProfile() {
        SharedPreferences prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);

        // Load display name from Firebase or prefs
        FirebaseUser user = firebaseAuth.getCurrentUser();
        if (user != null && user.getDisplayName() != null) {
            etDisplayName.setText(user.getDisplayName());
        } else {
            GoogleSignInAccount account = GoogleSignIn.getLastSignedInAccount(this);
            if (account != null && account.getDisplayName() != null) {
                etDisplayName.setText(account.getDisplayName());
            } else {
                etDisplayName.setText(prefs.getString(KEY_DISPLAY_NAME, ""));
            }
        }

        // Load profile photo
        String photoUriString = prefs.getString(KEY_PROFILE_PHOTO_URI, null);
        if (photoUriString != null) {
            selectedPhotoUri = Uri.parse(photoUriString);
            loadProfilePhoto(selectedPhotoUri);
        } else if (user != null && user.getPhotoUrl() != null) {
            loadProfilePhoto(user.getPhotoUrl());
        } else {
            GoogleSignInAccount account = GoogleSignIn.getLastSignedInAccount(this);
            if (account != null && account.getPhotoUrl() != null) {
                loadProfilePhoto(account.getPhotoUrl());
            }
        }

        // Load body measurements
        float height = prefs.getFloat(KEY_USER_HEIGHT, 0f);
        float weight = prefs.getFloat(KEY_USER_WEIGHT, 0f);
        int age = prefs.getInt(KEY_USER_AGE, 0);
        selectedGender = prefs.getString(KEY_USER_GENDER, "");
        selectedActivityLevel = prefs.getString(KEY_USER_ACTIVITY, "");
        selectedFitnessGoal = prefs.getString(KEY_FITNESS_GOAL, "");
        float targetWeight = prefs.getFloat(KEY_TARGET_WEIGHT, 0f);

        // Populate fields
        if (height > 0)
            etHeight.setText(String.valueOf((int) height));
        if (weight > 0)
            etWeight.setText(String.valueOf((int) weight));
        if (age > 0)
            etAge.setText(String.valueOf(age));
        if (targetWeight > 0)
            etTargetWeight.setText(String.valueOf((int) targetWeight));

        // Set selection displays
        if (!selectedGender.isEmpty() && !selectedGender.equals("Not specified")) {
            tvGender.setText(selectedGender);
        }
        if (!selectedActivityLevel.isEmpty() && !selectedActivityLevel.equals("Not specified")) {
            tvActivityLevel.setText(selectedActivityLevel);
        }
        if (!selectedFitnessGoal.isEmpty()) {
            tvFitnessGoal.setText(selectedFitnessGoal);
        }
    }

    private void loadProfilePhoto(Uri uri) {
        Glide.with(this)
                .load(uri)
                .placeholder(R.drawable.default_profile)
                .error(R.drawable.default_profile)
                .circleCrop()
                .into(ivProfilePhoto);
    }

    private void setupListeners() {
        // Back button
        findViewById(R.id.btnBack).setOnClickListener(v -> onBackPressed());

        // Save button
        findViewById(R.id.btnSave).setOnClickListener(v -> saveProfile());

        // Change photo
        findViewById(R.id.btnChangePhoto).setOnClickListener(v -> requestPhotoPermission());
        ivProfilePhoto.setOnClickListener(v -> requestPhotoPermission());

        // Gender selector
        findViewById(R.id.layoutGender).setOnClickListener(v -> showGenderDialog());

        // Activity level selector
        findViewById(R.id.layoutActivityLevel).setOnClickListener(v -> showActivityLevelDialog());

        // Fitness goal selector
        findViewById(R.id.layoutFitnessGoal).setOnClickListener(v -> showFitnessGoalDialog());

        // Real-time BMI update when height/weight changes
        TextWatcher bmiWatcher = new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
            }

            @Override
            public void afterTextChanged(Editable s) {
                updateBmiDisplay();
                updateWeightProgress();
            }
        };

        etHeight.addTextChangedListener(bmiWatcher);
        etWeight.addTextChangedListener(bmiWatcher);
        etTargetWeight.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
            }

            @Override
            public void afterTextChanged(Editable s) {
                updateWeightProgress();
            }
        });
    }

    private void requestPhotoPermission() {
        // Show photo source options dialog
        String[] options = { "Choose from Gallery", "Take Photo", "Cancel" };

        new AlertDialog.Builder(this)
                .setTitle("Change Profile Photo")
                .setItems(options, (dialog, which) -> {
                    if (which == 0) {
                        // Gallery option
                        checkGalleryPermissionAndOpen();
                    } else if (which == 1) {
                        // Camera option
                        checkCameraPermissionAndOpen();
                    }
                    // Cancel does nothing
                })
                .show();
    }

    private void checkGalleryPermissionAndOpen() {
        String permission;
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
            permission = Manifest.permission.READ_MEDIA_IMAGES;
        } else {
            permission = Manifest.permission.READ_EXTERNAL_STORAGE;
        }

        if (ContextCompat.checkSelfPermission(this, permission) == PackageManager.PERMISSION_GRANTED) {
            openPhotoPicker();
        } else if (shouldShowRequestPermissionRationale(permission)) {
            // Show rationale dialog
            new AlertDialog.Builder(this)
                    .setTitle("Photo Permission Required")
                    .setMessage(
                            "Alignify needs access to your photos to let you choose a profile picture. This helps personalize your experience.")
                    .setPositiveButton("Grant Permission", (dialog, which) -> {
                        permissionLauncher.launch(permission);
                    })
                    .setNegativeButton("Cancel", null)
                    .show();
        } else {
            permissionLauncher.launch(permission);
        }
    }

    private void checkCameraPermissionAndOpen() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) {
            openCamera();
        } else if (shouldShowRequestPermissionRationale(Manifest.permission.CAMERA)) {
            // Show rationale dialog
            new AlertDialog.Builder(this)
                    .setTitle("Camera Permission Required")
                    .setMessage("Alignify needs camera access to let you take a new profile photo.")
                    .setPositiveButton("Grant Permission", (dialog, which) -> {
                        cameraPermissionLauncher.launch(Manifest.permission.CAMERA);
                    })
                    .setNegativeButton("Cancel", null)
                    .show();
        } else {
            cameraPermissionLauncher.launch(Manifest.permission.CAMERA);
        }
    }

    private void openCamera() {
        Intent intent = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);
        if (intent.resolveActivity(getPackageManager()) != null) {
            cameraLauncher.launch(intent);
        } else {
            Toast.makeText(this, "No camera app available", Toast.LENGTH_SHORT).show();
        }
    }

    private void openPhotoPicker() {
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("image/*");
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION);
        photoPickerLauncher.launch(intent);
    }

    private void saveCameraPhoto(android.graphics.Bitmap bitmap) {
        try {
            // Save bitmap to internal storage
            java.io.File file = new java.io.File(getFilesDir(), "profile_photo.jpg");
            java.io.FileOutputStream fos = new java.io.FileOutputStream(file);
            bitmap.compress(android.graphics.Bitmap.CompressFormat.JPEG, 90, fos);
            fos.close();

            // Get URI for the saved file
            selectedPhotoUri = Uri.fromFile(file);

            // Load the saved photo
            loadProfilePhoto(selectedPhotoUri);
        } catch (java.io.IOException e) {
            e.printStackTrace();
            Toast.makeText(this, "Failed to save photo", Toast.LENGTH_SHORT).show();
        }
    }

    private void showGenderDialog() {
        int selectedIndex = -1;
        for (int i = 0; i < genderOptions.length; i++) {
            if (genderOptions[i].equals(selectedGender)) {
                selectedIndex = i;
                break;
            }
        }

        new AlertDialog.Builder(this)
                .setTitle("Select Gender")
                .setSingleChoiceItems(genderOptions, selectedIndex, (dialog, which) -> {
                    selectedGender = genderOptions[which];
                    tvGender.setText(selectedGender);
                    dialog.dismiss();
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void showActivityLevelDialog() {
        int selectedIndex = -1;
        for (int i = 0; i < activityOptions.length; i++) {
            if (activityOptions[i].equals(selectedActivityLevel)) {
                selectedIndex = i;
                break;
            }
        }

        new AlertDialog.Builder(this)
                .setTitle("Select Activity Level")
                .setSingleChoiceItems(activityOptions, selectedIndex, (dialog, which) -> {
                    selectedActivityLevel = activityOptions[which];
                    tvActivityLevel.setText(selectedActivityLevel);
                    dialog.dismiss();
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void showFitnessGoalDialog() {
        int selectedIndex = -1;
        for (int i = 0; i < fitnessGoalOptions.length; i++) {
            if (fitnessGoalOptions[i].equals(selectedFitnessGoal)) {
                selectedIndex = i;
                break;
            }
        }

        new AlertDialog.Builder(this)
                .setTitle("Select Fitness Goal")
                .setSingleChoiceItems(fitnessGoalOptions, selectedIndex, (dialog, which) -> {
                    selectedFitnessGoal = fitnessGoalOptions[which];
                    tvFitnessGoal.setText(selectedFitnessGoal);
                    dialog.dismiss();
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void updateBmiDisplay() {
        String heightStr = etHeight.getText().toString().trim();
        String weightStr = etWeight.getText().toString().trim();

        if (TextUtils.isEmpty(heightStr) || TextUtils.isEmpty(weightStr)) {
            tvBmiValue.setText("--");
            tvBmiCategory.setText("Enter height & weight");
            tvBmiCategory.setBackgroundResource(R.drawable.bg_tag_lime);
            tvBmiAdvice.setText("Enter your height and weight to calculate your BMI.");
            return;
        }

        try {
            float height = Float.parseFloat(heightStr);
            float weight = Float.parseFloat(weightStr);

            if (height <= 0 || weight <= 0) {
                tvBmiValue.setText("--");
                return;
            }

            float heightM = height / 100f;
            float bmi = weight / (heightM * heightM);

            tvBmiValue.setText(String.format("%.1f", bmi));

            String category;
            int backgroundRes;
            String advice;

            if (bmi < 18.5f) {
                category = "Underweight";
                backgroundRes = R.drawable.bg_bmi_underweight;
                advice = "You are slightly underweight. Consider increasing your caloric intake with nutritious foods to reach a healthier weight.";
            } else if (bmi < 25f) {
                category = "Normal Weight";
                backgroundRes = R.drawable.bg_tag_lime;
                advice = "Great job! You are within a healthy BMI range. Maintain your current lifestyle with balanced nutrition and regular exercise.";
            } else if (bmi < 30f) {
                category = "Overweight";
                backgroundRes = R.drawable.bg_tag_orange;
                advice = "You are slightly overweight. Consider incorporating more physical activity and mindful eating to reach a healthier weight.";
            } else {
                category = "Obese";
                backgroundRes = R.drawable.bg_tag_orange;
                advice = "Your BMI indicates obesity. We recommend consulting a healthcare professional for personalized guidance on weight management.";
            }

            tvBmiCategory.setText(category);
            tvBmiCategory.setBackgroundResource(backgroundRes);
            tvBmiAdvice.setText(advice);

        } catch (NumberFormatException e) {
            tvBmiValue.setText("--");
        }
    }

    private void updateWeightProgress() {
        String weightStr = etWeight.getText().toString().trim();
        String targetWeightStr = etTargetWeight.getText().toString().trim();

        if (TextUtils.isEmpty(weightStr) || TextUtils.isEmpty(targetWeightStr)) {
            findViewById(R.id.cardWeightProgress).setVisibility(android.view.View.GONE);
            return;
        }

        try {
            float currentWeight = Float.parseFloat(weightStr);
            float targetWeight = Float.parseFloat(targetWeightStr);

            if (currentWeight <= 0 || targetWeight <= 0) {
                findViewById(R.id.cardWeightProgress).setVisibility(android.view.View.GONE);
                return;
            }

            findViewById(R.id.cardWeightProgress).setVisibility(android.view.View.VISIBLE);

            tvCurrentWeightValue.setText(String.format("%.0f kg", currentWeight));
            tvTargetWeightValue.setText(String.format("%.0f kg", targetWeight));

            float difference = Math.abs(currentWeight - targetWeight);

            if (currentWeight > targetWeight) {
                tvWeightDifference.setText(String.format("%.0f kg to lose!", difference));
                tvWeightDifference.setTextColor(ContextCompat.getColor(this, R.color.accent));
            } else if (currentWeight < targetWeight) {
                tvWeightDifference.setText(String.format("%.0f kg to gain!", difference));
                tvWeightDifference.setTextColor(ContextCompat.getColor(this, R.color.correct_green));
            } else {
                tvWeightDifference.setText("You've reached your goal! 🎉");
                tvWeightDifference.setTextColor(ContextCompat.getColor(this, R.color.correct_green));
            }

        } catch (NumberFormatException e) {
            findViewById(R.id.cardWeightProgress).setVisibility(android.view.View.GONE);
        }
    }

    private void saveProfile() {
        // Validate required fields
        String displayName = etDisplayName.getText().toString().trim();
        String heightStr = etHeight.getText().toString().trim();
        String weightStr = etWeight.getText().toString().trim();
        String ageStr = etAge.getText().toString().trim();

        if (TextUtils.isEmpty(displayName)) {
            etDisplayName.setError("Name is required");
            etDisplayName.requestFocus();
            return;
        }

        if (TextUtils.isEmpty(heightStr)) {
            etHeight.setError("Height is required");
            etHeight.requestFocus();
            return;
        }

        if (TextUtils.isEmpty(weightStr)) {
            etWeight.setError("Weight is required");
            etWeight.requestFocus();
            return;
        }

        float height = Float.parseFloat(heightStr);
        float weight = Float.parseFloat(weightStr);
        int age = TextUtils.isEmpty(ageStr) ? 0 : Integer.parseInt(ageStr);
        float targetWeight = 0f;

        String targetWeightStr = etTargetWeight.getText().toString().trim();
        if (!TextUtils.isEmpty(targetWeightStr)) {
            targetWeight = Float.parseFloat(targetWeightStr);
        }

        // Calculate BMI
        float heightM = height / 100f;
        float bmi = weight / (heightM * heightM);

        String bmiCategory;
        if (bmi < 18.5f) {
            bmiCategory = "Underweight";
        } else if (bmi < 25f) {
            bmiCategory = "Normal";
        } else if (bmi < 30f) {
            bmiCategory = "Overweight";
        } else {
            bmiCategory = "Obese";
        }

        // Save to SharedPreferences
        SharedPreferences prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
        SharedPreferences.Editor editor = prefs.edit();

        editor.putString(KEY_DISPLAY_NAME, displayName);
        editor.putFloat(KEY_USER_HEIGHT, height);
        editor.putFloat(KEY_USER_WEIGHT, weight);
        editor.putInt(KEY_USER_AGE, age);
        editor.putString(KEY_USER_GENDER, selectedGender.isEmpty() ? "Not specified" : selectedGender);
        editor.putString(KEY_USER_ACTIVITY, selectedActivityLevel.isEmpty() ? "Not specified" : selectedActivityLevel);
        editor.putString(KEY_FITNESS_GOAL, selectedFitnessGoal);
        editor.putFloat(KEY_TARGET_WEIGHT, targetWeight);
        editor.putFloat(KEY_USER_BMI, bmi);
        editor.putString(KEY_USER_BMI_CATEGORY, bmiCategory);
        editor.putBoolean("profile_complete", true);

        if (selectedPhotoUri != null) {
            editor.putString(KEY_PROFILE_PHOTO_URI, selectedPhotoUri.toString());
        }

        editor.apply();

        // Update Firebase display name if user is signed in
        FirebaseUser user = firebaseAuth.getCurrentUser();
        if (user != null) {
            UserProfileChangeRequest profileUpdates = new UserProfileChangeRequest.Builder()
                    .setDisplayName(displayName)
                    .build();
            user.updateProfile(profileUpdates);
        }

        // Save to Firestore
        String email = prefs.getString("user_email", "");
        UserRepository.getInstance().saveUserProfile(
                email, displayName, bmi, bmiCategory,
                selectedActivityLevel.isEmpty() ? "Not specified" : selectedActivityLevel,
                (int) height, (int) weight, age,
                selectedGender.isEmpty() ? "Not specified" : selectedGender,
                new UserRepository.OnCompleteListener() {
                    @Override
                    public void onSuccess() {
                        runOnUiThread(() -> {
                            Toast.makeText(EditProfileActivity.this, "Profile saved!", Toast.LENGTH_SHORT).show();
                            finish();
                        });
                    }

                    @Override
                    public void onError(String error) {
                        runOnUiThread(() -> {
                            Toast.makeText(EditProfileActivity.this, "Profile saved locally", Toast.LENGTH_SHORT)
                                    .show();
                            finish();
                        });
                    }
                });
    }

    @Override
    public void onBackPressed() {
        super.onBackPressed();
        overridePendingTransition(R.anim.fade_in, R.anim.fade_out);
    }
}
