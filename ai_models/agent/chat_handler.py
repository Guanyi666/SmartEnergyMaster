"""
对话处理器 (Chat Handler)

集成阿里云百炼 DashScope API，支持：
  - 普通对话（含 Tool Calling）
  - 流式输出（SSE）
  - 深度思考模式（Reasoning Content）
  - 对话上下文管理
"""
import json
import time
import asyncio
from typing import AsyncGenerator, Dict, List, Optional, Tuple
from openai import OpenAI
from loguru import logger

try:
    from .agent_config import load_config, AgentConfig
    from .tools import get_all_tools_for_llm, execute_tool, TOOL_REGISTRY, get_tools_summary
    from .memory import MemoryManager, get_memory_manager
    from .orchestrator import AgentOrchestrator
    from .grounding import (
        build_ground_truth_block, validate_output, sanitize_response,
        build_agent_system_prompt, GLOBAL_FORBIDDEN
    )
except ImportError:
    from agent_config import load_config, AgentConfig
    from tools import get_all_tools_for_llm, execute_tool, TOOL_REGISTRY, get_tools_summary
    from memory import MemoryManager, get_memory_manager
    from orchestrator import AgentOrchestrator
    from grounding import (
        build_ground_truth_block, validate_output, sanitize_response,
        build_agent_system_prompt, GLOBAL_FORBIDDEN
    )


