import type { MenuTreeItem } from '@shared/types';
import type { Component } from 'vue';
import { RouterView, type RouteRecordRaw, type Router } from 'vue-router';
import type { AdminModuleNavigationItem, AdminNavigationItem } from '@/constants/admin-navigation';
import type { UserInfo } from '@/types';
import { hasPermission } from '@/utils/permission';
import AdminLayout from '@/layouts/AdminLayout.vue';
import PortalLayout from '@/layouts/PortalLayout.vue';

/** 当前后端首页误配时使用的临时兼容路径。 */
const DASHBOARD_PATH = '/dashboard';
/** PC 门户首页的实际访问路径。 */
const PORTAL_PATH = '/portal';
/** 后台菜单统一使用的路径前缀。 */
const ADMIN_PATH_PREFIX = '/admin';
/** 后端遗留成果菜单使用 /result，本地映射后才能进入后台统一壳层。 */
const LEGACY_RESULT_PATH = '/result';
/** 门户统一布局路由的固定名称，用于会话切换时清理动态路由。 */
const PORTAL_LAYOUT_ROUTE_NAME = 'PortalLayout';

/**
 * 监管渠道会话不进入顶部导航的业务模块：应用中心 9530、企业工作台 9800。
 * <p>
 * 监管角色为支撑「只读下钻」被授予了应用中心里的路由祖先菜单，菜单树因此带上 9530；
 * 若直接把它当一级模块展示，监管账号会看到一个点进去必然 403 的业务模块。这里只收敛导航，
 * 路由注册与权限码保持不变，监管工作台的只读快捷入口仍可正常打开。模块 ID 见
 * `V20260929120000__add_m5_pc_menus.sql`，与后端 `SysMenuServiceImpl.hiddenModuleIds` 同源。
 */
const REGULATOR_HIDDEN_ADMIN_MODULE_IDS = new Set([9530, 9800]);

/** 监管渠道会话；缺少会话租户类型（旧缓存数据）时不做渠道收敛，保持既有行为。 */
function isRegulatorChannelSession(userInfo: UserInfo | null): boolean {
  return userInfo?.orgType === 'REGULATOR';
}

/** Vite 在构建期收集的页面模块，避免运行时按任意字符串加载文件。 */
const viewModules = import.meta.glob(['../views/**/*.vue', '!../views/portal/index.vue']) as Record<string, () => Promise<{ default: Component }>>;

/** 已解析为可注册路由的菜单节点。 */
interface ResolvedMenuRoute {
  menu: MenuTreeItem;
  name: string;
  fullPath: string;
  route: RouteRecordRaw;
  children: ResolvedMenuRoute[];
}

/** 不参与菜单导航、但需要按既有按钮权限直接访问的后台页面定义。 */
interface AdminInternalRouteDefinition {
  name: string;
  path: string;
  component: () => Promise<{ default: Component }>;
  permission: string;
  sourcePath: string;
  title: string;
}

/** 门户工作流内部页定义：依附门户布局，不参与顶部导航。 */
interface PortalInternalRouteDefinition {
  name: string;
  path: string;
  component: () => Promise<{ default: Component }>;
  permission: string;
  sourcePath: string;
  title: string;
}

