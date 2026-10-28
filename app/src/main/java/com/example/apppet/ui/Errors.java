package com.example.apppet.ui;

import android.app.Activity;
import android.content.Intent;
import android.widget.Toast;

import com.example.apppet.LoginActivity;
import com.example.apppet.data.Result;
import com.example.apppet.data.SessionManager;
import com.example.apppet.net.ApiException;

/** Hiển thị lỗi mạng/API; phiên hết hạn thì đưa người dùng về màn đăng nhập. */
public final class Errors {
    private Errors() { }

    /** @return true nếu đã chuyển sang màn đăng nhập (Activity gọi nên dừng xử lý tiếp). */
    public static boolean show(Activity activity, Result<?> result) {
        if (result.error instanceof ApiException && ((ApiException) result.error).isSessionExpired()) {
            SessionManager.clear(activity);
            Toast.makeText(activity, result.message(), Toast.LENGTH_LONG).show();
            Intent intent = new Intent(activity, LoginActivity.class);
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
            activity.startActivity(intent);
            activity.finish();
            return true;
        }
        Toast.makeText(activity, result.message(), Toast.LENGTH_LONG).show();
        return false;
    }
}
