package com.example.apppet;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.ListView;
import android.widget.TextView;

import com.example.apppet.data.DatabaseHelper;
import com.example.apppet.data.SessionManager;
import com.example.apppet.model.Pet;
import com.example.apppet.ui.PetAdapter;

import java.util.List;

public class MainActivity extends Activity {
    private static final int REQUEST_ADD_PET = 1001;
    private DatabaseHelper databaseHelper;
    private PetAdapter adapter;
    private ListView listPets;
    private TextView tvCount;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        databaseHelper = new DatabaseHelper(this);
        // Khởi tạo DB và dữ liệu loại/giống mẫu trong lần mở đầu tiên.
        databaseHelper.getReadableDatabase();
        SessionManager.getUserId(this);

        listPets = findViewById(R.id.listPets);
        TextView tvEmpty = findViewById(R.id.tvEmpty);
        tvCount = findViewById(R.id.tvCount);
        Button btnAddPet = findViewById(R.id.btnAddPet);

        adapter = new PetAdapter(this);
        listPets.setAdapter(adapter);
        listPets.setEmptyView(tvEmpty);

        listPets.setOnItemClickListener((parent, view, position, id) -> {
            Pet selected = adapter.getItem(position);
            Intent intent = new Intent(MainActivity.this, PetDetailActivity.class);
            intent.putExtra(PetDetailActivity.EXTRA_PET_ID, selected.id);
            startActivity(intent);
        });

        btnAddPet.setOnClickListener(v ->
                startActivityForResult(new Intent(MainActivity.this, AddPetActivity.class), REQUEST_ADD_PET));

        loadPets();
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (adapter != null) loadPets();
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == REQUEST_ADD_PET && resultCode == RESULT_OK) loadPets();
    }

    private void loadPets() {
        long userId = SessionManager.getUserId(this);
        List<Pet> pets = databaseHelper.getPetsForUser(userId);
        adapter.setItems(pets);
        tvCount.setText("Bạn đang có " + pets.size() + " thú cưng");
    }
}
