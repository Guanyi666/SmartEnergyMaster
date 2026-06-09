"""数据准备 (Epic 6-1)：加载原始 UCI 数据 → 滚动 3σ 清洗+插值 → 按时间 70/15/15 切分。

为什么用滚动 3σ 而非全局 3σ：能耗本身是双峰（空载~3kWh / 满载~150kWh），
全局 3σ 会把合理的满载尖峰当成异常砍掉；滚动窗口的 3σ 只抓真正偏离局部趋势的毛刺。
"""
import pandas as pd

from config import RAW_CSV, TRAIN_RATIO, VAL_RATIO, SMOOTH_WINDOW


def load_raw() -> pd.DataFrame:
    df = pd.read_csv(RAW_CSV)
    # 原始 CSV 已按时间顺序排列（NSM 每天 0→85500，15min 一步）。
    # 造一个连续 datetime 索引仅用于排序/可视化，不参与建模。
    df.index = pd.date_range("2024-01-01", periods=len(df), freq="15min")
    return df


def clean_3sigma(df: pd.DataFrame, cols, window: int = 96, n_sigma: float = 3.0) -> pd.DataFrame:
    """对指定列做滚动 3σ 异常检测：超过局部均值 ±3σ 的点置 NaN 再线性插值。"""
    df = df.copy()
    total_flagged = 0
    for col in cols:
        s = df[col].astype(float)
        roll_mean = s.rolling(window, center=True, min_periods=window // 4).mean()
        roll_std = s.rolling(window, center=True, min_periods=window // 4).std()
        deviation = (s - roll_mean).abs()
        outliers = deviation > (n_sigma * roll_std)
        total_flagged += int(outliers.sum())
        s[outliers] = pd.NA
        df[col] = s.interpolate(method="linear", limit_direction="both")
    print(f"[clean] 滚动3σ 标记并插值异常点: {total_flagged} 个 (跨 {len(cols)} 列)")
    return df


def time_split(df: pd.DataFrame):
    """按时间顺序切 70/15/15（不打乱，避免未来信息泄漏）。"""
    n = len(df)
    n_train = int(n * TRAIN_RATIO)
    n_val = int(n * VAL_RATIO)
    train = df.iloc[:n_train]
    val = df.iloc[n_train:n_train + n_val]
    test = df.iloc[n_train + n_val:]
    print(f"[split] train={len(train)}  val={len(val)}  test={len(test)}")
    return train, val, test


# 需要做 3σ 清洗的原始传感器列（usage_smooth 是清洗后派生的，不在此列）
RAW_SENSOR_COLS = [
    "Usage_kWh",
    "CO2(tCO2)",
]


def prepare():
    df = load_raw()
    df = clean_3sigma(df, RAW_SENSOR_COLS)
    # 派生平滑目标：尾部 1h 滑动平均（因果，跨切分边界也只用各自过去值）
    df["usage_smooth"] = df["Usage_kWh"].rolling(SMOOTH_WINDOW, min_periods=1).mean()
    return time_split(df)


if __name__ == "__main__":
    tr, va, te = prepare()
    print(tr[RAW_SENSOR_COLS + ["usage_smooth"]].describe().round(2))
