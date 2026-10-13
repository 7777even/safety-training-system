<script setup lang="ts">
import { computed, onMounted, ref } from 'vue';
import { useRoute, useRouter } from 'vue-router';
import { ElMessage } from 'element-plus';
import {
  getEvaluationFormDetail,
  getEvaluationFormVersionDetail,
  type EvaluationForm,
  type EvaluationCalculationType,
  type EvaluationFormVersionDetail,
  type EvaluationItemType,
  type EvaluationPublishedStatus,
  type EvaluationSourceType
} from '@/api/evaluation-form';
import { useUserNameResolver } from '@/composables/use-user-name';
import { useUserStore } from '@/stores';
import { hasPermission } from '@/utils/permission';

const route = useRoute();
const router = useRouter();
const userStore = useUserStore();
const { loadUserNames, userLabel } = useUserNameResolver();
const loading = ref(false);
const detail = ref<EvaluationFormVersionDetail>();
const form = ref<EvaluationForm>();

const ITEM_TYPE_TEXT: Record<EvaluationItemType, string> = {
  SCORE: '分数项',
  TEXT: '文本项',
  CHOICE: '选择项'
};
const CALCULATION_TEXT: Record<EvaluationCalculationType, string> = {
  WEIGHT: '权重折算',
  ADJUSTMENT: '加减分调整'
};
const SOURCE_TEXT: Record<EvaluationSourceType, string> = {
  COURSE: '课程成绩',
  EXAM: '考试成绩',
  OFFLINE: '线下成绩',
  REWARD_PENALTY: '奖惩记录',
  MANUAL: '人工录入'
};
const COMPOSITE_SOURCE_TYPES: EvaluationSourceType[] = ['COURSE', 'EXAM', 'OFFLINE'];
const COMPOSITE_SOURCE_DESCRIPTION: Record<EvaluationSourceType, string> = {
  COURSE: '使用计划内已发布的正式课程成绩',
  EXAM: '使用计划内已发布的正式考试成绩',
  OFFLINE: '使用计划内已发布的正式线下成绩',
  REWARD_PENALTY: '使用已确认的奖惩记录',
  MANUAL: '使用人工录入的评价成绩'
};
/** 版本状态来自评价表单契约，统一转换为后台可读的中文状态。 */
const VERSION_STATUS_META: Record<EvaluationPublishedStatus, { label: string; type: 'info' | 'success' | 'warning' }> = {
  DRAFT: { label: '草稿', type: 'warning' },
  PUBLISHED: { label: '已发布', type: 'success' },
  ARCHIVED: { label: '已归档', type: 'info' }
};
/** 仅为已加载的只读版本提供状态展示信息。 */
const versionStatusMeta = computed(() => (detail.value ? VERSION_STATUS_META[detail.value.publishedStatus] : undefined));
/** 所属表单类型决定发布快照是否按综合成绩来源卡片展示。 */
const isCompositeForm = computed(() => form.value?.formType === 'COMPOSITE');
/** 专家检查版本仅显示项目快照，不展示成绩来源或自动规则。 */
const isExpertInspectionForm = computed(() => form.value?.formType === 'EXPERT_INSPECTION');
/** 将冻结规则按项目与规则页相同的三张来源卡片重新组织为只读信息。 */
const compositeSourceCards = computed(() =>
  COMPOSITE_SOURCE_TYPES.map((sourceType) => ({
    sourceType,
    rule: detail.value?.scoreRules.find((rule) => rule.sourceType === sourceType)
  }))
);

function formatWeight(weight: number | null): string {
  return weight === null ? '—' : weight.toFixed(2);
}

/** 评分规则按冻结项目 ID 关联，用项目名称和编码辅助人工核对。 */
function ruleItemText(evaluationItemId: number): string {
  const item = detail.value?.items.find((current) => current.evaluationItemId === evaluationItemId);
  return item ? `${item.itemName}（${item.itemCode}）` : `项目 ID：${evaluationItemId}`;
}

/** 版本详情只读取发布快照，不提供任何修改或重新发布动作。 */
async function loadVersion(): Promise<void> {
  if (!hasPermission(userStore.userInfo, 'evaluation:form:list')) return;
  const formVersionId = Number(route.params.formVersionId);
  if (!formVersionId) {
    ElMessage.error('缺少评价表单版本 ID');
    return;
  }
  loading.value = true;
  try {
    detail.value = await getEvaluationFormVersionDetail(formVersionId);
    form.value = await getEvaluationFormDetail(detail.value.evaluationFormId);
    await loadUserNames([detail.value.publishedBy]);
  } finally {
    loading.value = false;
  }
}

