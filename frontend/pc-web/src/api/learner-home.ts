import type { ApiResponse, LearnerExamTaskPage, LearnerHomeTask, LearnerMessage, LearnerPage, LearnerReference } from '@shared/types';
import { learnerTarget } from '@shared/utils/learner-home';
import { request } from './request';

/** 本批接口直接读取真实后端，错误不能伪装成空列表。 */
async function read<T>(url: string, params: Record<string, unknown> = {}): Promise<T> {
  const response = await request<ApiResponse<T>>({ url, method: 'GET', params });
  if (response.code !== 0) throw new Error(response.message || '读取失败');
  return response.data;
}
/** 本人首页，不传用户或机构ID。 */
export const getLearnerHome = (params: Record<string, unknown>): Promise<LearnerPage<LearnerHomeTask>> => read('/api/learner/home/tasks', params);
/** 持久化消息按原投递分页。 */
export const getLearnerMessages = (params: Record<string, unknown>): Promise<LearnerPage<LearnerMessage>> => read('/api/learner/messages', params);

/** 本人考试任务包含线上、线下、专项练习和题库练习，直接读取真实接口。 */
export const getMessageExamTasks = (pageNum: number): Promise<LearnerExamTaskPage> => read('/api/exam/learner/tasks', { pageNum, pageSize: 200 });

/** 旧消息跳转前重新读取当前业务权限和动作，不用旧消息绕过下架或结果可见规则。 */
export async function getLearnerTarget(reference: LearnerReference): Promise<string> {
  if (reference.kind === 'FORMAL_EXAM') {
    for (let page = 1; ; page++) {
      const result = await getLearnerHome({ pageNum: page, pageSize: 200, 'query.kind': 'FORMAL_EXAM' });
      const task = result.list.find(row => row.resourceId === reference.resourceId && row.parentId === reference.parentId);
      if (task) {
        if (!task.action) throw new Error('当前考试尚无可进入的入口，可能正在等待成绩');
        return learnerTarget(task, 'PC');
      }
      if (page * 200 >= result.total) throw new Error('考试已取消、不可见或当前账号无权访问');
    }
  }
  const url = reference.kind === 'TRAINING' ? `/api/training/learner/participants/${reference.resourceId}`
    : reference.kind === 'COURSE_CATALOG' ? `/api/course/learning/favorite-courses/${reference.resourceId}`
      : `/api/course/learning/enrollments/${reference.resourceId}`;
  const path = learnerTarget(reference, 'PC');
  await read(url);
  return path;
}
