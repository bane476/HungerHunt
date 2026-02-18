package com.foodrescue.app.view;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar; // Import Toolbar

import com.foodrescue.app.R;
import com.foodrescue.app.data.SharedPreferencesManager;
import com.foodrescue.app.model.Listing;
import com.foodrescue.app.model.User;
import com.foodrescue.app.utils.NotificationHelper; // Import NotificationHelper
import com.google.gson.Gson;

public class ListingDetailsActivity extends AppCompatActivity {

    private TextView textViewDetailTitle, textViewDetailDonorEmail, textViewDetailQuantity,
            textViewDetailDescription, textViewDetailPickupWindow, textViewClaimStatus;
    private Button buttonClaimListing, buttonEditListing, buttonDeleteListing;
    private SharedPreferencesManager sharedPreferencesManager;
    private Listing currentListing; // Store the current listing
    private String loggedInUserEmail;
    private User loggedInUser;
    private Toolbar toolbar; // Declare Toolbar

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

        textViewDetailTitle = findViewById(R.id.textViewDetailTitle);
        textViewDetailDonorEmail = findViewById(R.id.textViewDetailDonorEmail);
        textViewDetailQuantity = findViewById(R.id.textViewDetailQuantity);
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
                    currentListing.setClaimed(true);
                    currentListing.setClaimedByEmail(loggedInUserEmail);
                    sharedPreferencesManager.updateListing(currentListing);
                    Toast.makeText(ListingDetailsActivity.this, "Listing claimed successfully!", Toast.LENGTH_SHORT).show();
                    updateClaimStatusUI(); // Update UI
                    buttonClaimListing.setVisibility(View.GONE); // Hide button after claiming
                    // Send notification to donor
                    NotificationHelper.sendNotification(ListingDetailsActivity.this,
                            "Listing Claimed!",
                            currentListing.getTitle() + " has been claimed by " + loggedInUserEmail + ".",
                            currentListing.hashCode()); // Unique ID for notification
                    // Optionally, you might want to refresh the previous activity or navigate back
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
            textViewClaimStatus.setText("Claimed by: " + currentListing.getClaimedByEmail());
            textViewClaimStatus.setVisibility(View.VISIBLE);
        } else {
            textViewClaimStatus.setVisibility(View.GONE);
        }
    }
}
