import type {ApiResponse, LearnerExamAttempt} from '@shared/types';
import type {
  ExamAnticheatEvent,
  ExamScorePublishLog,
  PracticeResetPayload,
  ReviewExamAnswerPayload
} from '@/api/exam-grading';
import type {
  ExamAttemptRecord,
  ExamAttemptRecordPageQuery,
  ExamGradingPage,
  ExamReviewTask,
  ExamReviewTaskPageQuery
} from '@/api/exam-grading';

/** TODO replace real api：教师批阅与成绩处置 Contract Mock。 */
const success = <T>(data: T): Promise<ApiResponse<T>> => Promise.resolve({code: 0, message: 'success', data});
const failed = <T>(code: number, message: string): Promise<ApiResponse<T>> => Promise.resolve({
  code,
  message,
  data: null as T
});
let attempt: LearnerExamAttempt = {
  assessmentExecutionId: 1002,
  sourceType: 'STANDALONE_EXAM',
  resourceType: 'PAPER_VERSION',
  resourceId: 12,
  resourceName: '危险作业规范试卷',
  examAttemptId: 9,
  examId: 2,
  examCandidateId: 5,
  userId: 3,
  paperVersionId: 12,
  attemptNo: 1,
  attemptType: 'FIRST',
  attemptStatus: 'SUBMITTED',
  startedTime: '2026-07-29T09:00:00',
  deadlineTime: '2026-07-29T10:00:00',
  submittedTime: '2026-07-29T09:30:00',
  usedSeconds: 1800,
  finalScore: null,
  isPassed: null,
  correctCount: null,
  wrongCount: null,
  scorePublishStatus: 'UNPUBLISHED',
  scorePublishedTime: null,
  scoreVisible: false,
  retakeEligibility: {
    eligible: false,
    reasons: ['当前考生不在补考范围'],
    usedRetakeCount: 0,
    maxRetakeCount: 1
  },
  questions: [{
    attemptQuestionId: 101,
    questionId: 1,
    questionVersionId: 1,
    questionType: 'SINGLE_CHOICE',
    stemSnapshot: '安全帽应在何时佩戴？',
    gradingMode: 'AUTO',
    score: 10,
    sortOrder: 1,
    options: [],
    answer: {
      examAnswerId: 11,
      attemptQuestionId: 101,
      answerText: 'A',
      items: [],
      isAnswered: 1,
      gradingStatus: 'AUTO_GRADED',
      autoScore: 10,
      manualScore: null,
      finalScore: 10,
      isCorrect: 1
    }
  }, {
    attemptQuestionId: 102,
    questionId: 2,
    questionVersionId: 1,
    questionType: 'ESSAY',
    stemSnapshot: '简述发现安全隐患后的处置流程。',
    gradingMode: 'MANUAL',
    score: 20,
    sortOrder: 2,
    options: [],
    answer: {
      examAnswerId: 20,
      attemptQuestionId: 102,
      answerText: '立即报告，设置警戒，按流程整改并复查。',
      items: [],
      isAnswered: 1,
      gradingStatus: 'PENDING_REVIEW',
      autoScore: null,
      manualScore: null,
      finalScore: null,
      isCorrect: null
    }
  }]
};
let latestReviewComment: string | null = null;
let latestReviewedTime: string | null = null;
const anticheatEvents: ExamAnticheatEvent[] = [{
  anticheatEventId: 1,
  examAttemptId: 9,
  eventType: 'SWITCH_AWAY',
  eventTime: '2026-07-29T09:15:00',
  eventCount: 1,
  actionTaken: 'WARN'
}];
const scorePublishLogs: ExamScorePublishLog[] = [];

/** 按 Contract 分页截取 Mock 数据。 */
function paginate<T>(list: T[], pageNum: number, pageSize: number): ExamGradingPage<T> {
  const start = (pageNum - 1) * pageSize;
  return {list: list.slice(start, start + pageSize), total: list.length};
}

