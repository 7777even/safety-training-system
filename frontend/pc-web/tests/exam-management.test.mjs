import assert from 'node:assert/strict';
import {readFile} from 'node:fs/promises';
import test from 'node:test';
import ts from 'typescript';

test('exam create and detail share one workspace with embedded assignment and publish check', async () => {
  const [routes, listPage, workspace, assignmentPanel, publishCheck] = await Promise.all([
    readFile(new URL('../src/router/admin-dynamic-routes.ts', import.meta.url), 'utf8'),
    readFile(new URL('../src/views/admin/business-center/exams/exam/index.vue', import.meta.url), 'utf8'),
    readFile(new URL('../src/views/admin/business-center/exams/exam/workspace/index.vue', import.meta.url), 'utf8'),
    readFile(new URL('../src/views/admin/business-center/exams/exam/components/ExamAssignmentPanel.vue', import.meta.url), 'utf8'),
    readFile(new URL('../src/views/admin/business-center/exams/exam/components/ExamPublishCheck.vue', import.meta.url), 'utf8')
  ]);

  assert.match(routes, /exam\/workspace\/index\.vue/);
  assert.doesNotMatch(routes, /name: 'ExamAssignment'/);
  for (const token of ['ExamInformationPanel', 'ExamAssignmentPanel', 'ExamPublishCheck']) assert.match(workspace, new RegExp(token));
  assert.match(listPage, /stage=assignment/);
  assert.match(assignmentPanel, /bindExamSelection/);
  for (const token of ['考生配置', '批阅人配置', '发布考试']) assert.match(publishCheck, new RegExp(token));
});

test('exam detail workflow has information, assignment, and publish stages', async () => {
  const [detailPage, assignmentPage] = await Promise.all([
    readFile(new URL('../src/views/admin/business-center/exams/exam/workspace/index.vue', import.meta.url), 'utf8'),
    readFile(new URL('../src/views/admin/business-center/exams/exam/components/ExamAssignmentPanel.vue', import.meta.url), 'utf8')
  ]);

  for (const token of ['设置考试信息', '人员配置', '发布考试', 'admin-detail-page__steps', "'assignment'", "'publish'"]) {
    assert.match(detailPage, new RegExp(token));
  }
  assert.match(assignmentPage, /bindExamSelection/);
});

const mockUrl = new URL('../src/mock/exam.ts', import.meta.url);
const orgCollaborationMockUrl = new URL('../src/mock/org-collaboration.ts', import.meta.url);
const examOptionsUrl = new URL('../src/constants/exam-options.ts', import.meta.url);

/** 加载独立的考试 Contract Mock，避免用例之间共享浏览器会话数据。 */
async function loadExamMock() {
  const [source, orgCollaborationSource] = await Promise.all([readFile(mockUrl, 'utf8'), readFile(orgCollaborationMockUrl, 'utf8')]);
  const orgCollaborationOutput = ts.transpileModule(orgCollaborationSource, {
    compilerOptions: {
      module: ts.ModuleKind.ESNext,
      target: ts.ScriptTarget.ES2022
    }
  }).outputText;
  const orgCollaborationModule = `data:text/javascript;base64,${Buffer.from(orgCollaborationOutput).toString('base64')}`;
  const output = ts.transpileModule(source.replace("from './org-collaboration';", `from '${orgCollaborationModule}';`), {
    compilerOptions: {
      module: ts.ModuleKind.ESNext,
      target: ts.ScriptTarget.ES2022
    }
  }).outputText;
  return import(`data:text/javascript;base64,${Buffer.from(output).toString('base64')}#${Math.random()}`);
}

/** 运行统一考试字典，直接验证当前 Contract 值都有中文 label。 */
async function loadExamOptions() {
  const source = await readFile(examOptionsUrl, 'utf8');
  const output = ts.transpileModule(source, {
    compilerOptions: {module: ts.ModuleKind.ESNext, target: ts.ScriptTarget.ES2022}
  }).outputText;
  return import(`data:text/javascript;base64,${Buffer.from(output).toString('base64')}#${Math.random()}`);
}

