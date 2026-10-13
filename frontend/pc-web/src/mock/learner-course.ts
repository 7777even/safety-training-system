import type { LearnerCourseCategory } from '@shared/types/course-learning';
import type {
  ApiResponse,
  CourseEvaluationSubmit,
  CourseEvaluation,
  CourseEvaluationListItem,
  CourseLearningPage,
  CourseAssessmentAttempt,
  SaveLearnerExamAnswersPayload,
  SubmitLearnerExamAttemptPayload,
  LearnerCourseEnrollment,
  LearnerCourseOffering,
  LearnerCourseTasks,
  LearnerCourseDetail,
  StudySessionReport
} from '@shared/types';

/** 学员课程 Contract Mock；TODO replace real api：后端联调通过后删除本文件分支。 */
const enrollments: LearnerCourseEnrollment[] = [
  {
    enrollmentId: 1000,
    offeringId: 100,
    courseId: 10,
    courseName: '安全生产基础',
    offeringName: '2026年度安全班',
    courseType: 'REQUIRED',
    startTime: '2026-07-01T00:00:00',
    endTime: '2026-12-31T23:59:59',
    learningStatus: 'LEARNING',
    progressPercent: 35,
    lastStudyTime: '2026-07-27T10:00:00',
    completedTime: null,
    coverUrl: '/api/course/courses/10/cover'
  },
  {
    enrollmentId: 1001,
    offeringId: 101,
    courseId: 11,
    courseName: '应急处置选修',
    offeringName: '应急技能提升班',
    courseType: 'ELECTIVE',
    startTime: '2026-07-20T00:00:00',
    endTime: '2026-10-31T23:59:59',
    learningStatus: 'NOT_STARTED',
    progressPercent: 0,
    lastStudyTime: null,
    completedTime: null,
    coverUrl: null
  },
  {
    enrollmentId: 1002,
    offeringId: 102,
    courseId: 12,
    courseName: '消防安全专题',
    offeringName: '消防安全复训',
    courseType: 'REQUIRED',
    startTime: '2026-06-01T00:00:00',
    endTime: '2026-07-20T23:59:59',
    learningStatus: 'COMPLETED',
    progressPercent: 100,
    lastStudyTime: '2026-07-19T10:00:00',
    completedTime: '2026-07-19T10:30:00',
    coverUrl: null
  }
];
const offerings: LearnerCourseOffering[] = [
  {
    offeringId: 200,
    courseId: 20,
    courseName: '受限空间作业安全',
    offeringName: '八月公开选课',
    courseType: 'ELECTIVE',
    startTime: '2026-08-01T00:00:00',
    endTime: '2026-11-30T23:59:59',
    enrollmentType: 'OPEN',
    enrollmentAvailability: 'AVAILABLE',
    coverUrl: null
  }
];
const sessionIds = new Map<string, number>();
const evaluations = new Map<number, CourseEvaluation>();
/** 同次开课的公开评价样例。 */
const peerEvaluations = new Map<number, CourseEvaluationListItem[]>([
  [102, [
    { rating: 4.5, content: '内容实用，案例清晰。', isAnonymous: 1, displayName: '匿名学员', submittedTime: '2026-07-20' },
    { rating: 5, content: '课程安排合理。', isAnonymous: 0, displayName: '王学员', submittedTime: '2026-07-19T14:30:00' }
  ]]
]);
let nextEnrollmentId = 1003;
let nextSessionId = 1;
const courseAssessmentAttempts = new Map<number, CourseAssessmentAttempt>();

/** 构建统一 Result 成功外壳。 */
function success<T>(data: T): ApiResponse<T> {
  return { code: 0, message: 'success', data };
}

/** 构建 Contract 定义的业务失败外壳。 */
function failure<T = null>(code: number, message: string): ApiResponse<T> {
  return { code, message, data: null as T };
}

/** 查询当前学员课程报名。 */
export async function mockGetLearnerCoursePage(params: {
  pageNum: number;
  pageSize: number;
  'query.learningStatus'?: LearnerCourseEnrollment['learningStatus'];
}): Promise<ApiResponse<CourseLearningPage<LearnerCourseEnrollment>>> {
  const filtered = enrollments.filter((item) => !params['query.learningStatus'] || item.learningStatus === params['query.learningStatus']);
  const start = (params.pageNum - 1) * params.pageSize;
  return success({ list: filtered.slice(start, start + params.pageSize), total: filtered.length });
}

