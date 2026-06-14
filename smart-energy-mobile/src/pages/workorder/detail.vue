<template>
  <view class="detail-page">
    <!-- Custom Nav -->
    <view class="nav-bar">
      <view class="nav-left" @click="goBack">
        <text class="nav-back">‹</text>
        <text class="nav-title">工单详情</text>
      </view>
      <text class="nav-order-no">{{ order.orderNo || '' }}</text>
    </view>

    <view v-if="loading" class="loading-wrap">
      <text class="loading-text">加载中...</text>
    </view>

    <template v-else-if="order.id">
      <scroll-view scroll-y class="detail-scroll">
        <!-- Read-only indicator for resolved orders -->
        <view v-if="order.status === 'RESOLVED'" class="resolved-banner">
          <text class="resolved-icon">✅</text>
          <text class="resolved-text">此工单已完成，仅供查看</text>
        </view>

        <!-- Status Banner -->
        <view :class="['status-banner', statusBannerClass]">
          <text class="status-icon">{{ statusIcon }}</text>
          <view class="status-info">
            <text class="status-title">{{ order.title || '工单' }}</text>
            <view class="status-row">
              <view :class="['status-tag', statusClass]">
                <text class="status-tag-text">{{ statusLabel }}</text>
              </view>
              <view :class="['priority-tag', priorityClass]">
                <view :class="['priority-dot-sm', priorityClass]"></view>
                <text class="priority-tag-text">{{ priorityLabel }}</text>
              </view>
            </view>
          </view>
        </view>

        <!-- Device Info -->
        <view class="info-card">
          <text class="card-title">设备信息</text>
          <view class="info-row"><text class="info-label">设备名称</text><text class="info-value">{{ order.deviceName || '-' }}</text></view>
          <view class="info-row"><text class="info-label">设备编码</text><text class="info-value mono">{{ order.deviceCode || '-' }}</text></view>
          <view class="info-row"><text class="info-label">设备类型</text><text class="info-value">{{ order.deviceType || '-' }}</text></view>
          <view class="info-row"><text class="info-label">所在区域</text><text class="info-value">{{ order.deviceLocation || '-' }}</text></view>
        </view>

        <!-- Fault Info -->
        <view class="info-card">
          <text class="card-title">故障信息</text>
          <view class="info-row"><text class="info-label">故障类型</text><text class="info-value">{{ faultTypeLabel }}</text></view>
          <view class="info-row block"><text class="info-label">故障描述</text><text class="info-value desc">{{ order.description || order.faultType || '暂无描述' }}</text></view>
          <view class="info-row"><text class="info-label">工单来源</text><text class="info-value">{{ order.source === 'AUTO' ? '自动检测' : '手工报修' }}</text></view>
          <view v-if="order.assigneeName" class="info-row"><text class="info-label">当前指派人</text><text class="info-value highlight">{{ order.assigneeName }}</text></view>
        </view>

        <!-- Sensor Snapshot -->
        <view v-if="hasSensorData" class="info-card">
          <text class="card-title">触发时传感器快照</text>
          <view class="sensor-grid">
            <view class="sensor-item">
              <text class="sensor-label">温度</text>
              <text class="sensor-value temp">{{ order.latestTemperature }}°C</text>
            </view>
            <view class="sensor-item">
              <text class="sensor-label">振动</text>
              <text class="sensor-value vib">{{ order.latestVibration }} mm/s</text>
            </view>
            <view class="sensor-item">
              <text class="sensor-label">压力</text>
              <text class="sensor-value pres">{{ order.latestPressure }} kPa</text>
            </view>
          </view>
        </view>

        <!-- AI Diagnosis -->
        <view v-if="aiAdvice" class="info-card ai-card">
          <view class="card-header-row">
            <text class="card-title">AI 诊断建议</text>
            <view class="ai-badges">
              <view :class="['ai-strategy-badge', aiAdvice.strategy === 'LLM' ? 'strategy-llm' : 'strategy-det']">
                <text class="strategy-text">{{ aiAdvice.strategy === 'LLM' ? 'LLM 增强' : '规则匹配' }}</text>
              </view>
              <view :class="['ai-confidence-badge', confidenceLevel]">
                <text class="confidence-text">{{ aiConfidence }}%</text>
              </view>
            </view>
          </view>

          <!-- Confidence Bar -->
          <view class="confidence-bar-wrap">
            <view :class="['confidence-bar', confidenceLevel]" :style="{ width: aiConfidence + '%' }"></view>
          </view>

          <text v-if="aiAdvice.summary" class="ai-summary">{{ aiAdvice.summary }}</text>

          <!-- AI Steps -->
          <text v-if="aiAdvice.steps && aiAdvice.steps.length" class="subsection-title">建议步骤</text>
          <view v-for="(step, idx) in aiAdvice.steps" :key="idx" class="ai-step">
            <view class="ai-step-header">
              <view class="step-order">{{ step.order || idx + 1 }}</view>
              <text class="ai-step-action">{{ step.action }}</text>
              <view :class="['step-conf-badge', step.confidence >= 0.8 ? 'conf-high' : step.confidence >= 0.5 ? 'conf-mid' : 'conf-low']">
                <text class="step-conf-text">{{ Math.round((step.confidence || 0) * 100) }}%</text>
              </view>
            </view>
            <view class="ai-step-meta">
              <text v-if="step.aiDerived" class="ai-derived-tag">AI 推断</text>
              <text v-if="step.sourceSopCode" class="sop-source-tag">来源: {{ step.sourceSopCode }}</text>
            </view>
            <text v-if="step.rationale" class="ai-step-rationale">{{ step.rationale }}</text>
          </view>

          <view class="ai-refresh" @click="fetchAiAdvice">
            <text class="ai-refresh-icon">🔄</text>
            <text class="ai-refresh-text">刷新 AI 建议</text>
          </view>
        </view>

        <view v-else-if="aiLoading" class="info-card">
          <text class="card-title">AI 诊断建议</text>
          <view class="loading-row"><text class="loading-sub">正在生成诊断建议...</text></view>
        </view>

        <!-- SOP Recommendation -->
        <view v-if="matchedSop" class="info-card sop-card">
          <text class="card-title">SOP 推荐</text>
          <text class="sop-name">{{ matchedSop.sopCode }} — {{ matchedSop.title }}</text>
          <view class="sop-meta">
            <text class="sop-meta-item">⏱ 预计 {{ matchedSop.estimatedMinutes || '--' }} 分钟</text>
            <text class="sop-meta-item">📌 v{{ matchedSop.version }}</text>
          </view>

          <view v-if="matchedSop.steps && matchedSop.steps.length" class="sop-steps-preview">
            <text class="subsection-title">操作步骤 ({{ matchedSop.steps.length }} 步)</text>
            <view v-for="(s, i) in matchedSop.steps.slice(0, 4)" :key="i" class="mini-step">
              <text class="mini-step-num">{{ i + 1 }}.</text>
              <text class="mini-step-text">{{ s }}</text>
            </view>
            <text v-if="matchedSop.steps.length > 4" class="more-text">... 还有 {{ matchedSop.steps.length - 4 }} 步</text>
          </view>

          <view v-if="requiredParts.length" class="sop-parts">
            <text class="subsection-title">所需备件</text>
            <view v-for="(p, i) in requiredParts" :key="i" class="sop-part-tag">
              <text class="sop-part-text">{{ p }}</text>
            </view>
          </view>

          <button
            v-if="!order.sopId"
            class="accept-sop-btn"
            @click="handleAcceptSOP"
            :disabled="sopAccepting"
          >
            {{ sopAccepting ? '处理中...' : '接受推荐' }}
          </button>
          <view v-else class="sop-accepted-badge">
            <text class="accepted-text">✅ 已关联 SOP (ID: {{ order.sopId }})</text>
          </view>
        </view>

        <view v-else-if="sopLoading" class="info-card">
          <text class="card-title">SOP 推荐</text>
          <view class="loading-row"><text class="loading-sub">正在匹配 SOP...</text></view>
        </view>

        <!-- Spare Parts Application -->
        <view v-if="requiredParts.length && matchedSop" class="info-card parts-card">
          <text class="card-title">配件申请</text>
          <text class="parts-desc">选择需要申请的备件数量</text>

          <view v-for="(part, idx) in partRequests" :key="idx" class="part-request-row">
            <view class="part-req-info">
              <text class="part-req-name">{{ part.name }}</text>
              <text class="part-req-code">{{ part.code }}</text>
            </view>
            <view class="quantity-ctrl">
              <view class="qty-btn" @click="decrementPart(idx)"><text class="qty-btn-text">−</text></view>
              <text class="qty-value">{{ part.quantity }}</text>
              <view class="qty-btn" @click="incrementPart(idx)"><text class="qty-btn-text">+</text></view>
            </view>
          </view>

          <button
            class="submit-parts-btn"
            @click="handleSubmitParts"
            :disabled="partsSubmitting || totalPartsQty === 0"
          >
            {{ partsSubmitting ? '提交中...' : `提交配件申请 (${totalPartsQty} 件)` }}
          </button>

          <view v-if="partsSubmitted" class="parts-success">
            <text class="success-text">✅ 配件申请已提交</text>
          </view>
        </view>

        <!-- Historical Trends -->
        <view v-if="hasSensorData" class="info-card trends-card">
          <text class="card-title">15 分钟历史趋势</text>
          <text class="trends-note">故障触发前传感器读数变化</text>

          <view class="trend-item" v-if="order.latestTemperature != null">
            <text class="trend-label">温度</text>
            <text class="trend-value temp">{{ order.latestTemperature }}°C</text>
            <text class="trend-dir up">↑ 异常升高</text>
          </view>
          <view class="trend-item" v-if="order.latestVibration != null">
            <text class="trend-label">振动</text>
            <text class="trend-value vib">{{ order.latestVibration }} mm/s</text>
            <text class="trend-dir up">↑ 超过阈值</text>
          </view>
          <view class="trend-item" v-if="order.latestPressure != null">
            <text class="trend-label">压力</text>
            <text class="trend-value pres">{{ order.latestPressure }} kPa</text>
            <text class="trend-dir down">↓ 低于正常</text>
          </view>

          <text class="trends-footer">完整趋势图请查看 Web 仪表盘</text>
        </view>

        <!-- Similar Cases -->
        <view v-if="similarCases.length" class="info-card cases-card">
          <text class="card-title">相似维修案例 ({{ similarCases.length }})</text>
          <view v-for="c in similarCases" :key="c.id" class="case-item">
            <text class="case-title">{{ c.title }}</text>
            <view class="case-meta">
              <text class="case-tech">👤 {{ c.technician || '未知' }}</text>
              <text class="case-result">{{ c.repairResult || '无记录' }}</text>
            </view>
          </view>
        </view>

        <!-- Timeline -->
        <view class="info-card">
          <text class="card-title">时间线</text>
          <view class="timeline-item">
            <view class="timeline-dot"></view>
            <view class="timeline-content">
              <text class="timeline-label">创建时间</text>
              <text class="timeline-time">{{ formatTime(order.createdAt) }}</text>
            </view>
          </view>
          <view v-if="order.sourceTime" class="timeline-item">
            <view class="timeline-dot fault"></view>
            <view class="timeline-content">
              <text class="timeline-label">故障源时间</text>
              <text class="timeline-time">{{ formatTime(order.sourceTime) }}</text>
            </view>
          </view>
          <view v-if="order.acceptedAt" class="timeline-item">
            <view class="timeline-dot active"></view>
            <view class="timeline-content">
              <text class="timeline-label">接单时间</text>
              <text class="timeline-time">{{ formatTime(order.acceptedAt) }}</text>
            </view>
          </view>
          <view v-if="order.resolvedAt" class="timeline-item">
            <view class="timeline-dot done"></view>
            <view class="timeline-content">
              <text class="timeline-label">完成时间</text>
              <text class="timeline-time">{{ formatTime(order.resolvedAt) }}</text>
            </view>
          </view>
        </view>

        <!-- Active Assignees -->
        <view v-if="order.activeAssignments && order.activeAssignments.length" class="info-card">
          <text class="card-title">维修人员 ({{ order.activeAssignments.length }})</text>
          <view v-for="a in order.activeAssignments" :key="a.id" class="assignee-row">
            <view class="assignee-avatar" :style="{ background: a.avatarColor || '#f0a500' }">
              <text class="assignee-avatar-text">{{ (a.name || '工').charAt(0) }}</text>
            </view>
            <view class="assignee-info">
              <text class="assignee-name">{{ a.name }}</text>
              <text class="assignee-role">{{ a.role === 'PRIMARY' ? '主修' : '协助' }}</text>
            </view>
          </view>
        </view>

        <view class="safe-bottom"></view>
      </scroll-view>

      <!-- Bottom Action Bar -->
      <view class="action-bar" v-if="order.status === 'PENDING' || order.status === 'IN_PROGRESS'">
        <button v-if="order.status === 'PENDING'" class="action-btn start-btn" @click="handleStartRepair" :disabled="actionLoading">
          {{ actionLoading ? '处理中...' : '接单 / 开始维修' }}
        </button>
        <button v-if="order.status === 'IN_PROGRESS'" class="action-btn finish-btn" @click="showFinishModal = true">
          完成维修
        </button>
      </view>
    </template>

    <!-- Error -->
    <view v-else class="empty-wrap">
      <text class="empty-icon">⚠️</text>
      <text class="empty-text">无法加载工单详情</text>
      <view class="retry-btn" @click="fetchAll"><text class="retry-text">重新加载</text></view>
    </view>

    <!-- Finish Modal -->
    <view v-if="showFinishModal" class="modal-mask" @click="showFinishModal = false">
      <view class="modal-card" @click.stop>
        <text class="modal-title">完成维修</text>
        <view class="modal-body">
          <text class="modal-label">维修备注</text>
          <textarea class="modal-textarea" v-model="finishNote" placeholder="请描述维修过程和结果..." placeholder-style="color: #6b7280" :maxlength="500" />
          <text class="modal-label">现场照片（可选）</text>
          <view class="photo-section">
            <view v-for="(img, idx) in photos" :key="idx" class="photo-thumb" @click="previewPhoto(idx)">
              <image :src="img" mode="aspectFill" class="photo-img" />
              <view class="photo-remove" @click.stop="removePhoto(idx)">✕</view>
            </view>
            <view v-if="photos.length < 3" class="photo-add" @click="choosePhoto">
              <text class="photo-add-icon">+</text>
              <text class="photo-add-text">添加照片</text>
            </view>
          </view>
        </view>
        <view class="modal-actions">
          <button class="modal-btn cancel-btn" @click="showFinishModal = false">取消</button>
          <button class="modal-btn confirm-btn" @click="handleFinishRepair" :disabled="finishLoading">
            {{ finishLoading ? '提交中...' : '确认完成' }}
          </button>
        </view>
      </view>
    </view>
  </view>
