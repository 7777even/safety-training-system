import type { ApiResponse } from '@shared/types';
import * as courseOperationsMock from '@/mock/course-operations';
import { request } from './request';

/** 后端通用启停用标识。 */
export type BinaryStatus = 0 | 1;
/** 当前阶段允许创建的课程内容类型。 */
export type CourseItemType = 'ASSET' | 'LIVE' | 'QUESTION_BANK' | 'QUIZ';
export type CoursePublishStatus = 'DRAFT' | 'PUBLISHED' | 'ENDED' | 'CANCELLED';
export type CourseType = 'REQUIRED' | 'ELECTIVE';
export type EnrollmentType = 'ASSIGNED' | 'OPEN';
export type EligibilityType = 'ALL' | 'SELECTED';
export type CompletionRule = 'ALL_REQUIRED_ITEMS' | 'ALL_ITEMS';
export type LearningStatus = 'NOT_STARTED' | 'LEARNING' | 'COMPLETED' | 'EXPIRED';

/** 课程根级章节。 */
export interface CourseChapter {
  /** 父章节ID，0为根级。 */
  parentId?: number;
  chapterId: number;
  courseId: number;
  chapterName: string;
  sortOrder: number;
  status: BinaryStatus;
  version: number;
}

export interface ChapterCreatePayload {
  /** 父章节ID，0为根级。 */
  parentId?: number;
  courseId: number;
  chapterName: string;
  sortOrder?: number;
  status?: BinaryStatus;
}

export interface ChapterUpdatePayload {
  /** 父章节ID，0为根级。 */
  parentId?: number;
  chapterName: string;
  sortOrder: number;
  status: BinaryStatus;
  version: number;
}

/** 直播场次信息。 */
export interface LiveSession {
  liveSessionId: number;
  courseItemId: number;
  liveName: string;
  teacherUserId: number;
  liveUrl: string;
  startTime: string;
  endTime: string;
  audienceId?: number | null;
  liveStatus: 'DRAFT' | 'SCHEDULED' | 'CANCELLED' | 'LIVING' | 'ENDED';
  status: BinaryStatus;
  version: number;
}

export interface LiveSessionPayload {
  liveName: string;
  teacherUserId: number;
  liveUrl: string;
  startTime: string;
  endTime: string;
  audienceId?: number | null;
  liveStatus: LiveSession['liveStatus'];
  version: number;
  status: BinaryStatus;
}

/** 新建 LIVE 内容项时同事务创建的直播场次。 */
export interface LiveSessionCreatePayload {
  liveName: string;
  teacherUserId: number;
  liveUrl: string;
  startTime: string;
  endTime: string;
  audienceId?: number | null;
}

/** 章节下的素材或直播内容项。 */
export interface CourseItem {
  /** 关联同课程已有测验，null解除。 */
  linkedQuizItemId?: number | null;
  isFinalAssessment?: 0 | 1;
  passScore?: number | null;
  courseItemId: number;
  courseId: number;
  chapterId: number;
  itemType: CourseItemType;
  itemTitle: string;
  assetId?: number | null;
  questionBankId?: number | null;
  questionBankName?: string | null;
  paperVersionId?: number | null;
  paperId?: number | null;
  paperName?: string | null;
  paperVersionNo?: number | null;
  paperPublishedStatus?: string | null;
  isRequired: BinaryStatus;
  minimumStudySeconds: number;
  sortOrder: number;
  isLockedAfterParticipation: BinaryStatus;
  status: BinaryStatus;
  version: number;
  liveSession?: LiveSession | null;
}

export interface ItemCreatePayload {
  /** 关联同课程已有测验，null解除。 */
  linkedQuizItemId?: number | null;
  isFinalAssessment?: 0 | 1;
  passScore?: number | null;
  chapterId: number;
  itemType: CourseItemType;
  itemTitle: string;
  assetId?: number | null;
  questionBankId?: number | null;
  paperVersionId?: number | null;
  isRequired: BinaryStatus;
  minimumStudySeconds: number;
  sortOrder: number;
  status: BinaryStatus;
  isLockedAfterParticipation?: BinaryStatus;
  liveSession?: LiveSessionCreatePayload;
}