/** 查询当前学员可自主报名的开课。 */
export async function mockGetLearnerOfferingPage(params: {
  pageNum: number;
  pageSize: number;
  'query.keyword'?: string;
}): Promise<ApiResponse<CourseLearningPage<LearnerCourseOffering>>> {
  const keyword = params['query.keyword']?.trim();
  const filtered = offerings.filter((item) => !keyword || `${item.courseName}${item.offeringName}`.includes(keyword));
  const start = (params.pageNum - 1) * params.pageSize;
  return success({ list: filtered.slice(start, start + params.pageSize), total: filtered.length });
}

/** 自主报名，模拟后端幂等返回报名 ID。 */
export async function mockSelfEnroll(offeringId: number): Promise<ApiResponse<number>> {
  const offering = offerings.find((item) => item.offeringId === offeringId);
  if (!offering) return failure(5061, '开课不存在');

  const existing = enrollments.find((item) => item.offeringId === offeringId);
  if (existing) return success(existing.enrollmentId);

  const enrollmentId = nextEnrollmentId++;
  enrollments.push({ ...offering, enrollmentId, learningStatus: 'NOT_STARTED', progressPercent: 0, lastStudyTime: null, completedTime: null });
  return success(enrollmentId);
}

/** 查询本人学习任务快照。 */
export async function mockGetLearnerCourseTasks(enrollmentId: number): Promise<ApiResponse<LearnerCourseTasks>> {
  const enrollment = enrollments.find((item) => item.enrollmentId === enrollmentId);
  if (!enrollment) return failure(5071, '报名不存在');
  return success({
    ...enrollment,
    chapters: [
      {
        chapterId: 1,
        chapterName: '第一章 安全基础',
        sortOrder: 1,
        items: [
          {
            courseItemId: 101,
            itemType: 'ASSET',
            itemTitle: '事故案例学习',
            isRequired: 1,
            sortOrder: 1,
            progressStatus: enrollment.learningStatus === 'COMPLETED' ? 'COMPLETED' : 'LEARNING',
            studySeconds: 300,
            lastPositionSeconds: 300,
            lastPageNo: null,
            completedTime: enrollment.completedTime,
            lastStudyTime: enrollment.lastStudyTime,
            learnable: enrollment.learningStatus !== 'EXPIRED',
            unavailableReason: enrollment.learningStatus === 'EXPIRED' ? 'ENROLLMENT_EXPIRED' : null,
            assetId: 501,
            questionBankId: null, paperVersionId: null,
            liveSession: null
          },
          {
            courseItemId: 102,
            itemType: 'LIVE',
            itemTitle: '应急演练直播',
            isRequired: 0,
            sortOrder: 2,
            progressStatus: 'NOT_STARTED',
            studySeconds: 0,
            lastPositionSeconds: null,
            lastPageNo: null,
            completedTime: null,
            lastStudyTime: null,
            learnable: true,
            unavailableReason: null,
            assetId: null,
            questionBankId: null, paperVersionId: null,
            liveSession: {
              liveSessionId: 801,
              liveName: '应急演练直播',
              teacherUserId: 66,
              liveUrl: null,
              startTime: '2026-08-01T09:00:00',
              endTime: '2026-08-01T10:00:00',
              liveStatus: 'SCHEDULED'
            }
          },
          {
            courseItemId: 103,
            itemType: 'QUIZ',
            itemTitle: '安全生产课程测验',
            isRequired: 1,
            sortOrder: 3,
            progressStatus: 'NOT_STARTED',
            studySeconds: 0,
            lastPositionSeconds: null,
            lastPageNo: null,
            completedTime: null,
            lastStudyTime: null,
            learnable: true,
            unavailableReason: null,
            assetId: null,
            questionBankId: null,
            paperVersionId: 1,
            activeExamAttemptId: null,
            publishedExamAttemptId: null,
            examTaskStatus: 'NOT_STARTED',
            scorePublishStatus: 'UNPUBLISHED',
            isPassed: null,
            finalScore: null,
            liveSession: null
          },
          {
            courseItemId: 104,
            itemType: 'QUESTION_BANK',
            itemTitle: '安全理论题库练习',
            isRequired: 0,
            sortOrder: 4,
            progressStatus: 'LEARNING',
            studySeconds: 0,
            lastPositionSeconds: null,
            lastPageNo: null,
            completedTime: null,
            lastStudyTime: enrollment.lastStudyTime,
            learnable: true,
            unavailableReason: null,
            assetId: null,
            questionBankId: 2,
            paperVersionId: null,
            activeExamAttemptId: 902,
            publishedExamAttemptId: null,
            examTaskStatus: 'IN_PROGRESS',
            scorePublishStatus: 'UNPUBLISHED',
            isPassed: null,
            finalScore: null,
            liveSession: null
          },
          {
            courseItemId: 105,
            itemType: 'QUIZ',
            itemTitle: '课程测验成绩回看',
            isRequired: 0,
            sortOrder: 5,
            progressStatus: 'COMPLETED',
            studySeconds: 0,
            lastPositionSeconds: null,
            lastPageNo: null,
            completedTime: enrollment.completedTime,
            lastStudyTime: enrollment.lastStudyTime,
            learnable: true,
            unavailableReason: null,
            assetId: null,
            questionBankId: null,
            paperVersionId: 3,
            activeExamAttemptId: null,
            publishedExamAttemptId: 903,
            examTaskStatus: 'RESULT_EFFECTIVE',
            scorePublishStatus: 'EFFECTIVE',
            isPassed: 1,
            finalScore: 86,
            liveSession: null
          }
        ]
      }
    ]
  });
}

