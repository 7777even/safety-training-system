import assert from 'node:assert/strict';
import { readFile } from 'node:fs/promises';
import test from 'node:test';

const apiUrl = new URL('../src/api/organization.ts', import.meta.url);
const pageUrl = new URL('../src/views/admin/system/org/organization/index.vue', import.meta.url);
test('机构开户与编辑按契约覆盖培训机构、管理员摘要密码和类型修改', async () => {
  const [api, page] = await Promise.all([readFile(apiUrl, 'utf8'), readFile(pageUrl, 'utf8')]);

  assert.match(api, /'TRAINING_INSTITUTION'/);
  assert.match(api, /orgType\?: Exclude<OrganizationType, 'REGULATOR'>/);
  assert.doesNotMatch(api, /organizationMock|VITE_USE_API_MOCK/);
  assert.match(page, /await sha256\(form\.adminPassword\)/);
  assert.match(page, /orgType: form\.orgType === 'REGULATOR' \? undefined : form\.orgType/);
  assert.match(page, /value: 'TRAINING_INSTITUTION'/);
  assert.doesNotMatch(page, /value: 'REGULATOR'/);
});
