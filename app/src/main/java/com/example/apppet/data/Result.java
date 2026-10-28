package com.example.apppet.data;

import com.example.apppet.net.ApiException;

/** Kết quả của một việc chạy nền: có giá trị hoặc có lỗi (để hiện thông báo đúng cho người dùng). */
public final class Result<T> {
    public final T value;
    public final RuntimeException error;

    private Result(T value, RuntimeException error) {
        this.value = value;
        this.error = error;
    }

    public static <T> Result<T> ok(T value) {
        return new Result<>(value, null);
    }

    public static <T> Result<T> fail(RuntimeException error) {
        return new Result<>(null, error);
    }

    public boolean isOk() {
        return error == null;
    }

    public String message() {
        if (error instanceof ApiException) return ((ApiException) error).userMessage();
        return "Có lỗi xảy ra, vui lòng thử lại.";
    }
}
