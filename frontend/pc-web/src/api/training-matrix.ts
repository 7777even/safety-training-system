import type { ApiResponse } from '@shared/types';
import { request } from './request';
import type { TrainingPlanCreatePayload } from './training-plan';
/** 岗位培训矩阵条目，与 TrainingMatrixVO 契约一致。 */
export interface TrainingMatrix {
  matrixId: number;
  positionId: number;
  positionName: string;
  contentName: string;
  courseId: number;
  courseVersionId: number;
  courseName: string;
  requiredHours: number;
  status: 0 | 1;
  version: number;
}
/** 维护要求的请求，名称由后端主数据校验后填入。 */
export type TrainingMatrixPayload = Omit<TrainingMatrix, 'matrixId' | 'positionName' | 'courseName' | 'version'> & { version?: number };
/** 统一失败必须阻止界面报告成功。 */
function unwrap<T>(response: ApiResponse<T>): T {
  if (response.code !== 0) throw new Error(response.message || '岗位培训矩阵操作失败');
  return response.data;
}
/** 查询当前机构矩阵。 */
export async function getTrainingMatrices(): Promise<TrainingMatrix[]> {
  return unwrap(await request<ApiResponse<TrainingMatrix[]>>({ url: '/api/training/matrices', method: 'GET' }));
}
/** 保存要求，编辑必须传入读取时版本。 */
export async function saveTrainingMatrix(data: TrainingMatrixPayload, matrixId?: number): Promise<void> {
  unwrap(
    await request<ApiResponse<unknown>>({
      url: matrixId ? `/api/training/matrices/${matrixId}` : '/api/training/matrices',
      method: matrixId ? 'PUT' : 'POST',
      data
    })
  );
}
/** 管理员核对基础信息后原子创建草稿，服务端合并内容并冻结来源。 */
export async function createTrainingPlanFromMatrix(plan: TrainingPlanCreatePayload, matrixIds: number[]): Promise<number> {
  return unwrap(await request<ApiResponse<number>>({ url: '/api/training/matrices/create-plan', method: 'POST', data: { plan, matrixIds } }));
}
/** 查询计划不可变矩阵来源。 */
export async function getTrainingMatrixSource(planId: number): Promise<TrainingMatrix[]> {
  return unwrap(await request<ApiResponse<TrainingMatrix[]>>({ url: `/api/training/plans/${planId}/matrix-source`, method: 'GET' }));
}
