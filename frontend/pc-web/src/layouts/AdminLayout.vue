<script setup lang="ts">
import { computed, nextTick, onBeforeUnmount, onMounted, ref, type Component, watch } from 'vue';
import { useRoute, useRouter, type RouteLocationNormalizedLoaded } from 'vue-router';
import { ArrowLeft, ArrowRight, Close, Expand, Fold, Menu } from '@element-plus/icons-vue';
import * as ElementPlusIconsVue from '@element-plus/icons-vue';
import { LOGIN_ORG_CODE_STORAGE_KEY, LOGIN_ROUTE_PATH } from '@/constants/routes';
import { ADMIN_SIDEBAR_COLLAPSED_STORAGE_KEY, ADMIN_TABS_STORAGE_KEY } from '@/constants/ui';
import { findAuthorizedAdminModule, getAuthorizedAdminModules, getDefaultMenuPath, synchronizeMenuRoutes } from '@/router/admin-dynamic-routes';
import { useUserStore } from '@/stores';
import TopToolbar from '@/layouts/TopToolbar.vue';
import AdminSidebarCollapsedMenu from '@/layouts/AdminSidebarCollapsedMenu.vue';
import { normalizeAdminTabs, upsertAdminTab, type AdminTab } from '@/layouts/admin-tab-state';

const route = useRoute();
const router = useRouter();
const userStore = useUserStore();
const loggingOut = ref(false);
/** 侧边栏状态仅在当前浏览器标签会话内保留，关闭标签页后自动恢复默认值。*/
const sidebarCollapsed = ref(globalThis.sessionStorage.getItem(ADMIN_SIDEBAR_COLLAPSED_STORAGE_KEY) === 'true');
const tabs = ref<AdminTab[]>(loadTabs());
const tabScrollContainer = ref<globalThis.HTMLElement | null>(null);
/** 监听页签可视区域宽度，侧栏动画等非窗口尺寸变化也需更新滚动按钮状态。 */
let tabScrollResizeObserver: globalThis.ResizeObserver | undefined;
const canScrollTabsLeft = ref(false);
const canScrollTabsRight = ref(false);
const adminModules = computed(() => getAuthorizedAdminModules(userStore.userInfo));
const activeModule = computed(() => findAuthorizedAdminModule(userStore.userInfo, route.path));
const sidebarNavigation = computed(() => activeModule.value?.children ?? []);
/** 顶部工具栏导航项：以模块路径为索引，点击时进入模块首个可访问页面。 */
const adminModuleNavItems = computed(() =>
  adminModules.value.map((module) => ({
    title: module.title,
    path: module.path,
    target: module.landingPath
  }))
);
const defaultMenuPath = computed(() => getDefaultMenuPath(userStore.userInfo) ?? LOGIN_ROUTE_PATH);
/** 普通账号显示所属机构；平台 superAdmin 保留原用户名展示。 */
const userName = computed(() => {
  const info = userStore.userInfo;
  const name = info?.userName ?? info?.userCode ?? '管理员';
  return info?.userCode !== 'superAdmin' && info?.orgName ? `${info.orgName} -【${name}】` : name;
});
const isOverviewPage = computed(() => route.path === '/admin/overview' || route.path.endsWith('/overview'));
/** 仅资源参数变化时重建路由页面，避免详情页复用旧资源数据，同时保留工作台阶段切换状态。 */
const contentViewKey = computed(() => `${String(route.name ?? route.path)}:${JSON.stringify(route.params)}`);
const breadcrumbs = computed(() =>
  route.matched.filter((record) => typeof record.meta.title === 'string').map((record) => String(record.meta.title))
);

/** 读取当前浏览器会话中的已打开页面，损坏数据时回退为空列表。 */
function loadTabs(): AdminTab[] {
  try {
    const storedTabs = JSON.parse(globalThis.sessionStorage.getItem(ADMIN_TABS_STORAGE_KEY) ?? '[]') as unknown;
    return Array.isArray(storedTabs) ? normalizeAdminTabs(storedTabs.filter(isAdminTab)) : [];
  } catch {
    return [];
  }
}

/** 校验持久化页签的最小结构，避免旧数据破坏布局。 */
function isAdminTab(value: unknown): value is AdminTab {
  if (!value || typeof value !== 'object') {
    return false;
  }

  const tab = value as Partial<AdminTab>;
  return typeof tab.key === 'string' && typeof tab.title === 'string' && typeof tab.path === 'string' && typeof tab.closable === 'boolean';
}

