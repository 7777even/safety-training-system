import type { ApiResponse, LearnerPage } from '@shared/types';
import type { LearnerHistory, HistoryRecordType, HistoryCorrectionRequest } from '@shared/types/learner-history';
import { request } from './request';

/** 统一历史及更正直接读取真实服务，不能用Mock掩盖鉴权失败。 */
async function read<T>(url: string, params?: Record<string, unknown>): Promise<T> {
  const result = await request<ApiResponse<T>>({ url, method: 'GET', params });
  if (result.code !== 0) throw new Error(result.message || '档案读取失败');
  return result.data;
}
/** 本人历史分页，身份由服务端会话取得。 */
export const getMyHistory = (params: Record<string, unknown>): Promise<LearnerPage<LearnerHistory>> => read('/api/learner/history', params);
/** 本人原始摘要及只读更正轨迹。 */
export const getMyHistoryDetail = (type: HistoryRecordType, id: number): Promise<LearnerHistory> => read(`/api/learner/history/${type}/${id}`);
/** 管理查询须具备新留痕权限和对应领域读取权限。 */
export const getHistoryCorrections = (user: number, type: HistoryRecordType, id: number): Promise<LearnerHistory> => read(`/api/archive/persons/${user}/history/${type}/${id}/corrections`);
/** 管理追加更正，不能改正式业务结果。 */
export async function appendHistoryCorrection(user: number, type: HistoryRecordType, id: number, data: HistoryCorrectionRequest): Promise<LearnerHistory> {
  const result = await request<ApiResponse<LearnerHistory>>({ url: `/api/archive/persons/${user}/history/${type}/${id}/corrections`, method: 'POST', data });
  if (result.code !== 0) throw new Error(result.message || '档案更正失败');
  return result.data;
}
