<template>
  <div ref="containerRef" class="digital-twin-scene">
    <canvas ref="canvasRef" class="tw-canvas"></canvas>
    <div class="tw-overlay">
      <slot name="overlay" />
    </div>
    <div class="tw-hud">
      <span class="hud-tag">WEBGL · THREE.JS</span>
      <span class="hud-tag hud-tag--orange">PARTICLE FLOW · {{ particleCount }}</span>
    </div>
  </div>
</template>

<script setup>
import { ref, onMounted, onBeforeUnmount, watch } from 'vue'
import * as THREE from 'three'
import { OrbitControls } from 'three/examples/jsm/controls/OrbitControls.js'
import gsap from 'gsap'

const props = defineProps({
  devices:        { type: Array, default: () => [] },
  totalUsageKwh:  { type: [Number, String], default: 0 },
  totalCo2:       { type: [Number, String], default: 0 },
  faultCount:     { type: [Number, String], default: 0 },
  highlightDeviceId: { type: [Number, String], default: null }
})
const emit = defineEmits(['device-click', 'device-hover', 'device-positions'])

const containerRef = ref(null)
const canvasRef = ref(null)
const particleCount = ref(0)

// ============ 状态 ============
let scene, camera, renderer, controls
let raycaster, mouse
let deviceGroup, particleSystems = []
let frameId = null
let clock = new THREE.Clock()
const deviceMeshes = []   // [{ mesh, device, originalScale }]
let hoveredMesh = null
let resizeObserver

// ============ 工具 ============
function statusColor (status) {
  return ({
    RUNNING:    0x3bff9f,
    HIGH_LOAD:  0xffb347,
    IDLE:       0x5cdcff,
    STOPPED:    0x94a3b8,
    OFFLINE:    0x64748b,
    FAULT:      0xff5d5d,
    MAINTENANCE:0xff7e00
  })[status] || 0x5cdcff
}

// ============ 初始化 ============
function init () {
  const container = containerRef.value
  const canvas = canvasRef.value
  if (!container || !canvas) return

  // 场景
  scene = new THREE.Scene()
  scene.fog = new THREE.FogExp2(0x050b18, 0.015)

  // 相机
  const rect = container.getBoundingClientRect()
  camera = new THREE.PerspectiveCamera(45, rect.width / rect.height, 0.1, 1000)
  camera.position.set(0, 14, 24)

  // 渲染器
  renderer = new THREE.WebGLRenderer({ canvas, antialias: true, alpha: true })
  renderer.setSize(rect.width, rect.height)
  renderer.setPixelRatio(Math.min(window.devicePixelRatio, 2))
  renderer.shadowMap.enabled = true
  renderer.shadowMap.type = THREE.PCFSoftShadowMap
  renderer.toneMapping = THREE.ACESFilmicToneMapping
  renderer.toneMappingExposure = 1.2
  renderer.outputColorSpace = THREE.SRGBColorSpace

  // 控制器
  controls = new OrbitControls(camera, canvas)
  controls.enableDamping = true
  controls.dampingFactor = 0.06
  controls.minDistance = 12
  controls.maxDistance = 40
  controls.maxPolarAngle = Math.PI / 2.05
  controls.minPolarAngle = Math.PI / 6
  controls.autoRotate = true
  controls.autoRotateSpeed = 0.4
  controls.target.set(0, 1, 0)

  // 光照
  setupLights()

  // 背景地板
  setupFloor()

  // 设备组
  deviceGroup = new THREE.Group()
  scene.add(deviceGroup)

  // 流体管道
  setupPipelines()

  // 背景粒子（灰尘/能量）
  setupAmbientParticles()

  // 拾取
  raycaster = new THREE.Raycaster()
  mouse = new THREE.Vector2()

  // 事件
  canvas.addEventListener('click', onCanvasClick)
  canvas.addEventListener('mousemove', onCanvasMouseMove)
  resizeObserver = new ResizeObserver(onResize)
  resizeObserver.observe(container)

  // 启动动画
  animate()
}

function setupLights () {
  // 环境光
  const ambient = new THREE.AmbientLight(0x404060, 0.3)
  scene.add(ambient)

  // 半球光（天空/地面）
  const hemi = new THREE.HemisphereLight(0x5cdcff, 0x0a1929, 0.4)
  scene.add(hemi)

  // 主方向光（带阴影）
  const dirLight = new THREE.DirectionalLight(0xffffff, 1.0)
  dirLight.position.set(10, 20, 10)
  dirLight.castShadow = true
  dirLight.shadow.mapSize.width = 2048
  dirLight.shadow.mapSize.height = 2048
  dirLight.shadow.camera.near = 0.5
  dirLight.shadow.camera.far = 60
  dirLight.shadow.camera.left = -20
  dirLight.shadow.camera.right = 20
  dirLight.shadow.camera.top = 20
  dirLight.shadow.camera.bottom = -20
  scene.add(dirLight)

  // 青色补光
  const cyanLight = new THREE.PointLight(0x5cdcff, 0.6, 30)
  cyanLight.position.set(-8, 5, 8)
  scene.add(cyanLight)

  // 橙色补光
  const orangeLight = new THREE.PointLight(0xff7e00, 0.5, 25)
  orangeLight.position.set(8, 5, -8)
  scene.add(orangeLight)

  // 绿色补光
  const greenLight = new THREE.PointLight(0x3bff9f, 0.4, 20)
  greenLight.position.set(0, 8, 12)
  scene.add(greenLight)
}