onMounted(loadVersion);
</script>

<template>
  <section v-loading="loading" class="version-page admin-detail-page">
    <header class="admin-detail-page__header">
      <div>
        <h1>评价表单只读版本</h1>
        <p>
          这里展示发布时冻结的项目与评分规则，不随表单主体后续修改而变化。
          <ElTag v-if="versionStatusMeta" :type="versionStatusMeta.type" size="small" effect="light">{{ versionStatusMeta.label }}</ElTag>
        </p>
      </div>
      <div class="header-actions">
        <ElButton
          v-if="detail && hasPermission(userStore.userInfo, 'evaluation:form:edit')"
          @click="router.push(`/admin/business-center/training/evaluation-form/workspace/${detail.evaluationFormId}`)"
        >
          返回工作台
        </ElButton>
        <ElButton @click="router.push('/admin/business-center/training/evaluation-form')">返回列表</ElButton>
      </div>
    </header>

    <ElAlert class="readonly-alert" title="只读版本：发布后项目、权重和评分规则均不可修改。" type="info" :closable="false" show-icon />
    <!-- 只读详情采用连续阅读区，项目与规则不再被拆进高度受限的独立卡片。 -->
    <main v-if="detail" class="version-content">
      <template v-if="detail">
        <section class="version-overview" aria-labelledby="version-overview-title">
          <div class="version-hero">
            <div>
              <span class="version-hero__eyebrow">已发布 · 只读快照</span>
              <h2 id="version-overview-title">{{ form?.formName || '评价表单' }} V{{ detail.versionNo }}</h2>
              <p>该版本已冻结项目、权重和评分规则，后续仅供查询与追溯。</p>
            </div>
            <ElTag v-if="versionStatusMeta" class="version-hero__status" :type="versionStatusMeta.type">
              {{ versionStatusMeta.label }}
            </ElTag>
          </div>
          <div class="version-summary-grid">
            <div class="version-metric">
              <span>版本号</span>
              <strong>V{{ detail.versionNo }}</strong>
            </div>
            <div class="version-metric">
              <span>发布状态</span>
              <strong>
                <ElTag v-if="versionStatusMeta" class="version-table__tag" :type="versionStatusMeta.type">{{ versionStatusMeta.label }}</ElTag>
              </strong>
            </div>
            <div class="version-metric">
              <span>版本总分</span>
              <strong>{{ detail.totalScore }}</strong>
            </div>
            <div class="version-metric">
              <span>发布人</span>
              <strong>{{ userLabel(detail.publishedBy) }}</strong>
            </div>
            <div class="version-metric version-metric--wide">
              <span>发布时间</span>
              <strong>{{ detail.publishedTime ?? '—' }}</strong>
            </div>
            <div class="version-metric version-metric--wide">
              <span>版本说明</span>
              <strong>{{ detail.versionNote || '—' }}</strong>
            </div>
          </div>
        </section>

        <!-- 综合评价的冻结规则以工作台相同的来源卡片呈现，避免只读页与配置含义脱节。 -->
        <section v-if="isCompositeForm" class="snapshot-section composite-snapshot" aria-labelledby="composite-snapshot-title">
          <div class="snapshot-section__header">
            <div>
              <div class="snapshot-section__title-row">
                <span class="snapshot-section__sequence">01</span>
                <h2 id="composite-snapshot-title">综合成绩来源配置</h2>
              </div>
              <p>以下来源及表单层权重为该版本发布时冻结的综合成绩计算口径。</p>
            </div>
            <span class="snapshot-section__meta">只读快照</span>
          </div>
          <div class="composite-guide" aria-label="综合评价计算说明">
            <strong>计算方式</strong>
            <span>计划内同类内容先汇总，再按本表单冻结的来源权重计算综合成绩。</span>
          </div>
          <div class="source-card-grid">
            <article v-for="source in compositeSourceCards" :key="source.sourceType" class="source-card" :class="{ 'is-selected': source.rule }">
              <div class="source-card__header">
                <div>
                  <h3>{{ SOURCE_TEXT[source.sourceType] }}</h3>
                  <p>{{ COMPOSITE_SOURCE_DESCRIPTION[source.sourceType] }}</p>
                </div>
                <ElTag :type="source.rule ? 'success' : 'info'">{{ source.rule ? '已纳入' : '未纳入' }}</ElTag>
              </div>
              <template v-if="source.rule">
                <div class="source-card__weight">
                  <span>表单层权重</span>
                  <strong>{{ formatWeight(source.rule.weight) }}</strong>
                </div>
                <dl class="source-card__summary">
                  <div>
                    <dt>评价项目</dt>
                    <dd>{{ ruleItemText(source.rule.evaluationItemId) }}</dd>
                  </div>
                  <div>
                    <dt>计算方式</dt>
                    <dd>{{ CALCULATION_TEXT[source.rule.calculationType] }}</dd>
                  </div>
                  <div>
                    <dt>项目要求</dt>
                    <dd>满分 100，必填</dd>
                  </div>
                </dl>
              </template>
              <p v-else class="source-card__empty">未纳入时不会影响综合成绩计算。</p>
            </article>
          </div>
        </section>

        <section class="snapshot-section" aria-labelledby="items-snapshot-title">
          <div class="snapshot-section__header">
            <div>
              <div class="snapshot-section__title-row">
                <span class="snapshot-section__sequence">{{ isCompositeForm ? '02' : '01' }}</span>
                <h2 id="items-snapshot-title">{{ isExpertInspectionForm ? '专家查课项目快照' : '评价项目快照' }}</h2>
              </div>
              <p>以下 {{ detail.items.length }} 个项目已随版本冻结。</p>
            </div>
            <span class="snapshot-section__meta">共 {{ detail.items.length }} 项</span>
          </div>
          <div class="snapshot-section-heading">
            <span>项目共 {{ detail.items.length }} 项</span>
            <span>按排序字段展示，发布后不可修改。</span>
          </div>
          <div class="snapshot-table-wrap">
            <ElTable :data="detail.items" border class="snapshot-table">
              <ElTableColumn prop="itemCode" label="项目编码" min-width="160" />
              <ElTableColumn prop="itemName" label="项目名称" min-width="220" />
              <ElTableColumn label="类型" width="110">
                <template #default="{ row }">
                  <ElTag class="version-table__tag" effect="plain">{{ ITEM_TYPE_TEXT[row.itemType as EvaluationItemType] }}</ElTag>
                </template>
              </ElTableColumn>
              <ElTableColumn prop="maxScore" label="满分" width="100">
                <template #default="{ row }">{{ row.maxScore ?? '—' }}</template>
              </ElTableColumn>
              <ElTableColumn v-if="!isExpertInspectionForm" prop="weight" label="权重" width="100">
                <template #default="{ row }">{{ row.weight ?? '—' }}</template>
              </ElTableColumn>
              <ElTableColumn label="必填" width="90">
                <template #default="{ row }">
                  <ElTag class="version-table__tag" :type="row.isRequired === 1 ? 'danger' : 'info'" effect="plain">
                    {{ row.isRequired === 1 ? '必填' : '选填' }}
                  </ElTag>
                </template>
              </ElTableColumn>
              <ElTableColumn prop="sortOrder" label="排序" width="80" />
            </ElTable>
          </div>
        </section>

        <section v-if="!isExpertInspectionForm" class="snapshot-section" aria-labelledby="rules-snapshot-title">
          <div class="snapshot-section__header">
            <div>
              <div class="snapshot-section__title-row">
                <span class="snapshot-section__sequence">{{ isCompositeForm ? '03' : '02' }}</span>
                <h2 id="rules-snapshot-title">评分规则快照</h2>
              </div>
              <p>共 {{ detail.scoreRules.length }} 条，均通过冻结项目关联。</p>
            </div>
            <span class="snapshot-section__meta">共 {{ detail.scoreRules.length }} 条</span>
          </div>
          <ElEmpty v-if="!detail.scoreRules.length" description="该版本未配置自动评分规则" class="rules-empty" />
          <template v-else>
            <div class="snapshot-section-heading">
              <span>规则共 {{ detail.scoreRules.length }} 条</span>
              <span>按关联项目、计算方式和成绩来源核对。</span>
            </div>
            <div class="snapshot-table-wrap">
              <ElTable :data="detail.scoreRules" border class="snapshot-table">
                <ElTableColumn label="关联项目" min-width="220">
                  <template #default="{ row }">{{ ruleItemText(row.evaluationItemId) }}</template>
                </ElTableColumn>
                <ElTableColumn label="计算方式" width="130">
                  <template #default="{ row }">
                    {{ CALCULATION_TEXT[row.calculationType as EvaluationCalculationType] }}
                  </template>
                </ElTableColumn>
                <ElTableColumn label="成绩来源" width="130">
                  <template #default="{ row }">{{ SOURCE_TEXT[row.sourceType as EvaluationSourceType] }}</template>
                </ElTableColumn>
                <ElTableColumn prop="weight" label="权重" width="100">
                  <template #default="{ row }">{{ row.weight ?? '—' }}</template>
                </ElTableColumn>
                <ElTableColumn prop="scoreChange" label="加减分" width="100">
                  <template #default="{ row }">{{ row.scoreChange ?? '—' }}</template>
                </ElTableColumn>
                <ElTableColumn prop="ruleDescription" label="规则说明" min-width="320" />
                <ElTableColumn prop="sortOrder" label="排序" width="80" />
              </ElTable>
            </div>
          </template>
        </section>
      </template>
    </main>
  </section>
