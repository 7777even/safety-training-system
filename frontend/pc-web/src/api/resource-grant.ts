import type { ApiResponse } from '@shared/types';
import * as orgCollaborationMock from '@/mock/org-collaboration';
import { request } from './request';
/** 资源授权记录。 */
export interface ResourceGrant {
  orgGrantId: number;
  ownerOrganizationId: number;
  resourceType: string;
  resourceId: number;
  granteeOrganizationId: number;
  grantLevel: string;
  validFrom?: string;
  validTo?: string;
  status: 0 | 1;
  version: number;
  createTime?: string;
}
export type GrantLevel = 'VIEW' | 'USE' | 'MANAGE';
export interface ResourceGrantQuery {
  pageNum: number;
  pageSize: number;
  'query.ownerOrganizationId'?: number;
  'query.resourceType'?: string;
  'query.resourceId'?: number;
  'query.granteeOrganizationId'?: number;
  'query.grantLevel'?: GrantLevel;
  'query.status'?: 0 | 1;
}
export interface ResourceGrantCheckRequest {
  ownerOrganizationId: number;
  resourceType: string;
  resourceId: number;
  granteeOrganizationId: number;
  requiredLevel: GrantLevel;
}
export interface ResourceGrantCheckResult extends ResourceGrantCheckRequest {
  allowed: boolean;
}
export type ResourceGrantCreateRequest = Omit<ResourceGrant, 'orgGrantId' | 'version' | 'createTime'>;
export type ResourceGrantUpdateRequest = Pick<ResourceGrant, 'grantLevel' | 'validFrom' | 'validTo' | 'status' | 'version'>;
/** 默认使用 Contract Mock；联调通过后按接口删除对应 Mock 分支。 */
const USE_ORG_COLLABORATION_MOCK = import.meta.env.VITE_USE_API_MOCK !== 'false';
/** 解包统一 Result 响应。 */
function unwrap<T>(response: ApiResponse<T>): T {
  if (response.code !== 0) throw new Error(response.message || '请求失败，请稍后重试。');
  return response.data;
}
/** 分页查询资源授权。 */
export async function getResourceGrantPage(params: ResourceGrantQuery): Promise<{ list: ResourceGrant[]; total: number }> {
  if (!USE_ORG_COLLABORATION_MOCK)
    return unwrap(
      (await request<ApiResponse<{ list: ResourceGrant[]; total: number }>>({
        url: '/api/common/resource-grants',
        method: 'GET',
        params
      })) as ApiResponse<{ list: ResourceGrant[]; total: number }>
    );

  // TODO replace real api: the temporary mock has no query/detail/check methods, so retain the OpenAPI query semantics here.
  const all = unwrap(
    (await orgCollaborationMock.mockGetResourceGrantPage({ pageNum: 1, pageSize: Number.MAX_SAFE_INTEGER })) as ApiResponse<{
      list: ResourceGrant[];
      total: number;
    }>
  ).list;
  const filtered = all.filter(
    (item) =>
      (params['query.ownerOrganizationId'] === undefined || item.ownerOrganizationId === params['query.ownerOrganizationId']) &&
      (!params['query.resourceType'] || item.resourceType === params['query.resourceType']) &&
      (params['query.resourceId'] === undefined || item.resourceId === params['query.resourceId']) &&
      (params['query.granteeOrganizationId'] === undefined || item.granteeOrganizationId === params['query.granteeOrganizationId']) &&
      (!params['query.grantLevel'] || item.grantLevel === params['query.grantLevel']) &&
      (params['query.status'] === undefined || item.status === params['query.status'])
  );
  const start = (params.pageNum - 1) * params.pageSize;
  return { list: filtered.slice(start, start + params.pageSize), total: filtered.length };
}
/** 创建资源授权。 */
export async function createResourceGrant(data: ResourceGrantCreateRequest): Promise<number> {
  return unwrap(
    (USE_ORG_COLLABORATION_MOCK
      ? await orgCollaborationMock.mockCreateResourceGrant(data)
      : await request<ApiResponse<number>>({ url: '/api/common/resource-grants', method: 'POST', data })) as ApiResponse<number>
  );
}
/** 更新资源授权。 */
export async function updateResourceGrant(
  id: number,
  data: ResourceGrantUpdateRequest
): Promise<void> {
  unwrap(
    (USE_ORG_COLLABORATION_MOCK
      ? await orgCollaborationMock.mockUpdateResourceGrant(id, data)
      : await request<ApiResponse<null>>({ url: `/api/common/resource-grants/${id}`, method: 'PUT', data })) as ApiResponse<null>
  );
}
/** 查询资源授权详情。 */
export async function getResourceGrantDetail(orgGrantId: number): Promise<ResourceGrant> {
  if (!USE_ORG_COLLABORATION_MOCK)
    return unwrap(
      (await request<ApiResponse<ResourceGrant>>({ url: `/api/common/resource-grants/${orgGrantId}`, method: 'GET' })) as ApiResponse<ResourceGrant>
    );
  const result = await getResourceGrantPage({ pageNum: 1, pageSize: Number.MAX_SAFE_INTEGER });
  const item = result.list.find((value) => value.orgGrantId === orgGrantId);
  if (!item) throw new Error('资源授权不存在');
  return item;
}
/** 校验被授权机构是否具备资源访问级别。 */
export async function checkResourceGrant(params: ResourceGrantCheckRequest): Promise<ResourceGrantCheckResult> {
  if (!USE_ORG_COLLABORATION_MOCK)
    return unwrap(
      (await request<ApiResponse<ResourceGrantCheckResult>>({ url: '/api/common/resource-grants/check', method: 'GET', params })) as ApiResponse<ResourceGrantCheckResult>
    );
  const result = await getResourceGrantPage({
    pageNum: 1,
    pageSize: Number.MAX_SAFE_INTEGER,
    'query.ownerOrganizationId': params.ownerOrganizationId,
    'query.resourceType': params.resourceType,
    'query.resourceId': params.resourceId,
    'query.granteeOrganizationId': params.granteeOrganizationId,
    'query.status': 1
  });
  const levelRank: Record<GrantLevel, number> = { VIEW: 1, USE: 2, MANAGE: 3 };
  const now = new Date();
  const allowed = result.list.some(
    (item) =>
      levelRank[item.grantLevel as GrantLevel] >= levelRank[params.requiredLevel] &&
      (!item.validFrom || new Date(item.validFrom) <= now) &&
      (!item.validTo || new Date(item.validTo) >= now)
  );
  return { ...params, allowed };
}
/** 删除资源授权。 */
export async function deleteResourceGrant(id: number): Promise<void> {
  unwrap(
    (USE_ORG_COLLABORATION_MOCK
      ? await orgCollaborationMock.mockDeleteResourceGrant(id)
      : await request<ApiResponse<null>>({ url: `/api/common/resource-grants/${id}`, method: 'DELETE' })) as ApiResponse<null>
  );
}
