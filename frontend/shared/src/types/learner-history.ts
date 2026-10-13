/** 统一档案来源，来自learner/archive OpenAPI。 */
export type HistoryRecordType = 'COURSE' | 'FORMAL_EXAM' | 'COURSE_QUIZ' | 'PRACTICE' | 'SIMULATION' | 'TRAINING';
/** 原始或更正后的已有摘要，未公开成绩仍为null。 */
export interface HistorySummary {
  userName: string | null; userCode: string | null; occurredTime: string | null;
  status: string; score: number | null; hours: number | null; scoreVisible: boolean;
}
/** 现有业务入口引用，由各端映射，不接受任意URL。 */
export interface HistoryReference {
  kind: HistoryRecordType; resourceId: number; parentId: number | null;
  itemId: number | null; attemptId: number | null; action: string | null;
}
/** 本次更正字段在前一展示版本的前后值，不可覆盖。 */
export interface HistoryCorrection {
  correctionId: number; version: number; before: Partial<HistoryChanges>; after: Partial<HistoryChanges>;
  reason: string; operatorId: number; operatorName: string | null; operatedTime: string;
}
/** 更正白名单，字段适用性与领域类型仍由服务端校验。 */
export type HistoryChanges = Pick<HistorySummary, 'userName' | 'userCode' | 'occurredTime' | 'status' | 'score' | 'hours'>;
/** 两个摘要分开，档案更正不得冒充正式计算结果。 */
export interface LearnerHistory {
  recordType: HistoryRecordType; recordId: number; title: string;
  original: HistorySummary; current: HistorySummary; version: number;
  reference: HistoryReference; corrections: HistoryCorrection[];
}
/** 只追加更正，必须带当前展示版本和非空理由。 */
export interface HistoryCorrectionRequest { version: number; changes: Partial<HistoryChanges>; reason: string }