export interface ItemUpdatePayload {
  /** 关联同课程已有测验，null解除。 */
  linkedQuizItemId?: number | null;
  isFinalAssessment?: 0 | 1;
  passScore?: number | null;
  itemTitle: string;
  isRequired: BinaryStatus;
  minimumStudySeconds: number;
  sortOrder: number;
  status: BinaryStatus;
  version: number;
  assetId?: number | null;
  questionBankId?: number | null;
  paperVersionId?: number | null;
}

export interface ItemMovePayload {
  chapterId: number;
  sortOrder: number;
  version: number;
}

/** 课程下载素材关联。 */
export interface DownloadAsset {
  courseDownloadAssetId: number;
  courseId: number;
  assetId: number;
  sortOrder: number;
  status: BinaryStatus;
  version: number;
}

export interface CourseVersionSummary {
  courseVersionId: number;
  courseId: number;
  versionNo: number;
  snapshotHash: string;
  versionNote?: string | null;
  publishedBy: number;
  publishedTime: string;
}

export interface CourseVersion extends CourseVersionSummary {
  contentSnapshotJson: string;
}

export interface CourseOffering {
  courseOfferingId: number;
  offeringCode: string;
  courseId: number;
  courseName: string;
  courseVersionId?: number | null;
  /** 来源培训计划内容 ID；为空表示独立开课，非空表示培训生成。 */
  planContentId?: number | null;
  offeringName: string;
  startTime?: string | null;
  endTime?: string | null;
  courseType: CourseType;
  enrollmentType: EnrollmentType;
  eligibilityType: EligibilityType;
  eligibilitySelection?: import('./person-selection').PersonSelectionRequest;
  completionRule: CompletionRule;
  publishStatus: CoursePublishStatus;
  publishedTime?: string | null;
  status: BinaryStatus;
  updateTime: string;
  audienceId?: number | null;
  isSelfEnrollAllowed?: BinaryStatus;
  isExternalUserAllowed?: BinaryStatus;
  version?: number;
}

export interface CourseOfferingPayload {
  offeringCode: string;
  courseId: number;
  offeringName: string;
  startTime?: string | null;
  endTime?: string | null;
  courseType: CourseType;
  enrollmentType: EnrollmentType;
  eligibilityType: EligibilityType;
  eligibilitySelection?: import('./person-selection').PersonSelectionRequest;
  completionRule?: CompletionRule;
  audienceId?: number | null;
  isSelfEnrollAllowed?: BinaryStatus;
  isExternalUserAllowed?: BinaryStatus;
  status?: BinaryStatus;
}

export interface CourseOfferingUpdatePayload extends Omit<CourseOfferingPayload, 'offeringCode' | 'courseId'> {
  version: number;
}

export interface OfferingPageQuery {
  pageNum: number;
  pageSize: number;
  'query.offeringName'?: string;
  'query.courseId'?: number;
  /** 按培训计划内容过滤；为空时查询独立与培训生成的全部开课。 */
  'query.planContentId'?: number;
  'query.publishStatus'?: CoursePublishStatus;
  'query.courseType'?: CourseType;
  'query.enrollmentType'?: EnrollmentType;
  'query.status'?: BinaryStatus;
}

/** 开课报名列表项。 */
export interface CourseEnrollment {
  courseEnrollmentId: number;
  courseOfferingId: number;
  userId: number;
  userCode: string;
  userName: string;
  enrollmentSource: 'ASSIGN' | 'SELF' | 'LINK' | 'IMPORT' | 'TRAINING';
  enrolledTime: string;
  learningStatus: LearningStatus;
  progressPercent: number;
  totalStudySeconds: number;
  creditAwarded: number;
  completedTime?: string | null;
  lastStudyTime?: string | null;
}

export interface EnrollmentPageQuery {
  pageNum: number;
  pageSize: number;
  'query.userId'?: number;
  'query.learningStatus'?: LearningStatus;
  'query.enrollmentSource'?: CourseEnrollment['enrollmentSource'];
}

