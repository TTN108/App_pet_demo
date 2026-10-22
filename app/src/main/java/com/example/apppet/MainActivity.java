package com.example.apppet;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.RelativeLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.recyclerview.widget.RecyclerView;

import com.example.apppet.adapters.GalleryAdapter;
import com.example.apppet.adapters.PetAdapter;
import com.example.apppet.data.DatabaseHelper;
import com.example.apppet.data.SessionManager;
import com.example.apppet.model.CareGuide;
import com.example.apppet.model.Community;
import com.example.apppet.model.ForumPost;
import com.example.apppet.model.GalleryItem;
import com.example.apppet.model.Pet;
import com.example.apppet.model.User;
import com.example.apppet.ui.UiThemeManager;

import java.util.List;

public class MainActivity extends Activity implements PetAdapter.OnPetClickListener {

    private RelativeLayout rootMain;
    private ScrollView scrollViewMain;
    private LinearLayout bottomNav, cardCareGuide, cardForumPost, layoutProfileHeader;
    private TextView txtUserNameHeader, txtBannerPetTitle, txtGalleryTitle;
    private TextView txtCareTitle, txtCareSubtitle, txtForumGroup, txtForumTime, txtForumContent;
    private RecyclerView rvPets, rvGallery;
    private RelativeLayout bannerAiCamera;
    private ImageView btnNotification, btnBannerCamera;
    private ImageView navHome, navGallery, navBook, navChat, navProfile;

    private DatabaseHelper databaseHelper;
    private PetAdapter petAdapter;
    private List<Pet> petList;
    private Pet currentPet;
    private long userId;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        if (!SessionManager.isLoggedIn(this)) {
            startActivity(new Intent(MainActivity.this, LoginActivity.class));
            finish();
            return;
        }

        userId = SessionManager.getUserId(this);
        databaseHelper = new DatabaseHelper(this);
        databaseHelper.getReadableDatabase(); // init db & sample communities
        databaseHelper.syncCommunityMemberships(userId);

        setContentView(R.layout.activity_main);

