"""
智驭能效 系统级智能体 — FastAPI 服务入口

启动方式:
  python agent_service.py
  uvicorn agent.agent_service:app --host 0.0.0.0 --port 8001 --reload

API 端点:
  GET  /health              健康检查
  GET  /agent/config         获取公开配置
  PUT  /agent/config         更新配置（仅ADMIN）
  POST /agent/chat           非流式对话
  POST /agent/chat/stream    流式对话（SSE）
  GET  /agent/tools          获取Tool目录
  POST /agent/tools/execute  直接执行Tool
  GET  /agent/sessions       会话列表
  DELETE /agent/sessions/{id} 清除会话
  GET  /agent/agents         子Agent清单
"""
import json
import sys
from contextlib import asynccontextmanager
from pathlib import Path
from typing import List, Optional

# 确保当前目录在 sys.path 中（支持直接 python agent_service.py 启动）
_agent_dir = Path(__file__).resolve().parent
if str(_agent_dir) not in sys.path:
    sys.path.insert(0, str(_agent_dir))
if str(_agent_dir.parent) not in sys.path:
    sys.path.insert(0, str(_agent_dir.parent))

import uvicorn
from fastapi import FastAPI, HTTPException, Query
from fastapi.middleware.cors import CORSMiddleware
from fastapi.responses import StreamingResponse
from pydantic import BaseModel, Field
from loguru import logger

# 兼容直接运行（python agent_service.py）和包运行（uvicorn agent.agent_service:app）
try:
    from .agent_config import (load_config, update_config, update_api_key, get_public_config,
                                AgentConfig, test_llm_connectivity, test_all_agents_connectivity,
                                load_agent_configs, update_agent_config, get_agent_config)
    from .chat_handler import ChatHandler
    from .tools import get_all_tools_for_llm, get_tools_by_category, execute_tool
    from .memory import get_memory_manager
    from .orchestrator import AgentOrchestrator
except ImportError:
    from agent_config import (load_config, update_config, update_api_key, get_public_config,
                               AgentConfig, test_llm_connectivity, test_all_agents_connectivity,
                               load_agent_configs, update_agent_config, get_agent_config)
    from chat_handler import ChatHandler
    from tools import get_all_tools_for_llm, get_tools_by_category, execute_tool
    from memory import get_memory_manager
    from orchestrator import AgentOrchestrator


# ==================== 请求/响应模型 ====================

class ChatRequest(BaseModel):
    message: str = Field(..., description="用户消息", min_length=1, max_length=4000)
    session_id: Optional[str] = Field(None, description="会话ID（不传则新建）")
    jwt_token: Optional[str] = Field(None, description="用户的JWT Token（继承权限）")
    user_id: Optional[str] = Field("anonymous", description="用户标识")
    user_role: Optional[str] = Field("OPERATOR", description="用户角色")


class ChatResponse(BaseModel):
    session_id: str
    content: str
    tool_calls: List[dict] = []
    elapsed_seconds: float = 0.0
    config: dict = {}


class ToolExecuteRequest(BaseModel):
    tool_name: str = Field(..., description="Tool名称")
    arguments: dict = Field(default_factory=dict, description="Tool参数")
    jwt_token: Optional[str] = Field(None, description="JWT Token")


class ConfigUpdateRequest(BaseModel):
    role: str = Field(..., description="请求者角色（需为ADMIN）")
    api_key: Optional[str] = Field(None, description="新的API Key")
    model: Optional[str] = Field(None, description="模型名称")
    max_tokens: Optional[int] = Field(None, description="最大输出Token")
    temperature: Optional[float] = Field(None, description="生成温度")
    timeout: Optional[int] = Field(None, description="请求超时(秒)")
    enable_thinking: Optional[bool] = Field(None, description="是否启用深度思考")
    system_prompt: Optional[str] = Field(None, description="系统提示词")
    max_history_turns: Optional[int] = Field(None, description="最大对话轮次")


# ==================== 应用生命周期 ====================

@asynccontextmanager
async def lifespan(app: FastAPI):
    """启动时加载配置，确保配置文件存在"""
    cfg = load_config()
    logger.info(f"Agent 服务启动，模型: {cfg.model}, API Key: {cfg.api_key[:8]}...")
    yield


app = FastAPI(
    title="SmartEnergy AI Agent Service",
    version="1.0.0",
    description="智驭能效系统级智能体 — 多Agent架构的工业能源管理AI助手",
    lifespan=lifespan,
)

# CORS（允许前端跨域访问）
app.add_middleware(
    CORSMiddleware,
    allow_origins=["http://localhost:5173", "http://localhost:8080", "*"],
    allow_credentials=True,
    allow_methods=["*"],
    allow_headers=["*"],
)

