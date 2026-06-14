import { computed, readonly, shallowRef } from 'vue'
import { getDashboardSummary, getDevices, getForecast, getSensorHistory } from '../api'

const tierByHour = (hour) => {
  // 陕西省大工业电价 1-10kV（2024-2025）
  if ((hour >= 19 && hour < 21) || (hour >= 18 && hour < 20)) return { tier: '尖峰', price: 1.25, color: '#ff5d5d' }
  if (hour >= 23 || hour < 6) return { tier: '谷', price: 0.32, color: '#3bff9f' }
  if ((hour >= 8 && hour < 11) || (hour >= 18 && hour < 23)) return { tier: '峰', price: 0.95, color: '#ffb347' }
  if (hour >= 6 && hour < 8 || hour >= 11 && hour < 18) return { tier: '平', price: 0.60, color: '#52c8ff' }
  return { tier: '平', price: 0.60, color: '#52c8ff' }
}

export function useScheduler() {
  const devices = shallowRef([])
  const summary = shallowRef({})
  const forecast = shallowRef([])
  const history = shallowRef([])
  const decisions = shallowRef({})
  const loading = shallowRef(false)

  const priceHours = computed(() => Array.from({ length: 12 }, (_, index) => {
    const date = new Date(Date.now() + index * 60 * 60 * 1000)
    return { hour: `${String(date.getHours()).padStart(2, '0')}:00`, ...tierByHour(date.getHours()) }
  }))

  const currentPrice = computed(() => priceHours.value[0] || tierByHour(new Date().getHours()))
  const schedules = computed(() => devices.value.map((device, index) => ({
    ...device,
    start: 8 + (index % 4) * 2,
    duration: device.status === 'RUNNING' ? 6 : 3 + (index % 3),
    load: Math.round(Number(device.usageKwh || 40))
  })))
  // 分时电价峰谷差：尖峰 1.28 - 深谷 0.34 = 0.94 元/千瓦时
  const PEAK_VALLEY_DIFF = 0.94
  // 仿真遥测 usageKwh 是采样级归一读数，按机组规模折算为小时负荷（千瓦时/时）
  const FLEET_LOAD_SCALE = 80

  const suggestions = computed(() => devices.value.slice(0, 5).map((device, index) => {
    const delayHours = index + 1
    // 可错峰转移的小时负荷（千瓦时）
    const hourlyLoad = Math.max(Number(device.usageKwh || 0), 30) * FLEET_LOAD_SCALE
    // “降低负荷至 70%”只转移约一半负荷，“转移至低谷”转移全部
    const shiftRatio = index % 2 ? 0.5 : 1
    return {
      id: device.id,
      deviceName: device.deviceName,
      action: index % 2 ? '降低负荷至 70%' : '转移至低谷时段启动',
      delay: `${delayHours} 小时`,
      // 节省电费 = 转移负荷 × 推迟时长 × 峰谷电价差
      saving: Math.round(hourlyLoad * shiftRatio * delayHours * PEAK_VALLEY_DIFF).toLocaleString('zh-CN'),
      status: decisions.value[device.id]
    }
  }))

  const load = async () => {
    loading.value = true
    try {
      const deviceResult = await getDevices({ size: 999 })
      devices.value = deviceResult.records || []
      const focusCode = devices.value[0]?.deviceCode || 'EAF-01'
      const [summaryResult, forecastResult, historyResult] = await Promise.all([
        getDashboardSummary(focusCode),
        getForecast(focusCode),
        getSensorHistory(focusCode, 3)
      ])
      summary.value = summaryResult
      forecast.value = forecastResult || []
      history.value = historyResult || []
    } finally {
      loading.value = false
    }
  }

  const decide = (id, status) => {
    decisions.value = { ...decisions.value, [id]: status }
  }

  return {
    devices: readonly(devices),
    summary: readonly(summary),
    forecast: readonly(forecast),
    history: readonly(history),
    loading: readonly(loading),
    priceHours,
    currentPrice,
    schedules,
    suggestions,
    load,
    decide
  }
}
