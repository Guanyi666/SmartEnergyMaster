"""训练入口 (Epic 6-2)：数据→特征→LSTM 训练→MAPE 验收(<8%)→MC Dropout 95%CI→存档。

用法: ./.venv/bin/python train.py
产物: artifacts/lstm_forecaster.pt, artifacts/feat_scaler.pkl, artifacts/target_scaler.pkl, artifacts/meta.json
"""
import json
import numpy as np
import torch
import torch.nn as nn
from torch.utils.data import DataLoader, TensorDataset
import joblib

import config as C
from data_prep import prepare
from features import feature_pipeline as FP
from models.lstm_model import LSTMForecaster
import uncertainty as U


def set_seed(seed):
    np.random.seed(seed)
    torch.manual_seed(seed)


def to_loader(X_dyn, X_fut, y, batch_size, shuffle):
    ds = TensorDataset(torch.from_numpy(X_dyn), torch.from_numpy(X_fut), torch.from_numpy(y))
    return DataLoader(ds, batch_size=batch_size, shuffle=shuffle)


def mape(y_true, y_pred, eps=1e-2):
    """y 为原始 kWh 尺度。eps 防止极少数近零真值导致百分比爆炸。"""
    denom = np.maximum(np.abs(y_true), eps)
    return float(np.mean(np.abs(y_true - y_pred) / denom) * 100)


def evaluate(model, loader, target_scaler, device):
    model.eval()
    preds, trues = [], []
    with torch.no_grad():
        for x_dyn, x_fut, y in loader:
            out = model(x_dyn.to(device), x_fut.to(device)).cpu().numpy()
            preds.append(out)
            trues.append(y.numpy())
    preds = np.concatenate(preds)
    trues = np.concatenate(trues)
    # 反标准化回 kWh
    inv = lambda a: target_scaler.inverse_transform(a.reshape(-1, 1)).reshape(a.shape)
    preds_kwh, trues_kwh = inv(preds), inv(trues)
    overall = mape(trues_kwh, preds_kwh)
    per_step = [mape(trues_kwh[:, h], preds_kwh[:, h]) for h in range(trues_kwh.shape[1])]
    rmse = float(np.sqrt(np.mean((trues_kwh - preds_kwh) ** 2)))
    return overall, per_step, rmse, preds_kwh, trues_kwh