function setupFloor () {
  // 等距地板
  const floorGeo = new THREE.PlaneGeometry(50, 50, 1, 1)
  floorGeo.rotateX(-Math.PI / 2)
  const floorMat = new THREE.MeshStandardMaterial({
    color: 0x0a1929,
    metalness: 0.3,
    roughness: 0.7
  })
  const floor = new THREE.Mesh(floorGeo, floorMat)
  floor.position.y = -0.01
  floor.receiveShadow = true
  scene.add(floor)

  // 网格线
  const gridHelper = new THREE.GridHelper(50, 50, 0x1a4a68, 0x0d2540)
  gridHelper.position.y = 0.01
  gridHelper.material.opacity = 0.5
  gridHelper.material.transparent = true
  scene.add(gridHelper)
  scene.add(gridHelper)

  // 中心光晕圆环（自定义 shader）
  const ringGeo = new THREE.RingGeometry(8, 9, 64)
  const ringMat = new THREE.ShaderMaterial({
    uniforms: {
      uTime: { value: 0 },
      uColor: { value: new THREE.Color(0x5cdcff) }
    },
    vertexShader: `
      varying vec2 vUv;
      void main() {
        vUv = uv;
        gl_Position = projectionMatrix * modelViewMatrix * vec4(position, 1.0);
      }
    `,
    fragmentShader: `
      uniform float uTime;
      uniform vec3 uColor;
      varying vec2 vUv;
      void main() {
        float pulse = 0.5 + 0.5 * sin(uTime * 2.0);
        float alpha = mix(0.3, 0.9, pulse) * (1.0 - abs(vUv.y - 0.5) * 2.0);
        gl_FragColor = vec4(uColor * (1.0 + pulse * 0.5), alpha);
      }
    `,
    transparent: true,
    side: THREE.DoubleSide
  })
  const ring = new THREE.Mesh(ringGeo, ringMat)
  ring.rotation.x = -Math.PI / 2
  ring.position.y = 0.02
  scene.add(ring)
  ring.userData.shader = ringMat

  // ============ 分区边界标记 ============
  setupZoneMarkers()
}

function setupZoneMarkers () {
  // 上排：炼钢一车间（青色）
  createZoneMarker({ x: 0, z: 3, w: 18, h: 4, color: 0x5cdcff, label: '炼钢一车间' })
  // 下排：公用动力站（橙色）
  createZoneMarker({ x: 0, z: -3, w: 18, h: 4, color: 0xff7e00, label: '公用动力站' })
}

function createZoneMarker ({ x, z, w, h, color, label }) {
  // 矩形边界（线框）
  const halfW = w / 2
  const halfH = h / 2
  const points = [
    new THREE.Vector3(x - halfW, 0.05, z - halfH),
    new THREE.Vector3(x + halfW, 0.05, z - halfH),
    new THREE.Vector3(x + halfW, 0.05, z + halfH),
    new THREE.Vector3(x - halfW, 0.05, z + halfH),
    new THREE.Vector3(x - halfW, 0.05, z - halfH)
  ]
  const geo = new THREE.BufferGeometry().setFromPoints(points)
  const mat = new THREE.LineBasicMaterial({
    color,
    transparent: true,
    opacity: 0.6
  })
  const line = new THREE.Line(geo, mat)
  scene.add(line)

  // 半透明填充矩形（自定义 shader）
  const fillGeo = new THREE.PlaneGeometry(w, h)
  fillGeo.rotateX(-Math.PI / 2)
  const fillMat = new THREE.ShaderMaterial({
    uniforms: {
      uColor: { value: new THREE.Color(color) },
      uTime: { value: 0 }
    },
    vertexShader: `
      varying vec2 vUv;
      void main() {
        vUv = uv;
        gl_Position = projectionMatrix * modelViewMatrix * vec4(position, 1.0);
      }
    `,
    fragmentShader: `
      uniform vec3 uColor;
      uniform float uTime;
      varying vec2 vUv;
      void main() {
        vec2 c = vUv - 0.5;
        float d = length(c) * 2.0;
        float edge = smoothstep(0.5, 0.35, d);
        float pulse = 0.5 + 0.5 * sin(uTime * 1.5);
        float a = edge * 0.12 * (0.7 + pulse * 0.3);
        gl_FragColor = vec4(uColor, a);
      }
    `,
    transparent: true,
    side: THREE.DoubleSide,
    depthWrite: false
  })
  const fill = new THREE.Mesh(fillGeo, fillMat)
  fill.position.set(x, 0.03, z)
  scene.add(fill)
  fill.userData.shader = fillMat

  // 角标（4 个角的 L 形装饰）
  const cornerGeo = new THREE.BufferGeometry()
  const cornerSize = 0.4
  const corners = [
    [x - halfW, z - halfH, 1, 1],
    [x + halfW, z - halfH, -1, 1],
    [x + halfW, z + halfH, -1, -1],
    [x - halfW, z + halfH, 1, -1]
  ]
  const cornerPoints = []
  corners.forEach(([cx, cz, dx, dz]) => {
    cornerPoints.push(new THREE.Vector3(cx, 0.06, cz))
    cornerPoints.push(new THREE.Vector3(cx + cornerSize * dx, 0.06, cz))
    cornerPoints.push(new THREE.Vector3(cx, 0.06, cz))
    cornerPoints.push(new THREE.Vector3(cx, 0.06, cz + cornerSize * dz))
  })
  const cGeo = new THREE.BufferGeometry().setFromPoints(cornerPoints)
  const cMat = new THREE.LineBasicMaterial({ color, transparent: true, opacity: 0.9 })
  const cLine = new THREE.LineSegments(cGeo, cMat)
  scene.add(cLine)
}

