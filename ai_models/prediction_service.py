"""能耗预测微服务 (Epic 6-3)。FastAPI，加载 LSTM 模型对外提供预测接口。

启动方式:
  python prediction_service.py                              # 直接启动 (端口 8000)
  uvicorn prediction_service:app --host 0.0.0.0 --port 8000  # 或 uvicorn CLI

接口:
  GET  /health          健康检查 (模型未训练时仍可用)
  GET  /model-info      模型元信息(架构/指标/共形系数)
  POST /forecast        单设备预测(传一段历史读数)
  POST /batch-forecast  多设备批量预测
"""
from contextlib import asynccontextmanager
from typing import List, Optional, Union

import uvicorn
from fastapi import FastAPI, HTTPException
from pydantic import BaseModel, Field

import config as C
from predictor import Predictor


@asynccontextmanager
async def lifespan(app: FastAPI):
    # 启动时不强制加载模型（避免 training artifacts 缺失时启动失败）。
    # 模型在首次 /forecast 请求时懒加载；若 artifacts 不完整会返回 503。
    yield


app = FastAPI(title="SmartEnergy 能耗预测服务", version="1.0.0", lifespan=lifespan)
_predictor: Optional[Predictor] = None
_predictor_error: Optional[str] = None


def get_predictor() -> Predictor:
    global _predictor, _predictor_error
    if _predictor is None and _predictor_error is None:
        try:
            _predictor = Predictor()
        except Exception as e:
            _predictor_error = str(e)
    return _predictor


class Reading(BaseModel):
    usageKwh: float
    co2Emission: Optional[float] = 0.0
    nsm: int
    dayOfWeek: str
    weekStatus: Union[int, str]

    model_config = {"extra": "ignore"}


class ForecastRequest(BaseModel):
    deviceCode: str
    history: List[Reading] = Field(..., description=f"按时间升序的历史读数，建议 ≥{C.LOOKBACK} 条")


class BatchForecastRequest(BaseModel):
    items: List[ForecastRequest]


@app.get("/health")
def health():
    return {"status": "UP"}


@app.get("/model-info")
def model_info():
    p = get_predictor()
    if p is None:
        raise HTTPException(status_code=503, detail=_predictor_error or "模型未加载")
    m = p.meta
    return {
        "lookback": m["lookback"], "horizon": m["horizon"],
        "target": "usage_smooth(90min 滑动平均负荷)",
        "testMape": round(m["test_mape"], 2),
        "perStepMape": [round(x, 2) for x in m["per_step_mape"]],
        "rmse": round(m["rmse"], 2),
        "ciCoverage": round(m.get("ci_coverage_test", 0) * 100, 1),
        "conformalQ": round(m["ci_conformal_q"], 3),
    }


def _one(req: ForecastRequest) -> dict:
    p = get_predictor()
    if p is None:
        raise HTTPException(status_code=503, detail=_predictor_error or "模型未加载")
    readings = [r.model_dump() for r in req.history]
    # 输入归一化：训练数据 ~0-150，新数据 ~0-15000，÷100 对齐分布
    SCALE = 100.0
    for r in readings:
        r["usageKwh"] = float(r.get("usageKwh", 0)) / SCALE
        r["co2Emission"] = float(r.get("co2Emission", 0)) / SCALE
    forecasts = p.forecast(readings)
    # 反归一化
    for f in forecasts:
        f["mean"] = round(f["mean"] * SCALE, 2)
        f["lower"] = round(f["lower"] * SCALE, 2)
        f["upper"] = round(f["upper"] * SCALE, 2)
    return {"deviceCode": req.deviceCode, "forecasts": forecasts}


@app.post("/forecast")
def forecast(req: ForecastRequest):
    try:
        return _one(req)
    except Exception as e:
        raise HTTPException(status_code=400, detail=str(e))


@app.post("/batch-forecast")
def batch_forecast(req: BatchForecastRequest):
    results = []
    for item in req.items:
        try:
            results.append(_one(item))
        except Exception as e:
            results.append({"deviceCode": item.deviceCode, "error": str(e)})
    return {"results": results}


if __name__ == "__main__":
    uvicorn.run("prediction_service:app", host="0.0.0.0", port=8000, reload=False)
