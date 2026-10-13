import type { ApiResponse } from '@shared/types';
import { request } from './request';
/** 线下课堂及录入成绩，无新增作答流程。 */
export interface OfflineClassStatistics {
 offlineSessionId:number;sessionName:string;contentName:string;location:string;examContents:{examName:string;paperVersionId:number|null;paperName:string|null;versionNo:number|null}[];
 expectedCount:number;scoredCount:number;unscoredCount:number;
 assessments:{scoreName:string;scoredCount:number;unscoredCount:number;highestPercent:number|null;averagePercent:number|null;lowestPercent:number|null;scoreBands:{label:string;count:number;percent:number}[]}[];
 people:{trainingParticipantId:number;userId:number;learnerName:string;scoreName:string|null;score:number|null;maxScore:number|null;scoredTime:string|null}[];
}
/** 读取有权查看的课堂内容及成绩统计。 */
export async function getOfflineClassStatistics(id:number):Promise<OfflineClassStatistics> {
 const response=await request<ApiResponse<OfflineClassStatistics>>({url:`/api/training/offline-sessions/${id}/statistics`});return response.data;
}