function setupPipelines () {
  // 主管道（青、橙各一条横管）
  const createPipe = (yPos, color, particles) => {
    const curve = new THREE.LineCurve3(
      new THREE.Vector3(-12, yPos, 0),
      new THREE.Vector3(12, yPos, 0)
    )
    const tubeGeo = new THREE.TubeGeometry(curve, 32, 0.15, 8, false)
    const tubeMat = new THREE.MeshStandardMaterial({
      color: 0x1a4a68,
      metalness: 0.8,
      roughness: 0.3
    })
    const tube = new THREE.Mesh(tubeGeo, tubeMat)
    scene.add(tube)

    // 内部流体粒子
    if (particles) createFlowParticles(curve, color, 80)
  }
  createPipe(4, 0x5cdcff, true)
  createPipe(-4, 0xff7e00, true)
}

function createFlowParticles (curve, colorHex, count) {
  // 流体粒子系统
  const positions = new Float32Array(count * 3)
  const offsets = new Float32Array(count)
  for (let i = 0; i < count; i++) {
    const t = i / count
    const point = curve.getPoint(t)
    positions[i * 3] = point.x
    positions[i * 3 + 1] = point.y + (Math.random() - 0.5) * 0.1
    positions[i * 3 + 2] = point.z + (Math.random() - 0.5) * 0.1
    offsets[i] = t
  }

  const geo = new THREE.BufferGeometry()
  geo.setAttribute('position', new THREE.BufferAttribute(positions, 3))
  geo.setAttribute('offset', new THREE.BufferAttribute(offsets, 1))

  // 自定义 shader 粒子
  const mat = new THREE.ShaderMaterial({
    uniforms: {
      uTime: { value: 0 },
      uColor: { value: new THREE.Color(colorHex) },
      uSize: { value: 8 }
    },
    vertexShader: `
      attribute float offset;
      uniform float uTime;
      uniform float uSize;
      varying float vAlpha;
      void main() {
        float t = mod(offset - uTime * 0.15, 1.0);
        vec3 pos = vec3(mix(-12.0, 12.0, t), position.y, position.z);
        vec4 mvPosition = modelViewMatrix * vec4(pos, 1.0);
        gl_Position = projectionMatrix * mvPosition;
        gl_PointSize = uSize * (1.0 / -mvPosition.z) * (1.0 + sin(t * 6.28) * 0.3);
        vAlpha = sin(t * 3.14);
      }
    `,
    fragmentShader: `
      uniform vec3 uColor;
      varying float vAlpha;
      void main() {
        vec2 c = gl_PointCoord - vec2(0.5);
        float d = length(c);
        if (d > 0.5) discard;
        float a = smoothstep(0.5, 0.0, d) * vAlpha;
        gl_FragColor = vec4(uColor * (1.0 + a * 0.5), a);
      }
    `,
    transparent: true,
    blending: THREE.AdditiveBlending,
    depthWrite: false
  })

  const points = new THREE.Points(geo, mat)
  scene.add(points)
  particleSystems.push(points)
  particleCount.value += count
}

function setupAmbientParticles () {
  // 背景浮尘粒子
  const count = 200
  const positions = new Float32Array(count * 3)
  for (let i = 0; i < count; i++) {
    positions[i * 3] = (Math.random() - 0.5) * 30
    positions[i * 3 + 1] = Math.random() * 15
    positions[i * 3 + 2] = (Math.random() - 0.5) * 30
  }
  const geo = new THREE.BufferGeometry()
  geo.setAttribute('position', new THREE.BufferAttribute(positions, 3))

  const mat = new THREE.PointsMaterial({
    color: 0x5cdcff,
    size: 0.05,
    transparent: true,
    opacity: 0.4,
    blending: THREE.AdditiveBlending,
    depthWrite: false
  })
  const points = new THREE.Points(geo, mat)
  scene.add(points)
  particleSystems.push({ material: mat, update: (t) => {
    geo.attributes.position.array.forEach((_, i) => {
      if (i % 3 === 1) geo.attributes.position.array[i] += 0.005 * Math.sin(t + i)
    })
    geo.attributes.position.needsUpdate = true
  }})

  // 体积光柱（4 个设备上方的光锥）
  setupLightBeams()
}

