import type { ApiResponse } from '@shared/types';
import type { PersonSelectionRequest } from './person-selection';
import type { UserImportResult } from './system-user';
import { request } from './request';

export type TrainingNature = 'INITIAL' | 'REVIEW' | 'UNSPECIFIED';
export type TrainingPlanPublishStatus = 'DRAFT' | 'PUBLISHED' | 'RUNNING' | 'ENDED' | 'CANCELLED' | 'ARCHIVED';

/** 培训计划分页查询参数；机构由后端根据登录上下文识别。 */
export interface TrainingPlanPageQuery {
  pageNum: number;
  pageSize: number;
  'query.planCode'?: string;
  'query.planName'?: string;
  'query.trainingTypeId'?: number;
  'query.trainingNature'?: TrainingNature;
  'query.publishStatus'?: TrainingPlanPublishStatus;
  'query.status'?: 0 | 1;
}

export interface TrainingPlanPage<TPlan = TrainingPlan> {
  list: TPlan[];
  total: number;
}

/** 计划摘要与详情；发布后的适用最低学时为后端冻结值。 */
export interface TrainingPlan {
  trainingPlanId: number;
  organizationId: number;
  planCode: string;
  planName: string;
  trainingTypeId: number;
  trainingNature: TrainingNature;
  applicableRequiredHours?: number;
  trainingStandardId?: number;
  sourceTemplateId?: number;
  managerUserId?: number;
  startTime: string;
  endTime: string;
  location?: string;
  description?: string;
  publishStatus: TrainingPlanPublishStatus;
  status: 0 | 1;
  version: number;
  /** 计划创建时间，仅用于管理端列表展示。 */
  createTime?: string;
  stages?: TrainingPlanStage[];
  /** 计划详情返回的当前有效评价表单版本绑定摘要。 */
  evaluationForms?: TrainingPlanEvaluationForm[];
}

export interface TrainingPlanCreatePayload {
  planCode: string;
  planName: string;
  trainingTypeId: number;
  trainingNature: Exclude<TrainingNature, 'UNSPECIFIED'>;
  trainingStandardId?: number;
  sourceTemplateId?: number;
  managerUserId?: number;
  startTime: string;
  endTime: string;
  location?: string;
  description?: string;
  status: 0 | 1;
}

export interface TrainingPlanUpdatePayload extends Omit<TrainingPlanCreatePayload, 'planCode' | 'trainingStandardId' | 'sourceTemplateId'> {
  version: number;
  /** 更新时用 null 明确解除已有的标准或模板关联；省略字段表示保持原值。 */
  trainingStandardId?: number | null;
  sourceTemplateId?: number | null;
}

/** 草稿计划从已发布模板覆盖复制课表的请求；来源模板不能为空。 */
export interface TrainingPlanApplyTemplatePayload {
  version: number;
  sourceTemplateId: number;
}

/** 题库练习抽题规则：仅定义筛选条件和抽取数量，不定义练习分数。 */
export interface TrainingQuestionBankDrawRule {
  questionType: 'SINGLE' | 'MULTIPLE' | 'JUDGE' | 'FILL' | 'ESSAY';
  difficulty?: string | null;
  knowledgePointId?: number | null;
  drawCount: number;
  /** 历史练习配置可能回传；新题库练习不提交且服务端不计分。 */
  scorePerQuestion?: number | null;
  sortOrder: number;
}

/** 不同计划内容共用的执行配置，未使用字段不提交。 */
export interface TrainingContentExecutionConfig {
  completionRule?: 'ALL_REQUIRED_ITEMS' | 'ALL_ITEMS' | null;
  durationSeconds?: number | null;
  passScore?: number | null;
  maxAttempts?: number | null;
  drawRules?: TrainingQuestionBankDrawRule[] | null;
  teacherUserId?: number | null;
  location?: string | null;
  startTime?: string | null;
  endTime?: string | null;
}

/** 计划内容请求字段与 TrainingPlanContentRequest 保持一致。 */
export interface TrainingPlanContent {
  planContentId?: number;
  contentType: 'COURSE' | 'EXAM' | 'QUESTION_BANK' | 'OFFLINE';
  resourceId?: number;
  resourceVersionId?: number;
  assessmentMode?: 'QUIZ' | 'FORMAL_EXAM' | null;
  executionConfig?: TrainingContentExecutionConfig | null;
  contentName: string;
  isRequired: 0 | 1;
  isScored?: 0 | 1;
  scoreWeight?: number;
  scoreAggregationMode?: 'AVERAGE' | 'WEIGHTED_AVERAGE';
  requiredHours?: number;
  sortOrder: number;
  contentStatus?: string;
}

export interface TrainingPlanStage {
  planStageId?: number;
  /** 模板带入时保留的来源阶段 ID，仅用于追溯。 */
  sourceTemplateStageId?: number;
  stageName: string;
  stageType: 'ONLINE' | 'OFFLINE' | 'MIXED';
  requiredHours?: number;
  sortOrder: number;
  contents?: TrainingPlanContent[];
}

