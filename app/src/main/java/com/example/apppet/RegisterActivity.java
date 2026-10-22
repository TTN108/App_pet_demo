package com.example.apppet;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Color;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.text.method.HideReturnsTransformationMethod;
import android.text.method.PasswordTransformationMethod;
import android.util.Patterns;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import com.example.apppet.data.DatabaseHelper;
import com.example.apppet.data.SessionManager;

import java.util.regex.Pattern;

public class RegisterActivity extends Activity {

    private EditText edtFullName, edtEmail, edtUsername, edtPassword, edtConfirmPassword;
    private ImageView btnTogglePassword, btnToggleConfirmPassword;
    private TextView txtPasswordStrength, txtLoginBack;
    private CheckBox cbTerms;
    private Button btnRegister;

    private DatabaseHelper databaseHelper;
    private boolean isPasswordVisible = false;
    private boolean isConfirmPasswordVisible = false;

    // Password criteria regex patterns from btrong
    private final Pattern uppercasePattern = Pattern.compile("[A-Z]");
    private final Pattern lowercasePattern = Pattern.compile("[a-z]");
    private final Pattern digitPattern = Pattern.compile("[0-9]");
    private final Pattern specialCharPattern = Pattern.compile("[!@#$%^&*()_+\\-=\\[\\]{};':\"\\\\|,.<>/?]");
    private final Pattern usernamePattern = Pattern.compile("^[A-Za-z0-9_.]{3,30}$");

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_register);

        databaseHelper = new DatabaseHelper(this);

        initViews();
        setupListeners();
    }

    private void initViews() {
        edtFullName = findViewById(R.id.edtFullNameReg);
        edtEmail = findViewById(R.id.edtEmailReg);
        edtUsername = findViewById(R.id.edtUsernameReg);
        edtPassword = findViewById(R.id.edtPasswordReg);
        edtConfirmPassword = findViewById(R.id.edtConfirmPasswordReg);

        btnTogglePassword = findViewById(R.id.btnTogglePassword);
        btnToggleConfirmPassword = findViewById(R.id.btnToggleConfirmPassword);
        txtPasswordStrength = findViewById(R.id.txtPasswordStrength);
        cbTerms = findViewById(R.id.cbTerms);
        btnRegister = findViewById(R.id.btnRegister);
        txtLoginBack = findViewById(R.id.txtLoginBack);
    }

    private void setupListeners() {
        txtLoginBack.setOnClickListener(v -> finish());

        // Password visibility toggles
        btnTogglePassword.setOnClickListener(v -> {
            isPasswordVisible = !isPasswordVisible;
            if (isPasswordVisible) {
                edtPassword.setTransformationMethod(HideReturnsTransformationMethod.getInstance());
                btnTogglePassword.setImageResource(R.drawable.ic_eye);
            } else {
                edtPassword.setTransformationMethod(PasswordTransformationMethod.getInstance());
                btnTogglePassword.setImageResource(R.drawable.ic_eye_off);
            }
            edtPassword.setSelection(edtPassword.getText().length());
        });

        btnToggleConfirmPassword.setOnClickListener(v -> {
            isConfirmPasswordVisible = !isConfirmPasswordVisible;
            if (isConfirmPasswordVisible) {
                edtConfirmPassword.setTransformationMethod(HideReturnsTransformationMethod.getInstance());
                btnToggleConfirmPassword.setImageResource(R.drawable.ic_eye);
            } else {
                edtConfirmPassword.setTransformationMethod(PasswordTransformationMethod.getInstance());
                btnToggleConfirmPassword.setImageResource(R.drawable.ic_eye_off);
            }
            edtConfirmPassword.setSelection(edtConfirmPassword.getText().length());
        });

        // Real-time password strength evaluator
        edtPassword.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                evaluatePasswordStrength(s.toString());
            }

            @Override
            public void afterTextChanged(Editable s) {}
        });

        btnRegister.setOnClickListener(v -> handleRegister());
    }

    private void evaluatePasswordStrength(String password) {
        if (TextUtils.isEmpty(password)) {
            txtPasswordStrength.setText("Độ mạnh mật khẩu: Chưa nhập");
            txtPasswordStrength.setTextColor(Color.parseColor("#687386"));
            return;
        }

        int criteriaMet = 0;
        if (password.length() >= 8 && password.length() <= 20) criteriaMet++;
        if (uppercasePattern.matcher(password).find()) criteriaMet++;
        if (lowercasePattern.matcher(password).find()) criteriaMet++;
        if (digitPattern.matcher(password).find()) criteriaMet++;
        if (specialCharPattern.matcher(password).find()) criteriaMet++;

        if (criteriaMet <= 2) {
            txtPasswordStrength.setText("Độ mạnh mật khẩu: Yếu (Cần thêm ký tự/số/chữ hoa)");
            txtPasswordStrength.setTextColor(Color.parseColor("#C0392B")); // Red
        } else if (criteriaMet <= 4) {
            txtPasswordStrength.setText("Độ mạnh mật khẩu: Trung bình");
            txtPasswordStrength.setTextColor(Color.parseColor("#D97706")); // Orange
        } else {
            txtPasswordStrength.setText("Độ mạnh mật khẩu: Mạnh (Thỏa mãn tất cả yêu cầu)");
            txtPasswordStrength.setTextColor(Color.parseColor("#16A34A")); // Green
        }
    }

    private boolean isValidEmail(String email) {
        if (TextUtils.isEmpty(email)) return false;
        return Patterns.EMAIL_ADDRESS.matcher(email).matches();
    }

    private boolean isPasswordValid(String password) {
        if (TextUtils.isEmpty(password)) return false;
        if (password.length() < 8 || password.length() > 20) return false;
        if (!uppercasePattern.matcher(password).find()) return false;
        if (!lowercasePattern.matcher(password).find()) return false;
        if (!digitPattern.matcher(password).find()) return false;
        return specialCharPattern.matcher(password).find();
    }

    private void handleRegister() {
        String fullName = edtFullName.getText().toString().trim();
        String email = edtEmail.getText().toString().trim();
        String username = edtUsername.getText().toString().trim();
        String password = edtPassword.getText().toString();
        String confirmPassword = edtConfirmPassword.getText().toString();

        if (TextUtils.isEmpty(fullName) || TextUtils.isEmpty(email) || TextUtils.isEmpty(username)
                || TextUtils.isEmpty(password) || TextUtils.isEmpty(confirmPassword)) {
            Toast.makeText(this, "Vui lòng nhập đầy đủ tất cả các trường thông tin!", Toast.LENGTH_SHORT).show();
            return;
        }

        if (!usernamePattern.matcher(username).matches()) {
            Toast.makeText(this, "Tên đăng nhập gồm 3-30 ký tự: chữ không dấu, số, dấu chấm hoặc gạch dưới.", Toast.LENGTH_LONG).show();
            edtUsername.requestFocus();
            return;
        }

        if (!isValidEmail(email)) {
            Toast.makeText(this, "Email không hợp lệ! Vui lòng kiểm tra lại địa chỉ email.", Toast.LENGTH_LONG).show();
            edtEmail.requestFocus();
            return;
        }

        if (!isPasswordValid(password)) {
            Toast.makeText(this, "Mật khẩu không đạt yêu cầu! Cần từ 8-20 ký tự, gồm chữ hoa, chữ thường, số và ký tự đặc biệt.", Toast.LENGTH_LONG).show();
            edtPassword.requestFocus();
            return;
        }

        if (!password.equals(confirmPassword)) {
            Toast.makeText(this, "Mật khẩu xác nhận không khớp!", Toast.LENGTH_SHORT).show();
            edtConfirmPassword.requestFocus();
            return;
        }

        if (!cbTerms.isChecked()) {
            Toast.makeText(this, "Vui lòng tích đồng ý với điều khoản và điều kiện để tiếp tục!", Toast.LENGTH_SHORT).show();
            return;
        }

        if (databaseHelper.usernameExists(username)) {
            Toast.makeText(this, "Tên đăng nhập này đã được sử dụng!", Toast.LENGTH_SHORT).show();
            edtUsername.requestFocus();
            return;
        }

        if (databaseHelper.emailExists(email)) {
            Toast.makeText(this, "Email này đã được đăng ký tài khoản!", Toast.LENGTH_SHORT).show();
            edtEmail.requestFocus();
            return;
        }

        long userId = databaseHelper.registerUser(username, email, password, fullName);
        if (userId > 0) {
            SessionManager.setUserId(this, userId);
            Toast.makeText(this, "Đăng ký tài khoản thành công!", Toast.LENGTH_SHORT).show();
            Intent intent = new Intent(this, MainActivity.class);
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
            startActivity(intent);
            finish();
        } else {
            Toast.makeText(this, "Có lỗi xảy ra khi tạo tài khoản, vui lòng thử lại!", Toast.LENGTH_SHORT).show();
        }
    }
}