function setupLightBeams () {
  const beamPositions = [
    { x: -6, z: 3, color: 0xff7e00 },  // 电弧炉
    { x: 0, z: 3, color: 0x5cdcff },   // 水泵
    { x: 6, z: 3, color: 0xffb347 },   // 空压机
    { x: -6, z: -3, color: 0x3bff9f }, // 风机
    { x: 0, z: -3, color: 0xa78bfa },  // 变压器
    { x: 6, z: -3, color: 0xff5d5d }   // 锅炉
  ]

  beamPositions.forEach(({ x, z, color }) => {
    const beamGeo = new THREE.CylinderGeometry(0.05, 1.2, 8, 24, 1, true)
    beamGeo.translate(0, 4, 0)
    const beamMat = new THREE.ShaderMaterial({
      uniforms: {
        uColor: { value: new THREE.Color(color) },
        uTime: { value: 0 }
      },
      vertexShader: `
        varying vec2 vUv;
        varying float vY;
        void main() {
          vUv = uv;
          vY = position.y;
          gl_Position = projectionMatrix * modelViewMatrix * vec4(position, 1.0);
        }
      `,
      fragmentShader: `
        uniform vec3 uColor;
        uniform float uTime;
        varying vec2 vUv;
        varying float vY;
        void main() {
          float pulse = 0.5 + 0.5 * sin(uTime * 1.5 + vY * 0.5);
          float fade = 1.0 - vY / 8.0;
          float a = 0.15 * (0.6 + pulse * 0.4) * fade;
          gl_FragColor = vec4(uColor * (1.0 + pulse * 0.3), a);
        }
      `,
      transparent: true,
      side: THREE.DoubleSide,
      depthWrite: false,
      blending: THREE.AdditiveBlending
    })
    const beam = new THREE.Mesh(beamGeo, beamMat)
    beam.position.set(x, 0, z)
    scene.add(beam)
    beam.userData.shader = beamMat
  })
}

// 投影 3D 点到 2D 屏幕坐标（用于连接线）
function projectToScreen (worldPos) {
  if (!camera || !renderer) return null
  const vector = worldPos.clone()
  vector.project(camera)
  const rect = containerRef.value.getBoundingClientRect()
  return {
    x: (vector.x * 0.5 + 0.5) * rect.width,
    y: (-vector.y * 0.5 + 0.5) * rect.height,
    visible: vector.z < 1
  }
}

// 暴露给父组件：获取设备在屏幕上的位置
function getDeviceScreenPositions () {
  const result = {}
  deviceMeshes.forEach(({ mesh, device }) => {
    const worldPos = new THREE.Vector3()
    mesh.getWorldPosition(worldPos)
    worldPos.y += 3  // 设备顶部位置
    result[device.deviceType] = {
      deviceId: device.id,
      worldPos,
      screenPos: projectToScreen(worldPos)
    }
  })
  return result
}

defineExpose({ getDeviceScreenPositions })

