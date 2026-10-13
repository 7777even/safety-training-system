import assert from 'node:assert/strict';
import {readFile} from 'node:fs/promises';
import test from 'node:test';

const apiRoot = new URL('../src/api/', import.meta.url);
const taskFile = new URL('../../../openspec/changes/2026-08-18-refine-exam-workflow-entries/tasks.md', import.meta.url);

/** 考试 OpenAPI 每个 operation 在 PC Web 的既有调用封装。 */
const OPERATION_CALLERS = [
  ['exam.ts', 'getExamPage'], ['exam.ts', 'createExam'], ['exam.ts', 'getExam'], ['exam.ts', 'updateExam'], ['exam.ts', 'deleteExam'],
  ['exam.ts', 'publishExam'], ['exam.ts', 'bindExamSelection'], ['exam.ts', 'getExamCandidates'], ['exam.ts', 'replaceExamReviewers'], ['exam.ts', 'getExamReviewers'],
  ['exam-grading.ts', 'getExamReviewTaskPage'], ['exam-grading.ts', 'getExamAttemptRecordPage'], ['learner-exam.ts', 'startExamAttempt'],
  ['exam-grading.ts', 'getReviewAttempt'], ['learner-exam.ts', 'saveExamAnswers'], ['learner-exam.ts', 'submitExamAttempt'], ['learner-exam.ts', 'timeoutSubmitExamAttempt'],
  ['exam-grading.ts', 'reviewExamAnswer'], ['exam-grading.ts', 'publishExamScore'], ['exam-grading.ts', 'createRetakeAttempt'], ['exam-grading.ts', 'resetPractice'],
  ['learner-exam.ts', 'getLearnerExamTaskPage'], ['learner-exam.ts', 'getLearnerExamAttempt'], ['learner-exam.ts', 'recordExamAnticheatEvent'],
  ['exam-grading.ts', 'getExamAnticheatEvents'], ['exam-grading.ts', 'getExamScorePublishLogs']
];

test('考试 OpenAPI 的全部 operation 均有 PC Web 调用封装和审计记录', async () => {
  const sources = await Promise.all(
    ['exam.ts', 'exam-grading.ts', 'learner-exam.ts'].map(async (fileName) => [fileName, await readFile(new URL(fileName, apiRoot), 'utf8')])
  );
  const apiSources = new Map(sources);

  for (const [fileName, caller] of OPERATION_CALLERS) {
    assert.match(apiSources.get(fileName), new RegExp(`export const ${caller}\\s*=`), `${caller} 缺少调用封装`);
  }

  const tasks = await readFile(taskFile, 'utf8');
  assert.match(tasks, /覆盖审计记录/, 'Task 4 必须记录 operation 覆盖结论');
});
