import { examClientInfo } from '@shared/libs/exam-client-info';
import type { ApiResponse } from '@shared/types';
import { request } from './request';

export type TrainingContentType = 'COURSE' | 'EXAM' | 'QUESTION_BANK' | 'OFFLINE';
export type TrainingLearnerPlanStatus = 'RUNNING' | 'ENDED';
export interface TrainingLearnerTask {
  trainingParticipantId: number;
  trainingPlanId: number;
  /** 培训类型业务标识，不用于推导学员封面访问路径。 */
  trainingTypeId?: number | null;
  /** 培训类型名称；类型不存在或已停用时为空，页面提示名称待补全。 */
  trainingTypeName?: string | null;
  planName: string;
  /** 学员可见培训计划的受控封面相对路径；为空时页面展示占位。 */
  coverUrl?: string | null;
  startTime?: string;
  endTime?: string;
  publishStatus: TrainingLearnerPlanStatus;
  participantStatus: string;
  resultStatus?: 'PASSED' | 'FAILED' | 'INCOMPLETE';
}
export interface TrainingLearnerContent {
  planContentId: number;
  contentType: TrainingContentType;
  contentName: string;
  isRequired: 0 | 1;
  contentStatus?: string;
  requiredHours?: number;
}
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
  /** 线下场次当前状态，仅 OFFLINE 内容返回，不替代学员个人完成状态。 */
  sessionStatus?: string | null;
  /** 已完成考试或练习的最终答卷 ID；结果页必须使用它回看原始作答。 */
  finalAttemptId?: number | null;
  score?: number | null;
  /** 该内容已获得学时，单位秒；考试与题库练习为 0。 */
  effectiveDurationSeconds?: number | null;
}
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
export interface TrainingLearnerEnterPayload {
  clientAttemptToken?: string;
  platform?: 'PC' | 'MOBILE';
  operatingSystem?: string;
  browser?: string;
  clientIp?: string;
}

/** 当前登录学员本人可见的线下场次及其成绩投影。 */
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

/**
 * 学员可读的培训资料只读项。
 *
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

/** 保留后端业务码，供培训入口展示可理解的失败反馈。 */
export class TrainingLearnerApiError extends Error {
  constructor(public readonly code: number, message: string) {
    super(message);
    this.name = 'TrainingLearnerApiError';
  }
}

/** 解包真实 Result，页面可将后端权限、状态和业务错误原样反馈给学员。 */
function unwrap<T>(response: ApiResponse<T>): T {
  if (response.code !== 0) throw new TrainingLearnerApiError(response.code, response.message || '培训任务请求失败，请稍后重试。');
  return response.data;
}
/** 查询当前登录学员本人培训任务。 */
export const getTrainingLearnerTasks = async (): Promise<TrainingLearnerTask[]> =>
  unwrap(await request<ApiResponse<TrainingLearnerTask[]>>({ url: '/api/training/learner/tasks', method: 'GET' }));
/** 查询当前登录学员本人培训详情。 */
export const getTrainingLearnerParticipantDetail = async (trainingParticipantId: number): Promise<TrainingLearnerParticipantDetail> =>
  unwrap(
    await request<ApiResponse<TrainingLearnerParticipantDetail>>({
      url: `/api/training/learner/participants/${trainingParticipantId}`,
      method: 'GET'
    })
  );

/** 查询当前登录学员本人的线下场次，不调用管理端场次接口。 */
export const getTrainingLearnerOfflineSession = async (offlineSessionId: number): Promise<TrainingLearnerOfflineSession> =>
  unwrap(
    await request<ApiResponse<TrainingLearnerOfflineSession>>({
      url: `/api/training/learner/offline-sessions/${offlineSessionId}`,
      method: 'GET'
    })
  );

/** 读取培训理论考试须知，不改变考试或作答状态。 */
export const getTrainingLearnerExamNotice = async (
  trainingParticipantId: number,
  planContentId: number
): Promise<TrainingLearnerExamNotice> =>
  unwrap(await request<ApiResponse<TrainingLearnerExamNotice>>({
    url: `/api/training/learner/participants/${trainingParticipantId}/contents/${planContentId}/exam-notice`,
    method: 'GET'
  }));

/** 使用培训上下文统一进入内容，不传入 userId、examId 或执行实例 ID。 */
export const enterTrainingContent = async (
  trainingParticipantId: number,
  planContentId: number,
  data?: TrainingLearnerEnterPayload
): Promise<TrainingLearnerEnterResult> =>
  unwrap(
    await request<ApiResponse<TrainingLearnerEnterResult>, TrainingLearnerEnterPayload>({
      url: `/api/training/learner/participants/${trainingParticipantId}/contents/${planContentId}/enter`,
      method: 'POST',
      data: data ? { ...data, ...examClientInfo('MOBILE', typeof navigator === 'undefined' ? '' : navigator.userAgent) } : data
    })
  );

/** 查询当前登录学员本人参训计划下可读的一般资料，不接受 userId 参数。 */
export const getTrainingLearnerMaterials = async (trainingParticipantId: number): Promise<TrainingLearnerMaterial[]> =>
  unwrap(
    await request<ApiResponse<TrainingLearnerMaterial[]>>({
      url: `/api/training/learner/participants/${trainingParticipantId}/materials`,
      method: 'GET'
    })
  );

/** 构造学员侧资料原件的受控读取入口，不暴露存储服务地址。 */
export const getTrainingLearnerMaterialFileUrl = (trainingParticipantId: number, trainingAttachmentId: number): string =>
  `/api/training/learner/participants/${trainingParticipantId}/materials/${trainingAttachmentId}/file`;

/**
 * 按资料记录受控读取学员可见的资料原件字节，供页面本地预览。
 *
 * <p>返回字节而非受控地址：H5 端 `image` 标签不会携带 Bearer，页面统一用本地 Blob/字节渲染，
 * 不持有可直连地址；学员侧不提供下载，因此没有下载分支。
 */
export const getTrainingLearnerMaterialFile = async (
  trainingParticipantId: number,
  trainingAttachmentId: number
): Promise<ArrayBuffer> =>
  request<ArrayBuffer>({
    url: getTrainingLearnerMaterialFileUrl(trainingParticipantId, trainingAttachmentId),
    method: 'GET',
    responseType: 'arraybuffer'
  });
