import assert from 'node:assert/strict';
import { readFile } from 'node:fs/promises';
import test from 'node:test';
import { URL } from 'node:url';

const listPage = new URL('../src/views/admin/business-center/training/evaluation-form/index.vue', import.meta.url);
const workspacePage = new URL(
  '../src/views/admin/business-center/training/evaluation-form/workspace/index.vue',
  import.meta.url
);
const versionPage = new URL(
  '../src/views/admin/business-center/training/evaluation-form/version/index.vue',
  import.meta.url
);
const itemsPanel = new URL(
  '../src/views/admin/business-center/training/evaluation-form/components/EvaluationFormItemsPanel.vue',
  import.meta.url
);
const publishPanel = new URL(
  '../src/views/admin/business-center/training/evaluation-form/components/EvaluationFormPublishPanel.vue',
  import.meta.url
);

test('评价表单列表集中提供筛选、工作台入口、版本追溯和删除', async () => {
  const source = await readFile(listPage, 'utf8');

  assert.match(source, /getEvaluationFormPage/);
  assert.match(source, /getEvaluationFormVersions/);
  assert.match(source, /deleteEvaluationForm/);
  assert.match(source, /query\.formName/);
  assert.match(source, /query\.formType/);
  assert.match(source, /query\.status/);
  assert.match(source, /evaluation:form:add/);
  assert.match(source, /evaluation:form:edit/);
  assert.match(source, /evaluation:form:delete/);
  assert.match(source, /evaluation:form:list/);
  assert.match(source, /evaluation-form\/workspace/);
  assert.match(source, /evaluation-form\/version/);
  assert.match(source, /DEFAULT_LIST_PAGE_SIZE/);
});

test('评价表单工作台按基础信息、项目规则、确认发布形成连续操作节奏', async () => {
  const source = await readFile(workspacePage, 'utf8');

  assert.match(source, /stage-nav/);
  assert.match(source, /基础信息/);
  assert.match(source, /项目与规则/);
  assert.match(source, /确认发布/);
  assert.match(source, /createEvaluationForm/);
  assert.match(source, /updateEvaluationForm/);
  assert.match(source, /publishEvaluationForm/);
  assert.match(source, /getEvaluationFormVersions/);
  assert.match(source, /evaluationItemCode/);
  assert.match(source, /itemCode/);
  assert.match(source, /evaluation:form:add/);
  assert.match(source, /evaluation:form:edit/);
  assert.match(source, /evaluation:form:publish/);
  assert.match(source, /发布后版本内容不可修改/);
  assert.doesNotMatch(source, /OrganizationSelect|organizationId: undefined|message: '请选择所属机构'/);
  assert.match(source, /message: '请输入表单编码'/);
  assert.match(source, /message: '请输入表单名称'/);
  assert.match(source, /message: '请输入用途说明'/);
});

test('评价表单项目行可直接为当前项目设置唯一评分规则', async () => {
  const [workspaceSource, panelSource] = await Promise.all([
    readFile(workspacePage, 'utf8'),
    readFile(itemsPanel, 'utf8')
  ]);

  assert.match(workspaceSource, /function addScoreRuleForItem\(itemCode: string\): void/);
  assert.match(workspaceSource, /const item = items\.value\.find\(\(current\) => current\.itemCode === itemCode\)/);
  assert.match(workspaceSource, /weight: item\?\.itemType === 'SCORE' \? item\.weight : null/);
  assert.match(workspaceSource, /@add-rule-for-item="addScoreRuleForItem"/);
  assert.match(panelSource, /addRuleForItem: \[itemCode: string\]/);
  assert.match(panelSource, /设置规则/);
  assert.match(panelSource, /已配置规则/);
  assert.match(panelSource, /hasRuleForItem/);
});

test('综合评价以固定来源卡片配置，并对必填项和权重闭合给出明确反馈', async () => {
  const [workspaceSource, panelSource] = await Promise.all([
    readFile(workspacePage, 'utf8'),
    readFile(itemsPanel, 'utf8')
  ]);

  assert.match(panelSource, /综合成绩来源配置/);
  assert.match(panelSource, /纳入综合成绩/);
  assert.match(panelSource, /权重合计/);
  assert.match(panelSource, /required-label/);
  assert.match(panelSource, /validationMessage/);
  assert.match(workspaceSource, /综合成绩权重合计必须为 1/);
  assert.match(workspaceSource, /请选择至少一项成绩来源/);
});

