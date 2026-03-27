package com.example.dubaothoitiet;

import android.graphics.Color;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.recyclerview.widget.RecyclerView;

import org.json.JSONArray;
import org.json.JSONObject;

public class HourlyAdapter extends RecyclerView.Adapter<HourlyAdapter.ViewHolder> {
    JSONArray data;

    public HourlyAdapter(JSONArray data) { this.data = data; }

    @Override
    public ViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_hourly, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(ViewHolder holder, int position) {
        try {
            JSONObject item = data.getJSONObject(position);
            holder.tvGio.setText(item.getString("gio"));
            holder.tvNhietDo.setText(item.getDouble("nhiet") + "°"); // Đổi thành "nhiet" cho khớp Python

            // Nếu bạn muốn hiện cả tỷ lệ mưa và rủi ro ở đây:
            if(holder.tvTyLeMua != null) {
                holder.tvTyLeMua.setText("💧 " + item.getInt("ty_le_mua") + "%");
            }

            int ruiRo = item.getInt("rui_ro");
            if (holder.tvRuiRo != null) {
                if (ruiRo == 0) {
                    holder.tvRuiRo.setText("An Toàn");
                    holder.tvRuiRo.setTextColor(Color.GREEN);
                } else {
                    holder.tvRuiRo.setText("Nguy Hiểm");
                    holder.tvRuiRo.setTextColor(Color.RED);
                }
            }
        } catch (Exception e) { e.printStackTrace(); }
    }

    @Override
    public int getItemCount() { return data.length(); }

    class ViewHolder extends RecyclerView.ViewHolder {
        TextView tvGio, tvNhietDo, tvRuiRo,tvTyLeMua, tvGoiY;
        View cardBackground;

        public ViewHolder(View itemView) {
            super(itemView);
            tvGio = itemView.findViewById(R.id.tvGio);
            tvNhietDo = itemView.findViewById(R.id.tvNhietDo);
            tvRuiRo = itemView.findViewById(R.id.tvRuiRo);
            cardBackground = itemView.findViewById(R.id.cardBackground);
            tvTyLeMua = itemView.findViewById(R.id.tvTyLeMua);
            tvGoiY = itemView.findViewById(R.id.tvGoiY);
        }
    }
}
