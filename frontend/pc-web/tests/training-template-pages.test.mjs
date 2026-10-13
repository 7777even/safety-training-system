import assert from 'node:assert/strict';
import { readFile } from 'node:fs/promises';
import test from 'node:test';

const listPage = new URL(
  '../src/views/admin/business-center/training/template/index.vue',
  import.meta.url
);
const workspacePage = new URL(
  '../src/views/admin/business-center/training/template/workspace/index.vue',
  import.meta.url
);
const basicPanel = new URL(
  '../src/views/admin/business-center/training/template/components/TemplateBasicPanel.vue',
  import.meta.url
);
const stagesPanel = new URL(
  '../src/views/admin/business-center/training/template/components/TemplateStagesPanel.vue',
  import.meta.url
);
const publishPanel = new URL(
  '../src/views/admin/business-center/training/template/components/TemplatePublishPanel.vue',
  import.meta.url
);

test('培训模板列表集中提供筛选、分页和工作台入口', async () => {
  const source = await readFile(listPage, 'utf8');

  assert.match(source, /getTrainingTemplatePage/);
  assert.match(source, /getTrainingTemplateCategoryTree/);
  assert.match(source, /createTrainingTemplateCategory/);
  assert.match(source, /updateTrainingTemplateCategory/);
  assert.match(source, /deleteTrainingTemplateCategory/);
  assert.match(source, /deleteTrainingTemplate/);
  assert.match(source, /DEFAULT_LIST_PAGE_SIZE/);
  assert.match(source, /training:template:add/);
  assert.match(source, /training:template:edit/);
  assert.match(source, /training:template:delete/);
  assert.match(source, /router\.push/);
  assert.doesNotMatch(source, /OrganizationSelect/);
  assert.match(source, /getTrainingTypePage/);
  assert.match(source, /v-model="filters\.trainingTypeId"/);
  assert.doesNotMatch(source, /label="机构 ID"/);
  assert.doesNotMatch(source, /label="培训类型 ID"/);
  assert.match(source, /placeholder="请输入模板名称"/);
  assert.match(source, /master-detail-layout/);
  assert.match(source, /模板分类/);
  assert.match(source, /全部模板/);
  assert.match(source, /#default="\{ node, data \}"/);
  assert.match(source, /@node-click="selectCategory"/);
  assert.match(source, /<ElDialog/);
});

test('培训模板工作台集中完成基础信息、阶段内容与发布', async () => {
  const source = await readFile(workspacePage, 'utf8');

  assert.match(source, /getTrainingTemplate/);
  assert.match(source, /createTrainingTemplate/);
  assert.match(source, /updateTrainingTemplate/);
  assert.match(source, /replaceTrainingTemplateStages/);
  assert.match(source, /publishTrainingTemplate/);
  assert.match(source, /training:template:edit/);
  assert.match(source, /training:template:publish/);
  assert.match(source, /admin-detail-page__steps/);
  assert.match(source, /class="stage-item"/);
  assert.match(source, /COURSE/);
  assert.match(source, /EXAM/);
  assert.match(source, /QUESTION_BANK/);
  assert.match(source, /OFFLINE/);
  assert.match(source, /publishStatus === 'PUBLISHED'/);
  assert.match(source, /publishStatus === 'ARCHIVED'/);
  assert.match(source, /publishStatus !== 'DRAFT'/);
  assert.match(source, /ElMessageBox\.confirm/);
});

test('培训模板工作台使用真实数据列表选择培训依据与线上内容资源', async () => {
  const [source, basicSource, stagesSource, apiSource] = await Promise.all([
    readFile(workspacePage, 'utf8'),
    readFile(basicPanel, 'utf8'),
    readFile(stagesPanel, 'utf8'),
    readFile(new URL('../src/api/training-template.ts', import.meta.url), 'utf8')
  ]);

  assert.match(source, /getTrainingTypePage/);
  assert.match(source, /getTrainingStandardPage/);
  assert.match(source, /getTrainingReferenceResourcePage/);
  assert.match(source, /'query\.resourceType': 'COURSE'/);
  assert.match(source, /'query\.resourceType': 'EXAM'/);
  assert.match(source, /'query\.resourceType': 'QUESTION_BANK'/);
  assert.match(stagesSource, /props\.resourceOptions\(asContent\(row\)\.contentType\)/);
  assert.doesNotMatch(source, /message: '请选择所属机构'/);
  assert.match(source, /message: '请选择培训类型'/);
  assert.match(basicSource, /label="培训类型"/);
  assert.match(basicSource, /label="培训标准"/);
  assert.match(stagesSource, /row\.contentType === 'OFFLINE'/);
  assert.doesNotMatch(source, /TODO replace real api：可用于模板/);
  assert.doesNotMatch(source, /label="培训类型 ID"/);
  assert.doesNotMatch(source, /label="培训标准 ID"/);
  assert.doesNotMatch(source, /label="资源 ID"/);
  assert.doesNotMatch(source, /getCoursePage/);
  assert.doesNotMatch(source, /getExamPage/);
  assert.doesNotMatch(source, /getQuestionBankPage/);
  assert.match(apiSource, /resourceVersionId\?: number \| null/);
  assert.match(source, /resourceVersionId: content\.resourceVersionId/);
  assert.match(source, /content\.resourceVersionId = undefined/);
  assert.match(source, /resourceVersionId: selected\.versionId \?\? undefined/);
});

test('已发布模板可逐步查看阶段内容', async () => {
  const [source, basicSource] = await Promise.all([readFile(workspacePage, 'utf8'), readFile(basicPanel, 'utf8')]);

  assert.match(basicSource, /下一步：阶段内容/);
  assert.match(source, /@next="activeStep = 1"/);
  assert.doesNotMatch(basicSource, /managedCategoryTree|canCreateCategory|create-category/);
});

test('模板分类由当前登录机构确定，不提供机构筛选或选择', async () => {
  const source = await readFile(workspacePage, 'utf8');

  assert.doesNotMatch(source, /categoryFilterOrganizationId|OrganizationSelect|categoryForm\.organizationId/);
  assert.doesNotMatch(source, /请选择分类所属机构/);
});

test('培训模板工作台仅允许已保存的当前步骤进入下一步，并将规则同步到步骤导航', async () => {
  const [source, basicSource, stagesSource] = await Promise.all([
    readFile(workspacePage, 'utf8'),
    readFile(basicPanel, 'utf8'),
    readFile(stagesPanel, 'utf8')
  ]);

  assert.match(source, /basicStepCompleted/);
  assert.match(source, /stagesStepCompleted/);
  assert.match(source, /if \(step === 1\) return !basicStepCompleted\.value/);
  assert.match(source, /if \(step === 2\) return !stagesStepCompleted\.value/);
  assert.match(source, /@save="saveBasic"/);
  assert.match(source, /@save="saveStages"/);
  assert.match(basicSource, /下一步：阶段内容/);
  assert.match(basicSource, /:disabled="!props\.canNext"/);
  assert.match(stagesSource, /下一步：发布确认/);
  assert.match(stagesSource, /:disabled="!props\.canNext"/);
});

test('阶段编排恢复为最近一次已保存内容后重新允许进入发布确认', async () => {
  const source = await readFile(workspacePage, 'utf8');

  assert.match(source, /savedStagesFingerprint/);
  assert.match(source, /JSON\.stringify\(stages\.value\)/);
  assert.match(source, /const stagesStepCompleted = computed/);
  assert.doesNotMatch(source, /function markStagesDirty\(\): void \{[\s\S]*stagesStepCompleted\.value = false/);
});

test('模板分类树在列表页节点维护，新增分类明确使用顶层分类与同级排序', async () => {
  const [source, basicSource] = await Promise.all([readFile(listPage, 'utf8'), readFile(basicPanel, 'utf8')]);

  assert.match(source, /#default="\{ node, data \}"/);
  assert.match(source, /新增子级/);
  assert.match(source, /编辑/);
  assert.match(source, /删除/);
  assert.match(source, /顶层模板分类/);
  assert.match(source, /templateCategoryId: 0/);
  assert.match(source, /countCategoryChildren/);
  assert.match(source, /countCategoryChildren\(parentId\) \+ 1/);
  assert.doesNotMatch(source, /placeholder="不选择则创建顶级分类"/);
  assert.doesNotMatch(basicSource, /category-panel|managedCategoryTree/);
});

test('发布确认汇总当前模板基础信息、阶段与内容，供发布前检查', async () => {
  const [source, publishSource] = await Promise.all([readFile(workspacePage, 'utf8'), readFile(publishPanel, 'utf8')]);

  assert.match(source, /:template-code="basicForm\.templateCode"/);
  assert.match(source, /:stages="stages"/);
  assert.match(publishSource, /ElDescriptions/);
  assert.match(publishSource, /模板基础信息/);
  assert.match(publishSource, /阶段与内容清单/);
  assert.match(publishSource, /模板编码/);
  assert.match(publishSource, /培训类型/);
});
