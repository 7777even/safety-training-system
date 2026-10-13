import type { ApiResponse } from '@shared/types';
import { request } from './request';
/** 课程参与汇总，时长为有效学习秒。 */
export interface CourseStatisticsSummary { enrolledCount:number; participatedCount:number; completedCount:number; totalStudySeconds:number; studyCount:number; averageProgress:number|null }
/** 开班或分类统计，分类之间允许重叠。 */
export interface CourseStatisticsGroup { id:number; name:string; summary:CourseStatisticsSummary }
/** 课程统计完整结果。 */
export interface CourseStatistics {
  summary:CourseStatisticsSummary; offerings:CourseStatisticsGroup[]; categories:CourseStatisticsGroup[];
  knowledgePoints:{knowledgePointId:number;knowledgeName:string;questionCount:number;answeredCount:number;evaluatedCount:number;correctCount:number;accuracyRate:number|null}[];
  historicalKnowledgeMissingCount:number;
}
/** 学员每条报名的事实记录。 */
export interface CourseStatisticsPerson {
  courseEnrollmentId:number;userId:number;learnerName:string;learnerCode:string;courseId:number;courseName:string;courseOfferingId:number;offeringName:string;learningStatus:string;totalStudySeconds:number;studyCount:number;progressPercent:number|null;
}
/** 加载课程及分类统计。 */
export async function getCourseStatistics(courseId?:number,offeringId?:number):Promise<CourseStatistics> {
  const response=await request<ApiResponse<CourseStatistics>>({url:'/api/course/statistics',params:{courseId,offeringId}});return response.data;
}
/** 分页查询个人学习记录。 */
export async function getCourseStatisticsPeople(params:{courseId?:number;offeringId?:number;learnerKeyword?:string;pageNum:number;pageSize:number}):Promise<{list:CourseStatisticsPerson[];total:number}> {
  const response=await request<ApiResponse<{list:CourseStatisticsPerson[];total:number}>>({url:'/api/course/statistics/people',params});return response.data;
}
