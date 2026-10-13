import assert from 'node:assert/strict';
import { readFile } from 'node:fs/promises';
import test from 'node:test';

test('试卷工作台和版本页标题区显示已有状态标签', async () => {
  const [workspace, version] = await Promise.all([
    readFile(new URL('../src/views/admin/business-center/exams/paper/workspace/index.vue', import.meta.url), 'utf8'),
    readFile(new URL('../src/views/admin/business-center/exams/paper/version/index.vue', import.meta.url), 'utf8')
  ]);

  for (const source of [workspace, version]) {
    const header = source.match(/<header class="admin-detail-page__header">([\s\S]*?)<\/header>/)?.[1] ?? '';
    assert.match(header, /ElTag/);
  }
  assert.match(workspace, /basic\.status \? '启用' : '停用'/);
  assert.match(version, /publishStatusTag\(version\.publishedStatus\)/);
});