</template>

<style scoped lang="less">
.version-page {
  height: 100%;
}

.version-content {
  min-height: 0;
  flex: 1;
  overflow: auto;
  border: 1px solid var(--color-border);
  border-radius: var(--radius-card);
  background: var(--color-bg-surface);
  box-shadow: var(--shadow-sm);
}

.version-overview,
.snapshot-section {
  padding: 24px;
}

.snapshot-section {
  border-top: 1px solid var(--color-border);
}

.snapshot-section__header {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 16px;
  margin-bottom: 16px;

  h2,
  p {
    margin: 0;
  }

  h2 {
    color: var(--color-text-primary);
    font-size: 16px;
    font-weight: 600;
    line-height: 24px;
  }

  p {
    margin-top: 2px;
    color: var(--color-text-secondary);
    font-size: 13px;
    line-height: 20px;
  }
}

.snapshot-section__title-row {
  display: flex;
  align-items: center;
  gap: 10px;
}

.snapshot-section__sequence {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 26px;
  height: 22px;
  color: var(--color-primary);
  font-size: 12px;
  font-weight: 700;
  line-height: 1;
  background: var(--color-primary-subtle);
  border-radius: 6px;
}

.version-hero {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 24px;
  position: relative;
  padding: 22px 24px;
  overflow: hidden;
  background: var(--color-primary-subtle);
  border: 1px solid #dbeafe;
  border-radius: 10px;

  &::after {
    position: absolute;
    top: -48px;
    right: 42px;
    width: 132px;
    height: 132px;
    pointer-events: none;
    content: '';
    border: 20px solid rgba(37, 99, 235, 0.08);
    border-radius: 50%;
  }

  h2,
  p {
    margin: 0;
  }

  h2 {
    position: relative;
    z-index: 1;
    margin-top: 6px;
    color: var(--color-text-primary);
    font-size: 22px;
    font-weight: 700;
    line-height: 30px;
  }

  p {
    position: relative;
    z-index: 1;
    margin-top: 4px;
    color: var(--color-text-secondary);
    font-size: 13px;
    line-height: 20px;
  }
}

