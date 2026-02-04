package com.foodrescue.app.model;

public class FoodItem {
    private String name;
    private int quantity;

    public FoodItem() {
        // Default constructor required for calls to DataSnapshot.getValue(FoodItem.class)
    }

    public FoodItem(String name, int quantity) {
        this.name = name;
        this.quantity = quantity;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public int getQuantity() {
        return quantity;
    }

    public void setQuantity(int quantity) {
        this.quantity = quantity;
    }
}
