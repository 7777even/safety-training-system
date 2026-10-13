import assert from 'node:assert/strict';
import { readFileSync } from 'node:fs';
import test from 'node:test';

const api = readFileSync(new URL('../src/api/regulation.ts', import.meta.url), 'utf8');
const routes = readFileSync(new URL('../src/router/admin-dynamic-routes.ts', import.meta.url), 'utf8');
const request = readFileSync(new URL('../src/api/request.ts', import.meta.url), 'utf8');
const warnings = readFileSync(new URL('../src/views/admin/regulation/warnings/index.vue', import.meta.url), 'utf8');
const plans = readFileSync(new URL('../src/views/admin/regulation/plans/index.vue', import.meta.url), 'utf8');
const workbench = readFileSync(new URL('../src/views/admin/regulation/workbench/index.vue', import.meta.url), 'utf8');
const regulationOpenApi = JSON.parse(
  readFileSync(new URL('../../../docs/api/regulation.openapi.json', import.meta.url), 'utf8')
);
const systemAdminOpenApi = JSON.parse(
  readFileSync(new URL('../../../docs/api/system-admin.openapi.json', import.meta.url), 'utf8')
);

test('监管 PC 只消费当前 OpenAPI，目标租户只作用于计划只读请求', () => {
  for (const path of ['/api/regulation/workbench', '/api/regulation/learning-stats', '/api/regulation/warnings', '/api/regulation/training-plans']) {
    assert.ok(api.includes(path), path);
  }
  assert.match(api, /setTargetTenantId\(organizationId\)/);
  assert.match(api, /clearTargetTenantId\(\)/);
  assert.match(request, /config\.url\?\.startsWith\('\/api\/regulation\/training-plans'\)/);
  assert.doesNotMatch(routes, /RegulatorTenantAggregates|regulator:aggregate:view/);
  assert.match(plans, /onBeforeUnmount\(leaveRegulationPlanView\)/);
  assert.match(warnings, /getRegulationWarning\(warningId\)/);
  assert.match(warnings, /detailVisible\.value = true;\s*await load\(\)/);
  assert.doesNotMatch(warnings, /\/view['`]/);
  assert.match(workbench, /item\.granted && hasPermission/);
});

test('监管会话按渠道收敛后台导航，业务模块不作一级入口', () => {
  // 监管角色为支撑只读下钻被授予应用中心的路由祖先菜单，若不收敛，监管账号会看到
  // 点进去必然 403 的业务模块（应用中心 9530 / 企业工作台 9800）。
  assert.match(routes, /REGULATOR_HIDDEN_ADMIN_MODULE_IDS\s*=\s*new Set\(\[9530,\s*9800\]\)/);
  assert.match(routes, /orgType === 'REGULATOR'/);
  assert.match(routes, /regulatorChannel\s*\|\|\s*!REGULATOR_HIDDEN_ADMIN_MODULE_IDS\.has\(/);
  // 监管工作台不再提供必然 403 的课程制作入口，契约枚举同步收敛为两项。
  assert.deepEqual(
    regulationOpenApi.components.schemas.WorkbenchShortcutVO.properties.code.enum,
    ['CERTIFICATE', 'DATA_CENTER']
  );
  // profile 回填会话租户类型，供上述渠道判定使用。
  assert.ok(systemAdminOpenApi.components.schemas.UserVO.properties.orgType, 'UserVO 必须声明 orgType');
  assert.deepEqual(
    systemAdminOpenApi.components.schemas.UserVO.properties.orgType.enum,
    ['REGULATOR', 'ENTERPRISE', 'TRAINING_INSTITUTION']
  );
});

test('渠道收敛只作用于顶部导航，归属查找仍用完整模块列表', () => {
  // 证书管理挂在应用中心 9530 下，且 /admin/result 不落在任何模块 URL 前缀内，归属靠「按菜单树回溯」。
  // 若归属查找也用被收敛过的列表，监管点开证书管理会找不到归属模块 ⇒ 整条侧栏消失。
  assert.match(routes, /return buildAdminModules\(userInfo,\s*true\)/);
  assert.match(routes, /const modules = buildAdminModules\(userInfo,\s*false\)/);
});
