package com.example.apppet;

import android.os.Bundle;
import android.view.View;
import android.widget.EditText;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;

import com.example.apppet.data.AppExecutors;
import com.example.apppet.data.SessionManager;
import com.example.apppet.model.AdminItem;
import com.example.apppet.model.User;
import com.example.apppet.net.AdminRepository;
import com.example.apppet.net.UserRepository;
import com.example.apppet.ui.AdminAdapter;
import com.example.apppet.ui.Errors;
import com.example.apppet.ui.UiThemeManager;

import java.util.List;

/**
 * Hàng chờ duyệt cho quản trị viên: ảnh đóng góp, bài wiki đề xuất từ chat, báo cáo tin nhắn.
 * Quyền được server kiểm tra ở từng thao tác; app chỉ ẩn màn này với người không phải ADMIN.
 */
public class AdminActivity extends AppCompatActivity implements AdminAdapter.Listener {
    private AdminRepository admin;
    private AdminAdapter adapter;
    private TextView tabImages, tabWiki, tabReports, txtEmpty, txtCount;
    private int currentTab = AdminItem.TYPE_IMAGE;
    private int loadToken = 0;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_admin);
        UiThemeManager.applyTheme(findViewById(R.id.rootAdmin), this);
        admin = new AdminRepository(this);

        tabImages = findViewById(R.id.tabAdminImages);
        tabWiki = findViewById(R.id.tabAdminWiki);
        tabReports = findViewById(R.id.tabAdminReports);
        txtEmpty = findViewById(R.id.txtAdminEmpty);
        txtCount = findViewById(R.id.txtAdminCount);
        ListView list = findViewById(R.id.listAdmin);
        adapter = new AdminAdapter(this, this);
        list.setAdapter(adapter);
        findViewById(R.id.btnBackAdmin).setOnClickListener(v -> finish());
        tabImages.setOnClickListener(v -> selectTab(AdminItem.TYPE_IMAGE));
        tabWiki.setOnClickListener(v -> selectTab(AdminItem.TYPE_WIKI));
        tabReports.setOnClickListener(v -> selectTab(AdminItem.TYPE_REPORT));

        final UserRepository users = new UserRepository(this);
        final String userId = SessionManager.getUserId(this);
        AppExecutors.runResult(() -> users.getProfile(userId), result -> {
            if (isFinishing() || isDestroyed()) return;
            User me = result.isOk() ? result.value : null;
            if (!result.isOk()) {
                Errors.show(this, result);
                if (!isFinishing()) finish();
                return;
            }
            if (me == null || !me.isAdmin()) {
                Toast.makeText(this, "Chỉ quản trị viên mới vào được màn này.", Toast.LENGTH_LONG).show();
                finish();
                return;
            }
            selectTab(AdminItem.TYPE_IMAGE);
        });
    }

    private void selectTab(int tab) {
        currentTab = tab;
        style(tabImages, tab == AdminItem.TYPE_IMAGE);
        style(tabWiki, tab == AdminItem.TYPE_WIKI);
        style(tabReports, tab == AdminItem.TYPE_REPORT);
        load();
    }

    private void style(TextView tab, boolean selected) {
        tab.setBackgroundResource(selected ? R.drawable.bg_primary_button : R.drawable.bg_secondary_button);
        tab.setTextColor(getColor(selected ? R.color.white : R.color.text_primary));
    }

    private void load() {
        final int tab = currentTab;
        final int token = ++loadToken;
        txtCount.setText("Đang tải…");
        AppExecutors.runResult(() -> {
            switch (tab) {
                case AdminItem.TYPE_WIKI: return admin.pendingWiki();
                case AdminItem.TYPE_REPORT: return admin.reports();
                default: return admin.pendingImages();
            }
        }, result -> {
            if (isFinishing() || isDestroyed() || token != loadToken) return;
            if (!result.isOk()) {
                txtCount.setText("Không tải được");
                Errors.show(this, result);
                return;
            }
            List<AdminItem> items = result.value;
            adapter.setItems(items);
            refreshStatus();
        });
    }

    private void refreshStatus() {
        txtEmpty.setVisibility(adapter.getCount() == 0 ? View.VISIBLE : View.GONE);
        txtCount.setText(adapter.getCount() + " mục đang chờ");
    }

    @Override
    public void onYes(final AdminItem item) {
        switch (item.type) {
            case AdminItem.TYPE_IMAGE:
                run(item, () -> admin.moderateImage(item.id, true, null), "Đã duyệt ảnh.");
                break;
            case AdminItem.TYPE_WIKI:
                run(item, () -> admin.moderateWiki(item.id, true, null), "Đã duyệt bài wiki.");
                break;
            default:
                new AlertDialog.Builder(this).setTitle("Xóa tin nhắn vi phạm?")
                        .setMessage("Tin nhắn sẽ bị xóa với mọi người trong nhóm.")
                        .setPositiveButton("Xóa", (d, w) -> run(item, () -> admin.resolveReport(item.id, true), "Đã xóa tin nhắn."))
                        .setNegativeButton("Hủy", null).show();
        }
    }

    @Override
    public void onNo(final AdminItem item) {
        if (item.type == AdminItem.TYPE_REPORT) {
            run(item, () -> admin.resolveReport(item.id, false), "Đã bỏ qua báo cáo.");
            return;
        }
        final EditText reason = new EditText(this);
        reason.setHint("Lý do từ chối (không bắt buộc)");
        new AlertDialog.Builder(this).setTitle("Từ chối mục này?").setView(reason)
                .setPositiveButton("Từ chối", (d, w) -> {
                    final String text = reason.getText().toString().trim();
                    if (item.type == AdminItem.TYPE_IMAGE) {
                        run(item, () -> admin.moderateImage(item.id, false, text), "Đã từ chối ảnh.");
                    } else {
                        run(item, () -> admin.moderateWiki(item.id, false, text), "Đã từ chối bài wiki.");
                    }
                })
                .setNegativeButton("Hủy", null).show();
    }

    private void run(final AdminItem item, final java.util.function.Supplier<Boolean> action, final String doneMessage) {
        AppExecutors.runResult(action, result -> {
            if (isFinishing() || isDestroyed()) return;
            if (!result.isOk()) {
                Errors.show(this, result);
                return;
            }
            if (Boolean.TRUE.equals(result.value)) {
                adapter.remove(item);
                refreshStatus();
                Toast.makeText(this, doneMessage, Toast.LENGTH_SHORT).show();
            } else {
                Toast.makeText(this, "Mục này đã được xử lý trước đó.", Toast.LENGTH_SHORT).show();
                load();
            }
        });
    }
}
