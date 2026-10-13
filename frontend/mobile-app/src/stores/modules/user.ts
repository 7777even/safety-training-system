import { defineStore } from 'pinia';
import { AUTH_TOKEN_STORAGE_KEY } from '@shared/constants';
import { sha256 } from '@shared/libs/sha256';
import { getCurrentUser, login, logout as requestLogout } from '@/api/auth';
import type { UserInfo } from '@/types';

/** 读取 uni-app 本地存储的会话令牌。 */
function readStoredToken(): string | undefined {
  const token = uni.getStorageSync(AUTH_TOKEN_STORAGE_KEY);

  return typeof token === 'string' && token ? token : undefined;
}

/** 保存移动端会话令牌，供应用恢复时重新加载资料。 */
function storeToken(token: string): void {
  uni.setStorageSync(AUTH_TOKEN_STORAGE_KEY, token);
}

/** 清理移动端会话令牌。 */
function removeStoredToken(): void {
  uni.removeStorageSync(AUTH_TOKEN_STORAGE_KEY);
}

/** 移动端当前用户会话：只保存 Contract 定义的令牌、资料和角色。 */
export const useUserStore = defineStore('user', {
  state: () => ({
    token: readStoredToken(),
    userInfo: null as UserInfo | null,
    roles: [] as string[]
  }),
  actions: {
    setSession(token: string | undefined, userInfo: UserInfo | null, roles: string[]): void {
      this.token = token;
      this.userInfo = userInfo;
      this.roles = roles;

      if (token) {
        storeToken(token);
      } else {
        removeStoredToken();
      }
    },
    clearSession(): void {
      this.token = undefined;
      this.userInfo = null;
      this.roles = [];
      removeStoredToken();
    },
    /** 使用已拿到的令牌加载当前用户资料，完成登录态初始化。 */
    async initializeSession(token: string): Promise<void> {
      // 资料接口受 Bearer Token 保护，必须先让请求封装读取到新令牌。
      this.setSession(token, null, []);

      try {
        const userInfo = await getCurrentUser();
        this.setSession(token, userInfo, userInfo.roles ?? []);
      } catch (error) {
        this.clearSession();
        throw error;
      }
    },
    /** 账号密码登录：仅传输 SHA-256 摘要，不发送明文密码。 */
    async loginWithPassword(orgCode: string, userCode: string, password: string): Promise<void> {
      const token = await login(orgCode, userCode, await sha256(password));
      await this.initializeSession(token);
    },
    /** 应用恢复时尝试恢复现有会话；资料失效时清理本地令牌。 */
    async restoreSession(): Promise<void> {
      const token = this.token;

      if (!token) {
        return;
      }

      try {
        await this.initializeSession(token);
      } catch {
        this.clearSession();
      }
    },
    /** 退出服务端会话，并始终清理本地登录状态。 */
    async logout(): Promise<void> {
      try {
        if (this.token) {
          await requestLogout();
        }
      } finally {
        this.clearSession();
      }
    }
  }
});
