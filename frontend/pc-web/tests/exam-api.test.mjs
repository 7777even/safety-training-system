import assert from 'node:assert/strict';
import {readFile} from 'node:fs/promises';
import test from 'node:test';
import ts from 'typescript';

const apiUrl = new URL('../src/api/exam.ts', import.meta.url);

/** 以请求层为公开边界，验证考试编排与 OpenAPI 的映射。 */
async function loadExamApi(response = {code: 0, message: 'success', data: null}) {
  const source = await readFile(apiUrl, 'utf8');
  const requests = [];
  const output = ts.transpileModule(
    source
      .replace(
        /import\s*\{\s*request\s*}\s*from\s*'\.\/request';/,
        'const request = globalThis.__examRequest;',
      )
      .replace("import * as examMock from '@/mock/exam';", 'const examMock = globalThis.__examMock;'),
    {compilerOptions: {module: ts.ModuleKind.ESNext, target: ts.ScriptTarget.ES2022}}
  ).outputText;
  globalThis.__examRequest = async (config) => {
    requests.push(config);
    return response;
  };
  globalThis.__examMock = {};
  globalThis.__EXAM_MOCK_ENABLED__ = false;
  return {
    api: await import(`data:text/javascript;base64,${Buffer.from(output).toString('base64')}#${Math.random()}`),
    requests
  };
}

const createPayload = {
  organizationId: 1, examCode: 'E1', examName: '安全考试', examType: 'ONLINE', paperVersionId: 3,
  durationSeconds: 3600, passRatio: 0.6, passScore: 60, credit: 1,
  questionOrderMode: 'IMPORT', multiSelectScoring: 'ALL_OR_NOTHING', status: 1
};
const updatePayload = {
  examName: '安全考试（修订）', durationSeconds: 1800, passRatio: 0.6, passScore: 60, credit: 1,
  questionOrderMode: 'IMPORT', multiSelectScoring: 'ALL_OR_NOTHING', status: 1, version: 0
};

test('考试创建、人员选择绑定、批阅人替换和发布遵循 Exam OpenAPI', async () => {
  const {api, requests} = await loadExamApi({code: 0, message: 'success', data: 9});
  await api.getExamPage({pageNum: 1, pageSize: 20, 'query.organizationId': 1, 'query.publishStatus': 'DRAFT'});
  await api.createExam(createPayload);
  await api.updateExam(9, updatePayload);
  await api.deleteExam(9);
  await api.bindExamSelection(9, {
    selection: {selectionType: 'USER', subjectIds: [4]},
    contextType: 'STANDALONE',
    contextId: 0
  });
  await api.getExamCandidates(9);
  await api.replaceExamReviewers(9, {reviewerUserIds: [8, 9], assignmentSource: 'USER'});
  await api.publishExam(9);

  assert.deepEqual(requests, [
    {
      url: '/api/exam/exams',
      method: 'GET',
      params: {pageNum: 1, pageSize: 20, 'query.organizationId': 1, 'query.publishStatus': 'DRAFT'}
    },
    {url: '/api/exam/exams', method: 'POST', data: createPayload},
    {url: '/api/exam/exams/9', method: 'PUT', data: updatePayload},
    {url: '/api/exam/exams/9', method: 'DELETE'},
    {
      url: '/api/exam/exams/9/candidates/bind-selection',
      method: 'POST',
      data: {selection: {selectionType: 'USER', subjectIds: [4]}, contextType: 'STANDALONE', contextId: 0}
    },
    {url: '/api/exam/exams/9/candidates', method: 'GET', params: {pageNum: 1, pageSize: 20}},
    {url: '/api/exam/exams/9/reviewers', method: 'PUT', data: {reviewerUserIds: [8, 9], assignmentSource: 'USER'}},
    {url: '/api/exam/exams/9/publish', method: 'POST'}
  ]);
});

test('考试 API 向页面透传 Contract 业务错误', async () => {
  const {api} = await loadExamApi({code: 7004, message: '试卷版本未发布', data: null});
  await assert.rejects(() => api.publishExam(9), /试卷版本未发布/);
});

test('考试 API 保留 Contract Mock 开关和考试详情读取', async () => {
  const source = await readFile(apiUrl, 'utf8');
  assert.match(source, /import \* as examMock from '@\/mock\/exam'/);
  assert.match(source, /isExamMockEnabled/);
  assert.match(source, /mockGetExam/);

  const {api, requests} = await loadExamApi({code: 0, message: 'success', data: {examId: 9}});
  globalThis.__EXAM_MOCK_ENABLED__ = false;
  await api.getExam(9);
  delete globalThis.__EXAM_MOCK_ENABLED__;

  assert.deepEqual(requests, [{url: '/api/exam/exams/9', method: 'GET'}]);
});
