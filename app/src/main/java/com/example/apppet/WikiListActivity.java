package com.example.apppet;

import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.View;
import android.widget.EditText;
import android.widget.Spinner;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.apppet.adapters.WikiAdapter;
import com.example.apppet.data.AppExecutors;
import com.example.apppet.data.CatalogDatabase;
import com.example.apppet.model.WikiArticle;
import com.example.apppet.net.ContributionRepository;
import com.example.apppet.ui.TypeBreedFilter;
import com.example.apppet.ui.UiThemeManager;

import java.util.ArrayList;
import java.util.List;

/**
 * Wiki chăm sóc = bài trong app (dùng được khi mất mạng, hiện ngay) + bài cộng đồng đã được duyệt trên Supabase
 * (hiện thêm khi tải xong; không có mạng thì chỉ thiếu phần này).
 */
public class WikiListActivity extends AppCompatActivity implements WikiAdapter.Listener {
    public static final String EXTRA_PET_TYPE_ID = "pet_type_id";
    public static final String EXTRA_BREED_ID = "breed_id";

    private CatalogDatabase db;
    private ContributionRepository contrib;
    private TypeBreedFilter filter;
    private EditText etSearch;
    private WikiAdapter adapter;
    private TextView txtEmpty, txtCount;
    private final List<WikiArticle> shown = new ArrayList<>();
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Runnable searchRunnable = this::loadArticles;
    private int loadToken = 0;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_wiki_list);
        UiThemeManager.applyTheme(findViewById(R.id.rootWikiList), this);
        db = CatalogDatabase.getInstance(this);
        contrib = new ContributionRepository(this);

        etSearch = findViewById(R.id.etWikiSearch);
        RecyclerView rvWiki = findViewById(R.id.rvWiki);
        txtEmpty = findViewById(R.id.txtWikiEmpty);
        txtCount = findViewById(R.id.txtWikiCount);
        rvWiki.setLayoutManager(new LinearLayoutManager(this));
        adapter = new WikiAdapter(shown, this);
        rvWiki.setAdapter(adapter);
        findViewById(R.id.btnBackWiki).setOnClickListener(v -> finish());

        etSearch.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) { }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) { }

            @Override
            public void afterTextChanged(Editable s) {
                handler.removeCallbacks(searchRunnable);
                handler.postDelayed(searchRunnable, 300);      // chờ người dùng gõ xong
            }
        });

        long typeId = getIntent().getLongExtra(EXTRA_PET_TYPE_ID, 0L);
        long breedId = getIntent().getLongExtra(EXTRA_BREED_ID, 0L);
        filter = new TypeBreedFilter(this, db,
                (Spinner) findViewById(R.id.spnWikiType), (Spinner) findViewById(R.id.spnWikiBreed),
                typeId > 0 ? typeId : null, breedId > 0 ? breedId : null, this::loadArticles);
    }

    private void loadArticles() {
        final Long typeId = filter.selectedTypeId();
        final Long breedId = filter.selectedBreedId();
        final String query = etSearch.getText().toString();
        final int token = ++loadToken;
        // 1) bài trong app: nhanh, không cần mạng
        AppExecutors.run(() -> db.getWikiArticles(typeId, breedId, query, 100), local -> {
            if (isFinishing() || isDestroyed() || token != loadToken) return;
            shown.clear();
            if (local != null) shown.addAll(local);
            adapter.notifyDataSetChanged();
            refreshStatus();
            // 2) bài cộng đồng: thêm vào khi có mạng, lỗi thì bỏ qua (vẫn dùng được bài trong app)
            AppExecutors.runResult(() -> contrib.communityWiki(typeId, breedId, query, 50), remote -> {
                if (isFinishing() || isDestroyed() || token != loadToken || !remote.isOk()) return;
                int start = shown.size();
                shown.addAll(remote.value);
                adapter.notifyItemRangeInserted(start, remote.value.size());
                refreshStatus();
            });
        });
    }

    private void refreshStatus() {
        txtEmpty.setVisibility(shown.isEmpty() ? View.VISIBLE : View.GONE);
        txtCount.setText(shown.size() + " bài viết");
    }

    @Override
    public void onArticleClick(WikiArticle article) {
        Intent intent = new Intent(this, WikiDetailActivity.class);
        intent.putExtra(WikiDetailActivity.EXTRA_ARTICLE_ID, article.id);
        intent.putExtra(WikiDetailActivity.EXTRA_COMMUNITY, article.community);
        startActivity(intent);
    }

    @Override
    protected void onDestroy() {
        handler.removeCallbacks(searchRunnable);
        super.onDestroy();
    }
}
