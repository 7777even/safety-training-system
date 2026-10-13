/** 上传队列中执行状态变更所需的最小结构。 */
export interface UploadQueueState<TFile, TResult> {
  id: string;
  file: TFile;
  status: 'waiting' | 'uploading' | 'paused' | 'success' | 'failed';
  percent: number;
  previewUrl: string;
  result?: TResult;
  error?: string;
}

/** 执行单个文件上传，并将进度、成功或失败状态写回同一个响应式队列项。 */
export async function uploadQueueItem<TFile, TResult>(
  item: UploadQueueState<TFile, TResult>,
  uploadRequest: (file: TFile, onProgress: (percent: number) => void, signal: AbortSignal) => Promise<TResult>,
  signal = new globalThis.AbortController().signal
): Promise<void> {
  item.status = 'uploading';
  item.error = undefined;
  try {
    item.result = await uploadRequest(
      item.file,
      (percent) => {
        item.percent = Math.max(0, Math.min(100, Math.round(percent)));
      },
      signal
    );
    item.percent = 100;
    item.status = 'success';
  } catch (error) {
    if (signal.aborted) {
      item.status = 'paused';
      item.error = undefined;
      return;
    }
    item.status = 'failed';
    item.error = error instanceof Error ? error.message : '上传失败';
  }
}

/** 移除指定队列项，并立即释放它的本地预览地址。 */
export function removeUploadQueueItem<T extends { id: string; previewUrl: string }>(
  items: T[],
  itemId: string,
  revokeObjectUrl: (url: string) => void
): T[] {
  const target = items.find((item) => item.id === itemId);
  if (target) revokeObjectUrl(target.previewUrl);
  return items.filter((item) => item.id !== itemId);
}

/** 释放队列全部本地预览地址，用于清空队列或组件卸载。 */
export function releaseUploadQueueItems<T extends { previewUrl: string }>(items: T[], revokeObjectUrl: (url: string) => void): void {
  items.forEach((item) => revokeObjectUrl(item.previewUrl));
}
