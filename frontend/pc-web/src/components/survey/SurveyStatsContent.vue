<script setup lang="ts">
import { computed } from 'vue';
import type { SurveyStats } from '@/api/m4-outcome';

/**
 * 问卷统计只读展示块。
 *
 * 管理端「查看统计」与填写人「填写后可见」共用同一呈现口径：有效答卷数、各题选项
 * 分布与文本回答。数据全部来自服务端统计接口，组件不参与任何计算口径的改写。
 */
type QuestionStats = SurveyStats['questionStats'][number];

const props = defineProps<{ stats?: SurveyStats }>();

/** 选项计数按题内合计换算百分比，用于展示分布，不改变原始计数。 */
const questions = computed<QuestionStats[]>(() => props.stats?.questionStats ?? []);

function questionTotal(question: QuestionStats): number {
  return question.optionCounts.reduce((sum, option) => sum + option.count, 0);
}

function optionPercent(question: QuestionStats, count: number): number {
  const total = questionTotal(question);
  return total ? Math.round((count / total) * 100) : 0;
}
</script>
<template>
  <div class="stats">
    <div class="stats__summary">
      <div class="ws-metric ws-metric--accent">
        <span class="ws-metric__label">有效答卷</span>
        <span class="ws-metric__value">{{ props.stats?.totalSubmitted ?? 0 }}<em>份</em></span>
      </div>
      <p class="ws-hint">统计仅包含正式提交的答卷，同一填写人的重复提交由服务端拒绝。</p>
    </div>
    <ElEmpty v-if="!questions.length" description="暂无答卷统计">
      <span class="ws-empty__hint">当前投放还没有有效提交，选项分布和文本回答会在收到答卷后出现。</span>
    </ElEmpty>
    <section v-for="(question, index) in questions" :key="question.surveyQuestionId" class="ws-item-card">
      <header class="stats__head">
        <span class="ws-index">Q{{ index + 1 }}</span>
        <h3>{{ question.questionText }}</h3>
      </header>
      <div v-if="question.optionCounts.length" class="stats__options">
        <div v-for="option in question.optionCounts" :key="option.optionContent" class="stats__option">
          <div class="stats__option-row">
            <span class="stats__option-label">{{ option.optionContent }}</span>
            <span class="stats__option-count">
              {{ option.count }}<em> · {{ optionPercent(question, option.count) }}%</em>
            </span>
          </div>
          <div class="ws-bar__track">
            <div class="ws-bar__fill" :style="{ width: `${optionPercent(question, option.count)}%` }" />
          </div>
        </div>
      </div>
      <ul v-if="question.textAnswers.length" class="stats__texts">
        <li v-for="(answer, answerIndex) in question.textAnswers" :key="answerIndex">{{ answer }}</li>
      </ul>
      <p v-if="!question.optionCounts.length && !question.textAnswers.length" class="stats__empty">暂无回答</p>
    </section>
  </div>
</template>
<style scoped lang="less">
.stats {
  display: grid;
  gap: var(--space-4);
}

.stats__summary {
  display: grid;
  gap: var(--space-2);
  grid-template-columns: minmax(160px, 220px) minmax(0, 1fr);
  align-items: center;
}

.stats__head {
  display: flex;
  min-width: 0;
  align-items: baseline;
  gap: var(--space-2);
}

.stats__head h3 {
  margin: 0;
  color: var(--color-text-primary);
  font-size: 14px;
  font-weight: 600;
  line-height: 22px;
  overflow-wrap: anywhere;
}

.stats__options {
  display: grid;
  gap: 10px;
}

.stats__option {
  display: grid;
  gap: 4px;
}

.stats__option-row {
  display: flex;
  min-width: 0;
  align-items: baseline;
  justify-content: space-between;
  gap: var(--space-3);
  font-size: 13px;
  line-height: 20px;
}

.stats__option-label {
  color: var(--color-text-regular);
  overflow-wrap: anywhere;
}

.stats__option-count {
  flex: 0 0 auto;
  color: var(--color-text-primary);
  font-variant-numeric: tabular-nums;
  font-weight: 600;
}

.stats__option-count em {
  color: var(--color-text-secondary);
  font-style: normal;
  font-weight: 400;
}

.stats__texts {
  display: grid;
  gap: 6px;
  margin: 0;
  padding: 0;
  list-style: none;
}

.stats__texts li {
  padding: 8px 10px;
  border-radius: var(--radius-control);
  background: var(--color-bg-subtle);
  color: var(--color-text-regular);
  font-size: 13px;
  line-height: 20px;
  overflow-wrap: anywhere;
}

.stats__empty {
  margin: 0;
  color: var(--color-text-secondary);
  font-size: 12px;
}

@media (max-width: 720px) {
  .stats__summary {
    grid-template-columns: minmax(0, 1fr);
  }
}
</style>
