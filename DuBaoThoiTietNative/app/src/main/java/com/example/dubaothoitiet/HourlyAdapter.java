package com.example.dubaothoitiet;

import android.graphics.Color;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;

import org.json.JSONArray;
import org.json.JSONObject;

public class HourlyAdapter extends RecyclerView.Adapter<HourlyAdapter.ViewHolder> {
    JSONArray data;

    public HourlyAdapter(JSONArray data) { this.data = data; }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_hourly, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        try {
            JSONObject item = data.getJSONObject(position);
            
            // Đổ dữ liệu giờ và nhiệt độ
            holder.tvGio.setText(item.optString("gio", "00:00"));
            holder.tvNhietDo.setText((int)item.optDouble("nhiet", 0) + "°");

            // Tải icon thời tiết theo giờ từ Server
            String iconCode = item.optString("icon", "01d");
            Glide.with(holder.itemView.getContext())
                 .load("https://openweathermap.org/img/wn/" + iconCode + "@2x.png")
                 .placeholder(android.R.drawable.ic_menu_report_image)
                 .into(holder.imgIconHourly);

            // Cập nhật các view khác nếu tồn tại và đang hiển thị (phục vụ mục đích dự phòng)
            if(holder.tvTyLeMua != null) {
                holder.tvTyLeMua.setText("💧 " + item.optInt("ty_le_mua", 0) + "%");
            }

            if(holder.tvGoiY != null) {
                holder.tvGoiY.setText(item.optString("goi_y", ""));
            }

        } catch (Exception e) { e.printStackTrace(); }
    }

    @Override
    public int getItemCount() { return data != null ? data.length() : 0; }

    static class ViewHolder extends RecyclerView.ViewHolder {
        TextView tvGio, tvNhietDo, tvTyLeMua, tvGoiY, tvRuiRo;
        ImageView imgIconHourly;
        View cardBackground;

        public ViewHolder(View itemView) {
            super(itemView);
            tvGio = itemView.findViewById(R.id.tvGio);
            tvNhietDo = itemView.findViewById(R.id.tvNhietDo);
            imgIconHourly = itemView.findViewById(R.id.imgIconHourly); // Thêm ánh xạ icon
            cardBackground = itemView.findViewById(R.id.cardBackground);
            
            // Các view ẩn trong XML mẫu mới
            tvTyLeMua = itemView.findViewById(R.id.tvTyLeMua);
            tvGoiY = itemView.findViewById(R.id.tvGoiY);
            tvRuiRo = itemView.findViewById(R.id.tvRuiRo);
        }
    }
}
