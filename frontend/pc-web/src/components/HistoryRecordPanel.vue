<script setup lang="ts">
import type { LearnerHistory, HistorySummary } from '@shared/types/learner-history';
import { HISTORY_FIELDS, historyStatus } from '@shared/utils/learner-history';
/** 两种摘要明确并列，历史轨迹只读，不提供学员修改入口。 */
defineProps<{ record: LearnerHistory }>();
/** 未公开与不适用数值分别说明，避免把空值显示成0分。 */
function value(summary: HistorySummary, field: keyof typeof HISTORY_FIELDS): string {
  if (field === 'score' && !summary.scoreVisible) return '未公开或不评分';
  if (field === 'status') return historyStatus(summary.status);
  return summary[field] == null ? '不适用/无记录' : String(summary[field]);
}
/** 留痕字段使用中文名称，保留前后值与版本。 */
function changes(data: Record<string, unknown>): string {
  return Object.entries(data).map(([key, v]) => `${HISTORY_FIELDS[key as keyof typeof HISTORY_FIELDS]}：${key === 'status' && typeof v === 'string' ? historyStatus(v) : v ?? '空'}`).join('；') || '无可公开字段';
}
</script>
<template>
  <section class="history-record">
    <ElAlert title="档案更正仅改变展示，不改变原始业务结果、答卷、归档文件或证书。" type="info" :closable="false" />
    <div class="history-record__version">
      <span class="ws-hint">当前档案展示版本</span>
      <strong>v{{ record.version }}</strong>
    </div>
    <ElTable :data="Object.entries(HISTORY_FIELDS).map(([field, label]) => ({ field, label }))">
      <ElTableColumn prop="label" label="摘要字段" width="130" />
      <ElTableColumn label="原始记录"><template #default="{ row }">{{ value(record.original, row.field) }}</template></ElTableColumn>
      <ElTableColumn label="档案更正展示"><template #default="{ row }">{{ value(record.current, row.field) }}</template></ElTableColumn>
    </ElTable>
    <div class="ws-section" style="margin:10px 0">
      <div class="ws-section__head">
        <h3>更正轨迹</h3>
        <span class="ws-section__extra">共 {{ record.corrections.length }} 条</span>
      </div>
      <ElTable :data="record.corrections" empty-text="暂无更正记录">
        <ElTableColumn prop="version" label="版本" width="70" />
        <ElTableColumn label="前值"><template #default="{ row }">{{ changes(row.before) }}</template></ElTableColumn>
        <ElTableColumn label="后值"><template #default="{ row }">{{ changes(row.after) }}</template></ElTableColumn>
        <ElTableColumn prop="reason" label="理由" min-width="160" />
        <ElTableColumn prop="operatorName" label="操作人" width="120" />
        <ElTableColumn prop="operatedTime" label="操作时间" width="170" />
      </ElTable>
    </div>
  </section>
</template>
<style scoped lang="less">
.history-record__version {
  display: flex;
  margin: 12px 0;
  align-items: baseline;
  gap: var(--space-2);
}

.history-record__version strong {
  color: var(--color-primary);
  font-size: 16px;
  font-variant-numeric: tabular-nums;
}
</style>
