package com.example.apppet.adapters;

import android.graphics.Color;
import android.net.Uri;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.example.apppet.R;
import com.example.apppet.model.Pet;
import com.example.apppet.ui.PetIcons;

import java.util.List;

public class PetAdapter extends RecyclerView.Adapter<PetAdapter.PetViewHolder> {

    public interface OnPetClickListener {
        void onPetClick(Pet pet, int position);
        void onAddPetClick();
    }

    private final List<Pet> petList;
    private int selectedPosition = 0;
    private final OnPetClickListener listener;

    public PetAdapter(List<Pet> petList, OnPetClickListener listener) {
        this.petList = petList;
        this.listener = listener;
    }

    public void setSelectedPosition(int position) {
        this.selectedPosition = position;
        notifyDataSetChanged();
    }

    public int getSelectedPosition() {
        return selectedPosition;
    }

    @NonNull
    @Override
    public PetViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_pet_avatar, parent, false);
        return new PetViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull PetViewHolder holder, int position) {
        if (position < petList.size()) {
            Pet pet = petList.get(position);
            holder.txtPetName.setText(pet.getName());
            
            if (position == selectedPosition) {
                holder.containerAvatar.setBackgroundResource(R.drawable.bg_pet_active);
                holder.txtPetName.setTextColor(Color.parseColor("#2B6CB0"));
            } else {
                holder.containerAvatar.setBackgroundResource(R.drawable.bg_pet_inactive);
                holder.txtPetName.setTextColor(Color.parseColor("#64748B"));
            }

            if (pet.getImageUri() != null && !pet.getImageUri().trim().isEmpty()) {
                try {
                    holder.imgPetAvatar.setImageURI(Uri.parse(pet.getImageUri()));
                    holder.imgPetAvatar.setVisibility(View.VISIBLE);
                    holder.tvPetEmoji.setVisibility(View.GONE);
                } catch (Exception e) {
                    holder.imgPetAvatar.setVisibility(View.GONE);
                    holder.tvPetEmoji.setVisibility(View.VISIBLE);
                    holder.tvPetEmoji.setText(PetIcons.forType(pet.petTypeName));
                }
            } else {
                holder.imgPetAvatar.setVisibility(View.GONE);
                holder.tvPetEmoji.setVisibility(View.VISIBLE);
                holder.tvPetEmoji.setText(PetIcons.forType(pet.petTypeName));
            }

            holder.itemView.setOnClickListener(v -> {
                int previous = selectedPosition;
                selectedPosition = holder.getAdapterPosition();
                notifyItemChanged(previous);
                notifyItemChanged(selectedPosition);
                if (listener != null) {
                    listener.onPetClick(pet, selectedPosition);
                }
            });
        } else {
            // "Thêm" button item
            holder.txtPetName.setText("Thêm");
            holder.txtPetName.setTextColor(Color.parseColor("#64748B"));
            holder.containerAvatar.setBackgroundResource(R.drawable.bg_add_button);
            holder.tvPetEmoji.setVisibility(View.GONE);
            holder.imgPetAvatar.setVisibility(View.VISIBLE);
            holder.imgPetAvatar.setImageResource(R.drawable.ic_plus);

            holder.itemView.setOnClickListener(v -> {
                if (listener != null) {
                    listener.onAddPetClick();
                }
            });
        }
    }

    @Override
    public int getItemCount() {
        return petList.size() + 1; // +1 for "Thêm" button
    }

    static class PetViewHolder extends RecyclerView.ViewHolder {
        FrameLayout containerAvatar;
        ImageView imgPetAvatar;
        TextView tvPetEmoji, txtPetName;

        public PetViewHolder(@NonNull View itemView) {
            super(itemView);
            containerAvatar = itemView.findViewById(R.id.containerAvatar);
            imgPetAvatar = itemView.findViewById(R.id.imgPetAvatar);
            tvPetEmoji = itemView.findViewById(R.id.tvPetEmoji);
            txtPetName = itemView.findViewById(R.id.txtPetName);
        }
    }
}