</template>

<script setup>
import { ref, computed } from 'vue'
import { onLoad } from '@dcloudio/uni-app'
import { get, post, patch } from '@/utils/request'

const orderId = ref(null)
const order = ref({})
const loading = ref(true)

// AI Diagnosis
const aiAdvice = ref(null)
const aiLoading = ref(false)

// SOP
const matchedSop = ref(null)
const sopLoading = ref(false)
const sopAccepting = ref(false)

// Spare Parts
const partRequests = ref([])
const partsSubmitting = ref(false)
const partsSubmitted = ref(false)
const sparePartsMap = ref({}) // code → numeric ID mapping from spare-parts API

// Similar Cases
const similarCases = ref([])

// Actions
const actionLoading = ref(false)
const showFinishModal = ref(false)
const finishNote = ref('')
const photos = ref([])
const finishLoading = ref(false)

// --- Route param ---
onLoad((options) => {
  console.log('[detail] onLoad options:', JSON.stringify(options))
  // Try all possible ID field names (id, orderId, orderNo)
  const rawId = options?.id || options?.orderId || options?.orderNo
  if (rawId) {
    orderId.value = rawId
    console.log('[detail] orderId set to:', rawId, 'type:', typeof rawId)
    fetchAll()
  } else {
    console.error('[detail] No ID found in options:', options)
    loading.value = false
    uni.showToast({ title: '无效的工单ID', icon: 'none' })
  }
})

