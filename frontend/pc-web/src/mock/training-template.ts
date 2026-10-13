// 历史测试夹具：生产 API 已不再导入或调用本文件。
import type { ApiResponse } from '@shared/types';
import type {
  TrainingTemplateCategory,
  TrainingTemplateCategoryCreatePayload,
  TrainingTemplateCategoryPageQuery,
  TrainingTemplateCategoryTreeNode,
  TrainingTemplateCategoryTreeQuery,
  TrainingTemplateCategoryUpdatePayload,
  TrainingTemplatePage,
  TrainingTemplate,
  TrainingTemplateCreatePayload,
  TrainingTemplatePageQuery,
  TrainingTemplatePublishPayload,
  TrainingTemplateStagesReplacePayload,
  TrainingTemplateUpdatePayload
} from '@/api/training-template';

/** 培训模板 Mock 的浏览器会话状态。 */
const store = {
  categories: [] as TrainingTemplateCategory[],
  nextCategoryId: 3,
  templates: [] as TrainingTemplate[],
  nextTemplateId: 2,
  nextStageId: 2,
  nextContentId: 2
};

/** 构造 Contract 成功响应。 */
function success<TData>(data: TData): ApiResponse<TData> {
  return { code: 0, message: 'success', data };
}

/** 构造 Contract 业务失败响应。 */
function failed<TData>(code: number, message: string): ApiResponse<TData> {
  return { code, message, data: null as TData };
}

/** 重置培训模板 Mock，确保测试与开发刷新具有确定性。 */
export function resetTrainingTemplateMock(): void {
  store.categories = [
    {
      templateCategoryId: 1,
      organizationId: 1,
      parentId: 0,
      ancestors: '0',
      categoryName: '岗位安全培训',
      sortOrder: 1,
      status: 1,
      version: 0,
      createTime: '2026-07-31T09:00:00'
    },
    {
      templateCategoryId: 2,
      organizationId: 1,
      parentId: 1,
      ancestors: '0,1',
      categoryName: '新员工培训',
      sortOrder: 1,
      status: 1,
      version: 0,
      createTime: '2026-07-31T09:10:00'
    }
  ];
  store.nextCategoryId = 3;
  store.templates = [
    {
      trainingTemplateId: 1,
      organizationId: 1,
      templateCode: 'TPL-MOCK-001',
      templateName: '新员工岗位安全培训模板',
      templateCategoryId: 2,
      trainingTypeId: 1,
      trainingStandardId: 1,
      description: '覆盖新员工岗位风险识别与应急处置。',
      publishStatus: 'DRAFT',
      status: 1,
      version: 0,
      createTime: '2026-07-31T09:20:00',
      stages: [
        {
          templateStageId: 1,
          trainingTemplateId: 1,
          stageName: '线上学习',
          stageType: 'ONLINE',
          requiredHours: 2,
          sortOrder: 1,
          status: 1,
          contents: [
            {
              templateContentId: 1,
              templateStageId: 1,
              contentType: 'COURSE',
              resourceId: 1,
              contentName: '岗位安全基础课程',
              isRequired: 1,
              requiredHours: 2,
              sortOrder: 1,
              status: 1
            }
          ]
        }
      ]
    }
  ];
  store.nextTemplateId = 2;
  store.nextStageId = 2;
  store.nextContentId = 2;
}

/** 分页查询模板分类并支持 OpenAPI 定义的 query.* 筛选。 */
export async function mockGetTrainingTemplateCategoryPage(
  query: TrainingTemplateCategoryPageQuery
): Promise<ApiResponse<TrainingTemplatePage<TrainingTemplateCategory>>> {
  const keyword = query['query.categoryName']?.trim();
  const filtered = store.categories.filter(
    (item) =>
      (query['query.parentId'] === undefined || item.parentId === query['query.parentId']) &&
      (!keyword || item.categoryName.includes(keyword)) &&
      (query['query.status'] === undefined || item.status === query['query.status'])
  );
  const start = (query.pageNum - 1) * query.pageSize;
  return success({
    list: structuredClone(filtered.slice(start, start + query.pageSize)),
    total: filtered.length
  });
}

