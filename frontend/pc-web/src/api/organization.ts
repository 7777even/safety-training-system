import type { ApiResponse } from '@shared/types';
import { request } from './request';

/** 租户类型，与 org.openapi.json 保持一致。 */
export type OrganizationType = 'ENTERPRISE' | 'TRAINING_INSTITUTION' | 'REGULATOR';

/** 租户主数据。 */
export interface Organization {
  organizationId: number;
  orgCode: string;
  orgName: string;
  orgType: OrganizationType;
  contactUserId?: number;
  status: 0 | 1;
  description?: string;
  version: number;
  createTime?: string;
  updateTime?: string;
}

/** 租户分页查询参数。 */
export interface OrganizationPageQuery {
  pageNum: number;
  pageSize: number;
  'query.keyword'?: string;
  'query.orgType'?: OrganizationType;
  'query.status'?: 0 | 1;
}

/** 租户开户请求。 */
export interface OrganizationCreatePayload {
  orgCode: string;
  orgName: string;
  orgType: OrganizationType;
  contactUserId?: number;
  status?: 0 | 1;
  description?: string;
  adminUserCode: string;
  adminUserName: string;
  adminPassword: string;
}

/** 租户编辑请求。 */
export interface OrganizationUpdatePayload {
  orgName: string;
  orgType?: Exclude<OrganizationType, 'REGULATOR'>;
  contactUserId?: number;
  status: 0 | 1;
  description?: string;
  version: number;
}

/** 解包统一 Result 响应。 */
function unwrap<T>(response: ApiResponse<T>): T {
  if (response.code !== 0) throw new Error(response.message || '请求失败，请稍后重试。');
  return response.data;
}

/** 查询租户分页。 */
export async function getOrganizationPage(query: OrganizationPageQuery): Promise<{ list: Organization[]; total: number }> {
  return unwrap(await request<ApiResponse<{ list: Organization[]; total: number }>>({ url: '/api/org/organizations', method: 'GET', params: query }));
}

/** 查询租户详情。 */
export async function getOrganizationDetail(organizationId: number): Promise<Organization> {
  return unwrap(await request<ApiResponse<Organization>>({ url: `/api/org/organizations/${organizationId}`, method: 'GET' }));
}

/** 创建租户并初始化管理员。 */
export async function createOrganization(payload: OrganizationCreatePayload): Promise<number> {
  return unwrap(await request<ApiResponse<number>>({ url: '/api/org/organizations', method: 'POST', data: payload }));
}

/** 修改租户类型、名称、联系人、状态和说明。 */
export async function updateOrganization(organizationId: number, payload: OrganizationUpdatePayload): Promise<void> {
  unwrap(await request<ApiResponse<null>>({ url: `/api/org/organizations/${organizationId}`, method: 'PUT', data: payload }));
}
