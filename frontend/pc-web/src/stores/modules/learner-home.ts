import { defineStore } from 'pinia';
import type { LearnerHomeTask } from '@shared/types';

/**
 * 学习首页列表的会话内快照。
 * <p>
 * 首页卡片跳到任务详情、课程/考试子页后返回时，页面上会出现一次「重新请求 + 加载遮罩」，
 * 学员看到的是列表刷新而不是回到原位。这里把最近一次成功读取的筛选、页码与列表留在会话内，
 * 返回首页时先按原样铺回去，不再重复请求，也不再闪加载态。
 * <p>
 * 快照只在同一个账号内复用：换账号登录（企业编码/工号不同）会直接丢弃，避免串数据。
 */
export const useLearnerHomeStore = defineStore('learnerHome', {
  state: () => ({
    /** 快照所属账号（userCode），不匹配时一律重新读取。 */
    owner: '',
    /** 快照对应的筛选与页码，用于原样还原页签与分页。 */
    kind: 'ALL',
    page: 1,
    rows: [] as LearnerHomeTask[],
    total: 0,
    /** 快照时间戳，仅用于诊断与后续 TTL 策略，不参与复用判定。 */
    loadedAt: 0
  }),
  actions: {
    /** 记录一次成功读取的结果（含在线消息触发的静默补读）。 */
    snapshot(payload: { owner: string; kind: string; page: number; rows: LearnerHomeTask[]; total: number }): void {
      this.owner = payload.owner;
      this.kind = payload.kind;
      this.page = payload.page;
      this.rows = payload.rows;
      this.total = payload.total;
      this.loadedAt = Date.now();
    },
    /** 是否存在可复用的快照；只有同账号且已成功读过一次才算命中。 */
    reusableBy(owner: string): boolean {
      return Boolean(owner) && this.owner === owner && this.loadedAt > 0;
    }
  }
});
