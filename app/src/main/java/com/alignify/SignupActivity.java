package com.alignify;

import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.text.TextUtils;
import android.util.Log;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
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
import com.google.firebase.auth.UserProfileChangeRequest;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.SetOptions;

import java.util.HashMap;
import java.util.Map;

/**
 * Signup screen for creating new user accounts.
 */
public class SignupActivity extends AppCompatActivity {

    private static final String TAG = "SignupActivity";
    private static final String PREFS_NAME = "AlignifyPrefs";
    private static final String KEY_LOGGED_IN = "logged_in";
    private static final String KEY_USER_EMAIL = "user_email";
    private static final String KEY_USER_NAME = "user_name";
    private static final String KEY_PROFILE_COMPLETE = "profile_complete";

    // Google OAuth Web Client ID
    private static final String GOOGLE_CLIENT_ID = "135631564844-07m3vdbe2t64gmnncbrjedbt6mili3p7.apps.googleusercontent.com";

    private EditText nameInput;
    private EditText emailInput;
    private EditText passwordInput;
    private EditText confirmPasswordInput;
    private Button btnSignup;
    private Button btnGoogle;
    private TextView linkLogin;
    private ImageView btnBack;

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
        setContentView(R.layout.activity_signup);

        // Initialize Firebase
        firebaseAuth = FirebaseAuth.getInstance();
        firestore = FirebaseFirestore.getInstance();

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
        nameInput = findViewById(R.id.nameInput);
        emailInput = findViewById(R.id.emailInput);
        passwordInput = findViewById(R.id.passwordInput);
        confirmPasswordInput = findViewById(R.id.confirmPasswordInput);
        btnSignup = findViewById(R.id.btnSignup);
        btnGoogle = findViewById(R.id.btnGoogle);
        linkLogin = findViewById(R.id.linkLogin);
        btnBack = findViewById(R.id.btnBack);
    }

    private void setupListeners() {
        btnSignup.setOnClickListener(v -> handleEmailSignup());
        btnGoogle.setOnClickListener(v -> handleGoogleSignIn());
        linkLogin.setOnClickListener(v -> {
            // Go back to login
            finish();
        });
        btnBack.setOnClickListener(v -> finish());
    }

    private void handleEmailSignup() {
        String name = nameInput.getText().toString().trim();
        String email = emailInput.getText().toString().trim();
        String password = passwordInput.getText().toString().trim();
        String confirmPassword = confirmPasswordInput.getText().toString().trim();

        // Validation
        if (TextUtils.isEmpty(name)) {
            nameInput.setError("Name is required");
            nameInput.requestFocus();
            return;
        }

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

        if (password.length() < 6) {
            passwordInput.setError("Password must be at least 6 characters");
            passwordInput.requestFocus();
            return;
        }

        if (!password.equals(confirmPassword)) {
            confirmPasswordInput.setError("Passwords do not match");
            confirmPasswordInput.requestFocus();
            return;
        }

        // Show loading
        btnSignup.setEnabled(false);
        btnSignup.setText("Creating account...");

        // Create account with Firebase
        firebaseAuth.createUserWithEmailAndPassword(email, password)
                .addOnCompleteListener(this, task -> {
                    if (task.isSuccessful()) {
                        Log.d(TAG, "createUserWithEmail:success");
                        FirebaseUser user = firebaseAuth.getCurrentUser();

                        // Update display name
                        if (user != null) {
                            UserProfileChangeRequest profileUpdates = new UserProfileChangeRequest.Builder()
                                    .setDisplayName(name)
                                    .build();

                            user.updateProfile(profileUpdates)
                                    .addOnCompleteListener(profileTask -> {
                                        saveUserToFirestore(user, email, name, "");
                                    });
                        }
                    } else {
                        Log.e(TAG, "createUserWithEmail:failure", task.getException());
                        btnSignup.setEnabled(true);
                        btnSignup.setText("Create Account");

                        String errorMsg = task.getException() != null ? task.getException().getMessage()
                                : "Registration failed";
                        Toast.makeText(this, errorMsg, Toast.LENGTH_LONG).show();
                    }
                });
    }

    private void handleGoogleSignIn() {
        btnGoogle.setEnabled(false);
        btnGoogle.setText("Signing up...");

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
                                    saveUserToFirestore(user, email, displayName, photoUrl);
                                } else {
                                    Log.e(TAG, "Firebase Auth failed", authTask.getException());
                                    btnGoogle.setEnabled(true);
                                    btnGoogle.setText("Sign up with Google");
                                    Toast.makeText(this, "Authentication failed", Toast.LENGTH_SHORT).show();
                                }
                            });
                } else {
                    btnGoogle.setEnabled(true);
                    btnGoogle.setText("Sign up with Google");
                    Toast.makeText(this, "Failed to get authentication token", Toast.LENGTH_SHORT).show();
                }
            }
        } catch (ApiException e) {
            Log.e(TAG, "Google Sign-In failed: " + e.getStatusCode(), e);
            btnGoogle.setEnabled(true);
            btnGoogle.setText("Sign up with Google");

            if (e.getStatusCode() != 12501) { // Not cancelled
                Toast.makeText(this, "Sign-in failed (code: " + e.getStatusCode() + ")",
                        Toast.LENGTH_SHORT).show();
            }
        }
    }

    private void saveUserToFirestore(FirebaseUser user, String email, String displayName, String photoUrl) {
        if (user == null) {
            Log.e(TAG, "No Firebase user to save");
            return;
        }

        String uid = user.getUid();
        Log.d(TAG, "Saving new user to Firestore: " + uid);

        Map<String, Object> userData = new HashMap<>();
        userData.put("uid", uid);
        userData.put("email", email != null ? email : "");
        userData.put("name", displayName != null ? displayName : "");
        userData.put("photoUrl", photoUrl != null ? photoUrl : "");
        userData.put("createdAt", System.currentTimeMillis());
        userData.put("lastLoginAt", System.currentTimeMillis());
        userData.put("profileComplete", false);
        userData.put("authProvider", photoUrl != null && !photoUrl.isEmpty() ? "google" : "email");

        firestore.collection("users").document(uid)
                .set(userData, SetOptions.merge())
                .addOnSuccessListener(aVoid -> {
                    Log.d(TAG, "User saved to Firestore successfully");
                    saveLocalAndNavigate(email, displayName);
                })
                .addOnFailureListener(e -> {
                    Log.e(TAG, "Failed to save user to Firestore", e);
                    saveLocalAndNavigate(email, displayName);
                });
    }

    private void saveLocalAndNavigate(String email, String displayName) {
        SharedPreferences prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
        prefs.edit()
                .putBoolean(KEY_LOGGED_IN, true)
                .putString(KEY_USER_EMAIL, email != null ? email : "")
                .putString(KEY_USER_NAME, displayName != null ? displayName : "")
                .apply();

        Toast.makeText(this, "Account created! Welcome, " +
                (displayName != null ? displayName : email) + "!", Toast.LENGTH_SHORT).show();

        // Navigate to profile setup
        Intent intent = new Intent(this, ProfileSetupActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(intent);
        finish();
    }
}
