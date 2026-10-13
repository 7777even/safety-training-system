/**
 * 终端标识的跨端统一口径：键名与随机值生成放 shared，持久化由各端按自身存储实现。
 * 标识仅用于学习互斥检测（同一终端多账户），不是安全边界。
 */

/** 终端标识在本地存储中的键；PC 与移动端必须一致。 */
export const LEARNING_DEVICE_ID_KEY = 'sts:learning:device-id';

/** 生成终端标识：优先 crypto.randomUUID，不可用时退化为随机十六进制串。 */
export function generateDeviceId(): string {
  const cryptoRef = globalThis.crypto;
  if (cryptoRef && typeof cryptoRef.randomUUID === 'function') {
    return cryptoRef.randomUUID();
  }
  const bytes = new Uint8Array(16);
  if (cryptoRef && typeof cryptoRef.getRandomValues === 'function') {
    cryptoRef.getRandomValues(bytes);
  } else {
    for (let index = 0; index < bytes.length; index += 1) {
      bytes[index] = Math.floor(Math.random() * 256);
    }
  }
  return Array.from(bytes, (byte) => byte.toString(16).padStart(2, '0')).join('');
}
