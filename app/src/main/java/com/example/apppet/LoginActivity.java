package com.example.apppet;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.EditText;
import android.widget.TextView;

import com.example.apppet.data.DatabaseHelper;
import com.example.apppet.data.SessionManager;
import com.example.apppet.model.User;

public class LoginActivity extends Activity {
    private DatabaseHelper databaseHelper;
    private EditText etLogin, etPassword;
    private TextView tvError;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (SessionManager.isLoggedIn(this)) {
            openMain();
            return;
        }
        setContentView(R.layout.activity_login);
        databaseHelper = new DatabaseHelper(this);

        etLogin = findViewById(R.id.etLogin);
        etPassword = findViewById(R.id.etPassword);
        tvError = findViewById(R.id.tvLoginError);

        findViewById(R.id.btnLogin).setOnClickListener(v -> attemptLogin());
        findViewById(R.id.tvGoRegister).setOnClickListener(v ->
                startActivity(new Intent(LoginActivity.this, RegisterActivity.class)));
        etPassword.setOnEditorActionListener((v, actionId, event) -> {
            attemptLogin();
            return true;
        });
    }

    private void attemptLogin() {
        String login = etLogin.getText().toString().trim();
        String password = etPassword.getText().toString();
        if (login.isEmpty() || password.isEmpty()) {
            showError("Vui lòng nhập tên đăng nhập/email và mật khẩu.");
            return;
        }
        User user = databaseHelper.authenticate(login, password);
        if (user == null) {
            showError("Tên đăng nhập/email hoặc mật khẩu không đúng.");
            return;
        }
        SessionManager.setUserId(this, user.id);
        openMain();
    }

    private void showError(String message) {
        tvError.setText(message);
        tvError.setVisibility(View.VISIBLE);
    }

    private void openMain() {
        Intent intent = new Intent(this, MainActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(intent);
        finish();
    }
}