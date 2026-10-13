import { examClientInfo } from '@shared/libs/exam-client-info';
import type { ApiResponse } from '@shared/types';
import { request } from './request';

export type TrainingContentType = 'COURSE' | 'EXAM' | 'QUESTION_BANK' | 'OFFLINE';
export type TrainingLearnerPlanStatus = 'RUNNING' | 'ENDED';

/** 当前登录学员可见的培训任务摘要。 */
export interface TrainingLearnerTask {
  trainingParticipantId: number;
  trainingPlanId: number;
  /** 培训类型 ID，仅作业务标识，不能用于推导学员封面路径。 */
  trainingTypeId?: number | null;
  planName: string;
  startTime?: string;
  endTime?: string;
  publishStatus: TrainingLearnerPlanStatus;
  participantStatus: string;
  resultStatus?: 'PASSED' | 'FAILED' | 'INCOMPLETE';
  /** 学员可见培训计划的受控封面相对路径；为空时展示占位。 */
  coverUrl?: string | null;
}

/** 学员详情中的计划内容快照。 */
export interface TrainingLearnerContent {
  planContentId: number;
  contentType: TrainingContentType;
  contentName: string;
  isRequired: 0 | 1;
  contentStatus?: string;
  requiredHours?: number;
}

/** M3 内容结果明细，仅展示后端返回的状态与参考分。 */
export interface TrainingLearnerResultItem {
  planContentId: number;
  contentName: string;
  resultStatus: string;
  /** 执行实例 ID；OFFLINE 内容为本人可查看的线下场次 ID。 */
  executionId?: number | null;
  /** 结果事实来源；课程内容为 COURSE_ENROLLMENT。 */
  sourceRecordType?: string | null;
  /** 来源记录 ID；课程内容为本人课程报名 ID。 */
  sourceRecordId?: number | null;
  /** 已完成考试或练习的最终答卷 ID；结果页必须使用它回看原始作答。 */
  finalAttemptId?: number | null;
  /** 线下场次当前状态；仅线下内容返回，用于展示教师推进进度。 */
  sessionStatus?: string | null;
  score?: number | null;
  /** 该内容已获得学时，单位秒；与内容安排学时分开。 */
  effectiveDurationSeconds?: number | null;
}

/** 学员培训详情；计划状态决定是否仍可进入具体培训内容。 */
export interface TrainingLearnerParticipantDetail {
  participant?: {
    trainingParticipantId: number;
    participantStatus: string;
    /** 已获得线上学时，单位小时。 */
    onlineHours?: number | null;
    /** 已获得线下学时，单位小时。 */
    offlineHours?: number | null;
  };
  plan?: { trainingPlanId: number; planName: string; publishStatus: TrainingLearnerPlanStatus };
  contents: TrainingLearnerContent[];
  results: TrainingLearnerResultItem[];
}

/** 统一进入内容的请求，不允许携带 userId、examId 等上下文字段。 */
export interface TrainingLearnerEnterPayload {
  clientAttemptToken?: string;
  platform?: 'PC' | 'MOBILE';
  operatingSystem?: string;
  browser?: string;
  clientIp?: string;
}

/** 当前学员本人可见的线下场次及其成绩投影。 */
export interface TrainingLearnerOfflineSession {
  offlineSessionId: number;
  trainingPlanId: number;
  trainingParticipantId: number;
  planContentId: number;
  sessionName: string;
  location?: string | null;
  startTime?: string | null;
  endTime?: string | null;
  sessionStatus?: string | null;
  score?: number | null;
  scoredTime?: string | null;
  resultStatus: string;
  /** 已获得线下学时，单位秒。 */
  effectiveDurationSeconds: number;
}

/** 统一进入内容返回的唯一跳转依据。 */
export interface TrainingLearnerEnterResult {
  contentType: TrainingContentType;
  courseEnrollmentId?: number;
  examAttemptId?: number;
  offlineSessionId?: number;
  sessionName?: string;
  location?: string;
  startTime?: string;
  endTime?: string;
}

/** 培训理论考试的只读须知快照；读取时不创建或恢复作答。 */
export interface TrainingLearnerExamNotice {
  examId: number;
  examName: string;
  examType: string;
  openStartTime?: string | null;
  openEndTime?: string | null;
  durationSeconds?: number | null;
  passRatio?: number | null;
  passScore?: number | null;
  isNoticeEnabled?: 0 | 1 | null;
  noticeContent?: string | null;
}

