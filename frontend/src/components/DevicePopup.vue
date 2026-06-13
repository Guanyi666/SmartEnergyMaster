<template>
  <Transition name="popup">
    <div v-if="visible && device" class="device-popup-mask" @click.self="onClose">
      <div ref="popupRef" class="device-popup glass-popup">
        <!-- 标题栏 -->
        <header class="popup-header">
          <div class="popup-header-left">
            <span class="popup-status-dot" :style="{ background: statusColor, boxShadow: `0 0 8px ${statusColor}` }"></span>
            <div>
              <h3 class="popup-title">{{ device.deviceName }}</h3>
              <p class="popup-code">{{ device.deviceCode }} · {{ deviceTypeLabel }}</p>
            </div>
          </div>
          <div class="popup-header-right">
            <span class="popup-status-pill" :style="{ background: statusColor + '20', borderColor: statusColor, color: statusColor }">
              {{ formatStatus(device.status) }}
            </span>
            <button class="popup-close" @click="onClose">×</button>
          </div>
        </header>

        <!-- 主体：3D 预览 + 实时数据 -->
        <div class="popup-body">
          <!-- 左：3D 特写预览 -->
          <section class="popup-3d-section">
            <div ref="canvasContainerRef" class="popup-3d-canvas"></div>
            <div class="popup-3d-overlay">
              <span class="hud-tag hud-tag--cyan">3D PREVIEW</span>
              <span class="hud-tag hud-tag--orange">LIVE</span>
            </div>
            <div class="popup-3d-controls">
              <button :class="{ active: autoRotate }" @click="toggleRotate">AUTO</button>
              <button @click="resetCamera">RESET</button>
            </div>
          </section>

          <!-- 右：实时数据 + 趋势 -->
          <section class="popup-data-section">
            <!-- 4 项关键指标 -->
            <div class="metric-grid">
              <div class="metric-tile metric-tile--orange">
                <div class="metric-bar"></div>
                <div class="metric-icon">🌡</div>
                <div class="metric-body">
                  <span class="metric-label">温度 / TEMP</span>
                  <strong class="metric-value">{{ formatNum(device.temperature) }}<em>°C</em></strong>
                  <span class="metric-trend" :class="tempTrendClass">↑ {{ device.temperature > 800 ? '高' : '正常' }}</span>
                </div>
              </div>

              <div class="metric-tile metric-tile--cyan">
                <div class="metric-bar"></div>
                <div class="metric-icon">📊</div>
                <div class="metric-body">
                  <span class="metric-label">压力 / PRESS</span>
                  <strong class="metric-value">{{ formatNum(device.pressure) }}<em>kPa</em></strong>
                  <span class="metric-trend">正常</span>
                </div>
              </div>

              <div class="metric-tile metric-tile--violet">
                <div class="metric-bar"></div>
                <div class="metric-icon">〰</div>
                <div class="metric-body">
                  <span class="metric-label">振动 / VIB</span>
                  <strong class="metric-value">{{ formatNum(device.vibration) }}<em>mm/s</em></strong>
                  <span class="metric-trend">稳定</span>
                </div>
              </div>

              <div class="metric-tile metric-tile--green">
                <div class="metric-bar"></div>
                <div class="metric-icon">⚡</div>
                <div class="metric-body">
                  <span class="metric-label">功率 / POWER</span>
                  <strong class="metric-value">{{ formatNum(device.usageKwh) }}<em>kWh</em></strong>
                  <span class="metric-trend">运行中</span>
                </div>
              </div>
            </div>

            <!-- 趋势图 -->
            <div class="popup-trend glass-popup-inner">
              <div class="popup-trend-header">
                <h4>实时趋势 / REAL-TIME TREND</h4>
                <span class="muted">最近 30 分钟</span>
              </div>
              <TrendChart :records="trendData" />
            </div>

            <!-- 设备参数 -->
            <div class="popup-params glass-popup-inner">
              <h4>设备参数 / SPECS</h4>
              <div class="params-grid">
                <div class="param-row">
                  <span>设备类型</span>
                  <strong>{{ deviceTypeLabel }}</strong>
                </div>
                <div class="param-row">
                  <span>设备编号</span>
                  <strong>{{ device.deviceCode }}</strong>
                </div>
                <div class="param-row">
                  <span>所在位置</span>
                  <strong>{{ device.location || '炼钢一车间' }}</strong>
                </div>
                <div class="param-row">
                  <span>负责人</span>
                  <strong>{{ device.maintainer || '系统自动' }}</strong>
                </div>
                <div class="param-row">
                  <span>投运时间</span>
                  <strong>{{ device.createdAt ? new Date(device.createdAt).toLocaleDateString('zh-CN') : '2026-01-01' }}</strong>
                </div>
                <div class="param-row">
                  <span>最后更新</span>
                  <strong>{{ nowText }}</strong>
                </div>
              </div>
            </div>
          </section>
        </div>

        <!-- 底部操作栏 -->
        <footer class="popup-footer">
          <div class="footer-left">
            <span class="footer-marker">DEVICE_ID · {{ device.id }}</span>
          </div>
          <div class="footer-actions">
            <el-button size="small" @click="onClose">关闭</el-button>
            <el-button size="small" type="primary" @click="onViewDetail">查看完整详情</el-button>
          </div>
        </footer>
      </div>
    </div>
  </Transition>
