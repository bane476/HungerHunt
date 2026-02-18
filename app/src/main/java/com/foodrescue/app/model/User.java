package com.foodrescue.app.model;

public class User {
    private String name;
    private String email;
    private String password;
    private String phone;
    private String address;
    private String businessName;
    private String role;

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
}
