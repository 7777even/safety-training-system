import assert from 'node:assert/strict';
import {readFile} from 'node:fs/promises';
import test from 'node:test';
import ts from 'typescript';

const apiUrl = new URL('../src/api/exam-grading.ts', import.meta.url);

test('教师端按 OpenAPI 读取反作弊事件和成绩发布流水', async () => {
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
    return {code: 0, message: 'success', data: []};
  };
  globalThis.__gradingMock = {};
  globalThis.__EXAM_GRADING_MOCK_ENABLED__ = false;
  const api = await import(`data:text/javascript;base64,${Buffer.from(output).toString('base64')}#${Math.random()}`);
  await api.getExamAnticheatEvents(9);
  await api.getExamScorePublishLogs(9);
  assert.deepEqual(requests, [
    {url: '/api/exam/attempts/9/anticheat-events', method: 'GET'},
    {url: '/api/exam/attempts/9/score-publish-logs', method: 'GET'}
  ]);
});

test('批阅和成绩处置页展示审计回读结果', async () => {
  const [grading, results] = await Promise.all([
    readFile(new URL('../src/views/admin/business-center/exams/exam/components/grading/index.vue', import.meta.url), 'utf8'),
    readFile(new URL('../src/views/admin/business-center/exams/exam/components/results/index.vue', import.meta.url), 'utf8')
  ]);
  assert.match(grading, /getExamAnticheatEvents/);
  assert.match(grading, /eventType/);
  assert.match(grading, /label="行为证据"/);
  assert.match(grading, /function getEvidenceSummary/);
  assert.match(grading, /function getEvidenceDetail/);
  assert.match(grading, /查看详情/);
  assert.doesNotMatch(grading, /label="证据文件 ID"/);
  assert.match(results, /getExamScorePublishLogs/);
  assert.match(results, /scoreSnapshot/);
});

test('教师批阅使用中文标签并为主观题提供评分参考', async () => {
  const [grading, options] = await Promise.all([
    readFile(new URL('../src/views/admin/business-center/exams/exam/components/grading/index.vue', import.meta.url), 'utf8'),
    readFile(new URL('../src/constants/exam-options.ts', import.meta.url), 'utf8')
  ]);
  assert.match(options, /label:\s*'已记录'.*value:\s*'RECORD'/);
  assert.match(options, /label:\s*'待人工批阅'.*value:\s*'PENDING_MANUAL'/);
  assert.match(grading, /getExamEnumLabel\(row\.actionTaken, ANTICHEAT_ACTION_OPTIONS\)/);
  assert.match(grading, /getExamEnumLabel\(question\.answer\?\.gradingStatus, GRADING_STATUS_OPTIONS\)/);
  assert.match(grading, /参考答案/);
  assert.match(grading, /question\.standardAnswer/);
  assert.match(grading, /comments\.value\[item\.answer\.examAnswerId\] = item\.answer\.reviewComment \?\? ''/);
});
