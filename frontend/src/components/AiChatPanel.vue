<script setup>
/**
 * AI Chat 对话面板 — 智驭能效 系统级智能体
 *
 * 右下角浮动按钮 → 展开对话面板
 * 支持：流式对话、思考过程展示、操作采纳按钮
 * 主题：深色工业风，与现有指挥大屏风格统一
 */
import { ref, watch, nextTick, computed } from 'vue'
import { Promotion, Delete, Minus, Close } from '@element-plus/icons-vue'
import { useAgentStore } from '../stores/agent'
import { agentHealth } from '../api/agent'
import { readStoredSession } from '../utils/session'

const store = useAgentStore()

// ========== 本地状态 ==========
const inputText = ref('')
const messagesContainer = ref(null)
const isOnline = ref(false)
const llmOnline = ref(false)
const llmMessage = ref('')
const showSuggestions = ref(true)
const showReasoning = ref(true)

// ========== 用户信息 ==========
const userRole = computed(() => {
  const s = readStoredSession()
  return s?.user?.role || 'OPERATOR'
})
const ROLE_LABELS = {
  ADMIN: '系统管理员',
  OPERATOR: '运行操作员',
  MAINTENANCE_ENGINEER: '维修工程师',
  DEVICE_MANAGER: '工单管理员',
  MANAGER: '生产经理',
  HR_MANAGER: '人事管理员'
}
const roleLabel = computed(() => ROLE_LABELS[userRole.value] || userRole.value)

// ========== 快捷建议 ==========
const quickSuggestions = [
  { label: '全厂运行总览', icon: '🏭', text: '现在全厂运行情况怎么样？' },
  { label: '活跃告警查询', icon: '🚨', text: '最近有什么活跃告警？' },
  { label: 'EAF-01 实时数据', icon: '📊', text: '查询 EAF-01 当前温度和运行状态' },
  { label: '待处理工单', icon: '🔧', text: '查看所有待处理的维修工单' },
  { label: '生成运维日报', icon: '📋', text: '生成今日运维日报' },
  { label: '分时电价政策', icon: '⚡', text: '当前分时电价政策是什么？' }
]

// ========== 快捷操作按钮 ==========
const actionButtons = computed(() => {
  const last = store.lastMessage
  if (!last || last.role !== 'assistant') return []
  const content = last.content || ''
  const buttons = []
  if (content.includes('指派') || content.includes('推荐')) {
    buttons.push({ label: '确认指派', icon: '✅', action: 'confirm_assign', type: 'primary' })
    buttons.push({ label: '暂不处理', icon: '⏭️', action: 'reject', type: 'default' })
  }
  if (content.includes('调度建议') || content.includes('推迟') || content.includes('节省')) {
    buttons.push({ label: '采纳建议', icon: '👍', action: 'confirm_dispatch', type: 'primary' })
    buttons.push({ label: '暂不采纳', icon: '👎', action: 'reject', type: 'default' })
  }
  return buttons
})

// ========== 方法 ==========

async function handleSend() {
  const text = inputText.value.trim()
  if (!text || store.isLoading) return
  inputText.value = ''
  showSuggestions.value = false
  await store.sendMessage(text)
  await scrollToBottom()
}

async function sendSuggestion(text) {
  inputText.value = ''
  showSuggestions.value = false
  await store.sendMessage(text)
  await scrollToBottom()
}

function handleAction(action) {
  if (action === 'confirm_assign') store.sendMessage('确认指派，请执行')
  else if (action === 'confirm_dispatch') store.sendMessage('采纳调度建议')
  else store.sendMessage('忽略本次建议')
}

function handleKeydown(e) {
  if (e.key === 'Enter' && !e.shiftKey) {
    e.preventDefault()
    handleSend()
  }
}

async function scrollToBottom() {
  await nextTick()
  if (messagesContainer.value) {
    messagesContainer.value.scrollTop = messagesContainer.value.scrollHeight
  }
}

function handleClear() {
  store.clearSession()
  showSuggestions.value = true
}

