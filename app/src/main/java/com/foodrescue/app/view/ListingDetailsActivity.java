package com.foodrescue.app.view;

import android.content.Intent;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.widget.Toolbar; // Import Toolbar

import com.foodrescue.app.R;
import com.foodrescue.app.data.SharedPreferencesManager;
import com.foodrescue.app.firebase.FirebaseDatabaseService;
import com.foodrescue.app.model.Listing;
import com.foodrescue.app.model.User;
import com.foodrescue.app.utils.NotificationHelper; // Import NotificationHelper
import com.google.gson.Gson;

public class ListingDetailsActivity extends AppCompatActivity {

    private TextView textViewDetailTitle, textViewDetailDonorEmail, textViewDetailQuantity,
            textViewDetailPrice, textViewDetailDescription, textViewDetailPickupWindow, textViewClaimStatus;
    private Button buttonClaimListing, buttonEditListing, buttonDeleteListing;
    private SharedPreferencesManager sharedPreferencesManager;
    private Listing currentListing; // Store the current listing
    private String loggedInUserEmail;
    private User loggedInUser;
    private Toolbar toolbar; // Declare Toolbar
    private FirebaseDatabaseService firebaseDatabaseService;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_listing_details);

        toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        // Enable the Up button
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
            getSupportActionBar().setDisplayShowHomeEnabled(true);
            getSupportActionBar().setTitle("Listing Details"); // Set toolbar title
        }

        sharedPreferencesManager = new SharedPreferencesManager(this);
        firebaseDatabaseService = new FirebaseDatabaseService(this);

        textViewDetailTitle = findViewById(R.id.textViewDetailTitle);
        textViewDetailDonorEmail = findViewById(R.id.textViewDetailDonorEmail);
        textViewDetailQuantity = findViewById(R.id.textViewDetailQuantity);
        textViewDetailPrice = findViewById(R.id.textViewDetailPrice);
        textViewDetailDescription = findViewById(R.id.textViewDetailDescription);
        textViewDetailPickupWindow = findViewById(R.id.textViewDetailPickupWindow);
        textViewClaimStatus = findViewById(R.id.textViewClaimStatus); // Initialize from XML
        buttonClaimListing = findViewById(R.id.buttonClaimListing);
        buttonEditListing = findViewById(R.id.buttonEditListing);
        buttonDeleteListing = findViewById(R.id.buttonDeleteListing);

        loggedInUserEmail = sharedPreferencesManager.getLoggedInUserEmail();
        if (loggedInUserEmail != null) {
            loggedInUser = sharedPreferencesManager.getUser(loggedInUserEmail);
        }

        String listingJson = getIntent().getStringExtra("listing");
        if (listingJson != null) {
            Gson gson = new Gson();
            currentListing = gson.fromJson(listingJson, Listing.class);
            if (currentListing != null) {
                textViewDetailTitle.setText(currentListing.getTitle());
                textViewDetailDonorEmail.setText("Donor: " + currentListing.getDonorEmail());
                textViewDetailQuantity.setText("Quantity: " + currentListing.getQuantity());
                textViewDetailPrice.setText("Price: " + (TextUtils.isEmpty(currentListing.getPrice()) ? "Free" : currentListing.getPrice()));
                textViewDetailDescription.setText("Description: " + currentListing.getDescription());
                textViewDetailPickupWindow.setText("Pickup Window: " + currentListing.getPickupWindow());

                updateClaimStatusUI(); // Call method to update UI based on claim status

                if (loggedInUserEmail != null && loggedInUserEmail.equals(currentListing.getDonorEmail())) {
                    // Logged-in user is the donor
                    buttonEditListing.setVisibility(View.VISIBLE);
                    buttonDeleteListing.setVisibility(View.VISIBLE);
                    buttonClaimListing.setVisibility(View.GONE); // Donor cannot claim their own listing
                } else if (loggedInUser != null && loggedInUser.getRole().equals("Receiver")) {
                    // Logged-in user is a receiver
                    buttonEditListing.setVisibility(View.GONE);
                    buttonDeleteListing.setVisibility(View.GONE);
                    if (currentListing.isClaimed()) {
                        buttonClaimListing.setVisibility(View.GONE); // Already claimed
                    } else {
                        buttonClaimListing.setVisibility(View.VISIBLE); // Can claim
                    }
                } else {
                    // Not logged in or not a receiver
                    buttonEditListing.setVisibility(View.GONE);
                    buttonDeleteListing.setVisibility(View.GONE);
                    buttonClaimListing.setVisibility(View.GONE);
                }

                buttonEditListing.setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        Intent intent = new Intent(ListingDetailsActivity.this, AddListingActivity.class);
                        intent.putExtra("listing", listingJson); // Pass the listing for editing
                        startActivity(intent);
                        finish(); // Finish current activity so that onResume will refresh the list if needed
                    }
                });

                buttonDeleteListing.setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        sharedPreferencesManager.deleteListing(currentListing.getId());
                        Toast.makeText(ListingDetailsActivity.this, "Listing deleted!", Toast.LENGTH_SHORT).show();
                        finish(); // Go back to previous activity (e.g., DonorHomeActivity)
                    }
                });
            }
        } else {
            Toast.makeText(this, "Listing details not found.", Toast.LENGTH_SHORT).show();
            finish();
        }

        buttonClaimListing.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (loggedInUser != null && loggedInUser.getRole().equals("Receiver") && !currentListing.isClaimed()) {
                    showPaymentMethodPickerAndClaim();
                } else {
                    Toast.makeText(ListingDetailsActivity.this, "Unable to claim listing.", Toast.LENGTH_SHORT).show();
                }
            }
        });
    }

    @Override
    public boolean onSupportNavigateUp() {
        onBackPressed();
        return true;
    }

    private void updateClaimStatusUI() {
        if (currentListing.isClaimed()) {
            String claimStatus = "Claimed by: " + currentListing.getClaimedByEmail();
            if (!TextUtils.isEmpty(currentListing.getPaymentMethod())) {
                claimStatus += "\nPayment: " + currentListing.getPaymentMethod();
            }
            textViewClaimStatus.setText(claimStatus);
            textViewClaimStatus.setVisibility(View.VISIBLE);
        } else {
            textViewClaimStatus.setVisibility(View.GONE);
        }
    }

    private void showPaymentMethodPickerAndClaim() {
        final String[] paymentMethods = {"Cash", "UPI", "Card"};
        new AlertDialog.Builder(this)
                .setTitle("Select Payment Method")
                .setItems(paymentMethods, (dialog, which) -> completeClaim(paymentMethods[which]))
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void completeClaim(String paymentMethod) {
        currentListing.setClaimed(true);
        currentListing.setClaimedByEmail(loggedInUserEmail);
        currentListing.setPaymentMethod(paymentMethod);
        sharedPreferencesManager.updateListing(currentListing);
        firebaseDatabaseService.saveListing(currentListing);
        Toast.makeText(this, "Listing claimed successfully!", Toast.LENGTH_SHORT).show();
        updateClaimStatusUI();
        buttonClaimListing.setVisibility(View.GONE);
        NotificationHelper.sendNotification(
                this,
                "Listing Claimed!",
                currentListing.getTitle() + " claimed by " + loggedInUserEmail + " via " + paymentMethod + ".",
                currentListing.hashCode()
        );
    }
}
