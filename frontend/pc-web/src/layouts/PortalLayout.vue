<script setup lang="ts">
import { computed, ref } from 'vue';
import { useRoute, useRouter } from 'vue-router';
import { LOGIN_ORG_CODE_STORAGE_KEY, LOGIN_ROUTE_PATH } from '@/constants/routes';
import { getAuthorizedPortalNavigation, getDefaultMenuPath, getFirstNestedMenuPath, synchronizeMenuRoutes } from '@/router/admin-dynamic-routes';
import { useUserStore } from '@/stores';
import TopToolbar from '@/layouts/TopToolbar.vue';
import LearnerMessageEntry from '@/components/LearnerMessageEntry.vue';

const userStore = useUserStore();
const router = useRouter();
const route = useRoute();
const loggingOut = ref(false);

/** 管理入口只根据已返回的权限资料显示，不硬编码角色。 */
const defaultMenuPath = computed(() => getDefaultMenuPath(userStore.userInfo) ?? LOGIN_ROUTE_PATH);
const managementEntryPath = computed(() => getFirstNestedMenuPath(userStore.userInfo));
const hasAdminAccess = computed(() => Boolean(managementEntryPath.value));
const portalNavigation = computed(() => getAuthorizedPortalNavigation(userStore.userInfo));
/** 普通账号显示所属机构；平台 superAdmin 保留原用户名展示。 */
const userName = computed(() => {
  const info = userStore.userInfo;
  const name = info?.userName ?? info?.userCode ?? '学员';
  return info?.userCode !== 'superAdmin' && info?.orgName ? `${info.orgName} - ${name}` : name;
});

/** 进入已受路由守卫保护的后台入口，由动态菜单确定默认可访问页面。 */
async function enterAdmin(): Promise<void> {
  if (managementEntryPath.value) {
    await router.push(managementEntryPath.value);
  }
}

/** 退出服务端会话、清空动态菜单路由并跳转登录页。 */
async function logout(): Promise<void> {
  if (loggingOut.value) {
    return;
  }

  loggingOut.value = true;
  const orgCode = globalThis.sessionStorage.getItem(LOGIN_ORG_CODE_STORAGE_KEY);

  try {
    await userStore.logout();
    synchronizeMenuRoutes(router, null);
  } catch {
    // Store 的 finally 会清理本地会话，仍继续跳转至登录页。
  } finally {
    loggingOut.value = false;
    await router.replace({ path: LOGIN_ROUTE_PATH, query: orgCode ? { orgCode } : undefined });
  }
}
</script>

<template>
  <main class="portal-page">
    <TopToolbar :brand-to="defaultMenuPath" :user-name="userName" :nav-items="portalNavigation" :active-path="route.path">
      <LearnerMessageEntry />
      <ElButton v-if="hasAdminAccess" style="margin-left: 0" type="primary" link @click="enterAdmin">管理后台</ElButton>
      <ElButton style="margin-left: 0" :loading="loggingOut" link type="danger" @click="logout">退出登录</ElButton>
    </TopToolbar>

    <section class="portal-content">
      <!-- 二级导航等页面级附加内容由子路由注入，保证与门户页共用同一顶栏与内容内边距。 -->
      <slot />
      <RouterView />
    </section>
  </main>
</template>

<style scoped lang="less">
.portal-page {
  min-height: 100vh;
  background: var(--color-bg-page);
  color: var(--color-text-primary);
}
.portal-content {
  height: calc(100vh - var(--layout-toolbar-height));
  overflow: auto;
  padding: var(--layout-content-padding);
  box-sizing: border-box;
}
</style>
