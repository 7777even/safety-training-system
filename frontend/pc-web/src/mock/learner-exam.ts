import type {
  ApiResponse,
  LearnerExamAnswer,
  LearnerExamAttempt,
  LearnerExamTask,
  LearnerExamTaskPage,
  LearnerExamTaskQuery,
  SaveLearnerExamAnswersPayload,
  StartLearnerExamAttemptPayload,
  SubmitLearnerExamAttemptPayload
} from '@shared/types';
import type { RecordExamAnticheatEventPayload } from '@/api/learner-exam';

/** TODO replace real api：学员考试 Contract Mock，仅用于真实接口未完成阶段。 */
const MOCK_USER_ID = 1;
const MOCK_ATTEMPT_ID = 9001;
const now = new Date();
const toIso = (offsetSeconds: number): string => new Date(now.getTime() + offsetSeconds * 1000).toISOString();

/** Mock 任务覆盖开始、续答、待发布和已发布四种 Contract 状态。 */
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
    currentAttemptId: MOCK_ATTEMPT_ID,
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
    examCandidateId: 103,
    candidateStatus: 'SUBMITTED',
    attemptCount: 1,
    examId: 3,
    examName: '特种作业安全考试',
    examType: 'ONLINE',
    coverUrl: null,
    sourceType: 'STANDALONE_EXAM',
    sourceBusinessId: 3,
    openStartTime: toIso(-86400),
    openEndTime: toIso(86400),
    durationSeconds: 1800,
    currentAttemptId: 9002,
    attemptStatus: 'SUBMITTED',
    deadlineTime: toIso(-60),
    taskStatus: 'WAITING_RESULT',
    scorePublishStatus: 'UNPUBLISHED',
    scorePublishedTime: null,
    scoreVisible: false,
    finalScore: null,
    isPassed: null,
    availableAction: 'WAIT_REVIEW'
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
    openStartTime: toIso(-172800),
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
  },
];

/** 作答快照包含单选、多选、填空与简答，以验证不同交互。 */
let attempt: LearnerExamAttempt = {
  assessmentExecutionId: 1002,
  sourceType: 'STANDALONE_EXAM',
  resourceType: 'PAPER_VERSION',
  resourceId: 12,
  resourceName: '危化品作业规范试卷',
  examAttemptId: MOCK_ATTEMPT_ID,
  examId: 2,
  examCandidateId: 102,
  userId: MOCK_USER_ID,
  paperVersionId: 12,
  examName: '危化品作业规范考试',
  examType: 'ONLINE',
  responsibleUserId: 6,
  responsibleUserName: '张老师',
  openStartTime: toIso(-3600),
  openEndTime: toIso(86400),
  durationSeconds: 1800,
  passScore: 60,
  noticeContent: '请在安静环境中独立完成考试，考试过程中保持网络畅通，交卷后等待教师批阅和成绩发布。',
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
      questionType: 'SINGLE',
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
        },
        { optionKey: 'C', optionContent: '仅佩戴普通口罩', sortOrder: 3 }
      ],
      answer: null
    },
    {
      attemptQuestionId: 202,
      questionId: 2,
      questionVersionId: 1,
      questionType: 'MULTIPLE',
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
        },
        { optionKey: 'C', optionContent: '安全鞋', sortOrder: 3 }
      ],
      answer: null
    },
    {
      attemptQuestionId: 203,
      questionId: 3,
      questionVersionId: 1,
      questionType: 'FILL',
      stemSnapshot: '发生火灾时，应首先拨打 ______ 报警电话。',
      gradingMode: 'AUTO',
      score: 10,
      sortOrder: 3,
      options: [],
      answer: null
    },
    {
      attemptQuestionId: 204,
      questionId: 4,
      questionVersionId: 1,
      questionType: 'ESSAY',
      stemSnapshot: '请简述发现安全隐患后的处置流程。',
      gradingMode: 'MANUAL',
      score: 20,
      sortOrder: 4,
      options: [],
      answer: null
    }
  ]
};

