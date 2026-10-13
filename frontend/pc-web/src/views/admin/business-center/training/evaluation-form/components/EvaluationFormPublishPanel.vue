<script setup lang="ts">
/* eslint-disable vue/no-mutating-props -- 父工作台持有发布元数据草稿。 */
import { ref } from 'vue';
import type { FormInstance, FormRules } from 'element-plus';
import type {
  EvaluationCalculationType,
  EvaluationItemRequest,
  EvaluationItemType,
  EvaluationScoreRuleRequest,
  EvaluationSourceType
} from '@/api/evaluation-form';
const props = defineProps<{
  formName: string;
  items: EvaluationItemRequest[];
  scoreRules: EvaluationScoreRuleRequest[];
  meta: { totalScore: number; versionNote: string };
  rules: FormRules;
  canPublish: boolean;
  publishing: boolean;
  itemTypeText: (type: EvaluationItemType) => string;
  calculationText: (type: EvaluationCalculationType) => string;
  sourceText: (type: EvaluationSourceType) => string;
}>();
const emit = defineEmits<{ back: []; publish: [] }>();
const formRef = ref<FormInstance>();

/** 发布快照中用项目名称补充规则关联，便于最终核对。 */
function ruleItemName(itemCode: string): string {
  const item = props.items.find((current) => current.itemCode === itemCode);
  return item ? `${item.itemName || item.itemCode}（${item.itemCode}）` : itemCode;
}

/** 发布前由面板完成版本元数据校验。 */
async function submit(): Promise<void> {
  if (await formRef.value?.validate().catch(() => false)) emit('publish');
}
</script>
<template>
  <ElCard shadow="never" class="admin-detail-page__card evaluation-publish-panel">
    <template #header>
      <div class="card-heading">
        <div>
          <strong>确认发布</strong>
          <span>核对本次将被冻结的完整快照，并补充版本说明。</span>
        </div>
      </div>
    </template>
    <div class="admin-detail-page__form-scroll">
      <ElAlert
        class="publish-alert"
        title="发布后版本内容不可修改；如需调整，请返回工作台重新编排并发布新版本。"
        type="warning"
        :closable="false"
        show-icon
      />

      <section class="publish-section publish-section--overview">
        <div class="section-heading">
          <div>
            <h2>发布摘要</h2>
            <p>确认当前表单及其项目、规则数量。</p>
          </div>
        </div>
        <div class="publish-overview-grid">
          <div class="overview-item overview-item--wide">
            <span>表单名称</span>
            <strong>{{ props.formName || '未命名表单' }}</strong>
          </div>
          <div class="overview-item">
            <span>评价项目</span>
            <strong>{{ props.items.length }} 项</strong>
          </div>
          <div class="overview-item">
            <span>评分规则</span>
            <strong>{{ props.scoreRules.length }} 条</strong>
          </div>
        </div>
      </section>

      <section class="publish-section">
        <div class="section-heading">
          <div>
            <h2>版本设置</h2>
            <p>版本说明用于识别本次发布范围或调整内容。</p>
          </div>
        </div>
        <ElForm ref="formRef" :model="props.meta" :rules="props.rules" label-position="top" class="publish-form">
          <ElRow :gutter="24">
            <ElCol :span="4">
              <ElFormItem label="版本总分" prop="totalScore" required>
                <ElInputNumber v-model="props.meta.totalScore" :min="0" :precision="2" :controls="false" placeholder="请输入版本总分" />
              </ElFormItem>
            </ElCol>
            <ElCol :span="20">
              <ElFormItem label="版本说明">
                <ElInput v-model="props.meta.versionNote" type="textarea" placeholder="说明本次版本的适用范围或调整内容" />
              </ElFormItem>
            </ElCol>
          </ElRow>
        </ElForm>
      </section>

      <section class="publish-section">
        <div class="section-heading">
          <div>
            <h2>评价项目快照</h2>
            <p>以下项目将随本次发布冻结。</p>
          </div>
        </div>
        <ElTable :data="props.items" border class="snapshot-table">
          <ElTableColumn prop="itemCode" label="项目编码" min-width="180" />
          <ElTableColumn prop="itemName" label="项目名称" min-width="180" />
          <ElTableColumn label="类型" min-width="120">
            <template #default="{ row }">{{ props.itemTypeText(row.itemType) }}</template>
          </ElTableColumn>
          <ElTableColumn label="满分" min-width="110">
            <template #default="{ row }">{{ row.maxScore ?? '—' }}</template>
          </ElTableColumn>
          <ElTableColumn label="权重" min-width="110">
            <template #default="{ row }">{{ row.weight ?? '—' }}</template>
          </ElTableColumn>
          <ElTableColumn label="规则状态" min-width="120">
            <template #default="{ row }">
              <ElTag v-if="props.scoreRules.some((rule) => rule.evaluationItemCode === row.itemCode)" type="success">已配置</ElTag>
              <ElTag v-else type="info">无自动规则</ElTag>
            </template>
          </ElTableColumn>
        </ElTable>
      </section>

      <section class="publish-section">
        <div class="section-heading">
          <div>
            <h2>评分规则快照</h2>
            <p>以下计算规则将随本次发布冻结。</p>
          </div>
        </div>
        <ElEmpty v-if="!props.scoreRules.length" description="无自动评分规则" class="publish-empty" />
        <ElTable v-else :data="props.scoreRules" border class="snapshot-table">
          <ElTableColumn label="关联项目" min-width="220">
            <template #default="{ row }">{{ ruleItemName(row.evaluationItemCode) }}</template>
          </ElTableColumn>
          <ElTableColumn label="计算方式" min-width="140">
            <template #default="{ row }">{{ props.calculationText(row.calculationType) }}</template>
          </ElTableColumn>
          <ElTableColumn label="成绩来源" min-width="140">
            <template #default="{ row }">{{ props.sourceText(row.sourceType) }}</template>
          </ElTableColumn>
          <ElTableColumn prop="ruleDescription" label="规则说明" min-width="280" />
        </ElTable>
      </section>
    </div>
    <footer class="sticky-actions">
      <ElButton @click="emit('back')">返回调整</ElButton>
      <ElButton v-if="props.canPublish" type="primary" :loading="props.publishing" @click="submit">确认发布版本</ElButton>
    </footer>
  </ElCard>
