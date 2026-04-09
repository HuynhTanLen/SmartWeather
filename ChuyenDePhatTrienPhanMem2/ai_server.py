import datetime
from pathlib import Path

import joblib
import numpy as np
import pandas as pd
import requests
from flask import Flask, jsonify, request
from requests.exceptions import RequestException
from sklearn.ensemble import RandomForestClassifier
from sklearn.model_selection import GridSearchCV, train_test_split
from sklearn.preprocessing import StandardScaler

try:
    from tensorflow.keras.models import load_model
except ModuleNotFoundError:
    load_model = None

DATA_PATH = Path("df_weather_hourly.csv")
MODEL_PATH = Path("weather_model.pkl")
SCALER_PATH = Path("weather_scaler.pkl")
LSTM_MODEL_PATH = Path("weather_lstm.h5")
LSTM_SCALER_PATH = Path("scaler_lstm.pkl")

FEATURE_COLUMNS = ["Nhiet_Do", "Do_Am", "Gio_kph", "Mua_mm", "Chi_So_UV"]
EXPECTED_COLUMNS = ["Tinh_Thanh", "Ngay_Thang", *FEATURE_COLUMNS]

app = Flask(__name__)

DEFAULT_CITY = "Tan Uyen"
DEFAULT_LAT = 11.03
DEFAULT_LON = 106.80

best_rf = None
scaler = None
lstm_model = None
lstm_scaler = None


def safe_print(*args, **kwargs):
    try:
        print(*args, **kwargs)
    except UnicodeEncodeError:
        sanitized = [str(arg).encode("ascii", "ignore").decode("ascii") for arg in args]
        print(*sanitized, **kwargs)


def load_weather_dataframe(csv_path: Path = DATA_PATH) -> pd.DataFrame:
    if not csv_path.exists():
        raise FileNotFoundError(f"Khong tim thay file '{csv_path}'.")

    df = pd.read_csv(csv_path)
    if "location.name" in df.columns:
        df = df[
            [
                "location.name",
                "date",
                "day.maxtemp_c",
                "day.maxwind_kph",
                "day.totalprecip_mm",
                "day.avghumidity",
                "day.uv",
            ]
        ].rename(
            columns={
                "location.name": "Tinh_Thanh",
                "date": "Ngay_Thang",
                "day.maxtemp_c": "Nhiet_Do",
                "day.maxwind_kph": "Gio_kph",
                "day.totalprecip_mm": "Mua_mm",
                "day.avghumidity": "Do_Am",
                "day.uv": "Chi_So_UV",
            }
        )

    missing_columns = [column for column in EXPECTED_COLUMNS if column not in df.columns]
    if missing_columns:
        raise ValueError(f"CSV thieu cot bat buoc: {missing_columns}")

    df = df[EXPECTED_COLUMNS].copy()
    df["Ngay_Thang"] = pd.to_datetime(df["Ngay_Thang"], errors="coerce")

    for column in FEATURE_COLUMNS:
        df[column] = pd.to_numeric(df[column], errors="coerce")

    df = df.dropna(subset=["Ngay_Thang"])
    df = df.sort_values("Ngay_Thang").reset_index(drop=True)

    if df[FEATURE_COLUMNS].isnull().any().any():
        df[FEATURE_COLUMNS] = df[FEATURE_COLUMNS].interpolate(method="linear", limit_direction="both")
        df[FEATURE_COLUMNS] = df[FEATURE_COLUMNS].ffill().bfill()

    return df


def danh_gia_suc_khoe(row):
    nhiet, am, gio, mua, uv = row["Nhiet_Do"], row["Do_Am"], row["Gio_kph"], row["Mua_mm"], row["Chi_So_UV"]
    if (nhiet >= 35 and am >= 70) or (mua >= 80) or (uv >= 8) or (gio >= 60):
        return 2
    if (nhiet >= 32) or (mua >= 20) or (uv >= 6) or (gio >= 40):
        return 1
    return 0


def tinh_ty_le_mua(nhiet, am, gio):
    ty_le = 0
    if am > 90:
        ty_le += 60
    elif am > 80:
        ty_le += 40
    elif am > 70:
        ty_le += 20
    if nhiet >= 32 and am >= 75:
        ty_le += 20
    if gio >= 25 and am >= 75:
        ty_le += 15
    return min(ty_le, 100)


