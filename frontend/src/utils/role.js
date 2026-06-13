// 归一化角色名：去首尾空格、把所有空白压成下划线、大写
// 防御数据库写入 "HR MANAGER"/"HR_MANAGER "/"hr manager" 等脏数据导致路由死循环
const normalizeRole = (r) => (r == null ? '' : String(r).trim().replace(/\s+/g, '_').toUpperCase())

export const defaultHomeForRole = (role) => {
  const r = normalizeRole(role)
  return {
    MAINTENANCE_ENGINEER: '/maintenance',
    DEVICE_MANAGER: '/devices',
    MANAGER: '/admin',
    HR_MANAGER: '/admin/users',
    OPERATOR: '/dashboard',
    ADMIN: '/admin'
  }[r] || '/dashboard'
}

// 暴露给 router/layout 做角色判断时也走归一化
export const isRole = (actual, expected) => normalizeRole(actual) === normalizeRole(expected)

export { normalizeRole }