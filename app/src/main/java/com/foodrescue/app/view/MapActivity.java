package com.foodrescue.app.view;

import android.content.Intent;
import android.graphics.Color;
import android.location.Location;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.text.TextUtils;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar; // Import Toolbar

import com.bumptech.glide.Glide;
import com.foodrescue.app.R;
import com.foodrescue.app.data.SharedPreferencesManager;
import com.foodrescue.app.firebase.FirebaseDatabaseService;
import com.foodrescue.app.model.Listing;
import com.foodrescue.app.utils.ListingStatusHelper;
import com.foodrescue.app.utils.LocationHelper;
import com.google.gson.Gson;
import com.google.android.material.card.MaterialCardView;
import com.google.firebase.firestore.DocumentSnapshot;

import org.osmdroid.api.IMapController;
import org.osmdroid.config.Configuration;
import org.osmdroid.tileprovider.tilesource.TileSourceFactory;
import org.osmdroid.util.BoundingBox;
import org.osmdroid.util.GeoPoint;
import org.osmdroid.views.MapView;
import org.osmdroid.views.overlay.Marker;
import org.osmdroid.views.overlay.Polyline;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class MapActivity extends AppCompatActivity {
    private static final int LOCATION_PERMISSION_REQUEST_CODE = 452;
    private static final String OSRM_ROUTE_URL = "https://router.project-osrm.org/route/v1/driving/";

    private MapView mapView;
    private SharedPreferencesManager sharedPreferencesManager;
    private List<Listing> listings;
    private Toolbar toolbar; // Declare Toolbar
    private FirebaseDatabaseService firebaseDatabaseService;
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
    private Marker currentLocationMarker;
    private Polyline routePolyline;

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
        firebaseDatabaseService = new FirebaseDatabaseService(this);
        listings = new ArrayList<>();

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

        buttonOpenMapListing.setOnClickListener(v -> openSelectedListing());
        loadListings();
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
        loadCurrentLocationMarker();
    }

    @Override
    protected void onPause() {
        super.onPause();
        mapView.onPause();
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == LOCATION_PERMISSION_REQUEST_CODE && LocationHelper.hasLocationPermission(this)) {
            loadCurrentLocationMarker();
        }
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
            if (listing.isClaimed()
                    || "COMPLETED".equalsIgnoreCase(listing.getOrderStatus())
                    || ListingStatusHelper.isExpired(listing)) {
                continue;
            }
            if (!hasValidCoordinates(listing)) {
                continue;
            }
            validListings.add(listing);
        }
        return validListings;
    }

    private void loadListings() {
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
                    renderListingsOnMap(getValidListings(mergedListings));
                })
                .addOnFailureListener(e -> renderListingsOnMap(getValidListings(sharedPreferencesManager.getAllListings())));
    }

    private void renderListingsOnMap(List<Listing> mergedListings) {
        listings.clear();
        listings.addAll(mergedListings);
        mapView.getOverlays().clear();
        textViewMapSummary.setText(listings.size() + (listings.size() == 1 ? " listing on map" : " listings on map"));
        textViewMapEmpty.setVisibility(listings.isEmpty() ? android.view.View.VISIBLE : android.view.View.GONE);

        IMapController mapController = mapView.getController();
        for (Listing listing : listings) {
            GeoPoint location = new GeoPoint(listing.getLatitude(), listing.getLongitude());
            Marker marker = new Marker(mapView);
            marker.setPosition(location);
            marker.setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM);
            marker.setTitle(listing.getTitle());
            marker.setSubDescription(buildMarkerSnippet(listing));
            marker.setRelatedObject(listing);
            marker.setOnMarkerClickListener((clickedMarker, ignoredMap) -> {
                Listing clickedListing = (Listing) clickedMarker.getRelatedObject();
                if (clickedListing != null) {
                    selectedListing = clickedListing;
                    mapController.animateTo(clickedMarker.getPosition());
                    mapController.setZoom(15.0);
                    showListingPreview(clickedListing);
                    drawRouteToListing(clickedListing);
                }
                return true;
            });
            mapView.getOverlays().add(marker);
        }

        if (!listings.isEmpty()) {
            GeoPoint firstListingLocation = new GeoPoint(listings.get(0).getLatitude(), listings.get(0).getLongitude());
            mapController.setCenter(firstListingLocation);
            showListingPreview(listings.get(0));
        } else {
            selectedListing = null;
            mapController.setCenter(new GeoPoint(28.6139, 77.2090));
            cardSelectedListing.setVisibility(android.view.View.GONE);
        }
        loadCurrentLocationMarker();
        mapView.invalidate();
    }

    private void addOrReplaceListing(List<Listing> target, Listing listing, boolean preferIncoming) {
        if (listing == null || listing.getId() == null) {
            if (listing != null) {
                target.add(listing);
            }
            return;
        }
        for (int i = 0; i < target.size(); i++) {
            Listing existing = target.get(i);
            if (existing != null && listing.getId().equals(existing.getId())) {
                if (preferIncoming) {
                    target.set(i, listing);
                }
                return;
            }
        }
        target.add(listing);
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
    private void drawRouteToListing(Listing listing) {
        if (listing == null) {
            return;
        }
        if (currentLocationMarker == null || currentLocationMarker.getPosition() == null) {
            clearRouteOverlay();
            Toast.makeText(this, "Current location is unavailable, so route cannot be shown.", Toast.LENGTH_SHORT).show();
            return;
        }

        GeoPoint origin = currentLocationMarker.getPosition();
        GeoPoint destination = new GeoPoint(listing.getLatitude(), listing.getLongitude());
        fetchRoute(origin, destination);
    }

    private void fetchRoute(GeoPoint origin, GeoPoint destination) {
        clearRouteOverlay();

        new Thread(() -> {
            try {
                String requestUrl = String.format(
                        Locale.US,
                        "%s%f,%f;%f,%f?overview=full&geometries=geojson",
                        OSRM_ROUTE_URL,
                        origin.getLongitude(),
                        origin.getLatitude(),
                        destination.getLongitude(),
                        destination.getLatitude()
                );

                HttpURLConnection connection = (HttpURLConnection) new URL(requestUrl).openConnection();
                connection.setRequestMethod("GET");
                connection.setConnectTimeout(10000);
                connection.setReadTimeout(10000);

                int responseCode = connection.getResponseCode();
                if (responseCode < 200 || responseCode >= 300) {
                    throw new IllegalStateException("Route request failed with code " + responseCode);
                }

                StringBuilder response = new StringBuilder();
                try (BufferedReader reader = new BufferedReader(new InputStreamReader(connection.getInputStream()))) {
                    String line;
                    while ((line = reader.readLine()) != null) {
                        response.append(line);
                    }
                } finally {
                    connection.disconnect();
                }

                List<GeoPoint> routePoints = parseRoutePoints(response.toString());
                runOnUiThread(() -> renderRoute(routePoints));
            } catch (Exception e) {
                runOnUiThread(() -> Toast.makeText(
                        this,
                        "Could not load route. Check internet and try again.",
                        Toast.LENGTH_SHORT
                ).show());
            }
        }).start();
    }

    private List<GeoPoint> parseRoutePoints(String responseBody) throws Exception {
        List<GeoPoint> routePoints = new ArrayList<>();
        JSONObject root = new JSONObject(responseBody);
        JSONArray routes = root.optJSONArray("routes");
        if (routes == null || routes.length() == 0) {
            return routePoints;
        }

        JSONObject route = routes.getJSONObject(0);
        JSONObject geometry = route.getJSONObject("geometry");
        JSONArray coordinates = geometry.getJSONArray("coordinates");
        for (int i = 0; i < coordinates.length(); i++) {
            JSONArray point = coordinates.getJSONArray(i);
            routePoints.add(new GeoPoint(point.getDouble(1), point.getDouble(0)));
        }
        return routePoints;
    }

    private void renderRoute(List<GeoPoint> routePoints) {
        clearRouteOverlay();
        if (routePoints == null || routePoints.size() < 2) {
            Toast.makeText(this, "Route is unavailable for this listing.", Toast.LENGTH_SHORT).show();
            return;
        }

        routePolyline = new Polyline();
        routePolyline.setPoints(routePoints);
        routePolyline.setColor(Color.parseColor("#E67E22"));
        routePolyline.setWidth(10f);
        mapView.getOverlays().add(routePolyline);

        BoundingBox boundingBox = BoundingBox.fromGeoPointsSafe(routePoints);
        if (boundingBox != null) {
            mapView.zoomToBoundingBox(boundingBox, true, 120);
        }
        mapView.invalidate();
    }

    private void clearRouteOverlay() {
        if (routePolyline != null) {
            mapView.getOverlays().remove(routePolyline);
            routePolyline = null;
        }
    }

    private void loadCurrentLocationMarker() {
        String loggedInEmail = sharedPreferencesManager.getLoggedInUserEmail();
        if (loggedInEmail != null) {
            double[] savedLocation = sharedPreferencesManager.getUserLocation(loggedInEmail);
            if (savedLocation != null && savedLocation.length >= 2) {
                renderCurrentLocationMarker(new GeoPoint(savedLocation[0], savedLocation[1]));
                return;
            }
        }

        if (!LocationHelper.hasLocationPermission(this)) {
            LocationHelper.requestLocationPermission(this, LOCATION_PERMISSION_REQUEST_CODE);
            return;
        }

        LocationHelper.fetchLastLocation(this, location -> runOnUiThread(() -> {
            if (location == null) {
                return;
            }
            renderCurrentLocationMarker(new GeoPoint(location.getLatitude(), location.getLongitude()));
        }));
    }

    private void renderCurrentLocationMarker(GeoPoint currentPoint) {
        if (currentPoint == null) {
            return;
        }
        if (currentLocationMarker != null) {
            mapView.getOverlays().remove(currentLocationMarker);
        }
        currentLocationMarker = new Marker(mapView);
        currentLocationMarker.setPosition(currentPoint);
        currentLocationMarker.setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM);
        currentLocationMarker.setTitle("Your current location");
        currentLocationMarker.setSubDescription("Reference marker");
        currentLocationMarker.setDraggable(false);
        mapView.getOverlays().add(currentLocationMarker);
        mapView.invalidate();
    }
}
