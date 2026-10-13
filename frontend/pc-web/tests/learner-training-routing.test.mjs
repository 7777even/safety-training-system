import assert from 'node:assert/strict';
import { readFile } from 'node:fs/promises';
import test from 'node:test';
import ts from 'typescript';
import { createMemoryHistory, createRouter, RouterView } from 'vue-router';

/** 运行实际动态路由注册逻辑，仅替换构建期页面加载与布局依赖。 */
async function routesModule() {
  const source = await readFile(new URL('../src/router/admin-dynamic-routes.ts', import.meta.url), 'utf8');
  globalThis.__trainingRouteDependencies = {
    RouterView,
    hasPermission: (user, permission) => user?.perms?.includes(permission) ?? false
  };
  const script = source.replace(/^import .*;\r?\n/gm, '')
    .replace(/component: \(\) => import\('[^']+'\)/g, 'component: async () => ({default: {}})')
    .replace(/^const viewModules = .*;$/m, `const viewModules = {
      '../views/portal/home/index.vue': async () => ({default: {}}),
      '../views/portal/training/index.vue': async () => ({default: {}})
    };`);
  const output = ts.transpileModule(
    'const {RouterView,hasPermission}=globalThis.__trainingRouteDependencies; const AdminLayout={}, PortalLayout={};\n' + script,
    { compilerOptions: { module: ts.ModuleKind.ESNext, target: ts.ScriptTarget.ES2022 } }
  ).outputText;
  return import(`data:text/javascript;base64,${Buffer.from(output).toString('base64')}`);
}

test('只有首页菜单但具备本人培训权限时，任务与完整培训流程仍可导航', async () => {
  const module = await routesModule();
  const router = createRouter({ history: createMemoryHistory(), routes: [] });
  const home = { menuId: 1, menuName: '首页', menuType: 'M', path: '/portal/home', component: 'portal/home/index' };
  const user = { userId: 7, menus: [home], perms: ['training:learner:task'] };
  module.synchronizeMenuRoutes(router, user);
  for (const path of ['/portal/training/2', '/portal/training', '/portal/training/attempt/3',
    '/portal/training/2/contents/4/exam-notice', '/portal/training/attempt/3/result', '/portal/training/offline-session/5']) {
    assert.ok(router.resolve(path).matched.some(record => record.meta.menuType === 'INTERNAL'), path);
  }
  await router.push('/portal/training/2');
  assert.equal(router.currentRoute.value.params.trainingParticipantId, '2');
  assert.deepEqual(module.getAuthorizedPortalNavigation(user), [{ title: '首页', path: '/portal/home' }]);

  module.synchronizeMenuRoutes(router, { ...user, userId: 8, perms: [] });
  assert.equal(router.hasRoute('PortalTrainingDetail'), false, '切换为无培训权限账号必须移除入口');
  assert.equal(router.hasRoute('PortalTrainingTasks'), false);

  const training = { menuId: 2, menuName: '培训中心', menuType: 'M', path: '/portal/training', component: 'portal/training/index' };
  module.synchronizeMenuRoutes(router, { ...user, userId: 9, menus: [home, training] });
  assert.equal(router.resolve('/portal/training').name, 'Menu2', '已有培训菜单不被兼容入口覆盖');
  assert.equal(router.resolve('/portal/training/2').name, 'PortalTrainingDetail');
  delete globalThis.__trainingRouteDependencies;
});
