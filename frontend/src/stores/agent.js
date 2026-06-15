/**
 * Agent 对话状态管理 (Pinia Store)
 */
import { defineStore } from 'pinia'
import { ref, computed } from 'vue'
import { agentChatStream, agentChat, deleteAgentSession, agentHealth } from '../api/agent'
import { readStoredSession } from '../utils/session'

export const useAgentStore = defineStore('agent', () => {
  // ========== 状态 ==========
  const isOpen = ref(false)                    // 面板是否打开
  const isMinimized = ref(false)               // 是否最小化
  const isLoading = ref(false)                 // 是否正在加载
  const sessionId = ref(null)                  // 当前会话ID
  const messages = ref([])                     // 对话消息列表
  const streamContent = ref('')                // 流式输出的当前内容
  const streamReasoning = ref('')              // 流式输出的思考过程
  const error = ref('')                        // 错误信息
  const unreadCount = ref(0)                   // 未读消息数
  const llmReachable = ref(true)               // LLM 是否可达
  const llmStatusMessage = ref('')             // LLM 状态描述

  // ========== 计算属性 ==========
  const messageCount = computed(() => messages.value.length)
  const lastMessage = computed(() => messages.value[messages.value.length - 1] || null)

  // ========== 方法 ==========

  /**
   * 切换面板开关
   */
  function toggle() {
    isOpen.value = !isOpen.value
    isMinimized.value = false
    if (isOpen.value) {
      unreadCount.value = 0
    }
  }

  /**
   * 最小化面板
   */
  function minimize() {
    isMinimized.value = true
    isOpen.value = false
  }

  /**
   * 展开面板
   */
  function expand() {
    isMinimized.value = false
    isOpen.value = true
    unreadCount.value = 0
  }

  /**
   * 获取用户角色
   */
  function getUserRole() {
    const session = readStoredSession()
    return session?.user?.role || 'OPERATOR'
  }

  /**
   * 发送消息（流式）
   */
  async function sendMessage(text) {
    if (!text?.trim() || isLoading.value) return

    // 添加用户消息
    messages.value.push({
      role: 'user',
      content: text,
      timestamp: Date.now()
    })

    isLoading.value = true
    error.value = ''
    streamContent.value = ''
    streamReasoning.value = ''

    // 临时AI消息占位
    const aiMsgIndex = messages.value.length
    messages.value.push({
      role: 'assistant',
      content: '',
      reasoning: '',
      timestamp: Date.now(),
      streaming: true
    })

    try {
      const response = await agentChatStream(text, sessionId.value, getUserRole())

      if (!response.ok) {
        throw new Error(`HTTP ${response.status}: ${response.statusText}`)
      }

      const reader = response.body.getReader()
      const decoder = new TextDecoder()
      let buffer = ''

      while (true) {
        const { done, value } = await reader.read()
        if (done) break

        buffer += decoder.decode(value, { stream: true })
        const lines = buffer.split('\n')
        buffer = lines.pop() || ''

        for (const line of lines) {
          if (line.startsWith('data: ')) {
            const data = line.slice(6).trim()
            if (!data || data === '[DONE]') continue

            try {
              const parsed = JSON.parse(data)
              handleStreamEvent(parsed, aiMsgIndex)
            } catch (e) {
              // 非JSON数据，忽略
            }
          }
        }
      }

      // 标记流式完成
      messages.value[aiMsgIndex].streaming = false

    } catch (e) {
      error.value = e.message
      llmReachable.value = false
      // 区分连接失败和 LLM 不可用
      if (e.message.includes('Failed to fetch') || e.message.includes('NetworkError')) {
        messages.value[aiMsgIndex].content = `❌ **无法连接到 Agent 服务**\n\nAgent 服务（端口 8001）未启动或不可达。\n\n> 💡 请确认：\n> 1. Agent 服务是否已启动\n> 2. 如果后端已配置自动启动，请等待几秒后重试\n> 3. 或手动启动: \`cd ai_models && python -m uvicorn agent.agent_service:app --host 0.0.0.0 --port 8001\``
      } else {
        messages.value[aiMsgIndex].content = `❌ **Agent 服务异常**\n\n${e.message}\n\n由于本地无法部署大模型，智能对话功能需要外部 LLM 服务支持。请检查网络连接和 API 配置后重试。`
      }
      messages.value[aiMsgIndex].streaming = false
    } finally {
      isLoading.value = false
    }
  }

  /**
   * 发送消息（非流式，降级方案）
   */
  async function sendMessageNonStream(text) {
    if (!text?.trim() || isLoading.value) return

    messages.value.push({
      role: 'user',
      content: text,
      timestamp: Date.now()
    })

    isLoading.value = true
    error.value = ''

    try {
      const result = await agentChat(text, sessionId.value, getUserRole())
      sessionId.value = result.session_id

      messages.value.push({
        role: 'assistant',
        content: result.content,
        timestamp: Date.now(),
        toolCalls: result.tool_calls || [],
        elapsed: result.elapsed_seconds
      })
    } catch (e) {
      error.value = e.message
      messages.value.push({
        role: 'assistant',
        content: `⚠️ ${e.message}`,
        timestamp: Date.now()
      })
    } finally {
      isLoading.value = false
    }
  }

  /**
   * 处理流式事件
   */
  function handleStreamEvent(data, msgIndex) {
    switch (data.type) {
      case 'meta':
        sessionId.value = data.session_id
        break
      case 'intent':
        // 可展示意图识别结果
        messages.value[msgIndex].intent = data.intent
        messages.value[msgIndex].agent = data.agent
        break
      case 'reasoning':
        messages.value[msgIndex].reasoning += data.content
        streamReasoning.value += data.content
        break
      case 'reasoning_end':
        // 思考结束
        break
      case 'content':
        messages.value[msgIndex].content += data.content
        streamContent.value += data.content
        break
      case 'analysis':
        messages.value[msgIndex].analysis = data.content
        break
      case 'error':
        // LLM 不可用等错误 → 追加到消息内容
        messages.value[msgIndex].content += data.content
        llmReachable.value = false
        break
      case 'done':
        messages.value[msgIndex].elapsed = data.elapsed
        break
    }
  }

  /**
   * 清空当前会话
   */
  async function clearSession() {
    if (sessionId.value) {
      try { await deleteAgentSession(sessionId.value) } catch (e) { /* ignore */ }
    }
    sessionId.value = null
    messages.value = []
    streamContent.value = ''
    streamReasoning.value = ''
    error.value = ''
  }

  /**
   * 新建会话
   */
  function newSession() {
    sessionId.value = null
    messages.value = []
    streamContent.value = ''
    streamReasoning.value = ''
    error.value = ''
  }

  /**
   * 检查 LLM 连通性
   */
  async function checkLLMHealth() {
    try {
      const res = await agentHealth()
      if (res?.llm) {
        llmReachable.value = res.llm.reachable
        llmStatusMessage.value = res.llm.message || ''
      }
      return llmReachable.value
    } catch {
      llmReachable.value = false
      llmStatusMessage.value = 'Agent 服务不可达'
      return false
    }
  }

  return {
    // 状态
    isOpen, isMinimized, isLoading, sessionId, messages,
    streamContent, streamReasoning, error, unreadCount,
    llmReachable, llmStatusMessage,
    // 计算
    messageCount, lastMessage,
    // 方法
    toggle, minimize, expand, sendMessage, sendMessageNonStream,
    clearSession, newSession, checkLLMHealth
  }
})
