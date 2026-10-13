import type {UserInfo} from '@/types';
import {hasPermission} from './permission';

/** 只有拥有租户管理权限的 superAdmin 才允许跨租户选择组织主数据。 */
export function canManageTenantScope(userInfo: UserInfo | null): boolean {
  return hasPermission(userInfo, 'system:tenant:list');
}

/** 普通企业用户的组织主数据由登录会话提供，避免再次手工选择租户。 */
export function currentTenantId(userInfo: UserInfo | null): number | undefined {
  return userInfo?.organizationId;
}

/**
 * 会话租户的展示名。
 * <p>
 * 无跨租户权限时「所属租户/所属机构」只能回显当前租户，此处优先用登录会话回填的
 * {@code orgName}，避免把机构主键直接渲染给用户；名称缺失时回退主键，不出现空白。
 */
export function currentTenantLabel(userInfo: UserInfo | null): string {
  if (userInfo?.orgName) {
    return userInfo.orgName;
  }
  return userInfo?.organizationId === undefined ? '-' : String(userInfo.organizationId);
}
