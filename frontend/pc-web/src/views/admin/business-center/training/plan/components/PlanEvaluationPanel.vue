<script setup lang="ts">
import { computed } from 'vue';
import { CircleCheckFilled, CollectionTag, WarningFilled } from '@element-plus/icons-vue';
import type { TrainingPlanEvaluationForm } from '@/api/training-plan';
import type { CompositeBindingIssue } from '../workspace/composite-binding-precheck';

interface FormVersion {
  formVersionId: number;
  evaluationFormId: number;
  formName?: string;
  versionNo: number;
  versionNote?: string | null;
  publishedTime?: string | null;
}
interface ContextHint {
  formVersionId: number;
  sourceType: string;
  available: boolean;
  weight: number | null;
  calculationType: string;
  itemName?: string;
  itemCode?: string;
  maxScore?: number | null;
  isRequired?: 0 | 1;
}
const props = defineProps<{
  selectedIds: number[];
  bindings: TrainingPlanEvaluationForm[];
  versions: FormVersion[];
  readonly: boolean;
  contextHints: ContextHint[];
  /** 绑定前检查问题清单，只包含会被后端拒绝保存的项。 */
  precheckIssues: CompositeBindingIssue[];
}>();
const emit = defineEmits<{ selectionChange: [ids: number[]] }>();

const SOURCE_TEXT: Record<string, string> = {
  COURSE: '课程成绩',
  EXAM: '考试成绩',
  OFFLINE: '线下成绩',
  REWARD_PENALTY: '奖惩',
  MANUAL: '人工'
};
const COMPOSITE_SOURCE_TYPES = ['COURSE', 'EXAM', 'OFFLINE'];
const SOURCE_DESCRIPTION: Record<string, string> = {
  COURSE: '使用计划内已发布的正式课程成绩',
  EXAM: '使用计划内已发布的正式考试成绩',
  OFFLINE: '使用计划内已发布的正式线下成绩'
};
const SOURCE_LABEL: Record<string, string> = {
  COURSE: '课程',
  EXAM: '考试',
  OFFLINE: '线下'
};
const CALCULATION_TYPE_TEXT: Record<string, string> = {
  WEIGHT: '权重折算',
  ADJUSTMENT: '加减分调整'
};
/** 当前选择的版本用于呈现版本摘要，不替代保存时的绑定数据。 */
const selectedVersion = computed(() => props.versions.find((item) => item.formVersionId === props.selectedIds[0]));
/** 当前综合评价绑定保留启用与必填状态，避免把专家检查绑定混入该区域。 */
const compositeBinding = computed(() => props.bindings.find((item) => item.usageType === 'COMPOSITE'));
/** 来源提示仅作可读摘要，保存和发布仍由后端校验。 */
const availableSourceCount = computed(() => props.contextHints.filter((item) => item.available).length);
const unavailableSourceCount = computed(() => props.contextHints.length - availableSourceCount.value);
/** 固定展示三类综合成绩来源，使计划页与表单配置页保持同一信息结构。 */
const compositeSourceCards = computed(() =>
  COMPOSITE_SOURCE_TYPES.map((sourceType) => ({
    sourceType,
    hint: props.contextHints.find((item) => item.sourceType === sourceType)
  }))
);

/** 生成下拉选项的主标题，优先使用表单列表接口返回的 formName。 */
function versionOptionLabel(item: FormVersion): string {
  return item.formName ?? `评价表单 ${item.formVersionId}`;
}

/** 综合评价用途一次只保留一个有效版本，清空后提交空数组即可解除绑定。 */
function handleSelection(value?: number): void {
  emit('selectionChange', value ? [value] : []);
}

/** 将版本备注空值统一为可读文案，避免界面出现空白信息块。 */
function versionNoteText(version?: FormVersion): string {
  return version?.versionNote || '已发布不可变版本';
}

/** 冻结权重按两位小数显示，和综合成绩来源配置的输入口径保持一致。 */
function formatWeight(weight?: number | null): string {
  return weight == null ? '—' : weight.toFixed(2);
}

/** 项目要求来自冻结评价项目快照，避免计划页自行推导。 */
function itemRequirementText(hint: ContextHint): string {
  const scoreText = hint.maxScore == null ? '不参与计分' : `满分 ${hint.maxScore}`;
  return `${scoreText}，${hint.isRequired === 1 ? '必填' : '选填'}`;
}
</script>

