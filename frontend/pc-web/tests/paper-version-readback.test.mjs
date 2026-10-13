import assert from 'node:assert/strict';
import {readFile} from 'node:fs/promises';
import test from 'node:test';
import ts from 'typescript';

const apiUrl = new URL('../src/api/paper.ts', import.meta.url);
const mockUrl = new URL('../src/mock/paper.ts', import.meta.url);

/** 加载关闭 Mock 后的试卷 API，以请求记录验证 OpenAPI 映射。 */
async function loadPaperApi() {
  const source = await readFile(apiUrl, 'utf8');
  const requests = [];
  const output = ts.transpileModule(
    source
      .replace("import * as paperMock from '@/mock/paper';", 'const paperMock = globalThis.__paperMock;')
      .replace(/import\s*\{\s*request\s*}\s*from\s*'\.\/request';/, 'const request = globalThis.__paperRequest;'),
    {compilerOptions: {module: ts.ModuleKind.ESNext, target: ts.ScriptTarget.ES2022}}
  ).outputText;
  globalThis.__paperRequest = async (config) => {
    requests.push(config);
    return {code: 0, message: 'success', data: []};
  };
  globalThis.__paperMock = {};
  globalThis.__PAPER_MOCK_ENABLED__ = false;
  return {
    api: await import(`data:text/javascript;base64,${Buffer.from(output).toString('base64')}#${Math.random()}`),
    requests
  };
}

/** 加载仅保留当前测试会话状态的试卷 Contract Mock。 */
async function loadPaperMock() {
  const source = await readFile(mockUrl, 'utf8');
  const output = ts.transpileModule(source, {
    compilerOptions: {module: ts.ModuleKind.ESNext, target: ts.ScriptTarget.ES2022}
  }).outputText;
  return import(`data:text/javascript;base64,${Buffer.from(output).toString('base64')}#${Math.random()}`);
}

test('试卷版本列表和已发布版本筛选遵循 OpenAPI', async () => {
  const {api, requests} = await loadPaperApi();
  await api.getPaperPage({pageNum: 1, pageSize: 20, 'query.hasPublishedVersion': true});
  await api.getPaperVersions(9);
  assert.deepEqual(requests, [
    {
      url: '/api/paper/papers',
      method: 'GET',
      params: {pageNum: 1, pageSize: 20, 'query.hasPublishedVersion': true}
    },
    {url: '/api/paper/papers/9/versions', method: 'GET'}
  ]);
});

test('草稿和发布版本可按 paperVersionId 回读完整组卷配置', async () => {
  const mock = await loadPaperMock();
  mock.resetPaperMock();
  const payload = {
    durationSeconds: 3600,
    passRatio: 0.6,
    questionOrderMode: 'IMPORT',
    multiSelectScoring: 'ALL_OR_NOTHING',
    randomSeed: 20260801,
    questions: [{questionId: 1, questionVersionId: 1, score: 10, sortOrder: 1}],
    drawRules: []
  };
  const draft = await mock.mockComposePaper(1, payload);
  const summaries = await mock.mockGetPaperVersions(1);
  const detail = await mock.mockGetPaperVersion(draft.data.paperVersionId);

  assert.ok(draft.data.paperVersionId > 0);
  assert.equal(summaries.data[0].publishedStatus, 'DRAFT');
  assert.equal(detail.data.randomSeed, 20260801);
  assert.deepEqual(detail.data.drawRules, []);
});
