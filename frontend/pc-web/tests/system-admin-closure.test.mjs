import assert from 'node:assert/strict';
import {readFile} from 'node:fs/promises';
import test from 'node:test';

const read = (path) => readFile(new URL(path, import.meta.url), 'utf8');

test('system user form exposes member extension fields as selectable values', async () => {
  const source = await read('../src/views/admin/system/org/user/index.vue');
  assert.match(source, /userForm\.memberType/);
  assert.match(source, /userForm\.email/);
  assert.match(source, /userForm\.jobTitle/);
  assert.match(source, /userForm\.entryDate/);
  assert.match(source, /userForm\.tagIds/);
  assert.match(source, /userForm\.positionIds/);
  assert.doesNotMatch(source, /tagIdsText|positionIdsText/);
});

test('organization-scoped management pages distinguish global tenant operators', async () => {
  for (const relativePath of [
    '../src/views/admin/system/org/tag/index.vue',
    '../src/views/admin/system/org/position/index.vue',
    '../src/views/admin/system/org/dept/index.vue',
    '../src/views/admin/system/org/role/index.vue'
  ]) {
    const source = await read(relativePath);
    assert.match(source, /canSelectTenant|canManageTenantScope/);
  }
});

test('session logout clears target tenant and request maps regulator guard error codes', async () => {
  const userStore = await read('../src/stores/modules/user.ts');
  const request = await read('../src/api/request.ts');
  assert.match(userStore, /clearTargetTenantId/);
  assert.match(request, /4061|4062|4063|4064|4065/);
});