<template>
  <section class="composite-binding-card">
    <header class="panel-heading">
      <div><span class="binding-kicker">COMPOSITE</span><h2>综合评价</h2><p>选择发布版本，按课程、考试、线下正式成绩计算综合分。</p></div>
      <ElTag effect="plain" type="primary">成绩计算</ElTag>
    </header>
    <div class="composite-binding-card__body">
      <section class="binding-field">
        <label>表单版本</label>
        <div class="binding-field__control">
          <ElSelect
            :model-value="props.selectedIds[0]"
            :multiple="false"
            clearable
            filterable
            :disabled="props.readonly"
            placeholder="请选择已发布综合评价表单版本"
            @change="handleSelection($event as number | undefined)"
          >
            <ElOption v-for="item in props.versions" :key="item.formVersionId" :label="versionOptionLabel(item)" :value="item.formVersionId">
              <div class="evaluation-option">
                <strong>{{ versionOptionLabel(item) }}</strong>
              </div>
            </ElOption>
          </ElSelect>
        </div>
      </section>
      <article v-if="selectedVersion && compositeBinding" class="binding-summary">
        <span class="binding-summary__icon"><CircleCheckFilled /></span>
        <div class="binding-summary__main"><strong>{{ versionOptionLabel(selectedVersion) }}</strong><span>V{{ selectedVersion.versionNo }} · {{ versionNoteText(selectedVersion) }}</span></div>
        <div class="binding-summary__switches"><label>必填<ElSwitch v-model="compositeBinding.isRequired" :disabled="props.readonly" :active-value="1" :inactive-value="0" /></label><label>启用<ElSwitch v-model="compositeBinding.status" :disabled="props.readonly" :active-value="1" :inactive-value="0" /></label></div>
      </article>
      <section v-if="!props.readonly && props.precheckIssues.length" class="precheck-panel">
        <header class="precheck-panel__header">
          <span class="precheck-panel__icon"><WarningFilled /></span>
          <div>
            <h3>绑定前检查未通过（{{ props.precheckIssues.length }} 项）</h3>
            <p>以下问题会被后端拒绝保存，请逐项处理后重试；点击“保存评价表单”可直接定位到需要修改的步骤。</p>
          </div>
        </header>
        <ul class="precheck-panel__list">
          <li v-for="(issue, index) in props.precheckIssues" :key="index">{{ issue.message }}</li>
        </ul>
      </section>
      <section class="source-panel">
        <header class="source-panel__header"><div><h3>成绩来源摘要</h3><p>版本发布后冻结；可用性按计划中是否存在计入综合成绩的同类内容判定。</p></div><ElTag v-if="props.contextHints.length" :type="unavailableSourceCount ? 'warning' : 'success'" effect="light">{{ unavailableSourceCount ? `${unavailableSourceCount} 项待补齐` : '来源已就绪' }}</ElTag></header>
        <div v-if="props.contextHints.length === 0" class="source-empty">
          <CollectionTag />
          <span>{{ selectedVersion ? '该版本未读取到综合成绩来源。' : '选择版本后显示课程、考试和线下成绩来源。' }}</span>
        </div>
        <template v-else>
          <div class="composite-guide">
            <strong>计算方式</strong>
            <span>计划内同类内容先汇总，再按绑定表单冻结的来源权重计算综合成绩。</span>
          </div>
          <div class="source-grid">
            <article v-for="source in compositeSourceCards" :key="source.sourceType" class="source-card" :class="{ 'is-selected': source.hint, 'is-ready': source.hint?.available }">
              <div class="source-card__header">
                <div>
                  <h4>{{ SOURCE_TEXT[source.sourceType] }}</h4>
                  <p>{{ SOURCE_DESCRIPTION[source.sourceType] }}</p>
                </div>
                <ElTag :type="source.hint ? 'success' : 'info'" effect="light">{{ source.hint ? '已纳入' : '未纳入' }}</ElTag>
              </div>
              <template v-if="source.hint">
                <div class="source-card__weight">
                  <span>表单层权重</span>
                  <strong>{{ formatWeight(source.hint.weight) }}</strong>
                </div>
                <dl class="source-card__summary">
                  <div><dt>评价项目</dt><dd>{{ source.hint.itemName || '冻结项目缺失' }}{{ source.hint.itemCode ? `（${source.hint.itemCode}）` : '' }}</dd></div>
                  <div><dt>计算方式</dt><dd>{{ CALCULATION_TYPE_TEXT[source.hint.calculationType] ?? source.hint.calculationType }}</dd></div>
                  <div><dt>项目要求</dt><dd>{{ itemRequirementText(source.hint) }}</dd></div>
                </dl>
                <p class="source-card__availability" :class="{ 'is-ready': source.hint.available }">
                  {{ source.hint.available ? '当前计划可用' : `尚无「计入综合成绩」的${SOURCE_LABEL[source.sourceType] ?? '该类别'}内容` }}
                </p>
              </template>
              <p v-else class="source-card__empty">未纳入时不会影响综合成绩计算。</p>
            </article>
          </div>
        </template>
      </section>
    </div>
  </section>
