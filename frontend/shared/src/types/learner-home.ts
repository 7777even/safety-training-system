/** 首页与消息业务类型，来自learner.openapi.json。 */
export type LearnerReferenceKind = 'COURSE' | 'FORMAL_EXAM' | 'COURSE_QUIZ' | 'TRAINING' | 'COURSE_CATALOG';
/** 本人业务引用，ID不能作为任意URL或跨来源复用。 */
export interface LearnerReference {
  kind: LearnerReferenceKind;
  resourceId: number;
  parentId?: number | null;
  itemId?: number | null;
  /** 仅用于原领域允许回看的考卷。 */
  attemptId?: number | null;
  /** 当前可执行动作；持久化消息打开时需要重新获取。 */
  action?: string | null;
}
/** 本人首页真实分配任务。 */
export interface LearnerHomeTask extends LearnerReference {
  key: string;
  title: string;
  status: string;
  action: 'VIEW_COURSE' | 'VIEW_TRAINING' | 'START' | 'CONTINUE' | 'WAIT_REVIEW' | 'VIEW_RESULT' | null;
  endTime: string | null;
}
/** 不含已读状态的持久化消息。 */
export interface LearnerMessage extends LearnerReference {
  recipientId: number;
  messageId: number;
  title: string;
  content: string;
  createTime: string;
}
