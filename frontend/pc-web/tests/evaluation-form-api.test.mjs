import assert from 'node:assert/strict';
import { readFile } from 'node:fs/promises';
import test from 'node:test';
import ts from 'typescript';

const apiFile = new URL('../src/api/evaluation-form.ts', import.meta.url);
const mockFile = new URL('../src/mock/evaluation-form.ts', import.meta.url);

/** 编译不含运行时依赖的 TypeScript Mock，通过公开导出验证契约行为。 */
async function loadMockModule() {
  const source = await readFile(mockFile, 'utf8');
  const output = ts.transpileModule(source, {
    compilerOptions: { module: ts.ModuleKind.ESNext, target: ts.ScriptTarget.ES2022 }
  }).outputText;
  return import(`data:text/javascript;base64,${Buffer.from(output).toString('base64')}#${Date.now()}-${Math.random()}`);
}

async function expectBusinessError(action, expectedCode) {
  await assert.rejects(action, (error) => error?.response?.data?.code === expectedCode);
}

test('评价表单 Mock 支持分页筛选、CRUD 与乐观锁', async () => {
  const mock = await loadMockModule();
  mock.resetEvaluationFormMockState();

  const evaluationFormId = await mock.mockCreateEvaluationForm({
    organizationId: 1,
    formCode: 'FORM-SPECIAL-001',
    formName: '特种作业综合评价表',
    formType: 'COMPOSITE',
    status: 1,
    description: '用于特种作业培训综合评价。'
  });

  const page = await mock.mockGetEvaluationFormPage({
    pageNum: 1,
    pageSize: 10,
    'query.organizationId': 1,
    'query.formName': '特种作业',
    'query.formType': 'COMPOSITE',
    'query.status': 1
  });
  assert.equal(page.total, 1);
  assert.equal(page.list[0].evaluationFormId, evaluationFormId);

  const detail = await mock.mockGetEvaluationFormDetail(evaluationFormId);
  await expectBusinessError(
    () => mock.mockUpdateEvaluationForm(evaluationFormId, { ...detail, formName: '过期修改', version: 99 }),
    8504
  );

  await mock.mockUpdateEvaluationForm(evaluationFormId, {
    version: detail.version,
    formName: '特种作业人员综合评价表',
    formType: detail.formType,
    status: detail.status,
    description: detail.description
  });
  assert.equal((await mock.mockGetEvaluationFormDetail(evaluationFormId)).version, detail.version + 1);

  await mock.mockDeleteEvaluationForm(evaluationFormId);
  await expectBusinessError(() => mock.mockGetEvaluationFormDetail(evaluationFormId), 8501);
});

test('评价表单 Mock 发布不可变版本并按项目编码绑定评分规则', async () => {
  const mock = await loadMockModule();
  mock.resetEvaluationFormMockState();
  const form = await mock.mockGetEvaluationFormDetail(1);

  await expectBusinessError(
    () =>
      mock.mockPublishEvaluationForm(1, {
        version: 99,
        totalScore: 100,
        items: [{ itemCode: 'THEORY', itemName: '理论成绩', itemType: 'SCORE', sortOrder: 1, isRequired: 1 }]
      }),
    8504
  );

  const published = await mock.mockPublishEvaluationForm(1, {
    version: form.version,
    totalScore: 100,
    versionNote: '首次正式发布',
    items: [
      { itemCode: 'THEORY', itemName: '理论成绩', itemType: 'SCORE', maxScore: 100, weight: 0.6, sortOrder: 1, isRequired: 1 },
      { itemCode: 'PRACTICE', itemName: '实操成绩', itemType: 'SCORE', maxScore: 100, weight: 0.4, sortOrder: 2, isRequired: 1 }
    ],
    scoreRules: [
      {
        evaluationItemCode: 'THEORY',
        calculationType: 'WEIGHT',
        sourceType: 'EXAM',
        weight: 0.6,
        sortOrder: 1,
        ruleDescription: '考试成绩按 60% 折算。'
      }
    ]
  });

  assert.equal(published.publishedStatus, 'PUBLISHED');
  assert.equal(published.versionNo, 1);
  assert.equal(published.scoreRules[0].evaluationItemId, published.items[0].evaluationItemId);
  assert.equal('evaluationItemCode' in published.scoreRules[0], false);

  const versions = await mock.mockGetEvaluationFormVersions(1);
  assert.deepEqual(versions.map((item) => item.versionNo), [1]);
  const snapshot = await mock.mockGetEvaluationFormVersionDetail(published.formVersionId);
  assert.equal(snapshot.items[0].itemName, '理论成绩');

  const updatedForm = await mock.mockGetEvaluationFormDetail(1);
  await mock.mockUpdateEvaluationForm(1, {
    version: updatedForm.version,
    formName: '已改名的表单主体',
    formType: updatedForm.formType,
    status: updatedForm.status,
    description: updatedForm.description
  });
  assert.equal((await mock.mockGetEvaluationFormVersionDetail(published.formVersionId)).items[0].itemName, '理论成绩');
  await expectBusinessError(() => mock.mockDeleteEvaluationForm(1), 8507);
});

test('评价表单 API 覆盖 CRUD、发布、版本历史与真实请求', async () => {
  const source = await readFile(apiFile, 'utf8');

  for (const exportName of [
    'getEvaluationFormPage',
    'getEvaluationFormDetail',
    'createEvaluationForm',
    'updateEvaluationForm',
    'deleteEvaluationForm',
    'publishEvaluationForm',
    'getEvaluationFormVersions',
    'getEvaluationFormVersionDetail'
  ]) {
    assert.match(source, new RegExp(`export async function ${exportName}`));
  }
  assert.doesNotMatch(source, /VITE_USE_API_MOCK|__EVALUATION_FORM_CONTRACT_MOCK_ENABLED__/);
  assert.match(source, /\/api\/evaluation\/forms/);
  assert.match(source, /\/publish/);
  assert.match(source, /\/versions/);
  assert.match(source, /request<ApiResponse<EvaluationFormPage>>/);
  assert.match(source, /request<ApiResponse<EvaluationFormVersionDetail>>/);
});
