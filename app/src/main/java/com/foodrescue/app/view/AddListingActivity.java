package com.foodrescue.app.view;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar; // Import Toolbar
import com.foodrescue.app.R;
import com.foodrescue.app.data.SharedPreferencesManager;
import com.foodrescue.app.firebase.FirebaseDatabaseService;
import com.foodrescue.app.firebase.FirebaseStorageService;
import com.foodrescue.app.model.Listing;
import com.foodrescue.app.utils.LocationHelper;
import com.foodrescue.app.utils.NotificationHelper; // Import NotificationHelper
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.textfield.TextInputLayout;
import com.google.android.material.timepicker.MaterialTimePicker;
import com.google.android.material.timepicker.TimeFormat;
import com.google.gson.Gson;
import com.bumptech.glide.Glide;
import java.util.Calendar;
import java.util.Locale;
import java.util.UUID;

public class AddListingActivity extends AppCompatActivity {
    private static final int LOCATION_PERMISSION_REQUEST_CODE = 301;
    private static final double BASE_SIMULATED_LATITUDE = 28.6139;
    private static final double BASE_SIMULATED_LONGITUDE = 77.2090;

    private EditText editTextTitle, editTextQuantity, editTextPrice, editTextDescription, editTextPickupWindow;
    private Button buttonAddListing, buttonUseCurrentLocation, buttonSelectListingImage, buttonRemoveListingImage;
    private TextView textViewLocationStatus, textViewSavingStatus;
    private ImageView imageViewListingPreview;
    private LinearLayout layoutSavingState;
    private TextInputLayout layoutTitle, layoutQuantity, layoutPrice, layoutDescription, layoutPickupWindow;
    private SharedPreferencesManager sharedPreferencesManager;
    private Listing currentListing; // To hold the listing if we are in edit mode
    private Toolbar toolbar; // Declare Toolbar
    private FirebaseDatabaseService firebaseDatabaseService;
    private FirebaseStorageService firebaseStorageService;
    private Double selectedLatitude;
    private Double selectedLongitude;
    private Calendar pickupStartTime;
    private Calendar pickupEndTime;
    private String selectedImageUri;
    private String originalImageUri;
    private ActivityResultLauncher<String[]> imagePickerLauncher;

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
        firebaseStorageService = new FirebaseStorageService(this);
        NotificationHelper.createNotificationChannel(this);
        imagePickerLauncher = registerForActivityResult(new ActivityResultContracts.OpenDocument(), this::handleListingImageSelected);

        editTextTitle = findViewById(R.id.editTextTitle);
        editTextQuantity = findViewById(R.id.editTextQuantity);
        editTextPrice = findViewById(R.id.editTextPrice);
        editTextDescription = findViewById(R.id.editTextDescription);
        editTextPickupWindow = findViewById(R.id.editTextPickupWindow);
        layoutTitle = findViewById(R.id.layoutTitle);
        layoutQuantity = findViewById(R.id.layoutQuantity);
        layoutPrice = findViewById(R.id.layoutPrice);
        layoutDescription = findViewById(R.id.layoutDescription);
        layoutPickupWindow = findViewById(R.id.layoutPickupWindow);
        buttonAddListing = findViewById(R.id.buttonAddListing);
        buttonUseCurrentLocation = findViewById(R.id.buttonUseCurrentLocation);
        buttonSelectListingImage = findViewById(R.id.buttonSelectListingImage);
        buttonRemoveListingImage = findViewById(R.id.buttonRemoveListingImage);
        textViewLocationStatus = findViewById(R.id.textViewLocationStatus);
        textViewSavingStatus = findViewById(R.id.textViewSavingStatus);
        imageViewListingPreview = findViewById(R.id.imageViewListingPreview);
        layoutSavingState = findViewById(R.id.layoutSavingState);