/** 将页签状态保存到当前浏览器，页签不会写入服务端。 */
function persistTabs(): void {
  globalThis.sessionStorage.setItem(ADMIN_TABS_STORAGE_KEY, JSON.stringify(tabs.value));
}

/** Element Plus 菜单使用完整路由路径作为索引。 */
async function navigate(path: string): Promise<void> {
  await router.push(path);
}

/** 直接使用接口 icon 对应的 Element Plus 图标，未知图标回退为菜单图标。 */
function getNavigationIcon(icon: string): Component {
  return (ElementPlusIconsVue[icon as keyof typeof ElementPlusIconsVue] as Component | undefined) ?? Menu;
}

/** 返回当前角色菜单中首个可访问页面。 */
async function returnToPortal(): Promise<void> {
  await router.push(defaultMenuPath.value);
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
    clearAdminUiSession();
    loggingOut.value = false;
    await router.replace({ path: LOGIN_ROUTE_PATH, query: orgCode ? { orgCode } : undefined });
  }
}

/** 退出登录时清空当前用户的后台会话 UI 状态，避免下一个账号继承页签和侧栏折叠状态。 */
function clearAdminUiSession(): void {
  tabs.value = [];
  sidebarCollapsed.value = false;
  globalThis.sessionStorage.removeItem(ADMIN_TABS_STORAGE_KEY);
  globalThis.sessionStorage.removeItem(ADMIN_SIDEBAR_COLLAPSED_STORAGE_KEY);
}

/** 将当前路由加入页签，并保证首次进入后台时存在一个不可关闭的当前页。 */
function ensureTab(currentRoute: RouteLocationNormalizedLoaded): void {
  if (currentRoute.path === LOGIN_ROUTE_PATH) {
    return;
  }

  const title = typeof currentRoute.meta.title === 'string' ? currentRoute.meta.title : '后台页面';
  tabs.value = upsertAdminTab(tabs.value, currentRoute, title);

  tabs.value.forEach((tab) => {
    tab.closable = tabs.value.length > 1;
  });

  persistTabs();
}

/** 点击页签切换页面。 */
async function activateTab(tab: AdminTab): Promise<void> {
  await navigate(tab.path);
}

/** 关闭页签并切换到相邻页签，当前页关闭时才触发路由跳转。 */
async function closeTab(tab: AdminTab): Promise<void> {
  if (!tab.closable) {
    return;
  }

  const tabIndex = tabs.value.findIndex((item) => item.key === tab.key);
  tabs.value.splice(tabIndex, 1);
  tabs.value.forEach((item) => {
    item.closable = tabs.value.length > 1;
  });
  persistTabs();

  if (tab.path === route.fullPath) {
    await navigate(tabs.value[Math.max(0, tabIndex - 1)]?.path ?? defaultMenuPath.value);
  }
}

/** 处理 tabs-card 的批量关闭菜单。 */
async function handleTabCommand(command: string): Promise<void> {
  if (command === 'refresh') {
    router.go(0);
    return;
  }
  if (command === 'close-current') {
    const currentTab = tabs.value.find((tab) => tab.path === route.fullPath);
    if (currentTab) {
      await closeTab(currentTab);
    }
    return;
  }
  if (command === 'close-other') {
    tabs.value = tabs.value.filter((tab) => tab.path === route.fullPath || !tab.closable);
    persistTabs();
    return;
  }
  tabs.value = tabs.value.filter((tab) => !tab.closable || tab.path === route.fullPath);
  persistTabs();
}

/** 切换侧栏展开状态并持久化，内容区会自动获得释放出的宽度。 */
function toggleSidebar(): void {
  sidebarCollapsed.value = !sidebarCollapsed.value;
  globalThis.sessionStorage.setItem(ADMIN_SIDEBAR_COLLAPSED_STORAGE_KEY, String(sidebarCollapsed.value));
}

/** 更新 tabs-card 滚动状态 **/
function updateTabScrollState(): void {
  const container = tabScrollContainer.value;

  if (!container) {
    canScrollTabsLeft.value = false;
    canScrollTabsRight.value = false;
    return;
  }

  canScrollTabsLeft.value = container.scrollLeft > 0;
  canScrollTabsRight.value = container.scrollLeft + container.clientWidth < container.scrollWidth - 1;
}