/** 由当前主观题快照生成教师批阅任务列表。 */
function createReviewTasks(): ExamReviewTask[] {
  return attempt.questions
    .filter((question) => question.gradingMode === 'MANUAL' && question.answer?.examAnswerId)
    .map((question) => ({
      assessmentExecutionId: attempt.assessmentExecutionId,
      sourceType: attempt.sourceType,
      sourceBusinessId: attempt.examId,
      sourceName: '危化品作业规范考试',
      examId: attempt.examId,
      examName: '危化品作业规范考试',
      examCandidateId: attempt.examCandidateId,
      examAttemptId: attempt.examAttemptId,
      attemptNo: attempt.attemptNo,
      attemptStatus: attempt.attemptStatus,
      examAnswerId: question.answer!.examAnswerId!,
      attemptQuestionId: question.attemptQuestionId,
      questionType: question.questionType,
      stemSnapshot: question.stemSnapshot,
      score: question.score,
      answerText: question.answer!.answerText,
      learnerUserId: attempt.userId,
      learnerName: '张三',
      learnerCode: 'STU-0003',
      learnerOrganizationId: 1,
      learnerOrganizationName: '示例企业',
      submittedTime: attempt.submittedTime,
      reviewStatus: question.answer!.gradingStatus === 'MANUAL_GRADED' ? 'REVIEWED' : 'PENDING',
      latestManualScore: question.answer!.manualScore ?? null,
      latestReviewComment,
      latestReviewedTime,
      scorePublishStatus: attempt.scorePublishStatus,
      scorePublishedTime: attempt.scorePublishedTime
    }));
}

/** 由当前作答快照生成管理员成绩记录。 */
function createAttemptRecords(): ExamAttemptRecord[] {
  const manualQuestions = attempt.questions.filter((question) => question.gradingMode === 'MANUAL');
  const reviewStatus = manualQuestions.length === 0
    ? 'NOT_REQUIRED'
    : manualQuestions.every((question) => question.answer?.gradingStatus === 'MANUAL_GRADED') ? 'COMPLETED' : 'PENDING';
  return [{
    assessmentExecutionId: attempt.assessmentExecutionId,
    sourceType: attempt.sourceType,
    sourceBusinessId: attempt.examId,
    sourceName: '危化品作业规范考试',
    examId: attempt.examId,
    examName: '危化品作业规范考试',
    examCandidateId: attempt.examCandidateId,
    examAttemptId: attempt.examAttemptId,
    attemptNo: attempt.attemptNo,
    attemptType: attempt.attemptType,
    attemptStatus: attempt.attemptStatus,
    learnerUserId: attempt.userId,
    learnerName: '张三',
    learnerCode: 'STU-0003',
    learnerOrganizationId: 1,
    learnerOrganizationName: '示例企业',
    startedTime: attempt.startedTime,
    submittedTime: attempt.submittedTime,
    usedSeconds: attempt.usedSeconds,
    reviewStatus,
    reviewCompletedTime: reviewStatus === 'COMPLETED' ? latestReviewedTime : null,
    objectiveScore: attempt.questions.reduce((sum, question) => sum + (question.answer?.autoScore ?? 0), 0),
    subjectiveScore: attempt.questions.reduce((sum, question) => sum + (question.answer?.manualScore ?? 0), 0),
    finalScore: attempt.finalScore,
    isPassed: attempt.isPassed,
    scorePublishStatus: attempt.scorePublishStatus,
    scorePublishedTime: attempt.scorePublishedTime,
    resultEffectiveTime: attempt.resultEffectiveTime ?? null
  }];
}

/** 分页查询当前教师有权处理的 Mock 批阅任务。 */
export const mockGetExamReviewTaskPage = (params: ExamReviewTaskPageQuery): Promise<ApiResponse<ExamGradingPage<ExamReviewTask>>> => {
  const keyword = params['query.learnerKeyword']?.trim();
  const filtered = createReviewTasks().filter((item) =>
    (!params['query.examId'] || item.examId === params['query.examId'])
    && (!params['query.reviewStatus'] || item.reviewStatus === params['query.reviewStatus'])
    && (!keyword || `${item.learnerName}${item.learnerCode ?? ''}`.includes(keyword))
  );
  return success(paginate(filtered, params.pageNum, params.pageSize));
};

