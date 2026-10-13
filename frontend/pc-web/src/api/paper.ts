import type {ApiResponse} from '@shared/types';
import * as paperMock from '@/mock/paper';
import {request} from './request';

export type PaperType = 'FIXED' | 'RANDOM' | 'CUSTOM';
export type PaperStatus = 0 | 1;

export interface PaperPageQuery {
  pageNum: number;
  pageSize: number;
  'query.paperName'?: string;
  'query.paperType'?: PaperType;
  'query.archiveStatus'?: string;
  'query.status'?: PaperStatus;
  'query.hasPublishedVersion'?: boolean;
}

export interface Paper<T = PaperItem> {
  list: T[];
  total: number;
}

export interface PaperItem {
  paperId: number;
  paperCode?: string;
  paperName: string;
  paperType: PaperType;
  paperCategoryId?: number;
  coverFileId?: number;
  currentVersionNo?: number;
  archiveStatus?: string;
  status?: PaperStatus;
  version?: number;
  createTime?: string;
}

export interface PaperCreatePayload {
  paperCode: string;
  paperName: string;
  paperType: PaperType;
  status: PaperStatus;
  paperCategoryId?: number;
  coverFileId?: number;
}

export interface PaperQuestionPayload {
  questionId: number;
  questionVersionId?: number;
  score: number;
  sortOrder: number;
}

/** 已固化试卷版本中的题目快照。 */
export interface PaperVersionQuestion extends PaperQuestionPayload {
  paperQuestionId?: number;
  questionType?: string;
  sourceBankId?: number;
}

export interface PaperDrawRulePayload {
  questionBankId: number;
  questionType: string;
  difficulty?: string;
  knowledgePointId?: number;
  drawCount: number;
  scorePerQuestion: number;
  bankWeight?: number;
  sortOrder: number;
}

/** 已保存试卷版本的抽题规则回读结构。 */
export interface PaperDrawRule extends PaperDrawRulePayload {
  drawRuleId?: number;
  paperVersionId?: number;
}

export interface PaperComposePayload {
  durationSeconds: number;
  passRatio: number;
  questionOrderMode: string;
  multiSelectScoring: string;
  drawSettingMode?: string;
  versionNote?: string;
  randomSeed?: number;
  questions?: PaperQuestionPayload[];
  drawRules?: PaperDrawRulePayload[];
}

/** 试卷基础信息更新请求，version 用于乐观锁校验。 */
export interface PaperUpdatePayload {
  paperName: string;
  paperCategoryId?: number;
  coverFileId?: number;
  status: PaperStatus;
  version: number;
}

export interface PaperVersion {
  paperVersionId: number;
  paperId: number;
  versionNo: number;
  durationSeconds: number;
  totalScore: number;
  passRatio: number;
  passScore: number;
  questionCount: number;
  questionOrderMode: string;
  multiSelectScoring: string;
  drawSettingMode?: string;
  publishedStatus?: string;
  publishedBy?: number;
  publishedTime?: string;
  versionNote?: string;
  questions?: PaperVersionQuestion[];
  randomSeed?: number;
  drawRules?: PaperDrawRule[];
}

/** 试卷版本摘要用于把版本序号解析为真实 paperVersionId。 */
export interface PaperVersionSummary {
  paperVersionId: number;
  paperId: number;
  versionNo: number;
  publishedStatus: 'DRAFT' | 'PUBLISHED' | 'ARCHIVED';
  publishedBy?: number;
  publishedTime?: string;
}

/** 考试配置页展示的已发布试卷版本选项。 */
export interface PaperVersionOption extends PaperVersionSummary {
  paperName: string;
  /** 试卷分类用于新建考试时默认带入考试分类。 */
  paperCategoryId?: number;
  /** 试卷封面用于新建考试时默认带入考试封面。 */
  coverFileId?: number;
}

export interface PaperPreview {
  paperId: number;
  paperType: PaperType;
  totalScore: number;
  passScore: number;
  questionCount: number;
  questions?: PaperVersionQuestion[];
}

export class PaperApiError extends Error {
  constructor(
    public readonly code: number,
    message: string
  ) {
    super(message);
    this.name = 'PaperApiError';
  }
}

function unwrap<T>(response: ApiResponse<T>): T {
  if (response.code !== 0) throw new PaperApiError(response.code, response.message || '请求失败，请稍后重试。');
  return response.data;
}

export function isPaperMockEnabled(): boolean {
  const override = (globalThis as { __PAPER_MOCK_ENABLED__?: boolean }).__PAPER_MOCK_ENABLED__;
  return override ?? import.meta.env?.VITE_USE_API_MOCK !== 'false';
}

async function select<T>(mockAction: () => Promise<ApiResponse<T>>, realAction: () => Promise<ApiResponse<T>>): Promise<T> {
  return unwrap(isPaperMockEnabled() ? await mockAction() : await realAction());
}

export const getPaperPage = (params: PaperPageQuery): Promise<Paper> =>
  select(
    () => paperMock.mockGetPaperPage(params),
    () => request<ApiResponse<Paper>>({url: '/api/paper/papers', method: 'GET', params})
  );
export const createPaper = (data: PaperCreatePayload): Promise<number> =>
  select(
    () => paperMock.mockCreatePaper(data),
    () => request<ApiResponse<number>>({url: '/api/paper/papers', method: 'POST', data})
  );
export const getPaper = (paperId: number): Promise<PaperItem> =>
  select(
    () => paperMock.mockGetPaper(paperId),
    () => request<ApiResponse<PaperItem>>({url: `/api/paper/papers/${paperId}`, method: 'GET'})
  );
export const updatePaper = (paperId: number, data: PaperUpdatePayload): Promise<void> =>
  select(
    () => paperMock.mockUpdatePaper(paperId, data),
    () => request<ApiResponse<void>>({url: `/api/paper/papers/${paperId}`, method: 'PUT', data})
  );
export const deletePaper = (paperId: number): Promise<void> =>
  select(
    () => paperMock.mockDeletePaper(paperId),
    () => request<ApiResponse<void>>({url: `/api/paper/papers/${paperId}`, method: 'DELETE'})
  );
export const composePaper = (paperId: number, data: PaperComposePayload): Promise<PaperVersion> =>
  select(
    () => paperMock.mockComposePaper(paperId, data),
    () => request<ApiResponse<PaperVersion>>({url: `/api/paper/papers/${paperId}/compose`, method: 'POST', data})
  );
export const previewPaper = (paperId: number): Promise<PaperPreview> =>
  select(
    () => paperMock.mockPreviewPaper(paperId),
    () => request<ApiResponse<PaperPreview>>({url: `/api/paper/papers/${paperId}/preview`, method: 'GET'})
  );
export const publishPaper = (paperId: number, data: PaperComposePayload): Promise<PaperVersion> =>
  select(
    () => paperMock.mockPublishPaper(paperId, data),
    () => request<ApiResponse<PaperVersion>>({url: `/api/paper/papers/${paperId}/publish`, method: 'POST', data})
  );
export const getPaperVersions = (paperId: number): Promise<PaperVersionSummary[]> =>
  select(
    () => paperMock.mockGetPaperVersions(paperId),
    () => request<ApiResponse<PaperVersionSummary[]>>({url: `/api/paper/papers/${paperId}/versions`, method: 'GET'})
  );
export const getPaperVersion = (paperVersionId: number): Promise<PaperVersion> =>
  select(
    () => paperMock.mockGetPaperVersion(paperVersionId),
    () => request<ApiResponse<PaperVersion>>({url: `/api/paper/papers/versions/${paperVersionId}`, method: 'GET'})
  );
