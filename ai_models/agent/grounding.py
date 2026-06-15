"""
数据锚定模块 (Grounding Module)

防幻觉核心机制：
1. 输入锚定：LLM 调用前必须注入 GROUND TRUTH 数据块
2. 输出校验：LLM 回复后检查关键事实是否与真实数据一致
3. 知识库绑定：所有回答必须引用知识库或实时数据，禁止自由发挥
4. I/O Schema：定义每个 Agent 的严格输入输出格式

原则：LLM 是"翻译官"不是"创作者"——只能将系统真实数据翻译成自然语言，不得编造。
"""
import json
import re
from pathlib import Path
from typing import Dict, List, Optional, Tuple
from dataclasses import dataclass, field
from loguru import logger

KB_DIR = Path(__file__).resolve().parent / "knowledge_base"

# ==================== 知识库加载 ====================

_kb_cache: Dict[str, dict] = {}


def load_knowledge_base(filename: str) -> dict:
    """加载知识库文件（带缓存）"""
    if filename in _kb_cache:
        return _kb_cache[filename]
    path = KB_DIR / filename
    if path.exists():
        try:
            with open(path, "r", encoding="utf-8") as f:
                data = json.load(f)
            _kb_cache[filename] = data
            logger.info(f"知识库已加载: {filename}")
            return data
        except Exception as e:
            logger.error(f"知识库加载失败 {filename}: {e}")
    return {}


def get_device_spec(device_code: str) -> Optional[dict]:
    """获取指定设备的规格参数"""
    specs = load_knowledge_base("device_specs.json")
    return specs.get("devices", {}).get(device_code.upper())


def get_fault_pattern(fault_type: str) -> Optional[dict]:
    """获取指定故障模式的完整信息"""
    patterns = load_knowledge_base("fault_patterns.json")
    for fp in patterns.get("fault_patterns", []):
        if fp["id"] == fault_type:
            return fp
    return None


def get_sop(sop_id: str = None, device_type: str = None, keyword: str = None) -> List[dict]:
    """检索SOP"""
    sops = load_knowledge_base("sops.json")
    results = []
    for sop in sops.get("sops", []):
        if sop_id and sop["id"] == sop_id:
            return [sop]
        if device_type and device_type.lower() in sop.get("device_type", "").lower():
            results.append(sop)
        if keyword:
            title = sop.get("title", "")
            if keyword.lower() in title.lower():
                results.append(sop)
    return results


def get_all_knowledge_summary() -> str:
    """获取知识库总览"""
    specs = load_knowledge_base("device_specs.json")
    patterns = load_knowledge_base("fault_patterns.json")
    sops = load_knowledge_base("sops.json")

    devices = list(specs.get("devices", {}).keys())
    faults = [f["id"] for f in patterns.get("fault_patterns", [])]
    sop_list = [s["id"] for s in sops.get("sops", [])]

    return (
        f"知识库总览:\n"
        f"- 设备规格: {len(devices)}台 ({', '.join(devices)})\n"
        f"- 故障模式: {len(faults)}种 ({', '.join(faults)})\n"
        f"- SOP规程: {len(sop_list)}项 ({', '.join(sop_list)})\n"
    )


# ==================== I/O Schema 定义 ====================

