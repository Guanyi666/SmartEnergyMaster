<template>
  <div ref="containerRef" class="dt-hero">
    <!-- 主 WebGL 画布（底层） -->
    <canvas ref="canvasRef" class="dt-hero__canvas"></canvas>

    <!-- CSS2DRenderer 容器（上一层，承载 3D 跟随标签） -->
    <div ref="labelsLayerRef" class="dt-hero__labels"></div>

    <!-- 加载进度 Loading 蒙层 -->
    <Transition name="hero-fade">
      <div v-if="loading" class="dt-hero__loading">
        <div class="hero-loading__inner">
          <div class="hero-loading__ring"></div>
          <p class="hero-loading__title">DIGITAL TWIN BOOTING</p>
          <p class="hero-loading__sub">正在初始化数字孪生模型 · {{ progress }}%</p>
          <div class="hero-loading__bar">
            <span :style="{ width: progress + '%' }"></span>
          </div>
          <p class="hero-loading__hint">{{ loadingHint }}</p>
        </div>
      </div>
    </Transition>

    <!-- 中央扫描准星 -->
    <div class="dt-hero__crosshair" aria-hidden="true">
      <span></span><span></span><span></span><span></span>
    </div>
  </div>
</template>

<script setup>
/**
 * Dashboard 数字孪生 Hero —— 技术美术级 3D 场景
 * ──────────────────────────────────────────────────────────────
 * 设计目标:像"全息厂房 + 真实金属设备"，而不是玩具集
 *
 *  ① 光照与后期
 *     ‑ Bloom 严格阈值: threshold 0.85 / strength 0.8 / radius 0.3
 *       → 只有 emissiveIntensity 远超 1 的指示灯、扫描线会发光
 *     ‑ 三点照明: 冷暗蓝 Ambient + 暖白 Directional(带阴影) + 局部 PointLight
 *     ‑ IBL: RoomEnvironment + PMREMGenerator → 真实金属反射
 *
 *  ② 厂房空间
 *     ‑ 100×100m 打蜡水泥地 (深碳灰, roughness 0.1, 可镜面反射设备)
 *     ‑ 厂房虚拟外壳 (8 根支撑柱 + 顶部桁架 + 墙体轮廓, 蓝图发光线框)
 *     ‑ 中央科技网格地面 (subtle, 仅做空间感参照, 不参与 Bloom)
 *
 *  ③ 仿真模型
 *     ‑ 分区流水线布局: 左泵房 / 中主炼钢 / 右冷却
 *     ‑ GLTFLoader + DRACOLoader 异步加载, 失败回退组合式 PBR 占位
 *     ‑ 占位模型: 多几何体组合 (Box + Cylinder + Torus + Sphere 拼接)
 *     ‑ 每台设备 Group = [核心模型, 脚下光环底座, CSS2D 标签]
 * ──────────────────────────────────────────────────────────────
 */
import { ref, onMounted, onBeforeUnmount, watch } from 'vue'
import * as THREE from 'three'
import { OrbitControls } from 'three/examples/jsm/controls/OrbitControls.js'
import { EffectComposer } from 'three/examples/jsm/postprocessing/EffectComposer.js'
import { RenderPass } from 'three/examples/jsm/postprocessing/RenderPass.js'
import { UnrealBloomPass } from 'three/examples/jsm/postprocessing/UnrealBloomPass.js'
import { OutputPass } from 'three/examples/jsm/postprocessing/OutputPass.js'
import { CSS2DRenderer, CSS2DObject } from 'three/examples/jsm/renderers/CSS2DRenderer.js'
import { GLTFLoader } from 'three/examples/jsm/loaders/GLTFLoader.js'
import { DRACOLoader } from 'three/examples/jsm/loaders/DRACOLoader.js'
import { RGBELoader } from 'three/examples/jsm/loaders/RGBELoader.js'
import { RoomEnvironment } from 'three/examples/jsm/environments/RoomEnvironment.js'
import gsap from 'gsap'

const props = defineProps({
  devices: { type: Array, default: () => [] },
  highlightDeviceId: { type: [Number, String], default: null }
})
const emit = defineEmits(['device-click', 'ready'])

// ============ DOM 引用 ============
const containerRef = ref(null)
const canvasRef = ref(null)
const labelsLayerRef = ref(null)

// ============ UI 状态 ============
const loading = ref(true)
const progress = ref(0)
const loadingHint = ref('挂载场景...')

// ============ 三维状态 ============
let scene, camera, renderer, composer, bloomPass, labelRenderer
let controls
let raycaster, mouse
let deviceGroup           // 容器: 所有设备 Group
let envMap                // IBL
let frameId = null
let resizeObserver
let clock = new THREE.Clock()
let pmrem
const deviceObjects = []  // [{ root, model, halo, label, cssObj, device, type, ledMaterials }]
let hoveredObject = null

// ============ 设备布局表 ============
// ★ 严格对齐后端 device 表的 6 台真实设备 (deploy/init-sql/init.sql)
// 钢厂工艺流向: PUMP/COMP 公辅 → EAF 主炉 → LF 精炼 → CC 连铸,DC 除尘吸 EAF 烟气
const DEVICE_LAYOUT = {
  // —— 主炼钢线 (中央) ——
  ARC_FURNACE:       { x:  0,  z: -2, rotY:  0,     scale: [2.5, 2.5, 2.5], gltf: '/models/arc_furnace.glb',       zone: 'STEEL CORE'   },
  LADLE_FURNACE:     { x:  6,  z: -4, rotY:  0.20,  scale: [1.6, 1.8, 1.6], gltf: '/models/ladle_furnace.glb',     zone: 'STEEL CORE'   },
  CONTINUOUS_CASTER: { x:  12, z:  1, rotY: -0.25,  scale: [1.4, 2.2, 1.4], gltf: '/models/continuous_caster.glb', zone: 'CASTING BAY'  },

  // —— 公用动力 (左侧) ——
  PUMP:              { x: -9,  z:  3, rotY:  0.30,  scale: [0.7, 0.7, 0.7], gltf: '/models/pump.glb',              zone: 'UTILITY HOUSE'},
  COMPRESSOR:        { x: -7,  z:  6, rotY: -0.40,  scale: [0.7, 0.7, 0.7], gltf: '/models/compressor.glb',        zone: 'UTILITY HOUSE'},

  // —— 环保除尘 (后排) ——
  DUST_COLLECTOR:    { x: -3,  z: -8, rotY:  0.10,  scale: [1.5, 2.4, 1.5], gltf: '/models/dust_collector.glb',    zone: 'ENV STATION'  }
}
const DEVICE_TYPES = Object.keys(DEVICE_LAYOUT)
const DEVICE_LABELS = {
  ARC_FURNACE:       { cn: '电弧炉',     en: 'ARC FURNACE'       },
  LADLE_FURNACE:     { cn: '钢包精炼炉', en: 'LADLE FURNACE'     },
  CONTINUOUS_CASTER: { cn: '连铸机',     en: 'CONTINUOUS CASTER' },
  PUMP:              { cn: '循环水泵',   en: 'CIRC. PUMP'        },
  COMPRESSOR:        { cn: '空压机',     en: 'COMPRESSOR'        },
  DUST_COLLECTOR:    { cn: '除尘系统',   en: 'DUST COLLECTOR'    }
}

// 仅 5 种语义色,只用于 LED/状态环/标签;模型本体不上色
const STATUS_COLOR = {
  RUNNING:     0x3bff9f,
  HIGH_LOAD:   0xffb347,
  IDLE:        0x5cdcff,
  STOPPED:     0x94a3b8,
  OFFLINE:     0x64748b,
  FAULT:       0xff5d5d,
  MAINTENANCE: 0xff7e00
}

