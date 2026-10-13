<script setup lang="ts">
import { computed } from 'vue';
import { useRoute, useRouter } from 'vue-router';
import { User } from '@element-plus/icons-vue';

/** 顶部工具栏的导航项：path 用于菜单索引与激活态匹配，target 为点击后的跳转目标。 */
interface TopToolbarNavItem {
  title: string;
  path: string;
  target?: string;
}

const props = withDefaults(
  defineProps<{
    /** 品牌 Logo 点击后返回的默认页面。 */
    brandTo: string;
    /** 右上角展示的当前用户名。 */
    userName: string;
    /** 横向导航项，与后台模块菜单保持一致。 */
    navItems: TopToolbarNavItem[];
    /** 当前激活的导航索引，用于高亮选中项。 */
    activePath?: string;
  }>(),
  {
    activePath: ''
  }
);

const router = useRouter();
const route = useRoute();
/** 个人学习中心入口在任意子页均保持激活态，与顶栏菜单选中态语言一致。 */
const isLearnerCenterActive = computed(() => route.path.startsWith('/user-center'));

/** 菜单选中时跳转到导航项声明的目标路径，缺省回退到索引本身。 */
async function navigate(index: string): Promise<void> {
  const item = props.navItems.find((navItem) => navItem.path === index);
  const target = item?.target ?? item?.path ?? index;

  if (target) {
    await router.push(target);
  }
}
</script>

<template>
  <header class="top-toolbar">
    <RouterLink class="top-toolbar__brand" :to="brandTo">
      <span>安</span>
      <strong>安全教育培训系统</strong>
    </RouterLink>
    <ElMenu class="top-toolbar__menu" mode="horizontal" :default-active="activePath" @select="navigate">
      <ElMenuItem v-for="item in navItems" :key="item.path" :index="item.path">
        {{ item.title }}
      </ElMenuItem>
    </ElMenu>
    <div class="top-toolbar__actions">
      <ElPopover placement="bottom-end" trigger="click" width="176px">
        <template #reference>
          <ElButton class="top-toolbar__user" text>{{ userName }}</ElButton>
        </template>
        <div class="top-toolbar__user-menu">
          <RouterLink
            class="top-toolbar__user-entry"
            :class="{ 'top-toolbar__user-entry--active': isLearnerCenterActive }"
            to="/user-center"
          ><ElIcon><User /></ElIcon>个人学习中心</RouterLink>
          <slot />
        </div>
      </ElPopover>
    </div>
  </header>
</template>

<style scoped lang="less">
.top-toolbar {
  position: relative;
  display: flex;
  min-height: var(--layout-toolbar-height);
  height: var(--layout-toolbar-height);
  padding: 0 var(--layout-content-padding);
  align-items: center;
  box-sizing: border-box;
  background: var(--color-bg-surface);
  box-shadow: var(--shadow-card);

  :deep(.el-menu) {
    border-bottom: none;
  }
}
.top-toolbar::after {
  position: absolute;
  right: 0;
  bottom: 0;
  left: 0;
  z-index: 1;
  height: 1px;
  background: var(--color-border);
  content: '';
  pointer-events: none;
}
.top-toolbar__brand {
  display: flex;
  height: 44px;
  margin-right: var(--space-6);
  align-items: center;
  gap: var(--space-2);
  color: var(--color-primary);
  text-decoration: none;
  white-space: nowrap;
}
.top-toolbar__brand span {
  display: grid;
  width: 30px;
  height: 30px;
  place-items: center;
  border-radius: var(--radius-control);
  background: var(--color-primary);
  color: var(--color-bg-surface);
}
.top-toolbar__brand strong {
  font-size: 14px;
}
.top-toolbar__menu {
  flex: 1;
  min-width: 0;
  border-bottom: 0;
  height: var(--layout-toolbar-height);
}
.top-toolbar__menu :deep(.el-menu-item) {
  padding: 0 var(--space-4);
}
.top-toolbar__actions {
  display: flex;
  margin-left: var(--space-6);
  align-items: center;
  color: var(--color-text-regular);
  font-size: 14px;
}
.top-toolbar__user {
  color: var(--color-text-regular);
  font-weight: 500;
}
.top-toolbar__user-menu {
  display: flex;
  flex-direction: column;
  gap: var(--space-1);
}
.top-toolbar__user-menu a {
  display: flex;
  padding: var(--space-2) var(--space-3);
  align-items: center;
  gap: var(--space-2);
  border-radius: var(--radius-control);
  color: var(--color-text-regular);
  font-size: 13px;
  text-decoration: none;
  transition: background 0.2s ease, color 0.2s ease;
}
.top-toolbar__user-menu a:hover {
  background: var(--color-primary-subtle);
  color: var(--color-primary);
}
.top-toolbar__user-menu a.top-toolbar__user-entry--active {
  background: var(--color-primary-subtle);
  color: var(--color-primary);
  font-weight: 600;
}
@media (max-width: 900px) {
  .top-toolbar {
    padding: 0 var(--space-4);
  }
  .top-toolbar__brand strong {
    display: none;
  }
  .top-toolbar__brand {
    margin-right: var(--space-3);
  }
}
</style>
