import type { LearnerCourseCategory } from '@shared/types/course-learning';
import type {
  ApiResponse,
  CourseAssessmentAttempt,
  CourseEvaluation,
  CourseEvaluationListItem,
  CourseEvaluationSubmit,
  CourseLearningPage,
  LearnerCourseDetail,
  LearnerCourseEnrollment,
  LearnerCourseOffering,
  LearnerCourseTaskItem,
  LearnerCourseTasks,
  SaveLearnerExamAnswersPayload,
  StudySessionReport,
  SubmitLearnerExamAttemptPayload
} from '@shared/types';
import { COURSE_ASSESSMENT_ATTEMPT_STATUS, COURSE_ASSESSMENT_TASK_STATUS } from '@/constants';

/** 移动端课程 Contract Mock；TODO replace real api：后端联调通过后删除本地分支。 */
const courses: LearnerCourseEnrollment[] = [
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
/** 可自主报名的开课 Mock；字段与学员 Offerings Contract 保持一致，便于与 PC 学习中心对照。 */
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
const sessions = new Map<string, number>();
const evaluations = new Map<number, CourseEvaluation>();
/** 评价按开课隔离，模拟同课其他学员已提交的公开记录。 */
const peerEvaluations = new Map<number, CourseEvaluationListItem[]>([
  [102, [
    { rating: 4.5, content: '内容实用，案例清晰。', isAnonymous: 1, displayName: '匿名学员', submittedTime: '2026-07-20' },
    { rating: 5, content: '课程安排合理。', isAnonymous: 0, displayName: '王学员', submittedTime: '2026-07-19T14:30:00' }
  ]]
]);
let nextSessionId = 1;
let nextEnrollmentId = 2000;
const assessmentAttempts = new Map<string, CourseAssessmentAttempt>();
let nextAssessmentAttemptId = 901;

/** 返回指定课程内容项当前可恢复或回看的课程考核作答。 */
function assessmentKey(enrollmentId: number, itemId: number): string {
  return `${enrollmentId}:${itemId}`;
}

/** 构造课程题库练习和课程测验的冻结题目，题型与课程 Contract 保持一致。 */
function createAssessmentQuestions(itemId: number) {
  const objectiveQuestion = {
    attemptQuestionId: itemId * 100 + 1,
    questionId: itemId * 10 + 1,
    questionVersionId: itemId * 1000 + 1,
    questionType: 'SINGLE',
    stemSnapshot: '安全生产工作的基本方针是？',
    analysisSnapshot: '坚持安全第一、预防为主、综合治理。',
    standardAnswer: 'A',
    gradingMode: 'AUTO',
    score: 1,
    sortOrder: 1,
    options: [
      { optionKey: 'A', optionContentSnapshot: '安全第一、预防为主、综合治理', isCorrectSnapshot: 1 as const, sortOrder: 1 },
      { optionKey: 'B', optionContentSnapshot: '先生产、后安全', isCorrectSnapshot: 0 as const, sortOrder: 2 }
    ],
    answer: null
  };
  if (itemId === 105) {
    return [
      objectiveQuestion,
      {
        attemptQuestionId: itemId * 100 + 2,
        questionId: itemId * 10 + 2,
        questionVersionId: itemId * 1000 + 2,
        questionType: 'ESSAY',
        stemSnapshot: '请简述发现事故隐患后的处置要点。',
        analysisSnapshot: null,
        standardAnswer: null,
        gradingMode: 'MANUAL',
        score: 5,
        sortOrder: 2,
        options: [],
        answer: null
      }
    ];
  }
  return [objectiveQuestion];
}

/** 将 Mock 中的课程考核事实转换为课程目录内容项。 */
function assessmentTask(itemId: number, itemType: 'QUESTION_BANK' | 'QUIZ', itemTitle: string): LearnerCourseTaskItem {
  const attempt = assessmentAttempts.get(assessmentKey(1000, itemId));
  const effective = attempt?.scorePublishStatus === 'EFFECTIVE';
  const examTaskStatus = !attempt
    ? 'NOT_STARTED'
    : effective
      ? COURSE_ASSESSMENT_TASK_STATUS.RESULT_EFFECTIVE
      : attempt.attemptStatus === COURSE_ASSESSMENT_ATTEMPT_STATUS.ANSWERING
        ? COURSE_ASSESSMENT_TASK_STATUS.IN_PROGRESS
        : COURSE_ASSESSMENT_TASK_STATUS.WAITING_RESULT;
  return {
    courseItemId: itemId,
    itemType,
    itemTitle,
    isRequired: 1,
    sortOrder: itemId - 100,
    progressStatus: effective ? 'COMPLETED' : attempt ? 'LEARNING' : 'NOT_STARTED',
    studySeconds: 0,
    lastPositionSeconds: null,
    lastPageNo: null,
    completedTime: effective ? attempt?.resultEffectiveTime ?? null : null,
    lastStudyTime: effective ? attempt?.resultEffectiveTime ?? null : null,
    learnable: true,
    unavailableReason: null,
    assetId: null,
    questionBankId: itemType === 'QUESTION_BANK' ? 2 : null,
    paperVersionId: itemType === 'QUIZ' ? 1 : null,
    activeExamAttemptId: attempt && !effective ? attempt.examAttemptId : null,
    publishedExamAttemptId: effective ? attempt?.examAttemptId ?? null : null,
    examTaskStatus,
    scorePublishStatus: attempt?.scorePublishStatus ?? 'UNPUBLISHED',
    isPassed: attempt?.isPassed ?? null,
    finalScore: attempt?.finalScore ?? null,
    liveSession: null
  };
}

/** 构建统一成功 Result 外壳。 */
function success<T>(data: T): ApiResponse<T> {
  return { code: 0, message: 'success', data };
}

/** 构建学员不可访问时的业务失败外壳。 */
function failure<T = null>(code: number, message: string): ApiResponse<T> {
  return { code, message, data: null as T };
}

/** 查询移动端当前学员报名课程。 */
export async function mockGetLearnerCoursePage(params: {
  pageNum: number;
  pageSize: number;
  'query.learningStatus'?: LearnerCourseEnrollment['learningStatus'];
}): Promise<ApiResponse<CourseLearningPage<LearnerCourseEnrollment>>> {
  const filtered = courses.filter((item) => !params['query.learningStatus'] || item.learningStatus === params['query.learningStatus']);
  const start = (params.pageNum - 1) * params.pageSize;
  return success({ list: filtered.slice(start, start + params.pageSize), total: filtered.length });
}

/** 查询移动端可自主报名的开课。 */
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

/** 自助报名，模拟后端幂等：已报名直接返回既有报名 ID，不重复创建。 */
export async function mockSelfEnrollCourse(offeringId: number): Promise<ApiResponse<number>> {
  const offering = offerings.find((item) => item.offeringId === offeringId);
  if (!offering) return failure(5061, '开课不存在');

  const existing = courses.find((item) => item.offeringId === offeringId);
  if (existing) return success(existing.enrollmentId);

  const enrollmentId = nextEnrollmentId++;
  courses.push({
    ...offering,
    enrollmentId,
    learningStatus: 'NOT_STARTED',
    progressPercent: 0,
    lastStudyTime: null,
    completedTime: null
  });
  return success(enrollmentId);
}

/** 查询移动端本人学习任务。 */
export async function mockGetLearnerCourseTasks(enrollmentId: number): Promise<ApiResponse<LearnerCourseTasks>> {
  const course = courses.find((item) => item.enrollmentId === enrollmentId);
  if (!course) return failure(5071, '报名不存在');
  return success({
    ...course,
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
            progressStatus: course.learningStatus === 'COMPLETED' ? 'COMPLETED' : 'LEARNING',
            studySeconds: 300,
            lastPositionSeconds: 300,
            lastPageNo: null,
            completedTime: course.completedTime,
            lastStudyTime: course.lastStudyTime,
            learnable: true,
            unavailableReason: null,
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
          assessmentTask(103, 'QUIZ', '安全生产课程测验'),
          assessmentTask(104, 'QUESTION_BANK', '安全生产题库练习'),
          assessmentTask(105, 'QUIZ', '含主观题课程测验')
        ]
      }
    ]
  });
}

