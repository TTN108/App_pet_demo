package com.example.apppet;

import android.app.DatePickerDialog;
import android.content.ActivityNotFoundException;
import android.content.Context;
import android.net.Uri;
import android.os.Bundle;
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

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.PickVisualMediaRequest;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.FileProvider;

import com.example.apppet.data.AppExecutors;
import com.example.apppet.data.CatalogDatabase;
import com.example.apppet.data.ImageStorage;
import com.example.apppet.data.SessionManager;
import com.example.apppet.model.Breed;
import com.example.apppet.model.Pet;
import com.example.apppet.model.PetType;
import com.example.apppet.net.PetRepository;
import com.example.apppet.ui.Errors;
import com.example.apppet.ui.ImageLoader;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.Locale;

public class AddPetActivity extends AppCompatActivity {
    public static final String EXTRA_PET_ID = "com.example.apppet.EDIT_PET_ID";
    /** Điền sẵn từ màn Nhận diện (RecognizeActivity). */
    public static final String EXTRA_PREFILL_TYPE_ID = "com.example.apppet.PREFILL_TYPE_ID";
    public static final String EXTRA_PREFILL_BREED_ID = "com.example.apppet.PREFILL_BREED_ID";
    public static final String EXTRA_PREFILL_IMAGE_URI = "com.example.apppet.PREFILL_IMAGE_URI";
    public static final String EXTRA_RECOGNITION_ID = "com.example.apppet.RECOGNITION_ID";

    private static final String STATE_AVATAR = "state_avatar";
    private static final String STATE_BIRTH = "state_birth";
    private static final String STATE_BREED = "state_breed";
    private static final String STATE_CAPTURE = "state_capture";
    private static final String STATE_SESSION_FILES = "state_session_files";

    private CatalogDatabase catalog;
    private PetRepository pets;
    private boolean saving = false;
    private Button btnSaveRef;
    private EditText etName, etColor, etWeight, etDescription;
    private Spinner spPetType, spBreed, spGender;
    private TextView tvBirthDate, tvFormTitle, tvFormSubtitle;
    private ImageView ivAvatar;
    private String birthDate;
    private String avatarUri;
    private long editPetId = -1L;
    private String userId;
    private Pet existingPet;
    private long pendingBreedId = 0L;
    private long recognitionId = -1L;
    private Uri captureUri;
    private boolean savedOk = false;
    /** File ảnh do màn này tạo ra, để dọn khi người dùng hủy hoặc đổi ảnh. */
    private final Set<String> sessionFiles = new HashSet<>();
    private final List<PetType> petTypes = new ArrayList<>();
    private final List<Breed> breeds = new ArrayList<>();

