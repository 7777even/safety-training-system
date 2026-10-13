/** 将后端场次状态翻译为教师可理解的不可录分原因，不参与权限判断。 */
export function getRecordScoreUnavailableMessage(sessionStatus: string | undefined, canRecordScore: boolean): string {
  if (canRecordScore) return '';
  return (
    {
      SCHEDULED: '请先开始本场线下培训，开始后可录入参训人员成绩。',
      COMPLETED: '场次已结束，不能再录入成绩。',
      CANCELLED: '场次已取消，不能录入成绩。',
      IN_PROGRESS: '当前没有录入成绩的权限，请联系管理员。'
    }[sessionStatus ?? ''] ?? '当前场次暂不能录入成绩。'
  );
}

/** 将教师任务详情的既有访问拒绝响应转为明确的用户提示。 */
export function getTeacherTaskLoadErrorMessage(error: unknown): string {
  const status =
    typeof error === 'object' && error !== null && 'response' in error ? (error.response as { status?: number } | undefined)?.status : undefined;
  const code = typeof error === 'object' && error !== null && 'code' in error ? (error.code as number | undefined) : undefined;
  if (status === 403 || code === 8131) return '您没有查看或处理该场次的权限，请联系管理员。';
  return error instanceof Error ? error.message : '教师任务加载失败，请稍后重试。';
}
