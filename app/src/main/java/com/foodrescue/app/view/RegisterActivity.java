package com.foodrescue.app.view;

import android.os.Bundle;
import android.text.TextUtils;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;
import com.foodrescue.app.R;
import com.foodrescue.app.data.SharedPreferencesManager;
import com.foodrescue.app.model.User;

public class RegisterActivity extends AppCompatActivity {

    private EditText editTextName, editTextEmail, editTextPassword, editTextPhone;
    private RadioGroup radioGroupRole;
    private Button buttonRegister;
    private SharedPreferencesManager sharedPreferencesManager;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_register);

        sharedPreferencesManager = new SharedPreferencesManager(this);

        editTextName = findViewById(R.id.editTextName);
        editTextEmail = findViewById(R.id.editTextEmail);
        editTextPassword = findViewById(R.id.editTextPassword);
        editTextPhone = findViewById(R.id.editTextPhone);
        radioGroupRole = findViewById(R.id.radioGroupRole);
        buttonRegister = findViewById(R.id.buttonRegister);

        buttonRegister.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                registerUser();
            }
        });
    }

    private void registerUser() {
        String name = editTextName.getText().toString().trim();
        String email = editTextEmail.getText().toString().trim();
        String password = editTextPassword.getText().toString().trim();
        String phone = editTextPhone.getText().toString().trim();
        int selectedRoleId = radioGroupRole.getCheckedRadioButtonId();

        if (TextUtils.isEmpty(name) || TextUtils.isEmpty(email) || TextUtils.isEmpty(password) || TextUtils.isEmpty(phone) || selectedRoleId == -1) {
            Toast.makeText(this, "Please fill all fields", Toast.LENGTH_SHORT).show();
            return;
        }

        RadioButton selectedRadioButton = findViewById(selectedRoleId);
        String role = selectedRadioButton.getText().toString();

        User user = new User(name, email, password, phone, role);
        sharedPreferencesManager.saveUser(user);

        Toast.makeText(this, "Registration successful", Toast.LENGTH_SHORT).show();
        finish();
    }
}
