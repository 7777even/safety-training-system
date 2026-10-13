<script setup lang="ts">
import { computed, onMounted, ref } from 'vue';
import { useRoute } from 'vue-router';
import { ArrowLeft } from '@element-plus/icons-vue';
import { ElMessage } from 'element-plus';
import { DEFAULT_LIST_PAGE_SIZE, LIST_PAGE_SIZES } from '@/constants/pagination';
import StatisticsChart from '@/components/statistics/StatisticsChart.vue';
import {
  getTrainingClassStatistics,
  getTrainingClassPeople,
  type TrainingClassStatistics,
  type TrainingClassGroup,
  type TrainingClassPerson
} from '@/api/training-class-statistics';
/** 部门班级统计页：模式人数不能相加作为总人数。 */
const route = useRoute();
const planId = Number(route.query.trainingPlanId) || undefined;
const loading = ref(false);
const data = ref<TrainingClassStatistics>();
const selected = ref<TrainingClassGroup>();
const people = ref<TrainingClassPerson[]>([]);
const total = ref(0);
const pageNum = ref(1);
const pageSize = ref(DEFAULT_LIST_PAGE_SIZE);
const keyword = ref('');
const peopleLoading = ref(false);
const modes = computed(() => [
  { label: '线上', value: data.value?.onlineParticipantCount ?? 0 },
  { label: '线下', value: data.value?.offlineParticipantCount ?? 0 }
]);
const classes = computed(() =>
  (data.value?.classes ?? []).map((c) => ({
    label: `${c.planName} · ${c.deptName} · ${c.mode === 'ONLINE' ? '线上' : '线下'}`,
    value: c.participantCount
  }))
);
/** 线上占比仅用于观感，按去重参训人次为分母。 */
const onlineShare = computed(() => {
  const base = data.value?.totalParticipantCount ?? 0;
  return base ? Math.round(((data.value?.onlineParticipantCount ?? 0) / base) * 100) : 0;
});
/** 查询当前入口对应计划，空入口汇总全部可见计划。 */
async function load(): Promise<void> {
  loading.value = true;
  try {
    data.value = await getTrainingClassStatistics(planId);
  } catch {
    ElMessage.error('班级统计加载失败，请重试');
  } finally {
    loading.value = false;
  }
}
/** 下钻必须保留计划、部门及模式，包括未分配部门。 */
async function loadPeople(): Promise<void> {
  if (!selected.value) return;
  peopleLoading.value = true;
  try {
    const c = selected.value;
    const page = await getTrainingClassPeople({
      trainingPlanId: c.trainingPlanId,
      deptId: c.deptId ?? undefined,
      unassignedOnly: c.deptId === null,
      mode: c.mode,
      learnerKeyword: keyword.value,
      pageNum: pageNum.value,
      pageSize: pageSize.value
    });
    people.value = page.list;
    total.value = page.total;
  } catch {
    ElMessage.error('班级人员加载失败，请重试');
  } finally {
    peopleLoading.value = false;
  }
}
/** 切换班级时重置人员分页和关键字。 */
function drill(value: unknown): void {
  const row = value as TrainingClassGroup;
  selected.value = row;
  pageNum.value = 1;
  keyword.value = '';
  void loadPeople();
}
/** 人员关键字查询回到第一页。 */
function searchPeople(): void {
  pageNum.value = 1;
  void loadPeople();
}
onMounted(load);
</script>
<template>
  <section class="admin-detail-page statistics-detail" v-loading="loading">
    <header class="admin-detail-page__header">
      <div>
        <h1>班级统计</h1>
        <p>班级使用现有部门；人数按计划＋部门＋学员去重，混合计划同时进入线上、线下，两类人数不能相加作为总人数。</p>
      </div>
      <div class="detail-actions">
        <ElButton @click="load">刷新</ElButton>
        <ElButton :icon="ArrowLeft" @click="$router.back()">返回</ElButton>
      </div>
    </header>

    <ElCard shadow="never" class="admin-detail-page__card">
      <template v-if="data">
        <section class="ws-hero">
          <div class="ws-hero__main">
            <p class="ws-hero__eyebrow">TRAINING CLASS</p>
            <div class="ws-hero__title-row">
              <h2 class="ws-hero__title">班级统计</h2>
              <ElTag type="info" effect="plain">{{ data.classes.length }} 个班级</ElTag>
            </div>
            <div class="ws-hero__meta">
              <span class="ws-hero__chip">线上 {{ data.onlineParticipantCount }} 人次</span>
              <span class="ws-hero__chip">线下 {{ data.offlineParticipantCount }} 人次</span>
              <span>计划内学员去重，混合计划同时计入线上与线下。</span>
            </div>
          </div>
          <div class="ws-hero__aside">
            <div class="ws-hero__state">
              <strong class="hero-score">{{ onlineShare }}%</strong>
              <small>线上参与占比</small>
            </div>
          </div>
        </section>

        <section class="ws-metrics statistics-metrics">
          <div class="ws-metric ws-metric--accent">
            <span class="ws-metric__label">参训人次（计划内去重）</span>
            <span class="ws-metric__value">{{ data.totalParticipantCount }}</span>
          </div>
          <div class="ws-metric">
            <span class="ws-metric__label">线上参与人次</span>
            <span class="ws-metric__value">{{ data.onlineParticipantCount }}</span>
          </div>
          <div class="ws-metric">
            <span class="ws-metric__label">线下参与人次</span>
            <span class="ws-metric__value">{{ data.offlineParticipantCount }}</span>
          </div>
        </section>

        <StatisticsChart title="线上、线下参与构成" unit="人次" kind="pie" :data="modes" />
        <StatisticsChart title="部门班级人数" unit="人" :data="classes" />

        <section class="ws-section">
          <header class="ws-section__head">
            <div><h2>班级明细</h2><p>按计划、部门与模式下钻查看参训人员。</p></div>
            <span class="ws-section__extra">共 {{ data.classes.length }} 条</span>
          </header>
          <ElTable :data="data.classes" row-class-name="is-clickable" @row-click="drill">
            <ElTableColumn prop="planName" label="培训计划" />
            <ElTableColumn prop="deptName" label="部门班级" />
            <ElTableColumn label="培训模式">
              <template #default="{ row }">
                <ElTag :type="row.mode === 'ONLINE' ? 'primary' : 'success'" effect="plain">{{ row.mode === 'ONLINE' ? '线上' : '线下' }}</ElTag>
              </template>
            </ElTableColumn>
            <ElTableColumn prop="participantCount" label="人数" />
            <ElTableColumn label="操作" width="110">
              <template #default="{ row }"><ElButton link type="primary" @click.stop="drill(row)">查看人员</ElButton></template>
            </ElTableColumn>
            <template #empty><ElEmpty description="暂无班级统计" /></template>
          </ElTable>
        </section>
      </template>
      <ElEmpty v-else description="暂无班级统计数据" />
    </ElCard>

    <ElDialog
      :model-value="!!selected"
      :title="`${selected?.planName ?? ''} · ${selected?.deptName ?? ''} · ${selected?.mode === 'ONLINE' ? '线上' : '线下'}`"
      width="800px"
      @close="selected = undefined"
    >
      <div class="people-toolbar">
        <ElInput v-model="keyword" clearable placeholder="学员姓名或账号" @keyup.enter="searchPeople" />
        <ElButton type="primary" @click="searchPeople">查询</ElButton>
      </div>
      <ElTable v-loading="peopleLoading" :data="people">
        <ElTableColumn prop="learnerName" label="学员姓名" />
        <ElTableColumn prop="learnerCode" label="账号" />
        <ElTableColumn prop="deptName" label="部门" />
        <template #empty><ElEmpty description="暂无符合条件的人员" /></template>
      </ElTable>
      <div class="admin-business-page__pagination">
        <ElPagination
          v-model:current-page="pageNum"
          v-model:page-size="pageSize"
          :total="total"
          :page-sizes="LIST_PAGE_SIZES"
          layout="total, sizes, prev, pager, next"
          @current-change="loadPeople"
        />
      </div>
    </ElDialog>
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
.people-toolbar {
  display: flex;
  gap: var(--space-2);
  margin-bottom: var(--space-3);
}
:deep(.el-table__row.is-clickable) {
  cursor: pointer;
}
</style>
