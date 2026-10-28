package com.example.apppet;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.widget.ListView;
import android.widget.TextView;

import com.example.apppet.data.AppExecutors;
import com.example.apppet.data.SessionManager;
import com.example.apppet.model.Community;
import com.example.apppet.net.ChatRepository;
import com.example.apppet.ui.CommunityAdapter;
import com.example.apppet.ui.Errors;

import java.util.List;

public class CommunityListActivity extends Activity {
    private ChatRepository chat;
    private int loadToken = 0;
    private CommunityAdapter adapter;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (!SessionManager.isLoggedIn(this)) {
            finish();
            return;
        }
        setContentView(R.layout.activity_community_list);
        chat = new ChatRepository(this);

        findViewById(R.id.btnBackCommunities).setOnClickListener(v -> finish());

        ListView list = findViewById(R.id.listCommunities);
        TextView empty = findViewById(R.id.tvCommunityEmpty);
        adapter = new CommunityAdapter(this);
        list.setAdapter(adapter);
        list.setEmptyView(empty);
        list.setOnItemClickListener((parent, view, position, id) -> {
            Community selected = adapter.getItem(position);
            Intent intent = new Intent(CommunityListActivity.this, ChatActivity.class);
            intent.putExtra(ChatActivity.EXTRA_COMMUNITY_ID, selected.id);
            startActivity(intent);
        });
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (chat == null) return;
        // my_communities() tự cho người dùng vào nhóm của loài vừa thêm.
        final int token = ++loadToken;
        AppExecutors.runResult(() -> chat.myCommunities(), result -> {
            if (isFinishing() || isDestroyed() || token != loadToken) return;
            if (!result.isOk()) {
                Errors.show(this, result);
                return;
            }
            List<Community> communities = result.value;
            adapter.setItems(communities);
        });
    }
}