# 存储活跃的 ChatHandler 实例
_active_handlers: dict = {}

# LLM 连通性缓存（避免每次健康检查都调用 LLM API）
_llm_cache = {"result": None, "timestamp": 0}
_LLM_CACHE_TTL = 60  # 缓存 60 秒


def _get_or_create_handler(session_id: str = None, jwt_token: str = None,
                            user_id: str = "anonymous", user_role: str = "OPERATOR") -> ChatHandler:
    """获取或创建 ChatHandler"""
    global _active_handlers
    if session_id and session_id in _active_handlers:
        handler = _active_handlers[session_id]
        if jwt_token:
            handler.jwt_token = jwt_token
        return handler

    handler = ChatHandler(
        jwt_token=jwt_token,
        user_id=user_id,
        user_role=user_role,
        session_id=session_id,
    )
    _active_handlers[handler.session_id] = handler
    # 限制活跃会话数
    if len(_active_handlers) > 200:
        oldest_key = next(iter(_active_handlers))
        del _active_handlers[oldest_key]
    return handler


# ==================== API 端点 ====================

@app.get("/health")
def health(force_check: bool = Query(False, description="强制重新检查 LLM 连通性（绕过缓存）")):
    """
    健康检查 — 报告 Agent 服务状态 + LLM 连通性（结果缓存 60 秒）。
    前端可据此判断 AI 对话功能是否可用。
    """
    global _llm_cache
    cfg = load_config()
    mem = get_memory_manager()

    # LLM 连通性检查（带缓存）
    import time as _time
    now = _time.time()
    if force_check or _llm_cache["result"] is None or (now - _llm_cache["timestamp"]) > _LLM_CACHE_TTL:
        _llm_cache["result"] = test_llm_connectivity(timeout=6)
        _llm_cache["timestamp"] = now

    llm_status = _llm_cache["result"]

    return {
        "status": "UP" if llm_status["reachable"] else "DEGRADED",
        "service": "SmartEnergy AI Agent",
        "version": "1.0.0",
        "model": cfg.model,
        "active_sessions": mem.get_stats()["active_sessions"],
        "llm": {
            "reachable": llm_status["reachable"],
            "message": llm_status["message"],
            "model": llm_status["model"],
            "latency_ms": llm_status["latency_ms"],
            "cached": not force_check,
        },
    }


# ---- 配置管理 ----

@app.get("/agent/config")
def get_config():
    """获取公开配置（API Key脱敏）"""
    return get_public_config()


@app.put("/agent/config")
def update_agent_config(req: ConfigUpdateRequest):
    """
    更新Agent配置。仅ADMIN角色可操作。
    """
    if req.role.upper() != "ADMIN":
        raise HTTPException(status_code=403, detail="仅管理员(ADMIN)可修改配置")

    updates = {}
    if req.api_key is not None:
        updates["api_key"] = req.api_key
    if req.model is not None:
        updates["model"] = req.model
    if req.max_tokens is not None:
        updates["max_tokens"] = req.max_tokens
    if req.temperature is not None:
        updates["temperature"] = req.temperature
    if req.timeout is not None:
        updates["timeout"] = req.timeout
    if req.enable_thinking is not None:
        updates["enable_thinking"] = req.enable_thinking
    if req.system_prompt is not None:
        updates["system_prompt"] = req.system_prompt
    if req.max_history_turns is not None:
        updates["max_history_turns"] = req.max_history_turns

    if not updates:
        raise HTTPException(status_code=400, detail="没有需要更新的字段")

    success = update_config(updates, req.role)
    if not success:
        raise HTTPException(status_code=403, detail="配置更新失败")

    logger.info(f"配置已更新: {list(updates.keys())}")
    return {"success": True, "updated_fields": list(updates.keys())}


@app.put("/agent/config/api-key")
def update_api_key_endpoint(api_key: str = Query(..., description="新的API Key"),
                             role: str = Query(..., description="请求者角色")):
    """
    单独更新API Key。仅ADMIN角色可操作。
    """
    if role.upper() != "ADMIN":
        raise HTTPException(status_code=403, detail="仅管理员(ADMIN)可修改API Key")
    success = update_api_key(api_key, role)
    if not success:
        raise HTTPException(status_code=403, detail="API Key更新失败")
    return {"success": True, "message": "API Key已更新"}


# ---- 对话 ----

@app.post("/agent/chat", response_model=ChatResponse)
def chat(req: ChatRequest):
    """
    非流式对话 — 发送消息并获取完整回复。
    """
    handler = _get_or_create_handler(
        session_id=req.session_id,
        jwt_token=req.jwt_token,
        user_id=req.user_id,
        user_role=req.user_role,
    )
    result = handler.chat(req.message)
    return result


