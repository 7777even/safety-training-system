import type { ApiResponse } from '@shared/types';
import type { PersonSelectionRequest } from './person-selection';
import type { UserImportResult } from './system-user';
import * as examMock from '@/mock/exam';
import { request } from './request';

/** 考试发布状态，与 Exam OpenAPI 的查询参数保持一致。 */
export type ExamPublishStatus = 'DRAFT' | 'PUBLISHED';
/** 考试的启用状态。 */
export type ExamStatus = 0 | 1;

/** 考试策略配置，字段与 ExamPolicyRequest 一一对应。 */
export interface ExamPolicyRequest {
  isAnticheatEnabled?: ExamStatus;
  isWatermarkEnabled?: ExamStatus;
  isPcAllowed?: ExamStatus;
  isAppAllowed?: ExamStatus;
  isH5Allowed?: ExamStatus;
  switchLimit?: number;
  isSplitScreenForceSubmit?: ExamStatus;
  isCopyRefreshDevtoolDisabled?: ExamStatus;
  inactivitySeconds?: number;
  inactivityCountdownSeconds?: number;
  minimumAnswerSeconds?: number;
  isRetakeEnabled?: ExamStatus;
  retakeScope?: string;
  retakeStartTime?: string;
  retakeEndTime?: string;
  retakeMaxCount?: number;
  retakeScoreMode?: string;
  isRetakeRecordVisible?: ExamStatus;
  passPoint?: number;
  failPoint?: number;
  absentPoint?: number;
  lateForbidSeconds?: number;
  answerDetailMode?: string;
  isAnalysisVisible?: ExamStatus;
  isScoreVisible?: ExamStatus;
  resultNotifyAudienceId?: number;
  isRankingEnabled?: ExamStatus;
}

/** 考试列表分页查询参数。 */
export interface ExamPageQuery {
  pageNum: number;
  pageSize: number;
  'query.examType'?: string;
  'query.publishStatus'?: ExamPublishStatus;
  'query.examName'?: string;
  /** 按培训计划内容过滤；为空时查询独立与培训生成的全部考试。 */
  'query.planContentId'?: number;
  'query.status'?: ExamStatus;
}

/** Contract 分页结果。 */
export interface ExamPage<T = ExamItem> {
  list: T[];
  total: number;
}

/** 题库练习抽题规则；结构与培训侧 drawRules 一致，仅 examType=BANK_PRACTICE 使用。 */
export interface ExamDrawRulePayload {
  questionType: string;
  difficulty?: string;
  knowledgePointId?: number;
  drawCount: number;
  scorePerQuestion?: number;
  sortOrder?: number;
}

/** ExamVO：列表和详情共用的完整考试定义。 */
export interface ExamItem {
  examId: number;
  organizationId: number;
  examCode: string;
  examName: string;
  examType: string;
  examCategoryId?: number;
  coverFileId?: number;
  paperVersionId?: number;
  /** 试卷版本所属的试卷名称；未绑定或资源已不存在时为空。 */
  paperName?: string | null;
  /** 试卷版本号；未绑定或资源已不存在时为空。 */
  paperVersionNo?: number | null;
  questionBankId?: number;
  /** 题库名称；未绑定或资源已不存在时为空。 */
  questionBankName?: string | null;
  /** 题库练习抽题规则；仅考试类型为题库练习时返回。 */
  drawRules?: ExamDrawRulePayload[];
  /** 来源培训计划内容 ID；为空表示独立考试，非空表示培训生成。 */
  planContentId?: number | null;
  responsibleUserId?: number;
  openStartTime?: string;
  openEndTime?: string;
  durationSeconds: number;
  passRatio: number;
  passScore: number;
  credit: number;
  questionOrderMode: string;
  multiSelectScoring: string;
  isNoticeEnabled?: ExamStatus;
  noticeContent?: string;
  publishStatus: ExamPublishStatus;
  publishedTime?: string;
  status: ExamStatus;
  version: number;
  createTime?: string;
  policy?: ExamPolicyRequest;
}

/** 试卷绑定时可由后端带出的四项本场规则；未绑定试卷时必须由管理端填写。 */
export interface ExamRulePayload {
  durationSeconds?: number;
  passRatio?: number;
  passScore?: number;
  questionOrderMode?: string;
  multiSelectScoring?: string;
}

