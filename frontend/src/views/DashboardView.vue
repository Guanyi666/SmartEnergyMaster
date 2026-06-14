<template>
  <!--
    ★★★★★  严格 HUD 覆盖层骨架  ★★★★★
    ─ <DashboardHero3D> 3D 画布:absolute inset:0 z:1, 完全保留不动
    ─ .hud-overlay 全屏覆盖层 (z:10 pointer-events:none flex space-between)
        ├─ .hud-panel.is-left  400px (pointer-events:auto)
        └─ .hud-panel.is-right 400px (pointer-events:auto)
    ─ .hud-header 顶 80px 绝对定位 (pointer-events:auto)
    ─ .hud-bottom-bar 底中央绝对定位 (pointer-events:auto)
  -->
  <div ref="rootRef" class="dashboard-twin">
    <!-- ████ ① 3D 画布 (Three.js, 字节级保留) ████ -->
    <DashboardHero3D
      ref="heroRef"
      :devices="devices"
      :highlight-device-id="highlightDeviceId"
      @device-click="onDeviceClick"
      @ready="onHeroReady"
    />

    <!-- ████ ② 顶部 Header (绝对定位最上方) ████ -->
    <HudHeader
      :summary="summary"
      :devices="devices"
      :alert-count="alerts.length"
    />

    <!-- ████ ③ HUD 全屏覆盖层 (鼠标穿透 + 内含左右两个浮岛) ████ -->
    <div class="hud-overlay">
      <HudLeftPanel
        class="hud-panel is-left"
        data-hud="left"
        :summary="summary"
        :devices="devices"
        :alerts="alerts"
      />

      <HudRightPanel
        class="hud-panel is-right"
        data-hud="right"
        :devices="devices"
        :focus-code="focusDeviceCode"
        :summary="summary"
        :forecast="summary.forecast || []"
        :advice="summary.dispatchAdvice"
        :advice-decided="currentAdviceDecision"
        @select="onDeviceSelect"
        @decide="decideAdvice"
      />
    </div>

    <!-- ████ ④ 底部设备切换条 ████ -->
    <HudBottomNav
      :devices="devices"
      :focus-code="focusDeviceCode"
      @select="onDeviceSelect"
    />

    <!-- ████ 设备详情浮窗 (点击 3D 触发) ████ -->
    <DeviceFloatCard
      :visible="floatVisible"
      :device="floatDevice"
      @close="closeFloat"
      @view-detail="openFullDetail"
    />
    <DeviceDetailDialog v-model="detailVisible" :device="detailDevice" />
  </div>
</template>

<script setup>
import { ref, computed, onMounted, onBeforeUnmount, nextTick } from 'vue'
import { ElMessage } from 'element-plus'
import gsap from 'gsap'

import DashboardHero3D    from '../components/DashboardHero3D.vue'   // ★ Three.js 原样
import HudHeader          from '../components/hud/HudHeader.vue'
import HudLeftPanel       from '../components/hud/HudLeftPanel.vue'
import HudRightPanel      from '../components/hud/HudRightPanel.vue'
import HudBottomNav       from '../components/hud/HudBottomNav.vue'
import DeviceFloatCard    from '../components/hud/DeviceFloatCard.vue'
import DeviceDetailDialog from '../components/DeviceDetailDialog.vue'

import {
  getActiveAlerts, getDashboardSummary, getDevices,
  getLatestSensor, getSensorHistory, postDispatchDecision
} from '../api'
import { usePollingTask } from '../composables/usePollingTask'

const rootRef = ref(null)
const heroRef = ref(null)

const summary = ref({})
const devices = ref([])
const latestData = ref({})
const alerts = ref([])
const forecastHistory = ref([])

const focusDeviceCode = ref('EAF-01')
const highlightDeviceId = ref(null)

const floatVisible = ref(false)
const floatDevice = ref(null)
const detailVisible = ref(false)
const detailDevice = ref(null)

