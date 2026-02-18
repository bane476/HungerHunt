package com.foodrescue.app.view;

import android.content.Intent;
import android.location.Location;
import android.os.Bundle;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar; // Import Toolbar
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.foodrescue.app.R;
import com.foodrescue.app.adapters.ListingAdapter;
import com.foodrescue.app.data.SharedPreferencesManager;
import com.foodrescue.app.firebase.FirebaseDatabaseService;
import com.foodrescue.app.model.Listing;
import com.foodrescue.app.utils.NotificationHelper;
import com.google.gson.Gson;
import com.google.firebase.firestore.DocumentChange;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.ListenerRegistration;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class ReceiverHomeActivity extends AppCompatActivity implements ListingAdapter.OnListingClickListener {

    private RecyclerView recyclerViewListings;
    private ListingAdapter listingAdapter;
    private List<Listing> allListings;
    private SharedPreferencesManager sharedPreferencesManager;
    private TextView textViewEmptyState;
    private Toolbar toolbar; // Declare Toolbar
    private FirebaseDatabaseService firebaseDatabaseService;
    private ListenerRegistration listingsListener;
    private Location lastKnownLocation;

    private static final double SIMULATED_USER_LATITUDE = 28.6139;
    private static final double SIMULATED_USER_LONGITUDE = 77.2090;
    private static final double NOTIFICATION_RADIUS_KM = 5.0;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_receiver_home);

        toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);

        sharedPreferencesManager = new SharedPreferencesManager(this);
        firebaseDatabaseService = new FirebaseDatabaseService(this);

        NotificationHelper.createNotificationChannel(this);

        recyclerViewListings = findViewById(R.id.recyclerViewListings);
        recyclerViewListings.setLayoutManager(new LinearLayoutManager(this));
        textViewEmptyState = findViewById(R.id.textViewEmptyState);
        allListings = new ArrayList<>();
        listingAdapter = new ListingAdapter(allListings, this);
        recyclerViewListings.setAdapter(listingAdapter);
    }

    @Override
    protected void onResume() {
        super.onResume();
        loadAllListings();
        initializeSimulatedLocationAndStartListener();
    }

    private void loadAllListings() {
        firebaseDatabaseService.getAllListings()
                .addOnSuccessListener(querySnapshot -> {
                    List<Listing> mergedListings = new ArrayList<>();
                    Set<String> seenIds = new HashSet<>();

                    for (Listing listing : sharedPreferencesManager.getAllListings()) {
                        if (listing != null && isListingValid(listing)) {
                            mergedListings.add(listing);
                            if (listing.getId() != null) {
                                seenIds.add(listing.getId());
                            }
                        }
                    }

                    for (DocumentSnapshot doc : querySnapshot.getDocuments()) {
                        Listing listing = doc.toObject(Listing.class);
                        if (listing != null && isListingValid(listing)) {
                            String id = listing.getId();
                            if (id == null || !seenIds.contains(id)) {
                                mergedListings.add(listing);
                            }
                            if (id != null) {
                                seenIds.add(id);
                            }
                            if (listing.getId() != null) {
                                sharedPreferencesManager.markListingNotified(listing.getId());
                            }
                        }
                    }
                    allListings.clear();
                    allListings.addAll(mergedListings);
                    listingAdapter.updateListings(allListings);
                    updateEmptyState();
                })
                .addOnFailureListener(e -> {
                    allListings.clear();
                    for (Listing listing : sharedPreferencesManager.getAllListings()) {
                        if (isListingValid(listing)) {
                            allListings.add(listing);
                        }
                    }
                    listingAdapter.updateListings(allListings);
                    updateEmptyState();
                    Toast.makeText(this, "Using local listings (offline)", Toast.LENGTH_SHORT).show();
                });
    }

    private void initializeSimulatedLocationAndStartListener() {
        Location location = new Location("simulated");
        location.setLatitude(SIMULATED_USER_LATITUDE);
        location.setLongitude(SIMULATED_USER_LONGITUDE);
        lastKnownLocation = location;
        startListingsListenerIfNeeded();
    }

    private void startListingsListenerIfNeeded() {
        if (listingsListener != null) {
            return;
        }
        listingsListener = firebaseDatabaseService.listenForListings((snapshots, error) -> {
            if (error != null || snapshots == null) {
                return;
            }
            for (DocumentChange change : snapshots.getDocumentChanges()) {
                if (change.getType() != DocumentChange.Type.ADDED) {
                    continue;
                }
                Listing listing = change.getDocument().toObject(Listing.class);
                if (listing == null || listing.getId() == null) {
                    continue;
                }
                if (!isListingValid(listing)) {
                    continue;
                }
                if (sharedPreferencesManager.isListingNotified(listing.getId())) {
                    continue;
                }
                if (lastKnownLocation == null) {
                    continue;
                }
                if (isWithinRadius(lastKnownLocation, listing, NOTIFICATION_RADIUS_KM)) {
                    String title = "New Food Nearby";
                    String message = listing.getTitle() + " is available within 5 km.";
                    NotificationHelper.sendNotification(this, title, message, listing.getId().hashCode());
                    sharedPreferencesManager.markListingNotified(listing.getId());
                }
            }
        });
    }

    private boolean isWithinRadius(Location userLocation, Listing listing, double radiusKm) {
        float[] results = new float[1];
        Location.distanceBetween(
                userLocation.getLatitude(),
                userLocation.getLongitude(),
                listing.getLatitude(),
                listing.getLongitude(),
                results
        );
        return results[0] <= radiusKm * 1000.0;
    }

    @Override
    protected void onStop() {
        super.onStop();
        if (listingsListener != null) {
            listingsListener.remove();
            listingsListener = null;
        }
    }

    @Override
    public void onListingClick(Listing listing) {
        Intent intent = new Intent(ReceiverHomeActivity.this, ListingDetailsActivity.class);
        Gson gson = new Gson();
        String listingJson = gson.toJson(listing);
        intent.putExtra("listing", listingJson);
        startActivity(intent);
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.main_menu, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(@NonNull MenuItem item) {
        int id = item.getItemId();
        if (id == R.id.action_profile) {
            startActivity(new Intent(this, ProfileActivity.class));
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    private boolean isListingValid(Listing listing) {
        return listing != null
                && getAvailableQuantity(listing) > 0
                && !listing.isClaimed()
                && !"COMPLETED".equalsIgnoreCase(listing.getOrderStatus());
    }

    private int getAvailableQuantity(Listing listing) {
        if (listing == null || listing.getQuantity() == null) {
            return 0;
        }
        try {
            return Math.max(0, Integer.parseInt(listing.getQuantity()));
        } catch (NumberFormatException e) {
            String digits = listing.getQuantity().replaceAll("[^0-9]", "");
            if (digits.isEmpty()) {
                return 0;
            }
            try {
                return Math.max(0, Integer.parseInt(digits));
            } catch (NumberFormatException ignored) {
                return 0;
            }
        }
    }

    private void updateEmptyState() {
        if (allListings.isEmpty()) {
            textViewEmptyState.setVisibility(View.VISIBLE);
            recyclerViewListings.setVisibility(View.GONE);
        } else {
            textViewEmptyState.setVisibility(View.GONE);
            recyclerViewListings.setVisibility(View.VISIBLE);
        }
    }
}
