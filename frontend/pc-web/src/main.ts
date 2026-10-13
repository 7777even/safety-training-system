import { createApp } from 'vue';
import { createPinia } from 'pinia';
import { ElMessage, ElMessageBox } from 'element-plus';
import App from './App.vue';
import router from './router';
import { setRequestErrorHandler, setTokenProvider } from './api/request';
import { LOGIN_ROUTE_PATH } from './constants/routes';
import { getDefaultMenuPath, synchronizeMenuRoutes } from './router/admin-dynamic-routes';
import { useUserStore } from './stores';
import 'element-plus/dist/index.css';
import './styles/index.less';

const app = createApp(App);
const pinia = createPinia();

function disableMessageBoxLockScroll(): void {
  const originalAlert = ElMessageBox.alert.bind(ElMessageBox);
  const originalConfirm = ElMessageBox.confirm.bind(ElMessageBox);
  const originalPrompt = ElMessageBox.prompt.bind(ElMessageBox);
  /** 程序化确认框不继承 ElConfigProvider，统一补充中文默认按钮文案。 */
  const defaultMessageBoxOptions = {
    lockScroll: false,
    confirmButtonText: '确认',
    cancelButtonText: '取消'
  };

  ElMessageBox.alert = ((message, title, options) => originalAlert(message, title, {
    ...defaultMessageBoxOptions,
    ...options
  })) as typeof ElMessageBox.alert;

  ElMessageBox.confirm = ((message, title, options) => originalConfirm(message, title, {
    ...defaultMessageBoxOptions,
    ...options
  })) as typeof ElMessageBox.confirm;

  ElMessageBox.prompt = ((message, title, options) => originalPrompt(message, title, {
    ...defaultMessageBoxOptions,
    ...options
  })) as typeof ElMessageBox.prompt;
}

disableMessageBoxLockScroll();

app.use(pinia);

const userStore = useUserStore(pinia);
setTokenProvider(() => userStore.token);

/** 统一处理接口错误：未登录时清理会话并引导用户重新登录，其余错误展示服务端 message。 */
setRequestErrorHandler((error: unknown) => {
  const requestError = error as {
    code?: unknown;
    message?: unknown;
    response?: { status?: unknown; data?: unknown };
  };
  const responseData = requestError.response?.data as { message?: unknown } | undefined;
  const isUnauthorized = requestError.response?.status === 401 || requestError.code === 401;

  if (isUnauthorized) {
    userStore.clearSession();
    const message = typeof responseData?.message === 'string'
      ? responseData.message
      : typeof requestError.message === 'string' ? requestError.message : '请求失败，请稍后重试。';
    ElMessage.error(message);

    if (router.currentRoute.value.path !== LOGIN_ROUTE_PATH) {
      void router.replace({
        path: LOGIN_ROUTE_PATH,
        query: { redirect: router.currentRoute.value.fullPath }
      });
    }
    return;
  }

  const message = typeof responseData?.message === 'string'
    ? responseData.message
    : typeof requestError.message === 'string' ? requestError.message : '请求失败，请稍后重试。';
  ElMessage.error(message);
});

/** 恢复会话后先注册用户菜单路由，再挂载应用以支持浏览器直接访问菜单地址。 */
async function bootstrapApplication(): Promise<void> {
  await userStore.restoreSession();
  synchronizeMenuRoutes(router, userStore.userInfo);

  app.use(router);
  await router.isReady();

  const currentRoute = router.currentRoute.value;
  if (userStore.token && !currentRoute.matched.some((record) => typeof record.meta.menuId === 'number' || record.meta.m4PublicFlow === true || record.meta.personalFlow === true)) {
    await router.replace(getDefaultMenuPath(userStore.userInfo) ?? LOGIN_ROUTE_PATH);
  }

  if (!userStore.token && currentRoute.path !== LOGIN_ROUTE_PATH) {
    await router.replace(LOGIN_ROUTE_PATH);
  }

  app.mount('#app');
}

void bootstrapApplication();
