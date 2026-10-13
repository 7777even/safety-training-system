import type { LearnerCourseCategory } from '@shared/types/course-learning';
import { examClientInfo } from '@shared/libs/exam-client-info';
import type {
  ApiResponse,
  CourseAssessmentAttempt,
  CourseEvaluationSubmit,
  CourseEvaluationListItem,
  CourseLearningPage,
  LearnerCourseDetail,
  LearnerCourseEnrollment,
  LearnerCourseOffering,
  LearnerCourseTasks,
  SaveLearnerExamAnswersPayload,
  StudySessionReport,
  SubmitLearnerExamAttemptPayload
} from '@shared/types';
import * as learnerCourseMock from '@/mock/learner-course';
import { getAccessToken, getBaseURL, request } from './request';

/** 默认调用线上真实接口，仅显式开启时保留本地 Mock。 */
const USE_API_MOCK = import.meta.env.VITE_USE_API_MOCK === 'true';

/** 保留课程接口业务码，供入口页区分快照异常和普通失败。 */
export class LearnerCourseApiError extends Error {
  constructor(public readonly code: number, message: string) {
    super(message);
    this.name = 'LearnerCourseApiError';
  }
}

/** 解包 Contract Result 外壳。 */
function unwrapResponse<T>(response: ApiResponse<T>): T {
  if (response.code !== 0) throw new LearnerCourseApiError(response.code, response.message || '请求失败，请稍后重试。');
  return response.data;
}

/** 查询当前用户课程卡片列表。 */
export async function getLearnerCoursePage(params: {
  pageNum: number;
  pageSize: number;
  'query.learningStatus'?: LearnerCourseEnrollment['learningStatus'];
}): Promise<CourseLearningPage<LearnerCourseEnrollment>> {
  const response = USE_API_MOCK
    ? await learnerCourseMock.mockGetLearnerCoursePage(params)
    : await request<ApiResponse<CourseLearningPage<LearnerCourseEnrollment>>>({
        url: '/api/course/learning/my-enrollments',
        method: 'GET',
        params
      });
  return unwrapResponse(response);
}

/** 查询本人可报名课程；与 PC 学习中心共用同一学员 Contract，仅按关键字过滤。 */
export async function getLearnerOfferingPage(params: {
  pageNum: number;
  pageSize: number;
  'query.keyword'?: string;
  'query.categoryId'?: number;
}): Promise<CourseLearningPage<LearnerCourseOffering>> {
  const response = USE_API_MOCK
    ? await learnerCourseMock.mockGetLearnerOfferingPage(params)
    : await request<ApiResponse<CourseLearningPage<LearnerCourseOffering>>>({
        url: '/api/course/learning/offerings',
        method: 'GET',
        params
      });
  return unwrapResponse(response);
}

/** 学员自助报名；后端校验开课已发布、选课方式为 OPEN 且幂等返回报名 ID。 */
export async function selfEnrollCourse(offeringId: number): Promise<number> {
  const response = USE_API_MOCK
    ? await learnerCourseMock.mockSelfEnrollCourse(offeringId)
    : await request<ApiResponse<number>>({ url: `/api/course/offerings/${offeringId}/self-enroll`, method: 'POST' });
  return unwrapResponse(response);
}

/** 查询当前用户课程任务。 */
export async function getLearnerCourseTasks(enrollmentId: number): Promise<LearnerCourseTasks> {
  const response = USE_API_MOCK
    ? await learnerCourseMock.mockGetLearnerCourseTasks(enrollmentId)
    : await request<ApiResponse<LearnerCourseTasks>>({ url: `/api/course/learning/enrollments/${enrollmentId}/tasks`, method: 'GET' });
  return unwrapResponse(response);
}

/** 查询当前学员课程级详情，用于移动端课程简介和附件区域。 */
export async function getLearnerCourseDetail(enrollmentId: number): Promise<LearnerCourseDetail> {
  const response = USE_API_MOCK
    ? await learnerCourseMock.mockGetLearnerCourseDetail(enrollmentId)
    : await request<ApiResponse<LearnerCourseDetail>>({ url: `/api/course/learning/enrollments/${enrollmentId}`, method: 'GET' });
  return unwrapResponse(response);
}

/** 提交本人已完成课程评价，每报名仅可提交一次。 */
export async function submitCourseEvaluation(enrollmentId: number, data: CourseEvaluationSubmit): Promise<number> {
  const response = USE_API_MOCK
    ? await learnerCourseMock.mockSubmitCourseEvaluation(enrollmentId, data)
    : await request<ApiResponse<number>, CourseEvaluationSubmit>({
        url: `/api/course/learning/enrollments/${enrollmentId}/evaluation`,
        method: 'POST',
        data
      });
  return unwrapResponse(response);
}

/** 分页查看本人报名所在开课的公开评价。 */
export async function getPeerCourseEvaluations(
  enrollmentId: number, params: { pageNum: number; pageSize: number }
): Promise<CourseLearningPage<CourseEvaluationListItem>> {
  const response = USE_API_MOCK
    ? await learnerCourseMock.mockGetPeerCourseEvaluations(enrollmentId, params)
    : await request<ApiResponse<CourseLearningPage<CourseEvaluationListItem>>>({
        url: `/api/course/learning/enrollments/${enrollmentId}/evaluations`, method: 'GET', params
      });
  return unwrapResponse(response);
}

