<script setup lang="ts">
import { computed } from 'vue';
import { Collection, EditPen, Notebook, Star, Trophy, User } from '@element-plus/icons-vue';
import { useRoute,useRouter } from 'vue-router';
import { getAuthorizedPortalNavigation } from '@/router/admin-dynamic-routes';
import { LEARNER_CENTER_ROUTES } from '@/constants/learner-center';
import PortalLayout from '@/layouts/PortalLayout.vue';
import { useUserStore } from '@/stores';

/** 本人学习入口不依赖后台编辑权限，真实资源归属由学员接口校验。 */
const entries = [
  { label: '资料与改密', path: '/user-center/profile', icon: User },
  { label: '统一学习档案', path: '/user-center/history', icon: Notebook },
  { label: '我的证书', path: '/portal/certificates', icon: Trophy },
  { label: '消息中心', path: '/user-center/messages', icon: Notebook },
  { label: '我的练习', path: LEARNER_CENTER_ROUTES.practices, icon: EditPen },
  { label: '我的错题集', path: LEARNER_CENTER_ROUTES.wrongs, icon: Notebook },
  { label: '我的考试', path: LEARNER_CENTER_ROUTES.exams, icon: Trophy },
  { label: '关注课程', path: LEARNER_CENTER_ROUTES.courses, icon: Star },
  { label: '我的收藏', path: LEARNER_CENTER_ROUTES.favorites, icon: Collection },
  { label: '我的关注', path: LEARNER_CENTER_ROUTES.teachers, icon: User }
];

const route = useRoute();
const router = useRouter();
const userStore = useUserStore();

/**
 * 「我的证书」是独立门户菜单页：未分配该菜单的账号点进来会被路由守卫送回默认页，
 * 导航项按已授权门户导航收敛，与首页页头、顶部导航保持同一判定口径。
 */
const visibleEntries = computed(() =>
  entries.filter(
    (entry) =>
      entry.path !== '/portal/certificates' ||
      getAuthorizedPortalNavigation(userStore.userInfo).some((item) => item.path === '/portal/certificates')
  )
);

/** 二级页签激活态按当前路由判定；作答页按进入来源回归「我的练习」或「我的错题集」。 */
function isActive(path: string): boolean {
  if (route.path === path) return true;
  if (route.name !== 'LearnerPracticeAttempt') return false;
  return path === (route.query.from === 'wrongs' ? LEARNER_CENTER_ROUTES.wrongs : LEARNER_CENTER_ROUTES.practices);
}
</script>

<template>
  <PortalLayout>
    <header class="learner-center__heading">
      <div>
        <p class="learner-center__eyebrow">MY LEARNING</p>
        <h1>个人学习中心</h1>
      </div>
      <ElButton class="learner-center__back" @click="router.back()">返回</ElButton>
    </header>
    <nav class="learner-center__nav" aria-label="个人学习导航">
      <RouterLink
        v-for="entry in visibleEntries"
        :key="entry.path"
        class="learner-center__nav-item"
        :class="{ 'learner-center__nav-item--active': isActive(entry.path) }"
        :to="entry.path"
      >
        <ElIcon><component :is="entry.icon" /></ElIcon>
        <span>{{ entry.label }}</span>
      </RouterLink>
    </nav>
  </PortalLayout>
</template>

<style scoped lang="less">
/* 页头降为轻量标题：渐变横幅留给子页，避免两层蓝色大横幅互相争夺视觉重心。 */
.learner-center__heading {
  display: flex;
  margin-bottom: var(--space-3);
  align-items: flex-end;
  justify-content: space-between;
  gap: var(--space-4);
}

.learner-center__heading h1 {
  padding-left: 0;
  margin: 0;
  border-left: 0;
  color: var(--color-text-primary);
  font-size: 22px;
}

.learner-center__eyebrow {
  margin: 0 0 6px;
  color: var(--color-primary);
  font-size: 12px;
  font-weight: 600;
  letter-spacing: 1.6px;
}

.learner-center__back {
  flex: none;
  padding: 8px 16px;
  border: 1px solid var(--color-border);
  border-radius: 999px;
  background: var(--color-bg-surface);
  color: var(--color-text-regular);
  font-size: 13px;
  text-decoration: none;
  transition:
    border-color 0.2s ease,
    color 0.2s ease;
}

.learner-center__back:hover {
  border-color: var(--color-primary);
  color: var(--color-primary);
}

/* 二级导航改用门户页签语言：白底卡片 + 下划线激活，不再与子页页头争夺视觉层级。 */
.learner-center__nav {
  display: flex;
  gap: var(--space-6);
  padding: 0 var(--space-5);
  margin-bottom: var(--space-6);
  overflow-x: auto;
  border-radius: var(--radius-card);
  background: var(--color-bg-surface);
  box-shadow: var(--shadow-card);
}

.learner-center__nav-item {
  position: relative;
  display: flex;
  padding: 14px 2px 12px;
  align-items: center;
  gap: var(--space-2);
  border-bottom: 3px solid transparent;
  color: var(--color-text-regular);
  font-size: 14px;
  white-space: nowrap;
  text-decoration: none;
  transition:
    color 0.2s ease,
    border-color 0.2s ease;
}

.learner-center__nav-item:hover {
  color: var(--color-primary);
}

.learner-center__nav-item--active {
  border-bottom-color: var(--color-primary);
  color: var(--color-primary);
  font-weight: 600;
}

.learner-center__nav-item .el-icon {
  font-size: 16px;
}

@media (max-width: 768px) {
  .learner-center__heading {
    padding: var(--space-4);
  }

  .learner-center__nav {
    gap: var(--space-4);
    padding: 0 var(--space-4);
  }
}
</style>