// ============ 设备 3D 模型构造 ============
function buildDeviceModel (type, status, deviceData) {
  const group = new THREE.Group()
  const baseColor = statusColor(status)
  const metalMat = new THREE.MeshStandardMaterial({
    color: 0x9aafca, metalness: 0.85, roughness: 0.35
  })
  const accentMat = new THREE.MeshStandardMaterial({
    color: baseColor, metalness: 0.6, roughness: 0.3, emissive: baseColor, emissiveIntensity: 0.3
  })
  const darkMat = new THREE.MeshStandardMaterial({
    color: 0x2a3850, metalness: 0.7, roughness: 0.5
  })
  const glowMat = new THREE.MeshStandardMaterial({
    color: baseColor, emissive: baseColor, emissiveIntensity: 2.0
  })

  if (type === 'ARC_FURNACE') {
    // 罐体
    const body = new THREE.Mesh(
      new THREE.CylinderGeometry(1.2, 1.2, 2.2, 32),
      metalMat
    )
    body.position.y = 1.2
    body.castShadow = true
    group.add(body)

    // 顶盖
    const cap = new THREE.Mesh(
      new THREE.CylinderGeometry(1.0, 1.2, 0.3, 32),
      new THREE.MeshStandardMaterial({ color: 0xff7e00, metalness: 0.6, roughness: 0.4, emissive: 0xff7e00, emissiveIntensity: 0.5 })
    )
    cap.position.y = 2.5
    cap.castShadow = true
    group.add(cap)

    // 内部炉膛（自定义 shader 发光）
    const mouthGeo = new THREE.CircleGeometry(0.85, 32)
    const mouthMat = new THREE.ShaderMaterial({
      uniforms: {
        uTime: { value: 0 },
        uColor: { value: new THREE.Color(0xff7e00) }
      },
      vertexShader: `
        varying vec2 vUv;
        void main() {
          vUv = uv;
          gl_Position = projectionMatrix * modelViewMatrix * vec4(position, 1.0);
        }
      `,
      fragmentShader: `
        uniform float uTime;
        uniform vec3 uColor;
        varying vec2 vUv;
        void main() {
          vec2 c = vUv - vec2(0.5);
          float d = length(c) * 2.0;
          float pulse = 0.5 + 0.5 * sin(uTime * 3.0);
          float a = (1.0 - d) * (0.6 + pulse * 0.4);
          gl_FragColor = vec4(uColor * (1.0 + pulse), a);
        }
      `,
      transparent: true,
      side: THREE.DoubleSide
    })
    const mouth = new THREE.Mesh(mouthGeo, mouthMat)
    mouth.rotation.x = -Math.PI / 2
    mouth.position.y = 2.65
    mouth.userData.shader = mouthMat
    group.add(mouth)

    // 3 根电极
    for (let i = -1; i <= 1; i++) {
      const electrode = new THREE.Mesh(
        new THREE.CylinderGeometry(0.08, 0.08, 1.5, 8),
        new THREE.MeshStandardMaterial({ color: 0xc46a18, metalness: 0.9, roughness: 0.3 })
      )
      electrode.position.set(i * 0.55, 3.4, 0)
      group.add(electrode)

      const tip = new THREE.Mesh(new THREE.SphereGeometry(0.12, 12, 12), glowMat)
      tip.position.set(i * 0.55, 4.15, 0)
      group.add(tip)
    }

    // 控制面板
    const panel = new THREE.Mesh(new THREE.BoxGeometry(0.4, 0.3, 0.05), darkMat)
    panel.position.set(0, 1.2, 1.22)
    group.add(panel)
  } else if (type === 'PUMP') {
    // 电机
    const motor = new THREE.Mesh(
      new THREE.CylinderGeometry(0.7, 0.7, 1.6, 24),
      new THREE.MeshStandardMaterial({ color: 0x5a8ab8, metalness: 0.85, roughness: 0.3 })
    )
    motor.position.set(-1.0, 0.8, 0)
    motor.castShadow = true
    group.add(motor)

    // 散热片
    for (let i = 0; i < 6; i++) {
      const fin = new THREE.Mesh(
        new THREE.BoxGeometry(0.05, 1.2, 0.8),
        new THREE.MeshStandardMaterial({ color: 0x3a6a98, metalness: 0.7, roughness: 0.4 })
      )
      fin.position.set(-1.0 + (i - 2.5) * 0.18, 0.8, 0)
      group.add(fin)
    }

    // 泵壳（球体）
    const housing = new THREE.Mesh(
      new THREE.SphereGeometry(0.9, 24, 16),
      new THREE.MeshStandardMaterial({ color: 0x3a6a98, metalness: 0.85, roughness: 0.3 })
    )
    housing.position.set(0.4, 1.0, 0)
    housing.castShadow = true
    group.add(housing)

    // 内部旋转叶轮
    const impeller = new THREE.Group()
    impeller.position.set(0.4, 1.0, 0)
    for (let i = 0; i < 4; i++) {
      const blade = new THREE.Mesh(
        new THREE.BoxGeometry(0.1, 0.7, 0.4),
        new THREE.MeshStandardMaterial({ color: 0x5cdcff, metalness: 0.6, roughness: 0.3, emissive: 0x5cdcff, emissiveIntensity: 0.3 })
      )
      blade.rotation.y = (i * Math.PI) / 2
      blade.position.set(Math.cos((i * Math.PI) / 2) * 0.4, 0, Math.sin((i * Math.PI) / 2) * 0.4)
      impeller.add(blade)
    }
    group.add(impeller)
    group.userData.rotate = impeller

    // 出口管
    const outlet = new THREE.Mesh(
      new THREE.CylinderGeometry(0.2, 0.2, 0.8, 16),
      new THREE.MeshStandardMaterial({ color: 0x5cdcff, metalness: 0.7, roughness: 0.4 })
    )
    outlet.position.set(0.4, 0.1, 0)
    group.add(outlet)
  } else if (type === 'COMPRESSOR') {
    // 横卧储气罐
    const tankGeo = new THREE.CylinderGeometry(0.7, 0.7, 3.0, 32)
    tankGeo.rotateZ(Math.PI / 2)
    const tank = new THREE.Mesh(tankGeo, new THREE.MeshStandardMaterial({
      color: 0xd4621a, metalness: 0.85, roughness: 0.35, emissive: 0xff7e00, emissiveIntensity: 0.05
    }))
    tank.position.y = 1.0
    tank.castShadow = true
    group.add(tank)

    // 罐头
    const capL = new THREE.Mesh(new THREE.SphereGeometry(0.7, 24, 16), new THREE.MeshStandardMaterial({ color: 0xff8a30, metalness: 0.85, roughness: 0.35 }))
    capL.position.set(-1.5, 1.0, 0)
    group.add(capL)

    const capR = new THREE.Mesh(new THREE.SphereGeometry(0.7, 24, 16), new THREE.MeshStandardMaterial({ color: 0xff8a30, metalness: 0.85, roughness: 0.35 }))
    capR.position.set(1.5, 1.0, 0)
    group.add(capR)

    // 加强环
    for (let i = 0; i < 3; i++) {
      const ring = new THREE.Mesh(
        new THREE.TorusGeometry(0.71, 0.04, 8, 32),
        new THREE.MeshStandardMaterial({ color: 0x5a2000, metalness: 0.9, roughness: 0.3 })
      )
      ring.rotation.y = Math.PI / 2
      ring.position.set(-1 + i, 1.0, 0)
      group.add(ring)
    }

    // 压力表
    for (let i = 0; i < 2; i++) {
      const gauge = new THREE.Mesh(
        new THREE.CylinderGeometry(0.18, 0.18, 0.05, 16),
        new THREE.MeshStandardMaterial({ color: 0xffffff, metalness: 0.3, roughness: 0.6 })
      )
      gauge.position.set(-1.4, 1.5 - i * 0.5, 0.5)
      gauge.rotation.x = Math.PI / 2
      group.add(gauge)
    }

    // 顶部安全阀
    const valve = new THREE.Mesh(
      new THREE.CylinderGeometry(0.15, 0.15, 0.4, 12),
      new THREE.MeshStandardMaterial({ color: 0x5a3a20, metalness: 0.7, roughness: 0.5 })
    )
    valve.position.set(0, 2.0, 0)
    group.add(valve)
  } else if (type === 'FAN') {
    // 风筒（圆柱）
    const housing = new THREE.Mesh(
      new THREE.CylinderGeometry(1.0, 1.0, 1.6, 24, 1, true),
      new THREE.MeshStandardMaterial({ color: 0x3a9a4a, metalness: 0.7, roughness: 0.4, side: THREE.DoubleSide })
    )
    housing.position.y = 1.0
    housing.castShadow = true
    group.add(housing)

    // 内部旋转叶片组
    const blades = new THREE.Group()
    blades.position.set(0, 1.0, 0)
    for (let i = 0; i < 6; i++) {
      const blade = new THREE.Mesh(
        new THREE.BoxGeometry(0.1, 0.1, 1.5),
        new THREE.MeshStandardMaterial({ color: 0xa0e8a0, metalness: 0.5, roughness: 0.3, emissive: 0x3a9a4a, emissiveIntensity: 0.3 })
      )
      blade.rotation.y = (i * Math.PI) / 3
      blade.rotation.z = 0.3
      blades.add(blade)
    }
    group.add(blades)
    group.userData.rotate = blades

    // 中心轴
    const shaft = new THREE.Mesh(
      new THREE.CylinderGeometry(0.15, 0.15, 2.0, 12),
      new THREE.MeshStandardMaterial({ color: 0x5a5a5a, metalness: 0.85, roughness: 0.3 })
    )
    shaft.position.y = 1.0
    group.add(shaft)

    // 顶部排气口
    const outlet = new THREE.Mesh(
      new THREE.CylinderGeometry(0.3, 0.3, 0.6, 16),
      new THREE.MeshStandardMaterial({ color: 0x3a9a4a, metalness: 0.7, roughness: 0.4 })
    )
    outlet.position.y = 2.3
    group.add(outlet)
  } else if (type === 'TRANSFORMER') {
    // 主箱体
    const body = new THREE.Mesh(
      new THREE.BoxGeometry(2.2, 1.6, 1.4),
      new THREE.MeshStandardMaterial({ color: 0x6a7282, metalness: 0.85, roughness: 0.35 })
    )
    body.position.y = 0.9
    body.castShadow = true
    group.add(body)

    // 散热片（左右各 6 片）
    for (let side of [-1, 1]) {
      for (let i = 0; i < 6; i++) {
        const fin = new THREE.Mesh(
          new THREE.BoxGeometry(0.06, 1.4, 1.2),
          new THREE.MeshStandardMaterial({ color: 0x4a5262, metalness: 0.7, roughness: 0.4 })
        )
        fin.position.set(side * (1.2 + i * 0.1), 0.9, 0)
        group.add(fin)
      }
    }

    // 高压套管
    for (let i = -1; i <= 1; i++) {
      const bushing = new THREE.Mesh(
        new THREE.CylinderGeometry(0.08, 0.12, 1.0, 12),
        new THREE.MeshStandardMaterial({ color: 0xc8a060, metalness: 0.6, roughness: 0.4 })
      )
      bushing.position.set(i * 0.6, 2.2, 0)
      group.add(bushing)

      const ball = new THREE.Mesh(new THREE.SphereGeometry(0.13, 12, 12), glowMat)
      ball.position.set(i * 0.6, 2.8, 0)
      group.add(ball)
    }

    // 油枕
    for (let side of [-1, 1]) {
      const oil = new THREE.Mesh(
        new THREE.CylinderGeometry(0.18, 0.18, 1.4, 12),
        new THREE.MeshStandardMaterial({ color: 0x4a5262, metalness: 0.85, roughness: 0.35 })
      )
      oil.position.set(side * 1.3, 1.1, 0)
      group.add(oil)
    }
  } else if (type === 'BOILER') {
    // 主罐（横卧）
    const tankGeo = new THREE.CylinderGeometry(0.8, 0.8, 2.8, 32)
    tankGeo.rotateZ(Math.PI / 2)
    const tank = new THREE.Mesh(tankGeo, new THREE.MeshStandardMaterial({
      color: 0xb04040, metalness: 0.85, roughness: 0.35
    }))
    tank.position.y = 1.1
    tank.castShadow = true
    group.add(tank)

    // 罐头
    const capL = new THREE.Mesh(new THREE.SphereGeometry(0.8, 24, 16), new THREE.MeshStandardMaterial({ color: 0xd06060, metalness: 0.85, roughness: 0.35 }))
    capL.position.set(-1.4, 1.1, 0)
    group.add(capL)

    const capR = new THREE.Mesh(new THREE.SphereGeometry(0.8, 24, 16), new THREE.MeshStandardMaterial({ color: 0xd06060, metalness: 0.85, roughness: 0.35 }))
    capR.position.set(1.4, 1.1, 0)
    group.add(capR)

    // 加强环
    for (let i = 0; i < 3; i++) {
      const ring = new THREE.Mesh(
        new THREE.TorusGeometry(0.81, 0.05, 8, 32),
        new THREE.MeshStandardMaterial({ color: 0x3a0a0a, metalness: 0.9, roughness: 0.3 })
      )
      ring.rotation.y = Math.PI / 2
      ring.position.set(-0.9 + i * 0.9, 1.1, 0)
      group.add(ring)
    }

    // 顶部安全阀 + 蒸汽管
    const valve = new THREE.Mesh(new THREE.CylinderGeometry(0.15, 0.15, 0.5, 12), new THREE.MeshStandardMaterial({ color: 0x3a1010, metalness: 0.7, roughness: 0.5 }))
    valve.position.set(0, 2.2, 0)
    group.add(valve)

    // 燃烧器接口（带火焰 shader）
    const flameGeo = new THREE.CircleGeometry(0.25, 16)
    const flameMat = new THREE.ShaderMaterial({
      uniforms: {
        uTime: { value: 0 },
        uColor: { value: new THREE.Color(0xff5d5d) }
      },
      vertexShader: `
        varying vec2 vUv;
        void main() {
          vUv = uv;
          gl_Position = projectionMatrix * modelViewMatrix * vec4(position, 1.0);
        }
      `,
      fragmentShader: `
        uniform float uTime;
        uniform vec3 uColor;
        varying vec2 vUv;
        void main() {
          vec2 c = vUv - vec2(0.5);
          float d = length(c) * 2.0;
          float flicker = 0.7 + 0.3 * sin(uTime * 8.0);
          float a = (1.0 - d) * flicker;
          vec3 col = mix(uColor, vec3(1.0, 0.9, 0.3), (1.0 - d));
          gl_FragColor = vec4(col * (1.0 + a), a);
        }
      `,
      transparent: true,
      side: THREE.DoubleSide
    })
    const flame = new THREE.Mesh(flameGeo, flameMat)
    flame.position.set(-1.4, 0.7, 0)
    flame.rotation.y = Math.PI / 2
    flame.userData.shader = flameMat
    group.add(flame)
  }

  // 通用底座
  const base = new THREE.Mesh(
    new THREE.BoxGeometry(2.5, 0.1, 2.5),
    darkMat
  )
  base.position.y = 0.05
  base.castShadow = true
  base.receiveShadow = true
  group.add(base)

  return group
}

