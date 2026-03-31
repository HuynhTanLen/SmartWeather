import pandas as pd
import numpy as np
from sklearn.preprocessing import MinMaxScaler
import joblib
from tensorflow.keras.models import Sequential
from tensorflow.keras.layers import LSTM, Dense, Dropout

print("Đang chuẩn bị dữ liệu chuỗi thời gian cho LSTM...")
df = pd.read_csv('df_weather.csv')

# Chọn 5 thông số cần dự báo tương lai
features = ['Nhiet_Do', 'Do_Am', 'Gio_kph', 'Mua_mm', 'Chi_So_UV']
data = df[features].values

# Chuẩn hóa dữ liệu cho Deep Learning (LSTM rất nhạy cảm với số liệu lớn)
scaler_lstm = MinMaxScaler(feature_range=(0, 1))
scaled_data = scaler_lstm.fit_transform(data)

# Hàm tạo chuỗi thời gian (Dùng 24 giờ trước để đoán 1 giờ sau)
def create_sequences(dataset, time_step=24):
    X, Y = [], []
    for i in range(len(dataset) - time_step - 1):
        a = dataset[i:(i + time_step), :]
        X.append(a)
        Y.append(dataset[i + time_step, :]) # Đoán toàn bộ 5 thông số
    return np.array(X), np.array(Y)

time_step = 24
X, Y = create_sequences(scaled_data, time_step)

# Xây dựng kiến trúc Mạng Nơ-ron LSTM
print("Đang khởi tạo Mạng Nơ-ron LSTM...")
model = Sequential()
model.add(LSTM(50, return_sequences=True, input_shape=(time_step, 5)))
model.add(Dropout(0.2))
model.add(LSTM(50, return_sequences=False))
model.add(Dropout(0.2))
model.add(Dense(5)) # Output ra 5 thông số thời tiết

model.compile(optimizer='adam', loss='mean_squared_error')

# Bắt đầu huấn luyện (Train)
print("Bắt đầu huấn luyện Deep Learning (Sẽ mất vài phút)...")
model.fit(X, Y, epochs=20, batch_size=32, verbose=1)

# Lưu mô hình AI lại
model.save('weather_lstm.h5')
joblib.dump(scaler_lstm, 'scaler_lstm.pkl')
print("✅ Đã lưu vũ khí LSTM thành công!")