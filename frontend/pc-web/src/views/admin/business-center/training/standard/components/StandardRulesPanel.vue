<script setup lang="ts">
/** 标准规则行。 */
interface RuleRow {
  ruleType: 'TOTAL_HOURS' | 'ONLINE_RATIO' | 'COURSE' | 'EXAM' | 'OFFLINE';
  requiredValue?: number;
  unit?: string;
  resourceId?: number;
  sortOrder: number;
  status: 0 | 1;
  description: string;
}
interface Option {
  label: string;
  value: string | number;
}
const props = defineProps<{
  rules: RuleRow[];
  readonly: boolean;
  canEdit: boolean;
  saving: boolean;
  canNext: boolean;
  baselineText: string;
  summaryText: string;
  onlineRatioMaxPercent?: number;
  ruleTypeOptions: Option[];
  courseOptions: Option[];
  examOptions: Option[];
}>();
const emit = defineEmits<{ add: []; remove: [index: number]; 'rule-type-change': [row: RuleRow]; save: []; next: []; dirty: [] }>();
/** 按规则类型返回可绑定的资源选项。 */
function resourceOptions(type: RuleRow['ruleType']): Option[] {
  return type === 'COURSE' ? props.courseOptions : type === 'EXAM' ? props.examOptions : [];
}
/** 单条基础规则已被其他行使用时，从当前行下拉选项中排除，避免新增重复规则。 */
function ruleTypeOptionsFor(row: RuleRow): Option[] {
  const usedSingletonTypes = new Set(
    props.rules.filter((item) => item !== row && ['TOTAL_HOURS', 'ONLINE_RATIO'].includes(item.ruleType)).map((item) => item.ruleType)
  );
  return props.ruleTypeOptions.filter((option) => !usedSingletonTypes.has(option.value as RuleRow['ruleType']) || option.value === row.ruleType);
}
/** 指定课程和指定考试只需要资源，不需要额外数值。 */
function needsValue(type: RuleRow['ruleType']): boolean {
  return type !== 'COURSE' && type !== 'EXAM';
}
/** 单位由规则类型决定，避免输入与业务含义不一致。 */
function unitText(type: RuleRow['ruleType']): string {
  if (type === 'ONLINE_RATIO') return '%';
  if (type === 'TOTAL_HOURS' || type === 'OFFLINE') return '学时';
  return '—';
}
</script>
<template>
  <ElCard shadow="never" class="admin-detail-page__card">
    <template #header>
      <div class="card-header">
        <div>
          <h2>规则编排</h2>
          <p>规则按顺序覆盖保存，发布后不可修改。</p>
        </div>
        <ElButton v-if="props.canEdit" type="primary" plain @click="emit('add')">添加规则</ElButton>
      </div>
    </template>
    <div class="admin-detail-page__form-scroll rules-scroll">
      <ElAlert
        class="rules-baseline"
        title="培训类型基线与当前规则"
        :description="`${props.baselineText}。${props.summaryText}`"
        type="info"
        :closable="false"
        show-icon
      />
      <p class="required-hint">
        <span aria-hidden="true">*</span>
        表示必填；总学时和线上学时占比规则各只能配置一条，要求值和指定资源是否必填取决于规则类型。
      </p>
      <p v-if="props.rules.some((rule) => rule.ruleType === 'ONLINE_RATIO')" class="ratio-guidance">
        <b>线上占比填写说明</b>
        <span>按百分数填写，例如 30 表示 30%。</span>
        <span v-if="props.onlineRatioMaxPercent !== undefined">
          当前培训类型最高 {{ props.onlineRatioMaxPercent }}%，标准可设置为 0%～{{ props.onlineRatioMaxPercent }}%。
        </span>
      </p>
      <ElTable :data="props.rules" row-key="sortOrder" class="rules-table">
        <ElTableColumn min-width="150">
          <template #header>
            规则类型
            <i class="required-asterisk" aria-hidden="true">*</i>
          </template>
          <template #default="{ row }">
            <ElSelect
              v-model="row.ruleType"
              :disabled="props.readonly"
              placeholder="请选择规则类型"
              @change="emit('rule-type-change', row as RuleRow)"
            >
              <ElOption v-for="option in ruleTypeOptionsFor(row as RuleRow)" :key="option.value" :label="option.label" :value="option.value" />
            </ElSelect>
          </template>
        </ElTableColumn>
        <ElTableColumn label="要求值（按类型）" width="190">
          <template #default="{ row }">
            <div v-if="needsValue(row.ruleType as RuleRow['ruleType'])" class="ratio-field">
              <ElInputNumber
                v-model="row.requiredValue"
                :disabled="props.readonly"
                :min="0"
                :max="row.ruleType === 'ONLINE_RATIO' ? (props.onlineRatioMaxPercent ?? 100) : undefined"
                :precision="row.ruleType === 'ONLINE_RATIO' ? 1 : 1"
                @change="emit('dirty')"
              />
            </div>
            <span v-else class="muted-text">无需填写要求值</span>
          </template>
        </ElTableColumn>
        <ElTableColumn label="单位" width="100">
          <template #default="{ row }">
            <span
              class="unit-text"
              :class="{ 'is-ratio': row.ruleType === 'ONLINE_RATIO' }"
              :title="row.ruleType === 'ONLINE_RATIO' ? '线上学时占比固定为 %' : ''"
            >
              {{ unitText(row.ruleType as RuleRow['ruleType']) }}
            </span>
          </template>
        </ElTableColumn>
        <ElTableColumn label="指定资源（按类型）" min-width="210">
          <template #default="{ row }">
            <ElTag v-if="!['COURSE', 'EXAM'].includes(row.ruleType)" type="info">无需绑定资源</ElTag>
            <ElSelect v-else v-model="row.resourceId" :disabled="props.readonly" filterable placeholder="请选择已发布资源" @change="emit('dirty')">
              <ElOption
                v-for="item in resourceOptions(row.ruleType as RuleRow['ruleType'])"
                :key="item.value"
                :label="item.label"
                :value="item.value"
              />
            </ElSelect>
          </template>
        </ElTableColumn>
        <ElTableColumn width="110">
          <template #header>
            排序
            <i class="required-asterisk" aria-hidden="true">*</i>
          </template>
          <template #default="{ row }">
            <ElInputNumber v-model="row.sortOrder" :disabled="props.readonly" :min="0" controls-position="right" @change="emit('dirty')" />
          </template>
        </ElTableColumn>
        <ElTableColumn width="130">
          <template #header>
            状态
            <i class="required-asterisk" aria-hidden="true">*</i>
          </template>
          <template #default="{ row }">
            <ElSelect v-model="row.status" :disabled="props.readonly" @change="emit('dirty')">
              <ElOption label="启用" :value="1" />
              <ElOption label="停用" :value="0" />
            </ElSelect>
          </template>
        </ElTableColumn>
        <ElTableColumn min-width="220">
          <template #header>
            说明
            <i class="required-asterisk" aria-hidden="true">*</i>
          </template>
          <template #default="{ row }">
            <ElInput v-model="row.description" :disabled="props.readonly" placeholder="请输入规则说明" @input="emit('dirty')" />
          </template>
        </ElTableColumn>
        <ElTableColumn v-if="props.canEdit" label="操作" width="80">
          <template #default="{ $index }"><ElButton link type="danger" @click="emit('remove', $index)">移除</ElButton></template>
        </ElTableColumn>
        <template #empty><ElEmpty description="暂无规则，请先添加" /></template>
      </ElTable>
    </div>
    <footer class="sticky-actions">
      <ElButton v-if="props.canEdit" type="primary" :loading="props.saving" @click="emit('save')">保存规则</ElButton>
      <ElButton type="primary" :disabled="!props.canNext" @click="emit('next')">下一步：发布确认</ElButton>
    </footer>
  </ElCard>