/** 返回学习详情页使用的课程简介和附件聚合数据。 */
export async function mockGetLearnerCourseDetail(enrollmentId: number): Promise<ApiResponse<LearnerCourseDetail>> {
  const enrollment = enrollments.find((item) => item.enrollmentId === enrollmentId);
  if (!enrollment) return failure(5071, '报名不存在');
  return success({
    courseEnrollmentId: enrollment.enrollmentId,
    courseOfferingId: enrollment.offeringId,
    userId: 1,
    enrollmentSource: 'ASSIGN',
    enrolledTime: enrollment.startTime ?? new Date().toISOString(),
    learningStatus: enrollment.learningStatus,
    progressPercent: enrollment.progressPercent,
    totalStudySeconds: 300,
    creditAwarded: 0,
    completedTime: enrollment.completedTime,
    lastStudyTime: enrollment.lastStudyTime,
    course: {
      courseId: enrollment.courseId,
      courseCode: `COURSE-${enrollment.courseId}`,
      courseName: enrollment.courseName,
      coverUrl: enrollment.coverUrl,
      introduction: '面向学员的安全教育课程简介。',
      teachingObjective: '掌握课程要求的安全知识和操作要点。',
      syllabus: '课程目录请参见右侧章节。',
      assessmentMethod: '完成必修内容后按课程规则判定。',
      contentMode: 'CHAPTER',
      credit: 1,
      pointMode: 'NONE',
      pointValue: 0,
      sourceDownloadMode: 'ALL',
      isSpeedPlayAllowed: 1,
      isStudyAnticheatEnabled: 1,
      isStudyDescriptionEnabled: 1,
      isWatermarkEnabled: 1
    },
    downloadAssets: [{
      assetId: 501,
      assetName: '课程附件示例',
      assetType: 'DOCUMENT',
      fileName: '课程附件示例.pdf',
      mimeType: 'application/pdf',
      fileSize: 1024,
      fileInfoId: 601,
      sortOrder: 1,
      status: 1,
      downloadable: true,
      downloadUrl: `/api/course/learning/enrollments/${enrollmentId}/download-assets/501/file`,
      unavailableReason: null
    }],
    itemProgresses: [],
    evaluation: evaluations.get(enrollmentId) ?? null
  });
}