/** 创建模板分类。 */
export async function mockCreateTrainingTemplateCategory(
  data: TrainingTemplateCategoryCreatePayload
): Promise<ApiResponse<number>> {
  const templateCategoryId = store.nextCategoryId++;
  store.categories.push({
    templateCategoryId,
    organizationId: 1,
    parentId: data.parentId ?? 0,
    categoryName: data.categoryName,
    sortOrder: data.sortOrder,
    status: data.status,
    version: 0,
    createTime: new Date().toISOString()
  });
  return success(templateCategoryId);
}

/** 查询模板分类详情。 */
export async function mockGetTrainingTemplateCategory(
  templateCategoryId: number
): Promise<ApiResponse<TrainingTemplateCategory>> {
  const category = store.categories.find((item) => item.templateCategoryId === templateCategoryId);
  return category ? success(structuredClone(category)) : failed(8030, '模板分类不存在。');
}

/** 携带乐观锁版本更新模板分类。 */
export async function mockUpdateTrainingTemplateCategory(
  templateCategoryId: number,
  data: TrainingTemplateCategoryUpdatePayload
): Promise<ApiResponse<void>> {
  const category = store.categories.find((item) => item.templateCategoryId === templateCategoryId);
  if (!category) return failed(8030, '模板分类不存在。');
  if (category.version !== data.version) return failed(8041, '模板分类版本冲突。');
  Object.assign(category, data, { parentId: data.parentId ?? 0, version: category.version + 1 });
  return success(undefined);
}

/** 删除无子分类的模板分类。 */
export async function mockDeleteTrainingTemplateCategory(templateCategoryId: number): Promise<ApiResponse<void>> {
  const index = store.categories.findIndex((item) => item.templateCategoryId === templateCategoryId);
  if (index < 0) return failed(8030, '模板分类不存在。');
  if (store.categories.some((item) => item.parentId === templateCategoryId)) {
    return failed(8039, '模板分类存在子分类，不可删除。');
  }
  if (store.templates.some((item) => item.templateCategoryId === templateCategoryId)) {
    return failed(8040, '模板分类已被培训模板引用，不可删除。');
  }
  store.categories.splice(index, 1);
  return success(undefined);
}

/** 将扁平模板分类转换为 OpenAPI 定义的 children 树。 */
function buildCategoryTree(categories: TrainingTemplateCategory[]): TrainingTemplateCategoryTreeNode[] {
  const nodes = new Map<number, TrainingTemplateCategoryTreeNode>();
  categories.forEach((item) => nodes.set(item.templateCategoryId, { ...item, children: [] }));
  const roots: TrainingTemplateCategoryTreeNode[] = [];
  nodes.forEach((node) => {
    const parent = nodes.get(node.parentId);
    if (parent) parent.children.push(node);
    else roots.push(node);
  });
  const sortNodes = (items: TrainingTemplateCategoryTreeNode[]): void => {
    items.sort((left, right) => left.sortOrder - right.sortOrder);
    items.forEach((item) => sortNodes(item.children));
  };
  sortNodes(roots);
  return roots;
}

/** 查询完整模板分类树。 */
export async function mockGetTrainingTemplateCategoryTree(
  query: TrainingTemplateCategoryTreeQuery = {}
): Promise<ApiResponse<TrainingTemplateCategoryTreeNode[]>> {
  const filtered = store.categories.filter((item) => query.status === undefined || item.status === query.status);
  return success(buildCategoryTree(filtered));
}

/** 分页查询培训模板并支持 OpenAPI 定义的 query.* 筛选。 */
export async function mockGetTrainingTemplatePage(
  query: TrainingTemplatePageQuery
): Promise<ApiResponse<TrainingTemplatePage<TrainingTemplate>>> {
  const code = query['query.templateCode']?.trim();
  const name = query['query.templateName']?.trim();
  const filtered = store.templates.filter(
    (item) =>
      (!code || item.templateCode === code) &&
      (!name || item.templateName.includes(name)) &&
      (query['query.templateCategoryId'] === undefined || item.templateCategoryId === query['query.templateCategoryId']) &&
      (query['query.trainingTypeId'] === undefined || item.trainingTypeId === query['query.trainingTypeId']) &&
      (query['query.publishStatus'] === undefined || item.publishStatus === query['query.publishStatus']) &&
      (query['query.status'] === undefined || item.status === query['query.status'])
  );
  const start = (query.pageNum - 1) * query.pageSize;
  return success({
    list: structuredClone(filtered.slice(start, start + query.pageSize)),
    total: filtered.length
  });
}

