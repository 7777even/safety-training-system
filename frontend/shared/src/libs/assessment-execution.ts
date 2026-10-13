/** 考核执行冻结快照不可用时的后端业务码。 */
export const ASSESSMENT_EXECUTION_SNAPSHOT_ERROR_CODE = 7063;

/** 不向学员暴露快照、摘要等内部实现细节的统一提示。 */
export const ASSESSMENT_EXECUTION_SNAPSHOT_ERROR_MESSAGE = '考核配置异常，请联系管理员重新生成。';

/** 判断接口异常是否由不可执行的考核冻结快照引起。 */
export function isAssessmentExecutionSnapshotError(error: unknown): boolean {
  return typeof error === 'object'
    && error !== null
    && 'code' in error
    && (error as { code?: unknown }).code === ASSESSMENT_EXECUTION_SNAPSHOT_ERROR_CODE;
}

/** 将快照异常转换为学员可理解的提示，其他异常继续沿用服务端原文。 */
export function getAssessmentExecutionErrorMessage(error: unknown, fallback: string): string {
  if (isAssessmentExecutionSnapshotError(error)) return ASSESSMENT_EXECUTION_SNAPSHOT_ERROR_MESSAGE;
  return error instanceof Error ? error.message : fallback;
}
