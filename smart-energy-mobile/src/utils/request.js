const BASE_URL = 'http://192.168.x.x:8080/api'

const request = (options) => {
  const token = uni.getStorageSync('token')

  const header = {
    'Content-Type': 'application/json',
  }

  if (token) {
    header['Authorization'] = `Bearer ${token}`
  }

  return new Promise((resolve, reject) => {
    uni.request({
      url: BASE_URL + options.url,
      method: options.method || 'GET',
      data: options.data || {},
      header: { ...header, ...options.header },
      timeout: 15000,
      success: (res) => {
        if (res.statusCode === 401 || res.statusCode === 403) {
          uni.removeStorageSync('token')
          uni.removeStorageSync('userInfo')
          uni.reLaunch({ url: '/pages/login/login' })
          reject(new Error('Authentication failed'))
          return
        }

        if (res.statusCode >= 200 && res.statusCode < 300) {
          resolve(res.data)
        } else {
          uni.showToast({
            title: res.data?.message || `Request failed (${res.statusCode})`,
            icon: 'none',
            duration: 2000,
          })
          reject(res)
        }
      },
      fail: (err) => {
        uni.showToast({
          title: 'Network error, please try again',
          icon: 'none',
          duration: 2000,
        })
        reject(err)
      },
    })
  })
}

export const get = (url, params = {}) => {
  return request({ url, method: 'GET', data: params })
}

export const post = (url, data = {}) => {
  return request({ url, method: 'POST', data })
}

export const put = (url, data = {}) => {
  return request({ url, method: 'PUT', data })
}

export const del = (url, params = {}) => {
  return request({ url, method: 'DELETE', data: params })
}

export default {
  get,
  post,
  put,
  del,
}
