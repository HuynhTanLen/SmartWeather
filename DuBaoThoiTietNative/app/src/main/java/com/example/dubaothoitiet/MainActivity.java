package com.example.dubaothoitiet;

import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;
import android.content.SharedPreferences;
import android.app.Dialog;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import java.text.SimpleDateFormat;
import java.util.Date;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;
import com.google.android.material.floatingactionbutton.ExtendedFloatingActionButton;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

public class MainActivity extends AppCompatActivity {

    private static final String TAG = "MainActivity";
    private static final String BASE_URL = "http://10.0.2.2:5000/api/thoitiet_full";
    private String currentCity = "Tan Uyen";

    private static final String[] TEMP_KEYS = {"nhiet", "temp", "temperature"};
    private static final String[] FEELS_LIKE_KEYS = {"cam_giac", "feels_like"};
    private static final String[] DESCRIPTION_KEYS = {"tinh_trang", "description", "weather_description"};
    private static final String[] ADVICE_KEYS = {"goi_y", "advice"};
    private static final String[] RAIN_KEYS = {"ty_le_mua", "pop", "rain_chance", "precipitation_probability"};
    private static final String[] HUMIDITY_KEYS = {"do_am", "humidity"};
    private static final String[] UV_KEYS = {"uv", "uvi", "uv_index", "chi_so_uv"};
    private static final String[] WIND_KEYS = {"toc_do_gio", "wind_speed", "wind", "wind_kph", "wind_km_h", "wind_kmh", "gio_km_h", "gio"};
    private static final String[] ICON_KEYS = {"icon", "weather_icon"};
    private static final String[] DAILY_MAX_KEYS = {"max_predicted", "max_temp", "temp_max", "max", "nhiet_max"};
    private static final String[] DAILY_MIN_KEYS = {"min_predicted", "min_temp", "temp_min", "min", "nhiet_min"};

    RecyclerView rvHourly, rvDaily;
    TextView tvCityName, tvCurrentTemp, tvWeatherDescription, tvMainAdvice;
    ImageView imgMainIcon, btnMenu, btnSearch;
    LinearLayout layoutCity;
    Button btnShowMore;
    ExtendedFloatingActionButton fabPlantTree;
    DailyAdapter dailyAdapter;
    boolean isExpanded = false;

    WeatherDbHelper dbHelper;

    // Chi tiet thong so
    View cardFeelsLike, cardWind, cardHumidity, cardRain, cardUV;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        dbHelper = new WeatherDbHelper(this);

        // Anh xa View chinh
        tvCityName = findViewById(R.id.tvCity);
        tvCurrentTemp = findViewById(R.id.tvCurrentTemp);
        tvWeatherDescription = findViewById(R.id.tvWeatherDescription);
        imgMainIcon = findViewById(R.id.imgMainIcon);
        tvMainAdvice = findViewById(R.id.tvMainAdvice);
        btnShowMore = findViewById(R.id.btnShowMore);
        fabPlantTree = findViewById(R.id.fabPlantTree);

        layoutCity = findViewById(R.id.layoutCity);
        rvHourly = findViewById(R.id.rvHourly);
        rvDaily = findViewById(R.id.rvDaily);

        // Anh xa Cards chi tiet
        cardFeelsLike = findViewById(R.id.cardFeelsLike);
        cardWind = findViewById(R.id.cardWind);
        cardHumidity = findViewById(R.id.cardHumidity);
        cardRain = findViewById(R.id.cardRain);
        cardUV = findViewById(R.id.cardUV);

        rvHourly.setLayoutManager(new LinearLayoutManager(this, LinearLayoutManager.HORIZONTAL, false));
        rvDaily.setLayoutManager(new LinearLayoutManager(this, LinearLayoutManager.VERTICAL, false));

        loadDailyDataFromDb();

        if (layoutCity != null) {
            layoutCity.setOnClickListener(v -> showCitySelectionDialog());
        }

