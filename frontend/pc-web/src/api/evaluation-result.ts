import type { ApiResponse } from '@shared/types';
import { request } from './request';

/** 专家检查单项答案；字段与既有评价记录明细保持一致。 */
export interface ExpertInspectionItemAnswer {
  evaluationItemId: number;
  score?: number | null;
  textValue?: string | null;
}
/** 专家对计划内单门课程提交的不可编辑查课记录。 */
export interface ExpertInspectionSubmitRequest {
  trainingPlanId: number;
  planContentId: number;
  formVersionId: number;
  summary?: string | null;
  items: ExpertInspectionItemAnswer[];
}
/** 计划课程的专家查课状态摘要。 */
export interface ExpertInspectionSummary {
  planContentId: number;
  contentName: string;
  formVersionId: number;
  versionNo?: number | null;
  evaluationRecordId?: number | null;
  recordStatus?: string | null;
  evaluatorUserId?: number | null;
  evaluatorName?: string | null;
  submittedTime?: string | null;
  totalScore?: number | null;
  summary?: string | null;
}
/** 只读评价记录详情，专家查课提交后复用既有记录详情接口查看。 */
export interface EvaluationRecordDetail {
  evaluationRecordId: number;
  evaluatorUserId?: number | null;
  totalScore?: number | null;
  summary?: string | null;
  items: ExpertInspectionItemAnswer[];
}

/** 解包统一 Result 响应，业务失败继续保留后端返回的中文消息。 */
function unwrapResponse<TData>(response: ApiResponse<TData>): TData {
  if (response.code !== 0) throw new Error(response.message || '请求失败，请稍后重试。');
  return response.data;
}

/** 查询当前计划课程的专家查课提交状态。 */
export async function getExpertInspections(trainingPlanId: number): Promise<ExpertInspectionSummary[]> {
  return unwrapResponse(await request<ApiResponse<ExpertInspectionSummary[]>>({ url: `/api/training/plans/${trainingPlanId}/expert-inspections`, method: 'GET' }));
}

/** 原子提交专家查课记录，评价人由服务端从登录态写入。 */
export async function submitExpertInspection(data: ExpertInspectionSubmitRequest): Promise<void> {
  await unwrapResponse(await request<ApiResponse<unknown>>({ url: '/api/evaluation/expert-inspections/submit', method: 'POST', data }));
}
/** 读取已提交专家查课的逐项目答案。 */
export async function getEvaluationRecord(evaluationRecordId: number): Promise<EvaluationRecordDetail> {
  return unwrapResponse(await request<ApiResponse<EvaluationRecordDetail>>({ url: `/api/evaluation/records/${evaluationRecordId}`, method: 'GET' }));
}
