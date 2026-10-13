import { defineStore } from 'pinia';

/** 在线变化只用于消息页面刷新，不缓存消息正文或引入已读状态。 */
export const useLearnerMessageStore = defineStore('learnerMessages', {
  state: () => ({ revision: 0, total: 0, error: '' }),
  actions: {
    /** 本人消息数由真实接口返回。 */
    changed(total: number): void { this.total = total; this.revision++; this.error = ''; }
  }
});
