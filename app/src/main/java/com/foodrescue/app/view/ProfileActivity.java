package com.foodrescue.app.view;

import android.content.Intent;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.foodrescue.app.R;
import com.foodrescue.app.data.SharedPreferencesManager;
import com.foodrescue.app.model.User;

public class ProfileActivity extends AppCompatActivity {

    private TextView textViewEmail;
    private EditText editTextProfileName, editTextProfilePhone;
    private RadioGroup radioGroupProfileRole;
    private RadioButton radioButtonProfileDonor, radioButtonProfileReceiver;
    private Button buttonSaveProfile, buttonLogout;

    private SharedPreferencesManager sharedPreferencesManager;
    private User currentUser;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_profile);

        sharedPreferencesManager = new SharedPreferencesManager(this);

        textViewEmail = findViewById(R.id.textViewEmail);
        editTextProfileName = findViewById(R.id.editTextProfileName);
        editTextProfilePhone = findViewById(R.id.editTextProfilePhone);
        radioGroupProfileRole = findViewById(R.id.radioGroupProfileRole);
        radioButtonProfileDonor = findViewById(R.id.radioButtonProfileDonor);
        radioButtonProfileReceiver = findViewById(R.id.radioButtonProfileReceiver);
        buttonSaveProfile = findViewById(R.id.buttonSaveProfile);
        buttonLogout = findViewById(R.id.buttonLogout);

        loadUserProfile();

        buttonSaveProfile.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                saveUserProfile();
            }
        });

        buttonLogout.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                logoutUser();
            }
        });
    }

    private void loadUserProfile() {
        String loggedInEmail = sharedPreferencesManager.getLoggedInUserEmail();
        if (loggedInEmail != null) {
            currentUser = sharedPreferencesManager.getUser(loggedInEmail);
            if (currentUser != null) {
                textViewEmail.setText(currentUser.getEmail());
                editTextProfileName.setText(currentUser.getName());
                editTextProfilePhone.setText(currentUser.getPhone());

                if (currentUser.getRole().equals("Donor")) {
                    radioButtonProfileDonor.setChecked(true);
                } else {
                    radioButtonProfileReceiver.setChecked(true);
                }
            } else {
                Toast.makeText(this, "User data not found.", Toast.LENGTH_SHORT).show();
                finish();
            }
        } else {
            Toast.makeText(this, "Not logged in.", Toast.LENGTH_SHORT).show();
            startActivity(new Intent(ProfileActivity.this, LoginActivity.class));
            finish();
        }
    }

    private void saveUserProfile() {
        if (currentUser == null) {
            Toast.makeText(this, "Error: No user to save.", Toast.LENGTH_SHORT).show();
            return;
        }

        String newName = editTextProfileName.getText().toString().trim();
        String newPhone = editTextProfilePhone.getText().toString().trim();
        int selectedRoleId = radioGroupProfileRole.getCheckedRadioButtonId();

        if (TextUtils.isEmpty(newName) || TextUtils.isEmpty(newPhone) || selectedRoleId == -1) {
            Toast.makeText(this, "Please fill all fields", Toast.LENGTH_SHORT).show();
            return;
        }

        RadioButton selectedRadioButton = findViewById(selectedRoleId);
        String newRole = selectedRadioButton.getText().toString();

        // Create a new User object with updated details (email and password remain same)
        User updatedUser = new User(newName, currentUser.getEmail(), currentUser.getPassword(), newPhone, newRole);
        sharedPreferencesManager.saveUser(updatedUser); // saveUser will overwrite if email exists
        currentUser = updatedUser; // Update current user in activity
        Toast.makeText(this, "Profile updated successfully!", Toast.LENGTH_SHORT).show();
    }

    private void logoutUser() {
        sharedPreferencesManager.saveLoggedInUserEmail(null); // Clear logged in user
        Intent intent = new Intent(ProfileActivity.this, LoginActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_NEW_TASK);
        startActivity(intent);
        finish();
    }
}
