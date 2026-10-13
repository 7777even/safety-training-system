<script setup lang="ts">
import { computed, onMounted, ref } from 'vue';
import { useRoute } from 'vue-router';
import { ArrowLeft } from '@element-plus/icons-vue';
import { getExamStatistics, getExamStatisticsPeople, exportExamStatistics, type ExamStatistics, type ExamStatisticsPerson } from '@/api/exam-statistics';
import { DEFAULT_LIST_PAGE_SIZE, LIST_PAGE_SIZES } from '@/constants/pagination';

const route = useRoute();
/** 当前考试来自业务列表的真实资源 ID。 */
const examId = Number(route.params.examId);
const statistics = ref<ExamStatistics>();
const rows = ref<ExamStatisticsPerson[]>([]);
const selected = ref<ExamStatisticsPerson[]>([]);
const loading = ref(false);
const failed = ref(false);
const exporting = ref(false);
const keyword = ref('');
const absentOnly = ref(false);
const pageNum = ref(1);
const pageSize = ref(DEFAULT_LIST_PAGE_SIZE);
const total = ref(0);
/** 汇总指标卡片；语义色只在真有数值时启用，零值保持中性。 */
const metrics = computed(() => {
  const s = statistics.value;
  if (!s) return [] as { label: string; value: string | number; tone?: string }[];
  return [
    { label: '应考人数', value: s.expectedCount },
    { label: '参考人数', value: s.attendedCount },
    { label: '完成人数', value: s.completedCount },
    { label: '完成率', value: `${s.completionRate}%`, tone: 'accent' },
    { label: '最高分', value: s.highestScore ?? '—' },
    { label: '平均分', value: s.averageScore ?? '—' },
    { label: '最低分', value: s.lowestScore ?? '—' },
    { label: '待批阅人数', value: s.pendingReviewCount, tone: s.pendingReviewCount > 0 ? 'pending' : '' },
    { label: '未发布成绩人数', value: s.unpublishedCount, tone: s.unpublishedCount > 0 ? 'pending' : '' },
    { label: '缺考人数', value: s.absentCount, tone: s.absentCount > 0 ? 'pending' : '' },
    { label: '平均作答秒数', value: s.averageUsedSeconds ?? '—' }
  ];
});
const clientGroups = computed(() => statistics.value ? [
  { title:'平台',rows:statistics.value.platforms },{ title:'操作系统',rows:statistics.value.operatingSystems },{ title:'浏览器',rows:statistics.value.browsers }
] : []);
const QUESTION_TYPES: Record<string,string> = { SINGLE:'单选题',MULTIPLE:'多选题',SINGLE_CHOICE:'单选题',MULTIPLE_CHOICE:'多选题',JUDGE:'判断题',TRUE_FALSE:'判断题',BLANK:'填空题',FILL:'填空题',FILL_BLANK:'填空题',ESSAY:'简答题' };
/** 根据当前筛选读取人员；不改变全考试汇总。 */
async function loadPeople(): Promise<void> {
  const page = await getExamStatisticsPeople(examId,{ pageNum:pageNum.value,pageSize:pageSize.value,learnerKeyword:keyword.value.trim() || undefined,absentOnly:absentOnly.value || undefined });
  rows.value=page.list; total.value=page.total; selected.value=[];
}
/** 汇总与人员列表独立读取，失败保留重试入口。 */
async function load(): Promise<void> {
  loading.value=true; failed.value=false;
  try { statistics.value=await getExamStatistics(examId); await loadPeople(); }
  catch { failed.value=true; }
  finally { loading.value=false; }
}
/** 新筛选条件回到第一页。 */
async function query(): Promise<void> { pageNum.value=1; await load(); }
/** 批量导出当前筛选或选中人员，不只导出可见第一页。 */
async function download(onlySelected: boolean): Promise<void> {
  exporting.value=true;
  try {
    const blob=await exportExamStatistics(examId,{learnerKeyword:keyword.value.trim() || undefined,absentOnly:absentOnly.value || undefined},onlySelected ? selected.value.map(p=>p.examCandidateId) : undefined);
    const url=globalThis.URL.createObjectURL(blob); const link=globalThis.document.createElement('a');
    link.href=url; link.download='考试成绩.xlsx'; link.click(); globalThis.setTimeout(()=>globalThis.URL.revokeObjectURL(url),1000);
  } finally { exporting.value=false; }
}
onMounted(load);
</script>

