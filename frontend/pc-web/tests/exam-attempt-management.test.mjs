import assert from 'node:assert/strict';
import { readFile } from 'node:fs/promises';
import test from 'node:test';

const workspaceUrl = new URL('../src/views/admin/business-center/exams/exam/workspace/index.vue', import.meta.url);
const panelUrl = new URL('../src/views/admin/business-center/exams/exam/components/ExamAttemptManagementPanel.vue', import.meta.url);
const resultPageUrl = new URL('../src/views/admin/business-center/exams/exam/results/index.vue', import.meta.url);
const resultListUrl = new URL('../src/views/admin/business-center/exams/results/index.vue', import.meta.url);

test('考试工作台按当前考试展示学员作答并复用批阅和成绩处置入口', async () => {
  const [workspace, panel] = await Promise.all([readFile(workspaceUrl, 'utf8'), readFile(panelUrl, 'utf8')]);

  assert.match(workspace, /ExamAttemptManagementPanel/);
  assert.match(workspace, /name: 'attempts'/);
  assert.match(panel, /getExamAttemptRecordPage/);
  assert.match(panel, /'query\.examId': props\.examId/);
  assert.match(panel, /exam:review/);
  assert.match(panel, /exam:score:publish/);
  assert.match(panel, /grading/);
  assert.match(panel, /results/);
});

test('成绩处置通过作答路径和考生标识定位，支持为指定考生开补考', async () => {
  const [panel, resultPage, resultList] = await Promise.all([
    readFile(panelUrl, 'utf8'),
    readFile(resultPageUrl, 'utf8'),
    readFile(resultListUrl, 'utf8')
  ]);

  assert.match(panel, /examCandidateId: row\.examCandidateId/);
  assert.match(resultList, /examCandidateId: row\.examCandidateId/);
  assert.match(resultPage, /route\.query\.examCandidateId/);
  assert.match(resultPage, /examCandidateId/);
});

test('成绩处置处理本次作答的批阅、成绩发布、发布流水和既有补考入口', async () => {
  const resultPage = await readFile(resultPageUrl, 'utf8');

  assert.match(resultPage, /publishExamScore/);
  assert.match(resultPage, /getExamScorePublishLogs/);
  assert.match(resultPage, /返回批阅/);
  assert.match(resultPage, /发布正式成绩/);
  assert.match(resultPage, /成绩发布流水/);
  assert.match(resultPage, /createRetakeAttempt/);
  assert.doesNotMatch(resultPage, /resetPractice/);
  assert.match(resultPage, /开补考/);
  assert.doesNotMatch(resultPage, /练习重置/);
});
