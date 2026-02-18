package com.foodrescue.app.view;

import android.content.Intent;
import android.content.pm.PackageManager;
import android.location.Location;
import android.os.Bundle;
import android.view.Menu;
import android.view.MenuItem;
import android.widget.SearchView;
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
import com.foodrescue.app.utils.LocationHelper;
import com.foodrescue.app.utils.NotificationHelper;
import com.google.gson.Gson;
import com.google.firebase.firestore.DocumentChange;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.ListenerRegistration;

import java.util.ArrayList;
import java.util.List;

public class ReceiverHomeActivity extends AppCompatActivity implements ListingAdapter.OnListingClickListener {

    private RecyclerView recyclerViewListings;
    private ListingAdapter listingAdapter;
    private List<Listing> allListings;
    private SharedPreferencesManager sharedPreferencesManager;
    private SearchView searchViewListings;
    private Toolbar toolbar; // Declare Toolbar
    private FirebaseDatabaseService firebaseDatabaseService;
    private ListenerRegistration listingsListener;
    private Location lastKnownLocation;

    private static final int REQUEST_LOCATION = 2001;
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
        allListings = new ArrayList<>();
        listingAdapter = new ListingAdapter(allListings, this);
        recyclerViewListings.setAdapter(listingAdapter);

        searchViewListings = findViewById(R.id.searchViewListings);
        searchViewListings.setOnQueryTextListener(new SearchView.OnQueryTextListener() {
            @Override
            public boolean onQueryTextSubmit(String query) {
                return false;
            }

            @Override
            public boolean onQueryTextChange(String newText) {
                listingAdapter.getFilter().filter(newText);
                return false;
            }
        });
    }

    @Override
    protected void onResume() {
        super.onResume();
        loadAllListings();
        ensureLocationAndStartListener();
    }

    private void loadAllListings() {
        firebaseDatabaseService.getAllListings()
                .addOnSuccessListener(querySnapshot -> {
                    allListings.clear();
                    for (DocumentSnapshot doc : querySnapshot.getDocuments()) {
                        Listing listing = doc.toObject(Listing.class);
                        if (listing != null) {
                            allListings.add(listing);
                            if (listing.getId() != null) {
                                sharedPreferencesManager.markListingNotified(listing.getId());
                            }
                        }
                    }
                    listingAdapter.updateListings(allListings);
                })
                .addOnFailureListener(e -> {
                    allListings.clear();
                    allListings.addAll(sharedPreferencesManager.getAllListings());
                    listingAdapter.updateListings(allListings);
                    Toast.makeText(this, "Using local listings (offline)", Toast.LENGTH_SHORT).show();
                });
    }

    private void ensureLocationAndStartListener() {
        if (!LocationHelper.hasLocationPermission(this)) {
            LocationHelper.requestLocationPermission(this, REQUEST_LOCATION);
            return;
        }
        LocationHelper.fetchLastLocation(this, location -> {
            lastKnownLocation = location;
            startListingsListenerIfNeeded();
        });
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
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == REQUEST_LOCATION) {
            boolean granted = false;
            if (grantResults.length > 0) {
                for (int result : grantResults) {
                    if (result == PackageManager.PERMISSION_GRANTED) {
                        granted = true;
                        break;
                    }
                }
            }
            if (granted) {
                ensureLocationAndStartListener();
            } else {
                Toast.makeText(this, "Location permission is required for nearby alerts", Toast.LENGTH_SHORT).show();
            }
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
        } else if (id == R.id.action_map) {
            startActivity(new Intent(this, MapActivity.class));
            return true;
        }
        return super.onOptionsItemSelected(item);
    }
}
