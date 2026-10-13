import type { UserInfo } from '@/types';

/** 判断当前用户的按钮权限集合是否包含指定权限标识。 */
function hasMenuPermission(perms: string[] | undefined, permission: string): boolean {
  if (!perms || perms.length === 0) {
    return false;
  } else if (perms && perms.includes('*')) {
    return true;
  }
  return perms.includes(permission);
}

/** 按钮展示权限仅由 profile 接口返回的 perms 数组决定。 */
export function hasPermission(userInfo: UserInfo | null, permission: string): boolean {
  return hasMenuPermission(userInfo?.perms, permission);
}
