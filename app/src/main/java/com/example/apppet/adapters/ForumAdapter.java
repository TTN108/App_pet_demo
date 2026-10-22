package com.example.apppet.adapters;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.example.apppet.R;
import com.example.apppet.model.Community;
import com.example.apppet.ui.PetIcons;

import java.util.List;

public class ForumAdapter extends RecyclerView.Adapter<ForumAdapter.ForumViewHolder> {

    public interface OnCommunityClickListener {
        void onCommunityClick(Community community);
    }

    private final List<Community> communities;
    private final OnCommunityClickListener listener;

    public ForumAdapter(List<Community> communities, OnCommunityClickListener listener) {
        this.communities = communities;
        this.listener = listener;
    }

    @NonNull
    @Override
    public ForumViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_forum_card, parent, false);
        return new ForumViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ForumViewHolder holder, int position) {
        Community community = communities.get(position);
        holder.tvEmoji.setText(PetIcons.forType(community.petTypeName));
        holder.txtTitle.setText(community.name);
        holder.txtDesc.setText(community.memberCount + " thành viên · " + (community.description != null ? community.description : "Cộng đồng thú cưng"));
        
        holder.itemView.setOnClickListener(v -> {
            if (listener != null) {
                listener.onCommunityClick(community);
            }
        });
    }

    @Override
    public int getItemCount() {
        return communities != null ? communities.size() : 0;
    }

    static class ForumViewHolder extends RecyclerView.ViewHolder {
        TextView tvEmoji, txtTitle, txtDesc;

        public ForumViewHolder(@NonNull View itemView) {
            super(itemView);
            tvEmoji = itemView.findViewById(R.id.tvForumEmoji);
            txtTitle = itemView.findViewById(R.id.txtForumCardTitle);
            txtDesc = itemView.findViewById(R.id.txtForumCardDesc);
        }
    }
}
