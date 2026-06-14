<template>
  <view class="inventory-page">
    <!-- Custom Nav -->
    <view class="nav-bar">
      <text class="nav-title">备件库存</text>
      <text class="nav-subtitle">Spare Parts Inventory</text>
    </view>

    <!-- Search & Filter -->
    <view class="search-section">
      <view class="search-input-wrap">
        <text class="search-icon">🔍</text>
        <input
          class="search-input"
          v-model="keyword"
          placeholder="搜索备件名称、编号、规格..."
          placeholder-style="color: #6b7280"
          @confirm="doSearch"
        />
        <text v-if="keyword" class="search-clear" @click="keyword = ''; doSearch()">✕</text>
      </view>

      <view class="filter-row">
        <view :class="['toggle-chip', { active: lowStockOnly }]" @click="lowStockOnly = !lowStockOnly; doSearch()">
          <view :class="['toggle-dot', { active: lowStockOnly }]"></view>
          <text class="toggle-text">仅看低库存</text>
        </view>
        <view class="summary-chip">
          <text class="summary-text">共 {{ partList.length }} 种 · 低库存 {{ lowStockCount }} 项</text>
        </view>
      </view>
    </view>

    <!-- Loading -->
    <view v-if="loading" class="loading-wrap">
      <text class="loading-text">加载中...</text>
    </view>

    <!-- Parts List -->
    <scroll-view
      v-else
      scroll-y
      class="parts-scroll"
      @scrolltolower="loadMore"
      :refresher-enabled="true"
      :refresher-triggered="refreshing"
      @refresherrefresh="onRefresh"
    >
      <view v-if="partList.length > 0" class="parts-list">
        <view
          v-for="part in partList"
          :key="part.id"
          :class="['part-card', { 'low-stock': part.lowStock }]"
          @click="showPartDetail(part)"
        >
          <view v-if="part.lowStock" class="low-stock-ribbon">低库存</view>

          <view class="part-header">
            <text class="part-name">{{ part.name }}</text>
            <text class="part-code">{{ part.partCode }}</text>
          </view>

          <text class="part-spec" v-if="part.spec">{{ part.spec }}</text>

          <view class="stock-row">
            <view class="stock-info">
              <view class="stock-bar-wrap">
                <view
                  :class="['stock-bar', part.lowStock ? 'bar-danger' : 'bar-normal']"
                  :style="{ width: stockPercent(part) + '%' }"
                ></view>
              </view>
              <view class="stock-numbers">
                <text :class="['stock-current', { 'text-danger': part.lowStock }]">
                  {{ part.quantity ?? 0 }}
                </text>
                <text class="stock-divider">/</text>
                <text class="stock-safety">{{ part.safetyStock ?? 0 }}</text>
              </view>
            </view>
            <view v-if="part.lowStock" class="warning-badge">
              <text class="warning-icon">⚠️</text>
              <text class="warning-text">低于安全库存</text>
            </view>
          </view>

          <view class="part-footer">
            <text class="part-location" v-if="part.location">📍 {{ part.location }}</text>
            <text class="part-unit" v-if="part.unit">{{ part.unit }}</text>
          </view>
        </view>
      </view>

      <view v-else class="empty-wrap">
        <text class="empty-icon">📦</text>
        <text class="empty-text">暂无匹配的备件</text>
      </view>

      <view class="safe-bottom"></view>
    </scroll-view>

    <!-- Part Detail Modal -->
    <view v-if="detailPart" class="modal-mask" @click="detailPart = null">
      <view class="modal-card" @click.stop>
        <view class="modal-header">
          <text class="modal-name">{{ detailPart.name }}</text>
          <text class="modal-close" @click="detailPart = null">✕</text>
        </view>
        <scroll-view scroll-y class="modal-scroll">
          <view class="detail-grid">
            <view class="detail-item">
              <text class="detail-label">备件编号</text>
              <text class="detail-value mono">{{ detailPart.partCode }}</text>
            </view>
            <view class="detail-item">
              <text class="detail-label">规格型号</text>
              <text class="detail-value">{{ detailPart.spec || '-' }}</text>
            </view>
            <view class="detail-item">
              <text class="detail-label">计量单位</text>
              <text class="detail-value">{{ detailPart.unit || '-' }}</text>
            </view>
            <view class="detail-item">
              <text class="detail-label">当前库存</text>
              <text :class="['detail-value', 'stock-big', { 'text-danger': detailPart.lowStock }]">
                {{ detailPart.quantity ?? 0 }}
              </text>
            </view>
            <view class="detail-item">
              <text class="detail-label">安全库存</text>
              <text class="detail-value">{{ detailPart.safetyStock ?? 0 }}</text>
            </view>
            <view class="detail-item">
              <text class="detail-label">单价</text>
              <text class="detail-value">¥{{ detailPart.unitPrice || '-' }}</text>
            </view>
            <view class="detail-item">
              <text class="detail-label">供应商</text>
              <text class="detail-value">{{ detailPart.supplier || '-' }}</text>
            </view>
            <view class="detail-item">
              <text class="detail-label">存放位置</text>
              <text class="detail-value">{{ detailPart.location || '-' }}</text>
            </view>
          </view>

          <view v-if="detailPart.lowStock" class="detail-warning">
            <text class="warning-text-lg">⚠️ 当前库存低于安全库存阈值，请尽快补充</text>
          </view>

          <view class="safe-bottom-modal"></view>
        </scroll-view>
      </view>
    </view>
  </view>
