package com.example.apppet.adapters;

import android.graphics.Color;
import android.net.Uri;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;

import androidx.annotation.NonNull;
import androidx.cardview.widget.CardView;
import androidx.recyclerview.widget.RecyclerView;

import com.example.apppet.R;
import com.example.apppet.model.GalleryItem;

import java.util.List;

public class GalleryAdapter extends RecyclerView.Adapter<GalleryAdapter.GalleryViewHolder> {

    private final List<GalleryItem> items;

    public GalleryAdapter(List<GalleryItem> items) {
        this.items = items;
    }

    @NonNull
    @Override
    public GalleryViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_gallery, parent, false);
        return new GalleryViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull GalleryViewHolder holder, int position) {
        GalleryItem item = items.get(position);

        if (item.getColorHex() != null && !item.getColorHex().isEmpty()) {
            holder.cardGallery.setCardBackgroundColor(Color.parseColor(item.getColorHex()));
        }

        if (item.getImageUri() != null && !item.getImageUri().isEmpty()) {
            holder.imgGalleryPhoto.setVisibility(View.VISIBLE);
            holder.imgDownloadIcon.setVisibility(View.GONE);
            try {
                holder.imgGalleryPhoto.setImageURI(Uri.parse(item.getImageUri()));
            } catch (Exception ignored) {}
        } else if (item.isDownload()) {
            holder.imgGalleryPhoto.setVisibility(View.GONE);
            holder.imgDownloadIcon.setVisibility(View.VISIBLE);
        } else {
            holder.imgGalleryPhoto.setVisibility(View.GONE);
            holder.imgDownloadIcon.setVisibility(View.GONE);
        }
    }

    @Override
    public int getItemCount() {
        return items.size();
    }

    static class GalleryViewHolder extends RecyclerView.ViewHolder {
        CardView cardGallery;
        ImageView imgGalleryPhoto;
        ImageView imgDownloadIcon;

        public GalleryViewHolder(@NonNull View itemView) {
            super(itemView);
            cardGallery = itemView.findViewById(R.id.cardGallery);
            imgGalleryPhoto = itemView.findViewById(R.id.imgGalleryPhoto);
            imgDownloadIcon = itemView.findViewById(R.id.imgDownloadIcon);
        }
    }
}