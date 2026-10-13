/** 课程附件展示口径：文件大小与不可下载原因的跨端统一格式化。 */

/**
 * 将附件字节数转换为便于阅读的文件大小。
 * 契约中 fileSize 为可空字节数，未提供时返回占位文案，不直接暴露原始数值。
 */
export function formatAttachmentSize(value: number | null | undefined): string {
  if (value === null || value === undefined) return '未知大小';
  if (value < 1024) return `${value} B`;
  if (value < 1024 * 1024) return `${(value / 1024).toFixed(1)} KB`;
  if (value < 1024 * 1024 * 1024) return `${(value / 1024 / 1024).toFixed(1)} MB`;
  return `${(value / 1024 / 1024 / 1024).toFixed(1)} GB`;
}

/**
 * 将附件不可下载的契约原因码转换为学员可理解的中文说明。
 * 原因码枚举见 docs/api/course.openapi.json 的 LearnerCourseDownloadAsset.unavailableReason。
 */
export function getDownloadUnavailableReasonText(reason: string | null | undefined): string {
  const reasonTexts: Record<string, string> = {
    SOURCE_DOWNLOAD_DISABLED: '课程未开放源文件下载',
    ASSET_DISABLED: '附件已停用，暂不可下载',
    ASSET_PROCESSING: '附件正在处理中，暂不可下载',
    FILE_NOT_FOUND: '附件文件不存在，暂不可下载',
    UNSUPPORTED_ASSET_TYPE: '该附件类型暂不支持下载',
    STORAGE_UNAVAILABLE: '附件存储暂不可用，请稍后重试'
  };
  return (reason && reasonTexts[reason]) || '暂不可下载';
}