/** M2 独立工作流页面；来源列表菜单存在且按钮获授权时才注册。 */
const ADMIN_INTERNAL_ROUTE_DEFINITIONS: AdminInternalRouteDefinition[] = [
  {
    name: 'OfflineClassStatistics',
    path: '/admin/business-center/training/plan/offline-sessions/:offlineSessionId/statistics',
    component: () => import('@/views/admin/business-center/training/offline-statistics/index.vue'),
    permission: 'training:offline-score:list',
    sourcePath: '/admin/business-center/training/plan',
    title: '线下课堂统计'
  },
  {
    name: 'CourseStatistics',
    path: '/admin/business-center/course/center/:courseId/statistics',
    component: () => import('@/views/admin/business-center/course/statistics/index.vue'),
    permission: 'course:offering:list',
    sourcePath: '/admin/business-center/course/center',
    title: '课程学习统计'
  },
  {
    name: 'ExamStatistics',
    path: '/admin/business-center/exams/exam/:examId/statistics',
    component: () => import('@/views/admin/business-center/exams/exam/statistics/index.vue'),
    permission: 'exam:exam:list',
    sourcePath: '/admin/business-center/exams/exam',
    title: '考情分析'
  },
  {
    name: 'RegulationInstitutionPlans',
    path: '/admin/regulation/institutions/:organizationId/plans',
    component: () => import('@/views/admin/regulation/plans/index.vue'),
    permission: 'regulation:overview:view',
    sourcePath: '/admin/regulation/workbench',
    title: '机构培训计划'
  },
  {
    name: 'QuestionCreate',
    path: '/admin/business-center/exams/question/create',
    component: () => import('@/views/admin/business-center/exams/question/create/index.vue'),
    permission: 'question:question:add',
    sourcePath: '/admin/business-center/exams/question',
    title: '新增题目'
  },
  {
    name: 'QuestionDetail',
    path: '/admin/business-center/exams/question/:questionId',
    component: () => import('@/views/admin/business-center/exams/question/detail/index.vue'),
    permission: 'question:question:list',
    sourcePath: '/admin/business-center/exams/question',
    title: '题目详情'
  },
  {
    name: 'PaperCreate',
    path: '/admin/business-center/exams/paper/workspace',
    component: () => import('@/views/admin/business-center/exams/paper/workspace/index.vue'),
    permission: 'paper:paper:add',
    sourcePath: '/admin/business-center/exams/paper',
    title: '新增试卷'
  },
  {
    name: 'PaperWorkspace',
    path: '/admin/business-center/exams/paper/workspace/:paperId',
    component: () => import('@/views/admin/business-center/exams/paper/workspace/index.vue'),
    permission: 'paper:paper:edit',
    sourcePath: '/admin/business-center/exams/paper',
    title: '编辑试卷'
  },
  {
    name: 'PaperVersion',
    path: '/admin/business-center/exams/paper/:paperId/version/:paperVersionId',
    component: () => import('@/views/admin/business-center/exams/paper/version/index.vue'),
    permission: 'paper:paper:list',
    sourcePath: '/admin/business-center/exams/paper',
    title: '试卷版本详情'
  },
  {
    name: 'ExamCreate',
    path: '/admin/business-center/exams/exam/create',
    component: () => import('@/views/admin/business-center/exams/exam/workspace/index.vue'),
    permission: 'exam:exam:add',
    sourcePath: '/admin/business-center/exams/exam',
    title: '考试工作台'
  },
  {
    name: 'ExamDetail',
    path: '/admin/business-center/exams/exam/:examId',
    component: () => import('@/views/admin/business-center/exams/exam/workspace/index.vue'),
    permission: 'exam:exam:list',
    sourcePath: '/admin/business-center/exams/exam',
    title: '考试工作台'
  },
  {
    name: 'ExamGrading',
    path: '/admin/business-center/exams/exam/:examId/grading/:examAttemptId',
    component: () => import('@/views/admin/business-center/exams/exam/components/grading/index.vue'),
    permission: 'exam:review',
    // 批阅页由考试工作台入口触发，不依赖跨考试批阅菜单是否已配置。
    sourcePath: '/admin/business-center/exams/exam',
    title: '教师批阅'
  },
  {
    name: 'ExamResults',
    path: '/admin/business-center/exams/exam/:examId/results/:examAttemptId',
    component: () => import('@/views/admin/business-center/exams/exam/components/results/index.vue'),
    permission: 'exam:score:publish',
    // 成绩处置页由考试工作台入口触发，不依赖跨考试成绩菜单是否已配置。
    sourcePath: '/admin/business-center/exams/exam',
    title: '成绩处置'
  },
  {
    name: 'TrainingStandardCreate',
    path: '/admin/business-center/training/standard/create',
    component: () => import('@/views/admin/business-center/training/standard/workspace/index.vue'),
    permission: 'training:standard:add',
    sourcePath: '/admin/business-center/training/standard',
    title: '新增培训标准'
  },
  {
    name: 'TrainingStandardWorkspace',
    path: '/admin/business-center/training/standard/:trainingStandardId',
    component: () => import('@/views/admin/business-center/training/standard/workspace/index.vue'),
    permission: 'training:standard:list',
    sourcePath: '/admin/business-center/training/standard',
    title: '培训标准工作台'
  },
  {
    name: 'TrainingTemplateCreate',
    path: '/admin/business-center/training/template/create',
    component: () => import('@/views/admin/business-center/training/template/workspace/index.vue'),
    permission: 'training:template:add',
    sourcePath: '/admin/business-center/training/template',
    title: '新增培训模板'
  },
  {
    name: 'TrainingTemplateWorkspace',
    path: '/admin/business-center/training/template/:trainingTemplateId',
    component: () => import('@/views/admin/business-center/training/template/workspace/index.vue'),
    permission: 'training:template:list',
    sourcePath: '/admin/business-center/training/template',
    title: '培训模板工作台'
  },
  {
    name: 'TrainingClassStatistics',
    path: '/admin/business-center/training/plan/class-statistics',
    component: () => import('@/views/admin/business-center/training/class-statistics/index.vue'),
    permission: 'training:plan:list',
    sourcePath: '/admin/business-center/training/plan',
    title: '班级统计'
  },
  {
    name: 'TrainingStatistics',
    path: '/admin/business-center/training/plan/statistics',
    component: () => import('@/views/admin/business-center/training/statistics/index.vue'),
    permission: 'training:result:list',
    sourcePath: '/admin/business-center/training/plan',
    title: '培训统计与记录'
  },
  {
    name: 'TrainingPlanCreate',
    path: '/admin/business-center/training/plan/create',
    component: () => import('@/views/admin/business-center/training/plan/workspace/index.vue'),
    permission: 'training:plan:add',
    sourcePath: '/admin/business-center/training/plan',
    title: '新增培训计划'
  },
  {
    name: 'TrainingPlanWorkspace',
    path: '/admin/business-center/training/plan/:trainingPlanId',
    component: () => import('@/views/admin/business-center/training/plan/workspace/index.vue'),
    permission: 'training:plan:list',
    sourcePath: '/admin/business-center/training/plan',
    title: '培训计划工作台'
  },
  {
    // 正式成果依附具体培训计划，不作为无参数的侧栏菜单页面。
    name: 'TrainingEndedResult',
    path: '/admin/business-center/training/plan/:trainingPlanId/ended-result',
    component: () => import('@/views/admin/result/training-result/index.vue'),
    permission: 'training:result:list',
    sourcePath: '/admin/business-center/training/plan',
    title: '培训成果'
  },
  {
    name: 'TrainingTeacherTaskDetail',
    path: '/admin/business-center/training/teacher-tasks/:offlineSessionId',
    component: () => import('@/views/admin/business-center/training/teacher-tasks/detail/index.vue'),
    permission: 'training:teacher:task',
    sourcePath: '/admin/business-center/training/teacher-tasks',
    title: '线下教学任务'
  },
  {
    name: 'EvaluationFormCreate',
    path: '/admin/business-center/training/evaluation-form/workspace/create',
    component: () => import('@/views/admin/business-center/training/evaluation-form/workspace/index.vue'),
    permission: 'evaluation:form:add',
    sourcePath: '/admin/business-center/training/evaluation-form',
    title: '新增评价表单'
  },
  {
    name: 'EvaluationFormWorkspace',
    path: '/admin/business-center/training/evaluation-form/workspace/:evaluationFormId',
    component: () => import('@/views/admin/business-center/training/evaluation-form/workspace/index.vue'),
    permission: 'evaluation:form:list',
    sourcePath: '/admin/business-center/training/evaluation-form',
    title: '评价表单工作台'
  },
  {
    name: 'EvaluationFormVersion',
    path: '/admin/business-center/training/evaluation-form/version/:formVersionId',
    component: () => import('@/views/admin/business-center/training/evaluation-form/version/index.vue'),
    permission: 'evaluation:form:list',
    sourcePath: '/admin/business-center/training/evaluation-form',
    title: '评价表单版本'
  },
  {
    name: 'SurveyWorkspace',
    path: '/admin/result/survey/workspace/:surveyId?',
    component: () => import('@/views/admin/result/survey/workspace/index.vue'),
    permission: 'survey:survey:list',
    sourcePath: '/admin/result/survey',
    title: '问卷工作台'
  },
  {
    name: 'TrainingArchive',
    path: '/admin/business-center/training/plan/:trainingPlanId/archive',
    component: () => import('@/views/admin/result/archive/index.vue'),
    permission: 'archive:training:list',
    sourcePath: '/admin/business-center/training/plan',
    title: '培训档案'
  }
];