@app.post("/agent/chat/stream")
async def chat_stream(req: ChatRequest):
    """
    流式对话 — SSE格式实时输出。
    支持思考过程展示（reasoning_content）。
    """
    handler = _get_or_create_handler(
        session_id=req.session_id,
        jwt_token=req.jwt_token,
        user_id=req.user_id,
        user_role=req.user_role,
    )
    return StreamingResponse(
        handler.chat_stream(req.message),
        media_type="text/event-stream",
        headers={
            "Cache-Control": "no-cache",
            "Connection": "keep-alive",
            "X-Accel-Buffering": "no",
        }
    )


# ---- Tool 管理 ----

@app.get("/agent/tools")
def list_tools():
    """获取所有可用Tool目录"""
    cats = get_tools_by_category()
    result = {}
    for cat, tools in cats.items():
        result[cat] = [
            {
                "tool_id": t.tool_id,
                "name": t.name,
                "description": t.description,
                "permission_level": t.permission_level,
                "api_endpoint": t.api_endpoint,
                "parameters": [{"name": p.name, "type": p.type, "description": p.description, "required": p.required} for p in t.parameters],
            }
            for t in tools
        ]
    return {"total": sum(len(v) for v in result.values()), "categories": result}


@app.post("/agent/tools/execute")
def execute_tool_endpoint(req: ToolExecuteRequest):
    """直接执行Tool（绕过LLM）"""
    result = execute_tool(req.tool_name, req.arguments, req.jwt_token)
    return result


# ---- 会话管理 ----

@app.get("/agent/sessions")
def list_sessions(user_id: str = Query("", description="按用户筛选")):
    """列出活跃会话"""
    mem = get_memory_manager()
    return {"sessions": mem.list_sessions(user_id)}


@app.delete("/agent/sessions/{session_id}")
def delete_session(session_id: str):
    """删除指定会话"""
    mem = get_memory_manager()
    success = mem.delete_session(session_id)
    if session_id in _active_handlers:
        del _active_handlers[session_id]
    return {"success": success, "session_id": session_id}


# ---- 子Agent清单与管理 ----

@app.get("/agent/agents")
def list_agents():
    """获取所有子Agent清单（含独立LLM状态、自主级别）"""
    orch = AgentOrchestrator()
    return {"agents": orch.get_agent_manifest()}


@app.get("/agent/agents/config")
def list_agent_configs():
    """获取所有子Agent的配置信息（API Key脱敏）"""
    agents = load_agent_configs()
    result = {}
    for key, ac in agents.items():
        key_preview = ac.api_key[:4] + "****" + ac.api_key[-4:] if len(ac.api_key) > 8 else "****"
        result[key] = {
            "name": ac.name,
            "api_key": key_preview,
            "model": ac.model,
            "autonomy_level": ac.autonomy_level,
            "description": ac.description,
        }
    return {"agents": result}


@app.put("/agent/agents/{agent_key}/config")
def update_agent_config_endpoint(agent_key: str, req: ConfigUpdateRequest):
    """更新指定子Agent的配置。仅ADMIN角色可操作。"""
    if req.role.upper() != "ADMIN":
        raise HTTPException(status_code=403, detail="仅管理员(ADMIN)可修改Agent配置")

    updates = {}
    if req.api_key is not None:
        updates["api_key"] = req.api_key
    if req.model is not None:
        updates["model"] = req.model
    if req.enable_thinking is not None:
        updates["enable_thinking"] = req.enable_thinking

    if not updates:
        raise HTTPException(status_code=400, detail="没有需要更新的字段")

    success = update_agent_config(agent_key, updates, req.role)
    if not success:
        raise HTTPException(status_code=403, detail="配置更新失败")

    logger.info(f"Agent配置已更新: {agent_key} -> {list(updates.keys())}")
    return {"success": True, "agent_key": agent_key, "updated_fields": list(updates.keys())}


class DelegateRequest(BaseModel):
    question: str = Field(..., description="委托问题", min_length=1, max_length=4000)
    agent_key: str = Field(..., description="目标子Agent key")
    context: Optional[str] = Field(None, description="可选的上下文数据")
    jwt_token: Optional[str] = Field(None, description="JWT Token")


@app.post("/agent/delegate")
def delegate_to_agent(req: DelegateRequest):
    """
    将问题委托给指定子Agent，子Agent使用自己的LLM进行专业推理。
    总管Agent可以将复杂任务分解后委托给专业子Agent。
    """
    orch = AgentOrchestrator(jwt_token=req.jwt_token)
    result = orch.delegate_to_agent(req.agent_key, req.question, req.context)
    return result


