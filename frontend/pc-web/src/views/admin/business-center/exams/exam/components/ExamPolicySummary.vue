<script setup lang="ts">
import type { ExamItem } from '@/api/exam';
import { ANSWER_DETAIL_MODE_OPTIONS, RETAKE_SCOPE_OPTIONS, RETAKE_SCORE_MODE_OPTIONS, getExamEnumLabel } from '@/constants/exam-options';

defineProps<{ exam: ExamItem }>();

/** 将策略开关转换为管理端可读状态，避免直接展示 0/1。 */
function getPolicySwitchLabel(value?: number): string {
  if (value === 1) return '已启用';
  if (value === 0) return '未启用';
  return '未设置';
}

/** 汇总可作答终端，避免暴露内部开关字段。 */
function getAllowedClientLabel(exam: ExamItem): string {
  const policy = exam.policy;
  const clients = [policy?.isPcAllowed === 1 ? 'PC' : '', policy?.isAppAllowed === 1 ? 'App' : '', policy?.isH5Allowed === 1 ? 'H5' : ''].filter(
    Boolean
  );
  return clients.length ? clients.join('、') : '未设置';
}
</script>

<template>
  <ElDescriptions title="考试策略" :column="3" border class="exam-policy-summary">
    <ElDescriptionsItem label="允许作答端">{{ getAllowedClientLabel(exam) }}</ElDescriptionsItem>
    <ElDescriptionsItem label="允许补考">{{ getPolicySwitchLabel(exam.policy?.isRetakeEnabled) }}</ElDescriptionsItem>
    <template v-if="exam.policy?.isRetakeEnabled === 1">
      <ElDescriptionsItem label="补考范围">{{ getExamEnumLabel(exam.policy?.retakeScope, RETAKE_SCOPE_OPTIONS) }}</ElDescriptionsItem>
      <ElDescriptionsItem label="补考开放时间">
        {{ exam.policy?.retakeStartTime || '不限制' }} 至 {{ exam.policy?.retakeEndTime || '不限制' }}
      </ElDescriptionsItem>
      <ElDescriptionsItem label="最大补考次数">{{ exam.policy?.retakeMaxCount ?? '未设置' }}</ElDescriptionsItem>
      <ElDescriptionsItem label="补考成绩方式">{{ getExamEnumLabel(exam.policy?.retakeScoreMode, RETAKE_SCORE_MODE_OPTIONS) }}</ElDescriptionsItem>
      <ElDescriptionsItem label="显示补考记录">{{ getPolicySwitchLabel(exam.policy?.isRetakeRecordVisible) }}</ElDescriptionsItem>
    </template>
    <ElDescriptionsItem label="答案详情展示方式">
      {{ getExamEnumLabel(exam.policy?.answerDetailMode, ANSWER_DETAIL_MODE_OPTIONS) }}
    </ElDescriptionsItem>
    <ElDescriptionsItem label="展示试题解析">{{ getPolicySwitchLabel(exam.policy?.isAnalysisVisible) }}</ElDescriptionsItem>
    <ElDescriptionsItem label="展示成绩">{{ getPolicySwitchLabel(exam.policy?.isScoreVisible) }}</ElDescriptionsItem>
  </ElDescriptions>

  <ElDescriptions title="考试防作弊" :column="3" border class="exam-policy-summary">
    <ElDescriptionsItem label="启用考试防作弊">{{ getPolicySwitchLabel(exam.policy?.isAnticheatEnabled) }}</ElDescriptionsItem>
    <ElDescriptionsItem label="水印">{{ getPolicySwitchLabel(exam.policy?.isWatermarkEnabled) }}</ElDescriptionsItem>
  </ElDescriptions>
</template>

<style scoped lang="less">
/* 策略明细沿用字段表格，但与外层分组卡保持同一圆角、标签底色与标题层级。 */
.exam-policy-summary {
  margin-top: var(--space-4);
  border-radius: 10px;
  overflow: hidden;
}

.exam-policy-summary :deep(.el-descriptions__header) {
  margin-bottom: 10px;
}

.exam-policy-summary :deep(.el-descriptions__title) {
  color: var(--color-text-primary);
  font-size: 15px;
  font-weight: 600;
}

.exam-policy-summary :deep(.el-descriptions__label) {
  color: var(--color-text-secondary);
  font-weight: 600;
  background: var(--color-bg-subtle);
}
</style>
