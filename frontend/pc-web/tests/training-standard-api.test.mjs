import assert from 'node:assert/strict';
import {readFile} from 'node:fs/promises';
import test from 'node:test';
import ts from 'typescript';

const apiUrl = new URL('../src/api/training-standard.ts', import.meta.url);

/** 以导出的 API 函数为边界，验证标准及规则操作的真实请求 Contract。 */
async function loadTrainingStandardApi(response = {code: 0, message: 'success', data: null}) {
  const source = await readFile(apiUrl, 'utf8');
  const requests = [];
  const output = ts.transpileModule(
    source.replace(
      /import\s*\{\s*request\s*}\s*from\s*'\.\/request';/,
      'const request = globalThis.__trainingStandardRequest;'
    ),
    {compilerOptions: {module: ts.ModuleKind.ESNext, target: ts.ScriptTarget.ES2022}}
  ).outputText;
  globalThis.__trainingStandardRequest = async (config) => {
    requests.push(config);
    return response;
  };
  globalThis.__TRAINING_STANDARD_MOCK_ENABLED__ = false;
  return {
    api: await import(`data:text/javascript;base64,${Buffer.from(output).toString('base64')}#${Math.random()}`),
    requests
  };
}

const createPayload = {
  standardCode: 'STD-SAFE',
  standardName: '安全生产培训标准',
  trainingTypeId: 1,
  standardVersion: '2026',
  validFrom: '2026-01-01',
  validTo: '2026-12-31',
  status: 1
};
const rules = [{
  ruleType: 'TOTAL_HOURS',
  requiredValue: 24,
  unit: 'HOUR',
  sortOrder: 1,
  status: 1,
  description: '总学时不少于 24 小时'
}];

test('培训标准 CRUD、规则替换与发布严格映射 Training OpenAPI', async () => {
  const {api, requests} = await loadTrainingStandardApi({code: 0, message: 'success', data: 12});
  const query = {
    pageNum: 1,
    pageSize: 20,
    'query.standardCode': 'STD-SAFE',
    'query.standardName': '安全',
    'query.trainingTypeId': 1,
    'query.publishStatus': 'DRAFT',
    'query.status': 1
  };
  const updatePayload = {
    version: 2,
    standardName: '安全生产培训标准（修订）',
    trainingTypeId: 1,
    standardVersion: '2026',
    validFrom: '2026-01-01',
    validTo: '2026-12-31',
    status: 1
  };

  await api.getTrainingStandardPage(query);
  await api.getTrainingStandardDetail(12);
  await api.createTrainingStandard(createPayload);
  await api.updateTrainingStandard(12, updatePayload);
  await api.replaceTrainingStandardRules(12, {version: 2, rules});
  await api.publishTrainingStandard(12, 3);
  await api.deleteTrainingStandard(12);

  assert.deepEqual(requests, [
    {url: '/api/training/standards', method: 'GET', params: query},
    {url: '/api/training/standards/12', method: 'GET'},
    {url: '/api/training/standards', method: 'POST', data: createPayload},
    {url: '/api/training/standards/12', method: 'PUT', data: updatePayload},
    {url: '/api/training/standards/12/rules', method: 'PUT', data: {version: 2, rules}},
    {url: '/api/training/standards/12/publish', method: 'POST', data: {version: 3}},
    {url: '/api/training/standards/12', method: 'DELETE'}
  ]);
});

test('培训标准 API 不保留生产 Contract Mock 或机构查询参数', async () => {
  const source = await readFile(apiUrl, 'utf8');
  assert.doesNotMatch(source, /__TRAINING_STANDARD_MOCK_ENABLED__/);
  assert.doesNotMatch(source, /'query\.organizationId'/);
  assert.doesNotMatch(source, /interface TrainingStandardCreatePayload\s*\{[\s\S]*?organizationId:/);
});