/** 保留后端业务码和中文消息，以便学员页展示真实失败反馈。 */
export class TrainingLearnerApiError extends Error {
  constructor(public readonly code: number, message: string) {
    super(message);
    this.name = 'TrainingLearnerApiError';
  }
}

function unwrap<T>(response: ApiResponse<T>): T {
  if (response.code !== 0) throw new TrainingLearnerApiError(response.code, response.message || '培训任务请求失败，请稍后重试。');
  return response.data;
}

/** 查询当前登录学员本人培训任务，不接受任何用户筛选参数。 */
export const getTrainingLearnerTasks = async (): Promise<TrainingLearnerTask[]> =>
  unwrap(await request<ApiResponse<TrainingLearnerTask[]>>({ url: '/api/training/learner/tasks', method: 'GET' }));

/** 通过任务返回的受控路径读取培训封面，不由培训类型 ID 推导访问地址。 */
export const getTrainingLearnerCover = (coverUrl: string): Promise<Blob> =>
  request<Blob>({ url: coverUrl, method: 'GET', responseType: 'blob' });

/** 查询当前登录学员本人的培训详情。 */
export const getTrainingLearnerParticipantDetail = async (trainingParticipantId: number): Promise<TrainingLearnerParticipantDetail> =>
  unwrap(await request<ApiResponse<TrainingLearnerParticipantDetail>>({ url: `/api/training/learner/participants/${trainingParticipantId}`, method: 'GET' }));

/** 查询当前登录学员本人可见的线下场次，不调用管理端场次接口。 */
export const getTrainingLearnerOfflineSession = async (offlineSessionId: number): Promise<TrainingLearnerOfflineSession> =>
  unwrap(await request<ApiResponse<TrainingLearnerOfflineSession>>({
    url: `/api/training/learner/offline-sessions/${offlineSessionId}`,
    method: 'GET'
  }));

/** 读取培训理论考试须知，不改变考试或作答状态。 */
export const getTrainingLearnerExamNotice = async (
  trainingParticipantId: number,
  planContentId: number
): Promise<TrainingLearnerExamNotice> =>
  unwrap(await request<ApiResponse<TrainingLearnerExamNotice>>({
    url: `/api/training/learner/participants/${trainingParticipantId}/contents/${planContentId}/exam-notice`,
    method: 'GET'
  }));

/** 按培训上下文统一进入课程、考试、题库练习或线下场次。 */
export const enterTrainingContent = async (
  trainingParticipantId: number,
  planContentId: number,
  data?: TrainingLearnerEnterPayload
): Promise<TrainingLearnerEnterResult> =>
  unwrap(await request<ApiResponse<TrainingLearnerEnterResult>>({
    url: `/api/training/learner/participants/${trainingParticipantId}/contents/${planContentId}/enter`,
    method: 'POST',
    data: data ? { ...data, ...examClientInfo('PC', typeof navigator === 'undefined' ? '' : navigator.userAgent) } : data
  }));

/**
 * 学员可读的培训资料只读项。
 * <p>后端只下发本人参训计划下启用的一般资料，**不返回文件主键**；原件按资料记录经受控端点读取。
 */
export interface TrainingLearnerMaterial {
  trainingAttachmentId: number;
  attachmentName: string;
  description?: string | null;
  planStageId?: number | null;
  stageName?: string | null;
  sortOrder?: number | null;
  /** 是否已关联可读文件；为 false 时不提供预览入口。 */
  hasFile?: boolean;
}

/** 查询当前登录学员本人参训计划下可读的一般资料，不接受 userId 参数。 */
export const getTrainingLearnerMaterials = async (trainingParticipantId: number): Promise<TrainingLearnerMaterial[]> =>
  unwrap(await request<ApiResponse<TrainingLearnerMaterial[]>>({
    url: `/api/training/learner/participants/${trainingParticipantId}/materials`,
    method: 'GET'
  }));

/** 按资料记录受控读取学员可见的资料原件，供在线预览；学员侧不提供下载。 */
export const getTrainingLearnerMaterialFile = (
  trainingParticipantId: number,
  trainingAttachmentId: number
): Promise<Blob> =>
  request<Blob>({
    url: `/api/training/learner/participants/${trainingParticipantId}/materials/${trainingAttachmentId}/file`,
    method: 'GET',
    responseType: 'blob'
  });
