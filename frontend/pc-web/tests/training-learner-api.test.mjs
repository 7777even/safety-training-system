import assert from 'node:assert/strict';
import { readFile } from 'node:fs/promises';
import test from 'node:test';
import ts from 'typescript';

const apiUrl = new URL('../src/api/training-learner.ts', import.meta.url);
const trainingPageUrl = new URL('../src/views/portal/training/index.vue', import.meta.url);
const detailUrl = new URL('../src/views/portal/training/detail.vue', import.meta.url);
const offlineSessionUrl = new URL('../src/views/portal/training/offline-session.vue', import.meta.url);
const learnerStatusUrl = new URL('../src/views/portal/training/learner-training-status.ts', import.meta.url);
const routesUrl = new URL('../src/router/admin-dynamic-routes.ts', import.meta.url);
const assessmentErrorUrl = new URL('../../shared/src/libs/assessment-execution.ts', import.meta.url);

async function loadLearnerStatus() {
  const source = await readFile(learnerStatusUrl, 'utf8');
  const output = ts.transpileModule(source, {
    compilerOptions: { module: ts.ModuleKind.ESNext, target: ts.ScriptTarget.ES2022 }
  }).outputText;
  return import(`data:text/javascript;base64,${Buffer.from(output).toString('base64')}#${Math.random()}`);
}

async function loadApi() {
  const source = await readFile(apiUrl, 'utf8');
  const requests = [];
  const clientSource = await readFile(new URL('../../shared/src/libs/exam-client-info.ts',import.meta.url),'utf8');
  const clientJs = ts.transpileModule(clientSource,{compilerOptions:{module:ts.ModuleKind.ESNext,target:ts.ScriptTarget.ES2022}}).outputText;
  const clientUrl = `data:text/javascript;base64,${Buffer.from(clientJs).toString('base64')}`;
  const output = ts.transpileModule(
    source.replace("'@shared/libs/exam-client-info'",JSON.stringify(clientUrl)).replace(/import\s*\{\s*request\s*}\s*from\s*'\.\/request';/, 'const request = globalThis.__trainingLearnerRequest;'),
    { compilerOptions: { module: ts.ModuleKind.ESNext, target: ts.ScriptTarget.ES2022 } }
  ).outputText;
  globalThis.__trainingLearnerRequest = async (config) => {
    requests.push(config);
    return { code: 0, message: 'success', data: [] };
  };
  return { api: await import(`data:text/javascript;base64,${Buffer.from(output).toString('base64')}#${Math.random()}`), requests };
}

test('PC 学员培训接口仅查询本人任务，进入考试不指定作答类型，并读取本人线下场次', async () => {
  const { api, requests } = await loadApi();
  await api.getTrainingLearnerTasks();
  await api.getTrainingLearnerParticipantDetail(3);
  await api.enterTrainingContent(3, 8, { clientAttemptToken: 'token', platform: 'PC' });
  await api.getTrainingLearnerOfflineSession(9);
  assert.deepEqual(requests, [
    { url: '/api/training/learner/tasks', method: 'GET' },
    { url: '/api/training/learner/participants/3', method: 'GET' },
    { url: '/api/training/learner/participants/3/contents/8/enter', method: 'POST', data: { clientAttemptToken: 'token', platform: 'PC', operatingSystem:'未知', browser:'未知' } },
    { url: '/api/training/learner/offline-sessions/9', method: 'GET' }
  ]);
});

