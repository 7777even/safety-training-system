/**
 * API 契约的通用响应外壳。
 * 具体 code 语义必须由 docs/api 中已确认的契约定义。
 */
export interface ApiResponse<TData, TCode = number> {
  code: TCode;
  message: string;
  data: TData;
}

/** 本地账号登录请求，与 /api/auth/login Contract 对齐。 */
export interface LocalLoginRequest {
  identityType: 'local';
  authParams: {
    orgCode: string;
    userCode: string;
    password: string;
  };
}

/** 登录成功后的令牌信息。 */
export interface LoginResult {
  token: string;
}

/** 后端返回的可访问菜单树节点。 */
export interface MenuTreeItem {
  menuId: number;
  parentId?: number;
  menuName: string;
  path?: string;
  component?: string;
  perms?: string;
  icon?: string;
  sortOrder?: number;
  isVisible?: number;
  menuType?: string;
  moduleId?: number;
  children?: MenuTreeItem[];
}

/** 当前登录用户资料，字段来自 /api/system/users/profile。 */
export interface AuthenticatedUser {
  userId?: number;
  userCode?: string;
  userName?: string;
  phone?: string;
  status?: number;
  deptId?: number;
  deptName?: string;
  createBy?: number;
  createByName?: string;
  createTime?: string;
  version?: number;
  roleIds?: number[];
  roles?: string[];
  perms?: string[];
  menus?: MenuTreeItem[];
  organizationId?: number;
  /** 账号所属机构名称；普通账号与本次登录机构一致。 */
  orgName?: string;
  /** 会话租户类型；仅用户资料接口回填，用于按渠道收敛后台导航。 */
  orgType?: string;
}