export interface EnrollmentDetail {
  courseEnrollmentId: number;
  courseOfferingId: number;
  userId: number;
  learningStatus: LearningStatus;
  progressPercent: number;
  totalStudySeconds: number;
  creditAwarded: number;
  itemProgresses?: Array<{
    itemProgressId?: number;
    courseItemId: number;
    progressStatus: string;
    studySeconds: number;
    lastPositionSeconds?: number | null;
    lastPageNo?: number | null;
    attemptCount?: number;
    firstStartTime?: string | null;
    completedTime?: string | null;
    lastStudyTime?: string | null;
  }>;
  recentSessions?: Array<{
    studySessionId: number;
    courseItemId: number;
    deviceType: string;
    startedTime: string;
    endedTime?: string | null;
    validStudySeconds: number;
    endPositionSeconds?: number | null;
    endPageNo?: number | null;
    endReason?: string;
    clientTraceId?: string;
  }>;
  evaluation?: { rating: number; content?: string | null; submittedTime: string } | null;
}

/** 课程测验的阅卷人。 */
export interface CourseAssessmentReviewer {
  examReviewerId: number;
  assessmentExecutionId: number;
  reviewerUserId: number;
  reviewerName?: string;
  reviewerCode?: string;
  assignmentSource: string;
}
/** 课程测验待批阅题目。 */
export interface CourseAssessmentReviewTask {
  examAnswerId: number;
  examAttemptId: number;
  learnerName: string;
  stemSnapshot: string;
  answerText?: string | null;
  standardAnswer?: string | null;
  score: number;
  latestManualScore?: number | null;
  latestReviewComment?: string | null;
}
/** 课程考核自动生效结果。 */
export interface CourseAssessmentResult {
  examAttemptId: number;
  userId: number;
  learnerName?: string | null;
  attemptNo: number;
  finalScore: number;
  isPassed: BinaryStatus;
  correctCount: number;
  wrongCount: number;
  effectiveTime: string;
}

interface PageResult<TItem> {
  list: TItem[];
  total: number;
}

/** 课程 Contract Mock 开关：只有显式设置为 true 时启用 Mock。 */
export function isCourseOperationsMockEnabled(): boolean {
  const runtimeOverride = (globalThis as { __COURSE_OPERATIONS_MOCK_ENABLED__?: boolean }).__COURSE_OPERATIONS_MOCK_ENABLED__;
  return runtimeOverride ?? import.meta.env?.VITE_USE_API_MOCK === 'true';
}

/** 解包统一响应，并保留后端可行动的中文业务消息。 */
function unwrapResponse<TData>(response: ApiResponse<TData>): TData {
  if (response.code !== 0) {
    if (response.code === 2040) {
      throw new Error('数据已被更新，请刷新后重试。');
    }
    throw new Error(response.message || '请求失败，请稍后重试。');
  }
  return response.data;
}

async function call<TData>(config: Parameters<typeof request>[0]): Promise<TData> {
  return unwrapResponse(await request<ApiResponse<TData>>(config));
}

/** Mock 开启时优先执行本地行为；关闭后原样走真实 Contract 请求。 */
async function selectSource<T>(mockAction: () => T | Promise<T>, realAction: () => Promise<T>): Promise<T> {
  return isCourseOperationsMockEnabled() ? await mockAction() : await realAction();
}

export const getCourseChapters = (courseId: number) =>
  selectSource(
    () => courseOperationsMock.mockGetCourseChapters(courseId),
    () => call<CourseChapter[]>({ url: '/api/course/chapters', method: 'GET', params: { courseId } })
  );

export const createCourseChapter = (data: ChapterCreatePayload) =>
  selectSource(
    () => courseOperationsMock.mockCreateCourseChapter(data),
    () => call<number>({ url: '/api/course/chapters', method: 'POST', data })
  );

export const updateCourseChapter = (chapterId: number, data: ChapterUpdatePayload) =>
  selectSource(
    () => courseOperationsMock.mockUpdateCourseChapter(chapterId, data),
    () => call<null>({ url: `/api/course/chapters/${chapterId}`, method: 'PUT', data })
  );

export const deleteCourseChapter = (chapterId: number) =>
  selectSource(
    () => courseOperationsMock.mockDeleteCourseChapter(chapterId),
    () => call<null>({ url: `/api/course/chapters/${chapterId}`, method: 'DELETE' })
  );

export const getCourseItems = (chapterId: number) =>
  selectSource(
    () => courseOperationsMock.mockGetCourseItems(chapterId),
    () => call<CourseItem[]>({ url: '/api/course/items', method: 'GET', params: { chapterId } })
  );