test('创建考试时可保存 Vue 响应式表单，不对 Proxy 直接调用 structuredClone', async () => {
  const mock = await loadExamMock();
  mock.resetExamMock();
  const payload = new Proxy({
    organizationId: 1,
    examCode: 'EXAM-PROXY-001',
    examName: '响应式创建考试',
    examType: 'ONLINE',
    paperVersionId: 1,
    durationSeconds: 3600,
    passRatio: 0.6,
    passScore: 60,
    credit: 1,
    questionOrderMode: 'IMPORT',
    multiSelectScoring: 'ALL_OR_NOTHING',
    isNoticeEnabled: 0,
    status: 1,
    policy: new Proxy({isPcAllowed: 1}, {})
  }, {});
  const result = await mock.mockCreateExam(payload);
  assert.equal(result.code, 0);
});

test('考试管理列表进入独立的新建、详情和人员配置工作流', async () => {
  const page = await readFile(new URL('../src/views/admin/business-center/exams/exam/index.vue', import.meta.url), 'utf8');
  for (const token of ['getExamPage', '/exam/create', 'stage=assignment', '/admin/business-center/exams/exam/']) assert.match(page, new RegExp(token));
  assert.doesNotMatch(page, /ElDialog/);
});

test('考试创建、详情和人员配置均为独立页面且遵守编排边界', async () => {
  const [createPage, detailPage, assignmentPage] = await Promise.all([
    readFile(new URL('../src/views/admin/business-center/exams/exam/workspace/index.vue', import.meta.url), 'utf8'),
    readFile(new URL('../src/views/admin/business-center/exams/exam/workspace/index.vue', import.meta.url), 'utf8'),
    readFile(new URL('../src/views/admin/business-center/exams/exam/components/ExamAssignmentPanel.vue', import.meta.url), 'utf8')
  ]);
  for (const token of ['createExam', 'paperVersionId', 'ExamPolicyRequest']) assert.match(createPage, new RegExp(token));
  for (const token of ['getExam', 'updateExam', 'paperVersionId', 'ExamInformationPanel', 'publishExam', 'policy: form.policy']) assert.match(detailPage, new RegExp(token));
  for (const token of ['bindExamSelection', 'replaceExamReviewers', 'PersonSelectionDialog', 'selectionType', 'subjectIds', "contextType: 'STANDALONE'", 'contextId: 0', "assignmentSource: 'USER'", 'getExamCandidates', 'getExamReviewers']) assert.match(assignmentPage, new RegExp(token));
  assert.doesNotMatch(assignmentPage, /selectionType: 'USER', subjectIds: selectedCandidateItems/);
  for (const page of [createPage, detailPage, assignmentPage]) assert.doesNotMatch(page, /ElDialog/);
});

test('负责人和批阅人均从所属机构的人员选择器选择', async () => {
  const [definitionForm, assignmentPage] = await Promise.all([
    readFile(new URL('../src/views/admin/business-center/exams/exam/components/ExamDefinitionForm.vue', import.meta.url), 'utf8'),
    readFile(new URL('../src/views/admin/business-center/exams/exam/components/ExamAssignmentPanel.vue', import.meta.url), 'utf8')
  ]);
  assert.match(definitionForm, /PersonPickerDialog/);
  assert.match(definitionForm, /organization-id/);
  assert.match(assignmentPage, /PersonSelectionDialog/);
  assert.match(assignmentPage, /title="选择考试评卷人范围"/);
  assert.match(assignmentPage, /@confirm="selectReviewers"/);
  assert.match(assignmentPage, /selectedReviewers/);
});

test('考试人员配置不再依赖废弃的参与范围 Mock', async () => {
  const examMock = await readFile(mockUrl, 'utf8');
  assert.doesNotMatch(examMock, /mockGetAudiencePage/);
  assert.match(examMock, /mockBindExamSelection/);
});

test('考试定义使用字典、分类与题库选择器和封面上传', async () => {
  const [definitionForm, examOptions] = await Promise.all([
    readFile(new URL('../src/views/admin/business-center/exams/exam/components/ExamDefinitionForm.vue', import.meta.url), 'utf8'),
    readFile(new URL('../src/constants/exam-options.ts', import.meta.url), 'utf8')
  ]);

  for (const token of ['EXAM_TYPE_OPTIONS', 'QUESTION_ORDER_MODE_OPTIONS', 'MULTI_SELECT_SCORING_OPTIONS']) {
    assert.match(definitionForm, new RegExp(token));
  }
  for (const token of ['ONLINE', 'OFFLINE', 'SPECIAL_PRACTICE', 'BANK_PRACTICE']) {
    assert.match(examOptions, new RegExp(token));
  }
  assert.match(definitionForm, /getQuestionBankCategories/);
  assert.match(definitionForm, /getQuestionBankPage/);
  assert.match(definitionForm, /UnifiedFileUpload/);
  assert.match(definitionForm, /uploadAssetFile/);
  assert.doesNotMatch(definitionForm, /<ElInput v-model="form\.(?:examType|questionOrderMode|multiSelectScoring)"/);
  assert.doesNotMatch(definitionForm, /<ElInputNumber v-model="form\.(?:examCategoryId|coverFileId|questionBankId)"/);
});

