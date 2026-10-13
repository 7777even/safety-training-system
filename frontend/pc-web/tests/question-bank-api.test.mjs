import assert from 'node:assert/strict';
import {readFile} from 'node:fs/promises';
import test from 'node:test';
import ts from 'typescript';

const apiUrl = new URL('../src/api/question.ts', import.meta.url);

/** 通过 API 模块这一公开边界验证 OpenAPI 请求映射，而不依赖页面实现。 */
async function loadQuestionApi(response = {code: 0, message: 'success', data: null}, questionMock = {}) {
  const source = await readFile(apiUrl, 'utf8');
  const requests = [];
  const transformed = source
    .replace("import * as questionMock from '@/mock/question';", 'const questionMock = globalThis.__questionMock;')
    .replace(
      /import\s*\{\s*request\s*}\s*from\s*'\.\/request';/,
      'const request = globalThis.__questionRequest;',
    );
  const output = ts.transpileModule(transformed, {
    compilerOptions: {module: ts.ModuleKind.ESNext, target: ts.ScriptTarget.ES2022}
  }).outputText;

  globalThis.__questionRequest = async (config) => {
    requests.push(config);
    return response;
  };
  globalThis.__questionMock = questionMock;
  globalThis.__QUESTION_MOCK_ENABLED__ = false;

  const api = await import(`data:text/javascript;base64,${Buffer.from(output).toString('base64')}#${Math.random()}`);
  return {api, requests};
}

test('题库分页按 OpenAPI 使用 pageNum、pageSize 与 query 前缀', async () => {
  const {api, requests} = await loadQuestionApi({
    code: 0,
    message: 'success',
    data: {list: [{questionBankId: 8, bankName: '安全基础'}], total: 1}
  });

  const page = await api.getQuestionBankPage({
    pageNum: 1,
    pageSize: 20,
    'query.organizationId': 1,
    'query.bankName': '安全基础'
  });

  assert.deepEqual(page, {list: [{questionBankId: 8, bankName: '安全基础'}], total: 1});
  assert.deepEqual(requests, [{
    url: '/api/question/banks',
    method: 'GET',
    params: {pageNum: 1, pageSize: 20, 'query.organizationId': 1, 'query.bankName': '安全基础'}
  }]);
});

test('题库 API 默认启用 Contract Mock，显式关闭后才请求真实接口', async () => {
  const {api} = await loadQuestionApi();

  delete globalThis.__QUESTION_MOCK_ENABLED__;
  assert.equal(api.isQuestionMockEnabled(), true);
  globalThis.__QUESTION_MOCK_ENABLED__ = false;
  assert.equal(api.isQuestionMockEnabled(), false);
  delete globalThis.__QUESTION_MOCK_ENABLED__;
});

test('题库 API 开启 Mock 时不发起真实请求，并解包 Contract 分页结果', async () => {
  const {api, requests} = await loadQuestionApi(undefined, {
    mockGetQuestionBankPage: async () => ({
      code: 0,
      message: 'success',
      data: {list: [{questionBankId: 7, bankName: 'Mock 题库'}], total: 1}
    })
  });
  globalThis.__QUESTION_MOCK_ENABLED__ = true;

  const page = await api.getQuestionBankPage({pageNum: 1, pageSize: 20});

  assert.deepEqual(page, {list: [{questionBankId: 7, bankName: 'Mock 题库'}], total: 1});
  assert.deepEqual(requests, []);
  delete globalThis.__QUESTION_MOCK_ENABLED__;
});

