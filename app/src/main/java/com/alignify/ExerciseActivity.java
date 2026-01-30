package com.alignify;

import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.Matrix;
import android.media.MediaMetadataRetriever;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.speech.tts.TextToSpeech;
import android.util.Log;
import android.view.View;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;
import androidx.camera.core.CameraSelector;
import androidx.camera.core.ImageAnalysis;
import androidx.camera.core.ImageProxy;
import androidx.camera.core.Preview;
import androidx.camera.lifecycle.ProcessCameraProvider;
import androidx.core.content.ContextCompat;

import com.google.common.util.concurrent.ListenableFuture;
import com.google.mediapipe.framework.image.BitmapImageBuilder;
import com.google.mediapipe.framework.image.MPImage;
import com.google.mediapipe.tasks.core.Delegate;
import com.google.mediapipe.tasks.vision.core.RunningMode;
import com.google.mediapipe.tasks.vision.poselandmarker.PoseLandmarkerResult;
import com.alignify.databinding.ActivityExerciseBinding;
import com.alignify.data.UserRepository;
import com.alignify.exercises.*;

import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Exercise activity with camera preview and pose detection.
 */
public class ExerciseActivity extends AppCompatActivity implements PoseLandmarkerHelper.LandmarkerListener {

    private static final String TAG = "ExerciseActivity";
    public static final String EXTRA_EXERCISE_TYPE = "exercise_type";
    public static final String EXTRA_VIDEO_MODE = "video_mode";

    private ActivityExerciseBinding binding;
    private ExecutorService cameraExecutor;
    private ExecutorService videoExecutor;
    private Handler mainHandler;
    private PoseLandmarkerHelper poseLandmarkerHelper;
    private ExerciseDetector exerciseDetector;

    private final AtomicBoolean isDetecting = new AtomicBoolean(false);
    private String exerciseType = "bicep_curl";
    private boolean isFrontCamera = true;
    private boolean isVideoMode = false;
    private Uri videoUri = null;

    // Text to Speech - optimized settings
    private TextToSpeech tts;
    private boolean isTtsReady = false;
    private String lastSpokenFeedback = "";
    private long lastSpeakTime = 0L;
    private static final long SPEAK_COOLDOWN_MS = 5000L; // 5 seconds between same feedback
    private int consecutiveErrorCount = 0;
    private static final int MIN_ERRORS_BEFORE_SPEAK = 3; // Speak only after 3 consecutive errors

    // Session tracking for Firestore
    private long sessionStartTime = 0L;
    private int sessionErrors = 0;

    // Video picker
    private final ActivityResultLauncher<String> videoPicker = registerForActivityResult(
            new ActivityResultContracts.GetContent(), uri -> {
                if (uri != null) {
                    videoUri = uri;
                    isVideoMode = true;
                    binding.btnToggle.setText("Process Video");
                    Toast.makeText(this, "Video selected. Press Start to process.", Toast.LENGTH_SHORT).show();
                }
            });

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityExerciseBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        mainHandler = new Handler(Looper.getMainLooper());
        videoExecutor = Executors.newSingleThreadExecutor();

        exerciseType = getIntent().getStringExtra(EXTRA_EXERCISE_TYPE);
        if (exerciseType == null)
            exerciseType = "bicep_curl";
        isVideoMode = getIntent().getBooleanExtra(EXTRA_VIDEO_MODE, false);

        setupTTS();
        setupExerciseDetector();
        setupUI();

