import assert from 'node:assert/strict';
import {readFile} from 'node:fs/promises';
import test from 'node:test';
import ts from 'typescript';

const apiUrl = new URL('../src/api/training-type.ts', import.meta.url);

/** 以导出的 API 函数为边界，记录关闭 Contract Mock 后发出的真实请求。 */
async function loadTrainingTypeApi(response = {code: 0, message: 'success', data: null}) {
  const source = await readFile(apiUrl, 'utf8');
  const requests = [];
  const output = ts.transpileModule(
    source.replace(
      /import\s*\{\s*request\s*}\s*from\s*'\.\/request';/,
      'const request = globalThis.__trainingTypeRequest;'
    ),
    {compilerOptions: {module: ts.ModuleKind.ESNext, target: ts.ScriptTarget.ES2022}}
  ).outputText;
  globalThis.__trainingTypeRequest = async (config) => {
    requests.push(config);
    return response;
  };
  globalThis.__TRAINING_TYPE_MOCK_ENABLED__ = false;
  return {
    api: await import(`data:text/javascript;base64,${Buffer.from(output).toString('base64')}#${Math.random()}`),
    requests
  };
}

const createPayload = {
  typeCode: 'SPECIAL_OPERATION',
  typeName: '特种作业培训',
  workType: 'ELECTRICIAN',
  tag: '取证',
  initialHours: 72,
  reviewHours: 24,
  onlineMaxRatio: 0.4,
  status: 1,
  description: '特种作业人员安全技术培训'
};

test('培训类型 CRUD 严格映射 Training OpenAPI', async () => {
  const {api, requests} = await loadTrainingTypeApi({code: 0, message: 'success', data: 9});
  const query = {
    pageNum: 1,
    pageSize: 20,
    'query.typeCode': 'SPECIAL_OPERATION',
    'query.typeName': '特种',
    'query.status': 1
  };
  const updatePayload = {
    version: 2,
    typeName: '特种作业人员培训',
    status: 1,
    description: '修订说明'
  };

  await api.getTrainingTypePage(query);
  await api.getTrainingTypeDetail(9);
  await api.createTrainingType(createPayload);
  await api.updateTrainingType(9, updatePayload);
  await api.deleteTrainingType(9);

  assert.deepEqual(requests, [
    {url: '/api/training/types', method: 'GET', params: query},
    {url: '/api/training/types/9', method: 'GET'},
    {url: '/api/training/types', method: 'POST', data: createPayload},
    {url: '/api/training/types/9', method: 'PUT', data: updatePayload},
    {url: '/api/training/types/9', method: 'DELETE'}
  ]);
});

test('培训类型 API 不保留生产 Contract Mock 或机构查询参数', async () => {
  const source = await readFile(apiUrl, 'utf8');
  assert.doesNotMatch(source, /__TRAINING_TYPE_MOCK_ENABLED__/);
  assert.doesNotMatch(source, /'query\.organizationId'/);
  assert.doesNotMatch(source, /interface TrainingTypeCreatePayload\s*\{[\s\S]*?organizationId:/);
});