// ============ 设备布局 ============
const DEVICE_POSITIONS = {
  furnace:     { x: -6,  z: 3 },
  pump:        { x: 0,   z: 3 },
  compressor:  { x: 6,   z: 3 },
  fan:         { x: -6,  z: -3 },
  transformer: { x: 0,   z: -3 },
  boiler:      { x: 6,   z: -3 }
}

const DEVICE_TYPES = ['ARC_FURNACE', 'PUMP', 'COMPRESSOR', 'FAN', 'TRANSFORMER', 'BOILER']
const TYPE_TO_POS = {
  ARC_FURNACE: 'furnace',
  PUMP:        'pump',
  COMPRESSOR:  'compressor',
  FAN:         'fan',
  TRANSFORMER: 'transformer',
  BOILER:      'boiler'
}

function rebuildDevices () {
  if (!deviceGroup) return
  deviceMeshes.forEach(d => deviceGroup.remove(d.mesh))
  deviceMeshes.length = 0

  for (const type of DEVICE_TYPES) {
    const pos = DEVICE_POSITIONS[TYPE_TO_POS[type]]
    const device = props.devices.find(d => d.deviceType === type) || {
      id: `placeholder-${type}`,
      deviceName: typeLabel(type),
      deviceCode: type,
      deviceType: type,
      status: 'OFFLINE',
      temperature: 0, pressure: 0, vibration: 0, usageKwh: 0
    }
    const status = device.status || 'OFFLINE'
    const model = buildDeviceModel(type, status, device)

    model.position.set(pos.x, 0, pos.z)
    model.userData.device = device
    deviceGroup.add(model)
    deviceMeshes.push({ mesh: model, device, originalY: 0 })

    // 入场动画
    model.scale.set(0, 0, 0)
    gsap.to(model.scale, {
      x: 1, y: 1, z: 1,
      duration: 0.8,
      delay: Math.random() * 0.3,
      ease: 'back.out(1.7)'
    })
  }
}