test('新建考试选择试卷版本后默认带入试卷分类与封面文件', async () => {
  const [createPage, definitionForm, paperApi] = await Promise.all([
    readFile(new URL('../src/views/admin/business-center/exams/exam/workspace/index.vue', import.meta.url), 'utf8'),
    readFile(new URL('../src/views/admin/business-center/exams/exam/components/ExamDefinitionForm.vue', import.meta.url), 'utf8'),
    readFile(new URL('../src/api/paper.ts', import.meta.url), 'utf8')
  ]);

  assert.match(paperApi, /paperCategoryId\?: number/);
  assert.match(paperApi, /coverFileId\?: number/);
  assert.match(createPage, /paperCategoryId: paper\.paperCategoryId/);
  assert.match(createPage, /coverFileId: paper\.coverFileId/);
  assert.match(definitionForm, /form\.value\.examCategoryId = paper\.paperCategoryId/);
  assert.match(definitionForm, /form\.value\.coverFileId = paper\.coverFileId/);
  assert.match(createPage, /@organization-change="loadPaperVersions"/);
});

test('题库练习将抽题规则收敛到弹窗，并按真实候选池限制重抽和超抽', async () => {
  const [definitionForm, drawRuleDialog] = await Promise.all([
    readFile(new URL('../src/views/admin/business-center/exams/exam/components/ExamDefinitionForm.vue', import.meta.url), 'utf8'),
    readFile(new URL('../src/views/admin/business-center/exams/exam/components/ExamDrawRuleDialog.vue', import.meta.url), 'utf8')
  ]);

  assert.match(definitionForm, /ExamDrawRuleDialog/);
  assert.match(definitionForm, /查看\/修改规则/);
  assert.match(definitionForm, /:disabled="!form\.questionBankId"/);
  assert.doesNotMatch(definitionForm, /draw-rule-card/);
  for (const token of ["'query.archiveStatus': 'ACTIVE'", "'query.publishedStatus': 'PUBLISHED'", "'query.status': 1", 'questionTypeCounts', 'availableCountForRule', 'hasOverlappingRule', 'unionAvailableCount', 'knowledgePointIds?.includes']) {
    assert.match(drawRuleDialog, new RegExp(token.replace(/[.*+?^${}()|[\]\\]/g, '\\$&')));
  }
});

