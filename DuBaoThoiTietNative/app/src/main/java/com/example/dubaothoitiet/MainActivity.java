package com.example.dubaothoitiet;


import android.graphics.Color;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.IOException;

import okhttp3.*;

public class MainActivity extends AppCompatActivity {

    // Khai báo các View mới theo giao diện mới
    RecyclerView rvHourly, rvDaily;
    Button btnLoad;
    TextView tvCityName, tvSmartAdvice, tvDetailHumidity, tvDetailWind;

    // Link Server (Đảm bảo Python đã đổi tên endpoint thành /api/thoitiet_full)
    private static final String SERVER_URL = "https://sourish-petrina-saturnine.ngrok-free.dev/api/thoitiet_full";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        // Ánh xạ các View
        tvCityName = findViewById(R.id.tvCity);
        tvSmartAdvice = findViewById(R.id.tvMainAdvice);
        btnLoad = findViewById(R.id.btnRefresh); // ID mới trong XML là btnRefresh
        rvHourly = findViewById(R.id.rvHourly);
        rvDaily = findViewById(R.id.rvDaily);

        // Cấu hình LayoutManager
        rvHourly.setLayoutManager(new LinearLayoutManager(this, LinearLayoutManager.HORIZONTAL, false));
        rvDaily.setLayoutManager(new LinearLayoutManager(this, LinearLayoutManager.HORIZONTAL, false));

        btnLoad.setOnClickListener(v -> fetchDataFromServer()); //ffff
    }

    private void fetchDataFromServer() {
        OkHttpClient client = new OkHttpClient();

        Request request = new Request.Builder()
                .url(SERVER_URL)
                .addHeader("ngrok-skip-browser-warning", "true")
                .addHeader("User-Agent", "SkysWeatherApp/1.0")
                .get()
                .build();

        client.newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(@NonNull Call call, @NonNull IOException e) {
                runOnUiThread(() -> Toast.makeText(MainActivity.this, "Lỗi kết nối", Toast.LENGTH_SHORT).show());
            }

            @Override
            public void onResponse(@NonNull Call call, @NonNull Response response) throws IOException {
                if (response.isSuccessful()) {
                    String jsonString = response.body().string();
                    try {
                        JSONObject json = new JSONObject(jsonString);
                        String cityName = json.getString("thanh_pho");
                        JSONArray hourlyArray = json.getJSONArray("hourly");
                        JSONArray dailyArray = json.getJSONArray("daily");

                        runOnUiThread(() -> {
                            // 1. Hiển thị thông tin chính
                            tvCityName.setText(cityName);
                            try {
                                // Lấy gợi ý của giờ hiện tại cho thẻ AI
                                tvSmartAdvice.setText(hourlyArray.getJSONObject(0).getString("goi_y"));
                            } catch (Exception e) { e.printStackTrace(); }

                            // 2. Set Adapter cho 2 danh sách
                            rvHourly.setAdapter(new HourlyAdapter(hourlyArray));
                            rvDaily.setAdapter(new DailyAdapter(dailyArray));

                            Toast.makeText(MainActivity.this, "Đã cập nhật dự báo AI!", Toast.LENGTH_SHORT).show();
                        });
                    } catch (Exception e) { e.printStackTrace(); }
                }
            }
        });
    }
}