    private final ActivityResultLauncher<Uri> takePicture =
            registerForActivityResult(new ActivityResultContracts.TakePicture(), ok -> {
                if (Boolean.TRUE.equals(ok) && captureUri != null) importImage(captureUri);
            });
    private final ActivityResultLauncher<PickVisualMediaRequest> pickImage =
            registerForActivityResult(new ActivityResultContracts.PickVisualMedia(), uri -> {
                if (uri != null) importImage(uri);
            });

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_add_pet);

        catalog = CatalogDatabase.getInstance(this);
        pets = new PetRepository(this);
        userId = SessionManager.getUserId(this);
        editPetId = getIntent().getLongExtra(EXTRA_PET_ID, -1L);
        final boolean editing = editPetId > 0;
        if (editing) {
            // Hồ sơ nằm trên server: tải ở luồng nền rồi mới dựng form.
            AppExecutors.runResult(() -> pets.getPet(editPetId), result -> {
                if (isFinishing() || isDestroyed()) return;
                if (!result.isOk()) {
                    Errors.show(this, result);
                    if (!isFinishing()) finish();
                    return;
                }
                if (result.value == null) {
                    Toast.makeText(this, "Không tìm thấy thú cưng hoặc bạn không có quyền sửa.", Toast.LENGTH_LONG).show();
                    finish();
                    return;
                }
                existingPet = result.value;
                pendingBreedId = existingPet.breedId == null ? 0L : existingPet.breedId;
                birthDate = existingPet.birthDate;
                avatarUri = existingPet.avatarUri;
                restoreState(savedInstanceState);
                buildForm(savedInstanceState, true);
            });
            return;
        }
        long prefillBreed = getIntent().getLongExtra(EXTRA_PREFILL_BREED_ID, 0L);
        if (prefillBreed > 0) pendingBreedId = prefillBreed;
        recognitionId = getIntent().getLongExtra(EXTRA_RECOGNITION_ID, -1L);
        String prefillImage = getIntent().getStringExtra(EXTRA_PREFILL_IMAGE_URI);
        if (!TextUtils.isEmpty(prefillImage)) {
            avatarUri = prefillImage;
            sessionFiles.add(prefillImage);
        }
        restoreState(savedInstanceState);
        buildForm(savedInstanceState, false);
    }

    /** Khôi phục trạng thái khi xoay màn hình / bị hệ thống kill lúc đang ở app camera. */
    private void restoreState(Bundle savedInstanceState) {
        if (savedInstanceState == null) return;
        avatarUri = savedInstanceState.getString(STATE_AVATAR, avatarUri);
        birthDate = savedInstanceState.getString(STATE_BIRTH, birthDate);
        pendingBreedId = savedInstanceState.getLong(STATE_BREED, pendingBreedId);
        String cap = savedInstanceState.getString(STATE_CAPTURE);
        if (cap != null) captureUri = Uri.parse(cap);
        List<String> files = savedInstanceState.getStringArrayList(STATE_SESSION_FILES);
        if (files != null) sessionFiles.addAll(files);
    }

    private void buildForm(Bundle savedInstanceState, boolean editing) {
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
        btnSaveRef = btnSave;
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
        else if (!TextUtils.isEmpty(birthDate)) tvBirthDate.setText(birthDate);

        petTypes.addAll(catalog.getPetTypes());
        ArrayAdapter<PetType> typeAdapter = new ArrayAdapter<>(this,
                android.R.layout.simple_spinner_item, petTypes);
        typeAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spPetType.setAdapter(typeAdapter);

        long wantedTypeId = editing ? existingPet.petTypeId : getIntent().getLongExtra(EXTRA_PREFILL_TYPE_ID, 0L);
        if (wantedTypeId > 0 && savedInstanceState == null) {
            for (int i = 0; i < petTypes.size(); i++) {
                if (petTypes.get(i).id == wantedTypeId) {
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
        breeds.addAll(catalog.getBreedsByPetType(type.id));
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
        File dir = new File(getCacheDir(), "capture");
        if (!dir.exists() && !dir.mkdirs()) {
            Toast.makeText(this, "Không tạo được thư mục ảnh tạm.", Toast.LENGTH_LONG).show();
            return;
        }
        captureUri = FileProvider.getUriForFile(this, getPackageName() + ".fileprovider", new File(dir, "avatar.jpg"));
        try {
            takePicture.launch(captureUri);
        } catch (ActivityNotFoundException ex) {
            Toast.makeText(this, "Thiết bị không tìm thấy ứng dụng chụp ảnh.", Toast.LENGTH_LONG).show();
        }
    }

    private void openGallery() {
        pickImage.launch(new PickVisualMediaRequest.Builder()
                .setMediaType(ActivityResultContracts.PickVisualMedia.ImageOnly.INSTANCE).build());
    }

    /** Thu nhỏ, xoay đúng chiều và lưu ảnh vào bộ nhớ trong ở luồng nền. */
    private void importImage(Uri uri) {
        final Context app = getApplicationContext();
        AppExecutors.run(() -> {
            try {
                return ImageStorage.copyToAppStorage(app, uri, "pet_avatars");
            } catch (IOException ex) {
                return null;
            }
        }, result -> {
            if (isFinishing() || isDestroyed()) {
                if (result != null) ImageStorage.deleteIfOwned(app, result);
                return;
            }
            if (result == null) {
                Toast.makeText(this, "Không đọc được ảnh. Vui lòng thử ảnh khác.", Toast.LENGTH_LONG).show();
                return;
            }
            setAvatar(result);
            Toast.makeText(this, "Đã chọn ảnh. Nhấn lưu để cập nhật hồ sơ.", Toast.LENGTH_SHORT).show();
        });
    }

    private void setAvatar(String newUri) {
        if (avatarUri != null && sessionFiles.remove(avatarUri)) {
            ImageStorage.deleteIfOwned(this, avatarUri);        // bỏ ảnh tạm mà người dùng vừa thay
        }
        avatarUri = newUri;
        sessionFiles.add(newUri);
        showCurrentAvatar();
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putString(STATE_AVATAR, avatarUri);
        outState.putString(STATE_BIRTH, birthDate);
        int bp = spBreed != null ? spBreed.getSelectedItemPosition() : -1;
        outState.putLong(STATE_BREED, bp >= 0 && bp < breeds.size() ? breeds.get(bp).id : pendingBreedId);
        if (captureUri != null) outState.putString(STATE_CAPTURE, captureUri.toString());
        outState.putStringArrayList(STATE_SESSION_FILES, new ArrayList<>(sessionFiles));
    }

    @Override
    protected void onDestroy() {
        if (isFinishing()) {
            for (String f : sessionFiles) ImageStorage.deleteIfOwned(this, f);   // hủy form -> dọn ảnh tạm
        }
        super.onDestroy();
    }

    private void showCurrentAvatar() {
        ImageLoader.load(ivAvatar, avatarUri, android.R.drawable.ic_menu_camera);
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

        final String color = etColor.getText().toString().trim();
        final String description = etDescription.getText().toString().trim();
        final boolean editingNow = editPetId > 0;
        final Long finalBreedId = breedId;
        final String finalGender = gender;
        final Double finalWeight = weight;
        final String finalName = name;
        final long finalTypeId = type.id;
        final String finalBirth = birthDate;
        // Chỉ ảnh mới chọn (file nội bộ) mới cần tải lên; ảnh cũ đã là URL trên server.
        final String localAvatar = !TextUtils.isEmpty(avatarUri) && avatarUri.startsWith("file:") ? avatarUri : null;
        if (saving) return;
        saving = true;
        btnSaveRef.setEnabled(false);

        // Kết quả: 0 = xong, 1 = lưu hồ sơ được nhưng ảnh lỗi, 2 = không cập nhật được (không còn quyền/không tồn tại)
        AppExecutors.runResult(() -> {
            long petId;
            if (editingNow) {
                if (!pets.updatePet(editPetId, finalTypeId, finalBreedId, finalName, finalGender, finalBirth,
                        color, finalWeight, description)) {
                    return 2;
                }
                petId = editPetId;
            } else {
                petId = pets.insertPet(finalTypeId, finalBreedId, finalName, finalGender, finalBirth,
                        color, finalWeight, description);
            }
            if (localAvatar != null) {
                try {
                    Object[] uploaded = pets.setMainImage(userId, petId, new File(Uri.parse(localAvatar).getPath()));
                    try {
                        pets.linkRecognition(recognitionId, (Long) uploaded[0]);
                    } catch (RuntimeException ignored) {
                        // gắn kết quả nhận diện chỉ là thông tin phụ, không làm hỏng việc lưu thú cưng
                    }
                } catch (RuntimeException ex) {
                    return 1;
                }
            }
            return 0;
        }, result -> {
            saving = false;
            btnSaveRef.setEnabled(true);
            if (isFinishing() || isDestroyed()) return;
            if (!result.isOk()) {
                Errors.show(this, result);
                return;
            }
            int code = result.value;
            if (code == 2) {
                Toast.makeText(this, "Không cập nhật được thông tin thú cưng.", Toast.LENGTH_LONG).show();
                return;
            }
            savedOk = true;
            if (code == 1) {
                Toast.makeText(this, "Đã lưu hồ sơ nhưng chưa tải được ảnh. Bạn có thể thêm ảnh lại trong phần sửa.", Toast.LENGTH_LONG).show();
            } else {
                Toast.makeText(this, editingNow ? "Đã cập nhật thông tin thú cưng" : "Đã thêm thú cưng thành công",
                        Toast.LENGTH_SHORT).show();
            }
            setResult(RESULT_OK);
            finish();
        });
    }
}