function typeLabel (type) {
  return {
    ARC_FURNACE: '1号电弧炉', PUMP: '循环水泵', COMPRESSOR: '空压机',
    FAN: '风机', TRANSFORMER: '变压器', BOILER: '锅炉'
  }[type] || type
}

// ============ 交互 ============
function onCanvasClick (event) {
  if (!containerRef.value) return
  const rect = containerRef.value.getBoundingClientRect()
  mouse.x = ((event.clientX - rect.left) / rect.width) * 2 - 1
  mouse.y = -((event.clientY - rect.top) / rect.height) * 2 + 1

  raycaster.setFromCamera(mouse, camera)
  const intersects = raycaster.intersectObjects(deviceGroup.children, true)
  if (intersects.length > 0) {
    let obj = intersects[0].object
    while (obj && !obj.userData.device) obj = obj.parent
    if (obj && obj.userData.device) {
      controls.autoRotate = false
      emit('device-click', obj.userData.device)
      // 飞向目标
      gsap.to(controls.target, {
        x: obj.position.x, y: 1, z: obj.position.z,
        duration: 1, ease: 'power2.inOut'
      })
    }
  }
}

function onCanvasMouseMove (event) {
  if (!containerRef.value) return
  const rect = containerRef.value.getBoundingClientRect()
  mouse.x = ((event.clientX - rect.left) / rect.width) * 2 - 1
  mouse.y = -((event.clientY - rect.top) / rect.height) * 2 + 1

  raycaster.setFromCamera(mouse, camera)
  const intersects = raycaster.intersectObjects(deviceGroup.children, true)
  // 重置上一个悬停
  if (hoveredMesh) {
    hoveredMesh.scale.set(1, 1, 1)
    containerRef.value.style.cursor = 'default'
    hoveredMesh = null
  }
  if (intersects.length > 0) {
    let obj = intersects[0].object
    while (obj && !obj.userData.device) obj = obj.parent
    if (obj && obj.userData.device) {
      hoveredMesh = obj
      gsap.to(obj.scale, { x: 1.1, y: 1.1, z: 1.1, duration: 0.2 })
      containerRef.value.style.cursor = 'pointer'
    }
  }
}

