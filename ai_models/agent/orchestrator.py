"""
主控协调器 (Agent Orchestrator)

系统级智能体的核心大脑，负责：
  1. 意图识别 (Intent Parser) — 解析用户自然语言，识别意图类别
  2. 任务规划 (Task Planner) — 将复杂请求分解为子任务序列
  3. Agent 路由 (Agent Router) — 将子任务分配给最合适的子 Agent
  4. 结果聚合 (Result Aggregator) — 收集子 Agent 结果，组装最终回复
  5. 上下文管理 — 通过 MemoryManager 管理对话上下文

协作机制：
  - Task Delegate: 主控 → 子 Agent（任务委托）
  - Result Callback: 子 Agent → 主控（结果回调）
  - Context Share: 所有 Agent ↔ MemoryManager（共享记忆）
  - Human Interrupt: 任意节点 → 用户（人工中断确认）
"""
import json
import re
from typing import Any, Dict, List, Optional, Tuple
from dataclasses import dataclass, field
from loguru import logger

try:
    from .sub_agents import (
        ProductionMonitorAgent,
        FaultDiagnosisAgent,
        MaintenanceDispatchAgent,
        EnergyOptimizationAgent,
        ManagementDecisionAgent,
        KnowledgeAgent,
    )
    from .memory import MemoryManager, get_memory_manager
    from .tools import (
        TOOL_REGISTRY, TOOL_EXECUTORS, execute_tool,
        get_all_tools_for_llm, get_tools_summary
    )
    from .data_fetcher import get_fetcher
except ImportError:
    from sub_agents import (
        ProductionMonitorAgent,
        FaultDiagnosisAgent,
        MaintenanceDispatchAgent,
        EnergyOptimizationAgent,
        ManagementDecisionAgent,
        KnowledgeAgent,
    )
    from memory import MemoryManager, get_memory_manager
    from tools import (
        TOOL_REGISTRY, TOOL_EXECUTORS, execute_tool,
        get_all_tools_for_llm, get_tools_summary
    )
    from data_fetcher import get_fetcher


# ==================== 意图分类 ====================

# 意图 → (主Agent, 子意图编号前缀)
INTENT_ROUTING = {
    # 生产监控
    "设备状态": ("production_monitor", "PM-01"),
    "全厂状态": ("production_monitor", "PM-01"),
    "传感器数据": ("production_monitor", "PM-02"),
    "最新数据": ("production_monitor", "PM-02"),
    "温度": ("production_monitor", "PM-02"),
    "振动": ("production_monitor", "PM-02"),
    "压力": ("production_monitor", "PM-02"),
    "趋势": ("production_monitor", "PM-05"),
    "历史数据": ("production_monitor", "PM-05"),
    "预测": ("energy_optimization", "EO-03"),

    # 故障诊断
    "故障": ("fault_diagnosis", "FD-01"),
    "异常": ("fault_diagnosis", "FD-01"),
    "诊断": ("fault_diagnosis", "FD-06"),
    "根因": ("fault_diagnosis", "FD-05"),
    "健康度": ("fault_diagnosis", "FD-04"),
    "交叉验证": ("fault_diagnosis", "FD-02"),
    "报警": ("fault_diagnosis", "FD-01"),
    "告警": ("fault_diagnosis", "FD-01"),

    # 维修调度
    "工单": ("maintenance_dispatch", "MD-01"),
    "维修": ("maintenance_dispatch", "MD-01"),
    "指派": ("maintenance_dispatch", "MD-05"),
    "派工": ("maintenance_dispatch", "MD-05"),
    "推荐人员": ("maintenance_dispatch", "MD-05"),
    "负载": ("maintenance_dispatch", "MD-08"),
    "调度": ("maintenance_dispatch", "MD-01"),

    # 能耗优化
    "能耗": ("energy_optimization", "EO-01"),
    "电价": ("energy_optimization", "EO-01"),
    "节能": ("energy_optimization", "EO-04"),
    "节省": ("energy_optimization", "EO-04"),
    "推迟": ("energy_optimization", "EO-04"),
    "成本": ("energy_optimization", "EO-01"),

    # 管理决策
    "日报": ("management_decision", "MG-01"),
    "周报": ("management_decision", "MG-02"),
    "报表": ("management_decision", "MG-01"),
    "报告": ("management_decision", "MG-01"),
    "风险": ("management_decision", "MG-05"),
    "KPI": ("management_decision", "MG-01"),
    "预警": ("management_decision", "MG-05"),

    # 知识管理
    "手册": ("knowledge_agent", "KM-01"),
    "SOP": ("knowledge_agent", "KM-03"),
    "案例": ("knowledge_agent", "KM-02"),
    "政策": ("knowledge_agent", "KM-04"),
    "培训": ("knowledge_agent", "KM-05"),
    "帮助": ("knowledge_agent", "KM-01"),
    "怎么": ("knowledge_agent", "KM-01"),
    "什么是": ("knowledge_agent", "KM-01"),
}


