<template>
  <view class="knowledge-page">
    <!-- Custom Nav -->
    <view class="nav-bar">
      <text class="nav-title">维修知识库</text>
      <text class="nav-subtitle">SOP 标准操作规程</text>
    </view>

    <!-- Search Bar -->
    <view class="search-section">
      <view class="search-input-wrap">
        <text class="search-icon">🔍</text>
        <input
          class="search-input"
          v-model="keyword"
          placeholder="搜索 SOP 标题、设备类型、故障类型..."
          placeholder-style="color: #6b7280"
          @confirm="doSearch"
        />
        <text v-if="keyword" class="search-clear" @click="keyword = ''; doSearch()">✕</text>
      </view>

      <view class="filter-chips">
        <view
          :class="['filter-chip', { active: filterDeviceType }]"
          @click="filterDeviceType = ''; doSearch()"
        >
          <text class="chip-text">{{ filterDeviceType || '设备类型' }}</text>
          <text v-if="filterDeviceType" class="chip-clear">✕</text>
        </view>
        <view
          :class="['filter-chip', { active: filterFaultType }]"
          @click="filterFaultType = ''; doSearch()"
        >
          <text class="chip-text">{{ filterFaultType || '故障类型' }}</text>
          <text v-if="filterFaultType" class="chip-clear">✕</text>
        </view>
        <view class="filter-chip" v-if="!filterDeviceType && !filterFaultType" @click="showFilterPicker = true">
          <text class="chip-text">筛选 ▾</text>
        </view>
      </view>
    </view>

    <!-- Loading -->
    <view v-if="loading" class="loading-wrap">
      <text class="loading-text">加载中...</text>
    </view>

    <!-- SOP List -->
    <scroll-view
      v-else
      scroll-y
      class="sop-scroll"
      @scrolltolower="loadMore"
    >
      <view v-if="sopList.length > 0" class="sop-list">
        <view
          v-for="sop in sopList"
          :key="sop.id"
          class="sop-card"
          @click="showSopDetail(sop)"
        >
          <view class="sop-header">
            <text class="sop-code">{{ sop.sopCode }}</text>
            <text class="sop-version">v{{ sop.version }}</text>
          </view>
          <text class="sop-title">{{ sop.title }}</text>
          <view class="sop-tags">
            <view class="tag device-tag">
              <text class="tag-text">{{ deviceTypeLabel(sop.deviceType) }}</text>
            </view>
            <view class="tag fault-tag">
              <text class="tag-text">{{ faultTypeLabel(sop.faultType) }}</text>
            </view>
            <view class="tag time-tag">
              <text class="tag-text">⏱ {{ sop.estimatedMinutes || '--' }}分钟</text>
            </view>
          </view>
          <view class="sop-footer">
            <text class="sop-summary" v-if="sop.summary">{{ sop.summary }}</text>
            <view class="view-detail">
              <text class="detail-text">查看详情</text>
              <text class="detail-arrow">›</text>
            </view>
          </view>
        </view>
      </view>

      <view v-else class="empty-wrap">
        <text class="empty-icon">📋</text>
        <text class="empty-text">暂无匹配的 SOP</text>
      </view>

      <view class="safe-bottom"></view>
    </scroll-view>

    <!-- SOP Detail Modal -->
    <view v-if="detailSop" class="modal-mask" @click="detailSop = null">
      <view class="modal-card" @click.stop>
        <view class="modal-header">
          <text class="modal-code">{{ detailSop.sopCode }}</text>
          <text class="modal-close" @click="detailSop = null">✕</text>
        </view>
        <scroll-view scroll-y class="modal-scroll">
          <text class="modal-title">{{ detailSop.title }}</text>
          <view class="modal-tags">
            <view class="tag device-tag"><text class="tag-text">{{ deviceTypeLabel(detailSop.deviceType) }}</text></view>
            <view class="tag fault-tag"><text class="tag-text">{{ faultTypeLabel(detailSop.faultType) }}</text></view>
            <view class="tag time-tag"><text class="tag-text">⏱ {{ detailSop.estimatedMinutes || '--' }}分钟</text></view>
          </view>
          <text v-if="detailSop.summary" class="modal-summary">{{ detailSop.summary }}</text>

          <text v-if="detailSop.steps && detailSop.steps.length" class="modal-section-title">操作步骤</text>
          <view v-for="(step, i) in detailSop.steps" :key="i" class="step-row">
            <view class="step-num">{{ i + 1 }}</view>
            <text class="step-text">{{ step }}</text>
          </view>

          <text v-if="requiredSkills.length" class="modal-section-title">所需技能</text>
          <view class="skill-row">
            <view v-for="s in requiredSkills" :key="s" class="skill-tag">
              <text class="skill-text">{{ s }}</text>
            </view>
          </view>

          <text v-if="detailSop.requiredParts && detailSop.requiredParts.length" class="modal-section-title">所需备件</text>
          <view class="parts-row">
            <view v-for="p in detailSop.requiredParts" :key="p" class="part-tag">
              <text class="part-text">{{ p }}</text>
            </view>
          </view>

          <view class="safe-bottom-modal"></view>
        </scroll-view>
      </view>
    </view>

    <!-- Filter Picker Modal -->
    <view v-if="showFilterPicker" class="modal-mask" @click="showFilterPicker = false">
      <view class="picker-card" @click.stop>
        <text class="picker-title">筛选条件</text>
        <text class="picker-label">设备类型</text>
        <view class="picker-options">
          <view
            v-for="dt in deviceTypeOptions"
            :key="dt.value"
            :class="['picker-opt', { active: filterDeviceType === dt.value }]"
            @click="filterDeviceType = filterDeviceType === dt.value ? '' : dt.value"
          >
            <text class="picker-opt-text">{{ dt.label }}</text>
          </view>
        </view>
        <text class="picker-label">故障类型</text>
        <view class="picker-options">
          <view
            v-for="ft in faultTypeOptions"
            :key="ft.value"
            :class="['picker-opt', { active: filterFaultType === ft.value }]"
            @click="filterFaultType = filterFaultType === ft.value ? '' : ft.value"
          >
            <text class="picker-opt-text">{{ ft.label }}</text>
          </view>
        </view>
        <button class="picker-btn" @click="showFilterPicker = false; doSearch()">应用筛选</button>
      </view>
    </view>
  </view>
