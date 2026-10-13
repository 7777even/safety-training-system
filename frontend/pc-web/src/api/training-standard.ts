import type {ApiResponse} from '@shared/types';
import {request} from './request';

export type TrainingStandardStatus = 0 | 1;
export type TrainingStandardPublishStatus = 'DRAFT' | 'PUBLISHED' | 'ARCHIVED';
export type TrainingStandardRuleType = 'TOTAL_HOURS' | 'ONLINE_RATIO' | 'COURSE' | 'EXAM' | 'OFFLINE';

/** 培训标准分页查询，字段名保持 PageRequest 的 query 前缀。 */
export interface TrainingStandardPageQuery {
  pageNum: number;
  pageSize: number;
  'query.standardCode'?: string;
  'query.standardName'?: string;
  'query.trainingTypeId'?: number;
  'query.publishStatus'?: TrainingStandardPublishStatus;
  'query.status'?: TrainingStandardStatus;
}

export interface TrainingStandardPage {
  list: TrainingStandard[];
  total: number;
}

export interface TrainingStandardRulePayload {
  ruleType: TrainingStandardRuleType;
  requiredValue?: number;
  unit?: string;
  resourceType?: string;
  resourceId?: number;
  sortOrder: number;
  status: TrainingStandardStatus;
  description: string;
}

export interface TrainingStandardRule extends TrainingStandardRulePayload {
  standardRuleId: number;
  trainingStandardId: number;
}

/** TrainingStandardVO；详情包含规则，分页记录允许规则为空。 */
export interface TrainingStandard {
  trainingStandardId: number;
  organizationId: number;
  standardCode: string;
  standardName: string;
  trainingTypeId: number;
  standardVersion: string;
  validFrom?: string;
  validTo?: string;
  publishStatus: TrainingStandardPublishStatus;
  status: TrainingStandardStatus;
  version: number;
  createTime?: string;
  rules?: TrainingStandardRule[];
}

export interface TrainingStandardCreatePayload {
  standardCode: string;
  standardName: string;
  trainingTypeId: number;
  standardVersion: string;
  validFrom?: string;
  validTo?: string;
  status: TrainingStandardStatus;
}

export interface TrainingStandardUpdatePayload {
  version: number;
  standardName: string;
  trainingTypeId: number;
  standardVersion: string;
  validFrom?: string;
  validTo?: string;
  status: TrainingStandardStatus;
}

export interface TrainingStandardRulesReplacePayload {
  version: number;
  rules?: TrainingStandardRulePayload[];
}

export interface TrainingStandardPublishPayload {
  version: number;
}

export class TrainingStandardApiError extends Error {
  constructor(
    public readonly code: number,
    message: string
  ) {
    super(message);
    this.name = 'TrainingStandardApiError';
  }
}

function unwrap<T>(response: ApiResponse<T>): T {
  if (response.code !== 0) {
    throw new TrainingStandardApiError(response.code, response.message || '培训标准操作失败，请稍后重试。');
  }
  return response.data;
}

export const getTrainingStandardPage = async (params: TrainingStandardPageQuery): Promise<TrainingStandardPage> =>
  unwrap(await request<ApiResponse<TrainingStandardPage>>({url: '/api/training/standards', method: 'GET', params}));

export const getTrainingStandardDetail = async (trainingStandardId: number): Promise<TrainingStandard> =>
  unwrap(await request<ApiResponse<TrainingStandard>>({
    url: `/api/training/standards/${trainingStandardId}`,
    method: 'GET'
  }));

export const createTrainingStandard = async (data: TrainingStandardCreatePayload): Promise<number> =>
  unwrap(await request<ApiResponse<number>>({url: '/api/training/standards', method: 'POST', data}));

export const updateTrainingStandard = async (
  trainingStandardId: number,
  data: TrainingStandardUpdatePayload
): Promise<void> =>
  unwrap(await request<ApiResponse<void>>({url: `/api/training/standards/${trainingStandardId}`, method: 'PUT', data}));

export const deleteTrainingStandard = async (trainingStandardId: number): Promise<void> =>
  unwrap(await request<ApiResponse<void>>({url: `/api/training/standards/${trainingStandardId}`, method: 'DELETE'}));

export const replaceTrainingStandardRules = async (
  trainingStandardId: number,
  data: TrainingStandardRulesReplacePayload
): Promise<void> =>
  unwrap(await request<ApiResponse<void>>({
    url: `/api/training/standards/${trainingStandardId}/rules`,
    method: 'PUT',
    data
  }));

export const publishTrainingStandard = async (
  trainingStandardId: number,
  versionOrPayload: number | TrainingStandardPublishPayload
): Promise<void> => {
  const data = typeof versionOrPayload === 'number' ? {version: versionOrPayload} : versionOrPayload;
  return unwrap(await request<ApiResponse<void>>({
    url: `/api/training/standards/${trainingStandardId}/publish`,
    method: 'POST',
    data
  }));
};
