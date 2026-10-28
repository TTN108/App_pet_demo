package com.example.apppet;

import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.PickVisualMediaRequest;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;
import com.example.apppet.adapters.GalleryAdapter;
import com.example.apppet.data.AppExecutors;
import com.example.apppet.data.CatalogDatabase;
import com.example.apppet.data.ImageDownloader;
import com.example.apppet.data.ImageStorage;
import com.example.apppet.model.GalleryImage;
import com.example.apppet.net.ApiException;
import com.example.apppet.net.GalleryRepository;
import com.example.apppet.ui.ContributeDialog;
import com.example.apppet.ui.Errors;
import com.example.apppet.ui.TypeBreedFilter;
import com.example.apppet.ui.UiThemeManager;
import com.example.apppet.work.ContributionQueue;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * Bộ sưu tập ảnh theo loài/giống. Thông tin ảnh lấy từ Supabase theo từng trang (cuộn gần cuối thì tải tiếp),
 * ảnh hiển thị là bản thu nhỏ do ImageKit tạo theo yêu cầu; có xem lớn và tải ảnh gốc về máy.
 */
public class GalleryActivity extends AppCompatActivity implements GalleryAdapter.Listener {
    public static final String EXTRA_PET_TYPE_ID = "pet_type_id";
    public static final String EXTRA_BREED_ID = "breed_id";
    private static final int PAGE_SIZE = 40;

