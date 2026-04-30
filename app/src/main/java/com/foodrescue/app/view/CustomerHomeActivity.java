package com.foodrescue.app.view;

import android.content.Intent;
import android.location.Location;
import android.os.Bundle;
import android.text.SpannableString;
import android.text.Spanned;
import android.text.style.ForegroundColorSpan;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar; // Import Toolbar
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.foodrescue.app.R;
import com.foodrescue.app.adapters.ListingAdapter;
import com.foodrescue.app.data.SharedPreferencesManager;
import com.foodrescue.app.firebase.FirebaseDatabaseService;
import com.foodrescue.app.model.Listing;
import com.foodrescue.app.utils.LocationHelper;
import com.foodrescue.app.utils.NotificationHelper;
import com.google.gson.Gson;
import com.google.firebase.firestore.DocumentChange;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.ListenerRegistration;

import java.util.ArrayList;
import java.util.List;

public class CustomerHomeActivity extends AppCompatActivity implements ListingAdapter.OnListingClickListener {

    private RecyclerView recyclerViewListings;
    private ListingAdapter listingAdapter;
    private List<Listing> allListings;
    private List<Listing> sourceListings;
    private SharedPreferencesManager sharedPreferencesManager;
    private TextView textViewEmptyState;
    private Toolbar toolbar; // Declare Toolbar
    private FirebaseDatabaseService firebaseDatabaseService;
    private ListenerRegistration listingsListener;
    private Location lastKnownLocation;
    private static final int LOCATION_PERMISSION_REQUEST_CODE = 401;

