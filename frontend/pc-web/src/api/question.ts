import type {ApiResponse} from '@shared/types';
import * as questionMock from '@/mock/question';
import {request} from './request';

/** 题库与题目接口统一使用的启停状态。 */
export type QuestionStatus = 0 | 1;
/** Contract 已定义的题目类型。 */
export type QuestionType = 'SINGLE' | 'MULTIPLE' | 'JUDGE' | 'FILL' | 'ESSAY';
/** Contract 已定义的题目难度。 */
export type QuestionDifficulty = 'EASY' | 'MEDIUM' | 'HARD';
/** Contract 已定义的判分方式。 */
export type QuestionGradingMode = 'AUTO' | 'MANUAL';

/** 可供题目创建或更新使用的选项。 */
export interface QuestionOptionPayload {
  optionKey: string;
  optionContent: string;
  isCorrect: QuestionStatus;
  sortOrder: number;
}

/** 题目版本返回的选项。 */
export interface QuestionOption extends QuestionOptionPayload {
  questionOptionId: number;
}

/** 填空题的单个标准答案。 */
export interface QuestionBlankAnswerPayload {
  blankNo: number;
  answerGroupNo: number;
  answerText: string;
  sortOrder: number;
}

/** 简答题自动判分的关键词。 */
export interface QuestionKeywordPayload {
  keyword: string;
  weight?: number;
  sortOrder: number;
}

/** 知识点树节点。 */
export interface KnowledgePoint {
  knowledgePointId: number;
  organizationId: number;
  parentId: number;
  knowledgeCode: string;
  knowledgeName: string;
  ancestors?: string;
  sortOrder: number;
  status: QuestionStatus;
  version: number;
  children?: KnowledgePoint[];
}

/** 创建知识点请求。 */
export interface KnowledgePointCreatePayload {
  parentId: number;
  knowledgeCode: string;
  knowledgeName: string;
  sortOrder: number;
  status: QuestionStatus;
}

/** 题库分类记录。 */
export interface QuestionBankCategory {
  bankCategoryId: number;
  organizationId?: number;
  categoryName: string;
  description?: string;
  sortOrder?: number;
  status?: QuestionStatus;
}

/** 创建题库分类请求。 */
export interface QuestionBankCategoryCreatePayload {
  categoryName: string;
  description?: string;
  sortOrder: number;
  status: QuestionStatus;
}

/** 更新题目请求；发布版本由服务端创建。 */
export interface QuestionUpdatePayload {
  questionType: QuestionType;
  stem: string;
  difficulty: QuestionDifficulty;
  gradingMode: QuestionGradingMode;
  version: number;
  options?: QuestionOptionPayload[];
  analysis?: string;
  standardAnswer?: string;
  versionNote?: string;
  knowledgePointIds?: number[];
  blankAnswers?: QuestionBlankAnswerPayload[];
  keywords?: QuestionKeywordPayload[];
  isUnorderedAnswerAllowed?: QuestionStatus;
  isCaseIgnored?: QuestionStatus;
  isFullHalfWidthIgnored?: QuestionStatus;
}

/** 题目素材关联项。 */
export interface QuestionAssetPayload {
  assetId: number;
  assetRole: string;
  optionKey: string;
  sortOrder: number;
}

/** 题目版本返回的素材关联。 */
export interface QuestionAsset extends QuestionAssetPayload {
  questionAssetId: number;
  questionVersionId: number;
}

/** Contract JSON 批量导入题目行。 */
export interface QuestionImportRow {
  questionCode: string;
  questionType: QuestionType;
  stem: string;
  difficulty: QuestionDifficulty;
  options?: QuestionOptionPayload[];
}

/** Contract JSON 批量导入请求。 */
export interface QuestionImportPayload {
  sourceFileId: number;
  rows: QuestionImportRow[];
}

/** 导入接口返回的批次统计。 */
export interface QuestionImportResult {
  importBatchId: number;
  importStatus: string;
  totalRows: number;
  successRows: number;
  failureRows: number;
}

/** 分页题库请求参数，与 question.openapi.json 保持一致。 */
export interface QuestionBankPageQuery {
  pageNum: number;
  pageSize: number;
  'query.bankName'?: string;
  'query.bankCategoryId'?: number;
  'query.archiveStatus'?: string;
  'query.publishedStatus'?: 'DRAFT' | 'PUBLISHED';
  'query.status'?: QuestionStatus;
}

/** Contract 分页结果。 */
export interface QuestionPage<TItem> {
  list: TItem[];
  total: number;
}

/** 题库列表项；额外字段以服务端 Contract 返回为准。 */
export interface QuestionBank {
  questionBankId: number;
  organizationId?: number;
  bankCode?: string;
  bankName: string;
  bankCategoryId?: number;
  difficulty?: QuestionDifficulty;
  questionOrderMode?: string;
  archiveStatus?: string;
  status?: QuestionStatus;
  version?: number;
  createTime?: string;
  knowledgePointIds?: number[];
}

