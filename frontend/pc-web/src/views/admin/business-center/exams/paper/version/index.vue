<script setup lang="ts">
import { computed, onMounted, ref } from 'vue';
import { useRoute, useRouter } from 'vue-router';
import { ArrowLeft } from '@element-plus/icons-vue';
import { getQuestionBankPage, getQuestionDetail, type Question, type QuestionBank } from '@/api/question';
import { getPaperVersion, type PaperVersion } from '@/api/paper';
import { useUserNameResolver } from '@/composables/use-user-name';
import { formatDuration } from '@/utils/duration';
import {
  getExamEnumLabel,
  MULTI_SELECT_SCORING_OPTIONS,
  PAPER_TYPE_OPTIONS,
  PUBLISH_STATUS_OPTIONS,
  QUESTION_ORDER_MODE_OPTIONS,
  QUESTION_TYPE_OPTIONS
} from '@/constants/exam-options';

const route = useRoute();
const router = useRouter();
const { loadUserNames, userLabel } = useUserNameResolver();
const version = ref<PaperVersion>();
const questionDetails = ref<Record<number, Question>>({});
/** 题库主键到题库记录的缓存，用于把题目快照的来源题库解析为可读名称。 */
const questionBanks = ref<QuestionBank[]>([]);
const loading = ref(false);

/** 已发布版本只读汇总，供顶部指标卡展示。 */
const summary = computed(() => [
  { label: '版本号', value: version.value?.versionNo ?? '—', unit: '' },
  { label: '总分', value: version.value?.totalScore ?? '—', unit: '分' },
  { label: '及格分', value: version.value?.passScore ?? '—', unit: '分' },
  { label: '题目数', value: version.value?.questionCount ?? '—', unit: '题' }
]);

/**
 * 版本信息以紧凑键值行展示：等宽描述表格会让短值独占一整格，形成大片留白，
 * 这里改为「标签紧邻取值」的自适应多列布局，取值不再被拉满单元格。
 */
const versionFacts = computed<Array<{ label: string; value: string }>>(() => {
  const current = version.value;
  if (!current) return [];
  return [
    { label: '试卷版本', value: String(current.versionNo ?? '—') },
    { label: '题目顺序', value: getExamEnumLabel(current.questionOrderMode, QUESTION_ORDER_MODE_OPTIONS) },
    { label: '多选题计分', value: getExamEnumLabel(current.multiSelectScoring, MULTI_SELECT_SCORING_OPTIONS) },
    { label: '抽题配置', value: getExamEnumLabel(current.drawSettingMode, PAPER_TYPE_OPTIONS) },
    { label: '考试时长', value: formatDuration(current.durationSeconds, '—') },
    { label: '及格比例', value: String(current.passRatio ?? '—') },
    { label: '发布人', value: userLabel(current.publishedBy) },
    { label: '发布时间', value: current.publishedTime ?? '—' },
    { label: '版本说明', value: current.versionNote || '—' }
  ];
});

/** 发布状态标签配色，已发布为成功色，草稿为信息色。 */
function publishStatusTag(status?: string): 'success' | 'info' | 'warning' {
  if (status === 'PUBLISHED') return 'success';
  if (status === 'ARCHIVED') return 'warning';
  return 'info';
}

/** 题型标签配色，与题目预览保持一致。 */
function questionTypeTag(type?: string): 'primary' | 'success' | 'warning' | 'danger' | 'info' {
  if (type === 'SINGLE') return 'primary';
  if (type === 'MULTIPLE') return 'success';
  if (type === 'JUDGE') return 'info';
  if (type === 'FILL') return 'warning';
  return 'danger';
}

/** 来源题库优先显示题库名称，题库已不存在时回退为题库编号。 */
function bankLabel(questionBankId?: number): string {
  if (!questionBankId) return '—';
  return questionBanks.value.find((item) => item.questionBankId === questionBankId)?.bankName ?? `题库 #${questionBankId}`;
}

