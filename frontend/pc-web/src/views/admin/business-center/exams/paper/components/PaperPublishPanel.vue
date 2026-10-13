<script setup lang="ts">
import { ref } from 'vue';
import { Promotion } from '@element-plus/icons-vue';
import type { Question, QuestionBank } from '@/api/question';
import type { PaperDrawRulePayload, PaperPreview } from '@/api/paper';
import PaperPreviewContent from './PaperPreviewContent.vue';
import PaperPreviewDialog from './PaperPreviewDialog.vue';

const props = defineProps<{
  preview?: PaperPreview;
  drawRules: PaperDrawRulePayload[];
  questionDetails: Record<number, Question>;
  questionBanks: QuestionBank[];
}>();
const emit = defineEmits<{ compose: []; publish: [] }>();
const previewVisible = ref(false);
</script>

<template>
  <ElCard shadow="never" class="publish-stage admin-detail-page__card admin-detail-card-spacing">
    <template #header>
      <div class="publish-stage__head">
        <span class="publish-stage__icon"><ElIcon :size="16"><Promotion /></ElIcon></span>
        <div>
          <h3>发布确认</h3>
          <p>发布后将固化当前题目版本，已发布版本仅供查看。</p>
        </div>
      </div>
    </template>

    <ElResult
      v-if="!props.preview"
      icon="info"
      title="当前阶段尚未完成预览"
      sub-title="请返回组卷配置，保存草稿并成功预览后再发布试卷版本。"
    >
      <template #extra><ElButton type="primary" @click="emit('compose')">返回组卷配置</ElButton></template>
    </ElResult>

    <template v-else>
      <div class="admin-detail-page__form-scroll">
        <section class="publish-summary">
          <div class="publish-summary__item">
            <span class="publish-summary__label">题目数</span>
            <strong class="publish-summary__value">{{ props.preview.questionCount }}<em>题</em></strong>
          </div>
          <div class="publish-summary__item">
            <span class="publish-summary__label">总分</span>
            <strong class="publish-summary__value">{{ props.preview.totalScore }}<em>分</em></strong>
          </div>
          <div class="publish-summary__item">
            <span class="publish-summary__label">及格分</span>
            <strong class="publish-summary__value">{{ props.preview.passScore }}<em>分</em></strong>
          </div>
        </section>

        <PaperPreviewContent
          :preview="props.preview"
          :show-summary="false"
          :draw-rules="props.drawRules"
          :question-details="props.questionDetails"
          :question-banks="props.questionBanks"
        />

        <div class="publish-note">
          <ElIcon class="publish-note__icon"><Promotion /></ElIcon>
          <span>发布后将固定当前题目版本，后续对题库的修改不会影响该试卷版本。</span>
        </div>
      </div>

      <footer class="admin-detail-action-bar">
        <ElButton @click="previewVisible = true">查看预览</ElButton>
        <ElButton type="primary" @click="emit('publish')">确认发布版本</ElButton>
      </footer>
    </template>

    <PaperPreviewDialog
      v-model="previewVisible"
      :preview="props.preview"
      :draw-rules="props.drawRules"
      :question-details="props.questionDetails"
      :question-banks="props.questionBanks"
    />
  </ElCard>
</template>

<style scoped lang="less">
.publish-stage__head {
  display: flex;
  align-items: flex-start;
  gap: 10px;

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
    font-weight: 400;
  }
}

.publish-stage__icon {
  display: inline-flex;
  width: 32px;
  height: 32px;
  flex: none;
  align-items: center;
  justify-content: center;
  border-radius: 9px;
  background: var(--color-primary-subtle);
  color: var(--color-primary);
}

.publish-summary {
  display: grid;
  gap: 12px;
  grid-template-columns: repeat(3, minmax(0, 1fr));
}

.publish-summary__item {
  display: flex;
  flex-direction: column;
  gap: 6px;
  padding: 14px 16px;
  border: 1px solid var(--color-border);
  border-radius: 10px;
  background: var(--color-bg-subtle);
}

.publish-summary__label {
  color: var(--color-text-secondary);
  font-size: 12px;
}

.publish-summary__value {
  color: var(--color-text-primary);
  font-size: 22px;
  font-weight: 600;
  line-height: 1.2;

  em {
    margin-left: 2px;
    color: var(--color-text-secondary);
    font-size: 12px;
    font-style: normal;
    font-weight: 400;
  }
}

.publish-note {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-top: 16px;
  padding: 10px 14px;
  border: 1px solid #fde68a;
  border-radius: var(--radius-card);
  background: #fffbeb;
  color: #92400e;
  font-size: 13px;
}

.publish-note__icon {
  flex: none;
}
</style>
