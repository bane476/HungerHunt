package com.foodrescue.app.firebase;

import android.content.Context;

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

public class FirebaseDatabaseService {
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
                .addOnSuccessListener(documentSnapshot -> taskCompletionSource.setResult(toUser(documentSnapshot)))
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
        return documentSnapshot.toObject(User.class);
    }
}
