import assert from 'node:assert/strict';
import { readFile } from 'node:fs/promises';
import test from 'node:test';

const trainingApiUrl = new URL('../src/api/training-learner.ts', import.meta.url);
const trainingDetailUrl = new URL('../src/pages/training/detail.vue', import.meta.url);
const noticeUrl = new URL('../src/pages/exam/notice.vue', import.meta.url);
const attemptUrl = new URL('../src/pages/exam/attempt.vue', import.meta.url);
const trainingIndexUrl = new URL('../src/pages/training/index.vue', import.meta.url);

test('培训理论考试仅待开始进入须知，进行中统一进入后直接恢复作答', async () => {
  const [api, detail, notice] = await Promise.all([readFile(trainingApiUrl, 'utf8'), readFile(trainingDetailUrl, 'utf8'), readFile(noticeUrl, 'utf8')]);
  assert.match(api, /getTrainingLearnerExamNotice/);
  assert.match(api, /\/exam-notice/);
  assert.match(detail, /function shouldReadExamNotice/);
  assert.match(detail, /\['PENDING', 'NOT_STARTED'\]/);
  assert.match(detail, /contentStatus\(content\)/);
  assert.match(detail, /pages\/exam\/notice\?trainingParticipantId=/);
  assert.match(detail, /enterTrainingContent/);
  assert.match(notice, /getTrainingLearnerExamNotice/);
  assert.match(notice, /enterTrainingContent/);
});

test('移动端终态或截止的作答不会停留在 00:00 页面', async () => {
  const attempt = await readFile(attemptUrl, 'utf8');
  assert.match(attempt, /function isTerminalAttempt/);
  assert.match(attempt, /PENDING_PUBLISH/);
  assert.match(attempt, /function getDeadlineTimestamp/);
  assert.match(attempt, /--:--/);
  assert.match(attempt, /goResult/);
  assert.match(attempt, /isDeadlineExpired/);
  assert.match(attempt, /onShow/);
  assert.doesNotMatch(attempt, /--remainingSeconds\.value/);
});

test('移动端已提交考试可回看本人答案', async () => {
  const result = await readFile(new URL('../src/pages/exam/result.vue', import.meta.url), 'utf8');
  assert.match(result, /答卷已提交/);
  assert.match(result, /getLearnerAnswerLines/);
  assert.match(result, /当前仅展示已提交内容/);
});

test('移动端答题页展示契约题型文案', async () => {
  const attempt = await readFile(new URL('../src/pages/exam/attempt.vue', import.meta.url), 'utf8');
  assert.match(attempt, /SINGLE: '单选题'/);
  assert.match(attempt, /MULTIPLE: '多选题'/);
  assert.match(attempt, /JUDGE: '判断题'/);
  assert.match(attempt, /FILL: '填空题'/);
});

test('移动端独立考试在 H5 防作弊开启时拦截剪贴板与右键操作', async () => {
  const attempt = await readFile(attemptUrl, 'utf8');

  assert.match(attempt, /function bindClipboardGuard\(\): void/);
  assert.match(attempt, /function releaseClipboardGuard\(\): void/);
  assert.match(attempt, /本考试已禁止复制粘贴，本次操作已记录/);
  assert.match(attempt, /'COPY'/);
  for (const eventType of ['copy', 'cut', 'paste', 'contextmenu']) {
    assert.match(attempt, new RegExp(`addEventListener\\('${eventType}', blockClipboard\\)`));
    assert.match(attempt, new RegExp(`removeEventListener\\('${eventType}', blockClipboard\\)`));
  }
  assert.match(attempt, /:class="\{ 'no-copy': isAnticheatEnabled \}"/);
});

test('移动端独立考试水印背景覆盖整个可视作答区域', async () => {
  const attempt = await readFile(attemptUrl, 'utf8');

  assert.match(attempt, /\.exam-attempt-page \{[\s\S]*min-height: 100vh;/);
  assert.match(attempt, /\.exam-attempt-page \{[\s\S]*margin: 0 -32rpx;/);
  assert.match(attempt, /\.exam-attempt-page \{[\s\S]*padding: 0 32rpx 48rpx;/);
  assert.match(attempt, /\.exam-attempt-page \{[\s\S]*background-image: var\(--exam-watermark-image, none\);/);
});

test('移动端多选题使用多选控件', async () => {
  const attempt = await readFile(new URL('../src/pages/exam/attempt.vue', import.meta.url), 'utf8');
  assert.match(attempt, /function isMultipleQuestion/);
  assert.match(attempt, /\['MULTIPLE', 'MULTIPLE_CHOICE'\]/);
  assert.match(attempt, /v-else-if="isMultipleQuestion\(currentQuestion\) && currentQuestion\.options\.length"/);
});

test('移动培训列表恢复状态标签与明确的执行入口', async () => {
  const source = await readFile(trainingIndexUrl, 'utf8');
  assert.match(source, /statusTone/);
  assert.match(source, /resultTone/);
  assert.match(source, /status-summary/);
  assert.match(source, /查看并执行/);
});
