import assert from 'node:assert/strict';
import { readFile } from 'node:fs/promises';
import test from 'node:test';

const examWorkspaceUrl = new URL('../src/views/admin/business-center/exams/exam/workspace/index.vue', import.meta.url);
const paperWorkspaceUrl = new URL('../src/views/admin/business-center/exams/paper/workspace/index.vue', import.meta.url);

/** 工作台对外页面结构：根页面负责阶段编排，各阶段由独立 Panel 承载。 */
test('考试与试卷工作台在统一详情页承载各阶段 Panel', async () => {
  const [examWorkspace, paperWorkspace] = await Promise.all([readFile(examWorkspaceUrl, 'utf8'), readFile(paperWorkspaceUrl, 'utf8')]);

  for (const panel of ['ExamInformationPanel', 'ExamAssignmentPanel', 'ExamPublishCheck']) {
    assert.match(examWorkspace, new RegExp(`<${panel}`));
  }
  for (const panel of ['PaperBasicPanel', 'PaperComposePanel', 'PaperPublishPanel']) {
    assert.match(paperWorkspace, new RegExp(`<${panel}`));
  }
  assert.match(examWorkspace, /<main class="admin-detail-page__surface">/);
  assert.match(paperWorkspace, /<main class="admin-detail-page__surface">/);
});
