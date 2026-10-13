/** 移动端默认请求超时时间（毫秒）。 */
export const DEFAULT_REQUEST_TIMEOUT = 10_000;

/** 课程目录任务的考核状态，与课程任务快照的后端映射保持一致。 */
export const COURSE_ASSESSMENT_TASK_STATUS = {
  IN_PROGRESS: 'IN_PROGRESS',
  WAITING_RESULT: 'WAITING_RESULT',
  RESULT_EFFECTIVE: 'RESULT_EFFECTIVE'
} as const;

/** 课程作答详情的内部状态，仅用于课程答题与结果页面。 */
export const COURSE_ASSESSMENT_ATTEMPT_STATUS = {
  ANSWERING: 'ANSWERING',
  PENDING_REVIEW: 'PENDING_REVIEW',
  RESULT_EFFECTIVE: 'RESULT_EFFECTIVE'
} as const;

/** 课程考核独立页面路径，避免课程学习页散落路由字面量。 */
export const COURSE_ASSESSMENT_PAGE = {
  ATTEMPT: 'assessment-attempt',
  RESULT: 'assessment-result'
} as const;
