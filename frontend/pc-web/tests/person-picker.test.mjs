import assert from 'node:assert/strict';
import { readFile } from 'node:fs/promises';
import test from 'node:test';

const apiUrl = new URL('../src/api/person-picker.ts', import.meta.url);
const componentUrl = new URL('../src/components/organization/PersonPickerDialog.vue', import.meta.url);

test('统一人员选择器使用 common/person-picker 契约并支持 Mock 切换', async () => {
  const api = await readFile(apiUrl, 'utf8');
  assert.match(api, /\/api\/common\/person-picker/);
  assert.match(api, /VITE_USE_API_MOCK/);
  assert.match(api, /query\.organizationId/);
  assert.match(api, /query\.keyword/);
  assert.match(api, /query\.deptId/);
  assert.match(api, /query\.tagId/);
  assert.match(api, /query\.positionId/);
});

test('人员选择弹窗提供机构筛选、分页多选和确认输出', async () => {
  const component = await readFile(componentUrl, 'utf8');
  assert.match(component, /currentTenantId/);
  assert.match(component, /query\.organizationId/);
  assert.match(component, /ElTable/);
  assert.match(component, /type="selection"/);
  assert.match(component, /ElPagination/);
  assert.match(component, /confirm: \[items: PersonPickerItem\[\]\]/);
});
