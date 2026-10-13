<script setup lang="ts">
import { computed, onMounted, ref } from 'vue';
import { useRoute } from 'vue-router';
import { ArrowLeft } from '@element-plus/icons-vue';
import StatisticsChart from '@/components/statistics/StatisticsChart.vue';
import { getOfflineClassStatistics, type OfflineClassStatistics } from '@/api/offline-class-statistics';
const route = useRoute();
const stats = ref<OfflineClassStatistics>();
const loading = ref(false);
const failed = ref(false);
/** 关联考试的可读描述，未关联时给出明确口径而不是留空。 */
const examRows = computed(() =>
  (stats.value?.examContents ?? []).map((e) => ({
    name: e.examName,
    paper: e.paperName ? `${e.paperName}（版本 ${e.versionNo}）` : e.paperVersionId ? '已关联历史试卷版本' : '未关联试卷'
  }))
);
/** 每个考核内容的成绩概览，作为统计图副标题，避免卡片套卡片。 */
function assessmentNote(a: NonNullable<OfflineClassStatistics['assessments']>[number]): string {
  return `已评分 ${a.scoredCount} 人，未评分 ${a.unscoredCount} 人；最高 ${a.highestPercent ?? '—'}，平均 ${a.averagePercent ?? '—'}，最低 ${a.lowestPercent ?? '—'}`;
}
/** 加载真实场次及录分，不提供线下在线作答。 */
async function load(): Promise<void> {
  loading.value = true;
  failed.value = false;
  try {
    stats.value = await getOfflineClassStatistics(Number(route.params.offlineSessionId));
  } catch {
    failed.value = true;
  } finally {
    loading.value = false;
  }
}
onMounted(load);
</script>
<template>
  <section class="admin-detail-page statistics-detail" v-loading="loading">
    <header class="admin-detail-page__header">
      <div>
        <h1>线下课堂考试统计</h1>
        <p>展示场次已录入的线下考核成绩，成绩按试卷满分归一化到百分制。</p>
      </div>
      <div class="detail-actions">
        <ElButton @click="load">刷新</ElButton>
        <ElButton :icon="ArrowLeft" @click="$router.back()">返回</ElButton>
      </div>
    </header>

    <ElAlert v-if="failed" title="课堂统计加载失败，请重试" type="error" :closable="false" />
    <ElCard shadow="never" class="admin-detail-page__card">
      <template v-if="stats">
        <section class="ws-hero">
          <div class="ws-hero__main">
            <p class="ws-hero__eyebrow">OFFLINE CLASS</p>
            <div class="ws-hero__title-row"><h2 class="ws-hero__title">{{ stats.sessionName }}</h2></div>
            <div class="ws-hero__meta">
              <span class="ws-hero__chip">培训内容 {{ stats.contentName }}</span>
              <span class="ws-hero__chip">地点 {{ stats.location }}</span>
            </div>
          </div>
          <div class="ws-hero__aside">
            <div class="ws-hero__state">
              <strong class="hero-score">{{ stats.scoredCount }} / {{ stats.expectedCount }}</strong>
              <small>已评分 / 应有参训</small>
            </div>
          </div>
        </section>

        <section class="ws-metrics statistics-metrics">
          <div class="ws-metric">
            <span class="ws-metric__label">应有参训人数</span>
            <span class="ws-metric__value">{{ stats.expectedCount }}</span>
          </div>
          <div class="ws-metric ws-metric--pass">
            <span class="ws-metric__label">已评分</span>
            <span class="ws-metric__value">{{ stats.scoredCount }}</span>
          </div>
          <div class="ws-metric" :class="stats.unscoredCount > 0 ? 'ws-metric--pending' : ''">
            <span class="ws-metric__label">未评分</span>
            <span class="ws-metric__value">{{ stats.unscoredCount }}</span>
          </div>
        </section>

        <section class="ws-section">
          <header class="ws-section__head">
            <div><h2>同阶段已添加考试</h2><p>关联试卷版本用于把线下原始分归一化为百分制。</p></div>
            <span class="ws-section__extra">共 {{ examRows.length }} 项</span>
          </header>
          <div class="ws-kv">
            <div v-for="row in examRows" :key="row.name" class="ws-kv__item">
              <span class="ws-kv__label">{{ row.name }}</span>
              <span class="ws-kv__value">{{ row.paper }}</span>
            </div>
            <div v-if="!examRows.length" class="ws-kv__item ws-kv__item--span">
              <span class="ws-kv__value ws-kv__value--muted">未关联考试内容</span>
            </div>
          </div>
        </section>

        <StatisticsChart
          v-for="assessment in stats.assessments"
          :key="assessment.scoreName"
          :title="assessment.scoreName"
          :subtitle="assessmentNote(assessment)"
          unit="人"
          :data="assessment.scoreBands.map((b) => ({ label: b.label, value: b.count }))"
        />

        <section class="ws-section">
          <header class="ws-section__head">
            <div><h2>学员考核结果明细</h2><p>未评分学员保留占位，便于后续补录。</p></div>
            <span class="ws-section__extra">共 {{ stats.people.length }} 人</span>
          </header>
          <ElTable :data="stats.people">
            <ElTableColumn prop="learnerName" label="学员" />
            <ElTableColumn label="考核内容"><template #default="{ row }">{{ row.scoreName ?? '尚未评分' }}</template></ElTableColumn>
            <ElTableColumn label="原始成绩 / 满分">
              <template #default="{ row }">{{ row.score == null ? '未评分' : row.score + ' / ' + row.maxScore }}</template>
            </ElTableColumn>
            <ElTableColumn prop="scoredTime" label="评分时间" />
            <template #empty><ElEmpty description="暂无学员考核结果" /></template>
          </ElTable>
        </section>
      </template>
      <ElEmpty v-else description="暂无线下课堂统计数据" />
    </ElCard>
  </section>
</template>
<style scoped lang="less">
.statistics-metrics {
  margin: var(--space-4) 0;
}
.hero-score {
  color: var(--color-primary);
  font-size: 26px;
  font-variant-numeric: tabular-nums;
  font-weight: 700;
  line-height: 32px;
}
.detail-actions {
  display: flex;
  flex: 0 0 auto;
  align-items: center;
  gap: var(--space-2);
}
</style>
