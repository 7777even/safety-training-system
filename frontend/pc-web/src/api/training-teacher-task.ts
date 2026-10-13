import type { ApiResponse } from '@shared/types';
import { request } from './request';

/** 当前教师负责的线下场次摘要。 */
export interface TrainingTeacherTask {
  offlineSessionId: number;
  trainingPlanId: number;
  /** 所属培训计划的当前名称；计划资料缺失时为空。 */
  trainingPlanName?: string | null;
  sessionName: string;
  location?: string | null;
  startTime?: string | null;
  endTime?: string | null;
  sessionStatus: string;
  /** 场次状态流转的乐观锁版本，须由教师任务接口原样返回。 */
  version: number;
  participantCount: number;
  scoredCount: number;
  pendingScoreCount: number;
}

/** 教师场次详情中的应到参训人及其唯一成绩。 */
export interface TrainingTeacherRosterItem {
  trainingParticipantId: number;
  userId: number;
  /** 学员展示姓名；用户资料缺失时为空。 */
  userName?: string | null;
  score?: number | null;
  maxScore?: number | null;
  scoredTime?: string | null;
  resultStatus?: string | null;
}

/** 教师仅可对后端返回的名单录入线下成绩。 */
export interface TrainingTeacherTaskDetail extends TrainingTeacherTask {
  roster: TrainingTeacherRosterItem[];
  availableActions: string[];
}

/** 一场一人唯一的线下成绩记录。 */
export interface TrainingOfflineScore {
  offlineScoreId: number;
  trainingParticipantId: number;
  offlineSessionId: number;
  scoreName: string;
  score: number;
  maxScore: number;
  scoreSource: 'MANUAL' | 'IMPORT';
  scoredTime: string;
  version: number;
}

export interface TrainingOfflineScorePayload {
  trainingParticipantId: number;
  offlineSessionId: number;
  scoreName: string;
  score: number;
  maxScore: number;
  scoreSource: 'MANUAL';
  scoredTime: string;
}

/** 保留后端业务码，教师页面直接显示实际失败原因。 */
export class TrainingTeacherTaskApiError extends Error { constructor(public readonly code: number, message: string) { super(message); this.name = 'TrainingTeacherTaskApiError'; } }
function unwrap<T>(response: ApiResponse<T>): T { if (response.code !== 0) throw new TrainingTeacherTaskApiError(response.code, response.message || '教师任务操作失败，请稍后重试。'); return response.data; }

export const getTeacherTaskPage = async (params: { pageNum: number; pageSize: number }): Promise<{ list: TrainingTeacherTask[]; total: number }> =>
  unwrap(await request<ApiResponse<{ list: TrainingTeacherTask[]; total: number }>>({ url: '/api/training/teacher-tasks', method: 'GET', params }));

export const getTeacherTaskDetail = async (offlineSessionId: number): Promise<TrainingTeacherTaskDetail> =>
  unwrap(await request<ApiResponse<TrainingTeacherTaskDetail>>({ url: `/api/training/teacher-tasks/${offlineSessionId}`, method: 'GET' }));

/** 开始教师本人负责的已安排线下场次。 */
export const startTeacherOfflineSession = async (offlineSessionId: number, version: number): Promise<void> =>
  unwrap(await request<ApiResponse<void>>({ url: `/api/training/offline-sessions/${offlineSessionId}/start`, method: 'POST', data: { version } }));

/** 结束教师本人负责且已进行中的线下场次。 */
export const completeTeacherOfflineSession = async (offlineSessionId: number, version: number): Promise<void> =>
  unwrap(await request<ApiResponse<void>>({ url: `/api/training/offline-sessions/${offlineSessionId}/complete`, method: 'POST', data: { version } }));

/** 取消教师本人负责的已安排或进行中线下场次。 */
export const cancelTeacherOfflineSession = async (offlineSessionId: number, version: number): Promise<void> =>
  unwrap(await request<ApiResponse<void>>({ url: `/api/training/offline-sessions/${offlineSessionId}/cancel`, method: 'POST', data: { version } }));

/** 保存教师对本场名单中某一参训人的唯一线下成绩。 */
export const upsertOfflineScore = async (data: TrainingOfflineScorePayload): Promise<TrainingOfflineScore> =>
  unwrap(await request<ApiResponse<TrainingOfflineScore>>({ url: '/api/training/offline-scores', method: 'POST', data }));