/** tabs-card 滚动 **/
function scrollTabs(direction: 'left' | 'right'): void {
  const container = tabScrollContainer.value;

  if (!container) {
    return;
  }

  const distance = Math.max(container.clientWidth * 0.7, 180);
  container.scrollBy({ left: direction === 'left' ? -distance : distance, behavior: 'smooth' });
}

/** 窗口尺寸变化时重新计算 tabs-card 是否需要左右滑动。*/
function handleTabContainerResize(): void {
  updateTabScrollState();
}

watch(
  () => route.fullPath,
  () => ensureTab(route),
  { immediate: true }
);
watch(
  () => `${tabs.value.length}:${tabs.value.map((tab) => tab.path).join('|')}`,
  async () => {
    await nextTick();
    updateTabScrollState();
  },
  { flush: 'post' }
);
onMounted(async () => {
  /** 清理旧版本写入的 localStorage 状态，避免 UI 偏好跨浏览器标签页继续生效。*/
  globalThis.localStorage.removeItem(ADMIN_TABS_STORAGE_KEY);
  globalThis.localStorage.removeItem(ADMIN_SIDEBAR_COLLAPSED_STORAGE_KEY);
  globalThis.addEventListener('resize', handleTabContainerResize);
  await nextTick();
  if (tabScrollContainer.value) {
    tabScrollResizeObserver = new globalThis.ResizeObserver(updateTabScrollState);
    tabScrollResizeObserver.observe(tabScrollContainer.value);
  }
});
onBeforeUnmount(() => {
  globalThis.removeEventListener('resize', handleTabContainerResize);
  tabScrollResizeObserver?.disconnect();
});
</script>

<template>
  <div class="admin-layout">
    <TopToolbar :brand-to="defaultMenuPath" :user-name="userName" :nav-items="adminModuleNavItems" :active-path="activeModule?.path">
      <ElButton link type="primary" @click="returnToPortal">返回门户</ElButton>
      <ElButton style="margin-left: 0" :loading="loggingOut" link type="danger" @click="logout">退出登录</ElButton>
    </TopToolbar>
    <section class="admin-body">
      <aside v-if="!isOverviewPage && sidebarNavigation.length > 0" class="admin-sidebar" :class="{ 'admin-sidebar--collapsed': sidebarCollapsed }">
        <div class="admin-sidebar__menu-stack">
          <ElMenu class="admin-menu admin-sidebar__expanded-menu" :default-active="route.path" @select="navigate">
            <template v-for="item in sidebarNavigation" :key="item.path">
              <ElSubMenu v-if="item.children?.length" :index="item.path">
                <template #title>
                  <ElIcon><component :is="getNavigationIcon(item.icon)" /></ElIcon>
                  <span>{{ item.title }}</span>
                </template>
                <template v-for="child in item.children" :key="child.path">
                  <ElSubMenu v-if="child.children?.length" :index="child.path">
                    <template #title>
                      <ElIcon><component :is="getNavigationIcon(child.icon)" /></ElIcon>
                      <span>{{ child.title }}</span>
                    </template>
                    <ElMenuItem v-for="grandChild in child.children" :key="grandChild.path" :index="grandChild.path">
                      <ElIcon><component :is="getNavigationIcon(grandChild.icon)" /></ElIcon>
                      <span>{{ grandChild.title }}</span>
                    </ElMenuItem>
                  </ElSubMenu>
                  <ElMenuItem v-else :index="child.path">
                    <ElIcon><component :is="getNavigationIcon(child.icon)" /></ElIcon>
                    <span>{{ child.title }}</span>
                  </ElMenuItem>
                </template>
              </ElSubMenu>
              <ElMenuItem v-else :index="item.path">
                <ElIcon><component :is="getNavigationIcon(item.icon)" /></ElIcon>
                <span>{{ item.title }}</span>
              </ElMenuItem>
            </template>
          </ElMenu>
          <AdminSidebarCollapsedMenu class="admin-sidebar__collapsed-menu" :items="sidebarNavigation" :active-path="route.path" @select="navigate" />
        </div>
      </aside>
      <section class="admin-workspace">
        <div v-if="!isOverviewPage" class="admin-breadcrumb">
          <ElButton
            v-if="sidebarNavigation.length > 0"
            class="admin-breadcrumb__toggle"
            text
            :title="sidebarCollapsed ? '展开菜单' : '收起菜单'"
            @click="toggleSidebar"
          >
            <ElIcon>
              <Expand v-if="sidebarCollapsed" />
              <Fold v-else />
            </ElIcon>
          </ElButton>
          <ElBreadcrumb separator="/">
            <ElBreadcrumbItem v-for="(breadcrumb, index) in breadcrumbs" :key="`${breadcrumb}-${index}`">
              {{ breadcrumb }}
            </ElBreadcrumbItem>
          </ElBreadcrumb>
        </div>
        <div v-if="!isOverviewPage" class="admin-tabs">
          <ElButton class="admin-tabs__scroll-button" text :disabled="!canScrollTabsLeft" aria-label="向左滑动标签" @click="scrollTabs('left')">
            <ElIcon><ArrowLeft /></ElIcon>
          </ElButton>
          <div ref="tabScrollContainer" class="admin-tabs__list" @scroll="updateTabScrollState">
            <button
              v-for="tab in tabs"
              :key="tab.key"
              class="admin-tab"
              :class="{ 'admin-tab--active': tab.path === route.fullPath }"
              type="button"
              @click="activateTab(tab)"
            >
              <span>{{ tab.title }}</span>
              <ElIcon v-if="tab.closable" class="admin-tab__close" @click.stop="closeTab(tab)"><Close /></ElIcon>
            </button>
          </div>
          <ElButton class="admin-tabs__scroll-button" text :disabled="!canScrollTabsRight" aria-label="向右滑动标签" @click="scrollTabs('right')">
            <ElIcon><ArrowRight /></ElIcon>
          </ElButton>
          <ElDropdown trigger="click" @command="handleTabCommand">
            <ElButton class="admin-tabs__more" text aria-label="页签操作">⋮</ElButton>
            <template #dropdown>
              <ElDropdownMenu>
                <ElDropdownItem command="refresh">刷新当前</ElDropdownItem>
                <ElDropdownItem command="close-current" :disabled="!tabs.find((tab) => tab.path === route.fullPath)?.closable">
                  关闭当前
                </ElDropdownItem>
                <ElDropdownItem command="close-other">关闭其他</ElDropdownItem>
                <ElDropdownItem command="close-all">关闭全部</ElDropdownItem>
              </ElDropdownMenu>
            </template>
          </ElDropdown>
        </div>
        <main class="admin-content">
          <RouterView :key="contentViewKey" />
        </main>
      </section>
    </section>
  </div>
