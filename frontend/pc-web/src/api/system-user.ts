import type { ApiResponse } from '@shared/types';
import { request } from './request';

/** 角色下拉选项的独立加载上限，不作为用户列表的分页大小。 */
const ROLE_OPTION_LOAD_PAGE_SIZE = 100;

/** 用户状态与 OpenAPI Contract 的 0 停用、1 正常保持一致。 */
export type SystemStatus = 0 | 1;
export type MemberType = 'INTERNAL' | 'EXTERNAL' | 'REGULATOR';

/** 用户列表的分页返回结构。 */
export interface PageResult<TItem> {
  list: TItem[];
  total: number;
}

/** 用户管理列表记录。 */
export interface SystemUser {
  userId: number;
  userCode: string;
  userName: string;
  phone?: string;
  status: SystemStatus;
  deptId: number | null;
  deptName?: string | null;
  createTime?: string;
  version: number;
  roleIds?: number[];
  roles?: string[];
  organizationId?: number;
  /** 所属租户（机构）名称，由服务端展示翻译回填。 */
  orgName?: string;
  memberType?: MemberType;
  email?: string;
  jobTitle?: string;
  entryDate?: string;
  tagIds?: number[];
  positionIds?: number[];
  primaryPositionId?: number;
}

/** 部门树节点，用于用户筛选和编辑表单。 */
export interface DeptTreeNode {
  deptId: number;
  parentId: number;
  deptName: string;
  status: SystemStatus;
  children?: DeptTreeNode[];
}

/** 角色下拉选项所需的 Contract 字段。 */
export interface RoleOption {
  roleId: number;
  roleName: string;
  status: SystemStatus;
}

/** 用户分页筛选参数，键名严格使用 Contract 中的 query.* 形式。 */
export interface UserListQuery {
  pageNum: number;
  pageSize: number;
  'query.userCode'?: string;
  'query.userName'?: string;
  'query.phone'?: string;
  'query.status'?: SystemStatus;
  'query.deptId'?: number;
}

/** 新增用户请求。 */
export interface UserCreatePayload {
  userCode: string;
  userName: string;
  phone?: string;
  deptId: number;
  password: string;
  status: SystemStatus;
  roleIds?: number[];
  memberType?: MemberType;
  email?: string;
  jobTitle?: string;
  entryDate?: string;
  tagIds?: number[];
  positionIds?: number[];
  primaryPositionId?: number;
}

/** 修改用户请求，必须携带乐观锁版本号。 */
export interface UserUpdatePayload {
  userName: string;
  phone?: string;
  deptId: number;
  status: SystemStatus;
  roleIds?: number[];
  version: number;
  memberType?: MemberType;
  email?: string;
  jobTitle?: string;
  entryDate?: string;
  tagIds?: number[];
  positionIds?: number[];
  primaryPositionId?: number;
}

/** 解包系统管理接口的统一响应。 */
function unwrapResponse<TData>(response: ApiResponse<TData>): TData {
  if (response.code !== 0) {
    throw new Error(response.message || '请求失败，请稍后重试。');
  }

  return response.data;
}

/** 分页查询用户。 */
export async function getUserPage(query: UserListQuery): Promise<PageResult<SystemUser>> {
  const response = await request<ApiResponse<PageResult<SystemUser>>>({
    url: '/api/system/users',
    method: 'GET',
    params: query
  });

  return unwrapResponse(response);
}

/** 新增用户。 */
export async function createUser(payload: UserCreatePayload): Promise<number> {
  const response = await request<ApiResponse<number>>({
    url: '/api/system/users',
    method: 'POST',
    data: payload
  });

  return unwrapResponse(response);
}

/** 修改用户。 */
export async function updateUser(userId: number, payload: UserUpdatePayload): Promise<void> {
  const response = await request<ApiResponse<null>>({
    url: `/api/system/users/${userId}`,
    method: 'PUT',
    data: payload
  });

  unwrapResponse(response);
}

/** 删除用户。 */
export async function deleteUser(userId: number): Promise<void> {
  const response = await request<ApiResponse<null>>({
    url: `/api/system/users/${userId}`,
    method: 'DELETE'
  });

  unwrapResponse(response);
}

/** 重置用户密码。 */
export async function resetUserPassword(userId: number, password: string): Promise<void> {
  const response = await request<ApiResponse<null>>({
    url: `/api/system/users/${userId}/reset-password`,
    method: 'PUT',
    data: { password }
  });

  unwrapResponse(response);
}

/** 查询部门树。 */
export async function getDeptTree(): Promise<DeptTreeNode[]> {
  const response = await request<ApiResponse<DeptTreeNode[]>>({
    url: '/api/system/depts/tree',
    method: 'GET'
  });

  return unwrapResponse(response);
}

/** 查询可分配角色；Contract 仅提供分页接口，因此请求首个 100 条记录作为选择项。 */
export async function getRoleOptions(): Promise<RoleOption[]> {
  const response = await request<ApiResponse<PageResult<RoleOption>>>({
    url: '/api/system/roles',
    method: 'GET',
    params: { pageNum: 1, pageSize: ROLE_OPTION_LOAD_PAGE_SIZE }
  });

  return unwrapResponse(response).list;
}
