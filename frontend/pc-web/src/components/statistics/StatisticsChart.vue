<script setup lang="ts">
import { computed, ref } from 'vue';
import { ElMessage } from 'element-plus';
import { chartSegments, statisticsPng, type ChartDatum } from '@/libs/statistics-chart';
/** 后台统计图区块：自带 ws-section 分组，页面直接放进概览卡即可，避免卡片套卡片。只呈现已加载事实。 */
const props = withDefaults(
  defineProps<{ title: string; subtitle?: string; unit: string; data: ChartDatum[]; kind?: 'bar' | 'pie'; downloadable?: boolean }>(),
  { kind: 'bar', subtitle: '', downloadable: false }
);
const segments = computed(() => chartSegments(props.data));
const max = computed(() => Math.max(1, ...props.data.map((item) => item.value)));
/** 零值或无数据时统一显示空态，不画空条。 */
const empty = computed(() => !props.data.length || !props.data.some((item) => item.value > 0));
/** 说明行保留筛选口径与单位，副标题缺省时不留空分隔。 */
const note = computed(() => (props.subtitle ? `${props.subtitle} · 单位：${props.unit}` : `单位：${props.unit}`));
const exporting = ref(false);
const COLORS = ['#2563eb', '#16a34a', '#d97706', '#7c3aed', '#0891b2'];
/** 输出带筛选说明的 PNG。 */
async function download(): Promise<void> {
  exporting.value = true;
  try {
    const blob = await statisticsPng(props.title, props.subtitle, props.unit, props.data, props.kind);
    const url = globalThis.URL.createObjectURL(blob);
    const a = globalThis.document.createElement('a');
    a.href = url;
    a.download = props.title + '.png';
    a.click();
    globalThis.setTimeout(() => globalThis.URL.revokeObjectURL(url), 1000);
  } catch {
    ElMessage.error('统计图生成失败，请重试');
  } finally {
    exporting.value = false;
  }
}
</script>
<template>
  <section class="ws-section">
    <header class="ws-section__head">
      <div>
        <h3>{{ title }}</h3>
        <p>{{ note }}</p>
      </div>
      <div v-if="downloadable" class="section-actions">
        <ElButton :loading="exporting" @click="download">下载统计图</ElButton>
      </div>
    </header>
    <ElEmpty v-if="empty" description="暂无统计数据" />
    <div v-else>
      <svg v-if="kind === 'pie'" class="pie" viewBox="0 0 120 120" role="img" :aria-label="title">
        <circle
          v-for="(item, index) in segments"
          :key="item.label"
          cx="60"
          cy="60"
          r="40"
          fill="none"
          :stroke="COLORS[index % COLORS.length]"
          stroke-width="24"
          pathLength="100"
          :stroke-dasharray="`${item.percent} ${100 - item.percent}`"
          :stroke-dashoffset="-item.offset"
          transform="rotate(-90 60 60)"
        />
      </svg>
      <div v-for="(item, index) in segments" :key="item.label" class="chart-row">
        <span>{{ item.label }}</span>
        <svg v-if="kind === 'bar'" viewBox="0 0 500 24" preserveAspectRatio="none" aria-hidden="true">
          <rect width="500" height="24" fill="#eff6ff" rx="4" />
          <rect :width="(Math.max(0, item.value) / max) * 500" height="24" fill="#2563eb" rx="4" />
        </svg>
        <span v-else class="legend" :style="{ background: COLORS[index % COLORS.length] }" />
        <strong>{{ item.value }}{{ unit }}{{ kind === 'pie' ? '（' + item.percent.toFixed(2) + '%）' : '' }}</strong>
      </div>
    </div>
  </section>
</template>
<style scoped lang="less">
.section-actions {
  display: flex;
  flex: 0 0 auto;
  align-items: center;
  gap: var(--space-2);
}
.chart-row {
  display: flex;
  gap: 16px;
  align-items: center;
  margin: 12px 0;
  > span:first-child {
    width: 180px;
    flex-shrink: 0;
  }
  svg {
    flex: 1;
    min-width: 0;
    height: 24px;
  }
  strong {
    min-width: 100px;
  }
}
.pie {
  width: 200px;
  display: block;
  margin: auto;
}
.legend {
  width: 12px;
  height: 12px;
  border-radius: 3px;
}
</style>