/** 模拟从课程题库练习或课程测验内容项启动的本人作答快照。 */
export async function mockStartCourseExamAttempt(
  enrollmentId: number,
  itemId: number,
  _data: { clientAttemptToken: string; platform?: string }
): Promise<ApiResponse<CourseAssessmentAttempt>> {
  if (_data.clientAttemptToken === 'mock-assessment-snapshot-invalid') {
    return failure(7063, '考核执行快照已损坏');
  }
  if (!enrollments.some((item) => item.enrollmentId === enrollmentId) || ![103, 104, 105].includes(itemId)) return failure(5071, '课程考核任务不可用。');
  const existing = [...courseAssessmentAttempts.values()].find((item) => item.assessmentExecutionId === 800 + itemId);
  if (existing && itemId !== 104) return success(existing);
  const attempt: CourseAssessmentAttempt = {
    examAttemptId: itemId === 104 ? 902 : itemId === 105 ? 903 : 901,
    assessmentExecutionId: 800 + itemId,
    sourceType: itemId === 104 ? 'COURSE_BANK_PRACTICE' : 'COURSE_QUIZ',
    assessmentKind: itemId === 104 ? 'BANK_PRACTICE' : 'QUIZ',
    resourceType: itemId === 104 ? 'QUESTION_BANK' : 'PAPER_VERSION',
    resourceId: itemId === 104 ? 2 : itemId === 105 ? 3 : 1,
    resourceName: itemId === 104 ? '安全理论题库' : '安全生产课程测验',
    paperVersionId: itemId === 104 ? null : 1,
    attemptNo: 1,
    attemptStatus: 'ANSWERING',
    finalScore: null,
    isPassed: null,
    scorePublishStatus: 'UNPUBLISHED',
    resultEffectiveTime: null,
    scoreVisible: false,
    questions: []
  };
  courseAssessmentAttempts.set(attempt.examAttemptId, attempt);
  return success(attempt);
}

/** 读取 Mock 课程作答，保持课程与独立考试 Mock 状态隔离。 */
export async function mockGetCourseAssessmentAttempt(
  enrollmentId: number, itemId: number, attemptId: number
): Promise<ApiResponse<CourseAssessmentAttempt>> {
  const attempt = courseAssessmentAttempts.get(attemptId) ?? await mockStartCourseExamAttempt(enrollmentId, itemId, { clientAttemptToken: 'mock-resume' }).then((response) => response.data);
  return attempt ? success(attempt) : failure(5071, '课程考核任务不可用。');
}

/** 暂存 Mock 课程作答答案。 */
export async function mockSaveCourseAssessmentAnswers(
  enrollmentId: number, itemId: number, attemptId: number, data: SaveLearnerExamAnswersPayload
): Promise<ApiResponse<void>> {
  const response = await mockGetCourseAssessmentAttempt(enrollmentId, itemId, attemptId);
  if (response.code !== 0 || !response.data) return failure(5071, '课程考核任务不可用。');
  if (response.data.attemptStatus !== 'ANSWERING') return failure(7101, '当前作答不可修改。');
  response.data.questions.forEach((question) => {
    const answer = data.answers.find((item) => item.attemptQuestionId === question.attemptQuestionId);
    if (answer) question.answer = { ...answer, isAnswered: answer.answerText || answer.items.length ? 1 : 0 };
  });
  courseAssessmentAttempts.set(attemptId, response.data);
  return success(undefined);
}

/** 提交 Mock 课程作答，模拟课程结果自动生效。 */
export async function mockSubmitCourseAssessmentAttempt(
  enrollmentId: number, itemId: number, attemptId: number, _data: SubmitLearnerExamAttemptPayload
): Promise<ApiResponse<CourseAssessmentAttempt>> {
  void _data;
  const response = await mockGetCourseAssessmentAttempt(enrollmentId, itemId, attemptId);
  if (response.code !== 0 || !response.data) return failure(5071, '课程考核任务不可用。');
  const submitted = { ...response.data, attemptStatus: 'RESULT_EFFECTIVE', scorePublishStatus: 'EFFECTIVE' as const, scoreVisible: true, finalScore: 100, isPassed: 1 as const, resultEffectiveTime: new Date().toISOString() };
  courseAssessmentAttempts.set(attemptId, submitted);
  return success(submitted);
}

