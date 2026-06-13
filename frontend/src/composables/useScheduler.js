import { computed, readonly, shallowRef } from 'vue'
import { getDashboardSummary, getDevices, getForecast, getSensorHistory } from '../api'

const tierByHour = (hour) => {
  if ([10, 11, 18, 19].includes(hour)) return { tier: '尖峰', price: 1.28, color: '#ff5d5d' }
  if ([8, 9, 12, 13, 14, 15, 16, 17, 20, 21].includes(hour)) return { tier: '峰', price: 0.96, color: '#ffb347' }
  if ([0, 1, 2, 3, 4, 5].includes(hour)) return { tier: '谷', price: 0.34, color: '#3bff9f' }
  return { tier: '平', price: 0.62, color: '#52c8ff' }
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
  const suggestions = computed(() => devices.value.slice(0, 5).map((device, index) => ({
    id: device.id,
    deviceName: device.deviceName,
    action: index % 2 ? '降低负荷至 70%' : '转移至低谷时段启动',
    delay: `${index + 1} 小时`,
    saving: Math.round(Number(device.usageKwh || 60) * (0.28 + index * 0.04)),
    status: decisions.value[device.id]
  })))

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
