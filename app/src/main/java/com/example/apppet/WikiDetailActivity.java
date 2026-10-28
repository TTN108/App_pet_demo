package com.example.apppet;

import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.view.View;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.example.apppet.data.AppExecutors;
import com.example.apppet.data.CatalogDatabase;
import com.example.apppet.model.WikiArticle;
import com.example.apppet.net.ContributionRepository;
import com.example.apppet.ui.Errors;
import com.example.apppet.ui.UiThemeManager;

/** Nội dung một bài wiki (trong app hoặc từ cộng đồng), kèm ghi nguồn và nút mở bài gốc. */
public class WikiDetailActivity extends AppCompatActivity {
    public static final String EXTRA_ARTICLE_ID = "article_id";
    public static final String EXTRA_COMMUNITY = "community";

    private TextView txtTitle, txtMeta, txtContent, btnOpenSource;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_wiki_detail);
        UiThemeManager.applyTheme(findViewById(R.id.rootWikiDetail), this);
        txtTitle = findViewById(R.id.txtWikiTitle);
        txtMeta = findViewById(R.id.txtWikiMeta);
        txtContent = findViewById(R.id.txtWikiContent);
        btnOpenSource = findViewById(R.id.btnOpenWikiSource);
        findViewById(R.id.btnBackWikiDetail).setOnClickListener(v -> finish());

        final long id = getIntent().getLongExtra(EXTRA_ARTICLE_ID, -1L);
        if (getIntent().getBooleanExtra(EXTRA_COMMUNITY, false)) {
            final ContributionRepository contrib = new ContributionRepository(this);
            AppExecutors.runResult(() -> contrib.communityWikiArticle(id), result -> {
                if (isFinishing() || isDestroyed()) return;
                if (!result.isOk()) {
                    Errors.show(this, result);
                    finish();
                    return;
                }
                showOrFinish(result.value);
            });
        } else {
            final CatalogDatabase db = CatalogDatabase.getInstance(this);
            AppExecutors.run(() -> db.getWikiArticle(id), article -> {
                if (isFinishing() || isDestroyed()) return;
                showOrFinish(article);
            });
        }
    }

    private void showOrFinish(WikiArticle article) {
        if (article == null) {
            Toast.makeText(this, "Không tìm thấy bài viết", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }
        show(article);
    }

    private void show(WikiArticle a) {
        txtTitle.setText(a.title);
        StringBuilder meta = new StringBuilder();
        if (a.categoryName != null) meta.append(a.categoryName);
        if (a.sourceName != null) meta.append(meta.length() > 0 ? " · " : "").append(a.sourceName);
        if (a.crawledAt != null && a.crawledAt.length() >= 10) {
            meta.append(a.community ? " · đăng " : " · cập nhật ").append(a.crawledAt, 0, 10);
        }
        txtMeta.setText(meta);
        txtContent.setText(a.content);
        if (a.sourceUrl != null && (a.sourceUrl.startsWith("https://") || a.sourceUrl.startsWith("http://"))) {
            btnOpenSource.setVisibility(View.VISIBLE);
            btnOpenSource.setOnClickListener(v -> {
                try {
                    startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(a.sourceUrl)));
                } catch (ActivityNotFoundException ex) {
                    Toast.makeText(this, "Không có ứng dụng để mở liên kết", Toast.LENGTH_SHORT).show();
                }
            });
        }
    }
}
