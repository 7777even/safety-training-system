import type { ApiResponse } from '@shared/types';
import type { CourseContentMode, CoursePointMode, CoursePublishStatus, EnabledStatus, SourceDownloadMode } from '@/constants/course';
import { OPTIMISTIC_LOCK_ERROR_CODE } from '@/constants/course';
import {
  mockAssignCourseCategories,
  mockCreateCourse,
  mockCreateCourseCategory,
  mockDeleteCourse,
  mockDeleteCourseCategory,
  mockGetCourseCategoryTree,
  mockGetCourseDetail,
  mockGetCoursePage,
  mockSwitchCourseContentMode,
  mockUpdateCourse,
  mockUpdateCourseCategory
} from '@/mock/course-foundation';
import { request } from './request';

/** 统一课程 Mock 开关：只有显式设置为 true 时启用 Mock。 */
const USE_API_MOCK = import.meta.env.VITE_USE_API_MOCK === 'true';

/** 可保留业务码的课程接口错误，供页面识别乐观锁冲突。 */
export class CourseApiError extends Error {
  constructor(
    public readonly code: number,
    message: string
  ) {
    super(message);
    this.name = 'CourseApiError';
  }
}

/** 判断错误是否为 Contract 定义的乐观锁冲突。 */
export function isOptimisticLockError(error: unknown): boolean {
  return error instanceof CourseApiError && error.code === OPTIMISTIC_LOCK_ERROR_CODE;
}

function unwrapResponse<TData>(response: ApiResponse<TData>): TData {
  if (response.code !== 0) {
    throw new CourseApiError(response.code, response.message || '请求失败，请稍后重试。');
  }
  return response.data;
}

/** 课程分类树节点。 */
export interface CourseCategory {
  courseCategoryId: number;
  parentId: number;
  categoryName: string;
  description?: string;
  sortOrder?: number;
  status: EnabledStatus;
  version: number;
  children?: CourseCategory[];
}

export interface CategoryTreeQuery {
  categoryName?: string;
  status?: EnabledStatus;
}

export interface CategoryCreatePayload {
  parentId: number;
  categoryName: string;
  description?: string;
  sortOrder?: number;
  status?: EnabledStatus;
}

export interface CategoryUpdatePayload {
  parentId: number;
  categoryName: string;
  description?: string;
  sortOrder?: number;
  status: EnabledStatus;
  version: number;
}

/** 查询课程分类树。 */
export async function getCourseCategoryTree(params: CategoryTreeQuery = {}): Promise<CourseCategory[]> {
  const response = USE_API_MOCK
    ? await mockGetCourseCategoryTree(params)
    : await request<ApiResponse<CourseCategory[]>>({ url: '/api/course/categories/tree', method: 'GET', params });
  return unwrapResponse(response as ApiResponse<CourseCategory[]>);
}

/** 新增课程分类。 */
export async function createCourseCategory(data: CategoryCreatePayload): Promise<number> {
  const response = USE_API_MOCK
    ? await mockCreateCourseCategory(data)
    : await request<ApiResponse<number>>({ url: '/api/course/categories', method: 'POST', data });
  return unwrapResponse(response);
}

/** 更新课程分类；父级和排序字段同时承担移动排序语义。 */
export async function updateCourseCategory(categoryId: number, data: CategoryUpdatePayload): Promise<void> {
  const response = USE_API_MOCK
    ? await mockUpdateCourseCategory(categoryId, data)
    : await request<ApiResponse<null>>({ url: `/api/course/categories/${categoryId}`, method: 'PUT', data });
  unwrapResponse(response);
}

/** 删除课程分类。 */
export async function deleteCourseCategory(categoryId: number): Promise<void> {
  const response = USE_API_MOCK
    ? await mockDeleteCourseCategory(categoryId)
    : await request<ApiResponse<null>>({ url: `/api/course/categories/${categoryId}`, method: 'DELETE' });
  unwrapResponse(response);
}

/** 课程分页查询条件，字段名与 Contract 保持一致。 */
export interface CoursePageQuery {
  pageNum: number;
  pageSize: number;
  'query.courseName'?: string;
  'query.teacherUserId'?: number;
  'query.publishStatus'?: CoursePublishStatus;
  'query.status'?: EnabledStatus;
  'query.categoryId'?: number;
}

export interface CourseListItem {
  courseId: number;
  courseCode: string;
  courseName: string;
  contentMode: CourseContentMode;
  publishStatus: CoursePublishStatus;
  status: EnabledStatus;
  updateTime?: string;
}

/** Contract 通用分页结果。 */
export interface CoursePageResult<TItem> {
  list: TItem[];
  total: number;
}

interface CourseMutableFields {
  /** 主讲教师，null清空。 */
  teacherUserId?: number | null;
  courseName: string;
  /** 课程封面上传接口返回的文件 ID；null 表示清空封面。 */
  coverFileId?: number | null;
  introduction?: string;
  teachingObjective?: string;
  syllabus?: string;
  assessmentMethod?: string;
  credit: number;
  pointMode: CoursePointMode;
  pointValue: number;
  sourceDownloadMode: SourceDownloadMode;
  isSpeedPlayAllowed: EnabledStatus;
  isStudyAnticheatEnabled: EnabledStatus;
  isStudyDescriptionEnabled: EnabledStatus;
  isWatermarkEnabled: EnabledStatus;
}