</template>

<script setup>
import { ref, computed, watch, onMounted, onBeforeUnmount, nextTick } from 'vue'
import * as THREE from 'three'
import { OrbitControls } from 'three/examples/jsm/controls/OrbitControls.js'
import TrendChart from './TrendChart.vue'

const props = defineProps({
  visible: { type: Boolean, default: false },
  device:  { type: Object, default: null }
})
const emit = defineEmits(['close', 'view-detail'])

const popupRef = ref(null)
const canvasContainerRef = ref(null)
const nowText = ref(formatTime(new Date()))

let renderer, scene, camera, controls, frameId, clock

const autoRotate = ref(true)

const DEVICE_TYPE_LABELS = {
  ARC_FURNACE: '电弧炉',
  PUMP: '循环水泵',
  COMPRESSOR: '空压机',
  FAN: '风机',
  TRANSFORMER: '变压器',
  BOILER: '锅炉'
}
const deviceTypeLabel = computed(() => DEVICE_TYPE_LABELS[props.device?.deviceType] || '设备')

const statusColor = computed(() => {
  const c = ({
    RUNNING: '#3bff9f',
    HIGH_LOAD: '#ffb347',
    IDLE: '#5cdcff',
    STOPPED: '#94a3b8',
    OFFLINE: '#64748b',
    FAULT: '#ff5d5d',
    MAINTENANCE: '#ff7e00'
  })[props.device?.status] || '#5cdcff'
  return c
})

const formatStatus = (s) => ({
  RUNNING: '运行中', HIGH_LOAD: '高负荷', IDLE: '空转', STOPPED: '停机',
  OFFLINE: '离线', FAULT: '故障', MAINTENANCE: '维修中'
})[s] || s || '未知'

const formatNum = (n) => {
  if (n == null || n === '') return '--'
  const v = Number(n)
  if (Number.isNaN(v)) return '--'
  return v.toFixed(1)
}

const formatTime = (d) => `${String(d.getHours()).padStart(2,'0')}:${String(d.getMinutes()).padStart(2,'0')}:${String(d.getSeconds()).padStart(2,'0')}`

const tempTrendClass = computed(() => {
  const t = Number(props.device?.temperature) || 0
  if (t > 1000) return 'is-high'
  if (t > 500) return 'is-warn'
  return 'is-ok'
})

// 生成模拟趋势数据
const trendData = ref([])
function generateTrend () {
  const arr = []
  const baseT = Number(props.device?.temperature) || 600
  const baseP = Number(props.device?.pressure) || 150
  const now = Date.now()
  for (let i = 30; i >= 0; i--) {
    const time = new Date(now - i * 60 * 1000)
    arr.push({
      time: time.toISOString(),
      temperature: baseT + (Math.random() - 0.5) * 200,
      pressure: baseP + (Math.random() - 0.5) * 30,
      vibration: 5 + Math.random() * 5,
      usageKwh: 50 + Math.random() * 30,
      xianPriceTier: i % 4 < 2 ? 'FLAT' : (i % 4 === 2 ? 'PEAK' : 'VALLEY')
    })
  }
  trendData.value = arr
}

