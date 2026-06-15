"""
Agent 工具定义模块 (Tools Catalog)

将所有 Spring Boot REST API 封装为 LLM 可调用的 Tool 函数。
每个 Tool 包含：name, description, parameters（JSON Schema）, execute 方法。

权限级别：
  L0 = 自主查询，无需人工确认
  L1 = AI 建议，不直接调用 Tool
  L2 = 辅助执行，需人工确认
  L3 = 禁止
"""
from typing import Any, Callable, Dict, List, Optional
from dataclasses import dataclass, field
from data_fetcher import DataFetcher, get_fetcher
from loguru import logger


@dataclass
class ToolParameter:
    """Tool 参数定义"""
    name: str
    type: str = "string"
    description: str = ""
    required: bool = False
    enum: List[str] = None


@dataclass
class Tool:
    """Tool 定义"""
    tool_id: str                # 如 T-001
    name: str                   # 函数名
    description: str            # 功能描述（供 LLM 理解）
    permission_level: str       # L0 / L1 / L2 / L3
    parameters: List[ToolParameter] = field(default_factory=list)
    api_endpoint: str = ""      # 对应的 REST API
    http_method: str = "GET"
    category: str = "data_query"  # 工具组分类

    def to_openai_tool(self) -> dict:
        """转为 OpenAI Function Calling 格式"""
        props = {}
        required = []
        for p in self.parameters:
            prop_def = {"type": p.type, "description": p.description}
            if p.enum:
                prop_def["enum"] = p.enum
            props[p.name] = prop_def
            if p.required:
                required.append(p.name)
        return {
            "type": "function",
            "function": {
                "name": self.name,
                "description": self.description,
                "parameters": {
                    "type": "object",
                    "properties": props,
                    "required": required,
                }
            }
        }


# ==================== 执行函数 ====================

def _exec_query_device_list(fetcher: DataFetcher, **kwargs) -> dict:
    return fetcher.get_devices(
        page=kwargs.get("page", 1),
        size=kwargs.get("size", 20),
        device_type=kwargs.get("type"),
        status=kwargs.get("status"),
        keyword=kwargs.get("keyword")
    )


def _exec_query_device_detail(fetcher: DataFetcher, **kwargs) -> dict:
    return fetcher.get_device_detail(kwargs["deviceId"])


def _exec_query_latest_sensor(fetcher: DataFetcher, **kwargs) -> dict:
    return fetcher.get_latest_sensor(kwargs["deviceCode"])


def _exec_query_sensor_history(fetcher: DataFetcher, **kwargs) -> dict:
    return fetcher.get_sensor_history(
        kwargs["deviceCode"],
        hours=kwargs.get("hours", 24)
    )


def _exec_query_dashboard_summary(fetcher: DataFetcher, **kwargs) -> dict:
    return fetcher.get_dashboard_summary(kwargs.get("deviceCode"))


def _exec_get_dispatch_advice(fetcher: DataFetcher, **kwargs) -> dict:
    return fetcher.get_dispatch_advice(kwargs["deviceCode"])


def _exec_get_energy_forecast(fetcher: DataFetcher, **kwargs) -> dict:
    return fetcher.get_energy_forecast(kwargs["deviceCode"])


def _exec_query_work_orders(fetcher: DataFetcher, **kwargs) -> dict:
    return fetcher.get_work_orders(kwargs.get("status"))


def _exec_query_active_alerts(fetcher: DataFetcher, **kwargs) -> dict:
    return fetcher.get_active_alerts(kwargs.get("limit", 5))


def _exec_query_work_order_detail(fetcher: DataFetcher, **kwargs) -> dict:
    return fetcher.get_work_order_detail(kwargs["workOrderId"])


def _exec_auto_match_personnel(fetcher: DataFetcher, **kwargs) -> dict:
    return fetcher.auto_match_personnel(
        kwargs["faultType"],
        kwargs.get("workOrderId", 0),
        kwargs.get("topN", 3)
    )