</template>

<style scoped lang="less">
.card-header h2,
.evaluation-hero h3,
.evaluation-section h3 {
  margin: 0;
  color: var(--color-text-primary);
  font-size: 15px;
  font-weight: 600;
  line-height: 20px;
}
.card-header p,
.evaluation-hero p,
.evaluation-section p {
  margin: 4px 0 0;
  color: var(--color-text-secondary);
  font-size: 12px;
  line-height: 20px;
}
.evaluation-scroll {
  padding: 16px 18px 20px;
  background: var(--color-bg-subtle);
}
.evaluation-hero,
.evaluation-section {
  margin-bottom: 16px;
  border: 1px solid var(--color-border);
  border-radius: 10px;
  background: var(--color-bg-surface);
}
.evaluation-hero {
  display: grid;
  grid-template-columns: minmax(0, 1fr) auto;
  gap: 24px;
  padding: 20px;
  border-color: color-mix(in srgb, var(--color-primary) 24%, var(--color-border));
  background: linear-gradient(120deg, color-mix(in srgb, var(--color-primary) 8%, var(--color-bg-surface)), var(--color-bg-surface) 55%);
}
.evaluation-hero__identity {
  display: flex;
  align-items: flex-start;
  gap: 14px;
}
.evaluation-hero__icon,
.binding-row__icon {
  display: grid;
  place-items: center;
  border-radius: 10px;
  background: color-mix(in srgb, var(--color-primary) 14%, transparent);
  color: var(--color-primary);
}
.evaluation-hero__icon {
  width: 40px;
  height: 40px;
  flex: 0 0 40px;
  font-size: 21px;
}
.evaluation-hero__eyebrow {
  display: block;
  margin-bottom: 2px;
  color: var(--color-primary);
  font-size: 10px;
  font-weight: 700;
  letter-spacing: 0.12em;
}
.evaluation-hero__metrics {
  display: flex;
  gap: 10px;
}
.evaluation-hero__metrics div {
  display: grid;
  min-width: 104px;
  gap: 3px;
  padding: 8px 12px;
  border: 1px solid color-mix(in srgb, var(--color-primary) 18%, var(--color-border));
  border-radius: 8px;
  background: color-mix(in srgb, var(--color-bg-surface) 84%, transparent);
}
.evaluation-hero__metrics span,
.evaluation-hero__metrics small {
  color: var(--color-text-secondary);
  font-size: 12px;
  line-height: 18px;
}
.evaluation-hero__metrics strong {
  color: var(--color-text-primary);
  font-size: 20px;
  line-height: 26px;
}
.evaluation-section {
  padding: 18px;
}
.evaluation-section__header {
  display: flex;
  align-items: flex-start;
  gap: 10px;
  margin-bottom: 16px;
}
.evaluation-section__header > div {
  flex: 1 1 auto;
}
.evaluation-section__step {
  display: grid;
  width: 28px;
  height: 28px;
  flex: 0 0 28px;
  place-items: center;
  border-radius: 8px;
  background: var(--color-bg-subtle);
  color: var(--color-primary);
  font-size: 11px;
  font-weight: 700;
}
.evaluation-selection__content {
  display: flex;
  gap: 12px;
}
.evaluation-selection__content :deep(.el-select) {
  width: 20%;
}
.evaluation-option,
.selected-version {
  display: grid;
  gap: 2px;
}
.evaluation-option strong,
.selected-version strong {
  color: var(--color-text-primary);
  font-size: 13px;
  line-height: 18px;
}
.evaluation-option span,
.selected-version span {
  color: var(--color-text-secondary);
  font-size: 12px;
  line-height: 18px;
}
.selected-version {
  grid-template-columns: auto minmax(0, 1fr);
  align-items: center;
  padding: 9px 12px;
  border: 1px solid color-mix(in srgb, var(--color-success, #67c23a) 26%, var(--color-border));
  border-radius: 8px;
  background: color-mix(in srgb, var(--color-success, #67c23a) 6%, var(--color-bg-surface));
}
.selected-version > svg {
  color: var(--color-success, #67c23a);
  font-size: 20px;
}
.selected-version--empty {
  border-color: var(--color-border);
  background: var(--color-bg-subtle);
}
.selected-version--empty > svg,
.selected-version--empty span {
  color: var(--color-text-secondary);
}
.binding-empty,
.source-empty {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 12px;
  min-height: 76px;
  border: 1px dashed var(--color-border);
  border-radius: 8px;
  background: var(--color-bg-subtle);
  color: var(--color-text-secondary);
}
.binding-empty > svg,
.source-empty > svg {
  width: 20px;
  height: 20px;
  color: var(--color-primary);
  font-size: 24px;
}
.binding-empty div {
  display: grid;
  gap: 3px;
}
.binding-empty strong {
  color: var(--color-text-primary);
  font-size: 13px;
}
.binding-empty span,
.source-empty span {
  font-size: 12px;
  line-height: 18px;
}
.binding-row {
  display: grid;
  grid-template-columns: minmax(0, 1fr) auto auto;
  align-items: center;
  gap: 16px;
  padding: 14px;
  border: 1px solid var(--color-border);
  border-radius: 8px;
  background: var(--color-bg-subtle);
}
.binding-row + .binding-row {
  margin-top: 10px;
}
.binding-row__title,
.binding-row__controls,
.binding-row__tags {
  display: flex;
  align-items: center;
  gap: 10px;
}
.binding-row__title {
  min-width: 0;
}
.binding-row__icon {
  width: 32px;
  height: 32px;
  flex: 0 0 32px;
  font-size: 16px;
}
.binding-row__title > div {
  min-width: 0;
}
.binding-row__title strong {
  display: block;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.binding-row__title > div > span {
  display: block;
  margin-top: 2px;
  color: var(--color-text-secondary);
  font-size: 12px;
}
.binding-row__tags {
  flex: 0 0 auto;
  flex-wrap: wrap;
  justify-content: flex-end;
}
.binding-row__controls {
  flex: 0 0 auto;
  gap: 14px;
}
.binding-row__controls label {
  display: flex;
  align-items: center;
  gap: 7px;
  color: var(--color-text-secondary);
  font-size: 12px;
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
.source-grid {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 10px;
}
.source-card {
  display: flex;
  flex-direction: column;
  min-width: 0;
  padding: 14px;
  border: 1px solid var(--color-border);
  border-radius: 8px;
  background: var(--color-bg-surface);
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
}
.source-card__header h4,
.source-card__header p {
  margin: 0;
}
.source-card__header h4 {
  color: var(--color-text-primary);
  font-size: 15px;
  line-height: 22px;
}
.source-card__header p,
.source-card__empty {
  color: var(--color-text-secondary);
  font-size: 12px;
  line-height: 18px;
}
.source-card__header p {
  margin-top: 2px;
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
}
.source-card__weight span {
  color: var(--color-text-regular);
  font-size: 13px;
}
.source-card__weight strong {
  color: var(--color-primary);
  font-size: 18px;
  line-height: 24px;
}
.source-card__summary {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 8px;
  margin: 0;
  padding: 10px 0 0;
  border-top: 1px solid var(--color-border);
}
.source-card__summary div {
  min-width: 0;
}
.source-card__summary dt,
.source-card__summary dd {
  margin: 0;
  font-size: 12px;
  line-height: 18px;
}
.source-card__summary dt {
  color: var(--color-text-secondary);
}
.source-card__summary dd {
  overflow: hidden;
  color: var(--color-text-regular);
  text-overflow: ellipsis;
  white-space: nowrap;
}
.source-card__availability {
  margin: 10px 0 0;
  color: var(--color-warning, #e6a23c);
  font-size: 12px;
  line-height: 18px;
}
.source-card__availability.is-ready {
  color: var(--color-success, #67c23a);
}
.source-card__empty {
  margin: auto 0 0;
  padding-top: 28px;
}
@media (max-width: 900px) {
  .evaluation-hero,
  .evaluation-selection__content {
    grid-template-columns: 1fr;
  }
  .evaluation-hero__metrics {
    display: grid;
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }
  .binding-row {
    grid-template-columns: 1fr;
    align-items: stretch;
  }
  .binding-row__tags,
  .binding-row__controls {
    justify-content: flex-start;
  }
  .source-grid {
    grid-template-columns: 1fr;
  }
}

.composite-binding-card {
  display: flex;
  min-width: 0;
  flex-direction: column;
  overflow: hidden;
  border: 1px solid var(--color-border);
  border-radius: 10px;
  background: var(--color-bg-surface);
}
.panel-heading {
  display: flex;
  min-height: 92px;
  box-sizing: border-box;
  align-items: flex-start;
  justify-content: space-between;
  gap: 12px;
  padding: 16px 18px;
  border-bottom: 1px solid var(--color-border);
  background: var(--color-bg-surface);
}
.panel-heading h2 {
  margin: 0;
  color: var(--color-text-primary);
  font-size: 16px;
  line-height: 22px;
}
.panel-heading p {
  margin: 3px 0 0;
  color: var(--color-text-secondary);
  font-size: 12px;
  line-height: 18px;
}
.binding-kicker {
  display: block;
  margin-bottom: 2px;
  color: var(--color-primary);
  font-size: 12px;
  font-weight: 700;
  line-height: 18px;
}
.composite-binding-card__body {
  display: grid;
  gap: 14px;
  padding: 16px;
  background: var(--color-bg-subtle);
  overflow: auto;
}
.binding-field {
  display: grid;
  grid-template-columns: 72px minmax(0, 1fr);
  align-items: center;
  gap: 12px;
}
.binding-field > label {
  color: var(--color-text-regular);
  font-size: 13px;
  font-weight: 600;
}
.binding-field__control :deep(.el-select) {
  width: 100%;
}
.binding-summary {
  display: grid;
  grid-template-columns: auto minmax(0, 1fr) auto;
  align-items: center;
  gap: 10px;
  padding: 12px;
  border: 1px solid color-mix(in srgb, var(--color-success, #67c23a) 28%, var(--color-border));
  border-radius: 8px;
  background: color-mix(in srgb, var(--color-success, #67c23a) 5%, var(--color-bg-surface));
}
.binding-summary__icon {
  color: var(--color-success, #67c23a);
  font-size: 20px;
}
.binding-summary__main {
  min-width: 0;
}
.binding-summary__main strong,
.binding-summary__main span {
  display: block;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.binding-summary__main strong {
  color: var(--color-text-primary);
  font-size: 13px;
  line-height: 20px;
}
.binding-summary__main span {
  color: var(--color-text-secondary);
  font-size: 12px;
  line-height: 18px;
}
.binding-summary__switches {
  display: flex;
  gap: 12px;
}
.binding-summary__switches label {
  display: flex;
  align-items: center;
  gap: 6px;
  color: var(--color-text-secondary);
  font-size: 12px;
}
.precheck-panel {
  padding: 14px;
  border: 1px solid color-mix(in srgb, var(--color-warning, #e6a23c) 30%, var(--color-border));
  border-radius: 8px;
  background: color-mix(in srgb, var(--color-warning, #e6a23c) 6%, var(--color-bg-surface));
}
.precheck-panel__header {
  display: flex;
  align-items: flex-start;
  gap: 10px;
}
.precheck-panel__icon {
  display: grid;
  width: 24px;
  height: 24px;
  flex: 0 0 24px;
  place-items: center;
  border-radius: 6px;
  background: color-mix(in srgb, var(--color-warning, #e6a23c) 16%, transparent);
  color: var(--color-warning, #e6a23c);
  font-size: 15px;
}
.precheck-panel__header h3 {
  margin: 0;
  color: var(--color-text-primary);
  font-size: 14px;
  line-height: 20px;
}
.precheck-panel__header p {
  margin: 3px 0 0;
  color: var(--color-text-secondary);
  font-size: 12px;
  line-height: 18px;
}
.precheck-panel__list {
  display: grid;
  gap: 6px;
  margin: 10px 0 0;
  padding-left: 18px;
  color: var(--color-text-regular);
  font-size: 12px;
  line-height: 18px;
}
.source-panel {
  padding: 14px;
  border: 1px solid var(--color-border);
  border-radius: 8px;
  background: var(--color-bg-surface);
}
.source-panel__header {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 12px;
  margin-bottom: 12px;
}
.source-panel__header h3 {
  margin: 0;
  color: var(--color-text-primary);
  font-size: 14px;
  line-height: 20px;
}
.source-panel__header p {
  margin: 3px 0 0;
  color: var(--color-text-secondary);
  font-size: 12px;
  line-height: 18px;
}
.composite-guide {
  margin-bottom: 12px;
}
.source-card {
  padding: 12px;
}
@media (max-width: 720px) {
  .binding-field {
    grid-template-columns: 1fr;
    gap: 6px;
  }
  .binding-summary {
    grid-template-columns: auto minmax(0, 1fr);
  }
  .binding-summary__switches {
    grid-column: 1 / -1;
  }
}
</style>
