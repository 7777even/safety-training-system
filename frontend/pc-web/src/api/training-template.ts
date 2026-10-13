import type { ApiResponse } from '@shared/types';
import { request } from './request';
import type { TrainingContentExecutionConfig } from './training-plan';

/** 培训模块通用启停状态。 */
export type TrainingEnabledStatus = 0 | 1;

/** 培训模板发布状态，取值与 TrainingTemplateQuery Contract 一致。 */
export type TrainingTemplatePublishStatus = 'DRAFT' | 'PUBLISHED' | 'ARCHIVED';

/** 培训模板阶段类型。 */
export type TrainingTemplateStageType = 'ONLINE' | 'OFFLINE' | 'MIXED';

/** 培训模板内容类型。 */
export type TrainingTemplateContentType = 'COURSE' | 'EXAM' | 'QUESTION_BANK' | 'OFFLINE';

/** 培训模板接口业务错误，保留 Contract 业务码供页面展示。 */
export class TrainingTemplateApiError extends Error {
  constructor(
    public readonly code: number,
    message: string
  ) {
    super(message);
    this.name = 'TrainingTemplateApiError';
  }
}

/** 解包统一响应，并把业务失败转换为可识别异常。 */
function unwrap<TData>(response: ApiResponse<TData>): TData {
  if (response.code !== 0) {
    throw new TrainingTemplateApiError(response.code, response.message || '请求失败，请稍后重试。');
  }
  return response.data;
}

/** 通用分页数据。 */
export interface TrainingTemplatePage<TItem> {
  /** 当前页记录。 */
  list: TItem[];
  /** 符合条件的总记录数。 */
  total: number;
}

/** 模板分类分页查询参数，query.* 名称与 OpenAPI 保持一致。 */
export interface TrainingTemplateCategoryPageQuery {
  /** 页码。 */
  pageNum: number;
  /** 每页记录数。 */
  pageSize: number;
  /** 父分类 ID。 */
  'query.parentId'?: number;
  /** 分类名称模糊条件。 */
  'query.categoryName'?: string;
  /** 启停状态。 */
  'query.status'?: TrainingEnabledStatus;
}

/** 模板分类详情。 */
export interface TrainingTemplateCategory {
  /** 模板分类 ID。 */
  templateCategoryId: number;
  /** 所属机构 ID。 */
  organizationId: number;
  /** 父分类 ID，顶级分类为 0。 */
  parentId: number;
  /** 祖先 ID 路径。 */
  ancestors?: string;
  /** 分类名称。 */
  categoryName: string;
  /** 同级排序值。 */
  sortOrder: number;
  /** 启停状态。 */
  status: TrainingEnabledStatus;
  /** 乐观锁版本。 */
  version: number;
  /** 创建时间。 */
  createTime?: string;
}

/** 模板分类树节点。 */
export interface TrainingTemplateCategoryTreeNode {
  /** 模板分类 ID。 */
  templateCategoryId: number;
  /** 所属机构 ID。 */
  organizationId: number;
  /** 父分类 ID。 */
  parentId: number;
  /** 分类名称。 */
  categoryName: string;
  /** 同级排序值。 */
  sortOrder: number;
  /** 启停状态。 */
  status: TrainingEnabledStatus;
  /** 子分类列表，叶子节点为空数组。 */
  children: TrainingTemplateCategoryTreeNode[];
}

/** 模板分类创建参数。 */
export interface TrainingTemplateCategoryCreatePayload {
  /** 父分类 ID，不传时后端按 0 处理。 */
  parentId?: number;
  /** 分类名称。 */
  categoryName: string;
  /** 同级排序值。 */
  sortOrder: number;
  /** 启停状态。 */
  status: TrainingEnabledStatus;
}

/** 模板分类更新参数。 */
export interface TrainingTemplateCategoryUpdatePayload {
  /** 当前乐观锁版本。 */
  version: number;
  /** 父分类 ID。 */
  parentId?: number;
  /** 分类名称。 */
  categoryName: string;
  /** 同级排序值。 */
  sortOrder: number;
  /** 启停状态。 */
  status: TrainingEnabledStatus;
}

/** 独立模板分类树查询参数。 */
export interface TrainingTemplateCategoryTreeQuery {
  /** 启停状态。 */
  status?: TrainingEnabledStatus;
}

/** 分页查询模板分类。 */
export const getTrainingTemplateCategoryPage = async (
  params: TrainingTemplateCategoryPageQuery
): Promise<TrainingTemplatePage<TrainingTemplateCategory>> =>
  unwrap(await request<ApiResponse<TrainingTemplatePage<TrainingTemplateCategory>>>({
    url: '/api/training/template-categories', method: 'GET', params
  }));

