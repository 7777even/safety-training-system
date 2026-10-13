<script setup lang="ts">
/* eslint-disable vue/no-mutating-props -- 父工作台持有项目与规则草稿。 */
import { computed } from 'vue';
import type {
  EvaluationCalculationType,
  EvaluationItemRequest,
  EvaluationItemType,
  EvaluationScoreRuleRequest,
  EvaluationSourceType
} from '@/api/evaluation-form';

/** 项目与规则步骤仅编辑父工作台传入的草稿，不自行调用接口。 */
const props = defineProps<{
  items: EvaluationItemRequest[];
  scoreRules: EvaluationScoreRuleRequest[];
  itemTypeOptions: Array<{ label: string; value: EvaluationItemType }>;
  calculationOptions: Array<{ label: string; value: EvaluationCalculationType }>;
  sourceOptions: Array<{ label: string; value: EvaluationSourceType }>;
  isComposite: boolean;
  isExpertInspection: boolean;
  validationMessage: string;
}>();
const emit = defineEmits<{
  addItem: [];
  removeItem: [index: number];
  addRuleForItem: [itemCode: string];
  removeRule: [index: number];
  addCompositeSource: [sourceType: EvaluationSourceType];
  removeCompositeSource: [sourceType: EvaluationSourceType];
  back: [];
  next: [];
}>();

/** 固定来源卡片只读取三类计划正式成绩来源，避免项目与规则被重复配置。 */
const compositeSourceCards = computed(() =>
  props.sourceOptions.map((source) => ({ ...source, rule: props.scoreRules.find((rule) => rule.sourceType === source.value) }))
);
/** 已选来源的表单层权重合计，页面用其直接提示是否闭合。 */
const compositeWeightTotal = computed(() => props.scoreRules.reduce((total, rule) => total + (rule.weight ?? 0), 0));

function hasRuleForItem(itemCode: string): boolean {
  return props.scoreRules.some((rule) => rule.evaluationItemCode === itemCode);
}
function isItemDisabledForRule(itemCode: string, ruleIndex: number): boolean {
  return props.scoreRules.some((rule, index) => index !== ruleIndex && rule.evaluationItemCode === itemCode);
}
/** 文本项只记录意见，切换后清除不参与计分的满分和项目权重。 */
function handleItemTypeChange(index: number, itemType: EvaluationItemType): void {
  if (itemType !== 'TEXT') return;
  props.items[index].maxScore = null;
  props.items[index].weight = null;
}
/** 计算方式决定唯一可录入的参数，切换时清除另一种计算方式的遗留值。 */
function handleCalculationTypeChange(index: number, calculationType: EvaluationCalculationType): void {
  if (calculationType === 'WEIGHT') {
    props.scoreRules[index].scoreChange = null;
    return;
  }
  props.scoreRules[index].weight = null;
}
</script>

