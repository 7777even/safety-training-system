/** 本人资料响应；手机号为真实编辑值，授权相关字段只读。 */
export interface MyProfile {
  userCode: string;
  userName: string;
  phone: string | null;
  email: string | null;
  organizationId: number | null;
  orgName: string | null;
  deptId: number | null;
  deptName: string | null;
  memberType: string;
  roles: string[];
  version: number;
  hasLocalCredential: boolean;
  passwordChangeAllowed: boolean;
}
/** 允许修改的资料字段，不传账号、机构、部门或角色。 */
export interface MyProfileUpdate {
  userName: string;
  phone: string | null;
  email: string | null;
  version: number;
}
/** 密码只传输SHA-256摘要，前端确认值不发送。 */
export interface MyPasswordUpdate { oldPassword: string; newPassword: string }
