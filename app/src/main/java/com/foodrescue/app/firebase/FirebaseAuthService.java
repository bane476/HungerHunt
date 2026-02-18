package com.foodrescue.app.firebase;

import android.content.Context;
import android.util.Log;

import androidx.annotation.NonNull;

import com.google.firebase.FirebaseApp;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseAuthException;
import com.google.firebase.auth.FirebaseUser;

public class FirebaseAuthService {
    private static final String TAG = "FirebaseAuthService";

    public interface AuthCallback {
        void onSuccess();
        void onError(String message);
    }

    private FirebaseAuth firebaseAuth;
    private final boolean firebaseConfigured;
    private final String configurationErrorMessage;

    public FirebaseAuthService(@NonNull Context context) {
        boolean configured = false;
        String errorMessage = "";
        try {
            configured = !FirebaseApp.getApps(context).isEmpty();
            if (configured) {
                firebaseAuth = FirebaseAuth.getInstance();
            } else {
                errorMessage = "Firebase is not configured. Add app/google-services.json.";
            }
        } catch (Exception e) {
            errorMessage = "Firebase setup error: " + e.getMessage();
        }
        firebaseConfigured = configured;
        configurationErrorMessage = errorMessage;
    }

    public boolean isFirebaseConfigured() {
        return firebaseConfigured;
    }

    public String getConfigurationErrorMessage() {
        return configurationErrorMessage;
    }

    public void registerUser(String email, String password, AuthCallback callback) {
        if (!firebaseConfigured) {
            callback.onError(configurationErrorMessage);
            return;
        }

        firebaseAuth.createUserWithEmailAndPassword(email, password)
                .addOnCompleteListener(task -> {
                    if (task.isSuccessful()) {
                        callback.onSuccess();
                    } else {
                        String message = getAuthErrorMessage(task.getException(), false);
                        callback.onError(message);
                    }
                });
    }

    public void loginUser(String email, String password, AuthCallback callback) {
        if (!firebaseConfigured) {
            callback.onError(configurationErrorMessage);
            return;
        }

        firebaseAuth.signInWithEmailAndPassword(email, password)
                .addOnCompleteListener(task -> {
                    if (task.isSuccessful()) {
                        callback.onSuccess();
                    } else {
                        String message = getAuthErrorMessage(task.getException(), true);
                        callback.onError(message);
                    }
                });
    }

    public void signOut() {
        if (firebaseConfigured && firebaseAuth != null) {
            firebaseAuth.signOut();
        }
    }

    public String getCurrentUserEmail() {
        if (!firebaseConfigured || firebaseAuth == null) {
            return null;
        }
        FirebaseUser user = firebaseAuth.getCurrentUser();
        return user != null ? user.getEmail() : null;
    }

    private String getAuthErrorMessage(Exception exception, boolean isLogin) {
        if (exception == null) {
            return isLogin ? "Login failed. Please try again." : "Registration failed. Please try again.";
        }

        Log.e(TAG, "Firebase auth failed", exception);

        if (exception instanceof FirebaseAuthException) {
            String code = ((FirebaseAuthException) exception).getErrorCode();
            if ("ERROR_INVALID_EMAIL".equals(code)) {
                return "Invalid email format.";
            }
            if ("ERROR_USER_NOT_FOUND".equals(code) || "ERROR_WRONG_PASSWORD".equals(code) || "ERROR_INVALID_CREDENTIAL".equals(code) || "ERROR_INVALID_LOGIN_CREDENTIALS".equals(code)) {
                return "Invalid email or password.";
            }
            if ("ERROR_EMAIL_ALREADY_IN_USE".equals(code)) {
                return "This email is already registered.";
            }
            if ("ERROR_WEAK_PASSWORD".equals(code)) {
                return "Password must be at least 6 characters.";
            }
            if ("ERROR_NETWORK_REQUEST_FAILED".equals(code)) {
                return "Network error. Check your internet and try again.";
            }
            if ("ERROR_TOO_MANY_REQUESTS".equals(code)) {
                return "Too many attempts. Please wait and try again.";
            }
            if ("ERROR_OPERATION_NOT_ALLOWED".equals(code)) {
                return "Email/password sign-in is disabled in Firebase Console.";
            }
            return "Auth error (" + code + "): " + exception.getMessage();
        }

        String rawMessage = exception.getMessage();
        if (rawMessage == null || rawMessage.trim().isEmpty()) {
            return isLogin ? "Login failed. Please try again." : "Registration failed. Please try again.";
        }
        return rawMessage;
    }
}
