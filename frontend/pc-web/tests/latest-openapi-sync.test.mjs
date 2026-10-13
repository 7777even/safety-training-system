import assert from 'node:assert/strict';
import fs from 'node:fs';
import test from 'node:test';

const read = (path) => fs.readFileSync(new URL(path, import.meta.url), 'utf8');

test('system user and person picker follow nullable department and current person fields', () => {
  const userApi = read('../src/api/system-user.ts');
  const pickerApi = read('../src/api/person-picker.ts');
  const pickerView = read('../src/components/organization/PersonPickerDialog.vue');

  assert.match(userApi, /deptId: number \| null/);
  assert.match(pickerApi, /jobTitle\?: string/);
  assert.doesNotMatch(pickerApi, /orgMemberId|employeeNo/);
  assert.doesNotMatch(pickerView, /employeeNo|orgMemberId/);
  assert.match(pickerView, /jobTitle/);
});

test('M2 API request types no longer expose tenant input fields', () => {
  const questionApi = read('../src/api/question.ts');
  const paperApi = read('../src/api/paper.ts');
  const examApi = read('../src/api/exam.ts');
  const evaluationApi = read('../src/api/evaluation-form.ts');

  const requestBlocks = [
    /interface KnowledgePointCreatePayload \{[\s\S]*?\}/,
    /interface QuestionBankCategoryCreatePayload \{[\s\S]*?\}/,
    /interface QuestionImportPayload \{[\s\S]*?\}/,
    /interface QuestionBankPageQuery \{[\s\S]*?\}/,
    /interface QuestionBankCreatePayload \{[\s\S]*?\}/,
    /interface QuestionPageQuery \{[\s\S]*?\}/,
    /interface QuestionCreatePayload \{[\s\S]*?\}/,
    /interface PaperPageQuery \{[\s\S]*?\}/,
    /interface PaperCreatePayload \{[\s\S]*?\}/,
    /interface ExamPageQuery \{[\s\S]*?\}/,
    /interface ExamCreatePayload[\s\S]*?\{[\s\S]*?\}/,
    /interface ExamCandidatePageQuery \{[\s\S]*?\}/,
    /interface EvaluationFormPageParams \{[\s\S]*?\}/,
    /interface EvaluationFormCreateRequest \{[\s\S]*?\}/
  ];
  for (const source of [questionApi, paperApi, examApi, evaluationApi]) {
    for (const block of requestBlocks) {
      const match = source.match(block);
      if (match) assert.doesNotMatch(match[0], /organizationId\s*:|query\.organizationId/);
    }
  }
  assert.doesNotMatch(questionApi, /params:\s*\{\s*organizationId\s*\}/);
});

test('original asset preview is explicit download-only', () => {
  const viewer = read('../src/components/asset-viewer/AssetViewer.vue');
  assert.match(viewer, /original/);
  assert.match(viewer, /下载/);
});
