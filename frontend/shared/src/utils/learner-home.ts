import type { LearnerReference } from '../types/learner-home';

/** 两端只映射已确认的业务引用，目标页面继续向后端重新鉴权。 */
export function learnerTarget(reference: LearnerReference, platform: 'PC' | 'H5'): string {
  const id = reference.resourceId;
  if (!Number.isSafeInteger(id) || id < 1) throw new Error('业务入口不可用');
  if (reference.kind === 'COURSE' || reference.kind === 'COURSE_QUIZ') {
    return platform === 'PC' ? `/portal/class/learning/${id}` : `/pages/course/detail?enrollmentId=${id}`;
  }
  if (reference.kind === 'FORMAL_EXAM' && reference.parentId && Number.isSafeInteger(reference.parentId)) {
    if (reference.attemptId && reference.action === 'CONTINUE') {
      return platform === 'PC' ? `/portal/exams/${reference.parentId}/attempt/${reference.attemptId}` : `/pages/exam/attempt?examId=${reference.parentId}&examAttemptId=${reference.attemptId}`;
    }
    if (reference.attemptId && (reference.action === 'VIEW_RESULT' || reference.action === 'WAIT_REVIEW')) {
      return platform === 'PC' ? `/portal/exams/${reference.parentId}/result/${reference.attemptId}` : `/pages/exam/result?examId=${reference.parentId}&examAttemptId=${reference.attemptId}`;
    }
    return platform === 'PC' ? `/portal/exams/${reference.parentId}/notice/${id}` : `/pages/exam/notice?examId=${reference.parentId}&examCandidateId=${id}`;
  }
  if (reference.kind === 'TRAINING') return platform === 'PC' ? `/portal/training/${id}` : `/pages/training/detail?trainingParticipantId=${id}`;
  if (reference.kind === 'COURSE_CATALOG') return platform === 'PC' ? `/user-center/courses?courseId=${id}` : `/pages/favorite-course/index?courseId=${id}`;
  throw new Error('业务入口不可用');
}

/** 首页与消息共用中文类型展示。 */
export const LEARNER_KIND_LABELS: Record<LearnerReference['kind'], string> = {
  COURSE: '课程学习', FORMAL_EXAM: '考试', COURSE_QUIZ: '随堂测试', TRAINING: '培训', COURSE_CATALOG: '课程动态'
};
/** 状态只做展示映射，不据此推断授权或重新计算结果。 */
export function learnerStatusLabel(status: string): string {
  return ({ NOT_STARTED: '未开始', LEARNING: '学习中', COMPLETED: '已完成', UNFINISHED: '未完成', IN_PROGRESS: '进行中', ENDED: '已结束', RUNNING: '进行中' } as Record<string, string>)[status] ?? '查看任务详情';
}
