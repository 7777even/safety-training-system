import type {
  ApiResponse,
  LearnerExamAttempt,
  LearnerExamTask,
  LearnerExamTaskPage,
  LearnerExamTaskQuery,
  SaveLearnerExamAnswersPayload,
  StartLearnerExamAttemptPayload,
  SubmitLearnerExamAttemptPayload
} from '@shared/types';
import type { LearnerExamNotice, RecordExamAnticheatEventPayload } from '@/api/learner-exam';

/** TODO replace real api：移动端独立考试 Mock，避免依赖 PC 运行时代码。 */
const ATTEMPT_ID = 9001;
const toIso = (seconds: number): string => new Date(Date.now() + seconds * 1000).toISOString();
const success = <T>(data: T): Promise<ApiResponse<T>> => Promise.resolve({ code: 0, message: 'success', data });
/** 构造已发布成绩的结果快照，用于验证移动端成绩与解析展示。 */
function createPublishedAttempt(): LearnerExamAttempt {
  const answers = ['A', 'A,B,C', '119', '立即报告并按流程整改复查'];
  const standards = ['A', 'A、B、C', '119', '报告、警戒、整改并复查'];
  return {
    ...(JSON.parse(JSON.stringify(attempt)) as LearnerExamAttempt),
    examAttemptId: 9003,
    examId: 4,
    attemptStatus: 'SUBMITTED',
    submittedTime: toIso(-3600),
    finalScore: 48,
    isPassed: 1,
    correctCount: 3,
    wrongCount: 1,
    scorePublishStatus: 'PUBLISHED',
    scorePublishedTime: toIso(-1800),
    scoreVisible: true,
    questions: attempt.questions.map((question, index) => ({
      ...question,
      analysisSnapshot: index === 3 ? '处置应覆盖报告、现场警戒、整改和复查闭环。' : '依据安全生产基础规范判定。',
      correctAnswerSnapshotJson: JSON.stringify({ standardAnswer: standards[index] }),
      standardAnswer: standards[index],
      options: question.options.map((option) => ({
        ...option,
        optionContentSnapshot: option.optionContent,
        isCorrectSnapshot: standards[index].split(/[、,]/).includes(option.optionKey) ? 1 : 0
      })),
      answer: {
        examAnswerId: 300 + index,
        attemptQuestionId: question.attemptQuestionId,
        answerText: answers[index],
        items: [],
        isAnswered: 1,
        gradingStatus: index === 3 ? 'MANUAL_GRADED' : 'AUTO_GRADED',
        finalScore: index === 3 ? 18 : question.score,
        isCorrect: index === 3 ? 0 : 1
      }
    }))
  };
}

const taskList: LearnerExamTask[] = [
  {
    examCandidateId: 101,
    candidateStatus: 'NOT_STARTED',
    attemptCount: 0,
    examId: 1,
    examName: '安全生产基础考试',
    examType: 'ONLINE',
    coverUrl: '/api/exam/learner/exams/1/cover',
    sourceType: 'STANDALONE_EXAM',
    sourceBusinessId: 1,
    openStartTime: toIso(-3600),
    openEndTime: toIso(86400),
    durationSeconds: 3600,
    currentAttemptId: null,
    attemptStatus: null,
    deadlineTime: null,
    taskStatus: 'NOT_STARTED',
    scorePublishStatus: 'UNPUBLISHED',
    scorePublishedTime: null,
    scoreVisible: false,
    finalScore: null,
    isPassed: null,
    availableAction: 'START'
  },
  {
    examCandidateId: 102,
    candidateStatus: 'IN_PROGRESS',
    attemptCount: 1,
    examId: 2,
    examName: '危化品作业规范考试',
    examType: 'ONLINE',
    coverUrl: null,
    sourceType: 'STANDALONE_EXAM',
    sourceBusinessId: 2,
    openStartTime: toIso(-3600),
    openEndTime: toIso(86400),
    durationSeconds: 1800,
    currentAttemptId: ATTEMPT_ID,
    attemptStatus: 'ANSWERING',
    deadlineTime: toIso(1800),
    taskStatus: 'IN_PROGRESS',
    scorePublishStatus: 'UNPUBLISHED',
    scorePublishedTime: null,
    scoreVisible: false,
    finalScore: null,
    isPassed: null,
    availableAction: 'CONTINUE'
  },
  {
    examCandidateId: 104,
    candidateStatus: 'FINISHED',
    attemptCount: 1,
    examId: 4,
    examName: '消防安全知识考试',
    examType: 'ONLINE',
    coverUrl: null,
    sourceType: 'STANDALONE_EXAM',
    sourceBusinessId: 4,
    openStartTime: toIso(-86400),
    openEndTime: toIso(-3600),
    durationSeconds: 1800,
    currentAttemptId: 9003,
    attemptStatus: 'SUBMITTED',
    deadlineTime: toIso(-3600),
    taskStatus: 'RESULT_PUBLISHED',
    scorePublishStatus: 'PUBLISHED',
    scorePublishedTime: toIso(-1800),
    scoreVisible: true,
    finalScore: 88,
    isPassed: 1,
    availableAction: 'VIEW_RESULT'
  }
];

