package com.example.apppet;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.provider.MediaStore;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.RelativeLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.RecyclerView;

import com.example.apppet.adapters.ForumAdapter;
import com.example.apppet.adapters.PetAdapter;
import com.example.apppet.data.DatabaseHelper;
import com.example.apppet.data.SessionManager;
import com.example.apppet.model.Community;
import com.example.apppet.model.ForumPost;
import com.example.apppet.model.Pet;
import com.example.apppet.model.User;
import com.example.apppet.ui.UiThemeManager;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.util.List;

public class ProfileActivity extends AppCompatActivity implements PetAdapter.OnPetClickListener {

    private RelativeLayout rootProfile;
    private ImageView btnBack, imgUserAvatar, btnChangeAvatar;
    private TextView txtDisplayName, txtUsernameEmail, txtCreatedAt, btnAddPet;
    private LinearLayout themeDefault, themeSlate, themePurple, themeForest, themeEspresso;
    private LinearLayout bottomNavProfile, cardProfileInfo;
    private RecyclerView rvProfilePets, rvProfileForums;
    private Button btnLogout;
    private ImageView navHome, navGallery, navBook, navChat, navProfile;

    private DatabaseHelper databaseHelper;
    private long userId;

    private final ActivityResultLauncher<Intent> avatarPickerLauncher = registerForActivityResult(
            new ActivityResultContracts.StartActivityForResult(),
            result -> {
                if (result.getResultCode() == RESULT_OK && result.getData() != null) {
                    Uri imageUri = result.getData().getData();
                    if (imageUri != null) {
                        String localAvatarUri = saveImageToInternalStorage(imageUri);
                        databaseHelper.updateUserAvatar(userId, localAvatarUri);
                        SessionManager sm = new SessionManager(this);
                        sm.updateAvatarUri(localAvatarUri);
                        try {
                            imgUserAvatar.setImageURI(Uri.parse(localAvatarUri));
                            imgUserAvatar.setPadding(0, 0, 0, 0);
                        } catch (Exception ignored) {}
                        Toast.makeText(this, "Đã cập nhật ảnh đại diện hồ sơ!", Toast.LENGTH_SHORT).show();
                    }
                }
            }
    );

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        if (!SessionManager.isLoggedIn(this)) {
            startActivity(new Intent(ProfileActivity.this, LoginActivity.class));
            finish();
            return;
        }

        userId = SessionManager.getUserId(this);
        databaseHelper = new DatabaseHelper(this);

        setContentView(R.layout.activity_profile);