def _exec_assign_work_order(fetcher: DataFetcher, **kwargs) -> dict:
    return fetcher.assign_work_order(
        kwargs["workOrderId"],
        kwargs["personnelId"],
        kwargs.get("role")
    )


def _exec_update_work_order_status(fetcher: DataFetcher, **kwargs) -> dict:
    return fetcher.update_work_order_status(
        kwargs["workOrderId"],
        kwargs["status"],
        kwargs.get("assignee")
    )


def _exec_query_personnel_list(fetcher: DataFetcher, **kwargs) -> dict:
    return fetcher.get_personnel_list(
        page=kwargs.get("page", 1),
        size=kwargs.get("size", 20),
        specialization=kwargs.get("specialization"),
        skill_level=kwargs.get("skillLevel"),
        on_duty=kwargs.get("onDuty")
    )


def _exec_query_personnel_detail(fetcher: DataFetcher, **kwargs) -> dict:
    return fetcher.get_personnel_detail(kwargs["personnelId"])


def _exec_query_dispatch_summary(fetcher: DataFetcher, **kwargs) -> dict:
    return fetcher.get_dispatch_summary()


def _exec_query_dispatch_board(fetcher: DataFetcher, **kwargs) -> dict:
    return fetcher.get_dispatch_board()


def _exec_query_device_health(fetcher: DataFetcher, **kwargs) -> dict:
    return fetcher.get_device_health(kwargs["deviceId"])


def _exec_query_fault_history(fetcher: DataFetcher, **kwargs) -> dict:
    return fetcher.get_device_fault_history(kwargs["deviceId"])


def _exec_forecast_energy(fetcher: DataFetcher, **kwargs) -> dict:
    return fetcher.call_forecast_service(
        kwargs["deviceCode"],
        kwargs.get("history", [])
    )


def _exec_get_model_info(fetcher: DataFetcher, **kwargs) -> dict:
    return fetcher.get_model_info()


def _exec_query_users(fetcher: DataFetcher, **kwargs) -> dict:
    return fetcher.get_users(
        page=kwargs.get("page", 1),
        size=kwargs.get("size", 20)
    )


def _exec_query_sops(fetcher: DataFetcher, **kwargs) -> dict:
    return fetcher.get_sops(kwargs.get("keyword"))


def _exec_query_cases(fetcher: DataFetcher, **kwargs) -> dict:
    return fetcher.get_cases(
        keyword=kwargs.get("keyword"),
        fault_type=kwargs.get("faultType")
    )


def _exec_query_knowledge_graph(fetcher: DataFetcher, **kwargs) -> dict:
    return fetcher.get_knowledge_graph(kwargs.get("deviceCode"))


def _exec_get_repair_advice(fetcher: DataFetcher, **kwargs) -> dict:
    return fetcher.get_repair_advice(
        kwargs["deviceCode"],
        kwargs["faultType"],
        kwargs.get("sensorSnapshot")
    )


# ==================== Tool 注册表 ====================

# 所有已注册的 Tool
TOOL_REGISTRY: Dict[str, Tool] = {}

# Tool 名称到执行函数的映射
TOOL_EXECUTORS: Dict[str, Callable] = {}


def _register(tool: Tool, executor: Callable):
    TOOL_REGISTRY[tool.tool_id] = tool
    TOOL_REGISTRY[tool.name] = tool  # 同时按名称索引
    TOOL_EXECUTORS[tool.name] = executor


def _tp(name: str, type_str: str = "string", desc: str = "", required: bool = False, enum: list = None) -> ToolParameter:
    return ToolParameter(name=name, type=type_str, description=desc, required=required, enum=enum)


# ==================== 1. 数据查询工具组（7 个） ====================

