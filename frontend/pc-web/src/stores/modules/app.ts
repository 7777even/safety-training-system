import { defineStore } from 'pinia';

/** 应用级 UI 状态；不存放具体业务模块数据。 */
export const useAppStore = defineStore('app', {
  state: () => ({
    isInitialized: false
  }),
  actions: {
    markInitialized(): void {
      this.isInitialized = true;
    }
  }
});
