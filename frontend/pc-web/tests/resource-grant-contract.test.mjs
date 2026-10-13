import assert from 'node:assert/strict';
import { readFile } from 'node:fs/promises';
import test from 'node:test';

const apiUrl = new URL('../src/api/resource-grant.ts', import.meta.url);
const pageUrl = new URL('../src/views/admin/system/common/resource-grant/index.vue', import.meta.url);

test('resource grants expose contract detail and access-check API methods', async () => {
  const api = await readFile(apiUrl, 'utf8');
  assert.match(api, /getResourceGrantDetail/);
  assert.match(api, /checkResourceGrant/);
  assert.match(api, /\/api\/common\/resource-grants\/check/);
});

test('resource grant UI filters, selects related records, manages validity and checks access', async () => {
  const page = await readFile(pageUrl, 'utf8');
  assert.match(page, /query\.ownerOrganizationId/);
  assert.match(page, /CourseSelect/);
  assert.doesNotMatch(page, /kind="RESOURCE"/);
  assert.match(page, /type="datetimerange"/);
  assert.match(page, /showDetail/);
  assert.match(page, /checkAccess/);
  assert.match(page, /ElSwitch/);
});
