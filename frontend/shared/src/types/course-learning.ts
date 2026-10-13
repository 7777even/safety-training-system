/** 课程报名学习状态。 */
export type LearnerCourseStatus = 'NOT_STARTED' | 'LEARNING' | 'COMPLETED' | 'EXPIRED' | 'WITHDRAWN';

/** 学员课程内容项类型。 */
export type LearnerCourseItemType = 'ASSET' | 'LIVE' | 'QUESTION_BANK' | 'QUIZ';

/** 课程目录任务的考核状态，与课程任务快照 Contract 保持一致。 */
export type CourseAssessmentTaskStatus = 'NOT_STARTED' | 'IN_PROGRESS' | 'WAITING_RESULT' | 'RESULT_EFFECTIVE';

/** 课程目录任务的课程考核结果状态。 */
export type CourseAssessmentScorePublishStatus = 'UNPUBLISHED' | 'PENDING_REVIEW' | 'EFFECTIVE';

/** 学员本人课程报名摘要。 */
export interface LearnerCourseEnrollment {
  enrollmentId: number;
  offeringId: number;
  courseId: number;
  courseName: string;
  offeringName: string;
  courseType: 'REQUIRED' | 'ELECTIVE';
  startTime: string | null;
  endTime: string | null;
  learningStatus: Exclude<LearnerCourseStatus, 'WITHDRAWN'>;
  progressPercent: number;
  lastStudyTime: string | null;
  completedTime: string | null;
  /** 课程封面受控相对路径；为空时页面展示占位。 */
  coverUrl: string | null;
}

/** 学员可自主报名课程摘要。 */
export interface LearnerCourseOffering {
  offeringId: number;
  courseId: number;
  courseName: string;
  offeringName: string;
  courseType: 'REQUIRED' | 'ELECTIVE';
  startTime: string | null;
  endTime: string | null;
  enrollmentType: 'OPEN' | 'LINK';
  enrollmentAvailability: 'AVAILABLE';
  coverUrl: string | null;
}

/** 学员直播任务冻结引用。 */
export interface LearnerLiveTaskReference {
  liveSessionId: number;
  liveName: string;
  teacherUserId: number | null;
  liveUrl: string | null;
  startTime: string | null;
  endTime: string | null;
  liveStatus: string | null;
}

/** 学员学习内容项任务。 */
export interface LearnerCourseTaskItem {
  /** 课件关联的已有测验。 */
  linkedQuizItemId?: number | null;
  /** 结业测评及一次补测资格由后端冻结规则计算。 */
  isFinalAssessment?: number;
  canRetake?: boolean;
  passScore?: number | null;
  courseItemId: number;
  itemType: LearnerCourseItemType;
  itemTitle: string;
  isRequired: 0 | 1;
  sortOrder: number;
  progressStatus: 'NOT_STARTED' | 'LEARNING' | 'COMPLETED';
  studySeconds: number;
  lastPositionSeconds: number | null;
  lastPageNo: number | null;
  completedTime: string | null;
  lastStudyTime: string | null;
  learnable: boolean;
  unavailableReason: 'OFFERING_CANCELLED' | 'ENROLLMENT_WITHDRAWN' | 'ENROLLMENT_EXPIRED' | 'CONTENT_DISABLED' | null;
  assetId: number | null;
  questionBankId: number | null;
  paperVersionId: number | null;
  activeExamAttemptId?: number | null;
  publishedExamAttemptId?: number | null;
  examTaskStatus?: CourseAssessmentTaskStatus | null;
  scorePublishStatus?: CourseAssessmentScorePublishStatus | null;
  isPassed?: 0 | 1 | null;
  finalScore?: number | null;
  liveSession: LearnerLiveTaskReference | null;
}

/** 课程题库练习和课程测验的作答快照，与独立考试 Attempt 保持来源隔离。 */
export interface CourseAssessmentAttempt {
  examAttemptId: number;
  assessmentExecutionId: number;
  sourceType: 'COURSE_QUIZ' | 'COURSE_BANK_PRACTICE';
  assessmentKind: 'QUIZ' | 'BANK_PRACTICE';
  resourceType: 'PAPER_VERSION' | 'QUESTION_BANK';
  resourceId: number;
  resourceName: string;
  paperVersionId: number | null;
  attemptNo: number;
  attemptStatus: string;
  finalScore: number | null;
  isPassed: 0 | 1 | null;
  scorePublishStatus: 'UNPUBLISHED' | 'EFFECTIVE';
  resultEffectiveTime: string | null;
  scoreVisible: boolean;
  questions: import('./learner-exam').LearnerExamQuestion[];
}

/** 学员学习任务章节。 */
export interface LearnerCourseTaskChapter {
  /** 旧快照没有父关系时按根级显示。 */
  parentId?: number;
  chapterId: number;
  chapterName: string;
  sortOrder: number;
  items: LearnerCourseTaskItem[];
}

/** 学员本人课程学习任务清单。 */
export interface LearnerCourseTasks {
  enrollmentId: number;
  offeringId: number;
  courseId: number;
  courseName: string;
  offeringName: string;
  courseType: 'REQUIRED' | 'ELECTIVE';
  startTime: string | null;
  endTime: string | null;
  learningStatus: LearnerCourseStatus;
  progressPercent: number;
  coverUrl: string | null;
  chapters: LearnerCourseTaskChapter[];
}