/** 考试中心只保留一个门户入口，作答和结果均由来源菜单授权后注册为内部路由。 */
const PORTAL_INTERNAL_ROUTE_DEFINITIONS: PortalInternalRouteDefinition[] = [
  {
    name: 'PortalCourseLearning',
    path: '/portal/class/learning/:enrollmentId',
    component: () => import('@/views/portal/class/learning.vue'),
    permission: 'asset:asset:preview',
    sourcePath: '/portal/class',
    title: '课程学习'
  },
  {
    name: 'PortalCourseAssessmentAttempt',
    path: '/portal/class/learning/:enrollmentId/items/:itemId/attempts/:attemptId',
    component: () => import('@/views/portal/class/CourseAssessmentAttempt.vue'),
    permission: 'asset:asset:preview',
    sourcePath: '/portal/class',
    title: '课程考核作答'
  },
  {
    name: 'PortalCourseAssessmentResult',
    path: '/portal/class/learning/:enrollmentId/items/:itemId/results/:attemptId',
    component: () => import('@/views/portal/class/CourseAssessmentResult.vue'),
    permission: 'asset:asset:preview',
    sourcePath: '/portal/class',
    title: '课程考核结果'
  },
  {
    name: 'PortalExamNotice',
    path: '/portal/exams/:examId/notice/:examCandidateId',
    component: () => import('@/views/portal/exams/notice.vue'),
    permission: 'exam:attempt:start',
    sourcePath: '/portal/exams',
    title: '考生须知'
  },
  {
    name: 'PortalExamAttempt',
    path: '/portal/exams/:examId/attempt/:examAttemptId',
    component: () => import('@/views/portal/exams/attempt.vue'),
    permission: 'exam:learner:attempt:view',
    sourcePath: '/portal/exams',
    title: '考试作答'
  },
  {
    name: 'PortalExamResult',
    path: '/portal/exams/:examId/result/:examAttemptId',
    component: () => import('@/views/portal/exams/result.vue'),
    permission: 'exam:learner:attempt:view',
    sourcePath: '/portal/exams',
    title: '考试结果'
  },
  {
    name: 'PortalTrainingTasks',
    path: '/portal/training',
    component: () => import('@/views/portal/training/index.vue'),
    permission: 'training:learner:task',
    sourcePath: '/portal/training',
    title: '我的培训'
  },
  {
    name: 'PortalTrainingDetail',
    path: '/portal/training/:trainingParticipantId',
    component: () => import('@/views/portal/training/detail.vue'),
    permission: 'training:learner:task',
    sourcePath: '/portal/training',
    title: '培训详情'
  },
  {
    name: 'PortalTrainingExamAttempt',
    path: '/portal/training/attempt/:examAttemptId',
    component: () => import('@/views/portal/exams/attempt.vue'),
    permission: 'training:learner:task',
    sourcePath: '/portal/training',
    title: '培训考试作答'
  },
  {
    name: 'PortalTrainingExamNotice',
    path: '/portal/training/:trainingParticipantId/contents/:planContentId/exam-notice',
    component: () => import('@/views/portal/exams/notice.vue'),
    permission: 'training:learner:task',
    sourcePath: '/portal/training',
    title: '培训考试须知'
  },
  {
    name: 'PortalTrainingExamResult',
    path: '/portal/training/attempt/:examAttemptId/result',
    component: () => import('@/views/portal/exams/result.vue'),
    permission: 'training:learner:task',
    sourcePath: '/portal/training',
    title: '培训考试结果'
  },
  {
    name: 'PortalTrainingOfflineSession',
    path: '/portal/training/offline-session/:offlineSessionId',
    component: () => import('@/views/portal/training/offline-session.vue'),
    permission: 'training:learner:task',
    sourcePath: '/portal/training',
    title: '线下培训场次'
  }
];

