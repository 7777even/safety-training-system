import { defineStore } from 'pinia';
import { sha256 } from '@shared/libs/sha256';
import { getCurrentUser, login, logout as requestLogout } from '@/api/auth';
import { clearTargetTenantId } from '@/api/request';
import type { UserInfo } from '@/types';

/** 持久化会话槽位的存储键；槽位内 Token 只用于当前系统地址。 */
export const SESSION_SLOTS_STORAGE_KEY = 'safety-training-session-slots';
const TAB_ID_PREFIX = 'safety-training-tab:';
const SESSION_TTL_MS = 30 * 24 * 60 * 60 * 1000;
const SESSION_CLAIM_TTL_MS = 5 * 60 * 1000;

/** 一个可被浏览器重启后恢复的登录会话槽位。 */
export interface SessionSlot {
  slotId: string;
  token: string;
  userInfo: UserInfo;
  lastUsedAt: number;
  expiresAt: number;
  boundTabId?: string;
}

/** 生成不包含账户信息的随机标识。 */
function createId(prefix: string): string {
  const random = globalThis.crypto?.randomUUID?.() ?? `${Date.now()}-${Math.random().toString(36).slice(2)}`;
  return `${prefix}${random}`;
}

/** 为当前浏览器标签页取得稳定绑定标识；不保存 Token 或账户信息。 */
function getCurrentTabId(): string | undefined {
  if (typeof window === 'undefined') {
    return undefined;
  }

  if (!window.name.startsWith(TAB_ID_PREFIX)) {
    window.name = createId(TAB_ID_PREFIX);
  }

  return window.name;
}

/** 读取并清理过期的持久化会话槽位。 */
function readSessionSlots(): SessionSlot[] {
  if (typeof window === 'undefined') {
    return [];
  }

  try {
    const value = JSON.parse(window.localStorage.getItem(SESSION_SLOTS_STORAGE_KEY) ?? '[]');
    if (!Array.isArray(value)) {
      return [];
    }

    const now = Date.now();
    const validSlots = value.filter((slot): slot is SessionSlot =>
      typeof slot?.slotId === 'string' &&
      typeof slot?.token === 'string' &&
      typeof slot?.lastUsedAt === 'number' &&
      typeof slot?.expiresAt === 'number' &&
      slot.expiresAt > now &&
      slot.userInfo !== null &&
      typeof slot.userInfo === 'object'
    );

    if (validSlots.length !== value.length) {
      writeSessionSlots(validSlots);
    }

    return validSlots;
  } catch {
    return [];
  }
}

/** 保存全部会话槽位。 */
function writeSessionSlots(slots: SessionSlot[]): void {
  if (typeof window !== 'undefined') {
    window.localStorage.setItem(SESSION_SLOTS_STORAGE_KEY, JSON.stringify(slots));
  }
}

/** 更新当前标签页绑定的会话槽位。 */
function updateSessionSlot(slotId: string, token: string, userInfo: UserInfo): void {
  const now = Date.now();
  const slots = readSessionSlots();
  const currentTabId = getCurrentTabId();
  const index = slots.findIndex((slot) => slot.slotId === slotId);
  const slot: SessionSlot = {
    slotId,
    token,
    userInfo,
    lastUsedAt: now,
    expiresAt: now + SESSION_TTL_MS,
    boundTabId: currentTabId
  };

  if (index >= 0) {
    slots[index] = slot;
  } else {
    slots.push(slot);
  }
  writeSessionSlots(slots);
}

/** 解除当前标签页与会话槽位的绑定，但保留槽位供浏览器重启后恢复。 */
function unbindCurrentTab(slotId: string | undefined): void {
  if (!slotId) {
    return;
  }

  const slots = readSessionSlots().map((slot) =>
    slot.slotId === slotId ? { ...slot, boundTabId: undefined, lastUsedAt: Date.now() } : slot
  );
  writeSessionSlots(slots);
}

/** 删除一个持久化会话槽位。 */
function removeStoredSlot(slotId: string): void {
  writeSessionSlots(readSessionSlots().filter((slot) => slot.slotId !== slotId));
}

/** 从当前标签页绑定的槽位恢复初始令牌。 */
function readBoundSession(): { slotId: string; slot: SessionSlot } | undefined {
  const tabId = getCurrentTabId();
  if (!tabId) {
    return undefined;
  }

  const slot = readSessionSlots().find((item) => item.boundTabId === tabId);
  return slot ? { slotId: slot.slotId, slot } : undefined;
}

