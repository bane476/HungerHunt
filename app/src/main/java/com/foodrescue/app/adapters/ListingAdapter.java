package com.foodrescue.app.adapters;

import android.graphics.Color;
import android.graphics.Paint;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Filter;
import android.widget.Filterable;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.RecyclerView;

import com.foodrescue.app.R;
import com.foodrescue.app.model.Listing;

import java.util.ArrayList;
import java.util.List;

public class ListingAdapter extends RecyclerView.Adapter<ListingAdapter.ListingViewHolder> implements Filterable { // Implements Filterable

    private List<Listing> listingList; // Original unfiltered list
    private List<Listing> listingsFiltered; // Filtered list
    private OnListingClickListener listener;

    public interface OnListingClickListener {
        void onListingClick(Listing listing);
    }

    public ListingAdapter(List<Listing> listingList, OnListingClickListener listener) {
        this.listingList = listingList;
        this.listingsFiltered = new ArrayList<>(listingList); // Initialize filtered list with all data
        this.listener = listener;
    }

    @NonNull
    @Override
    public ListingViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_listing, parent, false);
        return new ListingViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ListingViewHolder holder, int position) {
        Listing listing = listingsFiltered.get(position); // Use filtered list
        holder.textViewTitle.setText(listing.getTitle());
        String priceText = (listing.getPrice() == null || listing.getPrice().trim().isEmpty()) ? "Free" : listing.getPrice();
        holder.textViewQuantity.setText("Qty: " + listing.getQuantity() + " | Price: " + priceText);
        int textPrimary = ContextCompat.getColor(holder.itemView.getContext(), R.color.text_primary);
        int textSecondary = ContextCompat.getColor(holder.itemView.getContext(), R.color.text_secondary);
        int successColor = ContextCompat.getColor(holder.itemView.getContext(), R.color.success);

        if (listing.isClaimed()) {
            holder.textViewTitle.setTextColor(Color.GRAY);
            holder.textViewQuantity.setTextColor(Color.GRAY);
            holder.textViewStatus.setTextColor(textSecondary);
            holder.textViewTitle.setPaintFlags(holder.textViewTitle.getPaintFlags() | Paint.STRIKE_THRU_TEXT_FLAG);
            holder.textViewQuantity.setPaintFlags(holder.textViewQuantity.getPaintFlags() | Paint.STRIKE_THRU_TEXT_FLAG);
            holder.textViewStatus.setText("Claimed by: " + listing.getClaimedByEmail());
        } else {
            holder.textViewTitle.setTextColor(textPrimary);
            holder.textViewQuantity.setTextColor(textSecondary);
            holder.textViewStatus.setTextColor(successColor);
            holder.textViewTitle.setPaintFlags(holder.textViewTitle.getPaintFlags() & (~Paint.STRIKE_THRU_TEXT_FLAG));
            holder.textViewQuantity.setPaintFlags(holder.textViewQuantity.getPaintFlags() & (~Paint.STRIKE_THRU_TEXT_FLAG));
            holder.textViewStatus.setText("Available");
        }

        holder.itemView.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                listener.onListingClick(listing);
            }
        });
    }

    @Override
    public int getItemCount() {
        return listingsFiltered.size(); // Use filtered list size
    }

    public static class ListingViewHolder extends RecyclerView.ViewHolder {
        public TextView textViewTitle;
        public TextView textViewQuantity;
        public TextView textViewStatus;

        public ListingViewHolder(@NonNull View itemView) {
            super(itemView);
            textViewTitle = itemView.findViewById(R.id.textViewTitle);
            textViewQuantity = itemView.findViewById(R.id.textViewQuantity);
            textViewStatus = itemView.findViewById(R.id.textViewStatus);
        }
    }

    public void updateListings(List<Listing> newListingList) {
        this.listingList = newListingList;
        this.listingsFiltered = new ArrayList<>(newListingList); // Update filtered list as well
        notifyDataSetChanged();
    }

    @Override
    public Filter getFilter() {
        return new Filter() {
            @Override
            protected FilterResults performFiltering(CharSequence charSequence) {
                String charString = charSequence.toString();
                if (charString.isEmpty()) {
                    listingsFiltered = new ArrayList<>(listingList); // Show all if search is empty
                } else {
                    List<Listing> filteredList = new ArrayList<>();
                    for (Listing listing : listingList) {
                        // Filter by title, description, or donor email (you can expand this)
                        if (listing.getTitle().toLowerCase().contains(charString.toLowerCase()) ||
                            listing.getDescription().toLowerCase().contains(charString.toLowerCase()) ||
                            listing.getDonorEmail().toLowerCase().contains(charString.toLowerCase())) {
                            filteredList.add(listing);
                        }
                    }
                    listingsFiltered = filteredList;
                }

                FilterResults filterResults = new FilterResults();
                filterResults.values = listingsFiltered;
                return filterResults;
            }

            @Override
            protected void publishResults(CharSequence charSequence, FilterResults filterResults) {
                listingsFiltered = (List<Listing>) filterResults.values;
                notifyDataSetChanged();
            }
        };
    }
}