// --- Fetch all data ---
function fetchAll() {
  if (!orderId.value) {
    loading.value = false
    return
  }
  loading.value = true
  const detailUrl = '/workorder/orders/' + orderId.value
  console.log('[detail] fetching:', detailUrl)
  // Matches Web frontend: getWorkOrderDetail(id) → GET /api/workorder/orders/{id}
  get(detailUrl)
    .then((data) => {
      if (!data || !data.id) {
        throw new Error('订单数据为空')
      }
      order.value = data
      fetchAiAdvice()
      fetchMatchedSOP()
      fetchSimilarCases()
      fetchSparePartsMap()
    })
    .catch((err) => {
      uni.showToast({ title: '加载详情失败', icon: 'none' })
      console.error('fetchAll failed:', err)
    })
    .finally(() => {
      loading.value = false
    })
}

// --- AI Advice ---
const fetchAiAdvice = () => {
  if (!order.value.deviceType && !order.value.faultType) return
  aiLoading.value = true
  post('/ai/repair-advice', {
    deviceType: order.value.deviceType,
    faultType: order.value.faultType,
    workOrderId: order.value.id,
    useLlm: true,
  })
    .then((data) => {
      aiAdvice.value = data
      // Build part requests from SOP if available
      if (data?.matchedSop) {
        buildPartRequests(data.matchedSop)
        if (!matchedSop.value) matchedSop.value = data.matchedSop
      }
    })
    .catch(() => { /* AI unavailable, skip */ })
    .finally(() => { aiLoading.value = false })
}

