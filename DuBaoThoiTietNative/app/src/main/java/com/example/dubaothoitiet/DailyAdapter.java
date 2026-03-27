package com.example.dubaothoitiet;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.recyclerview.widget.RecyclerView;

import org.json.JSONArray;
import org.json.JSONObject;

public class DailyAdapter extends RecyclerView.Adapter<DailyAdapter.ViewHolder> {
    JSONArray data;
    public DailyAdapter(JSONArray data) { this.data = data; }

    @Override
    public ViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_daily, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(ViewHolder holder, int position) {
        try {
            JSONObject item = data.getJSONObject(position);
            holder.tvDayOfWeek.setText(item.getString("ngay"));
            holder.tvDailyTemp.setText(item.getDouble("min") + "° / " + item.getDouble("max") + "°");
        } catch (Exception e) { e.printStackTrace(); }
    }

    @Override
    public int getItemCount() { return data.length(); }

    class ViewHolder extends RecyclerView.ViewHolder {
        TextView tvDayOfWeek, tvDailyTemp;
        public ViewHolder(View itemView) {
            super(itemView);
            tvDayOfWeek = itemView.findViewById(R.id.tvDayOfWeek);
            tvDailyTemp = itemView.findViewById(R.id.tvDailyTemp);
        }
    }
}
