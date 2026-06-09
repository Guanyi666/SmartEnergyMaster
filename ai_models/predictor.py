"""推理封装 (Epic 6-3)：加载已训练 artifacts，对一段历史读数输出未来负荷预测 + 95%CI。

输入只需线上 sensor_data 真实可得的字段（usage_kwh / co2 / nsm / day_of_week / week_status），
内部完成 usage_smooth 派生、日历编码、标准化、未来日历推算、MC Dropout 采样与共形区间。
"""
import json
import numpy as np
import pandas as pd
import torch

import config as C
from features import feature_pipeline as FP
from features.feature_pipeline import _DOW, CALENDAR_FEATURES
from models.lstm_model import LSTMForecaster
import uncertainty as U
import joblib

_WEEKEND_IDX = {5, 6}


def _future_calendar(last_nsm: int, last_dow: int, horizon: int) -> np.ndarray:
    """从最后一条读数推算未来 horizon 步（每步+15min）的日历特征 [horizon, 5]。"""
    feats = []
    nsm, dow = last_nsm, last_dow
    for _ in range(horizon):
        nsm += 900
        if nsm >= 86400:            # 跨天，星期 +1
            nsm -= 86400
            dow = (dow + 1) % 7
        feats.append([
            np.sin(2 * np.pi * nsm / 86400.0),
            np.cos(2 * np.pi * nsm / 86400.0),
            np.sin(2 * np.pi * dow / 7.0),
            np.cos(2 * np.pi * dow / 7.0),
            1.0 if dow in _WEEKEND_IDX else 0.0,
        ])
    arr = np.asarray(feats, dtype=np.float32)
    # 保证列顺序与训练一致
    assert arr.shape[1] == len(CALENDAR_FEATURES)
    return arr


class Predictor:
    def __init__(self):
        self.meta = json.loads((C.ARTIFACT_DIR / "meta.json").read_text())
        self.feat_scaler = joblib.load(C.ARTIFACT_DIR / "feat_scaler.pkl")
        self.target_scaler = joblib.load(C.ARTIFACT_DIR / "target_scaler.pkl")
        self.q = self.meta["ci_conformal_q"]
        self.model = LSTMForecaster(FP.n_dynamic_features(), FP.n_calendar_features(), C.HORIZON,
                                    C.HIDDEN_SIZE, C.NUM_LAYERS, C.DROPOUT)
        self.model.load_state_dict(torch.load(C.ARTIFACT_DIR / "lstm_forecaster.pt"))
        self.model.eval()

    def _to_frame(self, readings: list[dict]) -> pd.DataFrame:
        rows = []
        for r in readings:
            ws = r.get("weekStatus", r.get("week_status"))
            week_status = "Weekday" if str(ws) in ("1", "Weekday") else "Weekend"
            rows.append({
                "Usage_kWh": float(r.get("usageKwh", r.get("usage_kwh"))),
                "CO2(tCO2)": float(r.get("co2Emission", r.get("co2_emission", 0.0)) or 0.0),
                "NSM": int(r.get("nsm")),
                "Day_of_week": str(r.get("dayOfWeek", r.get("day_of_week"))),
                "WeekStatus": week_status,
            })
        df = pd.DataFrame(rows)
        df["usage_smooth"] = df["Usage_kWh"].rolling(C.SMOOTH_WINDOW, min_periods=1).mean()
        return df

    def forecast(self, readings: list[dict]) -> list[dict]:
        if not readings:
            raise ValueError("history 为空")
        df = self._to_frame(readings)
        dyn, _cal, _tgt = FP.transform(df, self.feat_scaler, self.target_scaler)

        # 不足 LOOKBACK 时用最早一条左侧补齐（设备历史尚短时的兜底）
        if len(dyn) < C.LOOKBACK:
            pad = np.repeat(dyn[:1], C.LOOKBACK - len(dyn), axis=0)
            dyn = np.concatenate([pad, dyn], axis=0)
        x_dyn = dyn[-C.LOOKBACK:][None].astype(np.float32)

        last = df.iloc[-1]
        x_fut = _future_calendar(int(last["NSM"]), _DOW.get(last["Day_of_week"], 0), C.HORIZON)[None]

        mean_kwh, std_kwh = U.mc_kwh(self.model, torch.from_numpy(x_dyn),
                                     torch.from_numpy(x_fut), self.target_scaler, C.MC_SAMPLES)
        lo, hi = U.interval(mean_kwh, std_kwh, self.q, floor=0.0)
        return [{
            "minutesAhead": (h + 1) * 15,
            "mean": round(float(mean_kwh[0, h]), 2),
            "lower": round(float(lo[0, h]), 2),
            "upper": round(float(hi[0, h]), 2),
        } for h in range(C.HORIZON)]
