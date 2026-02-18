package com.foodrescue.app.view;

import android.os.Bundle;
import android.text.TextUtils;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar; // Import Toolbar
import com.foodrescue.app.R;
import com.foodrescue.app.data.SharedPreferencesManager;
import com.foodrescue.app.firebase.FirebaseDatabaseService;
import com.foodrescue.app.model.Listing;
import com.foodrescue.app.utils.NotificationHelper; // Import NotificationHelper
import com.google.gson.Gson;
import java.util.UUID;

public class AddListingActivity extends AppCompatActivity {

    private EditText editTextTitle, editTextQuantity, editTextDescription, editTextPickupWindow, editTextLatitude, editTextLongitude;
    private Button buttonAddListing;
    private SharedPreferencesManager sharedPreferencesManager;
    private Listing currentListing; // To hold the listing if we are in edit mode
    private Toolbar toolbar; // Declare Toolbar
    private FirebaseDatabaseService firebaseDatabaseService;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_add_listing);

        toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        // Enable the Up button
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
            getSupportActionBar().setDisplayShowHomeEnabled(true);
        }

        sharedPreferencesManager = new SharedPreferencesManager(this);
        firebaseDatabaseService = new FirebaseDatabaseService(this);
        NotificationHelper.createNotificationChannel(this);

        editTextTitle = findViewById(R.id.editTextTitle);
        editTextQuantity = findViewById(R.id.editTextQuantity);
        editTextDescription = findViewById(R.id.editTextDescription);
        editTextPickupWindow = findViewById(R.id.editTextPickupWindow);
        editTextLatitude = findViewById(R.id.editTextLatitude);
        editTextLongitude = findViewById(R.id.editTextLongitude);
        buttonAddListing = findViewById(R.id.buttonAddListing);

        // Check if we are in edit mode
        String listingJson = getIntent().getStringExtra("listing");
        if (listingJson != null) {
            Gson gson = new Gson();
            currentListing = gson.fromJson(listingJson, Listing.class);
            if (currentListing != null) {
                editTextTitle.setText(currentListing.getTitle());
                editTextQuantity.setText(currentListing.getQuantity());
                editTextDescription.setText(currentListing.getDescription());
                editTextPickupWindow.setText(currentListing.getPickupWindow());
                editTextLatitude.setText(String.valueOf(currentListing.getLatitude()));
                editTextLongitude.setText(String.valueOf(currentListing.getLongitude()));
                buttonAddListing.setText("Update Listing"); // Change button text
                if (getSupportActionBar() != null) {
                    getSupportActionBar().setTitle("Edit Listing"); // Set toolbar title for edit mode
                }
            }
        } else {
             if (getSupportActionBar() != null) {
                    getSupportActionBar().setTitle("Add Listing"); // Set toolbar title for add mode
                }
        }

        buttonAddListing.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                saveOrUpdateListing();
            }
        });
    }

    @Override
    public boolean onSupportNavigateUp() {
        onBackPressed();
        return true;
    }

    private void saveOrUpdateListing() {
        String title = editTextTitle.getText().toString().trim();
        String quantity = editTextQuantity.getText().toString().trim();
        String description = editTextDescription.getText().toString().trim();
        String pickupWindow = editTextPickupWindow.getText().toString().trim();
        String latString = editTextLatitude.getText().toString().trim();
        String lonString = editTextLongitude.getText().toString().trim();

        if (TextUtils.isEmpty(title) || TextUtils.isEmpty(quantity) || TextUtils.isEmpty(description) || TextUtils.isEmpty(pickupWindow) || TextUtils.isEmpty(latString) || TextUtils.isEmpty(lonString)) {
            Toast.makeText(this, "Please fill all fields", Toast.LENGTH_SHORT).show();
            return;
        }

        double latitude, longitude;
        try {
            latitude = Double.parseDouble(latString);
            longitude = Double.parseDouble(lonString);
        } catch (NumberFormatException e) {
            Toast.makeText(this, "Invalid latitude or longitude", Toast.LENGTH_SHORT).show();
            return;
        }

        String donorEmail = sharedPreferencesManager.getLoggedInUserEmail();
        if (donorEmail == null) {
            Toast.makeText(this, "Error: Donor not logged in", Toast.LENGTH_SHORT).show();
            return;
        }

        if (currentListing == null) {
            // New listing
            String id = UUID.randomUUID().toString();
            Listing newListing = new Listing(id, donorEmail, title, quantity, description, pickupWindow, latitude, longitude);
            sharedPreferencesManager.saveListing(newListing);
            Toast.makeText(this, "Listing added successfully!", Toast.LENGTH_SHORT).show();
            // Send notification for new listing
            NotificationHelper.sendNotification(this, "New Listing Added!", "A new food listing '" + title + "' is available.", newListing.hashCode());
            // Save to Firestore for nearby users (demo mode)
            firebaseDatabaseService.saveListing(newListing);
        } else {
            // Update existing listing
            Listing updatedListing = new Listing(currentListing.getId(), donorEmail, title, quantity, description, pickupWindow, latitude, longitude);
            sharedPreferencesManager.updateListing(updatedListing);
            Toast.makeText(this, "Listing updated successfully!", Toast.LENGTH_SHORT).show();
            // Send notification for updated listing (optional, but good for consistency)
            NotificationHelper.sendNotification(this, "Listing Updated!", "The listing '" + title + "' has been updated.", updatedListing.hashCode());
            // Save update to Firestore for nearby users (demo mode)
            firebaseDatabaseService.saveListing(updatedListing);
        }
        finish();
    }
}
