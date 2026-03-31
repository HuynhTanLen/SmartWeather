import requests
import pandas as pd
import numpy as np
from sklearn.model_selection import train_test_split, GridSearchCV
from sklearn.preprocessing import StandardScaler
from sklearn.ensemble import RandomForestClassifier
from sklearn.metrics import recall_score
import datetime
import joblib
from tensorflow.keras.models import load_model
from flask import Flask, jsonify, request

print("==================================================")
print("🚀 HỆ THỐNG AI & API SERVER 24H")
print("==================================================\n")
lstm_model = load_model('weather_lstm.h5')
scaler_lstm = joblib.load('scaler_lstm.pkl')
# ==========================================
# BƯỚC 1: ETL & LÀM SẠCH DỮ LIỆU (Giữ nguyên logic cũ)
# ==========================================
print("[1/4] Đang xử lý chuẩn hóa tập dữ liệu gốc...")
try:
    df = pd.read_csv('df_weather.csv')
    if 'location.name' in df.columns:
        df = df[['location.name', 'date', 'day.maxtemp_c', 'day.maxwind_kph', 'day.totalprecip_mm', 'day.avghumidity',
                 'day.uv']].copy()
        df = df.rename(columns={'location.name': 'Tinh_Thanh', 'date': 'Ngay_Thang', 'day.maxtemp_c': 'Nhiet_Do',
                                'day.maxwind_kph': 'Gio_kph', 'day.totalprecip_mm': 'Mua_mm',
                                'day.avghumidity': 'Do_Am', 'day.uv': 'Chi_So_UV'})
        df.to_csv('df_weather.csv', index=False)
except FileNotFoundError:
    print("❌ Lỗi: Không tìm thấy file 'df_weather.csv'.")
    exit()

if df.isnull().sum().sum() > 0:
    cot_so = df.select_dtypes(include=['number']).columns
    df[cot_so] = df[cot_so].interpolate(method='linear')
    df = df.ffill().bfill()

# ==========================================
# BƯỚC 2: GÁN NHÃN & HUẤN LUYỆN
# ==========================================
print("[2/4] Đang gán nhãn và tái huấn luyện AI...")


def danh_gia_suc_khoe(row):
    nhiet, am, gio, mua, uv = row['Nhiet_Do'], row['Do_Am'], row['Gio_kph'], row['Mua_mm'], row['Chi_So_UV']
    if (nhiet >= 35 and am >= 70) or (mua >= 80) or (uv >= 8) or (gio >= 60):
        return 2
    elif (nhiet >= 32) or (mua >= 20) or (uv >= 6) or (gio >= 40):
        return 1
    else:
        return 0

df['Nhan_Canh_Bao'] = df.apply(danh_gia_suc_khoe, axis=1)
def tinh_ty_le_mua(nhiet, gio , am):
    ty_le = 0
    if am > 90:
        ty_le+=60
    elif am > 80:
        ty_le+=40
    elif am > 70:
        ty_le+=20
    if nhiet >= 32 and am >= 75:
        ty_le+=20
    if gio >= 25 and am >= 75:
        ty_le+=15

    return min(ty_le, 100)
def goi_y_hoat_dong(nhiet, am, gio, ty_le_mua, uv, muc):
    if muc == 2:
        if ty_le_mua >= 80: return f" Khả năng mưa {ty_le_mua}%: Tuyệt đối không ra ngoài"
        elif uv >= 8: return " UV rất cao: Tránh nắng hoàn toàn"
        elif nhiet >= 35 and am >= 70: return "Nóng ẩm nguy hiểm: Ở trong nhà, tránh mất nước"
        elif gio >= 60: return " Gió giật mạnh: Nguy hiểm khi di chuyển"
        else: return "❌ Thời tiết nguy hiểm: Nên ở trong nhà"

    elif muc == 1:
        if ty_le_mua >= 50: return f"Tỉ lệ mưa {ty_le_mua}%: Có thể mưa, nhớ mang ô/áo mưa"
        elif uv >= 6: return "UV cao: Ra ngoài cần bôi kem chống nắng"
        elif nhiet >= 32: return "Trời oi bức: Hạn chế hoạt động ngoài trời"
        elif gio >= 40: return "Gió mạnh: Cẩn thận khi lái xe"
        else: return "Thời tiết thay đổi: Cân nhắc các hoạt động nhẹ"

    else:
        # Nếu tỉ lệ mưa rất thấp (< 20%) và trời mát mẻ
        if 25 <= nhiet <= 32 and ty_le_mua < 20 and gio < 20:
            return "Thời tiết cực đẹp: Rất thích hợp đi dã ngoại hoặc cafe"
        # Nếu trời nóng nhưng không mưa
        elif nhiet > 30 and ty_le_mua < 10:
            return " Trời nắng ráo: Thích hợp đi bơi hoặc ở trong nhà mát"
        # Nếu UV vừa phải và khô ráo
        elif uv >= 5 and am < 60:
            return " Nắng ấm: Có thể phơi nắng sớm (có bảo vệ)"
        else:
            return "Thời tiết ổn định: Có thể đi dạo hoặc vận động nhẹ"

