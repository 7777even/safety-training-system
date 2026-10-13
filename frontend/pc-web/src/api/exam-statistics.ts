import type { ApiResponse } from '@shared/types';
import { request } from './request';

/** 分类人数和百分比。 */
export interface StatisticsBucket { label: string; count: number; percent: number }
/** 单次考试的完整考情汇总。 */
export interface ExamStatistics {
  expectedCount: number; attendedCount: number; completedCount: number; completionRate: number;
  pendingReviewCount: number; unpublishedCount: number; absentCount: number;
  highestScore: number | null; averageScore: number | null; lowestScore: number | null;
  averageUsedSeconds: number | null; scoreBands: StatisticsBucket[];
  questionTypes: { questionType: string; count: number; percent: number; evaluatedCount: number; correctCount: number; accuracyRate: number | null }[];
  wrongTop5: { questionVersionId: number; stem: string; wrongCount: number; wrongRate: number }[];
  platforms: StatisticsBucket[]; operatingSystems: StatisticsBucket[]; browsers: StatisticsBucket[];
}
/** 统计人员明细，成绩取最终结果，时间取最近作答。 */
export interface ExamStatisticsPerson {
  examCandidateId: number; userId: number; learnerName: string; learnerCode: string;
  candidateStatus: string; finalScore: number | null; rank: number | null;
  attended: boolean; completed: boolean; absent: boolean;
  startedTime: string | null; submittedTime: string | null; usedSeconds: number | null;
  platform: string | null; operatingSystem: string | null; browser: string | null;
}
/** 人员列表和导出共用筛选条件。 */
export interface ExamStatisticsQuery { learnerKeyword?: string; absentOnly?: boolean }
/** 加载当前考试汇总。 */
export async function getExamStatistics(examId: number): Promise<ExamStatistics> {
  const result = await request<ApiResponse<ExamStatistics>>({ url: `/api/exam/exams/${examId}/statistics` });
  return result.data;
}
/** 加载当前考试人员分页。 */
export async function getExamStatisticsPeople(examId: number, params: ExamStatisticsQuery & { pageNum: number; pageSize: number }): Promise<{ list: ExamStatisticsPerson[]; total: number }> {
  const result = await request<ApiResponse<{ list: ExamStatisticsPerson[]; total: number }>>({ url: `/api/exam/exams/${examId}/statistics/people`, params });
  return result.data;
}
/** 下载同范围真实 XLSX，数组使用重复参数。 */
export async function exportExamStatistics(examId: number, query: ExamStatisticsQuery, candidateIds?: number[]): Promise<Blob> {
  return request<Blob>({ url: `/api/exam/exams/${examId}/statistics/export`, params: { ...query, candidateIds }, paramsSerializer: { indexes: null }, responseType: 'blob' });
}