def goi_y_hoat_dong(nhiet, am, gio, ty_le_mua, uv, muc):
    if muc == 2:
        if ty_le_mua >= 80:
            return f"Kha nang mua {ty_le_mua}%: Tuyet doi khong ra ngoai"
        if uv >= 8:
            return "UV rat cao: Tranh nang hoan toan"
        if nhiet >= 35 and am >= 70:
            return "Nong am nguy hiem: O trong nha, tranh mat nuoc"
        if gio >= 60:
            return "Gio giat manh: Nguy hiem khi di chuyen"
        return "Thoi tiet nguy hiem: Nen o trong nha"

    if muc == 1:
        if ty_le_mua >= 50:
            return f"Ti le mua {ty_le_mua}%: Co the mua, nho mang o hoac ao mua"
        if uv >= 6:
            return "UV cao: Ra ngoai can boi kem chong nang"
        if nhiet >= 32:
            return "Troi oi buc: Han che hoat dong ngoai troi"
        if gio >= 40:
            return "Gio manh: Can than khi lai xe"
        return "Thoi tiet thay doi: Can nhac cac hoat dong nhe"

    if 25 <= nhiet <= 32 and ty_le_mua < 20 and gio < 20:
        return "Thoi tiet dep: Rat thich hop di dao ngoai hoac cafe"
    if nhiet > 30 and ty_le_mua < 10:
        return "Troi nang rao: Thich hop di boi hoac o trong nha mat"
    if uv >= 5 and am < 60:
        return "Nang am: Co tro the pho nang som"
    return "Thoi tiet on dinh: Co the di dao hoac van dong nhe"


def train_or_load_classifier(force_retrain: bool = False):
    global best_rf, scaler, lstm_model, lstm_scaler

    if not force_retrain and MODEL_PATH.exists() and SCALER_PATH.exists():
        best_rf = joblib.load(MODEL_PATH)
        scaler = joblib.load(SCALER_PATH)
    else:
        safe_print("[1/3] Dang xu ly va huan luyen model phan loai...")
        df = load_weather_dataframe()
        df["Nhan_Canh_Bao"] = df.apply(danh_gia_suc_khoe, axis=1)
        X = df[FEATURE_COLUMNS]
        y = df["Nhan_Canh_Bao"]
        X_train, _, y_train, _ = train_test_split(X, y, test_size=0.2, random_state=42)

        scaler = StandardScaler()
        X_train_scaled = scaler.fit_transform(X_train)

        rf = GridSearchCV(
            RandomForestClassifier(random_state=42),
            {"n_estimators": [50, 100]},
            cv=3,
            n_jobs=-1,
        ).fit(X_train_scaled, y_train)
        best_rf = rf.best_estimator_

        joblib.dump(best_rf, MODEL_PATH)
        joblib.dump(scaler, SCALER_PATH)

    if load_model is None:
        safe_print("[2/3] TensorFlow chua duoc cai. Se dung du lieu forecast truc tiep thay cho LSTM.")
        lstm_model = None
        lstm_scaler = None
    elif LSTM_MODEL_PATH.exists() and LSTM_SCALER_PATH.exists():
        safe_print("[2/3] Dang load mo hinh LSTM...")
        lstm_model = load_model(LSTM_MODEL_PATH, compile=False)
        lstm_scaler = joblib.load(LSTM_SCALER_PATH)
    else:
        safe_print("[2/3] Khong tim thay file LSTM. Se dung du lieu forecast truc tiep.")
        lstm_model = None
        lstm_scaler = None


def load_resources():
    train_or_load_classifier(force_retrain=False)


def ensure_classifier_loaded():
    global best_rf, scaler
    if best_rf is None or scaler is None:
        load_resources()
    if best_rf is None or scaler is None:
        raise RuntimeError("Khong the tai model phan loai Random Forest.")


def get_series(payload: dict, *keys: str):
    for key in keys:
        if key in payload:
            return payload[key]
    raise KeyError(f"Khong tim thay truong du lieu nao trong cac lua chon: {keys}")


def parse_requested_datetime(start_date: str | None, start_hour: str | None) -> datetime.datetime:
    tz_now = datetime.datetime.now(datetime.timezone(datetime.timedelta(hours=7))).replace(
        minute=0,
        second=0,
        microsecond=0,
        tzinfo=None,
    )
    if not start_date:
        return tz_now

    parsed_date = None
    for fmt in ("%d/%m/%Y", "%d/%m", "%Y-%m-%d"):
        try:
            parsed_date = datetime.datetime.strptime(start_date, fmt)
            if fmt == "%d/%m":
                parsed_date = parsed_date.replace(year=tz_now.year)
            break
        except ValueError:
            continue

    if parsed_date is None:
        raise ValueError("start_date khong hop le. Dung dd/mm, dd/mm/yyyy hoac yyyy-mm-dd.")

    if start_hour:
        try:
            hour = int(start_hour)
        except ValueError as exc:
            raise ValueError("start_hour phai la so nguyen tu 0 den 23.") from exc
        if hour < 0 or hour > 23:
            raise ValueError("start_hour phai trong khoang 0-23.")
    else:
        hour = 0

    return parsed_date.replace(hour=hour, minute=0, second=0, microsecond=0)


