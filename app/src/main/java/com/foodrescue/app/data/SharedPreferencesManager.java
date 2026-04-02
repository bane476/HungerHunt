package com.foodrescue.app.data;

import android.content.Context;
import android.content.SharedPreferences;
import com.foodrescue.app.model.User;
import com.foodrescue.app.model.Listing;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;

public class SharedPreferencesManager {
    private static final String PREF_NAME = "FoodRescuePrefs";
    private static final String KEY_USER = "user";
    private static final String KEY_LOGGED_IN_USER_EMAIL = "loggedInUserEmail";
    private static final String KEY_LISTINGS = "listings";
    private static final String KEY_NOTIFIED_LISTINGS = "notifiedListings";
    private static final String KEY_ORDER_HISTORY = "orderHistory";
    private static final String KEY_USER_LOCATION_LAT = "userLocationLat";
    private static final String KEY_USER_LOCATION_LNG = "userLocationLng";
    private static final String KEY_DATA_VERSION = "dataVersion";
    private static final int CURRENT_DATA_VERSION = 5;
    private static boolean migrationChecked = false;
    private SharedPreferences sharedPreferences;
    private Gson gson;

    public SharedPreferencesManager(Context context) {
        sharedPreferences = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
        gson = new Gson();
        if (!migrationChecked) {
            int storedVersion = sharedPreferences.getInt(KEY_DATA_VERSION, 0);
            if (storedVersion < CURRENT_DATA_VERSION) {
                sharedPreferences.edit()
                        .clear()
                        .putInt(KEY_DATA_VERSION, CURRENT_DATA_VERSION)
                        .commit();
            }
            migrationChecked = true;
        }
    }

    public void saveUser(User user) {
        String userJson = gson.toJson(user);
        sharedPreferences.edit().putString(KEY_USER + "_" + user.getEmail(), userJson).apply();
    }

    public User getUser(String email) {
        String userJson = sharedPreferences.getString(KEY_USER + "_" + email, null);
        if (userJson != null) {
            return gson.fromJson(userJson, User.class);
        }
        return null;
    }

    public void saveLoggedInUserEmail(String email) {
        sharedPreferences.edit().putString(KEY_LOGGED_IN_USER_EMAIL, email).apply();
    }

    public String getLoggedInUserEmail() {
        return sharedPreferences.getString(KEY_LOGGED_IN_USER_EMAIL, null);
    }

    public void saveListing(Listing listing) {
        Set<String> listingsJson = new HashSet<>(sharedPreferences.getStringSet(KEY_LISTINGS, new HashSet<>()));
        listingsJson.add(gson.toJson(listing));
        sharedPreferences.edit().putStringSet(KEY_LISTINGS, listingsJson).apply();
    }

    public List<Listing> getAllListings() {
        Set<String> listingsJson = new HashSet<>(sharedPreferences.getStringSet(KEY_LISTINGS, new HashSet<>()));
        List<Listing> listings = new ArrayList<>();
        for (String json : listingsJson) {
            listings.add(gson.fromJson(json, Listing.class));
        }
        return listings;
    }

    public void deleteListing(String listingId) {
        Set<String> listingsJson = new HashSet<>(sharedPreferences.getStringSet(KEY_LISTINGS, new HashSet<>()));
        Iterator<String> iterator = listingsJson.iterator();
        while (iterator.hasNext()) {
            String json = iterator.next();
            Listing listing = gson.fromJson(json, Listing.class);
            if (listing != null && listing.getId() != null && listing.getId().equals(listingId)) {
                iterator.remove();
                break;
            }
        }
        sharedPreferences.edit().putStringSet(KEY_LISTINGS, listingsJson).apply();
    }

    public void updateListing(Listing updatedListing) {
        Set<String> listingsJson = new HashSet<>(sharedPreferences.getStringSet(KEY_LISTINGS, new HashSet<>()));
        // Remove the old listing
        Iterator<String> iterator = listingsJson.iterator();
        while (iterator.hasNext()) {
            String json = iterator.next();
            Listing listing = gson.fromJson(json, Listing.class);
            if (listing != null && listing.getId() != null && listing.getId().equals(updatedListing.getId())) {
                iterator.remove();
                break;
            }
        }
        // Add the updated listing
        listingsJson.add(gson.toJson(updatedListing));
        sharedPreferences.edit().putStringSet(KEY_LISTINGS, listingsJson).apply();
    }

    public boolean isListingNotified(String listingId) {
        Set<String> notifiedIds = new HashSet<>(sharedPreferences.getStringSet(KEY_NOTIFIED_LISTINGS, new HashSet<>()));
        return notifiedIds.contains(listingId);
    }

    public void markListingNotified(String listingId) {
        Set<String> notifiedIds = new HashSet<>(sharedPreferences.getStringSet(KEY_NOTIFIED_LISTINGS, new HashSet<>()));
        notifiedIds.add(listingId);
        sharedPreferences.edit().putStringSet(KEY_NOTIFIED_LISTINGS, notifiedIds).apply();
    }

    public void addOrderHistoryEntry(String userEmail, String type, String entry) {
        String key = buildOrderHistoryKey(userEmail, type);
        Type typeToken = new TypeToken<List<String>>() {}.getType();
        String historyJson = sharedPreferences.getString(key, null);
        List<String> history = historyJson != null ? gson.fromJson(historyJson, typeToken) : new ArrayList<>();
        history.add(0, entry);
        sharedPreferences.edit().putString(key, gson.toJson(history)).apply();
    }

    public List<String> getOrderHistory(String userEmail, String type) {
        String key = buildOrderHistoryKey(userEmail, type);
        Type typeToken = new TypeToken<List<String>>() {}.getType();
        String historyJson = sharedPreferences.getString(key, null);
        if (historyJson == null) {
            return new ArrayList<>();
        }
        List<String> history = gson.fromJson(historyJson, typeToken);
        return history != null ? history : new ArrayList<>();
    }

    private String buildOrderHistoryKey(String userEmail, String type) {
        return KEY_ORDER_HISTORY + "_" + type + "_" + userEmail;
    }

    public void saveUserLocation(String userEmail, double latitude, double longitude) {
        sharedPreferences.edit()
                .putLong(buildUserLocationLatKey(userEmail), Double.doubleToRawLongBits(latitude))
                .putLong(buildUserLocationLngKey(userEmail), Double.doubleToRawLongBits(longitude))
                .apply();
    }

    public boolean hasUserLocation(String userEmail) {
        return sharedPreferences.contains(buildUserLocationLatKey(userEmail))
                && sharedPreferences.contains(buildUserLocationLngKey(userEmail));
    }

    public double[] getUserLocation(String userEmail) {
        if (!hasUserLocation(userEmail)) {
            return null;
        }
        double latitude = Double.longBitsToDouble(sharedPreferences.getLong(buildUserLocationLatKey(userEmail), 0L));
        double longitude = Double.longBitsToDouble(sharedPreferences.getLong(buildUserLocationLngKey(userEmail), 0L));
        return new double[]{latitude, longitude};
    }

    private String buildUserLocationLatKey(String userEmail) {
        return KEY_USER_LOCATION_LAT + "_" + userEmail;
    }

    private String buildUserLocationLngKey(String userEmail) {
        return KEY_USER_LOCATION_LNG + "_" + userEmail;
    }
}
