import type { ApiResponse } from '@shared/types';
import { request } from './request';

/** 培训可引用资源类型，与 Training OpenAPI 枚举保持一致。 */
export type TrainingReferenceResourceType = 'COURSE' | 'EXAM' | 'QUESTION_BANK';

/** 当前机构可引用资源分页查询参数。 */
export interface TrainingReferenceResourcePageQuery {
  /** 页码，从 1 开始。 */
  pageNum: number;
  /** 每页条数。 */
  pageSize: number;
  /** 资源类型。 */
  'query.resourceType': TrainingReferenceResourceType;
  /** 资源编码或名称关键字。 */
  'query.keyword'?: string;
}

/** 当前机构可引用资源。 */
export interface TrainingReferenceResource {
  /** 资源类型。 */
  resourceType: TrainingReferenceResourceType;
  /** 业务资源 ID。 */
  resourceId: number;
  /** 可执行版本 ID；题库按契约为空。 */
  versionId?: number | null;
  /** 资源编码。 */
  resourceCode: string;
  /** 资源名称。 */
  resourceName: string;
  /** 当前机构 ID。 */
  organizationId: number;
  /** 当前机构名称。 */
  organizationName: string;
  /** 发布或归档状态。 */
  publishStatus: string;
  /** 启用状态，当前接口只返回 1。 */
  status: 1;
  /** 是否可执行，当前接口只返回 true。 */
  executable: true;
}

/** 当前机构可引用资源分页结果。 */
export interface TrainingReferenceResourcePage {
  /** 当前页资源。 */
  list: TrainingReferenceResource[];
  /** 符合条件的记录总数。 */
  total: number;
}

/** 当前机构可引用资源接口业务错误。 */
export class TrainingReferenceApiError extends Error {
  constructor(
    public readonly code: number,
    message: string
  ) {
    super(message);
    this.name = 'TrainingReferenceApiError';
  }
}

/** 解包统一响应并保留后端业务码。 */
function unwrap<TData>(response: ApiResponse<TData>): TData {
  if (response.code !== 0) {
    throw new TrainingReferenceApiError(response.code, response.message || '查询可引用资源失败，请稍后重试。');
  }
  return response.data;
}

/** 分页查询登录用户当前机构下已启用且可执行的资源。 */
export async function getTrainingReferenceResourcePage(
  params: TrainingReferenceResourcePageQuery
): Promise<TrainingReferenceResourcePage> {
  return unwrap(
    await request<ApiResponse<TrainingReferenceResourcePage>>({
      url: '/api/training/reference-resources',
      method: 'GET',
      params
    })
  );
}
