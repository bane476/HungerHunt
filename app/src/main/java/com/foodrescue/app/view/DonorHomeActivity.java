package com.foodrescue.app.view;

import android.content.Intent;
import android.os.Bundle;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.SearchView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar; // Import Toolbar
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

public class DonorHomeActivity extends AppCompatActivity implements ListingAdapter.OnListingClickListener {

    private FloatingActionButton fabAddListing;
    private RecyclerView recyclerViewListings;
    private ListingAdapter listingAdapter;
    private List<Listing> donorListings;
    private SharedPreferencesManager sharedPreferencesManager;
    private String loggedInDonorEmail;
    private SearchView searchViewListings;
    private Toolbar toolbar; // Declare Toolbar

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_donor_home);

        toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);

        sharedPreferencesManager = new SharedPreferencesManager(this);
        loggedInDonorEmail = sharedPreferencesManager.getLoggedInUserEmail();

        fabAddListing = findViewById(R.id.fabAddListing);
        fabAddListing.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                startActivity(new Intent(DonorHomeActivity.this, AddListingActivity.class));
            }
        });

        recyclerViewListings = findViewById(R.id.recyclerViewListings);
        recyclerViewListings.setLayoutManager(new LinearLayoutManager(this));
        donorListings = new ArrayList<>();
        listingAdapter = new ListingAdapter(donorListings, this);
        recyclerViewListings.setAdapter(listingAdapter);

        searchViewListings = findViewById(R.id.searchViewListings);
        searchViewListings.setOnQueryTextListener(new SearchView.OnQueryTextListener() {
            @Override
            public boolean onQueryTextSubmit(String query) {
                return false;
            }

            @Override
            public boolean onQueryTextChange(String newText) {
                listingAdapter.getFilter().filter(newText);
                return false;
            }
        });
    }


    @Override
    protected void onResume() {
        super.onResume();
        loadDonorListings();
    }

    private void loadDonorListings() {
        if (loggedInDonorEmail == null) {
            Toast.makeText(this, "Error: Donor not logged in", Toast.LENGTH_SHORT).show();
            return;
        }

        List<Listing> allListings = sharedPreferencesManager.getAllListings();
        donorListings.clear();
        for (Listing listing : allListings) {
            if (listing.getDonorEmail().equals(loggedInDonorEmail)) {
                donorListings.add(listing);
            }
        }
        listingAdapter.updateListings(donorListings);
    }

    @Override
    public void onListingClick(Listing listing) {
        Intent intent = new Intent(DonorHomeActivity.this, ListingDetailsActivity.class);
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
        } else if (id == R.id.action_map) {
            startActivity(new Intent(this, MapActivity.class));
            return true;
        }
        return super.onOptionsItemSelected(item);
    }
}