export const createCourseItem = (data: ItemCreatePayload) =>
  selectSource(
    () => courseOperationsMock.mockCreateCourseItem(data),
    () => call<number>({ url: '/api/course/items', method: 'POST', data })
  );

export const updateCourseItem = (itemId: number, data: ItemUpdatePayload) =>
  selectSource(
    () => courseOperationsMock.mockUpdateCourseItem(itemId, data),
    () => call<null>({ url: `/api/course/items/${itemId}`, method: 'PUT', data })
  );

export const deleteCourseItem = (itemId: number) =>
  selectSource(
    () => courseOperationsMock.mockDeleteCourseItem(itemId),
    () => call<null>({ url: `/api/course/items/${itemId}`, method: 'DELETE' })
  );

export const moveCourseItem = (itemId: number, data: ItemMovePayload) =>
  selectSource(
    () => courseOperationsMock.mockMoveCourseItem(itemId, data),
    () => call<null>({ url: `/api/course/items/${itemId}/move`, method: 'PUT', data })
  );

export const getLiveSession = (itemId: number) =>
  selectSource(
    () => courseOperationsMock.mockGetLiveSession(itemId),
    () => call<LiveSession>({ url: `/api/course/items/${itemId}/live-session`, method: 'GET' })
  );

export const updateLiveSession = (itemId: number, data: LiveSessionPayload) =>
  selectSource(
    () => courseOperationsMock.mockUpdateLiveSession(itemId, data),
    () => call<null>({ url: `/api/course/items/${itemId}/live-session`, method: 'PUT', data })
  );

export const getDownloadAssets = (courseId: number) =>
  selectSource(
    () => courseOperationsMock.mockGetDownloadAssets(courseId),
    () => call<DownloadAsset[]>({ url: `/api/course/courses/${courseId}/download-assets`, method: 'GET' })
  );

export const replaceDownloadAssets = (courseId: number, assets: Array<{ assetId: number; sortOrder: number; status: BinaryStatus }>) =>
  selectSource(
    () => courseOperationsMock.mockReplaceDownloadAssets(courseId, assets),
    () => call<null>({ url: `/api/course/courses/${courseId}/download-assets`, method: 'PUT', data: { assets } })
  );

export const publishCourse = (courseId: number, data: { version: number; versionNote?: string }) =>
  selectSource(
    () => courseOperationsMock.mockPublishCourse(courseId, data),
    () => call<number>({ url: `/api/course/courses/${courseId}/publish`, method: 'POST', data })
  );

export const offlineCourse = (courseId: number, data: { version: number }) =>
  selectSource(
    () => courseOperationsMock.mockOfflineCourse(courseId, data),
    () => call<null>({ url: `/api/course/courses/${courseId}/offline`, method: 'PUT', data })
  );

export const getCourseVersions = (courseId: number) =>
  selectSource(
    () => courseOperationsMock.mockGetCourseVersions(courseId),
    () => call<CourseVersionSummary[]>({ url: `/api/course/courses/${courseId}/versions`, method: 'GET' })
  );

export const getCourseVersionDetail = (courseId: number, versionId: number) =>
  selectSource(
    () => courseOperationsMock.mockGetCourseVersionDetail(courseId, versionId),
    () => call<CourseVersion>({ url: `/api/course/courses/${courseId}/versions/${versionId}`, method: 'GET' })
  );

export const getCourseOfferingPage = (params: OfferingPageQuery) =>
  selectSource(
    () => courseOperationsMock.mockGetCourseOfferingPage(params),
    () => call<PageResult<CourseOffering>>({ url: '/api/course/offerings', method: 'GET', params })
  );

export const createCourseOffering = (data: CourseOfferingPayload) =>
  selectSource(
    () => courseOperationsMock.mockCreateCourseOffering(data),
    () => call<number>({ url: '/api/course/offerings', method: 'POST', data })
  );

export const getCourseOfferingDetail = (offeringId: number) =>
  selectSource(
    () => courseOperationsMock.mockGetCourseOfferingDetail(offeringId),
    () => call<CourseOffering>({ url: `/api/course/offerings/${offeringId}`, method: 'GET' })
  );

export const updateCourseOffering = (offeringId: number, data: CourseOfferingUpdatePayload) =>
  selectSource(
    () => courseOperationsMock.mockUpdateCourseOffering(offeringId, data),
    () => call<null>({ url: `/api/course/offerings/${offeringId}`, method: 'PUT', data })
  );

