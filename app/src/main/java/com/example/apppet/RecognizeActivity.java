package com.example.apppet;

import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.net.Uri;
import android.os.Bundle;
import android.view.View;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.PickVisualMediaRequest;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.FileProvider;

import com.example.apppet.data.AppExecutors;
import com.example.apppet.data.CatalogDatabase;
import com.example.apppet.data.ImageStorage;
import com.example.apppet.ml.BreedClassifier;
import com.example.apppet.model.BreedRef;
import com.example.apppet.net.ApiException;
import com.example.apppet.net.PetRepository;
import com.example.apppet.ui.ContributeDialog;
import com.example.apppet.ui.UiThemeManager;
import com.example.apppet.work.ContributionQueue;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Chụp (hoặc chọn) ảnh thú cưng, nhận diện loài/giống bằng model TFLite chạy trên máy,
 * lưu kết quả vào recognition_results và chuyển sang tạo thú cưng / xem wiki / xem ảnh mẫu.
 */
public class RecognizeActivity extends AppCompatActivity {
    private static final String STATE_CAPTURE_URI = "capture_uri";

    private static final class Candidate {
        final BreedClassifier.Prediction prediction;
        final BreedRef breed;          // null nếu nhãn chưa map được vào bảng breeds
        long recognitionId = -1L;

        Candidate(BreedClassifier.Prediction prediction, BreedRef breed) {
            this.prediction = prediction;
            this.breed = breed;
        }
    }

    private static final class Outcome {
        Bitmap bitmap;
        String localUri;
        final List<Candidate> candidates = new ArrayList<>();
        boolean modelMissing;
        String error;
    }

    private CatalogDatabase catalog;
    private PetRepository pets;
    private ImageView imgPhoto;
    private ProgressBar progress;
    private TextView txtStatus, btnTake, btnPick, btnCreatePet, btnOpenWiki, btnOpenGallery, btnContribute;
    private LinearLayout containerResults, rowRelated;

    private BreedClassifier classifier;
    private Uri captureUri;
    private String pendingLocalUri;
    private boolean handedOff = false;
    private Candidate selected;
    private final List<Candidate> shown = new ArrayList<>();
    private final List<TextView> rowViews = new ArrayList<>();