test('评价表单项目与规则草稿在发布前使用本地缓存防止刷新丢失', async () => {
  const source = await readFile(workspacePage, 'utf8');

  assert.match(source, /EVALUATION_COMPOSITION_DRAFT_PREFIX/);
  assert.match(source, /function restoreCompositionDraft\(\): void/);
  assert.match(source, /function persistCompositionDraft\(\): void/);
  assert.match(source, /watch\(\[items, scoreRules, savedCompositionFingerprint\]/);
  assert.match(source, /clearCompositionDraft\(\)/);
});

test('评价表单发布确认按项目关联展示项目和规则快照', async () => {
  const source = await readFile(publishPanel, 'utf8');

  assert.match(source, /publish-overview-grid/);
  assert.match(source, /ruleItemName/);
  assert.match(source, /关联项目/);
  assert.match(source, /无自动评分规则/);
});

test('评价表单版本详情读取所属表单，并只读展示冻结项目与评分规则', async () => {
  const source = await readFile(versionPage, 'utf8');

  assert.match(source, /getEvaluationFormVersionDetail/);
  assert.match(source, /getEvaluationFormDetail/);
  assert.match(source, /evaluation:form:list/);
  assert.match(source, /只读版本/);
  assert.match(source, /publishedBy/);
  assert.match(source, /evaluationItemId/);
  assert.doesNotMatch(source, /publishEvaluationForm/);
  assert.doesNotMatch(source, /updateEvaluationForm/);
  assert.doesNotMatch(source, /deleteEvaluationForm/);
});

test('已发布综合评价版本按来源卡片展示冻结的成绩权重', async () => {
  const source = await readFile(versionPage, 'utf8');

  assert.match(source, /isCompositeForm/);
  assert.match(source, /综合成绩来源配置/);
  assert.match(source, /COMPOSITE_SOURCE_TYPES/);
  assert.match(source, /source-card-grid/);
  assert.match(source, /表单层权重/);
  assert.match(source, /只读快照/);
});

test('评价表单版本详情使用连续阅读区展示完整快照', async () => {
  const source = await readFile(versionPage, 'utf8');

  assert.match(source, /version-hero/);
  assert.match(source, /version-hero__status/);
  assert.match(source, /snapshot-section__meta/);
  assert.match(source, /version-table__tag/);
  assert.match(source, /version-summary-grid/);
  assert.match(source, /version-content/);
  assert.match(source, /snapshot-section/);
  assert.match(source, /version-metric/);
  assert.match(source, /snapshot-section-heading/);
  assert.match(source, /snapshot-table-wrap/);
  assert.match(source, /ruleItemText/);
  assert.match(source, /关联项目/);
  assert.doesNotMatch(source, /<ElCard/);
  assert.doesNotMatch(source, /<ElDescriptions/);
});

test('专家检查与综合评价在工作台、版本和计划执行区按类型隔离', async () => {
  const [workspace, itemsPanel, versionSource, planWorkspace, expertBinding, expertExecution, resultApi] = await Promise.all([
    readFile(workspacePage, 'utf8'),
    readFile(new URL('../src/views/admin/business-center/training/evaluation-form/components/EvaluationFormItemsPanel.vue', import.meta.url), 'utf8'),
    readFile(versionPage, 'utf8'),
    readFile(new URL('../src/views/admin/business-center/training/plan/workspace/index.vue', import.meta.url), 'utf8'),
    readFile(new URL('../src/views/admin/business-center/training/plan/components/PlanExpertInspectionPanel.vue', import.meta.url), 'utf8'),
    readFile(new URL('../src/views/admin/business-center/training/plan/components/PlanExpertInspectionExecutionPanel.vue', import.meta.url), 'utf8'),
    readFile(new URL('../src/api/evaluation-result.ts', import.meta.url), 'utf8')
  ]);
  assert.match(workspace, /isExpertInspection/);
  assert.match(itemsPanel, /检查项目配置/);
  assert.match(workspace, /专家检查分数项满分合计必须为 100/);
  assert.match(workspace, /watch\(\(\) => basic\.formType/);
  assert.match(workspace, /if \(scoreRules\.value\.length\) scoreRules\.value = \[\];/);
  assert.match(versionSource, /专家查课项目快照/);
  assert.match(planWorkspace, /PlanExpertInspectionExecutionPanel/);
  assert.match(planWorkspace, /expertInspectionVisible/);
  assert.match(planWorkspace, /<ElDialog v-model="expertInspectionVisible" title="专家查课"/);
  assert.match(expertBinding, /不计入综合成绩/);
  assert.match(expertExecution, /<section class="expert-execution">/);
  assert.doesNotMatch(expertExecution, /<ElCard/);
  assert.match(expertExecution, /填写查课表/);
  assert.match(resultApi, /submitExpertInspection/);
});

test('专家查课仅在执行中从下一步操作按需打开弹窗', async () => {
  const [planWorkspace, executionPanel, expertExecution] = await Promise.all([
    readFile(new URL('../src/views/admin/business-center/training/plan/workspace/index.vue', import.meta.url), 'utf8'),
    readFile(new URL('../src/views/admin/business-center/training/plan/components/PlanExecutionPanel.vue', import.meta.url), 'utf8'),
    readFile(new URL('../src/views/admin/business-center/training/plan/components/PlanExpertInspectionExecutionPanel.vue', import.meta.url), 'utf8')
  ]);

  assert.match(executionPanel, /publishStatus === 'RUNNING' && props\.hasExpertInspection && props\.canViewExpertInspection/);
  assert.match(executionPanel, /@click="emit\('viewExpertInspection'\)"/);
  assert.match(planWorkspace, /@view-expert-inspection="expertInspectionVisible = true"/);
  assert.match(planWorkspace, /v-if="plan && expertInspectionBinding && expertInspectionVisible"/);
  assert.match(expertExecution, /\{ immediate: true \}/);
});

test('专家查课弹窗展示当前计划绑定的专家检查表单名称', async () => {
  const [workspace, executionPanel] = await Promise.all([
    readFile(new URL('../src/views/admin/business-center/training/plan/workspace/index.vue', import.meta.url), 'utf8'),
    readFile(new URL('../src/views/admin/business-center/training/plan/components/PlanExpertInspectionExecutionPanel.vue', import.meta.url), 'utf8')
  ]);
  assert.match(workspace, /:form-name="expertInspectionBinding\.formName"/);
  assert.match(executionPanel, /\{\{ props\.formName \|\| '专家检查评价表单' \}\}/);
  assert.doesNotMatch(executionPanel, /已绑定 V/);
});
