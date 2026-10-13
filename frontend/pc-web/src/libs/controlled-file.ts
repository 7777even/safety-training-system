/**
 * 受控文件接口只返回字节与 MIME，而查看器插件按文件名后缀选择渲染方式，
 * 因此需要按 MIME 把后缀还原出来。文件名仅用于本地预览与另存，不代表服务端真实文件名。
 */
const MIME_SUFFIXES: Record<string, string> = {
  'application/pdf': 'pdf',
  'image/png': 'png',
  'image/jpeg': 'jpg',
  'image/webp': 'webp',
  'image/gif': 'gif',
  'image/bmp': 'bmp',
  'text/plain': 'txt',
  'text/csv': 'csv',
  'application/json': 'json',
  'video/mp4': 'mp4',
  'video/webm': 'webm',
  'video/quicktime': 'mov',
  'audio/mpeg': 'mp3',
  'audio/wav': 'wav',
  'audio/ogg': 'ogg',
  'application/msword': 'doc',
  'application/vnd.openxmlformats-officedocument.wordprocessingml.document': 'docx',
  'application/vnd.ms-excel': 'xls',
  'application/vnd.openxmlformats-officedocument.spreadsheetml.sheet': 'xlsx',
  'application/vnd.ms-powerpoint': 'ppt',
  'application/vnd.openxmlformats-officedocument.presentationml.presentation': 'pptx'
};

/** MIME 未知时回退 bin，避免伪造出误导性的后缀。 */
export function suffixFromMime(mime: string | null | undefined): string {
  if (!mime) return 'bin';
  return MIME_SUFFIXES[mime.toLowerCase()] ?? 'bin';
}

/** 用业务名称加真实后缀组装本地文件名，仅用于预览与下载。 */
export function controlledFileName(label: string, mime: string | null | undefined): string {
  return `${label}.${suffixFromMime(mime)}`;
}

/** 触发浏览器另存受控文件字节，避免各页面各写一份 object URL 回收逻辑。 */
export function saveBlobAsFile(blob: Blob, fileName: string): void {
  const url = globalThis.URL.createObjectURL(blob);
  const link = globalThis.document.createElement('a');
  link.href = url;
  link.download = fileName;
  link.click();
  globalThis.URL.revokeObjectURL(url);
}
