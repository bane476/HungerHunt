package com.foodrescue.app.model;

public class Listing {
    private String id;
    private String donorEmail;
    private String title;
    private String quantity;
    private String price;
    private String description;
    private String pickupWindow;
    private double latitude;
    private double longitude;
    private boolean isClaimed;
    private String claimedByEmail;
    private String paymentMethod;

    public Listing() {
        // Required for Firebase deserialization
    }

    public Listing(String id, String donorEmail, String title, String quantity, String description, String pickupWindow, double latitude, double longitude) {
        this(id, donorEmail, title, quantity, null, description, pickupWindow, latitude, longitude, false, null, null);
    }

    public Listing(String id, String donorEmail, String title, String quantity, String price, String description, String pickupWindow, double latitude, double longitude) {
        this(id, donorEmail, title, quantity, price, description, pickupWindow, latitude, longitude, false, null, null);
    }

    public Listing(String id, String donorEmail, String title, String quantity, String description, String pickupWindow, double latitude, double longitude, boolean isClaimed, String claimedByEmail) {
        this(id, donorEmail, title, quantity, null, description, pickupWindow, latitude, longitude, isClaimed, claimedByEmail, null);
    }

    public Listing(String id, String donorEmail, String title, String quantity, String price, String description, String pickupWindow, double latitude, double longitude, boolean isClaimed, String claimedByEmail, String paymentMethod) {
        this.id = id;
        this.donorEmail = donorEmail;
        this.title = title;
        this.quantity = quantity;
        this.price = price;
        this.description = description;
        this.pickupWindow = pickupWindow;
        this.latitude = latitude;
        this.longitude = longitude;
        this.isClaimed = isClaimed;
        this.claimedByEmail = claimedByEmail;
        this.paymentMethod = paymentMethod;
    }

    public String getId() {
        return id;
    }

    public String getDonorEmail() {
        return donorEmail;
    }

    public void setDonorEmail(String donorEmail) {
        this.donorEmail = donorEmail;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getQuantity() {
        return quantity;
    }

    public void setQuantity(String quantity) {
        this.quantity = quantity;
    }

    public String getPrice() {
        return price;
    }

    public void setPrice(String price) {
        this.price = price;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public String getPickupWindow() {
        return pickupWindow;
    }

    public void setPickupWindow(String pickupWindow) {
        this.pickupWindow = pickupWindow;
    }

    public double getLatitude() {
        return latitude;
    }

    public void setLatitude(double latitude) {
        this.latitude = latitude;
    }

    public double getLongitude() {
        return longitude;
    }

    public void setLongitude(double longitude) {
        this.longitude = longitude;
    }

    public boolean isClaimed() {
        return isClaimed;
    }

    public void setClaimed(boolean claimed) {
        isClaimed = claimed;
    }

    public String getClaimedByEmail() {
        return claimedByEmail;
    }

    public void setId(String id) {
        this.id = id;
    }

    public void setClaimedByEmail(String claimedByEmail) {
        this.claimedByEmail = claimedByEmail;
    }

    public String getPaymentMethod() {
        return paymentMethod;
    }

    public void setPaymentMethod(String paymentMethod) {
        this.paymentMethod = paymentMethod;
    }
}
