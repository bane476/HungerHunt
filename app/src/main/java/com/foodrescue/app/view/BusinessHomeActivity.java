package com.foodrescue.app.view;

import android.content.Intent;
import android.os.Bundle;
import android.text.SpannableString;
import android.text.Spanned;
import android.text.style.ForegroundColorSpan;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar; // Import Toolbar
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.foodrescue.app.R;
import com.foodrescue.app.adapters.ListingAdapter;
import com.foodrescue.app.data.SharedPreferencesManager;
import com.foodrescue.app.model.Listing;
import com.google.android.material.floatingactionbutton.FloatingActionButton;
import com.google.gson.Gson;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;

public class BusinessHomeActivity extends AppCompatActivity implements ListingAdapter.OnListingClickListener {
    private static final DateTimeFormatter HISTORY_TIMESTAMP_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm", Locale.getDefault());
    private static final Pattern CLAIMED_QTY_PATTERN = Pattern.compile("Claimed Qty:\\s*(\\d+)");
    private static final Pattern PRICE_PATTERN = Pattern.compile("Price:\\s*([^|]+)");
    private static final Pattern TIME_PATTERN = Pattern.compile("Time:\\s*([0-9]{4}-[0-9]{2}-[0-9]{2} [0-9]{2}:[0-9]{2})");

