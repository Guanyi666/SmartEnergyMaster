"""
数据获取层 (Data Acquisition Layer)

通过 HTTP 调用 Spring Boot 主后端 API，获取系统的各种参数与数据。
作为 Agent 与业务系统之间的标准化数据桥梁。

环境变量：
  BACKEND_BASE_URL — Spring Boot 后端地址（默认 http://localhost:8080/api）
"""
import os
import time
from typing import Any, Dict, List, Optional
import httpx
from loguru import logger

# Spring Boot 后端地址（优先从环境变量读取，兼容 AgentServiceProcessManager 注入）
BACKEND_BASE_URL = os.getenv("BACKEND_BASE_URL", "http://localhost:8080/api")

# Python 预测服务地址
PREDICTION_BASE_URL = os.getenv("PREDICTION_BASE_URL", "http://localhost:8000")

# 默认请求超时
DEFAULT_TIMEOUT = 10.0

# ==================== 响应校验工具 ====================

def safe_get(data: dict, *keys: str):
    """
    安全获取嵌套字典值，逐个 key 尝试直到找到非空值。
    如果所有 key 都找不到，返回 None 并记录警告。
    用法: safe_get(response, "candidates", "matches", "records")
    """
    for key in keys:
        val = data.get(key) if isinstance(data, dict) else None
        if val is not None:
            return val
    logger.warning(f"safe_get: 所有键 {keys} 均未在响应中找到，响应键: {list(data.keys()) if isinstance(data, dict) else 'N/A'}")
    return None


