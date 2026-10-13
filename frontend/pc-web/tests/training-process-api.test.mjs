import assert from 'node:assert/strict';
import {readFile} from 'node:fs/promises';
import test from 'node:test';
import ts from 'typescript';

const apiUrl = new URL('../src/api/training-process.ts', import.meta.url);
const workspaceUrl = new URL('../src/views/admin/business-center/training/plan/workspace/index.vue', import.meta.url);
const processPanelUrl = new URL('../src/views/admin/business-center/training/plan/components/PlanProcessResultsPanel.vue', import.meta.url);

/** 记录过程事实查询，防止工作台脱离 Training OpenAPI 自行拼接请求。 */
async function loadTrainingProcessApi() {
  const source = await readFile(apiUrl, 'utf8');
  const requests = [];
  const output = ts.transpileModule(
    source.replace(/import\s*\{\s*request\s*}\s*from\s*'\.\/request';/, 'const request = globalThis.__trainingProcessRequest;'),
    {compilerOptions: {module: ts.ModuleKind.ESNext, target: ts.ScriptTarget.ES2022}}
  ).outputText;
  globalThis.__trainingProcessRequest = async (config) => {
    requests.push(config);
    return {code: 0, message: 'success', data: null};
  };
  return {api: await import(`data:text/javascript;base64,${Buffer.from(output).toString('base64')}#${Math.random()}`), requests};
}

test('计划过程事实查询严格映射培训 OpenAPI', async () => {
  const {api, requests} = await loadTrainingProcessApi();
  await api.getTrainingOfflineSessionPage({pageNum: 1, pageSize: 100, 'query.trainingPlanId': 9});
  await api.getTrainingAttachmentPage({pageNum: 1, pageSize: 100, 'query.trainingPlanId': 9});
  await api.getTrainingRewardPenaltyPage({pageNum: 1, pageSize: 100, 'query.trainingPlanId': 9});
  assert.deepEqual(requests, [
    {url: '/api/training/offline-sessions', method: 'GET', params: {pageNum: 1, pageSize: 100, 'query.trainingPlanId': 9}},
    {url: '/api/training/attachments', method: 'GET', params: {pageNum: 1, pageSize: 100, 'query.trainingPlanId': 9}},
    {url: '/api/training/reward-penalties', method: 'GET', params: {pageNum: 1, pageSize: 100, 'query.trainingPlanId': 9}}
  ]);
});

test('计划工作台集中追踪线下场次、附件与奖惩事实', async () => {
  const source = await readFile(workspaceUrl, 'utf8');
  assert.match(source, /getTrainingOfflineSessionPage/);
  assert.match(source, /getTrainingAttachmentPage/);
  assert.match(source, /getTrainingRewardPenaltyPage/);
  assert.match(source, /PlanProcessResultsPanel/);
  assert.match(source, /:offline-sessions="offlineSessions"/);
  assert.match(source, /:attachments="attachments"/);
  assert.match(source, /:reward-penalties="rewardPenalties"/);
});

test('计划过程页使用当前线下场次状态枚举展示中文状态', async () => {
  const source = await readFile(processPanelUrl, 'utf8');
  assert.match(source, /IN_PROGRESS:\s*'进行中'/);
  assert.match(source, /COMPLETED:\s*'已结束'/);
  assert.doesNotMatch(source, /RUNNING:\s*'进行中'/);
  assert.doesNotMatch(source, /ENDED:\s*'已结束'/);
});
