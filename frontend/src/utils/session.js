export const TOKEN_KEY = 'smart-energy-token'
export const USER_KEY = 'smart-energy-user'

export const clearStoredSession = () => {
  localStorage.removeItem(TOKEN_KEY)
  localStorage.removeItem(USER_KEY)
}

export const readStoredSession = () => {
  const token = localStorage.getItem(TOKEN_KEY) || ''
  const rawUser = localStorage.getItem(USER_KEY)

  if (!token && !rawUser) return { token: '', user: null }

  // Token 与用户信息必须同时存在；任意一项损坏都按失效会话处理。
  if (!token || !rawUser) {
    clearStoredSession()
    return { token: '', user: null }
  }

  try {
    const user = JSON.parse(rawUser)
    if (!user || typeof user !== 'object' || Array.isArray(user)) {
      throw new TypeError('Stored user is not an object')
    }
    return { token, user }
  } catch {
    clearStoredSession()
    return { token: '', user: null }
  }
}
