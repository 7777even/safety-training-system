<script setup lang="ts">
import { type Component } from 'vue';
import { ArrowRight, Menu } from '@element-plus/icons-vue';
import * as ElementPlusIconsVue from '@element-plus/icons-vue';
import type { AdminNavigationItem } from '@/constants/admin-navigation';

defineOptions({
  name: 'AdminSidebarCollapsedMenu'
});

const props = withDefaults(
  defineProps<{
    items: AdminNavigationItem[];
    activePath: string;
    depth?: number;
  }>(),
  {
    depth: 0
  }
);

const emit = defineEmits<{
  select: [path: string];
}>();

function getNavigationIcon(icon: string): Component {
  return (ElementPlusIconsVue[icon as keyof typeof ElementPlusIconsVue] as Component | undefined) ?? Menu;
}

function isActive(item: AdminNavigationItem): boolean {
  return props.activePath === item.path || props.activePath.startsWith(`${item.path}/`);
}
</script>

<template>
  <div class="admin-sidebar-collapsed-nav" :class="{ 'admin-sidebar-collapsed-nav--nested': depth > 0 }">
    <template v-for="item in items" :key="item.path">
      <ElPopover
        v-if="item.children?.length"
        placement="right-start"
        trigger="hover"
        :show-after="0"
        :hide-after="80"
        :offset="4"
        :width="140"
        :show-arrow="false"
        :teleported="true"
        popper-class="admin-sidebar-popup"
      >
        <template #reference>
          <button
            type="button"
            class="admin-sidebar-collapsed-nav__item admin-sidebar-collapsed-nav__item--parent"
            :class="{ 'is-active': isActive(item), 'admin-sidebar-collapsed-nav__item--root': depth === 0 }"
            :title="item.title"
          >
            <ElIcon class="admin-sidebar-collapsed-nav__icon">
              <component :is="getNavigationIcon(item.icon)" />
            </ElIcon>
            <span v-if="depth > 0" class="admin-sidebar-collapsed-nav__label">{{ item.title }}</span>
            <ElIcon v-if="depth > 0" class="admin-sidebar-collapsed-nav__arrow">
              <ArrowRight />
            </ElIcon>
          </button>
        </template>
        <div class="el-menu--popup admin-sidebar-popup__panel">
          <AdminSidebarCollapsedMenu
            :items="item.children"
            :active-path="activePath"
            :depth="depth + 1"
            @select="emit('select', $event)"
          />
        </div>
      </ElPopover>
      <button
        v-else
        type="button"
        class="admin-sidebar-collapsed-nav__item admin-sidebar-collapsed-nav__item--leaf"
        :class="{ 'is-active': isActive(item), 'admin-sidebar-collapsed-nav__item--root': depth === 0 }"
        :title="item.title"
        @click="emit('select', item.path)"
      >
        <ElIcon class="admin-sidebar-collapsed-nav__icon">
          <component :is="getNavigationIcon(item.icon)" />
        </ElIcon>
        <span v-if="depth > 0" class="admin-sidebar-collapsed-nav__label">{{ item.title }}</span>
      </button>
    </template>
  </div>
</template>

<style scoped lang="less">
.admin-sidebar-collapsed-nav {
  display: flex;
  min-height: 100%;
  flex-direction: column;
  padding: 2px 0;
}
.admin-sidebar-collapsed-nav--nested {
  min-width: 100%;
  padding: 0;
}
.admin-sidebar-collapsed-nav__item {
  display: flex;
  width: 100%;
  height: 40px;
  min-height: 40px;
  align-items: center;
  justify-content: center;
  padding: 0;
  border: 0;
  background: transparent;
  color: var(--color-text-regular);
  cursor: pointer;
  font: inherit;
  transition:
    color 160ms ease,
    background-color 160ms ease;
}
.admin-sidebar-collapsed-nav__item:hover,
.admin-sidebar-collapsed-nav__item.is-active {
  background: var(--color-primary-subtle);
  color: var(--color-primary);
}
.admin-sidebar-collapsed-nav__item--root {
  justify-content: center;
}
.admin-sidebar-collapsed-nav--nested .admin-sidebar-collapsed-nav__item {
  justify-content: center;
  padding: 0 5px;
}
.admin-sidebar-collapsed-nav--nested .admin-sidebar-collapsed-nav__item--parent {
  // 为右侧展开箭头预留空间，避免长标题与箭头相互挤压。
  padding-right: 34px;
}
.admin-sidebar-collapsed-nav__icon {
  flex: 0 0 auto;
}
.admin-sidebar-collapsed-nav--nested .admin-sidebar-collapsed-nav__icon {
  margin-right: 6px;
}
.admin-sidebar-collapsed-nav__label {
  min-width: 0;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.admin-sidebar-collapsed-nav__arrow {
  position: absolute;
  right: 8px;
  flex: 0 0 auto;
}
.admin-sidebar-collapsed-nav__item--parent,
.admin-sidebar-collapsed-nav__item--leaf {
  position: relative;
}
</style>