// ====================================================================
// 1. 初始化
// ====================================================================
function init () {
  const container = containerRef.value
  const canvas = canvasRef.value
  if (!container || !canvas) return

  loadingHint.value = '初始化渲染器...'
  progress.value = 5

  const rect = container.getBoundingClientRect()

  // —— Scene ——
  scene = new THREE.Scene()
  scene.background = null  // 透明背景, 让 CSS 渐变透出
  // 深色雾,营造工业纵深(配合反射地板效果绝佳)
  scene.fog = new THREE.Fog(0x05080f, 35, 90)

  // —— Camera ——
  camera = new THREE.PerspectiveCamera(40, rect.width / rect.height, 0.1, 500)
  camera.position.set(0, 18, 36)

  // —— Renderer ——
  renderer = new THREE.WebGLRenderer({
    canvas,
    antialias: true,
    alpha: true,
    powerPreference: 'high-performance'
  })
  renderer.setSize(rect.width, rect.height)
  renderer.setPixelRatio(Math.min(window.devicePixelRatio, 2))
  renderer.setClearColor(0x000000, 0)
  // 阴影: 工业柔和阴影
  renderer.shadowMap.enabled = true
  renderer.shadowMap.type = THREE.PCFSoftShadowMap
  // 色调映射: ACES 电影级,关键防过曝
  renderer.toneMapping = THREE.ACESFilmicToneMapping
  renderer.toneMappingExposure = 0.95
  renderer.outputColorSpace = THREE.SRGBColorSpace

  // —— CSS2DRenderer ——
  labelRenderer = new CSS2DRenderer()
  labelRenderer.setSize(rect.width, rect.height)
  labelRenderer.domElement.style.position = 'absolute'
  labelRenderer.domElement.style.top = '0'
  labelRenderer.domElement.style.left = '0'
  labelRenderer.domElement.style.pointerEvents = 'none'
  labelsLayerRef.value.appendChild(labelRenderer.domElement)

  // —— 后期合成器: RenderPass → Bloom(严格阈值) → Output ——
  composer = new EffectComposer(renderer)
  composer.setSize(rect.width, rect.height)
  composer.setPixelRatio(Math.min(window.devicePixelRatio, 2))
  composer.addPass(new RenderPass(scene, camera))
  bloomPass = new UnrealBloomPass(
    new THREE.Vector2(rect.width, rect.height),
    0.80,   // strength  —— 强度温和
    0.30,   // radius    —— 收紧扩散半径
    0.85    // threshold —— 极高阈值: 只有 emissive 远超 1 的元素才会泛光
  )
  composer.addPass(bloomPass)
  composer.addPass(new OutputPass())

  // —— Controls ——
  controls = new OrbitControls(camera, canvas)
  controls.enableDamping = true
  controls.dampingFactor = 0.08
  controls.minDistance = 18
  controls.maxDistance = 58
  controls.maxPolarAngle = Math.PI / 2.05  // 防止穿地板
  controls.minPolarAngle = Math.PI / 7
  controls.autoRotate = true
  controls.autoRotateSpeed = 0.25
  controls.target.set(0, 1.5, 0)

  raycaster = new THREE.Raycaster()
  mouse = new THREE.Vector2()

  // —— PMREM (IBL 生成器) ——
  pmrem = new THREE.PMREMGenerator(renderer)
  pmrem.compileEquirectangularShader()

  loadingHint.value = '生成 IBL 工业环境贴图...'
  progress.value = 18
  setupEnvironment()

  loadingHint.value = '配置 3 点工业光照系统...'
  progress.value = 32
  setupLights()

  loadingHint.value = '铺设反射打蜡水泥地面...'
  progress.value = 46
  setupFloor()

  loadingHint.value = '划分功能分区...'
  progress.value = 58
  setupZoneMarkers()

  deviceGroup = new THREE.Group()
  scene.add(deviceGroup)

  loadingHint.value = '加载工厂设备 (GLTF + Draco)...'
  progress.value = 72
  rebuildDevices()

  loadingHint.value = '铺设设备间管网与汇流...'
  progress.value = 90
  setupPipelines()

  // 事件
  canvas.addEventListener('click', onClick)
  canvas.addEventListener('mousemove', onMouseMove)
  resizeObserver = new ResizeObserver(onResize)
  resizeObserver.observe(container)

  loadingHint.value = '启动主循环...'
  progress.value = 100
  setTimeout(() => {
    loading.value = false
    emit('ready')
  }, 380)

  animate()
}

// ====================================================================
// 2. IBL —— 深色工业环境贴图
// ====================================================================
function setupEnvironment () {
  // RoomEnvironment: three.js 内置程序化"小房间",PMREM 探测后生成柔和的 IBL
  // 这是绝大多数 PBR 场景的最佳基础环境
  const roomEnv = new RoomEnvironment()
  envMap = pmrem.fromScene(roomEnv, 0.04).texture
  scene.environment = envMap

  // 可选: 项目里放 /env/industrial.hdr → 自动覆盖
  try {
    new RGBELoader().load(
      '/env/industrial.hdr',
      (hdr) => {
        hdr.mapping = THREE.EquirectangularReflectionMapping
        const next = pmrem.fromEquirectangular(hdr).texture
        envMap?.dispose?.()
        envMap = next
        scene.environment = envMap
        hdr.dispose()
      },
      undefined,
      () => { /* 静默回退 */ }
    )
  } catch (_) { /* noop */ }
}

// ====================================================================
// 3. 三点工业打光 —— 移除全部刺眼全局强光
// ====================================================================
function setupLights () {
  // ① 环境基调: 冷暗蓝, 极柔和。提供整体可见性
  scene.add(new THREE.AmbientLight(0x1a2b40, 0.55))

  // ② 半球光: 厂房上方冷蓝, 地面反射暖灰
  const hemi = new THREE.HemisphereLight(0x6088b0, 0x141820, 0.35)
  hemi.position.set(0, 30, 0)
  scene.add(hemi)

  // ③ 主光 —— Directional, 模拟厂房高位钠灯/聚光顶照
  //    色温稍暖(0xfff2dc), 强度 1.5, 大范围正交阴影
  const key = new THREE.DirectionalLight(0xfff2dc, 1.55)
  key.position.set(14, 28, 18)
  key.castShadow = true
  key.shadow.mapSize.width = 2048
  key.shadow.mapSize.height = 2048
  key.shadow.camera.near = 0.5
  key.shadow.camera.far = 90
  key.shadow.camera.left = -32
  key.shadow.camera.right = 32
  key.shadow.camera.top = 26
  key.shadow.camera.bottom = -22
  key.shadow.bias = -0.0004
  key.shadow.normalBias = 0.04
  key.shadow.radius = 6  // PCF 软化半径
  scene.add(key)

  // ④ 三盏局部低强度 PointLight 作为分区补光
  //    强度受控, 不会触发 Bloom (距离衰减后亮度低于 0.85)
  const accents = [
    { x: -16, y: 8, z: 0, color: 0x6ec5ff, intensity: 0.6 }, // 左泵房 冷蓝
    { x:   0, y: 8, z: 0, color: 0xffd6a5, intensity: 0.7 }, // 中炼钢 暖橙(低)
    { x:  16, y: 8, z: 0, color: 0x8bf3c3, intensity: 0.55 } // 右冷却 冷绿
  ]
  accents.forEach(({ x, y, z, color, intensity }) => {
    const p = new THREE.PointLight(color, intensity, 22, 1.8)
    p.position.set(x, y, z)
    scene.add(p)
  })

  // ⑤ 微弱补光打亮设备正面(避免侧光后正面漆黑)
  const fill = new THREE.DirectionalLight(0x7090b5, 0.25)
  fill.position.set(-10, 8, -14)
  scene.add(fill)
}

// ====================================================================
// 4. 反射打蜡水泥地
// ====================================================================
function setupFloor () {
  // 主反射地面 —— 100×100m, 深碳灰, 低粗糙度 → 反射上方设备
  const floorGeo = new THREE.PlaneGeometry(100, 100)
  const floorMat = new THREE.MeshStandardMaterial({
    color: 0x050505,
    metalness: 0.65,      // 半金属感, 反射但仍有漫反射
    roughness: 0.10,      // 极低粗糙 → 镜面反射
    envMapIntensity: 1.4
  })
  const floor = new THREE.Mesh(floorGeo, floorMat)
  floor.rotation.x = -Math.PI / 2
  floor.position.y = 0
  floor.receiveShadow = true
  scene.add(floor)

  // 次级亚光地基（避免完全镜面看起来像水面;给地板加一点厚度感）
  const baseGeo = new THREE.PlaneGeometry(100, 100)
  const baseMat = new THREE.MeshStandardMaterial({
    color: 0x0a0e16,
    metalness: 0.0,
    roughness: 0.95,
    transparent: true,
    opacity: 0.55
  })
  const base = new THREE.Mesh(baseGeo, baseMat)
  base.rotation.x = -Math.PI / 2
  base.position.y = -0.005
  scene.add(base)

  // 浅色技术网格 (LineSegments, 不触发 Bloom)
  const gridMain = new THREE.GridHelper(80, 40, 0x1a2840, 0x121822)
  gridMain.material.opacity = 0.45
  gridMain.material.transparent = true
  gridMain.position.y = 0.01
  scene.add(gridMain)

  // 中央 5m 加粗参考网格
  const gridFine = new THREE.GridHelper(20, 20, 0x2a5878, 0x162540)
  gridFine.material.opacity = 0.55
  gridFine.material.transparent = true
  gridFine.position.y = 0.012
  scene.add(gridFine)
}

// ====================================================================
// 5. (已移除) 厂房外壳线框 —— 让视线聚焦在地面网格和设备本身
//    旧版的 8 根支撑柱 + 顶部桁架 + 玻璃墙体已彻底删除
// ====================================================================