        btnSearch = findViewById(R.id.btnSearch);
        if (btnSearch != null) {
            btnSearch.setOnClickListener(v -> showCitySelectionDialog());
        }

        if (btnShowMore != null) {
            btnShowMore.setOnClickListener(v -> {
                isExpanded = !isExpanded;
                if (dailyAdapter != null) {
                    dailyAdapter.setShowAll(isExpanded);
                    btnShowMore.setText(isExpanded ? "Thu gon" : "Xem them");
                }
            });
        }

        if (fabPlantTree != null) {
            fabPlantTree.setOnClickListener(v -> showTreeNagDialog(false));
        }

        fetchDataFromServer();
    }

    @Override
    protected void onResume() {
        super.onResume();
        checkTreePlantingStatus();
    }

    private void showCitySelectionDialog() {
        String[] cities = {"Tan Uyen", "Ho Chi Minh", "Ha Noi", "Da Nang"};
        new AlertDialog.Builder(this)
            .setTitle("Chọn thành phố")
            .setItems(cities, (dialog, which) -> {
                currentCity = cities[which];
                tvCityName.setText("Đang tải...");
                fetchDataFromServer();
            })
            .show();
    }

    private void checkTreePlantingStatus() {
        SharedPreferences prefs = getSharedPreferences("AppPrefs", MODE_PRIVATE);
        String lastPlantDate = prefs.getString("LAST_PLANT_DATE", "");
        String todayDate = new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(new Date());

        if (!todayDate.equals(lastPlantDate)) {
            // Chua trong hnay, random hien
            int nagCount = prefs.getInt("NAG_COUNT", 0);
            nagCount++;
            prefs.edit().putInt("NAG_COUNT", nagCount).apply();
            
            showTreeNagDialog(true);
        }
    }

    private void showTreeNagDialog(boolean isAutoNag) {
        Dialog dialog = new Dialog(this);
        dialog.setContentView(R.layout.dialog_achievement_tree);
        if (dialog.getWindow() != null) {
            dialog.getWindow().setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
            dialog.getWindow().setLayout(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        }

        View container = dialog.findViewById(R.id.dialogContainer);
        Button btnConfirm = dialog.findViewById(R.id.btnConfirmPlantDialog);
        Button btnLater = dialog.findViewById(R.id.btnLater);
        TextView tvDesc = dialog.findViewById(R.id.tvDialogDesc);

        SharedPreferences prefs = getSharedPreferences("AppPrefs", MODE_PRIVATE);
        int nagCount = prefs.getInt("NAG_COUNT", 0);

        if (isAutoNag && nagCount > 1) {
            // Scale container len toi da 1.5x
            float scale = 1.0f + (nagCount * 0.1f);
            if (scale > 1.5f) scale = 1.5f;
            container.setScaleX(scale);
            container.setScaleY(scale);
            tvDesc.setText("Đây là lần thứ " + nagCount + " bạn mở app mà chưa trồng cây hôm nay. Mọi người đều đang cố gắng, còn bạn thì sao?");
        } else if (!isAutoNag){
            tvDesc.setText("Thực hiện hành động nhỏ, mang lại thay đổi lớn. Bạn đã sẵn sàng trồng cây?");
        }

        btnConfirm.setOnClickListener(v -> {
            String todayDate = new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(new Date());
            prefs.edit()
                .putString("LAST_PLANT_DATE", todayDate)
                .putInt("NAG_COUNT", 0)
                .apply();
            
            Toast.makeText(this, "🎉 Chúc mừng! Bạn đã nhận Thành tựu Cây Xanh trong ngày!", Toast.LENGTH_LONG).show();
            dialog.dismiss();
        });

        btnLater.setOnClickListener(v -> dialog.dismiss());
        dialog.show();
    }

    private void updateDetailCard(View card, String title, String value, String subtext, int iconRes) {
        if (card == null) {
            return;
        }

        TextView tvTitle = card.findViewById(R.id.tvDetailTitle);
        TextView tvValue = card.findViewById(R.id.tvDetailValue);
        TextView tvSub = card.findViewById(R.id.tvDetailSubText);
        ImageView imgIcon = card.findViewById(R.id.imgDetailIcon);

        if (tvTitle != null) {
            tvTitle.setText(title);
        }
        if (tvValue != null) {
            tvValue.setText(value);
        }
        if (tvSub != null) {
            tvSub.setText(subtext);
        }
        if (imgIcon != null) {
            imgIcon.setImageResource(iconRes);
        }
    }

    private JSONObject pickCurrentWeather(JSONObject rootJson, JSONArray hourlyArray) throws JSONException {
        JSONObject currentObject = rootJson.optJSONObject("current");
        if (currentObject != null) {
            return currentObject;
        }

        JSONObject currentWeatherObject = rootJson.optJSONObject("current_weather");
        if (currentWeatherObject != null) {
            return currentWeatherObject;
        }

        if (hourlyArray != null && hourlyArray.length() > 0) {
            return hourlyArray.getJSONObject(0);
        }
        return new JSONObject();
    }

    private String findString(JSONObject primary, JSONObject fallback, String defaultValue, String... keys) {
        for (String key : keys) {
            if (primary != null && primary.has(key) && !primary.isNull(key)) {
                Object value = primary.opt(key);
                if (value != null) {
                    return String.valueOf(value);
                }
            }
            if (fallback != null && fallback.has(key) && !fallback.isNull(key)) {
                Object value = fallback.opt(key);
                if (value != null) {
                    return String.valueOf(value);
                }
            }
        }
        return defaultValue;
    }

    private double findDouble(JSONObject primary, JSONObject fallback, double defaultValue, String... keys) {
        for (String key : keys) {
            Double primaryValue = readDouble(primary, key);
            if (primaryValue != null) {
                return primaryValue;
            }

            Double fallbackValue = readDouble(fallback, key);
            if (fallbackValue != null) {
                return fallbackValue;
            }
        }
        return defaultValue;
    }

    private Double readDouble(JSONObject object, String key) {
        if (object == null || !object.has(key) || object.isNull(key)) {
            return null;
        }

        Object value = object.opt(key);
        if (value instanceof Number) {
            return ((Number) value).doubleValue();
        }

        if (value instanceof String) {
            String raw = ((String) value).trim();
            if (raw.isEmpty() || raw.contains(":")) {
                return null;
            }

            raw = raw.replace("km/h", "").replace("m/s", "").trim();
            try {
                return Double.parseDouble(raw);
            } catch (NumberFormatException ignored) {
                return null;
            }
        }
        return null;
    }

    private String formatWeatherValue(double value) {
        if (value == Math.rint(value)) {
            return String.valueOf((int) value);
        }
        return String.format(Locale.US, "%.1f", value);
    }

    private double findDailyDouble(JSONObject item, double defaultValue, String... keys) {
        return findDouble(item, null, defaultValue, keys);
    }


    private void loadDailyDataFromDb() {
        List<DailyWeather> savedList = dbHelper.getAllDailyWeather();
        if (savedList != null) {
            dailyAdapter = new DailyAdapter(savedList);
            dailyAdapter.setShowAll(isExpanded);
            rvDaily.setAdapter(dailyAdapter);
        }
    }

    private void fetchDataFromServer() {
        String encodedCity = currentCity.replace(" ", "%20");
        String finalUrl = BASE_URL + "?city=" + encodedCity;

        OkHttpClient client = new OkHttpClient();
        Request request = new Request.Builder()
                .url(finalUrl)
                .addHeader("ngrok-skip-browser-warning", "true")
                .get()
                .build();

        client.newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(@NonNull Call call, @NonNull IOException e) {
                runOnUiThread(() -> Toast.makeText(MainActivity.this, "Loi ket noi", Toast.LENGTH_SHORT).show());
            }

            @Override
            public void onResponse(@NonNull Call call, @NonNull Response response) throws IOException {
                if (response.isSuccessful() && response.body() != null) {
                    String jsonString = response.body().string();
                    try {
                        JSONObject json = new JSONObject(jsonString);
                        String cityName = json.optString("thanh_pho", "Khong xac dinh");
                        JSONArray dailyArray = json.getJSONArray("daily");
                        JSONArray hourlyArray = json.optJSONArray("hourly");
                        JSONObject current = pickCurrentWeather(json, hourlyArray);

                        Log.d(TAG, "Weather response: " + jsonString);
                        Log.d(TAG, "Current payload: " + current);

                        runOnUiThread(() -> {
                            tvCityName.setText(cityName);
                            try {
                                if (hourlyArray != null && hourlyArray.length() > 0) {
                                    double currentTemp = findDouble(current, json, 0, TEMP_KEYS);
                                    double feelsLike = findDouble(current, json, currentTemp, FEELS_LIKE_KEYS);
                                    double windSpeed = findDouble(current, json, 0, WIND_KEYS);
                                    double uvIndex = findDouble(current, json, 0, UV_KEYS);
                                    int rainChance = (int) findDouble(current, json, 0, RAIN_KEYS);
                                    int humidity = (int) findDouble(current, json, 0, HUMIDITY_KEYS);

                                    tvCurrentTemp.setText((int) currentTemp + "°");
                                    tvWeatherDescription.setText(findString(current, json, "Trong xanh", DESCRIPTION_KEYS));
                                    tvMainAdvice.setText("Goi y: " + findString(current, json, "Thoi tiet on dinh.", ADVICE_KEYS));

                                    updateDetailCard(cardFeelsLike, "CAM GIAC NHU", (int) feelsLike + "°", "Cam nhan thuc te", android.R.drawable.ic_menu_info_details);
                                    updateDetailCard(cardWind, "TOC DO GIO", formatWeatherValue(windSpeed) + " km/h", "Thong tin gio hien tai", android.R.drawable.ic_menu_directions);
                                    updateDetailCard(cardHumidity, "DO AM", humidity + "%", "Do am khong khi", android.R.drawable.ic_menu_agenda);
                                    updateDetailCard(cardRain, "KHA NANG MUA", rainChance + "%", "Xac suat mua", android.R.drawable.ic_menu_report_image);
                                    updateDetailCard(cardUV, "CHI SO UV", formatWeatherValue(uvIndex), "Muc do tia cuc tim", android.R.drawable.ic_menu_compass);

                                    String iconCode = findString(current, json, "01d", ICON_KEYS);
                                    Glide.with(MainActivity.this)
                                            .load("https://openweathermap.org/img/wn/" + iconCode + "@4x.png")
                                            .into(imgMainIcon);
                                    rvHourly.setAdapter(new HourlyAdapter(hourlyArray));
                                }
                            } catch (Exception e) {
                                e.printStackTrace();
                            }
                        });

                        List<DailyWeather> newList = new ArrayList<>();
                        for (int i = 0; i < dailyArray.length(); i++) {
                            JSONObject item = dailyArray.getJSONObject(i);
                            double maxTemp = findDailyDouble(item, 0, DAILY_MAX_KEYS);
                            double minTemp = findDailyDouble(item, 0, DAILY_MIN_KEYS);
                            String dayIcon = findString(item, null, "01d", ICON_KEYS);
                            newList.add(new DailyWeather(item.getString("ngay"), maxTemp, minTemp, 0, 0, dayIcon));
                        }
                        dbHelper.saveDailyWeather(newList);
                        runOnUiThread(() -> loadDailyDataFromDb());
                    } catch (Exception e) {
                        Log.e("JSON_ERROR", e.getMessage());
                    }
                }
            }
        });
    }
}
