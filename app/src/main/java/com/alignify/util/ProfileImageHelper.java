package com.alignify.util;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.provider.MediaStore;
import android.util.Log;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;

/**
 * Helper class for managing profile images stored locally on the device.
 * Provides a centralized way to save, load, and delete profile images.
 */
public class ProfileImageHelper {

    private static final String TAG = "ProfileImageHelper";
    private static final String PROFILE_IMAGE_FILENAME = "profile_image.jpg";
    private static final int MAX_IMAGE_SIZE = 500; // Max dimension in pixels
    private static final int COMPRESSION_QUALITY = 85;

    /**
     * Get the profile image file.
     */
    public static File getProfileImageFile(Context context) {
        return new File(context.getFilesDir(), PROFILE_IMAGE_FILENAME);
    }

    /**
     * Check if a profile image exists locally.
     */
    public static boolean hasProfileImage(Context context) {
        File file = getProfileImageFile(context);
        return file.exists() && file.length() > 0;
    }

    /**
     * Get the local file path for the profile image (for use with Glide).
     */
    public static String getProfileImagePath(Context context) {
        File file = getProfileImageFile(context);
        if (file.exists()) {
            return file.getAbsolutePath();
        }
        return null;
    }

    /**
     * Save a profile image from a URI (gallery or camera).
     * Compresses and resizes the image for efficient storage.
     *
     * @param context  The application context
     * @param imageUri The URI of the image to save
     * @return true if saved successfully, false otherwise
     */
    public static boolean saveProfileImage(Context context, Uri imageUri) {
        try {
            // Load bitmap from URI
            Bitmap originalBitmap = MediaStore.Images.Media.getBitmap(
                    context.getContentResolver(), imageUri);

            if (originalBitmap == null) {
                Log.e(TAG, "Failed to load bitmap from URI");
                return false;
            }

            // Resize if necessary
            Bitmap resizedBitmap = resizeBitmap(originalBitmap, MAX_IMAGE_SIZE);

            // Save to internal storage
            File outputFile = getProfileImageFile(context);
            try (FileOutputStream fos = new FileOutputStream(outputFile)) {
                resizedBitmap.compress(Bitmap.CompressFormat.JPEG, COMPRESSION_QUALITY, fos);
                fos.flush();
            }

            // Recycle bitmaps to free memory
            if (resizedBitmap != originalBitmap) {
                originalBitmap.recycle();
            }

            Log.d(TAG, "Profile image saved: " + outputFile.getAbsolutePath());
            return true;

        } catch (IOException e) {
            Log.e(TAG, "Error saving profile image", e);
            return false;
        }
    }

    /**
     * Save a profile image from a Bitmap.
     */
    public static boolean saveProfileImage(Context context, Bitmap bitmap) {
        try {
            Bitmap resizedBitmap = resizeBitmap(bitmap, MAX_IMAGE_SIZE);

            File outputFile = getProfileImageFile(context);
            try (FileOutputStream fos = new FileOutputStream(outputFile)) {
                resizedBitmap.compress(Bitmap.CompressFormat.JPEG, COMPRESSION_QUALITY, fos);
                fos.flush();
            }

            if (resizedBitmap != bitmap) {
                resizedBitmap.recycle();
            }

            Log.d(TAG, "Profile image saved from bitmap");
            return true;

        } catch (IOException e) {
            Log.e(TAG, "Error saving profile image", e);
            return false;
        }
    }

    /**
     * Load the profile image as a Bitmap.
     */
    public static Bitmap loadProfileImage(Context context) {
        File file = getProfileImageFile(context);
        if (file.exists()) {
            return BitmapFactory.decodeFile(file.getAbsolutePath());
        }
        return null;
    }

    /**
     * Delete the local profile image.
     */
    public static boolean deleteProfileImage(Context context) {
        File file = getProfileImageFile(context);
        if (file.exists()) {
            boolean deleted = file.delete();
            Log.d(TAG, "Profile image deleted: " + deleted);
            return deleted;
        }
        return true;
    }

    /**
     * Resize bitmap to fit within maxSize while maintaining aspect ratio.
     */
    private static Bitmap resizeBitmap(Bitmap original, int maxSize) {
        int width = original.getWidth();
        int height = original.getHeight();

        if (width <= maxSize && height <= maxSize) {
            return original;
        }

        float ratio = (float) width / height;
        int newWidth, newHeight;

        if (width > height) {
            newWidth = maxSize;
            newHeight = (int) (maxSize / ratio);
        } else {
            newHeight = maxSize;
            newWidth = (int) (maxSize * ratio);
        }

        return Bitmap.createScaledBitmap(original, newWidth, newHeight, true);
    }
}
