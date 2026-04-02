package com.foodrescue.app.adapters;

import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.foodrescue.app.R;

import java.util.ArrayList;
import java.util.List;

public class OrderHistoryAdapter extends RecyclerView.Adapter<OrderHistoryAdapter.OrderHistoryViewHolder> {
    private final List<String> entries = new ArrayList<>();

    public void updateEntries(List<String> newEntries) {
        entries.clear();
        if (newEntries != null) {
            entries.addAll(newEntries);
        }
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public OrderHistoryViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_order_history, parent, false);
        return new OrderHistoryViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull OrderHistoryViewHolder holder, int position) {
        String entry = entries.get(position);
        String[] parts = entry.split("\\|");
        holder.textViewTitle.setText(parts.length > 0 ? parts[0].trim() : "Order");

        StringBuilder subtitle = new StringBuilder();
        for (int i = 1; i < parts.length; i++) {
            String part = parts[i].trim();
            if (TextUtils.isEmpty(part)) {
                continue;
            }
            if (subtitle.length() > 0) {
                subtitle.append("  •  ");
            }
            subtitle.append(part);
        }
        holder.textViewDetails.setText(subtitle.length() > 0 ? subtitle.toString() : entry);
    }

    @Override
    public int getItemCount() {
        return entries.size();
    }

    static class OrderHistoryViewHolder extends RecyclerView.ViewHolder {
        private final TextView textViewTitle;
        private final TextView textViewDetails;

        OrderHistoryViewHolder(@NonNull View itemView) {
            super(itemView);
            textViewTitle = itemView.findViewById(R.id.textViewOrderTitle);
            textViewDetails = itemView.findViewById(R.id.textViewOrderDetails);
        }
    }
}
