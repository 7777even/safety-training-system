import assert from 'node:assert/strict';
import { access, readFile } from 'node:fs/promises';
import test from 'node:test';

test('移动端考试提供须知、任务、作答和结果独立页面，并保留 Mock 切换', async () => {
  const pages = ['../src/pages/exam/index.vue', '../src/pages/exam/notice.vue', '../src/pages/exam/attempt.vue', '../src/pages/exam/result.vue'];
  await Promise.all(pages.map((path) => access(new URL(path, import.meta.url))));

  const apiSource = await readFile(new URL('../src/api/learner-exam.ts', import.meta.url), 'utf8');
  assert.match(apiSource, /VITE_USE_API_MOCK/);
  assert.match(apiSource, /默认调用线上真实接口/);
  assert.match(apiSource, /\/api\/exam\/learner\/tasks/);
  assert.match(apiSource, /recordExamAnticheatEvent/);
  assert.match(apiSource, /\/api\/exam\/attempts\/\$\{examAttemptId\}\/anticheat-events/);
  assert.match(apiSource, /getLearnerExamNotice/);
  assert.match(apiSource, /\/api\/exam\/exams\/\$\{examId\}/);
});

test('移动端未开始考试先阅览须知，点击开始后替换进入答题页', async () => {
  const [indexSource, noticeSource, pagesSource] = await Promise.all([
    readFile(new URL('../src/pages/exam/index.vue', import.meta.url), 'utf8'),
    readFile(new URL('../src/pages/exam/notice.vue', import.meta.url), 'utf8'),
    readFile(new URL('../src/pages.json', import.meta.url), 'utf8')
  ]);

  assert.match(indexSource, /availableAction === 'START'/);
  assert.match(indexSource, /\/pages\/exam\/notice\?examId=\$\{task\.examId\}/);
  assert.doesNotMatch(indexSource, /const attempt = await startExamAttempt/);
  assert.match(noticeSource, /getLearnerExamNotice/);
  assert.match(noticeSource, /startExamAttempt/);
  assert.match(noticeSource, /uni\.redirectTo\(\{ url: `\/pages\/exam\/attempt/);
  assert.match(noticeSource, /exam\.value\.passScore \/ exam\.value\.passRatio/);
  assert.match(pagesSource, /"path": "pages\/exam\/notice"/);
});

test('移动端考试中心展示契约中的类型和开放时间，线下考试不提供须知或开考入口', async () => {
  const indexSource = await readFile(new URL('../src/pages/exam/index.vue', import.meta.url), 'utf8');

  assert.match(indexSource, /getExamTypeText/);
  assert.match(indexSource, /开放时间 \{\{ getOpenTimeText\(task\) \}\}/);
  assert.match(indexSource, /const isOfflineExam/);
  assert.match(indexSource, /isOfflineExam\(task\)/);
  assert.match(indexSource, /const canHandleTask = \(task: LearnerExamTask\): boolean => !isOfflineExam\(task\) && Boolean\(task\.availableAction\)/);
  assert.match(indexSource, /v-if="canHandleTask\(task\)"/);
  assert.match(indexSource, /等待管理端录入成绩/);
});

test('移动端题库练习结果与 PC 一致：有效结果可回看，EFFECTIVE 状态可展示练习得分', async () => {
  const [indexSource, resultSource] = await Promise.all([
    readFile(new URL('../src/pages/exam/index.vue', import.meta.url), 'utf8'),
    readFile(new URL('../src/pages/exam/result.vue', import.meta.url), 'utf8')
  ]);

  assert.match(indexSource, /task\.availableAction === 'VIEW_RESULT' \|\| task\.availableAction === 'WAIT_REVIEW'/);
  assert.match(indexSource, /\/pages\/exam\/result\?examAttemptId=\$\{task\.currentAttemptId\}/);
  assert.match(indexSource, /WAIT_REVIEW: '查看答题情况'/);
  assert.match(indexSource, /BANK_PRACTICE/);
  assert.match(indexSource, /练习得分/);
  assert.match(resultSource, /\['PUBLISHED', 'EFFECTIVE'\]\.includes\(attempt\.value\.scorePublishStatus\)/);
});

test('移动端作答记录切离考试页面与超时交卷事件，结果页按字段裁剪答案解析', async () => {
  const [attemptSource, resultSource] = await Promise.all([
    readFile(new URL('../src/pages/exam/attempt.vue', import.meta.url), 'utf8'),
    readFile(new URL('../src/pages/exam/result.vue', import.meta.url), 'utf8')
  ]);

  assert.match(attemptSource, /recordExamAnticheatEvent/);
  assert.match(attemptSource, /SWITCH_AWAY/);
  assert.match(attemptSource, /TIMEOUT_SUBMIT/);
  assert.match(resultSource, /hasStandardAnswer/);
  assert.match(resultSource, /question\.analysisSnapshot/);
  assert.match(resultSource, /pending-result-hero/);
  assert.match(resultSource, /submitted-answers__summary/);
  assert.match(resultSource, /analysis--submitted/);
});

test('移动端独立考试在交卷和返回前先暂存本地答案', async () => {
  const attemptSource = await readFile(new URL('../src/pages/exam/attempt.vue', import.meta.url), 'utf8');

  assert.match(attemptSource, /async function ensureAnswersSaved\(\): Promise<boolean>/);
  assert.match(attemptSource, /if \(!\(await ensureAnswersSaved\(\)\)\) return;/);
  assert.match(attemptSource, /async function goBack\(\): Promise<void>/);
  assert.match(attemptSource, /@click="goBack"/);
});