/** 返回移动端课程简介与附件的最小 Contract Mock。 */
export async function mockGetLearnerCourseDetail(enrollmentId: number): Promise<ApiResponse<LearnerCourseDetail>> {
  const course = courses.find((item) => item.enrollmentId === enrollmentId);
  if (!course) return failure(5071, '报名不存在。');
  return success({
    courseEnrollmentId: course.enrollmentId,
    courseOfferingId: course.offeringId,
    userId: 1,
    enrollmentSource: 'ASSIGNED',
    enrolledTime: course.startTime ?? new Date().toISOString(),
    learningStatus: course.learningStatus,
    progressPercent: course.progressPercent,
    totalStudySeconds: 300,
    creditAwarded: 0,
    completedTime: course.completedTime,
    lastStudyTime: course.lastStudyTime,
    course: {
      courseId: course.courseId,
      courseCode: `COURSE-${course.courseId}`,
      courseName: course.courseName,
      coverUrl: course.coverUrl,
      introduction: '面向学员的安全教育课程简介。',
      teachingObjective: '掌握课程要求的安全知识和操作要点。',
      syllabus: '课程目录请参见学习章节。',
      assessmentMethod: '完成必修内容后按课程规则判定。',
      contentMode: 'MULTI_CHAPTER',
      credit: null,
      pointMode: 'NONE',
      pointValue: null,
      sourceDownloadMode: 'PARTIAL',
      isSpeedPlayAllowed: 1,
      isStudyAnticheatEnabled: 1,
      isStudyDescriptionEnabled: 1,
      isWatermarkEnabled: 1
    },
    downloadAssets: [],
    itemProgresses: [],
    evaluation: evaluations.get(enrollmentId) ?? null
  });
}

