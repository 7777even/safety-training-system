import type { ApiResponse } from '@shared/types';
import { request } from './request';

/** 评价表单类型，与 evaluation-form.openapi.json 保持一致。 */
export type EvaluationFormType = 'COMPOSITE' | 'EXPERT_INSPECTION';
/** 评价表单启用状态。 */
export type EvaluationFormStatus = 0 | 1;
/** 评价项目类型。 */
export type EvaluationItemType = 'SCORE' | 'TEXT' | 'CHOICE';
/** 评分规则计算类型。 */
export type EvaluationCalculationType = 'WEIGHT' | 'ADJUSTMENT';
/** 评分规则成绩来源。 */
export type EvaluationSourceType = 'COURSE' | 'EXAM' | 'OFFLINE' | 'REWARD_PENALTY' | 'MANUAL';
/** 不可变表单版本的发布状态。 */
export type EvaluationPublishedStatus = 'DRAFT' | 'PUBLISHED' | 'ARCHIVED';

/** 评价表单分页查询参数，字段名直接对应 OpenAPI query 参数。 */
export interface EvaluationFormPageParams {
  pageNum: number;
  pageSize: number;
  'query.formCode'?: string;
  'query.formName'?: string;
  'query.formType'?: EvaluationFormType;
  'query.status'?: EvaluationFormStatus;
}

/** 评价表单主体。 */
export interface EvaluationForm {
  evaluationFormId: number;
  formCode: string;
  formName: string;
  formType: EvaluationFormType;
  currentVersionNo: number;
  status: EvaluationFormStatus;
  description: string;
  version: number;
  createTime: string;
}

/** 评价表单分页结果。 */
export interface EvaluationFormPage {
  list: EvaluationForm[];
  total: number;
}

/** 创建评价表单主体。 */
export interface EvaluationFormCreateRequest {
  formCode: string;
  formName: string;
  formType: EvaluationFormType;
  status: EvaluationFormStatus;
  description: string;
}

/** 更新评价表单主体。 */
export interface EvaluationFormUpdateRequest {
  version: number;
  formName: string;
  formType: EvaluationFormType;
  status: EvaluationFormStatus;
  description: string;
}

/** 发布时提交的评价项目。 */
export interface EvaluationItemRequest {
  itemCode: string;
  itemName: string;
  itemType: EvaluationItemType;
  maxScore?: number | null;
  weight?: number | null;
  sortOrder: number;
  isRequired: 0 | 1;
}

/** 发布时提交的评分规则，通过项目编码绑定同批次项目。 */
export interface EvaluationScoreRuleRequest {
  evaluationItemCode: string;
  calculationType: EvaluationCalculationType;
  sourceType: EvaluationSourceType;
  scoreChange?: number | null;
  weight?: number | null;
  sortOrder: number;
  ruleDescription: string;
}

/** 发布评价表单新版本。 */
export interface EvaluationFormPublishRequest {
  version: number;
  totalScore: number;
  versionNote?: string | null;
  items: EvaluationItemRequest[];
  scoreRules?: EvaluationScoreRuleRequest[];
}

/** 已发布版本中的评价项目快照。 */
export interface EvaluationItem {
  evaluationItemId: number;
  formVersionId: number;
  itemCode: string;
  itemName: string;
  itemType: EvaluationItemType;
  maxScore: number | null;
  weight: number | null;
  sortOrder: number;
  isRequired: 0 | 1;
}

/** 已发布版本中的评分规则快照。 */
export interface EvaluationScoreRule {
  scoreRuleId: number;
  formVersionId: number;
  evaluationItemId: number;
  calculationType: EvaluationCalculationType;
  sourceType: EvaluationSourceType;
  scoreChange: number | null;
  weight: number | null;
  sortOrder: number;
  ruleDescription: string;
}

/** 评价表单版本历史摘要。 */
export interface EvaluationFormVersionSummary {
  formVersionId: number;
  evaluationFormId: number;
  versionNo: number;
  publishedStatus: EvaluationPublishedStatus;
  publishedBy: number | null;
  publishedTime: string | null;
  versionNote: string | null;
}

/** 评价表单不可变版本详情。 */
export interface EvaluationFormVersionDetail extends EvaluationFormVersionSummary {
  totalScore: number;
  items: EvaluationItem[];
  scoreRules: EvaluationScoreRule[];
}

/** 解包统一 Result 响应。 */
function unwrapResponse<TData>(response: ApiResponse<TData>): TData {
  if (response.code !== 0) throw new Error(response.message || '请求失败，请稍后重试。');
  return response.data;
}

/** 分页查询评价表单主体。 */
export async function getEvaluationFormPage(params: EvaluationFormPageParams): Promise<EvaluationFormPage> {
  return unwrapResponse(
    await request<ApiResponse<EvaluationFormPage>>({ url: '/api/evaluation/forms', method: 'GET', params })
  );
}

/** 查询评价表单主体详情。 */
export async function getEvaluationFormDetail(evaluationFormId: number): Promise<EvaluationForm> {
  return unwrapResponse(
    await request<ApiResponse<EvaluationForm>>({
      url: `/api/evaluation/forms/${evaluationFormId}`,
      method: 'GET'
    })
  );
}

/** 创建评价表单主体。 */
export async function createEvaluationForm(data: EvaluationFormCreateRequest): Promise<number> {
  return unwrapResponse(
    await request<ApiResponse<number>>({ url: '/api/evaluation/forms', method: 'POST', data })
  );
}

/** 按乐观锁版本更新评价表单主体。 */
export async function updateEvaluationForm(
  evaluationFormId: number,
  data: EvaluationFormUpdateRequest
): Promise<void> {
  unwrapResponse(
    await request<ApiResponse<null>>({
      url: `/api/evaluation/forms/${evaluationFormId}`,
      method: 'PUT',
      data
    })
  );
}

/** 删除尚未产生版本的评价表单主体。 */
export async function deleteEvaluationForm(evaluationFormId: number): Promise<void> {
  unwrapResponse(
    await request<ApiResponse<null>>({
      url: `/api/evaluation/forms/${evaluationFormId}`,
      method: 'DELETE'
    })
  );
}

/** 发布评价表单不可变新版本。 */
export async function publishEvaluationForm(
  evaluationFormId: number,
  data: EvaluationFormPublishRequest
): Promise<EvaluationFormVersionDetail> {
  return unwrapResponse(
    await request<ApiResponse<EvaluationFormVersionDetail>>({
      url: `/api/evaluation/forms/${evaluationFormId}/publish`,
      method: 'POST',
      data
    })
  );
}

/** 按版本号倒序查询评价表单版本历史。 */
export async function getEvaluationFormVersions(evaluationFormId: number): Promise<EvaluationFormVersionSummary[]> {
  return unwrapResponse(
    await request<ApiResponse<EvaluationFormVersionSummary[]>>({
      url: `/api/evaluation/forms/${evaluationFormId}/versions`,
      method: 'GET'
    })
  );
}

/** 查询评价表单不可变版本详情。 */
export async function getEvaluationFormVersionDetail(formVersionId: number): Promise<EvaluationFormVersionDetail> {
  return unwrapResponse(
    await request<ApiResponse<EvaluationFormVersionDetail>>({
      url: `/api/evaluation/forms/versions/${formVersionId}`,
      method: 'GET'
    })
  );
}
