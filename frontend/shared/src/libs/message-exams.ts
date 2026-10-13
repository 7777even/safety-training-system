import type { LearnerMessage } from '../types/learner-home';
import type { LearnerExamTask } from '../types/learner-exam';
import { getExamTypeText } from '../utils/exam';

/** 考试人员与考试双重引用，不能用标题或单个资源编号匹配。 */
export const messageExamKey = (candidateId: number | null, examId: number | null | undefined): string => `${candidateId}:${examId}`;

/** 仅为当前消息页读取本人考核信息，分页查全，不使用管理考试详情。 */
export async function loadMessageExams(messages: LearnerMessage[], readPage: (page: number) => Promise<{ list: LearnerExamTask[]; total: number }>): Promise<Record<string, LearnerExamTask>> {
  const remaining = new Set(messages.filter(row => row.kind === 'FORMAL_EXAM').map(row => messageExamKey(row.resourceId, row.parentId)));
  const tasks: Record<string, LearnerExamTask> = {};
  for (let page = 1; remaining.size; page++) {
    const result = await readPage(page);
    for (const task of result.list) {
      const key = messageExamKey(task.examCandidateId, task.examId);
      if (remaining.delete(key)) tasks[key] = task;
    }
    if (page * 200 >= result.total) break;
  }
  return tasks;
}

/** 消息正文保持原通知事实，当前考试类型与可用动作另行展示。 */
export function messageExamInfo(message: LearnerMessage, tasks: Record<string, LearnerExamTask>): { typeLabel: string; hint: string; actionLabel: string; canOpen: boolean } {
  if (message.kind !== 'FORMAL_EXAM') return { typeLabel: '', hint: '', actionLabel: '查看对应业务', canOpen: true };
  const task = tasks[messageExamKey(message.resourceId, message.parentId)];
  if (!task) return { typeLabel: '考试信息暂不可用', hint: '考试信息不可见或未能加载，请刷新消息后重试。', actionLabel: '暂无入口', canOpen: false };
  if (!['ONLINE', 'OFFLINE', 'SPECIAL_PRACTICE', 'BANK_PRACTICE'].includes(task.examType) || (task.availableAction && !['START', 'CONTINUE', 'WAIT_REVIEW', 'VIEW_RESULT'].includes(task.availableAction))) return { typeLabel: '考试信息暂不可用', hint: '考试类型或入口暂不可用，请刷新消息后重试。', actionLabel: '暂无入口', canOpen: false };
  const offline = task.examType === 'OFFLINE';
  const canOpen = !!task.availableAction && (!offline || (task.availableAction === 'VIEW_RESULT' && !!task.currentAttemptId));
  const actionLabel = canOpen ? ({ START: '进入考试', CONTINUE: '继续作答', WAIT_REVIEW: '查看答题情况', VIEW_RESULT: '查看成绩' }[task.availableAction!] ?? '查看对应业务') : (offline ? '待出成绩' : '暂无入口');
  return { typeLabel: getExamTypeText(task.examType), canOpen, actionLabel,
    hint: offline ? (canOpen ? '线下考试，成绩已可查看。' : '请按安排参加线下考试，不支持在线答题；成绩可查看后开放结果入口。') : (canOpen ? '' : '当前暂无可进入的入口，请等待安排或成绩发布。') };
}
