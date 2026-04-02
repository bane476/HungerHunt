package com.foodrescue.app.view;

import android.content.Intent;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.text.TextUtils;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar; // Import Toolbar

import com.bumptech.glide.Glide;
import com.foodrescue.app.R;
import com.foodrescue.app.data.SharedPreferencesManager;
import com.foodrescue.app.model.Listing;
import com.google.gson.Gson;
import com.google.android.material.card.MaterialCardView;

import org.osmdroid.api.IMapController;
import org.osmdroid.config.Configuration;
import org.osmdroid.tileprovider.tilesource.TileSourceFactory;
import org.osmdroid.util.GeoPoint;
import org.osmdroid.views.MapView;
import org.osmdroid.views.overlay.Marker;

import java.util.ArrayList;
import java.util.List;

public class MapActivity extends AppCompatActivity {

    private MapView mapView;
    private SharedPreferencesManager sharedPreferencesManager;
    private List<Listing> listings;
    private Toolbar toolbar; // Declare Toolbar
    private MaterialCardView cardSelectedListing;
    private TextView textViewMapSummary;
    private TextView textViewMapEmpty;
    private ImageView imageViewMapListing;
    private TextView textViewMapListingTitle;
    private TextView textViewMapListingBusiness;
    private TextView textViewMapListingMeta;
    private TextView textViewMapListingWindow;
    private TextView textViewMapListingDescription;
    private Button buttonOpenMapListing;
    private Listing selectedListing;

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
        listings = getValidListings(sharedPreferencesManager.getAllListings());

        mapView = findViewById(R.id.map);
        cardSelectedListing = findViewById(R.id.cardSelectedListing);
        textViewMapSummary = findViewById(R.id.textViewMapSummary);
        textViewMapEmpty = findViewById(R.id.textViewMapEmpty);
        imageViewMapListing = findViewById(R.id.imageViewMapListing);
        textViewMapListingTitle = findViewById(R.id.textViewMapListingTitle);
        textViewMapListingBusiness = findViewById(R.id.textViewMapListingBusiness);
        textViewMapListingMeta = findViewById(R.id.textViewMapListingMeta);
        textViewMapListingWindow = findViewById(R.id.textViewMapListingWindow);
        textViewMapListingDescription = findViewById(R.id.textViewMapListingDescription);
        buttonOpenMapListing = findViewById(R.id.buttonOpenMapListing);

        mapView.setTileSource(TileSourceFactory.MAPNIK);
        mapView.setMultiTouchControls(true);
        mapView.setBuiltInZoomControls(false);

        IMapController mapController = mapView.getController();
        mapController.setZoom(12.0);

        textViewMapSummary.setText(listings.size() + (listings.size() == 1 ? " listing on map" : " listings on map"));
        textViewMapEmpty.setVisibility(listings.isEmpty() ? android.view.View.VISIBLE : android.view.View.GONE);

        for (Listing listing : listings) {
            GeoPoint location = new GeoPoint(listing.getLatitude(), listing.getLongitude());
            Marker marker = new Marker(mapView);
            marker.setPosition(location);
            marker.setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM);
            marker.setTitle(listing.getTitle());
            marker.setSubDescription(buildMarkerSnippet(listing));
            marker.setRelatedObject(listing); // Store the Listing object
            marker.setOnMarkerClickListener(new Marker.OnMarkerClickListener() {
                @Override
                public boolean onMarkerClick(Marker marker, MapView mapView) {
                    Listing clickedListing = (Listing) marker.getRelatedObject();
                    if (clickedListing != null) {
                        selectedListing = clickedListing;
                        mapController.animateTo(marker.getPosition());
                        mapController.setZoom(15.0);
                        showListingPreview(clickedListing);
                    }
                    return true;
                }
            });
            mapView.getOverlays().add(marker);
        }

        if (!listings.isEmpty()) {
            GeoPoint firstListingLocation = new GeoPoint(listings.get(0).getLatitude(), listings.get(0).getLongitude());
            mapController.setCenter(firstListingLocation);
            showListingPreview(listings.get(0));
        } else {
            mapController.setCenter(new GeoPoint(28.6139, 77.2090));
            cardSelectedListing.setVisibility(android.view.View.GONE);
        }

        buttonOpenMapListing.setOnClickListener(v -> openSelectedListing());
    }

    @Override
    public boolean onSupportNavigateUp() {
        onBackPressed();
        return true;
    }

    @Override
    protected void onResume() {
        super.onResume();
        mapView.onResume();
    }

    @Override
    protected void onPause() {
        super.onPause();
        mapView.onPause();
    }

    private void showListingPreview(Listing listing) {
        selectedListing = listing;
        cardSelectedListing.setVisibility(android.view.View.VISIBLE);
        textViewMapListingTitle.setText(listing.getTitle());
        String businessName = TextUtils.isEmpty(listing.getBusinessName()) ? listing.getDonorEmail() : listing.getBusinessName();
        textViewMapListingBusiness.setText("Business: " + businessName);
        String price = TextUtils.isEmpty(listing.getPrice()) ? "Free" : listing.getPrice();
        textViewMapListingMeta.setText("Qty: " + listing.getQuantity() + "   •   Price: " + price);
        textViewMapListingWindow.setText("Pickup: " + listing.getPickupWindow());
        textViewMapListingDescription.setText(TextUtils.isEmpty(listing.getDescription()) ? "No description provided." : listing.getDescription());

        Glide.with(this)
                .load(listing.getImageUri())
                .placeholder(R.drawable.ic_launcher_foreground)
                .error(R.drawable.ic_launcher_foreground)
                .into(imageViewMapListing);
    }

    private void openSelectedListing() {
        if (selectedListing == null) {
            return;
        }
        Intent intent = new Intent(MapActivity.this, ListingDetailsActivity.class);
        Gson gson = new Gson();
        String listingJson = gson.toJson(selectedListing);
        intent.putExtra("listing", listingJson);
        startActivity(intent);
    }

    private List<Listing> getValidListings(List<Listing> sourceListings) {
        List<Listing> validListings = new ArrayList<>();
        if (sourceListings == null) {
            return validListings;
        }
        for (Listing listing : sourceListings) {
            if (listing == null) {
                continue;
            }
            if (listing.isClaimed() || "COMPLETED".equalsIgnoreCase(listing.getOrderStatus())) {
                continue;
            }
            if (!hasValidCoordinates(listing)) {
                continue;
            }
            validListings.add(listing);
        }
        return validListings;
    }

    private boolean hasValidCoordinates(Listing listing) {
        return listing.getLatitude() >= -90
                && listing.getLatitude() <= 90
                && listing.getLongitude() >= -180
                && listing.getLongitude() <= 180
                && !(listing.getLatitude() == 0d && listing.getLongitude() == 0d);
    }

    private String buildMarkerSnippet(Listing listing) {
        String businessName = TextUtils.isEmpty(listing.getBusinessName()) ? listing.getDonorEmail() : listing.getBusinessName();
        String price = TextUtils.isEmpty(listing.getPrice()) ? "Free" : listing.getPrice();
        return businessName + " • " + price;
    }
}