test('PC 门户培训页提供任务列表、详情和统一内容入口', async () => {
  const [api, index, detail, offlineSession, routes] = await Promise.all([
    readFile(apiUrl, 'utf8'),
    readFile(trainingPageUrl, 'utf8'),
    readFile(detailUrl, 'utf8'),
    readFile(offlineSessionUrl, 'utf8'),
    readFile(routesUrl, 'utf8')
  ]);
  assert.match(index, /getTrainingLearnerTasks/);
  assert.match(index, /我的培训/);
  assert.match(detail, /getTrainingLearnerParticipantDetail/);
  assert.match(detail, /enterTrainingContent/);
  assert.match(detail, /PENDING_REVIEW: '待阅卷'/);
  assert.match(detail, /PENDING_PUBLISH: '待发布成绩'/);
  assert.match(detail, /isExamResultAvailable/);
  assert.match(detail, /'PENDING_REVIEW', 'PENDING_PUBLISH', 'COMPLETED', 'PASSED', 'FAILED'/);
  assert.match(api, /finalAttemptId\?: number \| null/);
  assert.match(detail, /resultFor\(content\.planContentId\)\?\.finalAttemptId/);
  assert.match(detail, /\/portal\/training\/attempt\/\$\{finalAttemptId\}\/result/);
  assert.match(detail, /\/portal\/training\/attempt\/\$\{data\.examAttemptId\}\/result/);
  assert.match(api, /sourceRecordId\?: number \| null/);
  assert.match(api, /executionId\?: number \| null/);
  assert.match(detail, /\/portal\/class\/learning\/\$\{result\.sourceRecordId\}/);
  assert.match(detail, /\/portal\/training\/offline-session\/\$\{result\.executionId\}/);
  assert.match(detail, /@click="viewResult\(content\)"/);
  assert.match(detail, /EXPIRED: '已到期'/);
  assert.match(detail, /RUNNING: '进行中'/);
  assert.match(detail, /SKIPPED: '已跳过'/);
  assert.match(detail, /examAttemptId/);
  assert.match(detail, /PENDING_REVIEW.*查看答卷/);
  assert.match(detail, /contentType === 'QUESTION_BANK' \? '查看反馈' : '查看结果'/);
  assert.match(detail, /offline-session/);
  assert.match(detail, /安排\s*\{\{\s*content\.requiredHours\s*\}\}\s*学时/);
  assert.match(detail, /获得学时/);
  assert.match(detail, /effectiveDurationSeconds/);
  assert.doesNotMatch(detail, /attemptType: 'FIRST'/);
  assert.match(offlineSession, /getTrainingLearnerOfflineSession/);
  assert.match(offlineSession, /地点/);
  assert.match(offlineSession, /本人成绩/);
  assert.match(offlineSession, /获得学时/);
  assert.doesNotMatch(offlineSession, /attendance/);
  assert.match(routes, /PortalTrainingDetail/);
  assert.match(routes, /PortalTrainingExamAttempt/);
  assert.match(routes, /PortalTrainingOfflineSession/);
});

test('线下培训卡片优先展示教师推进的场次状态，个人完成状态保持独立', async () => {
  const { getLearnerContentDisplayStatus } = await loadLearnerStatus();
  assert.equal(
    getLearnerContentDisplayStatus('OFFLINE', 'PENDING', null, 'IN_PROGRESS'),
    'IN_PROGRESS'
  );
  assert.equal(
    getLearnerContentDisplayStatus('COURSE', 'PENDING', null, 'IN_PROGRESS'), 'PENDING');
});

test('学员线下培训详情分别展示场次状态和个人完成状态', async () => {
  const [api, detail, offlineSession] = await Promise.all([
    readFile(apiUrl, 'utf8'),
    readFile(detailUrl, 'utf8'),
    readFile(offlineSessionUrl, 'utf8')
  ]);
  assert.match(api, /sessionStatus\?: string \| null/);
  assert.match(detail, /getLearnerContentDisplayStatus/);
  assert.match(detail, /function isCompleted[\s\S]*contentStatus\(content\)/);
  assert.match(offlineSession, /场次状态/);
  assert.match(offlineSession, /完成状态/);
  assert.match(offlineSession, /sessionStatusText/);
  assert.match(offlineSession, /offline-session-hero/);
  assert.match(offlineSession, /offline-session__schedule/);
  assert.match(offlineSession, /offline-session__metrics/);
});

test('PC 培训入口将 7063 映射为统一提示且保留其他后端错误', async () => {
  const [api, detail, errorHelper] = await Promise.all([
    readFile(apiUrl, 'utf8'),
    readFile(detailUrl, 'utf8'),
    readFile(assessmentErrorUrl, 'utf8')
  ]);
  assert.match(api, /class TrainingLearnerApiError extends Error/);
  assert.match(detail, /getAssessmentExecutionErrorMessage\(error, '进入培训内容失败。'\)/);
  assert.match(errorHelper, /ASSESSMENT_EXECUTION_SNAPSHOT_ERROR_CODE = 7063/);
  assert.match(errorHelper, /return error instanceof Error \? error\.message : fallback/);
});
