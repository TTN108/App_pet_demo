package com.example.apppet;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.widget.ListView;
import android.widget.TextView;

import com.example.apppet.data.DatabaseHelper;
import com.example.apppet.data.SessionManager;
import com.example.apppet.model.Community;
import com.example.apppet.ui.CommunityAdapter;

import java.util.List;

public class CommunityListActivity extends Activity {
    private DatabaseHelper databaseHelper;
    private CommunityAdapter adapter;
    private long userId;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        userId = SessionManager.getUserId(this);
        if (userId <= 0) {
            finish();
            return;
        }
        setContentView(R.layout.activity_community_list);
        databaseHelper = new DatabaseHelper(this);

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
        if (databaseHelper == null) return;
        // Đồng bộ lại trong trường hợp vừa thêm thú cưng thuộc loại mới.
        databaseHelper.syncCommunityMemberships(userId);
        List<Community> communities = databaseHelper.getCommunitiesForUser(userId);
        adapter.setItems(communities);
    }
}
