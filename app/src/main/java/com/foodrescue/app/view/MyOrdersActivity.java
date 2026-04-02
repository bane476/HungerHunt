package com.foodrescue.app.view;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.foodrescue.app.R;
import com.foodrescue.app.adapters.OrderHistoryAdapter;
import com.foodrescue.app.data.SharedPreferencesManager;
import com.foodrescue.app.model.User;

import java.util.List;

public class MyOrdersActivity extends AppCompatActivity {
    private SharedPreferencesManager sharedPreferencesManager;
    private RecyclerView recyclerViewOrders;
    private TextView textViewOrdersHeader;
    private TextView textViewOrdersEmpty;
    private OrderHistoryAdapter adapter;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_my_orders);

        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
            getSupportActionBar().setTitle("My Orders");
        }

        sharedPreferencesManager = new SharedPreferencesManager(this);
        recyclerViewOrders = findViewById(R.id.recyclerViewOrders);
        textViewOrdersHeader = findViewById(R.id.textViewOrdersHeader);
        textViewOrdersEmpty = findViewById(R.id.textViewOrdersEmpty);

        adapter = new OrderHistoryAdapter();
        recyclerViewOrders.setLayoutManager(new LinearLayoutManager(this));
        recyclerViewOrders.setAdapter(adapter);

        loadOrders();
    }

    @Override
    public boolean onSupportNavigateUp() {
        onBackPressed();
        return true;
    }

    private void loadOrders() {
        String loggedInEmail = sharedPreferencesManager.getLoggedInUserEmail();
        if (loggedInEmail == null) {
            Toast.makeText(this, "Not logged in.", Toast.LENGTH_SHORT).show();
            startActivity(new Intent(this, LoginActivity.class));
            finish();
            return;
        }

        User currentUser = sharedPreferencesManager.getUser(loggedInEmail);
        if (currentUser == null) {
            Toast.makeText(this, "User data not found.", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        boolean isBusiness = isBusinessRole(currentUser.getRole());
        List<String> history = sharedPreferencesManager.getOrderHistory(loggedInEmail, isBusiness ? "donor" : "receiver");
        textViewOrdersHeader.setText(isBusiness ? "Business Order Activity" : "Customer Order Activity");
        textViewOrdersEmpty.setText(isBusiness ? "No business orders yet." : "No customer orders yet.");
        adapter.updateEntries(history);

        boolean isEmpty = history == null || history.isEmpty();
        textViewOrdersEmpty.setVisibility(isEmpty ? View.VISIBLE : View.GONE);
        recyclerViewOrders.setVisibility(isEmpty ? View.GONE : View.VISIBLE);
    }

    private boolean isBusinessRole(String role) {
        return "Business".equalsIgnoreCase(role)
                || "Provider".equalsIgnoreCase(role)
                || "Donor".equalsIgnoreCase(role);
    }
}