</template>

<style scoped lang="less">
.evaluation-publish-panel {
  .card-heading strong {
    display: block;
    color: var(--color-text-primary);
    font-size: 16px;
    font-weight: 600;
    line-height: 24px;
  }

  .card-heading span,
  .section-heading p {
    display: block;
    margin: 2px 0 0;
    color: var(--color-text-secondary);
    font-size: 13px;
    line-height: 20px;
  }

  .publish-alert {
    margin-bottom: 16px;
  }

  .publish-section {
    padding: 16px;
    background: var(--color-bg-subtle);
    border: 1px solid var(--color-border);
    border-radius: 8px;

    & + .publish-section {
      margin-top: 16px;
    }
  }

  .section-heading {
    margin-bottom: 14px;

    h2 {
      margin: 0;
      color: var(--color-text-primary);
      font-size: 16px;
      font-weight: 600;
      line-height: 24px;
    }
  }

  .snapshot-table {
    background: var(--color-bg-surface);
  }

  .publish-overview-grid {
    display: grid;
    grid-template-columns: minmax(0, 2fr) repeat(2, minmax(150px, 1fr));
    gap: 12px;
  }

  .overview-item {
    min-width: 0;
    padding: 14px 16px;
    background: var(--color-bg-surface);
    border: 1px solid var(--color-border);
    border-radius: 6px;

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
      font-size: 18px;
      font-weight: 600;
      line-height: 26px;
      text-overflow: ellipsis;
      white-space: nowrap;
    }
  }

  .publish-empty {
    padding: 24px 0 18px;
    background: var(--color-bg-surface);
    border: 1px dashed var(--color-border);
    border-radius: 6px;
  }

  .publish-form :deep(.el-form-item) {
    margin-bottom: 0;
  }

  .publish-form :deep(.el-input-number) {
    width: 100%;
  }

  @media (max-width: 960px) {
    .publish-overview-grid {
      grid-template-columns: 1fr;
    }
  }
}
</style>
