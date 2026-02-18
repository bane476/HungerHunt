package com.foodrescue.app.utils;

import android.text.TextUtils;
import android.util.Patterns;

public class Validator {
    private Validator() {
        // Utility class
    }

    public static boolean isValidEmail(String email) {
        return !TextUtils.isEmpty(email) && Patterns.EMAIL_ADDRESS.matcher(email).matches();
    }

    public static boolean isStrongEnoughPassword(String password) {
        return !TextUtils.isEmpty(password) && password.length() >= 6;
    }
}