.version-hero__eyebrow,
.snapshot-section__meta {
  color: var(--color-primary);
  font-size: 12px;
  font-weight: 600;
  line-height: 18px;
}

.version-hero__eyebrow {
  position: relative;
  z-index: 1;
  display: inline-flex;
  align-items: center;
  gap: 6px;
  padding: 3px 8px;
  background: var(--color-bg-surface);
  border: 1px solid #bfdbfe;
  border-radius: 999px;
}

.version-hero__status {
  position: relative;
  z-index: 1;
  flex: 0 0 auto;
  margin-top: 2px;
}

.header-actions {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
}

.readonly-alert {
  margin: 0;
}

.snapshot-table {
  background: var(--color-bg-surface);
}

.version-summary-grid {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: 14px;
  margin-top: 16px;
}

.version-metric {
  position: relative;
  min-width: 0;
  padding: 16px;
  background: var(--color-bg-surface);
  border: 1px solid var(--color-border);
  border-radius: 8px;
  box-shadow: 0 1px 2px rgba(15, 23, 42, 0.03);

  &::before {
    position: absolute;
    top: 0;
    left: 16px;
    width: 28px;
    height: 3px;
    content: '';
    background: var(--color-primary);
    border-radius: 0 0 3px 3px;
  }

  span {
    display: block;
    color: var(--color-text-secondary);
    font-size: 12px;
    line-height: 18px;
  }

  strong {
    display: block;
    margin-top: 4px;
    overflow: hidden;
    color: var(--color-text-primary);
    font-size: 16px;
    font-weight: 600;
    line-height: 24px;
    text-overflow: ellipsis;
    white-space: nowrap;
  }
}

