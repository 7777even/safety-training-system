import assert from 'node:assert/strict';
import { readFile } from 'node:fs/promises';
import test from 'node:test';

const trainingTypePage = new URL(
  '../src/views/admin/business-center/training/type/index.vue',
  import.meta.url
);
const trainingStandardPage = new URL(
  '../src/views/admin/business-center/training/standard/index.vue',
  import.meta.url
);
const trainingStandardWorkspace = new URL(
  '../src/views/admin/business-center/training/standard/workspace/index.vue',
  import.meta.url
);
const adminDynamicRoutes = new URL('../src/router/admin-dynamic-routes.ts', import.meta.url);

test('培训类型页面按当前机构契约维护初培和复审学时', async () => {
  const source = await readFile(trainingTypePage, 'utf8');

  assert.match(source, /DEFAULT_LIST_PAGE_SIZE/);
  assert.match(source, /getTrainingTypePage/);
  assert.match(source, /getTrainingTypeDetail/);
  assert.match(source, /createTrainingType/);
  assert.match(source, /updateTrainingType/);
  assert.match(source, /deleteTrainingType/);
  assert.match(source, /training:type:add/);
  assert.match(source, /training:type:edit/);
  assert.match(source, /training:type:delete/);
  assert.match(source, /ElMessageBox\.confirm/);
  assert.match(source, /v-model="form\.tag"/);
  assert.doesNotMatch(source, /OrganizationSelect/);
  assert.match(source, /UnifiedFileUpload/);
  assert.match(source, /v-model="uploadVisible"/);
  assert.match(source, /mode="dialog"/);
  assert.match(source, /uploadAssetFile/);
  assert.match(source, /form\.coverFileId = fileInfo\.fileInfoId/);
  assert.match(source, /form\.uploadedImageName = fileInfo\.fileName/);
  assert.match(source, /form\.uploadedImageSize = fileInfo\.fileSize/);
  assert.match(source, /message: '请输入大于 0 的初培最低学时'/);
  assert.match(source, /message: '请输入大于 0 的复审最低学时'/);
  assert.match(source, /message: '请输入类型编码'/);
  assert.match(source, /message: '请输入类型名称'/);
  assert.match(source, /message: '请输入培训类型说明'/);
  assert.doesNotMatch(source, /label="机构 ID"/);
  assert.doesNotMatch(source, /label="图片文件 ID"/);
  assert.doesNotMatch(source, /v-model="form\.coverFileId"/);
  assert.doesNotMatch(source, /mode="panel"/);
  assert.match(source, /placeholder="请输入类型名称"/);
});

test('培训标准页面以列表到独立工作台的节奏组织规则和发布操作', async () => {
  const source = await readFile(trainingStandardPage, 'utf8');

  assert.match(source, /DEFAULT_LIST_PAGE_SIZE/);
  assert.match(source, /getTrainingStandardPage/);
  assert.match(source, /training:standard:add/);
  assert.match(source, /training:standard:edit/);
  assert.match(source, /training:standard:publish/);
  assert.match(source, /ARCHIVED/);
  assert.match(source, /已归档/);
  assert.match(source, /router\.push/);
  assert.doesNotMatch(source, /<ElDialog/);
});

test('培训标准工作台在同一页面编排基础信息、规则和发布', async () => {
  const source = await readFile(trainingStandardWorkspace, 'utf8');

  assert.match(source, /getTrainingStandardDetail/);
  assert.match(source, /replaceTrainingStandardRules/);
  assert.match(source, /publishTrainingStandard/);
  assert.match(source, /training:standard:edit/);
  assert.match(source, /training:standard:publish/);
  assert.match(source, /admin-detail-page__steps/);
  assert.match(source, /class="stage-item"/);
  assert.match(source, /requiredValue/);
  assert.match(source, /ONLINE_RATIO/);
  assert.match(source, /form\.publishStatus !== 'DRAFT'/);
  assert.match(source, /if \(standardId\.value\) await loadDetail\(\)/);
  assert.match(source, /loadResourceOptions\(\)/);
  assert.doesNotMatch(source, /OrganizationSelect/);
  assert.match(source, /getTrainingTypePage/);
  assert.match(source, /getTrainingReferenceResourcePage/);
  assert.match(source, /'query\.resourceType': 'COURSE'/);
  assert.match(source, /'query\.resourceType': 'EXAM'/);
  assert.doesNotMatch(source, /getCoursePage/);
  assert.doesNotMatch(source, /getExamPage/);
  assert.match(source, /StandardBasicPanel/);
  assert.match(source, /StandardRulesPanel/);
  assert.doesNotMatch(source, /message: '请选择所属机构'/);
  assert.match(source, /message: '请选择培训类型'/);
  assert.doesNotMatch(source, /label="机构 ID"/);
  assert.doesNotMatch(source, /label="培训类型 ID"/);
  assert.doesNotMatch(source, /label="资源 ID"/);
  assert.doesNotMatch(source, /v-model="row\.resourceType"/);
  assert.match(source, /StandardRulesPanel/);
});

test('培训标准工作台由来源菜单授权后注册为内部路由', async () => {
  const source = await readFile(adminDynamicRoutes, 'utf8');

  assert.match(source, /TrainingStandardCreate/);
  assert.match(source, /\/admin\/business-center\/training\/standard\/create/);
  assert.match(source, /TrainingStandardWorkspace/);
  assert.match(source, /\/admin\/business-center\/training\/standard\/:trainingStandardId/);
  assert.match(source, /permission: 'training:standard:list'/);
  assert.match(source, /sourcePath: '\/admin\/business-center\/training\/standard'/);
});

test('模板和评价表单工作台由各自来源菜单与按钮权限注册', async () => {
  const source = await readFile(adminDynamicRoutes, 'utf8');

  assert.match(source, /TrainingTemplateCreate/);
  assert.match(source, /\/admin\/business-center\/training\/template\/:trainingTemplateId/);
  assert.match(source, /permission: 'training:template:list'/);
  assert.match(source, /EvaluationFormCreate/);
  assert.match(source, /\/admin\/business-center\/training\/evaluation-form\/workspace\/create/);
  assert.match(source, /\/admin\/business-center\/training\/evaluation-form\/workspace\/:evaluationFormId/);
  assert.match(source, /EvaluationFormVersion/);
  assert.match(source, /\/admin\/business-center\/training\/evaluation-form\/version\/:formVersionId/);
  assert.match(source, /evaluation:form:list/);
});
