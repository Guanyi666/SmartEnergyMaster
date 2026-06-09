"""LSTM 预测模型 (Epic 6-2)。

结构：LSTM 编码过去窗口 → 取末步隐状态 → 拼上"未来已知日历协变量" → MLP 输出 HORIZON 步。
Dropout 同时服务两个目的：训练正则化 + 推理期 MC Dropout 不确定性估计。
"""
import torch
import torch.nn as nn


class LSTMForecaster(nn.Module):
    def __init__(self, n_dyn_features, n_cal_features, horizon,
                 hidden_size=64, num_layers=2, dropout=0.2):
        super().__init__()
        self.horizon = horizon
        self.lstm = nn.LSTM(
            input_size=n_dyn_features,
            hidden_size=hidden_size,
            num_layers=num_layers,
            batch_first=True,
            dropout=dropout if num_layers > 1 else 0.0,
        )
        self.dropout = nn.Dropout(dropout)
        head_in = hidden_size + n_cal_features * horizon
        self.head = nn.Sequential(
            nn.Linear(head_in, hidden_size),
            nn.ReLU(),
            nn.Dropout(dropout),
            nn.Linear(hidden_size, horizon),
        )

    def forward(self, x_dyn, x_fut):
        # x_dyn: [B, lookback, n_dyn]  x_fut: [B, horizon, n_cal]
        out, _ = self.lstm(x_dyn)
        last = self.dropout(out[:, -1, :])           # 末步隐状态
        fut = x_fut.reshape(x_fut.size(0), -1)        # 展平未来日历协变量
        return self.head(torch.cat([last, fut], dim=1))  # [B, horizon]

    @torch.no_grad()
    def mc_predict(self, x_dyn, x_fut, n_samples=10):
        """MC Dropout：保持 dropout 开启跑 n 次，返回 (mean[B,H], std[B,H])。"""
        self.train()  # 关键：让 dropout 层在推理时仍随机失活
        preds = torch.stack([self(x_dyn, x_fut) for _ in range(n_samples)], dim=0)
        self.eval()
        return preds.mean(dim=0), preds.std(dim=0)