<template>
  <ElCard shadow="never" class="admin-detail-page__card evaluation-items-panel">
    <template #header>
      <div class="card-heading">
        <div>
          <strong>项目与规则</strong>
          <span>
            {{
              props.isComposite
                ? '选择纳入综合成绩的来源并配置表单层权重；项目和规则会自动生成。'
                : props.isExpertInspection
                  ? '配置专家现场检查项目；分数项满分合计固定为 100，文本项用于填写意见。'
                  : '先定义评价项目，再配置其成绩来源和计算方式；发布时将整体固化为版本。'
            }}
          </span>
        </div>
      </div>
    </template>
    <div class="admin-detail-page__form-scroll">
      <template v-if="props.isComposite">
        <section class="composite-guide" aria-label="综合评价计算说明">
          <strong>计算方式</strong>
          <span>计划内同类内容先汇总，再按本表单配置的来源权重计算综合成绩。</span>
        </section>
        <section class="composite-sources" aria-label="综合成绩来源配置">
          <div class="card-header">
            <div>
              <h2>综合成绩来源配置</h2>
              <p>
                <span class="required-label">
                  <i>*</i>
                  成绩来源
                </span>
                与
                <span class="required-label">
                  <i>*</i>
                  表单层权重
                </span>
                为必填项；每类来源只能配置一次。
              </p>
            </div>
          </div>
          <ElAlert
            v-if="props.validationMessage"
            :title="props.validationMessage"
            type="error"
            :closable="false"
            show-icon
            class="validation-message"
          />
          <div class="source-card-grid">
            <article v-for="source in compositeSourceCards" :key="source.value" class="source-card" :class="{ 'is-selected': source.rule }">
              <div class="source-card__header">
                <div>
                  <h3>{{ source.label }}</h3>
                  <p>使用计划内已发布的正式成绩</p>
                </div>
                <ElTag :type="source.rule ? 'success' : 'info'">{{ source.rule ? '已纳入' : '未纳入' }}</ElTag>
              </div>
              <template v-if="source.rule">
                <div class="source-card__field">
                  <label class="required-label">
                    <i>*</i>
                    表单层权重
                  </label>
                  <ElInputNumber v-model="source.rule.weight" :min="0" :max="1" :step="0.1" :precision="2" placeholder="请输入 0～1" />
                  <small>例如 0.60，表示该来源占综合成绩的 60%</small>
                </div>
                <dl class="source-card__summary">
                  <div>
                    <dt>评价项目</dt>
                    <dd>{{ props.items.find((item) => item.itemCode === source.rule?.evaluationItemCode)?.itemName || '自动生成' }}</dd>
                  </div>
                  <div>
                    <dt>计算方式</dt>
                    <dd>权重折算</dd>
                  </div>
                  <div>
                    <dt>项目要求</dt>
                    <dd>满分 100，必填</dd>
                  </div>
                </dl>
                <ElButton link type="danger" @click="emit('removeCompositeSource', source.value)">移出综合成绩</ElButton>
              </template>
              <div v-else class="source-card__empty">
                <span>不纳入时不会影响综合成绩计算。</span>
                <ElButton type="primary" plain @click="emit('addCompositeSource', source.value)">纳入综合成绩</ElButton>
              </div>
            </article>
          </div>
          <div class="weight-summary" :class="{ 'is-invalid': Math.abs(compositeWeightTotal - 1) > 0.000001 }">
            <strong>权重合计</strong>
            <span>{{ Number((compositeWeightTotal * 100).toFixed(2)) }}%</span>
            <small>{{ Math.abs(compositeWeightTotal - 1) > 0.000001 ? '需调整为 100% 后才能进入发布确认。' : '权重已闭合，可进入发布确认。' }}</small>
          </div>
        </section>
      </template>
      <template v-else-if="props.isExpertInspection">
        <section class="editor-section" aria-label="专家查课项目配置">
          <div class="card-header">
            <div>
              <h2>检查项目配置</h2>
              <p>仅支持分数项和文本意见项；不配置成绩来源、自动规则或项目权重。</p>
            </div>
            <ElButton type="primary" plain @click="emit('addItem')">新增项目</ElButton>
          </div>
          <ElAlert
            v-if="props.validationMessage"
            :title="props.validationMessage"
            type="error"
            :closable="false"
            show-icon
            class="validation-message"
          />
          <ElEmpty v-if="!props.items.length" description="暂无检查项目，请先新增项目" class="editor-empty" />
          <div v-else class="editor-table-wrap">
            <ElTable :data="props.items" border class="editor-table editor-table--expert" row-key="itemCode">
              <ElTableColumn min-width="150">
                <template #header>
                  <span class="required-label">
                    <i>*</i>
                    项目编码
                  </span>
                </template>
                <template #default="{ $index }"><ElInput v-model="props.items[$index].itemCode" placeholder="例如 COURSE_CONTENT" /></template>
              </ElTableColumn>
              <ElTableColumn min-width="200">
                <template #header>
                  <span class="required-label">
                    <i>*</i>
                    项目名称
                  </span>
                </template>
                <template #default="{ $index }"><ElInput v-model="props.items[$index].itemName" placeholder="请输入检查项目名称" /></template>
              </ElTableColumn>
              <ElTableColumn min-width="140">
                <template #header>
                  <span class="required-label">
                    <i>*</i>
                    类型
                  </span>
                </template>
                <template #default="{ $index }">
                  <ElSelect v-model="props.items[$index].itemType" placeholder="请选择类型" @change="handleItemTypeChange($index, $event)">
                    <ElOption label="分数项" value="SCORE" />
                    <ElOption label="文本意见项" value="TEXT" />
                  </ElSelect>
                </template>
              </ElTableColumn>
              <ElTableColumn label="满分" min-width="160">
                <template #default="{ $index }">
                  <ElInputNumber
                    v-model="props.items[$index].maxScore"
                    :min="0"
                    :disabled="props.items[$index].itemType === 'TEXT'"
                    :placeholder="props.items[$index].itemType === 'TEXT' ? '文本项不计分' : '请输入满分'"
                  />
                </template>
              </ElTableColumn>
              <ElTableColumn label="必填" width="84">
                <template #default="{ $index }"><ElSwitch v-model="props.items[$index].isRequired" :active-value="1" :inactive-value="0" /></template>
              </ElTableColumn>
              <ElTableColumn label="排序" min-width="120">
                <template #default="{ $index }"><ElInputNumber v-model="props.items[$index].sortOrder" :min="1" /></template>
              </ElTableColumn>
              <ElTableColumn label="操作" min-width="88" fixed="right">
                <template #default="{ $index }"><ElButton link type="danger" @click="emit('removeItem', $index)">删除</ElButton></template>
              </ElTableColumn>
            </ElTable>
          </div>
        </section>
      </template>
      <template v-else>
        <section class="editor-section">
          <div class="card-header">
            <div>
              <h2>评价项目</h2>
              <p>项目编码是规则的唯一关联依据，请保持稳定且不重复。</p>
            </div>
            <ElButton type="primary" plain @click="emit('addItem')">新增项目</ElButton>
          </div>
          <ElAlert
            v-if="props.validationMessage"
            :title="props.validationMessage"
            type="error"
            :closable="false"
            show-icon
            class="validation-message"
          />
          <ElEmpty v-if="!props.items.length" description="暂无项目，请先新增评价项目" class="editor-empty" />
          <div v-else class="editor-table-wrap">
            <ElTable :data="props.items" border class="editor-table editor-table--items" row-key="itemCode">
              <ElTableColumn min-width="150">
                <template #header>
                  <span class="required-label">
                    <i>*</i>
                    项目编码
                  </span>
                </template>
                <template #default="{ $index }"><ElInput v-model="props.items[$index].itemCode" placeholder="例如 THEORY" /></template>
              </ElTableColumn>
              <ElTableColumn min-width="180">
                <template #header>
                  <span class="required-label">
                    <i>*</i>
                    项目名称
                  </span>
                </template>
                <template #default="{ $index }"><ElInput v-model="props.items[$index].itemName" placeholder="请输入项目名称" /></template>
              </ElTableColumn>
              <ElTableColumn min-width="140">
                <template #header>
                  <span class="required-label">
                    <i>*</i>
                    类型
                  </span>
                </template>
                <template #default="{ $index }">
                  <ElSelect v-model="props.items[$index].itemType" placeholder="请选择类型" @change="handleItemTypeChange($index, $event)">
                    <ElOption v-for="option in props.itemTypeOptions" :key="option.value" :label="option.label" :value="option.value" />
                  </ElSelect>
                </template>
              </ElTableColumn>
              <ElTableColumn label="满分" min-width="150">
                <template #default="{ $index }">
                  <div class="calculation-parameter-field">
                    <span v-if="props.items[$index].itemType !== 'TEXT'" class="calculation-parameter-field__hint">取分范围：0～100（如：60）</span>
                    <ElInputNumber
                      v-model="props.items[$index].maxScore"
                      :min="0"
                      :disabled="props.items[$index].itemType === 'TEXT'"
                      :placeholder="props.items[$index].itemType === 'TEXT' ? '不参与计分' : '请输入满分'"
                    />
                  </div>
                </template>
              </ElTableColumn>
              <ElTableColumn label="权重" min-width="150">
                <template #default="{ $index }">
                  <div class="calculation-parameter-field">
                    <span v-if="props.items[$index].itemType !== 'TEXT'" class="calculation-parameter-field__hint">权重范围：0～1（如: 0.6）</span>
                    <ElInputNumber
                      v-model="props.items[$index].weight"
                      :min="0"
                      :max="1"
                      :step="0.1"
                      :disabled="props.items[$index].itemType === 'TEXT'"
                      :placeholder="props.items[$index].itemType === 'TEXT' ? '不参与计分' : '请输入权重'"
                    />
                  </div>
                </template>
              </ElTableColumn>
              <ElTableColumn label="必填" width="80">
                <template #default="{ $index }"><ElSwitch v-model="props.items[$index].isRequired" :active-value="1" :inactive-value="0" /></template>
              </ElTableColumn>
              <ElTableColumn label="排序" min-width="140">
                <template #default="{ $index }"><ElInputNumber v-model="props.items[$index].sortOrder" :min="1" /></template>
              </ElTableColumn>
              <ElTableColumn label="规则状态" min-width="130">
                <template #default="{ row }">
                  <ElTag v-if="hasRuleForItem(row.itemCode)" type="success">已配置规则</ElTag>
                  <ElTag v-else type="info">未配置规则</ElTag>
                </template>
              </ElTableColumn>
              <ElTableColumn label="操作" min-width="150" fixed="right">
                <template #default="{ row, $index }">
                  <div class="row-actions">
                    <ElButton v-if="!hasRuleForItem(row.itemCode)" link type="primary" @click="emit('addRuleForItem', row.itemCode)">
                      设置规则
                    </ElButton>
                    <ElButton link type="danger" @click="emit('removeItem', $index)">删除</ElButton>
                  </div>
                </template>
              </ElTableColumn>
            </ElTable>
          </div>
        </section>
        <section class="editor-section">
          <div class="card-header">
            <div>
              <h2>评分规则</h2>
              <p>每条规则从项目行发起创建，并说明成绩折算或调整方式。</p>
            </div>
          </div>
          <ElEmpty v-if="!props.scoreRules.length" description="暂无自动评分规则，可直接进入发布确认" class="editor-empty" />
          <div v-else class="editor-table-wrap">
            <ElTable :data="props.scoreRules" border class="editor-table editor-table--rules">
              <ElTableColumn min-width="150">
                <template #header>
                  <span class="required-label">
                    <i>*</i>
                    关联项目
                  </span>
                </template>
                <template #default="{ $index }">
                  <ElSelect v-model="props.scoreRules[$index].evaluationItemCode" placeholder="请选择项目">
                    <ElOption
                      v-for="item in props.items"
                      :key="item.itemCode"
                      :disabled="isItemDisabledForRule(item.itemCode, $index)"
                      :label="item.itemName || item.itemCode"
                      :value="item.itemCode"
                    />
                  </ElSelect>
                </template>
              </ElTableColumn>
              <ElTableColumn min-width="150">
                <template #header>
                  <span class="required-label">
                    <i>*</i>
                    计算方式
                  </span>
                </template>
                <template #default="{ $index }">
                  <ElSelect
                    v-model="props.scoreRules[$index].calculationType"
                    placeholder="请选择方式"
                    @change="handleCalculationTypeChange($index, $event)"
                  >
                    <ElOption v-for="option in props.calculationOptions" :key="option.value" :label="option.label" :value="option.value" />
                  </ElSelect>
                </template>
              </ElTableColumn>
              <ElTableColumn min-width="150">
                <template #header>
                  <span class="required-label">
                    <i>*</i>
                    成绩来源
                  </span>
                </template>
                <template #default="{ $index }">
                  <ElSelect v-model="props.scoreRules[$index].sourceType" placeholder="请选择来源">
                    <ElOption v-for="option in props.sourceOptions" :key="option.value" :label="option.label" :value="option.value" />
                  </ElSelect>
                </template>
              </ElTableColumn>
              <ElTableColumn min-width="160">
                <template #header>
                  <span class="required-label">
                    <i>*</i>
                    计算参数
                  </span>
                </template>
                <template #default="{ $index }">
                  <div class="calculation-parameter-field">
                    <span v-if="props.scoreRules[$index].calculationType === 'WEIGHT'" class="calculation-parameter-field__hint">
                      权重：0～1（如: 0.6）
                    </span>
                    <span v-else class="calculation-parameter-field__hint">加减分：正数加分，负数扣分</span>
                    <ElInputNumber
                      v-if="props.scoreRules[$index].calculationType === 'WEIGHT'"
                      v-model="props.scoreRules[$index].weight"
                      :min="0"
                      :max="1"
                      :step="0.1"
                      placeholder="请输入 0～1"
                    />
                    <ElInputNumber v-else v-model="props.scoreRules[$index].scoreChange" placeholder="例如 -5 或 5" />
                  </div>
                </template>
              </ElTableColumn>
              <ElTableColumn min-width="220">
                <template #header>
                  <span class="required-label">
                    <i>*</i>
                    规则说明
                  </span>
                </template>
                <template #default="{ $index }">
                  <ElInput v-model="props.scoreRules[$index].ruleDescription" placeholder="说明折算或调整规则" />
                </template>
              </ElTableColumn>
              <ElTableColumn label="操作" min-width="88">
                <template #default="{ $index }"><ElButton link type="danger" @click="emit('removeRule', $index)">删除</ElButton></template>
              </ElTableColumn>
            </ElTable>
          </div>
        </section>
      </template>
    </div>
    <footer class="sticky-actions">
      <ElButton @click="emit('back')">返回基础信息</ElButton>
      <ElButton type="primary" @click="emit('next')">确认当前编排并进入发布确认</ElButton>
    </footer>
  </ElCard>