</template>

<script setup>
import { ref, computed, onMounted } from 'vue'
import { onPullDownRefresh } from '@dcloudio/uni-app'
import { get } from '@/utils/request'

// --- Search & Filter State ---
const keyword = ref('')
const filterDeviceType = ref('')
const filterFaultType = ref('')
const showFilterPicker = ref(false)
const sopList = ref([])
const loading = ref(false)
const detailSop = ref(null)

// --- Filter Options ---
const deviceTypeOptions = [
  { value: 'ARC_FURNACE', label: '电弧炉' },
  { value: 'CIRCULATING_PUMP', label: '循环水泵' },
  { value: 'AIR_COMPRESSOR', label: '空压机' },
]

const faultTypeOptions = [
  { value: 'MECHANICAL_JAM', label: '机械卡涩' },
  { value: 'COOLING_INTERRUPT', label: '冷却中断' },
  { value: 'ELECTRICAL_OVERLOAD', label: '电气过载' },
  { value: 'SENSOR_DRIFT', label: '传感器漂移' },
  { value: 'BEARING_WEAR', label: '轴承磨损' },
  { value: 'INTERMITTENT_JAM', label: '间歇性卡涩' },
]

// --- Labels ---
const deviceTypeLabel = (t) => {
  const m = { ARC_FURNACE: '电弧炉', CIRCULATING_PUMP: '循环水泵', AIR_COMPRESSOR: '空压机' }
  return m[t] || t
}
const faultTypeLabel = (t) => {
  const m = {
    MECHANICAL_JAM: '机械卡涩', COOLING_INTERRUPT: '冷却中断',
    ELECTRICAL_OVERLOAD: '电气过载', SENSOR_DRIFT: '传感器漂移',
    BEARING_WEAR: '轴承磨损', INTERMITTENT_JAM: '间歇性卡涩',
  }
  return m[t] || t
}

const requiredSkills = computed(() => {
  if (!detailSop.value?.requiredSkills) return []
  const s = detailSop.value.requiredSkills
  if (Array.isArray(s)) return s
  if (typeof s === 'string') return JSON.parse(s)
  return []
})

// --- API Calls ---
const fetchSOPs = () => {
  loading.value = true
  const params = {}
  if (filterDeviceType.value) params.deviceType = filterDeviceType.value
  if (filterFaultType.value) params.faultType = filterFaultType.value
  if (keyword.value.trim()) params.keyword = keyword.value.trim()

  return get('/sops', params)
    .then((data) => {
      sopList.value = data || []
    })
    .catch(() => {
      uni.showToast({ title: '加载 SOP 失败', icon: 'none' })
    })
    .finally(() => {
      loading.value = false
    })
}

