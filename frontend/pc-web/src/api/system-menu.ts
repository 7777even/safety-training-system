import type { ApiResponse } from '@shared/types';
import { request } from './request';

/** 菜单可见状态与 Contract 的 0 隐藏、1 可见保持一致。 */
export type MenuVisibility = 0 | 1;

/** Contract 定义的菜单类型。 */
export type MenuType = 'D' | 'M' | 'C' | 'F';

/** 菜单树节点。 */
export interface SystemMenu {
  menuId: number;
  parentId: number;
  menuName: string;
  path?: string;
  component?: string;
  perms?: string;
  icon?: string;
  sortOrder?: number;
  isVisible: MenuVisibility;
  menuType: MenuType;
  moduleId?: number;
  version: number;
  children?: SystemMenu[];
}

/** 菜单树筛选条件。 */
export interface MenuTreeQuery {
  menuName?: string;
  perms?: string;
  isVisible?: MenuVisibility;
}

/** 菜单新增请求。 */
export interface MenuCreatePayload {
  parentId: number;
  menuName: string;
  path?: string;
  component?: string;
  perms?: string;
  icon?: string;
  sortOrder?: number;
  isVisible: MenuVisibility;
  menuType: MenuType;
  moduleId?: number;
}

/** 菜单编辑请求，必须携带乐观锁版本号。 */
export interface MenuUpdatePayload extends MenuCreatePayload {
  version: number;
}

/** 批量可见性提交的菜单版本映射。 */
export interface MenuVersionItem {
  menuId: number;
  version: number;
}

/** 解包统一响应并转换业务错误。 */
function unwrapResponse<TData>(response: ApiResponse<TData>): TData {
  if (response.code !== 0) {
    throw new Error(response.message || '请求失败，请稍后重试。');
  }

  return response.data;
}

/** 查询菜单树。 */
export async function getMenuTree(query: MenuTreeQuery = {}): Promise<SystemMenu[]> {
  const response = await request<ApiResponse<SystemMenu[]>>({
    url: '/api/system/menus/tree',
    method: 'GET',
    params: query
  });

  return unwrapResponse(response);
}

/** 新增菜单。 */
export async function createMenu(payload: MenuCreatePayload): Promise<number> {
  const response = await request<ApiResponse<number>>({
    url: '/api/system/menus',
    method: 'POST',
    data: payload
  });

  return unwrapResponse(response);
}

/** 修改菜单。 */
export async function updateMenu(menuId: number, payload: MenuUpdatePayload): Promise<void> {
  const response = await request<ApiResponse<null>>({
    url: `/api/system/menus/${menuId}`,
    method: 'PUT',
    data: payload
  });

  unwrapResponse(response);
}

/** 删除菜单。 */
export async function deleteMenu(menuId: number): Promise<void> {
  const response = await request<ApiResponse<null>>({
    url: `/api/system/menus/${menuId}`,
    method: 'DELETE'
  });

  unwrapResponse(response);
}

/** 修改单个菜单的可见性。 */
export async function updateMenuVisibility(menuId: number, isVisible: MenuVisibility, version: number): Promise<void> {
  const response = await request<ApiResponse<null>>({
    url: `/api/system/menus/${menuId}/visibility`,
    method: 'PUT',
    data: { isVisible, version }
  });

  unwrapResponse(response);
}

/** 批量修改菜单可见性。 */
export async function updateMenuBatchVisibility(visibleIds: number[], items: MenuVersionItem[]): Promise<void> {
  const response = await request<ApiResponse<null>>({
    url: '/api/system/menus/batch-visibility',
    method: 'PUT',
    data: { visibleIds, items }
  });

  unwrapResponse(response);
}
