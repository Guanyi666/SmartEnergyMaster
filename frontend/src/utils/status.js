export const statusMeta = {
  RUNNING: { label: '运行中', color: '#3bff9f' },
  HIGH_LOAD: { label: '高负荷', color: '#ffb347' },
  IDLE: { label: '空转', color: '#5bc0ff' },
  STOPPED: { label: '停机', color: '#94a3b8' },
  OFFLINE: { label: '离线', color: '#64748b' },
  FAULT: { label: '故障待处理', color: '#ff5d5d' },
  MAINTENANCE: { label: '维修中', color: '#ff9f43' }
}

export const priceTierMeta = {
  CRITICAL_PEAK: { label: '尖', color: '#ff5d5d' },
  PEAK: { label: '峰', color: '#ff9f43' },
  FLAT: { label: '平', color: '#5bc0ff' },
  VALLEY: { label: '谷', color: '#3bff9f' },
  DEEP_VALLEY: { label: '深谷', color: '#16c47f' }
}

export const getStatusMeta = (status) => statusMeta[status] || { label: status || '未知', color: '#94a3b8' }
export const getPriceTierMeta = (tier) => priceTierMeta[tier] || { label: tier || '--', color: '#94a3b8' }
