import { examClientInfo } from '@shared/libs/exam-client-info';
import type {
  ApiResponse,
  LearnerExamAttempt,
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

/** 默认调用线上真实接口，仅显式开启时保留本地 Mock。 */
const USE_API_MOCK = import.meta.env.VITE_USE_API_MOCK === 'true';

/** 移动端考前须知所需的既有 ExamVO 字段子集。 */
export interface LearnerExamNotice {
  examId: number;
  examName: string;
  examType: string;
  openStartTime?: string | null;
  openEndTime?: string | null;
  durationSeconds: number;
  passRatio: number;
  passScore: number;
  isNoticeEnabled?: 0 | 1;
  noticeContent?: string;
}

/** 解包 Result，接口失败时由调用页面显示真实中文反馈。 */
function unwrap<T>(response: ApiResponse<T>): T {
  if (response.code !== 0) throw new Error(response.message || '请求失败，请稍后重试。');
  return response.data;
}

/** 查询当前登录学员的考试任务；状态筛选与 PC 考试中心共用同一分页 Contract。 */
export async function getLearnerExamTaskPage(params: LearnerExamTaskQuery): Promise<LearnerExamTaskPage> {
  return unwrap(
    USE_API_MOCK
      ? await learnerExamMock.mockGetLearnerExamTaskPage(params)
      : await request<ApiResponse<LearnerExamTaskPage>>({ url: '/api/exam/learner/tasks', method: 'GET', params: { ...params } })
  );
}

/** 读取与 PC 考前须知一致的既有考试详情。 */
export async function getLearnerExamNotice(examId: number): Promise<LearnerExamNotice> {
  return unwrap(
    USE_API_MOCK
      ? await learnerExamMock.mockGetLearnerExamNotice(examId)
      : await request<ApiResponse<LearnerExamNotice>>({ url: `/api/exam/learner/exams/${examId}/notice`, method: 'GET' })
  );
}

/** 开始新的考试尝试。 */
export async function startExamAttempt(data: StartLearnerExamAttemptPayload): Promise<LearnerExamAttempt> {
  return unwrap(
    USE_API_MOCK
      ? await learnerExamMock.mockStartExamAttempt(data)
      : await request<ApiResponse<LearnerExamAttempt>, StartLearnerExamAttemptPayload>({
          url: '/api/exam/attempts/start',
          method: 'POST',
          data: { ...data, ...examClientInfo('H5', typeof navigator === 'undefined' ? '' : navigator.userAgent) }
        })
  );
}

/** 读取本人作答快照。 */
export async function getLearnerExamAttempt(examAttemptId: number): Promise<LearnerExamAttempt> {
  return unwrap(
    USE_API_MOCK
      ? await learnerExamMock.mockGetLearnerExamAttempt(examAttemptId)
      : await request<ApiResponse<LearnerExamAttempt>>({
          url: `/api/exam/learner/attempts/${examAttemptId}`,
          method: 'GET'
        })
  );
}

/** 暂存答案。 */
export async function saveExamAnswers(examAttemptId: number, data: SaveLearnerExamAnswersPayload): Promise<void> {
  return unwrap(
    USE_API_MOCK
      ? await learnerExamMock.mockSaveExamAnswers(examAttemptId, data)
      : await request<ApiResponse<void>, SaveLearnerExamAnswersPayload>({
          url: `/api/exam/attempts/${examAttemptId}/answers`,
          method: 'PUT',
          data
        })
  );
}

/** 手动交卷。 */
export async function submitExamAttempt(examAttemptId: number, data: SubmitLearnerExamAttemptPayload): Promise<void> {
  return unwrap(
    USE_API_MOCK
      ? await learnerExamMock.mockSubmitExamAttempt(examAttemptId, data)
      : await request<ApiResponse<void>, SubmitLearnerExamAttemptPayload>({
          url: `/api/exam/attempts/${examAttemptId}/submit`,
          method: 'POST',
          data
        })
  );
}

/** 超时交卷。 */
export async function timeoutSubmitExamAttempt(examAttemptId: number, data: SubmitLearnerExamAttemptPayload): Promise<void> {
  return unwrap(
    USE_API_MOCK
      ? await learnerExamMock.mockTimeoutSubmitExamAttempt(examAttemptId, data)
      : await request<ApiResponse<void>, SubmitLearnerExamAttemptPayload>({
          url: `/api/exam/attempts/${examAttemptId}/timeout-submit`,
          method: 'POST',
          data
        })
  );
}

/** 追加移动端采集到的最小反作弊事件，不干扰正常交卷。 */
export async function recordExamAnticheatEvent(examAttemptId: number, data: RecordExamAnticheatEventPayload): Promise<void> {
  return unwrap(
    USE_API_MOCK
      ? await learnerExamMock.mockRecordExamAnticheatEvent(examAttemptId, data)
      : await request<ApiResponse<void>, RecordExamAnticheatEventPayload>({
          url: `/api/exam/attempts/${examAttemptId}/anticheat-events`,
          method: 'POST',
          data
        })
  );
}
