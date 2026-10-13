/** 课程学习「学时说明」的跨端统一口径：有效学习时长格式化与说明文案。 */

/**
 * 将有效学习秒数格式化为便于阅读的中文时长。
 * 未提供或非正数时返回 `0 秒`，不暴露原始数值或负数。
 */
export function formatStudyDuration(seconds: number | null | undefined): string {
  const total = Math.max(0, Math.floor(seconds ?? 0));
  if (total < 60) return `${total} 秒`;
  const hours = Math.floor(total / 3600);
  const minutes = Math.floor((total % 3600) / 60);
  if (!hours) return `${minutes} 分钟`;
  return minutes ? `${hours} 小时 ${minutes} 分钟` : `${hours} 小时`;
}

/**
 * 生成「显示学时说明」开关开启后展示的说明文案。
 * 学员学习页只读展示，不参与学时计算；显式传入本项已记录时长时附带当前进度。
 */
export function getStudyDescriptionText(studiedSeconds?: number | null): string {
  const base = '学时以系统记录的有效学习时长为准：仅在页面可见且素材实际播放/停留时计时，切换页面、暂停播放或后台运行的时间不计入。';
  if (studiedSeconds === null || studiedSeconds === undefined) return base;
  return `${base}本项已记录有效学习时长 ${formatStudyDuration(studiedSeconds)}。`;
}
