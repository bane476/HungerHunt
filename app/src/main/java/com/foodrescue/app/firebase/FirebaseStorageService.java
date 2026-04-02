package com.foodrescue.app.firebase;

import android.content.ContentResolver;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;

import androidx.annotation.NonNull;

import com.google.android.gms.tasks.Task;
import com.google.android.gms.tasks.Tasks;
import com.google.firebase.FirebaseApp;
import com.google.firebase.storage.FirebaseStorage;
import com.google.firebase.storage.StorageMetadata;
import com.google.firebase.storage.StorageReference;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;

public class FirebaseStorageService {
    private static final int MAX_IMAGE_DIMENSION = 1600;
    private static final int JPEG_QUALITY = 80;

    private final Context appContext;
    private final FirebaseStorage storage;
    private final boolean firebaseConfigured;
    private final String configurationErrorMessage;

    public FirebaseStorageService(@NonNull Context context) {
        appContext = context.getApplicationContext();
        FirebaseStorage localStorage = null;
        boolean configured = false;
        String errorMessage = "Firebase is not configured. Add app/google-services.json.";
        try {
            configured = !FirebaseApp.getApps(context).isEmpty();
            if (configured) {
                localStorage = FirebaseStorage.getInstance();
                errorMessage = "";
            }
        } catch (Exception e) {
            errorMessage = "Firebase setup error: " + e.getMessage();
        }
        storage = localStorage;
        firebaseConfigured = configured;
        configurationErrorMessage = errorMessage;
    }

    public boolean isFirebaseConfigured() {
        return firebaseConfigured;
    }

    public Task<String> uploadListingImage(@NonNull String listingId, @NonNull Uri imageUri) {
        if (!firebaseConfigured || storage == null) {
            return Tasks.forException(new IllegalStateException(configurationErrorMessage));
        }

        byte[] compressedBytes;
        try {
            compressedBytes = compressImage(imageUri);
        } catch (IOException e) {
            return Tasks.forException(e);
        }

        StorageReference reference = getListingImageReference(listingId);
        StorageMetadata metadata = new StorageMetadata.Builder()
                .setContentType("image/jpeg")
                .build();

        return reference.putBytes(compressedBytes, metadata)
                .continueWithTask(task -> {
                    if (!task.isSuccessful()) {
                        Exception exception = task.getException();
                        if (exception != null) {
                            throw exception;
                        }
                        throw new IllegalStateException("Listing image upload failed.");
                    }
                    return reference.getDownloadUrl();
                })
                .continueWith(task -> {
                    Uri downloadUri = task.getResult();
                    return downloadUri != null ? downloadUri.toString() : null;
                });
    }

    public Task<Void> deleteListingImage(@NonNull String listingId) {
        if (!firebaseConfigured || storage == null) {
            return Tasks.forException(new IllegalStateException(configurationErrorMessage));
        }
        return getListingImageReference(listingId).delete();
    }

    public Task<Void> deleteImageByUrl(String imageUrl) {
        if (!firebaseConfigured || storage == null || imageUrl == null || imageUrl.trim().isEmpty()) {
            return Tasks.forResult(null);
        }
        try {
            return storage.getReferenceFromUrl(imageUrl).delete();
        } catch (IllegalArgumentException e) {
            return Tasks.forResult(null);
        }
    }

    public boolean isRemoteStorageUrl(String imageUri) {
        return imageUri != null
                && (imageUri.startsWith("https://firebasestorage.googleapis.com/")
                || imageUri.startsWith("gs://"));
    }

    private StorageReference getListingImageReference(String listingId) {
        return storage.getReference()
                .child("listing-images")
                .child(listingId + ".jpg");
    }

    private byte[] compressImage(Uri imageUri) throws IOException {
        ContentResolver contentResolver = appContext.getContentResolver();
        Bitmap decodedBitmap;
        try (InputStream inputStream = contentResolver.openInputStream(imageUri)) {
            if (inputStream == null) {
                throw new IOException("Unable to open selected image.");
            }
            decodedBitmap = BitmapFactory.decodeStream(inputStream);
        }

        if (decodedBitmap == null) {
            throw new IOException("Selected file is not a valid image.");
        }

        Bitmap scaledBitmap = scaleBitmapIfNeeded(decodedBitmap);
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        scaledBitmap.compress(Bitmap.CompressFormat.JPEG, JPEG_QUALITY, outputStream);

        if (scaledBitmap != decodedBitmap) {
            scaledBitmap.recycle();
        }
        decodedBitmap.recycle();

        return outputStream.toByteArray();
    }

    private Bitmap scaleBitmapIfNeeded(Bitmap bitmap) {
        int width = bitmap.getWidth();
        int height = bitmap.getHeight();
        int largestSide = Math.max(width, height);
        if (largestSide <= MAX_IMAGE_DIMENSION) {
            return bitmap;
        }

        float scale = (float) MAX_IMAGE_DIMENSION / largestSide;
        int scaledWidth = Math.round(width * scale);
        int scaledHeight = Math.round(height * scale);
        return Bitmap.createScaledBitmap(bitmap, scaledWidth, scaledHeight, true);
    }
}
