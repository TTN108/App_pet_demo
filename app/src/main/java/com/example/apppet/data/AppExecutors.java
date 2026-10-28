package com.example.apppet.data;

import android.os.Handler;
import android.os.Looper;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Consumer;
import java.util.function.Supplier;

/** Chạy việc nặng (DB, mạng, băm mật khẩu, nhận diện ảnh) ngoài luồng UI. */
public final class AppExecutors {
    private static final ExecutorService IO = Executors.newFixedThreadPool(3);
    private static final Handler MAIN = new Handler(Looper.getMainLooper());

    private AppExecutors() { }

    public static void io(Runnable task) {
        IO.execute(task);
    }

    public static void main(Runnable task) {
        MAIN.post(task);
    }

    /** Chạy work ở luồng nền rồi trả kết quả về luồng UI. Nếu work ném lỗi, done nhận null. */
    public static <T> void run(Supplier<T> work, Consumer<T> done) {
        IO.execute(() -> {
            T result = null;
            try {
                result = work.get();
            } catch (RuntimeException ignored) {
                // done(null) báo cho UI biết là thất bại
            }
            final T finalResult = result;
            MAIN.post(() -> done.accept(finalResult));
        });
    }

    /** Như run() nhưng giữ lại lỗi (ApiException có thông báo tiếng Việt) thay vì trả null. */
    public static <T> void runResult(Supplier<T> work, Consumer<Result<T>> done) {
        IO.execute(() -> {
            Result<T> result;
            try {
                result = Result.ok(work.get());
            } catch (RuntimeException ex) {
                result = Result.fail(ex);
            }
            final Result<T> finalResult = result;
            MAIN.post(() -> done.accept(finalResult));
        });
    }
}
