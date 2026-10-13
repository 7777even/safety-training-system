import assert from 'node:assert/strict';
import {readFile} from 'node:fs/promises';
import test from 'node:test';
import ts from 'typescript';

const apiUrl = new URL('../src/api/paper.ts', import.meta.url);

/** 通过 Paper API 的公开边界验证 OpenAPI 请求映射。 */
async function loadPaperApi(response = {code: 0, message: 'success', data: null}) {
  const source = await readFile(apiUrl, 'utf8');
  const requests = [];
  const output = ts.transpileModule(
    source
      .replace("import * as paperMock from '@/mock/paper';", 'const paperMock = globalThis.__paperMock;')
      .replace(
        /import\s*\{\s*request\s*}\s*from\s*'\.\/request';/,
        'const request = globalThis.__paperRequest;',
      ),
    {compilerOptions: {module: ts.ModuleKind.ESNext, target: ts.ScriptTarget.ES2022}}
  ).outputText;
  globalThis.__paperRequest = async (config) => {
    requests.push(config);
    return response;
  };
  globalThis.__paperMock = {};
  globalThis.__PAPER_MOCK_ENABLED__ = false;
  return {
    api: await import(`data:text/javascript;base64,${Buffer.from(output).toString('base64')}#${Math.random()}`),
    requests
  };
}

const composePayload = {
  durationSeconds: 3600,
  passRatio: 0.6,
  questionOrderMode: 'IMPORT',
  multiSelectScoring: 'ALL_OR_NOTHING',
  questions: [{questionId: 1, score: 10, sortOrder: 1}]
};

test('试卷分页、组卷预览与发布遵循 Paper OpenAPI', async () => {
  const {api, requests} = await loadPaperApi({code: 0, message: 'success', data: {list: [], total: 0}});
  await api.getPaperPage({pageNum: 1, pageSize: 20, 'query.paperName': '期末卷'});
  await api.getPaper(9);
  await api.updatePaper(9, {paperName: '更新后的试卷', status: 1, version: 0});
  await api.deletePaper(9);
  await api.composePaper(9, composePayload);
  await api.previewPaper(9);
  await api.publishPaper(9, composePayload);
  await api.getPaperVersion(3);

  assert.deepEqual(requests, [
    {url: '/api/paper/papers', method: 'GET', params: {pageNum: 1, pageSize: 20, 'query.paperName': '期末卷'}},
    {url: '/api/paper/papers/9', method: 'GET'},
    {url: '/api/paper/papers/9', method: 'PUT', data: {paperName: '更新后的试卷', status: 1, version: 0}},
    {url: '/api/paper/papers/9', method: 'DELETE'},
    {url: '/api/paper/papers/9/compose', method: 'POST', data: composePayload},
    {url: '/api/paper/papers/9/preview', method: 'GET'},
    {url: '/api/paper/papers/9/publish', method: 'POST', data: composePayload},
    {url: '/api/paper/papers/versions/3', method: 'GET'}
  ]);
});

test('试卷 API 默认启用 Contract Mock，显式关闭后才请求真实接口', async () => {
  const {api} = await loadPaperApi();

  delete globalThis.__PAPER_MOCK_ENABLED__;
  assert.equal(api.isPaperMockEnabled(), true);
  globalThis.__PAPER_MOCK_ENABLED__ = false;
  assert.equal(api.isPaperMockEnabled(), false);
  delete globalThis.__PAPER_MOCK_ENABLED__;
});

test('试卷发布向页面透传 Contract 业务错误', async () => {
  const {api} = await loadPaperApi({code: 6507, message: '试卷草稿不满足发布条件', data: null});
  await assert.rejects(() => api.publishPaper(9, composePayload), /试卷草稿不满足发布条件/);
});
