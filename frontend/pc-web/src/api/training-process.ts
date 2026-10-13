import type { ApiResponse } from '@shared/types';
import { request } from './request';

/** 培训计划下由执行生成的线下场次事实。 */
export interface TrainingOfflineSession {
  offlineSessionId: number;
  trainingPlanId: number;
  planStageId: number;
  planContentId: number;
  sessionName: string;
  teacherUserId: number;
  location?: string;
  startTime?: string;
  endTime?: string;
  sessionStatus: string;
}

/** 培训资料类型；与培训资料契约 `attachmentType` 的枚举一致。奖惩佐证已收口到奖惩记录，不再是资料类型。 */
export type TrainingAttachmentType = 'GENERAL' | 'IMAGE' | 'VIDEO' | 'EVALUATION';

/** 培训计划过程中的资料事实（含影像），版本用于编辑时的乐观锁校验。 */
export interface TrainingAttachment {
  trainingAttachmentId: number;
  trainingPlanId: number;
  planStageId?: number;
  attachmentType: TrainingAttachmentType;
  fileInfoId: number;
  attachmentName: string;
  description?: string;
  sortOrder?: number;
  status?: 0 | 1;
  version: number;
}

/** 计划内维护的资料内容；关联阶段只能从当前计划阶段选择。 */
export interface TrainingAttachmentPayload {
  planStageId?: number;
  attachmentType: TrainingAttachmentType;
  fileInfoId: number;
  attachmentName: string;
  description?: string;
  sortOrder: number;
  status: 0 | 1;
}

/** 培训计划过程中的奖惩事实，版本用于编辑时的乐观锁校验。 */
export interface TrainingRewardPenalty {
  rewardPenaltyId: number;
  trainingPlanId: number;
  trainingParticipantId: number;
  recordType: 'REWARD' | 'PENALTY';
  title: string;
  description?: string;
  scoreChange?: number;
  occurredDate?: string;
  evidenceFileId?: number;
  status: 0 | 1;
  version: number;
}

/** 计划内录入的奖惩内容；参训人只能从当前计划名单选择。 */
export interface TrainingRewardPenaltyPayload {
  recordType: 'REWARD' | 'PENALTY';
  title: string;
  description?: string;
  scoreChange?: number;
  occurredDate?: string;
  evidenceFileId?: number;
  status: 0 | 1;
}

/** 保留后端错误码及中文消息，让工作台反馈真实失败原因。 */
export class TrainingProcessApiError extends Error {
  constructor(public readonly code: number, message: string) {
    super(message);
    this.name = 'TrainingProcessApiError';
  }
}

function unwrap<T>(response: ApiResponse<T>): T {
  if (response.code !== 0) {
    throw new TrainingProcessApiError(response.code, response.message || '培训过程事实加载失败，请稍后重试。');
  }
  return response.data;
}

/** 按培训计划查询真实生成的线下场次。 */
export const getTrainingOfflineSessionPage = async (params: {
  pageNum: number;
  pageSize: number;
  'query.trainingPlanId': number;
}): Promise<{ list: TrainingOfflineSession[]; total: number }> =>
  unwrap(await request<ApiResponse<{ list: TrainingOfflineSession[]; total: number }>>({
    url: '/api/training/offline-sessions',
    method: 'GET',
    params
  }));

/** 按培训计划查询附件事实，附件本身不由本工作台伪造。 */
export const getTrainingAttachmentPage = async (params: {
  pageNum: number;
  pageSize: number;
  'query.trainingPlanId': number;
}): Promise<{ list: TrainingAttachment[]; total: number }> =>
  unwrap(await request<ApiResponse<{ list: TrainingAttachment[]; total: number }>>({
    url: '/api/training/attachments',
    method: 'GET',
    params
  }));

/** 按培训计划查询奖惩事实，保持过程记录与 M3 结果相互独立。 */
export const getTrainingRewardPenaltyPage = async (params: {
  pageNum: number;
  pageSize: number;
  'query.trainingPlanId': number;
}): Promise<{ list: TrainingRewardPenalty[]; total: number }> =>
  unwrap(await request<ApiResponse<{ list: TrainingRewardPenalty[]; total: number }>>({
    url: '/api/training/reward-penalties',
    method: 'GET',
    params
  }));

/** 按奖惩记录读取当前机构可见的佐证原始文件。 */
export const getTrainingRewardEvidence = async (id: number): Promise<Blob> => {
  try {
    return await request<Blob>({ url: `/api/training/reward-penalties/${id}/evidence`, method: 'GET', responseType: 'blob' });
  } catch {
    throw new Error('佐证文件不存在或无法读取，请刷新后重试。');
  }
};

/** 按资料记录读取当前机构可见的原始文件，图片与视频按原始类型返回。 */
export const getTrainingAttachmentFile = async (id: number): Promise<Blob> => {
  try {
    return await request<Blob>({ url: `/api/training/attachments/${id}/file`, method: 'GET', responseType: 'blob' });
  } catch {
    throw new Error('培训资料文件不存在或无法读取，请刷新后重试。');
  }
};

/** 在执行中的计划内新增培训资料；不允许手工填写文件主键。 */
export const createTrainingAttachment = async (
  data: TrainingAttachmentPayload & { trainingPlanId: number }
): Promise<number> => unwrap(await request<ApiResponse<number>>({
  url: '/api/training/attachments', method: 'POST', data
}));

/** 按已读取版本编辑培训资料。 */
export const updateTrainingAttachment = async (
  id: number, data: TrainingAttachmentPayload & { version: number }
): Promise<void> => { unwrap(await request<ApiResponse<null>>({
  url: `/api/training/attachments/${id}`, method: 'PUT', data
})); };

/** 删除计划内培训资料。 */
export const deleteTrainingAttachment = async (id: number): Promise<void> => { unwrap(await request<ApiResponse<null>>({
  url: `/api/training/attachments/${id}`, method: 'DELETE'
})); };

/** 在执行中的计划内新增奖惩记录。 */
export const createTrainingRewardPenalty = async (
  data: TrainingRewardPenaltyPayload & { trainingPlanId: number; trainingParticipantId: number }
): Promise<number> => unwrap(await request<ApiResponse<number>>({
  url: '/api/training/reward-penalties', method: 'POST', data
}));

/** 按已读取版本编辑奖惩记录。 */
export const updateTrainingRewardPenalty = async (
  id: number, data: TrainingRewardPenaltyPayload & { version: number }
): Promise<void> => { unwrap(await request<ApiResponse<null>>({
  url: `/api/training/reward-penalties/${id}`, method: 'PUT', data
})); };

/** 删除计划内奖惩记录，结果由服务端同步。 */
export const deleteTrainingRewardPenalty = async (id: number): Promise<void> => { unwrap(await request<ApiResponse<null>>({
  url: `/api/training/reward-penalties/${id}`, method: 'DELETE'
})); };