const success = <T>(data: T): Promise<ApiResponse<T>> => Promise.resolve({ code: 0, message: 'success', data });
const copyAttempt = (): LearnerExamAttempt => JSON.parse(JSON.stringify(attempt)) as LearnerExamAttempt;
/** 构造已发布成绩的结果快照，用于验证学员成绩与解析展示。 */
function createPublishedAttempt(): LearnerExamAttempt {
  const answers = ['A', 'A,B,C', '119', '立即报告并按流程整改复查'];
  const standards = ['A', 'A、B、C', '119', '报告、警戒、整改并复查'];
  return {
    ...copyAttempt(),
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

/** 分页读取 Mock 学员任务。 */
export const mockGetLearnerExamTaskPage = (params: LearnerExamTaskQuery): Promise<ApiResponse<LearnerExamTaskPage>> => {
  const { pageNum, pageSize } = params;
  const examName = params['query.examName']?.trim();
  const filteredTasks = taskList.filter((task) =>
    (!params['query.taskStatus'] || task.taskStatus === params['query.taskStatus']) &&
    (!params['query.sourceType'] || task.sourceType === params['query.sourceType']) &&
    (!examName || task.examName.includes(examName))
  );
  return success({ list: filteredTasks.slice((pageNum - 1) * pageSize, pageNum * pageSize), total: filteredTasks.length });
};
/** 创建并指向 Mock 当前作答尝试。 */
export const mockStartExamAttempt = (data: StartLearnerExamAttemptPayload): Promise<ApiResponse<LearnerExamAttempt>> => {
  attempt = {
    ...attempt,
    examId: data.examId,
    userId: data.userId,
    attemptStatus: 'ANSWERING',
    submittedTime: null,
    deadlineTime: toIso(3600)
  };
  return success(copyAttempt());
};
/** 读取 Mock 作答快照。 */
export const mockGetLearnerExamAttempt = (examAttemptId: number): Promise<ApiResponse<LearnerExamAttempt>> => {
  if (examAttemptId === 9003) return success(createPublishedAttempt());
  if (examAttemptId === 9002) return success({ ...copyAttempt(), examAttemptId, attemptStatus: 'SUBMITTED', submittedTime: toIso(-60) });
  return success(copyAttempt());
};
/** 保存 Mock 答案快照。 */
export const mockSaveExamAnswers = (_examAttemptId: number, data: SaveLearnerExamAnswersPayload): Promise<ApiResponse<void>> => {
  if (attempt.submittedTime)
    return Promise.resolve({
      code: 7101,
      message: '试卷已交卷，不能继续保存答案。',
      data: undefined
    });
  attempt.questions = attempt.questions.map((question) => {
    const source = data.answers.find((item) => item.attemptQuestionId === question.attemptQuestionId);
    if (!source) return question;
    const answer: LearnerExamAnswer = {
      ...source,
      isAnswered: source.answerText || source.items.length ? 1 : 0,
      lastSavedTime: new Date().toISOString()
    };
    return { ...question, answer };
  });
  return success(undefined);
};
/** 提交 Mock 作答，并锁定答案。 */
export const mockSubmitExamAttempt = (_examAttemptId: number, data: SubmitLearnerExamAttemptPayload): Promise<ApiResponse<void>> => {
  attempt = {
    ...attempt,
    attemptStatus: 'SUBMITTED',
    submittedTime: new Date().toISOString(),
    usedSeconds: data.usedSeconds ?? attempt.usedSeconds
  };
  return success(undefined);
};
/** 复用提交逻辑模拟超时交卷。 */
export const mockTimeoutSubmitExamAttempt = mockSubmitExamAttempt;
/** 接收作答端最小反作弊事件，Mock 不改变答题快照。 */
export const mockRecordExamAnticheatEvent = (attemptId: number, data: RecordExamAnticheatEventPayload): Promise<ApiResponse<void>> => {
  void attemptId;
  void data;
  return success(undefined);
};