function onClose () { emit('close') }
function onViewDetail () { emit('view-detail', props.device) }

function toggleRotate () { autoRotate.value = !autoRotate.value; if (controls) controls.autoRotate = autoRotate.value }
function resetCamera () {
  if (!camera || !controls) return
  camera.position.set(3, 2.5, 4)
  controls.target.set(0, 1, 0)
  controls.update()
}

// ============ 3D 特写渲染 ============
function initMini3D () {
  const container = canvasContainerRef.value
  if (!container || !props.device) return

  const rect = container.getBoundingClientRect()
  scene = new THREE.Scene()
  scene.background = null

  camera = new THREE.PerspectiveCamera(40, rect.width / rect.height, 0.1, 50)
  camera.position.set(3, 2.5, 4)

  renderer = new THREE.WebGLRenderer({ antialias: true, alpha: true })
  renderer.setSize(rect.width, rect.height)
  renderer.setPixelRatio(Math.min(window.devicePixelRatio, 2))
  renderer.toneMapping = THREE.ACESFilmicToneMapping
  container.appendChild(renderer.domElement)

  controls = new OrbitControls(camera, renderer.domElement)
  controls.enableDamping = true
  controls.autoRotate = autoRotate.value
  controls.autoRotateSpeed = 1.0
  controls.minDistance = 2
  controls.maxDistance = 8

  // 光照
  scene.add(new THREE.AmbientLight(0x404060, 0.4))
  scene.add(new THREE.HemisphereLight(0x5cdcff, 0x0a1929, 0.5))

  const dir = new THREE.DirectionalLight(0xffffff, 1.2)
  dir.position.set(5, 8, 5)
  scene.add(dir)

  const accent = new THREE.PointLight(new THREE.Color(statusColor.value), 1.0, 15)
  accent.position.set(2, 3, 2)
  scene.add(accent)

  // 设备模型
  const model = buildMiniModel(props.device.deviceType)
  scene.add(model)

  // 地面环
  const ringGeo = new THREE.RingGeometry(1.5, 1.7, 32)
  const ringMat = new THREE.MeshBasicMaterial({ color: new THREE.Color(statusColor.value), transparent: true, opacity: 0.5, side: THREE.DoubleSide })
  const ring = new THREE.Mesh(ringGeo, ringMat)
  ring.rotation.x = -Math.PI / 2
  ring.position.y = 0.01
  scene.add(ring)

  clock = new THREE.Clock()
  animate()
}

