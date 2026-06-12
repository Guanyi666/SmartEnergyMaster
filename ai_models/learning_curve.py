"""干净的学习曲线：评估"训练样本量"是否充足。

相比初版，消除两个污染因素：
1. 不再用"最近 X%"（会混入时效性/分布漂移），改为**随机子采样**训练窗口——
   每个数据量都均匀覆盖全年，单纯隔离"样本数量"这一变量。
2. 每个数据量都**训练到收敛**（完整早停+LR调度+梯度裁剪，与 train.py 一致），
   而非砍轮数，避免大数据量被"欠拟合"拖累。

读法：若测试 MAPE 在 75%→100% 已基本持平 → 数据量足够；若仍明显下降 → 需要更多数据。
"""
import numpy as np
import torch
import torch.nn as nn
from torch.utils.data import DataLoader, TensorDataset

import config as C
from data_prep import prepare
from features import feature_pipeline as FP
from models.lstm_model import LSTMForecaster


def mape(yt, yp, eps=1e-2):
    return float(np.mean(np.abs(yt - yp) / np.maximum(np.abs(yt), eps)) * 100)


def train_one(xd, xf, y, Xva_d, Xva_f, yva):
    """与 train.py 同配置训练到收敛，返回最佳模型。"""
    loader = DataLoader(TensorDataset(torch.from_numpy(xd), torch.from_numpy(xf),
                                      torch.from_numpy(y)), batch_size=C.BATCH_SIZE, shuffle=True)
    model = LSTMForecaster(FP.n_dynamic_features(), FP.n_calendar_features(), C.HORIZON,
                           C.HIDDEN_SIZE, C.NUM_LAYERS, C.DROPOUT)
    opt = torch.optim.Adam(model.parameters(), lr=C.LR)
    sched = torch.optim.lr_scheduler.ReduceLROnPlateau(opt, mode="min", factor=0.5, patience=4)
    loss_fn = nn.MSELoss()

    best, best_state, wait = float("inf"), None, 0
    for _ in range(C.EPOCHS):
        model.train()
        for bd, bf, by in loader:
            opt.zero_grad(); loss = loss_fn(model(bd, bf), by); loss.backward()
            torch.nn.utils.clip_grad_norm_(model.parameters(), C.GRAD_CLIP); opt.step()
        model.eval()
        with torch.no_grad():
            v = loss_fn(model(torch.from_numpy(Xva_d), torch.from_numpy(Xva_f)),
                        torch.from_numpy(yva)).item()
        sched.step(v)
        if v < best - 1e-5:
            best, best_state, wait = v, {k: t.clone() for k, t in model.state_dict().items()}, 0
        else:
            wait += 1
            if wait >= C.PATIENCE:
                break
    model.load_state_dict(best_state); model.eval()
    return model


def main():
    torch.manual_seed(C.SEED); np.random.seed(C.SEED)
    train_df, val_df, test_df = prepare()
    feat_scaler, target_scaler = FP.fit_scalers(train_df)

    def build(df):
        dyn, cal, tgt = FP.transform(df, feat_scaler, target_scaler)
        return FP.make_windows(dyn, cal, tgt)

    Xtr_d, Xtr_f, ytr = build(train_df)
    Xva_d, Xva_f, yva = build(val_df)
    Xte_d, Xte_f, yte = build(test_df)
    inv = lambda a: target_scaler.inverse_transform(a.reshape(-1, 1)).reshape(a.shape)
    yte_kwh = inv(yte)
    n_full = len(ytr)
    rng = np.random.default_rng(C.SEED)

    print(f"完整训练窗口数 = {n_full}（随机子采样，训练到收敛）\n")
    print(f"{'数据比例':>8} {'训练窗口':>8} {'测试MAPE':>9} {'+15min':>8} {'+30min':>8}")
    results = []
    for frac in [0.25, 0.50, 0.75, 1.00]:
        k = int(n_full * frac)
        idx = np.arange(n_full) if frac == 1.0 else rng.choice(n_full, size=k, replace=False)
        model = train_one(Xtr_d[idx], Xtr_f[idx], ytr[idx], Xva_d, Xva_f, yva)
        with torch.no_grad():
            pred = inv(model(torch.from_numpy(Xte_d), torch.from_numpy(Xte_f)).numpy())
        m, m15, m30 = mape(yte_kwh, pred), mape(yte_kwh[:, 0], pred[:, 0]), mape(yte_kwh[:, 1], pred[:, 1])
        results.append((frac, k, m, m15, m30))
        print(f"{int(frac*100):>7}% {k:>8} {m:>8.2f}% {m15:>7.2f}% {m30:>7.2f}%")

    drop = results[2][2] - results[3][2]
    print(f"\n75%→100% 测试 MAPE 变化 = {drop:+.2f} 个百分点 "
          f"({'仍在下降，更多数据有帮助' if drop > 0.3 else '基本持平 → 样本量足够'})")


if __name__ == "__main__":
    main()