</template>

<script setup>
import { ref, computed, onMounted } from 'vue'
import { get } from '@/utils/request'

// --- State ---
const keyword = ref('')
const lowStockOnly = ref(false)
const partList = ref([])
const loading = ref(false)
const refreshing = ref(false)
const detailPart = ref(null)

// --- Computed ---
const lowStockCount = computed(() => partList.value.filter(p => p.lowStock).length)

const stockPercent = (part) => {
  if (!part || part.safetyStock == null || part.safetyStock === 0) return 100
  const pct = ((part.quantity ?? 0) / part.safetyStock) * 100
  return Math.min(pct, 150) // cap at 150% for visual
}

// --- API ---
const fetchParts = () => {
  loading.value = true
  const params = {}
  if (keyword.value.trim()) params.keyword = keyword.value.trim()
  if (lowStockOnly.value) params.lowStockOnly = true

  return get('/spare-parts', params)
    .then((data) => {
      partList.value = data || []
    })
    .catch(() => {
      uni.showToast({ title: '加载备件库存失败', icon: 'none' })
    })
    .finally(() => {
      loading.value = false
      refreshing.value = false
    })
}

const doSearch = () => {
  fetchParts()
}

const onRefresh = () => {
  refreshing.value = true
  fetchParts()
}

const loadMore = () => {
  // Inventory API is not paginated currently
}

const showPartDetail = (part) => {
  get(`/spare-parts/${part.id}`)
    .then((data) => {
      detailPart.value = data
    })
    .catch(() => {
      detailPart.value = part
    })
}

onMounted(() => {
  fetchParts()
})
</script>

