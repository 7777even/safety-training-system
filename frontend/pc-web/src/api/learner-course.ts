import type { LearnerCourseCategory } from '@shared/types/course-learning';
import { examClientInfo } from '@shared/libs/exam-client-info';
import type {
  ApiResponse,
  CourseEvaluationSubmit,
  CourseEvaluationListItem,
  CourseLearningPage,
  CourseAssessmentAttempt,
  SaveLearnerExamAnswersPayload,
  SubmitLearnerExamAttemptPayload,
  LearnerCourseEnrollment,
  LearnerCourseDetail,
  LearnerCourseOffering,
  LearnerCourseTasks,
  StudySessionReport
} from '@shared/types';
import * as learnerCourseMock from '@/mock/learner-course';
import { request } from './request';

/** 学员课程 Contract Mock 开关；TODO replace real api：逐接口联调通过后关闭对应分支。 */
const USE_API_MOCK = import.meta.env.VITE_USE_API_MOCK === 'true';

/** 保留课程接口业务码，供入口页区分快照异常和普通失败。 */
export class LearnerCourseApiError extends Error {
  constructor(public readonly code: number, message: string) {
    super(message);
    this.name = 'LearnerCourseApiError';
  }
}

/** 解包 Result 外壳，保留后端业务提示。 */
function unwrapResponse<T>(response: ApiResponse<T>): T {
  if (response.code !== 0) throw new LearnerCourseApiError(response.code, response.message || '请求失败，请稍后重试。');
  return response.data;
}

/** 查询本人课程。 */
export async function getLearnerCoursePage(params: {
  pageNum: number;
  pageSize: number;
  'query.learningStatus'?: LearnerCourseEnrollment['learningStatus'];
}): Promise<CourseLearningPage<LearnerCourseEnrollment>> {
  const response = USE_API_MOCK
    ? await learnerCourseMock.mockGetLearnerCoursePage(params)
    : await request<ApiResponse<CourseLearningPage<LearnerCourseEnrollment>>>({ url: '/api/course/learning/my-enrollments', method: 'GET', params });
  return unwrapResponse(response);
}

/** 查询本人可报名课程。 */
export async function getLearnerOfferingPage(params: {
  pageNum: number;
  pageSize: number;
  'query.keyword'?: string;
  'query.categoryId'?: number;
}): Promise<CourseLearningPage<LearnerCourseOffering>> {
  const response = USE_API_MOCK
    ? await learnerCourseMock.mockGetLearnerOfferingPage(params)
    : await request<ApiResponse<CourseLearningPage<LearnerCourseOffering>>>({ url: '/api/course/learning/offerings', method: 'GET', params });
  return unwrapResponse(response);
}

/** 自主报名开课。 */
export async function selfEnrollCourse(offeringId: number): Promise<number> {
  const response = USE_API_MOCK
    ? await learnerCourseMock.mockSelfEnroll(offeringId)
    : await request<ApiResponse<number>>({ url: `/api/course/offerings/${offeringId}/self-enroll`, method: 'POST' });
  return unwrapResponse(response);
}

/** 查询本人课程学习任务。 */
export async function getLearnerCourseTasks(enrollmentId: number): Promise<LearnerCourseTasks> {
  const response = USE_API_MOCK
    ? await learnerCourseMock.mockGetLearnerCourseTasks(enrollmentId)
    : await request<ApiResponse<LearnerCourseTasks>>({ url: `/api/course/learning/enrollments/${enrollmentId}/tasks`, method: 'GET' });
  return unwrapResponse(response);
}

/** 查询当前学员的课程级详情，用于课程简介、附件和顶部学习摘要。 */
export async function getLearnerCourseDetail(enrollmentId: number): Promise<LearnerCourseDetail> {
  const response = USE_API_MOCK
    ? await learnerCourseMock.mockGetLearnerCourseDetail(enrollmentId)
    : await request<ApiResponse<LearnerCourseDetail>>({ url: `/api/course/learning/enrollments/${enrollmentId}`, method: 'GET' });
  return unwrapResponse(response);
}

/** 以当前报名身份下载冻结课程附件，响应为受控文件二进制流。 */
export async function downloadLearnerCourseAsset(_enrollmentId: number, _assetId: number, downloadUrl: string): Promise<Blob> {
  if (USE_API_MOCK) return new Blob(['课程附件模拟文件'], { type: 'application/octet-stream' });
  return request<Blob>({
    url: downloadUrl,
    method: 'GET',
    responseType: 'blob'
  });
}

