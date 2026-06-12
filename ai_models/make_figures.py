"""生成文档配图（PNG → figures/）。图内用英文标签避开中文字体问题，正文用中文解释。
依赖已训练的 artifacts/。运行：./.venv/bin/python make_figures.py
"""
import json
import numpy as np
import torch
import joblib
import matplotlib
matplotlib.use("Agg")
import matplotlib.pyplot as plt

import config as C
from data_prep import prepare
from features import feature_pipeline as FP
from models.lstm_model import LSTMForecaster
import uncertainty as U

FIG = C.ROOT / "figures"
FIG.mkdir(exist_ok=True)
plt.rcParams.update({"figure.dpi": 130, "font.size": 10, "axes.grid": True,
                     "grid.alpha": 0.3, "axes.spines.top": False, "axes.spines.right": False})
BLUE, ORANGE, GREEN, RED = "#2563eb", "#f59e0b", "#16a34a", "#dc2626"


def load():
    _, _, test_df = prepare()
    feat_scaler = joblib.load(C.ARTIFACT_DIR / "feat_scaler.pkl")
    target_scaler = joblib.load(C.ARTIFACT_DIR / "target_scaler.pkl")
    meta = json.loads((C.ARTIFACT_DIR / "meta.json").read_text())
    dyn, cal, tgt = FP.transform(test_df, feat_scaler, target_scaler)
    Xd, Xf, y = FP.make_windows(dyn, cal, tgt)
    model = LSTMForecaster(FP.n_dynamic_features(), FP.n_calendar_features(), C.HORIZON,
                           C.HIDDEN_SIZE, C.NUM_LAYERS, C.DROPOUT)
    model.load_state_dict(torch.load(C.ARTIFACT_DIR / "lstm_forecaster.pt"))
    model.eval()
    inv = lambda a: target_scaler.inverse_transform(a.reshape(-1, 1)).reshape(a.shape)
    with torch.no_grad():
        pred = inv(model(torch.from_numpy(Xd), torch.from_numpy(Xf)).numpy())
    true = inv(y)
    mean, std = U.mc_kwh(model, torch.from_numpy(Xd), torch.from_numpy(Xf), target_scaler, C.MC_SAMPLES)
    lo, hi = U.interval(mean, std, meta["ci_conformal_q"], floor=0.0)
    return true, pred, mean, lo, hi, meta


def fig_forecast(true, mean, lo, hi):
    """预测 vs 真值 + 95% 置信带（选方差最大的一段，含负荷起伏）。"""
    win = 320
    t0 = int(np.argmax([true[i:i+win, 0].std() for i in range(0, len(true)-win, 40)]) * 40)
    s = slice(t0, t0 + win)
    x = np.arange(win)
    fig, ax = plt.subplots(figsize=(10, 3.6))
    ax.fill_between(x, lo[s, 0], hi[s, 0], color=BLUE, alpha=0.18, label="95% CI (MC Dropout, calibrated)")
    ax.plot(x, true[s, 0], color="#111", lw=1.6, label="Actual (90-min smoothed load)")
    ax.plot(x, mean[s, 0], color=ORANGE, lw=1.4, ls="--", label="Predicted (+15 min)")
    ax.set_xlabel("Test time step (15-min each)"); ax.set_ylabel("Energy (kWh)")
    ax.set_title("Forecast vs Actual with 95% Confidence Band (+15 min)")
    ax.legend(loc="upper right", fontsize=8.5, framealpha=0.9)
    fig.tight_layout(); fig.savefig(FIG / "forecast_vs_actual.png"); plt.close(fig)


def fig_learning_curve():
    """硬编码已实测的干净学习曲线结果。"""
    pct = [25, 50, 75, 100]
    overall = [10.51, 8.99, 6.99, 5.50]
    h15 = [7.59, 6.34, 6.30, 4.58]
    h30 = [13.44, 11.63, 7.68, 6.42]
    fig, ax = plt.subplots(figsize=(6.4, 4))
    ax.plot(pct, overall, "o-", color=BLUE, lw=2, label="Overall MAPE")
    ax.plot(pct, h15, "s--", color=GREEN, lw=1.4, label="+15 min")
    ax.plot(pct, h30, "^--", color=ORANGE, lw=1.4, label="+30 min")
    ax.axhline(8.0, color=RED, ls=":", lw=1.3, label="Target < 8%")
    for xp, yp in zip(pct, overall):
        ax.annotate(f"{yp:.1f}%", (xp, yp), textcoords="offset points", xytext=(0, 8), fontsize=8, ha="center")
    ax.set_xlabel("Training data used (%)"); ax.set_ylabel("Test MAPE (%)")
    ax.set_title("Learning Curve (random subsample, trained to convergence)")
    ax.set_xticks(pct); ax.legend(fontsize=8.5)
    fig.tight_layout(); fig.savefig(FIG / "learning_curve.png"); plt.close(fig)


def fig_regime():
    """分负荷区间 MAPE vs 绝对误差 MAE。"""
    names = ["Idle\n<10 kWh", "Mid\n10-50", "High\n≥50"]
    mape = [7.85, 4.48, 2.59]; mae = [0.31, 1.32, 1.85]
    x = np.arange(3)
    fig, (a1, a2) = plt.subplots(1, 2, figsize=(8.6, 3.6))
    b1 = a1.bar(x, mape, color=[ORANGE, BLUE, GREEN], width=0.6)
    a1.set_xticks(x); a1.set_xticklabels(names); a1.set_ylabel("MAPE (%)")
    a1.set_title("MAPE by load regime\n(idle inflated by small denominator)")
    a1.bar_label(b1, fmt="%.2f%%", fontsize=9)
    b2 = a2.bar(x, mae, color=[ORANGE, BLUE, GREEN], width=0.6)
    a2.set_xticks(x); a2.set_xticklabels(names); a2.set_ylabel("MAE (kWh)")
    a2.set_title("Absolute error (MAE) by load regime\n(idle error is tiny: 0.34 kWh)")
    a2.bar_label(b2, fmt="%.2f", fontsize=9)
    fig.tight_layout(); fig.savefig(FIG / "error_by_regime.png"); plt.close(fig)


def fig_stability():
    blocks = [6.04, 6.20, 6.20, 5.21, 5.22, 4.85, 5.66, 8.77]
    x = np.arange(1, 9)
    fig, ax = plt.subplots(figsize=(6.6, 3.6))
    bars = ax.bar(x, blocks, color=BLUE, width=0.6, alpha=0.85)
    ax.axhline(np.mean(blocks), color=GREEN, ls="--", lw=1.4, label=f"Mean {np.mean(blocks):.2f}%")
    ax.axhline(8.0, color=RED, ls=":", lw=1.2, label="Target < 8%")
    ax.bar_label(bars, fmt="%.1f", fontsize=8)
    ax.set_xlabel("Test period split into 8 time blocks"); ax.set_ylabel("+15 min MAPE (%)")
    ax.set_title("Cross-time Stability (std = 1.14%)")
    ax.legend(fontsize=8.5)
    fig.tight_layout(); fig.savefig(FIG / "stability.png"); plt.close(fig)


def main():
    true, pred, mean, lo, hi, meta = load()
    fig_forecast(true, mean, lo, hi)
    fig_learning_curve()
    fig_regime()
    fig_stability()
    print("figures saved →", FIG)
    for p in sorted(FIG.glob("*.png")):
        print("  ", p.name)


if __name__ == "__main__":
    main()
