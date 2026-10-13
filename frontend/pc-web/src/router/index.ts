import { createRouter, createWebHistory } from 'vue-router';
import { LOGIN_ORG_CODE_STORAGE_KEY, LOGIN_ROUTE_PATH } from '@/constants/routes';
import { useUserStore } from '@/stores';
import { getDefaultMenuPath, synchronizeMenuRoutes } from './admin-dynamic-routes';

/** 登录和无需菜单登记的问卷填写页为静态路由；门户与后台页面均在读取 profile 菜单后动态注册。 */
const router = createRouter({
  history: createWebHistory(),
  routes: [
    {
      path: '/user-center', name: 'LearnerCenter',
      component: () => import('@/views/user-center/UserCenterHome.vue'),
      meta: { title: '个人学习中心', requiresAuth: true, personalFlow: true },
      children: [
        { path: '', redirect: '/user-center/practices' },
        { path: 'profile', name: 'MyProfile', component: () => import('@/views/user-center/profile/index.vue') },
        { path: 'history', name: 'MyHistory', component: () => import('@/views/user-center/history/index.vue') },
        { path: 'messages', name: 'LearnerMessages', component: () => import('@/views/user-center/messages/index.vue') },
        { path: 'teachers', name: 'LearnerTeacherFollow', component: () => import('@/views/user-center/teachers/index.vue') },
        { path: 'favorites', name: 'LearnerCourseFavorites', component: () => import('@/views/user-center/courses/index.vue') },
        { path: 'courses', name: 'LearnerCourseFollow', component: () => import('@/views/user-center/courses/index.vue') },
        { path: 'exams', name: 'PersonalExams', component: () => import('@/views/user-center/exams/index.vue') },
        { path: 'practices', name: 'LearnerPractices', component: () => import('@/views/user-center/practice/index.vue') },
        { path: 'wrong-questions', name: 'LearnerWrongQuestions', component: () => import('@/views/user-center/practice/index.vue') },
        { path: 'practice/:sessionId', name: 'LearnerPracticeAttempt', component: () => import('@/views/user-center/practice/attempt.vue') }
      ]
    },
    {
      path: LOGIN_ROUTE_PATH,
      name: 'Login',
      component: () => import('@/views/pages/LoginPage.vue'),
      meta: { title: '登录', requiresAuth: false }
    },
    {
      path: '/survey/fill/:surveyInstanceId',
      name: 'SurveyFill',
      component: () => import('@/views/pages/SurveyFillPage.vue'),
      meta: { title: '填写问卷', requiresAuth: true, m4PublicFlow: true }
    }
  ]
});

/** 使用动态注册后的路由记录校验菜单权限，支持题目详情等参数化菜单路径。 */
function hasAuthorizedMenuPath(path: string): boolean {
  return router.resolve(path).matched.some((record) => typeof record.meta.menuId === 'number' || record.meta.m4PublicFlow === true || record.meta.personalFlow === true);
}

/** 路由守卫只根据 profile 菜单改善访问体验，资源授权仍由后端接口校验。 */
router.beforeEach(async (to) => {
  const userStore = useUserStore();
  const lastOrgCode = globalThis.sessionStorage.getItem(LOGIN_ORG_CODE_STORAGE_KEY);
  const loginQuery = { redirect: to.fullPath, ...(lastOrgCode ? { orgCode: lastOrgCode } : {}) };
  if (!userStore.token) {
    return to.path === LOGIN_ROUTE_PATH ? true : { path: LOGIN_ROUTE_PATH, query: loginQuery };
  }
  if (!userStore.userInfo) {
    await userStore.restoreSession();
  }
  if (!userStore.token) {
    return to.path === LOGIN_ROUTE_PATH ? true : { path: LOGIN_ROUTE_PATH, query: loginQuery };
  }
  synchronizeMenuRoutes(router, userStore.userInfo);
  const defaultPath = getDefaultMenuPath(userStore.userInfo);
  if (to.path === LOGIN_ROUTE_PATH) {
    // 已登录时打开机构专属链接，先留在登录页明确切换账号，不静默更换租户。
    if (typeof to.query.orgCode === 'string' && to.query.orgCode.toUpperCase() !== lastOrgCode) return true;
    const redirect = typeof to.query.redirect === 'string' ? to.query.redirect : undefined;
    return redirect && hasAuthorizedMenuPath(redirect) ? redirect : (defaultPath ?? true);
  }
  if (!hasAuthorizedMenuPath(to.fullPath)) {
    return defaultPath ?? LOGIN_ROUTE_PATH;
  }
  if (to.matched.length === 0) {
    return { path: to.fullPath, replace: true };
  }
  return true;
});

export default router;
