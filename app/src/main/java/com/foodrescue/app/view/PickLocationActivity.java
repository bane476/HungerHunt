package com.foodrescue.app.view;

import android.content.Intent;
import android.location.Location;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.widget.Button;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;

import com.foodrescue.app.R;
import com.foodrescue.app.utils.LocationHelper;

import org.osmdroid.api.IMapController;
import org.osmdroid.config.Configuration;
import org.osmdroid.tileprovider.tilesource.TileSourceFactory;
import org.osmdroid.util.GeoPoint;
import org.osmdroid.views.MapView;
import org.osmdroid.views.overlay.Marker;

import java.util.Locale;

public class PickLocationActivity extends AppCompatActivity {
    private static final int LOCATION_PERMISSION_REQUEST_CODE = 451;
    private static final double DEFAULT_LATITUDE = 28.6139;
    private static final double DEFAULT_LONGITUDE = 77.2090;
    private static final String EXTRA_SELECTED_LATITUDE = "selectedLatitude";
    private static final String EXTRA_SELECTED_LONGITUDE = "selectedLongitude";

    private MapView mapView;
    private Toolbar toolbar;
    private TextView textViewSelectedLocation;
    private Button buttonUsePinnedLocation;
    private Button buttonCenterOnCurrentLocation;
    private Marker selectedLocationMarker;
    private Marker currentLocationMarker;
    private Double selectedLatitude;
    private Double selectedLongitude;
    private GeoPoint currentReferencePoint;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Configuration.getInstance().load(getApplicationContext(), PreferenceManager.getDefaultSharedPreferences(getApplicationContext()));
        Configuration.getInstance().setUserAgentValue(getPackageName());
        setContentView(R.layout.activity_pick_location);

        toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
            getSupportActionBar().setDisplayShowHomeEnabled(true);
            getSupportActionBar().setTitle("Pick Pickup Location");
        }

        mapView = findViewById(R.id.map);
        textViewSelectedLocation = findViewById(R.id.textViewSelectedLocation);
        buttonUsePinnedLocation = findViewById(R.id.buttonUsePinnedLocation);
        buttonCenterOnCurrentLocation = findViewById(R.id.buttonCenterOnCurrentLocation);

        mapView.setTileSource(TileSourceFactory.MAPNIK);
        mapView.setMultiTouchControls(true);
        mapView.setBuiltInZoomControls(false);

        double initialLatitude = getIntent().hasExtra(EXTRA_SELECTED_LATITUDE)
                ? getIntent().getDoubleExtra(EXTRA_SELECTED_LATITUDE, DEFAULT_LATITUDE)
                : DEFAULT_LATITUDE;
        double initialLongitude = getIntent().hasExtra(EXTRA_SELECTED_LONGITUDE)
                ? getIntent().getDoubleExtra(EXTRA_SELECTED_LONGITUDE, DEFAULT_LONGITUDE)
                : DEFAULT_LONGITUDE;

        GeoPoint initialPoint = new GeoPoint(initialLatitude, initialLongitude);
        currentReferencePoint = initialPoint;
        selectedLatitude = initialLatitude;
        selectedLongitude = initialLongitude;

        IMapController mapController = mapView.getController();
        mapController.setZoom(15.0);
        mapController.setCenter(initialPoint);

        selectedLocationMarker = buildSelectedLocationMarker(initialPoint);
        mapView.getOverlays().add(selectedLocationMarker);
        updateSelectedLocationLabel(initialLatitude, initialLongitude);

        buttonUsePinnedLocation.setOnClickListener(v -> returnSelectedLocation());
        buttonCenterOnCurrentLocation.setOnClickListener(v -> centerOnCurrentLocation());

        loadCurrentLocationMarker();
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

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == LOCATION_PERMISSION_REQUEST_CODE && LocationHelper.hasLocationPermission(this)) {
            loadCurrentLocationMarker();
        }
    }

    private Marker buildSelectedLocationMarker(GeoPoint initialPoint) {
        Marker marker = new Marker(mapView);
        marker.setPosition(initialPoint);
        marker.setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM);
        marker.setTitle("Pinned pickup location");
        marker.setSubDescription("Drag this marker to the pickup point.");
        marker.setDraggable(true);
        marker.setOnMarkerDragListener(new Marker.OnMarkerDragListener() {
            @Override
            public void onMarkerDrag(Marker marker) {
                updatePinnedLocation(marker.getPosition());
            }

            @Override
            public void onMarkerDragEnd(Marker marker) {
                updatePinnedLocation(marker.getPosition());
            }

            @Override
            public void onMarkerDragStart(Marker marker) {
                updatePinnedLocation(marker.getPosition());
            }
        });
        return marker;
    }

    private void updatePinnedLocation(GeoPoint point) {
        selectedLatitude = point.getLatitude();
        selectedLongitude = point.getLongitude();
        updateSelectedLocationLabel(selectedLatitude, selectedLongitude);
    }

    private void updateSelectedLocationLabel(double latitude, double longitude) {
        textViewSelectedLocation.setText(String.format(
                Locale.getDefault(),
                "Pinned pickup location: %.5f, %.5f",
                latitude,
                longitude
        ));
    }

    private void loadCurrentLocationMarker() {
        if (!LocationHelper.hasLocationPermission(this)) {
            LocationHelper.requestLocationPermission(this, LOCATION_PERMISSION_REQUEST_CODE);
            buttonCenterOnCurrentLocation.setEnabled(false);
            buttonCenterOnCurrentLocation.setText("Allow Location to Show Current Position");
            return;
        }

        buttonCenterOnCurrentLocation.setEnabled(false);
        buttonCenterOnCurrentLocation.setText("Finding Current Location...");
        LocationHelper.fetchLastLocation(this, location -> runOnUiThread(() -> applyCurrentLocation(location)));
    }

    private void applyCurrentLocation(Location location) {
        if (location == null) {
            buttonCenterOnCurrentLocation.setEnabled(false);
            buttonCenterOnCurrentLocation.setText("Current Location Unavailable");
            return;
        }

        GeoPoint currentPoint = new GeoPoint(location.getLatitude(), location.getLongitude());
        currentReferencePoint = currentPoint;

        if (currentLocationMarker != null) {
            mapView.getOverlays().remove(currentLocationMarker);
        }

        currentLocationMarker = new Marker(mapView);
        currentLocationMarker.setPosition(currentPoint);
        currentLocationMarker.setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM);
        currentLocationMarker.setTitle("Your current location");
        currentLocationMarker.setSubDescription("Reference point for the pinned pickup location.");
        currentLocationMarker.setDraggable(false);
        mapView.getOverlays().add(currentLocationMarker);
        mapView.invalidate();

        buttonCenterOnCurrentLocation.setEnabled(true);
        buttonCenterOnCurrentLocation.setText("Center on Current Location");
    }

    private void centerOnCurrentLocation() {
        if (currentReferencePoint == null) {
            loadCurrentLocationMarker();
            return;
        }
        mapView.getController().animateTo(currentReferencePoint);
        mapView.getController().setZoom(16.5);
    }

    private void returnSelectedLocation() {
        if (selectedLatitude == null || selectedLongitude == null) {
            return;
        }
        Intent result = new Intent();
        result.putExtra(EXTRA_SELECTED_LATITUDE, selectedLatitude);
        result.putExtra(EXTRA_SELECTED_LONGITUDE, selectedLongitude);
        setResult(RESULT_OK, result);
        finish();
    }
}
