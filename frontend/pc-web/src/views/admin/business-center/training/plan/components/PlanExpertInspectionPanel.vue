<script setup lang="ts">
import { computed } from 'vue';
import { CollectionTag, DocumentChecked } from '@element-plus/icons-vue';
import type { EvaluationFormVersionDetail, EvaluationFormVersionSummary } from '@/api/evaluation-form';
import type { TrainingPlanEvaluationForm } from '@/api/training-plan';

/** 专家查课表绑定区独立于综合评价，禁止把来源卡或成绩计算语义带入。 */
const props = defineProps<{
  versions: Array<EvaluationFormVersionSummary & { formName?: string }>;
  bindings: TrainingPlanEvaluationForm[];
  details: Record<number, EvaluationFormVersionDetail>;
  readonly: boolean;
}>();
const emit = defineEmits<{ selectionChange: [id?: number] }>();
const binding = computed(() => props.bindings.find((item) => item.usageType === 'EXPERT_INSPECTION'));
const selectedVersion = computed(() => props.versions.find((item) => item.formVersionId === binding.value?.formVersionId));
const detail = computed(() => (binding.value ? props.details[binding.value.formVersionId] : undefined));
const scoreCount = computed(() => detail.value?.items.filter((item) => item.itemType === 'SCORE').length ?? 0);
const textCount = computed(() => detail.value?.items.filter((item) => item.itemType === 'TEXT').length ?? 0);
const requiredCount = computed(() => detail.value?.items.filter((item) => item.isRequired === 1).length ?? 0);
/** 选择器只改专家检查用途，父工作台保留综合评价绑定。 */
function change(value?: number): void {
  emit('selectionChange', value);
}
</script>

<template>
  <section class="expert-binding">
    <header class="expert-binding__header">
      <div>
        <span class="expert-binding__step">专家查课表</span>
        <h2>专家检查</h2>
        <p>仅可选择已发布、已启用的专家检查版本；它只生成课程现场检查记录，不计入综合成绩。</p>
      </div>
      <ElTag effect="plain" type="success">独立用途</ElTag>
    </header>
    <div class="expert-binding__body">
      <ElSelect
        :model-value="binding?.formVersionId"
        clearable
        filterable
        :disabled="props.readonly"
        placeholder="请选择已发布专家查课表"
        @change="change($event as number | undefined)"
      >
        <ElOption
          v-for="item in props.versions"
          :key="item.formVersionId"
          :label="`${item.formName || '专家查课表'} V${item.versionNo}`"
          :value="item.formVersionId"
        />
      </ElSelect>
      <article v-if="binding" class="expert-binding__summary">
        <div class="expert-binding__title">
          <DocumentChecked />
          <div>
            <strong>{{ selectedVersion?.formName || binding.formName || '专家查课表' }}</strong>
            <span>V{{ selectedVersion?.versionNo ?? binding.versionNo ?? '—' }} · 冻结版本</span>
          </div>
        </div>
        <div class="expert-binding__metrics">
          <div>
            <span>分数项</span>
            <strong>{{ scoreCount }}</strong>
          </div>
          <div>
            <span>意见项</span>
            <strong>{{ textCount }}</strong>
          </div>
          <div>
            <span>总分</span>
            <strong>{{ detail?.totalScore ?? 100 }}</strong>
          </div>
          <div>
            <span>必填项目</span>
            <strong>{{ requiredCount }}</strong>
          </div>
        </div>
      </article>
      <div v-else class="expert-binding__empty">
        <CollectionTag />
        <span>未绑定专家查课表时，不生成课程查课入口。</span>
      </div>
    </div>
  </section>
</template>

<style scoped lang="less">
.expert-binding {
  display: flex;
  min-width: 0;
  flex-direction: column;
  overflow: hidden;
  border: 1px solid var(--color-border);
  border-radius: 10px;
  background: var(--color-bg-surface);
}
.expert-binding__header {
  display: flex;
  min-height: 92px;
  box-sizing: border-box;
  align-items: flex-start;
  justify-content: space-between;
  gap: 12px;
  padding: 13px 18px;
  border-bottom: 1px solid var(--color-border);
  background: var(--color-bg-surface);
}
.expert-binding__step {
  color: var(--color-primary);
  font-size: 12px;
  font-weight: 700;
}
.expert-binding__header h2 {
  margin: 0;
  color: var(--color-text-primary);
  font-size: 16px;
  line-height: 22px;
}
.expert-binding__header p,
.expert-binding__title span,
.expert-binding__empty {
  color: var(--color-text-secondary);
  font-size: 12px;
  line-height: 18px;
}
.expert-binding__header p {
  margin: 3px 0 0;
}
.expert-binding__body {
  display: grid;
  align-content: start;
  gap: 14px;
  padding: 16px;
  background: var(--color-bg-subtle);
}
:deep(.el-select) {
  width: 100%;
}
.expert-binding__summary {
  padding: 14px;
  border: 1px solid color-mix(in srgb, var(--color-success, #67c23a) 30%, var(--color-border));
  border-radius: 8px;
  background: color-mix(in srgb, var(--color-success, #67c23a) 5%, var(--color-bg-surface));
}
.expert-binding__title {
  display: flex;
  align-items: center;
  gap: 10px;
}
.expert-binding__title svg {
  width: 28px;
  color: var(--color-primary);
}
.expert-binding__title strong,
.expert-binding__title span {
  display: block;
}
.expert-binding__metrics {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 8px;
  margin-top: 12px;
}
.expert-binding__metrics div {
  padding: 8px;
  border-radius: 6px;
  background: var(--color-bg-subtle);
}
.expert-binding__metrics span,
.expert-binding__metrics strong {
  display: block;
}
.expert-binding__metrics span {
  color: var(--color-text-secondary);
  font-size: 12px;
}
.expert-binding__metrics strong {
  margin-top: 2px;
  color: var(--color-text-primary);
  font-size: 16px;
}
.expert-binding__empty {
  display: flex;
  align-items: center;
  gap: 8px;
  min-height: 56px;
  padding: 0 12px;
  border: 1px dashed var(--color-border);
  border-radius: 6px;
}
@media (max-width: 1120px) {
  .expert-binding__metrics {
    grid-template-columns: repeat(4, minmax(0, 1fr));
  }
}
@media (max-width: 640px) {
  .expert-binding__metrics {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }
}
</style>