OUTPUT_SCHEMAS = {
    "production_monitor": {
        "required_fields": ["设备总数", "运行状态分布", "关键指标"],
        "forbidden_patterns": [
            r"可能是", r"大概", r"估计", r"或许",  # 模糊词
            r"我推测", r"我认为", r"猜想",          # 主观词
        ],
        "format": "## 设备名称 (device_code)\n| 指标 | 数值 | 状态 |\n|---|---|---|",
        "rules": [
            "所有数值必须来自 API 返回的真实数据，不得估算",
            "如果 API 返回数据为空，必须明确报告'数据不可用'而非编造",
            "设备状态必须使用系统定义的枚举值: STOPPED/IDLE/RUNNING/HIGH_LOAD/FAULT/MAINTENANCE/OFFLINE",
        ]
    },
    "fault_diagnosis": {
        "required_fields": ["异常现象", "根因分析", "处置建议", "置信度"],
        "forbidden_patterns": [
            r"可能是", r"大概", r"估计",
            r"我推测", r"我认为",
        ],
        "format": "【异常现象】\n...\n【根因分析】\n...\n【处置建议】\n1. ...\n2. ...\n【置信度】XX%",
        "rules": [
            "故障类型必须来自知识库 fault_patterns.json 中定义的9种故障",
            "置信度必须基于规则匹配和传感器交叉验证给出具体数值",
            "处置建议必须来自对应故障模式的 procedures 字段",
            "未匹配到故障模式时不得强行诊断，应建议人工检查",
        ]
    },
    "maintenance_dispatch": {
        "required_fields": ["工单状态", "推荐人员", "匹配理由"],
        "forbidden_patterns": [r"可能是", r"大概", r"我推测"],
        "rules": [
            "人员推荐必须来自 API 返回的真实人员数据",
            "匹配理由必须基于技能匹配、等级、负载三个维度",
            "负载率>150%时必须触发人员短缺预警",
        ]
    },
    "energy_optimization": {
        "required_fields": ["当前电价时段", "建议动作", "预计收益"],
        "forbidden_patterns": [r"可能是", r"大概", r"我推测"],
        "format": "## 当前状态\n- 电价时段: ...\n- 预测功率: ...\n## 建议\n- 动作: ...\n- 预计节省: **XX元**\n> ⚠️ 此建议需人工确认后执行",
        "rules": [
            "电价时段必须来自系统配置的真实数据",
            "节能收益计算必须基于实时电价和预测功率",
            "必须标注'此建议需人工确认后执行'",
        ]
    },
    "management_decision": {
        "required_fields": ["日期", "关键指标", "设备状态", "活跃告警"],
        "forbidden_patterns": [r"可能是", r"大概", r"我推测"],
        "format": "══════\n  日报\n══════\n一、关键指标\n...\n二、设备状态\n...\n三、活跃告警\n...\n⚠️ 本报告由AI自动生成",
        "rules": [
            "报表中的所有数值必须来自 API 真实数据",
            "设备状态统计必须与 API 返回一致",
            "异常事件必须引用具体的时间、设备、数值",
        ]
    },
    "knowledge_agent": {
        "required_fields": ["检索关键词", "来源", "内容"],
        "forbidden_patterns": [r"我推测", r"我认为", r"可能是"],
        "rules": [
            "所有知识回答必须引用知识库中的具体来源",
            "如知识库未覆盖，必须明确告知用户'知识库中尚未收录此内容'",
            "SOP步骤必须按编号顺序完整列出",
        ]
    },
}

# 通用输出校验规则
GLOBAL_FORBIDDEN = [
    (r"可能是", "禁止使用模糊词'可能'——使用'数据表明'或'传感器显示'替代"),
    (r"大概|大约|估计", "禁止使用约数词——使用精确数值或明确标注'N/A'"),
    (r"我推测|我认为|我觉得|猜想", "禁止表达主观推测——只陈述数据和规则推导结果"),
    (r"无法获取数据|没有数据|无法访问", "如果数据为空，说'当前系统返回数据为空，请检查设备在线状态或数据采集链路'"),
    (r"根据我的经验|通常来说|一般来说", "禁止泛泛而谈——必须引用知识库或实时数据"),
]


# ==================== 锚定函数 ====================