/** 创建考试请求；响应中的生效规则不等同于创建时必须提交的规则。 */
export interface ExamCreatePayload extends ExamRulePayload {
  examCode: string;
  examName: string;
  examType: string;
  paperVersionId?: number | null;
  examCategoryId?: number;
  coverFileId?: number;
  questionBankId?: number;
  /** 题库练习抽题规则；其他考试类型忽略该字段。 */
  drawRules?: ExamDrawRulePayload[];
  responsibleUserId?: number;
  openStartTime?: string;
  openEndTime?: string;
  credit: number;
  isNoticeEnabled?: ExamStatus;
  noticeContent?: string;
  status: ExamStatus;
  policy?: ExamPolicyRequest;
}

/** 草稿考试更新请求；paperVersionId 传 null 表示解绑，省略表示维持当前绑定。 */
export interface ExamUpdatePayload extends Omit<ExamCreatePayload, 'examCode' | 'examType'> {
  version: number;
}

/** 按当前 OpenAPI 的统一人员选择条件绑定考生。 */
export interface BindExamSelectionPayload {
  selection: PersonSelectionRequest;
}

/** 批阅人替换请求。 */
export interface ExamReviewerPayload {
  reviewerUserIds?: number[];
  /** 与固定名单互斥，保存时由服务端展开并冻结。 */
  selection?: PersonSelectionRequest;
  assignmentSource?: 'USER' | 'DEPT' | 'POSITION' | 'GROUP' | 'ALL';
}

/** 考生列表项。 */
export interface ExamCandidate {
  examCandidateId: number;
  examId: number;
  userId: number;
  learnerName?: string;
  learnerCode?: string;
  learnerOrganizationId?: number;
  learnerOrganizationName?: string;
  deptId?: number;
  deptName?: string;
  assessmentExecutionId: number;
  sourceType: string;
  candidateStatus: string;
  attemptCount: number;
  bestScore?: number;
  finalScore?: number;
  finalAttemptId?: number;
  resultPublishedTime?: string;
  /** 课程考核自动生效时间；独立考试固定为空。 */
  resultEffectiveTime?: string | null;
  version?: number;
}

/** 考试人员分页筛选参数。 */
export interface ExamCandidatePageQuery {
  pageNum: number;
  pageSize: number;
  'query.learnerKeyword'?: string;
  'query.candidateStatus'?: string;
}

/** 考试批阅人展示结构。 */
export interface ExamReviewer {
  examReviewerId: number;
  assessmentExecutionId: number;
  examId: number;
  reviewerUserId: number;
  reviewerName?: string;
  reviewerCode?: string;
  reviewerOrganizationId: number;
  reviewerOrganizationName?: string;
  assignmentSource: 'ROLE' | 'TAG' | 'POSITION' | 'USER';
}

/** 引用数据的分页上限，不与列表默认分页混用。 */
export const EXAM_REFERENCE_PAGE_SIZE = 100;

/** 考试接口业务错误，保留后端业务码供页面反馈。 */
export class ExamApiError extends Error {
  constructor(
    public readonly code: number,
    message: string
  ) {
    super(message);
    this.name = 'ExamApiError';
  }
}

/** 统一解包 Result，避免页面重复处理业务失败。 */
function unwrap<TData>(response: ApiResponse<TData>): TData {
  if (response.code !== 0) throw new ExamApiError(response.code, response.message || '请求失败，请稍后重试。');
  return response.data;
}

/** Contract Mock 开关；后端联调时设置 VITE_USE_API_MOCK=false。 */
export function isExamMockEnabled(): boolean {
  const override = (globalThis as { __EXAM_MOCK_ENABLED__?: boolean }).__EXAM_MOCK_ENABLED__;
  return override ?? import.meta.env?.VITE_USE_API_MOCK !== 'false';
}

/** Mock 与真实请求共用入参和 Result 解包逻辑。 */
async function selectSource<T>(mockAction: () => Promise<ApiResponse<T>>, realAction: () => Promise<ApiResponse<T>>): Promise<T> {
  return unwrap(isExamMockEnabled() ? await mockAction() : await realAction());
}

/** 分页查询考试。 */
export const getExamPage = (params: ExamPageQuery): Promise<ExamPage> =>
  selectSource(
    () => examMock.mockGetExamPage(params),
    () => request<ApiResponse<ExamPage>>({ url: '/api/exam/exams', method: 'GET', params })
  );
/** 读取完整考试详情。 */
export const getExam = (examId: number): Promise<ExamItem> =>
  selectSource(
    () => examMock.mockGetExam(examId),
    () => request<ApiResponse<ExamItem>>({ url: `/api/exam/exams/${examId}`, method: 'GET' })
  );