def parse_float_arg(value: str | None, default: float, name: str) -> float:
    if value is None or value == "":
        return default
    try:
        return float(value)
    except ValueError as exc:
        raise ValueError(f"{name} phai la so hop le.") from exc


def score_conditions(temp, humidity, wind, rain, uv):
    rf_input = pd.DataFrame([[temp, humidity, wind, rain, uv]], columns=FEATURE_COLUMNS)
    scaled_input = scaler.transform(rf_input)
    nhan = int(best_rf.predict(scaled_input)[0])
    prob = int(tinh_ty_le_mua(temp, humidity, wind))
    return nhan, prob


def build_daily_humidity_lookup(hourly: dict) -> dict[str, float]:
    times = hourly.get("time", [])
    humidity_series = get_series(hourly, "relative_humidity_2m")
    humidity_by_day: dict[str, list[float]] = {}

    for idx, time_str in enumerate(times):
        day_key = time_str[:10]
        humidity_by_day.setdefault(day_key, []).append(float(humidity_series[idx] or 0))

    return {
        day_key: round(sum(values) / len(values), 1)
        for day_key, values in humidity_by_day.items()
        if values
    }


def build_hourly_from_api(hourly, now):
    times = hourly.get("time", [])
    temperature_series = get_series(hourly, "temperature_2m")
    humidity_series = get_series(hourly, "relative_humidity_2m")
    wind_series = get_series(hourly, "windspeed_10m", "wind_speed_10m")
    precipitation_series = get_series(hourly, "precipitation")
    uv_series = get_series(hourly, "uv_index")

    start_idx = 0
    current_key = now.strftime("%Y-%m-%dT%H")
    for idx, t_str in enumerate(times):
        if t_str.startswith(current_key):
            start_idx = idx
            break

    end_idx = min(start_idx + 24, len(times))
    hourly_data = []
    for idx in range(start_idx, end_idx):
        t = float(temperature_series[idx] or 0)
        h = float(humidity_series[idx] or 0)
        w = float(wind_series[idx] or 0)
        p = max(0.0, float(precipitation_series[idx] or 0))
        u = max(0.0, float(uv_series[idx] or 0))
        nhan, prob = score_conditions(t, h, w, p, u)
        hourly_data.append(
            {
                "gio": datetime.datetime.strptime(times[idx], "%Y-%m-%dT%H:%M").strftime("%H:00 %d/%m"),
                "nhiet": round(t, 1),
                "do_am": round(h, 1),
                "gio_kph": round(w, 1),
                "mua_mm": round(p, 1),
                "chi_so_uv": round(u, 1),
                "rui_ro": nhan,
                "ty_le_mua": prob,
                "goi_y": goi_y_hoat_dong(t, h, w, prob, u, nhan),
                "source": "Open-Meteo Forecast",
            }
        )
    return hourly_data


def build_hourly_from_lstm(hourly, now):
    times = hourly.get("time", [])
    if len(times) < 24:
        raise ValueError("Du lieu hourly khong du 24 moc thoi gian de chay LSTM.")

    temperature_series = get_series(hourly, "temperature_2m")
    humidity_series = get_series(hourly, "relative_humidity_2m")
    wind_series = get_series(hourly, "windspeed_10m", "wind_speed_10m")
    precipitation_series = get_series(hourly, "precipitation")
    uv_series = get_series(hourly, "uv_index")

    current_key = now.strftime("%Y-%m-%dT%H")
    current_idx = -1
    for idx, t_str in enumerate(times):
        if t_str.startswith(current_key):
            current_idx = idx
            break
    if current_idx == -1:
        current_idx = len(times)
    if current_idx < 24:
        raise ValueError("Khong du 24 gio lich su truoc moc bat dau de chay LSTM.")
    current_idx = min(current_idx, len(times))

    past_24h_data = []
    for idx in range(current_idx - 24, current_idx):
        past_24h_data.append(
            [
                float(temperature_series[idx] or 0),
                float(humidity_series[idx] or 0),
                float(wind_series[idx] or 0),
                float(precipitation_series[idx] or 0),
                float(uv_series[idx] or 0),
            ]
        )

    current_window_scaled = lstm_scaler.transform(np.array(past_24h_data, dtype=float))
    hourly_data = []
    for step in range(24):
        input_3d = current_window_scaled.reshape(1, 24, 5)
        next_hour_scaled = lstm_model.predict(input_3d, verbose=0)[0]
        current_window_scaled = np.append(current_window_scaled[1:], [next_hour_scaled], axis=0)

        next_hour_real = lstm_scaler.inverse_transform([next_hour_scaled])[0]
        t, h, w, p, u = [float(value) for value in next_hour_real]
        p = max(0.0, p)
        u = max(0.0, u)
        nhan, prob = score_conditions(t, h, w, p, u)
        future_time = now + datetime.timedelta(hours=step)
        hourly_data.append(
            {
                "gio": future_time.strftime("%H:00 %d/%m"),
                "nhiet": round(t, 1),
                "do_am": round(h, 1),
                "gio_kph": round(w, 1),
                "mua_mm": round(p, 1),
                "chi_so_uv": round(u, 1),
                "rui_ro": nhan,
                "ty_le_mua": prob,
                "goi_y": goi_y_hoat_dong(t, h, w, prob, u, nhan),
                "source": "LSTM Predicted",
            }
        )
    return hourly_data


