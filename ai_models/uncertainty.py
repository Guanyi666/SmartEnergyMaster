"""不确定性量化 (Epic 6-2)：MC Dropout + normalized 共形校准。

为什么要校准：MC Dropout 的原始标准差经常系统性高估/低估，直接 ±1.96σ 得到的区间
覆盖率不等于 95%。这里在验证集上做 normalized conformal：
  score_i = |y_i - mean_i| / σ_i,  q = score 的 (1-α) 分位数
测试/线上区间 = mean ± q·σ（下界 clamp 到 0）。这样既保留 MC 区间"该宽则宽"的自适应
形状，又让经验覆盖率对齐到 95%。共形保证在可交换数据上的边际覆盖。
"""
import numpy as np
import torch


@torch.no_grad()
def mc_forward(model, x_dyn, x_fut, n_samples):
    """MC Dropout：保持 dropout 开启采样 n 次，返回标准化空间的 (mean, std)。"""
    was_training = model.training
    model.train()
    preds = torch.stack([model(x_dyn, x_fut) for _ in range(n_samples)], dim=0)
    if not was_training:
        model.eval()
    return preds.mean(dim=0).cpu().numpy(), preds.std(dim=0).cpu().numpy()


def mc_kwh(model, x_dyn, x_fut, target_scaler, n_samples):
    """采样并反标准化到 kWh 尺度，返回 (mean_kwh, std_kwh)。"""
    mean_s, std_s = mc_forward(model, x_dyn, x_fut, n_samples)
    mean_kwh = target_scaler.inverse_transform(mean_s.reshape(-1, 1)).reshape(mean_s.shape)
    std_kwh = std_s * target_scaler.scale_[0]   # StandardScaler 逆变换：std 仅乘 scale_
    return mean_kwh, std_kwh


def calibrate(y_true_kwh, mean_kwh, std_kwh, alpha=0.05, eps=1e-6):
    """在校准集(验证集)上求 normalized 共形系数 q，使 mean±q·σ 覆盖 (1-α)。"""
    scores = np.abs(y_true_kwh - mean_kwh) / (std_kwh + eps)
    return float(np.quantile(scores, 1 - alpha))


def interval(mean_kwh, std_kwh, q, floor=0.0):
    """返回 (下界, 上界)，下界 clamp 到 floor（能耗不为负）。"""
    half = q * std_kwh
    return np.maximum(mean_kwh - half, floor), mean_kwh + half


def coverage(y_true, lo, hi):
    return float(((y_true >= lo) & (y_true <= hi)).mean())
