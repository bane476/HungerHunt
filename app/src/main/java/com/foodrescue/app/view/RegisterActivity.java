package com.foodrescue.app.view;

import android.os.Bundle;
import android.text.TextUtils;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.RadioGroup;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;
import androidx.lifecycle.ViewModelProvider;
import com.foodrescue.app.R;
import com.foodrescue.app.data.SharedPreferencesManager;
import com.foodrescue.app.model.User;
import com.foodrescue.app.repository.AuthRepository;
import com.foodrescue.app.utils.Validator;
import com.foodrescue.app.viewmodel.AuthViewModel;
import com.google.android.material.textfield.TextInputLayout;

import java.util.Locale;

public class RegisterActivity extends AppCompatActivity {

    private EditText editTextName, editTextBusinessName, editTextEmail, editTextPassword, editTextPhone, editTextAddress;
    private RadioGroup radioGroupRole;
    private TextInputLayout layoutName, layoutBusinessName;
    private Button buttonRegister;
    private AuthViewModel authViewModel;
    private SharedPreferencesManager sharedPreferencesManager;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_register);

        authViewModel = new ViewModelProvider(this).get(AuthViewModel.class);
        sharedPreferencesManager = new SharedPreferencesManager(this);

        editTextName = findViewById(R.id.editTextName);
        editTextBusinessName = findViewById(R.id.editTextBusinessName);
        editTextEmail = findViewById(R.id.editTextEmail);
        editTextPassword = findViewById(R.id.editTextPassword);
        editTextPhone = findViewById(R.id.editTextPhone);
        editTextAddress = findViewById(R.id.editTextAddress);
        radioGroupRole = findViewById(R.id.radioGroupRole);
        layoutName = findViewById(R.id.layoutName);
        layoutBusinessName = findViewById(R.id.layoutBusinessName);
        buttonRegister = findViewById(R.id.buttonRegister);

        applyRoleSpecificRegistrationUi(radioGroupRole.getCheckedRadioButtonId());
        radioGroupRole.setOnCheckedChangeListener((group, checkedId) -> applyRoleSpecificRegistrationUi(checkedId));

        buttonRegister.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                registerUser();
            }
        });
    }

    private void registerUser() {
        String name = editTextName.getText().toString().trim();
        String businessName = editTextBusinessName.getText().toString().trim();
        String email = editTextEmail.getText().toString().trim().toLowerCase(Locale.ROOT);
        String password = editTextPassword.getText().toString().trim();
        String phone = editTextPhone.getText().toString().trim();
        String address = editTextAddress.getText().toString().trim();
        int selectedRoleId = radioGroupRole.getCheckedRadioButtonId();

        if (TextUtils.isEmpty(name) || TextUtils.isEmpty(email) || TextUtils.isEmpty(password)
                || TextUtils.isEmpty(phone) || TextUtils.isEmpty(address) || selectedRoleId == -1) {
            Toast.makeText(this, "Please fill all fields", Toast.LENGTH_SHORT).show();
            return;
        }
        if (!Validator.isValidEmail(email)) {
            Toast.makeText(this, "Please enter a valid email", Toast.LENGTH_SHORT).show();
            return;
        }
        if (!Validator.isStrongEnoughPassword(password)) {
            Toast.makeText(this, "Password must be at least 6 characters", Toast.LENGTH_SHORT).show();
            return;
        }

        String role = selectedRoleId == R.id.radioButtonDonor ? "Business" : "Customer";
        if ("Business".equals(role) && TextUtils.isEmpty(businessName)) {
            Toast.makeText(this, "Business name is required for business accounts", Toast.LENGTH_SHORT).show();
            return;
        }

        User existingUser = sharedPreferencesManager.getUser(email);
        if (existingUser != null) {
            String existingRole = normalizeRole(existingUser.getRole());
            if (!existingRole.equals(role)) {
                Toast.makeText(this, "Email already registered as " + existingRole + ". Use a different email.", Toast.LENGTH_LONG).show();
                return;
            }
            Toast.makeText(this, "This email is already registered.", Toast.LENGTH_LONG).show();
            return;
        }

        User user = new User(name, email, password, phone, address, businessName, role);
        buttonRegister.setEnabled(false);
        authViewModel.registerUser(user, new AuthRepository.AuthResultCallback() {
            @Override
            public void onSuccess(User user, String message) {
                runOnUiThread(() -> {
                    buttonRegister.setEnabled(true);
                    Toast.makeText(RegisterActivity.this, message, Toast.LENGTH_SHORT).show();
                    finish();
                });
            }

            @Override
            public void onError(String message) {
                runOnUiThread(() -> {
                    buttonRegister.setEnabled(true);
                    Toast.makeText(RegisterActivity.this, message, Toast.LENGTH_SHORT).show();
                });
            }
        });
    }

    private String normalizeRole(String role) {
        if ("Donor".equalsIgnoreCase(role)
                || "Provider".equalsIgnoreCase(role)
                || "Business".equalsIgnoreCase(role)) {
            return "Business";
        }
        return "Customer";
    }

    private void applyRoleSpecificRegistrationUi(int selectedRoleId) {
        boolean roleSelected = selectedRoleId != -1;
        boolean isBusiness = selectedRoleId == R.id.radioButtonDonor;

        buttonRegister.setEnabled(roleSelected);
        layoutName.setHint(isBusiness ? "Owner Name" : "Name");
        layoutBusinessName.setVisibility(isBusiness ? View.VISIBLE : View.GONE);

        if (!isBusiness) {
            editTextBusinessName.setText("");
        }
    }
}
