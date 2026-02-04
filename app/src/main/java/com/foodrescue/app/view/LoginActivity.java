package com.foodrescue.app.view;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;
import androidx.annotation.NonNull; // Import for @NonNull
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat; // Import for ActivityCompat
import com.foodrescue.app.R;
import com.foodrescue.app.data.SharedPreferencesManager;
import com.foodrescue.app.model.User;
import com.foodrescue.app.utils.NotificationHelper; // Import NotificationHelper

public class LoginActivity extends AppCompatActivity {

    private static final int PERMISSION_REQUEST_CODE = 100;
    private EditText editTextEmail, editTextPassword;
    private Button buttonLogin;
    private TextView textViewRegister;
    private SharedPreferencesManager sharedPreferencesManager;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_login);

        sharedPreferencesManager = new SharedPreferencesManager(this);

        // Create notification channel
        NotificationHelper.createNotificationChannel(this);

        // Request POST_NOTIFICATIONS permission for Android 13+
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ActivityCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.POST_NOTIFICATIONS}, PERMISSION_REQUEST_CODE);
            }
        }

        editTextEmail = findViewById(R.id.editTextEmail);
        editTextPassword = findViewById(R.id.editTextPassword);
        buttonLogin = findViewById(R.id.buttonLogin);
        textViewRegister = findViewById(R.id.textViewRegister);

        buttonLogin.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                loginUser();
            }
        });

        textViewRegister.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                startActivity(new Intent(LoginActivity.this, RegisterActivity.class));
            }
        });
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == PERMISSION_REQUEST_CODE) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                Toast.makeText(this, "Notification permission granted!", Toast.LENGTH_SHORT).show();
            } else {
                Toast.makeText(this, "Notification permission denied. You may not receive notifications.", Toast.LENGTH_LONG).show();
            }
        }
    }

    private void loginUser() {
        String email = editTextEmail.getText().toString().trim();
        String password = editTextPassword.getText().toString().trim();

        if (TextUtils.isEmpty(email) || TextUtils.isEmpty(password)) {
            Toast.makeText(this, "Please fill all fields", Toast.LENGTH_SHORT).show();
            return;
        }

        User user = sharedPreferencesManager.getUser(email);

        if (user != null && user.getPassword().equals(password)) {
            Toast.makeText(this, "Login successful", Toast.LENGTH_SHORT).show();
            sharedPreferencesManager.saveLoggedInUserEmail(email); // Save logged-in user's email
            if (user.getRole().equals("Donor")) {
                startActivity(new Intent(LoginActivity.this, DonorHomeActivity.class));
            } else {
                startActivity(new Intent(LoginActivity.this, ReceiverHomeActivity.class));
            }
            finish();
        } else {
            Toast.makeText(this, "Invalid email or password", Toast.LENGTH_SHORT).show();
        }
    }
}
