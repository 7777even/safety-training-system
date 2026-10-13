import assert from 'node:assert/strict';
import { readFile } from 'node:fs/promises';
import test from 'node:test';

const workspaceUrl = new URL('../src/views/admin/business-center/exams/exam/workspace/index.vue', import.meta.url);
const informationPanelUrl = new URL('../src/views/admin/business-center/exams/exam/components/ExamInformationPanel.vue', import.meta.url);
const definitionFormUrl = new URL('../src/views/admin/business-center/exams/exam/components/ExamDefinitionForm.vue', import.meta.url);
const assignmentPanelUrl = new URL('../src/views/admin/business-center/exams/exam/components/ExamAssignmentPanel.vue', import.meta.url);
const publishCheckUrl = new URL('../src/views/admin/business-center/exams/exam/components/ExamPublishCheck.vue', import.meta.url);
const policySummaryUrl = new URL('../src/views/admin/business-center/exams/exam/components/ExamPolicySummary.vue', import.meta.url);
const examApiUrl = new URL('../src/api/exam.ts', import.meta.url);
const examMockUrl = new URL('../src/mock/exam.ts', import.meta.url);
const examAttemptUrl = new URL('../src/views/portal/exams/attempt.vue', import.meta.url);

test('新建考试禁用后续步骤，并将无效步骤参数回落到考试信息', async () => {
  const workspace = await readFile(workspaceUrl, 'utf8');

  assert.match(workspace, /function isStageDisabled\(target: ExamWorkspaceStage\): boolean/);
  assert.match(workspace, /isCreateMode\.value && target !== 'information'/);
  assert.match(workspace, /if \(isCreateMode\.value && value !== undefined\) return 'information';/);
  assert.match(workspace, /:disabled="isStageDisabled\(item\.name\)"/);
});

test('考试工作台标题区显示发布状态标签', async () => {
  const workspace = await readFile(workspaceUrl, 'utf8');
  const header = workspace.match(/<header class="admin-detail-page__header">([\s\S]*?)<\/header>/)?.[1] ?? '';

  assert.match(header, /ElTag/);
  assert.match(header, /exam\.publishStatus === 'PUBLISHED'/);
});

test('选择试卷版本后从版本详情带入四项考试规则', async () => {
  const [informationPanel, definitionForm] = await Promise.all([readFile(informationPanelUrl, 'utf8'), readFile(definitionFormUrl, 'utf8')]);

  assert.match(definitionForm, /paperVersionSelected: \[paperVersionId: number \| undefined\]/);
  assert.match(definitionForm, /emit\('paperVersionSelected', paperVersionId\)/);
  assert.match(informationPanel, /import \{ getPaperVersion(?:, type PaperVersionOption)? \} from '@\/api\/paper';/);
  assert.match(informationPanel, /const paperVersion = await getPaperVersion\(paperVersionId\);/);
  assert.match(informationPanel, /form\.value\.durationSeconds = paperVersion\.durationSeconds;/);
  assert.match(informationPanel, /form\.value\.passRatio = paperVersion\.passRatio;/);
  assert.match(informationPanel, /form\.value\.questionOrderMode = paperVersion\.questionOrderMode;/);
  assert.match(informationPanel, /form\.value\.multiSelectScoring = paperVersion\.multiSelectScoring;/);
  assert.match(informationPanel, /@paper-version-selected="applyPaperVersionDefaults"/);
});

