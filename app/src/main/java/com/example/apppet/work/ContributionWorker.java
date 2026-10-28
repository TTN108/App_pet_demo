package com.example.apppet.work;

import android.content.Context;
import android.net.Uri;

import androidx.annotation.NonNull;
import androidx.work.Data;
import androidx.work.Worker;
import androidx.work.WorkerParameters;

import com.example.apppet.data.SessionManager;
import com.example.apppet.net.ApiException;
import com.example.apppet.net.ContributionRepository;

import java.io.File;

/**
 * Gửi một ảnh đóng góp lên Supabase ở nền. Chạy khi có mạng; lỗi tạm thời (mất mạng, máy chủ bận) thì tự thử lại
 * theo giãn cách tăng dần; lỗi vĩnh viễn (hết quota ảnh chờ duyệt, sai giống, hết phiên) thì bỏ và xóa file.
 */
public class ContributionWorker extends Worker {
    static final String K_FILE = "file";
    static final String K_TYPE = "type";
    static final String K_BREED = "breed";
    static final String K_ORIGIN = "origin";
    static final String K_RECOGNITION = "recognition";
    static final String K_TITLE = "title";

    public ContributionWorker(@NonNull Context context, @NonNull WorkerParameters params) {
        super(context, params);
    }

    @NonNull
    @Override
    public Result doWork() {
        Data d = getInputData();
        String fileUri = d.getString(K_FILE);
        if (fileUri == null) return Result.failure();
        File file = new File(Uri.parse(fileUri).getPath());
        if (!file.exists()) return Result.failure();
        String userId = SessionManager.getUserId(getApplicationContext());
        if (userId.isEmpty()) {
            file.delete();
            return Result.failure();
        }
        try {
            new ContributionRepository(getApplicationContext()).contributeImage(
                    userId, d.getLong(K_TYPE, 0L), d.getLong(K_BREED, 0L), file,
                    d.getString(K_ORIGIN), d.getLong(K_RECOGNITION, -1L), d.getString(K_TITLE));
            file.delete();
            return Result.success();
        } catch (ApiException ex) {
            if (ex.status == 0 || ex.status == 429 || ex.status >= 500) return Result.retry();
            file.delete();
            return Result.failure();
        } catch (RuntimeException ex) {
            file.delete();
            return Result.failure();
        }
    }
}
