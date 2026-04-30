package com.foodrescue.app.repository;

import android.content.Context;
import android.text.TextUtils;
import android.util.Log;

import androidx.annotation.NonNull;

import com.foodrescue.app.data.SharedPreferencesManager;
import com.foodrescue.app.firebase.FirebaseAuthService;
import com.foodrescue.app.firebase.FirebaseDatabaseService;
import com.foodrescue.app.model.User;

import java.util.Locale;

public class AuthRepository {
    private static final String TAG = "AuthRepository";

    public interface AuthResultCallback {
        void onSuccess(User user, String message);
        void onError(String message);
    }

    private final SharedPreferencesManager sharedPreferencesManager;
    private final FirebaseAuthService firebaseAuthService;
    private final FirebaseDatabaseService firebaseDatabaseService;

    public AuthRepository(@NonNull Context context) {
        sharedPreferencesManager = new SharedPreferencesManager(context);
        firebaseAuthService = new FirebaseAuthService(context);
        firebaseDatabaseService = new FirebaseDatabaseService(context);
    }

    public void registerUser(@NonNull User user, @NonNull AuthResultCallback callback) {
        String normalizedEmail = normalizeEmail(user.getEmail());
        user.setEmail(normalizedEmail);

        User existingUser = sharedPreferencesManager.getUser(normalizedEmail);
        if (existingUser != null) {
            String existingRole = normalizeRole(existingUser.getRole());
            String requestedRole = normalizeRole(user.getRole());
            if (!existingRole.equals(requestedRole)) {
                callback.onError("Email is already registered as " + existingRole + ". Use a different email.");
                return;
            }
            callback.onError("This email is already registered.");
            return;
        }

        if (!firebaseAuthService.isFirebaseConfigured()) {
            sharedPreferencesManager.saveUser(user);
            sharedPreferencesManager.saveLoggedInUserEmail(normalizedEmail);
            callback.onSuccess(user, "Registered locally. Add google-services.json to enable Firebase Auth.");
            return;
        }

        firebaseAuthService.registerUser(normalizedEmail, user.getPassword(), new FirebaseAuthService.AuthCallback() {
            @Override
            public void onSuccess() {
                sharedPreferencesManager.saveUser(user);
                sharedPreferencesManager.saveLoggedInUserEmail(normalizedEmail);
                callback.onSuccess(user, "Registration successful");
                syncUserProfileToCloud(user, 0);
            }

            @Override
            public void onError(String message) {
                callback.onError(message);
            }
        });
    }

    public void loginUser(@NonNull String email, @NonNull String password, @NonNull AuthResultCallback callback) {
        String normalizedEmail = normalizeEmail(email);

        if (!firebaseAuthService.isFirebaseConfigured()) {
            User localUser = sharedPreferencesManager.getUser(normalizedEmail);
            if (localUser != null && password.equals(localUser.getPassword())) {
                sharedPreferencesManager.saveLoggedInUserEmail(normalizedEmail);
                callback.onSuccess(localUser, "Logged in locally. Add google-services.json to enable Firebase Auth.");
                return;
            }
            callback.onError("Invalid email or password");
            return;
        }

        firebaseAuthService.loginUser(normalizedEmail, password, new FirebaseAuthService.AuthCallback() {
            @Override
            public void onSuccess() {
                User localUser = sharedPreferencesManager.getUser(normalizedEmail);
                if (localUser != null) {
                    sharedPreferencesManager.saveLoggedInUserEmail(normalizedEmail);
                    callback.onSuccess(localUser, "Login successful");
                    return;
                }

                firebaseDatabaseService.getUserProfile(normalizedEmail)
                        .addOnSuccessListener(restoredUser -> {
                            if (restoredUser == null) {
                                firebaseAuthService.signOut();
                                callback.onError("This account exists in Firebase Auth, but its profile is missing in Firestore.");
                                return;
                            }
                            restoredUser.setEmail(normalizeEmail(firstNonBlank(restoredUser.getEmail(), normalizedEmail)));
                            if (TextUtils.isEmpty(restoredUser.getPassword())) {
                                restoredUser.setPassword(password);
                            }
                            restoredUser.setRole(normalizeRole(restoredUser.getRole()));
                            sharedPreferencesManager.saveUser(restoredUser);
                            sharedPreferencesManager.saveLoggedInUserEmail(restoredUser.getEmail());
                            syncUserProfileToCloud(restoredUser, 0);
                            callback.onSuccess(restoredUser, "Login successful");
                        })
                        .addOnFailureListener(e -> {
                            firebaseAuthService.signOut();
                            callback.onError("Login succeeded, but Firestore profile restore failed. Check Firebase rules/network and try again.");
                        });
            }

            @Override
            public void onError(String message) {
                callback.onError(message);
            }
        });
    }

    public void logoutUser() {
        firebaseAuthService.signOut();
        sharedPreferencesManager.saveLoggedInUserEmail(null);
    }

    public void saveUserProfile(@NonNull User user, @NonNull AuthResultCallback callback) {
        sharedPreferencesManager.saveUser(user);

        if (!firebaseAuthService.isFirebaseConfigured()) {
            callback.onSuccess(user, "Profile updated successfully!");
            return;
        }

        firebaseDatabaseService.saveUserProfile(user)
                .addOnSuccessListener(unused -> callback.onSuccess(user, "Profile updated successfully!"))
                .addOnFailureListener(e -> callback.onSuccess(user, "Profile updated locally, but cloud sync failed."));
    }

    private String normalizeRole(String role) {
        if ("Donor".equalsIgnoreCase(role)
                || "Provider".equalsIgnoreCase(role)
                || "Business".equalsIgnoreCase(role)) {
            return "Business";
        }
        return "Customer";
    }

    private String normalizeEmail(String email) {
        return email == null ? "" : email.trim().toLowerCase(Locale.ROOT);
    }

    private String firstNonBlank(String... values) {
        if (values == null) {
            return null;
        }
        for (String value : values) {
            if (!TextUtils.isEmpty(value)) {
                return value.trim();
            }
        }
        return null;
    }

    private void syncUserProfileToCloud(@NonNull User user, int attempt) {
        firebaseDatabaseService.saveUserProfile(user)
                .addOnFailureListener(e -> {
                    if (attempt < 1) {
                        syncUserProfileToCloud(user, attempt + 1);
                    } else {
                        Log.e(TAG, "Cloud profile sync failed after registration", e);
                    }
                });
    }
}