function renderMarkdown(text) {
  if (!text) return ''
  let html = text
    .replace(/&/g, '&amp;').replace(/</g, '&lt;').replace(/>/g, '&gt;')
    // 粗体
    .replace(/\*\*(.+?)\*\*/g, '<strong>$1</strong>')
    // 代码
    .replace(/`([^`]+)`/g, '<code class="md-code">$1</code>')
    // 换行
    .replace(/\n/g, '<br>')
    // 分隔线
    .replace(/═══+/g, '<hr class="md-hr">')
    // 标题
    .replace(/^### (.+)$/gm, '<h4 class="md-h4">$1</h4>')
    .replace(/^## (.+)$/gm, '<h3 class="md-h3">$1</h3>')
    // 列表
    .replace(/^- (.+)$/gm, '<li class="md-li">$1</li>')
    // 表格行
    .replace(/^\|(.+)\|$/gm, (match) => {
      if (match.includes('---')) return ''
      const cells = match.split('|').filter(Boolean).map(c => `<span class="md-cell">${c.trim()}</span>`)
      return `<div class="md-row">${cells.join('')}</div>`
    })
  return html
}

async function checkHealth() {
  try {
    const res = await agentHealth()
    isOnline.value = res?.status === 'UP' || res?.status === 'DEGRADED'
    llmOnline.value = res?.llm?.reachable ?? false
    llmMessage.value = res?.llm?.message || ''
    // 同步到 store
    store.llmReachable = llmOnline.value
    store.llmStatusMessage = llmMessage.value
  } catch { isOnline.value = false; llmOnline.value = false }
}

watch(() => store.isOpen, (val) => { if (val) { checkHealth(); scrollToBottom() } })
watch(() => store.messages.length, () => { scrollToBottom() })
checkHealth()
</script>

<template>
  <div class="ai-chat-wrapper">
    <!-- ========== 浮动按钮 ========== -->
    <el-tooltip content="AI 智能助手 · 系统级智能体" placement="left">
      <div
        class="ai-chat-fab"
        :class="{ active: store.isOpen, pulse: store.unreadCount > 0 && !store.isOpen }"
        @click="store.toggle()"
      >
        <el-badge :value="store.unreadCount" :hidden="store.unreadCount === 0" :max="99">
          <span class="fab-icon">⚡</span>
        </el-badge>
      </div>
    </el-tooltip>

    <!-- ========== 对话面板 ========== -->
    <Transition name="slide-up">
      <div v-if="store.isOpen" class="ai-chat-panel">
        <!-- 头部 -->
        <div class="ai-chat-header">
          <div class="header-left">
            <div class="header-icon-box">
              <span class="header-icon-inner">⚡</span>
            </div>
            <div class="header-text">
              <span class="header-title">智驭能效 · AI 助手</span>
              <span class="header-subtitle">SYSTEM AI AGENT v1.0</span>
            </div>
          </div>
          <div class="header-status-row">
            <el-tooltip :content="isOnline ? (llmOnline ? 'Agent 服务正常，LLM 可用' : 'Agent 服务在线但 LLM 不可达: ' + llmMessage) : 'Agent 服务离线'" placement="bottom">
              <span class="status-dot" :class="{ online: isOnline && llmOnline, offline: !isOnline, degraded: isOnline && !llmOnline }"></span>
            </el-tooltip>
            <span class="status-text">{{ !isOnline ? 'OFFLINE' : (llmOnline ? 'AI READY' : 'NO LLM') }}</span>
          </div>
          <div class="header-actions">
            <el-button size="small" text class="header-btn" @click="handleClear" title="清空会话记录">
              <el-icon><Delete /></el-icon>
            </el-button>
            <el-button size="small" text class="header-btn" @click="store.minimize()" title="最小化面板">
              <el-icon><Minus /></el-icon>
            </el-button>
            <el-button size="small" text class="header-btn" @click="store.toggle()" title="关闭面板">
              <el-icon><Close /></el-icon>
            </el-button>
          </div>
        </div>

        <!-- 消息列表 -->
        <div ref="messagesContainer" class="ai-chat-messages">
          <!-- 空状态 + 快捷建议 -->
          <div v-if="store.messages.length === 0" class="welcome-area">
            <div class="welcome-greeting">
              <span class="greeting-role">{{ roleLabel }}</span>，您好
            </div>
            <div class="welcome-title">我是智驭能效 AI 智能助手</div>
            <div class="welcome-desc">
              我可以协助您实时监控设备运行状态、诊断故障根因、管理维修工单、分析能耗趋势。
              请选择下方快捷提问，或直接输入您的需求。
            </div>
            <!-- LLM 不可用警告 -->
            <div v-if="isOnline && !llmOnline" class="llm-warning">
              <span class="llm-warning-icon">⚠️</span>
              <span>{{ llmMessage || '外部 LLM 服务不可用，AI 对话功能暂时受限' }}</span>
            </div>
            <!-- 快捷建议 -->
            <div class="quick-suggestions">
              <div
                v-for="s in quickSuggestions"
                :key="s.label"
                class="suggestion-chip"
                @click="sendSuggestion(s.text)"
              >
                <span class="chip-icon">{{ s.icon }}</span>
                <span class="chip-label">{{ s.label }}</span>
              </div>
            </div>
          </div>

          <!-- 消息气泡 -->
          <div v-for="(msg, idx) in store.messages" :key="idx" class="message-row" :class="msg.role">
            <!-- 用户消息 -->
            <div v-if="msg.role === 'user'" class="message-bubble user-bubble">
              <div class="bubble-label">YOU</div>
              {{ msg.content }}
            </div>

            <!-- AI消息 -->
            <div v-else class="message-bubble ai-bubble">
              <div class="bubble-label">
                <span class="label-dot"></span>
                SMART ENERGY AI
                <span v-if="msg.intent" class="intent-badge">{{ msg.agent || msg.intent }}</span>
              </div>

              <!-- 深度思考 -->
              <div v-if="msg.reasoning && showReasoning" class="reasoning-box">
                <div class="reasoning-toggle" @click="showReasoning = !showReasoning">
                  <span class="reasoning-icon">💭</span> 深度思考
                  <span class="reasoning-hint">— 点击收起</span>
                </div>
                <div class="reasoning-content">{{ msg.reasoning }}</div>
              </div>

              <!-- 回复内容 -->
              <div class="ai-content" v-html="renderMarkdown(msg.content)"></div>

              <!-- 流式加载指示 -->
              <span v-if="msg.streaming && !msg.content" class="typing-indicator">
                <span class="dot"></span><span class="dot"></span><span class="dot"></span>
              </span>

              <!-- 耗时 -->
              <div v-if="msg.elapsed" class="elapsed-text">
                响应耗时 {{ msg.elapsed }}s
              </div>
            </div>
          </div>

          <!-- 加载中 -->
          <div v-if="store.isLoading" class="loading-row">
            <span class="typing-indicator">
              <span class="dot"></span><span class="dot"></span><span class="dot"></span>
            </span>
          </div>
        </div>

        <!-- 操作按钮 -->
        <div v-if="actionButtons.length > 0" class="action-bar">
          <span class="action-hint">⚡ 系统建议操作：</span>
          <el-button
            v-for="btn in actionButtons" :key="btn.action"
            :type="btn.type" size="small" round
            @click="handleAction(btn.action)"
          >
            {{ btn.icon }} {{ btn.label }}
          </el-button>
        </div>

        <!-- 输入区域 -->
        <div class="ai-chat-input">
          <el-input
            v-model="inputText"
            type="textarea"
            :rows="1"
            :autosize="{ minRows: 1, maxRows: 4 }"
            placeholder="输入您的问题，Enter 发送，Shift+Enter 换行…"
            :disabled="store.isLoading"
            @keydown="handleKeydown"
            resize="none"
            class="chat-textarea"
          />
          <el-button
            type="primary"
            :disabled="!inputText.trim() || store.isLoading"
            :loading="store.isLoading"
            @click="handleSend"
            circle
            class="send-btn"
          >
            <el-icon><Promotion /></el-icon>
          </el-button>
        </div>

        <!-- 底部 -->
        <div class="ai-chat-footer">
          <span class="footer-warn">⚠ AI 建议仅供参考，关键决策请结合人工判断</span>
          <el-switch
            v-model="showReasoning"
            size="small"
            active-text="深度思考"
            inactive-text="隐藏思考"
            class="reasoning-switch"
          />
        </div>
      </div>
    </Transition>
  </div>
</template>

<script>
export default {}
</script>

<style scoped>
/* ================================================================
   智驭能效 AI 对话面板 — 深色工业风主题
   与指挥大屏视觉体系统一（暗色背景 + 青色光效 + 玻璃质感面板）
   ================================================================ */

/* ====== CSS 变量 ====== */
.ai-chat-wrapper {
  --panel-bg: #0a1929;
  --panel-bg-elevated: #0d2137;
  --panel-border: rgba(92, 220, 255, 0.12);
  --panel-border-strong: rgba(92, 220, 255, 0.22);
  --accent-cyan: #5cdcff;
  --accent-blue: #409eff;
  --accent-green: #00d4aa;
  --text-primary: #e8edf5;
  --text-secondary: #a0b4cc;
  --text-muted: #5a7290;
  --brand-gradient: linear-gradient(135deg, #5cdcff, #409eff);
  --glass-bg: rgba(13, 37, 64, 0.85);
  position: fixed;
  bottom: 24px;
  right: 24px;
  z-index: 9999;
  font-family: 'Inter', 'PingFang SC', 'Microsoft YaHei', sans-serif;
}

/* ====== 浮动按钮 ====== */
.ai-chat-fab {
  width: 56px;
  height: 56px;
  border-radius: 50%;
  background: linear-gradient(135deg, #0d2540, #102a4a);
  border: 1.5px solid rgba(92, 220, 255, 0.3);
  display: flex;
  align-items: center;
  justify-content: center;
  cursor: pointer;
  box-shadow: 0 0 20px rgba(92, 220, 255, 0.15), 0 4px 16px rgba(0,0,0,0.5);
  transition: all 0.3s ease;
  user-select: none;
}
.ai-chat-fab:hover {
  transform: scale(1.08);
  border-color: rgba(92, 220, 255, 0.6);
  box-shadow: 0 0 32px rgba(92, 220, 255, 0.3), 0 6px 24px rgba(0,0,0,0.6);
}
.ai-chat-fab.active {
  background: linear-gradient(135deg, #0d2540, #1a3a5c);
  border-color: var(--accent-cyan);
}
.ai-chat-fab.pulse { animation: fabPulse 2.5s ease-in-out infinite; }
.fab-icon { font-size: 24px; }
@keyframes fabPulse {
  0%, 100% { box-shadow: 0 0 20px rgba(92, 220, 255, 0.15), 0 4px 16px rgba(0,0,0,0.5); }
  50% { box-shadow: 0 0 36px rgba(92, 220, 255, 0.45), 0 4px 24px rgba(0,0,0,0.7); }
}

/* ====== 面板主体 ====== */
.ai-chat-panel {
  position: absolute;
  bottom: 68px;
  right: 0;
  width: 420px;
  height: 600px;
  background: var(--panel-bg);
  border-radius: 14px;
  box-shadow:
    0 0 60px rgba(0,0,0,0.55),
    0 0 0 1px var(--panel-border),
    inset 0 1px 0 rgba(255,255,255,0.03);
  display: flex;
  flex-direction: column;
  overflow: hidden;
  backdrop-filter: blur(20px);
}

/* ====== 头部 ====== */
.ai-chat-header {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 14px 16px;
  background: linear-gradient(180deg, rgba(13,33,55,0.95), rgba(10,25,41,0.9));
  border-bottom: 1px solid var(--panel-border);
  flex-shrink: 0;
}
.header-left { display: flex; align-items: center; gap: 10px; flex: 1; min-width: 0; }
.header-icon-box {
  width: 34px; height: 34px;
  border-radius: 8px;
  background: linear-gradient(135deg, rgba(92,220,255,0.25), rgba(64,158,255,0.15));
  border: 1px solid rgba(92,220,255,0.3);
  display: grid; place-items: center;
  flex-shrink: 0;
}
.header-icon-inner { font-size: 16px; }
.header-text { display: flex; flex-direction: column; min-width: 0; }
.header-title {
  font-size: 14px; font-weight: 700; letter-spacing: 0.5px;
  color: var(--text-primary);
  white-space: nowrap;
}
.header-subtitle {
  font-size: 9px; letter-spacing: 2.5px;
  color: var(--accent-cyan); opacity: 0.7;
  margin-top: 2px;
}
.header-status-row {
  display: flex; align-items: center; gap: 6px;
  padding: 3px 10px; border-radius: 999px;
  background: rgba(0,0,0,0.3);
  border: 1px solid rgba(255,255,255,0.06);
  flex-shrink: 0;
}
.status-dot { width: 7px; height: 7px; border-radius: 50%; }
.status-dot.online { background: var(--accent-green); box-shadow: 0 0 6px var(--accent-green); }
.status-dot.offline { background: #f56c6c; box-shadow: 0 0 6px #f56c6c; }
.status-dot.degraded { background: #e6a23c; box-shadow: 0 0 6px #e6a23c; }
.status-text { font-size: 10px; letter-spacing: 1.5px; font-weight: 600; color: var(--text-secondary); }
.header-actions { display: flex; gap: 0; flex-shrink: 0; }
.header-btn { color: var(--text-muted) !important; }
.header-btn:hover { color: var(--accent-cyan) !important; }

/* ====== 消息区域 ====== */
.ai-chat-messages {
  flex: 1;
  overflow-y: auto;
  padding: 16px;
  display: flex;
  flex-direction: column;
  gap: 14px;
  background: linear-gradient(180deg, rgba(10,25,41,0.5), rgba(6,14,24,0.6));
}

/* 滚动条 */
.ai-chat-messages::-webkit-scrollbar { width: 4px; }
.ai-chat-messages::-webkit-scrollbar-track { background: transparent; }
.ai-chat-messages::-webkit-scrollbar-thumb { background: rgba(92,220,255,0.15); border-radius: 4px; }
.ai-chat-messages::-webkit-scrollbar-thumb:hover { background: rgba(92,220,255,0.3); }

/* ====== 欢迎区域 ====== */
.welcome-area { text-align: center; padding: 28px 12px 20px; }
.welcome-greeting {
  font-size: 13px; color: var(--text-muted);
  letter-spacing: 1px; margin-bottom: 8px;
}
.greeting-role {
  color: var(--accent-cyan); font-weight: 600;
  padding: 1px 8px; border-radius: 4px;
  background: rgba(92,220,255,0.1);
  border: 1px solid rgba(92,220,255,0.15);
}
.welcome-title {
  font-size: 18px; font-weight: 700; color: var(--text-primary);
  letter-spacing: 1px; margin-bottom: 10px;
}
.welcome-desc {
  font-size: 12.5px; color: var(--text-secondary);
  line-height: 1.7; max-width: 340px; margin: 0 auto 20px;
}
.llm-warning {
  display: flex; align-items: center; gap: 6px;
  padding: 8px 12px; margin: 0 auto 16px; max-width: 340px;
  background: rgba(230, 162, 60, 0.1);
  border: 1px solid rgba(230, 162, 60, 0.25);
  border-radius: 8px;
  font-size: 12px; color: #e6a23c; text-align: left;
}
.llm-warning-icon { font-size: 14px; flex-shrink: 0; }
.quick-suggestions {
  display: flex; flex-wrap: wrap; gap: 8px;
  justify-content: center; max-width: 360px; margin: 0 auto;
}
.suggestion-chip {
  display: flex; align-items: center; gap: 5px;
  padding: 6px 13px; border-radius: 18px;
  background: rgba(13,33,55,0.8);
  border: 1px solid rgba(92,220,255,0.15);
  cursor: pointer;
  transition: all 0.2s ease;
  font-size: 12px; color: var(--text-secondary);
}
.suggestion-chip:hover {
  border-color: rgba(92,220,255,0.45);
  background: rgba(92,220,255,0.08);
  color: var(--accent-cyan);
  box-shadow: 0 0 12px rgba(92,220,255,0.1);
}
.chip-icon { font-size: 14px; }
.chip-label { letter-spacing: 0.3px; }

/* ====== 消息气泡 ====== */
.message-row { display: flex; }
.message-row.user { justify-content: flex-end; }
.message-row.assistant { justify-content: flex-start; }
.message-bubble {
  max-width: 88%;
  padding: 10px 14px;
  border-radius: 12px;
  font-size: 13px;
  line-height: 1.65;
  word-break: break-word;
}
.bubble-label {
  font-size: 9px; letter-spacing: 2px; font-weight: 600;
  margin-bottom: 6px;
  display: flex; align-items: center; gap: 6px;
}
.user-bubble {
  background: linear-gradient(135deg, #1a4a7a, #0d3060);
  border: 1px solid rgba(92,220,255,0.2);
  color: #dce8f5;
  border-bottom-right-radius: 3px;
}
.user-bubble .bubble-label { color: rgba(92,220,255,0.8); }
.ai-bubble {
  background: rgba(13,37,64,0.7);
  border: 1px solid rgba(255,255,255,0.06);
  color: var(--text-primary);
  border-bottom-left-radius: 3px;
}
.ai-bubble .bubble-label {
  color: var(--accent-cyan);
}
.label-dot {
  width: 6px; height: 6px; border-radius: 50%;
  background: var(--accent-cyan);
  box-shadow: 0 0 6px var(--accent-cyan);
}
.intent-badge {
  font-size: 9px; letter-spacing: 0.5px; font-weight: 500;
  padding: 1px 6px; border-radius: 3px;
  background: rgba(92,220,255,0.1);
  border: 1px solid rgba(92,220,255,0.2);
  color: var(--accent-cyan);
}

/* ====== 思考过程 ====== */
.reasoning-box {
  margin-bottom: 10px;
  border-left: 2px solid rgba(255,180,100,0.5);
  padding: 8px 10px;
  background: rgba(255,180,50,0.04);
  border-radius: 0 6px 6px 0;
}
.reasoning-toggle {
  cursor: pointer; font-size: 11px; font-weight: 500;
  color: rgba(255,200,120,0.8); margin-bottom: 6px;
  letter-spacing: 0.5px;
}
.reasoning-hint { font-size: 10px; color: var(--text-muted); font-weight: 400; }
.reasoning-content {
  white-space: pre-wrap; font-size: 12px;
  color: var(--text-muted); line-height: 1.6;
  font-style: italic;
}

/* ====== AI 内容样式 ====== */
.ai-content :deep(strong) { color: #fff; font-weight: 600; }
.ai-content :deep(.md-code) {
  background: rgba(0,0,0,0.3); padding: 1px 6px;
  border-radius: 3px; font-size: 12px;
  color: var(--accent-cyan);
}
.ai-content :deep(.md-h3) {
  font-size: 14px; font-weight: 600; margin: 10px 0 6px;
  color: #fff; border-left: 2px solid var(--accent-cyan);
  padding-left: 8px;
}
.ai-content :deep(.md-h4) {
  font-size: 13px; font-weight: 600; margin: 8px 0 4px;
  color: var(--text-primary);
}
.ai-content :deep(.md-li) {
  margin-left: 6px; color: var(--text-secondary);
}
.ai-content :deep(.md-hr) {
  border: none; border-top: 1px dashed rgba(255,255,255,0.08); margin: 10px 0;
}
.ai-content :deep(.md-row) {
  display: flex; gap: 4px; font-size: 11px; margin: 2px 0;
}
.ai-content :deep(.md-cell) {
  padding: 2px 8px; background: rgba(0,0,0,0.25);
  border-radius: 3px; color: var(--text-secondary);
}

/* ====== 耗时 ====== */
.elapsed-text {
  font-size: 10px; color: var(--text-muted);
  margin-top: 8px; letter-spacing: 0.5px;
}

/* ====== 输入动画 ====== */
.typing-indicator { display: inline-flex; gap: 5px; padding: 4px 2px; }
.typing-indicator .dot {
  width: 6px; height: 6px;
  background: var(--accent-cyan); border-radius: 50%;
  animation: typingDot 1.6s infinite ease-in-out both;
  opacity: 0.5;
}
.typing-indicator .dot:nth-child(1) { animation-delay: 0s; }
.typing-indicator .dot:nth-child(2) { animation-delay: 0.25s; }
.typing-indicator .dot:nth-child(3) { animation-delay: 0.5s; }
@keyframes typingDot {
  0%, 80%, 100% { transform: scale(0.5); opacity: 0.3; }
  40% { transform: scale(1.1); opacity: 1; }
}

/* ====== 操作栏 ====== */
.action-bar {
  display: flex; align-items: center; gap: 8px;
  padding: 10px 16px;
  background: rgba(255,180,50,0.06);
  border-top: 1px solid rgba(255,180,50,0.15);
  border-bottom: 1px solid rgba(255,180,50,0.15);
  flex-shrink: 0;
}
.action-hint {
  font-size: 11px; color: rgba(255,200,130,0.8);
  letter-spacing: 0.5px; margin-right: 4px;
}

/* ====== 输入区域 ====== */
.ai-chat-input {
  display: flex; align-items: flex-end; gap: 8px;
  padding: 12px 16px;
  background: rgba(10,22,38,0.8);
  border-top: 1px solid var(--panel-border);
  flex-shrink: 0;
}
.chat-textarea :deep(.el-textarea__inner) {
  border-radius: 20px;
  background: rgba(0,0,0,0.3) !important;
  border-color: rgba(255,255,255,0.08) !important;
  color: var(--text-primary) !important;
  font-size: 13px;
  padding-right: 14px;
  resize: none !important;
}
.chat-textarea :deep(.el-textarea__inner):focus {
  border-color: rgba(92,220,255,0.4) !important;
  box-shadow: 0 0 0 2px rgba(92,220,255,0.06) !important;
}
.chat-textarea :deep(.el-textarea__inner)::placeholder {
  color: var(--text-muted);
}
.send-btn {
  background: linear-gradient(135deg, var(--accent-cyan), var(--accent-blue)) !important;
  border: none !important;
  flex-shrink: 0;
}
.send-btn:hover {
  box-shadow: 0 0 16px rgba(92,220,255,0.4);
}

/* ====== 底部 ====== */
.ai-chat-footer {
  display: flex; align-items: center; gap: 10px;
  padding: 6px 16px;
  background: rgba(6,14,24,0.6);
  border-top: 1px solid rgba(255,255,255,0.03);
  flex-shrink: 0;
}
.footer-warn {
  font-size: 10.5px; color: var(--text-muted);
  letter-spacing: 0.3px; flex: 1;
}
.reasoning-switch {
  --el-switch-on-color: rgba(92,220,255,0.5);
  --el-switch-off-color: rgba(255,255,255,0.1);
  flex-shrink: 0;
}

/* ====== 过渡动画 ====== */
.slide-up-enter-active, .slide-up-leave-active { transition: all 0.3s cubic-bezier(0.4,0,0.2,1); }
.slide-up-enter-from, .slide-up-leave-to {
  opacity: 0;
  transform: translateY(24px) scale(0.95);
}

/* ====== 响应式 ====== */
@media (max-width: 480px) {
  .ai-chat-panel {
    width: calc(100vw - 24px);
    height: 520px;
    right: -4px;
    border-radius: 12px;
  }
  .header-subtitle { display: none; }
  .header-status-row { display: none; }
}
</style>
