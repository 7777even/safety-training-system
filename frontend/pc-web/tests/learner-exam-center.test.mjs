import assert from 'node:assert/strict';
import { readFile } from 'node:fs/promises';
import test from 'node:test';

const taskPageUrl = new URL('../src/views/portal/exams/index.vue', import.meta.url);
const apiUrl = new URL('../src/api/learner-exam.ts', import.meta.url);
const attemptPageUrl = new URL('../src/views/portal/exams/attempt.vue', import.meta.url);
const noticePageUrl = new URL('../src/views/portal/exams/notice.vue', import.meta.url);
const resultPageUrl = new URL('../src/views/portal/exams/result.vue', import.meta.url);

/** 学员考试中心以页面交互和请求封装作为公开测试边界。 */
test('考试中心将任务筛选和分页完整映射到本人任务接口', async () => {
  const [pageSource, apiSource] = await Promise.all([readFile(taskPageUrl, 'utf8'), readFile(apiUrl, 'utf8')]);

  for (const token of ['query.taskStatus', 'query.examName', 'query.contextType', 'ElPagination', 'total', 'DEFAULT_LIST_PAGE_SIZE']) {
    assert.match(pageSource, new RegExp(token.replace('.', '\\.')));
  }
  assert.match(apiSource, /LearnerExamTaskQuery/);
});

test('课程来源任务只回到我的课程，独立任务才允许调用开考接口', async () => {
  const pageSource = await readFile(taskPageUrl, 'utf8');

  assert.match(pageSource, /isCourseExamTask/);
  assert.match(pageSource, /LEARNER_EXAM_PORTAL_PATH\.COURSE_CENTER/);
  assert.match(pageSource, /isStandaloneExamTask/);
});