test('考试工作台集中加载引用数据，子步骤不重复请求题库与试卷版本', async () => {
  const [workspace, informationPanel, definitionForm, publishCheck] = await Promise.all([
    readFile(workspaceUrl, 'utf8'),
    readFile(informationPanelUrl, 'utf8'),
    readFile(definitionFormUrl, 'utf8'),
    readFile(publishCheckUrl, 'utf8')
  ]);

  assert.match(workspace, /async function loadReferenceOptions\(\): Promise<void>/);
  assert.match(workspace, /getQuestionBankCategories\(\)/);
  assert.match(workspace, /getQuestionBankPage\(\{ pageNum: 1, pageSize: EXAM_REFERENCE_PAGE_SIZE, 'query\.status': 1 \}\)/);
  assert.match(workspace, /:category-options="categoryOptions"/);
  assert.match(workspace, /:question-bank-options="questionBankOptions"/);
  assert.match(workspace, /:paper-options="paperOptions"/);
  assert.match(workspace, /async function ensureSelectedPaperVersion\(\): Promise<void>/);
  assert.match(workspace, /const version = await getPaperVersion\(paperVersionId\);/);
  for (const page of [informationPanel, definitionForm, publishCheck]) {
    assert.doesNotMatch(page, /getQuestionBankCategories\(/);
    assert.doesNotMatch(page, /getQuestionBankPage\(/);
    assert.doesNotMatch(page, /getPaperPage\(/);
    assert.doesNotMatch(page, /getPaperVersions\(/);
  }
});

test('考试只读态也加载考试分类选项，避免已发布考试只显示内部 ID', async () => {
  const workspace = await readFile(workspaceUrl, 'utf8');

  assert.match(workspace, /async function loadCategoryOptions\(\): Promise<void> \{/);
  assert.match(workspace, /categoryOptions\.value = await getQuestionBankCategories\(\);/);
  // 分类与详情并行读取；非草稿只读态不再因为跳过草稿引用数据而丢失分类名称。
  assert.match(workspace, /const \[detail\] = await Promise\.all\(\[getExam\(examId\.value\), loadCategoryOptions\(\)\.catch\(\(\) => undefined\)\]\);/);
  assert.match(workspace, /if \(detail\.publishStatus === 'DRAFT'\) \{/);
});

test('人员配置和发布核查各自只加载一次人员数据，并向工作台回传统计', async () => {
  const [workspace, assignmentPanel, publishCheck] = await Promise.all([
    readFile(workspaceUrl, 'utf8'),
    readFile(assignmentPanelUrl, 'utf8'),
    readFile(publishCheckUrl, 'utf8')
  ]);

  assert.doesNotMatch(workspace, /function loadCheckSummary/);
  assert.match(assignmentPanel, /const emit = defineEmits<\{ saved: \[candidateCount: number, reviewerCount: number\]; next: \[\] \}>\(\);/);
  assert.match(assignmentPanel, /emit\('saved', candidateTotal\.value, reviewerIds\.value\.length\);/);
  assert.match(publishCheck, /const emit = defineEmits<\{ publish: \[\]; summaryLoaded: \[candidateCount: number, reviewerCount: number\] \}>\(\);/);
  assert.match(publishCheck, /emit\('summaryLoaded', candidatePage\.total, reviewerList\.length\);/);
  assert.match(workspace, /@saved="updateCheckSummary"/);
  assert.match(workspace, /@summary-loaded="updateCheckSummary"/);
});

test('考试基础信息默认显示考试策略配置', async () => {
  const [informationPanel, definitionForm] = await Promise.all([readFile(informationPanelUrl, 'utf8'), readFile(definitionFormUrl, 'utf8')]);

  assert.doesNotMatch(definitionForm, /showPolicy/);
  assert.match(informationPanel, /<ExamPolicySummary v-if="exam" :exam="exam"/);
});

test('考试基础信息表单不透传未声明的 organizationChange 事件', async () => {
  const [workspace, informationPanel, definitionForm] = await Promise.all([
    readFile(workspaceUrl, 'utf8'),
    readFile(informationPanelUrl, 'utf8'),
    readFile(definitionFormUrl, 'utf8')
  ]);

  assert.doesNotMatch(workspace, /@organization-change/);
  assert.doesNotMatch(informationPanel, /@organization-change/);
  assert.doesNotMatch(informationPanel, /organizationChange: \[\]/);
  assert.doesNotMatch(definitionForm, /organizationChange/);
});

test('人员配置保存成功后可进入发布考试步骤', async () => {
  const [workspace, assignmentPanel] = await Promise.all([readFile(workspaceUrl, 'utf8'), readFile(assignmentPanelUrl, 'utf8')]);

  assert.match(assignmentPanel, /const configurationSaved = computed\(\(\) => candidateTotal\.value > 0\);/);
  assert.doesNotMatch(assignmentPanel, /configurationSaved\.value = true;/);
  assert.match(assignmentPanel, /v-if="configurationSaved"[^>]*@click="emit\('next'\)"/);
  assert.match(workspace, /@next="goStage\('publish'\)"/);
});

test('草稿人员配置允许保存空阅卷人数组', async () => {
  const assignmentPanel = await readFile(assignmentPanelUrl, 'utf8');

  assert.doesNotMatch(assignmentPanel, /请至少选择一名批阅人/);
  assert.match(assignmentPanel, /replaceExamReviewers\(props\.examId, \{ reviewerUserIds: reviewerIds\.value, assignmentSource: 'USER' \}\)/);
  assert.match(assignmentPanel, /function clearReviewers\(\): void/);
  assert.match(assignmentPanel, /清空批阅人/);
});

test('题库练习与线下考试发布只要求考生，批阅人仅用于需要人工批阅的考试', async () => {
  const [workspace, assignmentPanel, publishCheck] = await Promise.all([
    readFile(workspaceUrl, 'utf8'),
    readFile(assignmentPanelUrl, 'utf8'),
    readFile(publishCheckUrl, 'utf8')
  ]);

  assert.match(assignmentPanel, /requiresReviewer/);
  assert.match(assignmentPanel, /v-if="requiresReviewer"/);
  assert.match(publishCheck, /requiresReviewer/);
  assert.match(publishCheck, /!candidateCount \|\| \(requiresReviewer && !reviewerCount\)/);
  assert.match(workspace, /requiresReviewerForExam/);
  assert.match(workspace, /!candidateCount\.value \|\| \(requiresReviewerForExam\(exam\.value\) && !reviewerCount\.value\)/);
});

test('绑定试卷时按新契约省略未覆盖规则，草稿可换卷或解绑', async () => {
  const [workspace, definitionForm, examApi, examMock] = await Promise.all([
    readFile(workspaceUrl, 'utf8'),
    readFile(definitionFormUrl, 'utf8'),
    readFile(examApiUrl, 'utf8'),
    readFile(examMockUrl, 'utf8')
  ]);

  assert.match(examApi, /paperVersionId\?: number \| null;/);
  assert.match(examApi, /durationSeconds\?: number;/);
  assert.match(examApi, /passScore\?: number;/);
  assert.match(workspace, /const ruleOverrides = reactive<Record<ExamRuleOverrideField, boolean>>/);
  assert.match(workspace, /paperVersionChanged\.value = true;/);
  assert.match(workspace, /payload\.paperVersionId = form\.paperVersionId \?\? null/);
  assert.match(workspace, /if \(hasBoundPaper\) \{/);
  assert.match(definitionForm, /const needsPaperVersion = computed\(\(\) => form\.value\.examType === 'ONLINE' \|\| form\.value\.examType === 'SPECIAL_PRACTICE'\)/);
  assert.match(definitionForm, /const needsQuestionBank = computed\(\(\) => form\.value\.examType === 'BANK_PRACTICE'\)/);
  assert.match(definitionForm, /v-if="needsPaperVersion"/);
  assert.match(definitionForm, /v-if="needsQuestionBank"/);
  assert.match(definitionForm, /v-if="hasPaperVersion"/);
  assert.match(definitionForm, /默认考试时长/);
  assert.match(definitionForm, /<DurationInput v-model="form\.durationSeconds" @change="markRuleOverride\('durationSeconds'\)"/);
  assert.match(definitionForm, /v-else/);
  assert.match(definitionForm, /@change="markRuleOverride\('durationSeconds'\)"/);
  assert.match(definitionForm, /:disabled="false"/);
  assert.match(examMock, /function resolveRuleValues\(/);
  assert.match(examMock, /PAPER_VERSION_DEFAULTS/);
  assert.match(examMock, /preserveExisting && data\.passRatio === undefined/);
});

test('延期考试策略不渲染且编辑已有考试时保持原 policy 透传', async () => {
  const [workspace, definitionForm, policySummary] = await Promise.all([
    readFile(workspaceUrl, 'utf8'),
    readFile(definitionFormUrl, 'utf8'),
    readFile(policySummaryUrl, 'utf8')
  ]);
  const deferredFields = [
    'switchLimit',
    'isSplitScreenForceSubmit',
    'isCopyRefreshDevtoolDisabled',
    'inactivitySeconds',
    'inactivityCountdownSeconds',
    'minimumAnswerSeconds',
    'lateForbidSeconds',
    'passPoint',
    'failPoint',
    'absentPoint',
    'isRankingEnabled'
  ];

  for (const field of deferredFields) {
    assert.doesNotMatch(definitionForm, new RegExp(field));
    assert.doesNotMatch(policySummary, new RegExp(field));
  }
  assert.match(definitionForm, /作答终端、补考与成绩展示口径/);
  assert.match(definitionForm, /<strong>考试防作弊<\/strong>/);
  assert.match(definitionForm, /isWatermarkEnabled/);
  assert.match(definitionForm, /isRetakeEnabled/);
  assert.match(definitionForm, /answerDetailMode/);
  assert.match(definitionForm, /isAnalysisVisible/);
  assert.match(definitionForm, /isScoreVisible/);
  assert.match(workspace, /policy: form\.policy/);
  assert.doesNotMatch(definitionForm, /isRankingEnabled === undefined/);
});

test('已绑卷草稿不换卷时，下一步保存仍提交当前试卷版本', async () => {
  const workspace = await readFile(workspaceUrl, 'utf8');

  assert.match(workspace, /payload\.paperVersionId = form\.paperVersionId \?\? null;/);
  assert.doesNotMatch(workspace, /if \(paperVersionChanged\.value\) payload\.paperVersionId = form\.paperVersionId \?\? null;/);
});

test('考试封面使用与课程一致的图片预览、占位和上传提示', async () => {
  const definitionForm = await readFile(definitionFormUrl, 'utf8');

  assert.match(definitionForm, /class="exam-cover-field"/);
  assert.match(definitionForm, /<ElImage\s+v-if="coverPreviewUrl"/);
  assert.match(definitionForm, /class="exam-cover-placeholder"/);
  assert.match(definitionForm, /暂无封面/);
  assert.match(definitionForm, /上传封面/);
  assert.match(definitionForm, /JPG、PNG、WebP，最大 10 MiB/);
});

test('发布考试核查展示考试策略的关键配置', async () => {
  const [publishCheck, policySummary] = await Promise.all([readFile(publishCheckUrl, 'utf8'), readFile(policySummaryUrl, 'utf8')]);

  assert.match(publishCheck, /<ExamPolicySummary :exam="exam" class="check-block"/);
  assert.match(policySummary, /<ElDescriptions title="考试策略"/);
  assert.match(policySummary, /<ElDescriptions title="考试防作弊"/);
  for (const label of ['允许作答端', '水印', '允许补考', '答案详情展示方式', '展示成绩']) {
    assert.match(policySummary, new RegExp(`label="${label}"`));
  }
  assert.match(policySummary, /getExamEnumLabel\(exam\.policy\?\.retakeScope, RETAKE_SCOPE_OPTIONS/);
});

test('独立考试答题页按防作弊开关拦截剪贴板和右键操作并提示上报结果', async () => {
  const examAttempt = await readFile(examAttemptUrl, 'utf8');

  assert.match(examAttempt, /v-if="isAnticheatEnabled"/);
  assert.match(examAttempt, /本考试已开启防作弊：离开答题页面、复制粘贴与右键菜单都会被记录。/);
  assert.match(examAttempt, /function handleSwitchAway\(source: string\): void/);
  assert.match(examAttempt, /检测到离开答题页面，已记录本次异常行为。/);
  assert.match(examAttempt, /function blockClipboard\(event: globalThis\.Event\): void/);
  assert.match(examAttempt, /本考试已禁止复制粘贴，本次操作已记录。/);
  for (const eventType of ['copy', 'cut', 'paste', 'contextmenu']) {
    assert.match(examAttempt, new RegExp(`addEventListener\\('${eventType}', blockClipboard\\)`));
    assert.match(examAttempt, new RegExp(`removeEventListener\\('${eventType}', blockClipboard\\)`));
  }
});
