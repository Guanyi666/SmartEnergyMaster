export const BUILT_IN_ADMIN_ACCOUNT = '2026010001'

export const ROLE_ACCOUNT_MARKERS = {
  ADMIN: '01',
  MANAGER: '02',
  MAINTENANCE_ENGINEER: '03',
  HR_MANAGER: '04',
  OPERATOR: '05',
  DEVICE_MANAGER: '06'
}

export const accountFormatHint = '格式：入职年份 + 身份标识 + 四位顺序号；标识 01管理员、02生产经理、03维修、04人事、05操作员、06设备管理'

export const validateAccount = (username, role) => {
  const account = username?.trim() || ''
  if (!/^[12]\d{5}(?!0000)\d{4}$/.test(account)) return accountFormatHint
  const marker = ROLE_ACCOUNT_MARKERS[role]
  if (!marker || account.slice(4, 6) !== marker) {
    return `账号身份标识与角色不匹配，当前角色应使用 ${marker || '有效的两位标识'}`
  }
  return ''
}