_register(
    Tool("T-001", "query_device_list", "查询所有设备列表，获取实时状态、最新传感器数据和活跃工单数。可按类型、状态、关键词筛选。",
         "L0", [
             _tp("page", "integer", "页码（默认1）"),
             _tp("size", "integer", "每页条数（默认20）"),
             _tp("type", "string", "设备类型过滤：ARC_FURNACE/PUMP/COMPRESSOR"),
             _tp("status", "string", "状态过滤：RUNNING/STOPPED/FAULT/OFFLINE"),
             _tp("keyword", "string", "设备名称/编码搜索"),
         ], "/api/devices", "GET", "data_query"),
    _exec_query_device_list
)

_register(
    Tool("T-002", "query_device_detail", "查询指定设备的详细信息，含实时传感器数据和状态。",
         "L0", [
             _tp("deviceId", "integer", "设备ID", required=True),
         ], "/api/devices/{deviceId}", "GET", "data_query"),
    _exec_query_device_detail
)

_register(
    Tool("T-003", "query_latest_sensor", "查询指定设备的最新一条传感器数据（温度、振动、压力、功耗、CO₂排放等）。",
         "L0", [
             _tp("deviceCode", "string", "设备编码，如 EAF-01", required=True),
         ], "/api/sensor/latest/{deviceCode}", "GET", "data_query"),
    _exec_query_latest_sensor
)

_register(
    Tool("T-004", "query_sensor_history", "查询设备指定时间窗口内的历史传感器时序数据，用于趋势分析和图表绘制。",
         "L0", [
             _tp("deviceCode", "string", "设备编码", required=True),
             _tp("hours", "integer", "查询最近多少小时的数据（默认24）"),
         ], "/api/sensor/history/{deviceCode}", "GET", "data_query"),
    _exec_query_sensor_history
)

_register(
    Tool("T-005", "query_dashboard_summary", "查询全厂聚合总览：总功耗、累计碳排放、当前电价区间、运行/离线设备数、活跃告警数。",
         "L0", [
             _tp("deviceCode", "string", "焦点设备编码（可选）"),
         ], "/api/dashboard/summary", "GET", "data_query"),
    _exec_query_dashboard_summary
)

_register(
    Tool("T-006", "get_dispatch_advice", "获取基于实时数据+电价时段的4级调度建议（CRITICAL/WARN/GOOD/INFO）。",
         "L0", [
             _tp("deviceCode", "string", "设备编码", required=True),
         ], "/api/dashboard/dispatch-advice", "GET", "data_query"),
    _exec_get_dispatch_advice
)

_register(
    Tool("T-007", "get_energy_forecast", "查询设备未来30分钟的LSTM能耗预测曲线及95%置信区间。",
         "L0", [
             _tp("deviceCode", "string", "设备编码", required=True),
         ], "/api/dashboard/forecast", "GET", "data_query"),
    _exec_get_energy_forecast
)

# ==================== 2. 工单管理工具组（6 个） ====================

_register(
    Tool("T-008", "query_work_orders", "查询维修工单列表，可按状态筛选（PENDING/IN_PROGRESS/RESOLVED）。",
         "L0", [
             _tp("status", "string", "工单状态过滤"),
         ], "/api/work-orders", "GET", "work_order"),
    _exec_query_work_orders
)

_register(
    Tool("T-009", "query_active_alerts", "获取当前活跃告警汇总（PENDING和IN_PROGRESS状态的工单），按创建时间降序。",
         "L0", [
             _tp("limit", "integer", "返回条数（默认5）"),
         ], "/api/work-orders/active-alerts", "GET", "work_order"),
    _exec_query_active_alerts
)

_register(
    Tool("T-010", "query_work_order_detail", "查询工单详情：设备信息、故障类型、优先级、指派历史、处理时间线。",
         "L0", [
             _tp("workOrderId", "integer", "工单ID", required=True),
         ], "/api/workorder/orders/{workOrderId}", "GET", "work_order"),
    _exec_query_work_order_detail
)

_register(
    Tool("T-011", "auto_match_personnel", "根据故障类型自动匹配最合适的维修人员，返回候选人排行榜（含评分和理由）。",
         "L0", [
             _tp("faultType", "string", "故障类型", required=True),
             _tp("workOrderId", "integer", "关联工单ID"),
             _tp("topN", "integer", "返回前N名（默认3）"),
         ], "/api/workorder/orders/auto-match", "GET", "work_order"),
    _exec_auto_match_personnel
)

