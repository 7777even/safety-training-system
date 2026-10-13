import type {ApiResponse, LearnerExamAttempt, SubmitLearnerExamAttemptPayload} from '@shared/types';
import * as gradingMock from '@/mock/exam-grading';
import {request} from './request';

/** 主观题批阅请求，与 ReviewAnswerRequest 一致。 */
export interface ReviewExamAnswerPayload {
  afterScore: number;
  reviewComment?: string;
  reviewAction?: string;
}

/** 练习重置请求，与 PracticeResetRequest 一致。 */
export interface PracticeResetPayload {
  resetScope: string;
  reason?: string;
}

/** 教师批阅任务状态。 */
export type ExamReviewTaskStatus = 'PENDING' | 'REVIEWED';

/** 教师名下单道主观题批阅任务，与 ExamReviewTaskVO 一致。 */
export interface ExamReviewTask {
  assessmentExecutionId: number;
  sourceType: string;
  sourceBusinessId: number;
  sourceName: string;
  examId: number;
  examName: string;
  examCandidateId: number;
  examAttemptId: number;
  attemptNo: number;
  attemptStatus: string;
  examAnswerId: number;
  attemptQuestionId: number;
  questionType: string;
  stemSnapshot: string;
  score: number;
  answerText: string | null;
  learnerUserId: number;
  learnerName: string;
  learnerCode: string | null;
  learnerOrganizationId: number;
  learnerOrganizationName: string;
  submittedTime: string | null;
  reviewStatus: ExamReviewTaskStatus;
  latestManualScore: number | null;
  latestReviewComment: string | null;
  latestReviewedTime: string | null;
  scorePublishStatus: 'UNPUBLISHED' | 'PUBLISHED' | 'EFFECTIVE';
  scorePublishedTime: string | null;
}

/** 考试作答与成绩记录，与 ExamAttemptRecordVO 一致。 */
export interface ExamAttemptRecord {
  assessmentExecutionId: number;
  sourceType: string;
  sourceBusinessId: number;
  sourceName: string;
  examId: number;
  examName: string;
  examCandidateId: number;
  examAttemptId: number;
  attemptNo: number;
  attemptType: string;
  attemptStatus: string;
  learnerUserId: number;
  learnerName: string;
  learnerCode: string | null;
  learnerOrganizationId: number;
  learnerOrganizationName: string;
  startedTime: string;
  submittedTime: string | null;
  usedSeconds: number;
  reviewStatus: 'NOT_REQUIRED' | 'PENDING' | 'COMPLETED';
  reviewCompletedTime: string | null;
  objectiveScore: number | null;
  subjectiveScore: number | null;
  finalScore: number | null;
  isPassed: 0 | 1 | null;
  scorePublishStatus: 'UNPUBLISHED' | 'PUBLISHED' | 'EFFECTIVE';
  scorePublishedTime: string | null;
  /** 课程考核自动生效时间；独立考试记录固定为空。 */
  resultEffectiveTime?: string | null;
}

/** Contract 通用分页结果。 */
export interface ExamGradingPage<T> {
  list: T[];
  total: number;
}

/** 教师批阅任务分页查询参数。 */
export interface ExamReviewTaskPageQuery {
  pageNum: number;
  pageSize: number;
  'query.examId'?: number;
  'query.reviewStatus'?: ExamReviewTaskStatus;
  'query.learnerKeyword'?: string;
  'query.submittedStartTime'?: string;
  'query.submittedEndTime'?: string;
}

/** 考试成绩记录分页查询参数。 */
export interface ExamAttemptRecordPageQuery {
  pageNum: number;
  pageSize: number;
  'query.examId'?: number;
  'query.attemptStatus'?: string;
  'query.reviewStatus'?: ExamAttemptRecord['reviewStatus'];
  'query.scorePublishStatus'?: ExamAttemptRecord['scorePublishStatus'];
  'query.learnerKeyword'?: string;
  'query.submittedStartTime'?: string;
  'query.submittedEndTime'?: string;
}

/** 作答过程中的反作弊审计事件。 */
export interface ExamAnticheatEvent {
  anticheatEventId: number;
  examAttemptId: number;
  eventType: string;
  eventTime: string;
  eventCount: number;
  actionTaken?: string;
  evidenceFileId?: number;
  clientPayloadJson?: string;
}

/** 正式成绩发布流水。 */
export interface ExamScorePublishLog {
  scorePublishLogId: number;
  examId: number;
  examAttemptId: number;
  publishedBy: number;
  publishedTime: string;
  publishType: 'MANUAL' | 'AUTO';
  scoreSnapshot: number;
  resultSnapshot?: string | null;
}

/** Contract 业务失败，页面必须显示真实失败而非本地伪造成功。 */
export class ExamGradingApiError extends Error {
  constructor(public readonly code: number, message: string) {
    super(message);
    this.name = 'ExamGradingApiError';
  }
}