<style scoped>
.inventory-page {
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
  margin-bottom: 14rpx;
}
.search-icon { font-size: 28rpx; margin-right: 12rpx; }
.search-input { flex: 1; height: 100%; font-size: 26rpx; color: #e6edf3; background: transparent; }
.search-clear { font-size: 28rpx; color: #6b7280; padding: 8rpx; }

.filter-row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding-bottom: 4rpx;
}

.toggle-chip {
  display: flex;
  align-items: center;
  padding: 10rpx 24rpx;
  border-radius: 20rpx;
  background: #0d1117;
  border: 1rpx solid #21262d;
}
.toggle-chip.active { border-color: #e63946; background: rgba(230, 57, 70, 0.08); }

.toggle-dot {
  width: 24rpx; height: 24rpx; border-radius: 50%;
  background: #30363d; margin-right: 10rpx;
}
.toggle-dot.active { background: #e63946; box-shadow: 0 0 10rpx rgba(230, 57, 70, 0.5); }

.toggle-text { font-size: 24rpx; color: #8b949e; }
.toggle-chip.active .toggle-text { color: #e63946; }

.summary-chip { padding: 10rpx 16rpx; }
.summary-text { font-size: 22rpx; color: #484f58; }

/* Loading */
.loading-wrap { flex: 1; display: flex; align-items: center; justify-content: center; }
.loading-text { font-size: 26rpx; color: #8b949e; }

/* Parts Scroll */
.parts-scroll { flex: 1; }
.parts-list { padding: 20rpx 32rpx; }

.part-card {
  position: relative;
  background: #161b22;
  border: 1rpx solid #21262d;
  border-radius: 16rpx;
  padding: 24rpx;
  margin-bottom: 20rpx;
  overflow: hidden;
  transition: border-color 0.2s;
}
.part-card:active { border-color: #f0a500; }
.part-card.low-stock { border-left: 6rpx solid #e63946; }

.low-stock-ribbon {
  position: absolute; top: 0; right: 0;
  background: #e63946; color: #fff;
  font-size: 18rpx; font-weight: 700;
  padding: 4rpx 16rpx;
  border-radius: 0 0 0 12rpx;
}

.part-header {
  display: flex;
  align-items: baseline;
  justify-content: space-between;
  margin-bottom: 8rpx;
}
.part-name { font-size: 28rpx; font-weight: 700; color: #e6edf3; }
.part-code { font-size: 22rpx; color: #484f58; font-family: monospace; }

.part-spec { font-size: 24rpx; color: #8b949e; display: block; margin-bottom: 16rpx; }

.stock-row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 12rpx;
}
.stock-info { flex: 1; margin-right: 16rpx; }

.stock-bar-wrap {
  height: 8rpx; background: #21262d;
  border-radius: 4rpx; overflow: hidden;
  margin-bottom: 8rpx;
}
.stock-bar { height: 100%; border-radius: 4rpx; transition: width 0.5s; }
.bar-normal { background: #2ec4b6; }
.bar-danger { background: #e63946; }

.stock-numbers { display: flex; align-items: baseline; }
.stock-current { font-size: 28rpx; font-weight: 700; color: #e6edf3; }
.stock-divider { font-size: 22rpx; color: #484f58; margin: 0 6rpx; }
.stock-safety { font-size: 22rpx; color: #8b949e; }
.text-danger { color: #e63946; }

.warning-badge {
  display: flex; align-items: center;
  padding: 6rpx 14rpx; border-radius: 8rpx;
  background: rgba(230, 57, 70, 0.1);
  border: 1rpx solid rgba(230, 57, 70, 0.25);
}
.warning-icon { font-size: 22rpx; margin-right: 6rpx; }
.warning-text { font-size: 20rpx; color: #e63946; font-weight: 600; }

.part-footer { display: flex; align-items: center; justify-content: space-between; }
.part-location { font-size: 22rpx; color: #6b7280; }
.part-unit { font-size: 22rpx; color: #484f58; background: #21262d; padding: 2rpx 12rpx; border-radius: 6rpx; }

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
  width: 100%; max-height: 75vh;
  background: #1c2128; border-radius: 28rpx 28rpx 0 0;
  display: flex; flex-direction: column;
}
.modal-header {
  display: flex; align-items: center; justify-content: space-between;
  padding: 28rpx 32rpx 16rpx;
}
.modal-name { font-size: 30rpx; font-weight: 700; color: #e6edf3; flex: 1; }
.modal-close { font-size: 32rpx; color: #8b949e; padding: 8rpx; }

.modal-scroll { flex: 1; padding: 0 32rpx 40rpx; }

.detail-grid { margin-bottom: 16rpx; }
.detail-item {
  display: flex; justify-content: space-between; align-items: center;
  padding: 16rpx 0; border-bottom: 1rpx solid #21262d;
}
.detail-item:last-child { border-bottom: none; }
.detail-label { font-size: 24rpx; color: #8b949e; }
.detail-value { font-size: 26rpx; color: #c9d1d9; }
.detail-value.mono { font-family: monospace; }
.detail-value.stock-big { font-size: 32rpx; font-weight: 700; }

.detail-warning {
  background: rgba(230, 57, 70, 0.1);
  border: 1rpx solid rgba(230, 57, 70, 0.3);
  border-radius: 12rpx; padding: 20rpx; margin-top: 12rpx;
}
.warning-text-lg { font-size: 26rpx; color: #e63946; }

.safe-bottom { height: 40rpx; }
.safe-bottom-modal { height: 40rpx; }
</style>