function onResize () {
  if (!containerRef.value || !renderer || !camera) return
  const rect = containerRef.value.getBoundingClientRect()
  renderer.setSize(rect.width, rect.height)
  camera.aspect = rect.width / rect.height
  camera.updateProjectionMatrix()
}

// ============ 动画循环 ============
function animate () {
  frameId = requestAnimationFrame(animate)
  const elapsed = clock.getElapsedTime()

  // 旋转叶轮 / 风机
  deviceMeshes.forEach(({ mesh }) => {
    if (mesh.userData.rotate && mesh.userData.device.status === 'RUNNING') {
      mesh.userData.rotate.rotation.y += 0.05
    } else if (mesh.userData.rotate && mesh.userData.device.status === 'IDLE') {
      mesh.userData.rotate.rotation.y += 0.015
    }
    // 故障抖动
    if (mesh.userData.device.status === 'FAULT') {
      mesh.position.y = Math.sin(elapsed * 20) * 0.05
    } else {
      mesh.position.y = Math.sin(elapsed * 1.5 + mesh.position.x) * 0.05
    }
  })

  // 更新所有粒子 shader uniforms
  particleSystems.forEach(p => {
    if (p.material && p.material.uniforms && p.material.uniforms.uTime) {
      p.material.uniforms.uTime.value = elapsed
    }
    if (p.update) p.update(elapsed)
  })

  // 更新所有自定义 shader
  scene.traverse(obj => {
    if (obj.userData.shader && obj.userData.shader.uniforms && obj.userData.shader.uniforms.uTime) {
      obj.userData.shader.uniforms.uTime.value = elapsed
    }
  })

  controls.update()
  renderer.render(scene, camera)
}

// ============ 生命周期 ============
onMounted(() => {
  init()
  rebuildDevices()
})

watch(() => props.devices, () => {
  rebuildDevices()
}, { deep: true })

// 外部高亮触发
watch(() => props.highlightDeviceId, (newId) => {
  if (!newId) return
  const target = deviceMeshes.find(d => d.device.id === newId || d.device.deviceType === newId)
  if (!target) return
  controls.autoRotate = false
  gsap.to(controls.target, {
    x: target.mesh.position.x, y: 1, z: target.mesh.position.z,
    duration: 0.8, ease: 'power2.inOut'
  })
  gsap.to(target.mesh.scale, { x: 1.15, y: 1.15, z: 1.15, duration: 0.3, yoyo: true, repeat: 1 })
})

onBeforeUnmount(() => {
  if (frameId) cancelAnimationFrame(frameId)
  if (resizeObserver) resizeObserver.disconnect()
  if (controls) controls.dispose()
  if (renderer) {
    renderer.dispose()
    renderer.forceContextLoss?.()
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
})
</script>

<style scoped>
.digital-twin-scene {
  position: relative;
  width: 100%;
  height: 100%;
  min-height: 480px;
  border-radius: 14px;
  overflow: hidden;
  background:
    radial-gradient(ellipse at center, rgba(13, 37, 64, 0.4) 0%, rgba(5, 11, 24, 0.85) 100%);
}

.tw-canvas {
  display: block;
  width: 100%;
  height: 100%;
}

.tw-overlay {
  position: absolute;
  inset: 0;
  pointer-events: none;
}

.tw-hud {
  position: absolute;
  top: 10px;
  right: 12px;
  display: flex;
  flex-direction: column;
  gap: 4px;
  pointer-events: none;
  z-index: 5;
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
</style>