export const deleteCourseOffering = (offeringId: number) =>
  selectSource(
    () => courseOperationsMock.mockDeleteCourseOffering(offeringId),
    () => call<null>({ url: `/api/course/offerings/${offeringId}`, method: 'DELETE' })
  );

export const publishCourseOffering = (offeringId: number, version: number) =>
  selectSource(
    () => courseOperationsMock.mockPublishCourseOffering(offeringId, version),
    () => call<null>({ url: `/api/course/offerings/${offeringId}/publish`, method: 'POST', data: { version } })
  );
export const cancelCourseOffering = (offeringId: number, version: number) =>
  selectSource(
    () => courseOperationsMock.mockCancelCourseOffering(offeringId, version),
    () => call<null>({ url: `/api/course/offerings/${offeringId}/cancel`, method: 'PUT', data: { version } })
  );
export const endCourseOffering = (offeringId: number, version: number) =>
  selectSource(
    () => courseOperationsMock.mockEndCourseOffering(offeringId, version),
    () => call<null>({ url: `/api/course/offerings/${offeringId}/end`, method: 'PUT', data: { version } })
  );

export const getCourseEnrollmentPage = (offeringId: number, params: EnrollmentPageQuery) =>
  selectSource(
    () => courseOperationsMock.mockGetCourseEnrollmentPage(offeringId, params),
    () => call<PageResult<CourseEnrollment>>({ url: `/api/course/offerings/${offeringId}/enrollments`, method: 'GET', params })
  );

export const assignCourseEnrollments = (offeringId: number, selection: import('./person-selection').PersonSelectionRequest) =>
  selectSource(
    () => courseOperationsMock.mockAssignCourseEnrollments(offeringId, selection),
    () => call<null>({ url: `/api/course/offerings/${offeringId}/enrollments`, method: 'POST', data: { selection } })
  );

export const removeCourseEnrollment = (enrollmentId: number) =>
  selectSource(
    () => courseOperationsMock.mockRemoveCourseEnrollment(enrollmentId),
    () => call<null>({ url: `/api/course/enrollments/${enrollmentId}`, method: 'DELETE' })
  );

export const getAdminEnrollmentDetail = (enrollmentId: number) =>
  selectSource(
    () => courseOperationsMock.mockGetAdminEnrollmentDetail(enrollmentId),
    () => call<EnrollmentDetail>({ url: `/api/course/learning/admin/enrollments/${enrollmentId}`, method: 'GET' })
  );

/** 查询课程测验阅卷人。 */
export const getCourseAssessmentReviewers = (offeringId: number, itemId: number) =>
  call<CourseAssessmentReviewer[]>({ url: `/api/course/offerings/${offeringId}/items/${itemId}/assessment/reviewers`, method: 'GET' });
/** 替换课程测验阅卷人。 */
export const replaceCourseAssessmentReviewers = (offeringId: number, itemId: number, reviewerUserIds: number[]) =>
  call<null>({ url: `/api/course/offerings/${offeringId}/items/${itemId}/assessment/reviewers`, method: 'PUT', data: { reviewerUserIds, assignmentSource: 'USER' } });
/** 查询课程测验待批阅任务。 */
export const getCourseAssessmentReviewTasks = (offeringId: number, itemId: number) =>
  call<PageResult<CourseAssessmentReviewTask>>({ url: `/api/course/offerings/${offeringId}/items/${itemId}/assessment/review-tasks`, method: 'GET', params: { pageNum: 1, pageSize: 100 } });
/** 提交课程测验单题评分。 */
export const reviewCourseAssessmentAnswer = (offeringId: number, itemId: number, answerId: number, data: { afterScore: number; reviewComment?: string }) =>
  call<null>({ url: `/api/course/offerings/${offeringId}/items/${itemId}/assessment/answers/${answerId}/review`, method: 'POST', data: { ...data, reviewAction: 'SCORE' } });
/** 查询课程考核自动生效结果。 */
export const getCourseAssessmentResults = (offeringId: number, itemId: number) =>
  call<PageResult<CourseAssessmentResult>>({ url: `/api/course/offerings/${offeringId}/items/${itemId}/assessment/results`, method: 'GET', params: { pageNum: 1, pageSize: 100 } });
