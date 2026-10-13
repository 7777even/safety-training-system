import assert from 'node:assert/strict';
import { readFile } from 'node:fs/promises';
import test from 'node:test';
import ts from 'typescript';

const gradingPageUrl = new URL('../src/views/admin/business-center/exams/exam/components/grading/index.vue', import.meta.url);
const informationPanelUrl = new URL('../src/views/admin/business-center/exams/exam/components/ExamInformationPanel.vue', import.meta.url);
const reviewStateUrl = new URL('../src/views/admin/business-center/exams/exam/components/exam-review-state.ts', import.meta.url);
const tabStateUrl = new URL('../src/layouts/admin-tab-state.ts', import.meta.url);

/** 将无运行时依赖的 TypeScript 状态工具转译为 Node 可执行模块。 */
async function loadTypeScriptModule(url) {
  const source = await readFile(url, 'utf8');
  const output = ts.transpileModule(source, { compilerOptions: { module: ts.ModuleKind.ESNext, target: ts.ScriptTarget.ES2022 } }).outputText;
  return import(`data:text/javascript;base64,${Buffer.from(output).toString('base64')}#${Math.random()}`);
}

test('已发布正式成绩的作答在教师批阅页只能查看，不能提交改分', async () => {
  const [gradingPage, reviewState] = await Promise.all([readFile(gradingPageUrl, 'utf8'), loadTypeScriptModule(reviewStateUrl)]);

  assert.equal(reviewState.isScorePublished('PUBLISHED'), true);
  assert.equal(reviewState.isScorePublished('UNPUBLISHED'), false);
  assert.equal(reviewState.isScorePublished(undefined), false);
  assert.match(gradingPage, /import \{ isScorePublished as isScorePublishedStatus \} from '\.\.\/exam-review-state';/);
  assert.match(gradingPage, /const isScorePublished = computed\(\(\) => isScorePublishedStatus\(attempt\.value\?\.scorePublishStatus\)\);/);
  assert.match(gradingPage, /if \(isScorePublished\.value\) \{/);
  assert.match(gradingPage, /正式成绩已发布，当前作答不可再批阅或改分/);
  assert.match(gradingPage, /<ElAlert\s+v-if="isScorePublished"/);
  assert.match(gradingPage, /<ElInputNumber[\s\S]*?:disabled="isScorePublished"/);
  assert.match(gradingPage, /<ElInput[\s\S]*?:disabled="isScorePublished"/);
  assert.match(gradingPage, /type="primary"\s+:disabled="isScorePublished"/);
});

test('教师批阅在保存前校验全部人工题，并以统一入口顺序保存', async () => {
  const gradingPage = await readFile(gradingPageUrl, 'utf8');

  assert.match(gradingPage, /const pendingQuestionNumbers = computed/);
  assert.match(gradingPage, /async function saveAllScores\(\): Promise<void>/);
  assert.match(gradingPage, /请先完成第 \$\{pendingQuestionNumbers\.value\.join\('、'\)\} 题评分/);
  assert.match(gradingPage, /for \(const question of manualQuestions\.value\)/);
  assert.match(gradingPage, /reviewExamAnswer\(answerId/);
  assert.match(gradingPage, /部分评分保存失败/);
  assert.match(gradingPage, /保存全部评分/);
  assert.doesNotMatch(gradingPage, /该题批阅已保存/);
});

test('考试和课程工作台按资源归并 tabs-card，阶段切换不追加同名页签', async () => {
  const tabState = await loadTypeScriptModule(tabStateUrl);
  const examInformation = { path: '/admin/business-center/exams/exam/3', fullPath: '/admin/business-center/exams/exam/3', params: { examId: '3' }, query: {} };
  const examAssignment = { ...examInformation, fullPath: '/admin/business-center/exams/exam/3?stage=assignment', query: { stage: 'assignment' } };
  const examCreate = { path: '/admin/business-center/exams/exam/create', fullPath: '/admin/business-center/exams/exam/create', params: {}, query: {} };
  const courseBasic = { path: '/admin/business-center/course/center', fullPath: '/admin/business-center/course/center?courseId=5&stage=basic', params: {}, query: { courseId: '5', stage: 'basic' } };
  const courseOffering = { ...courseBasic, fullPath: '/admin/business-center/course/center?courseId=5&stage=offering', query: { courseId: '5', stage: 'offering' } };
  const courseCreate = { path: '/admin/business-center/course/center', fullPath: '/admin/business-center/course/center?mode=create&stage=basic', params: {}, query: { mode: 'create', stage: 'basic' } };
  const courseList = { path: '/admin/business-center/course/center', fullPath: '/admin/business-center/course/center', params: {}, query: {} };

  assert.equal(tabState.getAdminTabKey(examInformation), tabState.getAdminTabKey(examAssignment));
  assert.equal(tabState.getAdminTabKey(courseBasic), tabState.getAdminTabKey(courseOffering));
  assert.notEqual(tabState.getAdminTabKey(examCreate), tabState.getAdminTabKey(examInformation));
  assert.notEqual(tabState.getAdminTabKey(courseCreate), tabState.getAdminTabKey(courseList));
  assert.notEqual(tabState.getAdminTabKey(examInformation), tabState.getAdminTabKey({ ...examInformation, params: { examId: '4' }, path: '/admin/business-center/exams/exam/4' }));
  assert.notEqual(tabState.getAdminTabKey(courseBasic), tabState.getAdminTabKey({ ...courseBasic, query: { courseId: '6' } }));
  assert.equal(tabState.getAdminTabTitle(examCreate, '考试工作台'), '新增考试');
  assert.equal(tabState.getAdminTabTitle(examInformation, '考试工作台'), '考试 #3 · 考试信息');
  assert.equal(tabState.getAdminTabTitle(courseList, '课程中心'), '课程列表');
  assert.equal(tabState.getAdminTabTitle(courseCreate, '课程中心'), '新增课程');
  assert.equal(tabState.getAdminTabTitle(courseBasic, '课程中心'), '课程 #5 · 基础信息');

  let tabs = [];
  tabs = tabState.upsertAdminTab(tabs, examInformation, '考试工作台');
  tabs = tabState.upsertAdminTab(tabs, examAssignment, '考试工作台');
  assert.equal(tabs.length, 1);
  assert.equal(tabs[0].path, examAssignment.fullPath);
  assert.equal(tabs[0].title, '考试 #3 · 人员配置');
});

test('考试工作台从详情切换到新增时重置详情表单状态', async () => {
  const workspaceSource = await readFile(new URL('../src/views/admin/business-center/exams/exam/workspace/index.vue', import.meta.url), 'utf8');

  assert.match(workspaceSource, /function createDefaultExamForm\(\): ExamCreatePayload/);
  assert.match(workspaceSource, /function resetCreateWorkspace\(\): void/);
  assert.match(workspaceSource, /exam\.value = undefined/);
  assert.match(workspaceSource, /Object\.assign\(form, createDefaultExamForm\(\)\)/);
});

test('考试基础信息以后台详情卡片分组展示，不使用高密度三列表格', async () => {
  const informationPanel = await readFile(informationPanelUrl, 'utf8');

  assert.match(informationPanel, /class="exam-information"/);
  assert.match(informationPanel, /<ElDescriptions v-else :column="2" border/);
  assert.match(informationPanel, /class="exam-information__notice"/);
  assert.match(informationPanel, /white-space: pre-wrap/);
});