        initViews();
        setupNavigation();
    }

    @Override
    protected void onResume() {
        super.onResume();
        UiThemeManager.applyTheme(rootMain, this);
        setupUserSession();
        loadPetsData();
    }

    private void initViews() {
        rootMain = findViewById(R.id.rootMain);
        scrollViewMain = findViewById(R.id.scrollViewMain);
        bottomNav = findViewById(R.id.bottomNav);
        layoutProfileHeader = findViewById(R.id.layoutProfileHeader);
        txtUserNameHeader = findViewById(R.id.txtUserNameHeader);
        txtBannerPetTitle = findViewById(R.id.txtBannerPetTitle);
        txtGalleryTitle = findViewById(R.id.txtGalleryTitle);
        txtCareTitle = findViewById(R.id.txtCareTitle);
        txtCareSubtitle = findViewById(R.id.txtCareSubtitle);
        txtForumGroup = findViewById(R.id.txtForumGroup);
        txtForumTime = findViewById(R.id.txtForumTime);
        txtForumContent = findViewById(R.id.txtForumContent);
        cardCareGuide = findViewById(R.id.cardCareGuide);
        cardForumPost = findViewById(R.id.cardForumPost);

        rvPets = findViewById(R.id.rvPets);
        rvGallery = findViewById(R.id.rvGallery);
        bannerAiCamera = findViewById(R.id.bannerAiCamera);
        btnNotification = findViewById(R.id.btnNotification);
        btnBannerCamera = findViewById(R.id.btnBannerCamera);

        navHome = findViewById(R.id.navHome);
        navGallery = findViewById(R.id.navGallery);
        navBook = findViewById(R.id.navBook);
        navChat = findViewById(R.id.navChat);
        navProfile = findViewById(R.id.navProfile);

        layoutProfileHeader.setOnClickListener(v -> openProfileScreen());
        txtUserNameHeader.setOnClickListener(v -> openProfileScreen());
        bannerAiCamera.setOnClickListener(v -> openAddPetScreen());
        btnBannerCamera.setOnClickListener(v -> openAddPetScreen());
        btnNotification.setOnClickListener(v ->
                Toast.makeText(this, "Không có thông báo mới", Toast.LENGTH_SHORT).show()
        );

        // Click forum card -> open Community/Forum list
        cardForumPost.setOnClickListener(v -> openCommunityScreen());
    }

    private void setupUserSession() {
        User user = databaseHelper.getUser(userId);
        if (user != null) {
            txtUserNameHeader.setText(user.getShownName());
        } else {
            txtUserNameHeader.setText("Người dùng");
        }
    }

    private void loadPetsData() {
        petList = databaseHelper.getPetsByUserId(userId);

        if (petList.isEmpty()) {
            // Seed default pet "Mèo Mun" for user if none exists
            databaseHelper.addPet(userId, "Mèo Mun", 1, "Anh lông ngắn", "");
            petList = databaseHelper.getPetsByUserId(userId);
        }

        petAdapter = new PetAdapter(petList, this);
        rvPets.setAdapter(petAdapter);

        if (!petList.isEmpty()) {
            selectPet(petList.get(0));
        }
    }

    private void selectPet(Pet pet) {
        this.currentPet = pet;
        txtBannerPetTitle.setText("Chụp ảnh cho " + pet.getName());
        txtGalleryTitle.setText("Bộ sưu tập ảnh " + pet.getCategoryName().toLowerCase());

        loadGallery(pet.getCategoryId());
        loadCareGuide(pet.getCategoryId());
        loadForumPost(pet.getCategoryId());
    }

    private void loadGallery(int categoryId) {
        List<GalleryItem> galleryItems = databaseHelper.getGalleryByCategory(categoryId);
        GalleryAdapter galleryAdapter = new GalleryAdapter(galleryItems);
        rvGallery.setAdapter(galleryAdapter);
    }

    private void loadCareGuide(int categoryId) {
        CareGuide guide = databaseHelper.getCareGuideByCategory(categoryId);
        if (guide != null) {
            txtCareTitle.setText(guide.getTitle());
            txtCareSubtitle.setText(guide.getSubtitle());
        }
    }

    private void loadForumPost(int categoryId) {
        List<Community> communities = databaseHelper.getCommunitiesForUser(userId);
        Community targetCommunity = null;
        for (Community c : communities) {
            if (c.petTypeId == categoryId) {
                targetCommunity = c;
                break;
            }
        }
        if (targetCommunity == null && !communities.isEmpty()) {
            targetCommunity = communities.get(0);
        }

        if (targetCommunity != null) {
            txtForumGroup.setText(targetCommunity.name);
            txtForumTime.setText(targetCommunity.memberCount + " thành viên");
            txtForumContent.setText(targetCommunity.description != null && !targetCommunity.description.isEmpty() ?
                    targetCommunity.description : "Cộng đồng trao đổi và chăm sóc thú cưng");

            final long comId = targetCommunity.id;
            cardForumPost.setOnClickListener(v -> {
                Intent intent = new Intent(MainActivity.this, ChatActivity.class);
                intent.putExtra(ChatActivity.EXTRA_COMMUNITY_ID, comId);
                startActivity(intent);
            });
        }
    }

    @Override
    public void onPetClick(Pet pet, int position) {
        selectPet(pet);
    }

    @Override
    public void onAddPetClick() {
        openAddPetScreen();
    }

    private void openAddPetScreen() {
        Intent intent = new Intent(MainActivity.this, AddPetActivity.class);
        startActivity(intent);
    }

    private void openProfileScreen() {
        Intent intent = new Intent(MainActivity.this, ProfileActivity.class);
        startActivity(intent);
    }

    private void openCommunityScreen() {
        Intent intent = new Intent(MainActivity.this, CommunityListActivity.class);
        startActivity(intent);
    }

    private void setupNavigation() {
        navHome.setOnClickListener(v -> {
            if (scrollViewMain != null) {
                scrollViewMain.smoothScrollTo(0, 0);
            }
            loadPetsData();
            Toast.makeText(this, "Trang chủ - Đã làm mới", Toast.LENGTH_SHORT).show();
        });
        navGallery.setOnClickListener(v ->
                Toast.makeText(this, "Bộ sưu tập " + (currentPet != null ? currentPet.getName() : ""), Toast.LENGTH_SHORT).show()
        );
        navBook.setOnClickListener(v ->
                Toast.makeText(this, "Cẩm nang chăm sóc", Toast.LENGTH_SHORT).show()
        );
        navChat.setOnClickListener(v -> openCommunityScreen());
        navProfile.setOnClickListener(v -> openProfileScreen());
    }
}