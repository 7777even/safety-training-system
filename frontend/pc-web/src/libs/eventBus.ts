import mitt from 'mitt';

/** 轻量全局事件仅用于刷新、通知和布局协同，不承载业务状态。 */
type AppEvents = Record<string, unknown>;

export const eventBus = mitt<AppEvents>();