const aiConfidence = computed(() => {
  if (!aiAdvice.value?.overallConfidence) return 0
  return Math.round(aiAdvice.value.overallConfidence * 100)
})

const confidenceLevel = computed(() => {
  const c = aiConfidence.value
  if (c >= 80) return 'conf-high'
  if (c >= 50) return 'conf-mid'
  return 'conf-low'
})

// --- SOP ---
const fetchMatchedSOP = () => {
  const dt = order.value.deviceType
  const ft = order.value.faultType
  if (!ft) return
  sopLoading.value = true
  get('/sops/match', { deviceType: dt, faultType: ft })
    .then((data) => {
      matchedSop.value = data
      buildPartRequests(data)
    })
    .catch(() => { /* no SOP match */ })
    .finally(() => { sopLoading.value = false })
}

const requiredParts = computed(() => {
  if (!matchedSop.value?.requiredParts) return []
  const p = matchedSop.value.requiredParts
  if (Array.isArray(p)) return p
  return []
})

const buildPartRequests = (sop) => {
  if (!sop?.requiredParts || !Array.isArray(sop.requiredParts)) return
  if (partRequests.value.length > 0) return // only build once
  const codeToLabel = {}
  if (aiAdvice.value?.matchedSop?.requiredParts) {
    aiAdvice.value.matchedSop.requiredParts.forEach(p => { codeToLabel[p] = p })
  }
  // Resolve part codes to numeric spare_part IDs
  partRequests.value = sop.requiredParts.map((code) => {
    const numericId = sparePartsMap.value[code] || null
    return {
      name: codeToLabel[code] || code,
      code,
      partId: numericId, // resolved numeric ID for API submission
      quantity: 1,
    }
  })
  // Warn if any codes couldn't be resolved
  const unresolved = partRequests.value.filter(p => p.partId == null).map(p => p.code)
  if (unresolved.length) {
    console.warn('[detail] Unresolved spare part codes:', unresolved)
  }
}

// Fetch spare parts list to build code→id mapping
const fetchSparePartsMap = () => {
  get('/spare-parts', {})
    .then((data) => {
      const list = data || []
      list.forEach((p) => {
        if (p.partCode) sparePartsMap.value[p.partCode] = p.id
      })
    })
    .catch(() => { /* non-critical, parts submission will validate */ })
}

const totalPartsQty = computed(() => partRequests.value.reduce((s, p) => s + p.quantity, 0))

const incrementPart = (idx) => {
  partRequests.value[idx].quantity++
}
const decrementPart = (idx) => {
  const q = partRequests.value[idx].quantity
  if (q > 0) partRequests.value[idx].quantity--
}

// --- Similar Cases ---
const fetchSimilarCases = () => {
  const dt = order.value.deviceType
  const ft = order.value.faultType
  if (!ft) return
  get('/cases/similar', { deviceType: dt, faultType: ft, limit: 3 })
    .then((data) => {
      similarCases.value = data || []
    })
    .catch(() => { /* no cases */ })
}

// --- Status helpers ---
const statusBannerClass = computed(() => {
  const s = order.value.status
  if (s === 'PENDING') return 'banner-pending'
  if (s === 'IN_PROGRESS') return 'banner-progress'
  return 'banner-resolved'
})
const statusIcon = computed(() => {
  const s = order.value.status
  if (s === 'PENDING') return '⏳'
  if (s === 'IN_PROGRESS') return '🔧'
  return '✅'
})
const statusClass = computed(() => {
  const s = order.value.status
  if (s === 'PENDING') return 'status-pending'
  if (s === 'IN_PROGRESS') return 'status-progress'
  return 'status-resolved'
})
const statusLabel = computed(() => {
  const s = order.value.status
  if (s === 'PENDING') return '待处理'
  if (s === 'IN_PROGRESS') return '维修中'
  return '已解决'
})
const priorityClass = computed(() => {
  const p = order.value.priority
  if (p === 'CRITICAL') return 'priority-critical'
  if (p === 'HIGH') return 'priority-high'
  if (p === 'MEDIUM') return 'priority-medium'
  return 'priority-low'
})
const priorityLabel = computed(() => {
  const p = order.value.priority
  if (p === 'CRITICAL') return '紧急'
  if (p === 'HIGH') return '高'
  if (p === 'MEDIUM') return '中'
  if (p === 'LOW') return '低'
  return p || '中'
})
const faultTypeLabel = computed(() => {
  const m = {
    MECHANICAL_JAM: '机械卡涩', COOLING_INTERRUPT: '冷却中断',
    ELECTRICAL_OVERLOAD: '电气过载', SENSOR_DRIFT: '传感器漂移',
    BEARING_WEAR: '轴承磨损', INTERMITTENT_JAM: '间歇性卡涩',
  }
  return m[order.value.faultType] || order.value.faultType || '-'
})
const hasSensorData = computed(() => {
  return order.value.latestTemperature != null ||
    order.value.latestVibration != null ||
    order.value.latestPressure != null
})

// --- Actions ---
const goBack = () => { uni.navigateBack() }

const getUserName = () => {
  try {
    const stored = uni.getStorageSync('userInfo')
    const info = typeof stored === 'string' ? JSON.parse(stored) : stored
    return info?.nickname || info?.username || ''
  } catch (_) { return '' }
}