</template>

<style scoped lang="less">
.admin-layout {
  min-height: 100vh;
  background: var(--color-bg-page);
}
.admin-body {
  display: flex;
  height: calc(100vh - var(--layout-toolbar-height));
  overflow: hidden;
}
.admin-sidebar {
  width: var(--layout-sidebar-width);
  flex: 0 0 var(--layout-sidebar-width);
  padding: 0 0 5px;
  box-sizing: border-box;
  contain: layout style paint;
  // 菜单项超出视口时允许在侧栏内向下滚动，横向仍保持裁切。
  overflow-x: hidden;
  overflow-y: auto;
  background: var(--color-bg-surface);
  border-right: 1px solid var(--color-border);
  transform: translateZ(0);
  transition:
    width 320ms cubic-bezier(0.4, 0, 0.2, 1),
    flex-basis 320ms cubic-bezier(0.4, 0, 0.2, 1);
  will-change: width, flex-basis;
  &--collapsed {
    width: var(--layout-sidebar-collapsed-width);
    flex-basis: var(--layout-sidebar-collapsed-width);
  }
}
.admin-sidebar__menu-stack {
  position: relative;
  min-height: 100%;
}
.admin-menu {
  // 一级左内边距同时决定 SubMenu 展开箭头距右边界的距离（Element Plus 用该变量定位箭头），
  // 取 12px 避免箭头与文字贴住侧栏边框；每级再按 14px 递进，保证更深层级的文字仍留有呼吸空间。
  --el-menu-base-level-padding: 12px;
  --el-menu-level-padding: 14px;

  --el-menu-item-height: 40px; // 将 Item 高度从 50px 降到 40px
  --el-sub-menu-title-height: 40px; // 将 SubMenu 标题高度从 50px 降到 40px

  width: var(--layout-sidebar-width);
  min-width: var(--layout-sidebar-width);
  border-right: 0;
  transform: translateZ(0);
  will-change: auto;

  //使用 deep 强制覆盖任意深度的菜单项和子菜单标题
  &:deep(.el-menu-item),
  &:deep(.el-sub-menu__title) {
    height: 40px !important;
    line-height: 40px !important; // 必须加上行高，保证文字垂直居中
  }
}
.admin-menu:deep(.el-icon) {
  flex-shrink: 0;
}
// 侧栏为固定宽度，层级越深可用宽度越小：标题超长时省略而不是溢出被裁切。
.admin-menu:deep(.el-menu-item > span),
.admin-menu:deep(.el-sub-menu__title > span) {
  min-width: 0;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.admin-sidebar__expanded-menu {
  position: relative;
  z-index: 1;
  opacity: 1;
  visibility: visible;
  transition:
    opacity 160ms ease,
    transform 160ms ease,
    visibility 0s linear 0s;
}
.admin-sidebar--collapsed .admin-sidebar__expanded-menu {
  opacity: 0;
  visibility: hidden;
  pointer-events: none;
  transform: translateX(-6px);
}
.admin-sidebar__collapsed-menu {
  position: absolute;
  inset: 0;
  z-index: 2;
  width: 100%;
  min-width: 100%;
  opacity: 0;
  visibility: hidden;
  pointer-events: none;
  transition:
    opacity 160ms ease,
    transform 160ms ease,
    visibility 0s linear 160ms;
  transform: translateX(-6px);
}
.admin-sidebar--collapsed .admin-sidebar__collapsed-menu {
  opacity: 1;
  visibility: visible;
  pointer-events: auto;
  transform: translateX(0);
}
.admin-workspace {
  display: flex;
  min-width: 0;
  min-height: 0;
  flex: 1;
  flex-direction: column;
}
.admin-tabs {
  display: flex;
  height: 40px;
  flex: 0 0 40px;
  min-width: 0;
  align-items: center;
  padding: 0 var(--space-2);
  background: var(--color-bg-surface);
  border-bottom: 1px solid var(--color-border);
}
.admin-tabs__list {
  display: flex;
  min-width: 0;
  flex: 1;
  align-self: stretch;
  align-items: center;
  gap: var(--space-1);
  overflow: hidden;
}
.admin-tabs__scroll-button {
  width: 28px;
  height: 32px;
  flex: 0 0 28px;
  padding: 0;
  color: var(--color-text-secondary);
}
.admin-tabs__scroll-button:not(.is-disabled):hover {
  color: var(--color-primary);
  background: var(--color-primary-subtle);
}
.admin-tab {
  display: inline-flex;
  height: 28px;
  padding: 0 var(--space-3);
  align-items: center;
  gap: var(--space-2);
  border: 1px solid transparent;
  border-radius: var(--radius-control);
  background: transparent;
  color: var(--color-text-secondary);
  cursor: pointer;
  font: inherit;
  font-size: 13px;
  white-space: nowrap;
}
.admin-tab:hover,
.admin-tab--active {
  border-color: var(--color-border);
  background: var(--color-primary-subtle);
  color: var(--color-primary);
}
.admin-tab__close {
  visibility: hidden;
  opacity: 0;
  transform: scale(0.8);
  font-size: 12px;
  transition:
    opacity 160ms ease,
    transform 160ms ease;
}
.admin-tab:hover .admin-tab__close {
  visibility: visible;
  opacity: 1;
  transform: scale(1);
}
.admin-tabs__more {
  font-size: 20px;
}
.admin-breadcrumb {
  flex: 0 0 40px;
  padding: 0 var(--layout-content-padding);
  display: flex;
  gap: var(--space-2);
  align-items: center;
  background: var(--color-bg-page);
}
.admin-breadcrumb__toggle {
  width: 28px;
  height: 28px;
  flex: 0 0 28px;
  padding: 0;
  color: var(--color-text-secondary);
}
.admin-breadcrumb__toggle:hover {
  color: var(--color-primary);
  background: var(--color-primary-subtle);
}
.admin-content {
  min-height: 0;
  flex: 1;
  overflow: hidden;
  padding: var(--layout-content-padding) var(--layout-content-padding);
}
@media (max-width: 900px) {
  .admin-sidebar {
    width: var(--layout-sidebar-collapsed-width);
    flex-basis: var(--layout-sidebar-collapsed-width);
  }
}
</style>