/** 构造课程附件受控下载请求，下载前由后端校验报名、快照和素材状态。 */
export function downloadLearnerCourseAsset(
  _enrollmentId: number,
  _assetId: number,
  downloadUrl: string
): {
  url: string;
  header: Record<string, string>;
} {
  const token = getAccessToken();
  return {
    url: /^https?:\/\//.test(downloadUrl) ? downloadUrl : `${getBaseURL()}${downloadUrl}`,
    header: token ? { Authorization: `Bearer ${token}` } : {}
  };
}

/** 从课程题库练习或课程测验内容项启动作答，由服务端回写课程项进度事实。 */
export async function startCourseExamAttempt(
  enrollmentId: number,
  itemId: number,
  data: {
    clientAttemptToken: string;
    platform?: string;
  }
): Promise<CourseAssessmentAttempt> {
  const response = USE_API_MOCK
    ? await learnerCourseMock.mockStartCourseExamAttempt(enrollmentId, itemId, data)
    : await request<ApiResponse<CourseAssessmentAttempt>>({
        url: `/api/course/learning/enrollments/${enrollmentId}/items/${itemId}/attempts`,
        method: 'POST',
        data: { ...data, ...examClientInfo('H5', typeof navigator === 'undefined' ? '' : navigator.userAgent) }
      });
  return unwrapResponse(response);
}

/** 查询本人课程考核作答详情，不复用独立考试详情接口。 */
export async function getCourseAssessmentAttempt(enrollmentId: number, itemId: number, attemptId: number): Promise<CourseAssessmentAttempt> {
  const response = USE_API_MOCK
    ? await learnerCourseMock.mockGetCourseAssessmentAttempt(enrollmentId, itemId, attemptId)
    : await request<ApiResponse<CourseAssessmentAttempt>>({
        url: `/api/course/learning/enrollments/${enrollmentId}/items/${itemId}/attempts/${attemptId}`,
        method: 'GET'
      });
  return unwrapResponse(response);
}

/** 暂存课程题库练习或课程测验答案，不进入独立考试作答接口。 */
export async function saveCourseAssessmentAnswers(
  enrollmentId: number,
  itemId: number,
  attemptId: number,
  data: SaveLearnerExamAnswersPayload
): Promise<void> {
  const response = USE_API_MOCK
    ? await learnerCourseMock.mockSaveCourseAssessmentAnswers(enrollmentId, itemId, attemptId, data)
    : await request<ApiResponse<void>, SaveLearnerExamAnswersPayload>({
        url: `/api/course/learning/enrollments/${enrollmentId}/items/${itemId}/attempts/${attemptId}/answers`,
        method: 'PUT',
        data
      });
  unwrapResponse(response);
}

/** 提交课程题库练习或课程测验，成绩由课程规则自动生效。 */
export async function submitCourseAssessmentAttempt(
  enrollmentId: number,
  itemId: number,
  attemptId: number,
  data: SubmitLearnerExamAttemptPayload
): Promise<CourseAssessmentAttempt> {
  const response = USE_API_MOCK
    ? await learnerCourseMock.mockSubmitCourseAssessmentAttempt(enrollmentId, itemId, attemptId, data)
    : await request<ApiResponse<CourseAssessmentAttempt>, SubmitLearnerExamAttemptPayload>({
        url: `/api/course/learning/enrollments/${enrollmentId}/items/${itemId}/attempts/${attemptId}/submit`,
        method: 'POST',
        data
      });
  return unwrapResponse(response);
}

/** 上报移动端学习会话。 */
export async function reportStudySession(data: StudySessionReport): Promise<number> {
  const response = USE_API_MOCK
    ? await learnerCourseMock.mockReportStudySession(data)
    : await request<ApiResponse<number>, StudySessionReport>({ url: '/api/course/learning/sessions', method: 'POST', data });
  return unwrapResponse(response);
}


/** 登记本人直播参与，不向服务端上报第三方观看秒数。 */
export async function participateCourseLive(enrollmentId: number, itemId: number): Promise<{ liveUrl: string; progressStatus: 'COMPLETED' }> {
  if (USE_API_MOCK) return unwrapResponse(await learnerCourseMock.mockParticipateCourseLive(enrollmentId, itemId));
  return unwrapResponse(await request<ApiResponse<{ liveUrl: string; progressStatus: 'COMPLETED' }>>({
    url: `/api/course/learning/enrollments/${enrollmentId}/items/${itemId}/live-participation`, method: 'POST'
  }));
}

/** 学员分类树使用只读课程接口，无需分类管理权限。 */
export async function getLearnerCourseCategories(): Promise<LearnerCourseCategory[]> {
  if (USE_API_MOCK) return unwrapResponse(await learnerCourseMock.mockGetLearnerCourseCategories());
  return unwrapResponse(await request<ApiResponse<LearnerCourseCategory[]>>({ url: '/api/course/learning/categories/tree', method: 'GET' }));
}
