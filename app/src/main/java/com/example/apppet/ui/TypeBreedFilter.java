package com.example.apppet.ui;

import android.content.Context;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Spinner;

import com.example.apppet.data.CatalogDatabase;
import com.example.apppet.model.Breed;
import com.example.apppet.model.PetType;

import java.util.ArrayList;
import java.util.List;

/** Hai Spinner "Loài" + "Giống" liên động, dùng chung cho Bộ sưu tập và Wiki. */
public final class TypeBreedFilter {
    private final Context context;
    private final CatalogDatabase db;
    private final Spinner typeSpinner;
    private final Spinner breedSpinner;
    private final Runnable onChanged;
    private final List<PetType> types = new ArrayList<>();
    private final List<Breed> breeds = new ArrayList<>();
    private Long pendingBreedId;
    private String lastKey = null;

    public TypeBreedFilter(Context context, CatalogDatabase db, Spinner typeSpinner, Spinner breedSpinner,
                           Long initialTypeId, Long initialBreedId, Runnable onChanged) {
        this.context = context;
        this.db = db;
        this.typeSpinner = typeSpinner;
        this.breedSpinner = breedSpinner;
        this.onChanged = onChanged;
        this.pendingBreedId = initialBreedId;

        types.add(new PetType(0, "Tất cả loài"));
        types.addAll(db.getPetTypes());
        typeSpinner.setAdapter(new ArrayAdapter<>(context, android.R.layout.simple_spinner_dropdown_item, types));
        if (initialTypeId != null) {
            for (int i = 0; i < types.size(); i++) {
                if (types.get(i).id == initialTypeId) {
                    typeSpinner.setSelection(i);
                    break;
                }
            }
        }
        rebuildBreeds();

        typeSpinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                rebuildBreeds();
                fireIfChanged();
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) { }
        });
        breedSpinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                fireIfChanged();
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) { }
        });
    }

    private void rebuildBreeds() {
        breeds.clear();
        breeds.add(new Breed(0, 0, "Tất cả giống", null));
        Long typeId = selectedTypeId();
        if (typeId != null) breeds.addAll(db.getBreedsByPetType(typeId));
        breedSpinner.setAdapter(new ArrayAdapter<>(context, android.R.layout.simple_spinner_dropdown_item, breeds));
        breedSpinner.setEnabled(typeId != null);
        if (pendingBreedId != null) {
            for (int i = 0; i < breeds.size(); i++) {
                if (breeds.get(i).id == pendingBreedId) {
                    breedSpinner.setSelection(i);
                    break;
                }
            }
            pendingBreedId = null;      // chỉ áp dụng cho lần dựng đầu tiên
        }
    }

    private void fireIfChanged() {
        String key = selectedTypeId() + ":" + selectedBreedId();
        if (key.equals(lastKey)) return;
        lastKey = key;
        onChanged.run();
    }

    public Long selectedTypeId() {
        int pos = typeSpinner.getSelectedItemPosition();
        return pos <= 0 || pos >= types.size() ? null : types.get(pos).id;
    }

    public Long selectedBreedId() {
        int pos = breedSpinner.getSelectedItemPosition();
        return pos <= 0 || pos >= breeds.size() ? null : breeds.get(pos).id;
    }
}