test('题目创建与发布使用独立 Contract 地址，并向调用方暴露业务错误', async () => {
  const created = await loadQuestionApi({code: 0, message: 'success', data: 52});
  const questionId = await created.api.createQuestion({
    organizationId: 1,
    questionBankId: 8,
    questionCode: 'Q-001',
    questionType: 'SINGLE',
    difficulty: 'EASY',
    stem: '安全生产的首要责任主体是？',
    gradingMode: 'AUTO',
    options: [{optionKey: 'A', optionContent: '生产经营单位', isCorrect: 1, sortOrder: 1}]
  });

  assert.equal(questionId, 52);
  assert.deepEqual(created.requests, [{
    url: '/api/question/questions',
    method: 'POST',
    data: {
      organizationId: 1,
      questionBankId: 8,
      questionCode: 'Q-001',
      questionType: 'SINGLE',
      difficulty: 'EASY',
      stem: '安全生产的首要责任主体是？',
      gradingMode: 'AUTO',
      options: [{optionKey: 'A', optionContent: '生产经营单位', isCorrect: 1, sortOrder: 1}]
    }
  }]);

  const rejected = await loadQuestionApi({code: 6009, message: '题目版本冲突', data: null});
  await assert.rejects(() => rejected.api.publishQuestion(52), /题目版本冲突/);
  assert.deepEqual(rejected.requests, [{url: '/api/question/questions/52/publish', method: 'POST'}]);
});

test('知识点、分类、题目编辑、素材与导入均使用各自的 Contract 边界', async () => {
  const {api, requests} = await loadQuestionApi({code: 0, message: 'success', data: null});

  await api.getKnowledgePointTree(1);
  await api.getQuestionBankCategories(1);
  await api.createQuestionBankCategory({
    organizationId: 1,
    categoryName: '通用安全',
    description: '通用题库分类',
    sortOrder: 1,
    status: 1
  });
  await api.updateQuestion(52, {
    questionType: 'SINGLE',
    stem: '新题干',
    difficulty: 'EASY',
    gradingMode: 'AUTO',
    version: 0,
    options: [{optionKey: 'A', optionContent: '对', isCorrect: 1, sortOrder: 1}]
  });
  await api.replaceQuestionAssets(52, [{assetId: 9, assetRole: 'STEM', optionKey: '', sortOrder: 1}]);
  const importResult = await api.importQuestions(8, {
    organizationId: 1,
    sourceFileId: 0,
    rows: [{questionCode: 'Q2', questionType: 'JUDGE', stem: '安全第一', difficulty: 'EASY', options: []}]
  });

  assert.deepEqual(importResult, null);

  assert.deepEqual(requests, [
    {url: '/api/question/knowledge-points/tree', method: 'GET', params: {organizationId: 1}},
    {url: '/api/question/bank-categories', method: 'GET', params: {organizationId: 1}},
    {
      url: '/api/question/bank-categories',
      method: 'POST',
      data: {organizationId: 1, categoryName: '通用安全', description: '通用题库分类', sortOrder: 1, status: 1}
    },
    {
      url: '/api/question/questions/52',
      method: 'PUT',
      data: {
        questionType: 'SINGLE',
        stem: '新题干',
        difficulty: 'EASY',
        gradingMode: 'AUTO',
        version: 0,
        options: [{optionKey: 'A', optionContent: '对', isCorrect: 1, sortOrder: 1}]
      }
    },
    {
      url: '/api/question/questions/52/assets',
      method: 'PUT',
      data: [{assetId: 9, assetRole: 'STEM', optionKey: '', sortOrder: 1}]
    },
    {
      url: '/api/question/banks/8/import',
      method: 'POST',
      data: {
        organizationId: 1,
        sourceFileId: 0,
        rows: [{questionCode: 'Q2', questionType: 'JUDGE', stem: '安全第一', difficulty: 'EASY', options: []}]
      }
    }
  ]);
});

test('组卷选择使用发布状态服务端筛选，题目编辑从版本素材回显', async () => {
  const [apiSource, pickerSource, detailSource] = await Promise.all([
    readFile(apiUrl, 'utf8'),
    readFile(new URL('../src/views/admin/business-center/exams/paper/components/QuestionBankPickerDialog.vue', import.meta.url), 'utf8'),
    readFile(new URL('../src/views/admin/business-center/exams/question/detail/index.vue', import.meta.url), 'utf8')
  ]);

  assert.match(apiSource, /'query\.publishedStatus'\?:/);
  assert.match(apiSource, /assets\?: QuestionAsset/);
  assert.match(pickerSource, /'query\.publishedStatus': 'PUBLISHED'/);
  assert.doesNotMatch(pickerSource, /\.filter\(\(item\) => item\.currentVersion\?\.publishedStatus/);
  assert.match(detailSource, /version\.assets/);
});
