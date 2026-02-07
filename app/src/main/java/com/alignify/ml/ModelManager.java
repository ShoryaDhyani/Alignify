package com.alignify.ml;

import android.app.AlertDialog;
import android.content.Context;
import android.content.SharedPreferences;
import android.util.Log;

import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.storage.FirebaseStorage;
import com.google.firebase.storage.StorageReference;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.MappedByteBuffer;
import java.nio.channels.FileChannel;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Manages ML model downloads, versioning, and caching.
 * Downloads models from Firebase Storage on-demand and caches locally.
 * Falls back to bundled assets if download fails.
 */
public class ModelManager {
    private static final String TAG = "ModelManager";
    private static final String PREFS_NAME = "ModelVersions";
    private static final String MODELS_DIR = "models";

    // Model names
    public static final String MODEL_SQUAT = "squat_model";
    public static final String MODEL_PLANK = "plank_model";
    public static final String MODEL_LUNGE = "lunge_model";
    public static final String MODEL_BICEP = "bicep_model";

    private static ModelManager instance;
    private final Context context;
    private final FirebaseFirestore firestore;
    private final FirebaseStorage storage;
    private final SharedPreferences prefs;
    private final File modelsDir;

    // Track available updates
    private final Map<String, ModelInfo> availableUpdates = new HashMap<>();

    public interface ModelCallback {
        void onModelReady(File modelFile);

        void onError(String error);
    }

    public interface UpdateCheckCallback {
        void onUpdatesAvailable(List<ModelInfo> updates);

        void onNoUpdates();

        void onError(String error);
    }

    public static class ModelInfo {
        public String name;
        public int remoteVersion;
        public int localVersion;
        public String storagePath;

        public ModelInfo(String name, int remoteVersion, int localVersion, String storagePath) {
            this.name = name;
            this.remoteVersion = remoteVersion;
            this.localVersion = localVersion;
            this.storagePath = storagePath;
        }
    }

    private ModelManager(Context context) {
        this.context = context.getApplicationContext();
        this.firestore = FirebaseFirestore.getInstance();
        this.storage = FirebaseStorage.getInstance();
        this.prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        this.modelsDir = new File(context.getFilesDir(), MODELS_DIR);

        if (!modelsDir.exists()) {
            modelsDir.mkdirs();
        }
    }

    public static synchronized ModelManager getInstance(Context context) {
        if (instance == null) {
            instance = new ModelManager(context);
        }
        return instance;
    }

    /**
     * Check for model updates and notify via callback.
     * Shows dialog to user if updates are available.
     */
    public void checkForUpdates(Context activityContext, UpdateCheckCallback callback) {
        firestore.collection("model_versions")
                .get()
                .addOnSuccessListener(querySnapshot -> {
                    availableUpdates.clear();
                    List<ModelInfo> updates = new ArrayList<>();

                    for (DocumentSnapshot doc : querySnapshot) {
                        String modelName = doc.getId();
                        Long remoteVersion = doc.getLong("version");
                        String storagePath = doc.getString("path");

                        if (remoteVersion != null && storagePath != null) {
                            int localVersion = getLocalVersion(modelName);

                            if (remoteVersion > localVersion) {
                                ModelInfo info = new ModelInfo(modelName, remoteVersion.intValue(),
                                        localVersion, storagePath);
                                updates.add(info);
                                availableUpdates.put(modelName, info);
                            }
                        }
                    }

                    if (!updates.isEmpty()) {
                        // Show update dialog to user
                        showUpdateDialog(activityContext, updates, callback);
                    } else {
                        callback.onNoUpdates();
                    }
                })
                .addOnFailureListener(e -> {
                    Log.e(TAG, "Error checking for updates", e);
                    callback.onError(e.getMessage());
                });
    }

    /**
     * Show dialog to user about available model updates.
     */
    private void showUpdateDialog(Context activityContext, List<ModelInfo> updates,
            UpdateCheckCallback callback) {
        StringBuilder message = new StringBuilder();
        message.append("New AI model updates are available:\n\n");

        for (ModelInfo info : updates) {
            String displayName = formatModelName(info.name);
            message.append("• ").append(displayName)
                    .append(" (v").append(info.localVersion)
                    .append(" → v").append(info.remoteVersion).append(")\n");
        }

        message.append("\nDownload now for improved accuracy?");

        new AlertDialog.Builder(activityContext)
                .setTitle("Model Updates Available")
                .setMessage(message.toString())
                .setPositiveButton("Download", (dialog, which) -> {
                    downloadAllUpdates(updates, callback);
                })
                .setNegativeButton("Later", (dialog, which) -> {
                    callback.onUpdatesAvailable(updates);
                })
                .setIcon(android.R.drawable.ic_dialog_info)
                .show();
    }

