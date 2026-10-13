import type { ApiResponse } from '@shared/types';
import { mockGetPersonPickerPage } from '@/mock/person-picker';
import { request } from './request';

/** `/api/common/person-picker` 的人员记录。 */
export interface PersonPickerItem {
  userId: number;
  userCode: string;
  userName: string;
  organizationId: number;
  deptId?: number;
  deptName?: string;
  memberType?: string;
  jobTitle?: string;
  status: 0 | 1;
}

export interface PersonPickerQuery {
  pageNum: number;
  pageSize: number;
  'query.organizationId': number;
  'query.keyword'?: string;
  'query.deptId'?: number;
  'query.tagId'?: number;
  'query.positionId'?: number;
  /** 条件用户组的当前成员筛选。 */
  'query.groupId'?: number;
  'query.status'?: 0 | 1;
}

export interface PersonPickerPage {
  list: PersonPickerItem[];
  total: number;
}

/** TODO replace real api：设置 VITE_USE_API_MOCK=false 后直接请求同一 Contract。 */
const USE_API_MOCK = import.meta.env.VITE_USE_API_MOCK !== 'false';

function unwrap<T>(response: ApiResponse<T>): T {
  if (response.code !== 0) throw new Error(response.message || '请求失败，请稍后重试。');
  return response.data;
}

export async function getPersonPickerPage(query: PersonPickerQuery): Promise<PersonPickerPage> {
  return unwrap(
    // 新增分类条件直接消费真实接口，旧Mock不具备动态培训事实。
    (USE_API_MOCK && !query['query.positionId'] && !query['query.groupId']
      ? await mockGetPersonPickerPage(query)
      : await request<ApiResponse<PersonPickerPage>>({ url: '/api/common/person-picker', method: 'GET', params: query })) as ApiResponse<PersonPickerPage>
  );
}
