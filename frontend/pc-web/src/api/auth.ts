import type { ApiResponse, AuthenticatedUser, LocalLoginRequest, LoginResult } from '@shared/types';
import { request } from './request';

/** 解包系统管理 API 的统一响应，并把业务失败转换为页面可展示错误。 */
function unwrapResponse<TData>(response: ApiResponse<TData>): TData {
  if (response.code !== 0) {
    throw new Error(response.message || '请求失败，请稍后重试。');
  }

  return response.data;
}

/** 使用 local 渠道提交已摘要的账号密码，返回登录令牌。 */
export async function login(orgCode: string, userCode: string, password: string): Promise<string> {
  const data = await request<ApiResponse<LoginResult>>({
    url: '/api/auth/login',
    method: 'POST',
    data: {
      identityType: 'local',
      authParams: { orgCode, userCode, password }
    } satisfies LocalLoginRequest
  });

  return unwrapResponse(data).token;
}

/** 获取当前令牌对应的用户资料与展示权限。 */
export async function getCurrentUser(): Promise<AuthenticatedUser> {
  const data = await request<ApiResponse<AuthenticatedUser>>({
    url: '/api/system/users/profile',
    method: 'GET'
  });

  return unwrapResponse(data);
}

/** 使当前登录令牌失效。 */
export async function logout(): Promise<void> {
  const data = await request<ApiResponse<null>>({
    url: '/api/auth/logout',
    method: 'POST'
  });

  unwrapResponse(data);
}