class BroadcastRequest(BaseModel):
    question: str = Field(..., description="广播问题", min_length=1, max_length=4000)
    jwt_token: Optional[str] = Field(None, description="JWT Token")


@app.post("/agent/broadcast")
def broadcast_to_agents(req: BroadcastRequest):
    """
    向所有子Agent广播问题，每个Agent用自己的LLM独立回答。
    用于需要多角度综合分析的问题。
    """
    orch = AgentOrchestrator(jwt_token=req.jwt_token)
    results = orch.broadcast_to_agents(req.question)
    return {"question": req.question, "agents": results}


@app.get("/agent/agents/check")
def check_all_agents():
    """
    检查所有Agent（总管+6个子Agent）的API连通性。
    返回每个Agent的连通状态、延迟和模型信息。
    """
    return test_all_agents_connectivity(timeout=5)


# ---- 知识库管理 ----

# 导入 grounding 模块
try:
    from .grounding import (
        search_knowledge_base, get_all_knowledge_summary,
        add_knowledge_entry, validate_output, sanitize_response
    )
except ImportError:
    from grounding import (
        search_knowledge_base, get_all_knowledge_summary,
        add_knowledge_entry, validate_output, sanitize_response
    )


@app.get("/agent/knowledge-base")
def get_kb_summary():
    """获取知识库总览（设备数、故障模式数、SOP数）"""
    return {"summary": get_all_knowledge_summary()}


@app.get("/agent/knowledge-base/search")
def search_kb(query: str, kb_type: str = "all"):
    """
    检索知识库。
    kb_type: all | device | fault | sop
    """
    if kb_type in ("device", "fault", "sop"):
        orch = AgentOrchestrator()
        ka = orch.sub_agents.get("knowledge_agent")
        if ka:
            result = ka.search_local_kb(query, kb_type)
            return {"query": query, "type": kb_type, "result": result}
        return {"error": "知识管理Agent未初始化"}

    results = search_knowledge_base(query)
    return {"query": query, "type": "all", "results": results}


class KBEntryRequest(BaseModel):
    category: str = Field(..., description="知识库类别: device_specs | fault_patterns | sops")
    entry: dict = Field(..., description="知识库条目")
    role: str = Field(default="ADMIN", description="操作者角色")


@app.post("/agent/knowledge-base/entry")
def add_kb_entry(req: KBEntryRequest):
    """
    向知识库添加条目。仅 ADMIN 可操作。
    这是知识管理Agent维护知识库的核心接口。
    """
    if req.role.upper() != "ADMIN":
        raise HTTPException(status_code=403, detail="仅管理员(ADMIN)可修改知识库")

    if req.category not in ("device_specs", "fault_patterns", "sops"):
        raise HTTPException(status_code=400, detail=f"无效的知识库类别: {req.category}")

    success = add_knowledge_entry(req.category, req.entry)
    return {"success": success, "category": req.category}


class ValidateRequest(BaseModel):
    response: str = Field(..., description="待校验的AI回复")
    agent_type: Optional[str] = Field(None, description="Agent类型")


@app.post("/agent/validate-output")
def validate_ai_output(req: ValidateRequest):
    """
    校验 AI 输出是否符合规范。
    用于前端展示AI回复前的质量检查。
    """
    is_valid, violations = validate_output(req.response, req.agent_type)
    if not is_valid:
        sanitized = sanitize_response(req.response, req.agent_type)
        return {
            "valid": False,
            "violations": violations,
            "sanitized": sanitized,
            "warning": "AI回复包含模糊或违规表达，已自动标注"
        }
    return {"valid": True, "violations": []}


# ---- 系统提示词 ----

@app.get("/agent/system-prompt")
def get_system_prompt():
    """获取当前系统提示词上下文（含多Agent能力矩阵）"""
    orch = AgentOrchestrator()
    return {"system_prompt_context": orch.get_system_prompt_context()}


# ---- LLM 连通性测试 ----

@app.get("/agent/llm-check")
def check_llm():
    """
    测试 LLM API 连通性（强制实时检查，不读缓存）。
    返回是否可访问、延迟、模型信息。
    """
    global _llm_cache
    import time as _time
    result = test_llm_connectivity(timeout=8)
    _llm_cache["result"] = result
    _llm_cache["timestamp"] = _time.time()
    return result


# ==================== 启动入口 ====================

if __name__ == "__main__":
    import uvicorn
    logger.info("启动 SmartEnergy AI Agent Service on port 8001")
    uvicorn.run(
        "agent_service:app",
        host="0.0.0.0",
        port=8001,
        reload=False,
        log_level="info",
    )