// ====================================================================
// 6. 分区标记 —— 3 个功能区平面边框 (适配新错落布局)
// ====================================================================
function setupZoneMarkers () {
  // 4 个功能区(对齐 6 台真实设备)
  const zones = [
    // 左:公用动力区 (PUMP -9,3 + COMPRESSOR -7,6)
    { x: -8, z:  5,  w: 7,  d: 8, color: 0x5cdcff, label: 'UTILITY HOUSE', cn: '公用动力区' },
    // 中:主炼钢区 (ARC_FURNACE 0,-2)
    { x:  0, z: -2,  w: 7,  d: 7, color: 0xff7e00, label: 'STEEL CORE',    cn: '主炼钢区'   },
    // 右:连铸成坯区 (LADLE_FURNACE 6,-4 + CONTINUOUS_CASTER 12,1)
    { x:  9, z: -1,  w: 9,  d: 9, color: 0xff5d3a, label: 'CASTING BAY',   cn: '精炼连铸区' },
    // 后:环保除尘区 (DUST_COLLECTOR -3,-8)
    { x: -3, z: -8,  w: 5,  d: 4, color: 0x8b9ab0, label: 'ENV STATION',   cn: '环保除尘区' }
  ]

  zones.forEach(({ x, z, w, d, color, label, cn }) => {
    const hw = w / 2, hd = d / 2
    // 矩形边框 (LineSegments, 不触发 Bloom)
    const points = [
      new THREE.Vector3(x - hw, 0.06, z - hd), new THREE.Vector3(x + hw, 0.06, z - hd),
      new THREE.Vector3(x + hw, 0.06, z - hd), new THREE.Vector3(x + hw, 0.06, z + hd),
      new THREE.Vector3(x + hw, 0.06, z + hd), new THREE.Vector3(x - hw, 0.06, z + hd),
      new THREE.Vector3(x - hw, 0.06, z + hd), new THREE.Vector3(x - hw, 0.06, z - hd)
    ]
    const geo = new THREE.BufferGeometry().setFromPoints(points)
    const mat = new THREE.LineBasicMaterial({ color, transparent: true, opacity: 0.35 })
    scene.add(new THREE.LineSegments(geo, mat))

    // 4 角折角(亮色)
    const cornerL = 1.2
    const corners = [
      [x - hw, z - hd,  1,  1], [x + hw, z - hd, -1,  1],
      [x - hw, z + hd,  1, -1], [x + hw, z + hd, -1, -1]
    ]
    const cPoints = []
    corners.forEach(([cx, cz, dx, dz]) => {
      cPoints.push(new THREE.Vector3(cx, 0.08, cz), new THREE.Vector3(cx + cornerL * dx, 0.08, cz))
      cPoints.push(new THREE.Vector3(cx, 0.08, cz), new THREE.Vector3(cx, 0.08, cz + cornerL * dz))
    })
    const cGeo = new THREE.BufferGeometry().setFromPoints(cPoints)
    const cMat = new THREE.LineBasicMaterial({ color, transparent: true, opacity: 0.85 })
    scene.add(new THREE.LineSegments(cGeo, cMat))

    // 区域名 CSS2D 浮标 (贴近近边)
    const div = document.createElement('div')
    div.className = 'dt-zone-label'
    div.innerHTML = `<span class="dt-zone-label__en">${label}</span><span class="dt-zone-label__cn">${cn}</span>`
    div.style.setProperty('--zc', `#${color.toString(16).padStart(6, '0')}`)
    const obj = new CSS2DObject(div)
    obj.position.set(x, 0.1, z + hd + 0.2)
    scene.add(obj)
  })
}

// ====================================================================
// 7. GLTF + DRACO 加载基础
// ====================================================================
let _gltfLoader = null
let _dracoLoader = null
function getGltfLoader () {
  if (_gltfLoader) return _gltfLoader
  _dracoLoader = new DRACOLoader()
  _dracoLoader.setDecoderPath('https://www.gstatic.com/draco/v1/decoders/')
  _dracoLoader.setDecoderConfig({ type: 'js' })
  _gltfLoader = new GLTFLoader()
  _gltfLoader.setDRACOLoader(_dracoLoader)
  return _gltfLoader
}

/** GLTF 模型材质升级为 PBR (深灰金属化, 不触发 Bloom) */
function enhanceLoadedPBR (model) {
  model.traverse((child) => {
    if (!child.isMesh) return
    child.castShadow = true
    child.receiveShadow = true
    const old = child.material
    const mat = new THREE.MeshStandardMaterial({
      color: old?.color?.clone() || new THREE.Color(0x5b6470),
      metalness: 0.9,
      roughness: 0.32,
      envMapIntensity: 1.1,
      map: old?.map || null
    })
    if (old?.map) old.map.colorSpace = THREE.SRGBColorSpace
    child.material = mat
  })
}

// ====================================================================
// 8. 设备 Group 构造 —— 关键: 三层结构, 避免 scale 影响承台和光环
//    root (位置 + rotY)
//     ├─ plinth   (水泥承台,不受 scale 影响,尺寸由 layout.scale 决定)
//     ├─ halo     (脚下光环底座,不受 scale 影响)
//     ├─ modelWrap(应用 layout.scale →) 真实模型 / 占位组合
//     └─ cssObj   (CSS2D 跟随标签,Y 随 modelWrap 高度浮动)
// ====================================================================
async function loadDeviceModel (type, layout, device) {
  const [sx, sy, sz] = layout.scale

  // ── 8.1 设备根容器(位置 + 朝向)
  //    ★ y 锚定常量 = 0(贴合水泥承台 Plinth 顶面),设备不浮动不晃动
  const ANCHOR_Y = 0
  const root = new THREE.Group()
  root.position.set(layout.x, ANCHOR_Y, layout.z)
  root.rotation.y = layout.rotY || 0
  root.userData.device = device
  root.userData.deviceType = type
  root.userData.anchorY = ANCHOR_Y  // ← 显式记录,防御性兜底

  // ── 8.2 水泥承台(独立尺寸,贴地不缩放)
  const plinth = buildPlinth(layout.scale)
  root.add(plinth)

  // ── 8.3 脚下选中光环(独立尺寸,贴地不缩放)
  const halo = buildHaloBase(device.status, layout.scale)
  root.add(halo)

  // ── 8.4 模型缩放壳 —— 唯一被差异化 scale 缩放的层
  const modelWrap = new THREE.Group()
  modelWrap.scale.set(sx, sy, sz)
  root.add(modelWrap)

  // ── 8.5 占位组合式机械模型 (GLTF 加载完成前先呈现)
  const placeholder = buildCompositeProcedural(type, device.status)
  placeholder.userData._isPlaceholder = true
  modelWrap.add(placeholder)

  deviceGroup.add(root)

  // ── 8.6 CSS2D 跟随标签 (Y 按 scale 折算到合适高度)
  const { wrap, cssObj } = makeDeviceLabel(type, device)
  cssObj.position.set(0, 5.2 * sy * 0.5 + 0.5, 0) // 标签贴在模型顶部偏上
  root.add(cssObj)

  // ── 8.7 注册到全局
  const entry = {
    root, modelWrap, model: placeholder, halo, plinth,
    label: wrap, cssObj, device, type,
    ledMaterials: collectLEDs(placeholder),
    scale: layout.scale
  }
  deviceObjects.push(entry)

  // 入场动画(缩放整个 root,包含承台)
  root.scale.set(0, 0, 0)
  gsap.to(root.scale, {
    x: 1, y: 1, z: 1,
    duration: 1.0,
    delay: 0.05 + Math.random() * 0.35,
    ease: 'back.out(1.5)'
  })

  // ── 8.8 异步加载真实 GLTF (失败保留组合式占位)
  const url = layout.gltf
  if (!url) return
  try {
    const gltf = await getGltfLoader().loadAsync(url)
    const real = gltf.scene
    enhanceLoadedPBR(real)
    // 等比缩放到目标基准高 3.6m (差异化 scale 在 modelWrap 上额外叠加)
    const box = new THREE.Box3().setFromObject(real)
    const size = new THREE.Vector3()
    box.getSize(size)
    const targetH = 3.6
    const k = targetH / Math.max(size.y, 0.001)
    real.scale.setScalar(k)
    box.setFromObject(real)
    real.position.y -= box.min.y * (real.scale.y / Math.max(box.max.y - box.min.y, 0.001))

    modelWrap.remove(placeholder)
    disposeObject(placeholder)
    modelWrap.add(real)
    entry.model = real
    entry.ledMaterials = []
  } catch (_) {
    // 缺失/离线 — 维持组合式占位
  }
}

// ── 水泥承台(8 边棱柱,深灰低反光,贴地)
//    radius 由设备 XZ-scale 决定 -> 大设备大承台 / 小设备小承台
function buildPlinth (scale) {
  const [sx, , sz] = scale
  const footprint = Math.max(sx, sz)        // 取设备最大水平尺度
  const r = 1.6 * footprint + 0.5           // 承台半径略大于设备脚印
  const grp = new THREE.Group()

  // 主承台 —— 8 边棱柱模拟倒角混凝土平台
  const baseGeo = new THREE.CylinderGeometry(r, r + 0.18, 0.28, 8, 1)
  const baseMat = new THREE.MeshStandardMaterial({
    color: 0x121620,
    roughness: 0.88,   // 高粗糙度 = 哑光水泥
    metalness: 0.18,
    envMapIntensity: 0.4
  })
  const base = new THREE.Mesh(baseGeo, baseMat)
  base.position.y = 0.14
  base.receiveShadow = true
  base.castShadow = true
  grp.add(base)

  // 承台顶部 4 角螺栓帽 (体现工业紧固件细节)
  const boltMat = new THREE.MeshStandardMaterial({
    color: 0x2a2f3a, metalness: 0.9, roughness: 0.4
  })
  ;[0, Math.PI / 2, Math.PI, Math.PI * 1.5].forEach(a => {
    const bolt = new THREE.Mesh(
      new THREE.CylinderGeometry(0.07, 0.07, 0.06, 8),
      boltMat
    )
    bolt.position.set(Math.cos(a) * (r - 0.18), 0.31, Math.sin(a) * (r - 0.18))
    grp.add(bolt)
  })

  // 承台顶部薄装饰沟槽 (Inset 一层薄圆盘)
  const grooveGeo = new THREE.CylinderGeometry(r - 0.12, r - 0.12, 0.015, 32)
  const grooveMat = new THREE.MeshStandardMaterial({
    color: 0x080a10, roughness: 0.95, metalness: 0.1
  })
  const groove = new THREE.Mesh(grooveGeo, grooveMat)
  groove.position.y = 0.292
  grp.add(groove)

  // 承台台阶细节(底部多一层略大的延伸)
  const stepGeo = new THREE.CylinderGeometry(r + 0.25, r + 0.32, 0.06, 8)
  const stepMat = new THREE.MeshStandardMaterial({
    color: 0x0a0d14, roughness: 0.9, metalness: 0.15
  })
  const step = new THREE.Mesh(stepGeo, stepMat)
  step.position.y = 0.03
  step.receiveShadow = true
  grp.add(step)

  grp.userData._plinth = true
  grp.userData._plinthRadius = r
  return grp
}