/** 创建考试草稿。 */
export const createExam = (data: ExamCreatePayload): Promise<number> =>
  selectSource(
    () => examMock.mockCreateExam(data),
    () => request<ApiResponse<number>>({ url: '/api/exam/exams', method: 'POST', data })
  );
/** 更新草稿考试；试卷版本可更换或解绑，未提交规则字段遵循 OpenAPI 的保留/重填语义。 */
export const updateExam = (examId: number, data: ExamUpdatePayload): Promise<void> =>
  selectSource(
    () => examMock.mockUpdateExam(examId, data),
    () => request<ApiResponse<void>>({ url: `/api/exam/exams/${examId}`, method: 'PUT', data })
  );
/** 删除考试草稿。 */
export const deleteExam = (examId: number): Promise<void> =>
  selectSource(
    () => examMock.mockDeleteExam(examId),
    () => request<ApiResponse<void>>({ url: `/api/exam/exams/${examId}`, method: 'DELETE' })
  );
/** 按当前人员选择 Contract 绑定考生。 */
export const bindExamSelection = (examId: number, data: BindExamSelectionPayload): Promise<void> =>
  selectSource(
    () => examMock.mockBindExamSelection(examId, data),
    () => request<ApiResponse<void>>({ url: `/api/exam/exams/${examId}/candidates/bind-selection`, method: 'POST', data })
  );
/** 查询服务端已绑定考生。 */
export const getExamCandidates = (examId: number, params: ExamCandidatePageQuery = { pageNum: 1, pageSize: 20 }): Promise<ExamPage<ExamCandidate>> =>
  selectSource(
    () => examMock.mockGetExamCandidates(examId, params),
    () => request<ApiResponse<ExamPage<ExamCandidate>>>({ url: `/api/exam/exams/${examId}/candidates`, method: 'GET', params })
  );
/** 从 CSV 批量导入考生；返回逐行导入结果。 */
export const importExamCandidates = async (examId: number, file: File): Promise<UserImportResult> => {
  const formData = new FormData();
  formData.append('file', file);
  const res = await request<ApiResponse<UserImportResult>>({
    url: `/api/exam/exams/${examId}/candidates/import`,
    method: 'POST',
    data: formData,
    headers: { 'Content-Type': 'multipart/form-data' }
  });
  return res.data as UserImportResult;
};
/** 导出考生名单为 CSV（Blob 下载）。 */
export const exportExamCandidates = async (examId: number): Promise<Blob> =>
  request<Blob>({
    url: `/api/exam/exams/${examId}/candidates/export`,
    method: 'GET',
    responseType: 'blob'
  });
/** 替换考试批阅人。 */
export const replaceExamReviewers = (examId: number, data: ExamReviewerPayload): Promise<void> =>
  selectSource(
    () => examMock.mockReplaceExamReviewers(examId, data),
    () => request<ApiResponse<void>>({ url: `/api/exam/exams/${examId}/reviewers`, method: 'PUT', data })
  );
/** 查询当前已指派的考试批阅人，用于人员配置回显。 */
export const getExamReviewers = (examId: number): Promise<ExamReviewer[]> =>
  selectSource(
    () => examMock.mockGetExamReviewers(examId),
    () => request<ApiResponse<ExamReviewer[]>>({ url: `/api/exam/exams/${examId}/reviewers`, method: 'GET' })
  );
/** 发布考试；服务端校验试卷版本与状态。 */
export const publishExam = (examId: number): Promise<void> =>
  selectSource(
    () => examMock.mockPublishExam(examId),
    () => request<ApiResponse<void>>({ url: `/api/exam/exams/${examId}/publish`, method: 'POST' })
  );

/** 单条线下成绩录入项。 */
export interface OfflineScoreRecordPayload {
  examCandidateId: number;
  finalScore: number;
  remark?: string;
}

/** 线下成绩批量录入请求。 */
export interface OfflineScoresPayload {
  scores: OfflineScoreRecordPayload[];
}

/** 线下成绩录入结果。 */
export interface OfflineScoreRecordResult {
  examId: number;
  recordedCount: number;
  skippedCount: number;
}

/** 录入线下考试成绩；重复录入同一考生会覆盖既有成绩。 */
export const recordOfflineScores = (examId: number, data: OfflineScoresPayload): Promise<OfflineScoreRecordResult> =>
  selectSource(
    () => examMock.mockRecordOfflineScores(examId, data),
    () => request<ApiResponse<OfflineScoreRecordResult>>({ url: `/api/exam/exams/${examId}/offline-scores`, method: 'POST', data })
  );