const handleStartRepair = () => {
  actionLoading.value = true
  // Matches Web: patchWorkOrderStatus(id, { status: 'IN_PROGRESS' })
  // Backend requires explicit assignee — not auto-detected from SecurityContext
  const workerName = getUserName()
  patch('/work-orders/' + orderId.value + '/status', {
    status: 'IN_PROGRESS',
    assignee: workerName,
  })
    .then(() => {
      uni.showToast({ title: '已接单，开始维修', icon: 'success' })
      fetchAll()
    })
    .catch(() => {})
    .finally(() => { actionLoading.value = false })
}

const handleFinishRepair = () => {
  if (!finishNote.value.trim()) {
    uni.showToast({ title: '请填写维修备注', icon: 'none' })
    return
  }
  finishLoading.value = true
  // Matches Web: patchWorkOrderStatus(id, { status: 'RESOLVED', note })
  patch('/work-orders/' + orderId.value + '/status', {
    status: 'RESOLVED',
    note: finishNote.value,
  })
    .then(() => {
      uni.showToast({ title: '维修完成！', icon: 'success' })
      showFinishModal.value = false
      fetchAll()
    })
    .catch(() => {})
    .finally(() => { finishLoading.value = false })
}

const handleAcceptSOP = () => {
  if (!matchedSop.value?.id) return
  sopAccepting.value = true
  // Matches Web: patchWorkOrderSop(id, sopId)
  patch('/work-orders/' + orderId.value + '/sop', { sopId: matchedSop.value.id })
    .then(() => {
      uni.showToast({ title: '已关联 SOP', icon: 'success' })
      order.value.sopId = matchedSop.value.id
    })
    .catch(() => {})
    .finally(() => { sopAccepting.value = false })
}

const handleSubmitParts = () => {
  // Check sparePartsMap is populated
  if (Object.keys(sparePartsMap.value).length === 0) {
    uni.showToast({ title: '备件数据未加载，请稍后重试', icon: 'none' })
    return
  }
  // Validate: all items must have a numeric partId
  const unresolved = partRequests.value.filter(p => p.quantity > 0 && p.partId == null)
  if (unresolved.length) {
    uni.showToast({ title: `无法解析备件编码: ${unresolved.map(p => p.code).join(', ')}`, icon: 'none' })
    return
  }
  const items = partRequests.value
    .filter(p => p.quantity > 0)
    .map(p => ({
      partId: p.partId, // numeric spare_part.id required by backend (@NotNull Long)
      quantity: p.quantity, // @NotNull @Positive Integer
      workOrderId: parseInt(orderId.value, 10) || undefined,
      note: `工单 ${order.value.orderNo} 领用`,
    }))
  if (!items.length) {
    uni.showToast({ title: '请选择至少一种配件', icon: 'none' })
    return
  }
  const payload = { items }
  console.log('Submit Payload:', JSON.stringify(payload))
  partsSubmitting.value = true
  post('/spare-parts/usage/batch', payload)
    .then(() => {
      uni.showToast({ title: '配件申请已提交', icon: 'success' })
      partsSubmitted.value = true
    })
    .catch(() => {})
    .finally(() => { partsSubmitting.value = false })
}

const choosePhoto = () => {
  uni.chooseImage({
    count: 3 - photos.value.length,
    sizeType: ['compressed'],
    sourceType: ['camera', 'album'],
    success: (res) => { photos.value = [...photos.value, ...res.tempFilePaths] },
  })
}
const removePhoto = (idx) => { photos.value.splice(idx, 1) }
const previewPhoto = (idx) => { uni.previewImage({ current: idx, urls: photos.value }) }

const formatTime = (t) => {
  if (!t) return '-'
  const d = new Date(t)
  if (isNaN(d.getTime())) return t
  const y = d.getFullYear()
  const mo = String(d.getMonth() + 1).padStart(2, '0')
  const day = String(d.getDate()).padStart(2, '0')
  const h = String(d.getHours()).padStart(2, '0')
  const mi = String(d.getMinutes()).padStart(2, '0')
  return `${y}-${mo}-${day} ${h}:${mi}`
}
</script>