// ── 脚下光环底座(选中态发光指示,承台之上)
function buildHaloBase (status, scale) {
  const [sx, , sz] = scale
  const footprint = Math.max(sx, sz)
  const r = 1.6 * footprint + 0.5  // 与承台半径一致
  const c = STATUS_COLOR[status] ?? 0x5cdcff
  const grp = new THREE.Group()

  // 装饰金属环 (无 emissive, 不参与 Bloom)
  const ringGeo = new THREE.TorusGeometry(r + 0.05, 0.045, 12, 64)
  const ringMat = new THREE.MeshStandardMaterial({
    color: 0x2a3850, metalness: 0.85, roughness: 0.35, envMapIntensity: 1.0
  })
  const ring = new THREE.Mesh(ringGeo, ringMat)
  ring.rotation.x = -Math.PI / 2
  ring.position.y = 0.32
  grp.add(ring)

  // 发光光环 (MeshBasicMaterial → Bloom 触发,只在选中时被放大变亮)
  const glowGeo = new THREE.RingGeometry(r + 0.08, r + 0.35, 64)
  const glowMat = new THREE.MeshBasicMaterial({
    color: c, transparent: true, opacity: 0.28, side: THREE.DoubleSide
  })
  const glow = new THREE.Mesh(glowGeo, glowMat)
  glow.rotation.x = -Math.PI / 2
  glow.position.y = 0.33
  glow.userData._haloGlow = true
  grp.add(glow)

  grp.userData._halo = true
  grp.userData._statusColor = c
  return grp
}

// 提取占位模型中标记为 LED 的材质,以便状态变更时同步颜色
function collectLEDs (model) {
  const out = []
  model.traverse(o => {
    if (o.isMesh && o.userData._isLED) out.push(o.material)
  })
  return out
}

// ====================================================================
// 9. 组合式占位模型 —— 多几何体拼装 + 深灰 PBR + 少量 LED
//    *** 关键: 模型本体材质 emissiveIntensity=0, 不触发 Bloom ***
//    *** 只有少量 LED MeshBasicMaterial 触发 Bloom ***
// ====================================================================
function pbrMetalDark (color = 0x4a525e, opts = {}) {
  return new THREE.MeshStandardMaterial({
    color, metalness: 0.92, roughness: 0.28, envMapIntensity: 1.15, ...opts
  })
}
function pbrMetalLight (color = 0x6c7480, opts = {}) {
  return new THREE.MeshStandardMaterial({
    color, metalness: 0.88, roughness: 0.42, envMapIntensity: 1.05, ...opts
  })
}
function pbrPainted (color = 0x2a3344, opts = {}) {
  return new THREE.MeshStandardMaterial({
    color, metalness: 0.5, roughness: 0.55, envMapIntensity: 0.85, ...opts
  })
}
function makeLED (color, size = 0.1) {
  // LED = MeshBasicMaterial,不依赖 emissiveIntensity 即可发光
  // 颜色亮度足以越过 bloom threshold 0.85
  const mat = new THREE.MeshBasicMaterial({ color })
  const led = new THREE.Mesh(new THREE.SphereGeometry(size, 12, 12), mat)
  led.userData._isLED = true
  return led
}