</template>

<style scoped lang="less">
.evaluation-items-panel {
  .card-heading strong,
  h2,
  h3 {
    color: var(--color-text-primary);
  }
  .card-heading strong {
    display: block;
    font-size: 16px;
    font-weight: 600;
    line-height: 24px;
  }
  .card-heading span,
  .card-header p {
    display: block;
    margin: 2px 0 0;
    color: var(--color-text-secondary);
    font-size: 13px;
    line-height: 20px;
  }
  .composite-guide {
    display: flex;
    gap: 10px;
    margin-bottom: 16px;
    padding: 12px 14px;
    color: var(--color-primary);
    background: var(--color-primary-subtle);
    border: 1px solid #bfdbfe;
    border-radius: 8px;
    font-size: 13px;
    line-height: 20px;
  }
  .composite-sources,
  .editor-section {
    padding: 14px;
    background: var(--color-bg-subtle);
    border: 1px solid var(--color-border);
    border-radius: 8px;
  }
  .editor-section + .editor-section {
    margin-top: 16px;
  }
  .card-header {
    display: flex;
    align-items: flex-start;
    justify-content: space-between;
    gap: 16px;
    margin-bottom: 14px;
  }
  h2 {
    margin: 0;
    font-size: 16px;
    font-weight: 600;
    line-height: 24px;
  }
  .validation-message {
    margin: 0 0 14px;
  }
  .source-card-grid {
    display: grid;
    grid-template-columns: repeat(3, minmax(0, 1fr));
    gap: 10px;
  }
  .source-card {
    display: flex;
    flex-direction: column;
    padding: 12px;
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
  }
  .source-card h3 {
    margin: 0;
    font-size: 15px;
    line-height: 22px;
  }
  .source-card p,
  .source-card small {
    margin: 2px 0 0;
    color: var(--color-text-secondary);
    font-size: 12px;
    line-height: 16px;
  }
  .source-card__field {
    display: grid;
    grid-template-columns: 110px minmax(0, 1fr);
    align-items: center;
    gap: 4px 8px;
    margin: 10px 0 8px;
  }
  .source-card__field small {
    grid-column: 2;
  }
  .source-card__field :deep(.el-input-number) {
    width: 100%;
  }
  .source-card__summary {
    display: grid;
    grid-template-columns: repeat(3, minmax(0, 1fr));
    gap: 8px;
    margin: 0 0 8px;
    padding: 8px 0;
    border-top: 1px solid var(--color-border);
    border-bottom: 1px solid var(--color-border);
  }
  .source-card__summary div {
    display: flex;
    flex-direction: column;
    gap: 2px;
    font-size: 12px;
    line-height: 18px;
  }
  .source-card__summary dt {
    color: var(--color-text-secondary);
  }
  .source-card__summary dd {
    margin: 0;
    color: var(--color-text-regular);
  }
  .source-card__empty {
    display: flex;
    flex-direction: column;
    align-items: flex-start;
    gap: 10px;
    padding-top: 18px;
    color: var(--color-text-secondary);
    font-size: 12px;
    line-height: 18px;
  }
  .weight-summary {
    display: flex;
    align-items: center;
    gap: 8px;
    margin-top: 10px;
    padding: 9px 12px;
    color: #15803d;
    background: #f0fdf4;
    border: 1px solid #bbf7d0;
    border-radius: 8px;
  }
  .weight-summary span {
    font-size: 18px;
    font-weight: 600;
  }
  .weight-summary small {
    color: inherit;
    font-size: 12px;
  }
  .weight-summary.is-invalid {
    color: var(--color-danger);
    background: #fef2f2;
    border-color: #fecaca;
  }
  .required-label {
    color: var(--color-text-regular);
    font-weight: 500;
  }
  .required-label i {
    margin-right: 4px;
    color: var(--color-danger);
    font-style: normal;
  }
  .editor-empty {
    padding: 28px 0 20px;
    background: var(--color-bg-surface);
    border: 1px dashed var(--color-border);
    border-radius: 6px;
  }
  .editor-table {
    background: var(--color-bg-surface);
    border-radius: 6px;
    :deep(.el-input-number) {
      width: 100%;
    }
  }
  .calculation-parameter-field {
    display: flex;
    flex: 1;
    flex-direction: column;
    gap: 4px;
  }
  .calculation-parameter-field__hint {
    color: var(--color-text-secondary);
    font-size: 12px;
    line-height: 16px;
    white-space: nowrap;
  }
  .editor-table-wrap {
    overflow-x: auto;
    background: var(--color-bg-surface);
    border-radius: 6px;
  }
  .row-actions {
    display: flex;
    align-items: center;
    gap: 10px;
    white-space: nowrap;
  }
  .editor-table--items {
    min-width: 1360px;
  }
  .editor-table--rules {
    min-width: 1160px;
  }
  .editor-table--expert {
    min-width: 980px;
  }
  @media (max-width: 1200px) {
    .source-card-grid {
      grid-template-columns: 1fr;
    }
    .source-card {
      min-height: auto;
    }
  }
}
</style>
