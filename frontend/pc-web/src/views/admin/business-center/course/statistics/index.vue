<script setup lang="ts">
import { computed, onMounted, ref } from 'vue';
import { useRoute } from 'vue-router';
import { ArrowLeft } from '@element-plus/icons-vue';
import StatisticsChart from '@/components/statistics/StatisticsChart.vue';
import {
  getCourseStatistics,
  getCourseStatisticsPeople,
  type CourseStatistics,
  type CourseStatisticsPerson,
  type CourseStatisticsGroup
} from '@/api/course-statistics';
import { DEFAULT_LIST_PAGE_SIZE, LIST_PAGE_SIZES } from '@/constants/pagination';
const route = useRoute();
/** 不传课程时切换到租户内全部课程分类统计。 */
const allCourses = ref(false);
const courseId = Number(route.params.courseId);
const offeringId = ref<number>();
const offerings = ref<CourseStatisticsGroup[]>([]);
const stats = ref<CourseStatistics>();
const rows = ref<CourseStatisticsPerson[]>([]);
const loading = ref(false);
const failed = ref(false);
const keyword = ref('');
const pageNum = ref(1);
const pageSize = ref(DEFAULT_LIST_PAGE_SIZE);
const total = ref(0);
const title = computed(() => (allCourses.value ? '全部课程分类统计' : '课程学习统计'));
const subtitle = computed(() =>
  allCourses.value
    ? '全部课程；分类人数可能重叠'
    : offeringId.value
      ? (offerings.value.find((o) => o.id === offeringId.value)?.name ?? '当前开班')
      : '当前课程全部开班'
);
/** 汇总指标卡片；平均进度单列进概览右侧，避免与学习量混排。 */
const summaryMetrics = computed(() => {
  const s = stats.value;
  if (!s) return [] as { label: string; value: string | number; tone?: string }[];
  return [
    { label: '报名人数', value: s.summary.enrolledCount },
    { label: '参与人数', value: s.summary.participatedCount, tone: 'accent' },
    { label: '完成人数', value: s.summary.completedCount },
    { label: '有效学习秒数', value: s.summary.totalStudySeconds },
    { label: '学习次数', value: s.summary.studyCount }
  ];
});
const averageProgress = computed(() => stats.value?.summary.averageProgress ?? null);
const participation = computed(() =>
  stats.value
    ? [
        { label: '报名人数', value: stats.value.summary.enrolledCount },
        { label: '参与人数', value: stats.value.summary.participatedCount },
        { label: '完成人数', value: stats.value.summary.completedCount }
      ]
    : []
);
const durations = computed(() => stats.value?.offerings.map((o) => ({ label: o.name, value: o.summary.totalStudySeconds })) ?? []);
const categoryParticipants = computed(() => stats.value?.categories.map((c) => ({ label: c.name, value: c.summary.participatedCount })) ?? []);
const categoryDurations = computed(() => stats.value?.categories.map((c) => ({ label: c.name, value: c.summary.totalStudySeconds })) ?? []);
const knowledgeCoverage = computed(() => stats.value?.knowledgePoints.map((k) => ({ label: k.knowledgeName, value: k.questionCount })) ?? []);
/** 按当前课程、开班及学员筛选加载。 */
async function load(): Promise<void> {
  loading.value = true;
  failed.value = false;
  try {
    stats.value = await getCourseStatistics(allCourses.value ? undefined : courseId, offeringId.value);
    if (offeringId.value == null) offerings.value = stats.value.offerings;
    const page = await getCourseStatisticsPeople({
      courseId: allCourses.value ? undefined : courseId,
      offeringId: offeringId.value,
      learnerKeyword: keyword.value.trim() || undefined,
      pageNum: pageNum.value,
      pageSize: pageSize.value
    });
    rows.value = page.list;
    total.value = page.total;
  } catch {
    failed.value = true;
  } finally {
    loading.value = false;
  }
}
/** 新查询回到第一页。 */
async function query(): Promise<void> {
  pageNum.value = 1;
  await load();
}
/** 切换上游范围清理失效的开班。 */
async function changeScope(): Promise<void> {
  offeringId.value = undefined;
  await query();
}
onMounted(load);
</script>
<template>
  <section class="admin-detail-page statistics-detail" v-loading="loading">
    <header class="admin-detail-page__header">
      <div>
        <h1>{{ title }}</h1>
        <p>真实参与包含有效学习或考核行为；有效会话计学习次数，报名不等于参与，分类人数不可直接相加。</p>
      </div>
      <ElButton :icon="ArrowLeft" @click="$router.back()">返回</ElButton>
    </header>

    <ElCard class="admin-business-page__filters" shadow="never">
      <ElForm inline @submit.prevent>
        <ElFormItem><ElCheckbox v-model="allCourses" @change="changeScope">全部课程分类统计</ElCheckbox></ElFormItem>
        <ElFormItem label="开班">
          <ElSelect v-model="offeringId" clearable placeholder="全部开班" class="offering-select" @change="query">
            <ElOption v-for="o in offerings" :key="o.id" :label="o.name" :value="o.id" />
          </ElSelect>
        </ElFormItem>
        <ElFormItem label="学员"><ElInput v-model="keyword" clearable placeholder="请输入姓名或账号" @keyup.enter="query" /></ElFormItem>
        <ElFormItem><ElButton type="primary" @click="query">查询</ElButton></ElFormItem>
      </ElForm>
      <ElAlert v-if="failed" title="统计加载失败，请重试" type="error" :closable="false" />
    </ElCard>

    <ElCard shadow="never" class="admin-detail-page__card">
      <template v-if="stats">
        <section class="ws-hero">
          <div class="ws-hero__main">
            <p class="ws-hero__eyebrow">COURSE STATISTICS</p>
            <div class="ws-hero__title-row">
              <h2 class="ws-hero__title">{{ title }}</h2>
              <ElTag type="info" effect="plain">{{ subtitle }}</ElTag>
            </div>
            <div class="ws-hero__meta">
              <span class="ws-hero__chip">开班 {{ stats.offerings.length }} 个</span>
              <span class="ws-hero__chip">分类 {{ stats.categories.length }} 类</span>
              <span>时长为有效学习秒数，次数为有效学习会话。</span>
            </div>
          </div>
          <div class="ws-hero__aside">
            <div class="ws-hero__state">
              <strong class="hero-score">{{ averageProgress == null ? '—' : averageProgress + '%' }}</strong>
              <small>平均进度</small>
            </div>
          </div>
        </section>

        <section class="ws-metrics statistics-metrics">
          <div v-for="metric in summaryMetrics" :key="metric.label" class="ws-metric" :class="metric.tone ? `ws-metric--${metric.tone}` : ''">
            <span class="ws-metric__label">{{ metric.label }}</span>
            <span class="ws-metric__value">{{ metric.value }}</span>
          </div>
        </section>

        <StatisticsChart title="课程参与情况" :subtitle="subtitle" unit="人" :data="participation" />
        <StatisticsChart title="开班有效学习时长" :subtitle="subtitle" unit="秒" :data="durations" />
        <StatisticsChart title="分类参与人数" :subtitle="subtitle" unit="人" :data="categoryParticipants" />
        <StatisticsChart title="分类学习时长" :subtitle="subtitle" unit="秒" :data="categoryDurations" />

        <section class="ws-section">
          <header class="ws-section__head">
            <div><h2>考核知识点统计</h2><p>知识点来自冻结考核题目，不代表视频内容覆盖。</p></div>
            <span class="ws-section__extra">历史未记录题目 {{ stats.historicalKnowledgeMissingCount }} 道</span>
          </header>
          <StatisticsChart title="知识点覆盖题数" :subtitle="subtitle" unit="道" :data="knowledgeCoverage" />
          <ElTable :data="stats.knowledgePoints">
            <ElTableColumn prop="knowledgeName" label="知识点" />
            <ElTableColumn prop="questionCount" label="覆盖题数" />
            <ElTableColumn prop="answeredCount" label="已作答题数" />
            <ElTableColumn prop="evaluatedCount" label="已判定题数" />
            <ElTableColumn label="正确率">
              <template #default="{ row }">{{ row.accuracyRate == null ? '无判定数据' : row.accuracyRate + '%' }}</template>
            </ElTableColumn>
            <template #empty><ElEmpty description="暂无关联考核知识点统计" /></template>
          </ElTable>
        </section>

        <section class="ws-section">
          <header class="ws-section__head"><div><h2>个人学习记录</h2><p>按个人及开班保留明细，时长为有效学习秒数。</p></div></header>
          <ElTable :data="rows">
            <ElTableColumn prop="learnerName" label="姓名" />
            <ElTableColumn prop="learnerCode" label="账号" />
            <ElTableColumn prop="courseName" label="课程" />
            <ElTableColumn prop="offeringName" label="开班" />
            <ElTableColumn prop="totalStudySeconds" label="有效学习秒数" />
            <ElTableColumn prop="studyCount" label="学习次数" />
            <ElTableColumn label="进度">
              <template #default="{ row }">{{ row.progressPercent == null ? '—' : row.progressPercent + '%' }}</template>
            </ElTableColumn>
            <template #empty><ElEmpty description="暂无个人学习记录" /></template>
          </ElTable>
          <div class="admin-business-page__pagination">
            <ElPagination
              v-model:current-page="pageNum"
              v-model:page-size="pageSize"
              :total="total"
              :page-sizes="LIST_PAGE_SIZES"
              layout="total, sizes, prev, pager, next"
              @current-change="load"
              @size-change="query"
            />
          </div>
        </section>
      </template>
      <ElEmpty v-else description="暂无课程统计数据" />
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
.offering-select {
  width: 260px;
}
</style>
