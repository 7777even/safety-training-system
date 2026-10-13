import assert from 'node:assert/strict';
import { readFile } from 'node:fs/promises';
import test from 'node:test';

const workspaceUrl = new URL('../src/views/admin/business-center/exams/exam/workspace/index.vue', import.meta.url);

test('线上考试和专项练习未绑定试卷版本时不能进入人员配置', async () => {
  const workspace = await readFile(workspaceUrl, 'utf8');

  assert.match(workspace, /function requiresPaperVersion\(examType: string\): boolean/);
  assert.match(workspace, /if \(requiresPaperVersion\(form\.examType\) && !form\.paperVersionId\)/);
  assert.match(workspace, /请先选择已发布试卷版本，再配置考生。/);
});
