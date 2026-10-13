import type { ApiResponse, LearnerPage } from '@shared/types';
import type { LearnerHistory, HistoryRecordType } from '@shared/types/learner-history';
import { request } from './request';

/** 本人历史直接读取真实服务，不通过当前任务筛选已有记录。 */
async function read<T>(url: string, params?: Record<string, unknown>): Promise<T> {
  const result = await request<ApiResponse<T>>({ url, method: 'GET', params });
  if (result.code !== 0) throw new Error(result.message || '档案读取失败');
  return result.data;
}
/** 本人历史分页。 */
export const getMyHistory = (params: Record<string, unknown>): Promise<LearnerPage<LearnerHistory>> => read('/api/learner/history', params);
/** 本人原始摘要及只读更正轨迹；H5不提供更正写入。 */
export const getMyHistoryDetail = (type: HistoryRecordType, id: number): Promise<LearnerHistory> => read(`/api/learner/history/${type}/${id}`);
