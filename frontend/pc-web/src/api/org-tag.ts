import type { ApiResponse } from '@shared/types';
import * as orgCollaborationMock from '@/mock/org-collaboration';
import { request } from './request';

/** 机构标签状态。 */
export type OrgTagStatus = 0 | 1;
/** 机构标签记录。 */
export interface OrgTag {
  tagId: number;
  organizationId: number;
  /** 所属机构名称，由服务端展示翻译回填。 */
  orgName?: string;
  tagName: string;
  description?: string;
  status: OrgTagStatus;
  version: number;
  createTime?: string;
}
/** 标签分页查询参数。 */
export interface OrgTagPageQuery {
  pageNum: number;
  pageSize: number;
  'query.organizationId'?: number;
  'query.tagName'?: string;
  'query.status'?: OrgTagStatus;
}
/** 新增标签请求。 */
export interface OrgTagCreatePayload {
  organizationId: number;
  tagName: string;
  description?: string;
  status: OrgTagStatus;
}
/** 更新标签请求。 */
export interface OrgTagUpdatePayload extends Omit<OrgTagCreatePayload, 'organizationId'> {
  version: number;
}
interface OrgTagPage {
  list: OrgTag[];
  total: number;
}

/** 默认使用 Contract Mock；联调通过后按接口删除对应 Mock 分支。 */
const USE_ORG_COLLABORATION_MOCK = import.meta.env.VITE_USE_API_MOCK !== 'false';
/** 解包统一 Result 响应。 */
function unwrap<TData>(response: ApiResponse<TData>): TData {
  if (response.code !== 0) throw new Error(response.message || '请求失败，请稍后重试。');
  return response.data;
}
/** 分页查询机构标签。 */
export async function getOrgTagPage(query: OrgTagPageQuery): Promise<OrgTagPage> {
  return unwrap(
    (USE_ORG_COLLABORATION_MOCK
      ? await orgCollaborationMock.mockGetOrgTagPage(query)
      : await request<ApiResponse<OrgTagPage>>({ url: '/api/org/tags', method: 'GET', params: query })) as ApiResponse<OrgTagPage>
  );
}
/** 创建机构标签。 */
export async function createOrgTag(payload: OrgTagCreatePayload): Promise<number> {
  return unwrap(
    (USE_ORG_COLLABORATION_MOCK
      ? await orgCollaborationMock.mockCreateOrgTag(payload)
      : await request<ApiResponse<number>>({ url: '/api/org/tags', method: 'POST', data: payload })) as ApiResponse<number>
  );
}
/** 获取机构标签详情。 */
export async function getOrgTagDetail(tagId: number): Promise<OrgTag> {
  return unwrap(
    (USE_ORG_COLLABORATION_MOCK
      ? await orgCollaborationMock.mockGetOrgTagDetail(tagId)
      : await request<ApiResponse<OrgTag>>({ url: `/api/org/tags/${tagId}`, method: 'GET' })) as ApiResponse<OrgTag>
  );
}
/** 更新机构标签。 */
export async function updateOrgTag(tagId: number, payload: OrgTagUpdatePayload): Promise<void> {
  unwrap(
    (USE_ORG_COLLABORATION_MOCK
      ? await orgCollaborationMock.mockUpdateOrgTag(tagId, payload)
      : await request<ApiResponse<null>>({ url: `/api/org/tags/${tagId}`, method: 'PUT', data: payload })) as ApiResponse<null>
  );
}
/** 删除机构标签。 */
export async function deleteOrgTag(tagId: number): Promise<void> {
  unwrap(
    (USE_ORG_COLLABORATION_MOCK
      ? await orgCollaborationMock.mockDeleteOrgTag(tagId)
      : await request<ApiResponse<null>>({ url: `/api/org/tags/${tagId}`, method: 'DELETE' })) as ApiResponse<null>
  );
}
