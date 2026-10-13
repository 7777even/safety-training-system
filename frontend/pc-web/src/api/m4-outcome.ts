import type { ApiResponse } from '@shared/types';
import { request } from './request';

export type SurveyQuestionType = 'SINGLE' | 'MULTIPLE' | 'TEXT';
export type SurveyResultVisibility = 'ADMIN_ONLY' | 'SUBMITTER_AFTER_SUBMIT';
export interface SurveyOption {
  surveyOptionId?: number;
  optionKey: string;
  optionContent: string;
  sortOrder: number;
  status: 0 | 1;
}
export interface SurveyQuestion {
  surveyQuestionId?: number;
  questionType: SurveyQuestionType;
  questionText: string;
  isRequired: 0 | 1;
  sortOrder: number;
  status: 0 | 1;
  options: SurveyOption[];
}
export interface Survey {
  surveyId: number;
  surveyCode: string;
  surveyName: string;
  surveyType: 'TRAINING' | 'ORGANIZATION_EVALUATION';
  description?: string;
  contentStatus: 'DRAFT' | 'PUBLISHED';
  status: 0 | 1;
  version: number;
  questions?: SurveyQuestion[];
}
export interface SurveyInstance {
  surveyInstanceId: number;
  instanceCode: string;
  instanceName: string;
  surveyId: number;
  purpose: 'TRAINING_SURVEY' | 'ORG_EVALUATION';
  resultVisibility: SurveyResultVisibility;
  instanceStatus: string;
  fillUrl: string;
  qrContent: string;
}
export interface SurveyFill extends Pick<SurveyInstance, 'surveyInstanceId' | 'instanceName' | 'purpose' | 'resultVisibility'> {
  surveyName: string;
  submitted: boolean;
  questions: SurveyQuestion[];
}
/** 档案内的培训计划事实；字段口径严格对应 archive OpenAPI 1.3.0 的 plan 对象。 */
export interface ArchivePlanFact {
  trainingPlanId?: number;
  planCode?: string;
  planName?: string;
  publishStatus?: string;
  startTime?: string;
  endTime?: string;
  endedTime?: string;
  location?: string;
  trainingNature?: string;
  applicableRequiredHours?: number;
  trainingTypeId?: number;
  trainingTypeName?: string;
}
/** 档案内的企业事实。 */
export interface ArchiveOrganizationFact {
  organizationId?: number;
  organizationName?: string;
  orgCode?: string;
}
/** 培训内容项；是否必修、是否计分在契约中为 0/1 而非布尔。 */
export interface ArchiveContent {
  planContentId: number;
  planStageId?: number;
  stageName?: string;
  contentName: string;
  contentType: string;
  isRequired?: number;
  isScored?: number;
  scoreWeight?: number;
  requiredHours?: number;
  sortOrder?: number;
}
/** 参训人员；成绩、签到与综合评价以 trainingParticipantId 关联。 */
export interface ArchiveParticipant {
  trainingParticipantId: number;
  userId?: number;
  userName?: string;
  userCode?: string;
  participantOrgId?: number;
  participantOrgName?: string;
}
/** 签到情况三态：不适用 / 已到场（已录入线下成绩）/ 尚未录入线下成绩。 */
export type ArchiveSignInStatus = 'NOT_APPLICABLE' | 'SIGNED' | 'NOT_RECORDED';
export interface ArchiveAttendance {
  trainingParticipantId: number;
  userId?: number;
  userName?: string;
  signInStatus: ArchiveSignInStatus;
  offlineScore?: number;
  recordedTime?: string;
}
export interface ArchiveScore {
  trainingParticipantId: number;
  userId?: number;
  userName?: string;
  examScore?: number;
  courseScore?: number;
  offlineScore?: number;
  compositeScore?: number;
  resultStatus?: string;
}
export interface ArchiveEvaluation {
  trainingParticipantId: number;
  userId?: number;
  userName?: string;
  compositeScore?: number;
  resultStatus?: string;
}
/** 一期档案的已提交专家查课事实；个人和企业档案不返回。 */
export interface ArchiveExpertInspection {
  contentName: string;
  versionNo: number;
  evaluatorName?: string;
  submittedTime?: string;
  totalScore?: number;
  summary?: string;
  items: Array<{ itemName: string; itemType: 'SCORE' | 'TEXT'; score?: number; textValue?: string }>;
}
export interface ArchiveRewardPenalty {
  rewardPenaltyId: number;
  trainingParticipantId: number;
  recordType: string;
  title: string;
  description?: string;
  scoreChange?: number;
  occurredDate?: string;
  evidenceFileId?: number;
  status?: number;
}
/** 影像记录与附件清单共用结构；原件经受控资料端点领取，档案文件内不内嵌。 */
export interface ArchiveMaterial {
  trainingAttachmentId: number;
  attachmentType: string;
  attachmentName: string;
  description?: string;
  fileInfoId?: number;
  planStageId?: number;
  stageName?: string;
  sortOrder?: number;
}
/** 档案内证书清单；缺少 certificate:cert:list 时整个字段不返回。 */
export interface ArchiveCertificateItem {
  certificateId: number;
  certificateNo: string;
  holderUserId: number;
  sourceType: 'COURSE' | 'EXAM' | 'TRAINING';
  sourceId: number;
  issuedTime: string;
  certificateStatus: string;
}
/** 档案数据统计；比率分母为 0 时为空值，页面按"—"占位而不是 0%。 */
export interface ArchiveStatistics {
  participantCount: number;
  completedCount: number;
  passedCount: number;
  failedCount: number;
  incompleteCount: number;
  passRate?: number;
  averageCompositeScore?: number;
  contentCount: number;
  requiredContentCount: number;
  onlineHoursTotal: number;
  offlineHoursTotal: number;
  imageCount: number;
  attachmentCount: number;
  materialCount: number;
  rewardCount: number;
  penaltyCount: number;
  signedCount?: number;
  notRecordedCount?: number;
}
export interface ArchiveView {
  trainingPlanId: number;
  planCode: string;
  planName: string;
  publishStatus?: string;
  organizationId?: number;
  organizationName: string;
  participantCount: number;
  missingItems: string[];
  plan?: ArchivePlanFact;
  organization?: ArchiveOrganizationFact;
  contents?: ArchiveContent[];
  participants?: ArchiveParticipant[];
  attendance?: ArchiveAttendance[];
  scores?: ArchiveScore[];
  evaluations?: ArchiveEvaluation[];
  expertInspections?: ArchiveExpertInspection[];
  rewardPenalties?: ArchiveRewardPenalty[];
  materials?: ArchiveMaterial[];
  attachments?: ArchiveMaterial[];
  certificates?: ArchiveCertificateItem[];
  statistics?: ArchiveStatistics;
  fileInfoId?: number;
  /** 当前归档文件是否为 PDF 正本；历史 JSON 档案为 false，页面据此提示重新更新档案。 */
  archiveFileIsPdf?: boolean;
}
export interface ArchivePerson {
  /** 管理者已有领域读取权限覆盖的统一历史。 */
  history?: import('@shared/types/learner-history').LearnerHistory[];
  userId: number;
  userName: string;
  userCode?: string;
  missingItems: string[];
  trainings: ArchiveView[];
  fileInfoId?: number;
}
/** 更新一期档案请求；archiveCode 与快照原因均为契约必填。 */
export interface ArchiveCreateFromPlanRequest {
  trainingPlanId: number;
  archiveCode: string;
  versionReason: string;
}
/** 已签发证书；文件标识和签发时间严格对应 certificate OpenAPI。 */
export interface Certificate {
  certificateId: number;
  certificateNo: string;
  holderUserId: number;
  sourceType: 'COURSE' | 'EXAM' | 'TRAINING';
  sourceId: number;
  /** 来源课程、考试或培训计划的当前名称。 */
  sourceName?: string | null;
  certificateStatus: string;
  issuedTime: string;
  certificateFileId: number;
}
export interface TrainingEndedResult {
  trainingPlanId: number;
  planCode: string;
  planName: string;
  publishStatus: 'ENDED';
  participantCount: number;
  passedCount: number;
  failedCount: number;
  incompleteCount: number;
  averageCompositeScore?: number;
  participants: Array<{
    trainingParticipantId: number;
    userId: number;
    courseScore?: number;
    examScore?: number;
    offlineScore?: number;
    compositeScore?: number;
    resultStatus: string;
    onlineHours?: number;
    offlineHours?: number;
    resultFinalizedTime?: string;
    items: Array<{
      planContentId: number;
      contentName: string;
      executionType: string;
      resultStatus: string;
      score?: number;
      effectiveDurationSeconds?: number;
    }>;
  }>;
  rewardPenalties: Array<{
    rewardPenaltyId: number;
    trainingParticipantId: number;
    title: string;
    recordType: 'REWARD' | 'PENALTY';
    scoreChange?: number;
    description?: string;
    occurredDate?: string;
  }>;
}
function unwrap<T>(response: ApiResponse<T>): T {
  if (response.code !== 0) throw new Error(response.message || '请求失败，请稍后重试。');
  return response.data;
}
export async function getSurveyPage(params: Record<string, unknown>): Promise<{ list: Survey[]; total: number }> {
  return unwrap(await request<ApiResponse<{ list: Survey[]; total: number }>>({ url: '/api/survey/surveys', method: 'GET', params }));
}
export async function getSurvey(id: number): Promise<Survey> {
  return unwrap(await request<ApiResponse<Survey>>({ url: `/api/survey/surveys/${id}`, method: 'GET' }));
}
export async function saveSurvey(data: Partial<Survey>): Promise<number> {
  return unwrap(await request<ApiResponse<number>>({ url: '/api/survey/surveys', method: 'POST', data }));
}
/** 更新未投放问卷，版本号由详情接口返回，避免覆盖他人编辑。 */
export async function updateSurvey(id: number, data: Pick<Survey, 'version' | 'surveyName' | 'description' | 'status'>): Promise<void> {
  await unwrap(await request<ApiResponse<null>>({ url: `/api/survey/surveys/${id}`, method: 'PUT', data }));
}
export async function copySurvey(id: number, data: { surveyCode: string; surveyName: string }): Promise<number> {
  return unwrap(await request<ApiResponse<number>>({ url: `/api/survey/surveys/${id}/copy`, method: 'POST', data }));
}
export async function deleteSurvey(id: number): Promise<void> {
  await unwrap(await request<ApiResponse<null>>({ url: `/api/survey/surveys/${id}`, method: 'DELETE' }));
}
export async function previewSurvey(
  id: number,
  client: 'PC' | 'MOBILE'
): Promise<Pick<Survey, 'surveyId' | 'surveyName' | 'surveyType' | 'questions'>> {
  return unwrap(
    await request<ApiResponse<Pick<Survey, 'surveyId' | 'surveyName' | 'surveyType' | 'questions'>>>({
      url: `/api/survey/surveys/${id}/preview`,
      method: 'GET',
      params: { client }
    })
  );
}
export async function replaceSurveyQuestions(id: number, version: number, questions: SurveyQuestion[]): Promise<void> {
  await unwrap(await request<ApiResponse<null>>({ url: `/api/survey/surveys/${id}/questions`, method: 'PUT', data: { version, questions } }));
}
export async function getSurveyInstances(params: Record<string, unknown>): Promise<{ list: SurveyInstance[]; total: number }> {
  return unwrap(await request<ApiResponse<{ list: SurveyInstance[]; total: number }>>({ url: '/api/survey/instances', method: 'GET', params }));
}
export async function createSurveyInstance(data: Record<string, unknown>): Promise<SurveyInstance> {
  return unwrap(await request<ApiResponse<SurveyInstance>>({ url: '/api/survey/instances', method: 'POST', data }));
}
export async function getSurveyFill(id: number): Promise<SurveyFill> {
  return unwrap(await request<ApiResponse<SurveyFill>>({ url: `/api/survey/instances/${id}/fill`, method: 'GET' }));
}
export async function submitSurvey(id: number, answers: unknown[]): Promise<void> {
  await unwrap(await request<ApiResponse<null>>({ url: `/api/survey/instances/${id}/submit`, method: 'POST', data: { deviceType: 'PC', answers } }));
}
export interface SurveyStats {
  surveyInstanceId: number;
  totalSubmitted: number;
  questionStats: Array<{
    surveyQuestionId: number;
    questionText: string;
    optionCounts: Array<{ optionContent: string; count: number }>;
    textAnswers: string[];
  }>;
}
export async function getSurveyStats(id: number): Promise<SurveyStats> {
  return unwrap(await request<ApiResponse<SurveyStats>>({ url: `/api/survey/instances/${id}/stats`, method: 'GET' }));
}
export async function getEndedResult(id: number): Promise<TrainingEndedResult> {
  return unwrap(await request<ApiResponse<TrainingEndedResult>>({ url: `/api/training/plans/${id}/ended-result`, method: 'GET' }));
}
export async function getArchive(id: number): Promise<ArchiveView> {
  return unwrap(await request<ApiResponse<ArchiveView>>({ url: `/api/archive/trainings/${id}`, method: 'GET' }));
}
/** 从已结束且结果完整的培训更新一期档案；不完整时由服务端返回 409/9204。 */
export async function createArchiveFromPlan(data: ArchiveCreateFromPlanRequest): Promise<void> {
  await unwrap(await request<ApiResponse<null>>({ url: '/api/archive/trainings/create-from-plan', method: 'POST', data }));
}
/** 企业档案按当前会话机构返回已结束培训，接口不分页。 */
export async function getEnterpriseArchives(params: Record<string, unknown> = {}): Promise<ArchiveView[]> {
  return unwrap(await request<ApiResponse<ArchiveView[]>>({ url: '/api/archive/enterprises', method: 'GET', params }));
}
export async function getArchivePerson(id: number): Promise<ArchivePerson> {
  return unwrap(await request<ApiResponse<ArchivePerson>>({ url: `/api/archive/persons/${id}`, method: 'GET' }));
}
export async function downloadArchive(id: number): Promise<Blob> {
  return request<Blob>({ url: `/api/archive/trainings/${id}/file`, method: 'GET', responseType: 'blob' });
}
export async function downloadArchivePerson(id: number): Promise<Blob> {
  return request<Blob>({ url: `/api/archive/persons/${id}/file`, method: 'GET', responseType: 'blob' });
}
export async function getCertificates(params: Record<string, unknown>, mine = false): Promise<{ list: Certificate[]; total: number }> {
  return unwrap(
    await request<ApiResponse<{ list: Certificate[]; total: number }>>({
      url: mine ? '/api/certificate/my-certificates' : '/api/certificate/certificates',
      method: 'GET',
      params
    })
  );
}
export async function downloadCertificate(id: number, mine = false): Promise<Blob> {
  return request<Blob>({
    url: mine ? `/api/certificate/my-certificates/${id}/file` : `/api/certificate/certificates/${id}/file`,
    method: 'GET',
    responseType: 'blob'
  });
}
/** 证书模板与规则字段以 OpenAPI 为准保留对象结构，页面不增造未定义字段。 */
export interface CertificateElement {
  elementType: 'TEXT' | 'VARIABLE';
  variableCode?: string;
  staticContent?: string;
  xPosition: number;
  yPosition: number;
  width?: number;
  height?: number;
  fontFamily?: string;
  fontSize?: number;
  fontColor?: string;
  sortOrder: number;
  status: 0 | 1;
}
export interface CertificateTemplateVersion {
  backgroundFileId: number;
  widthPx: number;
  heightPx: number;
  versionNote?: string;
  elements: CertificateElement[];
}
export interface CertificateTemplate {
  certificateTemplateId: number;
  templateCode: string;
  templateName: string;
  currentVersionNo: number;
  status: 0 | 1;
  version: number;
  description?: string;
  draftVersion?: CertificateTemplateVersion;
  currentPublishedVersion?: CertificateTemplateVersion;
}
export interface CertificateRule {
  certificateRuleId: number;
  ruleName: string;
  sourceType: 'COURSE' | 'EXAM' | 'TRAINING';
  sourceId: number;
  certificateTemplateId: number;
  conditionType: 'PASS';
  isAutoIssue: 0 | 1;
  status: 0 | 1;
  version: number;
}
export interface CertificateRuleSourceCandidate {
  sourceType: CertificateRule['sourceType'];
  sourceId: number;
  sourceCode: string;
  sourceName: string;
  publishStatus: string;
}
export async function getTemplatePage(params: Record<string, unknown>): Promise<{ list: CertificateTemplate[]; total: number }> {
  return unwrap(
    await request<ApiResponse<{ list: CertificateTemplate[]; total: number }>>({ url: '/api/certificate/templates', method: 'GET', params })
  );
}
export async function getTemplate(id: number): Promise<CertificateTemplate> {
  return unwrap(await request<ApiResponse<CertificateTemplate>>({ url: `/api/certificate/templates/${id}`, method: 'GET' }));
}
export async function createTemplate(data: Record<string, unknown>): Promise<number> {
  return unwrap(await request<ApiResponse<number>>({ url: '/api/certificate/templates', method: 'POST', data }));
}
export async function updateTemplate(id: number, data: Record<string, unknown>): Promise<void> {
  await unwrap(await request<ApiResponse<null>>({ url: `/api/certificate/templates/${id}`, method: 'PUT', data }));
}
export async function deleteTemplate(id: number): Promise<void> {
  await unwrap(await request<ApiResponse<null>>({ url: `/api/certificate/templates/${id}`, method: 'DELETE' }));
}
export async function publishTemplate(id: number, data: Record<string, unknown>): Promise<void> {
  await unwrap(await request<ApiResponse<null>>({ url: `/api/certificate/templates/${id}/publish`, method: 'POST', data }));
}
export async function previewTemplate(id: number): Promise<Blob> {
  return request<Blob>({ url: `/api/certificate/templates/${id}/preview`, method: 'GET', responseType: 'blob' });
}
/** 读取模板当前设计底图，供设计器按真实底图所见即所得定位元素。 */
export async function getTemplateBackground(id: number): Promise<Blob> {
  return request<Blob>({ url: `/api/certificate/templates/${id}/background`, method: 'GET', responseType: 'blob' });
}
/** 规则接口返回完整数组，按页面参数在前端分页。 */
export async function getRulePage(params: { pageNum: number; pageSize: number }): Promise<{ list: CertificateRule[]; total: number }> {
  const rules = unwrap(await request<ApiResponse<CertificateRule[]>>({ url: '/api/certificate/rules', method: 'GET' }));
  const start = (params.pageNum - 1) * params.pageSize;
  return { list: rules.slice(start, start + params.pageSize), total: rules.length };
}
export async function getRule(id: number): Promise<CertificateRule> {
  return unwrap(await request<ApiResponse<CertificateRule>>({ url: `/api/certificate/rules/${id}`, method: 'GET' }));
}
export async function createRule(data: Record<string, unknown>): Promise<number> {
  return unwrap(await request<ApiResponse<number>>({ url: '/api/certificate/rules', method: 'POST', data }));
}
export async function getRuleSourceCandidates(params: Record<string, unknown>): Promise<{ list: CertificateRuleSourceCandidate[]; total: number }> {
  return unwrap(
    await request<ApiResponse<{ list: CertificateRuleSourceCandidate[]; total: number }>>({
      url: '/api/certificate/rule-source-candidates',
      method: 'GET',
      params
    })
  );
}
export async function updateRule(id: number, data: Record<string, unknown>): Promise<void> {
  await unwrap(await request<ApiResponse<null>>({ url: `/api/certificate/rules/${id}`, method: 'PUT', data }));
}
export async function deleteRule(id: number): Promise<void> {
  await unwrap(await request<ApiResponse<null>>({ url: `/api/certificate/rules/${id}`, method: 'DELETE' }));
}