const doSearch = () => {
  fetchSOPs()
}

// --- Pull to Refresh ---
onPullDownRefresh(() => {
  fetchSOPs().finally(() => {
    uni.stopPullDownRefresh()
  })
})

const loadMore = () => {
  // SOP list is not paginated in current API — no-op
}

const showSopDetail = (sop) => {
  get(`/sops/${sop.id}`)
    .then((data) => {
      detailSop.value = data
    })
    .catch(() => {
      // Fallback: show list data
      detailSop.value = sop
    })
}

onMounted(() => {
  fetchSOPs()
})
</script>

<style scoped>
.knowledge-page {
  min-height: 100vh;
  background: #0d1117;
  display: flex;
  flex-direction: column;
}

/* Nav */
.nav-bar {
  display: flex;
  align-items: baseline;
  justify-content: space-between;
  padding: 20rpx 32rpx 16rpx;
  background: #161b22;
  border-bottom: 1rpx solid #21262d;
}
.nav-title { font-size: 34rpx; font-weight: 700; color: #e6edf3; }
.nav-subtitle { font-size: 22rpx; color: #6b7280; }

/* Search */
.search-section {
  padding: 20rpx 32rpx 12rpx;
  background: #161b22;
  border-bottom: 1rpx solid #21262d;
}
.search-input-wrap {
  display: flex;
  align-items: center;
  background: #0d1117;
  border: 1rpx solid #21262d;
  border-radius: 16rpx;
  padding: 0 20rpx;
  height: 72rpx;
  margin-bottom: 16rpx;
}
.search-icon { font-size: 28rpx; margin-right: 12rpx; }
.search-input { flex: 1; height: 100%; font-size: 26rpx; color: #e6edf3; background: transparent; }
.search-clear { font-size: 28rpx; color: #6b7280; padding: 8rpx; }

.filter-chips {
  display: flex;
  gap: 16rpx;
  padding-bottom: 4rpx;
}
.filter-chip {
  display: flex;
  align-items: center;
  padding: 8rpx 20rpx;
  border-radius: 20rpx;
  background: #0d1117;
  border: 1rpx solid #21262d;
}
.filter-chip.active { border-color: #f0a500; background: rgba(240, 165, 0, 0.1); }
.chip-text { font-size: 22rpx; color: #8b949e; }
.filter-chip.active .chip-text { color: #f0a500; }
.chip-clear { font-size: 18rpx; color: #6b7280; margin-left: 8rpx; }

/* Loading */
.loading-wrap { flex: 1; display: flex; align-items: center; justify-content: center; }
.loading-text { font-size: 26rpx; color: #8b949e; }

/* SOP Scroll */
.sop-scroll { flex: 1; }
.sop-list { padding: 20rpx 32rpx; }

.sop-card {
  background: #161b22;
  border: 1rpx solid #21262d;
  border-radius: 16rpx;
  padding: 24rpx;
  margin-bottom: 20rpx;
  transition: border-color 0.2s;
}
.sop-card:active { border-color: #f0a500; }

.sop-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 12rpx;
}
.sop-code { font-size: 24rpx; color: #f0a500; font-family: monospace; font-weight: 600; }
.sop-version { font-size: 20rpx; color: #484f58; }

.sop-title {
  font-size: 28rpx; font-weight: 700; color: #e6edf3;
  display: block; margin-bottom: 14rpx;
}

.sop-tags { display: flex; gap: 10rpx; margin-bottom: 12rpx; flex-wrap: wrap; }

.tag {
  padding: 4rpx 14rpx; border-radius: 6rpx;
}
.device-tag { background: rgba(240, 165, 0, 0.12); border: 1rpx solid rgba(240, 165, 0, 0.25); }
.fault-tag { background: rgba(70, 130, 220, 0.12); border: 1rpx solid rgba(70, 130, 220, 0.25); }
.time-tag { background: rgba(46, 196, 182, 0.08); border: 1rpx solid rgba(46, 196, 182, 0.15); }
.tag-text { font-size: 20rpx; color: #c9d1d9; }

.sop-footer { display: flex; align-items: center; justify-content: space-between; }
.sop-summary { flex: 1; font-size: 22rpx; color: #6b7280; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; margin-right: 16rpx; }
.view-detail { display: flex; align-items: center; flex-shrink: 0; }
.detail-text { font-size: 24rpx; color: #f0a500; margin-right: 4rpx; }
.detail-arrow { font-size: 28rpx; color: #f0a500; font-weight: 700; }

/* Empty */
.empty-wrap { flex: 1; display: flex; flex-direction: column; align-items: center; justify-content: center; padding: 80rpx 0; }
.empty-icon { font-size: 64rpx; margin-bottom: 16rpx; }
.empty-text { font-size: 26rpx; color: #6b7280; }

/* Modal */
.modal-mask {
  position: fixed; top: 0; left: 0; right: 0; bottom: 0;
  background: rgba(0,0,0,0.7); z-index: 999;
  display: flex; align-items: flex-end; justify-content: center;
}

.modal-card {
  width: 100%; max-height: 80vh;
  background: #1c2128; border-radius: 28rpx 28rpx 0 0;
  display: flex; flex-direction: column;
}

.modal-header {
  display: flex; align-items: center; justify-content: space-between;
  padding: 28rpx 32rpx 16rpx;
}
.modal-code { font-size: 26rpx; color: #f0a500; font-family: monospace; font-weight: 600; }
.modal-close { font-size: 32rpx; color: #8b949e; padding: 8rpx; }

.modal-scroll { flex: 1; padding: 0 32rpx 40rpx; }
.modal-title { font-size: 32rpx; font-weight: 700; color: #e6edf3; display: block; margin-bottom: 16rpx; }
.modal-tags { display: flex; gap: 10rpx; margin-bottom: 20rpx; flex-wrap: wrap; }
.modal-summary { font-size: 26rpx; color: #8b949e; line-height: 40rpx; margin-bottom: 24rpx; display: block; }
.modal-section-title { font-size: 28rpx; font-weight: 700; color: #f0a500; margin-bottom: 16rpx; display: block; margin-top: 8rpx; }

.step-row { display: flex; margin-bottom: 14rpx; }
.step-num {
  width: 40rpx; height: 40rpx; border-radius: 50%;
  background: #21262d; font-size: 22rpx; color: #f0a500; font-weight: 700;
  display: flex; align-items: center; justify-content: center;
  margin-right: 16rpx; flex-shrink: 0; margin-top: 2rpx;
}
.step-text { font-size: 26rpx; color: #c9d1d9; line-height: 40rpx; flex: 1; }

.skill-row, .parts-row { display: flex; gap: 12rpx; flex-wrap: wrap; margin-bottom: 20rpx; }
.skill-tag { padding: 6rpx 18rpx; border-radius: 8rpx; background: rgba(46, 196, 182, 0.1); border: 1rpx solid rgba(46, 196, 182, 0.2); }
.skill-text { font-size: 22rpx; color: #2ec4b6; }
.part-tag { padding: 6rpx 18rpx; border-radius: 8rpx; background: rgba(240, 165, 0, 0.1); border: 1rpx solid rgba(240, 165, 0, 0.2); }
.part-text { font-size: 22rpx; color: #f0a500; font-family: monospace; }

/* Filter Picker */
.picker-card {
  width: 100%; background: #1c2128; border-radius: 28rpx 28rpx 0 0;
  padding: 32rpx;
}
.picker-title { font-size: 32rpx; font-weight: 700; color: #e6edf3; display: block; margin-bottom: 24rpx; }
.picker-label { font-size: 26rpx; color: #8b949e; display: block; margin-bottom: 12rpx; margin-top: 16rpx; }
.picker-options { display: flex; flex-wrap: wrap; gap: 12rpx; margin-bottom: 8rpx; }
.picker-opt {
  padding: 10rpx 24rpx; border-radius: 20rpx;
  background: #0d1117; border: 1rpx solid #21262d;
}
.picker-opt.active { border-color: #f0a500; background: rgba(240, 165, 0, 0.1); }
.picker-opt-text { font-size: 24rpx; color: #8b949e; }
.picker-opt.active .picker-opt-text { color: #f0a500; }
.picker-btn {
  width: 100%; height: 80rpx; line-height: 80rpx;
  background: linear-gradient(135deg, #f0a500, #d48500);
  color: #0d1117; font-size: 28rpx; font-weight: 700;
  border-radius: 14rpx; border: none; margin-top: 28rpx;
}

.safe-bottom { height: 40rpx; }
.safe-bottom-modal { height: 40rpx; }
</style>
