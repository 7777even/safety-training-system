import { defineStore } from 'pinia';

/** 前端仅维护可展示入口；后端仍负责真实资源鉴权。 */
export const usePermissionStore = defineStore('permission', {
  state: () => ({
    visibleRouteNames: [] as string[]
  }),
  actions: {
    setVisibleRouteNames(routeNames: string[]): void {
      this.visibleRouteNames = routeNames;
    }
  }
});
