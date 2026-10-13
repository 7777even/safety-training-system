import assert from 'node:assert/strict';
import { readFile } from 'node:fs/promises';
import test from 'node:test';
import ts from 'typescript';
const compile = source => `data:text/javascript;base64,${Buffer.from(ts.transpileModule(source, { compilerOptions: { module: ts.ModuleKind.ESNext, target: ts.ScriptTarget.ES2022 } }).outputText).toString('base64')}`;
const exam = compile(await readFile(new URL('../../shared/src/utils/exam.ts', import.meta.url), 'utf8'));
const source = (await readFile(new URL('../../shared/src/libs/message-exams.ts', import.meta.url), 'utf8')).replace("'../utils/exam'", JSON.stringify(exam));
const { loadMessageExams, messageExamInfo } = await import(compile(source));
const message = { kind: 'FORMAL_EXAM', resourceId: 2, parentId: 3 };
const task = { examCandidateId: 2, examId: 3, examType: 'OFFLINE', availableAction: null, currentAttemptId: null };
test('线下未发布成绩消息说明类型并禁用作答，已发布仅允许查看成绩', () => {
  const before = messageExamInfo(message, { '2:3': task });
  assert.equal(before.typeLabel, '线下考试'); assert.equal(before.canOpen, false); assert.match(before.hint, /不支持在线答题/);
  for (const availableAction of ['START', 'CONTINUE', 'WAIT_REVIEW']) {
    assert.equal(messageExamInfo(message, { '2:3': { ...task, availableAction } }).canOpen, false);
  }
  assert.equal(messageExamInfo(message, { '2:3': { ...task, availableAction: 'VIEW_RESULT' } }).canOpen, false);
  const after = messageExamInfo(message, { '2:3': { ...task, availableAction: 'VIEW_RESULT', currentAttemptId: 4 } });
  assert.equal(after.canOpen, true); assert.equal(after.actionLabel, '查看成绩');
  assert.equal(messageExamInfo(message, {}).canOpen, false);
});
test('线上动作复用服务端，消息分页查询跨页匹配且不会串考试', async () => {
  const calls = [];
  const tasks = await loadMessageExams([message, message], async page => {
    calls.push(page); return { total: 201, list: page === 1 ? [{ ...task, examId: 9 }] : [{ ...task, examType: 'ONLINE', availableAction: 'CONTINUE' }] };
  });
  assert.deepEqual(calls, [1, 2]); assert.equal(Object.keys(tasks).length, 1);
  assert.equal(messageExamInfo(message, tasks).actionLabel, '继续作答');
  assert.equal(messageExamInfo({ kind: 'COURSE' }, {}).canOpen, true);
  await loadMessageExams([{ kind: 'COURSE' }], () => { throw new Error('非考试不应请求'); });
});
for (const path of ['../src/views/user-center/messages/index.vue', '../../mobile-app/src/pages/messages/index.vue']) {
  test(`${path} 消费类型和可执行动作，线下等待成绩时不开放按钮`, async () => {
    const vue = await readFile(new URL(path, import.meta.url), 'utf8');
    assert.match(vue, /messageExamInfo/); assert.match(vue, /\.typeLabel/); assert.match(vue, /\.hint/);
    assert.match(vue, /:disabled="!examInfo\(message\)\.canOpen"/);
    assert.match(vue, /examInfo\(message\)\.actionLabel/);
  });
}
test('本人消息考试信息读取覆盖线上、线下及两种独立练习的现有接口', async () => {
  for (const path of ['../src/api/learner-home.ts', '../../mobile-app/src/api/learner-home.ts']) {
    const api = await readFile(new URL(path, import.meta.url), 'utf8');
    const seen = [];
    globalThis.messageExamTestRequest = async options => { seen.push(options); return { code: 0, data: { list: [task], total: 1 } }; };
    const module = await import(compile(api.replace(/import \{ learnerTarget \} from [^;]+;/, 'const learnerTarget = () => "";').replace(/import \{ request \} from [^;]+;/, 'const request = (...args) => globalThis.messageExamTestRequest(...args);') + '\n// ' + path));
    try {
      assert.equal((await module.getMessageExamTasks(2)).total, 1);
      assert.deepEqual(seen, [{ url: '/api/exam/learner/tasks', method: 'GET', params: { pageNum: 2, pageSize: 200 } }]);
    } finally { delete globalThis.messageExamTestRequest; }
  }
});

test('两类独立练习消息保留原入口，未知类型和动作不能开放入口', async () => {
  for (const [examType, label] of [['SPECIAL_PRACTICE', '专项练习'], ['BANK_PRACTICE', '题库练习']]) {
    const tasks = await loadMessageExams([message], async () => ({ total: 1, list: [{ ...task, examType, availableAction: 'START' }] }));
    const info = messageExamInfo(message, tasks);
    assert.equal(info.typeLabel, label); assert.equal(info.canOpen, true);
  }
  for (const invalid of [{ examType: null }, { examType: 'UNKNOWN' }, { examType: 'ONLINE', availableAction: 'INVALID' }]) {
    assert.equal(messageExamInfo(message, { '2:3': { ...task, availableAction: 'START', ...invalid } }).canOpen, false);
  }
});
