import type { ApiResponse } from '@shared/types';
import { request } from './request';

/** 部门状态与 Contract 的 0 停用、1 正常保持一致。 */
export type DeptStatus = 0 | 1;

/** 部门类型必须与系统管理 Contract 的枚举保持一致。 */
export type DeptType = 'SCHOOL' | 'COLLEGE' | 'MAJOR' | 'DEPARTMENT' | 'CLASS' | 'OTHER';

/** 部门树节点。 */
export interface SystemDept {
  deptId: number;
  organizationId: number;
  /** 所属机构名称，由服务端展示翻译回填。 */
  orgName?: string;
  parentId: number;
  deptName: string;
  deptCode: string;
  deptType?: DeptType;
  description?: string;
  sortOrder?: number;
  leader?: string;
  phone?: string;
  status: DeptStatus;
  version: number;
  children?: SystemDept[];
}

/** 部门树筛选参数。 */
export interface DeptTreeQuery {
  deptName?: string;
  status?: DeptStatus;
}

/** 新增部门请求。 */
export interface DeptCreatePayload {
  organizationId?: number;
  parentId?: number;
  deptName: string;
  deptCode: string;
  deptType?: DeptType;
  description?: string;
  sortOrder?: number;
  leader?: string;
  phone?: string;
  status: DeptStatus;
}

/** 修改部门请求，必须包含乐观锁版本号。 */
export interface DeptUpdatePayload extends Omit<DeptCreatePayload, 'organizationId'> {
  version: number;
}

/** 解包系统管理接口的统一响应。 */
function unwrapResponse<TData>(response: ApiResponse<TData>): TData {
  if (response.code !== 0) {
    throw new Error(response.message || '请求失败，请稍后重试。');
  }

  return response.data;
}

/** 查询部门树。 */
export async function getDeptManagementTree(query: DeptTreeQuery = {}): Promise<SystemDept[]> {
  const response = await request<ApiResponse<SystemDept[]>>({
    url: '/api/system/depts/tree',
    method: 'GET',
    params: query
  });

  return unwrapResponse(response);
}

/** 新增部门。 */
export async function createDept(payload: DeptCreatePayload): Promise<number> {
  const response = await request<ApiResponse<number>>({
    url: '/api/system/depts',
    method: 'POST',
    data: payload
  });

  return unwrapResponse(response);
}

/** 修改部门。 */
export async function updateDept(deptId: number, payload: DeptUpdatePayload): Promise<void> {
  const response = await request<ApiResponse<null>>({
    url: `/api/system/depts/${deptId}`,
    method: 'PUT',
    data: payload
  });

  unwrapResponse(response);
}

/** 删除部门。 */
export async function deleteDept(deptId: number): Promise<void> {
  const response = await request<ApiResponse<null>>({
    url: `/api/system/depts/${deptId}`,
    method: 'DELETE'
  });

  unwrapResponse(response);
}
