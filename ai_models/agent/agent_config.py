"""
Agent 系统配置管理模块

管理大模型 API Key、模型选择、超时等配置项。
支持多Agent架构：总管Agent + 6个专业子Agent，每个子Agent可拥有独立的API Key。
配置持久化到 agent_config.json 文件，首次运行时自动写入默认 API Key。
仅 ADMIN 角色可修改 API Key。
"""
import json
import os
from pathlib import Path
from typing import Optional, Dict
from pydantic import BaseModel, Field

# 配置文件路径（与 agent 包同级）
CONFIG_DIR = Path(__file__).resolve().parent
CONFIG_FILE = CONFIG_DIR / "agent_config.json"

# 默认子Agent配置
DEFAULT_SUB_AGENTS = {
    "production_monitor": {
        "name": "生产监控Agent",
        "api_key": "sk-ws-H.RERLHIH.T16a.MEQCIEy9lBoII_XlKORmo3cmfzOnJBHfl1BvSOyld7_lAgD4AiBkShEJXpwho1jcrEeiCOUk8FHycW0kvj_9WjEXepdFPQ",
        "model": "qwen-plus",
        "autonomy_level": "L3",
        "description": "7x24实时监控设备运行状态，发现异常主动告警，生成运行报告",
        "system_prompt": "你是生产监控专家。负责：1) 查询全厂设备实时运行状态；2) 解读传感器数据（温度/振动/压力/功耗/CO2）；3) 分析历史趋势并发现异常偏离；4) 基于阈值主动告警。请用数据说话，用表格和列表清晰呈现。"
    },
    "fault_diagnosis": {
        "name": "故障诊断Agent",
        "api_key": "sk-ws-H.RERLHIP.fRdd.MEYCIQC8z1awbSnE3JmQUpTczYUjnGM2Asr8q1KABH4Jj8drbwIhALtCf86tuvOrouFJV98zbtRVOHt_J_k0lhqebiKpSRl2",
        "model": "qwen-plus",
        "autonomy_level": "L1-L2",
        "description": "多维度传感器交叉验证，根因推理，生成三段式诊断报告",
        "system_prompt": "你是工业故障诊断专家。负责：1) 多维度传感器交叉验证（温度+振动+压力+功耗+CO2）；2) 基于9种故障模式的根因推理；3) 生成三段式诊断报告（异常现象->根因分析->处置建议）；4) 评估故障扩散风险。请给出置信度评分和具体的维修步骤。"
    },
    "maintenance_dispatch": {
        "name": "维修调度Agent",
        "api_key": "sk-ws-H.RERLHLP.uSwF.MEUCIQDbk78GdgPorbPZyTo-PSJRzDoHPg7A-p6J7sf9CkRceAIgb5DN3wO7aCHvHmLvMpaDbLuIDVGGdS5pN9em_qqkbaE",
        "model": "qwen-plus",
        "autonomy_level": "L2",
        "description": "工单全生命周期管理、智能派工推荐、人员负载均衡",
        "system_prompt": "你是维修调度专家。负责：1) 工单状态管理和优先级排序；2) 基于技能匹配+等级+负载+历史绩效的智能派工推荐；3) 人员负载均衡预警；4) 维修资源优化配置。请给出可执行的人员指派建议，并标注推荐理由。"
    },
    "energy_optimization": {
        "name": "能耗优化Agent",
        "api_key": "sk-ws-H.RERLHEY.mat7.MEYCIQCh6cM_gxlVxT7cNgFSUoOojvTLP2jDyb2ZE6TGXCp8vQIhAIHM0Zrp94zCEO15i4DNiYxI7LcjuRG87p0S3El9F9Ya",
        "model": "qwen-plus",
        "autonomy_level": "L1",
        "description": "基于分时电价的能耗分析与负荷调度优化建议",
        "system_prompt": "你是能源优化专家。负责：1) 基于分时电价分析能耗成本；2) 预测未来30分钟负荷趋势；3) 计算推迟排产可节省的电费；4) 推荐最佳设备启停时间。请给出量化的节能方案和预计收益。所有建议需人工确认后执行。"
    },
    "management_decision": {
        "name": "管理决策Agent",
        "api_key": "sk-ws-H.RERLHPR.NFze.MEUCIQCjv1MHfiwuiWGg5IrVCi_YV0nqUjBCiffYgSElTgc6FAIgTa26yf4_EKOtXUISXS9ZWxJboewagGJ-i0AurVYpBHs",
        "model": "qwen-plus",
        "autonomy_level": "L2",
        "description": "自动日报/周报生成、KPI分析、风险预警",
        "system_prompt": "你是管理决策分析专家。负责：1) 自动生成运维日报/周报（关键指标+异常事件+工单统计）；2) 设备健康度衰退趋势预测；3) 碳排放超标预警；4) 维修人员短缺预警。请生成结构化报告，含数据对比和趋势分析。"
    },
    "knowledge_agent": {
        "name": "知识管理Agent",
        "api_key": "sk-ws-H.RERLHYR.QUSk.MEUCIE_Jq_8Tw3AlsqJVvoZ0XoARpWJ1XlRaPNsP4HrJA-wJAiEA4LNaIkL_u1jCK2mHn5l3NuuUpnlPNmVjZQBoNHTtHNk",
        "model": "qwen-plus",
        "autonomy_level": "L3",
        "description": "设备手册问答、故障案例检索、SOP规程查询、智能培训",
        "system_prompt": "你是工业知识管理专家。负责：1) 设备操作手册问答；2) 历史故障案例检索与匹配；3) SOP标准操作规程查询；4) 分时电价政策解读；5) 新操作员上岗培训。请引用知识库中的具体来源，标注信息可信度。"
    }
}

