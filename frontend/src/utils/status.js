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

// 故障类型中文映射
export const faultTypeMeta = {
  MECHANICAL_JAM: { label: '机械卡涩', emoji: '🔥' },
  COOLING_INTERRUPT: { label: '冷却中断', emoji: '❄️' },
  ELECTRICAL_OVERLOAD: { label: '电气过载', emoji: '⚡' },
  SENSOR_DRIFT: { label: '传感器漂移', emoji: '📡' },
  BEARING_WEAR: { label: '轴承磨损', emoji: '⚙️' },
  INTERMITTENT_JAM: { label: '间歇性卡涩', emoji: '🔧' }
}
export const getFaultTypeMeta = (ft) => faultTypeMeta[ft] || { label: ft || '未知故障', emoji: '❓' }

// 优先级中文映射
export const priorityMeta = {
  CRITICAL: { label: '紧急', color: '#ff5d5d' },
  HIGH: { label: '高', color: '#ff9f43' },
  MEDIUM: { label: '中', color: '#5bc0ff' },
  LOW: { label: '低', color: '#94a3b8' }
}
export const getPriorityMeta = (p) => priorityMeta[p] || { label: p || '--', color: '#94a3b8' }

// 指派角色中文映射
export const roleMeta = {
  PRIMARY: '主要负责人',
  SECONDARY: '协助人员',
  SUPERVISOR: '监督人员',
  BACKUP: '备用人员'
}
export const getRoleLabel = (r) => roleMeta[r] || r || '--'

// 设备类型中文映射
export const deviceTypeMeta = {
  ARC_FURNACE: '电弧炉',
  PUMP: '循环水泵',
  COMPRESSOR: '空压机',
  LADLE_FURNACE: '钢包精炼炉',
  CONTINUOUS_CASTER: '连铸机',
  DUST_COLLECTOR: '除尘系统'
}
export const getDeviceTypeLabel = (dt) => deviceTypeMeta[dt] || dt || '--'

// 工况/负荷类型中文映射
export const loadTypeMeta = {
  REFINING: '精炼中',
  CASTING: '连铸中',
  DUST_COLLECTION: '除尘运行',
  COOLING_SUPPORT: '冷却供水',
  AIR_SUPPLY: '压缩空气供应',
  STOPPED: '停机',
  IDLE: '空转',
  RUNNING: '运行中',
  HIGH_LOAD: '高负荷'
}
export const getLoadTypeLabel = (lt) => loadTypeMeta[lt] || lt || '--'
