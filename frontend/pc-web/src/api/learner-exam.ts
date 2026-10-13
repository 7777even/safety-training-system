import { examClientInfo } from '@shared/libs/exam-client-info';
import type {
  ApiResponse,
  LearnerExamAttempt,
  LearnerExamNotice,
  LearnerExamTaskPage,
  LearnerExamTaskQuery,
  SaveLearnerExamAnswersPayload,
  StartLearnerExamAttemptPayload,
  SubmitLearnerExamAttemptPayload
} from '@shared/types';
import * as learnerExamMock from '@/mock/learner-exam';
import { request } from './request';

/** 作答期间记录的最小反作弊事件，与 AnticheatEventRequest 保持一致。 */
export interface RecordExamAnticheatEventPayload {
  eventType: string;
  eventTime: string;
  eventCount: number;
  actionTaken: string;
  clientPayloadJson?: string;
}

/** 学员考试接口业务错误。 */
export class LearnerExamApiError extends Error {
  constructor(
    public readonly code: number,
    message: string
  ) {
    super(message);
    this.name = 'LearnerExamApiError';
  }
}

/** 学员考试 Mock 开关；TODO replace real api：真实接口联调后设为 false。 */
export function isLearnerExamMockEnabled(): boolean {
  const override = (globalThis as { __LEARNER_EXAM_MOCK_ENABLED__?: boolean }).__LEARNER_EXAM_MOCK_ENABLED__;
  return override ?? import.meta.env.VITE_USE_API_MOCK !== 'false';
}

/** 解包统一 Result，避免页面将失败误显示为成功。 */
function unwrap<T>(response: ApiResponse<T>): T {
  if (response.code !== 0) throw new LearnerExamApiError(response.code, response.message || '请求失败，请稍后重试。');
  return response.data;
}

/** 在 Mock 和真实接口间切换，同时保持统一失败语义。 */
async function selectSource<T>(mockAction: () => Promise<ApiResponse<T>>, realAction: () => Promise<ApiResponse<T>>): Promise<T> {
  return unwrap(isLearnerExamMockEnabled() ? await mockAction() : await realAction());
}

/** 分页读取当前登录学员的考试任务。 */
export const getLearnerExamTaskPage = (params: LearnerExamTaskQuery): Promise<LearnerExamTaskPage> =>
  selectSource(
    () => learnerExamMock.mockGetLearnerExamTaskPage(params),
    () => request<ApiResponse<LearnerExamTaskPage>>({ url: '/api/exam/learner/tasks', method: 'GET', params })
  );

/** 只读取本机构本人考生须知，普通学员无需管理详情权限。 */
export async function getLearnerExamNotice(examId: number): Promise<LearnerExamNotice> {
  return unwrap(await request<ApiResponse<LearnerExamNotice>>({ url: `/api/exam/learner/exams/${examId}/notice`, method: 'GET' }));
}

/** 读取任务返回的受控考试封面；无封面时由调用方展示占位。 */
export const getLearnerExamCover = (coverUrl: string): Promise<Blob> => {
  if (isLearnerExamMockEnabled()) {
    return Promise.resolve(new Blob([
      '<svg xmlns="http://www.w3.org/2000/svg" width="640" height="240"><rect width="100%" height="100%" fill="#eff6ff"/><path d="M264 82h112v76H264z" fill="#bfdbfe"/><path d="M282 102h76M282 122h58M282 142h68" stroke="#2563eb" stroke-width="8" stroke-linecap="round"/></svg>'
    ], { type: 'image/svg+xml' }));
  }
  return request<Blob>({ url: coverUrl, method: 'GET', responseType: 'blob' });
};

/** 开始新的考试作答尝试。 */
export const startExamAttempt = (data: StartLearnerExamAttemptPayload): Promise<LearnerExamAttempt> =>
  selectSource(
    () => learnerExamMock.mockStartExamAttempt(data),
    () => request<ApiResponse<LearnerExamAttempt>>({ url: '/api/exam/attempts/start', method: 'POST', data: { ...data, ...examClientInfo('PC', typeof navigator === 'undefined' ? '' : navigator.userAgent) } })
  );
/** 恢复当前学员本人可访问的作答快照。 */
export const getLearnerExamAttempt = (examAttemptId: number): Promise<LearnerExamAttempt> =>
  selectSource(
    () => learnerExamMock.mockGetLearnerExamAttempt(examAttemptId),
    () => request<ApiResponse<LearnerExamAttempt>>({ url: `/api/exam/learner/attempts/${examAttemptId}`, method: 'GET' })
  );
/** 暂存当前作答，失败时由页面提示而不伪造保存成功。 */
export const saveExamAnswers = (examAttemptId: number, data: SaveLearnerExamAnswersPayload): Promise<void> =>
  selectSource(
    () => learnerExamMock.mockSaveExamAnswers(examAttemptId, data),
    () => request<ApiResponse<void>>({ url: `/api/exam/attempts/${examAttemptId}/answers`, method: 'PUT', data })
  );
/** 手动交卷。 */
export const submitExamAttempt = (examAttemptId: number, data: SubmitLearnerExamAttemptPayload): Promise<void> =>
  selectSource(
    () => learnerExamMock.mockSubmitExamAttempt(examAttemptId, data),
    () => request<ApiResponse<void>>({ url: `/api/exam/attempts/${examAttemptId}/submit`, method: 'POST', data })
  );
/** 倒计时归零后的超时交卷。 */
export const timeoutSubmitExamAttempt = (examAttemptId: number, data: SubmitLearnerExamAttemptPayload): Promise<void> =>
  selectSource(
    () => learnerExamMock.mockTimeoutSubmitExamAttempt(examAttemptId, data),
    () => request<ApiResponse<void>>({ url: `/api/exam/attempts/${examAttemptId}/timeout-submit`, method: 'POST', data })
  );
/** 追加作答端采集到的最小反作弊事件，不影响原有答题和交卷流程。 */
export const recordExamAnticheatEvent = (examAttemptId: number, data: RecordExamAnticheatEventPayload): Promise<void> =>
  selectSource(
    () => learnerExamMock.mockRecordExamAnticheatEvent(examAttemptId, data),
    () => request<ApiResponse<void>>({ url: `/api/exam/attempts/${examAttemptId}/anticheat-events`, method: 'POST', data })
  );