    /**
     * Download all pending model updates.
     */
    private void downloadAllUpdates(List<ModelInfo> updates, UpdateCheckCallback callback) {
        final int[] completed = { 0 };
        final int total = updates.size();

        for (ModelInfo info : updates) {
            downloadModel(info.name, info.storagePath, info.remoteVersion, new ModelCallback() {
                @Override
                public void onModelReady(File modelFile) {
                    completed[0]++;
                    if (completed[0] == total) {
                        callback.onNoUpdates(); // All updated
                    }
                }

                @Override
                public void onError(String error) {
                    Log.e(TAG, "Error downloading " + info.name + ": " + error);
                    completed[0]++;
                    if (completed[0] == total) {
                        callback.onError("Some models failed to download");
                    }
                }
            });
        }
    }

    /**
     * Get model file for the given model name.
     * Returns cached file if available, otherwise falls back to assets.
     */
    public void getModel(String modelName, ModelCallback callback) {
        File cachedModel = new File(modelsDir, modelName + ".tflite");

        if (cachedModel.exists()) {
            callback.onModelReady(cachedModel);
            return;
        }

        // Check if update is available
        if (availableUpdates.containsKey(modelName)) {
            ModelInfo info = availableUpdates.get(modelName);
            downloadModel(modelName, info.storagePath, info.remoteVersion, callback);
        } else {
            // Use bundled asset - return null to signal use asset loader
            callback.onModelReady(null);
        }
    }

    /**
     * Get model file synchronously. Returns null if should use asset.
     */
    public File getModelFileSync(String modelName) {
        File cachedModel = new File(modelsDir, modelName + ".tflite");
        return cachedModel.exists() ? cachedModel : null;
    }

    /**
     * Load model as MappedByteBuffer from cache or assets.
     */
    public MappedByteBuffer loadModel(String modelName) throws IOException {
        File cachedModel = getModelFileSync(modelName);

        if (cachedModel != null) {
            // Load from cached file
            FileInputStream fis = new FileInputStream(cachedModel);
            FileChannel channel = fis.getChannel();
            return channel.map(FileChannel.MapMode.READ_ONLY, 0, channel.size());
        }

        // Load from assets (fallback)
        return null; // Let caller handle asset loading
    }

    /**
     * Download a model from Firebase Storage.
     */
    private void downloadModel(String modelName, String storagePath, int version,
            ModelCallback callback) {
        StorageReference modelRef = storage.getReference().child(storagePath);
        File localFile = new File(modelsDir, modelName + ".tflite");

        modelRef.getFile(localFile)
                .addOnSuccessListener(taskSnapshot -> {
                    // Save version
                    prefs.edit().putInt(modelName, version).apply();
                    availableUpdates.remove(modelName);
                    Log.d(TAG, "Downloaded " + modelName + " v" + version);
                    callback.onModelReady(localFile);
                })
                .addOnFailureListener(e -> {
                    Log.e(TAG, "Failed to download " + modelName, e);
                    callback.onError(e.getMessage());
                });
    }

    /**
     * Get locally stored version number for a model.
     */
    public int getLocalVersion(String modelName) {
        return prefs.getInt(modelName, 0);
    }

    /**
     * Format model name for display.
     */
    private String formatModelName(String modelName) {
        switch (modelName) {
            case MODEL_SQUAT:
                return "Squat Detector";
            case MODEL_PLANK:
                return "Plank Detector";
            case MODEL_LUNGE:
                return "Lunge Detector";
            case MODEL_BICEP:
                return "Bicep Curl Detector";
            default:
                return modelName.replace("_", " ");
        }
    }

    /**
     * Check if a model has an update available.
     */
    public boolean hasUpdate(String modelName) {
        return availableUpdates.containsKey(modelName);
    }

    /**
     * Get all model names.
     */
    public static String[] getAllModelNames() {
        return new String[] { MODEL_SQUAT, MODEL_PLANK, MODEL_LUNGE, MODEL_BICEP };
    }
}
