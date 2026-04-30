package com.foodrescue.app.firebase;

import android.content.Context;
import android.text.TextUtils;
import android.util.Log;

import androidx.annotation.NonNull;

import com.google.android.gms.tasks.Tasks;
import com.google.android.gms.tasks.TaskCompletionSource;
import com.foodrescue.app.model.Listing;
import com.foodrescue.app.model.User;
import com.google.android.gms.tasks.Task;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.FirebaseApp;
import com.google.firebase.firestore.EventListener;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.ListenerRegistration;
import com.google.firebase.firestore.QuerySnapshot;

import java.util.Locale;
import java.util.Map;

public class FirebaseDatabaseService {
    private static final String TAG = "FirebaseDatabaseService";
    private final FirebaseFirestore db;
    private final boolean firebaseConfigured;
    private final String configurationErrorMessage;

    public FirebaseDatabaseService(@NonNull Context context) {
        FirebaseFirestore localDb = null;
        boolean configured = false;
        String errorMessage = "Firebase is not configured. Add app/google-services.json.";
        try {
            configured = !FirebaseApp.getApps(context).isEmpty();
            if (configured) {
                localDb = FirebaseFirestore.getInstance();
                errorMessage = "";
            }
        } catch (Exception e) {
            errorMessage = "Firebase setup error: " + e.getMessage();
        }
        db = localDb;
        firebaseConfigured = configured;
        configurationErrorMessage = errorMessage;
    }

    public Task<Void> saveListing(Listing listing) {
        if (!firebaseConfigured || db == null) {
            return Tasks.forException(new IllegalStateException(configurationErrorMessage));
        }
        return db.collection("listings")
                .document(listing.getId())
                .set(listing);
    }

    public Task<Void> deleteListing(String listingId) {
        if (!firebaseConfigured || db == null) {
            return Tasks.forException(new IllegalStateException(configurationErrorMessage));
        }
        return db.collection("listings")
                .document(listingId)
                .delete();
    }

    public Task<Void> saveUserProfile(User user) {
        if (!firebaseConfigured || db == null) {
            return Tasks.forException(new IllegalStateException(configurationErrorMessage));
        }
        return db.collection("users")
                .document(user.getEmail())
                .set(user);
    }

    public Task<User> getUserProfile(String email) {
        if (!firebaseConfigured || db == null) {
            return Tasks.forException(new IllegalStateException(configurationErrorMessage));
        }

        TaskCompletionSource<User> taskCompletionSource = new TaskCompletionSource<>();
        db.collection("users")
                .document(email)
                .get()
                .addOnSuccessListener(documentSnapshot -> {
                    User user = toUser(documentSnapshot);
                    if (user != null) {
                        taskCompletionSource.setResult(user);
                        return;
                    }
                    findUserProfileByEmailFallback(email, taskCompletionSource);
                })
                .addOnFailureListener(taskCompletionSource::setException);
        return taskCompletionSource.getTask();
    }

    public Task<QuerySnapshot> getAllListings() {
        if (!firebaseConfigured || db == null) {
            return Tasks.forException(new IllegalStateException(configurationErrorMessage));
        }
        return db.collection("listings").get();
    }

    public ListenerRegistration listenForListings(EventListener<QuerySnapshot> listener) {
        if (!firebaseConfigured || db == null) {
            return null;
        }
        return db.collection("listings").addSnapshotListener(listener);
    }

    private User toUser(DocumentSnapshot documentSnapshot) {
        if (documentSnapshot == null || !documentSnapshot.exists()) {
            return null;
        }

        try {
            User mappedUser = documentSnapshot.toObject(User.class);
            if (isUsableUser(mappedUser)) {
                normalizeUser(mappedUser, documentSnapshot.getId(), null);
                return mappedUser;
            }
        } catch (RuntimeException e) {
            Log.w(TAG, "Legacy Firestore profile needs manual mapping for document " + documentSnapshot.getId(), e);
        }

        return buildUserFromLegacyDocument(documentSnapshot);
    }

    private void findUserProfileByEmailFallback(String email, TaskCompletionSource<User> taskCompletionSource) {
        db.collection("users")
                .get()
                .addOnSuccessListener(querySnapshot -> {
                    for (DocumentSnapshot snapshot : querySnapshot.getDocuments()) {
                        User user = toUser(snapshot);
                        if (user != null
                                && user.getEmail() != null
                                && user.getEmail().equalsIgnoreCase(email)) {
                            taskCompletionSource.setResult(user);
                            return;
                        }
                    }
                    taskCompletionSource.setResult(null);
                })
                .addOnFailureListener(taskCompletionSource::setException);
    }

    private User buildUserFromLegacyDocument(DocumentSnapshot documentSnapshot) {
        Map<String, Object> data = documentSnapshot.getData();
        if (data == null || data.isEmpty()) {
            return null;
        }

        User user = new User();
        normalizeUser(
                user,
                firstNonBlank(
                        asTrimmedString(data.get("email")),
                        asTrimmedString(data.get("userEmail")),
                        documentSnapshot.getId()),
                data);

        if (!isUsableUser(user)) {
            return null;
        }
        return user;
    }

    private void normalizeUser(User user, String fallbackEmail, Map<String, Object> data) {
        if (user == null) {
            return;
        }

        user.setEmail(normalizeEmail(firstNonBlank(user.getEmail(), fallbackEmail)));
        user.setName(firstNonBlank(user.getName(), getField(data, "name", "fullName", "ownerName"), ""));
        user.setPassword(firstNonBlank(user.getPassword(), getField(data, "password", "passcode"), ""));
        user.setPhone(firstNonBlank(user.getPhone(), getField(data, "phone", "phoneNumber", "mobile"), ""));
        user.setAddress(firstNonBlank(user.getAddress(), getField(data, "address", "location"), ""));
        user.setBusinessName(firstNonBlank(user.getBusinessName(), getField(data, "businessName", "shopName", "storeName"), ""));
        user.setRole(normalizeRole(firstNonBlank(user.getRole(), getField(data, "role", "userType"), "Customer")));
    }

    private boolean isUsableUser(User user) {
        return user != null && !TextUtils.isEmpty(normalizeEmail(user.getEmail()));
    }

    private String getField(Map<String, Object> data, String... keys) {
        if (data == null || keys == null) {
            return null;
        }
        for (String key : keys) {
            String value = asTrimmedString(data.get(key));
            if (!TextUtils.isEmpty(value)) {
                return value;
            }
        }
        return null;
    }

    private String asTrimmedString(Object value) {
        if (value == null) {
            return null;
        }
        String text = String.valueOf(value).trim();
        return text.isEmpty() ? null : text;
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

    private String normalizeEmail(String email) {
        return email == null ? "" : email.trim().toLowerCase(Locale.ROOT);
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
}
