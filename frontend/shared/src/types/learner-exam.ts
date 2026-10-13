/** 学员考试任务状态，与 ExamLearnerTaskVO 保持一致。 */
export type LearnerExamTaskStatus = 'NOT_STARTED' | 'IN_PROGRESS' | 'WAITING_RESULT' | 'RESULT_PUBLISHED';
/** 学员端建议执行的考试操作。 */
export type LearnerExamAction = 'START' | 'CONTINUE' | 'WAIT_REVIEW' | 'VIEW_RESULT';

/** 本机构本人考试须知，只包含允许学员读取的摘要字段。 */
export interface LearnerExamNotice {
  /** 考试编号。 */
  examId: number;
  /** 考试名称。 */
  examName: string;
  /** 考试类型。 */
  examType: string;
  /** 开放开始时间。 */
  openStartTime: string | null;
  /** 开放结束时间。 */
  openEndTime: string | null;
  /** 作答时长秒数。 */
  durationSeconds: number;
  /** 合格比例。 */
  passRatio: number;
  /** 合格分数。 */
  passScore: number;
  /** 是否启用须知。 */
  isNoticeEnabled: 0 | 1;
  /** 须知正文。 */
  noticeContent: string | null;
}

/** 当前登录学员可见的考试任务聚合数据。 */
export interface LearnerExamTask {
  examCandidateId: number;
  candidateStatus: string;
  attemptCount: number;
  examId: number;
  examName: string;
  examType: string;
  /** 考试封面受控相对路径；为空时页面展示占位。 */
  coverUrl: string | null;
  /** 独立考试执行来源；课程考核不会出现在本接口。 */
  sourceType: 'STANDALONE_EXAM' | 'STANDALONE_PRACTICE';
  /** 稳定来源业务 ID；独立来源时等于考试 ID。 */
  sourceBusinessId: number;
  openStartTime: string | null;
  openEndTime: string | null;
  durationSeconds: number;
  currentAttemptId: number | null;
  attemptStatus: string | null;
  deadlineTime: string | null;
  taskStatus: LearnerExamTaskStatus;
  scorePublishStatus: 'PUBLISHED' | 'UNPUBLISHED' | 'EFFECTIVE';
  scorePublishedTime: string | null;
  scoreVisible: boolean;
  finalScore: number | null;
  isPassed: 0 | 1 | null;
  /** 建议操作；线下考试在管理端录入成绩前为空，此时学员无可用操作，仅展示等待结果。 */
  availableAction: LearnerExamAction | null;
}

/** Contract 分页数据。 */
export interface LearnerExamTaskPage {
  list: LearnerExamTask[];
  total: number;
}

/** 学员考试任务分页查询条件，与 ExamLearnerController 的 query 参数保持一致。 */
export interface LearnerExamTaskQuery {
  pageNum: number;
  pageSize: number;
  'query.taskStatus'?: LearnerExamTaskStatus;
  'query.examName'?: string;
  /** 仅支持独立考试或独立练习来源筛选。 */
  'query.sourceType'?: LearnerExamTask['sourceType'];
}

/** 题目快照中的选项。 */
export interface LearnerExamOption {
  attemptOptionId?: number;
  optionKey: string;
  optionContent?: string;
  optionContentSnapshot?: string;
  isCorrectSnapshot?: 0 | 1 | null;
  sortOrder: number;
}

/** 单题已保存答案。 */
export interface LearnerExamAnswer {
  examAnswerId?: number;
  examAttemptId?: number;
  attemptQuestionId: number;
  answerText: string | null;
  items: LearnerExamAnswerItem[];
  isAnswered: 0 | 1;
  gradingStatus?: string;
  answeredTime?: string | null;
  lastSavedTime?: string | null;
  autoScore?: number | null;
  manualScore?: number | null;
  /** 管理端最近一次批阅意见；学员详情由服务端裁剪为 null。 */
  reviewComment?: string | null;
  finalScore?: number | null;
  isCorrect?: 0 | 1 | null;
  /** 题库练习的逐题判断结果；主观题仅记录作答，不参与正确率。 */
  evaluationResult?: 'AUTO_CORRECT' | 'AUTO_WRONG' | 'NOT_EVALUATED' | null;
}

/** 答案暂存 item，与 SaveAnswerItemRequest 一致。 */
export interface LearnerExamAnswerItem {
  itemType: string;
  itemKey: string;
  itemValue: string;
  sortOrder: number;
}

