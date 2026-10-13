import { defineStore } from 'pinia';

/** 在线变化只驱动刷新，不缓存消息正文或添加未读计数。 */
export const useLearnerMessageStore = defineStore('learnerMessages', {
  state: () => ({ revision: 0, total: 0, error: '' }),
  actions: {
    /** 通知当前消息页重新读取真实分页。 */
    changed(total: number): void { this.total = total; this.revision++; this.error = ''; }
  }
});
