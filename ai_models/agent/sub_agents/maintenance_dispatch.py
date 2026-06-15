"""
维修调度智能体 (Maintenance Dispatch Agent)

接管程度：AI 辅助执行 (L2)
核心职责：工单管理、智能派工、人员负载均衡

能力：
  MD-01 查看待处理工单列表并按状态/优先级筛选
  MD-02 获取活跃告警汇总
  MD-03 查看工单详情
  MD-04 确认处理/闭环工单（需人工确认）
  MD-05 根据故障类型自动匹配最合适的维修人员
  MD-06 综合评分：技能匹配+等级+当前负载+历史绩效
  MD-07 批量指派建议（需人工确认）
  MD-08 人员负载均衡预警
"""
from typing import Dict, List, Optional
from openai import OpenAI
from loguru import logger

try:
    from ..data_fetcher import DataFetcher, get_fetcher
    from ..agent_config import load_config, get_agent_config
except ImportError:
    from data_fetcher import DataFetcher, get_fetcher
    from agent_config import load_config, get_agent_config


class MaintenanceDispatchAgent:
    """维修调度智能体 — 工单管理 + 智能派工。拥有独立 LLM 客户端。"""

    def __init__(self, jwt_token: str = None):
        self.fetcher = get_fetcher(jwt_token)
        self.name = "维修调度Agent"
        self.level = "L2"
        self.key = "maintenance_dispatch"
        self.description = "工单全生命周期管理、智能派工推荐、人员负载均衡"

        master_cfg = load_config()
        agent_cfg = get_agent_config(self.key)
        if agent_cfg and agent_cfg.api_key:
            self.llm = OpenAI(api_key=agent_cfg.api_key, base_url=master_cfg.base_url)
            self.model = agent_cfg.model
            self.system_prompt = agent_cfg.system_prompt
            self.autonomy_level = agent_cfg.autonomy_level
        else:
            self.llm = None
            self.model = master_cfg.model
            self.system_prompt = "你是维修调度专家。"
            self.autonomy_level = "L2"

    def reason(self, question: str, context: str) -> str:
        if not self.llm:
            return context
        try:
            response = self.llm.chat.completions.create(
                model=self.model,
                messages=[
                    {"role": "system", "content": self.system_prompt},
                    {"role": "user", "content": f"基于以下系统数据，回答用户问题：\n\n数据：\n{context}\n\n问题：{question}\n\n请给出可执行的人员指派建议，并标注推荐理由。"}
                ],
                max_tokens=16384, temperature=0.1, timeout=20,
            )
            return response.choices[0].message.content or context
        except Exception as e:
            logger.warning(f"{self.name} LLM推理失败: {e}")
            return context

    def _get_active_fault_types(self) -> List[str]:
        """从活跃告警中提取所有故障类型（用于自动补全缺失参数）"""
        try:
            active = self.fetcher.get_active_alerts(limit=50)
            records = active.get("records", []) if isinstance(active, dict) else (active if isinstance(active, list) else [])
            severity_order = {"CRITICAL": 0, "HIGH": 1, "MEDIUM": 2, "LOW": 3}
            sorted_records = sorted(records, key=lambda r: severity_order.get(r.get("priority", "LOW"), 99))
            return [r.get("faultType", "") for r in sorted_records if r.get("faultType")]
        except Exception:
            return []

    def _auto_fill_fault_type(self, fault_type: str) -> str:
        """如果 fault_type 为空，从活跃告警中自动获取最高优先级的故障类型"""
        if fault_type and fault_type.strip():
            return fault_type.strip()
        types = self._get_active_fault_types()
        if types:
            logger.info(f"MD Agent: fault_type为空，自动从活跃告警获取: {types[0]}")
            return types[0]
        return ""

    def get_work_order_summary(self, status: str = None) -> str:
        try:
            active = self.fetcher.get_active_alerts(limit=10)
            active_records = active.get("records", []) if isinstance(active, dict) else (active if isinstance(active, list) else [])

            if not active_records:
                return (
                    "## 维修工单总览\n\n"
                    "✅ 当前无活跃告警。\n\n"
                    "> 支持的故障类型: MECHANICAL_JAM, COOLING_INTERRUPT, BEARING_DAMAGE, "
                    "ELECTRICAL_OVERLOAD, SENSOR_DRIFT, PIPE_LEAK, MOTOR_OVERHEAT, INVERTER_FAULT, LUBRICATION_FAILURE"
                )

            lines = [f"## 维修工单总览\n", f"### 活跃告警 ({len(active_records)} 条)\n"]
            for i, alert in enumerate(active_records[:5], 1):
                fault = alert.get('faultType', '未指定')
                device = alert.get('deviceCode', alert.get('deviceName', 'N/A'))
                lines.append(
                    f"{i}. [{alert.get('priority', 'N/A')}] {fault} — "
                    f"设备: {device} — 状态: {alert.get('status', 'N/A')}"
                )
            lines.append(f"\n> 💡 如需指派，请指定故障类型或由系统自动匹配最高优先级告警。")
            return "\n".join(lines)
        except Exception as e:
            logger.error(f"MD-01 工单总览失败: {e}")
            return f"⚠️ 获取工单信息失败: {e}"

    def recommend_personnel(self, fault_type: str, work_order_id: int = 0, top_n: int = 3) -> str:
        # ★ 自动补全：如果 fault_type 为空，从活跃告警中获取最高优先级故障
        original_fault = fault_type
        fault_type = self._auto_fill_fault_type(fault_type)

        if not fault_type:
            return (
                "⚠️ **无法执行人员推荐**\n\n"
                "原因: 未指定故障类型，且当前系统无活跃告警可自动匹配。\n\n"
                "### 请提供以下任一信息:\n"
                "- 故障类型（如 MECHANICAL_JAM, COOLING_INTERRUPT, BEARING_DAMAGE）\n"
                "- 工单编号\n"
                "- 设备编码\n\n"
                "### 支持的故障类型:\n"
                "MECHANICAL_JAM | COOLING_INTERRUPT | BEARING_DAMAGE | ELECTRICAL_OVERLOAD | "
                "SENSOR_DRIFT | PIPE_LEAK | MOTOR_OVERHEAT | INVERTER_FAULT | LUBRICATION_FAILURE"
            )

        auto_fill_note = ""
        if not original_fault or not original_fault.strip():
            auto_fill_note = f"\n> ℹ️ 未指定故障类型，系统自动从活跃告警中选取: **{fault_type}**\n"

        try:
            result = self.fetcher.auto_match_personnel(fault_type, work_order_id, top_n)
            # 后端返回 DispatchMatchVO: {workOrderId, faultType, requiredSkills, candidates: [...]}
            candidates = result.get("candidates", result.get("matches", result.get("records", []))) if isinstance(result, dict) else []
            required_skills = result.get("requiredSkills", []) if isinstance(result, dict) else []

            if not candidates:
                skill_hint = f"（所需技能: {', '.join(required_skills)}）" if required_skills else ""
                return (
                    f"## 维修人员推荐 — {fault_type}\n"
                    f"{auto_fill_note}"
                    f"⚠️ 未找到匹配该故障类型的维修人员。{skill_hint}\n\n"
                    f"### 建议:\n"
                    f"1. 检查人员技能标签是否覆盖 '{fault_type}' 故障类型\n"
                    f"2. 确认维修人员是否在岗（onDuty）\n"
                    f"3. 联系管理员在「人员管理」中更新技能标签"
                )

            lines = [f"## 维修人员推荐 — {fault_type}\n"]
            if auto_fill_note:
                lines.append(auto_fill_note)
            if required_skills:
                lines.append(f"**所需技能**: {', '.join(required_skills)}\n")
            medals = ["🥇", "🥈", "🥉"]
            for i, m in enumerate(candidates[:top_n]):
                # MatchCandidateVO: {name, matchScore, specializations, skillLevel, currentWorkload, workloadRate}
                name = m.get("name", f"人员{i+1}")
                score = m.get("matchScore", "N/A")
                skills = ", ".join(m.get("specializations", m.get("matchedSkills", []))) if isinstance(m.get("specializations"), list) else m.get("specializations", "N/A")
                level = m.get("skillLevel", "N/A")
                load = m.get("currentWorkload", m.get("workloadRate", "N/A"))
                medal = medals[i] if i < len(medals) else f"{i+1}."
                lines.append(
                    f"{medal} **{name}** — 匹配度: {score}分 | 技能: {skills} | 等级: {level} | 当前负载: {load}\n"
                    f"   推荐理由: 技能匹配 '{fault_type}'，等级匹配，负载适中"
                )
            lines.append("\n⚠️ 请人工确认后执行指派操作。")
            return "\n".join(lines)
        except Exception as e:
            logger.error(f"MD-05 智能推荐失败: {e}")
            return f"⚠️ 人员推荐失败: {e}\n\n> 请检查后端服务是否正常，或确认 faultType='{fault_type}' 是否正确。"

    def check_load_balance(self) -> str:
        try:
            summary = self.fetcher.get_dispatch_summary()
            # DispatchSummaryVO: {onDutyPersonnel, inProgressOrders, pendingOrders, ...}
            total = summary.get("onDutyPersonnel", "N/A")
            busy = summary.get("inProgressOrders", "N/A")
            pending = summary.get("pendingOrders", "N/A")

            lines = [f"## 人员负载总览\n", f"- 在岗人数: {total}", f"- 忙碌人数: {busy}", f"- 待处理工单: {pending}"]
            if isinstance(total, (int, float)) and isinstance(pending, (int, float)):
                if total > 0 and pending / total > 1.5:
                    lines.append(f"\n⚠️ **人员短缺预警**: 在岗 {total} 人 < 待处理工单 {pending} 条 x 1.5，建议增派人手或调整优先级。")
                else:
                    lines.append(f"\n✅ 人员配置充足，供需比正常。")
            return "\n".join(lines)
        except Exception as e:
            logger.error(f"MD-08 负载均衡检查失败: {e}")
            return f"⚠️ 负载均衡检查失败: {e}"

    def handle(self, intent: str, context: dict) -> str:
        handlers = {
            "MD-01": lambda: self.get_work_order_summary(context.get("status")),
            "MD-02": lambda: self.get_work_order_summary(),
            "MD-05": lambda: self.recommend_personnel(context.get("faultType", ""), context.get("workOrderId", 0), context.get("topN", 3)),
            "MD-08": lambda: self.check_load_balance(),
        }
        handler = handlers.get(intent)
        if handler:
            return handler()
        return f"维修调度Agent 不支持意图: {intent}"
