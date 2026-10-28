package com.example.apppet.ui;

import android.content.Context;
import android.graphics.Typeface;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.example.apppet.R;
import com.example.apppet.net.TimeUtil;
import com.example.apppet.model.Message;

import java.util.ArrayList;
import java.util.List;

public class MessageAdapter extends BaseAdapter {
    private final Context context;
    private final LayoutInflater inflater;
    private final String currentUserId;
    private final int maxTextWidth;
    private final List<Message> items = new ArrayList<>();

    public MessageAdapter(Context context, String currentUserId) {
        this.context = context;
        this.inflater = LayoutInflater.from(context);
        this.currentUserId = currentUserId;
        this.maxTextWidth = (int) (context.getResources().getDisplayMetrics().widthPixels * 0.68f);
    }

    public void setItems(List<Message> list) {
        items.clear();
        if (list != null) items.addAll(list);
        notifyDataSetChanged();
    }

    @Override
    public int getCount() { return items.size(); }

    @Override
    public Message getItem(int position) { return items.get(position); }

    @Override
    public long getItemId(int position) { return items.get(position).id; }

    @Override
    public View getView(int position, View convertView, ViewGroup parent) {
        View row = convertView;
        if (row == null) row = inflater.inflate(R.layout.item_message, parent, false);

        Message m = getItem(position);
        boolean mine = currentUserId != null && currentUserId.equals(m.senderId);

        LinearLayout bubble = row.findViewById(R.id.llBubble);
        TextView sender = row.findViewById(R.id.tvMsgSender);
        TextView text = row.findViewById(R.id.tvMsgText);
        TextView time = row.findViewById(R.id.tvMsgTime);

        LinearLayout.LayoutParams params = (LinearLayout.LayoutParams) bubble.getLayoutParams();
        params.gravity = mine ? Gravity.END : Gravity.START;
        bubble.setLayoutParams(params);
        bubble.setBackgroundResource(mine ? R.drawable.bg_bubble_mine : R.drawable.bg_bubble_other);

        text.setMaxWidth(maxTextWidth);
        sender.setMaxWidth(maxTextWidth);

        if (mine) {
            sender.setVisibility(View.GONE);
        } else {
            sender.setVisibility(View.VISIBLE);
            sender.setText(m.senderName);
        }

        int mainColor = context.getColor(mine ? android.R.color.white : R.color.text_primary);
        int mutedColor = mine ? 0xCCFFFFFF : context.getColor(R.color.text_secondary);
        if (m.deleted) {
            text.setText("Tin nhắn đã bị xóa");
            text.setTypeface(null, Typeface.ITALIC);
            text.setTextColor(mine ? 0xCCFFFFFF : context.getColor(R.color.text_secondary));
        } else {
            text.setText(m.content);
            text.setTypeface(null, Typeface.NORMAL);
            text.setTextColor(mainColor);
        }
        time.setText(formatTime(m.createdAt));
        time.setTextColor(mutedColor);
        return row;
    }

    /** Hôm nay: HH:mm. Ngày khác: dd/MM HH:mm. */
    private String formatTime(String createdAt) {
        if (createdAt == null || createdAt.length() < 16) return "";
        String today = TimeUtil.nowLocal().substring(0, 10);
        String hhmm = createdAt.substring(11, 16);
        if (createdAt.startsWith(today)) return hhmm;
        return createdAt.substring(8, 10) + "/" + createdAt.substring(5, 7) + " " + hhmm;
    }
}
