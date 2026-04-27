import argparse
import datetime
import json
import traceback
import sys
import subprocess
from pathlib import Path

VENV_PYTHON = Path(__file__).resolve().parent / ".venv" / "Scripts" / "python.exe"
if sys.executable.lower() != str(VENV_PYTHON).lower() and VENV_PYTHON.exists():
    result = subprocess.run([str(VENV_PYTHON), __file__, *sys.argv[1:]], check=False)
    raise SystemExit(result.returncode)

import numpy as np
import requests
from sklearn.metrics import f1_score, mean_absolute_error

from ai_server import (
    ensure_classifier_loaded,
    score_conditions,
    get_series,
)
import ai_server

def safe_print(message):
    try:
        print(message)
    except UnicodeEncodeError:
        print(str(message).encode("ascii", "ignore").decode("ascii"))

def fetch_hourly_api_data(lat, lon, cache_file="api_hourly_cache.json"):
    url = (
        f"https://api.open-meteo.com/v1/forecast?latitude={lat}&longitude={lon}"
        "&hourly=temperature_2m,relative_humidity_2m,wind_speed_10m,precipitation,uv_index"
        "&timezone=Asia%2FHo_Chi_Minh&past_days=14&forecast_days=2"
    )
    safe_print(f"Dang dong bo tien trinh API tu: {url}")
    response = requests.get(url, timeout=15)
    response.raise_for_status()
    data = response.json()

    with open(cache_file, "w", encoding="utf-8") as f:
        json.dump(data, f, ensure_ascii=False, indent=2)
    return data

def load_or_fetch_api_data(lat, lon, force_refresh=False):
    cache_file = Path("api_hourly_cache.json")
    if not force_refresh and cache_file.exists():
        safe_print("Phat hien cache. Dang load file cache API truoc do...")
        with open(cache_file, "r", encoding="utf-8") as f:
            return json.load(f)
    return fetch_hourly_api_data(lat, lon, str(cache_file))

def extract_hourly_features(hourly_data, start_idx, length):
    times = hourly_data.get("time", [])
    temperature_series = get_series(hourly_data, "temperature_2m")
    humidity_series = get_series(hourly_data, "relative_humidity_2m")
    wind_series = get_series(hourly_data, "windspeed_10m", "wind_speed_10m")
    precipitation_series = get_series(hourly_data, "precipitation")
    uv_series = get_series(hourly_data, "uv_index")

    features = []
    labels = []

    for i in range(start_idx, start_idx + length):
        if i >= len(times):
            break
        t = float(temperature_series[i] or 0)
        h = float(humidity_series[i] or 0)
        w = float(wind_series[i] or 0)
        p = max(0.0, float(precipitation_series[i] or 0))
        u = max(0.0, float(uv_series[i] or 0))
        
        nhan, prob = score_conditions(t, h, w, p, u)
        
        features.append([t, h, w, p, u])
        labels.append(nhan)

    return np.array(features), np.array(labels), times[start_idx:start_idx + length]