def build_forecast_payload(response_json, start_at: datetime.datetime, city_name: str):
    ensure_classifier_loaded()
    hourly = response_json["hourly"]
    daily = response_json.get("daily", {})
    now = start_at
    daily_humidity_lookup = build_daily_humidity_lookup(hourly)

    if lstm_model is not None and lstm_scaler is not None:
        try:
            hourly_data = build_hourly_from_lstm(hourly, now)
        except ValueError:
            hourly_data = build_hourly_from_api(hourly, now)
    else:
        hourly_data = build_hourly_from_api(hourly, now)

    daily_data = []
    if "time" in daily:
        total_days = len(daily["time"])
        daily_wind = daily.get("windspeed_10m_max") or daily.get("wind_speed_10m_max") or [0] * total_days
        daily_uv = daily.get("uv_index_max") or [0] * total_days
        today = now.date()
        for idx in range(total_days):
            api_date = datetime.datetime.strptime(daily["time"][idx], "%Y-%m-%d").date()
            diff = (api_date - today).days
            if diff < 0:
                continue
            fmt_date = "Hom nay" if diff == 0 else "Ngay mai" if diff == 1 else api_date.strftime("%d/%m")
            daily_data.append(
                {
                    "ngay": fmt_date,
                    "max": float(daily.get("temperature_2m_max", [0] * total_days)[idx] or 0),
                    "min": float(daily.get("temperature_2m_min", [0] * total_days)[idx] or 0),
                    "mua_sum": float(daily.get("precipitation_sum", [0] * total_days)[idx] or 0),
                    "gio_max": float(daily_wind[idx] or 0),
                    "uv_max": float(daily_uv[idx] or 0),
                    "do_am_tb": float(daily_humidity_lookup.get(daily["time"][idx], 0)),
                }
            )

    return {"status": "success", "thanh_pho": city_name, "hourly": hourly_data, "daily": daily_data}


@app.route("/api/thoitiet_full", methods=["GET"])
def thoitiet_full():
    try:
        # Lấy tên thành phố từ App Android gửi lên, mặc định là Tan Uyen nếu không gửi
        city_name = request.args.get("city", "Tan Uyen")
        
        # Lấy tọa độ từ cuốn từ điển. Nếu nhập sai tên, lấy mặc định Tan Uyen
        lat, lon = VIETNAM_PROVINCES.get(city_name, (11.03, 106.80))
        
        # Đưa tọa độ ĐỘNG vào URL
        url = (
            f"https://api.open-meteo.com/v1/forecast?latitude={lat}&longitude={lon}"
            "&hourly=temperature_2m,relative_humidity_2m,windspeed_10m,precipitation,uv_index,precipitation_probability"
            "&daily=temperature_2m_max,temperature_2m_min,precipitation_sum,windspeed_10m_max"
            "&timezone=Asia%2FBangkok&past_days=2"
        )
        
        session = requests.Session()
        session.trust_env = False
        response = session.get(url, timeout=10)
        response.raise_for_status()
        res = response.json()

        if "hourly" not in res:
            return jsonify({"status": "error", "message": "API khong co du lieu"}), 500

        # Trả về kèm theo tên thành phố để Android App biết nó đang hiển thị ở đâu
        payload = build_forecast_payload(res)
        payload["thanh_pho"] = city_name 
        return jsonify(payload)
        
    except Exception as exc:
        safe_print("=== LOI ROUTE ===")
        import traceback
        traceback.print_exc()
        return jsonify({"status": "error", "message": str(exc)}), 500


if __name__ == "__main__":
    safe_print("==================================================")
    safe_print("HE THONG AI HYBRID (LSTM + RANDOM FOREST)")
    safe_print("==================================================")
    load_resources()
    safe_print("[3/3] SERVER DANG CHAY O CONG 5000")
    app.run(host="0.0.0.0", port=5000, debug=False)
