package com.example.apppet;

import android.app.Application;

import com.example.apppet.data.AppExecutors;
import com.example.apppet.data.CatalogDatabase;

public class AppPetApplication extends Application {
    @Override
    public void onCreate() {
        super.onCreate();
        // Dữ liệu tài khoản/thú cưng nay nằm trên Supabase: xóa DB cục bộ của các phiên bản cũ.
        deleteDatabase("app_pet_demo_v2.db");
        // Mở catalog (tạo + nạp seed lần đầu) ngoài luồng UI.
        AppExecutors.io(() -> CatalogDatabase.getInstance(this).getWritableDatabase());
    }
}
