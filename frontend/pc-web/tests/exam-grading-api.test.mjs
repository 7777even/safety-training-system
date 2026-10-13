import assert from 'node:assert/strict';
import {access, readFile} from 'node:fs/promises';
import test from 'node:test';
import ts from 'typescript';

const apiUrl = new URL('../src/api/exam-grading.ts', import.meta.url);

/** 以教师端 API 作为公开测试边界，验证 Task 5 的既有 OpenAPI 映射。 */
async function loadApi(response = {code: 0, message: 'success', data: null}) {
  const source = await readFile(apiUrl, 'utf8');
  const requests = [];
  const output = ts.transpileModule(
    source
      .replace(/import\s*\{\s*request\s*}\s*from\s*'\.\/request';/, 'const request = globalThis.__gradingRequest;')
      .replace("import * as gradingMock from '@/mock/exam-grading';", 'const gradingMock = globalThis.__gradingMock;'),
    {compilerOptions: {module: ts.ModuleKind.ESNext, target: ts.ScriptTarget.ES2022}}
  ).outputText;
  globalThis.__gradingRequest = async (config) => {
    requests.push(config);
    return response;
  };
  globalThis.__gradingMock = {};
  globalThis.__EXAM_GRADING_MOCK_ENABLED__ = false;
  return {
    api: await import(`data:text/javascript;base64,${Buffer.from(output).toString('base64')}#${Math.random()}`),
    requests
  };
}

test('教师批阅和成绩处置严格映射已定义的 Exam OpenAPI', async () => {
  await access(apiUrl);
  const {api, requests} = await loadApi({code: 0, message: 'success', data: {list: [], total: 0}});
  await api.getExamReviewTaskPage({
    pageNum: 1,
    pageSize: 20,
    'query.reviewStatus': 'PENDING',
    'query.learnerKeyword': '张三'
  });
  await api.getExamAttemptRecordPage({
    pageNum: 1,
    pageSize: 20,
    'query.reviewStatus': 'COMPLETED',
    'query.scorePublishStatus': 'UNPUBLISHED'
  });
  await api.getReviewAttempt(9);
  await api.reviewExamAnswer(20, {reviewerUserId: 8, afterScore: 8, reviewComment: '要点完整', reviewAction: 'SCORE'});
  await api.publishExamScore(9, 8);
  await api.createRetakeAttempt(5, 'retake-5-1');
  await api.resetPractice(5, {resetScope: 'ALL', reason: '重练'});
  assert.deepEqual(requests, [
    {
      url: '/api/exam/review-tasks',
      method: 'GET',
      params: {pageNum: 1, pageSize: 20, 'query.reviewStatus': 'PENDING', 'query.learnerKeyword': '张三'}
    },
    {
      url: '/api/exam/attempts',
      method: 'GET',
      params: {pageNum: 1, pageSize: 20, 'query.reviewStatus': 'COMPLETED', 'query.scorePublishStatus': 'UNPUBLISHED'}
    },
    {url: '/api/exam/attempts/9', method: 'GET'},
    {
      url: '/api/exam/answers/20/review',
      method: 'POST',
      data: {reviewerUserId: 8, afterScore: 8, reviewComment: '要点完整', reviewAction: 'SCORE'}
    },
    {url: '/api/exam/attempts/9/publish-score', method: 'POST', params: {publishedBy: 8}},
    {url: '/api/exam/candidates/5/retake', method: 'POST', params: {clientAttemptToken: 'retake-5-1'}},
    {url: '/api/exam/candidates/5/practice-reset', method: 'POST', data: {resetScope: 'ALL', reason: '重练'}}
  ]);
});

test('教师批阅与成绩处置为已授权内部页面，成绩处置复用既有补考接口', async () => {
  await Promise.all([
    access(new URL('../src/views/admin/business-center/exams/review/index.vue', import.meta.url)),
    access(new URL('../src/views/admin/business-center/exams/results/index.vue', import.meta.url)),
    access(new URL('../src/views/admin/business-center/exams/exam/grading/index.vue', import.meta.url)),
    access(new URL('../src/views/admin/business-center/exams/exam/results/index.vue', import.meta.url))
  ]);
  const [apiSource, routeSource, resultPageSource] = await Promise.all([
    readFile(apiUrl, 'utf8'),
    readFile(new URL('../src/router/admin-dynamic-routes.ts', import.meta.url), 'utf8'),
    readFile(new URL('../src/views/admin/business-center/exams/exam/results/index.vue', import.meta.url), 'utf8')
  ]);
  assert.doesNotMatch(apiSource, /\/api\/course\/.*exam/i);
  assert.match(routeSource, /\/admin\/business-center\/exams\/exam\/:examId\/grading\/:examAttemptId/);
  assert.match(routeSource, /permission: 'exam:review'/);
  assert.match(
    routeSource,
    /name: 'ExamGrading',[\s\S]*?sourcePath: '\/admin\/business-center\/exams\/exam'/
  );
  assert.match(routeSource, /\/admin\/business-center\/exams\/exam\/:examId\/results\/:examAttemptId/);
  assert.match(routeSource, /permission: 'exam:score:publish'/);
  assert.match(
    routeSource,
    /name: 'ExamResults',[\s\S]*?sourcePath: '\/admin\/business-center\/exams\/exam'/
  );
  assert.doesNotMatch(resultPageSource, /课程考试项回接未实施/);
  assert.match(resultPageSource, /publishExamScore/);
  assert.match(resultPageSource, /createRetakeAttempt/);
  assert.doesNotMatch(resultPageSource, /resetPractice/);
});
