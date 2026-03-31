package com.example.dubaothoitiet;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;

import java.util.ArrayList;
import java.util.List;

public class WeatherDbHelper extends SQLiteOpenHelper {
    private static final String DATABASE_NAME = "weather.db";
    private static final int DATABASE_VERSION = 1;

    // Tên bảng và các cột
    public static final String TABLE_DAILY = "daily_weather";
    public static final String COL_ID = "id";
    public static final String COL_NGAY = "ngay";
    public static final String COL_MAX = "max_temp";
    public static final String COL_MIN = "min_temp";
    public static final String COL_DO_AM = "do_am";
    public static final String COL_GIO = "suc_gio";
    public static final String COL_ICON = "icon";

    private static final String CREATE_TABLE_DAILY = "CREATE TABLE " + TABLE_DAILY + " (" +
            COL_ID + " INTEGER PRIMARY KEY AUTOINCREMENT, " +
            COL_NGAY + " TEXT UNIQUE, " +
            COL_MAX + " REAL, " +
            COL_MIN + " REAL, " +
            COL_DO_AM + " REAL, " +
            COL_GIO + " REAL, " +
            COL_ICON + " TEXT)";

    public WeatherDbHelper(Context context) {
        super(context, DATABASE_NAME, null, DATABASE_VERSION);
    }

    @Override
    public void onCreate(SQLiteDatabase db) {
        db.execSQL(CREATE_TABLE_DAILY);
    }

    @Override
    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        db.execSQL("DROP TABLE IF EXISTS " + TABLE_DAILY);
        onCreate(db);
    }

    // Lưu danh sách thời tiết vào DB
    public void saveDailyWeather(List<DailyWeather> list) {
        SQLiteDatabase db = this.getWritableDatabase();
        // Có thể xóa dữ liệu cũ trước khi lưu mới hoặc dùng INSERT OR REPLACE
        for (DailyWeather weather : list) {
            ContentValues values = new ContentValues();
            values.put(COL_NGAY, weather.getNgay());
            values.put(COL_MAX, getDoubleValue(weather.getTempMaxText())); // Tạm thời parse từ string hoặc sửa DailyWeather
            values.put(COL_MIN, getDoubleValue(weather.getTempMinText()));
            values.put(COL_DO_AM, getDoubleValue(weather.getDoAmText()));
            values.put(COL_GIO, getDoubleValue(weather.getSucGioText()));
            
            // Tìm cách lấy mã icon gốc thay vì URL
            values.put(COL_ICON, weather.getIconUrl()); 

            db.insertWithOnConflict(TABLE_DAILY, null, values, SQLiteDatabase.CONFLICT_REPLACE);
        }
        db.close();
    }

    private double getDoubleValue(String text) {
        try {
            return Double.parseDouble(text.replaceAll("[^0-9.]", ""));
        } catch (Exception e) {
            return 0;
        }
    }
}
