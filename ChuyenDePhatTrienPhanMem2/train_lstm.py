import argparse
import subprocess
import sys
from pathlib import Path

VENV_PYTHON = Path(__file__).resolve().parent / ".venv" / "Scripts" / "python.exe"
if sys.executable.lower() != str(VENV_PYTHON).lower() and VENV_PYTHON.exists():
    result = subprocess.run([str(VENV_PYTHON), __file__, *sys.argv[1:]], check=False)
    raise SystemExit(result.returncode)

import joblib
import numpy as np
import pandas as pd
from sklearn.model_selection import train_test_split
from sklearn.preprocessing import MinMaxScaler
from tensorflow.keras.callbacks import EarlyStopping
from tensorflow.keras.layers import Input
from tensorflow.keras.layers import LSTM, Dense, Dropout
from tensorflow.keras.models import Sequential

DATA_PATH = Path("df_weather_hourly.csv")
MODEL_PATH = Path("weather_lstm.h5")
SCALER_PATH = Path("scaler_lstm.pkl")
FEATURE_COLUMNS = ["Nhiet_Do", "Do_Am", "Gio_kph", "Mua_mm", "Chi_So_UV"]
LOOKBACK = 24

def safe_print(message):
    try:
        print(message)
    except UnicodeEncodeError:
        print(message.encode("ascii", "ignore").decode("ascii"))


def load_clean_dataframe(path: Path = DATA_PATH) -> pd.DataFrame:
    if not path.exists():
        raise FileNotFoundError(f"Khong tim thay file du lieu: {path}")

    df = pd.read_csv(path)
    required_columns = ["Tinh_Thanh", "Ngay_Thang", *FEATURE_COLUMNS]
    missing_columns = [column for column in required_columns if column not in df.columns]
    if missing_columns:
        raise ValueError(f"CSV thieu cot bat buoc: {missing_columns}")

    df = df[required_columns].copy()
    df["Ngay_Thang"] = pd.to_datetime(df["Ngay_Thang"], errors="coerce")
    for column in FEATURE_COLUMNS:
        df[column] = pd.to_numeric(df[column], errors="coerce")

    df = df.dropna(subset=["Tinh_Thanh", "Ngay_Thang"]).sort_values(["Tinh_Thanh", "Ngay_Thang"]).reset_index(drop=True)
    df[FEATURE_COLUMNS] = df.groupby("Tinh_Thanh")[FEATURE_COLUMNS].transform(
        lambda frame: frame.interpolate(method="linear", limit_direction="both").ffill().bfill()
    )
    df = df.dropna(subset=FEATURE_COLUMNS)
    return df


def build_sequences(df: pd.DataFrame, scaler: MinMaxScaler, lookback: int = LOOKBACK):
    sequences = []
    targets = []

    for _, city_df in df.groupby("Tinh_Thanh"):
        city_values = city_df[FEATURE_COLUMNS].to_numpy(dtype=np.float32)
        if len(city_values) <= lookback:
            continue

        scaled_values = scaler.transform(city_values)
        for idx in range(lookback, len(scaled_values)):
            sequences.append(scaled_values[idx - lookback:idx])
            targets.append(scaled_values[idx])

    if not sequences:
        raise RuntimeError("Khong tao duoc sequence nao de huan luyen. Kiem tra lai du lieu dau vao.")

    return np.array(sequences, dtype=np.float32), np.array(targets, dtype=np.float32)


def build_model(input_shape):
    model = Sequential(
        [
            Input(shape=input_shape),
            LSTM(64, return_sequences=True),
            Dropout(0.2),
            LSTM(32),
            Dropout(0.2),
            Dense(32, activation="relu"),
            Dense(len(FEATURE_COLUMNS)),
        ]
    )
    model.compile(optimizer="adam", loss="mse", metrics=["mae"])
    return model


def main():
    parser = argparse.ArgumentParser(description="Train LSTM weather forecaster")
    parser.add_argument("--epochs", type=int, default=20)
    parser.add_argument("--batch-size", type=int, default=32)
    parser.add_argument("--lookback", type=int, default=LOOKBACK)
    args = parser.parse_args()

    safe_print("[1/5] Dang doc va lam sach du lieu...")
    df = load_clean_dataframe()

    scaler = MinMaxScaler()
    scaler.fit(df[FEATURE_COLUMNS].to_numpy(dtype=np.float32))
    X, y = build_sequences(df, scaler, lookback=args.lookback)
    safe_print(f"Da tao {len(X)} sequence huan luyen tu {df['Tinh_Thanh'].nunique()} tinh/thanh.")

    X_train, X_test, y_train, y_test = train_test_split(X, y, test_size=0.2, random_state=42)

    safe_print("[2/5] Dang khoi tao mo hinh LSTM...")
    model = build_model((args.lookback, len(FEATURE_COLUMNS)))

    safe_print("[3/5] Dang huan luyen mo hinh...")
    callbacks = [EarlyStopping(monitor="val_loss", patience=5, restore_best_weights=True)]
    history = model.fit(
        X_train,
        y_train,
        validation_split=0.1,
        epochs=args.epochs,
        batch_size=args.batch_size,
        verbose=1,
        callbacks=callbacks,
    )

    safe_print("[4/5] Dang danh gia mo hinh...")
    loss, mae = model.evaluate(X_test, y_test, verbose=0)
    safe_print(f"Test loss: {loss:.6f}")
    safe_print(f"Test MAE (scaled): {mae:.6f}")

    MODEL_PATH.parent.mkdir(parents=True, exist_ok=True)
    model.save(MODEL_PATH)
    joblib.dump(scaler, SCALER_PATH)

    safe_print("[5/5] Da luu model va scaler.")
    safe_print(f"Epochs da chay: {len(history.history['loss'])}")
    safe_print(f"Model: {MODEL_PATH}")
    safe_print(f"Scaler: {SCALER_PATH}")


if __name__ == "__main__":
    main()
