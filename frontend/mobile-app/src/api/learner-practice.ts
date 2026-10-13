import type { ApiResponse, FavoriteQuestion, LearnerPage, PracticeAnswer, PracticeQuestion, PracticeSession, PracticeStart, PracticeTask } from '@shared/types';
import { request } from './request';

/** 本批只调用真实接口，不以Mock或本地存储代替本人业务数据。 */
async function call<T>(method: 'GET' | 'POST' | 'PUT' | 'DELETE', url: string, data?: object, params?: Record<string, unknown>): Promise<T> {
  const result = await request<ApiResponse<T>>({ method, url, data: data ? { ...data } : undefined, params });
  if (result.code !== 0) throw new Error(result.message || '请求失败，请稍后重试。');
  return result.data;
}
const ROOT = '/api/question/learner';
/** 分配练习、错题和会话分页。 */
export const getPracticeTasks = (params: Record<string, unknown>): Promise<LearnerPage<PracticeTask>> => call('GET', `${ROOT}/practices`, undefined, params);
export const getWrongQuestions = (params: Record<string, unknown>): Promise<LearnerPage<PracticeQuestion>> => call('GET', `${ROOT}/wrong-questions`, undefined, params);
/** 读取本人收藏题目的安全摘要。 */
export const getFavoriteQuestions = (params: Record<string, unknown>): Promise<LearnerPage<FavoriteQuestion>> => call('GET', `${ROOT}/favorite-questions`, undefined, params);
export const getPracticeSessions = (params: Record<string, unknown>): Promise<LearnerPage<PracticeSession>> => call('GET', `${ROOT}/sessions`, undefined, params);
/** 启动和恢复本人会话。 */
export const startPractice = (data: PracticeStart): Promise<PracticeSession> => call('POST', `${ROOT}/sessions`, data);
export const getPractice = (id: number): Promise<PracticeSession> => call('GET', `${ROOT}/sessions/${id}`);
/** 单题保存、提交和清除。 */
export const savePracticeAnswer = (id: number, itemId: number, data: PracticeAnswer): Promise<PracticeSession> => call('PUT', `${ROOT}/sessions/${id}/items/${itemId}/answer`, data);
export const submitPractice = (id: number): Promise<PracticeSession> => call('POST', `${ROOT}/sessions/${id}/submit`);
export const clearPractice = (id: number): Promise<void> => call('DELETE', `${ROOT}/sessions/${id}`);
/** 只修改本人题目状态。 */
export const setQuestionFavorite = (id: number, favorite: boolean): Promise<void> => call('PUT', `${ROOT}/questions/${id}/favorite`, { favorite });
export const setWrongDisposition = (id: number, disposition: 'DELETED' | 'MASTERED'): Promise<void> => call('PUT', `${ROOT}/wrong-questions/${id}/disposition`, { disposition });