function buildMiniModel (type) {
  const group = new THREE.Group()
  const color = new THREE.Color(statusColor.value)
  const mat = new THREE.MeshStandardMaterial({ color: 0x9aafca, metalness: 0.85, roughness: 0.35 })
  const accentMat = new THREE.MeshStandardMaterial({ color, metalness: 0.6, roughness: 0.3, emissive: color, emissiveIntensity: 0.5 })
  const darkMat = new THREE.MeshStandardMaterial({ color: 0x2a3850, metalness: 0.7, roughness: 0.5 })

  if (type === 'ARC_FURNACE') {
    const body = new THREE.Mesh(new THREE.CylinderGeometry(0.8, 0.8, 1.6, 32), mat)
    body.position.y = 0.8
    body.castShadow = true
    group.add(body)
    const cap = new THREE.Mesh(new THREE.CylinderGeometry(0.7, 0.8, 0.2, 32), new THREE.MeshStandardMaterial({ color: 0xff7e00, emissive: 0xff7e00, emissiveIntensity: 0.6 }))
    cap.position.y = 1.7
    group.add(cap)
    for (let i = -1; i <= 1; i++) {
      const e = new THREE.Mesh(new THREE.CylinderGeometry(0.06, 0.06, 1.0, 8), new THREE.MeshStandardMaterial({ color: 0xc46a18, metalness: 0.9 }))
      e.position.set(i * 0.35, 2.3, 0)
      group.add(e)
      const tip = new THREE.Mesh(new THREE.SphereGeometry(0.1, 12, 12), accentMat)
      tip.position.set(i * 0.35, 2.8, 0)
      group.add(tip)
    }
  } else if (type === 'PUMP') {
    const motor = new THREE.Mesh(new THREE.CylinderGeometry(0.5, 0.5, 1.2, 24), new THREE.MeshStandardMaterial({ color: 0x5a8ab8, metalness: 0.85 }))
    motor.position.set(-0.7, 0.6, 0)
    motor.castShadow = true
    group.add(motor)
    const housing = new THREE.Mesh(new THREE.SphereGeometry(0.65, 24, 16), new THREE.MeshStandardMaterial({ color: 0x3a6a98, metalness: 0.85 }))
    housing.position.set(0.3, 0.7, 0)
    housing.castShadow = true
    group.add(housing)
    const blades = new THREE.Group()
    for (let i = 0; i < 4; i++) {
      const b = new THREE.Mesh(new THREE.BoxGeometry(0.08, 0.5, 0.3), accentMat)
      b.rotation.y = (i * Math.PI) / 2
      b.position.set(Math.cos((i * Math.PI) / 2) * 0.3, 0, Math.sin((i * Math.PI) / 2) * 0.3)
      blades.add(b)
    }
    blades.position.set(0.3, 0.7, 0)
    group.add(blades)
  } else if (type === 'COMPRESSOR') {
    const tankGeo = new THREE.CylinderGeometry(0.5, 0.5, 2.2, 32)
    tankGeo.rotateZ(Math.PI / 2)
    const tank = new THREE.Mesh(tankGeo, new THREE.MeshStandardMaterial({ color: 0xd4621a, metalness: 0.85 }))
    tank.position.y = 0.7
    tank.castShadow = true
    group.add(tank)
    const cap1 = new THREE.Mesh(new THREE.SphereGeometry(0.5, 24, 16), new THREE.MeshStandardMaterial({ color: 0xff8a30, metalness: 0.85 }))
    cap1.position.set(-1.1, 0.7, 0)
    group.add(cap1)
    const cap2 = new THREE.Mesh(new THREE.SphereGeometry(0.5, 24, 16), new THREE.MeshStandardMaterial({ color: 0xff8a30, metalness: 0.85 }))
    cap2.position.set(1.1, 0.7, 0)
    group.add(cap2)
  } else if (type === 'FAN') {
    const housing = new THREE.Mesh(new THREE.CylinderGeometry(0.7, 0.7, 1.2, 24), new THREE.MeshStandardMaterial({ color: 0x3a9a4a, metalness: 0.7 }))
    housing.position.y = 0.6
    housing.castShadow = true
    group.add(housing)
    const blades = new THREE.Group()
    for (let i = 0; i < 5; i++) {
      const b = new THREE.Mesh(new THREE.BoxGeometry(0.08, 0.08, 1.1), accentMat)
      b.rotation.y = (i * Math.PI * 2) / 5
      b.rotation.z = 0.4
      blades.add(b)
    }
    blades.position.y = 0.6
    group.add(blades)
  } else if (type === 'TRANSFORMER') {
    const body = new THREE.Mesh(new THREE.BoxGeometry(1.6, 1.2, 1.0), new THREE.MeshStandardMaterial({ color: 0x6a7282, metalness: 0.85 }))
    body.position.y = 0.6
    body.castShadow = true
    group.add(body)
    for (let i = -1; i <= 1; i++) {
      const b = new THREE.Mesh(new THREE.CylinderGeometry(0.06, 0.09, 0.8, 12), new THREE.MeshStandardMaterial({ color: 0xc8a060, metalness: 0.6 }))
      b.position.set(i * 0.4, 1.6, 0)
      group.add(b)
      const ball = new THREE.Mesh(new THREE.SphereGeometry(0.1, 12, 12), accentMat)
      ball.position.set(i * 0.4, 2.0, 0)
      group.add(ball)
    }
  } else if (type === 'BOILER') {
    const tankGeo = new THREE.CylinderGeometry(0.55, 0.55, 2.0, 32)
    tankGeo.rotateZ(Math.PI / 2)
    const tank = new THREE.Mesh(tankGeo, new THREE.MeshStandardMaterial({ color: 0xb04040, metalness: 0.85 }))
    tank.position.y = 0.7
    tank.castShadow = true
    group.add(tank)
    const cap1 = new THREE.Mesh(new THREE.SphereGeometry(0.55, 24, 16), new THREE.MeshStandardMaterial({ color: 0xd06060, metalness: 0.85 }))
    cap1.position.set(-1.0, 0.7, 0)
    group.add(cap1)
    const cap2 = new THREE.Mesh(new THREE.SphereGeometry(0.55, 24, 16), new THREE.MeshStandardMaterial({ color: 0xd06060, metalness: 0.85 }))
    cap2.position.set(1.0, 0.7, 0)
    group.add(cap2)
    // 燃烧器火焰
    const flameMat = new THREE.MeshBasicMaterial({ color: 0xff5d5d, transparent: true, opacity: 0.8 })
    const flame = new THREE.Mesh(new THREE.SphereGeometry(0.25, 12, 12), flameMat)
    flame.position.set(-1.0, 0.4, 0)
    group.add(flame)
  }

  const base = new THREE.Mesh(new THREE.BoxGeometry(2.0, 0.08, 2.0), darkMat)
  base.position.y = 0.04
  group.add(base)

  return group
}