        setupPickupWindowPicker();
        buttonUseCurrentLocation.setOnClickListener(v -> captureSellerLocation());
        buttonSelectListingImage.setOnClickListener(v -> imagePickerLauncher.launch(new String[]{"image/*"}));
        buttonRemoveListingImage.setOnClickListener(v -> {
            selectedImageUri = null;
            renderListingImagePreview();
        });

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
                restorePickupTimes(currentListing.getPickupWindow());
                selectedImageUri = currentListing.getImageUri();
                originalImageUri = currentListing.getImageUri();
                renderListingImagePreview();
                selectedLatitude = currentListing.getLatitude();
                selectedLongitude = currentListing.getLongitude();
                updateLocationStatus();
                buttonAddListing.setText("Update Listing"); // Change button text
                if (getSupportActionBar() != null) {
                    getSupportActionBar().setTitle("Edit Listing"); // Set toolbar title for edit mode
                }
            }
        } else {
            if (getSupportActionBar() != null) {
                getSupportActionBar().setTitle("Add Listing"); // Set toolbar title for add mode
            }
            preloadStoredLocation();
            originalImageUri = null;
            renderListingImagePreview();
            updateLocationStatus();
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
        clearValidationErrors();

        String title = editTextTitle.getText().toString().trim();
        String quantity = editTextQuantity.getText().toString().trim();
        String price = editTextPrice.getText().toString().trim();
        String description = editTextDescription.getText().toString().trim();
        String pickupWindow = editTextPickupWindow.getText().toString().trim();

        if (!validateListingForm(title, quantity, price, description, pickupWindow)) {
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

        double[] listingCoordinates = resolveListingCoordinates(donorEmail);
        double latitude = listingCoordinates[0];
        double longitude = listingCoordinates[1];

        Listing listingToPersist;
        boolean isNewListing = currentListing == null;

        if (isNewListing) {
            String id = UUID.randomUUID().toString();
            listingToPersist = new Listing(id, donorEmail, businessName, title, quantity, price, description, pickupWindow, latitude, longitude, false, null, null);
        } else {
            listingToPersist = new Listing(
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
            listingToPersist.setOrderStatus(currentListing.getOrderStatus());
        }
        setSavingState(true, "Preparing listing...");
        persistListingWithImage(listingToPersist, isNewListing);
    }

    private void captureSellerLocation() {
        String donorEmail = sharedPreferencesManager.getLoggedInUserEmail();
        if (!LocationHelper.hasLocationPermission(this)) {
            LocationHelper.requestLocationPermission(this, LOCATION_PERMISSION_REQUEST_CODE);
            return;
        }

        buttonUseCurrentLocation.setEnabled(false);
        textViewLocationStatus.setText("Fetching current seller location...");
        LocationHelper.fetchLastLocation(this, location -> runOnUiThread(() -> {
            buttonUseCurrentLocation.setEnabled(true);
            if (location == null) {
                Toast.makeText(this, "Could not fetch current location. Using fallback location.", Toast.LENGTH_SHORT).show();
                updateLocationStatus();
                return;
            }

            selectedLatitude = location.getLatitude();
            selectedLongitude = location.getLongitude();
            if (donorEmail != null) {
                sharedPreferencesManager.saveUserLocation(donorEmail, selectedLatitude, selectedLongitude);
            }
            updateLocationStatus();
            Toast.makeText(this, "Seller location captured for navigation.", Toast.LENGTH_SHORT).show();
        }));
    }

    private double[] resolveListingCoordinates(String donorEmail) {
        if (selectedLatitude != null && selectedLongitude != null) {
            return new double[]{selectedLatitude, selectedLongitude};
        }

        if (currentListing != null && hasValidCoordinates(currentListing.getLatitude(), currentListing.getLongitude())) {
            return new double[]{currentListing.getLatitude(), currentListing.getLongitude()};
        }

        return getSimulatedCoordinatesForDonor(donorEmail);
    }

    private void updateLocationStatus() {
        if (selectedLatitude != null && selectedLongitude != null && hasValidCoordinates(selectedLatitude, selectedLongitude)) {
            textViewLocationStatus.setText(String.format(Locale.getDefault(),
                    "Saved seller location: %.5f, %.5f",
                    selectedLatitude,
                    selectedLongitude));
            return;
        }

        if (currentListing != null && hasValidCoordinates(currentListing.getLatitude(), currentListing.getLongitude())) {
            textViewLocationStatus.setText(String.format(Locale.getDefault(),
                    "Using saved seller location: %.5f, %.5f",
                    currentListing.getLatitude(),
                    currentListing.getLongitude()));
            return;
        }

        textViewLocationStatus.setText("Using fallback seller location. Tap the button to use current location.");
    }

    private boolean hasValidCoordinates(double latitude, double longitude) {
        return !Double.isNaN(latitude)
                && !Double.isNaN(longitude)
                && latitude >= -90
                && latitude <= 90
                && longitude >= -180
                && longitude <= 180
                && !(latitude == 0d && longitude == 0d);
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
        ensurePickupTimesInitialized();

        View dialogView = getLayoutInflater().inflate(R.layout.dialog_pickup_window, null);
        TextView textViewStartValue = dialogView.findViewById(R.id.textViewPickupStartValue);
        TextView textViewEndValue = dialogView.findViewById(R.id.textViewPickupEndValue);
        TextView textViewValidation = dialogView.findViewById(R.id.textViewPickupValidation);
        Button buttonStart = dialogView.findViewById(R.id.buttonSelectPickupStart);
        Button buttonEnd = dialogView.findViewById(R.id.buttonSelectPickupEnd);

        Runnable renderState = () -> {
            textViewStartValue.setText(formatTime(pickupStartTime));
            textViewEndValue.setText(formatTime(pickupEndTime));
            if (pickupEndTime.after(pickupStartTime)) {
                textViewValidation.setText("Pickup window will be saved as " + buildPickupWindowText());
            } else {
                textViewValidation.setText("End time must be after start time.");
            }
        };

        buttonStart.setOnClickListener(v -> showTimePicker("Pickup starts", pickupStartTime, selectedTime -> {
            pickupStartTime = selectedTime;
            if (!pickupEndTime.after(pickupStartTime)) {
                pickupEndTime = (Calendar) pickupStartTime.clone();
                pickupEndTime.add(Calendar.HOUR_OF_DAY, 1);
            }
            renderState.run();
        }));

        buttonEnd.setOnClickListener(v -> showTimePicker("Pickup ends", pickupEndTime, selectedTime -> {
            pickupEndTime = selectedTime;
            renderState.run();
        }));

        renderState.run();

        new MaterialAlertDialogBuilder(this)
                .setTitle("Set Pickup Window")
                .setView(dialogView)
                .setNegativeButton("Cancel", null)
                .setPositiveButton("Save", (dialog, which) -> {
                    if (!pickupEndTime.after(pickupStartTime)) {
                        Toast.makeText(this, "End time must be after start time", Toast.LENGTH_SHORT).show();
                        return;
                    }
                    editTextPickupWindow.setText(buildPickupWindowText());
                })
                .show();
    }

    private void showTimePicker(String title, Calendar initialTime, TimeSelectionListener listener) {
        MaterialTimePicker picker = new MaterialTimePicker.Builder()
                .setTimeFormat(TimeFormat.CLOCK_12H)
                .setHour(initialTime.get(Calendar.HOUR_OF_DAY))
                .setMinute(initialTime.get(Calendar.MINUTE))
                .setTitleText(title)
                .build();
        picker.addOnPositiveButtonClickListener(v -> {
            Calendar selectedTime = (Calendar) initialTime.clone();
            selectedTime.set(Calendar.HOUR_OF_DAY, picker.getHour());
            selectedTime.set(Calendar.MINUTE, picker.getMinute());
            selectedTime.set(Calendar.SECOND, 0);
            selectedTime.set(Calendar.MILLISECOND, 0);
            listener.onTimeSelected(selectedTime);
        });
        picker.show(getSupportFragmentManager(), title);
    }

    private double[] getSimulatedCoordinatesForDonor(String donorEmail) {
        int hash = Math.abs(donorEmail.hashCode());
        double latOffset = ((hash % 1000) - 500) / 50000.0;
        double lonOffset = (((hash / 1000) % 1000) - 500) / 50000.0;
        return new double[]{BASE_SIMULATED_LATITUDE + latOffset, BASE_SIMULATED_LONGITUDE + lonOffset};
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == LOCATION_PERMISSION_REQUEST_CODE) {
            if (LocationHelper.hasLocationPermission(this)) {
                captureSellerLocation();
            } else {
                Toast.makeText(this, "Location permission denied. Using fallback seller location.", Toast.LENGTH_SHORT).show();
                updateLocationStatus();
            }
        }
    }

    private void preloadStoredLocation() {
        String donorEmail = sharedPreferencesManager.getLoggedInUserEmail();
        if (donorEmail == null) {
            return;
        }
        double[] savedLocation = sharedPreferencesManager.getUserLocation(donorEmail);
        if (savedLocation == null || savedLocation.length < 2) {
            return;
        }
        selectedLatitude = savedLocation[0];
        selectedLongitude = savedLocation[1];
    }

    private void ensurePickupTimesInitialized() {
        if (pickupStartTime == null) {
            pickupStartTime = Calendar.getInstance();
            pickupStartTime.set(Calendar.SECOND, 0);
            pickupStartTime.set(Calendar.MILLISECOND, 0);
        }
        if (pickupEndTime == null) {
            pickupEndTime = (Calendar) pickupStartTime.clone();
            pickupEndTime.add(Calendar.HOUR_OF_DAY, 1);
        }
    }

    private void restorePickupTimes(String pickupWindow) {
        if (TextUtils.isEmpty(pickupWindow) || !pickupWindow.contains(" - ")) {
            return;
        }
        String[] parts = pickupWindow.split(" - ");
        if (parts.length != 2) {
            return;
        }
        Calendar restoredStart = parseTime(parts[0].trim());
        Calendar restoredEnd = parseTime(parts[1].trim());
        if (restoredStart != null && restoredEnd != null) {
            pickupStartTime = restoredStart;
            pickupEndTime = restoredEnd;
        }
    }

    private Calendar parseTime(String value) {
        String[] parts = value.split(":");
        if (parts.length != 2) {
            return null;
        }
        try {
            int hour = Integer.parseInt(parts[0]);
            int minute = Integer.parseInt(parts[1]);
            Calendar parsed = Calendar.getInstance();
            parsed.set(Calendar.HOUR_OF_DAY, hour);
            parsed.set(Calendar.MINUTE, minute);
            parsed.set(Calendar.SECOND, 0);
            parsed.set(Calendar.MILLISECOND, 0);
            return parsed;
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private String buildPickupWindowText() {
        return formatTime(pickupStartTime) + " - " + formatTime(pickupEndTime);
    }

    private String formatTime(Calendar calendar) {
        return String.format(Locale.getDefault(), "%02d:%02d",
                calendar.get(Calendar.HOUR_OF_DAY),
                calendar.get(Calendar.MINUTE));
    }

    private interface TimeSelectionListener {
        void onTimeSelected(Calendar selectedTime);
    }

    private void handleListingImageSelected(Uri uri) {
        if (uri == null) {
            return;
        }
        try {
            getContentResolver().takePersistableUriPermission(
                    uri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION
            );
        } catch (SecurityException ignored) {
        }
        selectedImageUri = uri.toString();
        renderListingImagePreview();
    }

    private void renderListingImagePreview() {
        if (TextUtils.isEmpty(selectedImageUri)) {
            Glide.with(this)
                    .load(R.drawable.ic_launcher_foreground)
                    .into(imageViewListingPreview);
            buttonRemoveListingImage.setVisibility(View.GONE);
            return;
        }
        Glide.with(this)
                .load(selectedImageUri)
                .placeholder(R.drawable.ic_launcher_foreground)
                .error(R.drawable.ic_launcher_foreground)
                .into(imageViewListingPreview);
        buttonRemoveListingImage.setVisibility(View.VISIBLE);
    }

    private void persistListingWithImage(Listing listing, boolean isNewListing) {
        if (shouldUploadImageToFirebase()) {
            setSavingState(true, "Uploading image...");
            firebaseStorageService.uploadListingImage(listing.getId(), Uri.parse(selectedImageUri))
                    .addOnSuccessListener(downloadUrl -> {
                        listing.setImageUri(downloadUrl);
                        cleanupReplacedRemoteImageIfNeeded(downloadUrl);
                        saveListingRecord(listing, isNewListing, false);
                    })
                    .addOnFailureListener(e -> {
                        listing.setImageUri(selectedImageUri);
                        saveListingRecord(listing, isNewListing, true);
                    });
            return;
        }

        listing.setImageUri(selectedImageUri);
        setSavingState(true, "Saving listing...");
        cleanupRemovedRemoteImageIfNeeded();
        saveListingRecord(listing, isNewListing, false);
    }

    private boolean shouldUploadImageToFirebase() {
        return !TextUtils.isEmpty(selectedImageUri)
                && selectedImageUri.startsWith("content://")
                && firebaseStorageService.isFirebaseConfigured();
    }

    private void saveListingRecord(Listing listing, boolean isNewListing, boolean imageUploadFailed) {
        if (isNewListing) {
            sharedPreferencesManager.saveListing(listing);
            NotificationHelper.sendNotification(this, "New Listing Added!", "A new food listing '" + listing.getTitle() + "' is available.", listing.hashCode());
        } else {
            sharedPreferencesManager.updateListing(listing);
            NotificationHelper.sendNotification(this, "Listing Updated!", "The listing '" + listing.getTitle() + "' has been updated.", listing.hashCode());
        }

        setSavingState(false, null);
        originalImageUri = listing.getImageUri();

        if (imageUploadFailed) {
            Toast.makeText(this, "Listing saved. Image upload failed, so cloud image sync is incomplete.", Toast.LENGTH_LONG).show();
        } else {
            Toast.makeText(this, isNewListing ? "Listing added successfully!" : "Listing updated successfully!", Toast.LENGTH_SHORT).show();
        }

        firebaseDatabaseService.saveListing(listing)
                .addOnFailureListener(e -> runOnUiThread(() -> Toast.makeText(
                        this,
                        imageUploadFailed
                                ? "Listing saved on this device, but image upload and cloud sync failed."
                                : "Listing saved on this device, but cloud sync failed.",
                        Toast.LENGTH_LONG
                ).show()));

        finish();
    }

    private void cleanupRemovedRemoteImageIfNeeded() {
        if (!TextUtils.isEmpty(selectedImageUri)) {
            return;
        }
        if (TextUtils.isEmpty(originalImageUri) || !firebaseStorageService.isRemoteStorageUrl(originalImageUri)) {
            return;
        }
        firebaseStorageService.deleteListingImage(currentListing.getId());
    }

    private void cleanupReplacedRemoteImageIfNeeded(String newImageUrl) {
        if (TextUtils.isEmpty(originalImageUri) || currentListing == null) {
            return;
        }
        if (!firebaseStorageService.isRemoteStorageUrl(originalImageUri)) {
            return;
        }
        if (originalImageUri.equals(newImageUrl)) {
            return;
        }
        firebaseStorageService.deleteImageByUrl(originalImageUri);
    }

    private void setSavingState(boolean isSaving, String statusText) {
        buttonAddListing.setEnabled(!isSaving);
        buttonUseCurrentLocation.setEnabled(!isSaving);
        buttonSelectListingImage.setEnabled(!isSaving);
        buttonRemoveListingImage.setEnabled(!isSaving && !TextUtils.isEmpty(selectedImageUri));
        layoutSavingState.setVisibility(isSaving ? View.VISIBLE : View.GONE);
        if (statusText != null) {
            textViewSavingStatus.setText(statusText);
        } else {
            textViewSavingStatus.setText("");
        }
    }

    private boolean validateListingForm(String title, String quantity, String price, String description, String pickupWindow) {
        boolean isValid = true;

        if (TextUtils.isEmpty(title)) {
            layoutTitle.setError("Enter a listing title.");
            isValid = false;
        }

        if (TextUtils.isEmpty(quantity)) {
            layoutQuantity.setError("Enter available quantity.");
            isValid = false;
        } else {
            try {
                int parsedQuantity = Integer.parseInt(quantity);
                if (parsedQuantity <= 0) {
                    layoutQuantity.setError("Quantity must be greater than 0.");
                    isValid = false;
                }
            } catch (NumberFormatException e) {
                layoutQuantity.setError("Enter a valid whole number.");
                isValid = false;
            }
        }

        if (TextUtils.isEmpty(price)) {
            layoutPrice.setError("Enter a price.");
            isValid = false;
        } else {
            try {
                double parsedPrice = Double.parseDouble(price);
                if (parsedPrice < 0) {
                    layoutPrice.setError("Price cannot be negative.");
                    isValid = false;
                }
            } catch (NumberFormatException e) {
                layoutPrice.setError("Enter a valid price.");
                isValid = false;
            }
        }

        if (TextUtils.isEmpty(description)) {
            layoutDescription.setError("Enter a short description.");
            isValid = false;
        } else if (description.length() < 10) {
            layoutDescription.setError("Description should be at least 10 characters.");
            isValid = false;
        }

        if (TextUtils.isEmpty(pickupWindow)) {
            layoutPickupWindow.setError("Select a pickup window.");
            isValid = false;
        }

        if (!isValid) {
            Toast.makeText(this, "Fix the highlighted fields.", Toast.LENGTH_SHORT).show();
        }

        return isValid;
    }

    private void clearValidationErrors() {
        layoutTitle.setError(null);
        layoutTitle.setErrorEnabled(false);
        layoutQuantity.setError(null);
        layoutQuantity.setErrorEnabled(false);
        layoutPrice.setError(null);
        layoutPrice.setErrorEnabled(false);
        layoutDescription.setError(null);
        layoutDescription.setErrorEnabled(false);
        layoutPickupWindow.setError(null);
        layoutPickupWindow.setErrorEnabled(false);
    }
}
