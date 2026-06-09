"""特征工程 (Epic 6-2)：日历周期编码 + 标准化（仅用训练集拟合）+ 滑窗样本构造。

设计要点：
- 时间是"已知未来协变量"——预测时未来时刻的 NSM/星期是确定的，所以未来 HORIZON
  步的日历特征可以直接喂给模型（X_fut），显著帮助学到日内/周内周期。
- scaler 只在 train 上 fit，再 transform val/test，杜绝数据泄漏。
"""
import numpy as np
from sklearn.preprocessing import StandardScaler

from config import DYNAMIC_FEATURES, TARGET, LOOKBACK, HORIZON

_DOW = {"Monday": 0, "Tuesday": 1, "Wednesday": 2, "Thursday": 3,
        "Friday": 4, "Saturday": 5, "Sunday": 6}

CALENDAR_FEATURES = ["nsm_sin", "nsm_cos", "dow_sin", "dow_cos", "is_weekend"]


def add_calendar(df):
    df = df.copy()
    nsm = df["NSM"].astype(float)
    df["nsm_sin"] = np.sin(2 * np.pi * nsm / 86400.0)
    df["nsm_cos"] = np.cos(2 * np.pi * nsm / 86400.0)
    dow = df["Day_of_week"].map(_DOW).astype(float)
    df["dow_sin"] = np.sin(2 * np.pi * dow / 7.0)
    df["dow_cos"] = np.cos(2 * np.pi * dow / 7.0)
    df["is_weekend"] = (df["WeekStatus"].astype(str).str.strip() == "Weekend").astype(float)
    return df


def fit_scalers(train_df):
    """返回 (特征scaler, 目标scaler)，均只在训练集上拟合。"""
    feat_scaler = StandardScaler().fit(train_df[DYNAMIC_FEATURES].values)
    target_scaler = StandardScaler().fit(train_df[[TARGET]].values)
    return feat_scaler, target_scaler


def transform(df, feat_scaler, target_scaler):
    """df -> (dyn[N, n_dyn], cal[N, n_cal], target_scaled[N])。dyn = 标准化传感器列 + 日历列。"""
    df = add_calendar(df)
    sensors = feat_scaler.transform(df[DYNAMIC_FEATURES].values)
    cal = df[CALENDAR_FEATURES].values.astype(np.float32)
    dyn = np.concatenate([sensors, cal], axis=1).astype(np.float32)
    target_scaled = target_scaler.transform(df[[TARGET]].values).astype(np.float32).ravel()
    return dyn, cal, target_scaled


def make_windows(dyn, cal, target_scaled, lookback=LOOKBACK, horizon=HORIZON):
    """滑窗：X_dyn[lookback, n_dyn], X_fut[horizon, n_cal], y[horizon]。"""
    X_dyn, X_fut, y = [], [], []
    n = len(dyn)
    for i in range(lookback, n - horizon + 1):
        X_dyn.append(dyn[i - lookback:i])
        X_fut.append(cal[i:i + horizon])              # 未来已知的日历协变量
        y.append(target_scaled[i:i + horizon])
    return (np.asarray(X_dyn, dtype=np.float32),
            np.asarray(X_fut, dtype=np.float32),
            np.asarray(y, dtype=np.float32))


def n_dynamic_features():
    return len(DYNAMIC_FEATURES) + len(CALENDAR_FEATURES)


def n_calendar_features():
    return len(CALENDAR_FEATURES)
