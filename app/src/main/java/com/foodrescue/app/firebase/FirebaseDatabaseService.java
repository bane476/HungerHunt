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
import com.google.firebase.firestore.Query;
import com.google.firebase.firestore.QuerySnapshot;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

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
        return getUserProfile(email, null, null);
    }

    public Task<User> getUserProfile(String email, String exactEmail, String uid) {
        if (!firebaseConfigured || db == null) {
            return Tasks.forException(new IllegalStateException(configurationErrorMessage));
        }

        TaskCompletionSource<User> taskCompletionSource = new TaskCompletionSource<>();
        List<String> docIds = buildLookupCandidates(email, exactEmail, uid);
        List<String> emailCandidates = buildLookupCandidates(email, exactEmail, null);
        fetchUserByDocumentId(docIds, 0, emailCandidates, taskCompletionSource);
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

    public Task<Boolean> hasListingsForUser(String email) {
        if (!firebaseConfigured || db == null) {
            return Tasks.forException(new IllegalStateException(configurationErrorMessage));
        }

        TaskCompletionSource<Boolean> taskCompletionSource = new TaskCompletionSource<>();
        db.collection("listings")
                .whereEqualTo("donorEmail", normalizeEmail(email))
                .limit(1)
                .get()
                .addOnSuccessListener(querySnapshot -> taskCompletionSource.setResult(!querySnapshot.isEmpty()))
                .addOnFailureListener(taskCompletionSource::setException);
        return taskCompletionSource.getTask();
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

    private void fetchUserByDocumentId(
            List<String> docIds,
            int index,
            List<String> emailCandidates,
            TaskCompletionSource<User> taskCompletionSource
    ) {
        if (index >= docIds.size()) {
            findUserProfileByEmailFallback(emailCandidates, 0, taskCompletionSource);
            return;
        }

        String docId = docIds.get(index);
        db.collection("users")
                .document(docId)
                .get()
                .addOnSuccessListener(documentSnapshot -> {
                    User user = toUser(documentSnapshot);
                    if (user != null) {
                        taskCompletionSource.setResult(user);
                        return;
                    }
                    fetchUserByDocumentId(docIds, index + 1, emailCandidates, taskCompletionSource);
                })
                .addOnFailureListener(e -> {
                    Log.w(TAG, "User profile document lookup failed for key " + docId, e);
                    fetchUserByDocumentId(docIds, index + 1, emailCandidates, taskCompletionSource);
                });
    }

    private void findUserProfileByEmailFallback(
            List<String> emailCandidates,
            int index,
            TaskCompletionSource<User> taskCompletionSource
    ) {
        if (index >= emailCandidates.size()) {
            taskCompletionSource.setResult(null);
            return;
        }

        String email = emailCandidates.get(index);
        Query query = db.collection("users").whereEqualTo("email", email).limit(1);
        query.get()
                .addOnSuccessListener(querySnapshot -> {
                    for (DocumentSnapshot snapshot : querySnapshot.getDocuments()) {
                        User user = toUser(snapshot);
                        if (user != null) {
                            taskCompletionSource.setResult(user);
                            return;
                        }
                    }
                    findUserProfileByEmailFallback(emailCandidates, index + 1, taskCompletionSource);
                })
                .addOnFailureListener(e -> {
                    Log.w(TAG, "User profile email lookup failed for " + email, e);
                    findUserProfileByEmailFallback(emailCandidates, index + 1, taskCompletionSource);
                });
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

    private List<String> buildLookupCandidates(String first, String second, String third) {
        Set<String> uniqueValues = new LinkedHashSet<>();
        addLookupValue(uniqueValues, first);
        addLookupValue(uniqueValues, normalizeEmail(first));
        addLookupValue(uniqueValues, second);
        addLookupValue(uniqueValues, normalizeEmail(second));
        addLookupValue(uniqueValues, third);
        return new ArrayList<>(uniqueValues);
    }

    private void addLookupValue(Set<String> target, String value) {
        if (!TextUtils.isEmpty(value)) {
            target.add(value.trim());
        }
    }
}
