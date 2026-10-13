<script setup lang="ts">
import { Document } from '@element-plus/icons-vue';
import type { Question, QuestionBank } from '@/api/question';
import type { PaperDrawRulePayload, PaperPreview } from '@/api/paper';
import PaperPreviewContent from './PaperPreviewContent.vue';

const props = defineProps<{
  modelValue: boolean;
  preview?: PaperPreview;
  drawRules: PaperDrawRulePayload[];
  questionDetails: Record<number, Question>;
  questionBanks: QuestionBank[];
}>();
const emit = defineEmits<{ 'update:modelValue': [value: boolean] }>();
</script>

<template>
  <ElDialog
    class="paper-preview-dialog"
    :model-value="modelValue"
    width="1040px"
    top="6vh"
    destroy-on-close
    :lock-scroll="false"
    @update:model-value="emit('update:modelValue', $event)"
  >
    <template #header>
      <div class="preview-dialog__head">
        <span class="preview-dialog__icon"><ElIcon :size="18"><Document /></ElIcon></span>
        <div class="preview-dialog__meta">
          <h3>试卷预览</h3>
          <p>以下为组卷配置的实时快照，发布后将固化为不可变更的试卷版本。</p>
        </div>
      </div>
    </template>
    <div class="preview-dialog__body">
      <PaperPreviewContent
        :preview="props.preview"
        :draw-rules="props.drawRules"
        :question-details="props.questionDetails"
        :question-banks="props.questionBanks"
      />
    </div>
    <template #footer>
      <ElButton type="primary" @click="emit('update:modelValue', false)">关闭</ElButton>
    </template>
  </ElDialog>
</template>

<style scoped lang="less">
.preview-dialog__head {
  display: flex;
  align-items: flex-start;
  gap: 12px;
}

.preview-dialog__icon {
  display: inline-flex;
  width: 36px;
  height: 36px;
  flex: none;
  align-items: center;
  justify-content: center;
  border-radius: var(--radius-card);
  background: var(--color-primary-subtle);
  color: var(--color-primary);
}

.preview-dialog__meta {
  h3 {
    margin: 0;
    color: var(--color-text-primary);
    font-size: 16px;
    font-weight: 600;
  }

  p {
    margin: 4px 0 0;
    color: var(--color-text-secondary);
    font-size: 12px;
  }
}

.preview-dialog__body {
  max-height: 62vh;
  padding-right: 4px;
  overflow-y: auto;
}
</style>

<style lang="less">
.paper-preview-dialog {
  border-radius: 14px;

  .el-dialog__header {
    margin-right: 0;
    padding: 18px 20px 14px;
    border-bottom: 1px solid var(--color-border);
  }

  .el-dialog__body {
    padding: 18px 20px;
  }

  .el-dialog__footer {
    padding: 12px 20px;
    border-top: 1px solid var(--color-border);
  }
}
</style>