test('未开始独立考试先进入考生须知页，由须知页正式创建作答', async () => {
  const [pageSource, noticeSource] = await Promise.all([readFile(taskPageUrl, 'utf8'), readFile(noticePageUrl, 'utf8')]);

  assert.match(pageSource, /name:\s*'PortalExamNotice'/);
  assert.doesNotMatch(pageSource, /await startExamAttempt\(/);
  assert.match(noticeSource, /getExam\(examId\)/);
  assert.match(noticeSource, /startExamAttempt/);
  assert.match(noticeSource, /createLearnerAttemptToken/);
});

test('考生须知页正式开始考试时携带稳定幂等令牌和可用的 PC 环境信息', async () => {
  const noticeSource = await readFile(noticePageUrl, 'utf8');

  assert.match(noticeSource, /createLearnerAttemptToken/);
  assert.match(noticeSource, /operatingSystem/);
  assert.match(noticeSource, /browser/);
  assert.doesNotMatch(noticeSource, /clientIp:/);
});

test('开考成功后通过内部作答路由进入考试详情，并显式处理跳转失败', async () => {
  const noticeSource = await readFile(noticePageUrl, 'utf8');

  assert.match(noticeSource, /name:\s*'PortalExamAttempt'/);
  assert.match(noticeSource, /isNavigationFailure/);
  assert.match(noticeSource, /router\.currentRoute\.value\.name !== 'PortalExamAttempt'/);
  assert.match(noticeSource, /starting/);
});

test('开始考试仅提交可存储的浏览器摘要，不提交完整 User-Agent', async () => {
  const noticeSource = await readFile(noticePageUrl, 'utf8');

  assert.match(noticeSource, /getBrowserSummary/);
  assert.match(noticeSource, /browser:\s*getBrowserSummary\(\)/);
  assert.doesNotMatch(noticeSource, /browser:\s*globalThis\.navigator\.userAgent/);
});

test('作答与结果页按实际耗时提交并只展示服务端返回的答案详情', async () => {
  const [attemptSource, resultSource] = await Promise.all([readFile(attemptPageUrl, 'utf8'), readFile(resultPageUrl, 'utf8')]);

  assert.match(attemptSource, /const usedSeconds = computed/);
  assert.match(attemptSource, /usedSeconds: usedSeconds\.value/);
  assert.match(resultSource, /答题回顾/);
  assert.match(resultSource, /hasAnswerReview/);
  assert.match(resultSource, /question\.answer\?\.isCorrect !== undefined/);
});

test('独立考试在手动提交或返回前保护未暂存答案', async () => {
  const attemptSource = await readFile(attemptPageUrl, 'utf8');

  assert.match(attemptSource, /const savedAnswerSnapshot = ref\(''\)/);
  assert.match(attemptSource, /const hasUnsavedAnswers = computed/);
  assert.match(attemptSource, /const payload = answerPayload\(\);/);
  assert.match(attemptSource, /const savedSnapshot = JSON\.stringify\(payload\);/);
  assert.match(attemptSource, /await saveExamAnswers\(attemptId, payload\);/);
  assert.match(attemptSource, /savedAnswerSnapshot\.value = savedSnapshot;/);
  assert.match(attemptSource, /savedAnswerSnapshot\.value = answerSnapshot\(\);/);
  assert.match(attemptSource, /async function ensureAnswersSaved\(action: '提交' \| '返回'\)/);
  assert.match(attemptSource, /confirmButtonText: '暂存并继续'/);
  assert.match(attemptSource, /cancelButtonText: '留在当前页'/);
  assert.match(attemptSource, /return save\(\);/);
  assert.match(attemptSource, /if \(!timeout && !\(await ensureAnswersSaved\('提交'\)\)\) return;/);
  assert.match(attemptSource, /async function backToExam\(\): Promise<void>/);
  assert.match(attemptSource, /if \(await ensureAnswersSaved\('返回'\)\) await router\.back\(\);/);
});

test('交卷后在成绩未发布时仅回看本人答案，不显示评分或解析', async () => {
  const resultSource = await readFile(resultPageUrl, 'utf8');

  assert.match(resultSource, /已提交答案/);
  assert.match(resultSource, /我的答案/);
  assert.match(resultSource, /v-if="attempt\.scoreVisible && attempt\.scorePublishStatus === 'PUBLISHED'"/);
});

test('等待成绩的独立考试可从考试中心直接进入答题回看', async () => {
  const pageSource = await readFile(taskPageUrl, 'utf8');

  assert.match(pageSource, /LEARNER_EXAM_ACTION\.VIEW_RESULT\s*\|\|\s*task\.availableAction === LEARNER_EXAM_ACTION\.WAIT_REVIEW/);
  assert.match(pageSource, /result\/\$\{task\.currentAttemptId\}/);
  assert.match(pageSource, /'查看答题情况'/);
  assert.match(pageSource, /\['CONTINUE', 'WAIT_REVIEW', 'VIEW_RESULT'\]\.includes\(task\.availableAction\)\s*&& Boolean\(task\.currentAttemptId\)/);
});

test('未录分的线下考试即使错误返回建议动作，也不展示须知或线上作答入口', async () => {
  const pageSource = await readFile(taskPageUrl, 'utf8');

  assert.match(pageSource, /function isOfflineExam\(task: LearnerExamTask\): boolean/);
  assert.match(pageSource, /if \(isOfflineExam\(task\) && task\.availableAction !== LEARNER_EXAM_ACTION\.VIEW_RESULT\)/);
  assert.match(pageSource, /if \(isOfflineExam\(task\)\)\s*\{\s*return task\.availableAction === LEARNER_EXAM_ACTION\.VIEW_RESULT/);
  assert.match(pageSource, /v-else-if="isOfflineExam\(activeTask\) \|\| !activeTask\.availableAction"/);
});

test('在线作答按后台题库的正式题型枚举展示并选择控件', async () => {
  const attemptSource = await readFile(attemptPageUrl, 'utf8');

  assert.match(attemptSource, /QUESTION_TYPE_OPTIONS/);
  assert.match(attemptSource, /questionType === 'MULTIPLE'/);
  assert.match(attemptSource, /\['FILL', 'ESSAY'\]/);
  assert.doesNotMatch(attemptSource, /MULTIPLE_CHOICE|FILL_BLANK|SINGLE_CHOICE|SHORT_ANSWER/);
});

test('考生须知页展示考试定义信息，作答页只承接已创建作答', async () => {
  const [noticeSource, attemptSource] = await Promise.all([readFile(noticePageUrl, 'utf8'), readFile(attemptPageUrl, 'utf8')]);

  for (const token of ['考生须知', '开始答题', '考试名称', '考试类型', '负责人', '开放时间', '考试须知', 'exam.durationSeconds', 'exam.passScore']) {
    assert.match(noticeSource, new RegExp(token.replace('.', '\\.')));
  }
  assert.match(attemptSource, /startAttemptRuntime\(\)/);
  assert.doesNotMatch(attemptSource, /考生须知|noticeAccepted|startAnswering|getExam\(/);
});

test('异常任务动作不会降级为开考，作答和结果页沿用学习中心页头', async () => {
  const [taskSource, attemptSource, resultSource] = await Promise.all([readFile(taskPageUrl, 'utf8'), readFile(attemptPageUrl, 'utf8'), readFile(resultPageUrl, 'utf8')]);

  assert.match(taskSource, /task\.availableAction !== LEARNER_EXAM_ACTION\.START/);
  assert.match(taskSource, /candidateStatus/);
  assert.match(taskSource, /attemptStatus/);
  assert.match(attemptSource, /page-heading__eyebrow/);
  assert.match(resultSource, /page-heading__eyebrow/);
  assert.match(resultSource, /getLearnerAnswerLines\(question\)/);
});

test('考试中心采用学习中心的门户横幅和网格卡片，不出现后台筛选表单', async () => {
  const pageSource = await readFile(taskPageUrl, 'utf8');

  assert.match(pageSource, /background:\s*linear-gradient\(120deg,\s*#155eef,\s*#4f8bff\)/);
  assert.match(pageSource, /grid-template-columns:\s*repeat\(auto-fill,\s*minmax\(280px,\s*1fr\)\)/);
  assert.doesNotMatch(pageSource, /<ElForm class="exam-filter"/);
});