def build_ground_truth_block(real_data: str, agent_type: str = None) -> str:
    """
    构建 GROUND TRUTH 数据块，注入到 LLM 提示词最前面。
    这是防幻觉的第一道防线：告诉LLM什么是真实数据。
    """
    schema = OUTPUT_SCHEMAS.get(agent_type, {})
    rules = schema.get("rules", [])
    format_hint = schema.get("format", "")
    # 合并全局禁止模式 + Agent特定禁止模式
    local_forbidden = schema.get("forbidden_patterns", [])
    # GLOBAL_FORBIDDEN 是 [(pattern, reason),...]; local_forbidden 是 [pattern,...]
    forbidden = list(GLOBAL_FORBIDDEN)
    for p in local_forbidden:
        forbidden.append((p, "禁止使用此模式"))

    block = [
        "==============================",
        "⚠️ GROUND TRUTH — 以下为系统真实数据，你必须基于此回答",
        "==============================",
        "",
        real_data,
        "",
        "==============================",
        "⚠️ 回答约束（违反将被拒绝）:",
    ]

    for i, rule in enumerate(rules, 1):
        block.append(f"  {i}. {rule}")

    # 禁止模式
    block.append("")
    block.append("  🚫 禁止使用的表达（违禁词检测）:")
    for pattern, reason in forbidden[:5]:
        block.append(f"     - 禁止: {pattern} → {reason}")

    if format_hint:
        block.append(f"\n  📐 输出格式要求:\n     {format_hint}")

    block.append("")
    block.append("如果你不知道某个数据的确切值，请如实说'N/A'，不要编造任何数值。")
    block.append("==============================")

    return "\n".join(block)


def validate_output(response: str, agent_type: str = None) -> Tuple[bool, List[str]]:
    """
    输出校验：检查 LLM 回复是否违反了约束规则。
    返回 (is_valid, violations_list)
    """
    violations = []
    schema = OUTPUT_SCHEMAS.get(agent_type, {})
    local_fb = schema.get("forbidden_patterns", [])
    forbidden = list(GLOBAL_FORBIDDEN) + [(p, "禁止使用此模式") for p in local_fb]

    for pattern, reason in forbidden:
        if re.search(pattern, response):
            violations.append(f"违禁词/模式 '{pattern}': {reason}")

    # 检查必要字段
    required = schema.get("required_fields", [])
    for field in required:
        if field not in response:
            violations.append(f"缺少必要字段: '{field}'")

    is_valid = len(violations) == 0
    if not is_valid:
        logger.warning(f"输出校验失败 ({agent_type}): {violations}")

    return is_valid, violations


def sanitize_response(response: str, agent_type: str = None) -> str:
    """
    清理 LLM 回复：如果检测到违规，追加警告标记。
    """
    is_valid, violations = validate_output(response, agent_type)
    if is_valid:
        return response

    # 追加违规警告
    warn = "\n\n---\n⚠️ **AI输出质量警告**：系统检测到以下问题：\n"
    for v in violations[:3]:
        warn += f"- {v}\n"
    warn += "> 以上内容可能不准确，请以系统实际数据为准。如发现明显错误，请反馈给管理员。"
    return response + warn


def extract_real_values_from_response(response: str) -> Dict[str, any]:
    """
    从 LLM 回复中提取数值，用于与真实数据对比。
    返回 {"temperature": 850, "vibration": 12.5, ...}
    """
    values = {}

    # 提取温度
    temp_match = re.search(r'温度[：:]\s*(\d+\.?\d*)\s*[°℃]', response)
    if temp_match:
        values["temperature"] = float(temp_match.group(1))

    # 提取振动
    vib_match = re.search(r'振动[：:]\s*(\d+\.?\d*)\s*mm', response)
    if vib_match:
        values["vibration"] = float(vib_match.group(1))

    # 提取压力
    pres_match = re.search(r'压力[：:]\s*(\d+\.?\d*)\s*kPa', response)
    if pres_match:
        values["pressure"] = float(pres_match.group(1))

    return values


def check_against_real_data(llm_response: str, real_sensor_data: dict) -> List[str]:
    """
    将 LLM 回复中提到的数值与真实传感器数据对比，发现不一致时报警。
    """
    discrepancies = []
    llm_values = extract_real_values_from_response(llm_response)

    for key, llm_val in llm_values.items():
        real_val = real_sensor_data.get(key)
        if real_val is not None and isinstance(real_val, (int, float)):
            diff_pct = abs(llm_val - real_val) / max(abs(real_val), 0.001) * 100
            if diff_pct > 10:
                discrepancies.append(
                    f"数值不一致 ({key}): LLM说{llm_val}, 实际{real_val} (偏差{diff_pct:.1f}%)"
                )

    if discrepancies:
        logger.warning(f"数据偏差检测: {discrepancies}")

    return discrepancies