test('重开抽题规则弹窗前题池未加载时，不得将已确认的抽题数量钳制为 1', async () => {
  const drawRuleDialog = await readFile(
    new URL('../src/views/admin/business-center/exams/exam/components/ExamDrawRuleDialog.vue', import.meta.url),
    'utf8'
  );

  assert.match(drawRuleDialog, /const questionPoolReady = computed/);
  assert.match(drawRuleDialog, /:max="questionPoolReady \? availableCountForRule/);
  assert.doesNotMatch(drawRuleDialog, /:max="Math\.max\(1, availableCountForRule/);
});

test('考试列表和详情通过 Contract 名称展示试卷版本与题库，不显示内部 ID', async () => {
  const [listPage, informationPanel, examApi] = await Promise.all([
    readFile(new URL('../src/views/admin/business-center/exams/exam/index.vue', import.meta.url), 'utf8'),
    readFile(new URL('../src/views/admin/business-center/exams/exam/components/ExamInformationPanel.vue', import.meta.url), 'utf8'),
    readFile(new URL('../src/api/exam.ts', import.meta.url), 'utf8')
  ]);

  for (const token of ['paperName', 'paperVersionNo', 'questionBankName']) assert.match(examApi, new RegExp(token));
  for (const token of ['getExamTypeLabel', 'resourceLabel', 'openStartTime', 'openEndTime']) assert.match(listPage, new RegExp(token));
  assert.doesNotMatch(listPage, /prop="paperVersionId"/);
  assert.match(informationPanel, /exam\?\.paperName/);
  assert.match(informationPanel, /exam\?\.questionBankName/);
  assert.doesNotMatch(informationPanel, /试卷版本 #/);
  assert.doesNotMatch(informationPanel, /题库 #/);
});

test('考试考生绑定提供全员、部门和指定人员三种 Contract 选择模式', async () => {
  const [assignmentPage, selectionDialog] = await Promise.all([
    readFile(new URL('../src/views/admin/business-center/exams/exam/components/ExamAssignmentPanel.vue', import.meta.url), 'utf8'),
    readFile(new URL('../src/components/organization/PersonSelectionDialog.vue', import.meta.url), 'utf8')
  ]);

  assert.match(assignmentPage, /PersonSelectionDialog/);
  assert.match(assignmentPage, /@confirm="selectCandidates"/);
  assert.match(assignmentPage, /selection/);
  assert.match(assignmentPage, /selectedUsers/);
  assert.match(assignmentPage, /selectedDepartmentNames/);
  assert.doesNotMatch(assignmentPage, /考生保存时按本次选择绑定/);
  assert.doesNotMatch(assignmentPage, /当前范围：/);
  assert.doesNotMatch(assignmentPage, /当前已配置批阅人会自动回显/);
  assert.match(selectionDialog, /preselectedUsers/);
  assert.match(selectionDialog, /props\.modelValue\?\.selectionType === 'USER' \? \[\.\.\.props\.preselectedUsers\]/);
});

test('考试详情不展示无操作入口的成绩通知范围原始 ID', async () => {
  const detailPage = await readFile(new URL('../src/views/admin/business-center/exams/exam/workspace/index.vue', import.meta.url), 'utf8');

  assert.doesNotMatch(detailPage, /resultNotifyAudienceId: '成绩通知范围 ID'/);
  assert.doesNotMatch(detailPage, /resultNotifyAudienceId/);
});

test('新建考试每份试卷只展示最新的已发布版本', async () => {
  const createPage = await readFile(new URL('../src/views/admin/business-center/exams/exam/workspace/index.vue', import.meta.url), 'utf8');

  assert.match(createPage, /filter\(\(version\) => version\.publishedStatus === 'PUBLISHED'\)/);
  assert.match(createPage, /sort\(\(left, right\) => right\.versionNo - left\.versionNo\)/);
  assert.match(createPage, /slice\(0, 1\)/);
});

test('考试详情、人员配置、批阅、成绩处置和学员任务统一使用字典 label 回显', async () => {
  const pages = await Promise.all([
    readFile(new URL('../src/views/admin/business-center/exams/exam/workspace/index.vue', import.meta.url), 'utf8'),
    readFile(new URL('../src/views/admin/business-center/exams/exam/components/ExamAssignmentPanel.vue', import.meta.url), 'utf8'),
    readFile(new URL('../src/views/admin/business-center/exams/exam/grading/index.vue', import.meta.url), 'utf8'),
    readFile(new URL('../src/views/admin/business-center/exams/exam/results/index.vue', import.meta.url), 'utf8'),
    readFile(new URL('../src/views/portal/exams/index.vue', import.meta.url), 'utf8')
  ]);

  for (const page of pages.slice(1)) assert.match(page, /getExamEnumLabel/);
  assert.match(pages[0], /ExamAssignmentPanel/);
  assert.match(pages[1], /CANDIDATE_STATUS_OPTIONS/);
  assert.match(pages[2], /ANTICHEAT_EVENT_TYPE_OPTIONS/);
  assert.match(pages[3], /SCORE_PUBLISH_TYPE_OPTIONS/);
  assert.match(pages[4], /LEARNER_TASK_STATUS_OPTIONS/);
});

test('考试域当前 Contract 状态值均可转换为中文 label', async () => {
  const options = await loadExamOptions();
  const candidates = new Map(options.CANDIDATE_STATUS_OPTIONS.map((item) => [item.value, item.label]));
  const attempts = new Map(options.ATTEMPT_STATUS_OPTIONS.map((item) => [item.value, item.label]));

  for (const value of ['PENDING', 'NOT_STARTED', 'IN_PROGRESS', 'SUBMITTED', 'FINISHED']) {
    assert.ok(candidates.get(value), `缺少考生状态 ${value} 的中文 label`);
  }
  for (const value of ['ANSWERING', 'SUBMITTED', 'PENDING_REVIEW', 'PENDING_PUBLISH']) {
    assert.ok(attempts.get(value), `缺少作答状态 ${value} 的中文 label`);
  }
  assert.equal(options.getExamEnumLabel('ARCHIVED', options.PUBLISH_STATUS_OPTIONS), '已归档');
});
