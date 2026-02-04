package com.foodrescue.app.view;

import android.content.Intent;
import android.os.Bundle;
import android.view.Menu; // Import Menu
import android.view.MenuItem; // Import MenuItem
import android.widget.SearchView;
import android.widget.Toast;

import androidx.annotation.NonNull; // Import for @NonNull
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.foodrescue.app.R;
import com.foodrescue.app.adapters.ListingAdapter;
import com.foodrescue.app.data.SharedPreferencesManager;
import com.foodrescue.app.model.Listing;
import com.google.gson.Gson;

import java.util.ArrayList;
import java.util.List;

public class ReceiverHomeActivity extends AppCompatActivity implements ListingAdapter.OnListingClickListener {

    private RecyclerView recyclerViewListings;
    private ListingAdapter listingAdapter;
    private List<Listing> allListings;
    private SharedPreferencesManager sharedPreferencesManager;
    private SearchView searchViewListings;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_receiver_home);

        sharedPreferencesManager = new SharedPreferencesManager(this);

        recyclerViewListings = findViewById(R.id.recyclerViewListings);
        recyclerViewListings.setLayoutManager(new LinearLayoutManager(this));
        allListings = new ArrayList<>();
        listingAdapter = new ListingAdapter(allListings, this);
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
        loadAllListings();
    }

    private void loadAllListings() {
        allListings.clear();
        allListings.addAll(sharedPreferencesManager.getAllListings());
        listingAdapter.updateListings(allListings);
    }

    @Override
    public void onListingClick(Listing listing) {
        Intent intent = new Intent(ReceiverHomeActivity.this, ListingDetailsActivity.class);
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
