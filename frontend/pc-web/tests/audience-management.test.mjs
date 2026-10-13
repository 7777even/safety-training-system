import assert from 'node:assert/strict';
import { readFile } from 'node:fs/promises';
import test from 'node:test';

const apiUrl = new URL('../src/api/audience.ts', import.meta.url);
const pageUrl = new URL('../src/views/admin/system/common/audience/index.vue', import.meta.url);

test('参与范围管理覆盖 Contract 的分页、规则替换、预览和冻结入口', async () => {
  const [api, page] = await Promise.all([readFile(apiUrl, 'utf8'), readFile(pageUrl, 'utf8')]);
  assert.match(api, /url:\s*'\/api\/common\/audiences'/);
  assert.match(api, /\/rules/);
  assert.match(api, /\/preview/);
  assert.match(api, /\/freeze/);
  assert.match(page, /参与范围/);
  assert.match(page, /预览成员/);
  assert.match(page, /冻结名单/);
});

test('audience form keeps normal admins in their session organization and validates required fields', async () => {
  const page = await readFile(pageUrl, 'utf8');
  assert.match(page, /canManageTenantScope/);
  assert.match(page, /currentTenantId/);
  assert.match(page, /v-if="canSelectOrganization"/);
  assert.match(page, /@change="changeOrganization"/);
  assert.match(page, /const formRef = ref<FormInstance>\(\)/);
  assert.match(page, /:rules="formRules"/);
  assert.match(page, /await formRef\.value\?\.validate\(\)\.catch/);
});
