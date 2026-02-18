package com.foodrescue.app.utils;

import android.Manifest;
import android.app.Activity;
import android.content.Context;
import android.content.pm.PackageManager;
import android.location.Location;

import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.LocationServices;

public class LocationHelper {

    public interface LocationResultListener {
        void onLocationResult(Location location);
    }

    public static boolean hasLocationPermission(Context context) {
        return ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
                || ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED;
    }

    public static void requestLocationPermission(Activity activity, int requestCode) {
        ActivityCompat.requestPermissions(
                activity,
                new String[]{Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION},
                requestCode
        );
    }

    public static void fetchLastLocation(Activity activity, LocationResultListener listener) {
        if (!hasLocationPermission(activity)) {
            listener.onLocationResult(null);
            return;
        }
        FusedLocationProviderClient client = LocationServices.getFusedLocationProviderClient(activity);
        client.getLastLocation()
                .addOnSuccessListener(listener::onLocationResult)
                .addOnFailureListener(e -> listener.onLocationResult(null));
    }
}
