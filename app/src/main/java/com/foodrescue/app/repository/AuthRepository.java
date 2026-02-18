package com.foodrescue.app.repository;

import android.content.Context;

import androidx.annotation.NonNull;

import com.foodrescue.app.data.SharedPreferencesManager;
import com.foodrescue.app.firebase.FirebaseAuthService;
import com.foodrescue.app.model.User;

public class AuthRepository {
    public interface AuthResultCallback {
        void onSuccess(User user, String message);
        void onError(String message);
    }

    private final SharedPreferencesManager sharedPreferencesManager;
    private final FirebaseAuthService firebaseAuthService;

    public AuthRepository(@NonNull Context context) {
        sharedPreferencesManager = new SharedPreferencesManager(context);
        firebaseAuthService = new FirebaseAuthService(context);
    }

    public void registerUser(@NonNull User user, @NonNull AuthResultCallback callback) {
        User existingUser = sharedPreferencesManager.getUser(user.getEmail());
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
            sharedPreferencesManager.saveLoggedInUserEmail(user.getEmail());
            callback.onSuccess(user, "Registered locally. Add google-services.json to enable Firebase Auth.");
            return;
        }

        firebaseAuthService.registerUser(user.getEmail(), user.getPassword(), new FirebaseAuthService.AuthCallback() {
            @Override
            public void onSuccess() {
                sharedPreferencesManager.saveUser(user);
                sharedPreferencesManager.saveLoggedInUserEmail(user.getEmail());
                callback.onSuccess(user, "Registration successful");
            }

            @Override
            public void onError(String message) {
                callback.onError(message);
            }
        });
    }

    public void loginUser(@NonNull String email, @NonNull String password, @NonNull AuthResultCallback callback) {
        if (!firebaseAuthService.isFirebaseConfigured()) {
            User localUser = sharedPreferencesManager.getUser(email);
            if (localUser != null && password.equals(localUser.getPassword())) {
                sharedPreferencesManager.saveLoggedInUserEmail(email);
                callback.onSuccess(localUser, "Logged in locally. Add google-services.json to enable Firebase Auth.");
                return;
            }
            callback.onError("Invalid email or password");
            return;
        }

        firebaseAuthService.loginUser(email, password, new FirebaseAuthService.AuthCallback() {
            @Override
            public void onSuccess() {
                User localUser = sharedPreferencesManager.getUser(email);
                if (localUser == null) {
                    firebaseAuthService.signOut();
                    callback.onError("Profile not found for this email. Please register first.");
                    return;
                }
                sharedPreferencesManager.saveLoggedInUserEmail(email);
                callback.onSuccess(localUser, "Login successful");
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

    private String normalizeRole(String role) {
        if ("Donor".equalsIgnoreCase(role)
                || "Provider".equalsIgnoreCase(role)
                || "Business".equalsIgnoreCase(role)) {
            return "Business";
        }
        return "Customer";
    }
}