const loadAll = async () => {
  const [s, d, l, a, h] = await Promise.all([
    getDashboardSummary(focusDeviceCode.value),
    getDevices({ size: 999 }),
    getLatestSensor(focusDeviceCode.value),
    getActiveAlerts(8),
    getSensorHistory(focusDeviceCode.value, 3)
  ])
  summary.value = s
  devices.value = d.records || d
  latestData.value = typeof l === 'string' ? {} : l
  alerts.value = a
  forecastHistory.value = Array.isArray(h) ? h : []
  if (!devices.value.find(x => x.deviceCode === focusDeviceCode.value) && devices.value.length) {
    focusDeviceCode.value = devices.value[0].deviceCode
  }
}
const { start: startPolling, run: refreshNow } = usePollingTask(loadAll, 5000)

const onDeviceSelect = async (dev) => {
  focusDeviceCode.value = dev.deviceCode
  highlightDeviceId.value = dev.id || dev.deviceType
  await refreshNow()
  heroRef.value?.applyHighlightById(dev.id || dev.deviceType)
}
const onDeviceClick = async (dev) => {
  focusDeviceCode.value = dev.deviceCode
  highlightDeviceId.value = dev.id || dev.deviceType
  floatDevice.value = dev
  floatVisible.value = true
  await refreshNow()
}
const closeFloat = () => {
  floatVisible.value = false
  heroRef.value?.clearHighlight()
  highlightDeviceId.value = null
}
const openFullDetail = (dev) => {
  floatVisible.value = false
  detailDevice.value = dev
  detailVisible.value = true
}
// 记录已处理的调度建议：用建议内容生成签名，已处理则隐藏采纳/忽略按钮，
// 改为显示"已采纳/已忽略"。轮询刷新时若建议内容未变则保持已处理，建议变化后自动恢复可操作。
const decidedAdviceKey = ref(null)
const decidedAdviceDecision = ref(null)
const adviceKey = (a) => a ? `${focusDeviceCode.value}|${a.level}|${a.title}|${a.content}` : ''
const currentAdviceDecision = computed(() =>
  summary.value?.dispatchAdvice && adviceKey(summary.value.dispatchAdvice) === decidedAdviceKey.value
    ? decidedAdviceDecision.value
    : null
)
const decideAdvice = async (decision) => {
  const advice = summary.value?.dispatchAdvice
  try {
    const res = await postDispatchDecision({ deviceCode: focusDeviceCode.value, decision })
    ElMessage.success(res?.message || (decision === 'CONFIRM' ? '已采纳调度建议' : '已忽略当前建议'))
    decidedAdviceKey.value = adviceKey(advice)
    decidedAdviceDecision.value = decision
  } catch (e) {
    ElMessage.error('提交失败,请稍后重试')
  }
}

const onHeroReady = () => {
  nextTick(() => {
    gsap.timeline({ defaults: { ease: 'power3.out' } })
      .from('.hud-header',  { y: -28, opacity: 0, duration: 0.55 })
      .from('.is-left',      { x: -36, opacity: 0, duration: 0.65 }, '-=0.3')
      .from('.is-right',     { x:  36, opacity: 0, duration: 0.65 }, '-=0.65')
      .from('.hud-bottom-bar', { y: 28, opacity: 0, duration: 0.5 }, '-=0.4')
  })
}

onMounted(async () => { await startPolling() })
onBeforeUnmount(() => { /* 轮询自动清理 */ })
</script>

<style scoped>
.dashboard-twin {
  position: relative;
  width: 100vw;
  height: 100vh;
  min-height: 100vh;
  overflow: hidden;
  background: #02060d;
  margin: 0;
  padding: 0;
  isolation: isolate;
}

/* ★ 全屏 HUD 覆盖层(鼠标穿透到 3D 画布) */
.hud-overlay {
  position: absolute;
  top: 0;
  left: 0;
  width: 100%;
  height: 100%;
  pointer-events: none;
  z-index: 10;
  display: flex;
  justify-content: space-between;
  /* 顶留 88px 给 Header,底留 100px 给 BottomNav */
  padding: 88px 20px 100px 20px;
  box-sizing: border-box;
}
</style>

