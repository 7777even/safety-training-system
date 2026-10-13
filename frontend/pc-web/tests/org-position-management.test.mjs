import assert from 'node:assert/strict';
import { readFile } from 'node:fs/promises';
import test from 'node:test';

const apiUrl = new URL('../src/api/org-position.ts', import.meta.url);
const pageUrl = new URL('../src/views/admin/system/org/position/index.vue', import.meta.url);

test('机构岗位管理使用 Contract 的分页与 CRUD 地址', async () => {
  const [api, page] = await Promise.all([readFile(apiUrl, 'utf8'), readFile(pageUrl, 'utf8')]);
  assert.match(api, /url: '\/api\/org\/positions'/);
  assert.match(api, /url: `\/api\/org\/positions\/\$\{positionId\}`/);
  assert.match(api, /'query\.positionCode'/);
  assert.match(page, /label="岗位编码"/);
  assert.match(page, /placeholder="请输入岗位名称"/);
  assert.match(page, /确认删除岗位/);
  assert.doesNotMatch(page, /hasPermission|v-if="can\(/);
});
