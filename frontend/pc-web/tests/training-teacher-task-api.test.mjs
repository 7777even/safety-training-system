import assert from 'node:assert/strict';
import {readFile} from 'node:fs/promises';
import test from 'node:test';
import ts from 'typescript';

const apiUrl = new URL('../src/api/training-teacher-task.ts', import.meta.url);
const detailUrl = new URL('../src/views/admin/business-center/training/teacher-tasks/detail/index.vue', import.meta.url);
const detailStateUrl = new URL('../src/views/admin/business-center/training/teacher-tasks/detail/teacher-task-detail-state.ts', import.meta.url);
const listUrl = new URL('../src/views/admin/business-center/training/teacher-tasks/index.vue', import.meta.url);

/** 通过教师任务公开 API 验证当前 OpenAPI 的请求映射。 */
async function loadApi() {
  const source = await readFile(apiUrl, 'utf8');
  const requests = [];
  const output = ts.transpileModule(source.replace(/import\s*\{\s*request\s*}\s*from\s*'\.\/request';/, 'const request = globalThis.__teacherTaskRequest;'), {compilerOptions: {module: ts.ModuleKind.ESNext, target: ts.ScriptTarget.ES2022}}).outputText;
  globalThis.__teacherTaskRequest = async (config) => { requests.push(config); return {code: 0, message: 'success', data: null}; };
  return {api: await import(`data:text/javascript;base64,${Buffer.from(output).toString('base64')}#${Math.random()}`), requests};
}

/** 加载不依赖组件运行时的教师任务状态文案，覆盖真实状态与权限分支。 */
async function loadDetailState() {
  const source = await readFile(detailStateUrl, 'utf8');
  const output = ts.transpileModule(source, { compilerOptions: { module: ts.ModuleKind.ESNext, target: ts.ScriptTarget.ES2022 } }).outputText;
  return import(`data:text/javascript;base64,${Buffer.from(output).toString('base64')}#${Math.random()}`);
}

test('教师任务严格以名单和一人一条线下成绩映射培训 OpenAPI', async () => {
  const {api, requests} = await loadApi();
  await api.getTeacherTaskPage({pageNum: 1, pageSize: 20});
  await api.getTeacherTaskDetail(9);
  await api.startTeacherOfflineSession(9, 3);
  await api.completeTeacherOfflineSession(9, 4);
  await api.cancelTeacherOfflineSession(9, 5);
  await api.upsertOfflineScore({trainingParticipantId: 2, offlineSessionId: 9, scoreName: '线下培训成绩', score: 90, maxScore: 100, scoreSource: 'MANUAL', scoredTime: '2026-09-01T10:00:00'});
  assert.deepEqual(requests, [
    {url: '/api/training/teacher-tasks', method: 'GET', params: {pageNum: 1, pageSize: 20}},
    {url: '/api/training/teacher-tasks/9', method: 'GET'},
    {url: '/api/training/offline-sessions/9/start', method: 'POST', data: {version: 3}},
    {url: '/api/training/offline-sessions/9/complete', method: 'POST', data: {version: 4}},
    {url: '/api/training/offline-sessions/9/cancel', method: 'POST', data: {version: 5}},
    {url: '/api/training/offline-scores', method: 'POST', data: {trainingParticipantId: 2, offlineSessionId: 9, scoreName: '线下培训成绩', score: 90, maxScore: 100, scoreSource: 'MANUAL', scoredTime: '2026-09-01T10:00:00'}}
  ]);
});

test('教师场次详情只由后端动作开放写入，并使用后端名单逐人录分', async () => {
  const source = await readFile(detailUrl, 'utf8');
  assert.match(source, /getTeacherTaskDetail/);
  assert.match(source, /availableActions/);
  assert.match(source, /START/);
  assert.match(source, /COMPLETE/);
  assert.match(source, /CANCEL/);
  assert.match(source, /version/);
  assert.match(source, /startTeacherOfflineSession/);
  assert.match(source, /completeTeacherOfflineSession/);
  assert.match(source, /cancelTeacherOfflineSession/);
  assert.match(source, /canRecordScore/);
  assert.match(source, /upsertOfflineScore/);
  assert.match(source, /roster/);
  assert.match(source, /RECORD_SCORE/);
  assert.match(source, /录入成绩/);
  assert.match(source, /线下培训成绩/);
  assert.match(source, /userName/);
  assert.match(source, /getRecordScoreUnavailableMessage/);
  assert.match(source, /getTeacherTaskLoadErrorMessage/);
  assert.match(source, /本期线下培训由教师录入参训人员成绩，成绩将作为培训完成依据。/);
  assert.doesNotMatch(source, /getTrainingAttendancePage|upsertTrainingAttendance|confirmTrainingAttendance|\/api\/training\/attendance/);
  assert.doesNotMatch(source, /仅处理后端返回的本人场次/);
  assert.doesNotMatch(source, /重复提交、无权限和版本冲突/);
  assert.doesNotMatch(source, /v-model=".*(?:userId|trainingParticipantId).*ElInput/);
});

test('教师任务按场次状态和访问拒绝显示实际原因', async () => {
  const state = await loadDetailState();
  assert.equal(state.getRecordScoreUnavailableMessage('SCHEDULED', false), '请先开始本场线下培训，开始后可录入参训人员成绩。');
  assert.equal(state.getRecordScoreUnavailableMessage('COMPLETED', false), '场次已结束，不能再录入成绩。');
  assert.equal(state.getRecordScoreUnavailableMessage('CANCELLED', false), '场次已取消，不能录入成绩。');
  assert.equal(state.getRecordScoreUnavailableMessage('IN_PROGRESS', false), '当前没有录入成绩的权限，请联系管理员。');
  assert.equal(state.getRecordScoreUnavailableMessage('IN_PROGRESS', true), '');
  assert.equal(state.getTeacherTaskLoadErrorMessage({ response: { status: 403 } }), '您没有查看或处理该场次的权限，请联系管理员。');
  assert.equal(state.getTeacherTaskLoadErrorMessage({ code: 8131 }), '您没有查看或处理该场次的权限，请联系管理员。');
});

test('教师任务列表显示所属培训并为缺失名称提供降级文案', async () => {
  const source = await readFile(listUrl, 'utf8');
  assert.match(source, /trainingPlanName/);
  assert.match(source, /所属培训/);
  assert.match(source, /培训名称未获取/);
});
