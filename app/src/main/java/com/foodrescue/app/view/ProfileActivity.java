package com.foodrescue.app.view;

import android.content.Intent;
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
import com.foodrescue.app.viewmodel.AuthViewModel;
import com.google.android.material.textfield.TextInputLayout;
import com.google.android.material.textfield.TextInputEditText; // Import TextInputEditText

import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class ProfileActivity extends AppCompatActivity {

    private TextView textViewEmail;
    private TextInputEditText editTextProfileName, editTextProfilePhone, editTextProfileAddress, editTextProfileBusinessName; // Changed to TextInputEditText
    private TextView textViewReceiverOrderHistory, textViewDonorOrderHistory;
    private TextView textViewStatDonorListings, textViewStatDonorUnits, textViewStatDonorCompleted,
            textViewStatReceiverClaims, textViewStatReceiverUnits, textViewStatReceiverCompleted;
    private TextView textViewStatsHeader, textViewReceiverHistoryHeader, textViewDonorHistoryHeader;
    private TextInputLayout layoutProfileName, layoutBusinessName;
    private LinearLayout layoutDonorStatsRow1, layoutDonorStatsRow2, layoutReceiverStatsRow, layoutReceiverStatsRow2;
    private Button buttonSaveProfile, buttonLogout;
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
        textViewReceiverOrderHistory = findViewById(R.id.textViewReceiverOrderHistory);
        textViewDonorOrderHistory = findViewById(R.id.textViewDonorOrderHistory);
        textViewStatDonorListings = findViewById(R.id.textViewStatDonorListings);
        textViewStatDonorUnits = findViewById(R.id.textViewStatDonorUnits);
        textViewStatDonorCompleted = findViewById(R.id.textViewStatDonorCompleted);
        textViewStatReceiverClaims = findViewById(R.id.textViewStatReceiverClaims);
        textViewStatReceiverUnits = findViewById(R.id.textViewStatReceiverUnits);
        textViewStatReceiverCompleted = findViewById(R.id.textViewStatReceiverCompleted);
        textViewStatsHeader = findViewById(R.id.textViewStatsHeader);
        textViewReceiverHistoryHeader = findViewById(R.id.textViewReceiverHistoryHeader);
        textViewDonorHistoryHeader = findViewById(R.id.textViewDonorHistoryHeader);
        layoutProfileName = findViewById(R.id.layoutProfileName);
        layoutBusinessName = findViewById(R.id.layoutBusinessName);
        layoutDonorStatsRow1 = findViewById(R.id.layoutDonorStatsRow1);
        layoutDonorStatsRow2 = findViewById(R.id.layoutDonorStatsRow2);
        layoutReceiverStatsRow = findViewById(R.id.layoutReceiverStatsRow);
        layoutReceiverStatsRow2 = findViewById(R.id.layoutReceiverStatsRow2);
        buttonSaveProfile = findViewById(R.id.buttonSaveProfile);
        buttonLogout = findViewById(R.id.buttonLogout);

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
                renderOrderHistory();
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

        // Create a new User object with updated details (email and password remain same)
        User updatedUser = new User(newName, currentUser.getEmail(), currentUser.getPassword(), newPhone, newAddress, newBusinessName, newRole);
        sharedPreferencesManager.saveUser(updatedUser); // saveUser will overwrite if email exists
        currentUser = updatedUser; // Update current user in activity
        applyRoleSpecificVisibility(newRole);
        Toast.makeText(this, "Profile updated successfully!", Toast.LENGTH_SHORT).show();
    }

    private void logoutUser() {
        authViewModel.logoutUser();
        Intent intent = new Intent(ProfileActivity.this, LoginActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_NEW_TASK);
        startActivity(intent);
        finish();
    }

    private void renderOrderHistory() {
        List<String> receiverHistory = sharedPreferencesManager.getOrderHistory(currentUser.getEmail(), "receiver");
        List<String> donorHistory = sharedPreferencesManager.getOrderHistory(currentUser.getEmail(), "donor");
        textViewReceiverOrderHistory.setText(formatHistory(receiverHistory));
        textViewDonorOrderHistory.setText(formatHistory(donorHistory));
    }

    private String formatHistory(List<String> history) {
        if (history == null || history.isEmpty()) {
            return "No previous orders.";
        }
        StringBuilder builder = new StringBuilder();
        for (String item : history) {
            builder.append("- ").append(item).append("\n\n");
        }
        return builder.toString().trim();
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

        textViewDonorHistoryHeader.setVisibility(isProvider ? View.VISIBLE : View.GONE);
        textViewDonorOrderHistory.setVisibility(isProvider ? View.VISIBLE : View.GONE);
        textViewReceiverHistoryHeader.setVisibility(isProvider ? View.GONE : View.VISIBLE);
        textViewReceiverOrderHistory.setVisibility(isProvider ? View.GONE : View.VISIBLE);
    }
}