function animate () {
  frameId = requestAnimationFrame(animate)
  const t = clock.getElapsedTime()
  scene.traverse(obj => {
    if (obj.userData.rotate) {
      obj.userData.rotate.rotation.y = t * 1.2
    }
  })
  controls.update()
  renderer.render(scene, camera)
}

function disposeMini3D () {
  if (frameId) cancelAnimationFrame(frameId)
  if (controls) controls.dispose()
  if (renderer) {
    renderer.dispose()
    renderer.forceContextLoss?.()
    if (renderer.domElement && renderer.domElement.parentNode) {
      renderer.domElement.parentNode.removeChild(renderer.domElement)
    }
  }
  if (scene) {
    scene.traverse(obj => {
      if (obj.geometry) obj.geometry.dispose()
      if (obj.material) {
        if (Array.isArray(obj.material)) obj.material.forEach(m => m.dispose())
        else obj.material.dispose()
      }
    })
  }
}

watch(() => props.visible, async (v) => {
  if (v) {
    await nextTick()
    generateTrend()
    initMini3D()
  } else {
    disposeMini3D()
  }
})

watch(() => props.device, () => {
  if (props.visible) {
    disposeMini3D()
    nextTick(() => {
      generateTrend()
      initMini3D()
    })
  }
})

// 时钟
let clockTimer = null
onMounted(() => {
  clockTimer = setInterval(() => { nowText.value = formatTime(new Date()) }, 1000)
})
onBeforeUnmount(() => {
  if (clockTimer) clearInterval(clockTimer)
  disposeMini3D()
})
</script>

<style scoped>
.device-popup-mask {
  position: fixed;
  inset: 0;
  background: rgba(0, 0, 0, 0.6);
  backdrop-filter: blur(8px);
  z-index: 1000;
  display: grid;
  place-items: center;
  padding: 20px;
}

.device-popup {
  width: min(1100px, 100%);
  max-height: calc(100vh - 40px);
  background: linear-gradient(180deg, rgba(13, 37, 64, 0.95), rgba(10, 25, 41, 0.95));
  border: 1px solid rgba(92, 220, 255, 0.35);
  border-radius: 16px;
  box-shadow: 0 20px 60px rgba(0, 0, 0, 0.6), 0 0 30px rgba(92, 220, 255, 0.25);
  display: flex;
  flex-direction: column;
  overflow: hidden;
  position: relative;
}

.device-popup::before {
  content: '';
  position: absolute;
  top: 0;
  left: 8%;
  right: 8%;
  height: 1px;
  background: linear-gradient(90deg, transparent, var(--accent-cyan), transparent);
  z-index: 1;
}

.glass-popup {
  backdrop-filter: blur(20px);
}

.glass-popup-inner {
  background: linear-gradient(180deg, rgba(13, 37, 64, 0.6), rgba(10, 25, 41, 0.4));
  border: 1px solid rgba(92, 220, 255, 0.15);
  border-radius: 10px;
  padding: 12px 14px;
}

