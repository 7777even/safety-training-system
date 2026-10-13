/** 培训编排摘要只用于前端引导，最终合规判断仍以服务端发布预检为准。 */
export interface TrainingCompositionContent {
  contentType: 'COURSE' | 'EXAM' | 'QUESTION_BANK' | 'OFFLINE';
  resourceId?: number;
  requiredHours?: number;
}

export interface TrainingCompositionStage {
  requiredHours?: number;
  contents?: TrainingCompositionContent[];
}

export interface TrainingCompositionRule {
  ruleType: 'TOTAL_HOURS' | 'ONLINE_RATIO' | 'COURSE' | 'EXAM' | 'OFFLINE';
  requiredValue?: number;
  resourceId?: number;
  status: 0 | 1;
}

/** 将契约中兼容的 0-1 或百分数比例统一为 0-1，供所有编排摘要复用。 */
export function normalizeTrainingRatio(value: number | undefined): number {
  const ratio = Number(value ?? 0);
  return ratio > 1 ? ratio / 100 : ratio;
}

/** 将接口 0～1 比例转换为页面填写的百分数，兼容历史百分数值回显。 */
export function toTrainingRatioPercent(value: number | undefined): number {
  return Number((normalizeTrainingRatio(value) * 100).toFixed(1));
}

/** 将页面填写的百分数转换为接口约定的 0～1 比例值。 */
export function toTrainingRatio(percent: number | undefined): number {
  return Number((Number(percent ?? 0) / 100).toFixed(4));
}

/** 判断内容是否属于不计入安排学时的考试类内容。 */
export function isAssessmentTrainingContent(contentType: TrainingCompositionContent['contentType']): boolean {
  return contentType === 'EXAM' || contentType === 'QUESTION_BANK';
}

/** 将历史草稿中的考试类学时归零，保持与服务端保存约束一致。 */
export function normalizeAssessmentRequiredHours(stages: Array<{ contents?: TrainingCompositionContent[] }>): void {
  stages.forEach((stage) => {
    stage.contents?.forEach((content) => {
      if (isAssessmentTrainingContent(content.contentType)) content.requiredHours = 0;
    });
  });
}

/** 以课程和线下内容的安排学时汇总计划或模板；线上学时仅统计课程。 */
export function summarizeTrainingComposition(stages: TrainingCompositionStage[]): { totalHours: number; onlineHours: number; onlineRatio: number } {
  const contents = stages.flatMap((stage) => stage.contents ?? []);
  const totalHours = contents
    .filter((item) => item.contentType === 'COURSE' || item.contentType === 'OFFLINE')
    .reduce((sum, item) => sum + Number(item.requiredHours ?? 0), 0);
  const onlineHours = contents
    .filter((item) => item.contentType === 'COURSE')
    .reduce((sum, item) => sum + Number(item.requiredHours ?? 0), 0);
  return { totalHours, onlineHours, onlineRatio: totalHours ? onlineHours / totalHours : 0 };
}

/** 返回标准中各项指定资源是否已经出现在当前编排。 */
export function summarizeStandardRuleCoverage(
  stages: TrainingCompositionStage[],
  rules: TrainingCompositionRule[]
): Array<{ label: string; satisfied: boolean }> {
  const contents = stages.flatMap((stage) => stage.contents ?? []);
  return rules.filter((rule) => rule.status === 1).flatMap((rule) => {
    if (rule.ruleType === 'TOTAL_HOURS' || rule.ruleType === 'ONLINE_RATIO') return [];
    const label = rule.ruleType === 'COURSE' ? '指定课程' : rule.ruleType === 'EXAM' ? '指定考试' : '线下培训';
    const satisfied = rule.ruleType === 'OFFLINE'
      ? contents.some((item) => item.contentType === 'OFFLINE')
      : contents.some((item) => item.contentType === rule.ruleType && item.resourceId === rule.resourceId);
    return [{ label, satisfied }];
  });
}
