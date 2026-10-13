import assert from 'node:assert/strict';
import {readFile} from 'node:fs/promises';
import test from 'node:test';
import ts from 'typescript';

const apiUrl = new URL('../src/api/training-plan.ts', import.meta.url);
const listPageUrl = new URL('../src/views/admin/business-center/training/plan/index.vue', import.meta.url);
const workspaceUrl = new URL('../src/views/admin/business-center/training/plan/workspace/index.vue', import.meta.url);
const basicPanelUrl = new URL('../src/views/admin/business-center/training/plan/components/PlanBasicPanel.vue', import.meta.url);
const processResultsPanelUrl = new URL('../src/views/admin/business-center/training/plan/components/PlanProcessResultsPanel.vue', import.meta.url);
const executionPanelUrl = new URL('../src/views/admin/business-center/training/plan/components/PlanExecutionPanel.vue', import.meta.url);
const courseOfferingUrl = new URL('../src/views/admin/business-center/course/components/CourseOfferingPanel.vue', import.meta.url);
const examListUrl = new URL('../src/views/admin/business-center/exams/exam/index.vue', import.meta.url);

/** 通过导出的计划 API 记录真实请求，验证管理员生命周期入口。 */
async function loadTrainingPlanApi() {
  const source = await readFile(apiUrl, 'utf8');
  const requests = [];
  const output = ts.transpileModule(
    source.replace(/import\s*\{\s*request\s*}\s*from\s*'\.\/request';/, 'const request = globalThis.__trainingPlanRequest;'),
    {compilerOptions: {module: ts.ModuleKind.ESNext, target: ts.ScriptTarget.ES2022}}
  ).outputText;
  globalThis.__trainingPlanRequest = async (config) => {
    requests.push(config);
    return {code: 0, message: 'success', data: null};
  };
  return {api: await import(`data:text/javascript;base64,${Buffer.from(output).toString('base64')}#${Math.random()}`), requests};
}

test('培训计划生命周期 API 严格映射 Training OpenAPI', async () => {
  const {api, requests} = await loadTrainingPlanApi();
  const query = {pageNum: 1, pageSize: 20, 'query.trainingNature': 'INITIAL'};
  const create = {
    planCode: 'PLAN-001', planName: '初培计划', trainingTypeId: 1, trainingNature: 'INITIAL',
    startTime: '2026-09-01 09:00:00', endTime: '2026-09-30 18:00:00', status: 1
  };
  await api.getTrainingPlanPage(query);
  await api.createTrainingPlan(create);
  await api.applyTrainingPlanTemplate(9, {version: 6, sourceTemplateId: 12});
  await api.publishTrainingPlan(9, 2);
  await api.precheckTrainingPlanPublish(9);
  await api.generateTrainingPlanExecutions(9);
  await api.startTrainingPlan(9, 3);
  await api.endTrainingPlan(9, 4);
  await api.cancelTrainingPlan(9, 5);
  await api.replaceTrainingPlanStages(9, {version: 6, stages: []});
  await api.bindTrainingPlanEvaluationForms(9, {version: 6, forms: []});
  await api.assignTrainingPlanParticipant(9, 11);
  await api.getTrainingPlanParticipants(9);
  await api.getTrainingPlanExecutionLinks(9);
  await api.getTrainingParticipantResults(9, 21);
  assert.deepEqual(requests, [
    {url: '/api/training/plans', method: 'GET', params: query},
    {url: '/api/training/plans', method: 'POST', data: create},
    {url: '/api/training/plans/9/apply-template', method: 'POST', data: {version: 6, sourceTemplateId: 12}},
    {url: '/api/training/plans/9/publish', method: 'POST', data: {version: 2}},
    {url: '/api/training/plans/9/publish-precheck', method: 'POST'},
    {url: '/api/training/plans/9/generate-executions', method: 'POST'},
    {url: '/api/training/plans/9/start', method: 'POST', data: {version: 3}},
    {url: '/api/training/plans/9/end', method: 'POST', data: {version: 4}},
    {url: '/api/training/plans/9/cancel', method: 'POST', data: {version: 5}},
    {url: '/api/training/plans/9/stages', method: 'PUT', data: {version: 6, stages: []}},
    {url: '/api/training/plans/9/evaluation-forms', method: 'PUT', data: {version: 6, forms: []}},
    {url: '/api/training/plans/9/participants/assign', method: 'POST', data: {userId: 11}},
    {url: '/api/training/plans/9/participants', method: 'GET'},
    {url: '/api/training/plans/9/executions', method: 'GET'},
    {url: '/api/training/plans/9/participants/21/results', method: 'GET'}
  ]);
});

