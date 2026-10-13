/** 后台页签最小持久化结构。 */
export interface AdminTab {
  key: string;
  title: string;
  path: string;
  closable: boolean;
}

/** 页签归并只依赖路由路径、参数和查询参数。 */
export interface AdminTabRoute {
  path: string;
  fullPath: string;
  params: Record<string, string | string[]>;
  query: Record<string, unknown>;
}

const EXAM_STAGE_TITLE: Record<string, string> = {
  information: '考试信息',
  assignment: '人员配置',
  publish: '发布核查',
  attempts: '考试情况'
};
const COURSE_STAGE_TITLE: Record<string, string> = {
  basic: '基础信息',
  content: '内容编排',
  publish: '发布版本',
  offering: '开课配置'
};

/** 读取单值路由参数或查询参数，数组值不参与资源标识。 */
function getSingleValue(value: unknown): string | undefined {
  return typeof value === 'string' ? value : undefined;
}

/** 将 URL 查询参数转为页签识别所需的单值对象。 */
function parseQuery(searchParams: URLSearchParams): Record<string, string> {
  return Object.fromEntries(searchParams.entries());
}

/** 从持久化路径还原最小路由信息，用于清理旧版本重复页签。 */
function createTabRoute(path: string): AdminTabRoute {
  const url = new URL(path, 'https://safety-training.local');
  const examDetailMatch = url.pathname.match(/^\/admin\/business-center\/exams\/exam\/(\d+)$/);

  return {
    path: url.pathname,
    fullPath: `${url.pathname}${url.search}`,
    params: examDetailMatch ? { examId: examDetailMatch[1] } : {},
    query: parseQuery(url.searchParams)
  };
}

/** 考试、课程工作台按资源归并页签，阶段查询参数只驱动当前工作台面板。 */
export function getAdminTabKey(currentRoute: AdminTabRoute): string {
  const examId = getSingleValue(currentRoute.params.examId);
  if (examId && /^\/admin\/business-center\/exams\/exam\/\d+$/.test(currentRoute.path)) {
    return `/admin/business-center/exams/exam/${examId}`;
  }

  const courseId = getSingleValue(currentRoute.query.courseId);
  if (courseId && currentRoute.path === '/admin/business-center/course/center') {
    return `/admin/business-center/course/center/${courseId}`;
  }

  return currentRoute.fullPath;
}

/** 根据工作台模式和资源标识生成可区分的页签标题。 */
export function getAdminTabTitle(currentRoute: AdminTabRoute, fallbackTitle: string): string {
  const examId = getSingleValue(currentRoute.params.examId);
  const tabTitle = getSingleValue(currentRoute.query.tabTitle)?.trim();
  if (currentRoute.path === '/admin/business-center/exams/exam/create') {
    return '新增考试';
  }
  if (examId && /^\/admin\/business-center\/exams\/exam\/\d+$/.test(currentRoute.path)) {
    const stage = getSingleValue(currentRoute.query.stage) ?? 'information';
    return `${tabTitle || `考试 #${examId}`} · ${EXAM_STAGE_TITLE[stage] ?? '考试信息'}`;
  }

  if (currentRoute.path === '/admin/business-center/course/center') {
    const courseId = getSingleValue(currentRoute.query.courseId);
    const mode = getSingleValue(currentRoute.query.mode);
    if (courseId) {
      const stage = getSingleValue(currentRoute.query.stage) ?? 'basic';
      return `${tabTitle || `课程 #${courseId}`} · ${COURSE_STAGE_TITLE[stage] ?? '基础信息'}`;
    }
    return mode === 'create' ? '新增课程' : '课程列表';
  }

  return fallbackTitle;
}

/** 规范化浏览器会话中的旧页签，避免同一资源残留多个不可区分的标签。 */
export function normalizeAdminTabs(storedTabs: AdminTab[]): AdminTab[] {
  const normalizedTabs = new Map<string, AdminTab>();

  storedTabs.forEach((tab) => {
    const tabRoute = createTabRoute(tab.path);
    const key = getAdminTabKey(tabRoute);
    normalizedTabs.set(key, {
      ...tab,
      key,
      title: getAdminTabTitle(tabRoute, tab.title),
      path: tabRoute.fullPath
    });
  });

  return [...normalizedTabs.values()];
}

/** 同一资源更新最近访问阶段，不同资源保留独立页签。 */
export function upsertAdminTab(tabs: AdminTab[], currentRoute: AdminTabRoute, title: string): AdminTab[] {
  const key = getAdminTabKey(currentRoute);
  const tabTitle = getAdminTabTitle(currentRoute, title);
  const existingIndex = tabs.findIndex((tab) => tab.key === key);
  if (existingIndex < 0) {
    return [...tabs, { key, title: tabTitle, path: currentRoute.fullPath, closable: true }];
  }

  return tabs.map((tab, index) => (index === existingIndex ? { ...tab, title: tabTitle, path: currentRoute.fullPath } : tab));
}