    private FloatingActionButton fabAddListing;
    private RecyclerView recyclerViewListings;
    private ListingAdapter listingAdapter;
    private List<Listing> businessListings;
    private SharedPreferencesManager sharedPreferencesManager;
    private String loggedInBusinessEmail;
    private Toolbar toolbar; // Declare Toolbar
    private TextView textViewTodayRevenue;
    private TextView textViewWeekRevenue;
    private TextView textViewMonthRevenue;
    private TextView textViewCompletedSales;
    private TextView textViewItemsSold;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_donor_home);

        toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        applyBrandedToolbarTitle();

        sharedPreferencesManager = new SharedPreferencesManager(this);
        loggedInBusinessEmail = sharedPreferencesManager.getLoggedInUserEmail();

        fabAddListing = findViewById(R.id.fabAddListing);
        textViewTodayRevenue = findViewById(R.id.textViewTodayRevenue);
        textViewWeekRevenue = findViewById(R.id.textViewWeekRevenue);
        textViewMonthRevenue = findViewById(R.id.textViewMonthRevenue);
        textViewCompletedSales = findViewById(R.id.textViewCompletedSales);
        textViewItemsSold = findViewById(R.id.textViewItemsSold);
        fabAddListing.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                startActivity(new Intent(BusinessHomeActivity.this, AddListingActivity.class));
            }
        });

        recyclerViewListings = findViewById(R.id.recyclerViewListings);
        recyclerViewListings.setLayoutManager(new LinearLayoutManager(this));
        businessListings = new ArrayList<>();
        listingAdapter = new ListingAdapter(businessListings, this);
        recyclerViewListings.setAdapter(listingAdapter);
    }


    @Override
    protected void onResume() {
        super.onResume();
        loadBusinessListings();
    }

    private void loadBusinessListings() {
        if (loggedInBusinessEmail == null) {
            Toast.makeText(this, "Error: Business account not logged in", Toast.LENGTH_SHORT).show();
            return;
        }

        List<Listing> allListings = sharedPreferencesManager.getAllListings();
        businessListings.clear();
        for (Listing listing : allListings) {
            if (listing.getDonorEmail().equals(loggedInBusinessEmail)) {
                businessListings.add(listing);
            }
        }
        listingAdapter.updateListings(businessListings);
        renderRevenueStats();
    }

    @Override
    public void onListingClick(Listing listing) {
        Intent intent = new Intent(BusinessHomeActivity.this, ListingDetailsActivity.class);
        Gson gson = new Gson();
        String listingJson = gson.toJson(listing);
        intent.putExtra("listing", listingJson);
        startActivity(intent);
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.main_menu, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(@NonNull MenuItem item) {
        int id = item.getItemId();
        if (id == R.id.action_profile) {
            startActivity(new Intent(this, ProfileActivity.class));
            return true;
        }
        if (id == R.id.action_orders) {
            startActivity(new Intent(this, MyOrdersActivity.class));
            return true;
        }
        if (id == R.id.action_map) {
            startActivity(new Intent(this, MapActivity.class));
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    private void applyBrandedToolbarTitle() {
        SpannableString brandedTitle = new SpannableString("HungerHunt");
        brandedTitle.setSpan(
                new ForegroundColorSpan(ContextCompat.getColor(this, R.color.white)),
                0,
                "Hunger".length(),
                Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
        );
        brandedTitle.setSpan(
                new ForegroundColorSpan(ContextCompat.getColor(this, R.color.orange_accent)),
                "Hunger".length(),
                "HungerHunt".length(),
                Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
        );
        toolbar.setTitle(brandedTitle);
    }

    private void renderRevenueStats() {
        RevenueSummary summary = buildRevenueSummary(sharedPreferencesManager.getOrderHistory(loggedInBusinessEmail, "donor"));
        textViewTodayRevenue.setText(formatCurrency(summary.todayRevenue));
        textViewWeekRevenue.setText(formatCurrency(summary.weekRevenue));
        textViewMonthRevenue.setText(formatCurrency(summary.monthRevenue));
        textViewCompletedSales.setText(String.valueOf(summary.completedSales));
        textViewItemsSold.setText(String.valueOf(summary.itemsSold));
    }

    private RevenueSummary buildRevenueSummary(List<String> donorHistory) {
        RevenueSummary summary = new RevenueSummary();
        if (donorHistory == null || donorHistory.isEmpty()) {
            return summary;
        }

        List<String> chronologicalHistory = new ArrayList<>(donorHistory);
        Collections.reverse(chronologicalHistory);
        List<SaleRecord> pendingSales = new ArrayList<>();
        LocalDate today = LocalDate.now();
        LocalDate weekStart = today.with(DayOfWeek.MONDAY);
        LocalDate monthStart = today.withDayOfMonth(1);

        for (String entry : chronologicalHistory) {
            if (entry == null) {
                continue;
            }

            if (entry.contains("| Claimed Qty:")) {
                SaleRecord saleRecord = parseClaimEntry(entry);
                if (saleRecord != null) {
                    pendingSales.add(saleRecord);
                }
                continue;
            }

            if (!entry.contains("status changed to COMPLETED")) {
                continue;
            }

            SaleRecord completedRecord = parseCompletedEntry(entry);
            if (completedRecord == null || completedRecord.completedAt == null) {
                continue;
            }

            if (completedRecord.quantity <= 0 || completedRecord.unitPrice < 0d) {
                SaleRecord matchedRecord = consumePendingSale(pendingSales, completedRecord.title);
                if (matchedRecord != null) {
                    if (completedRecord.quantity <= 0) {
                        completedRecord.quantity = matchedRecord.quantity;
                    }
                    if (completedRecord.unitPrice < 0d) {
                        completedRecord.unitPrice = matchedRecord.unitPrice;
                    }
                }
            }

            if (completedRecord.quantity <= 0 || completedRecord.unitPrice < 0d) {
                continue;
            }

            double saleRevenue = completedRecord.quantity * completedRecord.unitPrice;
            LocalDate completedDate = completedRecord.completedAt.toLocalDate();
            if (completedDate.equals(today)) {
                summary.todayRevenue += saleRevenue;
            }
            if (!completedDate.isBefore(weekStart)) {
                summary.weekRevenue += saleRevenue;
            }
            if (!completedDate.isBefore(monthStart)) {
                summary.monthRevenue += saleRevenue;
            }
            summary.completedSales++;
            summary.itemsSold += completedRecord.quantity;
        }

        return summary;
    }

    private SaleRecord parseClaimEntry(String entry) {
        SaleRecord record = new SaleRecord();
        record.title = extractTitle(entry);
        record.quantity = parseClaimedQuantity(entry);
        record.unitPrice = parsePrice(entry);
        record.completedAt = parseTime(entry);
        if (record.quantity <= 0 || record.unitPrice < 0d) {
            return null;
        }
        return record;
    }

    private SaleRecord parseCompletedEntry(String entry) {
        SaleRecord record = new SaleRecord();
        record.title = extractTitle(entry);
        record.quantity = parseClaimedQuantity(entry);
        record.unitPrice = parsePrice(entry);
        record.completedAt = parseTime(entry);
        return record;
    }

    private SaleRecord consumePendingSale(List<SaleRecord> pendingSales, String title) {
        for (int i = 0; i < pendingSales.size(); i++) {
            SaleRecord record = pendingSales.get(i);
            if (record != null && title.equalsIgnoreCase(record.title)) {
                pendingSales.remove(i);
                return record;
            }
        }
        return null;
    }

    private String extractTitle(String entry) {
        int separator = entry.indexOf('|');
        if (separator < 0) {
            int statusMarker = entry.indexOf(" status changed to ");
            return statusMarker > 0 ? entry.substring(0, statusMarker).trim() : entry.trim();
        }
        return entry.substring(0, separator).trim();
    }

    private int parseClaimedQuantity(String entry) {
        Matcher matcher = CLAIMED_QTY_PATTERN.matcher(entry);
        if (!matcher.find()) {
            return 0;
        }
        try {
            return Integer.parseInt(matcher.group(1));
        } catch (NumberFormatException ignored) {
            return 0;
        }
    }

    private double parsePrice(String entry) {
        Matcher matcher = PRICE_PATTERN.matcher(entry);
        if (!matcher.find()) {
            return -1d;
        }
        String priceText = matcher.group(1).trim();
        if ("Free".equalsIgnoreCase(priceText)) {
            return 0d;
        }
        priceText = priceText.replaceAll("[^0-9.]", "");
        if (priceText.isEmpty()) {
            return -1d;
        }
        try {
            return Double.parseDouble(priceText);
        } catch (NumberFormatException ignored) {
            return -1d;
        }
    }

    private LocalDateTime parseTime(String entry) {
        Matcher matcher = TIME_PATTERN.matcher(entry);
        if (!matcher.find()) {
            return null;
        }
        try {
            return LocalDateTime.parse(matcher.group(1), HISTORY_TIMESTAMP_FORMATTER);
        } catch (DateTimeParseException ignored) {
            return null;
        }
    }

    private String formatCurrency(double value) {
        return String.format(Locale.getDefault(), "Rs %.0f", value);
    }

    private static class SaleRecord {
        String title;
        int quantity;
        double unitPrice = -1d;
        LocalDateTime completedAt;
    }

    private static class RevenueSummary {
        double todayRevenue;
        double weekRevenue;
        double monthRevenue;
        int completedSales;
        int itemsSold;
    }
}