<template>
  <section class="admin-detail-page statistics-detail" v-loading="loading">
    <header class="admin-detail-page__header">
      <div>
        <h1>考情分析</h1>
        <p>人数按学员去重；成绩及题目分析采用最终成绩对应答卷，分段为满分归一化百分制。</p>
      </div>
      <ElButton :icon="ArrowLeft" @click="$router.back()">返回</ElButton>
    </header>

    <ElCard shadow="never" class="admin-business-page__filters">
      <ElForm inline @submit.prevent>
        <ElFormItem label="学员"><ElInput v-model="keyword" clearable placeholder="请输入姓名或账号" @keyup.enter="query" /></ElFormItem>
        <ElFormItem><ElCheckbox v-model="absentOnly">仅缺考人员</ElCheckbox></ElFormItem>
        <ElFormItem><ElButton type="primary" @click="query">查询</ElButton></ElFormItem>
      </ElForm>
      <ElAlert v-if="failed" title="统计加载失败，请重试" type="error" :closable="false" />
    </ElCard>

    <ElCard shadow="never" class="admin-detail-page__card">
      <template v-if="statistics">
        <section class="ws-hero">
          <div class="ws-hero__main">
            <p class="ws-hero__eyebrow">EXAM STATISTICS</p>
            <div class="ws-hero__title-row">
              <h2 class="ws-hero__title">考情分析</h2>
              <ElTag type="info" effect="plain">应考 {{ statistics.expectedCount }} 人</ElTag>
            </div>
            <div class="ws-hero__meta">
              <span class="ws-hero__chip">完成率 {{ statistics.completionRate }}%</span>
              <span class="ws-hero__chip">缺考 {{ statistics.absentCount }} 人</span>
              <span>未发布成绩不按零分统计；考试结束前未参考人员不计缺考。</span>
            </div>
          </div>
          <div class="ws-hero__aside">
            <div class="ws-hero__state">
              <strong class="hero-score">{{ statistics.averageScore ?? '—' }}</strong>
              <small>平均分</small>
            </div>
          </div>
        </section>

        <section class="ws-metrics statistics-metrics">
          <div v-for="metric in metrics" :key="metric.label" class="ws-metric" :class="metric.tone ? `ws-metric--${metric.tone}` : ''">
            <span class="ws-metric__label">{{ metric.label }}</span>
            <span class="ws-metric__value">{{ metric.value }}</span>
          </div>
        </section>

        <section class="ws-section">
          <header class="ws-section__head"><div><h2>成绩分段（百分制）</h2><p>按最终成绩归一化到百分制后分段统计人数。</p></div></header>
          <ElTable :data="statistics.scoreBands">
            <ElTableColumn prop="label" label="分段" /><ElTableColumn prop="count" label="人数" /><ElTableColumn prop="percent" label="占比（%）" />
          </ElTable>
        </section>

        <section class="ws-section">
          <header class="ws-section__head"><div><h2>题型占比与正确率</h2><p>正确率仅统计已判定题目，未判定题型不参与计分。</p></div></header>
          <ElTable :data="statistics.questionTypes">
            <ElTableColumn label="题型"><template #default="{ row }">{{ QUESTION_TYPES[row.questionType] ?? '其他题型' }}</template></ElTableColumn>
            <ElTableColumn prop="count" label="题数" /><ElTableColumn prop="percent" label="题型占比（%）" />
            <ElTableColumn prop="evaluatedCount" label="已判定题数" />
            <ElTableColumn label="正确率"><template #default="{ row }">{{ row.accuracyRate == null ? '未判定' : row.accuracyRate + '%' }}</template></ElTableColumn>
          </ElTable>
        </section>

        <section class="ws-section">
          <header class="ws-section__head"><div><h2>错题 TOP5</h2><p>按错误人数排序，题干取自考试冻结的快照。</p></div></header>
          <ElTable :data="statistics.wrongTop5">
            <ElTableColumn prop="stem" label="冻结题干" min-width="250" /><ElTableColumn prop="wrongCount" label="错误人数" /><ElTableColumn prop="wrongRate" label="错误率（%）" />
          </ElTable>
        </section>

        <section class="ws-section">
          <header class="ws-section__head"><div><h2>客户端统计</h2><p>最近一次正式作答的客户端环境分布。</p></div></header>
          <div class="statistics-clients">
            <div v-for="group in clientGroups" :key="group.title">
              <h4>{{ group.title }}</h4>
              <ElTable :data="group.rows"><ElTableColumn prop="label" label="分类" /><ElTableColumn prop="count" label="人数" /><ElTableColumn prop="percent" label="占比（%）" /></ElTable>
            </div>
          </div>
        </section>

        <section class="ws-section">
          <header class="ws-section__head">
            <div><h2>人员成绩与参考时间明细</h2><p>导出沿用当前筛选，「导出选中」只导出勾选人员。</p></div>
            <div class="section-actions">
              <ElButton :loading="exporting" :disabled="!selected.length" @click="download(true)">导出选中</ElButton>
              <ElButton type="primary" :loading="exporting" @click="download(false)">导出筛选全部</ElButton>
            </div>
          </header>
          <ElTable :data="rows" row-key="examCandidateId" @selection-change="selected=$event">
            <ElTableColumn type="selection" width="50" /><ElTableColumn prop="learnerName" label="姓名" /><ElTableColumn prop="learnerCode" label="账号" />
            <ElTableColumn label="成绩"><template #default="{ row }">{{ row.finalScore ?? '未发布' }}</template></ElTableColumn>
            <ElTableColumn label="排名"><template #default="{ row }">{{ row.rank ?? '—' }}</template></ElTableColumn>
            <ElTableColumn label="参考情况"><template #default="{ row }">{{ row.absent ? '缺考' : row.completed ? '已提交' : row.attended ? '已参考' : '未参考' }}</template></ElTableColumn>
            <ElTableColumn prop="startedTime" label="开始时间" min-width="170" /><ElTableColumn prop="submittedTime" label="提交时间" min-width="170" /><ElTableColumn prop="usedSeconds" label="作答秒数" />
            <template #empty><ElEmpty description="暂无符合条件的考试人员" /></template>
          </ElTable>
          <div class="admin-business-page__pagination"><ElPagination v-model:current-page="pageNum" v-model:page-size="pageSize" :total="total" :page-sizes="LIST_PAGE_SIZES" layout="total, sizes, prev, pager, next" @current-change="load" @size-change="query" /></div>
        </section>
      </template>
      <ElEmpty v-else description="暂无考情统计数据" />
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
.section-actions {
  display: flex;
  flex: 0 0 auto;
  align-items: center;
  gap: var(--space-2);
}
.statistics-clients {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: var(--space-4);
  h4 {
    margin: 0 0 var(--space-2);
    color: var(--color-text-regular);
    font-size: 13px;
    font-weight: 600;
  }
}
@media (max-width: 1000px) {
  .statistics-clients {
    grid-template-columns: 1fr;
  }
}
</style>
