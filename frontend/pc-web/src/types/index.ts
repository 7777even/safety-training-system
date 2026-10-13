import type { AuthenticatedUser } from '@shared/types';

/** 路由展示权限所使用的最小元数据结构。 */
export interface RouteDisplayMeta {
  title: string;
  requiresAuth: boolean;
  roles?: readonly string[];
}

/** 当前用户资料与系统管理 API Contract 对齐。 */
export type UserInfo = AuthenticatedUser;