<style scoped>
.detail-page { min-height: 100vh; background: #0d1117; display: flex; flex-direction: column; }

/* Nav */
.nav-bar { display: flex; align-items: center; justify-content: space-between; padding: 0 24rpx; height: 88rpx; background: #161b22; border-bottom: 1rpx solid #21262d; }
.nav-left { display: flex; align-items: center; }
.nav-back { font-size: 48rpx; color: #8b949e; margin-right: 8rpx; line-height: 1; }
.nav-title { font-size: 30rpx; font-weight: 600; color: #e6edf3; }
.nav-order-no { font-size: 22rpx; color: #484f58; font-family: monospace; }

/* Loading / Empty */
.loading-wrap { flex: 1; display: flex; flex-direction: column; align-items: center; justify-content: center; }
.loading-text { font-size: 28rpx; color: #8b949e; }
.loading-row { display: flex; justify-content: center; padding: 24rpx 0; }
.loading-sub { font-size: 24rpx; color: #6b7280; }
.empty-wrap { flex: 1; display: flex; flex-direction: column; align-items: center; justify-content: center; }
.empty-icon { font-size: 64rpx; margin-bottom: 16rpx; }
.empty-text { font-size: 28rpx; color: #8b949e; margin-bottom: 24rpx; }
.retry-btn { background: rgba(240,165,0,0.15); border: 1rpx solid rgba(240,165,0,0.3); padding: 14rpx 36rpx; border-radius: 12rpx; }
.retry-text { font-size: 26rpx; color: #f0a500; }

/* Scroll */
.detail-scroll { flex: 1; padding-bottom: 140rpx; }

/* Resolved Banner */
.resolved-banner {
  display: flex; align-items: center; justify-content: center;
  margin: 24rpx 32rpx 0; padding: 16rpx;
  background: rgba(46, 196, 182, 0.08);
  border: 1rpx solid rgba(46, 196, 182, 0.15);
  border-radius: 12rpx;
}
.resolved-icon { font-size: 28rpx; margin-right: 10rpx; }
.resolved-text { font-size: 24rpx; color: #2ec4b6; font-weight: 600; }

/* Status Banner */
.status-banner { display: flex; align-items: center; margin: 24rpx 32rpx; padding: 28rpx; border-radius: 20rpx; border: 1rpx solid #21262d; }
.banner-pending { background: linear-gradient(135deg,#1a1a1f,#161b22); border-color: rgba(240,165,0,0.3); }
.banner-progress { background: linear-gradient(135deg,#1a1f2b,#161b22); border-color: rgba(46,196,182,0.3); }
.banner-resolved { background: linear-gradient(135deg,#1a1f1a,#161b22); border-color: rgba(46,196,182,0.2); }
.status-icon { font-size: 48rpx; margin-right: 20rpx; }
.status-info { flex: 1; }
.status-title { font-size: 30rpx; font-weight: 700; color: #e6edf3; display: block; margin-bottom: 12rpx; }
.status-row { display: flex; gap: 12rpx; }
.status-tag { padding: 4rpx 16rpx; border-radius: 8rpx; }
.status-pending { background: rgba(240,165,0,0.15); border: 1rpx solid rgba(240,165,0,0.3); }
.status-progress { background: rgba(46,196,182,0.15); border: 1rpx solid rgba(46,196,182,0.3); }
.status-resolved { background: rgba(46,196,182,0.08); border: 1rpx solid rgba(46,196,182,0.15); }
.status-tag-text { font-size: 22rpx; font-weight: 600; color: #e6edf3; }
.priority-tag { display: flex; align-items: center; padding: 4rpx 16rpx; border-radius: 8rpx; }
.priority-critical { background: rgba(230,57,70,0.15); border: 1rpx solid rgba(230,57,70,0.3); }
.priority-high { background: rgba(240,165,0,0.15); border: 1rpx solid rgba(240,165,0,0.3); }
.priority-medium { background: rgba(255,159,67,0.1); border: 1rpx solid rgba(255,159,67,0.25); }
.priority-low { background: rgba(139,148,158,0.1); border: 1rpx solid rgba(139,148,158,0.2); }
.priority-dot-sm { width: 10rpx; height: 10rpx; border-radius: 50%; margin-right: 8rpx; }
.priority-dot-sm.priority-critical { background: #e63946; }
.priority-dot-sm.priority-high { background: #f0a500; }
.priority-dot-sm.priority-medium { background: #ff9f43; }
.priority-dot-sm.priority-low { background: #8b949e; }
.priority-tag-text { font-size: 22rpx; font-weight: 600; color: #e6edf3; }

/* Info Cards */
.info-card { background: #161b22; border: 1rpx solid #21262d; border-radius: 20rpx; margin: 0 32rpx 20rpx; padding: 28rpx; }
.card-title { font-size: 26rpx; font-weight: 700; color: #f0a500; margin-bottom: 20rpx; display: block; }
.card-header-row { display: flex; align-items: center; justify-content: space-between; margin-bottom: 20rpx; }
.card-header-row .card-title { margin-bottom: 0; }
.info-row { display: flex; justify-content: space-between; align-items: flex-start; padding: 14rpx 0; border-bottom: 1rpx solid #21262d; }
.info-row:last-child { border-bottom: none; }
.info-row.block { flex-direction: column; }
.info-label { font-size: 24rpx; color: #8b949e; flex-shrink: 0; margin-right: 24rpx; }
.info-value { font-size: 26rpx; color: #c9d1d9; text-align: right; word-break: break-all; }
.info-value.mono { font-family: monospace; }
.info-value.desc { text-align: left; margin-top: 8rpx; line-height: 40rpx; color: #9ba3af; }
.info-value.highlight { color: #2ec4b6; }

/* Sensor */
.sensor-grid { display: flex; gap: 16rpx; }
.sensor-item { flex: 1; background: #0d1117; border-radius: 14rpx; padding: 20rpx 12rpx; text-align: center; }
.sensor-label { display: block; font-size: 22rpx; color: #6b7280; margin-bottom: 8rpx; }
.sensor-value { font-size: 26rpx; font-weight: 700; }
.sensor-value.temp { color: #e63946; }
.sensor-value.vib { color: #f0a500; }
.sensor-value.pres { color: #2ec4b6; }

/* AI Card */
.ai-card { border-color: rgba(70,130,220,0.2); }
.ai-badges { display: flex; gap: 8rpx; }
.ai-strategy-badge { padding: 4rpx 12rpx; border-radius: 6rpx; }
.strategy-llm { background: rgba(70,130,220,0.15); border: 1rpx solid rgba(70,130,220,0.3); }
.strategy-det { background: rgba(46,196,182,0.1); border: 1rpx solid rgba(46,196,182,0.2); }
.strategy-text { font-size: 20rpx; color: #c9d1d9; }
.ai-confidence-badge { padding: 4rpx 12rpx; border-radius: 6rpx; }
.ai-confidence-badge.conf-high { background: rgba(46,196,182,0.15); border: 1rpx solid rgba(46,196,182,0.3); }
.ai-confidence-badge.conf-mid { background: rgba(240,165,0,0.15); border: 1rpx solid rgba(240,165,0,0.3); }
.ai-confidence-badge.conf-low { background: rgba(230,57,70,0.12); border: 1rpx solid rgba(230,57,70,0.3); }
.confidence-text { font-size: 20rpx; font-weight: 700; color: #e6edf3; }

.confidence-bar-wrap { height: 8rpx; background: #21262d; border-radius: 4rpx; overflow: hidden; margin-bottom: 20rpx; }
.confidence-bar { height: 100%; border-radius: 4rpx; transition: width 0.6s; }
.confidence-bar.conf-high { background: linear-gradient(90deg,#2ec4b6,#1a9e92); }
.confidence-bar.conf-mid { background: linear-gradient(90deg,#f0a500,#d48500); }
.confidence-bar.conf-low { background: linear-gradient(90deg,#e63946,#c0392b); }

.ai-summary { font-size: 26rpx; color: #8b949e; line-height: 40rpx; display: block; margin-bottom: 20rpx; }

.subsection-title { font-size: 24rpx; font-weight: 700; color: #e6edf3; margin-bottom: 14rpx; display: block; margin-top: 8rpx; }

.ai-step { background: #0d1117; border-radius: 12rpx; padding: 18rpx; margin-bottom: 12rpx; }
.ai-step-header { display: flex; align-items: flex-start; gap: 12rpx; }
.step-order { width: 36rpx; height: 36rpx; border-radius: 50%; background: #21262d; font-size: 20rpx; font-weight: 700; color: #f0a500; display: flex; align-items: center; justify-content: center; flex-shrink: 0; }
.ai-step-action { flex: 1; font-size: 24rpx; color: #c9d1d9; line-height: 38rpx; }
.step-conf-badge { padding: 2rpx 10rpx; border-radius: 6rpx; flex-shrink: 0; }
.step-conf-badge.conf-high { background: rgba(46,196,182,0.15); }
.step-conf-badge.conf-mid { background: rgba(240,165,0,0.15); }
.step-conf-badge.conf-low { background: rgba(230,57,70,0.12); }
.step-conf-text { font-size: 18rpx; font-weight: 700; color: #c9d1d9; }
.ai-step-meta { display: flex; gap: 10rpx; margin: 10rpx 0 0 48rpx; }
.ai-derived-tag { font-size: 18rpx; padding: 2rpx 10rpx; border-radius: 4rpx; background: rgba(70,130,220,0.15); color: #7ab8ff; }
.sop-source-tag { font-size: 18rpx; padding: 2rpx 10rpx; border-radius: 4rpx; background: rgba(240,165,0,0.1); color: #f0a500; }
.ai-step-rationale { font-size: 22rpx; color: #6b7280; line-height: 36rpx; margin: 8rpx 0 0 48rpx; }

.ai-refresh { display: flex; align-items: center; justify-content: center; padding: 16rpx 0 4rpx; }
.ai-refresh-icon { font-size: 24rpx; margin-right: 8rpx; }
.ai-refresh-text { font-size: 22rpx; color: #8b949e; }

/* SOP Card */
.sop-card { border-color: rgba(240,165,0,0.2); }
.sop-name { font-size: 26rpx; font-weight: 600; color: #e6edf3; display: block; margin-bottom: 10rpx; }
.sop-meta { display: flex; gap: 20rpx; margin-bottom: 16rpx; }
.sop-meta-item { font-size: 22rpx; color: #8b949e; }

.sop-steps-preview { margin-bottom: 16rpx; }
.mini-step { display: flex; padding: 6rpx 0; }
.mini-step-num { font-size: 22rpx; color: #484f58; margin-right: 10rpx; min-width: 28rpx; }
.mini-step-text { font-size: 24rpx; color: #9ba3af; }
.more-text { font-size: 22rpx; color: #6b7280; margin-top: 4rpx; display: block; }

.sop-parts { margin-bottom: 20rpx; }
.sop-part-tag { display: inline-block; padding: 6rpx 16rpx; margin: 0 10rpx 8rpx 0; border-radius: 8rpx; background: rgba(240,165,0,0.1); border: 1rpx solid rgba(240,165,0,0.2); }
.sop-part-text { font-size: 20rpx; color: #f0a500; font-family: monospace; }

.accept-sop-btn { width: 100%; height: 72rpx; line-height: 72rpx; background: linear-gradient(135deg,#2ec4b6,#1a9e92); color: #fff; font-size: 26rpx; font-weight: 700; border-radius: 12rpx; border: none; }
.accept-sop-btn[disabled] { opacity: 0.6; }
.sop-accepted-badge { text-align: center; padding: 16rpx 0; }
.accepted-text { font-size: 24rpx; color: #2ec4b6; }

/* Parts Card */
.parts-card { border-color: rgba(46,196,182,0.2); }
.parts-desc { font-size: 22rpx; color: #6b7280; display: block; margin-bottom: 16rpx; }
.part-request-row { display: flex; align-items: center; justify-content: space-between; padding: 16rpx 0; border-bottom: 1rpx solid #21262d; }
.part-request-row:last-of-type { border-bottom: none; }
.part-req-info { flex: 1; }
.part-req-name { font-size: 26rpx; color: #c9d1d9; display: block; }
.part-req-code { font-size: 20rpx; color: #484f58; font-family: monospace; }
.quantity-ctrl { display: flex; align-items: center; gap: 16rpx; }
.qty-btn { width: 52rpx; height: 52rpx; border-radius: 50%; background: #21262d; display: flex; align-items: center; justify-content: center; border: 1rpx solid #30363d; }
.qty-btn-text { font-size: 28rpx; color: #8b949e; font-weight: 700; line-height: 1; }
.qty-value { font-size: 28rpx; font-weight: 700; color: #e6edf3; min-width: 36rpx; text-align: center; }

.submit-parts-btn { width: 100%; height: 72rpx; line-height: 72rpx; background: linear-gradient(135deg,#f0a500,#d48500); color: #0d1117; font-size: 26rpx; font-weight: 700; border-radius: 12rpx; border: none; margin-top: 20rpx; }
.submit-parts-btn[disabled] { opacity: 0.6; }
.parts-success { text-align: center; padding: 16rpx 0 0; }
.success-text { font-size: 24rpx; color: #2ec4b6; }

/* Trends */
.trends-card { border-color: rgba(255,159,67,0.2); }
.trends-note { font-size: 22rpx; color: #6b7280; display: block; margin-bottom: 16rpx; }
.trend-item { display: flex; align-items: center; padding: 12rpx 0; border-bottom: 1rpx solid #21262d; }
.trend-item:last-of-type { border-bottom: none; }
.trend-label { font-size: 24rpx; color: #8b949e; min-width: 80rpx; }
.trend-value { font-size: 24rpx; font-weight: 700; min-width: 120rpx; }
.trend-value.temp { color: #e63946; }
.trend-value.vib { color: #f0a500; }
.trend-value.pres { color: #2ec4b6; }
.trend-dir { font-size: 22rpx; }
.trend-dir.up { color: #e63946; }
.trend-dir.down { color: #f0a500; }
.trends-footer { font-size: 20rpx; color: #484f58; margin-top: 12rpx; display: block; text-align: center; }

/* Cases */
.case-item { padding: 14rpx 0; border-bottom: 1rpx solid #21262d; }
.case-item:last-child { border-bottom: none; }
.case-title { font-size: 26rpx; font-weight: 600; color: #c9d1d9; display: block; margin-bottom: 6rpx; }
.case-meta { display: flex; gap: 16rpx; }
.case-tech { font-size: 22rpx; color: #8b949e; }
.case-result { font-size: 22rpx; color: #6b7280; flex: 1; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }

/* Timeline */
.timeline-item { display: flex; align-items: flex-start; padding: 12rpx 0; }
.timeline-item:last-child { padding-bottom: 0; }
.timeline-dot { width: 16rpx; height: 16rpx; border-radius: 50%; background: #484f58; margin-right: 16rpx; margin-top: 6rpx; flex-shrink: 0; }
.timeline-dot.fault { background: #e63946; }
.timeline-dot.active { background: #2ec4b6; }
.timeline-dot.done { background: #f0a500; }
.timeline-content { flex: 1; }
.timeline-label { display: block; font-size: 24rpx; color: #8b949e; }
.timeline-time { font-size: 22rpx; color: #484f58; font-family: monospace; }

/* Assignees */
.assignee-row { display: flex; align-items: center; padding: 12rpx 0; border-bottom: 1rpx solid #21262d; }
.assignee-row:last-child { border-bottom: none; }
.assignee-avatar { width: 56rpx; height: 56rpx; border-radius: 50%; display: flex; align-items: center; justify-content: center; margin-right: 16rpx; }
.assignee-avatar-text { font-size: 24rpx; font-weight: 700; color: #fff; }
.assignee-info { flex: 1; }
.assignee-name { display: block; font-size: 26rpx; color: #c9d1d9; }
.assignee-role { font-size: 22rpx; color: #8b949e; }

/* Action Bar */
.action-bar { position: fixed; bottom: 0; left: 0; right: 0; padding: 20rpx 32rpx; padding-bottom: calc(20rpx + env(safe-area-inset-bottom)); background: #161b22; border-top: 1rpx solid #21262d; }
.action-btn { width: 100%; height: 88rpx; line-height: 88rpx; border-radius: 16rpx; border: none; font-size: 32rpx; font-weight: 700; color: #fff; }
.start-btn { background: linear-gradient(135deg,#2ec4b6,#1a9e92); }
.finish-btn { background: linear-gradient(135deg,#f0a500,#d48500); color: #0d1117; }
.action-btn[disabled] { opacity: 0.6; }

/* Modal */
.modal-mask { position: fixed; top: 0; left: 0; right: 0; bottom: 0; background: rgba(0,0,0,0.7); display: flex; align-items: center; justify-content: center; padding: 40rpx; z-index: 999; }
.modal-card { width: 100%; max-width: 600rpx; background: #1c2128; border: 1rpx solid #21262d; border-radius: 24rpx; padding: 36rpx; }
.modal-title { font-size: 32rpx; font-weight: 700; color: #e6edf3; margin-bottom: 28rpx; display: block; }
.modal-body { margin-bottom: 28rpx; }
.modal-label { font-size: 26rpx; color: #8b949e; margin-bottom: 12rpx; display: block; }
.modal-textarea { width: 100%; height: 180rpx; background: #0d1117; border: 1rpx solid #21262d; border-radius: 12rpx; padding: 20rpx; font-size: 26rpx; color: #c9d1d9; box-sizing: border-box; margin-bottom: 24rpx; }
.photo-section { display: flex; flex-wrap: wrap; gap: 16rpx; }
.photo-thumb { position: relative; width: 160rpx; height: 160rpx; border-radius: 12rpx; overflow: hidden; }
.photo-img { width: 100%; height: 100%; }
.photo-remove { position: absolute; top: 4rpx; right: 4rpx; width: 36rpx; height: 36rpx; border-radius: 50%; background: rgba(0,0,0,0.7); color: #e63946; font-size: 22rpx; display: flex; align-items: center; justify-content: center; }
.photo-add { width: 160rpx; height: 160rpx; border: 2rpx dashed #30363d; border-radius: 12rpx; display: flex; flex-direction: column; align-items: center; justify-content: center; }
.photo-add-icon { font-size: 48rpx; color: #6b7280; line-height: 1; }
.photo-add-text { font-size: 20rpx; color: #6b7280; margin-top: 4rpx; }
.modal-actions { display: flex; gap: 20rpx; }
.modal-btn { flex: 1; height: 80rpx; line-height: 80rpx; border-radius: 14rpx; border: none; font-size: 28rpx; font-weight: 700; }
.cancel-btn { background: #21262d; color: #8b949e; }
.confirm-btn { background: linear-gradient(135deg,#2ec4b6,#1a9e92); color: #fff; }
.confirm-btn[disabled] { opacity: 0.6; }

.safe-bottom { height: 40rpx; }
</style>
