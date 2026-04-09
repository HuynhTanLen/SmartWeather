package com.example.dubaothoitiet;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import android.util.Log;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class WeatherDbHelper extends SQLiteOpenHelper {
    private static final String DATABASE_NAME = "weather.db";
    private static final int DATABASE_VERSION = 1;

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

    public void saveDailyWeather(List<DailyWeather> list) {
        if (list == null || list.isEmpty()) return;
        
        SQLiteDatabase db = this.getWritableDatabase();
        db.beginTransaction();
        try {
            // Để "Hôm nay" có ID cao nhất và hiện lên đầu khi dùng DESC, 
            // chúng ta sẽ chèn danh sách theo thứ tự ngược lại (Tương lai -> Hôm nay)
            for (int i = list.size() - 1; i >= 0; i--) {
                DailyWeather weather = list.get(i);
                ContentValues values = new ContentValues();
                values.put(COL_NGAY, weather.getNgay());
                values.put(COL_MAX, getDoubleFromText(weather.getTempMaxText()));
                values.put(COL_MIN, getDoubleFromText(weather.getTempMinText()));
                values.put(COL_DO_AM, getDoubleFromText(weather.getDoAmText()));
                values.put(COL_GIO, getDoubleFromText(weather.getSucGioText()));
                values.put(COL_ICON, weather.getIcon());

                db.insertWithOnConflict(TABLE_DAILY, null, values, SQLiteDatabase.CONFLICT_REPLACE);
            }
            db.setTransactionSuccessful();
        } finally {
            db.endTransaction();
            db.close();
        }
    }

    public List<DailyWeather> getAllDailyWeather() {
        List<DailyWeather> list = new ArrayList<>();
        SQLiteDatabase db = this.getReadableDatabase();
        // Sắp xếp ID DESC: Dữ liệu mới nhất (vừa tải) sẽ ở trên cùng
        Cursor cursor = db.query(TABLE_DAILY, null, null, null, null, null, COL_ID + " DESC");

        if (cursor != null && cursor.moveToFirst()) {
            do {
                list.add(new DailyWeather(
                        cursor.getString(cursor.getColumnIndexOrThrow(COL_NGAY)),
                        cursor.getDouble(cursor.getColumnIndexOrThrow(COL_MAX)),
                        cursor.getDouble(cursor.getColumnIndexOrThrow(COL_MIN)),
                        cursor.getDouble(cursor.getColumnIndexOrThrow(COL_DO_AM)),
                        cursor.getDouble(cursor.getColumnIndexOrThrow(COL_GIO)),
                        cursor.getString(cursor.getColumnIndexOrThrow(COL_ICON))
                ));
            } while (cursor.moveToNext());
        }
        if (cursor != null) cursor.close();
        db.close();
        return list;
    }

    private double getDoubleFromText(String text) {
        if (text == null) return 0;
        try {
            return Double.parseDouble(text.replaceAll("[^0-9.]", ""));
        } catch (Exception e) {
            return 0;
        }
    }
}
