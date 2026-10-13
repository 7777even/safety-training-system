<script setup lang="ts">
import { computed, reactive, ref, watch } from 'vue';
import { ElMessage } from 'element-plus';
import type { EvaluationFormVersionDetail } from '@/api/evaluation-form';
import {
  getEvaluationRecord,
  getExpertInspections,
  submitExpertInspection,
  type EvaluationRecordDetail,
  type ExpertInspectionSummary
} from '@/api/evaluation-result';

/** 计划运行中可提交一次查课表；已结束计划仅回看已提交记录。 */
const props = defineProps<{
  trainingPlanId: number;
  publishStatus: string;
  formVersionId?: number;
  /** 当前计划已绑定的专家检查评价表单名称。 */
  formName?: string;
  detail?: EvaluationFormVersionDetail;
  canSubmit: boolean;
}>();
const rows = ref<ExpertInspectionSummary[]>([]);
const loading = ref(false);
const submitting = ref(false);
const visible = ref(false);
const current = ref<ExpertInspectionSummary>();
const detailVisible = ref(false);
const recordDetail = ref<EvaluationRecordDetail>();
const answers = reactive<Record<number, { score?: number; textValue?: string }>>({});
const summary = ref('');
const canFill = computed(() => props.publishStatus === 'RUNNING' && props.canSubmit && Boolean(props.formVersionId) && Boolean(props.detail));
/** 绑定或计划变化时重读服务端状态，提交状态以服务端为准。 */
async function load(): Promise<void> {
  if (!props.formVersionId) {
    rows.value = [];
    return;
  }
  loading.value = true;
  try {
    rows.value = await getExpertInspections(props.trainingPlanId);
  } finally {
    loading.value = false;
  }
}
function open(row: ExpertInspectionSummary): void {
  current.value = row;
  summary.value = '';
  Object.keys(answers).forEach((key) => delete answers[Number(key)]);
  props.detail?.items.forEach((item) => {
    answers[item.evaluationItemId] = {};
  });
  visible.value = true;
}
/** 已提交记录沿用评价记录详情接口，只读显示冻结项目答案。 */
async function view(row: ExpertInspectionSummary): Promise<void> {
  if (!row.evaluationRecordId) return;
  recordDetail.value = await getEvaluationRecord(row.evaluationRecordId);
  detailVisible.value = true;
}
function itemAnswerText(itemId: number): string {
  const answer = recordDetail.value?.items.find((item) => item.evaluationItemId === itemId);
  const item = props.detail?.items.find((current) => current.evaluationItemId === itemId);
  return item?.itemType === 'SCORE' ? `${answer?.score ?? '—'} 分` : answer?.textValue || '—';
}
/** 前端先完成必填和范围提示，后端仍执行同一套不可绕过校验。 */
async function submit(): Promise<void> {
  if (!current.value || !props.formVersionId || !props.detail) return;
  const items = props.detail.items.map((item) => ({
    evaluationItemId: item.evaluationItemId,
    score: answers[item.evaluationItemId]?.score,
    textValue: answers[item.evaluationItemId]?.textValue?.trim() || undefined
  }));
  for (const item of props.detail.items) {
    const answer = answers[item.evaluationItemId];
    if (item.isRequired === 1 && (item.itemType === 'SCORE' ? answer?.score == null : !answer?.textValue?.trim())) {
      ElMessage.warning(`请填写：${item.itemName}`);
      return;
    }
    if (item.itemType === 'SCORE' && answer?.score != null && (answer.score < 0 || answer.score > Number(item.maxScore))) {
      ElMessage.warning(`${item.itemName} 必须在 0～${item.maxScore} 分之间`);
      return;
    }
  }
  submitting.value = true;
  try {
    await submitExpertInspection({
      trainingPlanId: props.trainingPlanId,
      planContentId: current.value.planContentId,
      formVersionId: props.formVersionId,
      summary: summary.value.trim() || undefined,
      items
    });
    visible.value = false;
    ElMessage.success('专家查课表已提交，记录不可编辑。');
    await load();
  } finally {
    submitting.value = false;
  }
}
watch(
  () => [props.trainingPlanId, props.formVersionId],
  () => {
    void load();
  },
  { immediate: true }
);
</script>