/** 创建模板分类。 */
export const createTrainingTemplateCategory = async (data: TrainingTemplateCategoryCreatePayload): Promise<number> =>
  unwrap(await request<ApiResponse<number>>({ url: '/api/training/template-categories', method: 'POST', data }));

/** 查询模板分类详情。 */
export const getTrainingTemplateCategory = async (templateCategoryId: number): Promise<TrainingTemplateCategory> =>
  unwrap(await request<ApiResponse<TrainingTemplateCategory>>({
    url: `/api/training/template-categories/${templateCategoryId}`, method: 'GET'
  }));

/** 携带乐观锁版本更新模板分类。 */
export const updateTrainingTemplateCategory = async (
  templateCategoryId: number,
  data: TrainingTemplateCategoryUpdatePayload
): Promise<void> => unwrap(await request<ApiResponse<void>>({
  url: `/api/training/template-categories/${templateCategoryId}`, method: 'PUT', data
}));

/** 删除模板分类。 */
export const deleteTrainingTemplateCategory = async (templateCategoryId: number): Promise<void> =>
  unwrap(await request<ApiResponse<void>>({
    url: `/api/training/template-categories/${templateCategoryId}`, method: 'DELETE'
  }));

/** 查询完整模板分类树；参数不使用 query.* 前缀。 */
export const getTrainingTemplateCategoryTree = async (
  params: TrainingTemplateCategoryTreeQuery = {}
): Promise<TrainingTemplateCategoryTreeNode[]> =>
  unwrap(await request<ApiResponse<TrainingTemplateCategoryTreeNode[]>>({
    url: '/api/training/template-categories/tree', method: 'GET', params
  }));

/** 培训模板分页查询参数。 */
export interface TrainingTemplatePageQuery {
  /** 页码。 */
  pageNum: number;
  /** 每页记录数。 */
  pageSize: number;
  /** 模板编码精确条件。 */
  'query.templateCode'?: string;
  /** 模板名称模糊条件。 */
  'query.templateName'?: string;
  /** 模板分类 ID。 */
  'query.templateCategoryId'?: number;
  /** 培训类型 ID。 */
  'query.trainingTypeId'?: number;
  /** 发布状态。 */
  'query.publishStatus'?: TrainingTemplatePublishStatus;
  /** 启停状态。 */
  'query.status'?: TrainingEnabledStatus;
}

/** 培训模板内容编排参数。 */
export interface TrainingTemplateContentPayload {
  /** 内容类型。 */
  contentType: TrainingTemplateContentType;
  /** 线上资源 ID；线下内容可不传。 */
  resourceId?: number;
  /** 资源版本 ID；课程为课程版本、考试为试卷版本，题库和线下内容为空。 */
  resourceVersionId?: number | null;
  /** 考试模式，仅 EXAM 使用。 */
  assessmentMode?: 'QUIZ' | 'FORMAL_EXAM' | null;
  /** 内容执行配置，结构与计划内容共用。 */
  executionConfig?: TrainingContentExecutionConfig | null;
  /** 内容名称。 */
  contentName: string;
  /** 是否必修。 */
  isRequired: TrainingEnabledStatus;
  /** 要求学时。 */
  requiredHours?: number;
  /** 阶段内排序值。 */
  sortOrder: number;
  /** 启停状态。 */
  status: TrainingEnabledStatus;
}

/** 培训模板阶段编排参数。 */
export interface TrainingTemplateStagePayload {
  /** 阶段名称。 */
  stageName: string;
  /** 阶段类型。 */
  stageType: TrainingTemplateStageType;
  /** 阶段要求学时。 */
  requiredHours?: number;
  /** 模板内排序值。 */
  sortOrder: number;
  /** 启停状态。 */
  status: TrainingEnabledStatus;
  /** 阶段内容列表。 */
  contents?: TrainingTemplateContentPayload[];
}

/** 培训模板内容详情。 */
export interface TrainingTemplateContent extends TrainingTemplateContentPayload {
  /** 模板内容 ID。 */
  templateContentId: number;
  /** 所属模板阶段 ID。 */
  templateStageId: number;
}

/** 培训模板阶段详情。 */
export interface TrainingTemplateStage extends Omit<TrainingTemplateStagePayload, 'contents'> {
  /** 模板阶段 ID。 */
  templateStageId: number;
  /** 所属培训模板 ID。 */
  trainingTemplateId: number;
  /** 阶段内容列表。 */
  contents?: TrainingTemplateContent[];
}