class ChatHandler:
    """
    LLM 对话处理器

    封装百炼 API 调用 + Tool Calling 循环。
    支持 L0-L3 权限模型和自动执行级别控制。
    """

    def __init__(self, jwt_token: str = None, user_id: str = "",
                 user_role: str = "OPERATOR", session_id: str = None):
        self.config = load_config()
        self.jwt_token = jwt_token
        self.user_id = user_id
        self.user_role = user_role
        self.memory = get_memory_manager(self.config.max_history_turns)
        self.orchestrator = AgentOrchestrator(
            jwt_token=jwt_token,
            session_id=session_id,
            user_id=user_id,
            user_role=user_role,
            auto_execute_level=self.config.auto_execute_level
        )
        self.session_id = self.orchestrator.session_id

        # init OpenAI client (Aliyun DashScope compatible mode)
        self.client = OpenAI(
            api_key=self.config.api_key,
            base_url=self.config.base_url,
        )

        self.tools = get_all_tools_for_llm()
        self.system_prompt = self._build_system_prompt()

    def _build_system_prompt(self) -> str:
        """Build complete system prompt with permission model and capability matrix."""
        base = self.config.system_prompt
        context = self.orchestrator.get_system_prompt_context()
        auto_level = self.config.auto_execute_level

        return (
            f"{base}\n\n"
            f"## Current Session\n"
            f"- User Role: {self.user_role}\n"
            f"- Auto Execute Level: {auto_level} "
            f"(L0=QueryOnly | L1=Suggest | L2=AssistedExec | L3=FullAuto)\n\n"
            f"## Your Capabilities\n"
            f"{context}\n\n"
            f"## Permission Matrix (YOU MUST FOLLOW)\n"
            f"| Operation | Level | Notes |\n"
            f"|-----------|-------|-------|\n"
            f"| Query devices/sensors/work-orders/reports | L0 | Any role, call Tools directly |\n"
            f"| Patrol scan, anomaly screening, threshold alert | L3 | Actively detect and alert |\n"
            f"| Generate daily/weekly/diagnosis reports | L3 | Auto-generate structured reports |\n"
            f"| Energy trend analysis, forecast interpretation | L1 | Give energy-saving suggestions |\n"
            f"| Auto-dispatch work-order (skill match) | L2 | Recommend, need operator confirm |\n"
            f"| Device parameter tuning advice | L1 | Calculate optimal values, operator executes |\n"
            f"| High-risk maintenance plan | L1 | Provide case reference, human decides |\n"
            f"| Production scheduling & load shift | L1 | Simulate only, no direct execution |\n"
            f"| Emergency shutdown | FORBIDDEN | Safety critical, NEVER suggest or execute |\n"
            f"| Device physical param modification | FORBIDDEN | E.g. power limit change |\n\n"
            f"## Response Rules\n"
            f"1. Use professional, concise Chinese; Markdown format\n"
            f"2. MUST base answers on system real-time data above - never say 'unable to get data'\n"
            f"3. Bold key values with **value**, mark anomalies with warning emoji\n"
            f"4. Tag low-confidence answers with confidence level; suggest human intervention\n"
            f"5. For L2 operations, remind user to confirm; for forbidden ops, refuse firmly\n"
            f"6. When detecting device anomalies, give 3-part diagnosis: symptom -> root cause -> fix\n"
            f"7. When user asks for advice, proactively check electricity price tiers and suggest energy-saving"
        )

    def _build_messages(self, user_input: str) -> List[dict]:
        """Build messages list to send to LLM."""
        messages = [{"role": "system", "content": self.system_prompt}]

        # Load conversation history
        history = self.memory.get_messages_for_llm(self.session_id)
        # Skip system prompt (already included)
        history = [m for m in history if m.get("role") != "system"]
        messages.extend(history)

        # Add current user input
        messages.append({"role": "user", "content": user_input})

        return messages

    # ==================== Non-streaming Chat ====================

    def chat(self, user_input: str) -> Dict[str, any]:
        """
        Non-streaming chat with Tool Calling loop.
        Returns structured result. On LLM failure, returns clear error message.
        """
        start_time = time.time()
        self.memory.add_message(self.session_id, "user", user_input)

        messages = self._build_messages(user_input)

        # Tool Calling loop (max 5 rounds)
        max_rounds = 5
        tool_results_summary = []

        # Build API call params (enable_thinking via extra_body)
        api_kwargs = {
            "model": self.config.model,
            "messages": messages,
            "max_tokens": self.config.max_tokens,
            "timeout": self.config.timeout,
        }
        if self.tools:
            api_kwargs["tools"] = self.tools
        # enable_thinking only works without temperature (DashScope requirement)
        if self.config.enable_thinking:
            api_kwargs["extra_body"] = {"enable_thinking": True}
        else:
            api_kwargs["temperature"] = self.config.temperature

        for round_num in range(max_rounds):
            try:
                response = self.client.chat.completions.create(**api_kwargs)
            except Exception as e:
                logger.error(f"LLM call failed: {e}")
                error_msg = self._classify_llm_error(e)
                self.memory.add_message(self.session_id, "assistant", error_msg)
                return self._build_result(error_msg, start_time, [])

            choice = response.choices[0]
            assistant_msg = choice.message

            # Check for Tool Calls
            if assistant_msg.tool_calls:
                # Record assistant message (with tool_calls)
                messages.append({
                    "role": "assistant",
                    "content": assistant_msg.content or "",
                    "tool_calls": [
                        {
                            "id": tc.id,
                            "type": "function",
                            "function": {
                                "name": tc.function.name,
                                "arguments": tc.function.arguments
                            }
                        }
                        for tc in assistant_msg.tool_calls
                    ]
                })

                # Execute each Tool Call
                for tc in assistant_msg.tool_calls:
                    tool_name = tc.function.name
                    try:
                        arguments = json.loads(tc.function.arguments)
                    except json.JSONDecodeError:
                        arguments = {}

                    logger.info(f"Tool Call: {tool_name}({arguments})")
                    result = execute_tool(tool_name, arguments, self.jwt_token)

                    # Format result
                    result_str = json.dumps(result, ensure_ascii=False, default=str)
                    tool_results_summary.append({
                        "tool": tool_name,
                        "arguments": arguments,
                        "success": result.get("success", False),
                    })

                    messages.append({
                        "role": "tool",
                        "tool_call_id": tc.id,
                        "content": result_str,
                    })
            else:
                # No Tool Call - final response
                final_content = assistant_msg.content or ""
                if not final_content.strip():
                    final_content = (
                        "AI service returned empty response. "
                        "Since local LLM deployment is not available, "
                        "please check if the external LLM service is working properly."
                    )
                self.memory.add_message(
                    self.session_id, "assistant", final_content,
                    tool_results=tool_results_summary
                )
                return self._build_result(final_content, start_time, tool_results_summary)

        # Exceeded max rounds
        fallback = (
            "Processing timeout: AI did not complete after multiple steps. "
            "Please simplify your question or retry later."
        )
        self.memory.add_message(self.session_id, "assistant", fallback)
        return self._build_result(fallback, start_time, tool_results_summary)

    # ==================== Streaming Chat ====================

    def _get_system_data(self, user_input: str) -> Tuple[str, any]:
        """
        Core method: get system real-time data via Orchestrator first.
        This data becomes LLM context, enabling LLM to answer based on real data.
        """
        try:
            intent_result = self.orchestrator.parse_intent(user_input)
            local_result = self.orchestrator.execute(intent_result)
            logger.info(
                f"Orchestrator: intent={intent_result.intent}, "
                f"agent={intent_result.agent_name}"
            )
            return local_result, intent_result
        except Exception as e:
            logger.warning(f"Orchestrator execution failed: {e}")
            try:
                pm = self.orchestrator.sub_agents["production_monitor"]
                fallback = pm.get_plant_overview()
                if not fallback or "获取全厂状态失败" in fallback:
                    return (
                        "## System Status\n\n"
                        "Unable to get real-time data. Possible causes:\n"
                        "1. Backend service not running (http://localhost:8080)\n"
                        "2. No device data in database - run data_pump.py\n"
                        "3. All devices offline or unregistered\n\n"
                        "### You can:\n"
                        "- Check backend: curl http://localhost:8080/api/health\n"
                        "- Start simulator: cd data && python data_pump.py\n"
                        "- Ask me knowledge base questions (device specs, fault patterns, SOPs)"
                    ), None
                return fallback, None
            except Exception:
                return (
                    "System initializing. Data pipeline not ready. Confirm:\n"
                    "1. Spring Boot backend running (port 8080)\n"
                    "2. data_pump.py simulator pushing data\n"
                    "3. At least one device registered in database"
                ), None

    def _classify_llm_error(self, error: Exception) -> str:
        """Classify LLM exceptions into user-readable Chinese error messages."""
        error_str = str(error).lower()
        if "401" in error_str or "403" in error_str or "unauthorized" in error_str or "invalid api key" in error_str:
            return (
                "**AI Service Auth Failed**\n\n"
                "API Key is invalid or expired. Please update it at "
                "System Config -> LLM Parameters page.\n\n"
                "> Tip: If you don't have an API Key, contact admin for an Aliyun DashScope key."
            )
        elif "timeout" in error_str or "timed out" in error_str:
            return (
                "**AI Service Timeout**\n\n"
                "Connection to dashscope.aliyuncs.com timed out. "
                "Please check your network connection.\n\n"
                "> Tip: If behind a corporate firewall, you may need proxy configuration."
            )
        elif "connection" in error_str or "refused" in error_str or "resolve" in error_str or "name or service not known" in error_str or "network" in error_str:
            return (
                "**Cannot Connect to AI Service**\n\n"
                "Cannot reach Aliyun DashScope API (dashscope.aliyuncs.com). "
                "The system requires external LLM service for intelligent conversation.\n\n"
                "> Suggestion: Check network or contact admin for firewall/proxy settings."
            )
        elif "rate" in error_str or "throttl" in error_str or "quota" in error_str:
            return (
                "**API Rate Limit Exceeded**\n\n"
                "API Key quota exhausted or rate limited. "
                "Please retry later or update API Key in System Config."
            )
        else:
            return (
                f"**AI Service Call Failed**\n\n"
                f"LLM service error: `{str(error)[:200]}`\n\n"
                f"Since local LLM deployment is not available, "
                f"intelligent conversation is temporarily unavailable. "
                f"Please check network and API config and retry."
            )

    async def chat_stream(self, user_input: str) -> AsyncGenerator[str, None]:
        """
        Streaming chat - SSE format output.

        Flow:
          1. Orchestrator fetches system real-time data as "local analysis"
          2. Feed local analysis as context to LLM for natural language generation
          3. On LLM failure -> output clear error (no fallback to raw data)
          4. Note: local LLM deployment unavailable, external LLM required
        """
        start_time = time.time()
        self.memory.add_message(self.session_id, "user", user_input)

        # Send session metadata
        yield f"data: {json.dumps({'type': 'meta', 'session_id': self.session_id, 'timestamp': start_time}, ensure_ascii=False)}\n\n"

        # Step 1: Get system real data
        local_data, intent_result = self._get_system_data(user_input)

        # Send intent info
        agent_type = intent_result.agent_name if intent_result else None
        if intent_result:
            yield f"data: {json.dumps({'type': 'intent', 'intent': intent_result.intent, 'agent': intent_result.agent_name, 'level': intent_result.agent_level}, ensure_ascii=False)}\n\n"

        # Step 2: Build prompt with GROUND TRUTH anchoring
        # 核心防幻觉机制：将真实数据包装为 GROUND TRUTH 块强制注入
        ground_truth = build_ground_truth_block(local_data, agent_type)
        grounding_rules = build_agent_system_prompt(agent_type or "knowledge_agent")

        llm_messages = [
            {
                "role": "system",
                "content": (
                    f"{self.config.system_prompt}\n\n"
                    f"Current User Role: {self.user_role}\n"
                    f"Auto Execute Level: {self.config.auto_execute_level}\n"
                    f"You are the SmartEnergy AI Assistant. Answer in professional, concise Chinese.\n\n"
                    f"{ground_truth}\n\n"
                    f"{grounding_rules}"
                )
            },
            {"role": "user", "content": user_input}
        ]

        full_content = ""
        full_reasoning = ""
        is_answering = False
        llm_error_msg = None

        # Step 3: Call LLM for natural language generation
        # enable_thinking via extra_body (DashScope compatible mode)
        stream_kwargs = {
            "model": self.config.model,
            "messages": llm_messages,
            "max_tokens": self.config.max_tokens,
            "timeout": self.config.timeout,
            "stream": True,
        }
        if self.config.enable_thinking:
            stream_kwargs["extra_body"] = {"enable_thinking": True}
        else:
            stream_kwargs["temperature"] = self.config.temperature

        try:
            stream = self.client.chat.completions.create(**stream_kwargs)

            for chunk in stream:
                if not chunk.choices:
                    continue
                delta = chunk.choices[0].delta

                # Reasoning content (if model supports deep thinking)
                reasoning = getattr(delta, "reasoning_content", None)
                if reasoning is not None and str(reasoning).strip():
                    full_reasoning += reasoning
                    yield f"data: {json.dumps({'type': 'reasoning', 'content': str(reasoning)}, ensure_ascii=False)}\n\n"

                # Main content
                content = getattr(delta, "content", None)
                if content is not None and str(content).strip():
                    if not is_answering and full_reasoning:
                        yield f"data: {json.dumps({'type': 'reasoning_end'}, ensure_ascii=False)}\n\n"
                    is_answering = True
                    full_content += content
                    yield f"data: {json.dumps({'type': 'content', 'content': str(content)}, ensure_ascii=False)}\n\n"

        except Exception as e:
            logger.error(f"LLM stream call failed: {e}")
            llm_error_msg = self._classify_llm_error(e)

        # Step 4: On LLM failure, output clear error
        if llm_error_msg:
            for line in llm_error_msg.split('\n'):
                yield f"data: {json.dumps({'type': 'error', 'content': line + '\n'}, ensure_ascii=False)}\n\n"
            full_content = llm_error_msg
        elif not full_content.strip():
            empty_msg = (
                "**AI Service Returned Empty Response**\n\n"
                "The model may be temporarily unavailable or the request was rejected. "
                "Since local LLM deployment is not available, intelligent conversation is unavailable.\n\n"
                "> You can try: 1) Refresh and retry 2) Check API Key validity in System Config"
            )
            for line in empty_msg.split('\n'):
                yield f"data: {json.dumps({'type': 'error', 'content': line + '\n'}, ensure_ascii=False)}\n\n"
            full_content = empty_msg

        # Step 5: Output validation & sanitization
        if full_content.strip() and not llm_error_msg:
            sanitized = sanitize_response(full_content.strip(), agent_type)
            if sanitized != full_content.strip():
                # 输出有违规，发送警告
                yield f"data: {json.dumps({'type': 'warning', 'content': '⚠️ AI输出已通过质量校验，部分模糊表达已标注'}, ensure_ascii=False)}\n\n"
                full_content = sanitized

        # Step 6: Save to memory
        self.memory.add_message(self.session_id, "assistant", full_content.strip())

        elapsed = time.time() - start_time
        yield f"data: {json.dumps({'type': 'done', 'elapsed': round(elapsed, 2), 'session_id': self.session_id}, ensure_ascii=False)}\n\n"

    # ==================== Helpers ====================

    def _build_result(self, content: str, start_time: float,
                      tool_calls: List[dict]) -> Dict[str, any]:
        return {
            "session_id": self.session_id,
            "content": content,
            "tool_calls": tool_calls,
            "elapsed_seconds": round(time.time() - start_time, 2),
            "config": {
                "model": self.config.model,
                "auto_execute_level": self.config.auto_execute_level,
            }
        }

    def clear_history(self):
        """Clear current session history."""
        self.memory.clear_session(self.session_id)

    def get_session_info(self) -> dict:
        """Get current session info."""
        session = self.memory.get_session(self.session_id)
        if session:
            return {
                "session_id": session.session_id,
                "user_id": session.user_id,
                "user_role": session.user_role,
                "message_count": len(session.messages),
                "created_at": session.created_at,
            }
        return {"error": "Session not found"}