/** 题目列表项；发布状态与版本均由服务端返回。 */
export interface Question {
  questionId: number;
  organizationId: number;
  questionBankId: number;
  questionCode: string;
  currentVersionNo?: number;
  archiveStatus?: string;
  status?: QuestionStatus;
  version?: number;
  currentVersion?: QuestionVersion;
  knowledgePointIds?: number[];
}

/** 题目当前版本内容，由 QuestionVO.currentVersion 返回。 */
export interface QuestionVersion {
  questionVersionId: number;
  questionId: number;
  versionNo: number;
  questionType: QuestionType;
  stem: string;
  difficulty: QuestionDifficulty;
  gradingMode: QuestionGradingMode;
  analysis?: string;
  standardAnswer?: string;
  publishedStatus?: string;
  publishedTime?: string;
  versionNote?: string;
  options?: QuestionOption[];
  isUnorderedAnswerAllowed?: QuestionStatus;
  isCaseIgnored?: QuestionStatus;
  isFullHalfWidthIgnored?: QuestionStatus;
  publishedBy?: number;
  blankAnswers?: QuestionBlankAnswerPayload[];
  keywords?: QuestionKeywordPayload[];
  assets?: QuestionAsset[];
}

/** 分页题目请求参数。 */
export interface QuestionPageQuery {
  pageNum: number;
  pageSize: number;
  'query.questionBankId'?: number;
  'query.questionCode'?: string;
  'query.archiveStatus'?: string;
  'query.publishedStatus'?: 'DRAFT' | 'PUBLISHED';
  'query.status'?: QuestionStatus;
}

/** 创建题库请求。 */
export interface QuestionBankCreatePayload {
  bankCode: string;
  bankName: string;
  bankCategoryId?: number;
  difficulty: QuestionDifficulty;
  questionOrderMode: string;
  status: QuestionStatus;
  knowledgePointIds?: number[];
}

/** 更新题库请求；字段与 QuestionBankUpdateRequest 保持一致，version 用于乐观锁校验。 */
export interface QuestionBankUpdatePayload {
  bankName: string;
  bankCategoryId?: number;
  difficulty: QuestionDifficulty;
  questionOrderMode: string;
  status: QuestionStatus;
  version: number;
  knowledgePointIds?: number[];
}

/** 创建题目请求。 */
export interface QuestionCreatePayload {
  questionBankId: number;
  questionCode: string;
  questionType: QuestionType;
  stem: string;
  difficulty: QuestionDifficulty;
  gradingMode: QuestionGradingMode;
  /** 仅选择题需要提交选项；字段是否存在以 OpenAPI required 为准。 */
  options?: QuestionOptionPayload[];
  analysis?: string;
  standardAnswer?: string;
  versionNote?: string;
  knowledgePointIds?: number[];
  blankAnswers?: QuestionBlankAnswerPayload[];
  keywords?: QuestionKeywordPayload[];
  isUnorderedAnswerAllowed?: QuestionStatus;
  isCaseIgnored?: QuestionStatus;
  isFullHalfWidthIgnored?: QuestionStatus;
}

/** 题目接口错误，保留后端业务码供页面统一反馈。 */
export class QuestionApiError extends Error {
  constructor(
    public readonly code: number,
    message: string
  ) {
    super(message);
    this.name = 'QuestionApiError';
  }
}

/** 解包统一 Result，避免页面重复处理业务失败。 */
function unwrap<TData>(response: ApiResponse<TData>): TData {
  if (response.code !== 0) {
    throw new QuestionApiError(response.code, response.message || '请求失败，请稍后重试。');
  }
  return response.data;
}

/** 题库 Contract Mock 开关；后端联调时设置 VITE_USE_API_MOCK=false。 */
export function isQuestionMockEnabled(): boolean {
  const runtimeOverride = (globalThis as { __QUESTION_MOCK_ENABLED__?: boolean }).__QUESTION_MOCK_ENABLED__;
  return runtimeOverride ?? import.meta.env?.VITE_USE_API_MOCK !== 'false';
}

/** Mock 开启时走本地会话数据，关闭后保持原样调用真实 Contract。 */
async function selectSource<TData>(mockAction: () => Promise<ApiResponse<TData>>, realAction: () => Promise<ApiResponse<TData>>): Promise<TData> {
  return unwrap(isQuestionMockEnabled() ? await mockAction() : await realAction());
}

/** 查询知识点树。 */
export async function getKnowledgePointTree(): Promise<KnowledgePoint[]> {
  return selectSource(
    () => questionMock.mockGetKnowledgePointTree(),
    () => request<ApiResponse<KnowledgePoint[]>>({
      url: '/api/question/knowledge-points/tree',
      method: 'GET',
    })
  );
}

