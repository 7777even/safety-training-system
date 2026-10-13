import { LEARNING_DEVICE_ID_KEY, generateDeviceId } from '@shared/utils/device-id';

/** 读取或生成本设备持久化的终端标识，随学习会话上报用于互斥检测；H5 与 App 均由 uni storage 承载。 */
export function getOrCreateLearningDeviceId(): string {
  let deviceId = uni.getStorageSync(LEARNING_DEVICE_ID_KEY) as string;
  if (!deviceId) {
    deviceId = generateDeviceId();
    uni.setStorageSync(LEARNING_DEVICE_ID_KEY, deviceId);
  }
  return deviceId;
}