class DataFetcher:
    """
    数据获取器 — 封装对主后端所有 REST API 的调用。
    支持携带 JWT Token 以继承用户权限。
    """

    def __init__(self, base_url: str = BACKEND_BASE_URL, jwt_token: Optional[str] = None):
        self.base_url = base_url.rstrip("/")
        self.jwt_token = jwt_token
        self._client: Optional[httpx.Client] = None

    @property
    def client(self) -> httpx.Client:
        if self._client is None:
            headers = {"Accept": "application/json"}
            if self.jwt_token:
                headers["Authorization"] = f"Bearer {self.jwt_token}"
            self._client = httpx.Client(headers=headers, timeout=DEFAULT_TIMEOUT)
        return self._client

    def set_token(self, token: str):
        """更新 JWT Token（用户登录后调用）"""
        self.jwt_token = token
        if self._client:
            self._client.headers["Authorization"] = f"Bearer {token}"

    def _get(self, path: str, params: dict = None) -> dict:
        url = f"{self.base_url}{path}"
        start = time.time()
        try:
            r = self.client.get(url, params=params)
            r.raise_for_status()
            elapsed = time.time() - start
            data = r.json()
            # 空响应检测
            if data is None:
                logger.warning(f"GET {url} → 200 but null response ({elapsed:.2f}s)")
                return {}
            if isinstance(data, dict) and len(data) == 0:
                logger.warning(f"GET {url} → 200 but empty dict ({elapsed:.2f}s)")
            if isinstance(data, list) and len(data) == 0:
                logger.debug(f"GET {url} → 200 but empty list ({elapsed:.2f}s)")
            else:
                logger.debug(f"GET {url} → {r.status_code} ({elapsed:.2f}s)")
            return data
        except Exception as e:
            logger.error(f"GET {url} failed: {e}")
            raise

    def _post(self, path: str, json_data: dict = None) -> dict:
        url = f"{self.base_url}{path}"
        try:
            r = self.client.post(url, json=json_data or {})
            r.raise_for_status()
            return r.json()
        except Exception as e:
            logger.error(f"POST {url} failed: {e}")
            raise

    def _patch(self, path: str, json_data: dict = None) -> dict:
        url = f"{self.base_url}{path}"
        try:
            r = self.client.patch(url, json=json_data or {})
            r.raise_for_status()
            return r.json()
        except Exception as e:
            logger.error(f"PATCH {url} failed: {e}")
            raise

    # ==================== 设备管理 ====================

    def get_devices(self, page: int = 1, size: int = 20,
                    device_type: str = None, status: str = None,
                    keyword: str = None) -> dict:
        """查询设备列表（T-001）"""
        params = {"page": page, "size": size}
        if device_type:
            params["type"] = device_type
        if status:
            params["status"] = status
        if keyword:
            params["keyword"] = keyword
        return self._get("/devices", params)

    def get_device_detail(self, device_id: int) -> dict:
        """查询设备详情（T-002）"""
        return self._get(f"/devices/{device_id}")

    def get_device_health(self, device_id: int) -> dict:
        """查询设备健康度评分（T-018）"""
        return self._get(f"/devices/{device_id}/health-score")

    def get_device_fault_history(self, device_id: int) -> dict:
        """查询设备故障历史（T-019）"""
        return self._get(f"/devices/{device_id}/fault-history")

    # ==================== 传感器数据 ====================

    def get_latest_sensor(self, device_code: str) -> dict:
        """查询最新传感器数据（T-003）"""
        return self._get(f"/sensor/latest/{device_code}")

    def get_sensor_history(self, device_code: str, hours: int = 24) -> dict:
        """查询历史传感器数据（T-004）"""
        return self._get(f"/sensor/history/{device_code}", {"hours": hours})

    # ==================== 仪表盘 ====================

    def get_dashboard_summary(self, device_code: str = None) -> dict:
        """大屏聚合总览（T-005）"""
        params = {}
        if device_code:
            params["deviceCode"] = device_code
        return self._get("/dashboard/summary", params)

    def get_dispatch_advice(self, device_code: str) -> dict:
        """调度建议（T-006）"""
        return self._get("/dashboard/dispatch-advice", {"deviceCode": device_code})

    def get_energy_forecast(self, device_code: str) -> dict:
        """能耗预测（T-007）"""
        return self._get("/dashboard/forecast", {"deviceCode": device_code})

    def post_dispatch_decision(self, device_code: str, decision: str) -> dict:
        """记录调度决策（确认/拒绝）"""
        return self._post("/dashboard/dispatch-advice/decision", {
            "deviceCode": device_code, "decision": decision
        })

    # ==================== 工单管理 ====================

    def get_work_orders(self, status: str = None) -> dict:
        """工单列表（T-008）"""
        params = {}
        if status:
            params["status"] = status
        return self._get("/work-orders", params)

    def get_active_alerts(self, limit: int = 5) -> dict:
        """活跃告警（T-009）"""
        return self._get("/work-orders/active-alerts", {"limit": limit})

    def get_work_order_detail(self, order_id: int) -> dict:
        """工单详情（T-010）"""
        return self._get(f"/workorder/orders/{order_id}")

    def update_work_order_status(self, order_id: int, status: str,
                                  assignee: str = None) -> dict:
        """更新工单状态（T-013）"""
        body = {"status": status}
        if assignee:
            body["assignee"] = assignee
        return self._patch(f"/work-orders/{order_id}/status", body)

    def auto_match_personnel(self, fault_type: str, work_order_id: int,
                              top_n: int = 3) -> dict:
        """自动匹配维修人员（T-011）。空 faultType 直接返回空结果。"""
        if not fault_type or not fault_type.strip():
            logger.warning("auto_match_personnel: faultType为空，跳过API调用")
            return {"candidates": [], "faultType": "", "requiredSkills": [], "message": "faultType未指定"}
        return self._get("/workorder/orders/auto-match", {
            "faultType": fault_type.strip(), "workOrderId": work_order_id, "topN": top_n
        })

    def assign_work_order(self, order_id: int, personnel_id: int,
                           role: str = None) -> dict:
        """指派工单（T-012）"""
        body = {"personnelId": personnel_id}
        if role:
            body["role"] = role
        return self._post(f"/workorder/orders/{order_id}/assign", body)

    # ==================== 人员管理 ====================

    def get_personnel_list(self, page: int = 1, size: int = 20,
                           specialization: str = None, skill_level: str = None,
                           on_duty: bool = None) -> dict:
        """人员列表（T-014）"""
        params = {"page": page, "size": size}
        if specialization:
            params["specialization"] = specialization
        if skill_level:
            params["skillLevel"] = skill_level
        if on_duty is not None:
            params["onDuty"] = on_duty
        return self._get("/workorder/personnel", params)

    def get_personnel_detail(self, pid: int) -> dict:
        """人员详情（T-015）"""
        return self._get(f"/workorder/personnel/{pid}")

    def get_dispatch_summary(self) -> dict:
        """调度总览（T-016）"""
        return self._get("/workorder/dashboard/summary")

    def get_dispatch_board(self) -> dict:
        """调度看板（T-017）"""
        return self._get("/workorder/dispatch-board")

    # ==================== 用户/认证 ====================

    def get_users(self, page: int = 1, size: int = 20) -> dict:
        """用户列表"""
        return self._get("/users", {"page": page, "size": size})

    def get_account_settings(self) -> dict:
        """获取当前用户设置"""
        return self._get("/account-settings")

    # ==================== 知识库 ====================

    def get_sops(self, keyword: str = None) -> dict:
        """SOP 列表"""
        params = {}
        if keyword:
            params["keyword"] = keyword
        return self._get("/sops", params)

    def get_cases(self, keyword: str = None, fault_type: str = None) -> dict:
        """维修案例列表"""
        params = {}
        if keyword:
            params["keyword"] = keyword
        if fault_type:
            params["faultType"] = fault_type
        return self._get("/cases", params)

    def get_knowledge_graph(self, device_code: str = None) -> dict:
        """知识图谱"""
        params = {}
        if device_code:
            params["deviceCode"] = device_code
        return self._get("/knowledge/graph", params)

    def get_repair_advice(self, device_code: str, fault_type: str,
                           sensor_snapshot: dict = None) -> dict:
        """AI 维修建议"""
        body = {"deviceCode": device_code, "faultType": fault_type}
        if sensor_snapshot:
            body["sensorSnapshot"] = sensor_snapshot
        return self._post("/ai/repair-advice", body)

    # ==================== 审计日志 ====================

    def get_audit_logs(self, page: int = 1, size: int = 20) -> dict:
        """审计日志列表"""
        return self._get("/audit-logs", {"page": page, "size": size})

    # ==================== 预测服务（Python :8000） ====================

    def call_forecast_service(self, device_code: str,
                               history: List[dict]) -> dict:
        """调用 Python 预测服务（T-021）"""
        import httpx
        try:
            r = httpx.post(
                f"{PREDICTION_BASE_URL}/forecast",
                json={"deviceCode": device_code, "history": history},
                timeout=15.0
            )
            r.raise_for_status()
            return r.json()
        except Exception as e:
            logger.error(f"预测服务调用失败: {e}")
            return {"error": str(e)}

    def get_model_info(self) -> dict:
        """获取预测模型信息（T-022）"""
        import httpx
        try:
            r = httpx.get(f"{PREDICTION_BASE_URL}/model-info", timeout=5.0)
            r.raise_for_status()
            return r.json()
        except Exception as e:
            logger.error(f"模型信息获取失败: {e}")
            return {"error": str(e)}

    def close(self):
        if self._client:
            self._client.close()
            self._client = None


# 全局单例（无 token，用于无需认证的查询）
_fetcher: Optional[DataFetcher] = None


def get_fetcher(jwt_token: str = None) -> DataFetcher:
    global _fetcher
    if _fetcher is None:
        _fetcher = DataFetcher(jwt_token=jwt_token)
    elif jwt_token:
        _fetcher.set_token(jwt_token)
    return _fetcher