/** 创建培训模板草稿。 */
export async function mockCreateTrainingTemplate(data: TrainingTemplateCreatePayload): Promise<ApiResponse<number>> {
  if (store.templates.some((item) => item.templateCode === data.templateCode)) {
    return failed(8032, '同机构下模板编码已存在。');
  }
  const trainingTemplateId = store.nextTemplateId++;
  store.templates.push({
    ...data,
    organizationId: 1,
    trainingTemplateId,
    publishStatus: 'DRAFT',
    version: 0,
    createTime: new Date().toISOString(),
    stages: []
  });
  return success(trainingTemplateId);
}

/** 查询包含阶段和内容的培训模板详情。 */
export async function mockGetTrainingTemplate(trainingTemplateId: number): Promise<ApiResponse<TrainingTemplate>> {
  const template = store.templates.find((item) => item.trainingTemplateId === trainingTemplateId);
  return template ? success(structuredClone(template)) : failed(8031, '培训模板不存在。');
}

/** 更新培训模板草稿基本信息。 */
export async function mockUpdateTrainingTemplate(
  trainingTemplateId: number,
  data: TrainingTemplateUpdatePayload
): Promise<ApiResponse<void>> {
  const template = store.templates.find((item) => item.trainingTemplateId === trainingTemplateId);
  if (!template) return failed(8031, '培训模板不存在。');
  if (template.publishStatus !== 'DRAFT') return failed(8033, '仅草稿模板可编辑或删除。');
  if (template.version !== data.version) return failed(8034, '培训模板版本冲突。');
  Object.assign(template, data, { version: template.version + 1 });
  return success(undefined);
}

/** 删除培训模板草稿。 */
export async function mockDeleteTrainingTemplate(trainingTemplateId: number): Promise<ApiResponse<void>> {
  const index = store.templates.findIndex((item) => item.trainingTemplateId === trainingTemplateId);
  if (index < 0) return failed(8031, '培训模板不存在。');
  if (store.templates[index].publishStatus !== 'DRAFT') return failed(8033, '仅草稿模板可编辑或删除。');
  store.templates.splice(index, 1);
  return success(undefined);
}

/** 携带乐观锁版本完整替换培训模板阶段。 */
export async function mockReplaceTrainingTemplateStages(
  trainingTemplateId: number,
  data: TrainingTemplateStagesReplacePayload
): Promise<ApiResponse<void>> {
  const template = store.templates.find((item) => item.trainingTemplateId === trainingTemplateId);
  if (!template) return failed(8031, '培训模板不存在。');
  if (template.publishStatus !== 'DRAFT') return failed(8035, '已发布模板不可编辑阶段。');
  if (template.version !== data.version) return failed(8034, '培训模板版本冲突。');
  template.stages = (data.stages ?? []).map((stage) => {
    const templateStageId = store.nextStageId++;
    return {
      ...stage,
      templateStageId,
      trainingTemplateId,
      contents: (stage.contents ?? []).map((content) => ({
        ...content,
        templateContentId: store.nextContentId++,
        templateStageId
      }))
    };
  });
  template.version += 1;
  return success(undefined);
}

/** 发布培训模板并冻结当前模板结构。 */
export async function mockPublishTrainingTemplate(
  trainingTemplateId: number,
  data: TrainingTemplatePublishPayload
): Promise<ApiResponse<void>> {
  const template = store.templates.find((item) => item.trainingTemplateId === trainingTemplateId);
  if (!template) return failed(8031, '培训模板不存在。');
  if (template.publishStatus !== 'DRAFT') return failed(8036, '当前状态不可发布。');
  if (template.version !== data.version) return failed(8034, '培训模板版本冲突。');
  template.publishStatus = 'PUBLISHED';
  template.version += 1;
  return success(undefined);
}

resetTrainingTemplateMock();
