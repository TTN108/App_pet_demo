package com.example.apppet;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.RelativeLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.PickVisualMediaRequest;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.RecyclerView;

import com.example.apppet.adapters.ForumAdapter;
import com.example.apppet.adapters.PetAdapter;
import com.example.apppet.data.AppExecutors;
import com.example.apppet.data.ImageStorage;
import com.example.apppet.data.SessionManager;
import com.example.apppet.model.Community;
import com.example.apppet.model.Pet;
import com.example.apppet.model.User;
import com.example.apppet.net.ChatRepository;
import com.example.apppet.net.PetRepository;
import com.example.apppet.net.UserRepository;
import com.example.apppet.ui.Errors;
import com.example.apppet.ui.ImageLoader;
import com.example.apppet.ui.UiThemeManager;

import java.io.File;
import java.io.IOException;
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

    private UserRepository users;
    private PetRepository pets;
    private ChatRepository chat;
    private String userId;
    private int loadToken = 0;

    private final ActivityResultLauncher<PickVisualMediaRequest> avatarPicker = registerForActivityResult(
            new ActivityResultContracts.PickVisualMedia(),
            uri -> {
                if (uri != null) uploadAvatar(uri);
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
        users = new UserRepository(this);
        pets = new PetRepository(this);
        chat = new ChatRepository(this);

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
            btnLogout.setEnabled(false);
            AppExecutors.run(() -> {
                users.signOut();                      // báo server hủy phiên rồi xóa token cục bộ
                return true;
            }, done -> {
                Toast.makeText(this, "Đã đăng xuất tài khoản", Toast.LENGTH_SHORT).show();
                Intent intent = new Intent(ProfileActivity.this, LoginActivity.class);
                intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
                startActivity(intent);
                finish();
            });
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
        // Hiện ngay thông tin đã lưu từ lần đăng nhập, rồi làm mới từ server.
        String cachedName = SessionManager.getDisplayName(this);
        if (!cachedName.isEmpty()) txtDisplayName.setText(cachedName);
        ImageLoader.load(imgUserAvatar, SessionManager.getAvatarUrl(this), android.R.drawable.ic_menu_camera);
        AppExecutors.runResult(() -> users.getProfile(userId), result -> {
            if (isFinishing() || isDestroyed()) return;
            if (!result.isOk()) {
                Errors.show(this, result);
                return;
            }
            User user = result.value;
            if (user == null) return;
            txtDisplayName.setText(user.getShownName());
            String email = SessionManager.getEmail(this);
            txtUsernameEmail.setText("@" + user.username + " · " + (email.isEmpty() ? "chưa có email" : email));
            txtCreatedAt.setText("Thành viên từ: " + formatJoinDate(user.createdAt));
            if (user.avatarUri != null && !user.avatarUri.trim().isEmpty()) {
                imgUserAvatar.setPadding(0, 0, 0, 0);
                ImageLoader.load(imgUserAvatar, user.avatarUri, android.R.drawable.ic_menu_camera);
            }
            SessionManager.cacheProfile(this, user.displayName, user.avatarUri);
            if (user.isAdmin()) showAdminEntry();
        });
    }

    private boolean adminEntryShown = false;

    /** Chỉ ADMIN mới thấy lối vào hàng chờ duyệt; server vẫn kiểm tra quyền ở từng thao tác. */
    private void showAdminEntry() {
        if (adminEntryShown) return;
        adminEntryShown = true;
        TextView entry = new TextView(this);
        entry.setText("Quản trị nội dung (duyệt ảnh, wiki, báo cáo)");
        entry.setGravity(android.view.Gravity.CENTER);
        entry.setTextColor(getColor(R.color.white));
        entry.setTypeface(null, android.graphics.Typeface.BOLD);
        entry.setBackgroundResource(R.drawable.bg_primary_button);
        android.view.ViewGroup parent = (android.view.ViewGroup) btnLogout.getParent();
        android.view.ViewGroup.LayoutParams lp = btnLogout.getLayoutParams();
        android.widget.LinearLayout.LayoutParams params = new android.widget.LinearLayout.LayoutParams(
                lp.width, (int) (50 * getResources().getDisplayMetrics().density));
        params.bottomMargin = (int) (12 * getResources().getDisplayMetrics().density);
        entry.setOnClickListener(v -> startActivity(new Intent(this, AdminActivity.class)));
        parent.addView(entry, parent.indexOfChild(btnLogout), params);
    }

    private void loadPetsAndForums() {
        final int token = ++loadToken;
        AppExecutors.runResult(() -> {
            Object[] data = new Object[2];
            data[0] = pets.listPets();
            data[1] = chat.myCommunities();
            return data;
        }, result -> {
            if (isFinishing() || isDestroyed() || token != loadToken) return;
            if (!result.isOk()) {
                Errors.show(this, result);
                return;
            }
            @SuppressWarnings("unchecked") List<Pet> petList = (List<Pet>) result.value[0];
            @SuppressWarnings("unchecked") List<Community> communities = (List<Community>) result.value[1];
            rvProfilePets.setAdapter(new PetAdapter(petList, this));
            rvProfileForums.setAdapter(new ForumAdapter(communities, community -> {
                Intent intent = new Intent(ProfileActivity.this, ChatActivity.class);
                intent.putExtra(ChatActivity.EXTRA_COMMUNITY_ID, community.id);
                startActivity(intent);
            }));
        });
    }

    private void openGalleryForAvatar() {
        avatarPicker.launch(new PickVisualMediaRequest.Builder()
                .setMediaType(ActivityResultContracts.PickVisualMedia.ImageOnly.INSTANCE).build());
    }

    /** Thu nhỏ + xoay đúng chiều ảnh, tải lên Supabase Storage rồi lưu URL vào hồ sơ. */
    private void uploadAvatar(Uri uri) {
        Toast.makeText(this, "Đang cập nhật ảnh đại diện…", Toast.LENGTH_SHORT).show();
        final android.content.Context app = getApplicationContext();
        AppExecutors.runResult(() -> {
            String local = null;
            try {
                local = ImageStorage.copyToAppStorage(app, uri, "avatar_tmp");
                return users.changeAvatar(userId, new File(Uri.parse(local).getPath()));
            } catch (IOException ex) {
                throw new com.example.apppet.net.ApiException(0, "io", "Không đọc được ảnh");
            } finally {
                ImageStorage.deleteIfOwned(app, local);       // chỉ là bản tạm để tải lên
            }
        }, result -> {
            if (isFinishing() || isDestroyed()) return;
            if (!result.isOk()) {
                Errors.show(this, result);
                return;
            }
            imgUserAvatar.setPadding(0, 0, 0, 0);
            ImageLoader.load(imgUserAvatar, result.value, android.R.drawable.ic_menu_camera);
            Toast.makeText(this, "Đã cập nhật ảnh đại diện hồ sơ!", Toast.LENGTH_SHORT).show();
        });
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

    /** "2026-10-22 08:30:00" -> "22/10/2026"; thiếu dữ liệu thì để trống thay vì ghi ngày giả. */
    private static String formatJoinDate(String createdAt) {
        if (createdAt == null || createdAt.length() < 10) return "không rõ";
        return createdAt.substring(8, 10) + "/" + createdAt.substring(5, 7) + "/" + createdAt.substring(0, 4);
    }
}