    private GalleryRepository repo;
    private CatalogDatabase catalog;
    private long contribType;
    private long contribBreed;
    private final ActivityResultLauncher<PickVisualMediaRequest> contributePicker =
            registerForActivityResult(new ActivityResultContracts.PickVisualMedia(), uri -> {
                if (uri != null) queueContribution(uri);
            });
    private TypeBreedFilter filter;
    private GridLayoutManager layoutManager;
    private GalleryAdapter adapter;
    private TextView txtEmpty, txtCount;
    private final List<GalleryImage> items = new ArrayList<>();
    private long lastId = 0L;
    private boolean loading = false;
    private boolean endReached = false;
    private int generation = 0;        // tăng mỗi khi đổi bộ lọc để bỏ kết quả của lượt tải cũ

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_gallery);
        UiThemeManager.applyTheme(findViewById(R.id.rootGallery), this);
        repo = new GalleryRepository(this);
        catalog = CatalogDatabase.getInstance(this);

        RecyclerView rvGrid = findViewById(R.id.rvGalleryGrid);
        txtEmpty = findViewById(R.id.txtGalleryEmpty);
        txtCount = findViewById(R.id.txtGalleryCount);
        layoutManager = new GridLayoutManager(this, 2);
        rvGrid.setLayoutManager(layoutManager);
        adapter = new GalleryAdapter(items, true, this);
        rvGrid.setAdapter(adapter);
        rvGrid.addOnScrollListener(new RecyclerView.OnScrollListener() {
            @Override
            public void onScrolled(RecyclerView recyclerView, int dx, int dy) {
                if (dy > 0 && layoutManager.findLastVisibleItemPosition() >= items.size() - 6) loadMore();
            }
        });
        findViewById(R.id.btnBackGallery).setOnClickListener(v -> finish());

        long typeId = getIntent().getLongExtra(EXTRA_PET_TYPE_ID, 0L);
        long breedId = getIntent().getLongExtra(EXTRA_BREED_ID, 0L);
        findViewById(R.id.btnContribute).setOnClickListener(v -> startContribution());
        filter = new TypeBreedFilter(this, catalog,
                (Spinner) findViewById(R.id.spnGalleryType), (Spinner) findViewById(R.id.spnGalleryBreed),
                typeId > 0 ? typeId : null, breedId > 0 ? breedId : null, this::resetAndLoad);
    }

    /** Đóng góp ảnh cho đúng loài/giống đang chọn ở bộ lọc: xin đồng ý -> chọn ảnh -> xếp hàng gửi ở nền. */
    private void startContribution() {
        final Long typeId = filter.selectedTypeId();
        final Long breedId = filter.selectedBreedId();
        if (typeId == null || breedId == null) {
            Toast.makeText(this, "Hãy chọn loài và giống ở bộ lọc phía trên trước khi đóng góp ảnh.", Toast.LENGTH_LONG).show();
            return;
        }
        ContributeDialog.show(this, catalog.breedDisplayName(breedId), false, () -> {
            contribType = typeId;
            contribBreed = breedId;
            contributePicker.launch(new PickVisualMediaRequest.Builder()
                    .setMediaType(ActivityResultContracts.PickVisualMedia.ImageOnly.INSTANCE).build());
        });
    }

    private void queueContribution(Uri uri) {
        final Context app = getApplicationContext();
        final long typeId = contribType;
        final long breedId = contribBreed;
        final String title = catalog.breedDisplayName(breedId);
        AppExecutors.runResult(() -> {
            try {
                return ImageStorage.copyToAppStorage(app, uri, "contrib_queue");     // thu nhỏ, xoay đúng chiều, xóa EXIF/vị trí
            } catch (IOException ex) {
                throw new ApiException(0, "io", "Không đọc được ảnh");
            }
        }, result -> {
            if (isFinishing() || isDestroyed()) return;
            if (!result.isOk()) {
                Toast.makeText(this, "Không đọc được ảnh. Hãy thử ảnh khác.", Toast.LENGTH_LONG).show();
                return;
            }
            ContributionQueue.enqueue(app, result.value, typeId, breedId, "UPLOAD", -1L, title);
            Toast.makeText(this, "Đã xếp hàng gửi ảnh. Ảnh sẽ hiện sau khi quản trị viên duyệt.", Toast.LENGTH_LONG).show();
        });
    }

    private void resetAndLoad() {
        generation++;
        items.clear();
        adapter.notifyDataSetChanged();
        lastId = 0L;
        endReached = false;
        loading = false;
        txtEmpty.setVisibility(View.GONE);
        txtCount.setText("Đang tải…");
        loadMore();
    }

    private void loadMore() {
        if (loading || endReached) return;
        loading = true;
        final int gen = generation;
        final Long typeId = filter.selectedTypeId();
        final Long breedId = filter.selectedBreedId();
        final long after = lastId;
        AppExecutors.runResult(() -> repo.page(typeId, breedId, after, PAGE_SIZE), result -> {
            if (isFinishing() || isDestroyed() || gen != generation) return;
            loading = false;
            if (!result.isOk()) {
                txtCount.setText("Không tải được ảnh");
                Errors.show(this, result);
                return;
            }
            List<GalleryImage> page = result.value;
            int start = items.size();
            items.addAll(page);
            adapter.notifyItemRangeInserted(start, page.size());
            if (!page.isEmpty()) lastId = page.get(page.size() - 1).id;
            if (page.size() < PAGE_SIZE) endReached = true;
            txtEmpty.setVisibility(items.isEmpty() ? View.VISIBLE : View.GONE);
            txtCount.setText(items.size() + (endReached ? " ảnh" : "+ ảnh"));
        });
    }

    @Override
    public void onImageClick(GalleryImage image) {
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        int pad = dp(16);
        box.setPadding(pad, pad, pad, 0);
        ImageView iv = new ImageView(this);
        iv.setScaleType(ImageView.ScaleType.FIT_CENTER);
        iv.setContentDescription(image.title != null ? image.title : "Ảnh thú cưng");
        box.addView(iv, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(300)));
        Glide.with(this).load(image.viewUrl()).into(iv);
        TextView credit = new TextView(this);
        credit.setTextSize(12);
        credit.setPadding(0, dp(8), 0, 0);
        credit.setText(image.description != null && !image.description.isEmpty()
                ? "Nguồn ảnh: " + image.description : "Nguồn ảnh: " + image.sourceType);
        box.addView(credit);

        AlertDialog.Builder builder = new AlertDialog.Builder(this)
                .setTitle(image.title != null ? image.title : "Ảnh")
                .setView(box)
                .setPositiveButton("Tải về", (d, w) -> onDownloadClick(image))
                .setNegativeButton("Đóng", null);
        if (image.sourceUrl != null && image.sourceUrl.startsWith("http")) {
            builder.setNeutralButton("Trang nguồn", (d, w) -> openUrl(image.sourceUrl));
        }
        builder.show();
    }

    @Override
    public void onDownloadClick(GalleryImage image) {
        Toast.makeText(this, "Đang tải ảnh…", Toast.LENGTH_SHORT).show();
        final Context app = getApplicationContext();
        final String url = image.downloadUrl();
        final String name = image.breedName != null ? image.breedName : "pet";
        AppExecutors.run(() -> {
            try {
                return "Đã lưu ảnh: " + ImageDownloader.download(app, url, name);
            } catch (IOException ex) {
                return "Không tải được ảnh: " + ex.getMessage();
            }
        }, message -> Toast.makeText(app, message != null ? message : "Lỗi khi tải ảnh", Toast.LENGTH_LONG).show());
    }

    private void openUrl(String url) {
        try {
            startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(url)));
        } catch (ActivityNotFoundException ex) {
            Toast.makeText(this, "Không có ứng dụng để mở liên kết", Toast.LENGTH_SHORT).show();
        }
    }

    private int dp(int value) {
        return (int) (value * getResources().getDisplayMetrics().density);
    }
}
