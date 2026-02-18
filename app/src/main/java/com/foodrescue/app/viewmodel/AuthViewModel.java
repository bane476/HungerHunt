package com.foodrescue.app.viewmodel;

import android.app.Application;

import androidx.annotation.NonNull;
import androidx.lifecycle.AndroidViewModel;

import com.foodrescue.app.model.User;
import com.foodrescue.app.repository.AuthRepository;

public class AuthViewModel extends AndroidViewModel {
    private final AuthRepository authRepository;

    public AuthViewModel(@NonNull Application application) {
        super(application);
        authRepository = new AuthRepository(application.getApplicationContext());
    }

    public void registerUser(@NonNull User user, @NonNull AuthRepository.AuthResultCallback callback) {
        authRepository.registerUser(user, callback);
    }

    public void loginUser(@NonNull String email, @NonNull String password, @NonNull AuthRepository.AuthResultCallback callback) {
        authRepository.loginUser(email, password, callback);
    }

    public void logoutUser() {
        authRepository.logoutUser();
    }
}
