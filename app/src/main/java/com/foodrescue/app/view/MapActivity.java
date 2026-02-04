package com.foodrescue.app.view;

import android.content.Intent;
import android.os.Bundle;
import androidx.appcompat.app.AppCompatActivity;

import com.foodrescue.app.R;
import com.foodrescue.app.data.SharedPreferencesManager;
import com.foodrescue.app.model.Listing;
import com.google.android.gms.maps.CameraUpdateFactory;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.OnMapReadyCallback;
import com.google.android.gms.maps.SupportMapFragment;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.Marker;
import com.google.android.gms.maps.model.MarkerOptions;
import com.google.gson.Gson;

import java.util.List;

public class MapActivity extends AppCompatActivity implements OnMapReadyCallback, GoogleMap.OnMarkerClickListener {

    private GoogleMap mMap;
    private SharedPreferencesManager sharedPreferencesManager;
    private List<Listing> listings;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_map);

        sharedPreferencesManager = new SharedPreferencesManager(this);
        listings = sharedPreferencesManager.getAllListings();

        SupportMapFragment mapFragment = (SupportMapFragment) getSupportFragmentManager()
                .findFragmentById(R.id.map);
        mapFragment.getMapAsync(this);
    }

    @Override
    public void onMapReady(GoogleMap googleMap) {
        mMap = googleMap;
        mMap.setOnMarkerClickListener(this);

        // Add markers for all listings
        for (Listing listing : listings) {
            LatLng location = new LatLng(listing.getLatitude(), listing.getLongitude());
            Marker marker = mMap.addMarker(new MarkerOptions().position(location).title(listing.getTitle()));
            if (marker != null) {
                marker.setTag(listing); // Store the Listing object in the marker's tag
            }
        }

        // Move camera to a default location (e.g., first listing or a central point)
        if (!listings.isEmpty()) {
            LatLng firstListingLocation = new LatLng(listings.get(0).getLatitude(), listings.get(0).getLongitude());
            mMap.moveCamera(CameraUpdateFactory.newLatLngZoom(firstListingLocation, 10));
        }
    }

    @Override
    public boolean onMarkerClick(Marker marker) {
        // Handle marker click to show listing details
        Listing listing = (Listing) marker.getTag();
        if (listing != null) {
            Intent intent = new Intent(MapActivity.this, ListingDetailsActivity.class);
            Gson gson = new Gson();
            String listingJson = gson.toJson(listing);
            intent.putExtra("listing", listingJson);
            startActivity(intent);
        }
        return true; // Consume the event so that the default behavior does not occur
    }
}
