"""
管理决策智能体 (Management Decision Agent)

接管程度：AI 辅助执行 (L2) — 报表生成；AI 建议 (L1) — 决策支持
核心职责：自动报表、KPI分析、风险预警

能力：
  MG-01 自动生成日报（关键指标+异常事件+工单统计）
  MG-02 自动生成周报（趋势对比+能耗分析+碳排放统计）
  MG-03 设备效率分析（OEE综合效率）
  MG-04 异常事件复盘报告
  MG-05 基于设备健康度衰退预测未来7天可能故障
  MG-06 碳排放超标预警
  MG-07 维修人员短缺预警
"""
from typing import Dict, List, Optional
import datetime
from openai import OpenAI
from loguru import logger

try:
    from ..data_fetcher import DataFetcher, get_fetcher
    from ..agent_config import load_config, get_agent_config
except ImportError:
    from data_fetcher import DataFetcher, get_fetcher
    from agent_config import load_config, get_agent_config


class ManagementDecisionAgent:
    """管理决策智能体 — 报表生成 + KPI分析 + 风险预警。拥有独立 LLM 客户端。"""

    def __init__(self, jwt_token: str = None):
        self.fetcher = get_fetcher(jwt_token)
        self.name = "管理决策Agent"
        self.level = "L2"
        self.key = "management_decision"
        self.description = "自动日报/周报生成、KPI分析、风险预警"

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
            self.system_prompt = "你是管理决策分析专家。"
            self.autonomy_level = "L2"

    def reason(self, question: str, context: str) -> str:
        if not self.llm:
            return context
        try:
            response = self.llm.chat.completions.create(
                model=self.model,
                messages=[
                    {"role": "system", "content": self.system_prompt},
                    {"role": "user", "content": f"基于以下系统数据，回答用户问题：\n\n数据：\n{context}\n\n问题：{question}\n\n请生成结构化报告，含数据对比和趋势分析。"}
                ],
                max_tokens=16384, temperature=0.1, timeout=20,
            )
            return response.choices[0].message.content or context
        except Exception as e:
            logger.warning(f"{self.name} LLM推理失败: {e}")
            return context

    def generate_daily_report(self) -> str:
        try:
            summary = self.fetcher.get_dashboard_summary()
            devices = self.fetcher.get_devices(page=1, size=50)
            active_alerts = self.fetcher.get_active_alerts(limit=10)

            dev_records = devices.get("records", []) if isinstance(devices, dict) else []
            alert_records = active_alerts.get("records", []) if isinstance(active_alerts, dict) else (active_alerts if isinstance(active_alerts, list) else [])

            today = datetime.date.today().strftime("%Y-%m-%d")

            report = (
                f"═══════════════════════════════════════\n"
                f"  智驭能效 — 运维日报\n"
                f"  日期：{today}\n"
                f"═══════════════════════════════════════\n\n"
                f"## 一、关键指标\n"
                f"- 总有功功率: {summary.get('totalUsageKwh', 'N/A')} kWh\n"
                f"- 累计碳排放: {summary.get('totalCo2Emission', 'N/A')} kg\n"
                f"- 当前电价区间: {summary.get('currentPriceTier', 'N/A')}\n"
                f"- 运行设备数: {summary.get('runningDeviceCount', 'N/A')}\n"
                f"- 离线设备数: {summary.get('offlineDeviceCount', 'N/A')}\n"
                f"- 活跃告警数: {summary.get('activeAlertCount', 'N/A')}\n\n"
                f"## 二、设备状态\n"
            )

            for d in dev_records[:5]:
                report += f"- {d.get('deviceCode', d.get('name', 'N/A'))}: {d.get('status', 'N/A')}\n"

            report += f"\n## 三、活跃告警\n"
            if alert_records:
                for a in alert_records[:5]:
                    report += f"- [{a.get('priority', 'N/A')}] {a.get('faultType', 'N/A')} — {a.get('deviceCode', a.get('deviceName', 'N/A'))} — {a.get('status', 'N/A')}\n"
            else:
                report += "✅ 当前无活跃告警。\n"

            report += f"\n═══════════════════════════════════════\n"
            report += "⚠️ 本报告由AI自动生成。\n"
            return report
        except Exception as e:
            logger.error(f"MG-01 日报生成失败: {e}")
            return f"⚠️ 日报生成失败: {e}"

    def predict_risk(self, device_id: int = None) -> str:
        try:
            if device_id:
                health = self.fetcher.get_device_health(device_id)
                score = health.get("overallScore", "N/A")
                trend = health.get("trend", health.get("degradationRate", "stable"))

                trend_map = {"stable": "稳定", "declining": "衰退中", "improving": "改善中"}
                trend_text = trend_map.get(str(trend).lower(), str(trend))

                risk_level = "低"
                if isinstance(score, (int, float)):
                    if score < 40:
                        risk_level = "🔴 高 — 建议立即安排检修"
                    elif score < 70:
                        risk_level = "🟠 中 — 建议1周内安排检修"
                    else:
                        risk_level = "🟢 低"

                return (
                    f"## 设备 {device_id} 健康度评估\n\n"
                    f"- **健康度评分**: {score}/100\n"
                    f"- **衰退趋势**: {trend_text}\n"
                    f"- **风险等级**: {risk_level}\n"
                )
            return "请指定设备ID以进行风险评估。"
        except Exception as e:
            logger.error(f"MG-05 风险预警失败: {e}")
            return f"⚠️ 风险预警失败: {e}"

    def handle(self, intent: str, context: dict) -> str:
        handlers = {
            "MG-01": lambda: self.generate_daily_report(),
            "MG-05": lambda: self.predict_risk(context.get("deviceId")),
        }
        handler = handlers.get(intent)
        if handler:
            return handler()
        return f"管理决策Agent 不支持意图: {intent}"
