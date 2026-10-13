import test from 'node:test';
import assert from 'node:assert/strict';
import { readFile } from 'node:fs/promises';
import ts from 'typescript';
import { ref, watch, computed, reactive, nextTick } from 'vue';

async function load(file) {
  const source = await readFile(new URL(file, import.meta.url), 'utf8');
  return import(`data:text/javascript;base64,${Buffer.from(ts.transpileModule(source, {
    compilerOptions: { module: ts.ModuleKind.ESNext, target: ts.ScriptTarget.ES2022 }
  }).outputText).toString('base64')}`);
}

test('消息跳转保留课程报名、考试人员、培训参训人各自引用', async () => {
  const { learnerTarget } = await load('../../shared/src/utils/learner-home.ts');
  assert.equal(learnerTarget({ kind: 'COURSE', resourceId: 31 }, 'PC'), '/portal/class/learning/31');
  assert.equal(learnerTarget({ kind: 'FORMAL_EXAM', resourceId: 7, parentId: 2 }, 'H5'), '/pages/exam/notice?examId=2&examCandidateId=7');
  assert.equal(learnerTarget({ kind: 'FORMAL_EXAM', resourceId: 7, parentId: 2, attemptId: 8, action: 'CONTINUE' }, 'PC'), '/portal/exams/2/attempt/8');
  assert.equal(learnerTarget({ kind: 'FORMAL_EXAM', resourceId: 7, parentId: 2, attemptId: 8, action: 'CONTINUE' }, 'H5'), '/pages/exam/attempt?examId=2&examAttemptId=8');
  assert.equal(learnerTarget({ kind: 'TRAINING', resourceId: 8 }, 'PC'), '/portal/training/8');
  assert.equal(learnerTarget({ kind: 'COURSE_CATALOG', resourceId: 9 }, 'PC'), '/user-center/courses?courseId=9');
  assert.throws(() => learnerTarget({ kind: 'https://evil.invalid', resourceId: 1 }, 'PC'), /不可用/);
});

test('SSE分片及CRLF不能丢掉实时消息事件，退出清理会中止连接', async () => {
  const { subscribeLearnerMessages } = await load('../../shared/src/libs/learner-messages.ts');
  let request; let refreshed = 0;
  const original = globalThis.fetch;
  let streamController;
  globalThis.fetch = async (url, options) => {
    request = { url, ...options };
    return new Response(new ReadableStream({ start(c) { streamController = c; } }), {
      headers: { 'Content-Type': 'text/event-stream' }
    });
  };
  try {
    const stop = subscribeLearnerMessages('/api/learner/messages/stream', 'token', () => { refreshed++; }, () => {});
    await new Promise(resolve => setTimeout(resolve, 0));
    const encoder = new TextEncoder();
    for (const part of ['event: mess', 'ages\r', '\ndata: {"total":2}\r\n', '\r\n: heartbeat\n\n']) streamController.enqueue(encoder.encode(part));
    await new Promise(resolve => setTimeout(resolve, 0));
    assert.equal(refreshed, 1);
    assert.equal(request.headers.Authorization, 'Bearer token');
    assert.equal(request.url, '/api/learner/messages/stream');
    stop();
    assert.equal(request.signal.aborted, true);
    streamController.close();
  } finally { globalThis.fetch = original; }
});