# 默认配置 — 首次运行时自动写入
DEFAULT_CONFIG = {
    "api_key": "sk-7afdca4a0a4949e9a5486ef4ba673ab9",
    "base_url": "https://dashscope.aliyuncs.com/compatible-mode/v1",
    "model": "qwen-plus",
    "max_tokens": 32768,
    "temperature": 0.3,
    "timeout": 30,
    "enable_thinking": False,
    "system_prompt": "你是智驭能效（SmartEnergyMaster）平台的系统级智能体（总管Agent），负责协调调度6个专业子Agent完成复杂任务。你可以：1) 理解操作员自然语言指令；2) 将复杂任务分解并委托给专业子Agent；3) 综合各子Agent结果给出统一回复。请用专业、简洁的中文回答，使用Markdown格式。",
    "max_history_turns": 10,
    "auto_execute_level": "L3",
    "agents": DEFAULT_SUB_AGENTS
}


class SubAgentConfig(BaseModel):
    """单个子Agent的配置模型"""
    name: str = Field(description="Agent名称")
    api_key: str = Field(description="Agent专用API Key")
    model: str = Field(default="qwen-plus", description="使用的模型")
    autonomy_level: str = Field(default="L1", description="自主执行级别 L0/L1/L2/L3")
    description: str = Field(default="", description="Agent能力描述")
    system_prompt: str = Field(default="", description="Agent专用系统提示词")


class AgentConfig(BaseModel):
    """总管Agent 配置模型"""
    api_key: str = Field(default=DEFAULT_CONFIG["api_key"], description="总管API Key")
    base_url: str = Field(default=DEFAULT_CONFIG["base_url"], description="API Base URL")
    model: str = Field(default=DEFAULT_CONFIG["model"], description="模型名称")
    max_tokens: int = Field(default=DEFAULT_CONFIG["max_tokens"], description="最大输出 Token")
    temperature: float = Field(default=DEFAULT_CONFIG["temperature"], description="生成温度")
    timeout: int = Field(default=DEFAULT_CONFIG["timeout"], description="请求超时(秒)")
    enable_thinking: bool = Field(default=DEFAULT_CONFIG["enable_thinking"], description="是否启用深度思考")
    system_prompt: str = Field(default=DEFAULT_CONFIG["system_prompt"], description="系统提示词")
    max_history_turns: int = Field(default=DEFAULT_CONFIG["max_history_turns"], description="最大对话轮次")
    auto_execute_level: str = Field(default=DEFAULT_CONFIG["auto_execute_level"], description="自动执行级别(L0/L1/L2/L3)")


