<script>
export default {
  onLaunch: function () {
    console.log('App Launch')

    // Auto-login: if token exists, skip login and go straight to workbench
    const token = uni.getStorageSync('token')
    if (token) {
      // Ensure we're on the workbench, not stuck on login page
      const pages = getCurrentPages()
      if (pages.length === 0 || pages[pages.length - 1]?.route === 'pages/login/login') {
        uni.reLaunch({ url: '/pages/index/index' })
      }
    }
  },
  onShow: function () {
    console.log('App Show')
  },
  onHide: function () {
    console.log('App Hide')
  },
}
</script>

<style>
/* Global dark theme reset */
page {
  background-color: #0d1117;
  color: #e6edf3;
  font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', 'PingFang SC',
    'Hiragino Sans GB', 'Microsoft YaHei', sans-serif;
}

view, text, input, button {
  box-sizing: border-box;
}

input {
  outline: none;
  border: none;
}

button {
  padding: 0;
  margin: 0;
}

button::after {
  border: none;
}
</style>
