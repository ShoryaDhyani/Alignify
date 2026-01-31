package com.alignify;

import android.Manifest;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.Bundle;
import android.provider.MediaStore;
import android.text.Editable;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.view.View;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;
import androidx.cardview.widget.CardView;
import androidx.core.content.ContextCompat;
import androidx.core.content.FileProvider;

import com.google.android.material.bottomsheet.BottomSheetDialog;

import com.alignify.data.UserRepository;
import com.bumptech.glide.Glide;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.chip.Chip;
import com.google.android.material.chip.ChipGroup;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.storage.FirebaseStorage;
import com.google.firebase.storage.StorageReference;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/**
 * Profile setup screen with modern UI and profile picture upload.
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
    private static final String KEY_PROFILE_IMAGE_URL = "profile_image_url";

    // Views
    private ImageView profileImage;
    private CardView profileImageCard;
    private TextInputEditText etName, etHeight, etWeight, etAge;
    private TextInputLayout tilName, tilHeight, tilWeight, tilAge;
    private MaterialButton btnMale, btnFemale, btnOther;
    private ChipGroup chipGroupActivity;
    private CardView bmiCard;
    private TextView tvBmiValue, tvBmiCategory;
    private View bmiScaleContainer;
    private ImageView bmiPointer;
    private MaterialButton btnSaveProfile;
    private ImageButton btnBack;
    private TextView pageTitle;

    private boolean isEditMode = false;
    private String selectedGender = "";
    private String selectedActivity = "";
    private Uri currentPhotoUri = null;
    private String profileImageUrl = null;

    // Camera/Gallery launchers
    private final ActivityResultLauncher<Intent> galleryLauncher = registerForActivityResult(
            new ActivityResultContracts.StartActivityForResult(), result -> {
                if (result.getResultCode() == RESULT_OK && result.getData() != null) {
                    Uri imageUri = result.getData().getData();
                    if (imageUri != null) {
                        setProfileImage(imageUri);
                        uploadProfileImage(imageUri);
                    }
                }
            });

    private final ActivityResultLauncher<Intent> cameraLauncher = registerForActivityResult(
            new ActivityResultContracts.StartActivityForResult(), result -> {
                if (result.getResultCode() == RESULT_OK) {
                    if (currentPhotoUri != null) {
                        setProfileImage(currentPhotoUri);
                        uploadProfileImage(currentPhotoUri);
                    }
                }
            });

    private final ActivityResultLauncher<String> cameraPermissionLauncher = registerForActivityResult(
            new ActivityResultContracts.RequestPermission(), isGranted -> {
                if (isGranted) {
                    openCamera();
                } else {
                    Toast.makeText(this, "Camera permission required", Toast.LENGTH_SHORT).show();
                }
            });

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_profile_setup_new);

        isEditMode = getIntent().getBooleanExtra("edit_mode", false);

        initViews();
        setupListeners();

        if (isEditMode) {
            loadExistingProfile();
            setupEditModeUI();
        }
    }

    private void initViews() {
        profileImage = findViewById(R.id.profileImage);
        profileImageCard = findViewById(R.id.profileImageCard);

        etName = findViewById(R.id.etName);
        etHeight = findViewById(R.id.etHeight);
        etWeight = findViewById(R.id.etWeight);
        etAge = findViewById(R.id.etAge);

        tilName = findViewById(R.id.tilName);
        tilHeight = findViewById(R.id.tilHeight);
        tilWeight = findViewById(R.id.tilWeight);
        tilAge = findViewById(R.id.tilAge);

        btnMale = findViewById(R.id.btnMale);
        btnFemale = findViewById(R.id.btnFemale);
        btnOther = findViewById(R.id.btnOther);

        chipGroupActivity = findViewById(R.id.chipGroupActivity);

        bmiCard = findViewById(R.id.bmiCard);
        tvBmiValue = findViewById(R.id.bmiValue);
        tvBmiCategory = findViewById(R.id.bmiCategory);
        bmiScaleContainer = findViewById(R.id.bmiScaleContainer);
        bmiPointer = findViewById(R.id.bmiPointer);

        btnSaveProfile = findViewById(R.id.btnSaveProfile);
        btnBack = findViewById(R.id.btnBack);
        pageTitle = findViewById(R.id.pageTitle);
    }

    private void setupListeners() {
        // Profile image click
        profileImageCard.setOnClickListener(v -> showImagePickerDialog());

        // Gender buttons
        btnMale.setOnClickListener(v -> selectGender("Male", btnMale));
        btnFemale.setOnClickListener(v -> selectGender("Female", btnFemale));
        btnOther.setOnClickListener(v -> selectGender("Other", btnOther));

        // Activity level chips
        chipGroupActivity.setOnCheckedChangeListener((group, checkedId) -> {
            Chip chip = findViewById(checkedId);
            if (chip != null) {
                selectedActivity = chip.getText().toString();
            }
        });

        // BMI calculation on height/weight change
        TextWatcher bmiWatcher = new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {}

            @Override
            public void afterTextChanged(Editable s) {
                calculateAndUpdateBMI();
            }
        };
        etHeight.addTextChangedListener(bmiWatcher);
        etWeight.addTextChangedListener(bmiWatcher);

        // Save button
        btnSaveProfile.setOnClickListener(v -> saveProfile());

        // Back button
        if (btnBack != null) {
            btnBack.setOnClickListener(v -> onBackPressed());
        }
    }

    private void selectGender(String gender, MaterialButton selectedBtn) {
        selectedGender = gender;

        // Reset all buttons
        btnMale.setStrokeColorResource(R.color.card_dark);
        btnFemale.setStrokeColorResource(R.color.card_dark);
        btnOther.setStrokeColorResource(R.color.card_dark);
        btnMale.setBackgroundTintList(ContextCompat.getColorStateList(this, R.color.card_dark));
        btnFemale.setBackgroundTintList(ContextCompat.getColorStateList(this, R.color.card_dark));
        btnOther.setBackgroundTintList(ContextCompat.getColorStateList(this, R.color.card_dark));

        // Highlight selected
        selectedBtn.setStrokeColorResource(R.color.accent);
        selectedBtn.setBackgroundTintList(ContextCompat.getColorStateList(this, R.color.accent_10));
    }

    private void showImagePickerDialog() {
        BottomSheetDialog bottomSheetDialog = new BottomSheetDialog(this, R.style.BottomSheetDialogTheme);
        View bottomSheetView = getLayoutInflater().inflate(R.layout.bottom_sheet_image_picker, null);
        bottomSheetDialog.setContentView(bottomSheetView);

        // Get views
        ImageButton btnClose = bottomSheetView.findViewById(R.id.btnCloseImagePicker);
        CardView cardCamera = bottomSheetView.findViewById(R.id.cardCamera);
        CardView cardGallery = bottomSheetView.findViewById(R.id.cardGallery);
        CardView cardRemovePhoto = bottomSheetView.findViewById(R.id.cardRemovePhoto);

        // Show remove option if profile image exists
        if (profileImageUrl != null && !profileImageUrl.isEmpty()) {
            cardRemovePhoto.setVisibility(View.VISIBLE);
        }

        // Close button
        btnClose.setOnClickListener(v -> bottomSheetDialog.dismiss());

        // Camera option
        cardCamera.setOnClickListener(v -> {
            bottomSheetDialog.dismiss();
            checkCameraPermissionAndOpen();
        });

        // Gallery option
        cardGallery.setOnClickListener(v -> {
            bottomSheetDialog.dismiss();
            openGallery();
        });

        // Remove photo option
        cardRemovePhoto.setOnClickListener(v -> {
            bottomSheetDialog.dismiss();
            removeProfileImage();
        });

        bottomSheetDialog.show();
    }

    private void removeProfileImage() {
        profileImageUrl = null;
        profileImage.setImageResource(R.drawable.ic_person);
        
        // Remove from SharedPreferences
        SharedPreferences prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
        prefs.edit().remove(KEY_PROFILE_IMAGE_URL).apply();
        
        // Remove from Firestore
        UserRepository.getInstance().updateProfileImageUrl("", new UserRepository.OnCompleteListener() {
            @Override
            public void onSuccess() {
                Toast.makeText(ProfileSetupActivity.this, "Profile picture removed", Toast.LENGTH_SHORT).show();
            }

            @Override
            public void onError(String error) {
                Toast.makeText(ProfileSetupActivity.this, "Failed to remove picture", Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void checkCameraPermissionAndOpen() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA)
                == PackageManager.PERMISSION_GRANTED) {
            openCamera();
        } else {
            cameraPermissionLauncher.launch(Manifest.permission.CAMERA);
        }
    }

    private void openCamera() {
        Intent takePictureIntent = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);
        if (takePictureIntent.resolveActivity(getPackageManager()) != null) {
            File photoFile = createImageFile();
            if (photoFile != null) {
                currentPhotoUri = FileProvider.getUriForFile(this,
                        getPackageName() + ".provider", photoFile);
                takePictureIntent.putExtra(MediaStore.EXTRA_OUTPUT, currentPhotoUri);
                cameraLauncher.launch(takePictureIntent);
            }
        }
    }

    private void openGallery() {
        Intent intent = new Intent(Intent.ACTION_PICK, MediaStore.Images.Media.EXTERNAL_CONTENT_URI);
        galleryLauncher.launch(intent);
    }

    private File createImageFile() {
        try {
            String timeStamp = new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(new Date());
            String imageFileName = "PROFILE_" + timeStamp + "_";
            File storageDir = getCacheDir();
            return File.createTempFile(imageFileName, ".jpg", storageDir);
        } catch (IOException e) {
            e.printStackTrace();
            return null;
        }
    }

    private void setProfileImage(Uri uri) {
        Glide.with(this)
                .load(uri)
                .circleCrop()
                .placeholder(R.drawable.ic_person)
                .into(profileImage);
    }

    private void uploadProfileImage(Uri imageUri) {
        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        if (user == null) {
            Toast.makeText(this, "Please sign in to upload profile picture", Toast.LENGTH_SHORT).show();
            return;
        }

        // Show loading
        Toast.makeText(this, "Uploading profile picture...", Toast.LENGTH_SHORT).show();

        // Compress image
        try {
            Bitmap bitmap = MediaStore.Images.Media.getBitmap(getContentResolver(), imageUri);
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            bitmap.compress(Bitmap.CompressFormat.JPEG, 70, baos);
            byte[] data = baos.toByteArray();

            // Upload to Firebase Storage
            StorageReference storageRef = FirebaseStorage.getInstance().getReference();
            StorageReference profileRef = storageRef.child("profile_images/" + user.getUid() + ".jpg");

            profileRef.putBytes(data)
                    .addOnSuccessListener(taskSnapshot -> {
                        profileRef.getDownloadUrl().addOnSuccessListener(uri -> {
                            profileImageUrl = uri.toString();
                            saveProfileImageUrl(profileImageUrl);
                            Toast.makeText(this, "Profile picture uploaded!", Toast.LENGTH_SHORT).show();
                        });
                    })
                    .addOnFailureListener(e -> {
                        Toast.makeText(this, "Upload failed: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                    });
        } catch (IOException e) {
            Toast.makeText(this, "Failed to process image", Toast.LENGTH_SHORT).show();
        }
    }

    private void saveProfileImageUrl(String url) {
        SharedPreferences prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
        prefs.edit().putString(KEY_PROFILE_IMAGE_URL, url).apply();

        // Also save to Firestore
        UserRepository.getInstance().updateProfileImageUrl(url, new UserRepository.OnCompleteListener() {
            @Override
            public void onSuccess() {}

            @Override
            public void onError(String error) {}
        });
    }

    private void calculateAndUpdateBMI() {
        String heightStr = etHeight.getText().toString().trim();
        String weightStr = etWeight.getText().toString().trim();

        if (TextUtils.isEmpty(heightStr) || TextUtils.isEmpty(weightStr)) {
            bmiCard.setVisibility(View.GONE);
            return;
        }

        try {
            float height = Float.parseFloat(heightStr);
            float weight = Float.parseFloat(weightStr);

            if (height <= 0 || weight <= 0) {
                bmiCard.setVisibility(View.GONE);
                return;
            }

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

            tvBmiValue.setText(String.format(Locale.US, "%.1f", bmi));
            tvBmiCategory.setText(category);
            tvBmiCategory.setTextColor(ContextCompat.getColor(this, categoryColor));

            // Update BMI scale pointer position
            updateBMIPointer(bmi);

            bmiCard.setVisibility(View.VISIBLE);
        } catch (NumberFormatException e) {
            bmiCard.setVisibility(View.GONE);
        }
    }

    private void updateBMIPointer(float bmi) {
        // Clamp BMI between 15 and 40 for visual representation
        float clampedBmi = Math.max(15f, Math.min(40f, bmi));
        float progress = (clampedBmi - 15f) / 25f; // 0 to 1

        // Post to get the actual width after layout
        bmiPointer.post(() -> {
            if (bmiScaleContainer != null) {
                int containerWidth = bmiScaleContainer.getWidth();
                float pointerX = containerWidth * progress - (bmiPointer.getWidth() / 2f);
                bmiPointer.setTranslationX(pointerX);
            }
        });
    }

    private void saveProfile() {
        String name = etName.getText().toString().trim();
        String heightStr = etHeight.getText().toString().trim();
        String weightStr = etWeight.getText().toString().trim();
        String ageStr = etAge.getText().toString().trim();

        // Validation
        boolean valid = true;
        if (TextUtils.isEmpty(name)) {
            tilName.setError("Name is required");
            valid = false;
        } else {
            tilName.setError(null);
        }
        if (TextUtils.isEmpty(heightStr)) {
            tilHeight.setError("Height is required");
            valid = false;
        } else {
            tilHeight.setError(null);
        }
        if (TextUtils.isEmpty(weightStr)) {
            tilWeight.setError("Weight is required");
            valid = false;
        } else {
            tilWeight.setError(null);
        }
        if (TextUtils.isEmpty(ageStr)) {
            tilAge.setError("Age is required");
            valid = false;
        } else {
            tilAge.setError(null);
        }

        if (!valid) return;

        float height = Float.parseFloat(heightStr);
        float weight = Float.parseFloat(weightStr);
        int age = Integer.parseInt(ageStr);

        // Calculate BMI
        float heightM = height / 100f;
        float bmi = weight / (heightM * heightM);

        String category;
        if (bmi < 18.5f) category = "Underweight";
        else if (bmi < 25f) category = "Normal";
        else if (bmi < 30f) category = "Overweight";
        else category = "Obese";

        // Save to preferences
        SharedPreferences prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
        String email = prefs.getString("user_email", "");

        prefs.edit()
                .putString("user_name", name)
                .putFloat(KEY_USER_HEIGHT, height)
                .putFloat(KEY_USER_WEIGHT, weight)
                .putInt(KEY_USER_AGE, age)
                .putString(KEY_USER_GENDER, selectedGender.isEmpty() ? "Not specified" : selectedGender)
                .putString(KEY_USER_ACTIVITY, selectedActivity.isEmpty() ? "Not specified" : selectedActivity)
                .putFloat(KEY_USER_BMI, bmi)
                .putString(KEY_USER_BMI_CATEGORY, category)
                .putBoolean(KEY_PROFILE_COMPLETE, true)
                .apply();

        // Save to Firestore
        UserRepository.getInstance().saveUserProfile(
                email, name, bmi, category, selectedActivity,
                (int) height, (int) weight, age, selectedGender,
                new UserRepository.OnCompleteListener() {
                    @Override
                    public void onSuccess() {
                        runOnUiThread(() -> {
                            String msg = isEditMode ? "Profile updated!" : "Profile created!";
                            Toast.makeText(ProfileSetupActivity.this, msg, Toast.LENGTH_SHORT).show();
                            navigateToDashboard();
                        });
                    }

                    @Override
                    public void onError(String error) {
                        runOnUiThread(() -> {
                            Toast.makeText(ProfileSetupActivity.this, 
                                    "Saved locally. Cloud sync pending.", Toast.LENGTH_SHORT).show();
                            navigateToDashboard();
                        });
                    }
                });
    }

    private void loadExistingProfile() {
        SharedPreferences prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);

        String name = prefs.getString("user_name", "");
        float height = prefs.getFloat(KEY_USER_HEIGHT, 0f);
        float weight = prefs.getFloat(KEY_USER_WEIGHT, 0f);
        int age = prefs.getInt(KEY_USER_AGE, 0);
        String gender = prefs.getString(KEY_USER_GENDER, "");
        String activity = prefs.getString(KEY_USER_ACTIVITY, "");
        String imageUrl = prefs.getString(KEY_PROFILE_IMAGE_URL, "");

        // Populate fields
        if (!TextUtils.isEmpty(name)) etName.setText(name);
        if (height > 0) etHeight.setText(String.valueOf((int) height));
        if (weight > 0) etWeight.setText(String.valueOf((int) weight));
        if (age > 0) etAge.setText(String.valueOf(age));

        // Set gender selection
        if (!TextUtils.isEmpty(gender)) {
            switch (gender) {
                case "Male": selectGender("Male", btnMale); break;
                case "Female": selectGender("Female", btnFemale); break;
                case "Other": selectGender("Other", btnOther); break;
            }
        }

        // Set activity level
        if (!TextUtils.isEmpty(activity)) {
            for (int i = 0; i < chipGroupActivity.getChildCount(); i++) {
                Chip chip = (Chip) chipGroupActivity.getChildAt(i);
                if (chip.getText().toString().equals(activity)) {
                    chip.setChecked(true);
                    selectedActivity = activity;
                    break;
                }
            }
        }

        // Load profile image from local cache first
        if (!TextUtils.isEmpty(imageUrl)) {
            profileImageUrl = imageUrl;
            Glide.with(this)
                    .load(imageUrl)
                    .circleCrop()
                    .placeholder(R.drawable.ic_person)
                    .into(profileImage);
        }
        
        // Also fetch from Firestore to ensure we have the latest
        UserRepository.getInstance().loadUserProfile(new UserRepository.OnProfileLoadedListener() {
            @Override
            public void onProfileLoaded(java.util.Map<String, Object> profileData) {
                if (profileData != null && profileData.containsKey("profileImageUrl")) {
                    String firebaseImageUrl = (String) profileData.get("profileImageUrl");
                    if (firebaseImageUrl != null && !firebaseImageUrl.isEmpty()) {
                        profileImageUrl = firebaseImageUrl;
                        // Update cache
                        prefs.edit().putString(KEY_PROFILE_IMAGE_URL, firebaseImageUrl).apply();
                        
                        // Load from Firebase URL
                        if (!isFinishing()) {
                            Glide.with(ProfileSetupActivity.this)
                                    .load(firebaseImageUrl)
                                    .circleCrop()
                                    .placeholder(R.drawable.ic_person)
                                    .into(profileImage);
                        }
                    }
                }
            }

            @Override
            public void onError(String error) {
                // Silently fail, keep cached/default image
            }
        });
    }

    private void setupEditModeUI() {
        if (pageTitle != null) pageTitle.setText("Edit Profile");
        if (btnBack != null) btnBack.setVisibility(View.VISIBLE);
        btnSaveProfile.setText("Save Changes");
    }

    private void navigateToDashboard() {
        if (isEditMode) {
            finish();
        } else {
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
