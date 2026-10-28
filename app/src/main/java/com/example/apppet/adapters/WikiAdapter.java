package com.example.apppet.adapters;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.example.apppet.R;
import com.example.apppet.model.WikiArticle;

import java.util.List;

public class WikiAdapter extends RecyclerView.Adapter<WikiAdapter.WikiViewHolder> {

    public interface Listener {
        void onArticleClick(WikiArticle article);
    }

    private final List<WikiArticle> items;
    private final Listener listener;

    public WikiAdapter(List<WikiArticle> items, Listener listener) {
        this.items = items;
        this.listener = listener;
    }

    @NonNull
    @Override
    public WikiViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        return new WikiViewHolder(LayoutInflater.from(parent.getContext()).inflate(R.layout.item_wiki, parent, false));
    }

    @Override
    public void onBindViewHolder(@NonNull WikiViewHolder holder, int position) {
        WikiArticle a = items.get(position);
        holder.txtTitle.setText(a.title);
        holder.txtSummary.setText(a.summary != null ? a.summary : "");
        StringBuilder meta = new StringBuilder();
        if (a.categoryName != null) meta.append(a.categoryName);
        if (a.sourceName != null) meta.append(meta.length() > 0 ? " · " : "").append(a.sourceName);
        holder.txtMeta.setText(meta);
        holder.itemView.setOnClickListener(v -> {
            if (listener != null) listener.onArticleClick(a);
        });
    }

    @Override
    public int getItemCount() {
        return items.size();
    }

    static class WikiViewHolder extends RecyclerView.ViewHolder {
        final TextView txtTitle, txtSummary, txtMeta;

        WikiViewHolder(@NonNull View itemView) {
            super(itemView);
            txtTitle = itemView.findViewById(R.id.txtWikiItemTitle);
            txtSummary = itemView.findViewById(R.id.txtWikiItemSummary);
            txtMeta = itemView.findViewById(R.id.txtWikiItemMeta);
        }
    }
}
