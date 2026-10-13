import type { ApiResponse } from '@shared/types';
import { request } from './request';
/** 首次审批状态独立于执行状态；记录的版本与计划版本不同即需重新提交。 */
export interface PlanApproval {
  approvalId: number;
  trainingPlanId: number;
  planVersion: number;
  submitterUserId: number;
  approverUserId: number;
  approvalStatus: 'PENDING' | 'APPROVED' | 'REJECTED';
  decisionComment?: string;
  version: number;
  createTime?: string;
}
/** 年度文件及计划冻结关联。 */
export interface AnnualPlan {
  annualPlanId: number;
  planYear: number;
  planName: string;
  fileInfoId: number;
  fileName: string;
  version: number;
}
/** 审批候选人由后端真实权限筛选。 */
export interface PlanApprover {
  userId: number;
  userName: string;
}
/** 业务失败不能转换为成功。 */
function unwrap<T>(res: ApiResponse<T>): T {
  if (res.code !== 0) throw new Error(res.message || '培训计划审批操作失败');
  return res.data;
}
/** 查看历史。 */
export async function getPlanApprovals(id: number): Promise<PlanApproval[]> {
  return unwrap(await request<ApiResponse<PlanApproval[]>>({ url: `/api/training/plans/${id}/approvals`, method: 'GET' }));
}
/** 查看其他有权限的人员。 */
export async function getPlanApprovers(): Promise<PlanApprover[]> {
  return unwrap(await request<ApiResponse<PlanApprover[]>>({ url: '/api/training/approval-approvers', method: 'GET' }));
}
/** 提交当前版本。 */
export async function submitPlanApproval(id: number, version: number, approverUserId: number): Promise<void> {
  unwrap(await request<ApiResponse<null>>({ url: `/api/training/plans/${id}/approvals`, method: 'POST', data: { version, approverUserId } }));
}
/** 提交审批结论。 */
export async function decidePlanApproval(id: number, approved: boolean, comment: string): Promise<void> {
  unwrap(await request<ApiResponse<null>>({ url: `/api/training/plans/${id}/approval-decision`, method: 'POST', data: { approved, comment } }));
}
/** 年度文件列表。 */
export async function getAnnualPlans(): Promise<AnnualPlan[]> {
  return unwrap(await request<ApiResponse<AnnualPlan[]>>({ url: '/api/training/annual-plans', method: 'GET' }));
}
/** 创建或按版本编辑年度文件。 */
export async function saveAnnualPlan(
  data: Omit<AnnualPlan, 'annualPlanId' | 'fileName' | 'version'> & { version?: number },
  id?: number
): Promise<void> {
  unwrap(
    await request<ApiResponse<unknown>>({
      url: id ? `/api/training/annual-plans/${id}` : '/api/training/annual-plans',
      method: id ? 'PUT' : 'POST',
      data
    })
  );
}
/** 读取冻结依据。 */
export async function getLinkedAnnualPlan(id: number): Promise<AnnualPlan | null> {
  return unwrap(await request<ApiResponse<AnnualPlan | null>>({ url: `/api/training/plans/${id}/annual-plan`, method: 'GET' }));
}
/** 关联或解除年度依据，服务端同步推进计划版本。 */
export async function bindAnnualPlan(id: number, version: number, annualPlanId?: number): Promise<void> {
  unwrap(
    await request<ApiResponse<null>>({
      url: `/api/training/plans/${id}/annual-plan`,
      method: 'PUT',
      data: { version, annualPlanId: annualPlanId ?? null }
    })
  );
}
/** 通过业务记录受控下载，不能读取任意文件ID。 */
export async function getAnnualPlanFile(id: number, linked = false): Promise<Blob> {
  return request<Blob>({
    url: linked ? `/api/training/plans/${id}/annual-plan/file` : `/api/training/annual-plans/${id}/file`,
    method: 'GET',
    responseType: 'blob'
  });
}