/** 门户顶部导航使用的已授权菜单项。 */
export interface PortalNavigationItem {
  title: string;
  path: string;
}

let activeRouteSignature = '';
let activeRootRouteNames: string[] = [];
let activeAuthorizedPaths: string[] = [];

/** 过滤不可见和按钮级菜单，并按接口排序字段稳定输出。 */
function getVisibleRouteMenus(menus: MenuTreeItem[] | undefined): MenuTreeItem[] {
  return [...(menus ?? [])]
    .filter((menu) => menu.isVisible !== 0 && menu.menuType !== 'F')
    .sort((left, right) => (left.sortOrder ?? 0) - (right.sortOrder ?? 0));
}

/** 清理接口返回的组件相对路径。 */
function normalizeComponent(component: string | undefined): string {
  return (component ?? '')
    .trim()
    .replace(/^\/+/, '')
    .replace(/\.vue$/, '');
}

/** 将接口相对路径补全为当前菜单的完整访问路径。 */
function resolveFullPath(path: string | undefined, parentPath?: string): string | undefined {
  const normalizedPath = path?.trim();

  if (!normalizedPath) {
    return parentPath;
  }

  if (normalizedPath.startsWith('/')) {
    const fullPath = normalizedPath === DASHBOARD_PATH ? PORTAL_PATH : normalizedPath;
    return fullPath === LEGACY_RESULT_PATH || fullPath.startsWith(`${LEGACY_RESULT_PATH}/`)
      ? `${ADMIN_PATH_PREFIX}${fullPath}`
      : fullPath;
  }

  return parentPath ? `${parentPath.replace(/\/$/, '')}/${normalizedPath.replace(/^\/+/, '')}` : undefined;
}

