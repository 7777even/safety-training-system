import assert from 'node:assert/strict';
import {readFile} from 'node:fs/promises';
import test from 'node:test';

test('试卷管理列表进入统一工作台与只读版本页', async () => {
  const page = await readFile(new URL('../src/views/admin/business-center/exams/paper/index.vue', import.meta.url), 'utf8');
  for (const token of ['getPaperPage', '新增试卷', '编辑与组卷', '查看版本', '/paper/editor', '/version/']) {
    assert.match(page, new RegExp(token));
  }
});