/** 分页查询 Mock 考试成绩记录。 */
export const mockGetExamAttemptRecordPage = (params: ExamAttemptRecordPageQuery): Promise<ApiResponse<ExamGradingPage<ExamAttemptRecord>>> => {
  const keyword = params['query.learnerKeyword']?.trim();
  const filtered = createAttemptRecords().filter((item) =>
    (!params['query.examId'] || item.examId === params['query.examId'])
    && (!params['query.attemptStatus'] || item.attemptStatus === params['query.attemptStatus'])
    && (!params['query.reviewStatus'] || item.reviewStatus === params['query.reviewStatus'])
    && (!params['query.scorePublishStatus'] || item.scorePublishStatus === params['query.scorePublishStatus'])
    && (!keyword || `${item.learnerName}${item.learnerCode ?? ''}`.includes(keyword))
  );
  return success(paginate(filtered, params.pageNum, params.pageSize));
};
/** 获取用于教师批阅的快照副本。 */
export const mockGetReviewAttempt = (examAttemptId: number): Promise<ApiResponse<LearnerExamAttempt>> => examAttemptId === attempt.examAttemptId ? success(structuredClone(attempt)) : failed(7001, '作答尝试不存在。');
/** 查询 Mock 作答防作弊事件。 */
export const mockGetExamAnticheatEvents = (examAttemptId: number): Promise<ApiResponse<ExamAnticheatEvent[]>> =>
  examAttemptId === attempt.examAttemptId
    ? success(structuredClone(anticheatEvents))
    : failed(7001, '作答尝试不存在。');
/** 查询 Mock 正式成绩发布流水。 */
export const mockGetExamScorePublishLogs = (examAttemptId: number): Promise<ApiResponse<ExamScorePublishLog[]>> =>
  examAttemptId === attempt.examAttemptId
    ? success(structuredClone(scorePublishLogs))
    : failed(7001, '作答尝试不存在。');
/** 提交 Mock 主观题评分。 */
export const mockReviewExamAnswer = (examAnswerId: number, data: ReviewExamAnswerPayload): Promise<ApiResponse<void>> => {
  const question = attempt.questions.find((item) => item.answer?.examAnswerId === examAnswerId);
  if (!question?.answer) return failed(7001, '答案不存在。');
  if (data.afterScore < 0 || data.afterScore > question.score) return failed(1001, '批阅分数必须在题目分值范围内。');
  question.answer = {
    ...question.answer,
    manualScore: data.afterScore,
    reviewComment: data.reviewComment ?? null,
    finalScore: data.afterScore,
    gradingStatus: 'MANUAL_GRADED'
  };
  latestReviewComment = data.reviewComment ?? null;
  latestReviewedTime = new Date().toISOString();
  return success(undefined);
};
/** 发布 Mock 成绩前要求所有主观题已完成评分。 */
export const mockPublishExamScore = (examAttemptId: number, publishedBy?: number): Promise<ApiResponse<void>> => {
  if (examAttemptId !== attempt.examAttemptId) return failed(7001, '作答尝试不存在。');
  if (attempt.questions.some((item) => item.gradingMode === 'MANUAL' && item.answer?.gradingStatus !== 'MANUAL_GRADED')) return failed(7102, '仍有主观题待批阅，不能发布成绩。');
  const finalScore = attempt.questions.reduce((sum, item) => sum + (item.answer?.finalScore ?? 0), 0);
  attempt = {
    ...attempt,
    finalScore,
    isPassed: finalScore >= 18 ? 1 : 0,
    scorePublishStatus: 'PUBLISHED',
    scorePublishedTime: new Date().toISOString(),
    scoreVisible: true
  };
  scorePublishLogs.push({
    scorePublishLogId: scorePublishLogs.length + 1,
    examId: attempt.examId,
    examAttemptId,
    publishedBy: publishedBy ?? 1,
    publishedTime: attempt.scorePublishedTime!,
    publishType: 'MANUAL',
    scoreSnapshot: finalScore,
    resultSnapshot: attempt.isPassed ? 'PASSED' : 'FAILED'
  });
  return success(undefined);
};
/** 生成补考尝试快照。 */
export const mockCreateRetakeAttempt = (examCandidateId: number, clientAttemptToken: string): Promise<ApiResponse<LearnerExamAttempt>> => !clientAttemptToken.trim() ? failed(1001, '补考幂等令牌不能为空。') : examCandidateId === attempt.examCandidateId ? success({
  ...structuredClone(attempt),
  examAttemptId: 10,
  attemptType: 'RETAKE',
  attemptStatus: 'ANSWERING',
  submittedTime: null,
  scoreVisible: false,
  scorePublishStatus: 'UNPUBLISHED',
  finalScore: null
}) : failed(7001, '考生不存在。');
/** 按 Contract 接受重置范围与原因。 */
export const mockResetPractice = (examCandidateId: number, data: PracticeResetPayload): Promise<ApiResponse<void>> => !data.resetScope ? failed(1001, '重置范围不能为空。') : examCandidateId === attempt.examCandidateId ? success(undefined) : failed(7001, '考生不存在。');
