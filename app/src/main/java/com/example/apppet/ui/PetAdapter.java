package com.example.apppet.ui;

import android.content.Context;
import android.text.TextUtils;
import android.net.Uri;
import android.widget.ImageView;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.TextView;

import com.example.apppet.R;
import com.example.apppet.model.Pet;

import java.util.ArrayList;
import java.util.List;

public class PetAdapter extends BaseAdapter {
    private final LayoutInflater inflater;
    private final List<Pet> pets = new ArrayList<>();

    public PetAdapter(Context context) {
        inflater = LayoutInflater.from(context);
    }

    public void setItems(List<Pet> items) {
        pets.clear();
        if (items != null) pets.addAll(items);
        notifyDataSetChanged();
    }

    @Override
    public int getCount() { return pets.size(); }

    @Override
    public Pet getItem(int position) { return pets.get(position); }

    @Override
    public long getItemId(int position) { return pets.get(position).id; }

    @Override
    public View getView(int position, View convertView, ViewGroup parent) {
        View row = convertView;
        if (row == null) row = inflater.inflate(R.layout.item_pet, parent, false);

        Pet pet = getItem(position);
        TextView icon = row.findViewById(R.id.tvPetIcon);
        ImageView avatar = row.findViewById(R.id.ivPetAvatar);
        TextView name = row.findViewById(R.id.tvPetName);
        TextView typeAndBreed = row.findViewById(R.id.tvPetTypeBreed);
        TextView summary = row.findViewById(R.id.tvPetSummary);

        icon.setText(iconForType(pet.petTypeName));
        if (pet.avatarUri != null && !pet.avatarUri.trim().isEmpty()) {
            try {
                avatar.setImageURI(Uri.parse(pet.avatarUri));
                avatar.setVisibility(View.VISIBLE);
                icon.setVisibility(View.GONE);
            } catch (Exception ex) {
                avatar.setVisibility(View.GONE);
                icon.setVisibility(View.VISIBLE);
            }
        } else {
            avatar.setVisibility(View.GONE);
            icon.setVisibility(View.VISIBLE);
        }
        name.setText(pet.name);
        String breed = pet.breedName == null || pet.breedName.trim().isEmpty() ? "Chưa rõ giống" : pet.breedName;
        typeAndBreed.setText(pet.petTypeName + "  ·  " + breed);

        List<String> bits = new ArrayList<>();
        if (pet.gender != null && !pet.gender.trim().isEmpty()) bits.add(pet.gender);
        if (pet.color != null && !pet.color.trim().isEmpty()) bits.add(pet.color);
        if (pet.weight != null) bits.add(trimNumber(pet.weight) + " kg");
        summary.setText(bits.isEmpty() ? "Chạm để xem thông tin chi tiết" : TextUtils.join("  ·  ", bits));
        return row;
    }

    private String iconForType(String type) {
        if (type == null) return "🐾";
        switch (type) {
            case "Chó": return "🐶";
            case "Mèo": return "🐱";
            case "Chim": return "🐦";
            case "Cá": return "🐟";
            case "Thỏ": return "🐰";
            default: return "🐾";
        }
    }

    private String trimNumber(Double value) {
        if (value == null) return "";
        if (value == Math.rint(value)) return String.valueOf(value.longValue());
        return String.format(java.util.Locale.getDefault(), "%.2f", value).replaceAll("0+$", "").replaceAll("\\.$", "");
    }
}