def blind_test(api_data, target_date, target_hour):
    hourly = api_data.get("hourly", {})
    times = hourly.get("time", [])
    
    target_str = f"{target_date}T{target_hour:02d}:00"
    
    try:
        target_idx = times.index(target_str)
    except ValueError:
        target_str_fall = f"{target_date}T00:00"
        try:
            target_idx = times.index(target_str_fall)
            safe_print(f"Khong tim thay gio {target_hour}, fallback ve 00h: {target_str_fall}")
            target_str = target_str_fall
        except ValueError:
            raise ValueError(f"Khong tim thay {target_date} hoac cac thoi diem lien quan trong API data. Vui long chon ngay khac nam trong pham vi API!")
        
    if target_idx < 24:
        raise ValueError(f"Khong du 24h lich su truoc moc {target_str} (Hien co {target_idx} muc_luc truoc do). Su dung target_date muon hon hoac API can mo rong past_days.")
        
    safe_print(f"\n==================================================")
    safe_print(f"BLIND TEST THEO GIO (CUA SO TRUOT 24H)")
    safe_print(f"==================================================")
    safe_print(f"Moc bat dau du doan (Bi chan tu day): {target_str}")
    
    context_x, _, _ = extract_hourly_features(hourly, target_idx - 24, 24)
    actual_y, actual_labels, actual_times = extract_hourly_features(hourly, target_idx, 24)
    
    num_to_predict = len(actual_y)
    safe_print(f"So gio duoc lay de tinh Toan F1: {num_to_predict}")
    if num_to_predict == 0:
        raise ValueError("Khong dong ho tuong lai de thuc thi du doan.")
        
    current_window_scaled = ai_server.lstm_scaler.transform(context_x)
    
    predicted_y = []
    predicted_labels = []
    
    safe_print("\n[ Gio ] | Thuc te (Temp, Hum, Mua, Nhan) | Du doan (Temp, Hum, Mua, Nhan) | Lech?")
    safe_print("-" * 90)
    
    for step in range(num_to_predict):
        input_3d = current_window_scaled.reshape(1, 24, 5)
        next_hour_scaled = ai_server.lstm_model.predict(input_3d, verbose=0)[0]
        
        current_window_scaled = np.append(current_window_scaled[1:], [next_hour_scaled], axis=0)
        
        next_hour_real = ai_server.lstm_scaler.inverse_transform([next_hour_scaled])[0]
        t, h, w, p, u = next_hour_real
        p = max(0.0, float(p))
        u = max(0.0, float(u))
        
        predicted_y.append([t, h, w, p, u])
        pred_label, _ = score_conditions(t, h, w, p, u)
        predicted_labels.append(pred_label)
        
        act_t, act_h, act_w, act_p, act_u = actual_y[step]
        act_label = actual_labels[step]
        time_str = actual_times[step].split("T")[1] if "T" in actual_times[step] else actual_times[step]
        
        diff_mark = "x" if act_label != pred_label else ""
        
        safe_print(f"[{time_str}] | TT: {act_t:4.1f}C, {act_h:4.1f}%, mua {act_p:4.1f}mm, nhan {act_label} "
                   f"| DD: {t:4.1f}C, {h:4.1f}%, mua {p:4.1f}mm, nhan {pred_label} | {diff_mark}")
        
    y_true = np.array(actual_y)
    y_pred = np.array(predicted_y)
    
    f1_macro = f1_score(actual_labels, predicted_labels, average='macro', zero_division=0)
    f1_weighted = f1_score(actual_labels, predicted_labels, average='weighted', zero_division=0)
    mae_t = mean_absolute_error(y_true[:, 0], y_pred[:, 0])
    mae_p = mean_absolute_error(y_true[:, 3], y_pred[:, 3])
    
    safe_print(f"\n==================================================")
    safe_print(f"KET QUA DANH GIA (F1-SCORE)")
    safe_print(f"==================================================")
    safe_print(f"Do lech Nhiet doi thuc te vs Du doan (MAE): {mae_t:.2f} doc C")
    safe_print(f"Do lech Luong mua thuc te vs Du doan (MAE): {mae_p:.2f} mm")
    safe_print(f"F1-Score (Macro) Phan loai nhan RF: {f1_macro:.4f}")
    safe_print(f"F1-Score (Weighted) Phan loai: {f1_weighted:.4f}")
    
    if list(actual_labels) == list(predicted_labels):
        safe_print("-> Hoan hao! Model du doan khop 100% cac nhan canh bao.")
    else:
        safe_print("-> Co the cai thien thong so mo hinh cho Du doan Gio.")

def main():
    parser = argparse.ArgumentParser(description="Blind test gio F1-score")
    parser.add_argument("--lat", default=11.03, type=float)
    parser.add_argument("--lon", default=106.80, type=float)
    parser.add_argument("--target-date", required=True, help="Ngay chan test YYYY-MM-DD")
    parser.add_argument("--target-hour", default=0, type=int, help="Gio chan test 0-23")
    parser.add_argument("--force-refresh", action="store_true", help="Lam moi API")
    
    args = parser.parse_args()
    
    try:
        safe_print("Nap du lieu va model...")
        ensure_classifier_loaded()
        if ai_server.lstm_model is None or ai_server.lstm_scaler is None:
            raise RuntimeError("Khong tim thay mo hinh LSTM. Chay train_lstm.py truoc.")
            
        data = load_or_fetch_api_data(args.lat, args.lon, force_refresh=args.force_refresh)
        blind_test(data, args.target_date, args.target_hour)
    except Exception as e:
        safe_print(f"Loi: {str(e)}")
        traceback.print_exc()

if __name__ == "__main__":
    main()
