import type { ApiResponse } from '@shared/types';
import { request } from './request';
/** 计划下的部门班级，混合计划允许同时归入两种模式。 */
export interface TrainingClassGroup { trainingPlanId:number;planName:string;deptId:number|null;deptName:string;mode:string;participantCount:number }
/** 汇总人数为计划内学员去重数。 */
export interface TrainingClassStatistics { totalParticipantCount:number;onlineParticipantCount:number;offlineParticipantCount:number;classes:TrainingClassGroup[] }
/** 班级人员。 */
export interface TrainingClassPerson { trainingPlanId:number;planName:string;deptId:number|null;deptName:string;userId:number;learnerName:string;learnerCode:string;mode:string }
/** 读取可见班级统计。 */
export async function getTrainingClassStatistics(trainingPlanId?:number):Promise<TrainingClassStatistics> {
  const response=await request<ApiResponse<TrainingClassStatistics>>({url:'/api/training/statistics/classes',params:{trainingPlanId}});return response.data;
}
/** 与汇总一致的范围内分页下钻。 */
export async function getTrainingClassPeople(params:{trainingPlanId?:number;deptId?:number;unassignedOnly?:boolean;mode?:string;learnerKeyword?:string;pageNum:number;pageSize:number}):Promise<{list:TrainingClassPerson[];total:number}> {
  const response=await request<ApiResponse<{list:TrainingClassPerson[];total:number}>>({url:'/api/training/statistics/classes/people',params});return response.data;
}