for (const file of ['../src/views/user-center/messages/index.vue', '../../mobile-app/src/pages/messages/index.vue',
  '../src/views/portal/home/index.vue', '../../mobile-app/src/pages/index/index.vue']) {
  test(`${file}: 在线事件发生在慢查询期间仍补读最新数据`, async () => {
    const source = await readFile(new URL(file, import.meta.url), 'utf8');
    const script = source.match(/<script setup lang="ts">([\s\S]*?)<\/script>/)[1].replace(/^import .*;\r?\n/gm, '');
    let mounted, resolveFirst, calls = 0;
    const messageStore = reactive({ revision: 0, total: 0 });
    const refs = [];
    const query = () => {
      calls++;
      return calls === 1 ? new Promise(resolve => { resolveFirst = resolve; })
        : Promise.resolve({ list: [{ title: '新任务' }], total: 1 });
    };
    const hooks = [];
    const names = ['ref', 'watch', 'computed', 'onMounted', 'onShow', 'useRouter', 'useUserStore',
      'useLearnerMessageStore', 'useLearnerHomeStore', 'getLearnerMessages', 'getLearnerHome', 'DEFAULT_LIST_PAGE_SIZE', 'LEARNER_LIST_PAGE_SIZE'];
    const values = [value => { const r = ref(value); refs.push(r); return r; }, (...args) => { hooks.push(watch(...args)); }, computed,
      fn => { mounted = fn; }, fn => { mounted = fn; }, () => ({}), () => ({ token: 'test', userInfo: {} }),
      () => messageStore, () => ({ reusableBy: () => false, snapshot: () => {} }), query, query, 20, 20];
    new Function(...names, ts.transpileModule(script, { compilerOptions: { target: ts.ScriptTarget.ES2022, module: ts.ModuleKind.None } }).outputText)(...values);
    try {
      const initial = mounted();
      messageStore.revision++;
      await nextTick();
      resolveFirst({ list: [], total: 0 });
      await initial; await nextTick(); await new Promise(resolve => setTimeout(resolve, 0));
      assert.equal(calls, 2);
      assert.ok(refs.some(r => Array.isArray(r.value) && r.value[0]?.title === '新任务'));
    } finally { hooks.forEach(stop => stop()); }
  });
}

test('学习首页命中会话快照时原样还原筛选与列表且不再请求接口', async () => {
  const source = await readFile(new URL('../src/views/portal/home/index.vue', import.meta.url), 'utf8');
  const script = source.match(/<script setup lang="ts">([\s\S]*?)<\/script>/)[1].replace(/^import .*;\r?\n/gm, '');
  let mounted; let calls = 0;
  const refs = []; const hooks = [];
  const snapshotRows = [{ title: '快照任务' }];
  const names = ['ref', 'watch', 'computed', 'onMounted', 'onShow', 'useRouter', 'useUserStore',
    'useLearnerMessageStore', 'useLearnerHomeStore', 'getLearnerMessages', 'getLearnerHome', 'DEFAULT_LIST_PAGE_SIZE', 'LEARNER_LIST_PAGE_SIZE'];
  const values = [value => { const r = ref(value); refs.push(r); return r; }, (...args) => { hooks.push(watch(...args)); }, computed,
    fn => { mounted = fn; }, fn => { mounted = fn; }, () => ({}), () => ({ token: 'test', userInfo: { userCode: 'admin' } }),
    () => reactive({ revision: 0, total: 0 }),
    () => ({ reusableBy: () => true, kind: 'TRAINING', page: 2, rows: snapshotRows, total: 4, snapshot: () => {} }),
    () => { calls++; return Promise.resolve({ list: [], total: 0 }); },
    () => { calls++; return Promise.resolve({ list: [], total: 0 }); },
    20, 20];
  new Function(...names, ts.transpileModule(script, { compilerOptions: { target: ts.ScriptTarget.ES2022, module: ts.ModuleKind.None } }).outputText)(...values);
  try {
    await mounted();
    await nextTick();
    assert.equal(calls, 0, '命中快照应直接用缓存，不再请求列表接口');
    assert.ok(refs.some(r => Array.isArray(r.value) && r.value[0]?.title === '快照任务'), '快照列表应原样铺回');
    assert.ok(refs.some(r => r.value === 'TRAINING'), '页签应还原为快照筛选');
    assert.ok(refs.some(r => r.value === 2), '页码应还原为快照页码');
    assert.ok(refs.some(r => r.value === 4), '总数应还原为快照总数');
  } finally { hooks.forEach(stop => stop()); }
});