/**
 * 读取题目快照的来源题库名称。
 * 这里不按启用状态过滤：版本固化的题目可能来自当前已停用或已归档的题库，
 * 纯名称回显不应因题库状态变化而退化成编号。
 */
async function loadQuestionBanks(): Promise<void> {
  try {
    questionBanks.value = (await getQuestionBankPage({ pageNum: 1, pageSize: 100 })).list;
  } catch {
    // 题库读取失败只影响名称回显，保留编号兜底即可。
    questionBanks.value = [];
  }
}

async function load(): Promise<void> {
  loading.value = true;
  try {
    version.value = await getPaperVersion(Number(route.params.paperVersionId));
    const [details] = await Promise.all([
      Promise.all((version.value.questions ?? []).map((item) => getQuestionDetail(item.questionId))),
      loadQuestionBanks(),
      loadUserNames([version.value.publishedBy])
    ]);
    questionDetails.value = Object.fromEntries(details.map((item) => [item.questionId, item]));
  } finally {
    loading.value = false;
  }
}

onMounted(load);
</script>

<template>
  <section v-loading="loading" class="admin-detail-page version-page">
    <header class="admin-detail-page__header">
      <div>
        <h1>已发布试卷版本</h1>
        <p>
          已发布版本不可修改，可用于创建考试并追溯每道题目的固化版本。
          <ElTag v-if="version" :type="publishStatusTag(version.publishedStatus)" size="small" effect="light">
            {{ getExamEnumLabel(version.publishedStatus, PUBLISH_STATUS_OPTIONS) }}
          </ElTag>
        </p>
      </div>
      <ElButton :icon="ArrowLeft" @click="router.back()">返回</ElButton>
    </header>

    <template v-if="version">
      <div class="version-summary">
        <div v-for="item in summary" :key="item.label" class="version-summary__item">
          <span class="version-summary__label">{{ item.label }}</span>
          <strong class="version-summary__value">{{ item.value }}<em v-if="item.unit">{{ item.unit }}</em></strong>
        </div>
      </div>

      <ElCard class="version-card" shadow="never">
        <template #header>
          <div class="version-card__head">
            <span>版本信息</span>
            <ElTag :type="publishStatusTag(version.publishedStatus)" size="small" effect="light">
              {{ getExamEnumLabel(version.publishedStatus, PUBLISH_STATUS_OPTIONS) }}
            </ElTag>
          </div>
        </template>
        <div class="version-facts">
          <div v-for="fact in versionFacts" :key="fact.label" class="version-facts__row">
            <span class="version-facts__label">{{ fact.label }}</span>
            <span class="version-facts__value">{{ fact.value }}</span>
          </div>
        </div>
      </ElCard>

      <ElCard class="version-card admin-detail-page__card" shadow="never">
        <template #header>
          <div class="version-card__head">
            <span>题目快照</span>
            <span class="version-card__count">共 {{ version.questions?.length ?? 0 }} 题</span>
          </div>
        </template>
        <ElTable :data="version.questions ?? []" stripe>
          <ElTableColumn label="排序" width="70" align="center">
            <template #default="{ row }">
              <span class="version-table__order">{{ row.sortOrder }}</span>
            </template>
          </ElTableColumn>
          <ElTableColumn label="题目编码" width="150">
            <template #default="{ row }">
              <span class="version-table__code">{{ questionDetails[row.questionId]?.questionCode ?? `#${row.questionId}` }}</span>
            </template>
          </ElTableColumn>
          <ElTableColumn label="题干" min-width="260" show-overflow-tooltip>
            <template #default="{ row }">{{ questionDetails[row.questionId]?.currentVersion?.stem ?? '—' }}</template>
          </ElTableColumn>
          <ElTableColumn label="来源题库" min-width="100" show-overflow-tooltip>
            <template #default="{ row }">{{ bankLabel(row.sourceBankId) }}</template>
          </ElTableColumn>
          <ElTableColumn label="题型" width="100">
            <template #default="{ row }">
              <ElTag size="small" effect="light" :type="questionTypeTag(row.questionType)">
                {{ getExamEnumLabel(row.questionType, QUESTION_TYPE_OPTIONS) }}
              </ElTag>
            </template>
          </ElTableColumn>
          <ElTableColumn prop="questionVersionId" label="题目版本" width="100" align="center" />
          <ElTableColumn prop="score" label="分值" width="80" align="center" />
        </ElTable>
      </ElCard>
    </template>
    <ElEmpty v-else-if="!loading" description="未找到该试卷版本" />
  </section>
