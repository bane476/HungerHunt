package com.foodrescue.app.view;

import android.os.Bundle;
import android.text.TextUtils;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TimePicker;
import android.widget.Toast;
import android.app.TimePickerDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar; // Import Toolbar
import com.foodrescue.app.R;
import com.foodrescue.app.data.SharedPreferencesManager;
import com.foodrescue.app.firebase.FirebaseDatabaseService;
import com.foodrescue.app.model.Listing;
import com.foodrescue.app.utils.NotificationHelper; // Import NotificationHelper
import com.google.gson.Gson;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Locale;
import java.util.UUID;

public class AddListingActivity extends AppCompatActivity {

    private static final double BASE_SIMULATED_LATITUDE = 28.6139;
    private static final double BASE_SIMULATED_LONGITUDE = 77.2090;

    private EditText editTextTitle, editTextQuantity, editTextPrice, editTextDescription, editTextPickupWindow;
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
        editTextPrice = findViewById(R.id.editTextPrice);
        editTextDescription = findViewById(R.id.editTextDescription);
        editTextPickupWindow = findViewById(R.id.editTextPickupWindow);
        buttonAddListing = findViewById(R.id.buttonAddListing);

        setupPickupWindowPicker();

        // Check if we are in edit mode
        String listingJson = getIntent().getStringExtra("listing");
        if (listingJson != null) {
            Gson gson = new Gson();
            currentListing = gson.fromJson(listingJson, Listing.class);
            if (currentListing != null) {
                editTextTitle.setText(currentListing.getTitle());
                editTextQuantity.setText(currentListing.getQuantity());
                editTextPrice.setText(currentListing.getPrice());
                editTextDescription.setText(currentListing.getDescription());
                editTextPickupWindow.setText(currentListing.getPickupWindow());
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
        String price = editTextPrice.getText().toString().trim();
        String description = editTextDescription.getText().toString().trim();
        String pickupWindow = editTextPickupWindow.getText().toString().trim();

        if (TextUtils.isEmpty(title) || TextUtils.isEmpty(quantity) || TextUtils.isEmpty(price) || TextUtils.isEmpty(description) || TextUtils.isEmpty(pickupWindow)) {
            Toast.makeText(this, "Please fill all fields", Toast.LENGTH_SHORT).show();
            return;
        }

        String donorEmail = sharedPreferencesManager.getLoggedInUserEmail();
        if (donorEmail == null) {
            Toast.makeText(this, "Error: Business account not logged in", Toast.LENGTH_SHORT).show();
            return;
        }
        String businessName = "";
        if (sharedPreferencesManager.getUser(donorEmail) != null) {
            businessName = sharedPreferencesManager.getUser(donorEmail).getBusinessName();
        }

        double[] simulatedCoordinates = getSimulatedCoordinatesForDonor(donorEmail);
        double latitude = simulatedCoordinates[0];
        double longitude = simulatedCoordinates[1];

        if (currentListing == null) {
            // New listing
            String id = UUID.randomUUID().toString();
            Listing newListing = new Listing(id, donorEmail, businessName, title, quantity, price, description, pickupWindow, latitude, longitude, false, null, null);
            sharedPreferencesManager.saveListing(newListing);
            Toast.makeText(this, "Listing added successfully!", Toast.LENGTH_SHORT).show();
            // Send notification for new listing
            NotificationHelper.sendNotification(this, "New Listing Added!", "A new food listing '" + title + "' is available.", newListing.hashCode());
            // Save to Firestore for nearby users (demo mode)
            firebaseDatabaseService.saveListing(newListing);
        } else {
            // Update existing listing
            Listing updatedListing = new Listing(
                    currentListing.getId(),
                    donorEmail,
                    businessName,
                    title,
                    quantity,
                    price,
                    description,
                    pickupWindow,
                    latitude,
                    longitude,
                    currentListing.isClaimed(),
                    currentListing.getClaimedByEmail(),
                    currentListing.getPaymentMethod()
            );
            sharedPreferencesManager.updateListing(updatedListing);
            Toast.makeText(this, "Listing updated successfully!", Toast.LENGTH_SHORT).show();
            // Send notification for updated listing (optional, but good for consistency)
            NotificationHelper.sendNotification(this, "Listing Updated!", "The listing '" + title + "' has been updated.", updatedListing.hashCode());
            // Save update to Firestore for nearby users (demo mode)
            firebaseDatabaseService.saveListing(updatedListing);
        }
        finish();
    }

    private void setupPickupWindowPicker() {
        editTextPickupWindow.setOnClickListener(v -> showPickupWindowPicker());
        editTextPickupWindow.setOnFocusChangeListener((v, hasFocus) -> {
            if (hasFocus) {
                showPickupWindowPicker();
            }
        });
    }

    private void showPickupWindowPicker() {
        showStartTimePicker();
    }

    private void showStartTimePicker() {
        final Calendar startTime = Calendar.getInstance();
        TimePickerDialog startTimePicker = new TimePickerDialog(
                this,
                (TimePicker view, int hourOfDay, int minute) -> {
                    startTime.set(Calendar.HOUR_OF_DAY, hourOfDay);
                    startTime.set(Calendar.MINUTE, minute);
                    showEndTimePicker(startTime);
                },
                startTime.get(Calendar.HOUR_OF_DAY),
                startTime.get(Calendar.MINUTE),
                true
        );
        startTimePicker.show();
    }

    private void showEndTimePicker(Calendar startTime) {
        final Calendar endTime = Calendar.getInstance();
        TimePickerDialog endTimePicker = new TimePickerDialog(
                this,
                (TimePicker view, int hourOfDay, int minute) -> {
                    endTime.set(Calendar.HOUR_OF_DAY, hourOfDay);
                    endTime.set(Calendar.MINUTE, minute);
                    if (!endTime.after(startTime)) {
                        Toast.makeText(this, "End time must be after start time", Toast.LENGTH_SHORT).show();
                        return;
                    }
                    SimpleDateFormat timeFormat = new SimpleDateFormat("HH:mm", Locale.getDefault());
                    String pickupWindow = timeFormat.format(startTime.getTime()) + " - "
                            + timeFormat.format(endTime.getTime());
                    editTextPickupWindow.setText(pickupWindow);
                },
                startTime.get(Calendar.HOUR_OF_DAY),
                startTime.get(Calendar.MINUTE),
                true
        );
        endTimePicker.show();
    }

    private double[] getSimulatedCoordinatesForDonor(String donorEmail) {
        int hash = Math.abs(donorEmail.hashCode());
        double latOffset = ((hash % 1000) - 500) / 50000.0;
        double lonOffset = (((hash / 1000) % 1000) - 500) / 50000.0;
        return new double[]{BASE_SIMULATED_LATITUDE + latOffset, BASE_SIMULATED_LONGITUDE + lonOffset};
    }
}
