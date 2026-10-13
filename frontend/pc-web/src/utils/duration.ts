/**
 * 将接口中的秒数转换为管理员易读的时、分、秒文本；零值是有效配置，不能按未设置处理。
 */
export function formatDuration(seconds?: number | null, emptyText = '未设置'): string {
  if (seconds === undefined || seconds === null) return emptyText;
  const normalizedSeconds = Math.max(0, Math.floor(seconds));
  const hours = Math.floor(normalizedSeconds / 3600);
  const minutes = Math.floor((normalizedSeconds % 3600) / 60);
  const restSeconds = normalizedSeconds % 60;
  const parts: string[] = [];
  if (hours) parts.push(`${hours} 小时`);
  if (minutes || hours) parts.push(`${minutes} 分`);
  parts.push(`${restSeconds} 秒`);
  return parts.join(' ');
}