/** 教师批阅 Mock 开关；TODO replace real api：真实接口联调后设为 false。 */
export function isExamGradingMockEnabled(): boolean {
  const override = (globalThis as { __EXAM_GRADING_MOCK_ENABLED__?: boolean }).__EXAM_GRADING_MOCK_ENABLED__;
  return override ?? import.meta.env.VITE_USE_API_MOCK !== 'false';
}

/** 解包 Result 响应。 */
function unwrap<T>(response: ApiResponse<T>): T {
  if (response.code !== 0) throw new ExamGradingApiError(response.code, response.message || '请求失败，请稍后重试。');
  return response.data;
}

/** 统一 Mock 与真实请求分支。 */
async function selectSource<T>(mockAction: () => Promise<ApiResponse<T>>, realAction: () => Promise<ApiResponse<T>>): Promise<T> {
  return unwrap(isExamGradingMockEnabled() ? await mockAction() : await realAction());
}

/** 分页读取当前教师有权批阅的主观题任务。 */
export const getExamReviewTaskPage = (params: ExamReviewTaskPageQuery): Promise<ExamGradingPage<ExamReviewTask>> => selectSource(
  () => gradingMock.mockGetExamReviewTaskPage(params),
  () => request<ApiResponse<ExamGradingPage<ExamReviewTask>>>({url: '/api/exam/review-tasks', method: 'GET', params})
);
/** 分页读取有权限考试范围内的作答与成绩记录。 */
export const getExamAttemptRecordPage = (params: ExamAttemptRecordPageQuery): Promise<ExamGradingPage<ExamAttemptRecord>> => selectSource(
  () => gradingMock.mockGetExamAttemptRecordPage(params),
  () => request<ApiResponse<ExamGradingPage<ExamAttemptRecord>>>({url: '/api/exam/attempts', method: 'GET', params})
);
/** 读取有 `exam:attempt:list` 权限的作答快照。 */
export const getReviewAttempt = (examAttemptId: number): Promise<LearnerExamAttempt> => selectSource(() => gradingMock.mockGetReviewAttempt(examAttemptId), () => request<ApiResponse<LearnerExamAttempt>>({
  url: `/api/exam/attempts/${examAttemptId}`,
  method: 'GET'
}));
/** 提交单道主观题的人工批阅。 */
export const reviewExamAnswer = (examAnswerId: number, data: ReviewExamAnswerPayload): Promise<void> => selectSource(() => gradingMock.mockReviewExamAnswer(examAnswerId, data), () => request<ApiResponse<void>>({
  url: `/api/exam/answers/${examAnswerId}/review`,
  method: 'POST',
  data
}));
/** 正式发布一份已完成评分的作答成绩。 */
export const publishExamScore = (examAttemptId: number, publishedBy?: number): Promise<void> => selectSource(() => gradingMock.mockPublishExamScore(examAttemptId, publishedBy), () => request<ApiResponse<void>>({
  url: `/api/exam/attempts/${examAttemptId}/publish-score`,
  method: 'POST',
  params: publishedBy ? {publishedBy} : undefined
}));
/** 读取作答期间追加记录的反作弊事件。 */
export const getExamAnticheatEvents = (examAttemptId: number): Promise<ExamAnticheatEvent[]> => selectSource(
  () => gradingMock.mockGetExamAnticheatEvents(examAttemptId),
  () => request<ApiResponse<ExamAnticheatEvent[]>>({
    url: `/api/exam/attempts/${examAttemptId}/anticheat-events`,
    method: 'GET'
  })
);
/** 读取历次正式成绩发布流水。 */
export const getExamScorePublishLogs = (examAttemptId: number): Promise<ExamScorePublishLog[]> => selectSource(
  () => gradingMock.mockGetExamScorePublishLogs(examAttemptId),
  () => request<ApiResponse<ExamScorePublishLog[]>>({
    url: `/api/exam/attempts/${examAttemptId}/score-publish-logs`,
    method: 'GET'
  })
);
/** 为指定考生开启补考尝试。 */
export const createRetakeAttempt = (examCandidateId: number, clientAttemptToken: string): Promise<LearnerExamAttempt> => selectSource(() => gradingMock.mockCreateRetakeAttempt(examCandidateId, clientAttemptToken), () => request<ApiResponse<LearnerExamAttempt>>({
  url: `/api/exam/candidates/${examCandidateId}/retake`,
  method: 'POST',
  params: {clientAttemptToken}
}));
/** 重置指定考生的练习记录。 */
export const resetPractice = (examCandidateId: number, data: PracticeResetPayload): Promise<void> => selectSource(() => gradingMock.mockResetPractice(examCandidateId, data), () => request<ApiResponse<void>>({
  url: `/api/exam/candidates/${examCandidateId}/practice-reset`,
  method: 'POST',
  data
}));

export type {SubmitLearnerExamAttemptPayload};
