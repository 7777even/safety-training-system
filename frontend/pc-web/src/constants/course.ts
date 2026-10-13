/** 后端通用启停用状态。 */
export type EnabledStatus = 0 | 1;

/** 课程发布状态。 */
export type CoursePublishStatus = 'DRAFT' | 'PUBLISHED' | 'OFFLINE';

/** 课程目录模式。 */
export type CourseContentMode = 'SINGLE' | 'MULTI_CHAPTER';

/** 课程积分计算模式。 */
export type CoursePointMode = 'NONE' | 'PER_ITEM' | 'COURSE';

/** 课程源文件下载模式。 */
export type SourceDownloadMode = 'NONE' | 'ALL' | 'PARTIAL';

export const ENABLED_STATUS_TEXT: Record<EnabledStatus, string> = { 0: '停用', 1: '启用' };
export const COURSE_PUBLISH_STATUS_TEXT: Record<CoursePublishStatus, string> = {
  DRAFT: '草稿',
  PUBLISHED: '已发布',
  OFFLINE: '已下线'
};
export const COURSE_CONTENT_MODE_TEXT: Record<CourseContentMode, string> = {
  SINGLE: '单章节',
  MULTI_CHAPTER: '多章节'
};
export const COURSE_POINT_MODE_TEXT: Record<CoursePointMode, string> = {
  NONE: '不计积分',
  PER_ITEM: '按内容项',
  COURSE: '按课程'
};
export const SOURCE_DOWNLOAD_MODE_TEXT: Record<SourceDownloadMode, string> = {
  NONE: '不允许下载',
  ALL: '全部允许',
  PARTIAL: '部分允许'
};

/** Contract 定义的乐观锁冲突业务码。 */
export const OPTIMISTIC_LOCK_ERROR_CODE = 2040;
export const OPTIMISTIC_LOCK_ERROR_MESSAGE = '数据已被更新，请刷新后重试';
