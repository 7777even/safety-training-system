<script setup lang="ts">
import { computed } from 'vue';

interface StandardSummary {
  standardCode: string;
  standardName: string;
  standardVersion: string;
  validRange: string[];
  status: 0 | 1;
}
interface RuleSummary {
  ruleType: string;
  requiredValue?: number;
  unit?: string;
  resourceId?: number;
  status: 0 | 1;
  description: string;
}
interface ResourceOption {
  value: number;
  label: string;
}
const props = defineProps<{
  readonly: boolean;
  canPublish: boolean;
  standard: StandardSummary;
  trainingTypeName: string;
  rules: RuleSummary[];
  courseOptions: ResourceOption[];
  examOptions: ResourceOption[];
}>();
const emit = defineEmits<{ publish: []; back: [] }>();
const ruleTypeText: Record<string, string> = {
  TOTAL_HOURS: '总学时',
  ONLINE_RATIO: '线上学时占比',
  COURSE: '指定课程',
  EXAM: '指定考试',
  OFFLINE: '线下培训'
};
/** 有效期缺失时给出明确文案，避免发布确认页出现空白取值。 */
const validRangeText = computed(() => props.standard.validRange?.filter(Boolean).join(' 至 ') || '未设置');
/** 规则启用情况，供分节标题右侧快速核对是否存在停用规则。 */
const enabledRuleCount = computed(() => props.rules.filter((rule) => rule.status === 1).length);
/** 将规则值转换为发布前易核对的文案。 */
function requirementText(rule: RuleSummary): string {
  if (rule.ruleType === 'COURSE' || rule.ruleType === 'EXAM') {
    const options = rule.ruleType === 'COURSE' ? props.courseOptions : props.examOptions;
    return options.find((item) => item.value === rule.resourceId)?.label ?? (rule.resourceId ? '绑定资源已失效，请重新选择' : '尚未绑定资源');
  }
  return `${rule.requiredValue ?? '-'}${rule.unit ?? (rule.ruleType === 'ONLINE_RATIO' ? '%' : '学时')}`;
}
</script>
<template>
  <ElCard shadow="never" class="admin-detail-page__card">
    <template #header><span>发布确认</span></template>
    <div class="admin-detail-page__form-scroll publish-scroll">
      <ElAlert title="发布后标准及规则将冻结为只读版本，请核对当前配置。" type="warning" :closable="false" show-icon />

      <section class="ws-hero">
        <div class="ws-hero__main">
          <span class="ws-hero__eyebrow">STANDARD RELEASE</span>
          <div class="ws-hero__title-row">
            <h2 class="ws-hero__title">{{ props.standard.standardName || '未命名标准' }}</h2>
          </div>
          <div class="ws-hero__meta">
            <span class="ws-hero__chip">标准编码 {{ props.standard.standardCode || '未设置' }}</span>
            <span class="ws-hero__chip">标准版本 {{ props.standard.standardVersion || '未设置' }}</span>
            <span class="ws-hero__chip">培训类型 {{ props.trainingTypeName }}</span>
          </div>
        </div>
        <div class="ws-hero__aside">
          <div class="ws-hero__state">
            <ElTag :type="props.standard.status === 1 ? 'success' : 'info'" size="small">
              {{ props.standard.status === 1 ? '启用中' : '已停用' }}
            </ElTag>
            <small>有效期 {{ validRangeText }}</small>
          </div>
        </div>
      </section>

      <section class="ws-section">
        <div class="ws-section__head">
          <div>
            <h3>基础信息</h3>
            <p>发布后以下字段将随版本一并冻结。</p>
          </div>
        </div>
        <ElDescriptions :column="2" class="publish-fields">
          <ElDescriptionsItem label="标准名称">{{ props.standard.standardName || '-' }}</ElDescriptionsItem>
          <ElDescriptionsItem label="标准编码">{{ props.standard.standardCode || '-' }}</ElDescriptionsItem>
          <ElDescriptionsItem label="培训类型">{{ props.trainingTypeName }}</ElDescriptionsItem>
          <ElDescriptionsItem label="标准版本">{{ props.standard.standardVersion || '-' }}</ElDescriptionsItem>
          <ElDescriptionsItem label="有效期" :span="2">{{ validRangeText }}</ElDescriptionsItem>
        </ElDescriptions>
      </section>

      <section class="ws-section">
        <div class="ws-section__head">
          <div>
            <h3>规则明细</h3>
            <p>按优先级校验学员的课程、考试与线下培训完成情况。</p>
          </div>
          <span class="ws-section__extra">共 {{ props.rules.length }} 条 · 启用 {{ enabledRuleCount }} 条</span>
        </div>
        <ElTable :data="props.rules" stripe>
          <ElTableColumn label="规则类型" min-width="140">
            <template #default="{ row }">{{ ruleTypeText[(row as RuleSummary).ruleType] || (row as RuleSummary).ruleType }}</template>
          </ElTableColumn>
          <ElTableColumn label="要求" min-width="140">
            <template #default="{ row }">{{ requirementText(row as RuleSummary) }}</template>
          </ElTableColumn>
          <ElTableColumn label="状态" width="100">
            <template #default="{ row }">
              <span class="rule-state" :class="(row as RuleSummary).status === 1 ? 'rule-state--on' : 'rule-state--off'">
                {{ (row as RuleSummary).status === 1 ? '启用' : '停用' }}
              </span>
            </template>
          </ElTableColumn>
          <ElTableColumn prop="description" label="说明" min-width="220" />
          <template #empty><ElEmpty description="尚未配置规则" /></template>
        </ElTable>
      </section>
    </div>
    <footer class="sticky-actions">
      <ElButton @click="emit('back')">上一步：规则编排</ElButton>
      <ElButton v-if="!props.readonly && props.canPublish" type="success" @click="emit('publish')">发布标准</ElButton>
    </footer>
  </ElCard>
</template>
<style scoped lang="less">
.publish-scroll {
  display: flex;
  flex-direction: column;
  gap: 16px;
}
/* 描述列表在分节卡内降噪：弱化标签、加重取值，避免与外层卡片重复描边。 */
.publish-fields :deep(.el-descriptions__label) {
  width: 88px;
  color: var(--color-text-secondary);
  font-weight: 400;
}
.publish-fields :deep(.el-descriptions__content) {
  color: var(--color-text-primary);
  font-variant-numeric: tabular-nums;
  font-weight: 500;
}
.publish-fields :deep(.el-descriptions__cell) {
  padding-bottom: 12px;
}
/* 规则状态用轻量药丸替代纯文字，停止使用的规则一眼可辨。 */
.rule-state {
  display: inline-block;
  padding: 1px 9px;
  border-radius: 999px;
  font-size: 12px;
  line-height: 20px;
}
.rule-state--on {
  background: #dcfce7;
  color: #15803d;
}
.rule-state--off {
  background: var(--color-bg-subtle);
  color: var(--color-text-secondary);
}
</style>
