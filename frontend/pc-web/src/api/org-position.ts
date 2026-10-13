import type { ApiResponse } from '@shared/types';
import * as orgCollaborationMock from '@/mock/org-collaboration';
import { request } from './request';

/** 机构岗位状态。 */
export type OrgPositionStatus = 0 | 1;
/** 机构岗位记录。 */
export interface OrgPosition {
  positionId: number;
  organizationId: number;
  /** 所属机构名称，由服务端展示翻译回填。 */
  orgName?: string;
  positionCode: string;
  positionName: string;
  description?: string;
  status: OrgPositionStatus;
  version: number;
}
/** 岗位分页查询参数。 */
export interface OrgPositionPageQuery {
  pageNum: number;
  pageSize: number;
  'query.organizationId'?: number;
  'query.positionCode'?: string;
  'query.positionName'?: string;
  'query.status'?: OrgPositionStatus;
}
/** 岗位新增请求。 */
export interface OrgPositionCreatePayload {
  organizationId: number;
  positionCode: string;
  positionName: string;
  description?: string;
  status: OrgPositionStatus;
}
/** 岗位更新请求。 */
export interface OrgPositionUpdatePayload extends Omit<OrgPositionCreatePayload, 'organizationId'> {
  version: number;
}
interface PageResult {
  list: OrgPosition[];
  total: number;
}

/** 默认使用 Contract Mock；联调通过后按接口删除对应 Mock 分支。 */
const USE_ORG_COLLABORATION_MOCK = import.meta.env.VITE_USE_API_MOCK !== 'false';
/** 解包统一 Result 响应。 */
function unwrap<T>(response: ApiResponse<T>): T {
  if (response.code !== 0) throw new Error(response.message || '请求失败，请稍后重试。');
  return response.data;
}
/** 分页查询岗位。 */
export async function getOrgPositionPage(query: OrgPositionPageQuery): Promise<PageResult> {
  return unwrap(
    (USE_ORG_COLLABORATION_MOCK
      ? await orgCollaborationMock.mockGetOrgPositionPage(query)
      : await request<ApiResponse<PageResult>>({ url: '/api/org/positions', method: 'GET', params: query })) as ApiResponse<PageResult>
  );
}
/** 创建岗位。 */
export async function createOrgPosition(payload: OrgPositionCreatePayload): Promise<number> {
  return unwrap(
    (USE_ORG_COLLABORATION_MOCK
      ? await orgCollaborationMock.mockCreateOrgPosition(payload)
      : await request<ApiResponse<number>>({ url: '/api/org/positions', method: 'POST', data: payload })) as ApiResponse<number>
  );
}
/** 查询岗位详情。 */
export async function getOrgPositionDetail(positionId: number): Promise<OrgPosition> {
  return unwrap(
    (USE_ORG_COLLABORATION_MOCK
      ? await orgCollaborationMock.mockGetOrgPositionDetail(positionId)
      : await request<ApiResponse<OrgPosition>>({ url: `/api/org/positions/${positionId}`, method: 'GET' })) as ApiResponse<OrgPosition>
  );
}
/** 更新岗位。 */
export async function updateOrgPosition(positionId: number, payload: OrgPositionUpdatePayload): Promise<void> {
  unwrap(
    (USE_ORG_COLLABORATION_MOCK
      ? await orgCollaborationMock.mockUpdateOrgPosition(positionId, payload)
      : await request<ApiResponse<null>>({ url: `/api/org/positions/${positionId}`, method: 'PUT', data: payload })) as ApiResponse<null>
  );
}
/** 删除岗位。 */
export async function deleteOrgPosition(positionId: number): Promise<void> {
  unwrap(
    (USE_ORG_COLLABORATION_MOCK
      ? await orgCollaborationMock.mockDeleteOrgPosition(positionId)
      : await request<ApiResponse<null>>({ url: `/api/org/positions/${positionId}`, method: 'DELETE' })) as ApiResponse<null>
  );
}
