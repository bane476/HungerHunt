package com.foodrescue.app.model;

public class User {
    private String name;
    private String email;
    private String password;
    private String phone;
    private String address;
    private String businessName;
    private String role;

    public User() {
        // Required for Firestore object mapping.
    }

    public User(String name, String email, String password, String phone, String role) {
        this(name, email, password, phone, "", "", role);
    }

    public User(String name, String email, String password, String phone, String address, String role) {
        this(name, email, password, phone, address, "", role);
    }

    public User(String name, String email, String password, String phone, String address, String businessName, String role) {
        this.name = name;
        this.email = email;
        this.password = password;
        this.phone = phone;
        this.address = address;
        this.businessName = businessName;
        this.role = role;
    }

    public String getName() {
        return name;
    }

    public String getEmail() {
        return email;
    }

    public String getPassword() {
        return password;
    }

    public String getPhone() {
        return phone;
    }

    public String getAddress() {
        return address;
    }

    public String getBusinessName() {
        return businessName;
    }

    public String getRole() {
        return role;
    }

    public void setName(String name) {
        this.name = name;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public void setPassword(String password) {
        this.password = password;
    }

    public void setPhone(String phone) {
        this.phone = phone;
    }

    public void setAddress(String address) {
        this.address = address;
    }

    public void setBusinessName(String businessName) {
        this.businessName = businessName;
    }

    public void setRole(String role) {
        this.role = role;
    }
}
