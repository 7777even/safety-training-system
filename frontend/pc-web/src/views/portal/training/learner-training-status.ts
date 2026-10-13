/**
 * 选择学员培训内容卡片的展示状态。
 * 线下内容已形成个人完成结果时优先展示该结果；未完成时才展示教师推进的场次状态。
 */
export function getLearnerContentDisplayStatus(
  contentType: string,
  resultStatus?: string | null,
  contentStatus?: string | null,
  sessionStatus?: string | null
): string {
  if (contentType === 'OFFLINE' && ['COMPLETED', 'PASSED', 'FAILED'].includes(resultStatus ?? '')) {
    return resultStatus ?? '';
  }
  if (contentType === 'OFFLINE' && sessionStatus) {
    return sessionStatus;
  }
  return resultStatus ?? contentStatus ?? '';
}
