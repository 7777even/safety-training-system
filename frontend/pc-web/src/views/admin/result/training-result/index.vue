<script setup lang="ts">
import { computed, onMounted, ref } from 'vue';
import { useRoute } from 'vue-router';
import { ArrowLeft } from '@element-plus/icons-vue';
import { getEndedResult, type TrainingEndedResult } from '@/api/m4-outcome';
import { useUserNameResolver } from '@/composables/use-user-name';

/** 已结束培训成果直接展示服务端冻结结果，不在页面重新计算分数。 */
const route = useRoute();
const data = ref<TrainingEndedResult>();
const loading = ref(false);
/** 学员姓名与奖惩归属统一走人员选择器解析，失败仅回退编号，不阻断结果回放。 */
const { loadUserNames, userLabel } = useUserNameResolver();

/** 内容项执行类型遵循 training.openapi.json 的 executionType 枚举。 */
const EXECUTION_TYPE_LABELS: Record<string, string> = {
  COURSE: '课程',
  EXAM: '考试',
  QUESTION_BANK: '题库练习',
  OFFLINE: '线下'
};
/** 内容项结果状态遵循 training.openapi.json 的逐内容 resultStatus 取值。 */
const ITEM_RESULT_LABELS: Record<string, string> = {
  PENDING: '待开始',
  IN_PROGRESS: '进行中',
  PENDING_REVIEW: '待阅卷',
  PENDING_PUBLISH: '待发布成绩',
  COMPLETED: '完成',
  FAILED: '不合格',
  EXPIRED: '到期'
};
/** 结果状态只区分合格、结果待补全与不合格三类，与列表和档案口径保持一致。 */
const RESULT_TAGS: Record<string, { label: string; type: 'success' | 'warning' | 'danger' }> = {
  PASSED: { label: '合格', type: 'success' },
  INCOMPLETE: { label: '结果待补全', type: 'warning' }
};

