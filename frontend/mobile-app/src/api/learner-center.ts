import { examClientInfo } from '@shared/libs/exam-client-info';
import type { ApiResponse, LearnerPage, PersonalAssessmentTask, PersonalAssessmentHistory, PersonalFavoriteCourse, PersonalTeacher } from '@shared/types';
import { request } from './request';
/** 本人中心调用真实领域接口，Result失败不伪装成功。 */
async function read<T>(url: string, params: Record<string, unknown>): Promise<T> {
  const response = await request<ApiResponse<T>>({ url, method: 'GET', params });
  if (response.code !== 0) throw new Error(response.message || '加载失败');
  return response.data;
}
/** 聚合正式与课程考核，保留各自引用。 */
export const getPersonalAssessments = (params: Record<string, unknown>): Promise<LearnerPage<PersonalAssessmentTask>> => read('/api/exam/learner/assessment-tasks', params);
/** 本人允许回看的全部历史考卷。 */
export const getPersonalAttemptHistory = (params: Record<string, unknown>): Promise<PersonalAssessmentHistory[]> => read('/api/exam/learner/attempt-history', params);
/** 课程测验仍由报名/课程项域启动，不使用独立考试接口。 */
export async function startPersonalCourseAttempt(enrollmentId: number, itemId: number): Promise<number> {
  const response = await request<ApiResponse<{ examAttemptId: number }>>({ url: `/api/course/learning/enrollments/${enrollmentId}/items/${itemId}/attempts`, method: 'POST', data: { clientAttemptToken: `personal-course-${Date.now()}-${Math.random()}`, ...examClientInfo('H5', typeof navigator === 'undefined' ? '' : navigator.userAgent) } });
  if (response.code !== 0) throw new Error(response.message || '启动失败');
  return response.data.examAttemptId;
}

/** 可访问课程目录与本人收藏列表复用同一课程状态。 */
export const getFavoriteCourseCatalog = (params: Record<string, unknown>): Promise<LearnerPage<PersonalFavoriteCourse>> => read('/api/course/learning/favorite-course-catalog', params);
export const getFavoriteCourses = (params: Record<string, unknown>): Promise<LearnerPage<PersonalFavoriteCourse>> => read('/api/course/learning/favorite-courses', params);
export const getFavoriteCourseDetail = (id: number): Promise<PersonalFavoriteCourse> => read(`/api/course/learning/favorite-courses/${id}`, {});
/** 取消仍调用真实本人接口，不通过本地过滤伪装成功。 */
export async function setCourseFavorite(id: number, favorite: boolean): Promise<void> {
  const response = await request<ApiResponse<void>>({ url: `/api/course/learning/favorite-courses/${id}`, method: 'PUT', data: { favorite } });
  if (response.code !== 0) throw new Error(response.message || '操作失败');
}

/** 同机构教师目录、本人关注列表与安全详情。 */
export const getPersonalTeachers = (params: Record<string, unknown>): Promise<LearnerPage<PersonalTeacher>> => read('/api/course/learning/teachers', params);
export const getTeacherFollows = (params: Record<string, unknown>): Promise<LearnerPage<PersonalTeacher>> => read('/api/course/learning/teacher-follows', params);
export const getPersonalTeacher = (id: number): Promise<PersonalTeacher> => read(`/api/course/learning/teachers/${id}`, {});
/** 教师关注不接受客户端指定学员身份。 */
export async function setTeacherFollow(id: number, followed: boolean): Promise<void> {
  const response = await request<ApiResponse<void>>({ url: `/api/course/learning/teacher-follows/${id}`, method: 'PUT', data: { followed } });
  if (response.code !== 0) throw new Error(response.message || '操作失败');
}