function buildCompositeProcedural (type, status) {
  const group = new THREE.Group()
  const accentColor = STATUS_COLOR[status] ?? 0x5cdcff

  if (type === 'ARC_FURNACE') {
    // 底座
    const base = new THREE.Mesh(new THREE.BoxGeometry(3.0, 0.4, 3.0), pbrMetalDark(0x2a3140))
    base.position.y = 0.4; base.castShadow = true; group.add(base)
    // 主体罐
    const body = new THREE.Mesh(new THREE.CylinderGeometry(1.25, 1.35, 2.0, 64), pbrMetalLight(0x5e6772))
    body.position.y = 1.6; body.castShadow = true; group.add(body)
    // 加强环 (Torus)
    for (let i = 0; i < 3; i++) {
      const tor = new THREE.Mesh(new THREE.TorusGeometry(1.32, 0.06, 14, 64), pbrMetalDark(0x161b25))
      tor.rotation.x = Math.PI / 2
      tor.position.y = 0.9 + i * 0.5
      group.add(tor)
    }
    // 锥形顶盖
    const cap = new THREE.Mesh(new THREE.ConeGeometry(1.1, 0.6, 48), pbrPainted(0x4a3018))
    cap.position.y = 2.9; cap.castShadow = true; group.add(cap)
    // 3 根电极
    for (let i = -1; i <= 1; i++) {
      const e = new THREE.Mesh(new THREE.CylinderGeometry(0.10, 0.10, 1.8, 12), pbrMetalDark(0x1c1108))
      e.position.set(i * 0.55, 4.0, 0); e.castShadow = true; group.add(e)
      // 电极顶 LED
      const tip = makeLED(accentColor, 0.12)
      tip.position.set(i * 0.55, 4.95, 0)
      group.add(tip)
    }
    // 出渣口管道 (Torus 弯管)
    const pipe = new THREE.Mesh(new THREE.TorusGeometry(0.55, 0.08, 10, 24, Math.PI), pbrMetalDark(0x1c2330))
    pipe.rotation.z = Math.PI / 2
    pipe.position.set(1.4, 1.2, 0)
    group.add(pipe)
    // 侧面控制面板 + 2 个状态 LED
    const panel = new THREE.Mesh(new THREE.BoxGeometry(0.4, 0.5, 0.06), pbrPainted(0x12182a))
    panel.position.set(0, 1.3, 1.32)
    group.add(panel)
    const l1 = makeLED(0x3bff9f, 0.06); l1.position.set(-0.08, 1.45, 1.36); group.add(l1)
    const l2 = makeLED(accentColor, 0.06); l2.position.set(0.08, 1.45, 1.36); group.add(l2)

  } else if (type === 'PUMP') {
    const base = new THREE.Mesh(new THREE.BoxGeometry(3.0, 0.35, 2.4), pbrMetalDark(0x252a36))
    base.position.y = 0.35; base.castShadow = true; group.add(base)
    // 电机 (大圆柱 + 散热片)
    const motor = new THREE.Mesh(new THREE.CylinderGeometry(0.78, 0.78, 1.6, 32), pbrMetalLight(0x586674))
    motor.rotation.z = Math.PI / 2
    motor.position.set(-1.0, 1.15, 0)
    motor.castShadow = true
    group.add(motor)
    // 散热片
    for (let i = 0; i < 9; i++) {
      const fin = new THREE.Mesh(new THREE.BoxGeometry(0.04, 1.55, 0.85), pbrMetalDark(0x2c3340))
      fin.position.set(-1.6 + i * 0.15, 1.15, 0)
      group.add(fin)
    }
    // 泵壳 (球体)
    const housing = new THREE.Mesh(new THREE.SphereGeometry(0.85, 32, 24), pbrMetalLight(0x4a5260))
    housing.position.set(0.55, 1.15, 0); housing.castShadow = true; group.add(housing)
    // 出口管 (圆柱)
    const outlet = new THREE.Mesh(new THREE.CylinderGeometry(0.25, 0.25, 1.4, 24), pbrMetalDark(0x33404f))
    outlet.position.set(0.55, 2.05, 0)
    group.add(outlet)
    // 出口法兰 (Torus)
    const flange = new THREE.Mesh(new THREE.TorusGeometry(0.3, 0.05, 10, 24), pbrMetalDark(0x161b25))
    flange.position.set(0.55, 2.75, 0)
    group.add(flange)
    // 旋转叶轮 (4 叶, 在 animate 中旋转)
    const impeller = new THREE.Group()
    impeller.position.set(0.55, 1.15, 0)
    for (let i = 0; i < 4; i++) {
      const b = new THREE.Mesh(new THREE.BoxGeometry(0.08, 0.65, 0.4), pbrMetalDark(0x303744))
      b.rotation.y = (i * Math.PI) / 2
      b.position.set(Math.cos((i * Math.PI) / 2) * 0.35, 0, Math.sin((i * Math.PI) / 2) * 0.35)
      impeller.add(b)
    }
    group.add(impeller); group.userData.rotate = impeller
    // 状态 LED
    const led = makeLED(accentColor, 0.1)
    led.position.set(-0.4, 2.05, 0.5)
    group.add(led)

  } else if (type === 'COMPRESSOR') {
    const base = new THREE.Mesh(new THREE.BoxGeometry(3.6, 0.35, 2.0), pbrMetalDark(0x252a36))
    base.position.y = 0.35; base.castShadow = true; group.add(base)
    // 横卧储气罐 (圆柱)
    const tank = new THREE.Mesh(new THREE.CylinderGeometry(0.85, 0.85, 3.0, 48), pbrMetalLight(0x5a6472))
    tank.rotation.z = Math.PI / 2
    tank.position.y = 1.4
    tank.castShadow = true
    group.add(tank)
    // 罐头 (2 个半球)
    ;[-1.5, 1.5].forEach(x => {
      const c = new THREE.Mesh(new THREE.SphereGeometry(0.85, 32, 18), pbrMetalLight(0x4a5260))
      c.position.set(x, 1.4, 0)
      group.add(c)
    })
    // 加强环 (Torus × 4)
    for (let i = 0; i < 4; i++) {
      const r = new THREE.Mesh(new THREE.TorusGeometry(0.86, 0.05, 12, 48), pbrMetalDark(0x1c2330))
      r.rotation.y = Math.PI / 2
      r.position.set(-1.2 + i * 0.8, 1.4, 0)
      group.add(r)
    }
    // 顶部安全阀 (圆柱)
    const valve = new THREE.Mesh(new THREE.CylinderGeometry(0.15, 0.18, 0.5, 16), pbrMetalDark(0x2c3340))
    valve.position.set(0, 2.45, 0)
    group.add(valve)
    // 仪表 (Torus 圆框 + Cylinder 表盘)
    ;[-0.6, 0.6].forEach(x => {
      const dial = new THREE.Mesh(new THREE.CylinderGeometry(0.18, 0.18, 0.05, 24), pbrPainted(0xe8e8e8))
      dial.rotation.x = Math.PI / 2
      dial.position.set(x, 1.7, 0.85)
      group.add(dial)
      const tor = new THREE.Mesh(new THREE.TorusGeometry(0.19, 0.02, 8, 24), pbrMetalDark(0x161b25))
      tor.rotation.x = Math.PI / 2
      tor.position.set(x, 1.7, 0.86)
      group.add(tor)
    })
    // LED 工况指示
    const led = makeLED(accentColor, 0.1)
    led.position.set(0, 1.95, 0.95)
    group.add(led)

  } else if (type === 'LADLE_FURNACE') {
    // 钢包精炼炉 (LF) ——「电弧炉的精炼小弟」:稍小圆罐 + 顶盖 + 3 电极
    const base = new THREE.Mesh(new THREE.BoxGeometry(2.6, 0.4, 2.6), pbrMetalDark(0x2a3140))
    base.position.y = 0.4; base.castShadow = true; group.add(base)
    // 钢包(圆台,上小下大)
    const ladle = new THREE.Mesh(new THREE.CylinderGeometry(0.95, 1.15, 1.7, 48), pbrPainted(0x6a3a1a))
    ladle.position.y = 1.45; ladle.castShadow = true; group.add(ladle)
    // 加强箍
    ;[0.85, 1.35, 1.85].forEach(y => {
      const ring = new THREE.Mesh(new THREE.TorusGeometry(0.97, 0.05, 12, 48), pbrMetalDark(0x1c1208))
      ring.rotation.x = Math.PI / 2
      ring.position.y = y
      group.add(ring)
    })
    // 顶盖 (圆锥+穿孔板)
    const cap = new THREE.Mesh(new THREE.CylinderGeometry(0.85, 0.95, 0.22, 48), pbrMetalLight(0x4a5260))
    cap.position.y = 2.42
    group.add(cap)
    // 3 根精炼电极
    for (let i = -1; i <= 1; i++) {
      const e = new THREE.Mesh(new THREE.CylinderGeometry(0.07, 0.07, 1.4, 12), pbrMetalDark(0x1c1108))
      e.position.set(i * 0.4, 3.22, 0)
      e.castShadow = true
      group.add(e)
      const tip = makeLED(accentColor, 0.10)
      tip.position.set(i * 0.4, 3.95, 0)
      group.add(tip)
    }
    // 侧倾耳轴 (两侧大圆柱表征)
    ;[-1.05, 1.05].forEach(x => {
      const trun = new THREE.Mesh(new THREE.CylinderGeometry(0.18, 0.18, 0.32, 16), pbrMetalDark(0x252a36))
      trun.rotation.z = Math.PI / 2
      trun.position.set(x, 1.4, 0)
      group.add(trun)
    })
    // 控制面板 LED
    const panel = new THREE.Mesh(new THREE.BoxGeometry(0.32, 0.4, 0.05), pbrPainted(0x12182a))
    panel.position.set(0, 1.3, 1.18)
    group.add(panel)
    const ind = makeLED(accentColor, 0.06)
    ind.position.set(0, 1.45, 1.22)
    group.add(ind)

  } else if (type === 'CONTINUOUS_CASTER') {
    // 连铸机 (CC) ——「高塔结晶器 + 弯曲弧形导辊」
    const base = new THREE.Mesh(new THREE.BoxGeometry(2.2, 0.4, 3.6), pbrMetalDark(0x252a36))
    base.position.y = 0.4; base.castShadow = true; group.add(base)
    // 中包(上方方形钢水分配箱)
    const tundish = new THREE.Mesh(new THREE.BoxGeometry(1.4, 0.6, 1.2), pbrPainted(0x80401a))
    tundish.position.set(0, 3.5, -1.0)
    tundish.castShadow = true
    group.add(tundish)
    // 中包浇注口
    const nozzle = new THREE.Mesh(new THREE.CylinderGeometry(0.10, 0.14, 0.5, 12), pbrMetalDark(0x12182a))
    nozzle.position.set(0, 3.05, -1.0)
    group.add(nozzle)
    // 结晶器 (高方塔)
    const mold = new THREE.Mesh(new THREE.BoxGeometry(0.55, 1.6, 0.55), pbrMetalLight(0x586674))
    mold.position.set(0, 2.0, -1.0)
    mold.castShadow = true
    group.add(mold)
    // 弧形导辊段 (12 对辊轮沿弯曲轨迹排列)
    const ROLL_CNT = 12
    for (let i = 0; i < ROLL_CNT; i++) {
      const t = i / (ROLL_CNT - 1)
      // 圆弧: 起点 (0, 1.2, -1) 终点 (0, 0.3, 2.2)
      const ang = t * Math.PI / 2  // 0 → 90°
      const cx = 0
      const cy = 1.2 - Math.sin(ang) * 0.9
      const cz = -1.0 + (1 - Math.cos(ang)) * 3.2
      const roll = new THREE.Mesh(new THREE.CylinderGeometry(0.10, 0.10, 0.8, 16), pbrMetalDark(0x4a5260))
      roll.rotation.z = Math.PI / 2
      roll.position.set(cx, cy, cz)
      group.add(roll)
    }
    // 切割段(末端的火焰切割机)
    const cutter = new THREE.Mesh(new THREE.BoxGeometry(0.5, 0.35, 0.5), pbrMetalDark(0x161b25))
    cutter.position.set(0, 0.62, 1.9)
    group.add(cutter)
    // 切割火焰
    const flame = makeLED(0xff8a30, 0.10)
    flame.position.set(0, 0.45, 2.05)
    group.add(flame)
    // 顶部走道扶手 (4 根立柱)
    ;[[-0.7, -1.0], [0.7, -1.0], [-0.7, 0.5], [0.7, 0.5]].forEach(([px, pz]) => {
      const pole = new THREE.Mesh(new THREE.CylinderGeometry(0.04, 0.04, 2.6, 8), pbrMetalDark(0x252a36))
      pole.position.set(px, 1.7, pz)
      group.add(pole)
    })
    // 状态 LED
    const led = makeLED(accentColor, 0.10)
    led.position.set(0, 3.95, -1.0)
    group.add(led)

  } else if (type === 'DUST_COLLECTOR') {
    // 主除尘系统 (DC) ——「布袋除尘器」: 高方柱仓体 + 顶部多个出风管 + 旋风分离器
    const base = new THREE.Mesh(new THREE.BoxGeometry(2.8, 0.4, 2.4), pbrMetalDark(0x252a36))
    base.position.y = 0.4; base.castShadow = true; group.add(base)
    // 主仓体 (大方柱)
    const housing = new THREE.Mesh(new THREE.BoxGeometry(2.2, 2.6, 1.8), pbrMetalLight(0x4a5260))
    housing.position.y = 1.95
    housing.castShadow = true
    group.add(housing)
    // 仓体顶部斜檐 (4 面坡顶箱)
    const roof = new THREE.Mesh(new THREE.CylinderGeometry(0.05, 1.45, 0.55, 4),
      pbrMetalDark(0x2c3340))
    roof.rotation.y = Math.PI / 4
    roof.position.y = 3.55
    group.add(roof)
    // 顶部 5 根出风管(并排小圆柱表征布袋筒)
    for (let i = -2; i <= 2; i++) {
      const stack = new THREE.Mesh(new THREE.CylinderGeometry(0.15, 0.18, 0.9, 16), pbrMetalDark(0x161b25))
      stack.position.set(i * 0.42, 4.25, 0)
      group.add(stack)
      // 出风口边缘
      const top = new THREE.Mesh(new THREE.TorusGeometry(0.17, 0.025, 8, 16), pbrMetalDark(0x080a10))
      top.rotation.x = Math.PI / 2
      top.position.set(i * 0.42, 4.7, 0)
      group.add(top)
    }
    // 侧面进气管 (左侧大圆柱)
    const inlet = new THREE.Mesh(new THREE.CylinderGeometry(0.38, 0.38, 0.9, 24), pbrPainted(0x5a4020))
    inlet.rotation.z = Math.PI / 2
    inlet.position.set(-1.55, 1.4, 0)
    inlet.castShadow = true
    group.add(inlet)
    // 进气管法兰
    const flange = new THREE.Mesh(new THREE.TorusGeometry(0.4, 0.05, 10, 24), pbrMetalDark(0x161b25))
    flange.rotation.y = Math.PI / 2
    flange.position.set(-1.95, 1.4, 0)
    group.add(flange)
    // 底部灰斗 (倒锥)
    const hopper = new THREE.Mesh(new THREE.ConeGeometry(0.85, 0.6, 4), pbrMetalDark(0x1c2330))
    hopper.rotation.y = Math.PI / 4
    hopper.position.y = 0.4
    hopper.scale.y = -1  // 倒置成漏斗
    group.add(hopper)
    // 检修门 (前面板)
    const door = new THREE.Mesh(new THREE.BoxGeometry(0.5, 0.7, 0.04), pbrPainted(0x12182a))
    door.position.set(0, 1.3, 0.92)
    group.add(door)
    // 状态 LED + 警示灯
    const led1 = makeLED(accentColor, 0.10)
    led1.position.set(0, 3.3, 0.92)
    group.add(led1)
    const led2 = makeLED(0xff8a30, 0.08)
    led2.position.set(0.6, 1.55, 0.92)
    group.add(led2)
  }

  // 受 GLTF 替换前的占位模型 — 所有 mesh 接收阴影
  group.traverse(o => {
    if (o.isMesh) {
      o.castShadow = true
      o.receiveShadow = true
    }
  })
  return group
}

