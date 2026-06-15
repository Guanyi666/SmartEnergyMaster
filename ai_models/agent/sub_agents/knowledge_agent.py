"""
知识管理智能体 (Knowledge Agent)

接管程度：AI 自主执行 (L3) — 知识检索
核心职责：设备手册问答、故障案例检索、SOP规程查询、智能培训

能力：
  KM-01 设备操作手册问答（RAG）
  KM-02 历史故障案例检索（RAG）
  KM-03 SOP规程检索（RAG）
  KM-04 分时电价政策查询
  KM-05 新操作员上岗培训（交互式学习）
  KM-06 故障案例推演
  KM-07 操作员技能评估与提升建议
"""
from typing import Dict, List, Optional
from openai import OpenAI
from loguru import logger

try:
    from ..data_fetcher import DataFetcher, get_fetcher
    from ..agent_config import load_config, get_agent_config
    from ..grounding import (
        search_knowledge_base, get_device_spec, get_fault_pattern,
        get_sop, get_all_knowledge_summary, build_agent_system_prompt,
        OUTPUT_SCHEMAS
    )
except ImportError:
    from data_fetcher import DataFetcher, get_fetcher
    from agent_config import load_config, get_agent_config
    from grounding import (
        search_knowledge_base, get_device_spec, get_fault_pattern,
        get_sop, get_all_knowledge_summary, build_agent_system_prompt,
        OUTPUT_SCHEMAS
    )


