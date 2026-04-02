package com.foodrescue.app.view;

import android.content.Intent;
import android.os.Bundle;
import android.text.SpannableString;
import android.text.Spanned;
import android.text.style.ForegroundColorSpan;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
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
import java.util.List;

public class BusinessHomeActivity extends AppCompatActivity implements ListingAdapter.OnListingClickListener {

    private FloatingActionButton fabAddListing;
    private RecyclerView recyclerViewListings;
    private ListingAdapter listingAdapter;
    private List<Listing> businessListings;
    private SharedPreferencesManager sharedPreferencesManager;
    private String loggedInBusinessEmail;
    private Toolbar toolbar; // Declare Toolbar

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
}
