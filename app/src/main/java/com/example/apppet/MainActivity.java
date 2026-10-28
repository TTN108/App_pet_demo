package com.example.apppet;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.RelativeLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.recyclerview.widget.RecyclerView;

import com.example.apppet.adapters.GalleryAdapter;
import com.example.apppet.adapters.PetAdapter;
import com.example.apppet.data.AppExecutors;
import com.example.apppet.data.CatalogDatabase;
import com.example.apppet.data.SessionManager;
import com.example.apppet.model.Community;
import com.example.apppet.model.GalleryImage;
import com.example.apppet.model.Pet;
import com.example.apppet.model.User;
import com.example.apppet.model.WikiArticle;
import com.example.apppet.net.ChatRepository;
import com.example.apppet.net.GalleryRepository;
import com.example.apppet.net.PetRepository;
import com.example.apppet.net.UserRepository;
import com.example.apppet.ui.Errors;
import com.example.apppet.ui.UiThemeManager;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class MainActivity extends Activity implements PetAdapter.OnPetClickListener, GalleryAdapter.Listener {

    /** Dữ liệu của phần phụ thuộc thú đang chọn, tải ở luồng nền rồi đẩy lên UI một lần. */
    private static final class HomeData {
        List<GalleryImage> gallery = new ArrayList<>();
        WikiArticle care;
        Community community;
    }

    private RelativeLayout rootMain;
    private ScrollView scrollViewMain;
    private LinearLayout cardCareGuide, cardForumPost, layoutProfileHeader;
    private TextView txtUserNameHeader, txtBannerPetTitle, txtGalleryTitle;
    private TextView txtCareTitle, txtCareSubtitle, txtForumGroup, txtForumTime, txtForumContent;
    private RecyclerView rvPets, rvGallery;
    private RelativeLayout bannerAiCamera;
    private ImageView btnNotification, btnBannerCamera;
    private ImageView navHome, navGallery, navBook, navChat, navProfile;

    private CatalogDatabase catalog;
    private UserRepository users;
    private PetRepository pets;
    private GalleryRepository gallery;
    private ChatRepository chat;
    private List<Pet> petList = new ArrayList<>();
    private Pet currentPet;
    private long selectedPetId = -1L;     // nhớ thú đang chọn để không bị đưa về con đầu tiên khi quay lại màn hình
    private String userId;
    private long careArticleId = -1L;
    private long forumCommunityId = -1L;
    private int homeToken = 0;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (!SessionManager.isLoggedIn(this)) {
            startActivity(new Intent(MainActivity.this, LoginActivity.class));
            finish();
            return;
        }
        userId = SessionManager.getUserId(this);
        catalog = CatalogDatabase.getInstance(this);
        users = new UserRepository(this);
        pets = new PetRepository(this);
        gallery = new GalleryRepository(this);
        chat = new ChatRepository(this);
        setContentView(R.layout.activity_main);
        initViews();
        setupNavigation();
        if (savedInstanceState != null) selectedPetId = savedInstanceState.getLong("selected_pet_id", -1L);
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putLong("selected_pet_id", selectedPetId);
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (users == null) return;     // đã chuyển sang màn đăng nhập
        UiThemeManager.applyTheme(rootMain, this);
        loadUserAndPets();
    }

    private void initViews() {
        rootMain = findViewById(R.id.rootMain);
        scrollViewMain = findViewById(R.id.scrollViewMain);
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
        bannerAiCamera.setOnClickListener(v -> openRecognizeScreen());
        btnBannerCamera.setOnClickListener(v -> openRecognizeScreen());
        btnNotification.setOnClickListener(v ->
                Toast.makeText(this, "Không có thông báo mới", Toast.LENGTH_SHORT).show());
        findViewById(R.id.btnSeeAllGallery).setOnClickListener(v -> openGalleryScreen());
        cardCareGuide.setOnClickListener(v -> {
            if (careArticleId > 0) {
                Intent intent = new Intent(this, WikiDetailActivity.class);
                intent.putExtra(WikiDetailActivity.EXTRA_ARTICLE_ID, careArticleId);
                startActivity(intent);
            } else {
                openWikiScreen();
            }
        });
        cardForumPost.setOnClickListener(v -> {
            if (forumCommunityId > 0) {
                Intent intent = new Intent(this, ChatActivity.class);
                intent.putExtra(ChatActivity.EXTRA_COMMUNITY_ID, forumCommunityId);
                startActivity(intent);
            } else {
                startActivity(new Intent(this, CommunityListActivity.class));
            }
        });
    }

    /** Nạp tên người dùng + danh sách thú ở luồng nền, giữ nguyên thú đang chọn. */
    private void loadUserAndPets() {
        final int token = ++homeToken;
        AppExecutors.runResult(() -> {
            Object[] result = new Object[2];
            result[0] = users.getProfile(userId);
            result[1] = pets.listPets();
            return result;
        }, result -> {
            if (isFinishing() || isDestroyed() || token != homeToken) return;
            if (!result.isOk()) {
                Errors.show(this, result);
                return;
            }
            User user = (User) result.value[0];
            @SuppressWarnings("unchecked") List<Pet> loaded = (List<Pet>) result.value[1];
            txtUserNameHeader.setText(user != null ? user.getShownName() : "Người dùng");
            petList = loaded;

            int index = 0;
            for (int i = 0; i < petList.size(); i++) {
                if (petList.get(i).id == selectedPetId) {
                    index = i;
                    break;
                }
            }
            PetAdapter adapter = new PetAdapter(petList, this);
            adapter.setSelectedPosition(index);
            rvPets.setAdapter(adapter);
            selectPet(petList.isEmpty() ? null : petList.get(index));
        });
    }

    /** pet = null khi người dùng chưa có thú cưng nào: hiện nội dung chung thay vì tự tạo thú giả. */
    private void selectPet(Pet pet) {
        currentPet = pet;
        selectedPetId = pet != null ? pet.id : -1L;
        if (pet != null) {
            txtBannerPetTitle.setText("Chụp ảnh cho " + pet.getName());
            txtGalleryTitle.setText("Bộ sưu tập ảnh " + pet.getCategoryName().toLowerCase(new Locale("vi")));
        } else {
            txtBannerPetTitle.setText("Chụp ảnh nhận diện thú cưng");
            txtGalleryTitle.setText("Bộ sưu tập ảnh");
        }

        final Long typeId = pet != null ? pet.petTypeId : null;
        final Long breedId = pet != null ? pet.breedId : null;
        final int token = ++homeToken;
        AppExecutors.run(() -> {
            HomeData data = new HomeData();
            List<WikiArticle> care = catalog.getWikiArticles(typeId, breedId, null, 1);     // wiki nằm trong app, không cần mạng
            data.care = care.isEmpty() ? null : care.get(0);
            try {                                                                         // phần cần mạng: lỗi thì chỉ bỏ phần đó
                data.gallery = typeId != null ? gallery.previewForPet(typeId, breedId, 12) : gallery.page(null, null, 0, 12);
            } catch (RuntimeException ignored) {
                data.gallery = new ArrayList<>();
            }
            try {
                for (Community c : chat.myCommunities()) {
                    if (typeId != null && c.petTypeId == typeId) {
                        data.community = c;
                        break;
                    }
                    if (data.community == null) data.community = c;
                }
            } catch (RuntimeException ignored) {
                data.community = null;
            }
            return data;
        }, data -> {
            if (isFinishing() || isDestroyed() || token != homeToken || data == null) return;
            rvGallery.setAdapter(new GalleryAdapter(data.gallery, false, this));

            if (data.care != null) {
                careArticleId = data.care.id;
                txtCareTitle.setText(data.care.title);
                txtCareSubtitle.setText(data.care.categoryName != null ? data.care.categoryName : "Hướng dẫn chăm sóc");
            } else {
                careArticleId = -1L;
                txtCareTitle.setText("Hướng dẫn chăm sóc");
                txtCareSubtitle.setText("Chạm để xem thư viện bài viết");
            }

            if (data.community != null) {
                forumCommunityId = data.community.id;
                txtForumGroup.setText(data.community.name);
                txtForumTime.setText(data.community.memberCount + " thành viên");
                txtForumContent.setText(data.community.lastMessage != null && !data.community.lastMessage.isEmpty()
                        ? data.community.lastMessage
                        : (data.community.description != null ? data.community.description
                        : "Cộng đồng trao đổi và chăm sóc thú cưng"));
            } else {
                forumCommunityId = -1L;
                txtForumGroup.setText("Cộng đồng");
                txtForumTime.setText("");
                txtForumContent.setText("Thêm thú cưng để tham gia nhóm chat theo loài.");
            }
        });
    }

    @Override
    public void onPetClick(Pet pet, int position) {
        selectPet(pet);
    }

    @Override
    public void onAddPetClick() {
        startActivity(new Intent(this, AddPetActivity.class));
    }

    @Override
    public void onImageClick(GalleryImage image) {
        openGalleryScreen();
    }

    @Override
    public void onDownloadClick(GalleryImage image) {
        openGalleryScreen();
    }

    private void openRecognizeScreen() {
        startActivity(new Intent(this, RecognizeActivity.class));
    }

    private void openProfileScreen() {
        startActivity(new Intent(this, ProfileActivity.class));
    }

    private void openGalleryScreen() {
        Intent intent = new Intent(this, GalleryActivity.class);
        if (currentPet != null) {
            intent.putExtra(GalleryActivity.EXTRA_PET_TYPE_ID, currentPet.petTypeId);
            if (currentPet.breedId != null) intent.putExtra(GalleryActivity.EXTRA_BREED_ID, currentPet.breedId);
        }
        startActivity(intent);
    }

    private void openWikiScreen() {
        Intent intent = new Intent(this, WikiListActivity.class);
        if (currentPet != null) {
            intent.putExtra(WikiListActivity.EXTRA_PET_TYPE_ID, currentPet.petTypeId);
            if (currentPet.breedId != null) intent.putExtra(WikiListActivity.EXTRA_BREED_ID, currentPet.breedId);
        }
        startActivity(intent);
    }

    private void setupNavigation() {
        navHome.setOnClickListener(v -> {
            scrollViewMain.smoothScrollTo(0, 0);
            loadUserAndPets();
        });
        navGallery.setOnClickListener(v -> openGalleryScreen());
        navBook.setOnClickListener(v -> openWikiScreen());
        navChat.setOnClickListener(v -> startActivity(new Intent(this, CommunityListActivity.class)));
        navProfile.setOnClickListener(v -> openProfileScreen());
    }
}