class KnowledgeAgent:
    """知识管理智能体 — RAG驱动知识检索 + 智能培训。拥有独立 LLM 客户端。"""

    def __init__(self, jwt_token: str = None):
        self.fetcher = get_fetcher(jwt_token)
        self.name = "知识管理Agent"
        self.level = "L3"
        self.key = "knowledge_agent"
        self.description = "设备手册问答、故障案例检索、SOP规程查询、智能培训"

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
            self.system_prompt = "你是工业知识管理专家。"
            self.autonomy_level = "L3"

    def reason(self, question: str, context: str) -> str:
        if not self.llm:
            return context
        try:
            response = self.llm.chat.completions.create(
                model=self.model,
                messages=[
                    {"role": "system", "content": self.system_prompt},
                    {"role": "user", "content": f"基于以下系统数据，回答用户问题：\n\n数据：\n{context}\n\n问题：{question}\n\n请引用知识库中的具体来源，标注信息可信度。"}
                ],
                max_tokens=16384, temperature=0.1, timeout=20,
            )
            return response.choices[0].message.content or context
        except Exception as e:
            logger.warning(f"{self.name} LLM推理失败: {e}")
            return context

    def search_knowledge(self, query: str, category: str = "all") -> str:
        """
        检索知识库（本地知识库 + 后端API）。
        优先使用本地结构化知识库，再降级到后端API。
        """
        # 第一步：本地知识库检索（精确、无幻觉）
        kb_results = search_knowledge_base(query)
        results = []

        for spec in kb_results.get("device_specs", [])[:2]:
            results.append(
                f"📋 [设备规格] {spec.get('device_code', '')} ({spec.get('type', '')}): "
                f"额定功率{spec.get('rated_power_kw', 'N/A')}kW, "
                f"正常温度{spec.get('normal_temp_range', [0,0])[0]}-{spec.get('normal_temp_range', [0,0])[1]}°C, "
                f"振动上限{spec.get('max_vibration', 'N/A')}mm/s"
            )

        for fp in kb_results.get("fault_patterns", [])[:2]:
            results.append(
                f"📝 [故障模式] {fp.get('id', '')} {fp.get('name', '')}: "
                f"严重度={fp.get('severity', 'N/A')}, "
                f"根因={', '.join(fp.get('root_causes', [])[:2])}"
            )

        for sop in kb_results.get("sops", [])[:2]:
            results.append(f"📖 [SOP] {sop.get('id', '')}: {sop.get('title', '')}")

        # 第二步：后端API补充检索
        try:
            if category in ("all", "sop"):
                sops = self.fetcher.get_sops(query)
                sop_records = sops.get("records", []) if isinstance(sops, dict) else (sops if isinstance(sops, list) else [])
                for s in sop_records[:2]:
                    results.append(f"📋 [在线SOP] {s.get('title', s.get('name', 'N/A'))}")

            if category in ("all", "case"):
                cases = self.fetcher.get_cases(keyword=query)
                case_records = cases.get("records", []) if isinstance(cases, dict) else (cases if isinstance(cases, list) else [])
                for c in case_records[:2]:
                    results.append(f"📝 [在线案例] {c.get('title', c.get('name', 'N/A'))} — {c.get('faultType', '')}")
        except Exception as e:
            logger.warning(f"后端知识检索失败（不影响本地结果）: {e}")

        total = kb_results.get("total", 0)
        if not results:
            return (
                f"## 知识检索: {query}\n\n"
                f"⚠️ 知识库中尚未收录与 '{query}' 直接相关的内容。\n\n"
                f"### 当前知识库包含:\n{get_all_knowledge_summary()}\n\n"
                f"### 建议\n"
                f"- 尝试使用设备编码（如 EAF-01）或故障类型（如 MECHANICAL_JAM）搜索\n"
                f"- 联系管理员补充相关知识条目\n"
            )

        return f"## 知识检索: {query}\n\n本地命中 {total} 条\n\n" + "\n".join(f"- {r}" for r in results)

    def search_local_kb(self, query: str, kb_type: str = "all") -> str:
        """
        纯本地知识库检索——不依赖后端API，零幻觉。
        """
        if kb_type == "device":
            spec = get_device_spec(query.upper())
            if spec:
                return (
                    f"## 设备规格: {query.upper()}\n\n"
                    f"- 类型: {spec.get('type', 'N/A')}\n"
                    f"- 额定功率: {spec.get('rated_power_kw', 'N/A')} kW\n"
                    f"- 正常温度范围: {spec.get('normal_temp_range', ['N/A'])}\n"
                    f"- 正常振动范围: {spec.get('normal_vibration_range', ['N/A'])} mm/s\n"
                    f"- 正常压力范围: {spec.get('normal_pressure_range', ['N/A'])} kPa\n"
                    f"- 维护间隔: {spec.get('maintenance_interval_hours', 'N/A')} 小时\n"
                    f"- 预期寿命: {spec.get('expected_lifetime_hours', 'N/A')} 小时\n"
                )
            return f"⚠️ 知识库中未找到设备 '{query}' 的规格信息。"

        if kb_type == "fault":
            pattern = get_fault_pattern(query.upper())
            if pattern:
                procedures = "\n".join(
                    f"  {p['step']}. [{p['time']}] {p['action']}"
                    for p in pattern.get("procedures", [])
                )
                return (
                    f"## 故障模式: {pattern.get('name', query)}\n\n"
                    f"- ID: {pattern.get('id', 'N/A')}\n"
                    f"- 触发条件: {pattern.get('trigger_conditions', 'N/A')}\n"
                    f"- 严重度: {pattern.get('severity', 'N/A')}\n"
                    f"- 根因: {', '.join(pattern.get('root_causes', []))}\n"
                    f"- 预估修复时间: {pattern.get('estimated_repair_time_hours', 'N/A')} 小时\n\n"
                    f"### 处置流程:\n{procedures}\n\n"
                    f"### 所需备件:\n" + "\n".join(f"- {sp}" for sp in pattern.get("spare_parts", []))
                )
            return f"⚠️ 知识库中未找到故障模式 '{query}'。"

        if kb_type == "sop":
            sops = get_sop(keyword=query)
            if sops:
                lines = [f"## SOP检索: {query}\n"]
                for sop in sops[:3]:
                    lines.append(f"### {sop['id']}: {sop['title']}")
                    for step in sop.get("steps", []):
                        lines.append(f"  {step['seq']}. {step['action']} [{step['check']}]")
                    if sop.get("warnings"):
                        lines.append(f"\n  ⚠️ 注意事项:")
                        for w in sop["warnings"]:
                            lines.append(f"     - {w}")
                    lines.append("")
                return "\n".join(lines)
            return f"⚠️ 知识库中未找到与 '{query}' 相关的SOP。"

        return self.search_knowledge(query, "all")

    def search_sop(self, keyword: str = None) -> str:
        try:
            sops = self.fetcher.get_sops(keyword)
            records = sops.get("records", []) if isinstance(sops, dict) else (sops if isinstance(sops, list) else [])
            if not records:
                return f"## SOP检索\n\n未找到相关标准操作流程。建议补充 '{keyword or '通用'}' 相关的SOP文档。"
            lines = [f"## SOP检索结果\n"]
            for i, sop in enumerate(records[:5], 1):
                title = sop.get("title", sop.get("name", f"SOP-{i}"))
                desc = sop.get("description", sop.get("content", ""))[:150]
                lines.append(f"{i}. **{title}**\n   {desc}")
            return "\n".join(lines)
        except Exception as e:
            logger.error(f"KM SOP检索失败: {e}")
            return f"## SOP检索\n\n⚠️ 检索失败: {e}"

    def query_price_policy(self) -> str:
        return (
            "## 分时电价政策\n\n"
            "| 时段类型 | 时间范围 | 电价（元/kWh） |\n"
            "|---------|---------|---------------|\n"
            "| 尖峰 | 7-8月 19:00-21:00 / 1/12月 18:00-20:00 | 1.25 |\n"
            "| 峰 | 08:00-11:00 / 18:00-23:00 | 0.95 |\n"
            "| 平 | 06:00-08:00 / 11:00-18:00 | 0.60 |\n"
            "| 谷 | 23:00-次日06:00 | 0.32 |\n"
            "| 深谷 | 节假日 00:00-06:00 | 0.22 |\n\n"
            "⚠️ 以上为默认配置，实际电价请以电网公司公告为准。\n"
            "管理员可在「系统配置 → 电价时段配置」中修改。"
        )

    def training_qa(self, topic: str = "general") -> str:
        topics = {
            "general": (
                "## 智驭能效 系统培训\n\n"
                "### 系统简介\n"
                "智驭能效（SmartEnergyMaster）是一个工业能源管理平台，用于监控钢铁厂核心设备"
                "（电弧炉、循环水泵、空压机）的实时运行状态。\n\n"
                "### 核心功能\n"
                "1. **实时监控** — 设备运行状态、传感器数据、大屏展示\n"
                "2. **故障检测** — 自动检测机械卡涩、冷却中断等异常\n"
                "3. **工单管理** — 故障工单的创建、指派、处理、闭环\n"
                "4. **能耗优化** — 分时电价策略、调度建议\n"
                "5. **智能报表** — 日报/周报自动生成\n\n"
                "### 设备类型\n"
                "- EAF-01: 电弧炉（主设备）\n"
                "- PUMP-01: 循环水泵\n"
                "- COMP-01: 空压机\n\n"
                "需要了解哪个模块？可以直接问我！"
            ),
            "fault": (
                "## 故障处理培训\n\n"
                "### 常见故障类型\n"
                "- **机械卡涩 (MECHANICAL_JAM)**: 空转状态下振动 > 15mm/s → 检查轴承润滑\n"
                "- **冷却中断 (COOLING_INTERRUPT)**: 温度 > 1000°C + 压力 < 50kPa → 检查冷却泵\n"
                "- **轴承损坏 (BEARING_DAMAGE)**: 温度↑ + 振动 > 20mm/s + 压力↓ → 停机对中\n\n"
                "### 处理流程\n"
                "1. 系统自动检测异常 → 创建工单\n"
                "2. 维修调度 → 匹配最合适的维修人员\n"
                "3. 维修人员确认处理 → 现场维修\n"
                "4. 维修完成 → 工单闭环 → 记录维修结果\n"
            ),
        }
        return topics.get(topic, topics["general"])

    def handle(self, intent: str, context: dict) -> str:
        handlers = {
            "KM-01": lambda: self.search_knowledge(context.get("query", ""), "all"),
            "KM-02": lambda: self.search_knowledge(context.get("query", ""), "case"),
            "KM-03": lambda: self.search_sop(context.get("keyword")),
            "KM-04": lambda: self.query_price_policy(),
            "KM-05": lambda: self.training_qa(context.get("topic", "general")),
            # 本地知识库精确检索（零幻觉）
            "KM-LOCAL-DEVICE": lambda: self.search_local_kb(context.get("query", ""), "device"),
            "KM-LOCAL-FAULT": lambda: self.search_local_kb(context.get("query", ""), "fault"),
            "KM-LOCAL-SOP": lambda: self.search_local_kb(context.get("query", ""), "sop"),
            "KM-LOCAL-ALL": lambda: self.search_local_kb(context.get("query", ""), "all"),
        }
        handler = handlers.get(intent)
        if handler:
            return handler()
        return f"知识管理Agent 不支持意图: {intent}"
