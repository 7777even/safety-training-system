import assert from 'node:assert/strict';
import {access, readFile} from 'node:fs/promises';
import test from 'node:test';
import ts from 'typescript';

const apiUrl = new URL('../src/api/learner-exam.ts', import.meta.url);

/** 以请求层为边界，验证学员端接口严格映射新增的 Exam OpenAPI。 */
async function loadLearnerExamApi(response = {code: 0, message: 'success', data: null}) {
  const source = await readFile(apiUrl, 'utf8');
  const requests = [];
  const output = ts.transpileModule(
    source
      .replace(/import\s*\{\s*request\s*}\s*from\s*'\.\/request';/, 'const request = globalThis.__learnerExamRequest;')
      .replace("import * as learnerExamMock from '@/mock/learner-exam';", 'const learnerExamMock = globalThis.__learnerExamMock;'),
    {compilerOptions: {module: ts.ModuleKind.ESNext, target: ts.ScriptTarget.ES2022}}
  ).outputText;
  globalThis.__learnerExamRequest = async (config) => {
    requests.push(config);
    return response;
  };
  globalThis.__learnerExamMock = {};
  globalThis.__LEARNER_EXAM_MOCK_ENABLED__ = false;
  return {
    api: await import(`data:text/javascript;base64,${Buffer.from(output).toString('base64')}#${Math.random()}`),
    requests
  };
}

test('学员考试 API 提供 Mock/真实切换与 Contract 请求映射', async () => {
  await access(apiUrl);
  const {api, requests} = await loadLearnerExamApi({code: 0, message: 'success', data: {examAttemptId: 7}});
  const answerPayload = {
    answers: [{
      attemptQuestionId: 11,
      answerText: 'A',
      items: [{itemType: 'OPTION', itemKey: 'A', itemValue: 'A', sortOrder: 1}]
    }]
  };

  await api.getLearnerExamTaskPage({pageNum: 1, pageSize: 20});
  const startedAttempt = await api.startExamAttempt({
    examId: 3,
    userId: 8,
    clientAttemptToken: 'pc-3-8',
    attemptType: 'FIRST',
    platform: 'PC'
  });
  assert.deepEqual(startedAttempt, {examAttemptId: 7});
  await api.getLearnerExamAttempt(7);
  await api.saveExamAnswers(7, answerPayload);
  await api.submitExamAttempt(7, {submitReason: 'MANUAL', usedSeconds: 120});
  await api.timeoutSubmitExamAttempt(7, {submitReason: 'TIMEOUT', usedSeconds: 1800});
  await api.recordExamAnticheatEvent(7, {eventType: 'SWITCH_AWAY', eventTime: '2026-08-18T09:00:00', eventCount: 1, actionTaken: 'RECORD'});

  assert.deepEqual(requests, [
    {url: '/api/exam/learner/tasks', method: 'GET', params: {pageNum: 1, pageSize: 20}},
    {
      url: '/api/exam/attempts/start',
      method: 'POST',
      data: {examId: 3, userId: 8, clientAttemptToken: 'pc-3-8', attemptType: 'FIRST', platform: 'PC'}
    },
    {url: '/api/exam/learner/attempts/7', method: 'GET'},
    {url: '/api/exam/attempts/7/answers', method: 'PUT', data: answerPayload},
    {url: '/api/exam/attempts/7/submit', method: 'POST', data: {submitReason: 'MANUAL', usedSeconds: 120}},
    {url: '/api/exam/attempts/7/timeout-submit', method: 'POST', data: {submitReason: 'TIMEOUT', usedSeconds: 1800}},
    {url: '/api/exam/attempts/7/anticheat-events', method: 'POST', data: {eventType: 'SWITCH_AWAY', eventTime: '2026-08-18T09:00:00', eventCount: 1, actionTaken: 'RECORD'}}
  ]);
});

test('学员考试页面为任务、作答、结果分别提供独立入口', async () => {
  const pages = [
    '../src/views/portal/exams/index.vue',
    '../src/views/portal/exams/attempt.vue',
    '../src/views/portal/exams/result.vue'
  ];
  await Promise.all(pages.map((path) => access(new URL(path, import.meta.url))));
});

