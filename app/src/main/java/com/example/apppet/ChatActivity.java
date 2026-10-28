package com.example.apppet;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.View;
import android.text.InputType;
import android.widget.ArrayAdapter;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.ScrollView;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import com.example.apppet.data.AppExecutors;
import com.example.apppet.data.CatalogDatabase;
import com.example.apppet.data.SessionManager;
import com.example.apppet.model.Community;
import com.example.apppet.model.Membership;
import com.example.apppet.model.Message;
import com.example.apppet.model.User;
import com.example.apppet.model.WikiCategory;
import com.example.apppet.net.ApiException;
import com.example.apppet.net.ChatRepository;
import com.example.apppet.net.ContributionRepository;
import com.example.apppet.net.RealtimeClient;
import com.example.apppet.net.UserRepository;
import com.example.apppet.ui.Errors;
import com.example.apppet.ui.MessageAdapter;

import java.util.ArrayList;
import java.util.List;
import java.util.TreeMap;

/**
 * Phòng chat của một cộng đồng. Tin mới đến gần như tức thì nhờ Supabase Realtime; nếu Realtime không kết nối được
 * thì app tự quay về hỏi server mỗi 3 giây. Mỗi lần chỉ tải phần tin mới/đã đổi (cursor), không tải lại cả phòng.
 */
public class ChatActivity extends Activity {
    public static final String EXTRA_COMMUNITY_ID = "com.example.apppet.COMMUNITY_ID";
    private static final int FIRST_PAGE = 100;
    private static final long POLL_FAST_MS = 3000L;      // Realtime không hoạt động
    private static final long POLL_SLOW_MS = 30000L;     // lưới an toàn khi Realtime đang chạy
    private static final long REFRESH_DEBOUNCE_MS = 250L;

    private static final String[] REPORT_LABELS = {
            "Spam hoặc quảng cáo", "Ngôn từ xúc phạm", "Nội dung không phù hợp", "Lý do khác"
    };
    private static final String[] REPORT_CODES = {"SPAM", "HARASSMENT", "INAPPROPRIATE", "OTHER"};

    private ChatRepository chat;
    private UserRepository users;
    private ContributionRepository contrib;
    private CatalogDatabase catalog;
    private RealtimeClient realtime;
    private MessageAdapter adapter;
    private ListView listMessages;
    private EditText etMessage;
    private View inputBar;
    private TextView tvNotice;
    private long communityId;
    private String userId;
    private boolean admin = false;
    private Membership membership;
    private boolean ready = false;
    private boolean offline = false;
    private boolean realtimeUp = false;
    private boolean firstLoad = true;
    private boolean refreshing = false;
    private boolean refreshAgain = false;
    private boolean sending = false;
    private String cursor = null;
    private final TreeMap<Long, Message> messages = new TreeMap<>();

    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Runnable poller = new Runnable() {
        @Override
        public void run() {
            refresh(false);
            handler.postDelayed(this, realtimeUp ? POLL_SLOW_MS : POLL_FAST_MS);
        }
    };
    private final Runnable debouncedRefresh = () -> refresh(false);

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        userId = SessionManager.getUserId(this);
        communityId = getIntent().getLongExtra(EXTRA_COMMUNITY_ID, -1L);
        if (userId.isEmpty() || communityId <= 0) {
            finish();
            return;
        }
        chat = new ChatRepository(this);
        users = new UserRepository(this);
        contrib = new ContributionRepository(this);
        catalog = CatalogDatabase.getInstance(this);

        setContentView(R.layout.activity_chat);
        findViewById(R.id.btnBackChat).setOnClickListener(v -> finish());
        listMessages = findViewById(R.id.listMessages);
        listMessages.setEmptyView(findViewById(R.id.tvChatEmpty));
        etMessage = findViewById(R.id.etMessage);
        inputBar = findViewById(R.id.inputBar);
        tvNotice = findViewById(R.id.tvChatNotice);
        inputBar.setVisibility(View.GONE);