/** TODO replace real api：与 PC 考前须知字段一致的考试详情 Mock。 */
const examNotices: Record<number, LearnerExamNotice> = {
  1: {
    examId: 1,
    examName: '安全生产基础考试',
    examType: 'ONLINE',
    openStartTime: toIso(-3600),
    openEndTime: toIso(86400),
    durationSeconds: 3600,
    passRatio: 0.6,
    passScore: 60,
    isNoticeEnabled: 1,
    noticeContent: '请在规定时间内独立完成考试，交卷后将无法继续修改答案。'
  }
};

let attempt: LearnerExamAttempt = {
  assessmentExecutionId: 1002,
  sourceType: 'STANDALONE_EXAM',
  resourceType: 'PAPER_VERSION',
  resourceId: 12,
  resourceName: '危化品作业规范试卷',
  examAttemptId: ATTEMPT_ID,
  examId: 2,
  examCandidateId: 102,
  userId: 1,
  paperVersionId: 12,
  attemptNo: 1,
  attemptType: 'FIRST',
  attemptStatus: 'ANSWERING',
  startedTime: toIso(-120),
  deadlineTime: toIso(1800),
  submittedTime: null,
  usedSeconds: 120,
  finalScore: null,
  isPassed: null,
  correctCount: null,
  wrongCount: null,
  scorePublishStatus: 'UNPUBLISHED',
  scorePublishedTime: null,
  scoreVisible: false,
  questions: [
    {
      attemptQuestionId: 201,
      questionId: 1,
      questionVersionId: 1,
      questionType: 'SINGLE_CHOICE',
      stemSnapshot: '进入受限空间作业前，应优先确认哪项安全措施？',
      gradingMode: 'AUTO',
      score: 10,
      sortOrder: 1,
      options: [
        { optionKey: 'A', optionContent: '检测气体并办理作业许可', sortOrder: 1 },
        {
          optionKey: 'B',
          optionContent: '直接进入并快速完成作业',
          sortOrder: 2
        }
      ],
      answer: null
    },
    {
      attemptQuestionId: 202,
      questionId: 2,
      questionVersionId: 1,
      questionType: 'MULTIPLE_CHOICE',
      stemSnapshot: '下列哪些属于个人防护用品？',
      gradingMode: 'AUTO',
      score: 10,
      sortOrder: 2,
      options: [
        { optionKey: 'A', optionContent: '安全帽', sortOrder: 1 },
        {
          optionKey: 'B',
          optionContent: '防护手套',
          sortOrder: 2
        }
      ],
      answer: null
    },
    {
      attemptQuestionId: 203,
      questionId: 3,
      questionVersionId: 1,
      questionType: 'FILL_BLANK',
      stemSnapshot: '发生火灾时，应首先拨打 ______ 报警电话。',
      gradingMode: 'AUTO',
      score: 10,
      sortOrder: 3,
      options: [],
      answer: null
    }
  ]
};

