import assert from 'node:assert/strict';
import { readFile } from 'node:fs/promises';
import test from 'node:test';
import ts from 'typescript';

const apiUrl = new URL('../src/api/training-reference.ts', import.meta.url);

/** 以请求配置为公开边界加载当前机构可引用资源 API。 */
async function loadTrainingReferenceApi(response) {
  const source = await readFile(apiUrl, 'utf8');
  const requests = [];
  const output = ts.transpileModule(
    source.replace(
      /import\s*\{\s*request\s*}\s*from\s*'\.\/request';/,
      'const request = globalThis.__trainingReferenceRequest;'
    ),
    { compilerOptions: { module: ts.ModuleKind.ESNext, target: ts.ScriptTarget.ES2022 } }
  ).outputText;
  globalThis.__trainingReferenceRequest = async (config) => {
    requests.push(config);
    return response;
  };
  return {
    api: await import(`data:text/javascript;base64,${Buffer.from(output).toString('base64')}#${Math.random()}`),
    requests
  };
}

test('当前机构可引用资源查询不提交机构 ID，并保留可执行版本标识', async () => {
  const response = {
    code: 0,
    message: 'success',
    data: {
      list: [
        {
          resourceType: 'COURSE',
          resourceId: 7001,
          versionId: 7101,
          resourceCode: 'COURSE-001',
          resourceName: '安全生产基础课程',
          organizationId: 1,
          organizationName: '示例企业',
          publishStatus: 'PUBLISHED',
          status: 1,
          executable: true
        }
      ],
      total: 1
    }
  };
  const { api, requests } = await loadTrainingReferenceApi(response);

  const result = await api.getTrainingReferenceResourcePage({
    pageNum: 1,
    pageSize: 100,
    'query.resourceType': 'COURSE',
    'query.keyword': '安全生产'
  });

  assert.deepEqual(requests, [
    {
      url: '/api/training/reference-resources',
      method: 'GET',
      params: {
        pageNum: 1,
        pageSize: 100,
        'query.resourceType': 'COURSE',
        'query.keyword': '安全生产'
      }
    }
  ]);
  assert.deepEqual(result, response.data);
  assert.equal(result.list[0].versionId, 7101);
  assert.equal('organizationId' in requests[0].params, false);
  assert.equal('query.organizationId' in requests[0].params, false);
});

test('当前机构可引用资源查询把业务失败转换为中文异常', async () => {
  const { api } = await loadTrainingReferenceApi({
    code: 8053,
    message: '未找到当前机构',
    data: null
  });

  await assert.rejects(
    () =>
      api.getTrainingReferenceResourcePage({
        pageNum: 1,
        pageSize: 100,
        'query.resourceType': 'EXAM'
      }),
    (error) => error.name === 'TrainingReferenceApiError'
      && error.code === 8053
      && error.message === '未找到当前机构'
  );
});

test('当前机构可引用资源 API 不保留生产 Contract Mock', async () => {
  const source = await readFile(apiUrl, 'utf8');
  assert.doesNotMatch(source, /__TRAINING_REFERENCE_MOCK_ENABLED__/);
  assert.doesNotMatch(source, /MOCK_REFERENCE_RESOURCES/);
});
