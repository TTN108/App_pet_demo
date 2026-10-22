package com.example.apppet;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.widget.EditText;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import com.example.apppet.data.DatabaseHelper;
import com.example.apppet.data.SessionManager;
import com.example.apppet.model.Community;
import com.example.apppet.model.Membership;
import com.example.apppet.model.Message;
import com.example.apppet.model.User;
import com.example.apppet.ui.MessageAdapter;

import java.util.ArrayList;
import java.util.List;

/** Phòng chat của một cộng đồng. Tự làm mới 2 giây/lần để thấy tin nhắn mới. */
public class ChatActivity extends Activity {
    public static final String EXTRA_COMMUNITY_ID = "com.example.apppet.COMMUNITY_ID";
    private static final int PAGE_SIZE = 150;
    private static final long POLL_MS = 2000L;

    private static final String[] REPORT_REASONS = {
            "Spam hoặc quảng cáo", "Ngôn từ xúc phạm", "Nội dung không phù hợp", "Lý do khác"
    };

    private DatabaseHelper databaseHelper;
    private MessageAdapter adapter;
    private ListView listMessages;
    private EditText etMessage;
    private View inputBar;
    private TextView tvNotice;
    private long communityId;
    private long userId;
    private User currentUser;
    private Membership membership;
    private String lastSignature = "";
    private boolean firstLoad = true;

    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Runnable poller = new Runnable() {
        @Override
        public void run() {
            refresh(false);
            handler.postDelayed(this, POLL_MS);
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        userId = SessionManager.getUserId(this);
        communityId = getIntent().getLongExtra(EXTRA_COMMUNITY_ID, -1L);
        if (userId <= 0 || communityId <= 0) {
            finish();
            return;
        }
        databaseHelper = new DatabaseHelper(this);
        currentUser = databaseHelper.getUser(userId);
        Community community = databaseHelper.getCommunity(communityId);
        membership = databaseHelper.getMembership(communityId, userId);
        if (currentUser == null || community == null || membership == null || membership.isBanned()) {
            Toast.makeText(this, "Bạn không có quyền vào nhóm này.", Toast.LENGTH_LONG).show();
            finish();
            return;
        }

        setContentView(R.layout.activity_chat);
        ((TextView) findViewById(R.id.tvChatTitle)).setText(community.name);
        ((TextView) findViewById(R.id.tvChatSubtitle)).setText(community.memberCount + " thành viên");
        findViewById(R.id.btnBackChat).setOnClickListener(v -> finish());

        listMessages = findViewById(R.id.listMessages);
        listMessages.setEmptyView(findViewById(R.id.tvChatEmpty));
        etMessage = findViewById(R.id.etMessage);
        inputBar = findViewById(R.id.inputBar);
        tvNotice = findViewById(R.id.tvChatNotice);

        adapter = new MessageAdapter(this, userId);
        listMessages.setAdapter(adapter);
        listMessages.setOnItemLongClickListener((parent, view, position, id) -> {
            showMessageActions(adapter.getItem(position));
            return true;
        });

        findViewById(R.id.btnSend).setOnClickListener(v -> sendMessage());
        applyMembershipState();
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (databaseHelper == null || membership == null) return;
        membership = databaseHelper.getMembership(communityId, userId);
        if (membership == null || membership.isBanned()) {
            Toast.makeText(this, "Bạn không còn quyền vào nhóm này.", Toast.LENGTH_LONG).show();
            finish();
            return;
        }
        applyMembershipState();
        handler.removeCallbacks(poller);
        refresh(false);
        handler.postDelayed(poller, POLL_MS);
    }

    @Override
    protected void onPause() {
        super.onPause();
        handler.removeCallbacks(poller);
    }

    private void applyMembershipState() {
        if (membership.canSend()) {
            inputBar.setVisibility(View.VISIBLE);
            tvNotice.setVisibility(View.GONE);
        } else {
            inputBar.setVisibility(View.GONE);
            tvNotice.setVisibility(View.VISIBLE);
            tvNotice.setText("Bạn đang bị tạm khóa chat trong nhóm này nên chỉ có thể đọc tin nhắn.");
        }
    }

    private void sendMessage() {
        String text = etMessage.getText().toString().trim();
        if (text.isEmpty()) return;
        long id = databaseHelper.insertMessage(communityId, userId, text);
        if (id < 0) {
            Toast.makeText(this, "Không thể gửi tin nhắn lúc này.", Toast.LENGTH_SHORT).show();
            membership = databaseHelper.getMembership(communityId, userId);
            if (membership == null || membership.isBanned()) {
                finish();
                return;
            }
            applyMembershipState();
            return;
        }
        etMessage.setText("");
        refresh(true);
    }

    private void refresh(boolean forceScroll) {
        List<Message> items = databaseHelper.getRecentMessages(communityId, PAGE_SIZE);
        String signature = signature(items);
        if (!forceScroll && signature.equals(lastSignature)) return;
        lastSignature = signature;
        adapter.setItems(items);
        if ((firstLoad || forceScroll) && adapter.getCount() > 0) {
            listMessages.setSelection(adapter.getCount() - 1);
        }
        firstLoad = false;
    }

    /** Thay đổi khi có tin mới hoặc có tin bị xóa, để tránh vẽ lại danh sách vô ích. */
    private String signature(List<Message> items) {
        int deleted = 0;
        for (Message m : items) if (m.deleted) deleted++;
        long lastId = items.isEmpty() ? 0L : items.get(items.size() - 1).id;
        return lastId + ":" + items.size() + ":" + deleted;
    }

    private boolean canModerate() {
        return (membership != null && membership.isModerator()) || (currentUser != null && currentUser.isAdmin());
    }

    private void showMessageActions(final Message message) {
        if (message.deleted) return;
        boolean mine = message.senderId == userId;

        final List<String> labels = new ArrayList<>();
        final List<Integer> actions = new ArrayList<>();
        labels.add("Sao chép");
        actions.add(0);
        if (!mine) {
            labels.add("Báo cáo tin nhắn");
            actions.add(1);
        }
        if (mine || canModerate()) {
            labels.add(mine ? "Xóa tin nhắn" : "Xóa tin nhắn (kiểm duyệt)");
            actions.add(2);
        }

        new AlertDialog.Builder(this)
                .setItems(labels.toArray(new String[0]), (dialog, which) -> {
                    switch (actions.get(which)) {
                        case 0: copyMessage(message); break;
                        case 1: showReportDialog(message); break;
                        default: confirmDelete(message); break;
                    }
                })
                .show();
    }

    private void copyMessage(Message message) {
        ClipboardManager clipboard = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
        if (clipboard != null) {
            clipboard.setPrimaryClip(ClipData.newPlainText("message", message.content));
            Toast.makeText(this, "Đã sao chép tin nhắn.", Toast.LENGTH_SHORT).show();
        }
    }

    private void showReportDialog(final Message message) {
        new AlertDialog.Builder(this)
                .setTitle("Báo cáo tin nhắn")
                .setItems(REPORT_REASONS, (dialog, which) -> {
                    boolean created = databaseHelper.reportMessage(message.id, userId, REPORT_REASONS[which], null);
                    Toast.makeText(this,
                            created ? "Đã gửi báo cáo. Cảm ơn bạn!" : "Bạn đã báo cáo tin nhắn này rồi.",
                            Toast.LENGTH_SHORT).show();
                })
                .setNegativeButton("Hủy", null)
                .show();
    }

    private void confirmDelete(final Message message) {
        new AlertDialog.Builder(this)
                .setTitle("Xóa tin nhắn?")
                .setMessage("Tin nhắn sẽ hiển thị là \"đã bị xóa\" với mọi người trong nhóm.")
                .setPositiveButton("Xóa", (dialog, which) -> {
                    if (databaseHelper.deleteMessage(message.id, userId)) {
                        refresh(true);
                    } else {
                        Toast.makeText(this, "Bạn không có quyền xóa tin nhắn này.", Toast.LENGTH_SHORT).show();
                    }
                })
                .setNegativeButton("Hủy", null)
                .show();
    }
}
