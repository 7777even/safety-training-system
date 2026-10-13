import type {ApiResponse} from '@shared/types';
import {request} from './request';

export type TrainingTypeStatus = 0 | 1;

/** 培训类型分页查询参数，与 Training OpenAPI 保持一致。 */
export interface TrainingTypePageQuery {
  pageNum: number;
  pageSize: number;
  'query.typeCode'?: string;
  'query.typeName'?: string;
  'query.status'?: TrainingTypeStatus;
}

export interface TrainingTypePage {
  list: TrainingType[];
  total: number;
}

/** 培训类型详情；所属机构仅供后端返回后的只读展示。 */
export interface TrainingType {
  trainingTypeId: number;
  organizationId: number;
  typeCode: string;
  typeName: string;
  workType?: string;
  tag?: string;
  /** 封面文件 ID，仅供管理端编辑回填；展示走受控封面接口。 */
  coverFileId?: number;
  initialHours: number;
  reviewHours: number;
  onlineMaxRatio?: number;
  status: TrainingTypeStatus;
  description: string;
  version: number;
  createTime?: string;
}

/** 新建培训类型请求；所属机构由后端按当前用户上下文确定。 */
export interface TrainingTypeCreatePayload {
  typeCode: string;
  typeName: string;
  workType?: string;
  tag?: string;
  coverFileId?: number;
  initialHours: number;
  reviewHours: number;
  onlineMaxRatio?: number;
  status: TrainingTypeStatus;
  description: string;
}

export interface TrainingTypeUpdatePayload {
  version: number;
  typeName: string;
  workType?: string;
  tag?: string;
  coverFileId?: number;
  initialHours: number;
  reviewHours: number;
  onlineMaxRatio?: number;
  status: TrainingTypeStatus;
  description: string;
}

/** 保留后端业务码，供页面显示真实失败原因。 */
export class TrainingTypeApiError extends Error {
  constructor(public readonly code: number, message: string) {
    super(message);
    this.name = 'TrainingTypeApiError';
  }
}

function unwrap<T>(response: ApiResponse<T>): T {
  if (response.code !== 0) {
    throw new TrainingTypeApiError(response.code, response.message || '培训类型操作失败，请稍后重试。');
  }
  return response.data;
}

export const getTrainingTypePage = async (params: TrainingTypePageQuery): Promise<TrainingTypePage> =>
  unwrap(await request<ApiResponse<TrainingTypePage>>({url: '/api/training/types', method: 'GET', params}));

export const getTrainingTypeDetail = async (trainingTypeId: number): Promise<TrainingType> =>
  unwrap(await request<ApiResponse<TrainingType>>({url: `/api/training/types/${trainingTypeId}`, method: 'GET'}));

/** 按培训类型读取受控封面，调用方负责在无封面时展示占位。 */
export const getTrainingTypeCover = (trainingTypeId: number): Promise<Blob> =>
  request<Blob>({ url: `/api/training/types/${trainingTypeId}/cover`, method: 'GET', responseType: 'blob' });

export const createTrainingType = async (data: TrainingTypeCreatePayload): Promise<number> =>
  unwrap(await request<ApiResponse<number>>({url: '/api/training/types', method: 'POST', data}));

export const updateTrainingType = async (trainingTypeId: number, data: TrainingTypeUpdatePayload): Promise<void> =>
  unwrap(await request<ApiResponse<void>>({url: `/api/training/types/${trainingTypeId}`, method: 'PUT', data}));

export const deleteTrainingType = async (trainingTypeId: number): Promise<void> =>
  unwrap(await request<ApiResponse<void>>({url: `/api/training/types/${trainingTypeId}`, method: 'DELETE'}));