    private static final double SIMULATED_USER_LATITUDE = 28.6139;
    private static final double SIMULATED_USER_LONGITUDE = 77.2090;
    private static final double GEOFENCE_RADIUS_KM = 5.0;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_receiver_home);

        toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        applyBrandedToolbarTitle();

        sharedPreferencesManager = new SharedPreferencesManager(this);
        firebaseDatabaseService = new FirebaseDatabaseService(this);

        NotificationHelper.createNotificationChannel(this);

        recyclerViewListings = findViewById(R.id.recyclerViewListings);
        recyclerViewListings.setLayoutManager(new LinearLayoutManager(this));
        textViewEmptyState = findViewById(R.id.textViewEmptyState);
        allListings = new ArrayList<>();
        sourceListings = new ArrayList<>();
        listingAdapter = new ListingAdapter(allListings, this);
        recyclerViewListings.setAdapter(listingAdapter);
    }

    @Override
    protected void onResume() {
        super.onResume();
        loadAllListings();
        initializeStoredLocationAndStartListener();
    }

    private void loadAllListings() {
        firebaseDatabaseService.getAllListings()
                .addOnSuccessListener(querySnapshot -> {
                    List<Listing> mergedListings = new ArrayList<>();

                    for (Listing listing : sharedPreferencesManager.getAllListings()) {
                        addOrReplaceListing(mergedListings, listing, false);
                    }

                    for (DocumentSnapshot doc : querySnapshot.getDocuments()) {
                        Listing listing = doc.toObject(Listing.class);
                        addOrReplaceListing(mergedListings, listing, true);
                    }
                    applyListingVisibilityFilter(mergedListings);
                })
                .addOnFailureListener(e -> {
                    List<Listing> mergedListings = new ArrayList<>();
                    for (Listing listing : sharedPreferencesManager.getAllListings()) {
                        addOrReplaceListing(mergedListings, listing, false);
                    }
                    applyListingVisibilityFilter(mergedListings);
                    Toast.makeText(this, "Using local listings (offline)", Toast.LENGTH_SHORT).show();
                });
    }

    private void initializeStoredLocationAndStartListener() {
        String loggedInEmail = sharedPreferencesManager.getLoggedInUserEmail();
        if (loggedInEmail != null) {
            double[] savedLocation = sharedPreferencesManager.getUserLocation(loggedInEmail);
            if (savedLocation != null && savedLocation.length >= 2) {
                Location location = new Location("stored");
                location.setLatitude(savedLocation[0]);
                location.setLongitude(savedLocation[1]);
                lastKnownLocation = location;
                startListingsListenerIfNeeded();
                applyListingVisibilityFilter(sourceListings);
                return;
            }
        }

        if (!LocationHelper.hasLocationPermission(this)) {
            LocationHelper.requestLocationPermission(this, LOCATION_PERMISSION_REQUEST_CODE);
            fallbackToSimulatedLocation();
            applyListingVisibilityFilter(sourceListings);
            return;
        }

        LocationHelper.fetchLastLocation(this, location -> runOnUiThread(() -> {
            if (location != null) {
                lastKnownLocation = location;
                if (loggedInEmail != null) {
                    sharedPreferencesManager.saveUserLocation(loggedInEmail, location.getLatitude(), location.getLongitude());
                }
            } else {
                fallbackToSimulatedLocation();
            }
            startListingsListenerIfNeeded();
            applyListingVisibilityFilter(sourceListings);
        }));
    }

    private void fallbackToSimulatedLocation() {
        Location location = new Location("simulated");
        location.setLatitude(SIMULATED_USER_LATITUDE);
        location.setLongitude(SIMULATED_USER_LONGITUDE);
        lastKnownLocation = location;
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
                if (isWithinRadius(lastKnownLocation, listing, GEOFENCE_RADIUS_KM)) {
                    applyListingVisibilityFilterWithSingleUpdate(listing);
                    if (sharedPreferencesManager.isListingNotified(listing.getId())) {
                        return;
                    }
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
        return hasValidCoordinates(listing) && results[0] <= radiusKm * 1000.0;
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
        Intent intent = new Intent(CustomerHomeActivity.this, ListingDetailsActivity.class);
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
        if (id == R.id.action_orders) {
            startActivity(new Intent(this, MyOrdersActivity.class));
            return true;
        }
        if (id == R.id.action_map) {
            startActivity(new Intent(this, MapActivity.class));
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

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == LOCATION_PERMISSION_REQUEST_CODE) {
            initializeStoredLocationAndStartListener();
        }
    }

    private void addOrReplaceListing(List<Listing> target, Listing listing, boolean preferIncoming) {
        if (listing == null || !isListingValid(listing)) {
            return;
        }

        String id = listing.getId();
        if (id == null) {
            target.add(listing);
            return;
        }

        for (int i = 0; i < target.size(); i++) {
            Listing existing = target.get(i);
            if (existing != null && id.equals(existing.getId())) {
                if (preferIncoming) {
                    target.set(i, listing);
                }
                return;
            }
        }
        target.add(listing);
    }

    private void applyListingVisibilityFilter(List<Listing> listingsSource) {
        sourceListings.clear();
        sourceListings.addAll(listingsSource);

        List<Listing> visibleListings = new ArrayList<>();
        for (Listing listing : sourceListings) {
            if (shouldDisplayListing(listing)) {
                visibleListings.add(listing);
            }
        }
        allListings.clear();
        allListings.addAll(visibleListings);
        listingAdapter.updateListings(allListings);
        updateEmptyState();
    }

    private void applyListingVisibilityFilterWithSingleUpdate(Listing incomingListing) {
        if (!shouldDisplayListing(incomingListing)) {
            return;
        }

        List<Listing> updatedSourceListings = new ArrayList<>(sourceListings);
        addOrReplaceListing(updatedSourceListings, incomingListing, true);
        applyListingVisibilityFilter(updatedSourceListings);
    }

    private boolean shouldDisplayListing(Listing listing) {
        if (!isListingValid(listing)) {
            return false;
        }
        if (lastKnownLocation == null) {
            return true;
        }
        return isWithinRadius(lastKnownLocation, listing, GEOFENCE_RADIUS_KM);
    }

    private boolean hasValidCoordinates(Listing listing) {
        if (listing == null) {
            return false;
        }
        double latitude = listing.getLatitude();
        double longitude = listing.getLongitude();
        return !Double.isNaN(latitude)
                && !Double.isNaN(longitude)
                && latitude >= -90
                && latitude <= 90
                && longitude >= -180
                && longitude <= 180
                && !(latitude == 0d && longitude == 0d);
    }

    private void applyBrandedToolbarTitle() {
        SpannableString brandedTitle = new SpannableString("HungerHunt");
        brandedTitle.setSpan(
                new ForegroundColorSpan(ContextCompat.getColor(this, R.color.white)),
                0,
                "Hunger".length(),
                Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
        );
        brandedTitle.setSpan(
                new ForegroundColorSpan(ContextCompat.getColor(this, R.color.orange_accent)),
                "Hunger".length(),
                "HungerHunt".length(),
                Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
        );
        toolbar.setTitle(brandedTitle);
    }
}