test('PC 与移动端结果页展示 Contract 返回的正确答案、标准答案和解析', async () => {
  const [typeSource, pcResultSource, mobileResultSource, pcCourseResultSource, mobileCourseResultSource] = await Promise.all([
    readFile(new URL('../../shared/src/types/learner-exam.ts', import.meta.url), 'utf8'),
    readFile(new URL('../src/views/portal/exams/result.vue', import.meta.url), 'utf8'),
    readFile(new URL('../../mobile-app/src/pages/exam/result.vue', import.meta.url), 'utf8'),
    readFile(new URL('../src/views/portal/class/CourseAssessmentResult.vue', import.meta.url), 'utf8'),
    readFile(new URL('../../mobile-app/src/pages/course/assessment-result.vue', import.meta.url), 'utf8')
  ]);

  assert.match(typeSource, /analysisSnapshot/);
  assert.match(typeSource, /correctAnswerSnapshotJson/);
  assert.match(typeSource, /standardAnswer/);
  assert.match(typeSource, /isCorrectSnapshot/);
  for (const source of [pcResultSource, mobileResultSource]) {
    assert.match(source, /标准答案/);
    assert.match(source, /题目解析/);
    assert.match(source, /analysisSnapshot/);
  }
  for (const source of [pcResultSource, mobileResultSource, pcCourseResultSource, mobileCourseResultSource]) {
    assert.match(source, /function getStandardAnswer[\s\S]*?option\.optionContentSnapshot \?\? option\.optionContent/);
  }
});

test('PC 与移动端结果页仅在服务端返回答案或解析字段时展示对应内容', async () => {
  const [pcResultSource, mobileResultSource] = await Promise.all([
    readFile(new URL('../src/views/portal/exams/result.vue', import.meta.url), 'utf8'),
    readFile(new URL('../../mobile-app/src/pages/exam/result.vue', import.meta.url), 'utf8')
  ]);

  for (const source of [pcResultSource, mobileResultSource]) {
    assert.match(source, /hasStandardAnswer/);
    assert.match(source, /question\.analysisSnapshot/);
    assert.match(source, /v-if="hasStandardAnswer\(question\)"/);
  }
});

test('PC 考试结果在题干上方展示题型，并将判题状态置于同一行右侧', async () => {
  const source = await readFile(new URL('../src/views/portal/exams/result.vue', import.meta.url), 'utf8');

  assert.match(source, /function getQuestionTypeText/);
  assert.match(source, /analysis__type/);
  assert.match(source, /analysis__status-tag/);
  assert.match(source, /getQuestionTypeText\(question\)/);
});

test('培训题库练习在 PC 与移动端均按不计分练习反馈展示', async () => {
  const [typeSource, pcAttemptSource, pcResultSource, pcTrainingSource, mobileAttemptSource, mobileResultSource, mobileTrainingSource] = await Promise.all([
    readFile(new URL('../../shared/src/types/learner-exam.ts', import.meta.url), 'utf8'),
    readFile(new URL('../src/views/portal/exams/attempt.vue', import.meta.url), 'utf8'),
    readFile(new URL('../src/views/portal/exams/result.vue', import.meta.url), 'utf8'),
    readFile(new URL('../src/views/portal/training/detail.vue', import.meta.url), 'utf8'),
    readFile(new URL('../../mobile-app/src/pages/exam/attempt.vue', import.meta.url), 'utf8'),
    readFile(new URL('../../mobile-app/src/pages/exam/result.vue', import.meta.url), 'utf8'),
    readFile(new URL('../../mobile-app/src/pages/training/detail.vue', import.meta.url), 'utf8')
  ]);

  for (const field of ['assessmentKind', 'evaluatedCount', 'unassessedCount', 'accuracyRate', 'evaluationMode', 'evaluationResult']) {
    assert.match(typeSource, new RegExp(field));
  }
  for (const source of [pcAttemptSource, mobileAttemptSource]) {
    assert.match(source, /isTrainingPractice/);
    assert.match(source, /提交练习/);
    assert.match(source, /完成全部题目后提交/);
  }
  for (const source of [pcResultSource, mobileResultSource]) {
    assert.match(source, /isTrainingPractice/);
    assert.match(source, /练习反馈/);
    assert.match(source, /正确率/);
    assert.match(source, /不计分/);
    assert.match(source, /不判定/);
  }
  for (const source of [pcTrainingSource, mobileTrainingSource]) {
    assert.match(source, /shouldDisplayScore/);
    assert.match(source, /查看反馈/);
  }
});
