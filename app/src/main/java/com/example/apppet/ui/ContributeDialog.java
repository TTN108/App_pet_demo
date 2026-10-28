package com.example.apppet.ui;

import android.app.Activity;
import android.view.ViewGroup;
import android.widget.CheckBox;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.appcompat.app.AlertDialog;

/** Hộp thoại xin đồng ý trước khi gửi ảnh đóng góp. Chỉ khi người dùng tích xác nhận mới bấm "Gửi" được. */
public final class ContributeDialog {
    private ContributeDialog() { }

    public static void show(Activity activity, String breedLabel, boolean fromCamera, Runnable onConfirm) {
        int pad = (int) (20 * activity.getResources().getDisplayMetrics().density);
        LinearLayout box = new LinearLayout(activity);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(pad, pad / 2, pad, 0);

        TextView info = new TextView(activity);
        info.setTextSize(14);
        info.setText("Ảnh " + (fromCamera ? "vừa chụp" : "bạn chọn") + " sẽ được gửi cho giống \"" + breedLabel + "\". "
                + "Quản trị viên xem xét trước khi ảnh hiện trong bộ sưu tập, và ảnh đã duyệt có thể được dùng để cải thiện "
                + "khả năng nhận diện giống. Vị trí chụp trong ảnh (nếu có) đã bị xóa.");
        box.addView(info, new ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        final CheckBox consent = new CheckBox(activity);
        consent.setText("Tôi xác nhận ảnh do tôi chụp hoặc tôi được phép chia sẻ, và không chứa người hay thông tin cá nhân.");
        consent.setTextSize(13);
        box.addView(consent);

        final AlertDialog dialog = new AlertDialog.Builder(activity)
                .setTitle("Đóng góp ảnh cho bộ sưu tập")
                .setView(box)
                .setPositiveButton("Gửi", (d, w) -> onConfirm.run())
                .setNegativeButton("Hủy", null)
                .create();
        dialog.show();
        dialog.getButton(AlertDialog.BUTTON_POSITIVE).setEnabled(false);
        consent.setOnCheckedChangeListener((button, checked) ->
                dialog.getButton(AlertDialog.BUTTON_POSITIVE).setEnabled(checked));
    }
}
