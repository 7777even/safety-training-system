import assert from 'node:assert/strict';
import { readFileSync } from 'node:fs';
import test from 'node:test';

const api = readFileSync(new URL('../src/api/ops-workbench.ts', import.meta.url), 'utf8');
const page = readFileSync(new URL('../src/views/admin/ops/workbench/index.vue', import.meta.url), 'utf8');
const menus = JSON.parse(readFileSync(new URL('../../../backend/sts-service-admin-init/src/main/resources/data/menus.json', import.meta.url), 'utf8'));

test('企业工作台消费正式接口并按权限呈现真实待办', () => {
  assert.match(api, /\/api\/ops\/workbench\/enterprise/);
  assert.match(page, /hasPermission\(userStore\.userInfo, item\.permission\)/);
  assert.match(page, /openTodoCount/);
  assert.match(page, /DRAFT_COURSE/);
  assert.match(page, /DRAFT_PLAN/);
  assert.match(page, /PENDING_PARTICIPANT/);
  assert.doesNotMatch(page, /mock|Mock|Math\.random/);
  assert.equal(menus.find((menu) => menu.id === 9800)?.path, '/admin/ops');
  assert.equal(menus.find((menu) => menu.id === 9801)?.comp, 'ops/workbench/index');
});