        adapter = new MessageAdapter(this, userId);
        listMessages.setAdapter(adapter);
        listMessages.setOnItemLongClickListener((parent, view, position, id) -> {
            showMessageActions(adapter.getItem(position));
            return true;
        });
        findViewById(R.id.btnSend).setOnClickListener(v -> sendMessage());

        loadRoomInfo();
    }

    /** Thông tin nhóm, quyền của người dùng và vai trò hệ thống: 3 lời gọi nhỏ ở luồng nền. */
    private void loadRoomInfo() {
        AppExecutors.runResult(() -> {
            Object[] info = new Object[3];
            info[0] = chat.getCommunity(communityId);
            info[1] = chat.myMembership(communityId);
            User me = users.getProfile(userId);
            info[2] = me != null && me.isAdmin();
            return info;
        }, result -> {
            if (isFinishing() || isDestroyed()) return;
            if (!result.isOk()) {
                if (!Errors.show(this, result)) finish();
                return;
            }
            Community community = (Community) result.value[0];
            Membership m = (Membership) result.value[1];
            if (community == null || m == null || m.isBanned()) {
                Toast.makeText(this, "Bạn không có quyền vào nhóm này.", Toast.LENGTH_LONG).show();
                finish();
                return;
            }
            membership = m;
            admin = Boolean.TRUE.equals(result.value[2]);
            ((TextView) findViewById(R.id.tvChatTitle)).setText(community.name);
            ((TextView) findViewById(R.id.tvChatSubtitle)).setText(community.memberCount + " thành viên");
            ready = true;
            applyState();
            startLive();
        });
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (ready) startLive();
    }

    @Override
    protected void onPause() {
        super.onPause();
        stopLive();
    }

    private void startLive() {
        stopLive();
        realtime = new RealtimeClient(this, communityId, new RealtimeClient.Listener() {
            @Override
            public void onChange() {
                AppExecutors.main(() -> {
                    handler.removeCallbacks(debouncedRefresh);
                    handler.postDelayed(debouncedRefresh, REFRESH_DEBOUNCE_MS);
                });
            }

            @Override
            public void onStatus(boolean connected) {
                AppExecutors.main(() -> {
                    realtimeUp = connected;
                    if (connected) refresh(false);        // bù tin có thể đến lúc đang mất kết nối
                });
            }
        });
        realtime.start();
        handler.removeCallbacks(poller);
        handler.post(poller);
    }

    private void stopLive() {
        handler.removeCallbacks(poller);
        handler.removeCallbacks(debouncedRefresh);
        if (realtime != null) realtime.stop();
        realtime = null;
        realtimeUp = false;
    }

    private void applyState() {
        if (membership == null) return;
        if (!membership.canSend()) {
            inputBar.setVisibility(View.GONE);
            tvNotice.setVisibility(View.VISIBLE);
            tvNotice.setText("Bạn đang bị tạm khóa chat trong nhóm này nên chỉ có thể đọc tin nhắn.");
        } else {
            inputBar.setVisibility(View.VISIBLE);
            if (offline) {
                tvNotice.setVisibility(View.VISIBLE);
                tvNotice.setText("Mất kết nối, đang thử lại…");
            } else {
                tvNotice.setVisibility(View.GONE);
            }
        }
    }

    private void sendMessage() {
        final String text = etMessage.getText().toString().trim();
        if (text.isEmpty() || sending) return;
        sending = true;
        AppExecutors.runResult(() -> chat.sendMessage(communityId, text), result -> {
            sending = false;
            if (isFinishing() || isDestroyed()) return;
            if (!result.isOk()) {
                if (!Errors.show(this, result)) reloadMembership();   // có thể vừa bị khóa chat/loại khỏi nhóm
                return;
            }
            etMessage.setText("");
            refresh(true);
        });
    }

    private void reloadMembership() {
        AppExecutors.runResult(() -> chat.myMembership(communityId), result -> {
            if (isFinishing() || isDestroyed() || !result.isOk()) return;
            if (result.value == null || result.value.isBanned()) {
                Toast.makeText(this, "Bạn không còn quyền vào nhóm này.", Toast.LENGTH_LONG).show();
                finish();
                return;
            }
            membership = result.value;
            applyState();
        });
    }

    /** Một lượt đồng bộ tại một thời điểm; yêu cầu đến khi đang chạy sẽ được chạy lại ngay sau đó. */
    private void refresh(final boolean forceScroll) {
        if (!ready) return;
        if (refreshing) {
            refreshAgain = true;
            return;
        }
        refreshing = true;
        final String since = cursor;
        AppExecutors.runResult(() -> chat.getMessages(communityId, since, FIRST_PAGE), result -> {
            refreshing = false;
            if (isFinishing() || isDestroyed()) return;
            if (!result.isOk()) {
                if (result.error instanceof ApiException && ((ApiException) result.error).isSessionExpired()) {
                    Errors.show(this, result);
                    return;
                }
                boolean wasOffline = offline;
                offline = result.error instanceof ApiException && ((ApiException) result.error).status == 0;
                if (offline != wasOffline) applyState();
            } else {
                if (offline) {
                    offline = false;
                    applyState();
                }
                merge(result.value, forceScroll);
            }
            if (refreshAgain) {
                refreshAgain = false;
                refresh(false);
            }
        });
    }

    private void merge(ChatRepository.Page page, boolean forceScroll) {
        cursor = page.cursor;
        boolean changed = false;
        for (Message m : page.items) {
            Message old = messages.put(m.id, m);
            if (old == null || old.deleted != m.deleted) changed = true;
        }
        if (!changed && !forceScroll && !firstLoad) return;
        boolean nearBottom = adapter.getCount() == 0 || listMessages.getLastVisiblePosition() >= adapter.getCount() - 2;
        adapter.setItems(new ArrayList<>(messages.values()));
        if ((firstLoad || forceScroll || nearBottom) && adapter.getCount() > 0) {
            listMessages.setSelection(adapter.getCount() - 1);
        }
        firstLoad = false;
    }

    private boolean canModerate() {
        return (membership != null && membership.isModerator()) || admin;
    }

    private void showMessageActions(final Message message) {
        if (message.deleted) return;
        boolean mine = userId.equals(message.senderId);

        final List<String> labels = new ArrayList<>();
        final List<Integer> actions = new ArrayList<>();
        labels.add("Sao chép");
        actions.add(0);
        if (!mine && message.senderId != null) {
            labels.add("Báo cáo tin nhắn");
            actions.add(1);
        }
        if (mine || canModerate()) {
            labels.add(mine ? "Xóa tin nhắn" : "Xóa tin nhắn (kiểm duyệt)");
            actions.add(2);
        }
        if (message.content != null && message.content.trim().length() >= 20 && message.senderId != null) {
            labels.add("Đề xuất đưa vào wiki");
            actions.add(3);
        }

        new AlertDialog.Builder(this)
                .setItems(labels.toArray(new String[0]), (dialog, which) -> {
                    switch (actions.get(which)) {
                        case 0: copyMessage(message); break;
                        case 1: showReportDialog(message); break;
                        case 3: showProposeWikiDialog(message); break;
                        default: confirmDelete(message); break;
                    }
                })
                .show();
    }

    private int dp(int value) {
        return (int) (value * getResources().getDisplayMetrics().density);
    }

    /**
     * Đề xuất biến nội dung trao đổi hữu ích thành bài wiki. Người đề xuất được sửa nội dung (bỏ thông tin cá nhân,
     * trình bày lại) trước khi gửi; bài chờ quản trị viên duyệt mới hiện trong mục Wiki của mọi người.
     */
    private void showProposeWikiDialog(final Message message) {
        final List<WikiCategory> categories = catalog.getWikiCategories();
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(dp(20), dp(8), dp(20), dp(8));

        TextView note = new TextView(this);
        note.setTextSize(13);
        note.setText("Bài viết được quản trị viên duyệt trước khi hiển thị. Hãy bỏ số điện thoại, địa chỉ và thông tin cá nhân khỏi nội dung.");
        box.addView(note);

        final EditText etTitle = new EditText(this);
        etTitle.setHint("Tiêu đề bài viết (5-120 ký tự)");
        etTitle.setSingleLine(true);
        box.addView(etTitle);

        final Spinner spCategory = new Spinner(this);
        spCategory.setAdapter(new ArrayAdapter<>(this, android.R.layout.simple_spinner_dropdown_item, categories));
        box.addView(spCategory);

        final EditText etContent = new EditText(this);
        etContent.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_MULTI_LINE);
        etContent.setGravity(Gravity.TOP | Gravity.START);
        etContent.setMinLines(6);
        etContent.setText(message.content);
        box.addView(etContent);

        final CheckBox cbAuthor = new CheckBox(this);
        cbAuthor.setText("Hiển thị tên tôi là người đề xuất");
        box.addView(cbAuthor);

        ScrollView scroll = new ScrollView(this);
        scroll.addView(box);
        final AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle("Đề xuất đưa vào wiki")
                .setView(scroll)
                .setPositiveButton("Gửi", null)          // gắn xử lý bên dưới để thông báo lỗi mà không đóng hộp thoại
                .setNegativeButton("Hủy", null)
                .create();
        dialog.show();
        dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
            final String title = etTitle.getText().toString().trim();
            final String content = etContent.getText().toString().trim();
            if (title.length() < 5 || title.length() > 120 || content.length() < 20 || content.length() > 4000) {
                Toast.makeText(this, "Tiêu đề cần 5-120 ký tự, nội dung cần 20-4000 ký tự.", Toast.LENGTH_LONG).show();
                return;
            }
            final WikiCategory category = (WikiCategory) spCategory.getSelectedItem();
            final boolean showAuthor = cbAuthor.isChecked();
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setEnabled(false);
            AppExecutors.runResult(() -> contrib.proposeWiki(message.id, title, category.id, content, null, showAuthor), result -> {
                if (isFinishing() || isDestroyed()) return;
                if (!result.isOk()) {
                    dialog.getButton(AlertDialog.BUTTON_POSITIVE).setEnabled(true);
                    Errors.show(this, result);
                    return;
                }
                dialog.dismiss();
                Toast.makeText(this, "Đã gửi đề xuất. Cảm ơn bạn đã chia sẻ kinh nghiệm!", Toast.LENGTH_LONG).show();
            });
        });
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
                .setItems(REPORT_LABELS, (dialog, which) -> {
                    final String code = REPORT_CODES[which];
                    AppExecutors.runResult(() -> chat.reportMessage(message.id, code, null), result -> {
                        if (isFinishing() || isDestroyed()) return;
                        if (!result.isOk()) {
                            Errors.show(this, result);
                            return;
                        }
                        Toast.makeText(this,
                                Boolean.TRUE.equals(result.value) ? "Đã gửi báo cáo. Cảm ơn bạn!"
                                        : "Không gửi được báo cáo (bạn đã báo cáo tin này rồi hoặc đây là tin của bạn).",
                                Toast.LENGTH_SHORT).show();
                    });
                })
                .setNegativeButton("Hủy", null)
                .show();
    }

    private void confirmDelete(final Message message) {
        new AlertDialog.Builder(this)
                .setTitle("Xóa tin nhắn?")
                .setMessage("Tin nhắn sẽ hiển thị là \"đã bị xóa\" với mọi người trong nhóm.")
                .setPositiveButton("Xóa", (dialog, which) ->
                        AppExecutors.runResult(() -> chat.deleteMessage(message.id), result -> {
                            if (isFinishing() || isDestroyed()) return;
                            if (!result.isOk()) {
                                Errors.show(this, result);
                            } else if (Boolean.TRUE.equals(result.value)) {
                                refresh(true);
                            } else {
                                Toast.makeText(this, "Bạn không có quyền xóa tin nhắn này.", Toast.LENGTH_SHORT).show();
                            }
                        }))
                .setNegativeButton("Hủy", null)
                .show();
    }

    @Override
    protected void onDestroy() {
        stopLive();
        super.onDestroy();
    }
}
