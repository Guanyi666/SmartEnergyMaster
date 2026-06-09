"""能耗预测微服务 (Epic 6-3)。FastAPI，加载 LSTM 模型对外提供预测接口。

启动: ./.venv/bin/uvicorn prediction_service:app --host 0.0.0.0 --port 8000
接口:
  GET  /health          健康检查
  GET  /model-info      模型元信息(架构/指标/共形系数)
  POST /forecast        单设备预测(传一段历史读数)
  POST /batch-forecast  多设备批量预测
"""
from typing import List, Optional, Union

from fastapi import FastAPI, HTTPException
from pydantic import BaseModel, Field

from predictor import Predictor
import config as C

app = FastAPI(title="SmartEnergy 能耗预测服务", version="1.0.0")
_predictor: Optional[Predictor] = None


def get_predictor() -> Predictor:
    global _predictor
    if _predictor is None:
        _predictor = Predictor()
    return _predictor


@app.on_event("startup")
def _startup():
    get_predictor()   # 启动即加载模型，避免首个请求慢


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
    m = get_predictor().meta
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
    readings = [r.model_dump() for r in req.history]
    forecasts = get_predictor().forecast(readings)
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