export interface TrainingPlanEvaluationForm {
  planEvaluationFormId?: number;
  formVersionId: number;
  evaluationFormId?: number;
  formName?: string;
  versionNo?: number;
  publishedStatus?: 'PUBLISHED' | 'ARCHIVED';
  usageType: 'COMPOSITE' | 'EXPERT_INSPECTION' | 'PARTICIPANT' | 'COURSE_QUALITY';
  isRequired: 0 | 1;
  status: 0 | 1;
}

export interface TrainingParticipant {
  trainingParticipantId: number;
  trainingPlanId: number;
  userId: number;
  participantStatus: string;
  onlineHours?: number;
  offlineHours?: number;
}

export interface TrainingExecutionLink {
  executionLinkId: number;
  planContentId: number;
  executionType: string;
  executionId: number;
}

/** 安排培训内容后的逐项失败信息，供管理端定位具体内容。 */
export interface TrainingExecutionGenerationFailure {
  planContentId: number;
  contentType: string;
  code?: number;
  message?: string;
  contentName?: string;
  /** 课程缺少阅卷人时已落下的开课草稿 ID，用于进入课程考核管理补充配置。 */
  offeringId?: number | null;
}

/** 安排培训内容后的汇总结果。 */
export interface TrainingExecutionGenerationResult {
  trainingPlanId: number;
  offlineSessionsCreated: number;
  executionLinksCreated: number;
  skippedExisting: number;
  failedCount: number;
  failures: TrainingExecutionGenerationFailure[];
}

/** 发布预检的单条阻塞项；题库规则问题会携带规则级定位信息。 */
export interface TrainingPublishIssue {
  code?: number;
  message?: string;
  planContentId?: number | null;
  contentType?: string | null;
  stageId?: number | null;
  stageName?: string | null;
  contentName?: string | null;
  resourceId?: number | null;
  resourceName?: string | null;
  ruleIndex?: number | null;
  questionType?: string | null;
  difficulty?: string | null;
  knowledgePointId?: number | null;
  requiredCount?: number | null;
  availableCount?: number | null;
  suggestion?: string | null;
}

/** 发布预检结果；ready=false 时必须逐项处理 issues 后重新预检。 */
export interface TrainingPublishPrecheck {
  trainingPlanId: number;
  ready: boolean;
  issues: TrainingPublishIssue[];
}

/** 培训结果包含参考分与获得学时，安排学时仍以计划内容 requiredHours 为准。 */
export interface TrainingParticipantResults {
  trainingParticipantId: number;
  courseScore?: number;
  examScore?: number;
  offlineScore?: number;
  compositeScore?: number;
  officialCalculationRunId?: number;
  resultStatus?: string;
  onlineHours?: number;
  offlineHours?: number;
  items?: Array<{
    planContentId: number;
    contentName: string;
    resultStatus?: string;
    score?: number | null;
    /** 该内容已获得学时，单位秒。 */
    effectiveDurationSeconds?: number | null;
  }>;
}

/** 保留后端业务码，供工作台显示真实预检或生命周期失败原因。 */
export class TrainingPlanApiError extends Error {
  constructor(
    public readonly code: number,
    message: string
  ) {
    super(message);
    this.name = 'TrainingPlanApiError';
  }
}

function unwrap<T>(response: ApiResponse<T>): T {
  if (response.code !== 0) throw new TrainingPlanApiError(response.code, response.message || '培训计划操作失败，请稍后重试。');
  return response.data;
}

export const getTrainingPlanPage = async (params: TrainingPlanPageQuery): Promise<TrainingPlanPage> =>
  unwrap(await request<ApiResponse<TrainingPlanPage>>({ url: '/api/training/plans', method: 'GET', params }));

export const getTrainingPlan = async (trainingPlanId: number): Promise<TrainingPlan> =>
  unwrap(await request<ApiResponse<TrainingPlan>>({ url: `/api/training/plans/${trainingPlanId}`, method: 'GET' }));

export const createTrainingPlan = async (data: TrainingPlanCreatePayload): Promise<number> =>
  unwrap(await request<ApiResponse<number>>({ url: '/api/training/plans', method: 'POST', data }));

export const updateTrainingPlan = async (trainingPlanId: number, data: TrainingPlanUpdatePayload): Promise<void> =>
  unwrap(await request<ApiResponse<void>>({ url: `/api/training/plans/${trainingPlanId}`, method: 'PUT', data }));

/** 草稿计划显式从模板覆盖复制阶段与内容，后端负责校验模板和事务一致性。 */
export const applyTrainingPlanTemplate = async (
  trainingPlanId: number,
  data: TrainingPlanApplyTemplatePayload
): Promise<void> =>
  unwrap(await request<ApiResponse<void>>({ url: `/api/training/plans/${trainingPlanId}/apply-template`, method: 'POST', data }));

export const deleteTrainingPlan = async (trainingPlanId: number): Promise<void> =>
  unwrap(await request<ApiResponse<void>>({ url: `/api/training/plans/${trainingPlanId}`, method: 'DELETE' }));

