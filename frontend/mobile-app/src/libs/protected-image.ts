import { request } from '@/api/request';

/**
 * 读取受鉴权图片为页面可用地址。
 *
 * H5 的 image 标签不会自动携带 Bearer，因此改由统一请求加载为 Blob URL；其他端保留受控地址，交由原生图片组件处理。
 */
export async function loadProtectedImageUrl(url: string): Promise<string> {
  // #ifdef H5
  const binary = await request<ArrayBuffer>({ url, method: 'GET', responseType: 'arraybuffer' });
  return globalThis.URL.createObjectURL(new globalThis.Blob([binary], { type: 'image/*' }));
  // #endif
  // #ifndef H5
  return url;
  // #endif
}

/** 释放 H5 Blob URL，避免列表反复进入时保留已失效封面。 */
export function revokeProtectedImageUrl(url: string | null): void {
  // #ifdef H5
  if (url?.startsWith('blob:')) globalThis.URL.revokeObjectURL(url);
  // #endif
}
