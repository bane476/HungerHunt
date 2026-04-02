package com.foodrescue.app.view;

import android.content.Intent;
import android.location.Location;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar; // Import Toolbar
import androidx.lifecycle.ViewModelProvider;

import com.foodrescue.app.R;
import com.foodrescue.app.data.SharedPreferencesManager;
import com.foodrescue.app.model.Listing;
import com.foodrescue.app.model.User;
import com.foodrescue.app.utils.LocationHelper;
import com.foodrescue.app.viewmodel.AuthViewModel;
import com.google.android.material.textfield.TextInputLayout;
import com.google.android.material.textfield.TextInputEditText; // Import TextInputEditText

import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class ProfileActivity extends AppCompatActivity {
    private static final int LOCATION_PERMISSION_REQUEST_CODE = 501;

    private TextView textViewEmail;
    private TextInputEditText editTextProfileName, editTextProfilePhone, editTextProfileAddress, editTextProfileBusinessName; // Changed to TextInputEditText
    private TextView textViewStatDonorListings, textViewStatDonorUnits, textViewStatDonorCompleted,
            textViewStatReceiverClaims, textViewStatReceiverUnits, textViewStatReceiverCompleted;
    private TextView textViewStatsHeader, textViewSavedLocation;
    private TextInputLayout layoutProfileName, layoutBusinessName;
    private LinearLayout layoutDonorStatsRow1, layoutDonorStatsRow2, layoutReceiverStatsRow, layoutReceiverStatsRow2;
    private Button buttonSaveProfile, buttonLogout, buttonUpdateLocation;
    private Toolbar toolbar; // Declare Toolbar

    private SharedPreferencesManager sharedPreferencesManager;
    private User currentUser;
    private AuthViewModel authViewModel;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_profile);

        toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        // Enable the Up button
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
            getSupportActionBar().setDisplayShowHomeEnabled(true);
            getSupportActionBar().setTitle("User Profile"); // Set toolbar title
        }

        sharedPreferencesManager = new SharedPreferencesManager(this);
        authViewModel = new ViewModelProvider(this).get(AuthViewModel.class);

        textViewEmail = findViewById(R.id.textViewEmail);
        editTextProfileName = findViewById(R.id.editTextProfileName);
        editTextProfilePhone = findViewById(R.id.editTextProfilePhone);
        editTextProfileAddress = findViewById(R.id.editTextProfileAddress);
        editTextProfileBusinessName = findViewById(R.id.editTextProfileBusinessName);
        textViewStatDonorListings = findViewById(R.id.textViewStatDonorListings);
        textViewStatDonorUnits = findViewById(R.id.textViewStatDonorUnits);
        textViewStatDonorCompleted = findViewById(R.id.textViewStatDonorCompleted);
        textViewStatReceiverClaims = findViewById(R.id.textViewStatReceiverClaims);
        textViewStatReceiverUnits = findViewById(R.id.textViewStatReceiverUnits);
        textViewStatReceiverCompleted = findViewById(R.id.textViewStatReceiverCompleted);
        textViewStatsHeader = findViewById(R.id.textViewStatsHeader);
        textViewSavedLocation = findViewById(R.id.textViewSavedLocation);
        layoutProfileName = findViewById(R.id.layoutProfileName);
        layoutBusinessName = findViewById(R.id.layoutBusinessName);
        layoutDonorStatsRow1 = findViewById(R.id.layoutDonorStatsRow1);
        layoutDonorStatsRow2 = findViewById(R.id.layoutDonorStatsRow2);
        layoutReceiverStatsRow = findViewById(R.id.layoutReceiverStatsRow);
        layoutReceiverStatsRow2 = findViewById(R.id.layoutReceiverStatsRow2);
        buttonSaveProfile = findViewById(R.id.buttonSaveProfile);
        buttonLogout = findViewById(R.id.buttonLogout);
        buttonUpdateLocation = findViewById(R.id.buttonUpdateLocation);

        loadUserProfile();

        buttonSaveProfile.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                saveUserProfile();
            }
        });

        buttonLogout.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                logoutUser();
            }
        });

        buttonUpdateLocation.setOnClickListener(v -> refreshSavedLocation());
    }

    @Override
    public boolean onSupportNavigateUp() {
        onBackPressed();
        return true;
    }

    private void loadUserProfile() {
        String loggedInEmail = sharedPreferencesManager.getLoggedInUserEmail();
        if (loggedInEmail != null) {
            currentUser = sharedPreferencesManager.getUser(loggedInEmail);
            if (currentUser != null) {
                textViewEmail.setText(currentUser.getEmail());
                editTextProfileName.setText(currentUser.getName());
                editTextProfilePhone.setText(currentUser.getPhone());
                editTextProfileAddress.setText(currentUser.getAddress());
                editTextProfileBusinessName.setText(currentUser.getBusinessName());
                String normalizedRole = normalizeRole(currentUser.getRole());
                applyRoleSpecificVisibility(normalizedRole);
                renderSavedLocation();
                renderDashboardStats();
            } else {
                Toast.makeText(this, "User data not found.", Toast.LENGTH_SHORT).show();
                finish();
            }
        } else {
            Toast.makeText(this, "Not logged in.", Toast.LENGTH_SHORT).show();
            startActivity(new Intent(ProfileActivity.this, LoginActivity.class));
            finish();
        }
    }

    private void saveUserProfile() {
        if (currentUser == null) {
            Toast.makeText(this, "Error: No user to save.", Toast.LENGTH_SHORT).show();
            return;
        }

        String newName = editTextProfileName.getText().toString().trim();
        String newPhone = editTextProfilePhone.getText().toString().trim();
        String newAddress = editTextProfileAddress.getText().toString().trim();
        String newBusinessName = editTextProfileBusinessName.getText().toString().trim();

        if (TextUtils.isEmpty(newName) || TextUtils.isEmpty(newPhone) || TextUtils.isEmpty(newAddress)) {
            Toast.makeText(this, "Please fill all fields", Toast.LENGTH_SHORT).show();
            return;
        }

        String newRole = normalizeRole(currentUser.getRole());
        if ("Business".equals(newRole) && TextUtils.isEmpty(newBusinessName)) {
            Toast.makeText(this, "Business name is required for business accounts", Toast.LENGTH_SHORT).show();
            return;
        }

        User updatedUser = new User(newName, currentUser.getEmail(), currentUser.getPassword(), newPhone, newAddress, newBusinessName, newRole);
        buttonSaveProfile.setEnabled(false);
        authViewModel.saveUserProfile(updatedUser, new com.foodrescue.app.repository.AuthRepository.AuthResultCallback() {
            @Override
            public void onSuccess(User user, String message) {
                runOnUiThread(() -> {
                    buttonSaveProfile.setEnabled(true);
                    currentUser = user;
                    applyRoleSpecificVisibility(newRole);
                    Toast.makeText(ProfileActivity.this, message, Toast.LENGTH_SHORT).show();
                });
            }

            @Override
            public void onError(String message) {
                runOnUiThread(() -> {
                    buttonSaveProfile.setEnabled(true);
                    Toast.makeText(ProfileActivity.this, message, Toast.LENGTH_SHORT).show();
                });
            }
        });
    }

    private void logoutUser() {
        authViewModel.logoutUser();
        Intent intent = new Intent(ProfileActivity.this, LoginActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_NEW_TASK);
        startActivity(intent);
        finish();
    }

    private void renderDashboardStats() {
        String userEmail = currentUser.getEmail();
        List<Listing> allListings = sharedPreferencesManager.getAllListings();
        List<String> receiverHistory = sharedPreferencesManager.getOrderHistory(userEmail, "receiver");
        List<String> donorHistory = sharedPreferencesManager.getOrderHistory(userEmail, "donor");

        int donorListings = 0;
        for (Listing listing : allListings) {
            if (listing != null && userEmail.equals(listing.getDonorEmail())) {
                donorListings++;
            }
        }

        int donorUnitsDonated = sumClaimedQuantities(donorHistory);
        int donorCompleted = countHistoryContains(donorHistory, "status changed to COMPLETED");
        int receiverClaims = countHistoryContains(receiverHistory, "Claimed Qty:");
        int receiverUnits = sumClaimedQuantities(receiverHistory);
        int receiverCompleted = countHistoryContains(receiverHistory, "status changed to COMPLETED");

        textViewStatDonorListings.setText("Business Listings\n" + donorListings);
        textViewStatDonorUnits.setText("Units Sold\n" + donorUnitsDonated);
        textViewStatDonorCompleted.setText("Business Completed\n" + donorCompleted);
        textViewStatReceiverClaims.setText("Customer Purchases\n" + receiverClaims);
        textViewStatReceiverUnits.setText("Units Purchased\n" + receiverUnits);
        textViewStatReceiverCompleted.setText("Customer Completed\n" + receiverCompleted);
    }

    private int sumClaimedQuantities(List<String> history) {
        if (history == null || history.isEmpty()) {
            return 0;
        }
        Pattern pattern = Pattern.compile("Claimed Qty:\\s*(\\d+)");
        int total = 0;
        for (String item : history) {
            Matcher matcher = pattern.matcher(item);
            if (matcher.find()) {
                try {
                    total += Integer.parseInt(matcher.group(1));
                } catch (NumberFormatException ignored) {
                }
            }
        }
        return total;
    }

    private int countHistoryContains(List<String> history, String token) {
        if (history == null || history.isEmpty()) {
            return 0;
        }
        int count = 0;
        for (String item : history) {
            if (item != null && item.contains(token)) {
                count++;
            }
        }
        return count;
    }

    private boolean isBusinessRole(String role) {
        return "Business".equalsIgnoreCase(role)
                || "Provider".equalsIgnoreCase(role)
                || "Donor".equalsIgnoreCase(role);
    }

    private String normalizeRole(String role) {
        return isBusinessRole(role) ? "Business" : "Customer";
    }

    private void applyRoleSpecificVisibility(String normalizedRole) {
        boolean isProvider = "Business".equals(normalizedRole);
        layoutProfileName.setHint(isProvider ? "Owner Name" : "Name");
        layoutBusinessName.setVisibility(isProvider ? View.VISIBLE : View.GONE);

        textViewStatsHeader.setVisibility(View.VISIBLE);
        layoutDonorStatsRow1.setVisibility(isProvider ? View.VISIBLE : View.GONE);
        layoutDonorStatsRow2.setVisibility(isProvider ? View.VISIBLE : View.GONE);
        layoutReceiverStatsRow.setVisibility(isProvider ? View.GONE : View.VISIBLE);
        layoutReceiverStatsRow2.setVisibility(isProvider ? View.GONE : View.VISIBLE);
    }

    private void refreshSavedLocation() {
        if (currentUser == null) {
            return;
        }
        if (!LocationHelper.hasLocationPermission(this)) {
            LocationHelper.requestLocationPermission(this, LOCATION_PERMISSION_REQUEST_CODE);
            return;
        }
        buttonUpdateLocation.setEnabled(false);
        LocationHelper.fetchLastLocation(this, this::handleLocationUpdate);
    }

    private void handleLocationUpdate(Location location) {
        runOnUiThread(() -> {
            buttonUpdateLocation.setEnabled(true);
            if (currentUser == null) {
                return;
            }
            if (location == null) {
                Toast.makeText(this, "Could not fetch current location.", Toast.LENGTH_SHORT).show();
                return;
            }
            sharedPreferencesManager.saveUserLocation(currentUser.getEmail(), location.getLatitude(), location.getLongitude());
            renderSavedLocation();
            Toast.makeText(this, "Location updated.", Toast.LENGTH_SHORT).show();
        });
    }

    private void renderSavedLocation() {
        if (currentUser == null) {
            textViewSavedLocation.setText("Location not available.");
            return;
        }
        double[] savedLocation = sharedPreferencesManager.getUserLocation(currentUser.getEmail());
        if (savedLocation == null || savedLocation.length < 2) {
            textViewSavedLocation.setText("Location not saved yet. Tap Update Saved Location.");
            return;
        }
        textViewSavedLocation.setText(String.format(Locale.getDefault(), "Saved location: %.5f, %.5f", savedLocation[0], savedLocation[1]));
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == LOCATION_PERMISSION_REQUEST_CODE && LocationHelper.hasLocationPermission(this)) {
            refreshSavedLocation();
        }
    }
}