<!--
  ★ 非作用域全局样式 ★ —— 所有 HUD 子组件共享 .hud-panel 基底类
  必须放在 DashboardView 中以确保 Vue Scoped 不污染
-->
<style>
.hud-panel {
  position: relative;
  pointer-events: auto;            /* ★ HUD 面板恢复鼠标交互 */
  /* 4 角直角折线 (8x8, 2px, #00FFFF) 通过多重 linear-gradient 背景实现 */
  background:
    /* TL */
    linear-gradient(#00FFFF, #00FFFF) top left / 8px 2px no-repeat,
    linear-gradient(#00FFFF, #00FFFF) top left / 2px 8px no-repeat,
    /* TR */
    linear-gradient(#00FFFF, #00FFFF) top right / 8px 2px no-repeat,
    linear-gradient(#00FFFF, #00FFFF) top right / 2px 8px no-repeat,
    /* BL */
    linear-gradient(#00FFFF, #00FFFF) bottom left / 8px 2px no-repeat,
    linear-gradient(#00FFFF, #00FFFF) bottom left / 2px 8px no-repeat,
    /* BR */
    linear-gradient(#00FFFF, #00FFFF) bottom right / 8px 2px no-repeat,
    linear-gradient(#00FFFF, #00FFFF) bottom right / 2px 8px no-repeat,
    rgba(4, 15, 30, 0.7);
  border: 1px solid rgba(0, 255, 255, 0.15);
  border-radius: 2px;
  color: #d9e8f5;
  font-family: 'DIN', 'DIN Alternate', Arial, 'Microsoft YaHei', sans-serif;
  letter-spacing: 0.5px;
}

/* HUD 子段(每个面板内部的区段) */
.hud-block {
  position: relative;
  background:
    /* 同样 4 角折角,但更小 (6x6 1px) */
    linear-gradient(#00FFFF, #00FFFF) top left / 6px 1px no-repeat,
    linear-gradient(#00FFFF, #00FFFF) top left / 1px 6px no-repeat,
    linear-gradient(#00FFFF, #00FFFF) top right / 6px 1px no-repeat,
    linear-gradient(#00FFFF, #00FFFF) top right / 1px 6px no-repeat,
    linear-gradient(#00FFFF, #00FFFF) bottom left / 6px 1px no-repeat,
    linear-gradient(#00FFFF, #00FFFF) bottom left / 1px 6px no-repeat,
    linear-gradient(#00FFFF, #00FFFF) bottom right / 6px 1px no-repeat,
    linear-gradient(#00FFFF, #00FFFF) bottom right / 1px 6px no-repeat,
    rgba(0, 30, 60, 0.35);
  border: 1px solid rgba(0, 255, 255, 0.1);
  border-radius: 2px;
}

/* 通用块标题 */
.hud-block-title {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 6px 12px;
  border-bottom: 1px solid rgba(0, 255, 255, 0.12);
  background: linear-gradient(90deg, rgba(0, 255, 255, 0.12), transparent 70%);
}
.hud-block-title::before {
  content: '';
  display: inline-block;
  width: 3px;
  height: 12px;
  background: #00FFFF;
  box-shadow: 0 0 6px #00FFFF;
}
.hud-block-title__cn {
  font-size: 13px;
  letter-spacing: 2px;
  font-weight: 600;
  color: #ffffff;
}
.hud-block-title__en {
  margin-left: auto;
  font-size: 10px;
  letter-spacing: 1.5px;
  color: rgba(0, 255, 255, 0.55);
  font-family: Arial, sans-serif;
}

/* 通用青色数字样式 */
.hud-num {
  color: #00FFFF;
  font-family: 'DIN', 'DIN Alternate', 'Bahnschrift', Arial, sans-serif;
  font-variant-numeric: tabular-nums;
  text-shadow: 0 0 6px rgba(0, 255, 255, 0.5);
}
</style>