_register(
    Tool("T-012", "assign_work_order", "将工单指派给指定维修人员。⚠️ 需要人工确认后才执行。",
         "L2", [
             _tp("workOrderId", "integer", "工单ID", required=True),
             _tp("personnelId", "integer", "人员ID", required=True),
             _tp("role", "string", "指派角色"),
         ], "/api/workorder/orders/{id}/assign", "POST", "work_order"),
    _exec_assign_work_order
)

_register(
    Tool("T-013", "update_work_order_status", "更新工单状态（PENDING→IN_PROGRESS→RESOLVED）。⚠️ 需要人工确认后才执行。",
         "L2", [
             _tp("workOrderId", "integer", "工单ID", required=True),
             _tp("status", "string", "目标状态", required=True, enum=["IN_PROGRESS", "RESOLVED"]),
             _tp("assignee", "string", "指派人姓名"),
         ], "/api/work-orders/{id}/status", "PATCH", "work_order"),
    _exec_update_work_order_status
)

# ==================== 3. 人员管理工具组（4 个） ====================

_register(
    Tool("T-014", "query_personnel_list", "查询维修人员列表，可按专业、技能等级、在岗状态筛选。",
         "L0", [
             _tp("page", "integer", "页码"),
             _tp("size", "integer", "每页条数"),
             _tp("specialization", "string", "专业方向"),
             _tp("skillLevel", "string", "技能等级"),
             _tp("onDuty", "boolean", "是否在岗"),
         ], "/api/workorder/personnel", "GET", "personnel"),
    _exec_query_personnel_list
)

_register(
    Tool("T-015", "query_personnel_detail", "查询指定维修人员的详细信息。",
         "L0", [
             _tp("personnelId", "integer", "人员ID", required=True),
         ], "/api/workorder/personnel/{id}", "GET", "personnel"),
    _exec_query_personnel_detail
)

_register(
    Tool("T-016", "query_dispatch_summary", "查询维修调度总览统计（待处理工单数、在岗人数、负载分布等）。",
         "L0", [], "/api/workorder/dashboard/summary", "GET", "personnel"),
    _exec_query_dispatch_summary
)

_register(
    Tool("T-017", "query_dispatch_board", "查询维修调度看板完整数据。",
         "L0", [], "/api/workorder/dispatch-board", "GET", "personnel"),
    _exec_query_dispatch_board
)

# ==================== 4. 设备管理工具组（3 个） ====================

_register(
    Tool("T-018", "query_device_health", "查询设备健康度评分（0-100分）及衰退趋势。",
         "L0", [
             _tp("deviceId", "integer", "设备ID", required=True),
         ], "/api/devices/{id}/health-score", "GET", "device_mgmt"),
    _exec_query_device_health
)

_register(
    Tool("T-019", "query_fault_history", "查询指定设备的历史故障记录列表。",
         "L0", [
             _tp("deviceId", "integer", "设备ID", required=True),
         ], "/api/devices/{id}/fault-history", "GET", "device_mgmt"),
    _exec_query_fault_history
)

_register(
    Tool("T-020", "query_users", "查询系统用户列表。",
         "L0", [
             _tp("page", "integer", "页码"),
             _tp("size", "integer", "每页条数"),
         ], "/api/users", "GET", "device_mgmt"),
    _exec_query_users
)

# ==================== 5. 预测分析工具组（2 个） ====================

_register(
    Tool("T-021", "forecast_energy", "调用LSTM预测模型，基于历史数据预测未来30分钟能耗趋势。",
         "L0", [
             _tp("deviceCode", "string", "设备编码", required=True),
             _tp("history", "array", "历史传感器读数数组"),
         ], "/forecast (Python :8000)", "POST", "prediction"),
    _exec_forecast_energy
)