/** 创建知识点。 */
export async function createKnowledgePoint(payload: KnowledgePointCreatePayload): Promise<number> {
  return selectSource(() => questionMock.mockCreateKnowledgePoint(payload), () => request<ApiResponse<number>>({
    url: '/api/question/knowledge-points',
    method: 'POST',
    data: payload
  }));
}

/** 查询题库分类列表。 */
export async function getQuestionBankCategories(): Promise<QuestionBankCategory[]> {
  return selectSource(
    () => questionMock.mockGetQuestionBankCategories(),
    () => request<ApiResponse<QuestionBankCategory[]>>({
      url: '/api/question/bank-categories',
      method: 'GET',
    })
  );
}

/** 创建题库分类。 */
export async function createQuestionBankCategory(payload: QuestionBankCategoryCreatePayload): Promise<number> {
  return selectSource(
    () => questionMock.mockCreateQuestionBankCategory(payload),
    () => request<ApiResponse<number>>({url: '/api/question/bank-categories', method: 'POST', data: payload})
  );
}

/** 分页查询题库。 */
export async function getQuestionBankPage(params: QuestionBankPageQuery): Promise<QuestionPage<QuestionBank>> {
  return selectSource(
    () => questionMock.mockGetQuestionBankPage(params),
    () => request<ApiResponse<QuestionPage<QuestionBank>>>({url: '/api/question/banks', method: 'GET', params})
  );
}

/** 分页查询题目。 */
export async function getQuestionPage(params: QuestionPageQuery): Promise<QuestionPage<Question>> {
  return selectSource(
    () => questionMock.mockGetQuestionPage(params),
    () => request<ApiResponse<QuestionPage<Question>>>({url: '/api/question/questions', method: 'GET', params})
  );
}

/** 读取题目详情与当前版本。 */
export async function getQuestionDetail(questionId: number): Promise<Question> {
  return selectSource(
    () => questionMock.mockGetQuestionDetail(questionId),
    () => request<ApiResponse<Question>>({url: `/api/question/questions/${questionId}`, method: 'GET'})
  );
}

/** 创建题库。 */
export async function createQuestionBank(payload: QuestionBankCreatePayload): Promise<number> {
  return selectSource(() => questionMock.mockCreateQuestionBank(payload), () => request<ApiResponse<number>>({
    url: '/api/question/banks',
    method: 'POST',
    data: payload
  }));
}

/** 创建题目。 */
export async function createQuestion(payload: QuestionCreatePayload): Promise<number> {
  return selectSource(() => questionMock.mockCreateQuestion(payload), () => request<ApiResponse<number>>({
    url: '/api/question/questions',
    method: 'POST',
    data: payload
  }));
}

/** 更新题库基础信息、分类与关联知识点；version 由服务端乐观锁校验。 */
export async function updateQuestionBank(questionBankId: number, payload: QuestionBankUpdatePayload): Promise<void> {
  await selectSource(
    () => questionMock.mockUpdateQuestionBank(questionBankId, payload),
    () => request<ApiResponse<null>>({url: `/api/question/banks/${questionBankId}`, method: 'PUT', data: payload})
  );
}

/** 更新题目当前编辑内容；已发布版本不由前端就地修改。 */
export async function updateQuestion(questionId: number, payload: QuestionUpdatePayload): Promise<void> {
  await selectSource(
    () => questionMock.mockUpdateQuestion(questionId, payload),
    () => request<ApiResponse<null>>({url: `/api/question/questions/${questionId}`, method: 'PUT', data: payload})
  );
}

/** 替换题干或选项的基础素材关联，不包含媒体转码。 */
export async function replaceQuestionAssets(questionId: number, payload: QuestionAssetPayload[]): Promise<void> {
  await selectSource(
    () => questionMock.mockReplaceQuestionAssets(questionId, payload),
    () => request<ApiResponse<null>>({
      url: `/api/question/questions/${questionId}/assets`,
      method: 'PUT',
      data: payload
    })
  );
}

/** 按 Contract 提交 JSON 批量导入，导入结果由后端 import_batch 维护。 */
export async function importQuestions(questionBankId: number, payload: QuestionImportPayload): Promise<QuestionImportResult> {
  return selectSource(
    () => questionMock.mockImportQuestions(questionBankId, payload),
    () => request<ApiResponse<QuestionImportResult>>({
      url: `/api/question/banks/${questionBankId}/import`,
      method: 'POST',
      data: payload
    })
  );
}

/** 发布题目当前版本；发布后的版本由服务端固化。 */
export async function publishQuestion(questionId: number): Promise<void> {
  await selectSource(
    () => questionMock.mockPublishQuestion(questionId),
    () => request<ApiResponse<null>>({url: `/api/question/questions/${questionId}/publish`, method: 'POST'})
  );
}
