package com.example.apppet.ui;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.ImageView;
import android.widget.TextView;

import com.bumptech.glide.Glide;
import com.example.apppet.R;
import com.example.apppet.model.AdminItem;

import java.util.ArrayList;
import java.util.List;

/** Danh sách mục chờ duyệt, mỗi mục có hai nút hành động (nhãn tùy loại). */
public class AdminAdapter extends BaseAdapter {
    public interface Listener {
        void onYes(AdminItem item);

        void onNo(AdminItem item);
    }

    private final Context context;
    private final Listener listener;
    private final List<AdminItem> items = new ArrayList<>();

    public AdminAdapter(Context context, Listener listener) {
        this.context = context;
        this.listener = listener;
    }

    public void setItems(List<AdminItem> newItems) {
        items.clear();
        items.addAll(newItems);
        notifyDataSetChanged();
    }

    public void remove(AdminItem item) {
        items.remove(item);
        notifyDataSetChanged();
    }

    @Override
    public int getCount() {
        return items.size();
    }

    @Override
    public AdminItem getItem(int position) {
        return items.get(position);
    }

    @Override
    public long getItemId(int position) {
        return items.get(position).id;
    }

    @Override
    public View getView(int position, View convertView, ViewGroup parent) {
        View view = convertView != null ? convertView
                : LayoutInflater.from(context).inflate(R.layout.item_admin, parent, false);
        final AdminItem item = items.get(position);
        ImageView img = view.findViewById(R.id.imgAdminItem);
        ((TextView) view.findViewById(R.id.txtAdminTitle)).setText(item.title);
        ((TextView) view.findViewById(R.id.txtAdminBody)).setText(item.body);
        if (item.imageUrl != null && !item.imageUrl.isEmpty()) {
            img.setVisibility(View.VISIBLE);
            Glide.with(context).load(item.imageUrl).centerCrop().into(img);
        } else {
            Glide.with(context).clear(img);
            img.setVisibility(View.GONE);
        }
        TextView yes = view.findViewById(R.id.btnAdminYes);
        TextView no = view.findViewById(R.id.btnAdminNo);
        yes.setText(item.type == AdminItem.TYPE_REPORT ? "Xóa tin (vi phạm)" : "Duyệt");
        no.setText(item.type == AdminItem.TYPE_REPORT ? "Bỏ qua" : "Từ chối");
        yes.setOnClickListener(v -> listener.onYes(item));
        no.setOnClickListener(v -> listener.onNo(item));
        return view;
    }
}