/** 定期刷新当前标签页的占用时间，避免活跃标签页被其他标签页静默接管。 */
function touchCurrentSessionClaim(): void {
  const tabId = getCurrentTabId();
  if (!tabId) {
    return;
  }

  const now = Date.now();
  const slots = readSessionSlots();
  let changed = false;
  const updatedSlots = slots.map((slot) => {
    if (slot.boundTabId !== tabId) {
      return slot;
    }

    changed = true;
    return { ...slot, lastUsedAt: now, expiresAt: now + SESSION_TTL_MS };
  });

  if (changed) {
    writeSessionSlots(updatedSlots);
  }
}

const boundSession = readBoundSession();

if (typeof window !== 'undefined') {
  window.setInterval(touchCurrentSessionClaim, 60 * 1000);
}

export const useUserStore = defineStore('user', {
  state: () => ({
    token: boundSession?.slot.token,
    userInfo: boundSession?.slot.userInfo ?? null,
    roles: boundSession?.slot.userInfo.roles ?? [],
    slotId: boundSession?.slotId
  }),
  actions: {
    /** 写入当前页面会话，并同步更新当前绑定的持久化槽位。 */
    setSession(token: string | undefined, userInfo: UserInfo | null, roles: string[], slotId?: string): void {
      const targetSlotId = slotId ?? this.slotId;
      this.token = token;
      this.userInfo = userInfo;
      this.roles = roles;
      this.slotId = targetSlotId;

      if (token && userInfo && targetSlotId) {
        updateSessionSlot(targetSlotId, token, userInfo);
      }
    },
    /** 列出尚未过期的持久化账户槽位，供登录页恢复。 */
    listSessionSlots(): SessionSlot[] {
      return readSessionSlots();
    },
    /** 创建当前标签页专属槽位并绑定。 */
    createSessionSlot(): string {
      const slotId = createId('slot:');
      this.slotId = slotId;
      return slotId;
    },
    /** 使用指定槽位恢复账户，并通过服务端资料接口验证 Token 仍有效。 */
    async restoreSessionSlot(slotId: string): Promise<void> {
      const slot = readSessionSlots().find((item) => item.slotId === slotId);
      if (!slot) {
        throw new Error('保存的登录会话不存在或已过期');
      }

      const currentTabId = getCurrentTabId();
      if (
        slot.boundTabId &&
        slot.boundTabId !== currentTabId &&
        Date.now() - slot.lastUsedAt < SESSION_CLAIM_TTL_MS
      ) {
        throw new Error('该登录会话正在其他标签页使用，请选择其他账户');
      }

      this.slotId = slotId;
      await this.initializeSession(slot.token, slotId);
    },
    /** 清理当前页面状态；可选地删除当前持久化槽位。 */
    clearSession(removeSlot = false): void {
      const slotId = this.slotId;
      this.token = undefined;
      this.userInfo = null;
      this.roles = [];
      this.slotId = undefined;
      clearTargetTenantId();

      if (removeSlot && slotId) {
        removeStoredSlot(slotId);
      } else {
        unbindCurrentTab(slotId);
      }
    },
    /** 使用令牌加载当前用户资料，完成当前标签页会话初始化。 */
    async initializeSession(token: string, slotId?: string): Promise<void> {
      this.token = token;
      const targetSlotId = slotId ?? this.slotId;
      this.slotId = targetSlotId;

      try {
        const userInfo = await getCurrentUser();
        this.setSession(token, userInfo, userInfo.roles ?? [], targetSlotId);
      } catch (error) {
        this.clearSession(Boolean(targetSlotId));
        throw error;
      }
    },
    /** 账号密码登录：新登录建立独立槽位，不覆盖其他标签页账户。 */
    async loginWithPassword(orgCode: string, userCode: string, password: string): Promise<void> {
      const token = await login(orgCode, userCode, await sha256(password));
      const slotId = this.createSessionSlot();
      await this.initializeSession(token, slotId);
    },
    /** 浏览器刷新后验证当前标签页已绑定的持久化会话。 */
    async restoreSession(): Promise<void> {
      if (!this.token || !this.slotId) {
        return;
      }

      try {
        await this.initializeSession(this.token, this.slotId);
      } catch {
        this.clearSession(true);
      }
    },
    /** 退出服务端会话并删除当前账户槽位，其他标签页槽位不受影响。 */
    async logout(): Promise<void> {
      try {
        if (this.token) {
          await requestLogout();
        }
      } finally {
        this.clearSession(true);
      }
    },
    /** 仅移除指定的已保存账户槽位。 */
    removeSessionSlot(slotId: string): void {
      removeStoredSlot(slotId);
    }
  }
});
