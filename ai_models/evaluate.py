"""模型可信度评估 (Phase 3 复核)：加载已训练模型，做
1) 整体 & 分步 MAPE
2) 跨时间分块的 MAPE 稳定性（单一测试集的方差到底有多大）
3) 分负荷区间误差（空载/中载/高载，看 MAPE 是被谁拉高/拉低）
4) 对比基线：persistence(上一刻) / seasonal-naive(昨天同时刻)
"""
import numpy as np
import torch
import joblib

import config as C
from data_prep import prepare
from features import feature_pipeline as FP
from models.lstm_model import LSTMForecaster


def mape(yt, yp, eps=1e-2):
    return float(np.mean(np.abs(yt - yp) / np.maximum(np.abs(yt), eps)) * 100)


def main():
    _, _, test_df = prepare()
    feat_scaler = joblib.load(C.ARTIFACT_DIR / "feat_scaler.pkl")
    target_scaler = joblib.load(C.ARTIFACT_DIR / "target_scaler.pkl")

    dyn, cal, tgt = FP.transform(test_df, feat_scaler, target_scaler)
    Xd, Xf, y = FP.make_windows(dyn, cal, tgt)

    model = LSTMForecaster(FP.n_dynamic_features(), FP.n_calendar_features(), C.HORIZON,
                           C.HIDDEN_SIZE, C.NUM_LAYERS, C.DROPOUT)
    model.load_state_dict(torch.load(C.ARTIFACT_DIR / "lstm_forecaster.pt"))
    model.eval()

    with torch.no_grad():
        pred_s = model(torch.from_numpy(Xd), torch.from_numpy(Xf)).numpy()
    inv = lambda a: target_scaler.inverse_transform(a.reshape(-1, 1)).reshape(a.shape)
    pred, true = inv(pred_s), inv(y)

    print(f"样本数={len(true)}  overall MAPE={mape(true, pred):.2f}%  "
          f"+15min={mape(true[:,0],pred[:,0]):.2f}%  +30min={mape(true[:,1],pred[:,1]):.2f}%")

    # 2) 跨时间分块稳定性（按时间顺序切 8 块，看每块 +15min MAPE）
    print("\n[稳定性] 测试期切 8 个时间块的 +15min MAPE:")
    chunks = np.array_split(np.arange(len(true)), 8)
    block_mapes = [mape(true[idx, 0], pred[idx, 0]) for idx in chunks]
    for i, m in enumerate(block_mapes):
        print(f"  块{i+1}: {m:5.2f}%")
    print(f"  → 均值 {np.mean(block_mapes):.2f}%  标准差 {np.std(block_mapes):.2f}%  "
          f"最差 {np.max(block_mapes):.2f}%")

    # 3) 分负荷区间（按真值 +15min 分箱）
    print("\n[分负荷区间] +15min 误差:")
    t = true[:, 0]; p = pred[:, 0]
    bins = [("空载 <10kWh", t < 10), ("中载 10-50", (t >= 10) & (t < 50)), ("高载 ≥50", t >= 50)]
    for name, mask in bins:
        if mask.sum() == 0:
            continue
        print(f"  {name:14s} n={mask.sum():4d} ({100*mask.mean():4.1f}%)  "
              f"MAPE={mape(t[mask],p[mask]):5.2f}%  MAE={np.mean(np.abs(t[mask]-p[mask])):.2f}kWh")

    # 4) 基线对比（都在平滑目标、原始 kWh 尺度上）
    print("\n[基线对比] +15min MAPE:")
    sm = test_df["usage_smooth"].values[C.LOOKBACK:]      # 与窗口对齐的真值序列
    persist = test_df["usage_smooth"].values[C.LOOKBACK-1:-1]
    season = test_df["usage_smooth"].values[C.LOOKBACK-96:-96] if len(test_df) > C.LOOKBACK+96 else None
    n = len(true)
    print(f"  LSTM(本模型)        {mape(true[:,0], pred[:,0]):5.2f}%")
    print(f"  persistence(上一刻)  {mape(sm[:n], persist[:n]):5.2f}%")
    if season is not None:
        print(f"  seasonal(昨天同时刻) {mape(sm[:n], season[:n]):5.2f}%")


if __name__ == "__main__":
    main()