    private final ActivityResultLauncher<Uri> takePicture =
            registerForActivityResult(new ActivityResultContracts.TakePicture(), ok -> {
                if (Boolean.TRUE.equals(ok) && captureUri != null) analyze(captureUri);
            });
    private final ActivityResultLauncher<PickVisualMediaRequest> pickImage =
            registerForActivityResult(new ActivityResultContracts.PickVisualMedia(), uri -> {
                if (uri != null) analyze(uri);
            });

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_recognize);
        UiThemeManager.applyTheme(findViewById(R.id.rootRecognize), this);
        catalog = CatalogDatabase.getInstance(this);
        pets = new PetRepository(this);

        imgPhoto = findViewById(R.id.imgRecognizePhoto);
        progress = findViewById(R.id.progressRecognize);
        txtStatus = findViewById(R.id.txtRecognizeStatus);
        btnTake = findViewById(R.id.btnTakePhoto);
        btnPick = findViewById(R.id.btnPickPhoto);
        btnCreatePet = findViewById(R.id.btnCreatePet);
        btnOpenWiki = findViewById(R.id.btnOpenWiki);
        btnOpenGallery = findViewById(R.id.btnOpenGallery);
        btnContribute = findViewById(R.id.btnContribute);
        btnContribute.setOnClickListener(v -> startContribution());
        containerResults = findViewById(R.id.containerResults);
        rowRelated = findViewById(R.id.rowRelated);

        if (savedInstanceState != null) {
            String saved = savedInstanceState.getString(STATE_CAPTURE_URI);
            if (saved != null) captureUri = Uri.parse(saved);
        }

        findViewById(R.id.btnBackRecognize).setOnClickListener(v -> finish());
        btnTake.setOnClickListener(v -> takePhoto());
        btnPick.setOnClickListener(v -> pickImage.launch(new PickVisualMediaRequest.Builder()
                .setMediaType(ActivityResultContracts.PickVisualMedia.ImageOnly.INSTANCE).build()));
        btnCreatePet.setOnClickListener(v -> createPet());
        btnOpenWiki.setOnClickListener(v -> openRelated(WikiListActivity.class,
                WikiListActivity.EXTRA_PET_TYPE_ID, WikiListActivity.EXTRA_BREED_ID));
        btnOpenGallery.setOnClickListener(v -> openRelated(GalleryActivity.class,
                GalleryActivity.EXTRA_PET_TYPE_ID, GalleryActivity.EXTRA_BREED_ID));
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        if (captureUri != null) outState.putString(STATE_CAPTURE_URI, captureUri.toString());
    }

    private void takePhoto() {
        File dir = new File(getCacheDir(), "capture");
        if (!dir.exists() && !dir.mkdirs()) {
            Toast.makeText(this, "Không tạo được thư mục ảnh tạm", Toast.LENGTH_SHORT).show();
            return;
        }
        captureUri = FileProvider.getUriForFile(this, getPackageName() + ".fileprovider", new File(dir, "capture.jpg"));
        try {
            takePicture.launch(captureUri);
        } catch (ActivityNotFoundException ex) {
            Toast.makeText(this, "Không tìm thấy ứng dụng camera. Hãy chọn ảnh từ thư viện.", Toast.LENGTH_LONG).show();
        }
    }

    private void analyze(Uri uri) {
        discardPendingPhoto();
        setBusy(true);
        selected = null;
        shown.clear();
        rowViews.clear();
        containerResults.removeAllViews();
        btnCreatePet.setVisibility(View.GONE);
        btnContribute.setVisibility(View.GONE);
        btnContribute.setEnabled(true);
        btnContribute.setText("Đóng góp ảnh này cho bộ sưu tập");
        rowRelated.setVisibility(View.GONE);
        txtStatus.setText("Đang nhận diện…");
        final Context app = getApplicationContext();
        AppExecutors.run(() -> runAnalysis(app, uri), this::onAnalyzed);
    }

    /** Chạy ở luồng nền. */
    private Outcome runAnalysis(Context app, Uri uri) {
        Outcome out = new Outcome();
        try {
            out.bitmap = ImageStorage.decode(app, uri, 1024);
            out.localUri = ImageStorage.saveJpeg(app, out.bitmap, "pet_photos");
            if (!BreedClassifier.isModelPresent(app)) {
                out.modelMissing = true;
                return out;
            }
            if (classifier == null) classifier = new BreedClassifier(app);
            JSONArray json = new JSONArray();
            for (BreedClassifier.Prediction p : classifier.classify(out.bitmap, 3)) {
                out.candidates.add(new Candidate(p, catalog.findBreedByLabel(p.label)));
                json.put(new JSONObject().put("label", p.label).put("p", Math.round(p.probability * 1000) / 1000.0));
            }
            String resultJson = json.toString();
            for (Candidate c : out.candidates) {
                try {                  // ghi lên server là việc phụ: lỗi mạng không làm hỏng kết quả nhận diện
                    c.recognitionId = pets.insertRecognition(
                            c.breed != null ? c.breed.petTypeId : null, c.breed != null ? c.breed.breedId : null,
                            c.prediction.probability, BreedClassifier.MODEL_NAME, c.prediction.label, resultJson);
                } catch (RuntimeException ex) {
                    c.recognitionId = -1L;
                }
            }
        } catch (java.io.IOException | JSONException | RuntimeException ex) {
            out.error = "Không xử lý được ảnh: " + ex.getMessage();
        }
        return out;
    }

    private void onAnalyzed(Outcome out) {
        if (isFinishing() || isDestroyed()) {
            if (out != null && out.localUri != null) ImageStorage.deleteIfOwned(getApplicationContext(), out.localUri);
            return;
        }
        setBusy(false);
        if (out == null || out.error != null) {
            txtStatus.setText(out != null ? out.error : "Có lỗi khi nhận diện, hãy thử lại.");
            return;
        }
        imgPhoto.setImageBitmap(out.bitmap);
        pendingLocalUri = out.localUri;
        btnCreatePet.setVisibility(View.VISIBLE);

        if (out.modelMissing) {
            txtStatus.setText("Chưa có model nhận diện (assets/ml/breed_classifier.tflite). Chạy tools/train_breed_classifier.py "
                    + "rồi build lại app. Bạn vẫn có thể tạo thú cưng thủ công với ảnh này.");
            return;
        }
        shown.addAll(out.candidates);
        for (int i = 0; i < shown.size(); i++) addResultRow(i, shown.get(i));
        for (Candidate c : shown) {
            if (c.breed != null) {
                select(c);
                break;
            }
        }
        Candidate top = shown.isEmpty() ? null : shown.get(0);
        if (top != null && top.breed != null && top.prediction.probability >= BreedClassifier.CONFIDENCE_THRESHOLD) {
            txtStatus.setText(String.format(Locale.US, "Có vẻ đây là %s (%d%%). Chạm vào kết quả khác nếu chưa đúng.",
                    top.breed.displayName(), Math.round(top.prediction.probability * 100)));
        } else {
            txtStatus.setText("Chưa chắc chắn. Ảnh có thể không phải loài/giống có trong dữ liệu. "
                    + "Hãy chọn kết quả đúng bên dưới hoặc tạo thú cưng thủ công.");
        }
    }

    private void addResultRow(int index, Candidate c) {
        TextView row = new TextView(this);
        int pad = (int) (14 * getResources().getDisplayMetrics().density);
        row.setPadding(pad, pad, pad, pad);
        row.setTextSize(14);
        row.setTextColor(getColor(R.color.text_primary));
        row.setBackgroundResource(R.drawable.bg_card);
        int percent = Math.round(c.prediction.probability * 100);
        String name = c.breed != null
                ? c.breed.displayName() + " · " + c.breed.petTypeName
                : "Nhãn \"" + c.prediction.label + "\" (chưa có trong danh mục giống)";
        row.setText((index + 1) + ". " + name + " — " + percent + "%");
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        lp.bottomMargin = (int) (8 * getResources().getDisplayMetrics().density);
        if (c.breed != null) row.setOnClickListener(v -> select(c));
        containerResults.addView(row, lp);
        rowViews.add(row);
    }

    private void select(Candidate c) {
        selected = c;
        for (int i = 0; i < shown.size(); i++) {
            rowViews.get(i).setBackgroundResource(shown.get(i) == c ? R.drawable.bg_result_selected : R.drawable.bg_card);
        }
        rowRelated.setVisibility(View.VISIBLE);
        if (pendingLocalUri != null) btnContribute.setVisibility(View.VISIBLE);
    }

    /** Đóng góp ảnh vừa chụp cho đúng giống người dùng đang chọn (đã xác nhận hoặc đã sửa so với dự đoán của model). */
    private void startContribution() {
        final Candidate c = selected;
        if (c == null || c.breed == null || pendingLocalUri == null) return;
        ContributeDialog.show(this, c.breed.displayName(), true, () -> queueContribution(c));
    }

    private void queueContribution(final Candidate c) {
        final Context app = getApplicationContext();
        final String source = pendingLocalUri;
        if (source == null) return;
        btnContribute.setEnabled(false);
        AppExecutors.runResult(() -> {
            try {
                return ImageStorage.copyOwned(app, source, "contrib_queue");    // bản riêng cho hàng đợi gửi
            } catch (IOException ex) {
                throw new ApiException(0, "io", "Không sao chép được ảnh");
            }
        }, result -> {
            if (isFinishing() || isDestroyed()) return;
            if (!result.isOk()) {
                btnContribute.setEnabled(true);
                Toast.makeText(this, "Không chuẩn bị được ảnh để gửi.", Toast.LENGTH_LONG).show();
                return;
            }
            ContributionQueue.enqueue(app, result.value, c.breed.petTypeId, c.breed.breedId, "CAMERA",
                    c.recognitionId, c.breed.breedName);
            btnContribute.setText("Đã xếp hàng gửi, chờ duyệt");
            Toast.makeText(this, "Cảm ơn bạn! Ảnh sẽ hiện sau khi quản trị viên duyệt.", Toast.LENGTH_LONG).show();
        });
    }

    private void createPet() {
        Intent intent = new Intent(this, AddPetActivity.class);
        if (pendingLocalUri != null) intent.putExtra(AddPetActivity.EXTRA_PREFILL_IMAGE_URI, pendingLocalUri);
        if (selected != null && selected.breed != null) {
            intent.putExtra(AddPetActivity.EXTRA_PREFILL_TYPE_ID, selected.breed.petTypeId);
            intent.putExtra(AddPetActivity.EXTRA_PREFILL_BREED_ID, selected.breed.breedId);
            intent.putExtra(AddPetActivity.EXTRA_RECOGNITION_ID, selected.recognitionId);
        }
        handedOff = true;       // AddPetActivity chịu trách nhiệm file ảnh từ đây
        startActivity(intent);
        finish();
    }

    private void openRelated(Class<?> target, String typeExtra, String breedExtra) {
        if (selected == null || selected.breed == null) return;
        Intent intent = new Intent(this, target);
        intent.putExtra(typeExtra, selected.breed.petTypeId);
        intent.putExtra(breedExtra, selected.breed.breedId);
        startActivity(intent);
    }

    private void setBusy(boolean busy) {
        progress.setVisibility(busy ? View.VISIBLE : View.GONE);
        btnTake.setEnabled(!busy);
        btnPick.setEnabled(!busy);
    }

    private void discardPendingPhoto() {
        if (pendingLocalUri != null && !handedOff) ImageStorage.deleteIfOwned(getApplicationContext(), pendingLocalUri);
        pendingLocalUri = null;
    }

    @Override
    protected void onDestroy() {
        discardPendingPhoto();
        if (classifier != null) {
            try {
                classifier.close();
            } catch (RuntimeException ignored) {
                // đang chạy dở trên luồng nền: bỏ qua
            }
        }
        super.onDestroy();
    }
}