/** 模拟已完成课程一报名一评价，并供本人详情回看。 */
export async function mockSubmitCourseEvaluation(enrollmentId: number, data: CourseEvaluationSubmit): Promise<ApiResponse<number>> {
  const course = courses.find((item) => item.enrollmentId === enrollmentId);
  if (!course) return failure(5071, '报名不存在');
  if (course.learningStatus !== 'COMPLETED') return failure(5082, '仅已完成学习可提交评价');
  if (evaluations.has(enrollmentId)) return failure(5085, '该报名已提交评价');
  if (data.rating < 0 || data.rating > 5 || (data.content?.length ?? 0) > 2000) return failure(1001, '课程评价参数不合法');
  const submittedTime = new Date().toISOString();
  evaluations.set(enrollmentId, { ...data, courseEvaluationId: enrollmentId, submittedTime });
  peerEvaluations.set(course.offeringId, [
    { rating: data.rating, content: data.content ?? null, isAnonymous: data.isAnonymous,
      displayName: data.isAnonymous === 1 ? '匿名学员' : '当前学员', submittedTime: data.isAnonymous === 1 ? submittedTime.slice(0, 10) : submittedTime },
    ...(peerEvaluations.get(course.offeringId) ?? [])
  ]);
  return success(enrollmentId);
}

/** 按当前学员的报名范围分页查询同次开课评价。 */
export async function mockGetPeerCourseEvaluations(enrollmentId: number, params: { pageNum: number; pageSize: number }): Promise<ApiResponse<CourseLearningPage<CourseEvaluationListItem>>> {
  const course = courses.find((item) => item.enrollmentId === enrollmentId);
  if (!course) return failure(5071, '报名不存在');
  const list = peerEvaluations.get(course.offeringId) ?? [];
  const start = (params.pageNum - 1) * params.pageSize;
  return success({ list: list.slice(start, start + params.pageSize), total: list.length });
}

/** 按客户端追踪标识幂等上报移动学习会话。 */
export async function mockReportStudySession(data: StudySessionReport): Promise<ApiResponse<number>> {
  if (!courses.some((item) => item.enrollmentId === data.enrollmentId)) return failure(5071, '报名不存在');
  const key = `${data.enrollmentId}:${data.clientTraceId}`;
  const sessionId = sessions.get(key) ?? nextSessionId++;
  sessions.set(key, sessionId);
  return success(sessionId);
}

/** 模拟从课程题库练习或课程测验内容项启动的作答快照。 */
export async function mockStartCourseExamAttempt(enrollmentId: number, itemId: number, data: {
  clientAttemptToken: string;
  platform?: string
}): Promise<ApiResponse<CourseAssessmentAttempt>> {
  if (data.clientAttemptToken === 'mock-assessment-snapshot-invalid') {
    return failure(7063, '考核执行快照已损坏');
  }
  if (!courses.some((item) => item.enrollmentId === enrollmentId) || ![103, 104, 105].includes(itemId)) {
    return failure(5071, '课程考核任务不可用。');
  }
  const key = assessmentKey(enrollmentId, itemId);
  const existing = assessmentAttempts.get(key);
  if (existing && existing.attemptStatus !== COURSE_ASSESSMENT_ATTEMPT_STATUS.RESULT_EFFECTIVE) return success(existing);
  const questionBank = itemId === 104;
  const attempt: CourseAssessmentAttempt = {
    examAttemptId: nextAssessmentAttemptId++,
    assessmentExecutionId: 900 + itemId,
    sourceType: questionBank ? 'COURSE_BANK_PRACTICE' : 'COURSE_QUIZ',
    assessmentKind: questionBank ? 'BANK_PRACTICE' : 'QUIZ',
    resourceType: questionBank ? 'QUESTION_BANK' : 'PAPER_VERSION',
    resourceId: questionBank ? 2 : 1,
    resourceName: questionBank ? '安全生产题库练习' : itemId === 105 ? '含主观题课程测验' : '安全生产课程测验',
    paperVersionId: questionBank ? null : 1,
    attemptNo: 1,
    attemptStatus: COURSE_ASSESSMENT_ATTEMPT_STATUS.ANSWERING,
    finalScore: null,
    isPassed: null,
    scorePublishStatus: 'UNPUBLISHED',
    resultEffectiveTime: null,
    scoreVisible: false,
    questions: createAssessmentQuestions(itemId)
  };
  assessmentAttempts.set(key, attempt);
  return success(attempt);
}

