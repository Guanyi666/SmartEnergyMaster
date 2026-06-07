import { onBeforeUnmount, onMounted } from 'vue'

export function usePollingTask(task, intervalMs = 5000) {
  let timer = null
  let running = false
  let active = false

  const run = async () => {
    if (!active || running) {
      return
    }

    running = true
    try {
      await task()
    } finally {
      running = false
      if (active) {
        timer = window.setTimeout(run, intervalMs)
      }
    }
  }

  const start = async () => {
    active = true
    window.clearTimeout(timer)
    await run()
  }

  const stop = () => {
    active = false
    window.clearTimeout(timer)
  }

  const handleVisibility = () => {
    if (document.visibilityState === 'visible' && active) {
      window.clearTimeout(timer)
      run()
    }
  }

  onMounted(() => {
    document.addEventListener('visibilitychange', handleVisibility)
  })

  onBeforeUnmount(() => {
    stop()
    document.removeEventListener('visibilitychange', handleVisibility)
  })

  return { start, stop, run }
}
