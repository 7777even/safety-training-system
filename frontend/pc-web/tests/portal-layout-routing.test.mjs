import assert from 'node:assert/strict';
import { existsSync } from 'node:fs';
import { readFile } from 'node:fs/promises';
import test from 'node:test';
import {createMemoryHistory, createRouter} from 'vue-router';

const routeFilePath = new URL('../src/router/admin-dynamic-routes.ts', import.meta.url);
const routerFilePath = new URL('../src/router/index.ts', import.meta.url);
const mainFilePath = new URL('../src/main.ts', import.meta.url);
const loginPageFilePath = new URL('../src/views/pages/LoginPage.vue', import.meta.url);
const portalLayoutFilePath = new URL('../src/layouts/PortalLayout.vue', import.meta.url);

test('门户菜单路由使用统一门户布局并在内容区渲染子页面', async () => {
  const [routeSource, portalLayoutSource] = await Promise.all([
    readFile(routeFilePath, 'utf8'),
    readFile(portalLayoutFilePath, 'utf8')
  ]);

  assert.match(routeSource, /import PortalLayout from '@\/layouts\/PortalLayout\.vue';/);
  assert.match(routeSource, /function isPortalMenuPath\(path: string\): boolean/);
  assert.match(routeSource, /function buildRegisteredRouterRoutes\(routes: ResolvedMenuRoute\[\]\): RouteRecordRaw\[\]/);
  assert.match(routeSource, /path: PORTAL_PATH,\s*name: PORTAL_LAYOUT_ROUTE_NAME,\s*component: PortalLayout,/);
  assert.ok(routeSource.includes("path: route.fullPath.slice(PORTAL_PATH.length).replace(/^\\//, '')"));
  assert.match(portalLayoutSource, /<section class="portal-content">\s*<RouterView\s*\/>\s*<\/section>/);
});

test('五个前台菜单组件均有可解析页面，课程与考试已实现且其余模块保留占位提示', async () => {
  const portalPagePaths = [
    new URL('../src/views/portal/home/index.vue', import.meta.url),
    new URL('../src/views/portal/class/index.vue', import.meta.url),
    new URL('../src/views/portal/exams/index.vue', import.meta.url),
    new URL('../src/views/portal/training/index.vue', import.meta.url),
    new URL('../src/views/portal/teaching-aids/index.vue', import.meta.url)
  ];

  portalPagePaths.forEach((pagePath) => assert.equal(existsSync(pagePath), true));

  const [coursePage, examPage, ...placeholderPages] = await Promise.all(portalPagePaths.slice(1).map((pagePath) => readFile(pagePath, 'utf8')));
  assert.match(coursePage, /我的课程/);
  assert.match(examPage, /我的考试/);

  const placeholderTitles = ['培训中心', '教学辅助'];
  placeholderPages.forEach((pageSource, index) => {
    assert.match(pageSource, new RegExp(placeholderTitles[index]));
    assert.match(pageSource, /暂未开放/);
  });
});

test('门户父目录不显示，顶部导航展开其已授权的五个页面菜单', async () => {
  const routeSource = await readFile(routeFilePath, 'utf8');

  assert.match(routeSource, /function getAuthorizedPortalNavigation\(userInfo: UserInfo \| null\): PortalNavigationItem\[\]/);
  assert.match(routeSource, /collectPortalNavigation\(route\)/);
  assert.match(routeSource, /route\.children\.length > 0/);
});

test('参数化菜单路径应授权具体题目详情地址而非回退首页', async () => {
  const router = createRouter({
    history: createMemoryHistory(),
    routes: [
      {
        path: '/admin/business-center/exams/question/:questionId',
        name: 'QuestionDetail',
        component: {},
        meta: {menuId: 1164, requiresAuth: true}
      }
    ]
  });
  const routeSource = await readFile(routerFilePath, 'utf8');
  const resolvedRoute = router.resolve('/admin/business-center/exams/question/42');

  assert.equal(resolvedRoute.matched.some((record) => record.meta.menuId === 1164), true);
  assert.match(routeSource, /function hasAuthorizedMenuPath\(path: string\): boolean/);
  assert.match(routeSource, /hasAuthorizedMenuPath\(to\.fullPath\)/);
  assert.match(routeSource, /hasAuthorizedMenuPath\(redirect\)/);
  assert.doesNotMatch(routeSource, /menuPaths\.includes\(to\.path\)/);
});

test('会话恢复和登录跳转均应保留已授权的参数化动态路由', async () => {
  const [mainSource, loginPageSource] = await Promise.all([
    readFile(mainFilePath, 'utf8'),
    readFile(loginPageFilePath, 'utf8')
  ]);

  assert.match(mainSource, /currentRoute\.matched\.some\(\(record\) => typeof record\.meta\.menuId === 'number' \|\| record\.meta\.m4PublicFlow === true\)/);
  assert.doesNotMatch(mainSource, /menuPaths\.includes\(currentPath\)/);
  assert.match(loginPageSource, /router\.resolve\(redirect\)\.matched\.some\(\(record\) => typeof record\.meta\.menuId === 'number' \|\| record\.meta\.m4PublicFlow === true\)/);
  assert.doesNotMatch(loginPageSource, /menuPaths\.includes\(redirect\)/);
});

test('题库、试卷和考试的内部页面按按钮权限注册且不参与菜单导航', async () => {
  const routeSource = await readFile(routeFilePath, 'utf8');

  assert.match(routeSource, /const ADMIN_INTERNAL_ROUTE_DEFINITIONS/);
  assert.match(routeSource, /question:question:add/);
  assert.match(routeSource, /paper:paper:edit/);
  assert.match(routeSource, /exam:exam:edit/);
  assert.match(routeSource, /function registerAuthorizedInternalRoutes/);
  assert.match(routeSource, /hasPermission\(userInfo, definition\.permission\)/);
  assert.match(routeSource, /hiddenInMenu: true/);
  assert.match(routeSource, /menuId: parent\.sourceRoute\.menu\.menuId/);
  assert.match(routeSource, /activeAuthorizedPaths = \[\.\.\.collectRoutePaths\(routes\), \.\.\.internalPaths\]/);
});
