import assert from 'node:assert/strict';
import { readFile } from 'node:fs/promises';
import test from 'node:test';
import ts from 'typescript';

const stateUrl = new URL('../src/views/admin/business-center/exams/exam/components/results/score-disposition-state.ts', import.meta.url);
const pageUrl = new URL('../src/views/admin/business-center/exams/exam/components/results/index.vue', import.meta.url);

/** 将成绩处置的纯状态转译为 Node 可执行模块。 */
async function loadTypeScriptModule(url) {
  const source = await readFile(url, 'utf8');
  const output = ts.transpileModule(source, {
    compilerOptions: { module: ts.ModuleKind.ESNext, target: ts.ScriptTarget.ES2022 }
  }).outputText;
  return import(`data:text/javascript;base64,${Buffer.from(output).toString('base64')}#${Math.random()}`);
}

test('成绩处置按题目快照展示考生答案与三类得分，并阻断待批阅成绩发布', async () => {
  const state = await loadTypeScriptModule(stateUrl);
  const questions = [
    {
      sortOrder: 1,
      questionType: 'SINGLE',
      score: 10,
      gradingMode: 'AUTO',
      answer: { answerText: 'A', autoScore: 10, manualScore: null, finalScore: 10, gradingStatus: 'AUTO_GRADED' }
    },
    {
      sortOrder: 2,
      questionType: 'SHORT_ANSWER',
      score: 20,
      gradingMode: 'MANUAL',
      answer: { answerText: '立即报告现场情况\n设置警戒区域并组织疏散\n按流程整改后复查。', autoScore: null, manualScore: null, finalScore: null, gradingStatus: 'PENDING_MANUAL' }
    }
  ];

  const rows = state.toScoreDispositionQuestionRows(questions);

  assert.deepEqual(rows[0], {
    questionNo: 1,
    questionType: 'SINGLE',
    answerText: 'A',
    autoScore: 10,
    manualScore: null,
    finalScore: 10,
    score: 10,
    gradingStatus: 'AUTO_GRADED'
  });
  assert.equal(rows[1].answerText, '立即报告现场情况\n设置警戒区域并组织疏散\n按流程整改后复查。');
  assert.equal(state.hasPendingManualReview(questions), true);
  assert.equal(state.canPublishScore({ scorePublishStatus: 'UNPUBLISHED', questions }), false);
  assert.equal(state.canPublishScore({ scorePublishStatus: 'EFFECTIVE', questions: [] }), false);
});

test('开补考仅在服务端资格通过且当前用户有权限时显示', async () => {
  const state = await loadTypeScriptModule(stateUrl);

  assert.equal(state.shouldShowRetakeAction({ eligible: true, reasons: [] }, true), true);
  assert.equal(state.shouldShowRetakeAction({ eligible: false, reasons: ['已超过补考次数'] }, true), false);
  assert.equal(state.shouldShowRetakeAction({ eligible: true, reasons: [] }, false), false);
  assert.equal(state.getRetakeEligibilityHint({ eligible: false, reasons: ['不在补考开放时间内'] }), '不在补考开放时间内');
});

test('成绩处置页将逐题核对与发布操作放入可滚动的右侧抽屉', async () => {
  const page = await readFile(pageUrl, 'utf8');

  assert.match(page, /toScoreDispositionQuestionRows/);
  assert.match(page, /hasPendingManualReview/);
  assert.match(page, /shouldShowRetakeAction/);
  assert.match(page, /retakeEligibility/);
  assert.match(page, /逐题作答与得分核对/);
  assert.match(page, /查看作答与得分/);
  assert.match(page, /<ElDrawer v-model="detailDrawerVisible"/);
  assert.match(page, /class="drawer-table"/);
  assert.match(page, /height="100%"/);
  assert.match(page, /class="drawer-actions"/);
  assert.match(page, /QUESTION_TYPE_OPTIONS/);
  assert.match(page, /predictedIsPassed/);
  assert.match(page, /white-space: pre-wrap/);
  assert.doesNotMatch(page, /show-overflow-tooltip/);
  assert.match(page, /{{ attempt\.finalScore \?\? '-' }}/);
});