</template>

<style scoped lang="less">
.version-page {
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.version-summary {
  display: grid;
  gap: 12px;
  grid-template-columns: repeat(4, minmax(0, 1fr));
}

.version-summary__item {
  display: flex;
  flex-direction: column;
  gap: 6px;
  padding: 16px 18px;
  border: 1px solid var(--color-border);
  border-radius: var(--radius-card);
  background: var(--color-bg-surface);
  box-shadow: 0 1px 2px rgb(15 23 42 / 4%);
}

.version-summary__label {
  color: var(--color-text-secondary);
  font-size: 12px;
}

.version-summary__value {
  color: var(--color-text-primary);
  font-size: 24px;
  font-weight: 600;
  line-height: 1.2;

  em {
    margin-left: 2px;
    color: var(--color-text-secondary);
    font-size: 12px;
    font-style: normal;
    font-weight: 400;
  }
}

.version-card {
  margin: 0;
}

.version-card__head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  color: var(--color-text-primary);
  font-size: 15px;
  font-weight: 600;
}

.version-card__count {
  color: var(--color-text-secondary);
  font-size: 12px;
  font-weight: 400;
}

/* 版本信息：三列键值网格，标签与取值紧邻，避免等宽单元格内的大片留白。 */
.version-facts {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  column-gap: var(--space-6);
}

.version-facts__row {
  display: flex;
  min-width: 0;
  align-items: baseline;
  gap: var(--space-3);
  padding: 10px 0;
}

/* 末行不画分隔线，避免卡片底部多出一条悬空横线。 */
.version-facts__row:not(:nth-last-child(-n + 3)) {
  border-bottom: 1px dashed var(--color-border);
}

.version-facts__label {
  flex: none;
  min-width: 68px;
  color: var(--color-text-secondary);
  font-size: 12px;
}

.version-facts__value {
  min-width: 0;
  color: var(--color-text-primary);
  font-size: 13px;
  font-weight: 500;
  line-height: 1.5;
  overflow-wrap: anywhere;
}

.version-table__order {
  display: inline-flex;
  width: 22px;
  height: 22px;
  align-items: center;
  justify-content: center;
  border-radius: 6px;
  background: var(--color-primary-subtle);
  color: var(--color-primary);
  font-size: 12px;
  font-weight: 600;
}

.version-table__code {
  color: var(--color-text-secondary);
  font-family: ui-monospace, SFMono-Regular, Menlo, monospace;
  font-size: 12px;
}

@media (width <= 960px) {
  .version-facts {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }

  /* 列数变化后末行的判定基准也要跟着变，否则会出现多余或缺失的横线。 */
  .version-facts__row:not(:nth-last-child(-n + 3)) {
    border-bottom: 0;
  }

  .version-facts__row:not(:nth-last-child(-n + 2)) {
    border-bottom: 1px dashed var(--color-border);
  }
}

@media (width <= 720px) {
  .version-summary {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }
}

@media (width <= 560px) {
  .version-facts {
    grid-template-columns: minmax(0, 1fr);
  }

  .version-facts__row:not(:nth-last-child(-n + 2)) {
    border-bottom: 0;
  }

  .version-facts__row:not(:last-child) {
    border-bottom: 1px dashed var(--color-border);
  }
}
</style>