// ====================================================================
// 10. CSS2D 跟随标签
// ====================================================================
function makeDeviceLabel (type, device) {
  const wrap = document.createElement('div')
  wrap.className = 'dt-device-label'
  wrap.innerHTML = `
    <div class="dt-device-label__connector"></div>
    <div class="dt-device-label__card">
      <div class="dt-device-label__corner dt-device-label__corner--tl"></div>
      <div class="dt-device-label__corner dt-device-label__corner--tr"></div>
      <div class="dt-device-label__corner dt-device-label__corner--bl"></div>
      <div class="dt-device-label__corner dt-device-label__corner--br"></div>
      <span class="dt-device-label__status"></span>
      <div class="dt-device-label__text">
        <strong>${DEVICE_LABELS[type]?.cn || type}</strong>
        <em>${DEVICE_LABELS[type]?.en || ''}</em>
      </div>
      <div class="dt-device-label__metric">
        <span class="dt-device-label__metric-label">P</span>
        <span class="dt-device-label__metric-value">${Number(device.usageKwh || 0).toFixed(0)}</span>
      </div>
    </div>
  `
  const cssObj = new CSS2DObject(wrap)
  const c = STATUS_COLOR[device.status] ?? 0x5cdcff
  wrap.style.setProperty('--dot-color', `#${c.toString(16).padStart(6, '0')}`)
  return { wrap, cssObj }
}

function rebuildDevices () {
  if (!deviceGroup) return
  deviceObjects.forEach(({ root }) => {
    deviceGroup.remove(root)
    disposeObject(root)
  })
  deviceObjects.length = 0

  DEVICE_TYPES.forEach(type => {
    const layout = DEVICE_LAYOUT[type]
    const dev = props.devices.find(d => d.deviceType === type) || {
      id: `ph-${type}`,
      deviceName: DEVICE_LABELS[type]?.cn || type,
      deviceCode: type,
      deviceType: type,
      status: 'OFFLINE',
      temperature: 0, pressure: 0, vibration: 0, usageKwh: 0
    }
    loadDeviceModel(type, layout, dev)
  })
}

function disposeObject (obj) {
  obj.traverse(o => {
    if (o.geometry) o.geometry.dispose()
    if (o.material) {
      if (Array.isArray(o.material)) o.material.forEach(m => m.dispose())
      else o.material.dispose()
    }
  })
}

// ====================================================================
// 9.5 设备管网 —— 钢厂真实工艺流向
//     主工艺线: EAF (电弧炉熔炼)  →  LF (钢包精炼)  →  CC (连铸成坯)
//     公辅支线: PUMP (循环水) → EAF  /  COMPRESSOR (压缩空气) → EAF
//     环保支线: EAF → DUST_COLLECTOR (烟气除尘,反向)
// ====================================================================
const PIPE_NETWORK = [
  // —— 主工艺线 (钢水流向): 橙色蒸汽红
  { from: 'ARC_FURNACE',   to: 'LADLE_FURNACE',     color: 0xff7e00, radius: 0.24, glow: true  }, // 钢水包转运
  { from: 'LADLE_FURNACE', to: 'CONTINUOUS_CASTER', color: 0xff5d3a, radius: 0.22, glow: true  }, // 精炼钢水入连铸
  // —— 公辅支线
  { from: 'PUMP',          to: 'ARC_FURNACE',       color: 0x5cdcff, radius: 0.20, glow: true  }, // 循环冷却水
  { from: 'COMPRESSOR',    to: 'ARC_FURNACE',       color: 0xffb347, radius: 0.13, glow: false }, // 压缩空气
  // —— 环保支线: 烟气从 EAF 抽到除尘器
  { from: 'ARC_FURNACE',   to: 'DUST_COLLECTOR',    color: 0x8b9ab0, radius: 0.20, glow: false }  // 烟气抽风
]

function setupPipelines () {
  // 收集各设备承台半径,用于决定管线起止点(不能扎进承台)
  const footprintR = {}
  for (const t of Object.keys(DEVICE_LAYOUT)) {
    const [sx, , sz] = DEVICE_LAYOUT[t].scale
    footprintR[t] = 1.6 * Math.max(sx, sz) + 0.55
  }

  PIPE_NETWORK.forEach(({ from, to, color, radius, glow }) => {
    const A = DEVICE_LAYOUT[from]
    const B = DEVICE_LAYOUT[to]
    if (!A || !B) return
    const pipe = buildPipe(
      new THREE.Vector3(A.x, 0, A.z),
      new THREE.Vector3(B.x, 0, B.z),
      footprintR[from], footprintR[to],
      radius, color, glow
    )
    scene.add(pipe)
  })

  // 中央汇流环 —— 电弧炉承台外圈一圈接驳法兰盘
  const hub = DEVICE_LAYOUT.ARC_FURNACE
  const hubR = footprintR.ARC_FURNACE + 0.04
  const hubRing = new THREE.Mesh(
    new THREE.TorusGeometry(hubR, 0.08, 14, 64),
    new THREE.MeshStandardMaterial({ color: 0x2a313e, metalness: 0.92, roughness: 0.28, envMapIntensity: 1.0 })
  )
  hubRing.rotation.x = -Math.PI / 2
  hubRing.position.set(hub.x, 0.42, hub.z)
  scene.add(hubRing)
}

