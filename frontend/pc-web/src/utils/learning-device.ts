import { LEARNING_DEVICE_ID_KEY, generateDeviceId } from '@shared/utils/device-id';

/** 读取或生成本浏览器持久化的终端标识，随学习会话上报用于互斥检测。 */
export function getOrCreateLearningDeviceId(): string {
  let deviceId = globalThis.localStorage?.getItem(LEARNING_DEVICE_ID_KEY) ?? '';
  if (!deviceId) {
    deviceId = generateDeviceId();
    globalThis.localStorage?.setItem(LEARNING_DEVICE_ID_KEY, deviceId);
  }
  return deviceId;
}