/** 按 Contract 状态限制退选。 */
export async function mockWithdrawEnrollment(enrollmentId: number): Promise<ApiResponse<null>> {
  const index = enrollments.findIndex((item) => item.enrollmentId === enrollmentId);
  const enrollment = enrollments[index];
  if (!enrollment) return failure(5071, '报名不存在');
  if (enrollment.courseType !== 'ELECTIVE' || !['NOT_STARTED', 'LEARNING'].includes(enrollment.learningStatus))
    return failure(5073, '当前报名不允许退选');
  enrollments.splice(index, 1);
  return success(null);
}

/** 仅在已完成课程提交一次评价。 */
export async function mockSubmitCourseEvaluation(enrollmentId: number, data: CourseEvaluationSubmit): Promise<ApiResponse<number>> {
  const enrollment = enrollments.find((item) => item.enrollmentId === enrollmentId);
  if (!enrollment) return failure(5071, '报名不存在');
  if (enrollment.learningStatus !== 'COMPLETED') return failure(5082, '课程尚未完成，不能评价');
  if (evaluations.has(enrollmentId)) return failure(5085, '该报名已提交评价');
  if (data.rating < 0 || data.rating > 5) return failure(1001, '评分必须在 0 到 5 之间');
  const submittedTime = new Date().toISOString();
  evaluations.set(enrollmentId, { ...data, courseEvaluationId: enrollmentId, submittedTime });
  peerEvaluations.set(enrollment.offeringId, [
    { rating: data.rating, content: data.content ?? null, isAnonymous: data.isAnonymous,
      displayName: data.isAnonymous === 1 ? '匿名学员' : '当前学员', submittedTime: data.isAnonymous === 1 ? submittedTime.slice(0, 10) : submittedTime },
    ...(peerEvaluations.get(enrollment.offeringId) ?? [])
  ]);
  return success(enrollmentId);
}

/** 按本人报名的开课范围分页返回公开评价。 */
export async function mockGetPeerCourseEvaluations(enrollmentId: number, params: { pageNum: number; pageSize: number }): Promise<ApiResponse<CourseLearningPage<CourseEvaluationListItem>>> {
  const enrollment = enrollments.find((item) => item.enrollmentId === enrollmentId);
  if (!enrollment) return failure(5071, '报名不存在');
  const list = peerEvaluations.get(enrollment.offeringId) ?? [];
  const start = (params.pageNum - 1) * params.pageSize;
  return success({ list: list.slice(start, start + params.pageSize), total: list.length });
}

/** 按报名与客户端追踪标识幂等记录学习会话。 */
export async function mockReportStudySession(data: StudySessionReport): Promise<ApiResponse<number>> {
  if (!enrollments.some((item) => item.enrollmentId === data.enrollmentId)) return failure(5071, '报名不存在');
  const key = `${data.enrollmentId}:${data.clientTraceId}`;
  const sessionId = sessionIds.get(key) ?? nextSessionId++;
  sessionIds.set(key, sessionId);
  return success(sessionId);
}

/** Mock未配置公开分类时返回空树；真实分区由只读接口提供。 */
export async function mockGetLearnerCourseCategories(): Promise<ApiResponse<LearnerCourseCategory[]>> {
  return success([]);
}

/** Mock仅模拟合法直播跳转，始终不授予第三方观看学时。 */
export async function mockParticipateCourseLive(enrollmentId: number, itemId: number): Promise<ApiResponse<{ liveUrl: string; progressStatus: 'COMPLETED' }>> {
  const response = await mockGetLearnerCourseTasks(enrollmentId);
  if (response.code !== 0) return failure(response.code, response.message);
  const item = response.data.chapters.flatMap(ch => ch.items).find(row => row.courseItemId === itemId);
  const live = item?.liveSession;
  const now = Date.now();
  if (!item?.learnable || item.itemType !== 'LIVE' || !live || !live.liveUrl || !live.liveStatus || !live.startTime || !live.endTime
    || !['SCHEDULED', 'LIVING'].includes(live.liveStatus)
    || now < Date.parse(live.startTime) || now > Date.parse(live.endTime)) return failure(5086, '当前直播不可参加');
  return success({ liveUrl: live.liveUrl, progressStatus: 'COMPLETED' });
}
