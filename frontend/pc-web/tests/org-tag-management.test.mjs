import assert from 'node:assert/strict';
import { readFile } from 'node:fs/promises';
import test from 'node:test';

const apiUrl = new URL('../src/api/org-tag.ts', import.meta.url);
const pageUrl = new URL('../src/views/admin/system/org/tag/index.vue', import.meta.url);

test('机构标签管理使用 Contract 的分页与 CRUD 地址', async () => {
  const [api, page] = await Promise.all([readFile(apiUrl, 'utf8'), readFile(pageUrl, 'utf8')]);

  assert.match(api, /url: '\/api\/org\/tags'/);
  assert.match(api, /url: `\/api\/org\/tags\/\$\{tagId\}`/);
  assert.match(api, /'query\.organizationId'/);
  assert.match(page, /label="所属机构 ID"/);
  assert.match(page, /placeholder="请输入标签名称"/);
  assert.match(page, /确认删除标签/);
  assert.doesNotMatch(page, /hasPermission|v-if="can\(/);
});
