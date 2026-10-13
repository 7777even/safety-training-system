import assert from 'node:assert/strict';
import { readFile } from 'node:fs/promises';
import test from 'node:test';
import ts from 'typescript';

const optionsUrl = new URL('../src/constants/exam-options.ts', import.meta.url);
const definitionFormUrl = new URL('../src/views/admin/business-center/exams/exam/components/ExamDefinitionForm.vue', import.meta.url);
const workspaceUrl = new URL('../src/views/admin/business-center/exams/exam/workspace/index.vue', import.meta.url);
const attemptPanelUrl = new URL('../src/views/admin/business-center/exams/exam/components/ExamAttemptManagementPanel.vue', import.meta.url);
const resultListUrl = new URL('../src/views/admin/business-center/exams/results/index.vue', import.meta.url);
const resultPageUrl = new URL('../src/views/admin/business-center/exams/exam/results/index.vue', import.meta.url);

/** 加载考试字典，以契约中的已知枚举验证用户可见文案。 */
async function loadOptions() {
  const source = await readFile(optionsUrl, 'utf8');
  const output = ts.transpileModule(source, {
    compilerOptions: { module: ts.ModuleKind.ESNext, target: ts.ScriptTarget.ES2022 }
  }).outputText;
  return import(`data:text/javascript;base64,${Buffer.from(output).toString('base64')}#${Math.random()}`);
}

test('补考策略提供三种范围，新增考试默认以不及格人员为补考范围', async () => {
  const [options, definitionForm, workspace] = await Promise.all([
    loadOptions(),
    readFile(definitionFormUrl, 'utf8'),
    readFile(workspaceUrl, 'utf8')
  ]);

  assert.deepEqual(
    options.RETAKE_SCOPE_OPTIONS.map((item) => item.value),
    ['FAILED', 'FAILED_OR_ABSENT', 'ABSENT']
  );
  assert.match(definitionForm, /retakeScope/);
  assert.match(workspace, /retakeScope: 'FAILED'/);
});

test('成绩处置携带考生标识并使用既有补考接口的幂等令牌', async () => {
  const [attemptPanel, resultList, resultPage] = await Promise.all([
    readFile(attemptPanelUrl, 'utf8'),
    readFile(resultListUrl, 'utf8'),
    readFile(resultPageUrl, 'utf8')
  ]);

  for (const source of [attemptPanel, resultList]) {
    assert.match(source, /examCandidateId: row\.examCandidateId/);
  }
  assert.match(resultPage, /route\.query\.examCandidateId/);
  assert.match(resultPage, /attempt\.value\?\.examCandidateId/);
  assert.match(resultPage, /createRetakeAttempt/);
  assert.match(resultPage, /retakeToken/);
  assert.match(resultPage, /exam:attempt:start/);
  assert.match(resultPage, /ExamGradingApiError/);
  assert.match(resultPage, /\$\{error\.code\}：\$\{error\.message\}/);
  assert.doesNotMatch(resultPage, /openStartTime.*createRetakeAttempt/s);
  assert.doesNotMatch(resultPage, /durationSeconds.*createRetakeAttempt/s);
});

test('成绩处置将作答状态、发布方式和发布结果映射为中文', async () => {
  const [options, resultPage] = await Promise.all([loadOptions(), readFile(resultPageUrl, 'utf8')]);

  assert.equal(options.getExamEnumLabel('PUBLISHED', options.ATTEMPT_STATUS_OPTIONS), '已发布');
  assert.equal(options.getExamEnumLabel('SINGLE', options.SCORE_PUBLISH_TYPE_OPTIONS), '单次发布');
  assert.equal(options.getExamEnumLabel('FAILED', options.SCORE_RESULT_OPTIONS), '未通过');
  assert.match(resultPage, /SCORE_RESULT_OPTIONS/);
  assert.match(resultPage, /getExamEnumLabel\(row\.resultSnapshot, SCORE_RESULT_OPTIONS/);
});