/** 查询 Mock 中属于当前报名和内容项的课程考核作答快照。 */
export async function mockGetCourseAssessmentAttempt(
  enrollmentId: number,
  itemId: number,
  attemptId: number
): Promise<ApiResponse<CourseAssessmentAttempt>> {
  const attempt = assessmentAttempts.get(assessmentKey(enrollmentId, itemId));
  if (!attempt || attempt.examAttemptId !== attemptId) return failure(7020, '课程作答不存在。');
  return success(attempt);
}

/** 暂存 Mock 作答的全量答案载荷。 */
export async function mockSaveCourseAssessmentAnswers(
  enrollmentId: number,
  itemId: number,
  attemptId: number,
  data: SaveLearnerExamAnswersPayload
): Promise<ApiResponse<void>> {
  const result = await mockGetCourseAssessmentAttempt(enrollmentId, itemId, attemptId);
  if (result.code !== 0) return failure(result.code, result.message);
  if (result.data.attemptStatus !== COURSE_ASSESSMENT_ATTEMPT_STATUS.ANSWERING) return failure(7021, '当前尝试不可作答。');
  for (const answer of data.answers) {
    const question = result.data.questions.find((item) => item.attemptQuestionId === answer.attemptQuestionId);
    if (!question) return failure(7022, '作答题目不存在。');
    question.answer = {
      attemptQuestionId: answer.attemptQuestionId,
      answerText: answer.answerText,
      items: answer.items,
      isAnswered: answer.answerText?.trim() || answer.items.length ? 1 : 0
    };
  }
  return success(undefined);
}

/** 提交 Mock 课程考核；主观题等待批阅，其余题目立即自动生效。 */
export async function mockSubmitCourseAssessmentAttempt(
  enrollmentId: number,
  itemId: number,
  attemptId: number,
  data: SubmitLearnerExamAttemptPayload
): Promise<ApiResponse<CourseAssessmentAttempt>> {
  void data;
  const result = await mockGetCourseAssessmentAttempt(enrollmentId, itemId, attemptId);
  if (result.code !== 0) return result;
  const attempt = result.data;
  if (attempt.attemptStatus !== COURSE_ASSESSMENT_ATTEMPT_STATUS.ANSWERING) return success(attempt);
  if (attempt.assessmentKind === 'BANK_PRACTICE' && attempt.questions.some((question) => question.answer?.isAnswered !== 1)) {
    return failure(7064, '题库练习需完成全部题目后提交。');
  }
  if (attempt.questions.some((question) => question.gradingMode === 'MANUAL')) {
    attempt.attemptStatus = COURSE_ASSESSMENT_ATTEMPT_STATUS.PENDING_REVIEW;
    return success(attempt);
  }
  let correctCount = 0;
  for (const question of attempt.questions) {
    const correct = question.answer?.answerText === question.standardAnswer;
    if (correct) correctCount += 1;
    if (question.answer) {
      question.answer.isCorrect = correct ? 1 : 0;
      question.answer.finalScore = correct ? question.score : 0;
      question.answer.gradingStatus = 'AUTO_GRADED';
    }
  }
  attempt.attemptStatus = COURSE_ASSESSMENT_ATTEMPT_STATUS.RESULT_EFFECTIVE;
  attempt.finalScore = correctCount;
  attempt.isPassed = correctCount === attempt.questions.length ? 1 : 0;
  attempt.scorePublishStatus = 'EFFECTIVE';
  attempt.resultEffectiveTime = '2026-08-28T10:00:00';
  attempt.scoreVisible = true;
  return success(attempt);
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