<template>
  <section class="expert-execution">
    <div class="expert-execution__header">
      <p>仅列出当前计划课程；记录提交后不可编辑，也不参与综合成绩计算。</p>
      <ElTag v-if="props.formVersionId" type="success" effect="plain">{{ props.formName || '专家检查评价表单' }}</ElTag>
    </div>
    <ElAlert v-if="!props.formVersionId" title="当前计划未绑定专家查课表。" type="info" :closable="false" show-icon />
    <ElAlert
      v-if="props.formVersionId && props.publishStatus !== 'RUNNING'"
      title="仅执行中的培训计划可填写专家查课表；当前仅可查看已提交状态。"
      type="info"
      :closable="false"
      show-icon
    />
    <ElTable v-if="props.formVersionId" v-loading="loading" :data="rows" border class="expert-execution__table">
      <ElTableColumn prop="contentName" label="课程内容" min-width="240" />
      <ElTableColumn label="查课状态" width="120">
        <template #default="{ row }">
          <ElTag :type="row.evaluationRecordId ? 'success' : 'info'">{{ row.evaluationRecordId ? '已提交' : '未提交' }}</ElTag>
        </template>
      </ElTableColumn>
      <ElTableColumn label="总分" width="100">
        <template #default="{ row }">{{ row.totalScore ?? '—' }}</template>
      </ElTableColumn>
      <ElTableColumn label="评价人" width="130">
        <template #default="{ row }">{{ row.evaluatorName ?? (row.evaluatorUserId ? `用户 ${row.evaluatorUserId}` : '—') }}</template>
      </ElTableColumn>
      <ElTableColumn prop="submittedTime" label="提交时间" min-width="170" />
      <ElTableColumn label="操作" width="130" fixed="right">
        <template #default="{ row }">
          <ElButton v-if="!row.evaluationRecordId && canFill" link type="primary" @click="open(row as ExpertInspectionSummary)">填写查课表</ElButton>
          <ElButton v-else-if="row.evaluationRecordId" link type="primary" @click="view(row as ExpertInspectionSummary)">查看详情</ElButton>
          <span v-else>—</span>
        </template>
      </ElTableColumn>
    </ElTable>
    <ElDialog v-model="visible" title="填写专家查课表" width="680px" destroy-on-close>
      <ElAlert title="提交后不可编辑，请核对分数和意见。" type="warning" :closable="false" show-icon />
      <ElForm label-position="top" class="expert-execution__form">
        <ElFormItem
          v-for="item in props.detail?.items"
          :key="item.evaluationItemId"
          :label="`${item.itemName}（${item.itemCode}）${item.isRequired === 1 ? ' *' : ''}`"
        >
          <ElInputNumber
            v-if="item.itemType === 'SCORE'"
            v-model="answers[item.evaluationItemId].score"
            :min="0"
            :max="Number(item.maxScore)"
            :precision="2"
            :placeholder="`请输入 0～${item.maxScore} 分`"
            style="width: 200px;"
          />
          <ElInput v-else v-model="answers[item.evaluationItemId].textValue" type="textarea" :rows="3" placeholder="请输入检查意见" />
        </ElFormItem>
        <ElFormItem label="查课摘要"><ElInput v-model="summary" type="textarea" :rows="3" placeholder="可选，补充本次查课总体意见" /></ElFormItem>
      </ElForm>
      <template #footer>
        <div class="expert-execution__footer">
          <span>专家检查只形成课程监督记录，不会折算或写入综合成绩。</span>
          <div>
            <ElButton @click="visible = false">取消</ElButton>
          <ElButton type="primary" :loading="submitting" @click="submit">提交查课表</ElButton>
          </div>
        </div>
      </template>
    </ElDialog>
    <ElDialog v-model="detailVisible" title="专家查课详情" width="640px">
      <ElDescriptions v-if="recordDetail" :column="2" border>
        <ElDescriptionsItem label="总分">{{ recordDetail.totalScore ?? '—' }}</ElDescriptionsItem>
        <ElDescriptionsItem label="查课摘要">{{ recordDetail.summary || '—' }}</ElDescriptionsItem>
      </ElDescriptions>
      <ElTable v-if="recordDetail" :data="props.detail?.items" border class="expert-execution__table">
        <ElTableColumn prop="itemName" label="检查项目" />
        <ElTableColumn label="答案">
          <template #default="{ row }">{{ itemAnswerText(row.evaluationItemId) }}</template>
        </ElTableColumn>
      </ElTable>
    </ElDialog>
  </section>
</template>

<style scoped lang="less">
.expert-execution__header,
.expert-execution__footer {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
}
.expert-execution p,
.expert-execution__footer span {
  margin: 4px 0 0;
  color: var(--color-text-secondary);
  font-size: 12px;
}
.expert-execution__table {
  margin-top: 16px;
}
.expert-execution__form {
  margin-top: 16px;
}
.expert-execution__footer span {
  margin: 0;
}
</style>
