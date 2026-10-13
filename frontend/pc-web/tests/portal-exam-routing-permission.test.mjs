import assert from 'node:assert/strict';
import {readFile} from 'node:fs/promises';
import test from 'node:test';

const routerUrl = new URL('../src/router/admin-dynamic-routes.ts', import.meta.url);
const taskPageUrl = new URL('../src/views/portal/exams/index.vue', import.meta.url);
const attemptPageUrl = new URL('../src/views/portal/exams/attempt.vue', import.meta.url);

/** 门户考试中心的公开边界：来源菜单和按钮权限齐备时才能解析内部须知、作答与结果路由。 */
test('门户考试中心按来源菜单和权限注册内部须知、作答、结果路由', async () => {
  const source = await readFile(routerUrl, 'utf8');

  assert.match(source, /PORTAL_INTERNAL_ROUTE_DEFINITIONS/);
  assert.match(source, /\/portal\/exams\/:examId\/notice\/:examCandidateId/);
  assert.match(source, /\/portal\/exams\/:examId\/attempt\/:examAttemptId/);
  assert.match(source, /\/portal\/exams\/:examId\/result\/:examAttemptId/);
  assert.match(source, /exam:attempt:start/);
  assert.match(source, /exam:learner:attempt:view/);
  assert.match(source, /registerAuthorizedPortalInternalRoutes/);
  assert.match(source, /hiddenInMenu: true/);
});

/** 按钮展示边界：页面不得把菜单可见性当作作答操作权限。 */
test('门户考试页按 profile F 权限控制开始、暂存和提交入口', async () => {
  const [taskPage, attemptPage] = await Promise.all([readFile(taskPageUrl, 'utf8'), readFile(attemptPageUrl, 'utf8')]);

  assert.match(taskPage, /hasPermission/);
  assert.match(taskPage, /exam:attempt:start/);
  assert.match(taskPage, /exam:learner:attempt:view/);
  assert.match(attemptPage, /hasPermission/);
  assert.match(attemptPage, /exam:attempt:answer/);
  assert.match(attemptPage, /exam:attempt:submit/);
});
