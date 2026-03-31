package com.example.dubaothoitiet;

import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import okhttp3.*;

public class MainActivity extends AppCompatActivity {

    RecyclerView rvHourly, rvDaily;
    TextView tvCityName, tvCurrentTemp, tvWeatherDescription, tvMainAdvice;
    ImageView imgMainIcon, btnMenu, btnSearch;
    LinearLayout layoutCity;
    Button btnShowMore;
    DailyAdapter dailyAdapter;
    boolean isExpanded = false;
    
    // Database Helper
    WeatherDbHelper dbHelper;

    View cardFeelsLike, cardWind, cardHumidity, cardRain;

    private static final String SERVER_URL = "https://sourish-petrina-saturnine.ngrok-free.dev/api/thoitiet_full";
    private final Handler autoUpdateHandler = new Handler(Looper.getMainLooper());
    private final Runnable autoUpdateRunnable = new Runnable() {
        @Override
        public void run() {
            fetchDataFromServer();
            autoUpdateHandler.postDelayed(this, 10 * 60 * 1000);
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        // Khởi tạo Database
        dbHelper = new WeatherDbHelper(this);

        tvCityName = findViewById(R.id.tvCity);
        tvCurrentTemp = findViewById(R.id.tvCurrentTemp);
        tvWeatherDescription = findViewById(R.id.tvWeatherDescription);
        imgMainIcon = findViewById(R.id.imgMainIcon);
        tvMainAdvice = findViewById(R.id.tvMainAdvice);
        btnShowMore = findViewById(R.id.btnShowMore);
        
        btnMenu = findViewById(R.id.btnMenu);
        btnSearch = findViewById(R.id.btnSearch);
        layoutCity = findViewById(R.id.layoutCity);
        
        rvHourly = findViewById(R.id.rvHourly);
        rvDaily = findViewById(R.id.rvDaily);

        cardFeelsLike = findViewById(R.id.cardFeelsLike);
        cardWind = findViewById(R.id.cardWind);
        cardHumidity = findViewById(R.id.cardHumidity);
        cardRain = findViewById(R.id.cardRain);

        rvHourly.setLayoutManager(new LinearLayoutManager(this, LinearLayoutManager.HORIZONTAL, false));
        rvDaily.setLayoutManager(new LinearLayoutManager(this, LinearLayoutManager.VERTICAL, false));

        if (btnMenu != null) {
            btnMenu.setOnClickListener(v -> Toast.makeText(this, "Menu đang được phát triển", Toast.LENGTH_SHORT).show());
        }
        
        if (btnSearch != null) {
            btnSearch.setOnClickListener(v -> Toast.makeText(this, "Tìm kiếm đang được phát triển", Toast.LENGTH_SHORT).show());
        }
        
        if (layoutCity != null) {
            layoutCity.setOnClickListener(v -> {
                Toast.makeText(this, "Đang cập nhật...", Toast.LENGTH_SHORT).show();
                fetchDataFromServer();
            });
        }

        if (btnShowMore != null) {
            btnShowMore.setOnClickListener(v -> {
                isExpanded = !isExpanded;
                if (dailyAdapter != null) {
                    dailyAdapter.setShowAll(isExpanded);
                    btnShowMore.setText(isExpanded ? "Thu gọn" : "Xem thêm");
                }
            });
        }

        fetchDataFromServer();
        autoUpdateHandler.postDelayed(autoUpdateRunnable, 10 * 60 * 1000);
    }

    private void updateDetailCard(View card, String title, String value, String subtext, int iconRes) {
        if (card == null) return;
        TextView tvTitle = card.findViewById(R.id.tvDetailTitle);
        TextView tvValue = card.findViewById(R.id.tvDetailValue);
        TextView tvSub = card.findViewById(R.id.tvDetailSubText);
        ImageView imgIcon = card.findViewById(R.id.imgDetailIcon);

        if (tvTitle != null) tvTitle.setText(title);
        if (tvValue != null) tvValue.setText(value);
        if (tvSub != null) tvSub.setText(subtext);
        if (imgIcon != null) imgIcon.setImageResource(iconRes);
    }

    private void fetchDataFromServer() {
        OkHttpClient client = new OkHttpClient();
        Request request = new Request.Builder()
                .url(SERVER_URL)
                .addHeader("ngrok-skip-browser-warning", "true")
                .get()
                .build();

        client.newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(@NonNull Call call, @NonNull IOException e) {
                runOnUiThread(() -> Toast.makeText(MainActivity.this, "Lỗi kết nối", Toast.LENGTH_SHORT).show());
            }

            @Override
            public void onResponse(@NonNull Call call, @NonNull Response response) throws IOException {
                if (response.isSuccessful() && response.body() != null) {
                    String jsonString = response.body().string();
                    try {
                        JSONObject json = new JSONObject(jsonString);
                        String cityName = json.getString("thanh_pho");
                        JSONArray hourlyArray = json.getJSONArray("hourly");
                        JSONArray dailyArray = json.getJSONArray("daily");

                        JSONObject current = hourlyArray.getJSONObject(0);
                        String currentTemp = (int)current.getDouble("nhiet") + "°";
                        String description = current.optString("tinh_trang", "Ít mây");
                        String iconCode = current.optString("icon", "01d");
                        
                        String feelsLike = (int)current.optDouble("cam_giac", current.getDouble("nhiet")) + "°";
                        String windSpeed = current.optDouble("gio", 12.0) + " km/h";
                        String humidity = current.optInt("do_am", 65) + "%";
                        String rainChance = current.optInt("ty_le_mua", 0) + "%";

                        List<DailyWeather> listDaily = new ArrayList<>();
                        for (int i = 0; i < dailyArray.length(); i++) {
                            JSONObject item = dailyArray.getJSONObject(i);
                            listDaily.add(new DailyWeather(
                                    item.getString("ngay"),
                                    item.getDouble("max"),
                                    item.getDouble("min"),
                                    item.getDouble("mua_sum"),
                                    item.getDouble("gio_max"),
                                    item.optString("icon", "01d")
                            ));
                        }
                        
                        // --- LƯU VÀO SQLITE ---
                        dbHelper.saveDailyWeather(listDaily);

                        runOnUiThread(() -> {
                            tvCityName.setText(cityName);
                            tvCurrentTemp.setText(currentTemp);
                            tvWeatherDescription.setText(description);
                            tvMainAdvice.setText("🤖 Gợi ý: " + current.optString("goi_y", "Trời hôm nay rất đẹp!"));

                            updateDetailCard(cardFeelsLike, "CẢM GIÁC NHƯ", feelsLike, "Nhiệt độ cảm nhận thực tế", android.R.drawable.ic_menu_info_details);
                            updateDetailCard(cardWind, "GIÓ", windSpeed, "Tốc độ gió hiện tại", android.R.drawable.ic_menu_directions);
                            updateDetailCard(cardHumidity, "ĐỘ ẨM", humidity, "Lượng hơi nước trong không khí", android.R.drawable.ic_menu_agenda);
                            updateDetailCard(cardRain, "TỶ LỆ MƯA", rainChance, "Khả năng xảy ra mưa", android.R.drawable.ic_menu_report_image);

                            if (imgMainIcon != null) {
                                imgMainIcon.setVisibility(View.VISIBLE);
                                Glide.with(MainActivity.this)
                                     .load("https://openweathermap.org/img/wn/" + iconCode + "@4x.png")
                                     .into(imgMainIcon);
                            }

                            rvHourly.setAdapter(new HourlyAdapter(hourlyArray));
                            
                            dailyAdapter = new DailyAdapter(listDaily);
                            dailyAdapter.setShowAll(isExpanded);
                            rvDaily.setAdapter(dailyAdapter);
                        });
                    } catch (Exception e) { e.printStackTrace(); }
                }
            }
        });
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        autoUpdateHandler.removeCallbacks(autoUpdateRunnable);
    }
}