</template>
<style scoped lang="less">
.rules-scroll {
  padding: 4px 2px 16px;
}
.rules-baseline {
  margin-bottom: 12px;
}
.required-hint {
  margin: 0 0 10px;
  color: var(--color-text-secondary);
  font-size: 12px;
}
.required-hint span {
  color: var(--el-color-danger);
  font-weight: 600;
}
.required-asterisk {
  color: var(--el-color-danger);
  font-style: normal;
  font-weight: 600;
}
.rules-table :deep(.el-table__cell) {
  padding: 10px 0;
}
.rules-table :deep(.el-select),
.rules-table :deep(.el-input-number) {
  width: 100%;
}
.ratio-field {
  width: 100%;
}
.ratio-guidance {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 6px 12px;
  margin: 0 0 12px;
  padding: 10px 12px;
  border-left: 3px solid var(--color-primary);
  border-radius: 0 var(--radius-control) var(--radius-control) 0;
  color: var(--color-text-secondary);
  background: var(--color-primary-subtle);
  font-size: 12px;
  line-height: 18px;
}
.ratio-guidance b {
  color: var(--color-text-primary);
}
.muted-text {
  color: var(--color-text-secondary);
  font-size: 13px;
}
.unit-text {
  display: inline-flex;
  min-width: 52px;
  justify-content: center;
  padding: 5px 8px;
  color: var(--color-text-regular);
  background: var(--color-bg-subtle);
  border: 1px solid var(--color-border);
  border-radius: 6px;
}
.unit-text.is-ratio {
  color: var(--color-primary);
  background: var(--color-primary-subtle);
  border-color: #bfdbfe;
}
</style>
