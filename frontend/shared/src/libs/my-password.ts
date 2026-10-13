import { sha256 } from './sha256';
import type { MyPasswordUpdate } from '../types/my-profile';

/** 双端在发送前校验确认密码，明文仅留在页面当前表单。 */
export async function prepareMyPassword(oldPassword: string, newPassword: string, confirmation: string): Promise<MyPasswordUpdate> {
  if (!oldPassword || !newPassword || !confirmation) throw new Error('请填写旧密码、新密码及确认密码');
  if (newPassword !== confirmation) throw new Error('两次输入的新密码不一致');
  return { oldPassword: await sha256(oldPassword), newPassword: await sha256(newPassword) };
}