def _load_raw_config() -> dict:
    """加载原始配置JSON（包含agents等扩展字段）"""
    if CONFIG_FILE.exists():
        try:
            with open(CONFIG_FILE, "r", encoding="utf-8") as f:
                return json.load(f)
        except Exception:
            pass
    return {}


def load_config() -> AgentConfig:
    """加载总管配置。若配置文件不存在，自动创建并写入默认配置。"""
    raw = _load_raw_config()
    if raw:
        merged = {**DEFAULT_CONFIG, **raw}
        # 提取AgentConfig需要的字段
        agent_fields = {k: v for k, v in merged.items() if k in AgentConfig.model_fields}
        return AgentConfig(**agent_fields)
    # 首次运行：写入默认配置
    _save_full_config(DEFAULT_CONFIG)
    return AgentConfig()


def load_agent_configs() -> Dict[str, SubAgentConfig]:
    """加载所有子Agent配置。"""
    raw = _load_raw_config()
    agents_data = raw.get("agents", {}) if raw else {}
    # 合并默认值
    merged_agents = {**DEFAULT_SUB_AGENTS, **agents_data}
    result = {}
    for key, data in merged_agents.items():
        result[key] = SubAgentConfig(**data)
    return result


def get_agent_config(agent_key: str) -> Optional[SubAgentConfig]:
    """获取指定子Agent的配置。"""
    agents = load_agent_configs()
    return agents.get(agent_key)


def _save_full_config(data: dict) -> None:
    """持久化完整配置到文件（含agents）"""
    CONFIG_DIR.mkdir(parents=True, exist_ok=True)
    with open(CONFIG_FILE, "w", encoding="utf-8") as f:
        json.dump(data, f, indent=2, ensure_ascii=False)


def save_config(config: AgentConfig) -> None:
    """持久化总管配置到文件（保留agents字段）"""
    raw = _load_raw_config()
    raw.update(config.model_dump())
    _save_full_config(raw)


def update_api_key(new_key: str, role: str, agent_key: str = None) -> bool:
    """
    更新 API Key。仅 ADMIN 角色可操作。
    agent_key: 可选，指定子Agent的key；为空则更新总管API Key。
    返回 True 表示成功，False 表示权限不足。
    """
    if role.upper() != "ADMIN":
        return False
    raw = _load_raw_config() or dict(DEFAULT_CONFIG)
    if agent_key:
        if "agents" not in raw:
            raw["agents"] = {}
        if agent_key in raw["agents"]:
            raw["agents"][agent_key]["api_key"] = new_key
        else:
            raw["agents"][agent_key] = {"api_key": new_key}
    else:
        raw["api_key"] = new_key
    _save_full_config(raw)
    return True


def update_agent_config(agent_key: str, updates: dict, role: str) -> bool:
    """
    更新指定子Agent的配置。仅 ADMIN 角色可操作。
    """
    if role.upper() != "ADMIN":
        return False
    raw = _load_raw_config() or dict(DEFAULT_CONFIG)
    if "agents" not in raw:
        raw["agents"] = {}
    if agent_key not in raw["agents"]:
        raw["agents"][agent_key] = {}
    raw["agents"][agent_key].update(updates)
    _save_full_config(raw)
    return True


def update_config(updates: dict, role: str) -> bool:
    """
    批量更新总管配置。仅 ADMIN 角色可操作。
    """
    if role.upper() != "ADMIN":
        return False
    cfg = load_config()
    for key, value in updates.items():
        if hasattr(cfg, key) and key != "api_key":
            setattr(cfg, key, value)
    if "api_key" in updates:
        cfg.api_key = updates["api_key"]
    save_config(cfg)
    return True


