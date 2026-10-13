<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue';
import { useRouter } from 'vue-router';
import { Notebook } from '@element-plus/icons-vue';
import type { LearnerHistory } from '@shared/types/learner-history';
import { HISTORY_LABELS, historyTarget, historyStatus } from '@shared/utils/learner-history';
import { DEFAULT_LIST_PAGE_SIZE } from '@/constants/pagination';
import { getMyHistory, getMyHistoryDetail } from '@/api/learner-history';
import HistoryRecordPanel from '@/components/HistoryRecordPanel.vue';

/** 本人档案包含退出、结束和完成记录，查询不依赖当前有效任务。 */
const router = useRouter(), rows = ref<LearnerHistory[]>([]), total = ref(0), page = ref(1), busy = ref(false), error = ref('');
const query = reactive({ recordType: 'ALL', keyword: '', dates: [] as string[] | null });
const detail = ref<LearnerHistory>(), visible = ref(false);
/** 分类、名称与日期均走统一契约。 */
async function load(): Promise<void> {
  busy.value = true; error.value = '';
  try {
    const result = await getMyHistory({ pageNum: page.value, pageSize: DEFAULT_LIST_PAGE_SIZE, 'query.recordType': query.recordType,
      'query.keyword': query.keyword, 'query.fromDate': query.dates?.[0], 'query.toDate': query.dates?.[1] });
    rows.value = result.list; total.value = result.total;
  } catch (e) { error.value = e instanceof Error ? e.message : '历史读取失败'; }
  finally { busy.value = false; }
}
/** 筛选重置页码，避免把后页空结果误认为无历史。 */
function search(): void { page.value = 1; void load(); }
/** 原始摘要和轨迹由本人详情接口重新鉴权。 */
async function inspect(row: LearnerHistory): Promise<void> {
  try { detail.value = await getMyHistoryDetail(row.recordType, row.recordId); visible.value = true; }
  catch (e) { error.value = e instanceof Error ? e.message : '历史详情读取失败'; }
}
/** 回看原受控业务页；失效时该页继续显示后端原因。 */
async function openOriginal(row: LearnerHistory): Promise<void> {
  try { const current = await getMyHistoryDetail(row.recordType, row.recordId); await router.push(historyTarget(current.reference, 'PC')); }
  catch (e) { error.value = e instanceof Error ? e.message : '原始记录不可访问'; }
}
/** 状态标签只做展示着色，不据此推断访问权限。 */
function statusTone(status: string): 'success' | 'warning' | 'plain' {
  if (['COMPLETED', 'PASSED'].includes(status)) return 'success';
  if (['EXPIRED', 'WITHDRAWN', 'CLEARED', 'ABSENT'].includes(status)) return 'plain';
  return 'warning';
}
onMounted(load);
</script>
<template>
  <section class="portal-list-page">
    <header class="portal-list-page__heading">
      <div>
        <p class="portal-list-page__eyebrow">MY HISTORY</p>
        <h1>我的统一学习档案</h1>
        <p>学习与考试的全部已有历史；原始记录与档案更正分开展示。</p>
      </div>
      <div class="portal-heading__actions">
        <ElButton :loading="busy" @click="load">刷新</ElButton>
      </div>
    </header>

    <ElAlert v-if="error" type="error" :title="error" :closable="false" />

    <div class="portal-toolbar">
      <ElSelect v-model="query.recordType" placeholder="请选择来源" aria-label="历史来源" style="width:150px">
        <ElOption label="全部" value="ALL" />
        <ElOption v-for="(label, type) in HISTORY_LABELS" :key="type" :label="label" :value="type" />
      </ElSelect>
      <ElInput v-model="query.keyword" placeholder="请输入来源名称" clearable style="width:200px" @keyup.enter="search" />
      <!-- 区间选择器在工具行内会吃满剩余宽度（实测 962px），显式限宽并禁止伸展。 -->
      <ElDatePicker v-model="query.dates" type="daterange" value-format="YYYY-MM-DD" start-placeholder="开始日期" end-placeholder="结束日期" style="width:280px;flex:none" />
      <ElButton type="primary" :loading="busy" @click="search">查询</ElButton>
    </div>

    <div class="portal-section-head">
      <h2 class="portal-section-title">全部历史</h2>
      <span class="portal-section-hint">共 {{ total }} 条</span>
    </div>

    <section v-if="!busy && !error && !rows.length" class="portal-list-state">
      <ElEmpty description="暂无符合条件的历史" />
    </section>
    <div v-else v-loading="busy" class="portal-list-grid">
      <ElCard v-for="row in rows" :key="`${row.recordType}-${row.recordId}`" class="portal-list-card" shadow="never">
        <div class="portal-list-card__cover portal-cover">
          <span class="portal-cover__icon"><ElIcon><Notebook /></ElIcon></span>
          <span class="portal-cover__text">
            <strong>{{ HISTORY_LABELS[row.recordType] }}</strong>
            <em>{{ row.version ? `档案更正 v${row.version}` : '原始记录' }}</em>
          </span>
          <ElIcon class="portal-cover__mark"><Notebook /></ElIcon>
        </div>
        <div class="portal-list-card__content">
          <div class="portal-list-card__header">
            <h3>{{ row.title }}</h3>
            <span class="portal-chip" :class="{ 'portal-chip--success': statusTone(row.current.status) === 'success', 'portal-chip--warning': statusTone(row.current.status) === 'warning', 'portal-chip--plain': statusTone(row.current.status) === 'plain' }">
              {{ historyStatus(row.current.status) }}
            </span>
          </div>
          <p class="portal-list-card__meta">发生时间：{{ row.current.occurredTime }}</p>
          <p class="portal-list-card__meta">成绩：{{ row.current.scoreVisible ? (row.current.score ?? '不评分') : '未公开或不评分' }} · 学时：{{ row.current.hours ?? '不适用' }}</p>
          <div class="portal-list-card__actions portal-list-card__actions--split">
            <span class="portal-list-card__actions-group">
              <ElButton link @click="openOriginal(row)">原始记录</ElButton>
            </span>
            <ElButton type="primary" @click="inspect(row)">档案与留痕</ElButton>
          </div>
        </div>
      </ElCard>
    </div>

    <div v-if="total" class="portal-list-page__pagination">
      <ElPagination v-model:current-page="page" :page-size="DEFAULT_LIST_PAGE_SIZE" :total="total" layout="prev, pager, next, total" @current-change="load" />
    </div>

    <ElDialog v-model="visible" title="原始记录与档案更正" width="900px">
      <HistoryRecordPanel v-if="detail" :record="detail" />
    </ElDialog>
  </section>
</template>
<style scoped lang="less">
h3 {
  overflow-wrap: anywhere;
}
</style>
