import { sha256 as sha256Fallback } from 'js-sha256';

/**
 * 生成 API Contract 要求的小写十六进制 SHA-256 摘要。
 * 登录密码仅以摘要参与传输，禁止将明文提交到服务端。
 */
export async function sha256(value: string): Promise<string> {
  // 优先使用浏览器原生实现，HTTP 测试环境回退到成熟的 SHA-256 实现。
  if (globalThis.crypto?.subtle) {
    const source = new TextEncoder().encode(value);
    const digest = await globalThis.crypto.subtle.digest('SHA-256', source);

    return Array.from(new Uint8Array(digest), (byte) => byte.toString(16).padStart(2, '0')).join('');
  }

  return sha256Fallback(value);
}

/** 计算文件字节的 SHA-256，HTTP 环境下回退到 JavaScript 实现。 */
export async function sha256Bytes(value: ArrayBuffer): Promise<string> {
  if (globalThis.crypto?.subtle) {
    const digest = await globalThis.crypto.subtle.digest('SHA-256', value);
    return Array.from(new Uint8Array(digest), (byte) => byte.toString(16).padStart(2, '0')).join('');
  }

  return sha256Fallback(value);
}
