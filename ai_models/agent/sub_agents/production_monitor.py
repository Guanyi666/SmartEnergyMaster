"""
生产监控智能体 (Production Monitor Agent)

接管程度：AI 自主执行 (L3)
核心职责：7x24 实时监控设备运行状态，发现异常主动告警，生成运行报告

能力：
  PM-01 查询全厂设备实时运行状态
  PM-02 查询指定设备的最新传感器数据
  PM-03 查询全厂总功耗/碳排放/电价区间
  PM-04 大屏数据异常主动推送告警
  PM-05 查询设备历史时序数据并分析趋势
  PM-06 对比当前读数与历史同期，发现异常偏离
  PM-07 基于LSTM预测未来30分钟负荷趋势
  PM-08 电价时段切换提醒
"""
from typing import Dict, List, Optional
from openai import OpenAI
from loguru import logger

try:
    from ..data_fetcher import DataFetcher, get_fetcher
    from ..agent_config import load_config, get_agent_config, SubAgentConfig
except ImportError:
    from data_fetcher import DataFetcher, get_fetcher
    from agent_config import load_config, get_agent_config, SubAgentConfig


class ProductionMonitorAgent:
    """
    生产监控智能体 — 实时监控 + 趋势分析 + 预警
    拥有独立 LLM 客户端，可进行专业领域的智能推理。
    """

    def __init__(self, jwt_token: str = None):
        self.fetcher = get_fetcher(jwt_token)
        self.name = "生产监控Agent"
        self.level = "L3"
        self.key = "production_monitor"
        self.description = "7x24实时监控设备运行状态，发现异常主动告警，生成运行报告"

        # 初始化独立的 LLM 客户端
        master_cfg = load_config()
        agent_cfg = get_agent_config(self.key)
        if agent_cfg and agent_cfg.api_key:
            self.llm = OpenAI(
                api_key=agent_cfg.api_key,
                base_url=master_cfg.base_url,
            )
            self.model = agent_cfg.model
            self.system_prompt = agent_cfg.system_prompt
            self.autonomy_level = agent_cfg.autonomy_level
        else:
            self.llm = None
            self.model = master_cfg.model
            self.system_prompt = "你是生产监控专家。"
            self.autonomy_level = "L3"

    def reason(self, question: str, context: str) -> str:
        """使用本Agent的LLM进行专业领域推理"""
        if not self.llm:
            return context  # 降级：直接返回原始数据
        try:
            response = self.llm.chat.completions.create(
                model=self.model,
                messages=[
                    {"role": "system", "content": self.system_prompt},
                    {"role": "user", "content": f"基于以下系统数据，回答用户问题：\n\n数据：\n{context}\n\n问题：{question}\n\n请用专业、简洁的中文回答，关键数值加粗，异常标出。"}
                ],
                max_tokens=16384,
                temperature=0.1,
                timeout=20,
            )
            return response.choices[0].message.content or context
        except Exception as e:
            logger.warning(f"{self.name} LLM推理失败: {e}")
            return context

    # ==================== PM-01: 全厂状态总览 ====================

    def get_plant_overview(self) -> str:
        """查询全厂设备运行状态，生成自然语言摘要。"""
        try:
            devices = self.fetcher.get_devices(page=1, size=50)
            summary = self.fetcher.get_dashboard_summary()

            # API 返回 PageVO: {total, page, size, records}
            records = devices.get("records", []) if isinstance(devices, dict) else []

            running = sum(1 for d in records if d.get("status") == "RUNNING")
            stopped = sum(1 for d in records if d.get("status") == "STOPPED")
            fault = sum(1 for d in records if d.get("status") in ("FAULT", "MAINTENANCE"))
            offline = sum(1 for d in records if d.get("status") == "OFFLINE")

            total_power = summary.get("totalUsageKwh", "N/A")
            co2 = summary.get("totalCo2Emission", "N/A")
            price_tier = summary.get("currentPriceTier", "N/A")
            running_count = summary.get("runningDeviceCount", running)
            offline_count = summary.get("offlineDeviceCount", offline)

            return (
                f"## 全厂运行总览\n\n"
                f"- 设备总数: {len(records)} 台 | 运行: {running_count} | 停机: {stopped} | 故障/维修: {fault} | 离线: {offline_count}\n"
                f"- 总有功功率: {total_power} kWh\n"
                f"- 累计碳排放: {co2} kg\n"
                f"- 当前电价区间: {price_tier}\n"
            )
        except Exception as e:
            logger.error(f"PM-01 全厂状态总览失败: {e}")
            return f"⚠️ 获取全厂状态失败: {e}"

    # ==================== PM-02: 单设备传感器解读 ====================

    def get_device_sensor_report(self, device_code: str) -> str:
        """查询指定设备最新传感器数据并生成解读报告。"""
        try:
            sensor = self.fetcher.get_latest_sensor(device_code)
            data = sensor if isinstance(sensor, dict) else {}

            temp = data.get("temperature", "N/A")
            vibration = data.get("vibration", "N/A")
            pressure = data.get("pressure", "N/A")
            power = data.get("usageKwh", "N/A")
            op_status = data.get("operatingStatus", "N/A")
            co2 = data.get("co2Emission", "N/A")

            status_map = {0: "停机", 1: "空转", 2: "运行", 3: "高负荷"}
            status_text = status_map.get(op_status, str(op_status))

            anomalies = []
            if isinstance(temp, (int, float)) and temp > 1000:
                anomalies.append(f"⚠️ 温度 {temp}°C 超过1000°C上限")
            if isinstance(vibration, (int, float)) and vibration > 15:
                anomalies.append(f"⚠️ 振动 {vibration} mm/s 超过15mm/s上限")
            if isinstance(pressure, (int, float)) and pressure < 50:
                anomalies.append(f"⚠️ 压力 {pressure} kPa 低于50kPa下限")

            report = (
                f"## {device_code} 最新传感器数据\n\n"
                f"| 指标 | 数值 | 状态 |\n|------|------|------|\n"
                f"| 运行状态 | {status_text} | — |\n"
                f"| 温度 | {temp} °C | {'⚠️ 偏高' if isinstance(temp, (int,float)) and temp > 1000 else '✅ 正常'} |\n"
                f"| 振动 | {vibration} mm/s | {'⚠️ 偏高' if isinstance(vibration, (int,float)) and vibration > 15 else '✅ 正常'} |\n"
                f"| 压力 | {pressure} kPa | {'⚠️ 偏低' if isinstance(pressure, (int,float)) and pressure < 50 else '✅ 正常'} |\n"
                f"| 有功功率 | {power} kWh | — |\n"
                f"| CO2排放 | {co2} kg/h | — |\n"
            )
            if anomalies:
                report += f"\n### ⚠️ 异常发现\n" + "\n".join(f"- {a}" for a in anomalies)
            return report
        except Exception as e:
            logger.error(f"PM-02 传感器数据获取失败 ({device_code}): {e}")
            return f"⚠️ 获取 {device_code} 传感器数据失败: {e}"

    # ==================== PM-05: 趋势分析 ====================

    def analyze_trend(self, device_code: str, hours: int = 24) -> str:
        """分析设备历史数据趋势。"""
        try:
            history = self.fetcher.get_sensor_history(device_code, hours)
            if isinstance(history, dict):
                records = history.get("records", [])
            elif isinstance(history, list):
                records = history
            else:
                records = []

            if not records:
                return f"⚠️ {device_code} 近 {hours} 小时无历史数据。"

            temps = [r.get("temperature", 0) for r in records if r.get("temperature")]
            vibs = [r.get("vibration", 0) for r in records if r.get("vibration")]
            powers = [r.get("usageKwh", 0) for r in records if r.get("usageKwh")]

            def trend_desc(values, label):
                if len(values) < 2:
                    return f"{label}: 数据不足"
                first = sum(values[:3]) / max(len(values[:3]), 1)
                last = sum(values[-3:]) / max(len(values[-3:]), 1)
                change = (last - first) / max(abs(first), 1) * 100 if first != 0 else 0
                direction = "↑ 上升" if change > 5 else ("↓ 下降" if change < -5 else "→ 平稳")
                return f"{label}: 平均 {last:.1f}，{direction}（{change:+.1f}%）"

            lines = [f"## {device_code} 近 {hours} 小时趋势分析\n", f"数据点: {len(records)} 个"]
            if temps:
                lines.append(trend_desc(temps, "温度"))
            if vibs:
                lines.append(trend_desc(vibs, "振动"))
            if powers:
                lines.append(trend_desc(powers, "有功功率"))
            return "\n".join(lines)
        except Exception as e:
            logger.error(f"PM-05 趋势分析失败 ({device_code}): {e}")
            return f"⚠️ 趋势分析失败: {e}"

    # ==================== 统一切入点 ====================

    def handle(self, intent: str, context: dict) -> str:
        handlers = {
            "PM-01": lambda: self.get_plant_overview(),
            "PM-02": lambda: self.get_device_sensor_report(context.get("deviceCode", "")),
            "PM-05": lambda: self.analyze_trend(context.get("deviceCode", ""), context.get("hours", 24)),
        }
        handler = handlers.get(intent)
        if handler:
            return handler()
        return f"生产监控Agent 不支持意图: {intent}"