df['ty_le_mua'] = df.apply(lambda row: tinh_ty_le_mua(row['Nhiet_Do'], row['Do_Am'], row['Gio_kph']), axis=1)
df['goi_y_thong_minh'] = df.apply(lambda row: goi_y_hoat_dong(row['Nhiet_Do'], row['Do_Am'], row['Gio_kph'], row['ty_le_mua'], row['Chi_So_UV'], row['Nhan_Canh_Bao']), axis=1)
X = df[['Nhiet_Do', 'Do_Am', 'Gio_kph', 'Mua_mm', 'Chi_So_UV']]
y = df['Nhan_Canh_Bao']
X_train, X_test, y_train, y_test = train_test_split(X, y, test_size=0.2, random_state=42)

scaler = StandardScaler()
X_train_scaled = scaler.fit_transform(X_train)

# Tối ưu Random Forest
rf = GridSearchCV(RandomForestClassifier(random_state=42), {'n_estimators': [50, 100]}, cv=3, n_jobs=-1).fit(
    X_train_scaled, y_train)
best_rf = rf.best_estimator_

joblib.dump(best_rf, 'weather_model.pkl')
joblib.dump(scaler, 'weather_scaler.pkl')

# ==========================================
# BƯỚC 3: API SERVER CHO HOURLY FORECAST
# ==========================================
print("[3/4] Đang thiết lập Endpoint 24h...")
app = Flask(__name__)


@app.route('/api/thoitiet_full', methods=['GET'])
def thoitiet_full():
    try:
        # 1. URL chuẩn (Đã thêm precipitation_probability và windspeed_10m_max)
        url = "https://api.open-meteo.com/v1/forecast?latitude=11.03&longitude=106.80&hourly=temperature_2m,relative_humidity_2m,windspeed_10m,precipitation,uv_index,precipitation_probability&daily=temperature_2m_max,temperature_2m_min,precipitation_sum,windspeed_10m_max&timezone=Asia%2FBangkok&forecast_days=15"

        response = requests.get(url, timeout=10)
        res = response.json()

        # Kiểm tra nếu API trả về lỗi
        if 'hourly' not in res:
            return jsonify({'status': 'error', 'message': 'API Open-Meteo không trả về dữ liệu'}), 500

        # --- PHẦN 1: XỬ LÝ HOURLY (Dự báo 24h) ---
        now_hour = datetime.datetime.now().hour
        hourly_data = []
        # Lấy tối đa 24 giờ tính từ giờ hiện tại
        for i in range(now_hour, min(now_hour + 24, len(res['hourly']['time']))):
            t = res['hourly']['temperature_2m'][i]
            h = res['hourly']['relative_humidity_2m'][i]
            w = res['hourly']['windspeed_10m'][i]
            p = res['hourly']['precipitation'][i]
            u = res['hourly']['uv_index'][i]
            prob = res['hourly'].get('precipitation_probability', [0] * 168)[i]

            # Dự báo AI
            input_scaled = scaler.transform([[t, h, w, p, u]])
            nhan = int(best_rf.predict(input_scaled)[0])

            hourly_data.append({
                "gio": res['hourly']['time'][i].split("T")[1],
                "nhiet": t,
                "rui_ro": nhan,
                "ty_le_mua": prob,
                "goi_y": goi_y_hoat_dong(t, h, w, prob, u, nhan)
            })

        # --- PHẦN 2: XỬ LÝ DAILY (Dự báo 15 ngày) ---
        # ĐẶT NGOÀI VÒNG LẶP HOURLY
        daily_data = []
        if 'daily' in res:
            today = datetime.datetime.now().date()
            for j in range(len(res['daily']['time'])):
                raw_date = res['daily']['time'][j]
                api_date = datetime.datetime.strptime(raw_date, "%Y-%m-%d").date()
                diff = (api_date - today).days

                fmt_date = "Hôm nay" if diff == 0 else "Ngày mai" if diff == 1 else api_date.strftime("%d/%m")

                daily_data.append({
                    "ngay": fmt_date,
                    "max": res['daily']['temperature_2m_max'][j],
                    "min": res['daily']['temperature_2m_min'][j],
                    "mua_sum": res['daily'].get('precipitation_sum', [0] * 15)[j],
                    "gio_max": res['daily'].get('windspeed_10m_max', [0] * 15)[j]
                })

        return jsonify({
            'status': 'success',
            'thanh_pho': "Tân Uyên",
            'hourly': hourly_data,
            'daily': daily_data
        })

    except Exception as e:
        print(f"LỖI SERVER: {str(e)}")  # In ra terminal để bạn debug
        return jsonify({'status': 'error', 'message': str(e)}), 500
if __name__ == '__main__':
    print("[4/4] 🟢 SERVER ĐÃ MỞ (Cổng 5000). Sẵn sàng phục vụ App Android!")
    app.run(host='0.0.0.0', port=5000, debug=False)