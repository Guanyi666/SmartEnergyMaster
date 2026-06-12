export const defaultHomeForRole = (role) => ({
  MAINTENANCE_ENGINEER: '/maintenance',
  DEVICE_MANAGER: '/devices',
  MANAGER: '/admin',
  HR_MANAGER: '/admin/users',
  OPERATOR: '/dashboard',
  ADMIN: '/admin'
}[role] || '/dashboard')
