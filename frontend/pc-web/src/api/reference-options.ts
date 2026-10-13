import type { ApiResponse } from '@shared/types';
import { getDeptManagementTree, type SystemDept } from './system-dept';
import type { Audience } from './audience';
import type { OrgPosition } from './org-position';
import type { OrgTag } from './org-tag';
import type { PersonPickerItem } from './person-picker';
import { request } from './request';

/** 圈人规则主体选项类型。 */
export type ReferenceKind = 'DEPT' | 'TAG' | 'POSITION' | 'USER' | 'AUDIENCE';

/** 规则主体下拉选项。 */
export interface ReferenceOption {
  id: number;
  name: string;
}

interface PageResult<TItem> {
  list: TItem[];
  total: number;
}

function unwrap<TData>(response: ApiResponse<TData>): TData {
  if (response.code !== 0) {
    throw new Error(response.message || '请求失败，请稍后重试。');
  }

  return response.data;
}

/** 将部门树展平为带层级名称的选项，避免同名部门无法区分。 */
function flattenDepartments(nodes: SystemDept[], parentNames: string[] = []): ReferenceOption[] {
  return nodes.flatMap((node) => {
    const names = [...parentNames, node.deptName];
    return [{ id: node.deptId, name: names.join(' / ') }, ...flattenDepartments(node.children ?? [], names)];
  });
}

/** 查询圈人规则使用的真实主体选项，不依赖页面 Mock。 */
export async function getReferenceOptions(kind: ReferenceKind, organizationId?: number): Promise<ReferenceOption[]> {
  if (!organizationId) {
    throw new Error('请先选择所属机构。');
  }

  if (kind === 'DEPT') {
    const tree = await getDeptManagementTree({ status: 1 });
    return flattenDepartments(tree.filter((item) => item.organizationId === organizationId));
  }

  if (kind === 'TAG') {
    const response = await request<ApiResponse<PageResult<OrgTag>>>({
      url: '/api/org/tags',
      method: 'GET',
      params: { pageNum: 1, pageSize: 100, 'query.organizationId': organizationId, 'query.status': 1 }
    });
    return unwrap(response).list.map((item) => ({ id: item.tagId, name: item.tagName }));
  }

  if (kind === 'POSITION') {
    const response = await request<ApiResponse<PageResult<OrgPosition>>>({
      url: '/api/org/positions',
      method: 'GET',
      params: { pageNum: 1, pageSize: 100, 'query.organizationId': organizationId, 'query.status': 1 }
    });
    return unwrap(response).list.map((item) => ({ id: item.positionId, name: item.positionName }));
  }

  if (kind === 'USER') {
    const response = await request<ApiResponse<PageResult<PersonPickerItem>>>({
      url: '/api/common/person-picker',
      method: 'GET',
      params: { pageNum: 1, pageSize: 100, 'query.organizationId': organizationId, 'query.status': 1 }
    });
    return unwrap(response).list.map((item) => ({ id: item.userId, name: `${item.userName}（${item.userCode}）` }));
  }

  const response = await request<ApiResponse<PageResult<Audience>>>({
    url: '/api/common/audiences',
    method: 'GET',
    params: {
      pageNum: 1,
      pageSize: 100,
      'query.organizationId': organizationId,
      'query.audienceKind': 'TEMPLATE',
      'query.purpose': 'PARTICIPANT',
      'query.status': 1
    }
  });
  return unwrap(response).list.map((item) => ({ id: item.audienceId, name: item.audienceName }));
}