/** 单根管道 —— CatmullRomCurve3 平滑过渡, 双层结构(外管+顶部发光条) + 等距支撑托架 */
function buildPipe (from, to, fromR, toR, radius, colorHex, withGlow) {
  const grp = new THREE.Group()

  // 1) 计算管道起止点 —— 沿设备连线方向退出承台外缘
  const dir = new THREE.Vector3().subVectors(to, from).normalize()
  const start = from.clone().addScaledVector(dir, fromR + 0.05)
  const end   = to.clone().addScaledVector(dir, -(toR + 0.05))
  const pipeY = 0.42  // 管道中心高度: 离地约 0.42m,贴近地面但不蹭地

  // 2) 控制点 —— 起 / 升 / 中 / 降 / 止,中间略抬高制造跨越感
  const mid = new THREE.Vector3((start.x + end.x) / 2, pipeY + 0.18, (start.z + end.z) / 2)
  const curvePoints = [
    new THREE.Vector3(start.x, pipeY, start.z),
    new THREE.Vector3(start.x + (mid.x - start.x) * 0.45, pipeY + 0.08, start.z + (mid.z - start.z) * 0.45),
    mid,
    new THREE.Vector3(end.x + (mid.x - end.x) * 0.45,     pipeY + 0.08, end.z + (mid.z - end.z) * 0.45),
    new THREE.Vector3(end.x, pipeY, end.z)
  ]
  const curve = new THREE.CatmullRomCurve3(curvePoints, false, 'centripetal', 0.5)
  const tubeSeg = 96

  // 3) 外管 —— 深灰金属 PBR
  const outerGeo = new THREE.TubeGeometry(curve, tubeSeg, radius, 12, false)
  const outerMat = new THREE.MeshStandardMaterial({
    color: 0x3a414c,
    metalness: 0.92,
    roughness: 0.32,
    envMapIntensity: 1.0
  })
  const outer = new THREE.Mesh(outerGeo, outerMat)
  outer.castShadow = true
  outer.receiveShadow = true
  grp.add(outer)

  // 4) 顶部发光条纹 (可选) —— 沿管道顶面铺设细发光带
  if (withGlow) {
    const stripePoints = curvePoints.map(p => new THREE.Vector3(p.x, p.y + radius * 0.95, p.z))
    const stripeCurve = new THREE.CatmullRomCurve3(stripePoints, false, 'centripetal', 0.5)
    const stripeGeo = new THREE.TubeGeometry(stripeCurve, tubeSeg, radius * 0.18, 6, false)
    const stripeMat = new THREE.MeshBasicMaterial({ color: colorHex })
    grp.add(new THREE.Mesh(stripeGeo, stripeMat))
  }

  // 5) 法兰圆环 (起止两端)
  const flangeMat = new THREE.MeshStandardMaterial({
    color: 0x161a22, metalness: 0.95, roughness: 0.32
  })
  ;[
    { pos: start, tangent: curve.getTangent(0)   },
    { pos: end,   tangent: curve.getTangent(1).clone().negate() }
  ].forEach(({ pos, tangent }) => {
    const flange = new THREE.Mesh(
      new THREE.TorusGeometry(radius * 1.55, radius * 0.22, 10, 24),
      flangeMat
    )
    flange.position.copy(pos)
    // 将 torus 朝向管道前进方向 (默认 torus 在 XY 平面, 法线 Z+)
    const lookTarget = pos.clone().add(tangent)
    flange.lookAt(lookTarget)
    grp.add(flange)
  })

  // 6) 支撑托架 —— 每 ~2.5m 一个 U 形钢架
  const totalLen = curve.getLength()
  const bracketCount = Math.max(1, Math.floor(totalLen / 2.5))
  const bracketMat = new THREE.MeshStandardMaterial({
    color: 0x14171f, metalness: 0.7, roughness: 0.55
  })
  for (let i = 1; i < bracketCount; i++) {
    const t = i / bracketCount
    const p = curve.getPoint(t)
    // 立柱 —— 从地面到管道下方
    const colHeight = p.y - 0.02
    const col = new THREE.Mesh(
      new THREE.BoxGeometry(radius * 2.6, colHeight, radius * 1.6),
      bracketMat
    )
    col.position.set(p.x, colHeight / 2, p.z)
    col.castShadow = true
    col.receiveShadow = true
    grp.add(col)
    // U 形托架顶部弧形 —— 用一个细 Torus 一半模拟
    const yoke = new THREE.Mesh(
      new THREE.TorusGeometry(radius * 1.05, radius * 0.16, 8, 20, Math.PI),
      bracketMat
    )
    yoke.position.set(p.x, p.y - 0.02, p.z)
    yoke.rotation.x = Math.PI / 2
    yoke.rotation.z = Math.PI
    grp.add(yoke)
  }

  return grp
}

// ====================================================================
// 11. 高亮 / Raycast
// ====================================================================
function applyHighlight (target) {
  clearHighlight()
  if (!target) return
  hoveredObject = target
  // 抬升 + 放大
  gsap.to(target.root.scale, { x: 1.08, y: 1.08, z: 1.08, duration: 0.3, ease: 'power2.out' })
  // 光环底座变亮 (仅 halo glow,本体不动)
  target.halo.traverse(o => {
    if (o.userData._haloGlow) {
      gsap.to(o.material, { opacity: 0.85, duration: 0.3 })
      gsap.fromTo(o.scale, { x: 0.5, y: 0.5, z: 0.5 }, { x: 1.15, y: 1.15, z: 1.15, duration: 0.5, ease: 'power2.out' })
    }
  })
  // LED 暂时变高强度 (放大尺寸即可触发 Bloom)
  target.ledMaterials.forEach(m => { /* MeshBasicMaterial 本身已亮 */ })
  // 标签高亮
  target.label.classList.add('is-active')
}
function clearHighlight () {
  if (!hoveredObject) return
  gsap.to(hoveredObject.root.scale, { x: 1, y: 1, z: 1, duration: 0.3, ease: 'power2.out' })
  hoveredObject.halo.traverse(o => {
    if (o.userData._haloGlow) {
      gsap.to(o.material, { opacity: 0.32, duration: 0.3 })
      gsap.to(o.scale, { x: 1, y: 1, z: 1, duration: 0.4 })
    }
  })
  hoveredObject.label.classList.remove('is-active')
  hoveredObject = null
}

function pickAt (event) {
  if (!containerRef.value) return null
  const rect = containerRef.value.getBoundingClientRect()
  mouse.x = ((event.clientX - rect.left) / rect.width) * 2 - 1
  mouse.y = -((event.clientY - rect.top) / rect.height) * 2 + 1
  raycaster.setFromCamera(mouse, camera)
  const hits = raycaster.intersectObjects(deviceGroup.children, true)
  if (!hits.length) return null
  let obj = hits[0].object
  while (obj && !obj.userData.device) obj = obj.parent
  if (!obj) return null
  return deviceObjects.find(d => d.root === obj) || null
}

function onClick (event) {
  const target = pickAt(event)
  if (!target) return
  controls.autoRotate = false
  applyHighlight(target)
  gsap.to(controls.target, {
    x: target.root.position.x, y: 1.8, z: target.root.position.z,
    duration: 1.0, ease: 'power3.inOut'
  })
  emit('device-click', target.device)
}
function onMouseMove (event) {
  const target = pickAt(event)
  containerRef.value.style.cursor = target ? 'pointer' : 'default'
}

function onResize () {
  if (!containerRef.value || !renderer || !camera || !composer || !labelRenderer) return
  const rect = containerRef.value.getBoundingClientRect()
  renderer.setSize(rect.width, rect.height)
  composer.setSize(rect.width, rect.height)
  bloomPass.setSize(rect.width, rect.height)
  labelRenderer.setSize(rect.width, rect.height)
  camera.aspect = rect.width / rect.height
  camera.updateProjectionMatrix()
}

// ====================================================================
// 12. 主循环
// ====================================================================
function animate () {
  frameId = requestAnimationFrame(animate)
  const elapsed = clock.getElapsedTime()

  // 设备动画
  deviceObjects.forEach(({ root, model, device, halo, label }) => {
    if (model.userData.rotate) {
      const s = device.status
      if (s === 'RUNNING')        model.userData.rotate.rotation.y += 0.045
      else if (s === 'HIGH_LOAD') model.userData.rotate.rotation.y += 0.07
      else if (s === 'IDLE')      model.userData.rotate.rotation.y += 0.01
    }

    // ★★★ 物理锚定 ★★★
    // 重型工业设备必须稳稳地贴在水泥承台(Plinth)顶面,绝不浮动晃动。
    // 此前的 Math.sin(elapsed) 浮动 + FAULT 抖动已彻底删除。
    // 故障态改为通过 LED 颜色 / 光环颜色 / 标签状态指示,不再使用物理位移。
    // (root.position.y 在 loadDeviceModel 中初始化为 0,此处不再写入 → 永远锚定)

    // 光环呼吸 (合法动效,保留)
    halo.traverse(o => {
      if (o.userData._haloGlow && o !== hoveredObject?.halo) {
        o.material.opacity = 0.22 + 0.10 * (0.5 + 0.5 * Math.sin(elapsed * 1.2 + root.position.x))
      }
    })
    // 标签数值实时更新 (合法动效,保留)
    const valEl = label.querySelector('.dt-device-label__metric-value')
    if (valEl) valEl.textContent = Number(device.usageKwh || 0).toFixed(0)
  })

  controls.update()
  composer.render()
  labelRenderer.render(scene, camera)
}

// ====================================================================
// 13. Vue 生命周期
// ====================================================================
onMounted(() => {
  init()
})
onBeforeUnmount(() => {
  if (frameId) cancelAnimationFrame(frameId)
  if (resizeObserver) resizeObserver.disconnect()
  if (controls) controls.dispose()
  if (renderer) { renderer.dispose(); renderer.forceContextLoss?.() }
  if (composer) composer.dispose?.()
  if (pmrem) pmrem.dispose()
  if (envMap) envMap.dispose?.()
  if (_dracoLoader) _dracoLoader.dispose?.()
  if (scene) scene.traverse(o => {
    if (o.geometry) o.geometry.dispose()
    if (o.material) {
      if (Array.isArray(o.material)) o.material.forEach(m => m.dispose())
      else o.material.dispose()
    }
  })
})

// 设备数据变化
watch(() => props.devices, () => {
  if (!deviceObjects.length) return rebuildDevices()
  deviceObjects.forEach(obj => {
    const fresh = props.devices.find(d => d.deviceType === obj.type)
    if (!fresh) return
    obj.device = fresh
    const c = STATUS_COLOR[fresh.status] ?? 0x5cdcff
    obj.label.style.setProperty('--dot-color', `#${c.toString(16).padStart(6, '0')}`)
    // 同步光环颜色
    obj.halo.traverse(o => {
      if (o.userData._haloGlow) o.material.color.setHex(c)
    })
    // 同步 LED 颜色 (仅对占位模型,真实 GLTF 加载后 ledMaterials 为空)
    obj.ledMaterials.forEach(m => m.color?.setHex(c))
  })
}, { deep: true })

watch(() => props.highlightDeviceId, (id) => {
  if (!id) return clearHighlight()
  const target = deviceObjects.find(d => d.device?.id === id || d.type === id)
  if (target) applyHighlight(target)
})