def main():
    set_seed(C.SEED)
    device = torch.device("cuda")
    C.ARTIFACT_DIR.mkdir(exist_ok=True)

    # 1. 数据准备 + 切分
    train_df, val_df, test_df = prepare()

    # 2. 特征：scaler 仅在 train 上 fit
    feat_scaler, target_scaler = FP.fit_scalers(train_df)

    def build(df):
        dyn, cal, tgt = FP.transform(df, feat_scaler, target_scaler)
        return FP.make_windows(dyn, cal, tgt)

    Xtr_d, Xtr_f, ytr = build(train_df)
    Xva_d, Xva_f, yva = build(val_df)
    Xte_d, Xte_f, yte = build(test_df)
    print(f"[windows] train={len(ytr)} val={len(yva)} test={len(yte)} "
          f"| dyn_feat={FP.n_dynamic_features()} cal_feat={FP.n_calendar_features()}")

    train_loader = to_loader(Xtr_d, Xtr_f, ytr, C.BATCH_SIZE, True)
    val_loader = to_loader(Xva_d, Xva_f, yva, C.BATCH_SIZE, False)
    test_loader = to_loader(Xte_d, Xte_f, yte, C.BATCH_SIZE, False)

    # 3. 模型
    model = LSTMForecaster(FP.n_dynamic_features(), FP.n_calendar_features(), C.HORIZON,
                           C.HIDDEN_SIZE, C.NUM_LAYERS, C.DROPOUT).to(device)
    opt = torch.optim.Adam(model.parameters(), lr=C.LR)
    scheduler = torch.optim.lr_scheduler.ReduceLROnPlateau(opt, mode="min", factor=0.5, patience=4)
    loss_fn = nn.MSELoss()

    # 4. 训练 + 早停
    best_val, best_state, wait = float("inf"), None, 0
    for epoch in range(1, C.EPOCHS + 1):
        model.train()
        tr_loss = 0.0
        for x_dyn, x_fut, y in train_loader:
            opt.zero_grad()
            out = model(x_dyn.to(device), x_fut.to(device))
            loss = loss_fn(out, y.to(device))
            loss.backward()
            torch.nn.utils.clip_grad_norm_(model.parameters(), C.GRAD_CLIP)
            opt.step()
            tr_loss += loss.item() * len(y)
        tr_loss /= len(ytr)

        model.eval()
        with torch.no_grad():
            v = sum(loss_fn(model(xd.to(device), xf.to(device)), yv.to(device)).item() * len(yv)
                    for xd, xf, yv in val_loader) / len(yva)

        scheduler.step(v)
        flag = ""
        if v < best_val - 1e-5:
            best_val, best_state, wait = v, {k: t.clone() for k, t in model.state_dict().items()}, 0
            flag = " *"
        else:
            wait += 1
        print(f"epoch {epoch:02d}  train_mse={tr_loss:.4f}  val_mse={v:.4f}{flag}")
        if wait >= C.PATIENCE:
            print(f"[early-stop] val 连续 {C.PATIENCE} 轮无改善")
            break

    model.load_state_dict(best_state)

    # 5. 验收：测试集 MAPE
    overall, per_step, rmse, preds_kwh, trues_kwh = evaluate(model, test_loader, target_scaler, device)
    print("\n===== 测试集评估 (原始 kWh 尺度) =====")
    print(f"整体 MAPE = {overall:.2f}%   (验收目标 < {C.MAPE_TARGET}%)")
    for h, m in enumerate(per_step):
        print(f"  +{(h+1)*15:>3}min  MAPE = {m:.2f}%")
    print(f"RMSE = {rmse:.2f} kWh")
    verdict = "✅ 通过" if overall < C.MAPE_TARGET else "❌ 未达标"
    print(f"验收: {verdict}")

    # 6. MC Dropout + 共形校准的不确定性
    inv = lambda a: target_scaler.inverse_transform(a.reshape(-1, 1)).reshape(a.shape)
    # 6a. 验证集上校准共形系数 q（使 95% 区间真覆盖 95%）
    val_mean, val_std = U.mc_kwh(model, torch.from_numpy(Xva_d).float().to(device),
                                 torch.from_numpy(Xva_f).float().to(device),
                                 target_scaler, C.MC_SAMPLES)
    yva_kwh = inv(yva)
    q = U.calibrate(yva_kwh, val_mean, val_std, alpha=0.05)
    # 6b. 测试集上验证校准前后的覆盖率与平均区间宽度
    test_mean, test_std = U.mc_kwh(model, torch.from_numpy(Xte_d).float().to(device),
                                   torch.from_numpy(Xte_f).float().to(device),
                                   target_scaler, C.MC_SAMPLES)
    lo_raw, hi_raw = U.interval(test_mean, test_std, C.CI_Z, floor=0.0)
    lo_cal, hi_cal = U.interval(test_mean, test_std, q, floor=0.0)
    cov_raw, cov_cal = U.coverage(trues_kwh, lo_raw, hi_raw), U.coverage(trues_kwh, lo_cal, hi_cal)
    width_raw = float(np.mean(hi_raw - lo_raw)); width_cal = float(np.mean(hi_cal - lo_cal))
    print("\n===== MC Dropout 不确定性 (95% 区间) =====")
    print(f"校准系数 q = {q:.3f} (原始 1.96σ 相当于 q=1.96)")
    print(f"  校准前(±1.96σ): 覆盖率={cov_raw*100:5.1f}%  平均宽度={width_raw:5.2f} kWh")
    print(f"  校准后(±q·σ)  : 覆盖率={cov_cal*100:5.1f}%  平均宽度={width_cal:5.2f} kWh  ← 目标≈95%")
    print("  示例 (前5个测试样本, +15min):")
    for i in range(5):
        print(f"    样本{i}: 预测 {test_mean[i,0]:6.2f} kWh  95%CI "
              f"[{lo_cal[i,0]:5.2f}, {hi_cal[i,0]:5.2f}]  真值 {trues_kwh[i,0]:6.2f}")

    # 7. 存档
    torch.save(model.state_dict(), C.ARTIFACT_DIR / "lstm_forecaster.pt")
    joblib.dump(feat_scaler, C.ARTIFACT_DIR / "feat_scaler.pkl")
    joblib.dump(target_scaler, C.ARTIFACT_DIR / "target_scaler.pkl")
    meta = {
        "lookback": C.LOOKBACK, "horizon": C.HORIZON,
        "dynamic_features": FP.n_dynamic_features(), "calendar_features": FP.n_calendar_features(),
        "hidden_size": C.HIDDEN_SIZE, "num_layers": C.NUM_LAYERS, "dropout": C.DROPOUT,
        "test_mape": overall, "per_step_mape": per_step, "rmse": rmse,
        "mc_samples": C.MC_SAMPLES,
        "ci_conformal_q": q,              # 线上构造 95%CI 用：mean ± q·σ_mc，下界 clamp 0
        "ci_coverage_test": cov_cal,
        "ci_avg_width_kwh": width_cal,
    }
    (C.ARTIFACT_DIR / "meta.json").write_text(json.dumps(meta, indent=2, ensure_ascii=False))
    print(f"\n[saved] artifacts → {C.ARTIFACT_DIR}")


if __name__ == "__main__":
    main()
