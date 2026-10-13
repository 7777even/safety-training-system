/** 课程工作台支持的固定阶段。 */
export type CourseWorkspaceStage = 'basic' | 'content' | 'publish' | 'offering';
/** 课程中心当前呈现列表、新增或详情。 */
export type CourseWorkspaceMode = 'list' | 'create' | 'detail';

export interface CourseWorkspaceState {
  mode: CourseWorkspaceMode;
  courseId: number;
  stage: CourseWorkspaceStage;
}

type WorkspaceQueryValue = string | null | Array<string | null> | undefined;
export type CourseWorkspaceQuery = Record<string, WorkspaceQueryValue>;

const WORKSPACE_STAGES: CourseWorkspaceStage[] = ['basic', 'content', 'publish', 'offering'];

/** 将路由查询参数解析为稳定的课程工作台状态。 */
export function resolveCourseWorkspace(query: CourseWorkspaceQuery): CourseWorkspaceState {
  const rawCourseId = Array.isArray(query.courseId) ? query.courseId[0] : query.courseId;
  const parsedCourseId = Number(rawCourseId || 0);
  const courseId = Number.isInteger(parsedCourseId) && parsedCourseId > 0 ? parsedCourseId : 0;

  if (courseId > 0) {
    const rawStage = Array.isArray(query.stage) ? query.stage[0] : query.stage;
    const stage = WORKSPACE_STAGES.includes(rawStage as CourseWorkspaceStage) ? (rawStage as CourseWorkspaceStage) : 'basic';
    return { mode: 'detail', courseId, stage };
  }

  const rawMode = Array.isArray(query.mode) ? query.mode[0] : query.mode;
  return rawMode === 'create' ? { mode: 'create', courseId: 0, stage: 'basic' } : { mode: 'list', courseId: 0, stage: 'basic' };
}

/** 生成保存后可以刷新、前进和后退的工作台查询参数。 */
export function buildCourseWorkspaceQuery(courseId: number, stage: CourseWorkspaceStage, tabTitle?: string): Record<string, string> {
  return { courseId: String(courseId), stage, ...(tabTitle ? { tabTitle } : {}) };
}