/** 判断菜单是否属于后台框架。 */
function isAdminMenuPath(path: string): boolean {
  return path === ADMIN_PATH_PREFIX || path.startsWith(`${ADMIN_PATH_PREFIX}/`);
}

/** 判断菜单是否属于前台门户统一布局。 */
function isPortalMenuPath(path: string): boolean {
  return path === PORTAL_PATH || path.startsWith(`${PORTAL_PATH}/`);
}

/** 根据菜单组件和路径目录约定查找已构建的页面模块。 */
function resolveMenuComponent(menu: MenuTreeItem, fullPath: string, isAdminMenu: boolean): (() => Promise<{ default: Component }>) | undefined {
  const component = normalizeComponent(menu.component);
  const modulePaths: string[] = [];

  if (component) {
    if (fullPath.startsWith('/admin/result/') && !component.startsWith('admin/')) {
      modulePaths.push(`../views/admin/result/${component}.vue`);
    }
    if (isAdminMenu && !component.startsWith('admin/')) {
      modulePaths.push(`../views/admin/${component}.vue`);
    }

    modulePaths.push(`../views/${component}.vue`);
  }

  modulePaths.push(`../views${fullPath}/index.vue`);
  return modulePaths.map((path) => viewModules[path]).find((module) => module !== undefined);
}

/** 生成稳定且符合 Vue Router 命名规则的动态路由名称。 */
function getRouteName(menu: MenuTreeItem): string {
  return `Menu${menu.menuId}`;
}

/** 生成菜单对应的页面元信息。 */
function getRouteMeta(menu: MenuTreeItem) {
  return {
    title: menu.menuName,
    requiresAuth: true,
    menuId: menu.menuId,
    menuType: menu.menuType,
    icon: menu.icon
  };
}

/** 将单个菜单节点递归转换为路由；无页面组件的叶子菜单不会注册。 */
function resolveMenuRoute(
  menu: MenuTreeItem,
  parentPath: string | undefined,
  isAdminMenu: boolean,
  isRootMenu: boolean
): ResolvedMenuRoute | undefined {
  const fullPath = resolveFullPath(menu.path, parentPath);

  if (!fullPath) {
    return undefined;
  }

  const childMenus = getVisibleRouteMenus(menu.children);
  const useAdminLayout = isRootMenu && isAdminMenuPath(fullPath);
  const childRoutes = childMenus
    .map((child) => resolveMenuRoute(child, fullPath, isAdminMenu || useAdminLayout, false))
    .filter((route): route is ResolvedMenuRoute => Boolean(route));
  const component = resolveMenuComponent(menu, fullPath, isAdminMenu || useAdminLayout);

  if (!component && childRoutes.length === 0) {
    return undefined;
  }

  const routeMeta = getRouteMeta(menu);
  let route: RouteRecordRaw;

  if (useAdminLayout) {
    const defaultChildPath = getFirstLeafPath(childRoutes);

    route = {
      path: fullPath,
      name: getRouteName(menu),
      component: AdminLayout,
      meta: routeMeta,
      children: defaultChildPath
        ? [
            {
              path: '',
              redirect: defaultChildPath
            },
            ...childRoutes.map((child) => child.route)
          ]
        : [
            {
              path: '',
              name: `${getRouteName(menu)}Content`,
              component: component as NonNullable<typeof component>,
              meta: routeMeta
            }
          ]
    };
  } else if (childRoutes.length > 0) {
    route = {
      path: fullPath,
      name: getRouteName(menu),
      component: component ?? RouterView,
      meta: routeMeta,
      children: childRoutes.map((child) => child.route)
    };
  } else {
    route = {
      path: fullPath,
      name: getRouteName(menu),
      component: component as NonNullable<typeof component>,
      meta: routeMeta
    };
  }

  return {
    menu,
    name: getRouteName(menu),
    fullPath,
    route,
    children: childRoutes
  };
}

