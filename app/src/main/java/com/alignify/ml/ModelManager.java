package com.alignify.ml;

import android.app.AlertDialog;
import android.content.Context;
import android.content.SharedPreferences;
import android.util.Log;

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
 * Manages ML model versioning and caching.
 * Models are loaded from bundled assets.
 * Remote update functionality has been removed (offline-only mode).
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
    private final SharedPreferences prefs;
    private final File modelsDir;

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
     * Check for model updates.
     * In offline mode, no remote updates are available.
     */
    public void checkForUpdates(Context activityContext, UpdateCheckCallback callback) {
        // No remote update checks in offline mode
        callback.onNoUpdates();
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

        // Use bundled asset - return null to signal use asset loader
        callback.onModelReady(null);
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
            // Use try-with-resources to prevent file descriptor leaks
            try (FileInputStream fis = new FileInputStream(cachedModel);
                 FileChannel channel = fis.getChannel()) {
                return channel.map(FileChannel.MapMode.READ_ONLY, 0, channel.size());
            }
        }

        // Load from assets (fallback)
        return null; // Let caller handle asset loading
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
     * Always returns false in offline mode.
     */
    public boolean hasUpdate(String modelName) {
        return false;
    }

    /**
     * Get all model names.
     */
    public static String[] getAllModelNames() {
        return new String[] { MODEL_SQUAT, MODEL_PLANK, MODEL_LUNGE, MODEL_BICEP };
    }
}
