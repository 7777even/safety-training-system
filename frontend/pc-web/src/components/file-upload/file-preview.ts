export type FilePreviewKind = 'image' | 'audio' | 'video' | 'pdf' | 'text' | 'unsupported';

export interface FilePreviewSource {
  fileName: string;
  mimeType?: string | null;
}

/** 根据浏览器原生能力判断文件预览类型，不把不可预览格式伪装为可预览。 */
export function resolveFilePreviewKind(source: FilePreviewSource): FilePreviewKind {
  const mimeType = source.mimeType?.toLowerCase() ?? '';
  const suffix = source.fileName.split('.').pop()?.toLowerCase() ?? '';

  if (mimeType.startsWith('image/') || ['png', 'jpg', 'jpeg', 'gif', 'webp', 'bmp'].includes(suffix)) return 'image';
  if (mimeType.startsWith('audio/') || ['mp3', 'wav', 'ogg', 'm4a', 'aac'].includes(suffix)) return 'audio';
  if (mimeType.startsWith('video/') || ['mp4', 'webm', 'mov', 'm4v'].includes(suffix)) return 'video';
  if (mimeType === 'application/pdf' || suffix === 'pdf') return 'pdf';
  if (mimeType.startsWith('text/') || ['txt', 'md', 'csv', 'json', 'log'].includes(suffix)) return 'text';
  return 'unsupported';
}
