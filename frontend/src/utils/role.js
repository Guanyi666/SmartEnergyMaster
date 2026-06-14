// 归一化角色名：去首尾空格、把所有空白压成下划线、大写
// 防御数据库写入 "HR MANAGER"/"HR_MANAGER "/"hr manager" 等脏数据导致路由死循环
const normalizeRole = (r) => (r == null ? '' : String(r).trim().replace(/\s+/g, '_').toUpperCase())

export const defaultHomeForRole = (role) => {
  const r = normalizeRole(role)

  // 拥有监控大屏权限的成员登录后优先进入大屏
  if (['OPERATOR', 'MANAGER', 'ADMIN'].includes(r)) {
    return '/dashboard'
  }

  return {
    MAINTENANCE_ENGINEER: '/maintenance',
    DEVICE_MANAGER: '/operations/orders',
    HR_MANAGER: '/admin/people'
  }[r] || '/account-settings'
}

// 暴露给 router/layout 做角色判断时也走归一化
export const isRole = (actual, expected) => normalizeRole(actual) === normalizeRole(expected)

export { normalizeRole }