        initViews();
        loadUserProfile();
        setupNavigation();
        setupThemeSelector();
    }

    @Override
    protected void onResume() {
        super.onResume();
        UiThemeManager.applyTheme(rootProfile, this);
        loadPetsAndForums();
    }

    private void initViews() {
        rootProfile = findViewById(R.id.rootProfile);
        btnBack = findViewById(R.id.btnBackProfile);
        imgUserAvatar = findViewById(R.id.imgUserAvatar);
        btnChangeAvatar = findViewById(R.id.btnChangeAvatar);
        txtDisplayName = findViewById(R.id.txtProfileDisplayName);
        txtUsernameEmail = findViewById(R.id.txtProfileUsernameEmail);
        txtCreatedAt = findViewById(R.id.txtProfileCreatedAt);
        btnAddPet = findViewById(R.id.btnAddPetProfile);
        cardProfileInfo = findViewById(R.id.cardProfileInfo);

        themeDefault = findViewById(R.id.themeDefault);
        themeSlate = findViewById(R.id.themeSlate);
        themePurple = findViewById(R.id.themePurple);
        themeForest = findViewById(R.id.themeForest);
        themeEspresso = findViewById(R.id.themeEspresso);

        rvProfilePets = findViewById(R.id.rvProfilePets);
        rvProfileForums = findViewById(R.id.rvProfileForums);
        btnLogout = findViewById(R.id.btnLogout);
        bottomNavProfile = findViewById(R.id.bottomNavProfile);

        navHome = findViewById(R.id.navHomeProfile);
        navGallery = findViewById(R.id.navGalleryProfile);
        navBook = findViewById(R.id.navBookProfile);
        navChat = findViewById(R.id.navChatProfile);
        navProfile = findViewById(R.id.navProfileActive);

        btnBack.setOnClickListener(v -> finish());
        btnChangeAvatar.setOnClickListener(v -> openGalleryForAvatar());
        btnAddPet.setOnClickListener(v -> startActivity(new Intent(ProfileActivity.this, AddPetActivity.class)));

        btnLogout.setOnClickListener(v -> {
            SessionManager.clear(this);
            Toast.makeText(this, "Đã đăng xuất tài khoản", Toast.LENGTH_SHORT).show();
            Intent intent = new Intent(ProfileActivity.this, LoginActivity.class);
            intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
            startActivity(intent);
            finish();
        });
    }

    private void setupThemeSelector() {
        themeDefault.setOnClickListener(v -> {
            SessionManager.setThemeColors(this, "#F4F7FC", "#FFFFFF");
            UiThemeManager.applyTheme(rootProfile, this);
            Toast.makeText(this, "Đã đổi sang chủ đề Trắng sáng", Toast.LENGTH_SHORT).show();
        });
        themeSlate.setOnClickListener(v -> {
            SessionManager.setThemeColors(this, "#E2E8F0", "#FFFFFF");
            UiThemeManager.applyTheme(rootProfile, this);
            Toast.makeText(this, "Đã đổi sang chủ đề Xanh Slate", Toast.LENGTH_SHORT).show();
        });
        themePurple.setOnClickListener(v -> {
            SessionManager.setThemeColors(this, "#F3E8FF", "#FFFFFF");
            UiThemeManager.applyTheme(rootProfile, this);
            Toast.makeText(this, "Đã đổi sang chủ đề Tím Đêm", Toast.LENGTH_SHORT).show();
        });
        themeForest.setOnClickListener(v -> {
            SessionManager.setThemeColors(this, "#DCFCE7", "#FFFFFF");
            UiThemeManager.applyTheme(rootProfile, this);
            Toast.makeText(this, "Đã đổi sang chủ đề Xanh Rêu", Toast.LENGTH_SHORT).show();
        });
        themeEspresso.setOnClickListener(v -> {
            SessionManager.setThemeColors(this, "#FEF3C7", "#FFFFFF");
            UiThemeManager.applyTheme(rootProfile, this);
            Toast.makeText(this, "Đã đổi sang chủ đề Cà Phê", Toast.LENGTH_SHORT).show();
        });
    }

    private void loadUserProfile() {
        User user = databaseHelper.getUser(userId);

        if (user != null) {
            txtDisplayName.setText(user.getShownName());
            txtUsernameEmail.setText("@" + user.username + " · " + (user.email != null ? user.email : "chưa có email"));
            txtCreatedAt.setText("Thành viên từ: 01/01/2025");

            String avatarUri = user.avatarUri;
            if (avatarUri == null || avatarUri.trim().isEmpty()) {
                SessionManager sm = new SessionManager(this);
                avatarUri = sm.getAvatarUri();
            }
            if (avatarUri != null && !avatarUri.trim().isEmpty()) {
                try {
                    imgUserAvatar.setImageURI(Uri.parse(avatarUri));
                    imgUserAvatar.setPadding(0, 0, 0, 0);
                } catch (Exception ignored) {}
            }
        }
    }

    private void loadPetsAndForums() {
        // Load Pets
        List<Pet> pets = databaseHelper.getPetsByUserId(userId);
        PetAdapter petAdapter = new PetAdapter(pets, this);
        rvProfilePets.setAdapter(petAdapter);

        // Load Joined Communities
        List<Community> communities = databaseHelper.getCommunitiesForUser(userId);
        ForumAdapter forumAdapter = new ForumAdapter(communities, community -> {
            Intent intent = new Intent(ProfileActivity.this, ChatActivity.class);
            intent.putExtra(ChatActivity.EXTRA_COMMUNITY_ID, community.id);
            startActivity(intent);
        });
        rvProfileForums.setAdapter(forumAdapter);
    }

    private void openGalleryForAvatar() {
        Intent intent = new Intent(Intent.ACTION_PICK, MediaStore.Images.Media.EXTERNAL_CONTENT_URI);
        avatarPickerLauncher.launch(intent);
    }

    private String saveImageToInternalStorage(Uri uri) {
        try {
            InputStream inputStream = getContentResolver().openInputStream(uri);
            if (inputStream == null) return uri.toString();

            File file = new File(getFilesDir(), "avatar_" + System.currentTimeMillis() + ".jpg");
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

    @Override
    public void onPetClick(Pet pet, int position) {
        Intent intent = new Intent(ProfileActivity.this, PetDetailActivity.class);
        intent.putExtra(PetDetailActivity.EXTRA_PET_ID, pet.id);
        startActivity(intent);
    }

    @Override
    public void onAddPetClick() {
        startActivity(new Intent(ProfileActivity.this, AddPetActivity.class));
    }

    private void setupNavigation() {
        navHome.setOnClickListener(v -> {
            startActivity(new Intent(ProfileActivity.this, MainActivity.class));
            finish();
        });
        navGallery.setOnClickListener(v ->
                Toast.makeText(this, "Bộ sưu tập", Toast.LENGTH_SHORT).show()
        );
        navBook.setOnClickListener(v ->
                Toast.makeText(this, "Cẩm nang chăm sóc", Toast.LENGTH_SHORT).show()
        );
        navChat.setOnClickListener(v ->
                startActivity(new Intent(ProfileActivity.this, CommunityListActivity.class))
        );
        navProfile.setOnClickListener(v ->
                Toast.makeText(this, "Bạn đang ở màn hình Hồ sơ cá nhân", Toast.LENGTH_SHORT).show()
        );
    }
}