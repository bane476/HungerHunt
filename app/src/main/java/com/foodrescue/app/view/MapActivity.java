package com.foodrescue.app.view;

import android.content.Intent;
import android.os.Bundle;
import android.preference.PreferenceManager;

import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar; // Import Toolbar

import com.foodrescue.app.R;
import com.foodrescue.app.data.SharedPreferencesManager;
import com.foodrescue.app.model.Listing;
import com.google.gson.Gson;

import org.osmdroid.api.IMapController;
import org.osmdroid.config.Configuration;
import org.osmdroid.tileprovider.tilesource.TileSourceFactory;
import org.osmdroid.util.GeoPoint;
import org.osmdroid.views.MapView;
import org.osmdroid.views.overlay.Marker;

import java.util.List;

public class MapActivity extends AppCompatActivity {

    private MapView mapView;
    private SharedPreferencesManager sharedPreferencesManager;
    private List<Listing> listings;
    private Toolbar toolbar; // Declare Toolbar

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // Set the user agent for OSMdroid
        Configuration.getInstance().load(getApplicationContext(), PreferenceManager.getDefaultSharedPreferences(getApplicationContext()));
        Configuration.getInstance().setUserAgentValue(getPackageName());

        setContentView(R.layout.activity_map);

        toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        // Enable the Up button
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
            getSupportActionBar().setDisplayShowHomeEnabled(true);
            getSupportActionBar().setTitle("Map"); // Set toolbar title
        }

        sharedPreferencesManager = new SharedPreferencesManager(this);
        listings = sharedPreferencesManager.getAllListings();

        mapView = findViewById(R.id.map);
        mapView.setTileSource(TileSourceFactory.MAPNIK);
        mapView.setMultiTouchControls(true);

        IMapController mapController = mapView.getController();
        mapController.setZoom(10.0);

        // Add markers for all listings
        for (Listing listing : listings) {
            GeoPoint location = new GeoPoint(listing.getLatitude(), listing.getLongitude());
            Marker marker = new Marker(mapView);
            marker.setPosition(location);
            marker.setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM);
            marker.setTitle(listing.getTitle());
            marker.setRelatedObject(listing); // Store the Listing object
            marker.setOnMarkerClickListener(new Marker.OnMarkerClickListener() {
                @Override
                public boolean onMarkerClick(Marker marker, MapView mapView) {
                    Listing clickedListing = (Listing) marker.getRelatedObject();
                    if (clickedListing != null) {
                        Intent intent = new Intent(MapActivity.this, ListingDetailsActivity.class);
                        Gson gson = new Gson();
                        String listingJson = gson.toJson(clickedListing);
                        intent.putExtra("listing", listingJson);
                        startActivity(intent);
                    }
                    return true;
                }
            });
            mapView.getOverlays().add(marker);
        }

        // Move camera to a default location
        if (!listings.isEmpty()) {
            GeoPoint firstListingLocation = new GeoPoint(listings.get(0).getLatitude(), listings.get(0).getLongitude());
            mapController.setCenter(firstListingLocation);
        } else {
            // Default to a central location if no listings
            mapController.setCenter(new GeoPoint(40.7128, -74.0060)); // New York
        }
    }

    @Override
    public boolean onSupportNavigateUp() {
        onBackPressed();
        return true;
    }
}