/* 头部 */
.popup-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 14px 18px;
  border-bottom: 1px solid rgba(92, 220, 255, 0.18);
  background: rgba(13, 37, 64, 0.4);
}

.popup-header-left {
  display: flex;
  align-items: center;
  gap: 12px;
}

.popup-status-dot {
  width: 10px;
  height: 10px;
  border-radius: 50%;
  animation: status-blink 1.5s ease-in-out infinite;
}

@keyframes status-blink {
  0%, 100% { opacity: 1; }
  50% { opacity: 0.5; }
}

.popup-title {
  margin: 0;
  font-size: 17px;
  font-weight: 700;
  letter-spacing: 2px;
  color: #ffffff;
}

.popup-code {
  margin: 2px 0 0;
  font-size: 11px;
  color: var(--text-muted);
  letter-spacing: 1px;
}

.popup-header-right {
  display: flex;
  align-items: center;
  gap: 10px;
}

.popup-status-pill {
  padding: 4px 12px;
  border: 1px solid;
  border-radius: 999px;
  font-size: 11px;
  font-weight: 700;
  letter-spacing: 2px;
}

.popup-close {
  width: 28px;
  height: 28px;
  background: rgba(13, 37, 64, 0.6);
  border: 1px solid rgba(92, 220, 255, 0.2);
  border-radius: 6px;
  color: var(--text-secondary);
  font-size: 18px;
  line-height: 1;
  cursor: pointer;
  transition: all 0.2s ease;
}

.popup-close:hover {
  border-color: var(--accent-red);
  color: var(--accent-red);
  background: rgba(255, 93, 93, 0.1);
}

/* 主体 */
.popup-body {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 14px;
  padding: 14px 18px;
  overflow: hidden;
  min-height: 0;
}

.popup-3d-section {
  position: relative;
  border-radius: 12px;
  overflow: hidden;
  background:
    radial-gradient(ellipse at center, rgba(13, 37, 64, 0.5) 0%, rgba(5, 11, 24, 0.85) 100%);
  border: 1px solid rgba(92, 220, 255, 0.18);
  min-height: 360px;
}

.popup-3d-canvas {
  position: absolute;
  inset: 0;
  width: 100%;
  height: 100%;
}

.popup-3d-canvas :deep(canvas) {
  width: 100% !important;
  height: 100% !important;
  display: block;
}

.popup-3d-overlay {
  position: absolute;
  top: 10px;
  right: 12px;
  display: flex;
  flex-direction: column;
  gap: 4px;
  pointer-events: none;
  z-index: 2;
}

.hud-tag {
  display: inline-block;
  padding: 3px 8px;
  background: rgba(13, 37, 64, 0.7);
  border: 1px solid rgba(92, 220, 255, 0.3);
  border-radius: 4px;
  font-size: 9px;
  letter-spacing: 2px;
  color: var(--accent-cyan);
  font-weight: 700;
  backdrop-filter: blur(8px);
}

.hud-tag--orange {
  border-color: rgba(255, 126, 0, 0.4);
  color: var(--accent-orange);
}

.popup-3d-controls {
  position: absolute;
  bottom: 10px;
  left: 12px;
  display: flex;
  gap: 6px;
  z-index: 2;
}

.popup-3d-controls button {
  padding: 4px 10px;
  background: rgba(13, 37, 64, 0.7);
  border: 1px solid rgba(92, 220, 255, 0.25);
  border-radius: 4px;
  font-size: 10px;
  letter-spacing: 1px;
  color: var(--text-secondary);
  cursor: pointer;
  font-weight: 700;
  backdrop-filter: blur(8px);
}

.popup-3d-controls button.active {
  border-color: var(--accent-cyan);
  color: var(--accent-cyan);
  background: rgba(92, 220, 255, 0.15);
}

/* 数据区 */
.popup-data-section {
  display: flex;
  flex-direction: column;
  gap: 12px;
  min-height: 0;
  overflow-y: auto;
}

.metric-grid {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 10px;
}

.metric-tile {
  position: relative;
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 12px 14px 12px 18px;
  background: linear-gradient(180deg, rgba(13, 37, 64, 0.7), rgba(10, 25, 41, 0.5));
  border: 1px solid rgba(92, 220, 255, 0.15);
  border-radius: 10px;
  overflow: hidden;
}

