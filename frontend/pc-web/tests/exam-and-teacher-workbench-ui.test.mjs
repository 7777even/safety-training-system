import assert from 'node:assert/strict';
import { readFile } from 'node:fs/promises';
import test from 'node:test';
import { URL } from 'node:url';

const resultsUrl = new URL('../src/views/admin/business-center/exams/exam/components/results/index.vue', import.meta.url);
const teacherTaskUrl = new URL('../src/views/admin/business-center/training/teacher-tasks/detail/index.vue', import.meta.url);

test('成绩处置以紧凑摘要、操作区和发布流水组织既有作答事实', async () => {
  const source = await readFile(resultsUrl, 'utf8');

  assert.match(source, /class="score-disposition-hero"/);
  assert.match(source, /class="score-disposition-summary"/);
  assert.match(source, /class="score-disposition-actions"/);
  assert.match(source, /class="score-publish-history"/);
  assert.match(source, /publishExamScore/);
  assert.match(source, /createRetakeAttempt/);
});

test('线下教学任务以状态横幅、指标卡和名单成绩区组织既有操作', async () => {
  const source = await readFile(teacherTaskUrl, 'utf8');

  assert.match(source, /class="[^"]*teacher-task-hero/);
  assert.match(source, /class="[^"]*teacher-task-metrics/);
  assert.match(source, /class="[^"]*teacher-task-roster/);
  assert.match(source, /class="[^"]*teacher-task-status/);
  assert.match(source, /availableActions/);
  assert.match(source, /upsertOfflineScore/);
});
