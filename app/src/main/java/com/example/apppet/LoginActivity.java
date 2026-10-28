package com.example.apppet;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.EditText;
import android.widget.TextView;

import com.example.apppet.data.AppExecutors;
import com.example.apppet.data.SessionManager;
import com.example.apppet.net.UserRepository;

public class LoginActivity extends Activity {
    private UserRepository users;
    private EditText etLogin, etPassword;
    private TextView tvError;
    private View btnLogin;
    private boolean busy = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (SessionManager.isLoggedIn(this)) {
            openMain();
            return;
        }
        setContentView(R.layout.activity_login);
        users = new UserRepository(this);

        etLogin = findViewById(R.id.etLogin);
        etPassword = findViewById(R.id.etPassword);
        tvError = findViewById(R.id.tvLoginError);

        btnLogin = findViewById(R.id.btnLogin);
        btnLogin.setOnClickListener(v -> attemptLogin());
        findViewById(R.id.tvGoRegister).setOnClickListener(v ->
                startActivity(new Intent(LoginActivity.this, RegisterActivity.class)));
        etPassword.setOnEditorActionListener((v, actionId, event) -> {
            attemptLogin();
            return true;
        });
    }

    private void attemptLogin() {
        final String login = etLogin.getText().toString().trim();
        final String password = etPassword.getText().toString();
        if (login.isEmpty() || password.isEmpty()) {
            showError("Vui lòng nhập tên đăng nhập/email và mật khẩu.");
            return;
        }
        if (busy) return;
        busy = true;
        btnLogin.setEnabled(false);
        tvError.setVisibility(View.GONE);
        AppExecutors.runResult(() -> users.signIn(login, password), result -> {
            busy = false;
            if (isFinishing() || isDestroyed()) return;
            btnLogin.setEnabled(true);
            if (!result.isOk()) {
                showError(result.message());
                return;
            }
            openMain();
        });
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