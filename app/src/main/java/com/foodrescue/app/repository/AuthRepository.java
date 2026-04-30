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
                String authenticatedEmail = firebaseAuthService.getCurrentUserEmail();
                String authenticatedUid = firebaseAuthService.getCurrentUserUid();

                if (localUser != null) {
                    localUser.setEmail(normalizeEmail(firstNonBlank(localUser.getEmail(), authenticatedEmail, normalizedEmail)));
                    if (TextUtils.isEmpty(localUser.getPassword())) {
                        localUser.setPassword(password);
                    }
                    localUser.setRole(normalizeRole(localUser.getRole()));
                    sharedPreferencesManager.saveUser(localUser);
                    sharedPreferencesManager.saveLoggedInUserEmail(localUser.getEmail());
                    callback.onSuccess(localUser, "Login successful");
                    return;
                }

                firebaseDatabaseService.getUserProfile(normalizedEmail, authenticatedEmail, authenticatedUid)
                        .addOnSuccessListener(restoredUser -> {
                            if (restoredUser == null) {
                                recoverMissingProfile(normalizedEmail, password, callback);
                                return;
                            }
                            restoredUser.setEmail(normalizeEmail(firstNonBlank(restoredUser.getEmail(), normalizedEmail)));
                            if (TextUtils.isEmpty(restoredUser.getPassword())) {
                                restoredUser.setPassword(password);
                            }
                            restoredUser.setRole(normalizeRole(restoredUser.getRole()));
                            sharedPreferencesManager.saveUser(restoredUser);
                            sharedPreferencesManager.saveLoggedInUserEmail(restoredUser.getEmail());
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
        if ("Receiver".equalsIgnoreCase(role) || "Customer".equalsIgnoreCase(role)) {
            return "Customer";
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

    private void recoverMissingProfile(
            @NonNull String normalizedEmail,
            @NonNull String password,
            @NonNull AuthResultCallback callback
    ) {
        User cachedUser = sharedPreferencesManager.getUser(normalizedEmail);
        if (cachedUser != null) {
            cachedUser.setEmail(normalizeEmail(firstNonBlank(cachedUser.getEmail(), normalizedEmail)));
            if (TextUtils.isEmpty(cachedUser.getPassword())) {
                cachedUser.setPassword(password);
            }
            cachedUser.setRole(normalizeRole(cachedUser.getRole()));
            sharedPreferencesManager.saveUser(cachedUser);
            sharedPreferencesManager.saveLoggedInUserEmail(normalizedEmail);
            callback.onSuccess(cachedUser, "Login successful");
            return;
        }

        firebaseDatabaseService.hasListingsForUser(normalizedEmail)
                .addOnSuccessListener(hasListings -> {
                    User recoveredUser = buildRecoveredUser(normalizedEmail, password, hasListings, null);
                    sharedPreferencesManager.saveUser(recoveredUser);
                    sharedPreferencesManager.saveLoggedInUserEmail(normalizedEmail);
                    callback.onSuccess(recoveredUser, "Login successful");
                })
                .addOnFailureListener(e -> {
                    User recoveredUser = buildRecoveredUser(normalizedEmail, password, false, null);
                    sharedPreferencesManager.saveUser(recoveredUser);
                    sharedPreferencesManager.saveLoggedInUserEmail(normalizedEmail);
                    callback.onSuccess(recoveredUser, "Login successful");
                });
    }

    @NonNull
    private User buildRecoveredUser(
            @NonNull String normalizedEmail,
            @NonNull String password,
            boolean hasListings,
            User cachedUser
    ) {
        String fallbackName = buildFallbackName(normalizedEmail);
        String inferredRole = hasListings ? "Business" : "Customer";
        String role = cachedUser != null ? normalizeRole(cachedUser.getRole()) : inferredRole;
        String businessName = firstNonBlank(
                cachedUser != null ? cachedUser.getBusinessName() : null,
                "Business".equals(role) ? fallbackName : "",
                ""
        );
        String phone = cachedUser != null ? firstNonBlank(cachedUser.getPhone(), "") : "";
        String address = cachedUser != null ? firstNonBlank(cachedUser.getAddress(), "") : "";
        String name = cachedUser != null ? firstNonBlank(cachedUser.getName(), fallbackName) : fallbackName;
        if (!"Business".equals(role)) {
            businessName = "";
        }
        return new User(name, normalizedEmail, password, phone, address, businessName, role);
    }

    @NonNull
    private String buildFallbackName(@NonNull String email) {
        int atIndex = email.indexOf('@');
        String base = atIndex > 0 ? email.substring(0, atIndex) : email;
        base = base.replace('.', ' ').replace('_', ' ').trim();
        if (base.isEmpty()) {
            return "User";
        }

        String[] parts = base.split("\\s+");
        StringBuilder builder = new StringBuilder();
        for (String part : parts) {
            if (part.isEmpty()) {
                continue;
            }
            if (builder.length() > 0) {
                builder.append(' ');
            }
            builder.append(Character.toUpperCase(part.charAt(0)));
            if (part.length() > 1) {
                builder.append(part.substring(1));
            }
        }
        return builder.length() == 0 ? "User" : builder.toString();
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
