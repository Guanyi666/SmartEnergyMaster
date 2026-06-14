const LEGACY_TOKEN_KEY = 'smart-energy-token'
const LEGACY_USER_KEY = 'smart-energy-user'

let sessionToken = ''
let sessionUser = null

const stripSensitiveUserFields = (user) => {
  if (!user || typeof user !== 'object' || Array.isArray(user)) {
    return user
  }
  const { token, password, ...safeUser } = user
  return safeUser
}

export const setSessionToken = (token) => {
  sessionToken = token || ''
  if (sessionToken) {
    localStorage.setItem(LEGACY_TOKEN_KEY, sessionToken)
  }
}

export const setSessionUser = (user) => {
  sessionUser = stripSensitiveUserFields(user)
  if (sessionUser) {
    localStorage.setItem(LEGACY_USER_KEY, JSON.stringify(sessionUser))
  }
}

export const getSessionToken = () => sessionToken

const clearLegacyPersistentSession = () => {
  localStorage.removeItem(LEGACY_TOKEN_KEY)
  localStorage.removeItem(LEGACY_USER_KEY)
}

export const clearStoredSession = () => {
  sessionToken = ''
  sessionUser = null
  clearLegacyPersistentSession()
}

export const readStoredSession = () => {
  // 优先从内存读取（当前会话），F5 刷新后回退到 localStorage
  if (sessionToken && sessionUser) {
    return { token: sessionToken, user: sessionUser }
  }
  // 页面刷新后内存丢失，从 localStorage 恢复
  const storedToken = localStorage.getItem(LEGACY_TOKEN_KEY)
  const storedUser = localStorage.getItem(LEGACY_USER_KEY)
  if (storedToken && storedUser) {
    try {
      const user = JSON.parse(storedUser)
      if (user && typeof user === 'object' && !Array.isArray(user)) {
        sessionToken = storedToken
        sessionUser = stripSensitiveUserFields(user)
        return { token: sessionToken, user: sessionUser }
      }
    } catch { /* corrupt data, fall through */ }
  }
  return { token: '', user: null }
}
