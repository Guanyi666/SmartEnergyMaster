/**
 * Agent API 封装 — 与 Python Agent 服务 (:8001) 通信
 */
import { ElMessage } from 'element-plus'
import { getSessionToken } from '../utils/session'

const AGENT_BASE = 'http://localhost:8001'

/**
 * 通用请求封装
 */
async function agentRequest(path, options = {}) {
  const url = `${AGENT_BASE}${path}`
  const token = getSessionToken()
  const headers = {
    'Content-Type': 'application/json',
    ...(token ? { Authorization: `Bearer ${token}` } : {}),
    ...options.headers
  }
  try {
    const res = await fetch(url, { ...options, headers })
    if (!res.ok) {
      const err = await res.json().catch(() => ({ detail: res.statusText }))
      throw new Error(err.detail || err.message || `HTTP ${res.status}`)
    }
    return await res.json()
  } catch (e) {
    if (options.silent) return { error: e.message }
    ElMessage.error(`Agent 服务异常: ${e.message}`)
    throw e
  }
}

// ==================== 对话 ====================

/**
 * 非流式对话
 */
export function agentChat(message, sessionId, userRole = 'OPERATOR') {
  const token = getSessionToken()
  return agentRequest('/agent/chat', {
    method: 'POST',
    body: JSON.stringify({
      message,
      session_id: sessionId || null,
      jwt_token: token || null,
      user_role: userRole,
      user_id: 'web-user'
    })
  })
}

/**
 * 流式对话 — 返回 fetch Response（由调用方处理 SSE）
 */
export function agentChatStream(message, sessionId, userRole = 'OPERATOR') {
  const token = getSessionToken()
  return fetch(`${AGENT_BASE}/agent/chat/stream`, {
    method: 'POST',
    headers: {
      'Content-Type': 'application/json',
      ...(token ? { Authorization: `Bearer ${token}` } : {})
    },
    body: JSON.stringify({
      message,
      session_id: sessionId || null,
      jwt_token: token || null,
      user_role: userRole,
      user_id: 'web-user'
    })
  })
}

// ==================== 配置 ====================

/**
 * 获取Agent公开配置
 */
export function getAgentConfig() {
  return agentRequest('/agent/config', { silent: false })
}

/**
 * 更新Agent配置（仅ADMIN）
 */
export function updateAgentConfig(payload) {
  return agentRequest('/agent/config', {
    method: 'PUT',
    body: JSON.stringify(payload)
  })
}

/**
 * 更新API Key（仅ADMIN）
 */
export function updateAgentApiKey(apiKey, role) {
  return agentRequest(`/agent/config/api-key?api_key=${encodeURIComponent(apiKey)}&role=${role}`, {
    method: 'PUT'
  })
}

/**
 * 获取所有子Agent配置（含脱敏API Key）
 */
export function getAgentConfigs() {
  return agentRequest('/agent/agents/config', { silent: true })
}

/**
 * 更新指定子Agent的配置（仅ADMIN）
 */
export function updateSubAgentConfig(agentKey, payload) {
  return agentRequest(`/agent/agents/${agentKey}/config`, {
    method: 'PUT',
    body: JSON.stringify(payload)
  })
}

/**
 * 检查所有Agent的API连通性
 */
export function checkAllAgentsHealth() {
  return agentRequest('/agent/agents/check', { silent: true })
}

// ==================== Tool ====================

/**
 * 获取所有可用Tool
 */
export function getAgentTools() {
  return agentRequest('/agent/tools')
}

/**
 * 直接执行Tool
 */
export function executeAgentTool(toolName, args) {
  const token = getSessionToken()
  return agentRequest('/agent/tools/execute', {
    method: 'POST',
    body: JSON.stringify({
      tool_name: toolName,
      arguments: args,
      jwt_token: token || null
    })
  })
}

// ==================== 会话 ====================

/**
 * 获取会话列表
 */
export function getAgentSessions() {
  return agentRequest('/agent/sessions')
}

/**
 * 删除会话
 */
export function deleteAgentSession(sessionId) {
  return agentRequest(`/agent/sessions/${sessionId}`, { method: 'DELETE' })
}

// ==================== Agent清单 ====================

/**
 * 获取子Agent清单
 */
export function getAgentList() {
  return agentRequest('/agent/agents')
}

// ==================== 健康检查 ====================

/**
 * Agent服务健康检查（含 LLM 连通性状态）
 */
export function agentHealth() {
  return agentRequest('/health', { silent: true })
}

/**
 * LLM 连通性专项检查
 */
export function checkLLM() {
  return agentRequest('/agent/llm-check', { silent: true })
}
