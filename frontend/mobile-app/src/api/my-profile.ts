import type { ApiResponse } from '@shared/types';
import type { MyProfile, MyProfileUpdate, MyPasswordUpdate } from '@shared/types/my-profile';
import { request } from './request';

/** 本人接口直接调用真实服务，不复用管理用户编辑或脱敏响应。 */
async function call<T>(url: string, method: 'GET' | 'PUT', data?: MyProfileUpdate | MyPasswordUpdate): Promise<T> {
  const result = await request<ApiResponse<T>, MyProfileUpdate | MyPasswordUpdate>({ url, method, data });
  if (result.code !== 0) throw new Error(result.message || '操作失败');
  return result.data;
}
/** 读取本人真实资料。 */
export const getMyProfile = (): Promise<MyProfile> => call('/api/system/users/my-profile', 'GET');
/** 仅更新三项允许字段及版本。 */
export const updateMyProfile = (data: MyProfileUpdate): Promise<void> => call('/api/system/users/my-profile', 'PUT', data);
/** 旧密码校验后改密，成功时服务端撤销全部已有会话。 */
export const updateMyPassword = (data: MyPasswordUpdate): Promise<void> => call('/api/system/users/my-password', 'PUT', data);
