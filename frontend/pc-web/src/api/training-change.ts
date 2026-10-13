import type { ApiResponse } from '@shared/types';
import { request } from './request';
import type { TrainingPlan, TrainingPlanUpdatePayload, TrainingPlanStage, TrainingPlanEvaluationForm } from './training-plan';
import type { AnnualPlan } from './training-approval';
import type { PersonSelectionRequest } from './person-selection';
/** 执行中可变更的四项信息。 */
export interface PlanBasicChange {
  planName: string;
  managerUserId?: number | null;
  location?: string | null;
  description?: string | null;
}
/** 完整替换只用于无安排和无事实的已发布计划。 */
export interface PlanFullChange {
  plan: TrainingPlanUpdatePayload;
  stages: TrainingPlanStage[];
  forms: TrainingPlanEvaluationForm[];
  participantSelection: PersonSelectionRequest;
  participantUserIds?: number[];
  annualSnapshot?: AnnualPlan;
  annual?: { version: number; annualPlanId?: number | null };
}
/** 保存独立变更而非修改生效计划。 */
export interface PlanChangePayload {
  baseVersion: number;
  version?: number;
  changeType: 'BASIC' | 'FULL';
  basic?: PlanBasicChange;
  full?: PlanFullChange;
}
/** 变更详情包含基准快照和指定审批人。 */
export interface PlanChange {
  changeId: number;
  trainingPlanId: number;
  baseVersion: number;
  version: number;
  submitterUserId: number;
  approverUserId?: number;
  changeStatus: 'DRAFT' | 'PENDING' | 'APPROVED' | 'REJECTED';
  request: PlanChangePayload;
  before: TrainingPlan;
  beforeParticipants: Array<{ userId: number }>;
  decisionComment?: string;
  createTime?: string;
}
/** 失败必须保留原计划并向界面暴露中文提示。 */
function unwrap<T>(res: ApiResponse<T>): T {
  if (res.code !== 0) throw new Error(res.message || '计划变更操作失败');
  return res.data;
}
/** 读取独立草稿和历史。 */
export async function getPlanChanges(id: number): Promise<PlanChange[]> {
  return unwrap(await request<ApiResponse<PlanChange[]>>({ url: `/api/training/plans/${id}/changes`, method: 'GET' }));
}
/** 创建或编辑自己未提交的草稿，返回记录ID。 */
export async function savePlanChange(id: number, data: PlanChangePayload, changeId?: number): Promise<number> {
  const value = unwrap(
    await request<ApiResponse<number | null>>({
      url: changeId ? `/api/training/plans/${id}/changes/${changeId}` : `/api/training/plans/${id}/changes`,
      method: changeId ? 'PUT' : 'POST',
      data
    })
  );
  return changeId ?? value!;
}
/** 提交变更记录版本，与计划基准版本分别校验。 */
export async function submitPlanChange(id: number, changeId: number, version: number, approverUserId: number): Promise<void> {
  unwrap(
    await request<ApiResponse<null>>({
      url: `/api/training/plans/${id}/changes/${changeId}/submit`,
      method: 'POST',
      data: { version, approverUserId }
    })
  );
}
/** 批准事务生效；驳回仅记录意见。 */
export async function decidePlanChange(id: number, changeId: number, approved: boolean, comment: string): Promise<void> {
  unwrap(
    await request<ApiResponse<null>>({ url: `/api/training/plans/${id}/changes/${changeId}/decision`, method: 'POST', data: { approved, comment } })
  );
}
