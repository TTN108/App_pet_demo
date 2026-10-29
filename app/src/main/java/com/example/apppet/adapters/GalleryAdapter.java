package com.example.apppet.adapters;

import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;
import com.example.apppet.R;
import com.example.apppet.model.GalleryImage;

import java.util.List;

/** Ảnh bộ sưu tập tải từ URL bằng Glide. grid = true: lưới có nút tải về; false: thẻ nhỏ cuộn ngang ở trang chủ. */
public class GalleryAdapter extends RecyclerView.Adapter<GalleryAdapter.GalleryViewHolder> {

    public interface Listener {
        void onImageClick(GalleryImage image);

        void onDownloadClick(GalleryImage image);
    }

    private final List<GalleryImage> items;
    private final boolean grid;
    private final Listener listener;

    public GalleryAdapter(List<GalleryImage> items, boolean grid, Listener listener) {
        this.items = items;
        this.grid = grid;
        this.listener = listener;
    }

    @NonNull
    @Override
    public GalleryViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(grid ? R.layout.item_gallery_grid : R.layout.item_gallery, parent, false);
        return new GalleryViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull GalleryViewHolder holder, int position) {
        GalleryImage item = items.get(position);
        ColorDrawable placeholder = new ColorDrawable(Color.parseColor("#E2E8F0"));
        Glide.with(holder.imgPhoto.getContext())
                .load(item.thumbUrl())
                .centerCrop()
                .placeholder(placeholder)
                .error(placeholder)
                .into(holder.imgPhoto);
        holder.imgPhoto.setVisibility(View.VISIBLE);
        holder.imgPhoto.setContentDescription(item.title != null ? item.title : "Ảnh thú cưng");

        if (holder.txtCaption != null) {
            holder.txtCaption.setText(item.title != null ? item.title : "");
        }
        holder.imgDownload.setVisibility(grid ? View.VISIBLE : View.GONE);
        holder.itemView.setOnClickListener(v -> {
            if (listener != null) listener.onImageClick(item);
        });
        if (grid) {
            holder.imgDownload.setOnClickListener(v -> {
                if (listener != null) listener.onDownloadClick(item);
            });
        }
    }

    @Override
    public void onViewRecycled(@NonNull GalleryViewHolder holder) {
        Glide.with(holder.imgPhoto.getContext()).clear(holder.imgPhoto);
        super.onViewRecycled(holder);
    }

    @Override
    public int getItemCount() {
        return items.size();
    }

    static class GalleryViewHolder extends RecyclerView.ViewHolder {
        final ImageView imgPhoto;
        final ImageView imgDownload;
        final TextView txtCaption;

        GalleryViewHolder(@NonNull View itemView) {
            super(itemView);
            imgPhoto = itemView.findViewById(R.id.imgGalleryPhoto);
            imgDownload = itemView.findViewById(R.id.imgDownloadIcon);
            txtCaption = itemView.findViewById(R.id.txtGalleryCaption);   // chỉ có ở layout lưới
        }
    }
}
