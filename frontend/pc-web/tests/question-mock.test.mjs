import assert from 'node:assert/strict';
import {readFile} from 'node:fs/promises';
import test from 'node:test';
import ts from 'typescript';

const mockUrl = new URL('../src/mock/question.ts', import.meta.url);

/** 将仅含类型导入的 Contract Mock 编译为可执行模块，验证浏览器会话数据行为。 */
async function loadQuestionMock() {
  const source = await readFile(mockUrl, 'utf8');
  const output = ts.transpileModule(source, {
    compilerOptions: {module: ts.ModuleKind.ESNext, target: ts.ScriptTarget.ES2022}
  }).outputText;
  return import(`data:text/javascript;base64,${Buffer.from(output).toString('base64')}#${Math.random()}`);
}

test('已发布题目版本在 Mock 中不可重新编辑为草稿', async () => {
  const questionMock = await loadQuestionMock();
  questionMock.resetQuestionMock();

  const result = await questionMock.mockUpdateQuestion(1, {
    questionType: 'SINGLE',
    stem: '不应覆盖发布版本',
    difficulty: 'EASY',
    gradingMode: 'AUTO',
    version: 0,
    options: []
  });

  assert.equal(result.code, 6009);
  assert.match(result.message, /不可编辑/);
});

test('Mock 导入将重复编码行计为失败并返回准确统计', async () => {
  const questionMock = await loadQuestionMock();
  questionMock.resetQuestionMock();

  const result = await questionMock.mockImportQuestions(1, {
    organizationId: 1,
    sourceFileId: 9,
    rows: [
      {questionCode: 'Q-SAFE-001', questionType: 'SINGLE', stem: '重复题目', difficulty: 'EASY', options: []},
      {questionCode: 'Q-SAFE-002', questionType: 'JUDGE', stem: '可导入题目', difficulty: 'EASY', options: []}
    ]
  });

  assert.deepEqual(result.data, {
    importBatchId: 1,
    importStatus: 'PARTIAL_SUCCESS',
    totalRows: 2,
    successRows: 1,
    failureRows: 1
  });
});

test('题目分页按发布状态服务端筛选，且素材替换后可从详情回显', async () => {
  const questionMock = await loadQuestionMock();
  questionMock.resetQuestionMock();

  const created = await questionMock.mockCreateQuestion({
    organizationId: 1,
    questionBankId: 1,
    questionCode: 'Q-DRAFT-001',
    questionType: 'SINGLE',
    stem: '草稿题目',
    difficulty: 'EASY',
    gradingMode: 'AUTO',
    options: []
  });
  await questionMock.mockReplaceQuestionAssets(created.data, [
    {assetId: 9, assetRole: 'STEM', optionKey: '', sortOrder: 1}
  ]);

  const publishedPage = await questionMock.mockGetQuestionPage({
    pageNum: 1,
    pageSize: 20,
    'query.publishedStatus': 'PUBLISHED'
  });
  const detail = await questionMock.mockGetQuestionDetail(created.data);

  assert.equal(publishedPage.data.total, 1);
  assert.deepEqual(detail.data.currentVersion.assets, [{
    questionAssetId: 1,
    questionVersionId: created.data,
    assetId: 9,
    assetRole: 'STEM',
    optionKey: '',
    sortOrder: 1
  }]);
});
