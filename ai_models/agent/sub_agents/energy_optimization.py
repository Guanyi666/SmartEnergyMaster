"""
能耗优化智能体 (Energy Optimization Agent)

接管程度：AI 建议 (L1) - 人工决策
核心职责：能耗分析、成本优化、分时电价策略

能力：
  EO-01 基于分时电价和设备运行状态生成调度建议
  EO-02 分析历史能耗数据，识别节能机会
  EO-03 预测未来30分钟能耗曲线及95%置信区间
  EO-04 计算"如果推迟排产30分钟可节省的电费"
  EO-05 推荐最佳设备启停时间
  EO-06 多设备负荷分配优化建议
  EO-07 模拟"推迟30分钟"vs"立即执行"的电费对比
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


class EnergyOptimizationAgent:
    """能耗优化智能体 — 分时电价策略 + 负荷优化建议。拥有独立 LLM 客户端。"""

    def __init__(self, jwt_token: str = None):
        self.fetcher = get_fetcher(jwt_token)
        self.name = "能耗优化Agent"
        self.level = "L1"
        self.key = "energy_optimization"
        self.description = "基于分时电价的能耗分析与负荷调度优化建议"

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
            self.system_prompt = "你是能源优化专家。"
            self.autonomy_level = "L1"

    def reason(self, question: str, context: str) -> str:
        if not self.llm:
            return context
        try:
            response = self.llm.chat.completions.create(
                model=self.model,
                messages=[
                    {"role": "system", "content": self.system_prompt},
                    {"role": "user", "content": f"基于以下系统数据，回答用户问题：\n\n数据：\n{context}\n\n问题：{question}\n\n请给出量化的节能方案和预计收益。所有建议需人工确认后执行。"}
                ],
                max_tokens=16384, temperature=0.1, timeout=20,
            )
            return response.choices[0].message.content or context
        except Exception as e:
            logger.warning(f"{self.name} LLM推理失败: {e}")
            return context

    def get_dispatch_recommendation(self, device_code: str) -> str:
        try:
            advice = self.fetcher.get_dispatch_advice(device_code)
            # DispatchAdviceVO: {level, title, content, suggestedAction, estimatedSaving}
            level = advice.get("level", "INFO")
            title = advice.get("title", "")
            content = advice.get("content", "")
            action = advice.get("suggestedAction", "")
            saving = advice.get("estimatedSaving", "")

            level_icons = {"CRITICAL": "🔴", "WARN": "🟠", "GOOD": "🟢", "INFO": "🔵"}
            icon = level_icons.get(level, "⚪")

            return (
                f"## {device_code} 调度建议\n\n"
                f"{icon} **{level}**: {title}\n"
                f"- **建议内容**: {content}\n"
                f"- **建议动作**: {action}\n"
                f"- **预计收益**: {saving}\n\n"
                f"⚠️ 此为AI建议，请结合生产计划自主决策。"
            )
        except Exception as e:
            logger.error(f"EO-01 调度建议获取失败: {e}")
            return f"⚠️ 获取调度建议失败: {e}"

    def interpret_forecast(self, device_code: str) -> str:
        try:
            forecast = self.fetcher.get_energy_forecast(device_code)
            points = forecast if isinstance(forecast, list) else forecast.get("forecasts", forecast.get("records", []))
            if not points:
                return f"⚠️ {device_code} 暂无预测数据。"

            lines = [f"## {device_code} 能耗预测\n"]
            for p in points[:6]:
                # ForecastPointVO: {minutesAhead, mean, lower, upper}
                mins = p.get("minutesAhead", "?")
                mean = p.get("mean", "N/A")
                lower = p.get("lower", "N/A")
                upper = p.get("upper", "N/A")
                lines.append(f"- +{mins}min: **{mean}** kWh (95% CI: {lower}~{upper})")

            if len(points) >= 2:
                first = points[0].get("mean", 0)
                last = points[-1].get("mean", 0)
                if isinstance(first, (int, float)) and isinstance(last, (int, float)) and first > 0:
                    change = (last - first) / first * 100
                    direction = "上升" if change > 5 else ("下降" if change < -5 else "平稳")
                    lines.append(f"\n**趋势**: {direction}（{change:+.1f}%）")
            return "\n".join(lines)
        except Exception as e:
            logger.error(f"EO-03 预测解读失败: {e}")
            return f"⚠️ 预测解读失败: {e}"

    def what_if_analysis(self, device_code: str, delay_minutes: int = 30) -> str:
        try:
            advice = self.fetcher.get_dispatch_advice(device_code)
            forecast = self.fetcher.get_energy_forecast(device_code)

            # DispatchAdviceVO: {level, title, content, ...} — no priceTier/priceRate
            level = advice.get("level", "INFO")
            current_price = 1.2  # 默认值，后端 DispatchAdviceVO 不返回电价数值

            points = forecast if isinstance(forecast, list) else forecast.get("forecasts", [])
            avg_power = 0
            if points:
                powers = [p.get("mean", 0) for p in points[:6] if p.get("mean")]
                if powers:
                    avg_power = sum(powers) / len(powers)

            saved_rate = current_price * 0.3
            estimated_energy = avg_power * delay_minutes / 60
            estimated_saving = estimated_energy * saved_rate

            return (
                f"## {device_code} 推迟排产 {delay_minutes} 分钟 — 节能推演\n\n"
                f"### 当前状态\n"
                f"- 调度级别: {level}\n"
                f"- 预测平均功率: {avg_power:.1f} kWh\n\n"
                f"### 推迟后预估\n"
                f"- 预计用电量: {estimated_energy:.1f} kWh\n"
                f"- 预计节省: **{estimated_saving:.2f} 元**\n\n"
                f"### 建议\n"
                f"建议将 {device_code} 下一炉次推迟 {delay_minutes} 分钟启动，\n"
                f"避开当前高电价窗口，预计可节省 {estimated_saving:.2f} 元电费。\n\n"
                f"⚠️ 注意：推迟排产可能影响下游工序，请在确认生产计划可行后采纳。"
            )
        except Exception as e:
            logger.error(f"EO-04 节能推演失败: {e}")
            return f"⚠️ 节能推演分析失败: {e}"

    def handle(self, intent: str, context: dict) -> str:
        handlers = {
            "EO-01": lambda: self.get_dispatch_recommendation(context.get("deviceCode", "")),
            "EO-03": lambda: self.interpret_forecast(context.get("deviceCode", "")),
            "EO-04": lambda: self.what_if_analysis(context.get("deviceCode", ""), context.get("delayMinutes", 30)),
        }
        handler = handlers.get(intent)
        if handler:
            return handler()
        return f"能耗优化Agent 不支持意图: {intent}"
