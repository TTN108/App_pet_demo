package com.example.apppet.work;

import android.content.Context;

import androidx.work.BackoffPolicy;
import androidx.work.Constraints;
import androidx.work.Data;
import androidx.work.NetworkType;
import androidx.work.OneTimeWorkRequest;
import androidx.work.WorkManager;

import java.util.concurrent.TimeUnit;

/** Xếp hàng gửi ảnh đóng góp: tồn tại qua khi tắt app/mất mạng, tự gửi khi có kết nối. */
public final class ContributionQueue {
    private ContributionQueue() { }

    /**
     * @param localFileUri file:// trong bộ nhớ của app (worker sẽ xóa sau khi gửi xong)
     * @param origin       "UPLOAD" hoặc "CAMERA"
     * @param recognitionId id kết quả nhận diện (ảnh từ camera) hoặc -1
     */
    public static void enqueue(Context context, String localFileUri, long petTypeId, long breedId,
                               String origin, long recognitionId, String title) {
        Data data = new Data.Builder()
                .putString(ContributionWorker.K_FILE, localFileUri)
                .putLong(ContributionWorker.K_TYPE, petTypeId)
                .putLong(ContributionWorker.K_BREED, breedId)
                .putString(ContributionWorker.K_ORIGIN, origin)
                .putLong(ContributionWorker.K_RECOGNITION, recognitionId)
                .putString(ContributionWorker.K_TITLE, title)
                .build();
        OneTimeWorkRequest request = new OneTimeWorkRequest.Builder(ContributionWorker.class)
                .setConstraints(new Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS)
                .setInputData(data)
                .build();
        WorkManager.getInstance(context.getApplicationContext()).enqueue(request);
    }
}