/** 从 profile 菜单树生成所有可解析的 PC 动态路由。 */
function getResolvedMenuRoutes(userInfo: UserInfo | null): ResolvedMenuRoute[] {
  if (!userInfo) {
    return [];
  }

  return getVisibleRouteMenus(userInfo.menus)
    .map((menu) => resolveMenuRoute(menu, undefined, false, true))
    .filter((route): route is ResolvedMenuRoute => Boolean(route));
}

/** 将已解析的前台菜单收敛为一个门户父路由，保证顶栏在菜单切换时保持一致。 */
function buildRegisteredRouterRoutes(routes: ResolvedMenuRoute[]): RouteRecordRaw[] {
  const portalRoutes = routes.filter((route) => isPortalMenuPath(route.fullPath));
  const nonPortalRoutes = routes.filter((route) => !isPortalMenuPath(route.fullPath));

  if (portalRoutes.length === 0) {
    return nonPortalRoutes.map((route) => route.route);
  }

  return [
    {
      path: PORTAL_PATH,
      name: PORTAL_LAYOUT_ROUTE_NAME,
      component: PortalLayout,
      meta: { requiresAuth: true },
      children: portalRoutes.map((route) => ({
        ...route.route,
        path: route.fullPath.slice(PORTAL_PATH.length).replace(/^\//, '')
      }))
    },
    ...nonPortalRoutes.map((route) => route.route)
  ];
}

/** 展开已注册菜单路由的完整路径，用于登录跳转和访问守卫。 */
function collectRoutePaths(routes: ResolvedMenuRoute[]): string[] {
  return routes.flatMap((route) => [route.fullPath, ...collectRoutePaths(route.children)]);
}

/** 在已授权菜单树中查找某个列表页面及其所属后台模块根路由。 */
function findAuthorizedInternalRouteParent(
  routes: ResolvedMenuRoute[],
  sourcePath: string
): { moduleRoute: ResolvedMenuRoute; sourceRoute: ResolvedMenuRoute } | undefined {
  for (const moduleRoute of routes) {
    const sourceRoute = findResolvedRoute(moduleRoute, sourcePath);
    if (sourceRoute) {
      return { moduleRoute, sourceRoute };
    }
  }

  return undefined;
}

/** 递归按完整路径定位已解析菜单路由。 */
function findResolvedRoute(route: ResolvedMenuRoute, fullPath: string): ResolvedMenuRoute | undefined {
  if (route.fullPath === fullPath) {
    return route;
  }

  for (const child of route.children) {
    const matchedRoute = findResolvedRoute(child, fullPath);
    if (matchedRoute) {
      return matchedRoute;
    }
  }

  return undefined;
}

/** 注册当前用户已获授权的后台内部页面；这些页面不进入菜单树。 */
function registerAuthorizedInternalRoutes(router: Router, routes: ResolvedMenuRoute[], userInfo: UserInfo | null): string[] {
  const internalPaths: string[] = [];

  for (const definition of ADMIN_INTERNAL_ROUTE_DEFINITIONS) {
    const parent = findAuthorizedInternalRouteParent(routes, definition.sourcePath);
    if (!parent || !hasPermission(userInfo, definition.permission)) {
      continue;
    }

    router.addRoute(parent.moduleRoute.name, {
      path: definition.path,
      name: definition.name,
      component: definition.component,
      meta: {
        title: definition.title,
        requiresAuth: true,
        menuId: parent.sourceRoute.menu.menuId,
        menuType: 'INTERNAL',
        hiddenInMenu: true
      }
    });
    internalPaths.push(definition.path);
  }

  return internalPaths;
}

/** 注册门户已授权的工作流内部页，保证刷新和直链仍在门户布局内解析。 */
function registerAuthorizedPortalInternalRoutes(router: Router, routes: ResolvedMenuRoute[], userInfo: UserInfo | null): string[] {
  const internalPaths: string[] = [];

  for (const definition of PORTAL_INTERNAL_ROUTE_DEFINITIONS) {
    // 首页已有本人培训任务时，不要求额外的培训中心菜单；仍校验既有本人培训权限。
    const sourceRoute = routes.map((route) => findResolvedRoute(route, definition.sourcePath)).find(Boolean)
      ?? (definition.sourcePath === '/portal/training'
        ? routes.map((route) => findResolvedRoute(route, '/portal/home')).find(Boolean)
        : undefined);
    // 已有培训列表菜单时保留原菜单路由，兼容入口只服务缺少该菜单的账号。
    if (definition.name === 'PortalTrainingTasks' && sourceRoute?.fullPath === '/portal/training') continue;
    // 课程内部页继承“课程中心”菜单授权；学员不应再额外持有后台按钮权限才能进入作答页。
    const inheritsCourseMenuAccess = definition.sourcePath === '/portal/class';
    if (!sourceRoute || (!inheritsCourseMenuAccess && !hasPermission(userInfo, definition.permission))) {
      continue;
    }

    router.addRoute(PORTAL_LAYOUT_ROUTE_NAME, {
      path: definition.path.slice(`${PORTAL_PATH}/`.length),
      name: definition.name,
      component: definition.component,
      meta: {
        title: definition.title,
        requiresAuth: true,
        menuId: sourceRoute.menu.menuId,
        menuType: 'INTERNAL',
        hiddenInMenu: true
      }
    });
    internalPaths.push(definition.path);
  }

  return internalPaths;
}

/** 将已解析菜单转换为布局使用的可点击导航树。 */
function buildAdminNavigation(routes: ResolvedMenuRoute[]): AdminNavigationItem[] {
  const buildNavigation = (routes: ResolvedMenuRoute[]): AdminNavigationItem[] =>
    routes.map((route) => ({
      title: route.menu.menuName,
      path: route.fullPath,
      icon: route.menu.icon ?? '',
      children: route.children.length > 0 ? buildNavigation(route.children) : undefined
    }));

  return buildNavigation(routes);
}

/** 生成后台顶栏模块及其当前模块左侧菜单树（已按会话渠道收敛业务模块入口）。 */
export function getAuthorizedAdminModules(userInfo: UserInfo | null): AdminModuleNavigationItem[] {
  return buildAdminModules(userInfo, true);
}

/**
 * 构建后台模块列表（含模块内左侧菜单树）。
 * <p>
 * {@code applyChannelConvergence} 只应作用于**顶部导航展示**。归属查找（`findAuthorizedAdminModule`）
 * 必须用未收敛的完整列表：被隐藏的模块仍是其下只读下钻页面的载体，若一并滤掉，监管点开
 * 证书管理（挂在应用中心 9530 下、且 `/admin/result` 不落在任何模块 URL 前缀内）时找不到归属模块，
 * **整条侧栏会消失**。
 */
function buildAdminModules(
  userInfo: UserInfo | null,
  applyChannelConvergence: boolean
): AdminModuleNavigationItem[] {
  const regulatorChannel = applyChannelConvergence && isRegulatorChannelSession(userInfo);

  return getResolvedMenuRoutes(userInfo)
    .filter((route) => isAdminMenuPath(route.fullPath) && route.menu.menuType === 'D')
    // 监管会话只保留监管与系统管理渠道，业务模块仅作只读下钻的路由载体，不作为一级入口。
    .filter((route) => !regulatorChannel || !REGULATOR_HIDDEN_ADMIN_MODULE_IDS.has(route.menu.moduleId ?? -1))
    .map((route) => ({
      title: route.menu.menuName,
      path: route.fullPath,
      landingPath: getFirstLeafPath(route.children) ?? route.fullPath,
      icon: route.menu.icon ?? '',
      children: buildAdminNavigation(route.children)
    }));
}

/** 收集模块子树内全部导航路径，供 URL 前缀无法判定归属时按菜单树回溯。 */
function collectNavigationPaths(items: AdminNavigationItem[]): string[] {
  return items.flatMap((item) => [item.path, ...collectNavigationPaths(item.children ?? [])]);
}

/** 判断目标路径是否命中给定前缀（含完全相等）。 */
function isPathUnder(targetPath: string, basePath: string): boolean {
  return targetPath === basePath || targetPath.startsWith(`${basePath}/`);
}

/** 根据当前已授权路径定位所属后台模块，供布局决定是否展示侧栏。 */
export function findAuthorizedAdminModule(userInfo: UserInfo | null, path: string): AdminModuleNavigationItem | undefined {
  // 渠道收敛只作用于顶部导航展示，归属查找必须用完整列表，否则被隐藏模块下的页面会失去侧栏。
  const modules = buildAdminModules(userInfo, false);
  const prefixMatched = modules
    .filter((module) => isPathUnder(path, module.path))
    .sort((left, right) => right.path.length - left.path.length)[0];

  if (prefixMatched) {
    return prefixMatched;
  }

  // 成果菜单沿用后端遗留的绝对路径 /result 并被归一为 /admin/result，不落在任何模块的 URL 前缀内，
  // 但其菜单节点仍挂在应用中心下；此时按菜单归属回溯，否则进入成果页面时整条侧栏会被隐藏。
  return modules.find((module) => collectNavigationPaths(module.children).some((childPath) => isPathUnder(path, childPath)));
}

/** 展开门户目录下的可点击页面；目录节点本身不作为顶部导航项。 */
function collectPortalNavigation(route: ResolvedMenuRoute): PortalNavigationItem[] {
  if (route.children.length > 0) {
    return route.children.flatMap((child) => collectPortalNavigation(child));
  }

  return [{ title: route.menu.menuName, path: route.fullPath }];
}

/** 生成门户顶部导航，门户目录不显示，仅展示其已授权的页面菜单。 */
export function getAuthorizedPortalNavigation(userInfo: UserInfo | null): PortalNavigationItem[] {
  return getResolvedMenuRoutes(userInfo)
    .filter((route) => !isAdminMenuPath(route.fullPath))
    .flatMap((route) => collectPortalNavigation(route));
}

/** 返回当前角色菜单中第一个可访问页面，作为登录后的默认落点。 */
export function getDefaultMenuPath(userInfo: UserInfo | null): string | undefined {
  const findPagePath = (routes: ResolvedMenuRoute[]): string | undefined => {
    for (const route of routes) {
      if (route.children.length === 0) {
        return route.fullPath;
      }

      const childPath = findPagePath(route.children);
      if (childPath) {
        return childPath;
      }
    }

    return undefined;
  };

  return findPagePath(getResolvedMenuRoutes(userInfo));
}

/** 返回菜单排序中的第一个后台页面，作为门户进入后台的入口。 */
export function getFirstNestedMenuPath(userInfo: UserInfo | null): string | undefined {
  for (const route of getResolvedMenuRoutes(userInfo)) {
    if (isAdminMenuPath(route.fullPath)) {
      return route.children.length > 0 ? getFirstLeafPath(route.children) : route.fullPath;
    }
  }

  return undefined;
}

/** 从已解析路由树中取第一个页面叶子节点。 */
function getFirstLeafPath(routes: ResolvedMenuRoute[]): string | undefined {
  for (const route of routes) {
    if (route.children.length === 0) {
      return route.fullPath;
    }

    const childPath = getFirstLeafPath(route.children);
    if (childPath) {
      return childPath;
    }
  }

  return undefined;
}

/** 根据最新 profile 菜单树替换浏览器运行时动态路由。 */
export function synchronizeMenuRoutes(router: Router, userInfo: UserInfo | null): string[] {
  const routes = getResolvedMenuRoutes(userInfo);
  const registeredRoutes = buildRegisteredRouterRoutes(routes);
  const signature = JSON.stringify({ userId: userInfo?.userId, menus: userInfo?.menus, perms: userInfo?.perms });

  if (signature === activeRouteSignature) {
    return activeAuthorizedPaths;
  }

  activeRootRouteNames.forEach((name) => router.removeRoute(name));
  activeRootRouteNames = [];

  registeredRoutes.forEach((route) => {
    router.addRoute(route);
    activeRootRouteNames.push(route.name as string);
  });

  const internalPaths = [
    ...registerAuthorizedInternalRoutes(router, routes, userInfo),
    ...registerAuthorizedPortalInternalRoutes(router, routes, userInfo)
  ];

  activeRouteSignature = signature;
  activeAuthorizedPaths = [...collectRoutePaths(routes), ...internalPaths];
  return activeAuthorizedPaths;
}