def build_agent_system_prompt(agent_type: str) -> str:
    """
    为每个 Agent 构建包含知识库约束的系统提示词。
    """
    schema = OUTPUT_SCHEMAS.get(agent_type, {})
    rules = schema.get("rules", [])
    local_fb = schema.get("forbidden_patterns", [])
    forbidden = list(GLOBAL_FORBIDDEN[:3]) + [(p, "禁止使用此模式") for p in local_fb[:2]]

    parts = []

    # 注入知识库摘要
    kb_summary = get_all_knowledge_summary()
    parts.append(f"## 知识库（你必须基于此回答）\n{kb_summary}")

    # 注入 Schema 规则
    parts.append("\n## 输出规范（必须遵守）")
    for i, rule in enumerate(rules, 1):
        parts.append(f"{i}. {rule}")

    if forbidden:
        parts.append(f"\n### 禁止表达")
        for pattern, reason in forbidden:
            parts.append(f"- ❌ 禁止: `{pattern}` → {reason}")

    parts.append("\n### 核心原则")
    parts.append("你是数据的翻译官，不是创作者。")
    parts.append("只将系统真实数据翻译成自然语言，不得添加任何数据中不存在的信息。")

    return "\n".join(parts)


# ==================== 知识库管理 API ====================

def search_knowledge_base(query: str) -> dict:
    """统一知识库检索"""
    query_lower = query.lower()
    results = {"device_specs": [], "fault_patterns": [], "sops": [], "raw": []}

    # 搜索设备规格
    specs = load_knowledge_base("device_specs.json")
    for code, spec in specs.get("devices", {}).items():
        if query_lower in code.lower() or query_lower in spec.get("type", "").lower():
            results["device_specs"].append({"device_code": code, **spec})

    # 搜索故障模式
    patterns = load_knowledge_base("fault_patterns.json")
    for fp in patterns.get("fault_patterns", []):
        if (query_lower in fp["id"].lower() or
            query_lower in fp["name"].lower() or
            any(query_lower in rc.lower() for rc in fp.get("root_causes", []))):
            results["fault_patterns"].append(fp)

    # 搜索SOP
    sops_data = load_knowledge_base("sops.json")
    for sop in sops_data.get("sops", []):
        if (query_lower in sop.get("id", "").lower() or
            query_lower in sop.get("title", "").lower() or
            query_lower in sop.get("device_type", "").lower()):
            results["sops"].append(sop)

    # 汇总所有结果
    for cat, items in results.items():
        if cat == "raw":
            continue
        if isinstance(items, list):
            for item in items[:3]:
                if isinstance(item, dict):
                    results["raw"].append(item.get("name", item.get("title", item.get("id", str(item)))))

    results["total"] = sum(
        len(v) if isinstance(v, list) else 0
        for v in [results["device_specs"], results["fault_patterns"], results["sops"]]
    )
    return results


def add_knowledge_entry(category: str, entry: dict) -> bool:
    """向知识库添加条目"""
    file_map = {
        "device_specs": "device_specs.json",
        "fault_patterns": "fault_patterns.json",
        "sops": "sops.json",
    }
    filename = file_map.get(category)
    if not filename:
        return False

    data = load_knowledge_base(filename)
    if not data:
        data = {}

    key_map = {"device_specs": "devices", "fault_patterns": "fault_patterns", "sops": "sops"}
    list_key = key_map.get(category)
    if list_key not in data:
        data[list_key] = []

    if isinstance(data[list_key], list):
        data[list_key].append(entry)
    elif isinstance(data[list_key], dict):
        data[list_key].update(entry)

    try:
        path = KB_DIR / filename
        with open(path, "w", encoding="utf-8") as f:
            json.dump(data, f, indent=2, ensure_ascii=False)
        _kb_cache.pop(filename, None)  # 清除缓存
        logger.info(f"知识库条目已添加: {category}")
        return True
    except Exception as e:
        logger.error(f"知识库写入失败: {e}")
        return False
