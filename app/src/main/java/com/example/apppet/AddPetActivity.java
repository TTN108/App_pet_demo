package com.example.apppet;

import android.app.Activity;
import android.app.DatePickerDialog;
import android.content.Intent;
import android.graphics.Bitmap;
import android.net.Uri;
import android.os.Bundle;
import android.provider.MediaStore;
import android.text.TextUtils;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import com.example.apppet.data.DatabaseHelper;
import com.example.apppet.data.SessionManager;
import com.example.apppet.model.Breed;
import com.example.apppet.model.Pet;
import com.example.apppet.model.PetType;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;
import java.util.Locale;

public class AddPetActivity extends Activity {
    public static final String EXTRA_PET_ID = "com.example.apppet.EDIT_PET_ID";
    private static final int REQUEST_CAMERA = 2201;
    private static final int REQUEST_GALLERY = 2203;

    private DatabaseHelper databaseHelper;
    private EditText etName, etColor, etWeight, etDescription;
    private Spinner spPetType, spBreed, spGender;
    private TextView tvBirthDate, tvFormTitle, tvFormSubtitle;
    private ImageView ivAvatar;
    private String birthDate;
    private String avatarUri;
    private long editPetId = -1L;
    private long userId;
    private Pet existingPet;
    private long pendingBreedId = 0L;
    private final List<PetType> petTypes = new ArrayList<>();
    private final List<Breed> breeds = new ArrayList<>();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_add_pet);

        databaseHelper = new DatabaseHelper(this);
        userId = SessionManager.getUserId(this);
        editPetId = getIntent().getLongExtra(EXTRA_PET_ID, -1L);
        boolean editing = editPetId > 0;
        if (editing) {
            existingPet = databaseHelper.getPetForUser(editPetId, userId);
            if (existingPet == null) {
                Toast.makeText(this, "Không tìm thấy thú cưng hoặc bạn không có quyền sửa.", Toast.LENGTH_LONG).show();
                finish();
                return;
            }
            pendingBreedId = existingPet.breedId == null ? 0L : existingPet.breedId;
            birthDate = existingPet.birthDate;
            avatarUri = existingPet.avatarUri;
        }

        etName = findViewById(R.id.etPetName);
        etColor = findViewById(R.id.etPetColor);
        etWeight = findViewById(R.id.etPetWeight);
        etDescription = findViewById(R.id.etPetDescription);
        spPetType = findViewById(R.id.spPetType);
        spBreed = findViewById(R.id.spPetBreed);
        spGender = findViewById(R.id.spPetGender);
        tvBirthDate = findViewById(R.id.tvBirthDate);
        ivAvatar = findViewById(R.id.ivPetAvatarPreview);
        tvFormTitle = findViewById(R.id.tvAddPetTitle);
        tvFormSubtitle = findViewById(R.id.tvAddPetSubtitle);
        Button btnSave = findViewById(R.id.btnSavePet);
        Button btnCancel = findViewById(R.id.btnCancelAddPet);
        Button btnClearDate = findViewById(R.id.btnClearBirthDate);
        Button btnTakeAvatar = findViewById(R.id.btnTakePetAvatar);
        Button btnPickGallery = findViewById(R.id.btnPickGallery);
        TextView btnBack = findViewById(R.id.btnBackAddPet);

        tvFormTitle.setText(editing ? "Sửa thông tin thú cưng" : "Thêm thú cưng");
        tvFormSubtitle.setText(editing ? "Cập nhật hồ sơ và ảnh đại diện" : "Nhập thông tin cơ bản của thú cưng");
        btnSave.setText(editing ? "Lưu thay đổi" : "Lưu thú cưng");
        btnBack.setOnClickListener(v -> finish());
        btnCancel.setOnClickListener(v -> finish());
        btnSave.setOnClickListener(v -> savePet());
        tvBirthDate.setOnClickListener(v -> showDatePicker());
        btnTakeAvatar.setOnClickListener(v -> openCamera());
        btnPickGallery.setOnClickListener(v -> openGallery());
        btnClearDate.setOnClickListener(v -> {
            birthDate = null;
            tvBirthDate.setText("Chạm để chọn ngày sinh (không bắt buộc)");
        });

        ArrayAdapter<String> genderAdapter = new ArrayAdapter<>(this,
                android.R.layout.simple_spinner_item,
                new String[]{"Chưa xác định", "Đực", "Cái"});
        genderAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spGender.setAdapter(genderAdapter);

        if (editing) populateForm(existingPet);

        petTypes.addAll(databaseHelper.getPetTypes());
        ArrayAdapter<PetType> typeAdapter = new ArrayAdapter<>(this,
                android.R.layout.simple_spinner_item, petTypes);
        typeAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spPetType.setAdapter(typeAdapter);

        if (editing) {
            for (int i = 0; i < petTypes.size(); i++) {
                if (petTypes.get(i).id == existingPet.petTypeId) {
                    spPetType.setSelection(i);
                    break;
                }
            }
        }

        spPetType.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                if (position >= 0 && position < petTypes.size()) loadBreeds(petTypes.get(position));
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {
                breeds.clear();
                spBreed.setAdapter(new ArrayAdapter<>(AddPetActivity.this,
                        android.R.layout.simple_spinner_item, breeds));
            }
        });

        showCurrentAvatar();
        if (petTypes.isEmpty()) {
            Toast.makeText(this, "Chưa có loại thú cưng. Vui lòng kiểm tra cơ sở dữ liệu.", Toast.LENGTH_LONG).show();
            btnSave.setEnabled(false);
        }
    }

    private void populateForm(Pet pet) {
        etName.setText(pet.name);
        etColor.setText(pet.color == null ? "" : pet.color);
        etWeight.setText(pet.weight == null ? "" : String.valueOf(pet.weight));
        etDescription.setText(pet.description == null ? "" : pet.description);
        if (pet.gender != null) {
            if ("Đực".equals(pet.gender)) spGender.setSelection(1);
            else if ("Cái".equals(pet.gender)) spGender.setSelection(2);
            else spGender.setSelection(0);
        }
        tvBirthDate = findViewById(R.id.tvBirthDate);
        if (!TextUtils.isEmpty(birthDate)) tvBirthDate.setText(birthDate);
    }

    private void loadBreeds(PetType type) {
        breeds.clear();
        breeds.add(new Breed(0L, type.id, "Chưa rõ / Lai", null));
        breeds.addAll(databaseHelper.getBreedsByPetType(type.id));
        ArrayAdapter<Breed> breedAdapter = new ArrayAdapter<>(this,
                android.R.layout.simple_spinner_item, breeds);
        breedAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spBreed.setAdapter(breedAdapter);
        int wantedIndex = 0;
        for (int i = 0; i < breeds.size(); i++) {
            if (breeds.get(i).id == pendingBreedId) {
                wantedIndex = i;
                break;
            }
        }
        spBreed.setSelection(wantedIndex);
        pendingBreedId = 0L;
    }

    private void showDatePicker() {
        Calendar calendar = Calendar.getInstance();
        if (!TextUtils.isEmpty(birthDate)) {
            String[] parts = birthDate.split("-");
            if (parts.length == 3) {
                try {
                    calendar.set(Calendar.YEAR, Integer.parseInt(parts[0]));
                    calendar.set(Calendar.MONTH, Integer.parseInt(parts[1]) - 1);
                    calendar.set(Calendar.DAY_OF_MONTH, Integer.parseInt(parts[2]));
                } catch (NumberFormatException ignored) { }
            }
        }
        DatePickerDialog dialog = new DatePickerDialog(this, (picker, year, month, day) -> {
            birthDate = String.format(Locale.US, "%04d-%02d-%02d", year, month + 1, day);
            tvBirthDate.setText(birthDate);
        }, calendar.get(Calendar.YEAR), calendar.get(Calendar.MONTH), calendar.get(Calendar.DAY_OF_MONTH));
        dialog.getDatePicker().setMaxDate(System.currentTimeMillis());
        dialog.show();
    }

    private void openCamera() {
        Intent intent = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);
        if (intent.resolveActivity(getPackageManager()) == null) {
            Toast.makeText(this, "Thiết bị không tìm thấy ứng dụng chụp ảnh.", Toast.LENGTH_LONG).show();
            return;
        }
        startActivityForResult(intent, REQUEST_CAMERA);
    }

    private void openGallery() {
        Intent intent = new Intent(Intent.ACTION_PICK, MediaStore.Images.Media.EXTERNAL_CONTENT_URI);
        startActivityForResult(intent, REQUEST_GALLERY);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (resultCode != RESULT_OK) return;

        if (requestCode == REQUEST_GALLERY && data != null && data.getData() != null) {
            Uri imageUri = data.getData();
            avatarUri = saveImageToInternalStorage(imageUri);
            showCurrentAvatar();
            Toast.makeText(this, "Đã chọn ảnh từ thư viện. Nhấn lưu để cập nhật hồ sơ.", Toast.LENGTH_SHORT).show();
            return;
        }

        if (requestCode == REQUEST_CAMERA && data != null && data.getExtras() != null) {
            Object result = data.getExtras().get("data");
            if (!(result instanceof Bitmap)) {
                Toast.makeText(this, "Không nhận được ảnh từ camera. Vui lòng thử lại.", Toast.LENGTH_LONG).show();
                return;
            }
            Bitmap bitmap = (Bitmap) result;
            FileOutputStream output = null;
            try {
                File directory = new File(getFilesDir(), "pet_avatars");
                if (!directory.exists() && !directory.mkdirs()) throw new IOException("Không tạo được thư mục ảnh");
                File photoFile = new File(directory, "pet_avatar_" + System.currentTimeMillis() + ".jpg");
                output = new FileOutputStream(photoFile);
                if (!bitmap.compress(Bitmap.CompressFormat.JPEG, 90, output)) throw new IOException("Không ghi được ảnh");
                output.flush();
                avatarUri = Uri.fromFile(photoFile).toString();
                showCurrentAvatar();
                Toast.makeText(this, "Đã chụp ảnh avatar. Nhấn lưu để cập nhật hồ sơ.", Toast.LENGTH_SHORT).show();
            } catch (Exception ex) {
                Toast.makeText(this, "Không thể lưu ảnh: " + ex.getMessage(), Toast.LENGTH_LONG).show();
            } finally {
                if (output != null) {
                    try { output.close(); } catch (Exception ignored) { }
                }
            }
        }
    }

    private String saveImageToInternalStorage(Uri uri) {
        try {
            InputStream inputStream = getContentResolver().openInputStream(uri);
            if (inputStream == null) return uri.toString();

            File file = new File(getFilesDir(), "pet_avatar_" + System.currentTimeMillis() + ".jpg");
            FileOutputStream outputStream = new FileOutputStream(file);
            byte[] buffer = new byte[1024];
            int read;
            while ((read = inputStream.read(buffer)) != -1) {
                outputStream.write(buffer, 0, read);
            }
            outputStream.flush();
            outputStream.close();
            inputStream.close();
            return Uri.fromFile(file).toString();
        } catch (Exception e) {
            e.printStackTrace();
            return uri.toString();
        }
    }

    private void showCurrentAvatar() {
        if (!TextUtils.isEmpty(avatarUri)) {
            try {
                ivAvatar.setImageURI(Uri.parse(avatarUri));
            } catch (Exception ignored) { }
        } else {
            ivAvatar.setImageResource(android.R.drawable.ic_menu_camera);
        }
    }

    private void savePet() {
        String name = etName.getText().toString().trim();
        if (name.isEmpty()) {
            etName.setError("Vui lòng nhập tên thú cưng");
            etName.requestFocus();
            return;
        }
        int typePosition = spPetType.getSelectedItemPosition();
        if (typePosition < 0 || typePosition >= petTypes.size()) {
            Toast.makeText(this, "Vui lòng chọn loại thú cưng", Toast.LENGTH_SHORT).show();
            return;
        }
        PetType type = petTypes.get(typePosition);
        Long breedId = null;
        int breedPosition = spBreed.getSelectedItemPosition();
        if (breedPosition >= 0 && breedPosition < breeds.size()) {
            long selectedBreedId = breeds.get(breedPosition).id;
            if (selectedBreedId > 0) breedId = selectedBreedId;
        }
        String gender = null;
        if (spGender.getSelectedItemPosition() == 1) gender = "Đực";
        else if (spGender.getSelectedItemPosition() == 2) gender = "Cái";

        Double weight = null;
        String weightText = etWeight.getText().toString().trim().replace(',', '.');
        if (!weightText.isEmpty()) {
            try {
                weight = Double.parseDouble(weightText);
                if (weight <= 0) {
                    etWeight.setError("Cân nặng phải lớn hơn 0");
                    etWeight.requestFocus();
                    return;
                }
            } catch (NumberFormatException ex) {
                etWeight.setError("Nhập cân nặng hợp lệ, ví dụ 4.5");
                etWeight.requestFocus();
                return;
            }
        }

        String color = etColor.getText().toString().trim();
        String description = etDescription.getText().toString().trim();
        long savedPetId;
        boolean saved;
        if (editPetId > 0) {
            saved = databaseHelper.updatePetForUser(editPetId, userId, type.id, breedId, name, gender,
                    birthDate, color, weight, description);
            savedPetId = editPetId;
        } else {
            savedPetId = databaseHelper.insertPet(userId, type.id, breedId, name, gender,
                    birthDate, color, weight, description);
            saved = savedPetId > 0;
        }

        if (!saved) {
            Toast.makeText(this, editPetId > 0 ? "Không cập nhật được thông tin thú cưng." :
                    "Không lưu được thú cưng. Hãy kiểm tra lại dữ liệu.", Toast.LENGTH_LONG).show();
            return;
        }
        if (!TextUtils.isEmpty(avatarUri)) {
            boolean photoSaved = databaseHelper.savePetAvatarForUser(savedPetId, userId, avatarUri);
            if (!photoSaved) {
                Toast.makeText(this, "Đã lưu hồ sơ nhưng chưa lưu được ảnh avatar vào database.", Toast.LENGTH_LONG).show();
                setResult(RESULT_OK);
                finish();
                return;
            }
        }
        Toast.makeText(this, editPetId > 0 ? "Đã cập nhật thông tin thú cưng" : "Đã thêm thú cưng thành công", Toast.LENGTH_SHORT).show();
        setResult(RESULT_OK);
        finish();
    }
}
