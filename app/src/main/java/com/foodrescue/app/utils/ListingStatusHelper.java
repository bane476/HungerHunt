package com.foodrescue.app.utils;

import android.text.TextUtils;

import com.foodrescue.app.model.Listing;

import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.Locale;

public final class ListingStatusHelper {
    private static final DateTimeFormatter PICKUP_TIME_FORMATTER = DateTimeFormatter.ofPattern("HH:mm", Locale.getDefault());

    private ListingStatusHelper() {
    }

    public static boolean isExpired(Listing listing) {
        if (listing == null) {
            return false;
        }
        if ("COMPLETED".equalsIgnoreCase(listing.getOrderStatus())
                || "PICKED_UP".equalsIgnoreCase(listing.getOrderStatus())) {
            return false;
        }
        if ("EXPIRED".equalsIgnoreCase(listing.getOrderStatus())) {
            return true;
        }

        LocalTime pickupEndTime = parsePickupWindowEnd(listing.getPickupWindow());
        if (pickupEndTime == null) {
            return false;
        }

        // Current implementation only stores time (HH:mm). 
        // We assume listings are for the current day.
        // A listing is only expired if the current time is strictly after the pickup end time.
        return LocalTime.now().isAfter(pickupEndTime);
    }

    public static LocalTime parsePickupWindowEnd(String pickupWindow) {
        if (TextUtils.isEmpty(pickupWindow) || !pickupWindow.contains(" - ")) {
            return null;
        }
        String[] parts = pickupWindow.split(" - ");
        if (parts.length != 2) {
            return null;
        }
        try {
            return LocalTime.parse(parts[1].trim(), PICKUP_TIME_FORMATTER);
        } catch (DateTimeParseException ignored) {
            return null;
        }
    }
}
