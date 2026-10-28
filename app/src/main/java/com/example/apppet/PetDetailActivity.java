package com.example.apppet;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.os.Bundle;
import android.text.TextUtils;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import com.example.apppet.data.AppExecutors;
import com.example.apppet.model.Pet;
import com.example.apppet.net.PetRepository;
import com.example.apppet.ui.Errors;
import com.example.apppet.ui.ImageLoader;

import java.util.Locale;

public class PetDetailActivity extends Activity {
    public static final String EXTRA_PET_ID = "com.example.apppet.PET_ID";
    private static final int REQUEST_EDIT_PET = 2202;
    private long petId;
    private PetRepository pets;
    private int loadToken = 0;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_pet_detail);
        petId = getIntent().getLongExtra(EXTRA_PET_ID, -1L);
        pets = new PetRepository(this);

        findViewById(R.id.btnBackDetail).setOnClickListener(v -> finish());
        findViewById(R.id.btnEditPet).setOnClickListener(v -> {
            Intent intent = new Intent(PetDetailActivity.this, AddPetActivity.class);
            intent.putExtra(AddPetActivity.EXTRA_PET_ID, petId);
            startActivityForResult(intent, REQUEST_EDIT_PET);
        });
        findViewById(R.id.btnDeletePet).setOnClickListener(v -> {
            new AlertDialog.Builder(this)
                    .setTitle("Xóa thú cưng?")
                    .setMessage("Bạn có chắc chắn muốn xóa thú cưng này không? Hành động này không thể hoàn tác.")
                    .setPositiveButton("Xóa", (dialog, which) ->
                            AppExecutors.runResult(() -> pets.deletePet(petId), result -> {
                                if (isFinishing() || isDestroyed()) return;
                                if (result.isOk() && Boolean.TRUE.equals(result.value)) {
                                    Toast.makeText(this, "Đã xóa thú cưng thành công.", Toast.LENGTH_SHORT).show();
                                    setResult(RESULT_OK);
                                    finish();
                                } else if (!result.isOk()) {
                                    Errors.show(this, result);
                                } else {
                                    Toast.makeText(this, "Không thể xóa thú cưng lúc này.", Toast.LENGTH_SHORT).show();
                                }
                            }))
                    .setNegativeButton("Hủy", null)
                    .show();
        });
        // renderPet() được gọi trong onResume() ngay sau đó; không tải hai lần.
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (pets != null && petId > 0) renderPet();
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == REQUEST_EDIT_PET && resultCode == RESULT_OK) renderPet();
    }

    private void renderPet() {
        final int token = ++loadToken;
        AppExecutors.runResult(() -> pets.getPet(petId), result -> {
            if (isFinishing() || isDestroyed() || token != loadToken) return;
            if (!result.isOk()) {
                Errors.show(this, result);
                return;
            }
            if (result.value == null) {
                Toast.makeText(this, "Không tìm thấy thú cưng hoặc bạn không có quyền xem.", Toast.LENGTH_LONG).show();
                finish();
                return;
            }
            showPet(result.value);
        });
    }

    private void showPet(Pet pet) {
        setText(R.id.tvDetailPetName, pet.name);
        setText(R.id.tvDetailPetType, pet.petTypeName);
        setText(R.id.tvDetailPetBreed, valueOrFallback(pet.breedName, "Chưa rõ / Lai"));
        setText(R.id.tvDetailPetGender, valueOrFallback(pet.gender, "Chưa xác định"));
        setText(R.id.tvDetailPetBirthDate, valueOrFallback(pet.birthDate, "Chưa cập nhật"));
        setText(R.id.tvDetailPetColor, valueOrFallback(pet.color, "Chưa cập nhật"));
        setText(R.id.tvDetailPetWeight, pet.weight == null ? "Chưa cập nhật" : trimNumber(pet.weight) + " kg");
        setText(R.id.tvDetailPetDescription, valueOrFallback(pet.description, "Chưa có mô tả"));
        setText(R.id.tvDetailPetCreatedAt, formatTimestamp(pet.createdAt));

        ImageView avatar = findViewById(R.id.ivDetailPetAvatar);
        TextView placeholder = findViewById(R.id.tvDetailPetIcon);
        if (!TextUtils.isEmpty(pet.avatarUri)) {
            ImageLoader.load(avatar, pet.avatarUri, android.R.drawable.ic_menu_gallery);
            avatar.setVisibility(ImageView.VISIBLE);
            placeholder.setVisibility(TextView.GONE);
        } else {
            avatar.setVisibility(ImageView.GONE);
            placeholder.setVisibility(TextView.VISIBLE);
            placeholder.setText(iconForType(pet.petTypeName));
        }
    }

    private String iconForType(String type) {
        if ("Chó".equals(type)) return "🐶";
        if ("Mèo".equals(type)) return "🐱";
        if ("Chim".equals(type)) return "🐦";
        if ("Cá".equals(type)) return "🐟";
        if ("Thỏ".equals(type)) return "🐰";
        return "🐾";
    }

    private void setText(int viewId, String value) {
        TextView view = findViewById(viewId);
        view.setText(value == null ? "Chưa cập nhật" : value);
    }

    private String valueOrFallback(String value, String fallback) {
        return value == null || value.trim().isEmpty() ? fallback : value;
    }

    private String trimNumber(Double value) {
        if (value == null) return "";
        if (value == Math.rint(value)) return String.valueOf(value.longValue());
        return String.format(Locale.getDefault(), "%.2f", value).replaceAll("0+$", "").replaceAll("\\.$", "");
    }

    private String formatTimestamp(String value) {
        if (value == null || value.trim().isEmpty()) return "Chưa rõ";
        return value.replace('T', ' ');
    }
}