@dataclass
class OrchestrationResult:
    """编排结果"""
    intent: str                        # 识别到的意图
    agent_name: str                    # 处理该意图的子Agent名
    agent_level: str                   # Agent权限级别
    sub_intent: str                    # 子意图编号
    extracted_params: dict             # 提取的参数
    direct_response: str = ""          # 直接回复（简单意图）
    tool_calls_needed: List[str] = field(default_factory=list)  # 需要调用的Tool
    needs_confirmation: bool = False   # 是否需要人工确认


class AgentOrchestrator:
    """
    主控协调器 — 多Agent系统的总控大脑。

    使用方式：
        orch = AgentOrchestrator(jwt_token="...")
        result = await orch.process("EAF-01温度异常，帮我看看")
        print(result)

    权限模型：
        L0 — 仅查询（任何角色可用）
        L1 — 建议输出（AI 生成建议文本）
        L2 — 辅助执行（AI 准备操作，需操作员确认后才执行）
        L3 — 完全自主执行（数据查询、报表生成、异常巡检、阈值告警）
        禁止 — 紧急停机、设备物理参数修改
    """

    def __init__(self, jwt_token: str = None, session_id: str = None,
                 user_id: str = "", user_role: str = "OPERATOR",
                 auto_execute_level: str = "L0"):
        self.jwt_token = jwt_token
        self.user_id = user_id
        self.user_role = user_role
        self.auto_execute_level = auto_execute_level.upper()  # L0/L1/L2/L3
        self.memory = get_memory_manager()
        self.fetcher = get_fetcher(jwt_token)

        # 初始化所有子Agent
        self.sub_agents = {
            "production_monitor": ProductionMonitorAgent(jwt_token),
            "fault_diagnosis": FaultDiagnosisAgent(jwt_token),
            "maintenance_dispatch": MaintenanceDispatchAgent(jwt_token),
            "energy_optimization": EnergyOptimizationAgent(jwt_token),
            "management_decision": ManagementDecisionAgent(jwt_token),
            "knowledge_agent": KnowledgeAgent(jwt_token),
        }

        # 会话管理
        self.session_id = session_id
        if not self.session_id:
            self.session_id = self.memory.create_session(
                user_id=user_id or "anonymous",
                user_role=user_role,
                jwt_token=jwt_token or "",
                metadata={"source": "orchestrator"}
            )

    # ==================== 意图识别 ====================

    def parse_intent(self, user_input: str) -> OrchestrationResult:
        """
        基于关键词匹配的意图识别。
        Phase 1: 简单关键词匹配
        Phase 2-3: 升级为 LLM 语义意图识别
        """
        text = user_input.lower().strip()

        # 尝试提取设备编码
        device_match = re.search(r'(EAF|PUMP|COMP)[-]?\d+', user_input, re.IGNORECASE)
        device_code = device_match.group(0).upper().replace("-", "-") if device_match else None
        if device_match and "-" not in device_code:
            # EAF01 → EAF-01
            code = device_match.group(0).upper()
            if len(code) >= 5:
                device_code = code[:3] + "-" + code[3:]

        # 尝试提取时间参数
        hours_match = re.search(r'(\d+)\s*(小时|h)', text)
        hours = int(hours_match.group(1)) if hours_match else 24

        params = {"deviceCode": device_code, "hours": hours}

        # 关键词匹配
        for keyword, (agent, intent_code) in INTENT_ROUTING.items():
            if keyword in text or keyword.lower() in text:
                agent_obj = self.sub_agents.get(agent)
                level = getattr(agent_obj, "level", "L0") if agent_obj else "L0"
                return OrchestrationResult(
                    intent=keyword,
                    agent_name=agent,
                    agent_level=level,
                    sub_intent=intent_code,
                    extracted_params=params,
                )

        # 默认：通用问答
        agent_obj = self.sub_agents.get("knowledge_agent")
        return OrchestrationResult(
            intent="通用问答",
            agent_name="knowledge_agent",
            agent_level=getattr(agent_obj, 'autonomy_level', agent_obj.level) if agent_obj else "L0",
            sub_intent="KM-01",
            extracted_params=params,
        )

    # ==================== 任务执行 ====================

    def _enrich_params(self, params: dict, agent_name: str) -> dict:
        """
        ★ 主动上下文补全：在执行前自动填充缺失参数。
        如果 deviceCode/faultType 为空，尝试从系统实时数据中自动获取。
        """
        enriched = dict(params)
        try:
            # 补全 deviceCode
            if not enriched.get("deviceCode"):
                devices = self.fetcher.get_devices(page=1, size=5)
                records = devices.get("records", []) if isinstance(devices, dict) else []
                if records:
                    enriched["deviceCode"] = records[0].get("deviceCode", "")
                    logger.info(f"自动补全 deviceCode: {enriched['deviceCode']}")

            # 补全 faultType
            if not enriched.get("faultType") and agent_name in ("maintenance_dispatch", "fault_diagnosis"):
                active = self.fetcher.get_active_alerts(limit=5)
                alert_records = active.get("records", []) if isinstance(active, dict) else []
                if alert_records:
                    severity_order = {"CRITICAL": 0, "HIGH": 1, "MEDIUM": 2, "LOW": 3}
                    alert_records.sort(key=lambda r: severity_order.get(r.get("priority", "LOW"), 99))
                    enriched["faultType"] = alert_records[0].get("faultType", "")
                    logger.info(f"自动补全 faultType: {enriched['faultType']}")
        except Exception as e:
            logger.warning(f"参数补全失败: {e}")
        return enriched

    def execute(self, intent_result: OrchestrationResult) -> str:
        """
        根据意图识别结果，路由到子Agent执行。
        ★ 自动执行检查：当 auto_execute_level >= agent_level 时，可自动执行。
        ★ 主动补全：缺失参数从系统实时数据中自动填充。
        """
        agent = self.sub_agents.get(intent_result.agent_name)
        if not agent:
            return f"⚠️ 未找到处理 '{intent_result.intent}' 的Agent。"

        # ★ 主动补全参数
        intent_result.extracted_params = self._enrich_params(
            intent_result.extracted_params, intent_result.agent_name
        )

        logger.info(
            f"Orchestrator 路由: intent={intent_result.intent}, "
            f"agent={intent_result.agent_name}, sub={intent_result.sub_intent}, "
            f"agent_level={intent_result.agent_level}, auto_execute={self.auto_execute_level}"
        )

        # ★ 权限检查：agent_level vs auto_execute_level
        agent_level = intent_result.agent_level
        level_order = {"L0": 0, "L1": 1, "L2": 2, "L3": 3}
        agent_lv = level_order.get(agent_level, 0) if isinstance(agent_level, str) else 0
        auto_lv = level_order.get(self.auto_execute_level, 0)

        try:
            # ★ 通用问答：先拉全厂状态作为背景，再给知识库回答
            if intent_result.intent == "通用问答":
                return self._handle_general_query(intent_result)

            result = agent.handle(intent_result.sub_intent, intent_result.extracted_params)

            # ★ 权限标签
            if agent_lv > auto_lv and agent_lv >= 2:
                # 需要人工确认但当前权限不足
                result += (
                    f"\n\n---\n"
                    f"🛡️ **操作权限不足**\n"
                    f"- 所需级别: {agent_level}（{'AI辅助执行' if agent_level == 'L2' else 'AI自主执行'}）\n"
                    f"- 当前级别: {self.auto_execute_level}（{'仅查询' if self.auto_execute_level == 'L0' else '仅建议' if self.auto_execute_level == 'L1' else '辅助执行'}）\n"
                    f"- 请管理员在「系统配置→大模型参数配置」中将自动执行级别提升为 {agent_level}，或手动执行此操作。"
                )
            elif agent_lv == 2 and auto_lv >= 2:
                # L2 操作已被授权
                result += "\n\n---\n✅ 此操作已在授权范围内自动执行（L2 辅助执行模式）。"
            elif agent_lv >= 3 and auto_lv >= 3:
                # L3 操作完全自主执行
                result += "\n\n---\n🤖 此操作已由 AI 自主执行，无需人工确认。"

            return result
        except Exception as e:
            logger.error(f"Agent {intent_result.agent_name} 执行失败: {e}")
            return f"⚠️ {intent_result.agent_name} 执行出错: {e}\n\n> 💡 提示：请检查后端服务是否正常，或联系管理员。"

    def _handle_general_query(self, intent_result: OrchestrationResult) -> str:
        """
        处理通用问答——获取全厂概览作为数据背景。
        确保用户即使问"你好"或闲聊，也能看到有价值的系统状态信息。
        """
        parts = []
        # 1. 尝试获取全厂状态总览
        try:
            pm = self.sub_agents.get("production_monitor")
            if pm:
                overview = pm.get_plant_overview()
                if overview and "⚠️" not in overview:
                    parts.append(overview)
        except Exception as e:
            logger.warning(f"全厂概览获取失败: {e}")

        # 2. 尝试获取活跃告警
        try:
            md = self.sub_agents.get("maintenance_dispatch")
            if md:
                alerts = md.get_work_order_summary()
                if alerts and "⚠️" not in alerts and "无" not in alerts:
                    parts.append(alerts)
        except Exception as e:
            logger.warning(f"告警获取失败: {e}")

        if parts:
            parts.insert(0, "## 📊 当前系统状态\n")
            parts.append("\n---\n💡 您可以问我：设备状态、故障诊断、工单管理、能耗分析等问题。")
            return "\n".join(parts)

        # 3. 完全无法获取数据时的友好提示
        return (
            '## 🏭 智驭能效 · AI 智能助手\n\n'
            '我是您的工业能源管理AI助手。我可以帮您：\n\n'
            '| 功能 | 示例问题 |\n'
            '|------|---------|\n'
            '| 📊 设备监控 | 「现在全厂运行情况怎么样？」 |\n'
            '| 🔍 故障诊断 | 「EAF-01 振动异常，帮我分析」 |\n'
            '| 🔧 工单管理 | 「查看待处理工单」 |\n'
            '| ⚡ 能耗优化 | 「当前电价下有什么节能建议？」 |\n'
            '| 📋 报表生成 | 「生成今日运维日报」 |\n'
            '| 📚 知识检索 | 「电弧炉正常温度范围是多少？」 |\n\n'
            '💡 请直接输入您的问题，或点击下方的快捷提问。\n\n'
            '⚠️ 注意：如需获取实时设备数据，请确保后端服务已启动。'
        )

    # ==================== 完整处理流程 ====================

    def process(self, user_input: str) -> Dict[str, Any]:
        """
        完整的处理流程：意图识别 → 路由 → 执行 → 结果。
        """
        # 1. 意图识别
        intent_result = self.parse_intent(user_input)

        # 2. 保存用户消息到记忆
        self.memory.add_message(self.session_id, "user", user_input)

        # 3. 执行
        response = self.execute(intent_result)

        # 4. 保存AI回复到记忆
        self.memory.add_message(self.session_id, "assistant", response)

        return {
            "session_id": self.session_id,
            "intent": intent_result.intent,
            "agent": intent_result.agent_name,
            "level": intent_result.agent_level,
            "response": response,
            "needs_confirmation": intent_result.needs_confirmation,
        }

    # ==================== Tool 执行 ====================

    def execute_tool_directly(self, tool_name: str, arguments: dict) -> dict:
        """
        直接执行指定 Tool（绕过意图识别）。
        """
        return execute_tool(tool_name, arguments, self.jwt_token)

    # ==================== 子Agent列表 ====================

    def get_agent_manifest(self) -> List[dict]:
        """获取所有子Agent的能力清单（含独立API Key状态和自主级别）"""
        manifest = []
        for key, agent in self.sub_agents.items():
            has_llm = getattr(agent, 'llm', None) is not None
            manifest.append({
                "key": key,
                "name": agent.name,
                "level": getattr(agent, 'autonomy_level', agent.level),
                "description": agent.description,
                "has_own_llm": has_llm,
                "model": getattr(agent, 'model', 'N/A'),
            })
        return manifest

    def get_system_prompt_context(self) -> str:
        """获取系统上下文（Agent清单 + 权限矩阵 + Tool清单）"""
        agents = self.get_agent_manifest()
        lines = ["# 多Agent系统 — 能力清单\n"]
        lines.append("| Agent | 自主级别 | 独立LLM | 职责 |")
        lines.append("|-------|---------|---------|------|")
        for a in agents:
            llm_status = "✅" if a["has_own_llm"] else "❌"
            lines.append(f"| {a['name']} | {a['level']} | {llm_status} | {a['description']} |")
        lines.append(f"\n当前自动执行级别: {self.auto_execute_level}")
        lines.append(f"\n{get_tools_summary()}")
        return "\n".join(lines)

    def delegate_to_agent(self, agent_key: str, question: str, context: str = None) -> dict:
        """
        将问题委托给指定子Agent，子Agent使用自己的LLM进行专业推理。
        返回 {"agent": str, "level": str, "response": str, "used_llm": bool}
        """
        agent = self.sub_agents.get(agent_key)
        if not agent:
            return {"agent": agent_key, "level": "N/A", "response": f"Agent '{agent_key}' 不存在", "used_llm": False}

        if not context:
            # 自动获取系统数据作为上下文
            try:
                context = agent.handle("PM-01" if agent_key == "production_monitor" else
                                       "FD-02" if agent_key == "fault_diagnosis" else
                                       "MD-02" if agent_key == "maintenance_dispatch" else
                                       "EO-01" if agent_key == "energy_optimization" else
                                       "MG-01" if agent_key == "management_decision" else
                                       "KM-01", {"query": question})
            except Exception:
                context = "系统数据获取中..."

        has_llm = hasattr(agent, 'reason') and callable(getattr(agent, 'reason'))
        if has_llm:
            try:
                response = agent.reason(question, context)
                return {
                    "agent": agent.name,
                    "level": getattr(agent, 'autonomy_level', agent.level),
                    "response": response,
                    "used_llm": True,
                    "model": getattr(agent, 'model', 'N/A'),
                }
            except Exception as e:
                logger.warning(f"Agent {agent_key} LLM推理失败: {e}")

        return {
            "agent": agent.name,
            "level": getattr(agent, 'autonomy_level', agent.level),
            "response": context,
            "used_llm": False,
        }

    def broadcast_to_agents(self, question: str) -> Dict[str, dict]:
        """
        向所有子Agent广播问题，每个Agent用自己LLM独立回答。
        用于需要多角度综合分析的问题（如"全面评估当前系统状态"）。
        """
        results = {}
        for key in self.sub_agents:
            results[key] = self.delegate_to_agent(key, question)
        return results
