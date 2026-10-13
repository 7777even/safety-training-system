import assert from 'node:assert/strict';
import { readFile } from 'node:fs/promises';
import test from 'node:test';
import ts from 'typescript';

const apiUrl = new URL('../src/api/training-template.ts', import.meta.url);

/** 以请求配置为公开边界加载培训模板 API，并强制切换到真实请求分支。 */
async function loadTrainingTemplateApi(
  response = { code: 0, message: 'success', data: null }
) {
  const source = await readFile(apiUrl, 'utf8');
  const requests = [];
  const output = ts.transpileModule(
    source
      .replace(
        /import\s*\{\s*request\s*}\s*from\s*'\.\/request';/,
        'const request = globalThis.__trainingTemplateRequest;'
      ),
    { compilerOptions: { module: ts.ModuleKind.ESNext, target: ts.ScriptTarget.ES2022 } }
  ).outputText;
  globalThis.__trainingTemplateRequest = async (config) => {
    requests.push(config);
    return response;
  };
  return {
    api: await import(`data:text/javascript;base64,${Buffer.from(output).toString('base64')}#${Math.random()}`),
    requests
  };
}

test('模板分类分页、详情、CRUD 与树查询遵循 Training OpenAPI', async () => {
  const { api, requests } = await loadTrainingTemplateApi({ code: 0, message: 'success', data: 7 });
  const pageQuery = {
    pageNum: 1,
    pageSize: 20,
    'query.parentId': 0,
    'query.categoryName': '岗位',
    'query.status': 1
  };
  const createPayload = {
    parentId: 0,
    categoryName: '岗位培训',
    sortOrder: 1,
    status: 1
  };
  const updatePayload = {
    version: 0,
    parentId: 0,
    categoryName: '岗位培训（修订）',
    sortOrder: 2,
    status: 1
  };

  await api.getTrainingTemplateCategoryPage(pageQuery);
  await api.createTrainingTemplateCategory(createPayload);
  await api.getTrainingTemplateCategory(7);
  await api.updateTrainingTemplateCategory(7, updatePayload);
  await api.deleteTrainingTemplateCategory(7);
  await api.getTrainingTemplateCategoryTree({ status: 1 });

  assert.deepEqual(requests, [
    { url: '/api/training/template-categories', method: 'GET', params: pageQuery },
    { url: '/api/training/template-categories', method: 'POST', data: createPayload },
    { url: '/api/training/template-categories/7', method: 'GET' },
    { url: '/api/training/template-categories/7', method: 'PUT', data: updatePayload },
    { url: '/api/training/template-categories/7', method: 'DELETE' },
    {
      url: '/api/training/template-categories/tree',
      method: 'GET',
      params: { status: 1 }
    }
  ]);
});

test('培训模板 CRUD、阶段替换和发布遵循 Training OpenAPI', async () => {
  const { api, requests } = await loadTrainingTemplateApi({ code: 0, message: 'success', data: 9 });
  const pageQuery = {
    pageNum: 1,
    pageSize: 20,
    'query.templateName': '岗位',
    'query.templateCategoryId': 2,
    'query.trainingTypeId': 3,
    'query.publishStatus': 'DRAFT',
    'query.status': 1
  };
  const createPayload = {
    templateCode: 'TPL-001',
    templateName: '岗位安全培训模板',
    templateCategoryId: 2,
    trainingTypeId: 3,
    trainingStandardId: 4,
    description: '用于高风险岗位的安全培训。',
    status: 1
  };
  const updatePayload = {
    version: 0,
    templateName: '岗位安全培训模板（修订）',
    templateCategoryId: 2,
    trainingTypeId: 3,
    trainingStandardId: 4,
    description: '调整阶段内容。',
    status: 1
  };
  const stagesPayload = {
    version: 1,
    stages: [
      {
        stageName: '线上学习',
        stageType: 'ONLINE',
        requiredHours: 2,
        sortOrder: 1,
        status: 1,
        contents: [
          {
            contentType: 'COURSE',
            resourceId: 11,
            resourceVersionId: 101,
            contentName: '岗位风险识别课程',
            isRequired: 1,
            requiredHours: 2,
            sortOrder: 1,
            status: 1
          },
          {
            contentType: 'EXAM',
            resourceId: 12,
            resourceVersionId: 202,
            assessmentMode: 'FORMAL_EXAM',
            contentName: '岗位安全考试',
            isRequired: 1,
            requiredHours: 1,
            sortOrder: 2,
            status: 1
          }
        ]
      }
    ]
  };
  // 保留独立快照，避免调用实现原地改写入参时掩盖资源版本字段丢失。
  const expectedStagesPayload = structuredClone(stagesPayload);

  await api.getTrainingTemplatePage(pageQuery);
  await api.createTrainingTemplate(createPayload);
  await api.getTrainingTemplate(9);
  await api.updateTrainingTemplate(9, updatePayload);
  await api.replaceTrainingTemplateStages(9, stagesPayload);
  await api.publishTrainingTemplate(9, { version: 2 });
  await api.deleteTrainingTemplate(9);

  const submittedContents = requests[4].data.stages[0].contents;
  assert.deepEqual(
    submittedContents.map(({ contentType, resourceVersionId }) => ({ contentType, resourceVersionId })),
    [
      { contentType: 'COURSE', resourceVersionId: 101 },
      { contentType: 'EXAM', resourceVersionId: 202 }
    ]
  );

  assert.deepEqual(requests, [
    { url: '/api/training/templates', method: 'GET', params: pageQuery },
    { url: '/api/training/templates', method: 'POST', data: createPayload },
    { url: '/api/training/templates/9', method: 'GET' },
    { url: '/api/training/templates/9', method: 'PUT', data: updatePayload },
    { url: '/api/training/templates/9/stages', method: 'PUT', data: expectedStagesPayload },
    { url: '/api/training/templates/9/publish', method: 'POST', data: { version: 2 } },
    { url: '/api/training/templates/9', method: 'DELETE' }
  ]);
});

test('培训模板 API 不保留生产 Mock 与机构输入参数', async () => {
  const source = await readFile(apiUrl, 'utf8');
  assert.doesNotMatch(source, /trainingTemplateMock|__TRAINING_TEMPLATE_MOCK_ENABLED__/);
  assert.doesNotMatch(source, /'query\.organizationId'/);
  assert.doesNotMatch(source, /interface TrainingTemplateCreatePayload\s*\{[^}]*organizationId:/);
  assert.doesNotMatch(source, /interface TrainingTemplateCategoryCreatePayload\s*\{[^}]*organizationId:/);
});