def _mask_key(key: str) -> str:
    """脱敏 API Key"""
    if len(key) > 8:
        return key[:4] + "****" + key[-4:]
    return "****"


def get_public_config() -> dict:
    """获取可公开的配置（隐藏 API Key 中间部分）"""
    cfg = load_config()
    d = cfg.model_dump()
    d["api_key"] = _mask_key(d.get("api_key", ""))
    # 添加子Agent信息（脱敏）
    agents = load_agent_configs()
    d["agents"] = {}
    for key, ac in agents.items():
        d["agents"][key] = {
            "name": ac.name,
            "api_key": _mask_key(ac.api_key),
            "model": ac.model,
            "autonomy_level": ac.autonomy_level,
            "description": ac.description,
        }
    return d


def validate_api_key(api_key: str = None) -> dict:
    """
    验证 API Key 格式和连通性。
    返回 {"valid": bool, "message": str, "model": str}
    """
    if api_key is None:
        cfg = load_config()
        api_key = cfg.api_key

    if not api_key or not (api_key.startswith("sk-") or api_key.startswith("sk-ws-")):
        return {"valid": False, "message": "API Key 格式无效：应以 sk- 或 sk-ws- 开头", "model": None}

    if len(api_key) < 20:
        return {"valid": False, "message": "API Key 长度不足（< 20字符）", "model": None}

    return {"valid": True, "message": "API Key 格式有效", "model": load_config().model}


def test_llm_connectivity(api_key: str = None, timeout: int = 8) -> dict:
    """
    测试 LLM API 连通性。
    发送一个最小的 API 请求，验证 API Key 是否有效、网络是否可达。
    返回 {"reachable": bool, "message": str, "model": str, "latency_ms": float}
    """
    import time as _time
    cfg = load_config()

    if api_key is None:
        api_key = cfg.api_key

    # 先校验格式
    fmt_check = validate_api_key(api_key)
    if not fmt_check["valid"]:
        return {
            "reachable": False,
            "message": f"API Key 格式无效: {fmt_check['message']}",
            "model": cfg.model,
            "latency_ms": 0
        }

    try:
        from openai import OpenAI
        client = OpenAI(
            api_key=api_key,
            base_url=cfg.base_url,
            timeout=timeout,
        )
        start = _time.time()
        response = client.chat.completions.create(
            model=cfg.model,
            messages=[{"role": "user", "content": "ping"}],
            max_tokens=5,
            temperature=0,
        )
        latency = (_time.time() - start) * 1000
        return {
            "reachable": True,
            "message": f"LLM 连通正常",
            "model": cfg.model,
            "latency_ms": round(latency, 1),
        }
    except Exception as e:
        error_str = str(e)
        if "401" in error_str or "403" in error_str or "unauthorized" in error_str.lower():
            hint = "API Key 无效或已过期，请在系统配置页面更新"
        elif "timeout" in error_str.lower() or "timed out" in error_str.lower():
            hint = "LLM 服务连接超时，请检查网络"
        elif "connection" in error_str.lower() or "refused" in error_str.lower() or "resolve" in error_str.lower():
            hint = "无法连接到 LLM 服务（dashscope.aliyuncs.com），请检查网络"
        else:
            hint = f"LLM 调用异常: {error_str[:120]}"
        return {
            "reachable": False,
            "message": hint,
            "model": cfg.model,
            "latency_ms": 0
        }


def test_all_agents_connectivity(timeout: int = 5) -> dict:
    """
    测试所有Agent（总管+6个子Agent）的API连通性。
    返回每个Agent的连通状态。
    """
    results = {}
    # 总管
    cfg = load_config()
    results["orchestrator"] = test_llm_connectivity(cfg.api_key, timeout)
    # 子Agent
    agents = load_agent_configs()
    for key, ac in agents.items():
        results[key] = test_llm_connectivity(ac.api_key, timeout)
    return results
