import assert from 'node:assert/strict';
import { readFile } from 'node:fs/promises';
import test from 'node:test';

const trainingDetailUrl = new URL('../src/views/portal/training/detail.vue', import.meta.url);
const noticeUrl = new URL('../src/views/portal/exams/notice.vue', import.meta.url);
const trainingApiUrl = new URL('../src/api/training-learner.ts', import.meta.url);
const routerUrl = new URL('../src/router/admin-dynamic-routes.ts', import.meta.url);
const attemptUrl = new URL('../src/views/portal/exams/attempt.vue', import.meta.url);
const trainingIndexUrl = new URL('../src/views/portal/training/index.vue', import.meta.url);

/** 培训理论考试须知与绝对截止时间的用户可见入口回归。 */
test('待开始培训理论考试先读须知，进行中恢复作答，题库练习仍直接开始', async () => {
  const [detailSource, noticeSource, apiSource, routerSource] = await Promise.all([
    readFile(trainingDetailUrl, 'utf8'), readFile(noticeUrl, 'utf8'), readFile(trainingApiUrl, 'utf8'), readFile(routerUrl, 'utf8')
  ]);

  assert.match(detailSource, /content\.contentType === 'EXAM'/);
  assert.match(detailSource, /PortalTrainingExamNotice/);
  assert.match(detailSource, /\['EXAM', 'QUESTION_BANK'\]\.includes\(content\.contentType\)/);
  const noticeEligibility = detailSource.match(/function shouldReadExamNotice[\s\S]*?\n}/)?.[0] ?? '';
  assert.match(noticeEligibility, /contentStatus\(content\)/);
  assert.match(noticeEligibility, /'PENDING', 'NOT_STARTED'/);
  assert.doesNotMatch(noticeEligibility, /IN_PROGRESS/);
  assert.match(noticeSource, /getTrainingLearnerExamNotice/);
  assert.match(noticeSource, /enterTrainingContent/);
  assert.match(apiSource, /contents\/\$\{planContentId\}\/exam-notice/);
  assert.match(routerSource, /name: 'PortalTrainingExamNotice'/);
});

test('作答页以可解析的服务端截止时间决定本地计时，不用无效时间自动交卷', async () => {
  const attemptSource = await readFile(attemptUrl, 'utf8');

  assert.match(attemptSource, /isValidDeadline/);
  assert.match(attemptSource, /作答截止时间异常/);
  assert.match(attemptSource, /remainingTimeText/);
  assert.match(attemptSource, /deadlineTime.*Date\.parse|Date\.parse.*deadlineTime/s);
  assert.doesNotMatch(attemptSource, /remainingSeconds\.value -= 1;\s*if \(remainingSeconds\.value <= 0\)/s);
});

test('已提交待发布成绩的培训作答不再停留在零秒答题页', async () => {
  const attemptSource = await readFile(attemptUrl, 'utf8');
  assert.match(attemptSource, /function isTerminalAttempt/);
  assert.match(attemptSource, /PENDING_PUBLISH/);
  assert.match(attemptSource, /isTerminalAttempt\(attempt\.value\)/);
});

test('培训列表恢复状态标签与紧凑卡片展示', async () => {
  const source = await readFile(trainingIndexUrl, 'utf8');
  assert.match(source, /statusTagType/);
  assert.match(source, /resultTagType/);
  assert.match(source, /training-card__status-summary/);
  assert.match(source, /查看并执行/);
});
