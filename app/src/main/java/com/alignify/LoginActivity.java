package com.alignify;

import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.text.TextUtils;
import android.util.Log;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;

import com.google.android.gms.auth.api.signin.GoogleSignIn;
import com.google.android.gms.auth.api.signin.GoogleSignInAccount;
import com.google.android.gms.auth.api.signin.GoogleSignInClient;
import com.google.android.gms.auth.api.signin.GoogleSignInOptions;
import com.google.android.gms.common.api.ApiException;
import com.google.android.gms.tasks.Task;
import com.google.firebase.auth.AuthCredential;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.auth.GoogleAuthProvider;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.SetOptions;

import java.util.HashMap;
import java.util.Map;

/**
 * Login screen for existing user authentication.
 */
public class LoginActivity extends AppCompatActivity {

    private static final String TAG = "LoginActivity";
    private static final String PREFS_NAME = "AlignifyPrefs";
    private static final String KEY_LOGGED_IN = "logged_in";
    private static final String KEY_USER_EMAIL = "user_email";
    private static final String KEY_USER_NAME = "user_name";
    private static final String KEY_PROFILE_COMPLETE = "profile_complete";
    private static final String KEY_USER_BMI = "user_bmi";
    private static final String KEY_USER_BMI_CATEGORY = "user_bmi_category";
    private static final String KEY_USER_ACTIVITY = "user_activity";
    private static final String KEY_USER_HEIGHT = "user_height";
    private static final String KEY_USER_WEIGHT = "user_weight";
    private static final String KEY_USER_AGE = "user_age";
    private static final String KEY_USER_GENDER = "user_gender";

    // Google OAuth Web Client ID
    private static final String GOOGLE_CLIENT_ID = "135631564844-07m3vdbe2t64gmnncbrjedbt6mili3p7.apps.googleusercontent.com";

    private EditText emailInput;
    private EditText passwordInput;
    private Button btnLogin;
    private Button btnGoogle;
    private Button btnGuest;
    private TextView linkSignup;
    private TextView forgotPassword;

    private GoogleSignInClient googleSignInClient;
    private FirebaseAuth firebaseAuth;
    private FirebaseFirestore firestore;

    private final ActivityResultLauncher<Intent> googleSignInLauncher = registerForActivityResult(
            new ActivityResultContracts.StartActivityForResult(),
            result -> {
                Task<GoogleSignInAccount> task = GoogleSignIn.getSignedInAccountFromIntent(result.getData());
                handleGoogleSignInResult(task);
            });

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // Initialize Firebase
        firebaseAuth = FirebaseAuth.getInstance();
        firestore = FirebaseFirestore.getInstance();