defineExpose({
  clearHighlight,
  applyHighlightById: (id) => {
    const t = deviceObjects.find(d => d.device?.id === id || d.type === id)
    if (t) applyHighlight(t)
  }
})
</script>

<style scoped>
.dt-hero {
  position: absolute;
  inset: 0;
  width: 100%;
  height: 100%;
  overflow: hidden;
  background:
    radial-gradient(ellipse at 50% 28%, rgba(40, 70, 110, 0.32) 0%, transparent 55%),
    radial-gradient(ellipse at 12% 90%, rgba(255, 126, 0, 0.07) 0%, transparent 50%),
    radial-gradient(ellipse at 88% 12%, rgba(60, 140, 200, 0.14) 0%, transparent 55%),
    linear-gradient(180deg, #03060d 0%, #06091a 50%, #02040a 100%);
}

.dt-hero__canvas {
  position: absolute;
  inset: 0;
  display: block;
  width: 100%;
  height: 100%;
  z-index: 1;
}

.dt-hero__labels {
  position: absolute;
  inset: 0;
  pointer-events: none;
  z-index: 2;
}

/* Loading */
.dt-hero__loading {
  position: absolute;
  inset: 0;
  z-index: 10;
  display: grid;
  place-items: center;
  background: linear-gradient(180deg, rgba(3, 6, 13, 0.96), rgba(2, 4, 10, 0.99));
  backdrop-filter: blur(8px);
}
.hero-loading__inner { position: relative; text-align: center; padding: 28px 48px; }
.hero-loading__ring {
  width: 80px;
  height: 80px;
  border-radius: 50%;
  border: 2px solid rgba(92, 220, 255, 0.18);
  border-top-color: #5cdcff;
  border-right-color: #ff7e00;
  margin: 0 auto 20px;
  animation: hero-spin 1.1s linear infinite;
  box-shadow: 0 0 22px rgba(92, 220, 255, 0.4);
}
@keyframes hero-spin { to { transform: rotate(360deg); } }
.hero-loading__title {
  margin: 0 0 4px;
  font-size: 14px;
  letter-spacing: 6px;
  font-weight: 700;
  color: #ffffff;
  text-shadow: 0 0 14px rgba(92, 220, 255, 0.6);
}
.hero-loading__sub {
  margin: 0 0 18px;
  font-size: 11px;
  letter-spacing: 2px;
  color: #5cdcff;
}
.hero-loading__bar {
  width: 240px;
  height: 4px;
  margin: 0 auto;
  background: rgba(92, 220, 255, 0.12);
  border-radius: 2px;
  overflow: hidden;
  box-shadow: inset 0 0 4px rgba(0, 0, 0, 0.4);
}
.hero-loading__bar span {
  display: block;
  height: 100%;
  background: linear-gradient(90deg, #5cdcff, #ff7e00);
  box-shadow: 0 0 12px #5cdcff;
  border-radius: 2px;
  transition: width 0.25s ease;
}
.hero-loading__hint {
  margin: 14px 0 0;
  font-size: 10px;
  letter-spacing: 2px;
  color: #94a3b8;
  font-family: 'SF Mono', Consolas, monospace;
  min-height: 14px;
}
.hero-fade-enter-active, .hero-fade-leave-active { transition: opacity 0.5s ease; }
.hero-fade-enter-from, .hero-fade-leave-to { opacity: 0; }

/* 中央准星 */
.dt-hero__crosshair {
  position: absolute;
  top: 50%; left: 50%;
  width: 36px; height: 36px;
  transform: translate(-50%, -50%);
  pointer-events: none;
  opacity: 0.4;
  z-index: 3;
}
.dt-hero__crosshair span {
  position: absolute;
  background: #5cdcff;
  box-shadow: 0 0 6px #5cdcff;
}
.dt-hero__crosshair span:nth-child(1) { top: 0; left: 50%; width: 1px; height: 8px; transform: translateX(-50%); }
.dt-hero__crosshair span:nth-child(2) { bottom: 0; left: 50%; width: 1px; height: 8px; transform: translateX(-50%); }
.dt-hero__crosshair span:nth-child(3) { left: 0; top: 50%; width: 8px; height: 1px; transform: translateY(-50%); }
.dt-hero__crosshair span:nth-child(4) { right: 0; top: 50%; width: 8px; height: 1px; transform: translateY(-50%); }
</style>

<!-- ============ 全局 CSS:CSS2DRenderer 元素必须全局可见 ============ -->
<style>
.dt-device-label {
  --dot-color: #5cdcff;
  position: relative;
  pointer-events: none;
  transform: translate(-50%, -100%);
  filter: drop-shadow(0 4px 12px rgba(0, 0, 0, 0.55));
  user-select: none;
}
.dt-device-label__connector {
  position: absolute;
  bottom: -36px;
  left: 50%;
  width: 1px;
  height: 36px;
  background: linear-gradient(180deg, var(--dot-color), transparent);
  transform: translateX(-50%);
  opacity: 0.85;
}
.dt-device-label__connector::after {
  content: '';
  position: absolute;
  bottom: -3px; left: 50%;
  width: 6px; height: 6px;
  background: var(--dot-color);
  border-radius: 50%;
  box-shadow: 0 0 8px var(--dot-color);
  transform: translateX(-50%);
}
.dt-device-label__card {
  position: relative;
  display: inline-flex;
  align-items: center;
  gap: 10px;
  padding: 6px 14px 6px 12px;
  background: rgba(5, 10, 22, 0.82);
  border: 1px solid color-mix(in srgb, var(--dot-color) 50%, transparent);
  border-radius: 6px;
  backdrop-filter: blur(10px);
  -webkit-backdrop-filter: blur(10px);
  box-shadow: 0 0 18px color-mix(in srgb, var(--dot-color) 30%, transparent),
              inset 0 1px 0 rgba(255, 255, 255, 0.06);
  min-width: 130px;
}
.dt-device-label__corner {
  position: absolute;
  width: 8px; height: 8px;
  border-color: var(--dot-color);
  border-style: solid;
}
.dt-device-label__corner--tl { top: -1px; left: -1px; border-width: 1px 0 0 1px; }
.dt-device-label__corner--tr { top: -1px; right: -1px; border-width: 1px 1px 0 0; }
.dt-device-label__corner--bl { bottom: -1px; left: -1px; border-width: 0 0 1px 1px; }
.dt-device-label__corner--br { bottom: -1px; right: -1px; border-width: 0 1px 1px 0; }
.dt-device-label__status {
  width: 8px; height: 8px;
  border-radius: 50%;
  background: var(--dot-color);
  box-shadow: 0 0 6px var(--dot-color);
  animation: dt-label-blink 1.4s ease-in-out infinite;
  flex-shrink: 0;
}
@keyframes dt-label-blink {
  0%, 100% { opacity: 1; transform: scale(1); }
  50% { opacity: 0.55; transform: scale(0.85); }
}
.dt-device-label__text {
  display: flex;
  flex-direction: column;
  line-height: 1.1;
  min-width: 0;
}
.dt-device-label__text strong {
  font-size: 12px;
  letter-spacing: 2px;
  color: #ffffff;
  font-weight: 700;
  white-space: nowrap;
}
.dt-device-label__text em {
  font-size: 8px;
  font-style: normal;
  letter-spacing: 1px;
  color: var(--dot-color);
  font-family: 'Bahnschrift', monospace;
  margin-top: 2px;
}
.dt-device-label__metric {
  display: flex;
  align-items: baseline;
  gap: 3px;
  padding-left: 8px;
  border-left: 1px solid color-mix(in srgb, var(--dot-color) 28%, transparent);
}
.dt-device-label__metric-label {
  font-size: 9px;
  color: #94a3b8;
  letter-spacing: 1px;
  font-weight: 600;
}
.dt-device-label__metric-value {
  font-size: 13px;
  color: var(--dot-color);
  font-weight: 700;
  font-family: 'Bahnschrift', monospace;
  font-variant-numeric: tabular-nums;
  text-shadow: 0 0 6px color-mix(in srgb, var(--dot-color) 60%, transparent);
}
.dt-device-label.is-active .dt-device-label__card {
  border-color: var(--dot-color);
  box-shadow: 0 0 24px color-mix(in srgb, var(--dot-color) 60%, transparent),
              inset 0 0 12px color-mix(in srgb, var(--dot-color) 20%, transparent);
  transform: scale(1.05);
}

/* 分区地面标签 */
.dt-zone-label {
  --zc: #5cdcff;
  pointer-events: none;
  transform: translate(-50%, -50%);
  font-family: 'Bahnschrift', monospace;
  text-align: center;
  user-select: none;
}
.dt-zone-label__en {
  display: block;
  font-size: 11px;
  letter-spacing: 4px;
  font-weight: 700;
  color: var(--zc);
  text-shadow: 0 0 8px color-mix(in srgb, var(--zc) 60%, transparent);
  margin-bottom: 2px;
}
.dt-zone-label__cn {
  display: block;
  font-size: 10px;
  letter-spacing: 3px;
  color: rgba(255, 255, 255, 0.7);
  text-shadow: 0 0 6px color-mix(in srgb, var(--zc) 40%, transparent);
}
</style>
