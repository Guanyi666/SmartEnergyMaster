"""
记忆管理器 (Memory Manager)

管理对话上下文与历史记录。
- 短期记忆：当前会话的对话轮次（滑动窗口）
- 会话存储：按 session_id 隔离的多用户对话
- 上下文压缩：超出窗口时自动摘要旧对话
"""
import time
import uuid
from typing import Dict, List, Optional
from dataclasses import dataclass, field
from collections import OrderedDict
from loguru import logger


@dataclass
class ChatMessage:
    """单条对话消息"""
    role: str           # "user" | "assistant" | "system" | "tool"
    content: str
    timestamp: float = field(default_factory=time.time)
    tool_calls: Optional[List[dict]] = None
    tool_results: Optional[List[dict]] = None

    def to_openai(self) -> dict:
        msg = {"role": self.role, "content": self.content}
        if self.tool_calls:
            msg["tool_calls"] = self.tool_calls
        return msg


@dataclass
class ConversationSession:
    """一次对话会话"""
    session_id: str
    user_id: str = ""
    user_role: str = "OPERATOR"
    jwt_token: str = ""
    messages: List[ChatMessage] = field(default_factory=list)
    created_at: float = field(default_factory=time.time)
    last_active: float = field(default_factory=time.time)
    metadata: dict = field(default_factory=dict)

    def to_summary(self) -> str:
        """生成会话摘要"""
        return f"会话 {self.session_id[:8]} — 用户: {self.user_id} — {len(self.messages)} 条消息"


class MemoryManager:
    """
    记忆管理器 — 多会话对话记忆存储。
    每个 session_id 对应一个独立的对话上下文。
    """

    def __init__(self, max_history_turns: int = 10, max_sessions: int = 100):
        self.max_history_turns = max_history_turns  # 每个会话保留的最大轮次
        self.max_sessions = max_sessions
        self._sessions: OrderedDict[str, ConversationSession] = OrderedDict()

    def create_session(self, user_id: str = "", user_role: str = "OPERATOR",
                       jwt_token: str = "", metadata: dict = None) -> str:
        """创建新会话，返回 session_id"""
        sid = str(uuid.uuid4())[:12]
        session = ConversationSession(
            session_id=sid,
            user_id=user_id,
            user_role=user_role,
            jwt_token=jwt_token,
            metadata=metadata or {},
        )
        self._sessions[sid] = session
        self._evict_if_needed()
        logger.info(f"创建会话: {sid} (user={user_id}, role={user_role})")
        return sid

    def get_session(self, session_id: str) -> Optional[ConversationSession]:
        """获取会话"""
        session = self._sessions.get(session_id)
        if session:
            session.last_active = time.time()
        return session

    def add_message(self, session_id: str, role: str, content: str,
                    tool_calls: List[dict] = None,
                    tool_results: List[dict] = None) -> bool:
        """向会话添加一条消息"""
        session = self.get_session(session_id)
        if not session:
            logger.warning(f"会话不存在: {session_id}")
            return False
        msg = ChatMessage(
            role=role, content=content,
            tool_calls=tool_calls, tool_results=tool_results
        )
        session.messages.append(msg)
        self._trim(session)
        return True

    def get_messages_for_llm(self, session_id: str,
                              system_prompt: str = "") -> List[dict]:
        """
        获取格式化的消息列表（供 LLM 调用）。
        包含系统提示词 + 对话历史（截断到 max_history_turns 轮）。
        """
        session = self.get_session(session_id)
        messages = []

        # 系统提示词
        if system_prompt:
            messages.append({"role": "system", "content": system_prompt})

        if session and session.messages:
            # 截取最近 N 轮（一轮 = user + assistant）
            recent = session.messages
            if len(recent) > self.max_history_turns * 2:
                recent = recent[-(self.max_history_turns * 2):]
            for m in recent:
                msg = m.to_openai()
                if m.tool_results:
                    # 工具结果合并到 assistant 消息后
                    pass
                messages.append(msg)
        return messages

    def get_conversation_text(self, session_id: str) -> str:
        """获取对话纯文本（用于摘要）"""
        session = self.get_session(session_id)
        if not session:
            return ""
        lines = []
        for m in session.messages:
            role_label = {"user": "用户", "assistant": "AI", "system": "系统", "tool": "工具"}.get(m.role, m.role)
            lines.append(f"[{role_label}]: {m.content[:200]}")
        return "\n".join(lines)

    def clear_session(self, session_id: str) -> bool:
        """清除指定会话"""
        if session_id in self._sessions:
            del self._sessions[session_id]
            return True
        return False

    def delete_session(self, session_id: str) -> bool:
        """删除会话（同 clear）"""
        return self.clear_session(session_id)

    def list_sessions(self, user_id: str = "") -> List[dict]:
        """列出会话（可按用户筛选）"""
        result = []
        for s in self._sessions.values():
            if user_id and s.user_id != user_id:
                continue
            result.append({
                "session_id": s.session_id,
                "user_id": s.user_id,
                "user_role": s.user_role,
                "message_count": len(s.messages),
                "created_at": s.created_at,
                "last_active": s.last_active,
                "first_message": s.messages[0].content[:100] if s.messages else "",
            })
        return sorted(result, key=lambda x: x["last_active"], reverse=True)

    def _trim(self, session: ConversationSession):
        """截断过长对话（保留最近 N 轮，旧消息做摘要处理）"""
        max_msgs = self.max_history_turns * 2 + 2  # +2 容差
        if len(session.messages) > max_msgs:
            # 保留最近的消息
            overflow = len(session.messages) - max_msgs
            old = session.messages[:overflow]
            recent = session.messages[overflow:]
            # 生成摘要
            summary = f"[已压缩 {len(old)} 条历史消息] " + " ".join(
                m.content[:50] for m in old[-3:]
            )
            summary_msg = ChatMessage(role="system", content=summary)
            session.messages = [summary_msg] + recent
            logger.debug(f"会话 {session.session_id} 压缩了 {overflow} 条旧消息")

    def _evict_if_needed(self):
        """淘汰最旧的会话（超出 max_sessions 限制）"""
        while len(self._sessions) > self.max_sessions:
            oldest_key = next(iter(self._sessions))
            del self._sessions[oldest_key]
            logger.debug(f"淘汰旧会话: {oldest_key}")

    def get_stats(self) -> dict:
        """获取记忆管理器统计"""
        total_msgs = sum(len(s.messages) for s in self._sessions.values())
        return {
            "active_sessions": len(self._sessions),
            "total_messages": total_msgs,
            "max_sessions": self.max_sessions,
            "max_turns_per_session": self.max_history_turns,
        }


# 全局单例
_memory_manager: Optional[MemoryManager] = None


def get_memory_manager(max_history_turns: int = 10) -> MemoryManager:
    global _memory_manager
    if _memory_manager is None:
        _memory_manager = MemoryManager(max_history_turns=max_history_turns)
    return _memory_manager
