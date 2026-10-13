import type { ApiResponse } from '@shared/types';
import * as orgCollaborationMock from '@/mock/org-collaboration';
import { request } from './request';

export type AudienceKind = 'TEMPLATE' | 'BOUND';
export type AudiencePurpose = 'VISIBILITY' | 'PARTICIPANT' | 'REVIEWER' | 'NOTIFICATION';
export type AudienceResolveMode = 'DYNAMIC' | 'FROZEN';
export type AudienceSubjectType = 'ALL' | 'DEPT' | 'TAG' | 'POSITION' | 'USER' | 'AUDIENCE';

export interface AudienceRule {
  audienceRuleId?: number;
  decision: 'INCLUDE' | 'EXCLUDE';
  subjectType: AudienceSubjectType;
  subjectId: number;
}

export interface Audience {
  audienceId: number;
  organizationId: number;
  audienceName: string;
  audienceKind: AudienceKind;
  businessType?: string | null;
  businessId?: number | null;
  purpose: AudiencePurpose;
  resolveMode: AudienceResolveMode;
  isInternalLinkAllowed: 0 | 1;
  isExternalLinkAllowed: 0 | 1;
  status: 0 | 1;
  version: number;
  createTime?: string;
  updateTime?: string | null;
  rules?: AudienceRule[] | null;
}

export interface AudienceCreateRequest {
  organizationId: number;
  audienceName: string;
  audienceKind: AudienceKind;
  businessType?: string;
  businessId?: number;
  purpose: AudiencePurpose;
  resolveMode: AudienceResolveMode;
  isInternalLinkAllowed?: 0 | 1;
  isExternalLinkAllowed?: 0 | 1;
  status: 0 | 1;
  rules?: AudienceRule[];
}

export interface AudienceUpdateRequest {
  version: number;
  audienceName: string;
  isInternalLinkAllowed?: 0 | 1;
  isExternalLinkAllowed?: 0 | 1;
  status: 0 | 1;
}

export interface AudienceMember {
  audienceMemberId: number;
  audienceId: number;
  userId: number;
  userCode?: string | null;
  userName?: string | null;
  finalDecision: 'INCLUDE' | 'EXCLUDE';
  resolvedTime: string;
}

const USE_ORG_COLLABORATION_MOCK = import.meta.env.VITE_USE_API_MOCK !== 'false';
function unwrap<T>(response: ApiResponse<T>): T {
  if (response.code !== 0) throw new Error(response.message || '请求失败，请稍后重试。');
  return response.data;
}

export async function getAudiencePage(params: Record<string, unknown>): Promise<{ list: Audience[]; total: number }> {
  return unwrap((USE_ORG_COLLABORATION_MOCK
    ? await orgCollaborationMock.mockGetAudiencePage(params)
    : await request<ApiResponse<{ list: Audience[]; total: number }>>({ url: '/api/common/audiences', method: 'GET', params })) as ApiResponse<{ list: Audience[]; total: number }>);
}
export async function getAudienceDetail(audienceId: number): Promise<Audience> {
  return unwrap((USE_ORG_COLLABORATION_MOCK
    ? await orgCollaborationMock.mockGetAudienceDetail(audienceId)
    : await request<ApiResponse<Audience>>({ url: `/api/common/audiences/${audienceId}`, method: 'GET' })) as ApiResponse<Audience>);
}
export async function createAudience(data: AudienceCreateRequest): Promise<number> {
  return unwrap((USE_ORG_COLLABORATION_MOCK
    ? await orgCollaborationMock.mockCreateAudience(data)
    : await request<ApiResponse<number>>({ url: '/api/common/audiences', method: 'POST', data })) as ApiResponse<number>);
}
export async function updateAudience(audienceId: number, data: AudienceUpdateRequest): Promise<void> {
  unwrap((USE_ORG_COLLABORATION_MOCK
    ? await orgCollaborationMock.mockUpdateAudience(audienceId, data)
    : await request<ApiResponse<null>>({ url: `/api/common/audiences/${audienceId}`, method: 'PUT', data })) as ApiResponse<null>);
}
export async function deleteAudience(audienceId: number): Promise<void> {
  unwrap((USE_ORG_COLLABORATION_MOCK
    ? await orgCollaborationMock.mockDeleteAudience(audienceId)
    : await request<ApiResponse<null>>({ url: `/api/common/audiences/${audienceId}`, method: 'DELETE' })) as ApiResponse<null>);
}
export async function replaceAudienceRules(audienceId: number, rules: AudienceRule[]): Promise<void> {
  unwrap((USE_ORG_COLLABORATION_MOCK
    ? await orgCollaborationMock.mockReplaceAudienceRules(audienceId, rules)
    : await request<ApiResponse<null>>({ url: `/api/common/audiences/${audienceId}/rules`, method: 'PUT', data: { rules } })) as ApiResponse<null>);
}
export async function previewAudience(audienceId: number): Promise<{ audienceId: number; resolveMode: AudienceResolveMode; matchedCount: number; userIds: number[] }> {
  return unwrap((USE_ORG_COLLABORATION_MOCK
    ? await orgCollaborationMock.mockPreviewAudience(audienceId)
    : await request<ApiResponse<{ audienceId: number; resolveMode: AudienceResolveMode; matchedCount: number; userIds: number[] }>>({ url: `/api/common/audiences/${audienceId}/preview`, method: 'POST' })) as ApiResponse<{ audienceId: number; resolveMode: AudienceResolveMode; matchedCount: number; userIds: number[] }>);
}
export async function freezeAudience(audienceId: number): Promise<{ memberCount: number }> {
  return unwrap((USE_ORG_COLLABORATION_MOCK
    ? await orgCollaborationMock.mockFreezeAudience(audienceId)
    : await request<ApiResponse<{ memberCount: number }>>({ url: `/api/common/audiences/${audienceId}/freeze`, method: 'POST' })) as ApiResponse<{ memberCount: number }>);
}
export async function getAudienceMembers(audienceId: number, params: Record<string, unknown>): Promise<{ list: AudienceMember[]; total: number }> {
  return unwrap((USE_ORG_COLLABORATION_MOCK
    ? await orgCollaborationMock.mockGetAudienceMembers(audienceId, params)
    : await request<ApiResponse<{ list: AudienceMember[]; total: number }>>({ url: `/api/common/audiences/${audienceId}/members`, method: 'GET', params })) as ApiResponse<{ list: AudienceMember[]; total: number }>);
}
export async function containsAudienceUser(audienceId: number, userId: number): Promise<{ audienceId: number; userId: number; contained: boolean }> {
  return unwrap((USE_ORG_COLLABORATION_MOCK
    ? await orgCollaborationMock.mockContainsAudienceUser(audienceId, userId)
    : await request<ApiResponse<{ audienceId: number; userId: number; contained: boolean }>>({ url: `/api/common/audiences/${audienceId}/contains`, method: 'GET', params: { userId } })) as ApiResponse<{ audienceId: number; userId: number; contained: boolean }>);
}