/** 通过率按参训人数计算，不完整结果计入分母，避免高估达标比例。 */
const passRate = computed(() => {
  const total = data.value?.participantCount ?? 0;
  if (!total) return 0;
  return Math.round(((data.value?.passedCount ?? 0) / total) * 100);
});
/** 参训明细直接取服务端数组，页面不再做二次过滤。 */
const participantRows = computed(() => data.value?.participants ?? []);
/** 奖惩按参训人回显姓名，找不到归属时保留主键。 */
function participantLabel(trainingParticipantId: number): string {
  const participant = participantRows.value.find((item) => item.trainingParticipantId === trainingParticipantId);
  return participant ? userLabel(participant.userId) : `参训人 #${trainingParticipantId}`;
}
function executionTypeLabel(value: string): string {
  return EXECUTION_TYPE_LABELS[value] ?? value;
}
function itemResultLabel(value: string): string {
  return ITEM_RESULT_LABELS[value] ?? value;
}
function resultTag(status: string): 'success' | 'warning' | 'danger' {
  return RESULT_TAGS[status]?.type ?? 'danger';
}
function resultLabel(status: string): string {
  return RESULT_TAGS[status]?.label ?? '未通过';
}
async function load(): Promise<void> {
  loading.value = true;
  try {
    data.value = await getEndedResult(Number(route.params.trainingPlanId));
    await loadUserNames(participantRows.value.map((item) => item.userId));
  } finally {
    loading.value = false;
  }
}
onMounted(load);
</script>
<template>
  <section v-loading="loading" class="admin-detail-page">
    <header class="admin-detail-page__header">
      <div>
        <h1>{{ data?.planName || '培训成果' }}</h1>
        <p>计划编号：{{ data?.planCode || '—' }}；仅显示已结束培训的正式结果，综合分缺失表示结果未完整。</p>
      </div>
      <ElButton :icon="ArrowLeft" @click="$router.back()">返回</ElButton>
    </header>
    <ElCard v-if="data" class="admin-detail-page__card" shadow="never">
      <section class="ws-hero">
        <div class="ws-hero__main">
          <p class="ws-hero__eyebrow">TRAINING RESULT</p>
          <div class="ws-hero__title-row">
            <h2 class="ws-hero__title">{{ data.planName }}</h2>
            <ElTag type="info" effect="plain">已结束</ElTag>
          </div>
          <div class="ws-hero__meta">
            <span class="ws-hero__chip">计划编号 {{ data.planCode }}</span>
            <span class="ws-hero__chip">参训 {{ data.participantCount }} 人</span>
            <span>结果为计划结束时冻结的投影，页面只读回放，不在前端重算分数。</span>
          </div>
        </div>
        <div class="ws-hero__aside">
          <div class="ws-hero__state">
            <strong class="average-score">{{ data.averageCompositeScore ?? '—' }}</strong>
            <small>平均综合分{{ data.averageCompositeScore === undefined || data.averageCompositeScore === null ? '（未生成）' : '' }}</small>
          </div>
        </div>
      </section>

      <section class="ws-metrics result-metrics">
        <div class="ws-metric">
          <span class="ws-metric__label">参训人数</span>
          <span class="ws-metric__value">{{ data.participantCount }}</span>
        </div>
        <div class="ws-metric ws-metric--pass">
          <span class="ws-metric__label">通过人数</span>
          <span class="ws-metric__value">
            {{ data.passedCount }}
            <em>合格</em>
          </span>
        </div>
        <div class="ws-metric ws-metric--fail">
          <span class="ws-metric__label">未通过人数</span>
          <span class="ws-metric__value">
            {{ data.failedCount }}
            <em>不合格</em>
          </span>
        </div>
        <div class="ws-metric ws-metric--pending">
          <span class="ws-metric__label">结果待补全</span>
          <span class="ws-metric__value">
            {{ data.incompleteCount }}
            <em>待补全</em>
          </span>
        </div>
        <div class="ws-metric">
          <span class="ws-metric__label">通过率</span>
          <div class="ws-bar">
            <div class="ws-bar__track">
              <div class="ws-bar__fill" :class="passRate < 60 ? 'ws-bar__fill--warn' : 'ws-bar__fill--pass'" :style="{ width: `${passRate}%` }" />
            </div>
            <div class="ws-bar__legend">
              <span>{{ passRate }}%</span>
              <span>按参训人数</span>
            </div>
          </div>
        </div>
      </section>

      <section class="ws-section">
        <header class="ws-section__head">
          <div>
            <h2>学员成绩与明细</h2>
            <p>展开学员行可查看线上、线下学时与各内容项完成情况。</p>
          </div>
          <span class="ws-section__extra">共 {{ participantRows.length }} 人</span>
        </header>
        <ElTable :data="participantRows" row-key="trainingParticipantId" empty-text="本期暂无学员成果">
          <ElTableColumn type="expand">
            <template #default="{ row }">
              <div class="participant-detail">
                <div class="ws-kv">
                  <div class="ws-kv__item">
                    <span class="ws-kv__label">线上学时</span>
                    <span class="ws-kv__value">{{ row.onlineHours ?? 0 }}</span>
                  </div>
                  <div class="ws-kv__item">
                    <span class="ws-kv__label">线下学时</span>
                    <span class="ws-kv__value">{{ row.offlineHours ?? 0 }}</span>
                  </div>
                  <div class="ws-kv__item">
                    <span class="ws-kv__label">结果确认时间</span>
                    <span class="ws-kv__value">{{ row.resultFinalizedTime || '—' }}</span>
                  </div>
                </div>
                <ElTable :data="row.items || []" size="small" border empty-text="暂无内容项">
                  <ElTableColumn prop="contentName" label="培训内容" show-overflow-tooltip />
                  <ElTableColumn label="内容类型" width="120">
                    <template #default="{ row: item }">{{ executionTypeLabel(item.executionType) }}</template>
                  </ElTableColumn>
                  <ElTableColumn label="完成情况" width="130">
                    <template #default="{ row: item }">{{ itemResultLabel(item.resultStatus) }}</template>
                  </ElTableColumn>
                  <ElTableColumn label="成绩" width="100">
                    <template #default="{ row: item }">{{ item.score ?? '—' }}</template>
                  </ElTableColumn>
                </ElTable>
              </div>
            </template>
          </ElTableColumn>
          <ElTableColumn label="学员" min-width="150">
            <template #default="{ row }">{{ userLabel(row.userId) }}</template>
          </ElTableColumn>
          <ElTableColumn label="课程成绩" width="110">
            <template #default="{ row }">{{ row.courseScore ?? '—' }}</template>
          </ElTableColumn>
          <ElTableColumn label="考试成绩" width="110">
            <template #default="{ row }">{{ row.examScore ?? '—' }}</template>
          </ElTableColumn>
          <ElTableColumn label="线下成绩" width="110">
            <template #default="{ row }">{{ row.offlineScore ?? '—' }}</template>
          </ElTableColumn>
          <ElTableColumn label="综合分" width="110">
            <template #default="{ row }">
              <strong v-if="row.compositeScore !== undefined && row.compositeScore !== null" class="composite-score">
                {{ row.compositeScore }}
              </strong>
              <span v-else class="composite-score--empty">未生成</span>
            </template>
          </ElTableColumn>
          <ElTableColumn label="结果" width="130">
            <template #default="{ row }">
              <ElTag :type="resultTag(row.resultStatus)">{{ resultLabel(row.resultStatus) }}</ElTag>
            </template>
          </ElTableColumn>
        </ElTable>
      </section>

      <section class="ws-section">
        <header class="ws-section__head">
          <div>
            <h2>本期奖惩</h2>
            <p>奖惩记录随计划结果一并冻结，分值变化不影响已生成的综合分。</p>
          </div>
          <span class="ws-section__extra">共 {{ data.rewardPenalties.length }} 条</span>
        </header>
        <ElTable :data="data.rewardPenalties" empty-text="暂无奖惩记录">
          <ElTableColumn label="类型" width="100">
            <template #default="{ row }">
              <ElTag :type="row.recordType === 'REWARD' ? 'warning' : 'danger'" effect="light">
                {{ row.recordType === 'REWARD' ? '奖励' : '处罚' }}
              </ElTag>
            </template>
          </ElTableColumn>
          <ElTableColumn prop="title" label="标题" min-width="180" show-overflow-tooltip />
          <ElTableColumn label="参训人" min-width="140">
            <template #default="{ row }">{{ participantLabel(row.trainingParticipantId) }}</template>
          </ElTableColumn>
          <ElTableColumn label="分值变化" width="110">
            <template #default="{ row }">
              <span v-if="row.scoreChange === undefined || row.scoreChange === null">—</span>
              <span v-else :class="row.scoreChange >= 0 ? 'score-gain' : 'score-loss'">
                {{ row.scoreChange > 0 ? `+${row.scoreChange}` : row.scoreChange }}
              </span>
            </template>
          </ElTableColumn>
          <ElTableColumn label="发生日期" width="130">
            <template #default="{ row }">{{ row.occurredDate || '—' }}</template>
          </ElTableColumn>
          <ElTableColumn label="说明" min-width="200">
            <template #default="{ row }">{{ row.description || '—' }}</template>
          </ElTableColumn>
        </ElTable>
      </section>
    </ElCard>
    <div v-else-if="!loading" class="ws-empty">
      <ElEmpty description="未读取到培训成果">
        <span class="ws-empty__hint">请从培训计划工作台在计划结束状态下进入「培训成果」，页面按计划主键加载冻结结果。</span>
      </ElEmpty>
    </div>
  </section>
</template>
<style scoped lang="less">
.average-score {
  color: var(--color-primary);
  font-size: 26px;
  font-variant-numeric: tabular-nums;
  font-weight: 700;
  line-height: 32px;
}

/* 明细展开后内容区变长时，保留成果概览的完整高度。 */
.ws-hero {
  flex-shrink: 0;
}

.result-metrics {
  margin: var(--space-4) 0;
}

.participant-detail {
  display: grid;
  gap: var(--space-3);
  padding: 4px 12px 12px 48px;
}

.composite-score {
  color: var(--color-text-primary);
  font-variant-numeric: tabular-nums;
}

.composite-score--empty {
  color: var(--color-text-secondary);
}

.score-gain {
  color: #15803d;
  font-variant-numeric: tabular-nums;
}

.score-loss {
  color: var(--color-danger);
  font-variant-numeric: tabular-nums;
}
</style>