        // Check if already logged in
        FirebaseUser currentUser = firebaseAuth.getCurrentUser();
        SharedPreferences prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);

        if (currentUser != null || prefs.getBoolean(KEY_LOGGED_IN, false)) {
            navigateToNextScreen();
            return;
        }

        setContentView(R.layout.activity_login);

        // Configure Google Sign-In
        GoogleSignInOptions gso = new GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
                .requestIdToken(GOOGLE_CLIENT_ID)
                .requestEmail()
                .build();

        googleSignInClient = GoogleSignIn.getClient(this, gso);

        initViews();
        setupListeners();
    }

    private void initViews() {
        emailInput = findViewById(R.id.emailInput);
        passwordInput = findViewById(R.id.passwordInput);
        btnLogin = findViewById(R.id.btnLogin);
        btnGoogle = findViewById(R.id.btnGoogle);
        btnGuest = findViewById(R.id.btnGuest);
        linkSignup = findViewById(R.id.linkSignup);
        forgotPassword = findViewById(R.id.forgotPassword);
    }

    private void setupListeners() {
        btnLogin.setOnClickListener(v -> handleEmailLogin());
        btnGoogle.setOnClickListener(v -> handleGoogleSignIn());
        btnGuest.setOnClickListener(v -> handleGuestLogin());
        linkSignup.setOnClickListener(v -> {
            startActivity(new Intent(this, SignupActivity.class));
        });
        forgotPassword.setOnClickListener(v -> handleForgotPassword());
    }

    private void handleEmailLogin() {
        String email = emailInput.getText().toString().trim();
        String password = passwordInput.getText().toString().trim();

        // Validation
        if (TextUtils.isEmpty(email)) {
            emailInput.setError("Email is required");
            emailInput.requestFocus();
            return;
        }

        if (!android.util.Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
            emailInput.setError("Enter a valid email");
            emailInput.requestFocus();
            return;
        }

        if (TextUtils.isEmpty(password)) {
            passwordInput.setError("Password is required");
            passwordInput.requestFocus();
            return;
        }

        // Show loading
        btnLogin.setEnabled(false);
        btnLogin.setText("Signing in...");

        // Sign in with Firebase
        firebaseAuth.signInWithEmailAndPassword(email, password)
                .addOnCompleteListener(this, task -> {
                    if (task.isSuccessful()) {
                        Log.d(TAG, "signInWithEmail:success");
                        FirebaseUser user = firebaseAuth.getCurrentUser();
                        updateLastLogin(user, email);
                    } else {
                        Log.e(TAG, "signInWithEmail:failure", task.getException());
                        btnLogin.setEnabled(true);
                        btnLogin.setText("Sign In");

                        String errorMsg = task.getException() != null ? task.getException().getMessage()
                                : "Authentication failed";
                        Toast.makeText(this, errorMsg, Toast.LENGTH_LONG).show();
                    }
                });
    }

    private void handleGuestLogin() {
        btnGuest.setEnabled(false);
        btnGuest.setText("Loading...");

        firebaseAuth.signInAnonymously()
                .addOnCompleteListener(this, task -> {
                    if (task.isSuccessful()) {
                        Log.d(TAG, "signInAnonymously:success");
                        FirebaseUser user = firebaseAuth.getCurrentUser();
                        saveGuestUserAndNavigate(user);
                    } else {
                        Log.e(TAG, "signInAnonymously:failure", task.getException());
                        btnGuest.setEnabled(true);
                        btnGuest.setText("Continue as Guest");
                        Toast.makeText(this, "Guest login failed. Please try again.",
                                Toast.LENGTH_SHORT).show();
                    }
                });
    }

    private void saveGuestUserAndNavigate(FirebaseUser user) {
        if (user == null) {
            saveLocalAndNavigate(null, "Guest", false);
            return;
        }

        String uid = user.getUid();
        String guestName = "Guest User";

        Map<String, Object> userData = new HashMap<>();
        userData.put("uid", uid);
        userData.put("email", "");
        userData.put("name", guestName);
        userData.put("isAnonymous", true);
        userData.put("authProvider", "anonymous");
        userData.put("createdAt", System.currentTimeMillis());
        userData.put("lastLoginAt", System.currentTimeMillis());
        userData.put("profileComplete", false);

        firestore.collection("users").document(uid)
                .set(userData, SetOptions.merge())
                .addOnSuccessListener(aVoid -> {
                    Log.d(TAG, "Guest user saved to Firestore");
                    saveLocalAndNavigate("", guestName, false);
                })
                .addOnFailureListener(e -> {
                    Log.e(TAG, "Failed to save guest user", e);
                    // Navigate anyway - data will sync later
                    saveLocalAndNavigate("", guestName, false);
                });
    }

    private void handleForgotPassword() {
        String email = emailInput.getText().toString().trim();

        if (TextUtils.isEmpty(email)) {
            emailInput.setError("Enter your email first");
            emailInput.requestFocus();
            return;
        }

        if (!android.util.Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
            emailInput.setError("Enter a valid email");
            emailInput.requestFocus();
            return;
        }

        firebaseAuth.sendPasswordResetEmail(email)
                .addOnCompleteListener(task -> {
                    if (task.isSuccessful()) {
                        Toast.makeText(this, "Password reset email sent!", Toast.LENGTH_LONG).show();
                    } else {
                        Toast.makeText(this, "Failed to send reset email", Toast.LENGTH_SHORT).show();
                    }
                });
    }

    private void handleGoogleSignIn() {
        btnGoogle.setEnabled(false);
        btnGoogle.setText("Signing in...");

        googleSignInClient.signOut().addOnCompleteListener(this, task -> {
            Intent signInIntent = googleSignInClient.getSignInIntent();
            googleSignInLauncher.launch(signInIntent);
        });
    }

    private void handleGoogleSignInResult(Task<GoogleSignInAccount> completedTask) {
        try {
            GoogleSignInAccount account = completedTask.getResult(ApiException.class);

            if (account != null) {
                String email = account.getEmail();
                String displayName = account.getDisplayName();
                String idToken = account.getIdToken();
                String photoUrl = account.getPhotoUrl() != null ? account.getPhotoUrl().toString() : "";

                Log.d(TAG, "Google Sign-In successful: " + email);

                if (idToken != null) {
                    AuthCredential credential = GoogleAuthProvider.getCredential(idToken, null);
                    firebaseAuth.signInWithCredential(credential)
                            .addOnCompleteListener(this, authTask -> {
                                if (authTask.isSuccessful()) {
                                    Log.d(TAG, "Firebase Auth successful");
                                    FirebaseUser user = firebaseAuth.getCurrentUser();
                                    // Use updateLastLogin which properly fetches and restores profile data
                                    updateLastLoginWithGoogleInfo(user, email, displayName, photoUrl);
                                } else {
                                    Log.e(TAG, "Firebase Auth failed", authTask.getException());
                                    btnGoogle.setEnabled(true);
                                    btnGoogle.setText("Continue with Google");
                                    Toast.makeText(this, "Authentication failed", Toast.LENGTH_SHORT).show();
                                }
                            });
                } else {
                    btnGoogle.setEnabled(true);
                    btnGoogle.setText("Continue with Google");
                    Toast.makeText(this, "Failed to get authentication token", Toast.LENGTH_SHORT).show();
                }
            }
        } catch (ApiException e) {
            Log.e(TAG, "Google Sign-In failed: " + e.getStatusCode(), e);
            btnGoogle.setEnabled(true);
            btnGoogle.setText("Continue with Google");

            if (e.getStatusCode() != 12501) { // Not cancelled
                String errorMessage;
                switch (e.getStatusCode()) {
                    case 10:
                        errorMessage = "Developer error - check OAuth configuration";
                        break;
                    default:
                        errorMessage = "Sign-in failed (code: " + e.getStatusCode() + ")";
                }
                Toast.makeText(this, errorMessage, Toast.LENGTH_SHORT).show();
            }
        }
    }

    /**
     * Handles login for Google Sign-In users.
     * Fetches existing profile data and updates/creates the user document.
     */
    private void updateLastLoginWithGoogleInfo(FirebaseUser user, String email, String displayName, String photoUrl) {
        if (user == null) {
            Log.e(TAG, "No Firebase user to save");
            return;
        }

        String uid = user.getUid();
        Log.d(TAG, "Updating user with Google info: " + uid);

        // First, fetch existing user data
        firestore.collection("users").document(uid)
                .get()
                .addOnSuccessListener(documentSnapshot -> {
                    final boolean[] profileComplete = { false };

                    // Build user data to save/update
                    Map<String, Object> userData = new HashMap<>();
                    userData.put("uid", uid);
                    userData.put("email", email != null ? email : "");
                    userData.put("name", displayName != null ? displayName : "");
                    userData.put("photoUrl", photoUrl != null ? photoUrl : "");
                    userData.put("lastLoginAt", System.currentTimeMillis());
                    userData.put("authProvider", "google");

                    if (documentSnapshot.exists()) {
                        Log.d(TAG, "Existing user found, restoring profile data");

                        // Restore profile completion status
                        Boolean isComplete = documentSnapshot.getBoolean("profileComplete");
                        profileComplete[0] = isComplete != null && isComplete;

                        // Restore all profile data to SharedPreferences
                        SharedPreferences prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
                        SharedPreferences.Editor editor = prefs.edit();

                        // Restore BMI
                        Double bmi = documentSnapshot.getDouble("bmi");
                        if (bmi != null) {
                            editor.putFloat(KEY_USER_BMI, bmi.floatValue());
                            Log.d(TAG, "Restored BMI: " + bmi);
                        }

                        // Restore BMI category
                        String bmiCategory = documentSnapshot.getString("bmiCategory");
                        if (bmiCategory != null) {
                            editor.putString(KEY_USER_BMI_CATEGORY, bmiCategory);
                        }

                        // Restore activity level
                        String activityLevel = documentSnapshot.getString("activityLevel");
                        if (activityLevel != null) {
                            editor.putString(KEY_USER_ACTIVITY, activityLevel);
                        }

                        // Restore height - handle both Long and Double
                        Object heightObj = documentSnapshot.get("height");
                        if (heightObj != null) {
                            float heightVal = heightObj instanceof Long ? ((Long) heightObj).floatValue()
                                    : ((Double) heightObj).floatValue();
                            editor.putFloat(KEY_USER_HEIGHT, heightVal);
                        }

                        // Restore weight - handle both Long and Double
                        Object weightObj = documentSnapshot.get("weight");
                        if (weightObj != null) {
                            float weightVal = weightObj instanceof Long ? ((Long) weightObj).floatValue()
                                    : ((Double) weightObj).floatValue();
                            editor.putFloat(KEY_USER_WEIGHT, weightVal);
                        }

                        // Restore age
                        Long age = documentSnapshot.getLong("age");
                        if (age != null) {
                            editor.putInt(KEY_USER_AGE, age.intValue());
                        }

                        // Restore gender
                        String gender = documentSnapshot.getString("gender");
                        if (gender != null) {
                            editor.putString(KEY_USER_GENDER, gender);
                        }

                        editor.apply();
                        Log.d(TAG, "Profile data restored, profileComplete=" + profileComplete[0]);
                    } else {
                        Log.d(TAG, "New Google user, creating profile");
                        userData.put("createdAt", System.currentTimeMillis());
                        userData.put("profileComplete", false);
                    }

                    // Save user data to Firestore
                    firestore.collection("users").document(uid)
                            .set(userData, SetOptions.merge())
                            .addOnSuccessListener(aVoid -> {
                                Log.d(TAG, "User saved to Firestore");
                                saveLocalAndNavigate(email, displayName, profileComplete[0]);
                            })
                            .addOnFailureListener(e -> {
                                Log.e(TAG, "Failed to save user to Firestore", e);
                                saveLocalAndNavigate(email, displayName, profileComplete[0]);
                            });
                })
                .addOnFailureListener(e -> {
                    Log.e(TAG, "Failed to fetch user data", e);
                    // Try to save user anyway
                    Map<String, Object> userData = new HashMap<>();
                    userData.put("uid", uid);
                    userData.put("email", email != null ? email : "");
                    userData.put("name", displayName != null ? displayName : "");
                    userData.put("photoUrl", photoUrl != null ? photoUrl : "");
                    userData.put("lastLoginAt", System.currentTimeMillis());
                    userData.put("authProvider", "google");
                    userData.put("createdAt", System.currentTimeMillis());
                    userData.put("profileComplete", false);

                    firestore.collection("users").document(uid)
                            .set(userData, SetOptions.merge())
                            .addOnCompleteListener(task -> saveLocalAndNavigate(email, displayName, false));
                });
    }

    private void updateLastLogin(FirebaseUser user, String email) {
        if (user == null) {
            saveLocalAndNavigate(email, email.split("@")[0], false);
            return;
        }

        String uid = user.getUid();
        final String defaultName = user.getDisplayName() != null ? user.getDisplayName() : email.split("@")[0];

        // Fetch user profile from Firestore and restore local data
        firestore.collection("users").document(uid)
                .get()
                .addOnSuccessListener(documentSnapshot -> {
                    final boolean[] profileComplete = { false };
                    final String[] displayName = { defaultName };

                    if (documentSnapshot.exists()) {
                        Log.d(TAG, "Found user profile in Firestore");

                        // Restore profile completion status
                        Boolean isComplete = documentSnapshot.getBoolean("profileComplete");
                        profileComplete[0] = isComplete != null && isComplete;

                        // Restore profile data locally
                        SharedPreferences prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
                        SharedPreferences.Editor editor = prefs.edit();

                        // Restore name
                        String name = documentSnapshot.getString("name");
                        if (name != null && !name.isEmpty()) {
                            displayName[0] = name;
                            editor.putString(KEY_USER_NAME, name);
                        }

                        // Restore BMI
                        Double bmi = documentSnapshot.getDouble("bmi");
                        if (bmi != null) {
                            editor.putFloat(KEY_USER_BMI, bmi.floatValue());
                            Log.d(TAG, "Restored BMI: " + bmi);
                        }

                        // Restore BMI category
                        String bmiCategory = documentSnapshot.getString("bmiCategory");
                        if (bmiCategory != null) {
                            editor.putString(KEY_USER_BMI_CATEGORY, bmiCategory);
                        }

                        // Restore activity level
                        String activityLevel = documentSnapshot.getString("activityLevel");
                        if (activityLevel != null) {
                            editor.putString(KEY_USER_ACTIVITY, activityLevel);
                        }

                        // Restore height - handle both Long and Double
                        Object heightObj = documentSnapshot.get("height");
                        if (heightObj != null) {
                            float heightVal = heightObj instanceof Long ? ((Long) heightObj).floatValue()
                                    : ((Double) heightObj).floatValue();
                            editor.putFloat(KEY_USER_HEIGHT, heightVal);
                            Log.d(TAG, "Restored height: " + heightVal);
                        }

                        // Restore weight - handle both Long and Double
                        Object weightObj = documentSnapshot.get("weight");
                        if (weightObj != null) {
                            float weightVal = weightObj instanceof Long ? ((Long) weightObj).floatValue()
                                    : ((Double) weightObj).floatValue();
                            editor.putFloat(KEY_USER_WEIGHT, weightVal);
                            Log.d(TAG, "Restored weight: " + weightVal);
                        }

                        // Restore age
                        Long age = documentSnapshot.getLong("age");
                        if (age != null) {
                            editor.putInt(KEY_USER_AGE, age.intValue());
                        }

                        // Restore gender
                        String gender = documentSnapshot.getString("gender");
                        if (gender != null) {
                            editor.putString(KEY_USER_GENDER, gender);
                        }

                        editor.apply();
                        Log.d(TAG, "Profile data restored locally, profileComplete=" + profileComplete[0]);

                        // Update last login
                        Map<String, Object> updates = new HashMap<>();
                        updates.put("lastLoginAt", System.currentTimeMillis());
                        firestore.collection("users").document(uid).update(updates);
                    } else {
                        Log.d(TAG, "No user profile found in Firestore");
                    }

                    saveLocalAndNavigate(email, displayName[0], profileComplete[0]);
                })
                .addOnFailureListener(e -> {
                    Log.e(TAG, "Failed to fetch user data", e);
                    saveLocalAndNavigate(email, defaultName, false);
                });
    }

    private void saveUserToFirestore(FirebaseUser user, String email, String displayName, String photoUrl) {
        if (user == null) {
            Log.e(TAG, "No Firebase user to save");
            return;
        }

        String uid = user.getUid();
        Log.d(TAG, "Saving user to Firestore: " + uid);

        Map<String, Object> userData = new HashMap<>();
        userData.put("uid", uid);
        userData.put("email", email != null ? email : "");
        userData.put("name", displayName != null ? displayName : "");
        userData.put("photoUrl", photoUrl != null ? photoUrl : "");
        userData.put("lastLoginAt", System.currentTimeMillis());
        userData.put("authProvider", "google");

        // Only set createdAt if new user
        firestore.collection("users").document(uid).get()
                .addOnSuccessListener(documentSnapshot -> {
                    boolean existingProfileComplete = false;
                    if (!documentSnapshot.exists()) {
                        userData.put("createdAt", System.currentTimeMillis());
                        userData.put("profileComplete", false);
                    } else {
                        // Check if profile was already complete
                        existingProfileComplete = documentSnapshot.getBoolean("profileComplete") != null &&
                                documentSnapshot.getBoolean("profileComplete");
                    }

                    final boolean finalProfileComplete = existingProfileComplete;
                    firestore.collection("users").document(uid)
                            .set(userData, SetOptions.merge())
                            .addOnSuccessListener(aVoid -> {
                                Log.d(TAG, "User saved to Firestore");
                                saveLocalAndNavigate(email, displayName, finalProfileComplete);
                            })
                            .addOnFailureListener(e -> {
                                Log.e(TAG, "Failed to save user", e);
                                saveLocalAndNavigate(email, displayName, finalProfileComplete);
                            });
                })
                .addOnFailureListener(e -> {
                    // Just save anyway
                    firestore.collection("users").document(uid)
                            .set(userData, SetOptions.merge())
                            .addOnCompleteListener(task -> saveLocalAndNavigate(email, displayName, false));
                });
    }

    private void saveLocalAndNavigate(String email, String displayName, boolean profileComplete) {
        SharedPreferences prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
        prefs.edit()
                .putBoolean(KEY_LOGGED_IN, true)
                .putBoolean(KEY_PROFILE_COMPLETE, profileComplete)
                .putString(KEY_USER_EMAIL, email != null ? email : "")
                .putString(KEY_USER_NAME, displayName != null ? displayName : "")
                .apply();

        Toast.makeText(this, "Welcome back, " +
                (displayName != null ? displayName : email) + "!", Toast.LENGTH_SHORT).show();
        navigateToNextScreen();
    }

    private void navigateToNextScreen() {
        SharedPreferences prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
        boolean profileComplete = prefs.getBoolean(KEY_PROFILE_COMPLETE, false);

        Intent intent;
        if (profileComplete) {
            intent = new Intent(this, DashboardActivity.class);
        } else {
            intent = new Intent(this, ProfileSetupActivity.class);
        }

        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(intent);
        finish();
    }
}
