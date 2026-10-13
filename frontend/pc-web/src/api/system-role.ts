import type { ApiResponse, MenuTreeItem } from '@shared/types';
import { request } from './request';

/** 角色状态与 Contract 的 0 停用、1 正常保持一致。 */
export type RoleStatus = 0 | 1;

/** 角色级别仅用于业务归类，不产生隐含特权。 */
export type RoleLevel = 'ADMIN' | 'TEACHER' | 'STUDENT';

/** 角色分页记录。 */
export interface SystemRole {
  roleId: number;
  organizationId: number;
  /** 所属租户（机构）名称，由服务端展示翻译回填。 */
  orgName?: string;
  roleName: string;
  roleCode: string;
  roleLevel?: RoleLevel;
  description?: string;
  status: RoleStatus;
  createTime?: string;
  version: number;
  menuIds?: number[];
}

/** 角色列表 Contract 规定的查询参数。 */
export interface RoleListQuery {
  pageNum: number;
  pageSize: number;
  'query.roleName'?: string;
  'query.roleCode'?: string;
  'query.status'?: RoleStatus;
}

/** 角色新增请求。 */
export interface RoleCreatePayload {
  organizationId: number;
  roleName: string;
  roleCode: string;
  roleLevel?: RoleLevel;
  description?: string;
  status: RoleStatus;
  menuIds: number[];
}

/** 角色编辑请求，包含乐观锁版本号。 */
export interface RoleUpdatePayload extends Omit<RoleCreatePayload, 'organizationId'> {
  version: number;
}

/** 角色列表响应。 */
interface RolePageResult {
  list: SystemRole[];
  total: number;
}

/** 解包统一响应并抛出可展示的业务错误。 */
function unwrapResponse<TData>(response: ApiResponse<TData>): TData {
  if (response.code !== 0) {
    throw new Error(response.message || '请求失败，请稍后重试。');
  }

  return response.data;
}

/** 分页查询角色。 */
export async function getRolePage(query: RoleListQuery): Promise<RolePageResult> {
  const response = await request<ApiResponse<RolePageResult>>({
    url: '/api/system/roles',
    method: 'GET',
    params: query
  });

  return unwrapResponse(response);
}

/** 新增角色。 */
export async function createRole(payload: RoleCreatePayload): Promise<number> {
  const response = await request<ApiResponse<number>>({
    url: '/api/system/roles',
    method: 'POST',
    data: payload
  });

  return unwrapResponse(response);
}

/** 获取角色详情及其已分配菜单。 */
export async function getRoleDetail(roleId: number): Promise<SystemRole> {
  const response = await request<ApiResponse<SystemRole>>({
    url: `/api/system/roles/${roleId}`,
    method: 'GET'
  });

  return unwrapResponse(response);
}

/** 修改角色。 */
export async function updateRole(roleId: number, payload: RoleUpdatePayload): Promise<void> {
  const response = await request<ApiResponse<null>>({
    url: `/api/system/roles/${roleId}`,
    method: 'PUT',
    data: payload
  });

  unwrapResponse(response);
}

/** 删除角色。 */
export async function deleteRole(roleId: number): Promise<void> {
  const response = await request<ApiResponse<null>>({
    url: `/api/system/roles/${roleId}`,
    method: 'DELETE'
  });

  unwrapResponse(response);
}

/** 获取菜单树，用于角色菜单授权。 */
export async function getRoleMenuTree(): Promise<MenuTreeItem[]> {
  const response = await request<ApiResponse<MenuTreeItem[]>>({
    url: '/api/system/menus/tree',
    method: 'GET'
  });

  return unwrapResponse(response);
}
