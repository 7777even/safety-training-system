import type { ApiResponse } from '@shared/types';
import { request } from './request';
/** 计划汇总；正式完成与未通过、未完成分列。 */
export interface TrainingPlanStatistics { trainingPlanId:number;planName:string;publishStatus:string;expectedCount:number;completedCount:number;failedCount:number;incompleteCount:number;completionRate:number }
/** 逐内容来源事实，已结束时为冻结结果。 */
export interface TrainingProgressItem { planContentId:number;contentName:string;executionType:string;resultStatus:string;effectiveDurationSeconds:number|null;score:number|null;completedTime:string|null }
/** 学员必修完成进度及正式结论。 */
export interface TrainingProgressPerson { trainingParticipantId:number;userId:number;learnerName:string;learnerCode:string;resultStatus:string;requiredCount:number;completedContentCount:number;progressPercent:number;onlineHours:number|null;offlineHours:number|null;finalized:boolean;items:TrainingProgressItem[] }
/** 按名称及分页读取有权查看的计划统计。 */
export async function getTrainingPlanStatistics(params:{planName?:string;pageNum:number;pageSize:number}):Promise<{list:TrainingPlanStatistics[];total:number}> {const response=await request<ApiResponse<{list:TrainingPlanStatistics[];total:number}>>({url:'/api/training/statistics/plans',params});return response.data;}
/** 完成人员与进度明细共享同一只读接口。 */
export async function getTrainingProgressPeople(trainingPlanId:number,params:{completedOnly?:boolean;learnerKeyword?:string;pageNum:number;pageSize:number}):Promise<{list:TrainingProgressPerson[];total:number}> {const response=await request<ApiResponse<{list:TrainingProgressPerson[];total:number}>>({url:`/api/training/statistics/plans/${trainingPlanId}/people`,params});return response.data;}