_register(
    Tool("T-022", "get_model_info", "获取当前LSTM预测模型的元信息（架构、精度指标、共形系数）。",
         "L0", [], "/model-info (Python :8000)", "GET", "prediction"),
    _exec_get_model_info
)

# ==================== 6. 知识库工具组（3 个） ====================

_register(
    Tool("T-023", "query_sops", "查询标准操作流程（SOP）列表。支持关键词搜索。",
         "L0", [
             _tp("keyword", "string", "搜索关键词"),
         ], "/api/sops", "GET", "knowledge"),
    _exec_query_sops
)

_register(
    Tool("T-024", "query_cases", "查询历史维修案例库，支持按关键词和故障类型检索。",
         "L0", [
             _tp("keyword", "string", "搜索关键词"),
             _tp("faultType", "string", "故障类型"),
         ], "/api/cases", "GET", "knowledge"),
    _exec_query_cases
)

_register(
    Tool("T-025", "query_knowledge_graph", "查询设备-故障-部件-方案知识图谱。",
         "L0", [
             _tp("deviceCode", "string", "设备编码（可选）"),
         ], "/api/knowledge/graph", "GET", "knowledge"),
    _exec_query_knowledge_graph
)


# ==================== Tool 执行器 ====================

def execute_tool(tool_name: str, arguments: dict, jwt_token: str = None) -> dict:
    """
    执行指定的 Tool。
    对于 L2 级别的 Tool，返回需人工确认的标记。
    """
    tool = TOOL_REGISTRY.get(tool_name)
    if not tool:
        return {"error": f"未找到 Tool: {tool_name}"}

    executor = TOOL_EXECUTORS.get(tool_name)
    if not executor:
        return {"error": f"Tool {tool_name} 没有注册执行函数"}

    fetcher = get_fetcher(jwt_token)

    try:
        result = executor(fetcher, **arguments)

        # L2 Tool 包装确认标记
        if tool.permission_level == "L2":
            return {
                "requires_confirmation": True,
                "tool_name": tool_name,
                "tool_id": tool.tool_id,
                "arguments": arguments,
                "result_preview": result,
                "message": f"⚠️ 此操作（{tool.description[:50]}...）需要人工确认后才执行。"
            }

        return {"success": True, "data": result}

    except Exception as e:
        logger.error(f"Tool {tool_name} 执行失败: {e}")
        return {"error": str(e), "tool_name": tool_name}


def get_all_tools_for_llm() -> List[dict]:
    """获取所有 L0 级 Tool 的 OpenAI Function Calling 格式定义（供 LLM 使用）"""
    return [t.to_openai_tool() for t in TOOL_REGISTRY.values()
            if isinstance(t, Tool) and t.tool_id.startswith("T-")]


def get_tools_by_category() -> Dict[str, List[Tool]]:
    """按工具组分类返回所有 Tool"""
    cats = {}
    for t in TOOL_REGISTRY.values():
        if isinstance(t, Tool) and t.tool_id.startswith("T-"):
            cats.setdefault(t.category, []).append(t)
    return cats


def get_tools_summary() -> str:
    """生成 Tool 目录摘要（供 System Prompt 使用）"""
    lines = ["## 可用工具目录\n"]
    by_cat = get_tools_by_category()
    cat_names = {
        "data_query": "📊 数据查询",
        "work_order": "🔧 工单管理",
        "personnel": "👷 人员管理",
        "device_mgmt": "⚙️ 设备管理",
        "prediction": "🔮 预测分析",
        "knowledge": "📚 知识库",
    }
    for cat, tools in by_cat.items():
        cat_label = cat_names.get(cat, cat)
        lines.append(f"\n### {cat_label}")
        for t in tools:
            perm_icon = {"L0": "🟢", "L1": "🟡", "L2": "🟠"}.get(t.permission_level, "🔴")
            lines.append(f"- {perm_icon} **{t.name}** ({t.tool_id}): {t.description}")
    return "\n".join(lines)