.metric-bar {
  position: absolute;
  left: 0;
  top: 8px;
  bottom: 8px;
  width: 3px;
  border-radius: 0 2px 2px 0;
}

.metric-tile--orange .metric-bar { background: var(--accent-orange); box-shadow: 0 0 8px var(--accent-orange); }
.metric-tile--cyan .metric-bar { background: var(--accent-cyan); box-shadow: 0 0 8px var(--accent-cyan); }
.metric-tile--violet .metric-bar { background: var(--accent-violet); box-shadow: 0 0 8px var(--accent-violet); }
.metric-tile--green .metric-bar { background: var(--accent-green); box-shadow: 0 0 8px var(--accent-green); }

.metric-icon {
  font-size: 20px;
  width: 32px;
  height: 32px;
  display: grid;
  place-items: center;
  background: rgba(92, 220, 255, 0.1);
  border-radius: 8px;
  flex-shrink: 0;
}

.metric-body {
  display: flex;
  flex-direction: column;
  min-width: 0;
  flex: 1;
}

.metric-label {
  font-size: 10px;
  letter-spacing: 1px;
  color: var(--text-muted);
  font-weight: 600;
}

.metric-value {
  font-size: 20px;
  font-weight: 700;
  color: #ffffff;
  font-variant-numeric: tabular-nums;
  line-height: 1.1;
  margin-top: 2px;
}

.metric-value em {
  font-style: normal;
  font-size: 11px;
  color: var(--text-muted);
  margin-left: 4px;
  font-weight: 500;
}

.metric-trend {
  font-size: 10px;
  margin-top: 2px;
  letter-spacing: 1px;
}

.metric-trend.is-high { color: var(--accent-red); }
.metric-trend.is-warn { color: var(--accent-amber); }
.metric-trend.is-ok { color: var(--accent-green); }

/* 趋势 */
.popup-trend {
  display: flex;
  flex-direction: column;
}

.popup-trend-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 8px;
}

.popup-trend-header h4 {
  margin: 0;
  font-size: 12px;
  letter-spacing: 2px;
  color: var(--accent-cyan);
  font-weight: 700;
}

.popup-trend :deep(.chart-box) {
  height: 120px;
}

/* 参数 */
.popup-params h4 {
  margin: 0 0 8px;
  font-size: 12px;
  letter-spacing: 2px;
  color: var(--accent-cyan);
  font-weight: 700;
}

.params-grid {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 6px;
}

.param-row {
  display: flex;
  justify-content: space-between;
  padding: 5px 10px;
  background: rgba(13, 37, 64, 0.5);
  border: 1px solid rgba(92, 220, 255, 0.08);
  border-radius: 5px;
}

.param-row span {
  font-size: 10px;
  color: var(--text-muted);
  letter-spacing: 1px;
}

.param-row strong {
  font-size: 11px;
  color: var(--text-primary);
  font-weight: 600;
}

/* 底部 */
.popup-footer {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 10px 18px;
  border-top: 1px solid rgba(92, 220, 255, 0.18);
  background: rgba(13, 37, 64, 0.4);
}

.footer-marker {
  font-size: 10px;
  color: var(--text-muted);
  letter-spacing: 2px;
  font-family: 'SF Mono', Consolas, monospace;
}

.footer-actions {
  display: flex;
  gap: 8px;
}

/* Vue Transition */
.popup-enter-active,
.popup-leave-active {
  transition: opacity 0.3s ease;
}

.popup-enter-active .device-popup,
.popup-leave-active .device-popup {
  transition: transform 0.4s cubic-bezier(0.34, 1.56, 0.64, 1), opacity 0.3s ease;
}

.popup-enter-from,
.popup-leave-to {
  opacity: 0;
}

.popup-enter-from .device-popup,
.popup-leave-to .device-popup {
  transform: scale(0.85) translateY(20px);
  opacity: 0;
}

@media (max-width: 1024px) {
  .popup-body {
    grid-template-columns: 1fr;
  }
  .popup-3d-section {
    min-height: 280px;
  }
}
</style>