/** 从课程题库练习或课程测验内容项启动本人作答，并返回课程考核快照。 */
export async function startCourseExamAttempt(
  enrollmentId: number,
  itemId: number,
  data: { clientAttemptToken: string; platform?: string }
): Promise<CourseAssessmentAttempt> {
  const response = USE_API_MOCK
    ? await learnerCourseMock.mockStartCourseExamAttempt(enrollmentId, itemId, data)
    : await request<ApiResponse<CourseAssessmentAttempt>>({
      url: `/api/course/learning/enrollments/${enrollmentId}/items/${itemId}/attempts`,
      method: 'POST',
      data: { ...data, ...examClientInfo('PC', typeof navigator === 'undefined' ? '' : navigator.userAgent) }
    });
  return unwrapResponse(response);
}

/** 通过课程学员响应返回的受控路径读取封面，避免用课程 ID 重建路径。 */
export async function getLearnerCourseCover(coverUrl: string): Promise<Blob> {
  if (USE_API_MOCK) return new Blob(['课程封面模拟文件'], { type: 'image/svg+xml' });
  return request<Blob>({ url: coverUrl, method: 'GET', responseType: 'blob' });
}

/** 查询本人课程考核作答详情，不进入独立考试详情接口。 */
export async function getCourseAssessmentAttempt(enrollmentId: number, itemId: number, attemptId: number): Promise<CourseAssessmentAttempt> {
  const response = USE_API_MOCK
    ? await learnerCourseMock.mockGetCourseAssessmentAttempt(enrollmentId, itemId, attemptId)
    : await request<ApiResponse<CourseAssessmentAttempt>>({
      url: `/api/course/learning/enrollments/${enrollmentId}/items/${itemId}/attempts/${attemptId}`,
      method: 'GET'
    });
  return unwrapResponse(response);
}

/** 暂存课程题库练习或课程测验答案，不走独立考试作答接口。 */
export async function saveCourseAssessmentAnswers(
  enrollmentId: number, itemId: number, attemptId: number, data: SaveLearnerExamAnswersPayload
): Promise<void> {
  const response = USE_API_MOCK
    ? await learnerCourseMock.mockSaveCourseAssessmentAnswers(enrollmentId, itemId, attemptId, data)
    : await request<ApiResponse<void>>({
      url: `/api/course/learning/enrollments/${enrollmentId}/items/${itemId}/attempts/${attemptId}/answers`, method: 'PUT', data
    });
  unwrapResponse(response);
}

/** 提交课程题库练习或课程测验，结果由课程规则自动生效。 */
export async function submitCourseAssessmentAttempt(
  enrollmentId: number, itemId: number, attemptId: number, data: SubmitLearnerExamAttemptPayload
): Promise<CourseAssessmentAttempt> {
  const response = USE_API_MOCK
    ? await learnerCourseMock.mockSubmitCourseAssessmentAttempt(enrollmentId, itemId, attemptId, data)
    : await request<ApiResponse<CourseAssessmentAttempt>>({
      url: `/api/course/learning/enrollments/${enrollmentId}/items/${itemId}/attempts/${attemptId}/submit`, method: 'POST', data
    });
  return unwrapResponse(response);
}

/** 上报学员学习会话。 */
export async function reportStudySession(data: StudySessionReport): Promise<number> {
  const response = USE_API_MOCK
    ? await learnerCourseMock.mockReportStudySession(data)
    : await request<ApiResponse<number>>({ url: '/api/course/learning/sessions', method: 'POST', data });
  return unwrapResponse(response);
}

/** 退选本人允许退选的选修课程。 */
export async function withdrawLearnerCourse(enrollmentId: number): Promise<void> {
  const response = USE_API_MOCK
    ? await learnerCourseMock.mockWithdrawEnrollment(enrollmentId)
    : await request<ApiResponse<null>>({ url: `/api/course/enrollments/${enrollmentId}/withdraw`, method: 'POST' });
  unwrapResponse(response);
}

/** 提交本人已完成课程评价。 */
export async function submitCourseEvaluation(enrollmentId: number, data: CourseEvaluationSubmit): Promise<number> {
  const response = USE_API_MOCK
    ? await learnerCourseMock.mockSubmitCourseEvaluation(enrollmentId, data)
    : await request<ApiResponse<number>>({ url: `/api/course/learning/enrollments/${enrollmentId}/evaluation`, method: 'POST', data });
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