export interface CourseCreatePayload extends CourseMutableFields {
  courseCode: string;
  contentMode: CourseContentMode;
  status?: EnabledStatus;
  categoryIds?: number[];
}

export interface CourseUpdatePayload extends CourseMutableFields {
  status: EnabledStatus;
  version: number;
}

export interface CourseDetail extends CourseMutableFields {
  teacherName?: string | null;
  courseId: number;
  courseCode: string;
  currentVersionId?: number | null;
  contentMode: CourseContentMode;
  publishStatus: CoursePublishStatus;
  publishedTime?: string | null;
  status: EnabledStatus;
  version: number;
  categoryIds?: number[];
}

/** 课程封面上传结果。 */
export interface CourseCoverUploadResult {
  fileInfoId: number;
  fileName: string;
  fileSize: number;
  contentType: string;
}

/** 分页查询课程。 */
export async function getCoursePage(params: CoursePageQuery): Promise<CoursePageResult<CourseListItem>> {
  const response = USE_API_MOCK
    ? await mockGetCoursePage(params)
    : await request<ApiResponse<CoursePageResult<CourseListItem>>>({ url: '/api/course/courses', method: 'GET', params });
  return unwrapResponse(response as ApiResponse<CoursePageResult<CourseListItem>>);
}

/** 创建草稿课程。 */
export async function createCourse(data: CourseCreatePayload): Promise<number> {
  const response = USE_API_MOCK
    ? await mockCreateCourse(data)
    : await request<ApiResponse<number>>({ url: '/api/course/courses', method: 'POST', data });
  return unwrapResponse(response);
}

/** 上传课程封面图片，并返回可写入 coverFileId 的文件信息。 */
export async function uploadCourseCover(file: File, onProgress?: (percent: number) => void, signal?: AbortSignal): Promise<CourseCoverUploadResult> {
  if (USE_API_MOCK) {
    onProgress?.(100);
    return {
      fileInfoId: Date.now(),
      fileName: file.name,
      fileSize: file.size,
      contentType: file.type
    };
  }
  const data = new FormData();
  data.append('file', file);
  return unwrapResponse(
    await request<ApiResponse<CourseCoverUploadResult>>({
      url: '/api/course/covers',
      method: 'POST',
      data,
      headers: { 'Content-Type': 'multipart/form-data' },
      signal,
      onUploadProgress: (event) => {
        if (event.total) onProgress?.((event.loaded / event.total) * 100);
      }
    })
  );
}

/** 读取当前课程的受控封面图片，用于管理端缩略图和放大预览。 */
export async function getCourseCoverPreview(courseId: number): Promise<Blob> {
  if (USE_API_MOCK) {
    return new Blob(
      [
        '<svg xmlns="http://www.w3.org/2000/svg" width="640" height="360"><rect width="100%" height="100%" fill="#e6f4ff"/><text x="50%" y="50%" text-anchor="middle" dominant-baseline="middle" fill="#409eff" font-size="32">课程封面</text></svg>'
      ],
      { type: 'image/svg+xml' }
    );
  }
  return request<Blob>({
    url: `/api/course/courses/${courseId}/cover`,
    method: 'GET',
    responseType: 'blob'
  });
}

/** 查询课程详情。 */
export async function getCourseDetail(courseId: number): Promise<CourseDetail> {
  const response = USE_API_MOCK
    ? await mockGetCourseDetail(courseId)
    : await request<ApiResponse<CourseDetail>>({ url: `/api/course/courses/${courseId}`, method: 'GET' });
  return unwrapResponse(response as ApiResponse<CourseDetail>);
}

/** 更新课程基本信息。 */
export async function updateCourse(courseId: number, data: CourseUpdatePayload): Promise<void> {
  const response = USE_API_MOCK
    ? await mockUpdateCourse(courseId, data)
    : await request<ApiResponse<null>>({ url: `/api/course/courses/${courseId}`, method: 'PUT', data });
  unwrapResponse(response);
}

/** 逻辑删除课程。 */
export async function deleteCourse(courseId: number): Promise<void> {
  const response = USE_API_MOCK
    ? await mockDeleteCourse(courseId)
    : await request<ApiResponse<null>>({ url: `/api/course/courses/${courseId}`, method: 'DELETE' });
  unwrapResponse(response);
}

/** 覆盖课程分类关联。 */
export async function assignCourseCategories(courseId: number, categoryIds: number[]): Promise<void> {
  const response = USE_API_MOCK
    ? await mockAssignCourseCategories(courseId, categoryIds)
    : await request<ApiResponse<null>>({
        url: `/api/course/courses/${courseId}/categories`,
        method: 'PUT',
        data: { categoryIds }
      });
  unwrapResponse(response);
}

/** 独立切换课程目录模式，避免基本信息更新后继续使用旧版本。 */
export async function switchCourseContentMode(courseId: number, contentMode: CourseContentMode, version: number): Promise<void> {
  const response = USE_API_MOCK
    ? await mockSwitchCourseContentMode(courseId, contentMode, version)
    : await request<ApiResponse<null>>({
        url: `/api/course/courses/${courseId}/content-mode`,
        method: 'PUT',
        data: { contentMode, version }
      });
  unwrapResponse(response);
}