/** 发布、开始、结束与取消均携带当前乐观锁版本。 */
const withVersion = async (trainingPlanId: number, action: string, version: number): Promise<void> =>
  unwrap(await request<ApiResponse<void>>({ url: `/api/training/plans/${trainingPlanId}/${action}`, method: 'POST', data: { version } }));

export const publishTrainingPlan = (trainingPlanId: number, version: number): Promise<void> => withVersion(trainingPlanId, 'publish', version);
export const startTrainingPlan = (trainingPlanId: number, version: number): Promise<void> => withVersion(trainingPlanId, 'start', version);
export const endTrainingPlan = (trainingPlanId: number, version: number): Promise<void> => withVersion(trainingPlanId, 'end', version);
export const cancelTrainingPlan = (trainingPlanId: number, version: number): Promise<void> => withVersion(trainingPlanId, 'cancel', version);

export const precheckTrainingPlanPublish = async (trainingPlanId: number): Promise<TrainingPublishPrecheck> =>
  unwrap(await request<ApiResponse<TrainingPublishPrecheck>>({ url: `/api/training/plans/${trainingPlanId}/publish-precheck`, method: 'POST' }));

export const generateTrainingPlanExecutions = async (trainingPlanId: number): Promise<TrainingExecutionGenerationResult> =>
  unwrap(
    await request<ApiResponse<TrainingExecutionGenerationResult>>({
      url: `/api/training/plans/${trainingPlanId}/generate-executions`,
      method: 'POST'
    })
  );

/** 草稿计划整体覆盖阶段与内容，发布后由后端拒绝修改。 */
export const replaceTrainingPlanStages = async (trainingPlanId: number, data: { version: number; stages: TrainingPlanStage[] }): Promise<void> =>
  unwrap(
    await request<ApiResponse<void>>({
      url: `/api/training/plans/${trainingPlanId}/stages`,
      method: 'PUT',
      data
    })
  );

/** 草稿计划整体绑定评价表单版本。 */
export const bindTrainingPlanEvaluationForms = async (
  trainingPlanId: number,
  data: { version: number; forms: TrainingPlanEvaluationForm[] }
): Promise<void> =>
  unwrap(
    await request<ApiResponse<void>>({
      url: `/api/training/plans/${trainingPlanId}/evaluation-forms`,
      method: 'PUT',
      data
    })
  );

/** 指派单名参训人；人员选择入口由后端权限和候选集控制。 */
export const assignTrainingPlanParticipant = async (trainingPlanId: number, userId: number): Promise<number> =>
  unwrap(
    await request<ApiResponse<number>>({
      url: `/api/training/plans/${trainingPlanId}/participants/assign`,
      method: 'POST',
      data: { userId }
    })
  );

/** 按统一人员选择条件批量绑定参训人员，后端负责展开并去重。 */
export const bindTrainingPlanParticipantSelection = async (
  trainingPlanId: number,
  selection: PersonSelectionRequest
): Promise<TrainingParticipant[]> =>
  unwrap(
    await request<ApiResponse<TrainingParticipant[]>>({
      url: `/api/training/plans/${trainingPlanId}/participants/bind-selection`,
      method: 'POST',
      data: { selection }
    })
  );

export const getTrainingPlanParticipants = async (trainingPlanId: number): Promise<TrainingParticipant[]> =>
  unwrap(
    await request<ApiResponse<TrainingParticipant[]>>({
      url: `/api/training/plans/${trainingPlanId}/participants`,
      method: 'GET'
    })
  );

/** 从 CSV 批量导入参训人员；返回逐行导入结果。 */
export const importTrainingParticipants = async (trainingPlanId: number, file: File): Promise<UserImportResult> => {
  const formData = new FormData();
  formData.append('file', file);
  const res = await request<ApiResponse<UserImportResult>>({
    url: `/api/training/plans/${trainingPlanId}/participants/import`,
    method: 'POST',
    data: formData,
    headers: { 'Content-Type': 'multipart/form-data' }
  });
  return res.data as UserImportResult;
};

/** 导出参训人员名单为 CSV（Blob 下载）。 */
export const exportTrainingParticipants = async (trainingPlanId: number): Promise<Blob> =>
  request<Blob>({
    url: `/api/training/plans/${trainingPlanId}/participants/export`,
    method: 'GET',
    responseType: 'blob'
  });

export const getTrainingPlanExecutionLinks = async (trainingPlanId: number): Promise<TrainingExecutionLink[]> =>
  unwrap(
    await request<ApiResponse<TrainingExecutionLink[]>>({
      url: `/api/training/plans/${trainingPlanId}/executions`,
      method: 'GET'
    })
  );

export const getTrainingParticipantResults = async (trainingPlanId: number, trainingParticipantId: number): Promise<TrainingParticipantResults> =>
  unwrap(
    await request<ApiResponse<TrainingParticipantResults>>({
      url: `/api/training/plans/${trainingPlanId}/participants/${trainingParticipantId}/results`,
      method: 'GET'
    })
  );
