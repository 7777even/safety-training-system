import type { ApiResponse } from '@shared/types';
import { clearTargetTenantId, request, setTargetTenantId } from './request';

/** 当前监管接口的通用分页结构。 */
export interface RegulationPage<T> { list: T[]; total: number }
/** 机构概览及监管工作台事实。 */
export interface RegulationInstitution { organizationId: number; orgCode: string; orgName: string; orgType: 'ENTERPRISE' | 'TRAINING_INSTITUTION'; planCount: number; participantCount: number }
export interface RegulationShortcut { code: string; title: string; path: string; permission: string; granted: boolean }
export interface RegulationWorkbench {
  enterpriseCount: number; trainingInstitutionCount: number; planTotalCount: number; runningPlanCount: number;
  endedPlanCount: number; participantCount: number; completedParticipantCount: number; unreadWarningCount: number;
  trainingTypeDistribution: { trainingTypeId: number; typeName: string; planCount: number }[];
  specialOperation: { planCount: number; participantCount: number; completedParticipantCount: number };
  institutions: RegulationInstitution[]; shortcuts: RegulationShortcut[];
}
/** 园区与机构两级学习统计使用同一口径。 */
export interface RegulationLearningStats {
  organizationId?: number; orgCode?: string; orgName?: string; orgType?: string;
  employeeAccountCount: number; classSessionCount: number; classLearnerCount: number;
  participatingOfferingCount: number; avgSessionsPerLearner: number; participationRate: number;
  institutions: RegulationLearningStats[];
}
export interface RegulationLearner { userId: number; userName: string; userCode: string; organizationId: number; orgName?: string; offeringCount: number }
export interface RegulationOffering { courseOfferingId: number; offeringName: string; organizationId: number; orgName?: string; learnerCount: number }
/** 学时不足证据与查看、结束时间均由服务端留存。 */
export interface RegulationWarning {
  warningId: number; regulatedOrgId: number; regulatedOrgName: string; trainingPlanId: number; planCode: string; planName: string;
  trainingTypeId: number; trainingTypeName: string; trainingNature: 'INITIAL' | 'REVIEW'; planTotalHours: number;
  requiredHours: number; hoursGap: number; warningStatus: 'UNREAD' | 'READ' | 'ENDED';
  detectedTime: string; firstViewedTime?: string; closedTime?: string;
}
export interface RegulationWarningQuery { pageNum: number; pageSize: number; 'query.warningStatus'?: RegulationWarning['warningStatus']; 'query.regulatedOrgId'?: number; 'query.trainingTypeId'?: number }
export interface RegulationPlan { trainingPlanId: number; organizationId: number; planCode: string; planName: string; publishStatus: string; trainingNature: string; applicableRequiredHours?: number }
export interface RegulationParticipant { trainingParticipantId: number; userId: number; userName?: string; participantStatus: string; onlineHours: number; offlineHours: number }
export interface RegulationParticipantResults { trainingParticipantId: number; courseScore?: number; examScore?: number; offlineScore?: number; compositeScore?: number; resultStatus: string; onlineHours: number; offlineHours: number; items: unknown[] }

/** 解包项目的统一 Result。 */
function unwrap<T>(result: ApiResponse<T>): T {
  if (result.code !== 0) throw new Error(result.message || '请求失败，请稍后重试。');
  return result.data;
}

export const getRegulationWorkbench = async (): Promise<RegulationWorkbench> =>
  unwrap(await request<ApiResponse<RegulationWorkbench>>({ url: '/api/regulation/workbench', method: 'GET' }));
export const getRegulationLearningStats = async (organizationId?: number): Promise<RegulationLearningStats> =>
  unwrap(await request<ApiResponse<RegulationLearningStats>>({ url: '/api/regulation/learning-stats', method: 'GET', params: { organizationId } }));
export const getRegulationLearners = async (organizationId?: number): Promise<RegulationLearner[]> =>
  unwrap(await request<ApiResponse<RegulationLearner[]>>({ url: '/api/regulation/learning-stats/learners', method: 'GET', params: { organizationId } }));
export const getRegulationOfferings = async (organizationId?: number): Promise<RegulationOffering[]> =>
  unwrap(await request<ApiResponse<RegulationOffering[]>>({ url: '/api/regulation/learning-stats/offerings', method: 'GET', params: { organizationId } }));
export const exportRegulationInstitutions = (): Promise<Blob> =>
  request<Blob>({ url: '/api/regulation/institutions/export', method: 'GET', responseType: 'blob' });
export const exportRegulationLearning = (organizationId?: number): Promise<Blob> =>
  request<Blob>({ url: '/api/regulation/learning-stats/export', method: 'GET', params: { organizationId }, responseType: 'blob' });
export const getRegulationWarningCount = async (): Promise<number> =>
  unwrap(await request<ApiResponse<number>>({ url: '/api/regulation/warnings/unread-count', method: 'GET' }));
export const getRegulationWarnings = async (params: RegulationWarningQuery): Promise<RegulationPage<RegulationWarning>> =>
  unwrap(await request<ApiResponse<RegulationPage<RegulationWarning>>>({ url: '/api/regulation/warnings', method: 'GET', params }));
/** 详情 GET 会由服务端记录首次查看，无需另发标记请求。 */
export const getRegulationWarning = async (warningId: number): Promise<RegulationWarning> =>
  unwrap(await request<ApiResponse<RegulationWarning>>({ url: `/api/regulation/warnings/${warningId}`, method: 'GET' }));
export const exportRegulationWarnings = (query: Omit<RegulationWarningQuery, 'pageNum' | 'pageSize'>): Promise<Blob> =>
  request<Blob>({ url: '/api/regulation/warnings/export', method: 'GET', params: {
    warningStatus: query['query.warningStatus'], regulatedOrgId: query['query.regulatedOrgId'], trainingTypeId: query['query.trainingTypeId']
  }, responseType: 'blob' });

/** 目标租户视角只在计划只读页存活，离开时立即清除。 */
export function enterRegulationPlanView(organizationId: number): void { setTargetTenantId(organizationId); }
export function leaveRegulationPlanView(): void { clearTargetTenantId(); }
export const getRegulationPlans = async (pageNum: number, pageSize: number): Promise<RegulationPage<RegulationPlan>> =>
  unwrap(await request<ApiResponse<RegulationPage<RegulationPlan>>>({ url: '/api/regulation/training-plans', method: 'GET', params: { pageNum, pageSize } }));
export const getRegulationPlan = async (trainingPlanId: number): Promise<RegulationPlan> =>
  unwrap(await request<ApiResponse<RegulationPlan>>({ url: `/api/regulation/training-plans/${trainingPlanId}`, method: 'GET' }));
export const getRegulationParticipants = async (trainingPlanId: number): Promise<RegulationParticipant[]> =>
  unwrap(await request<ApiResponse<RegulationParticipant[]>>({ url: `/api/regulation/training-plans/${trainingPlanId}/participants`, method: 'GET' }));
export const getRegulationParticipantResults = async (trainingPlanId: number, participantId: number): Promise<RegulationParticipantResults> =>
  unwrap(await request<ApiResponse<RegulationParticipantResults>>({ url: `/api/regulation/training-plans/${trainingPlanId}/participants/${participantId}/results`, method: 'GET' }));

/** 保存服务端返回的真实 UTF-8 CSV。 */
export function saveRegulationCsv(blob: Blob, filename: string): void {
  const url = globalThis.URL.createObjectURL(blob);
  const link = globalThis.document.createElement('a');
  link.href = url;
  link.download = filename;
  link.click();
  globalThis.URL.revokeObjectURL(url);
}