.version-metric--wide {
  grid-column: span 2;
}

.snapshot-section-heading {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
  margin-bottom: 12px;
  padding: 9px 12px;
  color: var(--color-text-secondary);
  font-size: 13px;
  line-height: 20px;
  background: var(--color-bg-subtle);
  border-radius: 6px;
}

.composite-guide {
  display: flex;
  gap: 10px;
  margin-bottom: 16px;
  padding: 12px 14px;
  color: var(--color-primary);
  font-size: 13px;
  line-height: 20px;
  background: var(--color-primary-subtle);
  border: 1px solid #bfdbfe;
  border-radius: 8px;
}

.source-card-grid {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 10px;
}

.source-card {
  display: flex;
  flex-direction: column;
  min-width: 0;
  padding: 14px;
  background: var(--color-bg-surface);
  border: 1px solid var(--color-border);
  border-radius: 8px;
}

.source-card.is-selected {
  border-color: #93c5fd;
  box-shadow: 0 1px 2px rgb(15 23 42 / 6%);
}

.source-card__header {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 8px;

  h3,
  p {
    margin: 0;
  }

  h3 {
    color: var(--color-text-primary);
    font-size: 15px;
    line-height: 22px;
  }

  p {
    margin-top: 2px;
    color: var(--color-text-secondary);
    font-size: 12px;
    line-height: 18px;
  }
}

.source-card__weight {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  margin: 14px 0 10px;
  padding: 10px 12px;
  background: var(--color-bg-subtle);
  border-radius: 6px;

  span {
    color: var(--color-text-regular);
    font-size: 13px;
  }

  strong {
    color: var(--color-primary);
    font-size: 18px;
    line-height: 24px;
  }
}

.source-card__summary {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 8px;
  margin: 0;
  padding: 10px 0 0;
  border-top: 1px solid var(--color-border);

  div {
    min-width: 0;
  }

  dt,
  dd {
    margin: 0;
    font-size: 12px;
    line-height: 18px;
  }

  dt {
    color: var(--color-text-secondary);
  }

  dd {
    overflow: hidden;
    color: var(--color-text-regular);
    text-overflow: ellipsis;
    white-space: nowrap;
  }
}

.source-card__empty {
  margin: auto 0 0;
  padding-top: 28px;
  color: var(--color-text-secondary);
  font-size: 12px;
  line-height: 18px;
}

.version-table__tag {
  width: 60px;
  color: var(--el-tag-text-color)!important;
  font-weight: 500;
}

.snapshot-table-wrap {
  overflow-x: auto;
  background: var(--color-bg-surface);
  border: 1px solid var(--color-border);
  border-radius: 8px;

  :deep(.el-table) {
    min-width: 920px;
    --el-table-border-color: var(--color-border);
    --el-table-header-bg-color: var(--color-bg-subtle);
    --el-table-header-text-color: var(--color-text-regular);
    --el-table-row-hover-bg-color: var(--color-primary-subtle);
  }

  :deep(.el-table th.el-table__cell) {
    height: 44px;
    font-size: 13px;
    font-weight: 600;
  }

  :deep(.el-table td.el-table__cell) {
    height: 48px;
  }
}

.rules-empty {
  padding: 28px 0;
  background: var(--color-bg-subtle);
  border: 1px dashed var(--color-border);
  border-radius: 8px;
}

@media (max-width: 960px) {
  .version-summary-grid {
    grid-template-columns: 1fr;
  }

  .version-metric--wide {
    grid-column: span 1;
  }

  .snapshot-section-heading {
    align-items: flex-start;
    flex-direction: column;
    gap: 4px;
  }

  .version-hero {
    align-items: flex-start;
    flex-direction: column;
    gap: 8px;
  }

  .source-card-grid {
    grid-template-columns: 1fr;
  }
}
</style>