/** 培训模板详情。 */
export interface TrainingTemplate {
  /** 培训模板 ID。 */
  trainingTemplateId: number;
  /** 所属机构 ID。 */
  organizationId: number;
  /** 模板编码。 */
  templateCode: string;
  /** 模板名称。 */
  templateName: string;
  /** 模板分类 ID。 */
  templateCategoryId?: number;
  /** 培训类型 ID。 */
  trainingTypeId: number;
  /** 培训标准 ID。 */
  trainingStandardId?: number;
  /** 模板说明。 */
  description?: string;
  /** 发布状态。 */
  publishStatus: TrainingTemplatePublishStatus;
  /** 启停状态。 */
  status: TrainingEnabledStatus;
  /** 乐观锁版本。 */
  version: number;
  /** 创建时间。 */
  createTime?: string;
  /** 阶段及内容编排。 */
  stages?: TrainingTemplateStage[];
}

/** 培训模板创建参数。 */
export interface TrainingTemplateCreatePayload {
  /** 模板编码。 */
  templateCode: string;
  /** 模板名称。 */
  templateName: string;
  /** 模板分类 ID。 */
  templateCategoryId?: number;
  /** 培训类型 ID。 */
  trainingTypeId: number;
  /** 培训标准 ID。 */
  trainingStandardId?: number;
  /** 模板说明。 */
  description?: string;
  /** 启停状态。 */
  status: TrainingEnabledStatus;
}

/** 培训模板更新参数。 */
export interface TrainingTemplateUpdatePayload {
  /** 当前乐观锁版本。 */
  version: number;
  /** 模板名称。 */
  templateName: string;
  /** 模板分类 ID。 */
  templateCategoryId?: number;
  /** 培训类型 ID。 */
  trainingTypeId: number;
  /** 培训标准 ID。 */
  trainingStandardId?: number;
  /** 模板说明。 */
  description?: string;
  /** 启停状态。 */
  status: TrainingEnabledStatus;
}

/** 培训模板阶段覆盖参数。 */
export interface TrainingTemplateStagesReplacePayload {
  /** 当前乐观锁版本。 */
  version: number;
  /** 完整阶段列表；空列表表示清空编排。 */
  stages?: TrainingTemplateStagePayload[];
}

/** 培训模板发布参数。 */
export interface TrainingTemplatePublishPayload {
  /** 当前乐观锁版本。 */
  version: number;
}

/** 分页查询培训模板。 */
export const getTrainingTemplatePage = async (
  params: TrainingTemplatePageQuery
): Promise<TrainingTemplatePage<TrainingTemplate>> =>
  unwrap(await request<ApiResponse<TrainingTemplatePage<TrainingTemplate>>>({
    url: '/api/training/templates', method: 'GET', params
  }));

/** 创建培训模板草稿。 */
export const createTrainingTemplate = async (data: TrainingTemplateCreatePayload): Promise<number> =>
  unwrap(await request<ApiResponse<number>>({ url: '/api/training/templates', method: 'POST', data }));

/** 查询包含阶段和内容的培训模板详情。 */
export const getTrainingTemplate = async (trainingTemplateId: number): Promise<TrainingTemplate> =>
  unwrap(await request<ApiResponse<TrainingTemplate>>({
    url: `/api/training/templates/${trainingTemplateId}`, method: 'GET'
  }));

/** 更新培训模板草稿基本信息。 */
export const updateTrainingTemplate = async (
  trainingTemplateId: number,
  data: TrainingTemplateUpdatePayload
): Promise<void> => unwrap(await request<ApiResponse<void>>({
  url: `/api/training/templates/${trainingTemplateId}`, method: 'PUT', data
}));

/** 删除培训模板草稿。 */
export const deleteTrainingTemplate = async (trainingTemplateId: number): Promise<void> =>
  unwrap(await request<ApiResponse<void>>({url: `/api/training/templates/${trainingTemplateId}`, method: 'DELETE'}));

/** 使用乐观锁版本完整替换模板阶段及内容。 */
export const replaceTrainingTemplateStages = async (
  trainingTemplateId: number,
  data: TrainingTemplateStagesReplacePayload
): Promise<void> => unwrap(await request<ApiResponse<void>>({
  url: `/api/training/templates/${trainingTemplateId}/stages`, method: 'PUT', data
}));

/** 发布培训模板，发布后模板及阶段进入只读状态。 */
export const publishTrainingTemplate = async (
  trainingTemplateId: number,
  data: TrainingTemplatePublishPayload
): Promise<void> => unwrap(await request<ApiResponse<void>>({
  url: `/api/training/templates/${trainingTemplateId}/publish`, method: 'POST', data
}));
