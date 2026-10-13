import assert from 'node:assert/strict';
import {readFile} from 'node:fs/promises';
import test from 'node:test';
import ts from 'typescript';

const apiUrl = new URL('../src/api/exam.ts', import.meta.url);
const mockUrl = new URL('../src/mock/exam.ts', import.meta.url);
const orgMockUrl = new URL('../src/mock/org-collaboration.ts', import.meta.url);

/** 加载关闭 Mock 的考试 API，验证真实请求参数。 */
async function loadApi() {
  const source = await readFile(apiUrl, 'utf8');
  const requests = [];
  const output = ts.transpileModule(
    source
      .replace(/import\s*\{\s*request\s*}\s*from\s*'\.\/request';/, 'const request = globalThis.__examRequest;')
      .replace("import * as examMock from '@/mock/exam';", 'const examMock = globalThis.__examMock;'),
    {compilerOptions: {module: ts.ModuleKind.ESNext, target: ts.ScriptTarget.ES2022}}
  ).outputText;
  globalThis.__examRequest = async (config) => {
    requests.push(config);
    return {code: 0, message: 'success', data: config.url.endsWith('/reviewers') ? [] : {list: [], total: 0}};
  };
  globalThis.__examMock = {};
  globalThis.__EXAM_MOCK_ENABLED__ = false;
  return {
    api: await import(`data:text/javascript;base64,${Buffer.from(output).toString('base64')}#${Math.random()}`),
    requests
  };
}

/** 加载独立考试 Mock 会话。 */
async function loadMock() {
  const [source, orgSource] = await Promise.all([readFile(mockUrl, 'utf8'), readFile(orgMockUrl, 'utf8')]);
  const orgOutput = ts.transpileModule(orgSource, {
    compilerOptions: {module: ts.ModuleKind.ESNext, target: ts.ScriptTarget.ES2022}
  }).outputText;
  const orgModule = `data:text/javascript;base64,${Buffer.from(orgOutput).toString('base64')}`;
  const output = ts.transpileModule(source.replace("from './org-collaboration';", `from '${orgModule}';`), {
    compilerOptions: {module: ts.ModuleKind.ESNext, target: ts.ScriptTarget.ES2022}
  }).outputText;
  return import(`data:text/javascript;base64,${Buffer.from(output).toString('base64')}#${Math.random()}`);
}

test('候选人分页和批阅人回读遵循补全后的 OpenAPI', async () => {
  const {api, requests} = await loadApi();
  const params = {
    pageNum: 2,
    pageSize: 20,
    'query.learnerKeyword': '张三',
    'query.candidateStatus': 'PENDING',
    'query.organizationId': 1
  };
  await api.getExamCandidates(9, params);
  await api.getExamReviewers(9);
  assert.deepEqual(requests, [
    {url: '/api/exam/exams/9/candidates', method: 'GET', params},
    {url: '/api/exam/exams/9/reviewers', method: 'GET'}
  ]);
});

test('考试详情策略、批阅人和候选人分页均可从 Mock 回读', async () => {
  const mock = await loadMock();
  mock.resetExamMock();
  const created = await mock.mockCreateExam({
    organizationId: 1,
    examCode: 'POLICY-001',
    examName: '策略回读考试',
    examType: 'ONLINE',
    paperVersionId: 1,
    durationSeconds: 3600,
    passRatio: 0.6,
    passScore: 60,
    credit: 1,
    questionOrderMode: 'IMPORT',
    multiSelectScoring: 'ALL_OR_NOTHING',
    status: 1,
    policy: {isPcAllowed: 1, switchLimit: 3}
  });
  await mock.mockReplaceExamReviewers(created.data, {reviewerUserIds: [8, 9], assignmentSource: 'USER'});
  await mock.mockBindExamSelection(created.data, {selection: {selectionType: 'USER', subjectIds: [7]}});
  await mock.mockPublishExam(created.data);
  const detail = await mock.mockGetExam(created.data);
  const reviewers = await mock.mockGetExamReviewers(created.data);
  const candidates = await mock.mockGetExamCandidates(created.data, {pageNum: 1, pageSize: 20});

  assert.deepEqual(detail.data.policy, {isPcAllowed: 1, switchLimit: 3});
  assert.equal(detail.data.publishStatus, 'PUBLISHED');
  assert.deepEqual(reviewers.data.map((item) => item.reviewerUserId), [8, 9]);
  assert.equal(candidates.data.total, 1);
  assert.deepEqual(candidates.data.list.map((item) => item.userId), [7]);
});

test('草稿考试 Mock 允许以空数组清空批阅人', async () => {
  const mock = await loadMock();
  mock.resetExamMock();

  await mock.mockReplaceExamReviewers(1, {reviewerUserIds: [8], assignmentSource: 'USER'});
  const cleared = await mock.mockReplaceExamReviewers(1, {reviewerUserIds: [], assignmentSource: 'USER'});
  const reviewers = await mock.mockGetExamReviewers(1);

  assert.equal(cleared.code, 0);
  assert.deepEqual(reviewers.data, []);
});

test('考试配置页面回显策略、批阅人与候选人身份字段', async () => {
  const [detail, assignment] = await Promise.all([
    readFile(new URL('../src/views/admin/business-center/exams/exam/workspace/index.vue', import.meta.url), 'utf8'),
    readFile(new URL('../src/views/admin/business-center/exams/exam/components/ExamAssignmentPanel.vue', import.meta.url), 'utf8')
  ]);
  assert.match(detail, /policy: form\.policy/);
  assert.doesNotMatch(detail, /:show-policy="false"/);
  assert.match(assignment, /getExamReviewers/);
  assert.match(assignment, /learnerName/);
  assert.match(assignment, /learnerCode/);
  assert.match(assignment, /deptName/);
  assert.match(assignment, /ElPagination/);
});
