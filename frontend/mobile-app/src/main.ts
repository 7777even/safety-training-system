import { createSSRApp } from 'vue'
import { createPinia } from 'pinia'
import App from './App.vue'
import { setRequestErrorHandler, setTokenProvider } from './api/request'
import { useUserStore } from './stores'
import './styles/index.less'

/** 创建 uni-app Vue 应用实例。 */
export function createApp() {
  const app = createSSRApp(App)
  const pinia = createPinia()

  app.use(pinia)

  const userStore = useUserStore(pinia)
  setTokenProvider(() => userStore.token)

  let isLoginPromptVisible = false

  /** 统一处理受保护接口的未登录响应，避免业务页面各自处理会话失效。 */
  setRequestErrorHandler((error: unknown): void => {
    const requestError = error as {
      message?: unknown
      response?: { status?: unknown; data?: { message?: unknown } }
    }

    if (requestError.response?.status !== 401 || isLoginPromptVisible) return

    userStore.clearSession()
    isLoginPromptVisible = true
    const responseMessage = requestError.response.data?.message
    const content = typeof responseMessage === 'string' ? responseMessage : '登录已失效，请重新登录。'

    uni.showModal({
      title: '登录提示',
      content,
      confirmText: '去登录',
      cancelText: '取消',
      success: ({ confirm }) => {
        if (confirm) uni.reLaunch({ url: '/pages/auth/LoginPage' })
      },
      complete: () => {
        isLoginPromptVisible = false
      }
    })
  })

  void userStore.restoreSession()

  return { app }
}
