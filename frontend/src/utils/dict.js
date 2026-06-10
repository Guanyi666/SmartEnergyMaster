// 与后端 KnowledgeGraphService 中的中英对照表保持一致，后续新增枚举值请两端同步更新。

export const DEVICE_LABELS = Object.freeze({
  ARC_FURNACE: '电弧炉',
  PUMP: '循环水泵',
  COMPRESSOR: '空压机'
})

export const FAULT_LABELS = Object.freeze({
  MECHANICAL_JAM: '机械卡涩',
  COOLING_INTERRUPT: '冷却中断',
  BEARING_WEAR: '轴承磨损',
  FURNACE_LINING: '炉衬侵蚀',
  HYDRAULIC_LEAK: '液压泄漏',
  VFD_OVERLOAD: '变频器过载',
  PIPE_BLOCK: '管路堵塞',
  SENSOR_DRIFT: '传感器漂移'
})

// SOP 编号里设备/故障的简写 → 原枚举值，用于重新拼接中文编号
export const DEVICE_CODE_ABBREV = Object.freeze({
  ARC: 'ARC_FURNACE',
  PUMP: 'PUMP',
  COMP: 'COMPRESSOR'
})

export const FAULT_CODE_ABBREV = Object.freeze({
  MECH: 'MECHANICAL_JAM',
  COOL: 'COOLING_INTERRUPT',
  BEAR: 'BEARING_WEAR',
  LINING: 'FURNACE_LINING',
  HYD: 'HYDRAULIC_LEAK',
  VFD: 'VFD_OVERLOAD',
  PIPE: 'PIPE_BLOCK',
  DRIFT: 'SENSOR_DRIFT'
})

export function deviceLabel(code) {
  return DEVICE_LABELS[code] || code || ''
}

export function faultLabel(code) {
  return FAULT_LABELS[code] || code || ''
}

// 把 "SOP-ARC-MECH-001" 转成 "SOP-电弧炉-机械卡涩-001"
export function formatSopCode(code) {
  if (!code) return ''
  const m = String(code).match(/^(SOP)-([A-Z]+)-([A-Z]+)-(\d+)$/)
  if (!m) return code
  const [, prefix, devAbbrev, faultAbbrev, num] = m
  const devCode = DEVICE_CODE_ABBREV[devAbbrev] || devAbbrev
  const faultCode = FAULT_CODE_ABBREV[faultAbbrev] || faultAbbrev
  return prefix + '-' + deviceLabel(devCode) + '-' + faultLabel(faultCode) + '-' + num
}

export const DEVICE_TYPE_OPTIONS = Object.entries(DEVICE_LABELS).map(([value, label]) => ({ value, label }))

// 反查：用户输入可能是中文标签，也可能是英文枚举，统一归一为后端识别的枚举值。
// 未匹配时按原样返回，保留 LIKE 模糊查询能力。
export function resolveDeviceCode(input) {
  if (!input) return ''
  const trimmed = String(input).trim()
  if (DEVICE_LABELS[trimmed]) return trimmed
  for (const [code, label] of Object.entries(DEVICE_LABELS)) {
    if (label === trimmed) return code
  }
  return trimmed
}

export function resolveFaultCode(input) {
  if (!input) return ''
  const trimmed = String(input).trim().toUpperCase()
  if (FAULT_LABELS[trimmed]) return trimmed
  for (const [code, label] of Object.entries(FAULT_LABELS)) {
    if (label === trimmed) return code
  }
  return trimmed
}