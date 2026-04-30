package com.foodrescue.app.view;

import android.content.Intent;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.NumberPicker;
import android.widget.TextView;
import android.widget.ImageView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.widget.Toolbar; // Import Toolbar
import androidx.core.content.ContextCompat;

import com.foodrescue.app.R;
import com.foodrescue.app.data.SharedPreferencesManager;
import com.foodrescue.app.firebase.FirebaseDatabaseService;
import com.foodrescue.app.firebase.FirebaseStorageService;
import com.foodrescue.app.model.Listing;
import com.foodrescue.app.model.User;
import com.foodrescue.app.utils.NotificationHelper; // Import NotificationHelper
import com.google.android.material.chip.Chip;
import com.google.android.gms.tasks.Tasks;
import com.google.gson.Gson;
import com.bumptech.glide.Glide;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class ListingDetailsActivity extends AppCompatActivity {
    private TextView textViewDetailTitle, textViewDetailDonorEmail, textViewDetailQuantity,
            textViewDetailPrice, textViewDetailDescription, textViewDetailPickupWindow, textViewClaimStatus, textViewDeleteStatus;
    private ImageView imageViewListingDetail;
    private Button buttonClaimListing, buttonEditListing, buttonDeleteListing, buttonUpdateOrderStatus;
    private Chip chipOrderStatus;
    private LinearLayout layoutDeleteState;
    private SharedPreferencesManager sharedPreferencesManager;
    private Listing currentListing; // Store the current listing
    private String loggedInUserEmail;
    private User loggedInUser;
    private Toolbar toolbar; // Declare Toolbar
    private FirebaseDatabaseService firebaseDatabaseService;
    private FirebaseStorageService firebaseStorageService;

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
        firebaseStorageService = new FirebaseStorageService(this);

        textViewDetailTitle = findViewById(R.id.textViewDetailTitle);
        imageViewListingDetail = findViewById(R.id.imageViewListingDetail);
        textViewDetailDonorEmail = findViewById(R.id.textViewDetailDonorEmail);
        textViewDetailQuantity = findViewById(R.id.textViewDetailQuantity);
        textViewDetailPrice = findViewById(R.id.textViewDetailPrice);
        textViewDetailDescription = findViewById(R.id.textViewDetailDescription);
        textViewDetailPickupWindow = findViewById(R.id.textViewDetailPickupWindow);
        textViewClaimStatus = findViewById(R.id.textViewClaimStatus); // Initialize from XML
        textViewDeleteStatus = findViewById(R.id.textViewDeleteStatus);
        chipOrderStatus = findViewById(R.id.chipOrderStatus);
        buttonClaimListing = findViewById(R.id.buttonClaimListing);
        buttonEditListing = findViewById(R.id.buttonEditListing);
        buttonDeleteListing = findViewById(R.id.buttonDeleteListing);
        buttonUpdateOrderStatus = findViewById(R.id.buttonUpdateOrderStatus);
        layoutDeleteState = findViewById(R.id.layoutDeleteState);

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
                String businessName = TextUtils.isEmpty(currentListing.getBusinessName())
                        ? currentListing.getDonorEmail()
                        : currentListing.getBusinessName();
                textViewDetailDonorEmail.setText("Business: " + businessName);
                textViewDetailQuantity.setText("Quantity: " + currentListing.getQuantity());
                textViewDetailPrice.setText("Price: " + (TextUtils.isEmpty(currentListing.getPrice()) ? "Free" : currentListing.getPrice()));
                textViewDetailDescription.setText("Description: " + currentListing.getDescription());
                textViewDetailPickupWindow.setText("Pickup Window: " + currentListing.getPickupWindow());
                renderListingImage();

                updateClaimStatusUI(); // Call method to update UI based on claim status

                if (loggedInUserEmail != null && loggedInUserEmail.equals(currentListing.getDonorEmail())) {
                    // Logged-in user is the donor
                    buttonEditListing.setVisibility(View.VISIBLE);
                    buttonDeleteListing.setVisibility(View.VISIBLE);
                    buttonClaimListing.setVisibility(View.GONE); // Donor cannot claim their own listing
                } else if (loggedInUser != null && isCustomerRole(loggedInUser.getRole())) {
                    // Logged-in user is a receiver
                    buttonEditListing.setVisibility(View.GONE);
                    buttonDeleteListing.setVisibility(View.GONE);
                    if (currentListing.isClaimed() || getAvailableQuantity() <= 0) {
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
                        confirmDeleteListing();
                    }
                });

                buttonUpdateOrderStatus.setOnClickListener(v -> advanceOrderStatus());
                updateOrderStatusButtonVisibility();
            }
        } else {
            Toast.makeText(this, "Listing details not found.", Toast.LENGTH_SHORT).show();
            finish();
        }

        buttonClaimListing.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (loggedInUser != null && isCustomerRole(loggedInUser.getRole()) && !currentListing.isClaimed()) {
                    showQuantityPicker();
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
            claimStatus += "\nStatus: " + getCurrentOrderStatus();
            textViewClaimStatus.setText(claimStatus);
            textViewClaimStatus.setVisibility(View.VISIBLE);
            updateOrderStatusChip();
        } else {
            textViewClaimStatus.setText("Available quantity: " + getAvailableQuantity());
            textViewClaimStatus.setVisibility(View.VISIBLE);
            chipOrderStatus.setVisibility(View.GONE);
        }
    }

    private void showQuantityPicker() {
        int availableQuantity = getAvailableQuantity();
        if (availableQuantity <= 0) {
            Toast.makeText(this, "No quantity available to claim.", Toast.LENGTH_SHORT).show();
            return;
        }

        final NumberPicker quantityPicker = new NumberPicker(this);
        quantityPicker.setMinValue(1);
        quantityPicker.setMaxValue(availableQuantity);
        quantityPicker.setValue(1);
        quantityPicker.setWrapSelectorWheel(false);

        new AlertDialog.Builder(this)
                .setTitle("Select Quantity")
                .setView(quantityPicker)
                .setPositiveButton("Next", (dialog, which) -> {
                    int requestedQuantity = quantityPicker.getValue();
                    showPaymentMethodPickerAndClaim(requestedQuantity);
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void showPaymentMethodPickerAndClaim(int claimedQuantity) {
        final String[] paymentMethods = {"Cash", "UPI", "Card"};
        new AlertDialog.Builder(this)
                .setTitle("Select Payment Method")
                .setItems(paymentMethods, (dialog, which) -> completeClaim(paymentMethods[which], claimedQuantity))
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void completeClaim(String paymentMethod, int claimedQuantity) {
        int availableQuantity = getAvailableQuantity();
        int remainingQuantity = availableQuantity - claimedQuantity;

        currentListing.setQuantity(String.valueOf(Math.max(remainingQuantity, 0)));
        if (remainingQuantity <= 0) {
            currentListing.setClaimed(true);
            currentListing.setClaimedByEmail(loggedInUserEmail);
            currentListing.setPaymentMethod(paymentMethod);
            currentListing.setOrderStatus("CLAIMED");
        } else {
            currentListing.setClaimed(false);
            currentListing.setClaimedByEmail(null);
            currentListing.setPaymentMethod(null);
            currentListing.setOrderStatus(null);
        }

        sharedPreferencesManager.updateListing(currentListing);
        firebaseDatabaseService.saveListing(currentListing);
        recordOrderHistory(paymentMethod, claimedQuantity, remainingQuantity);

        String message = remainingQuantity > 0
                ? "Claimed " + claimedQuantity + ". Remaining: " + remainingQuantity
                : "Listing claimed successfully!";
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show();

        textViewDetailQuantity.setText("Quantity: " + currentListing.getQuantity());
        updateClaimStatusUI();
        if (remainingQuantity <= 0) {
            buttonClaimListing.setVisibility(View.GONE);
        }
        updateOrderStatusButtonVisibility();
        NotificationHelper.sendNotification(
                this,
                "Listing Claimed!",
                currentListing.getTitle() + " (" + claimedQuantity + ") claimed by " + loggedInUserEmail + " via " + paymentMethod + ".",
                currentListing.hashCode()
        );
    }

    private void recordOrderHistory(String paymentMethod, int claimedQuantity, int remainingQuantity) {
        String price = TextUtils.isEmpty(currentListing.getPrice()) ? "Free" : currentListing.getPrice();
        String claimedAt = new SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()).format(new Date());

        String receiverEntry = currentListing.getTitle()
                + " | Claimed Qty: " + claimedQuantity
                + " | Price: " + price
                + " | Business: " + (TextUtils.isEmpty(currentListing.getBusinessName()) ? currentListing.getDonorEmail() : currentListing.getBusinessName())
                + " | Payment: " + paymentMethod
                + " | Remaining: " + Math.max(remainingQuantity, 0)
                + " | Time: " + claimedAt;

        String donorEntry = currentListing.getTitle()
                + " | Claimed Qty: " + claimedQuantity
                + " | Price: " + price
                + " | Claimed by: " + loggedInUserEmail
                + " | Payment: " + paymentMethod
                + " | Remaining: " + Math.max(remainingQuantity, 0)
                + " | Time: " + claimedAt;

        sharedPreferencesManager.addOrderHistoryEntry(loggedInUserEmail, "receiver", receiverEntry);
        sharedPreferencesManager.addOrderHistoryEntry(currentListing.getDonorEmail(), "donor", donorEntry);
    }

    private int getAvailableQuantity() {
        try {
            return Math.max(0, Integer.parseInt(currentListing.getQuantity()));
        } catch (NumberFormatException e) {
            return currentListing.isClaimed() ? 0 : 1;
        }
    }

    private String getCurrentOrderStatus() {
        return TextUtils.isEmpty(currentListing.getOrderStatus()) ? "CLAIMED" : currentListing.getOrderStatus();
    }

    private boolean canUserAdvanceStatus() {
        if (!currentListing.isClaimed() || loggedInUserEmail == null) {
            return false;
        }
        boolean isDonor = loggedInUserEmail.equals(currentListing.getDonorEmail());
        boolean isReceiver = loggedInUserEmail.equals(currentListing.getClaimedByEmail());
        return (isDonor || isReceiver) && !"COMPLETED".equalsIgnoreCase(getCurrentOrderStatus());
    }

    private void updateOrderStatusButtonVisibility() {
        if (canUserAdvanceStatus()) {
            buttonUpdateOrderStatus.setVisibility(View.VISIBLE);
            if ("CLAIMED".equalsIgnoreCase(getCurrentOrderStatus())) {
                buttonUpdateOrderStatus.setText("Mark as Picked Up");
            } else if ("PICKED_UP".equalsIgnoreCase(getCurrentOrderStatus())) {
                buttonUpdateOrderStatus.setText("Mark as Completed");
            } else {
                buttonUpdateOrderStatus.setText("Update Order Status");
            }
        } else {
            buttonUpdateOrderStatus.setVisibility(View.GONE);
        }
    }

    private void advanceOrderStatus() {
        if (!canUserAdvanceStatus()) {
            return;
        }

        String currentStatus = getCurrentOrderStatus();
        String nextStatus;
        if ("CLAIMED".equalsIgnoreCase(currentStatus)) {
            nextStatus = "PICKED_UP";
        } else if ("PICKED_UP".equalsIgnoreCase(currentStatus)) {
            nextStatus = "COMPLETED";
        } else {
            return;
        }

        currentListing.setOrderStatus(nextStatus);
        sharedPreferencesManager.updateListing(currentListing);
        firebaseDatabaseService.saveListing(currentListing);
        updateClaimStatusUI();
        updateOrderStatusButtonVisibility();

        String stamp = new SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()).format(new Date());
        String historyMessage = buildOrderStatusHistoryMessage(nextStatus, stamp);
        if (!TextUtils.isEmpty(currentListing.getClaimedByEmail())) {
            sharedPreferencesManager.addOrderHistoryEntry(currentListing.getClaimedByEmail(), "receiver", historyMessage);
        }
        sharedPreferencesManager.addOrderHistoryEntry(currentListing.getDonorEmail(), "donor", historyMessage);

        Toast.makeText(this, "Order status: " + nextStatus, Toast.LENGTH_SHORT).show();
    }

    private void updateOrderStatusChip() {
        String status = getCurrentOrderStatus();
        chipOrderStatus.setText(status);
        chipOrderStatus.setVisibility(View.VISIBLE);
        if ("CLAIMED".equalsIgnoreCase(status)) {
            chipOrderStatus.setChipBackgroundColorResource(R.color.status_claimed);
        } else if ("PICKED_UP".equalsIgnoreCase(status)) {
            chipOrderStatus.setChipBackgroundColorResource(R.color.status_picked_up);
        } else if ("COMPLETED".equalsIgnoreCase(status)) {
            chipOrderStatus.setChipBackgroundColorResource(R.color.status_completed);
        } else {
            chipOrderStatus.setChipBackgroundColorResource(R.color.text_secondary);
        }
        chipOrderStatus.setTextColor(ContextCompat.getColor(this, R.color.white));
    }

    private String buildOrderStatusHistoryMessage(String nextStatus, String stamp) {
        StringBuilder builder = new StringBuilder();
        builder.append(currentListing.getTitle())
                .append(" status changed to ")
                .append(nextStatus);

        if ("COMPLETED".equalsIgnoreCase(nextStatus)) {
            builder.append(" | Claimed Qty: ").append(resolveClaimedQuantityForHistory());
            builder.append(" | Price: ").append(TextUtils.isEmpty(currentListing.getPrice()) ? "Free" : currentListing.getPrice());
        }

        builder.append(" | Time: ").append(stamp);
        return builder.toString();
    }

    private int resolveClaimedQuantityForHistory() {
        List<String> donorHistory = sharedPreferencesManager.getOrderHistory(currentListing.getDonorEmail(), "donor");
        if (donorHistory == null || donorHistory.isEmpty()) {
            return 0;
        }

        String titlePrefix = currentListing.getTitle() + " | Claimed Qty:";
        for (String entry : donorHistory) {
            if (entry == null || !entry.startsWith(titlePrefix)) {
                continue;
            }

            java.util.regex.Matcher matcher = java.util.regex.Pattern.compile("Claimed Qty:\\s*(\\d+)").matcher(entry);
            if (matcher.find()) {
                try {
                    return Integer.parseInt(matcher.group(1));
                } catch (NumberFormatException ignored) {
                    return 0;
                }
            }
        }
        return 0;
    }

    private boolean isCustomerRole(String role) {
        return "Customer".equalsIgnoreCase(role) || "Receiver".equalsIgnoreCase(role);
    }

    private void renderListingImage() {
        if (currentListing == null || TextUtils.isEmpty(currentListing.getImageUri())) {
            Glide.with(this)
                    .load(R.drawable.ic_launcher_foreground)
                    .into(imageViewListingDetail);
            return;
        }
        Glide.with(this)
                .load(currentListing.getImageUri())
                .placeholder(R.drawable.ic_launcher_foreground)
                .error(R.drawable.ic_launcher_foreground)
                .into(imageViewListingDetail);
    }

    private void confirmDeleteListing() {
        new AlertDialog.Builder(this)
                .setTitle("Delete Listing")
                .setMessage("This will remove the listing and its uploaded image. This action cannot be undone.")
                .setNegativeButton("Cancel", null)
                .setPositiveButton("Delete", (dialog, which) -> deleteListing())
                .show();
    }

    private void deleteListing() {
        if (currentListing == null) {
            return;
        }

        setDeleteState(true, "Deleting listing...");
        sharedPreferencesManager.deleteListing(currentListing.getId());

        Tasks.whenAllComplete(
                firebaseDatabaseService.deleteListing(currentListing.getId()),
                firebaseStorageService.deleteListingImage(currentListing.getId())
        ).addOnCompleteListener(task -> runOnUiThread(() -> {
            setDeleteState(false, null);
            boolean allSuccessful = true;
            if (task.getResult() != null) {
                for (com.google.android.gms.tasks.Task<?> completedTask : task.getResult()) {
                    if (!completedTask.isSuccessful()) {
                        allSuccessful = false;
                        break;
                    }
                }
            }

            if (allSuccessful) {
                Toast.makeText(this, "Listing deleted!", Toast.LENGTH_SHORT).show();
            } else {
                Toast.makeText(this, "Listing deleted on this device, but some cloud cleanup failed.", Toast.LENGTH_LONG).show();
            }
            finish();
        }));
    }

    private void setDeleteState(boolean isDeleting, String statusText) {
        layoutDeleteState.setVisibility(isDeleting ? View.VISIBLE : View.GONE);
        if (statusText != null) {
            textViewDeleteStatus.setText(statusText);
        }
        buttonDeleteListing.setEnabled(!isDeleting);
        buttonEditListing.setEnabled(!isDeleting);
        buttonClaimListing.setEnabled(!isDeleting);
        buttonUpdateOrderStatus.setEnabled(!isDeleting);
    }
}
