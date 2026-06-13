// 共享工具：状态色 + 格式化

export const STATUS_COLORS = {
  RUNNING:    '#3bff9f',
  HIGH_LOAD:  '#ffb347',
  IDLE:       '#5cdcff',
  STOPPED:    '#94a3b8',
  OFFLINE:    '#64748b',
  FAULT:      '#ff5d5d',
  MAINTENANCE:'#ff7e00'
}

export function statusColor (device) {
  if (!device) return '#64748b'
  return STATUS_COLORS[(device.status || '').toUpperCase()] || '#5cdcff'
}

export function formatNum (n) {
  if (n == null || n === '') return '--'
  const v = Number(n)
  if (Number.isNaN(v)) return '--'
  if (Math.abs(v) >= 1000) return (v / 1000).toFixed(1) + 'k'
  return v.toFixed(1)
}

const STATUS_LABELS = {
  RUNNING: '运行中',
  HIGH_LOAD: '高负荷',
  IDLE: '空转',
  STOPPED: '停机',
  OFFLINE: '离线',
  FAULT: '故障',
  MAINTENANCE: '维修中'
}
export function formatStatus (s) {
  return STATUS_LABELS[s] || s || '未知'
}

export function placeholderDevice (type) {
  return {
    id: `placeholder-${type}`,
    deviceName: type,
    deviceCode: type,
    deviceType: type,
    status: 'OFFLINE',
    temperature: 0,
    pressure: 0,
    vibration: 0,
    usageKwh: 0
  }
}