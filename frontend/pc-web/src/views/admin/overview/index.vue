<script setup lang="ts">
/** 静态后台概览仅承载布局，不请求或模拟课程、考试等业务数据。 */
const overviewCards = [
  { label: '培训覆盖率', description: '等待培训数据接入' },
  { label: '学习完成率', description: '等待学习数据接入' },
  { label: '待处理考试', description: '等待考试数据接入' },
  { label: '临期证书', description: '等待证书数据接入' }
];
</script>

<template>
  <section class="admin-overview">
    <section class="overview-cards">
      <ElCard v-for="item in overviewCards" :key="item.label" shadow="never">
        <p>{{ item.label }}</p>
        <strong>—</strong>
        <span>{{ item.description }}</span>
      </ElCard>
    </section>
    <section class="overview-grid">
      <ElCard shadow="never">
        <template #header>
          <span>培训进度趋势</span>
        </template>
        <div class="chart-placeholder">
          <span>数据趋势将在后续培训模块接入</span>
        </div>
      </ElCard>
      <ElCard shadow="never">
        <template #header>
          <span>考试通过分布</span>
        </template>
        <div class="chart-placeholder">
          <span>考试数据将在后续考试模块接入</span>
        </div>
      </ElCard>
    </section>
    <section class="overview-grid">
      <ElCard shadow="never">
        <template #header>
          <span>待办事项</span>
        </template>
        <ElEmpty description="暂无待办数据" />
      </ElCard>
      <ElCard shadow="never">
        <template #header>
          <span>培训计划</span>
        </template>
        <ElEmpty description="暂无计划数据" />
      </ElCard>
    </section>
  </section>
</template>

<style scoped lang="less">
.overview-cards {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: 16px;
}
.overview-cards :deep(.el-card__body) {
  display: grid;
  min-height: 116px;
  align-content: space-between;
}
.overview-cards p,
.overview-cards span {
  margin: 0;
  color: var(--color-text-secondary);
  font-size: 13px;
}
.overview-cards strong {
  color: var(--color-text-primary);
  font-size: 30px;
}
.overview-grid {
  display: grid;
  margin-top: 24px;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 24px;
}
.overview-grid :deep(.el-card) {
  min-height: 250px;
}
.overview-grid :deep(.el-card__header) {
  color: var(--color-text-primary);
  font-size: 16px;
  font-weight: 600;
}
.chart-placeholder {
  display: grid;
  min-height: 150px;
  place-items: center;
  border-radius: var(--radius-control);
  background: var(--color-bg-subtle);
  color: var(--color-text-secondary);
  font-size: 14px;
}
@media (max-width: 1100px) {
  .overview-cards {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }
}
@media (max-width: 760px) {
  .overview-grid {
    grid-template-columns: 1fr;
  }
}
</style>
