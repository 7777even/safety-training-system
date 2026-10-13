import type { HistoryReference, HistoryRecordType, HistorySummary } from '../types/learner-history';
import { learnerTarget } from './learner-home';

/** 历史使用原受控入口，不套用“当前任务有效”过滤；目标接口重新鉴权。 */
export function historyTarget(ref: HistoryReference, platform: 'PC' | 'H5'): string {
  if (!Number.isSafeInteger(ref.resourceId) || ref.resourceId < 1) throw new Error('原始记录入口不可用');
  if (ref.kind === 'PRACTICE' || ref.kind === 'SIMULATION') return platform === 'PC'
    ? `/user-center/practice/${ref.resourceId}` : `/pages/practice/attempt?id=${ref.resourceId}`;
  return learnerTarget({ ...ref, kind: ref.kind }, platform);
}
/** 六类来源中文展示。 */
export const HISTORY_LABELS: Record<HistoryRecordType, string> = {
  COURSE: '课程学习', FORMAL_EXAM: '正式考试', COURSE_QUIZ: '随堂测试', PRACTICE: '个人练习', SIMULATION: '个人模拟', TRAINING: '培训'
};
/** 更正字段的中文展示，两端保持一致。 */
export const HISTORY_FIELDS: Record<keyof Omit<HistorySummary, 'scoreVisible'>, string> = {
  userName: '历史姓名', userCode: '历史账号', occurredTime: '发生时间', status: '状态', score: '成绩', hours: '学时'
};
/** 来自各来源领域的状态，仅翻译展示，不据此推断访问权限。 */
export function historyStatus(status: string): string {
  return ({ NOT_STARTED: '未开始', LEARNING: '学习中', COMPLETED: '已完成', EXPIRED: '已过期',
    IN_PROGRESS: '进行中', SUBMITTED: '已提交', PENDING_REVIEW: '待批阅', PENDING_PUBLISH: '待发布',
    PASSED: '合格', FAILED: '未合格', ABSENT: '缺考', ANSWERING: '作答中', CLEARED: '已清理',
    PENDING: '待开始', WITHDRAWN: '已退出' } as Record<string, string>)[status] ?? status;
}