        if (!isVideoMode) {
            setupCamera();
        }
    }

    private void setupTTS() {
        tts = new TextToSpeech(this, status -> {
            if (status == TextToSpeech.SUCCESS) {
                tts.setLanguage(Locale.US);
                tts.setSpeechRate(1.0f);
                isTtsReady = true;
            }
        });
    }

    private void speakFeedback(String feedback, boolean isError) {
        if (!isTtsReady || !isDetecting.get())
            return;

        // Only speak errors
        if (!isError) {
            consecutiveErrorCount = 0;
            return;
        }

        consecutiveErrorCount++;

        // Wait for multiple consecutive errors before speaking
        if (consecutiveErrorCount < MIN_ERRORS_BEFORE_SPEAK)
            return;

        long currentTime = System.currentTimeMillis();

        // Debounce: don't repeat same feedback too quickly
        if (feedback.equals(lastSpokenFeedback) && currentTime - lastSpeakTime < SPEAK_COOLDOWN_MS) {
            return;
        }

        // Reset counter after speaking
        consecutiveErrorCount = 0;
        lastSpokenFeedback = feedback;
        lastSpeakTime = currentTime;

        tts.speak(feedback, TextToSpeech.QUEUE_FLUSH, null, "feedback");
    }

    private void setupExerciseDetector() {
        switch (exerciseType) {
            case "bicep_curl":
                exerciseDetector = new BicepCurlDetector(this);
                break;
            case "squat":
                exerciseDetector = new SquatDetector(this);
                break;
            case "lunge":
                exerciseDetector = new LungeDetector(this);
                break;
            case "plank":
                exerciseDetector = new PlankDetector(this);
                break;
            default:
                exerciseDetector = new BicepCurlDetector(this);
                break;
        }

        binding.exerciseNameText.setText(exerciseDetector.getExerciseName());
    }

    private void setupUI() {
        binding.btnBack.setOnClickListener(v -> {
            // Ensure detection stops before finishing
            isDetecting.set(false);
            finish();
        });

        binding.btnToggle.setOnClickListener(v -> {
            if (isDetecting.get()) {
                // Stop detection
                isDetecting.set(false);
                binding.btnToggle.setText(isVideoMode ? "Process Video" : "Start");
                if (isVideoMode)
                    binding.btnToggle.setEnabled(false); // Wait for cleanup

                // Reset UI if camera mode (video mode resets in finally block)
                if (!isVideoMode) {
                    exerciseDetector.reset();
                    consecutiveErrorCount = 0;
                    updateUI(new ExerciseDetector.DetectionResult(
                            true,
                            "Press Start to begin",
                            0,
                            ""));
                }
            } else {
                // Start detection
                if (isVideoMode) {
                    if (videoUri != null) {
                        processVideo();
                    } else {
                        Toast.makeText(this, "Please select a video first", Toast.LENGTH_SHORT).show();
                    }
                } else {
                    isDetecting.set(true);
                    sessionStartTime = System.currentTimeMillis();
                    sessionErrors = 0;
                    binding.btnToggle.setText("Stop");
                }
            }
        });

        binding.btnFlipCamera.setOnClickListener(v -> {
            if (isVideoMode) {
                // In video mode, flip button opens video picker
                videoPicker.launch("video/*");
            } else {
                isFrontCamera = !isFrontCamera;
                ListenableFuture<ProcessCameraProvider> cameraProviderFuture = ProcessCameraProvider.getInstance(this);
                cameraProviderFuture.addListener(() -> {
                    try {
                        ProcessCameraProvider cameraProvider = cameraProviderFuture.get();
                        bindCameraUseCases(cameraProvider);
                    } catch (Exception e) {
                        Log.e(TAG, "Camera provider error", e);
                    }
                }, ContextCompat.getMainExecutor(this));
            }
        });

        // Long press on flip button to switch to video mode
        binding.btnFlipCamera.setOnLongClickListener(v -> {
            if (!isVideoMode) {
                // Switch TO Video Mode
                isVideoMode = true;
                binding.btnFlipCamera.setText("📁");
                Toast.makeText(this, "Video mode enabled. Tap to select video.", Toast.LENGTH_SHORT).show();
            } else {
                // Switch TO Camera Mode
                isVideoMode = false;
                videoUri = null;
                binding.btnFlipCamera.setText("🔄"); // Reset icon
                binding.btnToggle.setText("Start");

                // Reset visibility
                binding.cameraPreview.setVisibility(View.VISIBLE);
                binding.videoFrameView.setVisibility(View.GONE);

                setupCamera();
                Toast.makeText(this, "Camera mode enabled.", Toast.LENGTH_SHORT).show();
            }
            return true;
        });

        updateUI(new ExerciseDetector.DetectionResult(
                true,
                "Press Start to begin",
                0,
                ""));
    }

    private void processVideo() {
        if (videoUri == null)
            return;

        isDetecting.set(true);
        binding.btnToggle.setText("Stop");
        binding.btnToggle.setEnabled(true); // Allow stopping

        // Hide camera, show video frame view
        binding.cameraPreview.setVisibility(View.GONE);
        binding.videoFrameView.setVisibility(View.VISIBLE);

        // Unbind camera to release resources and turn off light
        ListenableFuture<ProcessCameraProvider> cameraProviderFuture = ProcessCameraProvider.getInstance(this);
        cameraProviderFuture.addListener(() -> {
            try {
                cameraProviderFuture.get().unbindAll();
            } catch (Exception e) {
                Log.e(TAG, "Failed to unbind camera", e);
            }
        }, ContextCompat.getMainExecutor(this));

        videoExecutor.execute(() -> {
            PoseLandmarkerHelper videoLandmarkerHelper = null;
            MediaMetadataRetriever retriever = new MediaMetadataRetriever();

            try {
                // Create VIDEO mode helper with GPU acceleration
                videoLandmarkerHelper = new PoseLandmarkerHelper(
                        this,
                        RunningMode.VIDEO,
                        0.5f,
                        0.5f,
                        0.5f,
                        Delegate.GPU, // Try GPU first, auto-fallback to CPU
                        null);

                retriever.setDataSource(this, videoUri);
                String durationStr = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION);
                long duration = durationStr != null ? Long.parseLong(durationStr) : 0L;

                // Real-time synchronization
                long videoStartTime = System.currentTimeMillis();
                int frameCount = 0;
                PoseLandmarkerResult[] lastPoseResult = new PoseLandmarkerResult[1];

                // Performance optimization: Display every frame, detect every 2nd frame
                int detectionInterval = 2; // Detect every 2nd frame (15 FPS detection for 30 FPS display)

                PoseLandmarkerHelper finalVideoHelper = videoLandmarkerHelper;

                while (isDetecting.get()) {
                    long elapsedTime = System.currentTimeMillis() - videoStartTime;
                    if (elapsedTime > duration)
                        break;

                    Bitmap bitmap = retriever.getFrameAtTime(elapsedTime * 1000, MediaMetadataRetriever.OPTION_CLOSEST);

                    if (bitmap != null) {
                        Bitmap finalBitmap = bitmap;

                        // Always display the frame for smooth 30 FPS playback
                        mainHandler.post(() -> {
                            if (!isDetecting.get())
                                return;
                            binding.videoFrameView.setImageBitmap(finalBitmap);
                        });

                        // Only run pose detection every Nth frame for performance
                        boolean shouldDetect = (frameCount % detectionInterval == 0);

                        if (shouldDetect) {
                            MPImage mpImage = new BitmapImageBuilder(finalBitmap).build();
                            PoseLandmarkerResult result = finalVideoHelper.detectVideoFrame(mpImage, elapsedTime);

                            if (result != null) {
                                lastPoseResult[0] = result;
                            }
                        }

                        // Update UI with latest pose detection result (may be from previous frame)
                        if (lastPoseResult[0] != null) {
                            PoseLandmarkerResult poseResult = lastPoseResult[0];
                            int finalFrameWidth = finalBitmap.getWidth();
                            int finalFrameHeight = finalBitmap.getHeight();

                            mainHandler.post(() -> {
                                if (!isDetecting.get())
                                    return;

                                binding.overlayView.setResults(
                                        poseResult,
                                        finalFrameWidth,
                                        finalFrameHeight,
                                        false);

                                ExerciseDetector.DetectionResult detectionResult = exerciseDetector.detect(poseResult);
                                updateUI(detectionResult);
                                binding.overlayView.setFeedbackColor(detectionResult.isCorrect());
                            });
                        }

                        frameCount++;
                    }

                    // Small yield for responsiveness
                    try {
                        Thread.sleep(5);
                    } catch (InterruptedException e) {
                        break;
                    }
                }

            } catch (Exception e) {
                Log.e(TAG, "Error processing video", e);
                mainHandler.post(() -> {
                    if (!isFinishing()) {
                        Toast.makeText(this, "Error: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                    }
                });
            } finally {
                // Cleanup resources safely
                try {
                    retriever.release();
                    if (videoLandmarkerHelper != null) {
                        videoLandmarkerHelper.clearPoseLandmarker();
                    }
                } catch (Exception e) {
                    Log.e(TAG, "Error releasing resources", e);
                }

                mainHandler.post(() -> {
                    if (!isFinishing()) {
                        isDetecting.set(false);
                        binding.btnToggle.setText("Process Video");
                        binding.btnToggle.setEnabled(true);

                        // If user cancelled and not in video mode anymore, restore camera
                        if (!isVideoMode) {
                            restoreCamera();
                        }
                    }
                });
            }
        });
    }

    private void restoreCamera() {
        binding.cameraPreview.setVisibility(View.VISIBLE);
        binding.videoFrameView.setVisibility(View.GONE);
        setupCamera();
    }

    private void setupCamera() {
        cameraExecutor = Executors.newSingleThreadExecutor();

        poseLandmarkerHelper = new PoseLandmarkerHelper(
                this,
                RunningMode.LIVE_STREAM,
                0.5f,
                0.5f,
                0.5f,
                Delegate.GPU, // GPU acceleration with auto-fallback to CPU
                this);

        ListenableFuture<ProcessCameraProvider> cameraProviderFuture = ProcessCameraProvider.getInstance(this);

        cameraProviderFuture.addListener(() -> {
            try {
                ProcessCameraProvider cameraProvider = cameraProviderFuture.get();
                bindCameraUseCases(cameraProvider);
            } catch (Exception e) {
                Log.e(TAG, "Camera provider error", e);
            }
        }, ContextCompat.getMainExecutor(this));
    }

    private void bindCameraUseCases(ProcessCameraProvider cameraProvider) {
        int lensFacing = isFrontCamera ? CameraSelector.LENS_FACING_FRONT : CameraSelector.LENS_FACING_BACK;
        CameraSelector cameraSelector = new CameraSelector.Builder()
                .requireLensFacing(lensFacing)
                .build();

        Preview preview = new Preview.Builder()
                .setTargetRotation(binding.cameraPreview.getDisplay().getRotation())
                .build();
        preview.setSurfaceProvider(binding.cameraPreview.getSurfaceProvider());

        ImageAnalysis imageAnalyzer = new ImageAnalysis.Builder()
                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                .setOutputImageFormat(ImageAnalysis.OUTPUT_IMAGE_FORMAT_RGBA_8888)
                .setTargetRotation(binding.cameraPreview.getDisplay().getRotation())
                .build();

        imageAnalyzer.setAnalyzer(cameraExecutor, imageProxy -> {
            if (isDetecting.get() && poseLandmarkerHelper.isReady()) {
                Bitmap bitmap = imageProxy.toBitmap();

                // Rotate bitmap based on image rotation degrees
                int rotationDegrees = imageProxy.getImageInfo().getRotationDegrees();
                Bitmap rotatedBitmap;
                if (rotationDegrees != 0) {
                    Matrix matrix = new Matrix();
                    matrix.postRotate(rotationDegrees);
                    rotatedBitmap = Bitmap.createBitmap(
                            bitmap, 0, 0, bitmap.getWidth(), bitmap.getHeight(), matrix, true);
                } else {
                    rotatedBitmap = bitmap;
                }

                MPImage mpImage = new BitmapImageBuilder(rotatedBitmap).build();

                poseLandmarkerHelper.detectLiveStream(
                        mpImage,
                        imageProxy.getImageInfo().getTimestamp());
            }
            imageProxy.close();
        });

        try {
            cameraProvider.unbindAll();
            cameraProvider.bindToLifecycle(
                    this,
                    cameraSelector,
                    preview,
                    imageAnalyzer);
        } catch (Exception e) {
            Log.e(TAG, "Use case binding failed", e);
        }
    }

    @Override
    public void onResults(PoseLandmarkerResult result, MPImage input) {
        if (!isDetecting.get())
            return;

        runOnUiThread(() -> {
            // Update overlay
            binding.overlayView.setResults(
                    result,
                    input.getWidth(),
                    input.getHeight(),
                    isFrontCamera);

            // Run exercise detection
            ExerciseDetector.DetectionResult detectionResult = exerciseDetector.detect(result);

            // Update UI
            updateUI(detectionResult);

            // Update overlay color
            binding.overlayView.setFeedbackColor(detectionResult.isCorrect());
        });
    }

    @Override
    public void onError(String error) {
        runOnUiThread(() -> Toast.makeText(this, error, Toast.LENGTH_SHORT).show());
    }

    private void updateUI(ExerciseDetector.DetectionResult result) {
        // Update rep counter
        if (exerciseType.equals("plank")) {
            binding.repCounterText.setText("Hold: " + result.getRepCount() + "s");
        } else {
            binding.repCounterText.setText(getString(R.string.reps, result.getRepCount()));
        }

        // Update feedback
        binding.feedbackText.setText(result.getFeedback());
        binding.feedbackText.setTextColor(
                ContextCompat.getColor(
                        this,
                        result.isCorrect() ? R.color.correct_green : R.color.error_red));

        // Speak feedback for errors (optimized)
        speakFeedback(result.getFeedback(), !result.isCorrect());

        // Track errors for session stats
        if (!result.isCorrect()) {
            sessionErrors++;
        }
    }

    /**
     * Saves the completed workout session to Firestore.
     */
    private void saveWorkoutSession() {
        if (sessionStartTime == 0)
            return;

        int durationSeconds = (int) ((System.currentTimeMillis() - sessionStartTime) / 1000);
        int repCount = exerciseDetector != null ? exerciseDetector.getRepCount() : 0;

        // Only save if session was meaningful (at least 30 seconds or 1 rep)
        if (durationSeconds < 30 && repCount == 0) {
            return;
        }

        // Estimate calories burned (rough estimate based on exercise intensity)
        int caloriesEstimate = (int) (durationSeconds * 0.15); // ~9 cal/min

        // Save to workout history
        UserRepository.getInstance().saveWorkoutSession(
                exerciseType,
                repCount,
                durationSeconds,
                sessionErrors,
                null);

        // Also update daily activity aggregates
        UserRepository.getInstance().recordWorkoutToDaily(durationSeconds, caloriesEstimate);

        Log.d(TAG, "Workout saved: " + exerciseType + ", reps=" + repCount + ", duration=" + durationSeconds + "s");
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();

        // Save workout if there was an active session
        if (isDetecting.get() && sessionStartTime > 0) {
            saveWorkoutSession();
        }
        isDetecting.set(false);

        if (cameraExecutor != null) {
            cameraExecutor.shutdown();
        }
        if (videoExecutor != null) {
            videoExecutor.shutdown();
        }
        if (poseLandmarkerHelper != null) {
            poseLandmarkerHelper.clearPoseLandmarker();
        }
        if (exerciseDetector != null) {
            exerciseDetector.close();
        }
        if (tts != null) {
            tts.stop();
            tts.shutdown();
        }
    }
}
