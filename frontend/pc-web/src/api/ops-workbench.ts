import type { ApiResponse } from '@shared/types';
import { request } from './request';

/** 企业工作台快捷入口与分类待办，字段以 ops.openapi.json 为准。 */
export interface OpsShortcut { code: string; title: string; path: string; permission: string }
export interface OpsTodo { code: string; title: string; count: number }
export interface OpsWorkbench {
  shortcuts: OpsShortcut[];
  publishedCourseCount: number;
  runningExamCount: number;
  runningPlanCount: number;
  currentParticipantCount: number;
  endedPassedCount: number;
  endedFailedCount: number;
  openTodoCount: number;
  todos: OpsTodo[];
}

/** 查询当前登录企业的正式工作台事实。 */
export async function getOpsWorkbench(): Promise<OpsWorkbench> {
  const result = await request<ApiResponse<OpsWorkbench>>({ url: '/api/ops/workbench/enterprise', method: 'GET' });
  if (result.code !== 0) throw new Error(result.message || '请求失败，请稍后重试。');
  return result.data;
}