/** 学员学习详情中的课程简介。 */
export interface LearnerCourseInfo {
  teacherUserId?: number | null;
  teacherName?: string | null;
  courseId: number;
  courseCode: string | null;
  courseName: string;
  coverUrl: string | null;
  introduction: string | null;
  teachingObjective: string | null;
  syllabus: string | null;
  assessmentMethod: string | null;
  contentMode: string | null;
  credit: number | null;
  pointMode: string | null;
  pointValue: number | null;
  sourceDownloadMode: 'NONE' | 'ALL' | 'PARTIAL' | null;
  /** 是否允许倍速播放：1 允许、0 不允许；为空按不允许处理。 */
  isSpeedPlayAllowed: 0 | 1 | null;
  /** 是否启用学习防作弊：1 启用、0 不启用；为空按不启用处理。 */
  isStudyAnticheatEnabled: 0 | 1 | null;
  /** 是否显示学时说明：1 显示、0 不显示；为空按不显示处理。 */
  isStudyDescriptionEnabled: 0 | 1 | null;
  /** 是否启用学习水印：1 启用、0 不启用；为空按不启用处理。 */
  isWatermarkEnabled: 0 | 1 | null;
}

/** 学员学习详情中的课程附件引用。 */
export interface LearnerCourseDownloadAsset {
  assetId: number;
  assetName: string | null;
  assetType: string | null;
  fileName: string | null;
  mimeType: string | null;
  fileSize: number | null;
  fileInfoId: number | null;
  sortOrder: number | null;
  status: 0 | 1 | null;
  downloadable: boolean;
  downloadUrl: string | null;
  unavailableReason: string | null;
}

/** 学员学习详情聚合数据。 */
/** 本人报告最近生效结果。 */
export interface CourseReportResult {
  courseItemId: number;
  examAttemptId: number;
  sourceType: string;
  attemptNo: number;
  finalScore: number | null;
  isPassed: 0 | 1 | null;
  correctCount: number | null;
  wrongCount: number | null;
  accuracyPercent: number | null;
}
export interface LearnerCourseDetail {
  assessmentResults?: CourseReportResult[];
  courseEnrollmentId: number;
  courseOfferingId: number;
  userId: number;
  enrollmentSource: string;
  enrolledTime: string;
  learningStatus: LearnerCourseStatus;
  progressPercent: number;
  recognizedStudySeconds?: number | null;
  totalStudySeconds: number;
  creditAwarded: number;
  completedTime: string | null;
  lastStudyTime: string | null;
  course: LearnerCourseInfo | null;
  downloadAssets: LearnerCourseDownloadAsset[];
  itemProgresses: Array<{
    courseItemId: number;
    progressStatus: string;
    studySeconds: number;
    lastPositionSeconds: number | null;
    lastPageNo: number | null;
    completedTime: string | null;
    lastStudyTime: string | null;
  }>;
  /** 本人已提交的课程评价；未评价时为空。 */
  evaluation?: CourseEvaluation | null;
}

/** Contract 通用分页结果。 */
export interface CourseLearningPage<TItem> {
  list: TItem[];
  total: number;
}

/** 学习会话上报载荷。 */
export interface StudySessionReport {
  enrollmentId: number;
  courseItemId: number;
  clientTraceId: string;
  deviceType: 'PC' | 'APP' | 'H5';
  /** 终端标识：客户端生成并持久化，用于同一终端多账户同时学习的互斥检测。 */
  deviceId: string;
  startedTime: string;
  endedTime?: string | null;
  validStudySeconds: number;
  startPositionSeconds?: number | null;
  endPositionSeconds?: number | null;
  startPageNo?: number | null;
  endPageNo?: number | null;
  /** HEARTBEAT 为定时检查点，保持会话活跃；非 HEARTBEAT 收尾立即释放互斥。 */
  endReason: 'COMPLETED' | 'USER_EXIT' | 'SCREEN_LEAVE' | 'TIMEOUT' | 'ERROR' | 'HEARTBEAT';
}

/** 课程评价提交载荷。 */
export interface CourseEvaluationSubmit {
  rating: number;
  content?: string | null;
  isAnonymous: 0 | 1;
}

/** 课程详情返回的本人评价事实。 */
export interface CourseEvaluation extends CourseEvaluationSubmit {
  courseEvaluationId: number;
  submittedTime: string;
}

/** 同次开课评价列表的公开字段，不含评价人及报名 ID。 */
export interface CourseEvaluationListItem {
  rating: number;
  content: string | null;
  isAnonymous: 0 | 1;
  displayName: string;
  /** 匿名项仅有日期，实名项包含日期时间。 */
  submittedTime: string;
}

/** 学员只读的当前租户启用分类，可按任意父分类包含后代筛选。 */
export interface LearnerCourseCategory {
  courseCategoryId: number;
  parentId: number;
  categoryName: string;
  children: LearnerCourseCategory[];
}