/** 分页读取移动端任务，并按与 PC 考试中心一致的任务状态筛选。 */
export const mockGetLearnerExamTaskPage = ({
  pageNum,
  pageSize,
  'query.taskStatus': taskStatus
}: LearnerExamTaskQuery): Promise<ApiResponse<LearnerExamTaskPage>> => {
  const filtered = taskList.filter((item) => !taskStatus || item.taskStatus === taskStatus);
  return success({
    list: filtered.slice((pageNum - 1) * pageSize, pageNum * pageSize),
    total: filtered.length
  });
};
/** 读取 Mock 考前须知，未知考试沿用任务的基本信息。 */
export const mockGetLearnerExamNotice = (examId: number): Promise<ApiResponse<LearnerExamNotice>> => {
  const notice = examNotices[examId];
  if (notice) return success(notice);
  const task = taskList.find((item) => item.examId === examId);
  return success({
    examId,
    examName: task?.examName ?? '考试',
    examType: task?.examType ?? 'ONLINE',
    openStartTime: task?.openStartTime,
    openEndTime: task?.openEndTime,
    durationSeconds: task?.durationSeconds ?? 0,
    passRatio: 0,
    passScore: 0,
    isNoticeEnabled: 0
  });
};
/** 启动 Mock 作答。 */
export const mockStartExamAttempt = (data: StartLearnerExamAttemptPayload): Promise<ApiResponse<LearnerExamAttempt>> => {
  attempt = {
    ...attempt,
    examId: data.examId,
    userId: data.userId,
    submittedTime: null,
    attemptStatus: 'ANSWERING',
    deadlineTime: toIso(3600)
  };
  return success(JSON.parse(JSON.stringify(attempt)) as LearnerExamAttempt);
};
/** 读取 Mock 尝试。 */
export const mockGetLearnerExamAttempt = (examAttemptId: number): Promise<ApiResponse<LearnerExamAttempt>> => {
  if (examAttemptId === 9003) return success(createPublishedAttempt());
  if (examAttemptId === 9002)
    return success({
      ...(JSON.parse(JSON.stringify(attempt)) as LearnerExamAttempt),
      examAttemptId,
      attemptStatus: 'SUBMITTED',
      submittedTime: toIso(-60)
    });
  return success(JSON.parse(JSON.stringify(attempt)) as LearnerExamAttempt);
};
/** 更新 Mock 答案。 */
export const mockSaveExamAnswers = (_attemptId: number, data: SaveLearnerExamAnswersPayload): Promise<ApiResponse<void>> => {
  if (attempt.submittedTime)
    return Promise.resolve({
      code: 7101,
      message: '试卷已交卷，不能继续保存答案。',
      data: undefined
    });
  attempt.questions = attempt.questions.map((question) => {
    const answer = data.answers.find((item) => item.attemptQuestionId === question.attemptQuestionId);
    return answer
      ? {
          ...question,
          answer: { ...answer, isAnswered: answer.answerText || answer.items.length ? 1 : 0 }
        }
      : question;
  });
  return success(undefined);
};
/** 锁定 Mock 作答。 */
export const mockSubmitExamAttempt = (_attemptId: number, data: SubmitLearnerExamAttemptPayload): Promise<ApiResponse<void>> => {
  attempt = {
    ...attempt,
    attemptStatus: 'SUBMITTED',
    submittedTime: new Date().toISOString(),
    usedSeconds: data.usedSeconds ?? attempt.usedSeconds
  };
  return success(undefined);
};
/** 超时交卷复用提交快照逻辑。 */
export const mockTimeoutSubmitExamAttempt = mockSubmitExamAttempt;
/** 接收移动端最小反作弊事件，Mock 不改变答题快照。 */
export const mockRecordExamAnticheatEvent = (attemptId: number, data: RecordExamAnticheatEventPayload): Promise<ApiResponse<void>> => {
  void attemptId;
  void data;
  return success(undefined);
};