/** 作答题目快照。 */
export interface LearnerExamQuestion {
  attemptQuestionId: number;
  questionId: number;
  questionVersionId: number;
  questionType: string;
  stemSnapshot: string;
  /** 填空题需填写的空位数量；仅下发数量，不包含标准答案。 */
  blankCount?: number | null;
  analysisSnapshot?: string | null;
  correctAnswerSnapshotJson?: string | null;
  standardAnswer?: string | null;
  gradingMode: string;
  /** 题库练习题目的反馈方式；RECORD_ONLY 仅供学习对照。 */
  evaluationMode?: 'AUTO' | 'RECORD_ONLY' | null;
  score: number;
  sortOrder: number;
  options: LearnerExamOption[];
  answer?: LearnerExamAnswer | null;
}

/** 管理端作答详情中的补考资格摘要，由服务端按考试策略与实际作答记录计算。 */
export interface ExamRetakeEligibility {
  eligible: boolean;
  reasons: string[];
  usedRetakeCount: number;
  maxRetakeCount: number;
}

/** 作答恢复与结果查询共用的尝试快照。 */
export interface LearnerExamAttempt {
  /** 考核执行实例 ID，用于追溯独立考试来源。 */
  assessmentExecutionId: number;
  /** 执行来源类型。 */
  sourceType: string;
  /** 冻结资源类型。 */
  resourceType: string;
  /** 冻结资源 ID。 */
  resourceId: number;
  /** 冻结资源名称。 */
  resourceName: string;
  /** 考核语义；BANK_PRACTICE 为培训题库练习，不产生分数和及格结论。 */
  assessmentKind?: string | null;
  examAttemptId: number;
  examId: number;
  examCandidateId: number;
  userId: number;
  paperVersionId: number;
  /** 考前须知页使用的考试概要字段；旧接口未回传时前端按未设置展示。 */
  examName?: string | null;
  examType?: string | null;
  responsibleUserId?: number | null;
  responsibleUserName?: string | null;
  openStartTime?: string | null;
  openEndTime?: string | null;
  durationSeconds?: number | null;
  passScore?: number | null;
  noticeContent?: string | null;
  attemptNo: number;
  attemptType: string;
  attemptStatus: string;
  startedTime: string;
  deadlineTime: string;
  submittedTime: string | null;
  usedSeconds: number;
  finalScore: number | null;
  isPassed: 0 | 1 | null;
  /** 管理端发布前核对的预计及格结果；待人工批阅时为空。 */
  predictedIsPassed?: 0 | 1 | null;
  correctCount: number | null;
  wrongCount: number | null;
  /** 可自动判断题数；主观题不计入该数量。 */
  evaluatedCount?: number | null;
  /** 未判定题数，通常为主观题。 */
  unassessedCount?: number | null;
  /** 可自动判断题的正确率百分比；没有该类题目时为空。 */
  accuracyRate?: number | null;
  scorePublishStatus: 'PUBLISHED' | 'UNPUBLISHED' | 'EFFECTIVE';
  scorePublishedTime: string | null;
  /** 课程考核自动生效时间；独立考试固定为空。 */
  resultEffectiveTime?: string | null;
  scoreVisible: boolean;
  /** 冻结的独立考试防作弊事件审计开关；课程考核固定按关闭处理。 */
  isAnticheatEnabled?: 0 | 1;
  /** 冻结的独立考试答题页水印开关；课程考核固定按关闭处理。 */
  isWatermarkEnabled?: 0 | 1;
  /** 仅管理端作答详情返回；字段缺失时不得显示开补考入口。 */
  retakeEligibility?: ExamRetakeEligibility;
  questions: LearnerExamQuestion[];
}

/** 开始作答请求。 */
export interface StartLearnerExamAttemptPayload {
  examId: number;
  userId: number;
  clientAttemptToken: string;
  attemptType?: string;
  platform?: string;
  operatingSystem?: string;
  browser?: string;
  clientIp?: string;
}

/** 答案暂存请求。 */
export interface SaveLearnerExamAnswersPayload {
  answers: Array<Pick<LearnerExamAnswer, 'attemptQuestionId' | 'answerText' | 'items'>>;
}

/** 手动或超时交卷请求。 */
export interface SubmitLearnerExamAttemptPayload {
  submitReason?: string;
  usedSeconds?: number;
}