test('培训计划前端不再暴露人工结果同步接口', async () => {
  const source = await readFile(apiUrl, 'utf8');
  assert.doesNotMatch(source, /synchronizeTrainingPlanResults|results\/synchronize/);
});

test('培训计划列表与工作台使用真实生命周期入口', async () => {
  const [list, workspace, basicPanel, processResultsPanel, executionPanel] = await Promise.all([
    readFile(listPageUrl, 'utf8'),
    readFile(workspaceUrl, 'utf8'),
    readFile(basicPanelUrl, 'utf8'),
    readFile(processResultsPanelUrl, 'utf8'),
    readFile(executionPanelUrl, 'utf8')
  ]);
  assert.match(list, /admin-business-page/);
  assert.match(list, /getTrainingPlanPage/);
  assert.match(list, /training:plan:list/);
  assert.match(workspace, /admin-detail-page/);
  assert.match(workspace, /createTrainingPlan/);
  assert.match(workspace, /applyTrainingPlanTemplate/);
  assert.doesNotMatch(workspace, /getTrainingTemplate\(/);
  assert.match(workspace, /updateTrainingPlan/);
  assert.match(workspace, /trainingStandardId: form\.trainingStandardId \?\? null/);
  assert.match(workspace, /sourceTemplateId: form\.sourceTemplateId \?\? null/);
  assert.match(workspace, /precheckTrainingPlanPublish/);
  assert.match(workspace, /generateTrainingPlanExecutions/);
  assert.match(workspace, /replaceTrainingPlanStages/);
  assert.match(workspace, /assignTrainingPlanParticipant/);
  assert.match(workspace, /getUserPage/);
  assert.match(workspace, /bindTrainingPlanEvaluationForms/);
  assert.match(workspace, /getEvaluationFormVersions/);
  assert.match(workspace, /getTrainingReferenceResourcePage/);
  assert.match(workspace, /QUESTION_BANK/);
  assert.doesNotMatch(workspace, /v-model="form\.userId"/);
  assert.match(workspace, /applicableRequiredHours/);
  assert.match(basicPanel, /历史未标明/);
  assert.match(executionPanel, /查看培训结果/);
});

test('培训计划列表展示既有契约中的安排与创建信息', async () => {
  const list = await readFile(listPageUrl, 'utf8');
  assert.match(list, /label="培训时间"/);
  assert.match(list, /label="培训地点"/);
  assert.match(list, /label="创建时间"/);
  assert.match(list, /formatPlanTimeRange\(row\.startTime, row\.endTime\)/);
  assert.match(list, /formatPlanDateTime\(row\.createTime\)/);
  assert.match(list, /formatPlanLocation\(row\.location\)/);
  assert.doesNotMatch(list, /managerUserId/);
});

test('独立课程考试与培训生成对象按 planContentId 清晰区分', async () => {
  const [courseApi, examApi, courseOffering, examList] = await Promise.all([
    readFile(new URL('../src/api/course-operations.ts', import.meta.url), 'utf8'),
    readFile(new URL('../src/api/exam.ts', import.meta.url), 'utf8'),
    readFile(courseOfferingUrl, 'utf8'),
    readFile(examListUrl, 'utf8')
  ]);

  for (const source of [courseApi, examApi, courseOffering, examList]) assert.match(source, /planContentId/);
  assert.match(courseApi, /'query\.planContentId'\?: number/);
  assert.match(examApi, /'query\.planContentId'\?: number/);
  assert.match(courseOffering, /独立开课/);
  assert.match(courseOffering, /培训生成/);
  assert.match(examList, /独立考试/);
  assert.match(examList, /培训生成/);
});

test('草稿模板覆盖仅通过契约接口处理取消和业务边界', async () => {
  const workspace = await readFile(workspaceUrl, 'utf8');
  assert.match(
    workspace,
    /applyTrainingPlanTemplate\(plan\.value\.trainingPlanId, \{ version: plan\.value\.version, sourceTemplateId: form\.sourceTemplateId \}\)/
  );
  assert.match(workspace, /error !== 'cancel' && error !== 'close'/);
  assert.match(workspace, /error\.code === 8157/);
  assert.match(workspace, /error\.code === 8158/);
  assert.match(workspace, /已更新草稿模板来源，保存基础信息后生效；不会复制或清空当前课表/);
});
