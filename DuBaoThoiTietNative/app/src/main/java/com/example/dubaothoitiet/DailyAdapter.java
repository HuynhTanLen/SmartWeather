package com.example.dubaothoitiet;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import android.widget.ImageView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;

import java.util.List;

public class DailyAdapter extends RecyclerView.Adapter<DailyAdapter.ViewHolder> {

    private List<DailyWeather> dailyList;
    private boolean showAll = false; // Trạng thái xem thêm

    public DailyAdapter(List<DailyWeather> dailyList) {
        this.dailyList = dailyList;
    }

    public void setShowAll(boolean showAll) {
        this.showAll = showAll;
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_weather, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        DailyWeather day = dailyList.get(position);

        holder.tvDate.setText(day.getNgay());
        
        // Cập nhật Min/Max vào 2 ô riêng biệt (Khớp với item_weather.xml mới)
        if (holder.tvTempMin != null) holder.tvTempMin.setText(day.getTempMinText());
        if (holder.tvTempMax != null) holder.tvTempMax.setText(day.getTempMaxText());
        
        // Tải ảnh icon
        Glide.with(holder.itemView.getContext())
             .load(day.getIconUrl())
             .placeholder(android.R.drawable.ic_menu_report_image)
             .into(holder.imgIcon);
    }

    @Override
    public int getItemCount() {
        if (dailyList == null) return 0;
        // Nếu không showAll thì chỉ hiện 3 ngày đầu, ngược lại hiện hết
        if (!showAll && dailyList.size() > 3) {
            return 3;
        }
        return dailyList.size();
    }

    public static class ViewHolder extends RecyclerView.ViewHolder {
        TextView tvDate, tvTempMin, tvTempMax;
        ImageView imgIcon;

        public ViewHolder(@NonNull View itemView) {
            super(itemView);
            tvDate = itemView.findViewById(R.id.tvDate);
            tvTempMin = itemView.findViewById(R.id.tvTempMin);
            tvTempMax = itemView.findViewById(R.id.tvTempMax);
            imgIcon = itemView.findViewById(R.id.imgIcon);
        }
    }
}