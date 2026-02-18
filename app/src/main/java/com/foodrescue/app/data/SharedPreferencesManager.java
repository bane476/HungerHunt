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
    private SharedPreferences sharedPreferences;
    private Gson gson;

    public SharedPreferencesManager(Context context) {
        sharedPreferences = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
        gson = new Gson();
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
        Set<String> listingsJson = sharedPreferences.getStringSet(KEY_LISTINGS, new HashSet<String>());
        listingsJson.add(gson.toJson(listing));
        sharedPreferences.edit().putStringSet(KEY_LISTINGS, listingsJson).apply();
    }

    public List<Listing> getAllListings() {
        Set<String> listingsJson = sharedPreferences.getStringSet(KEY_LISTINGS, new HashSet<String>());
        List<Listing> listings = new ArrayList<>();
        for (String json : listingsJson) {
            listings.add(gson.fromJson(json, Listing.class));
        }
        return listings;
    }

    public void deleteListing(String listingId) {
        Set<String> listingsJson = sharedPreferences.getStringSet(KEY_LISTINGS, new HashSet<String>());
        Iterator<String> iterator = listingsJson.iterator();
        while (iterator.hasNext()) {
            String json = iterator.next();
            Listing listing = gson.fromJson(json, Listing.class);
            if (listing != null && listing.getId().equals(listingId)) {
                iterator.remove();
                break;
            }
        }
        sharedPreferences.edit().putStringSet(KEY_LISTINGS, listingsJson).apply();
    }

    public void updateListing(Listing updatedListing) {
        Set<String> listingsJson = sharedPreferences.getStringSet(KEY_LISTINGS, new HashSet<String>());
        // Remove the old listing
        Iterator<String> iterator = listingsJson.iterator();
        while (iterator.hasNext()) {
            String json = iterator.next();
            Listing listing = gson.fromJson(json, Listing.class);
            if (listing != null && listing.getId().equals(updatedListing.getId())) {
                iterator.remove();
                break;
            }
        }
        // Add the updated listing
        listingsJson.add(gson.toJson(updatedListing));
        sharedPreferences.edit().putStringSet(KEY_LISTINGS, listingsJson).apply();
    }

    public boolean isListingNotified(String listingId) {
        Set<String> notifiedIds = sharedPreferences.getStringSet(KEY_NOTIFIED_LISTINGS, new HashSet<String>());
        return notifiedIds.contains(listingId);
    }

    public void markListingNotified(String listingId) {
        Set<String> notifiedIds = sharedPreferences.getStringSet(KEY_NOTIFIED_LISTINGS, new HashSet<String>());
        notifiedIds.add(listingId);
        sharedPreferences.edit().putStringSet(KEY_NOTIFIED_LISTINGS, notifiedIds).apply();
    }
}
