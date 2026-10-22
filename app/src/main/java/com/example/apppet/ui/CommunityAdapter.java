package com.example.apppet.ui;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.TextView;

import com.example.apppet.R;
import com.example.apppet.model.Community;

import java.util.ArrayList;
import java.util.List;

public class CommunityAdapter extends BaseAdapter {
    private final LayoutInflater inflater;
    private final List<Community> items = new ArrayList<>();

    public CommunityAdapter(Context context) {
        inflater = LayoutInflater.from(context);
    }

    public void setItems(List<Community> list) {
        items.clear();
        if (list != null) items.addAll(list);
        notifyDataSetChanged();
    }

    @Override
    public int getCount() { return items.size(); }

    @Override
    public Community getItem(int position) { return items.get(position); }

    @Override
    public long getItemId(int position) { return items.get(position).id; }

    @Override
    public View getView(int position, View convertView, ViewGroup parent) {
        View row = convertView;
        if (row == null) row = inflater.inflate(R.layout.item_community, parent, false);
        Community c = getItem(position);
        ((TextView) row.findViewById(R.id.tvCommunityIcon)).setText(PetIcons.forType(c.petTypeName));
        ((TextView) row.findViewById(R.id.tvCommunityName)).setText(c.name);
        ((TextView) row.findViewById(R.id.tvCommunityMembers)).setText(c.memberCount + " thành viên");
        boolean hasMessage = c.lastMessage != null && !c.lastMessage.trim().isEmpty();
        ((TextView) row.findViewById(R.id.tvCommunityLast)).setText(
                hasMessage ? c.lastMessage.replace('\n', ' ') : "Chưa có tin nhắn - hãy chào cả nhóm!");
        return row;
    }
}
