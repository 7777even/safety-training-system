/** 判断作答是否已发布正式成绩，发布后评分事实不可修改。 */
export function isScorePublished(scorePublishStatus: 'PUBLISHED' | 'UNPUBLISHED' | 'EFFECTIVE' | undefined): boolean {
  return scorePublishStatus === 'PUBLISHED' || scorePublishStatus === 'EFFECTIVE';
}
