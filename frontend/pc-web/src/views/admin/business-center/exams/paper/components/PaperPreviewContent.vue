<script setup lang="ts">
import type { Question, QuestionBank } from '@/api/question';
import type { PaperDrawRulePayload, PaperPreview } from '@/api/paper';
import { getExamEnumLabel, PAPER_TYPE_OPTIONS, QUESTION_TYPE_OPTIONS } from '@/constants/exam-options';

/** 试卷预览所需的题目、题库和随机规则数据。 */
const props = defineProps<{
  preview?: PaperPreview;
  showSummary?: boolean;
  drawRules: PaperDrawRulePayload[];
  questionDetails: Record<number, Question>;
  questionBanks: QuestionBank[];
}>();

/** 按题目 ID 读取题库列表未包含的题干和编码。 */
function questionDetail(questionId: number): Question | undefined {
  return props.questionDetails[questionId];
}

/** 将题库 ID 转换为预览可读的题库名称，缺少来源信息时按占位符展示。 */
function bankName(questionBankId?: number): string {
  if (!questionBankId) return '—';
  return props.questionBanks.find((item) => item.questionBankId === questionBankId)?.bankName ?? `题库 #${questionBankId}`;
}

/** 题型标签配色，帮助阅卷人快速区分客观题与主观题。 */
function questionTypeTag(type?: string): 'primary' | 'success' | 'warning' | 'danger' | 'info' {
  if (type === 'SINGLE') return 'primary';
  if (type === 'MULTIPLE') return 'success';
  if (type === 'JUDGE') return 'info';
  if (type === 'FILL') return 'warning';
  return 'danger';
}

/** 选择题在预览中直接展示选项与正确项，减少来回跳转。 */
function isChoiceType(type?: string): boolean {
  return type === 'SINGLE' || type === 'MULTIPLE';
}

/** 选项是否被标记为正确项，isCorrect 为 1 表示正确。 */
function isCorrectOption(isCorrect: number | undefined): boolean {
  return isCorrect === 1;
}
</script>

<template>
  <ElEmpty v-if="!preview" description="暂无可预览的试卷数据" />
  <template v-else>
    <section v-if="showSummary !== false" class="preview-summary">
      <div class="preview-summary__item">
        <span class="preview-summary__label">总分</span>
        <strong class="preview-summary__value">{{ preview.totalScore }}<em>分</em></strong>
      </div>
      <div class="preview-summary__item">
        <span class="preview-summary__label">及格分</span>
        <strong class="preview-summary__value">{{ preview.passScore }}<em>分</em></strong>
      </div>
      <div class="preview-summary__item">
        <span class="preview-summary__label">题目数</span>
        <strong class="preview-summary__value">{{ preview.questionCount }}<em>题</em></strong>
      </div>
      <div class="preview-summary__item">
        <span class="preview-summary__label">组卷类型</span>
        <strong class="preview-summary__value preview-summary__value--text">{{ getExamEnumLabel(preview.paperType, PAPER_TYPE_OPTIONS) }}</strong>
      </div>
    </section>

    <section class="preview-block">
      <header class="preview-block__head">
        <h4>试卷内容</h4>
        <span v-if="preview.questions?.length" class="preview-block__count">共 {{ preview.questions.length }} 题</span>
      </header>

      <ol v-if="preview.questions?.length" class="question-list">
        <li v-for="(row, index) in preview.questions" :key="row.paperQuestionId ?? row.questionId" class="question-item">
          <div class="question-item__head">
            <span class="question-item__index">{{ index + 1 }}</span>
            <ElTag size="small" effect="light" :type="questionTypeTag(row.questionType)">
              {{ getExamEnumLabel(row.questionType, QUESTION_TYPE_OPTIONS) }}
            </ElTag>
            <span class="question-item__code">{{ questionDetail(row.questionId)?.questionCode ?? `#${row.questionId}` }}</span>
            <span class="question-item__score">{{ row.score }} 分</span>
          </div>
          <p class="question-item__stem">{{ questionDetail(row.questionId)?.currentVersion?.stem ?? '—' }}</p>
          <ul v-if="isChoiceType(row.questionType) && questionDetail(row.questionId)?.currentVersion?.options?.length" class="option-list">
            <li
              v-for="option in questionDetail(row.questionId)?.currentVersion?.options"
              :key="option.optionKey"
              class="option-item"
              :class="{ 'is-correct': isCorrectOption(option.isCorrect) }"
            >
              <span class="option-item__key">{{ option.optionKey }}</span>
              <span class="option-item__text">{{ option.optionContent }}</span>
              <span v-if="isCorrectOption(option.isCorrect)" class="option-item__flag">正确</span>
            </li>
          </ul>
          <div class="question-item__foot">
            <span>来源题库：{{ bankName(row.sourceBankId) }}</span>
            <span>排序：{{ row.sortOrder }}</span>
          </div>
        </li>
      </ol>

      <div v-else class="question-empty">
        <ElEmpty description="随机题目将在发布时确定，当前展示抽题规则。" :image-size="72" />
      </div>
    </section>

    <section v-if="drawRules.length" class="preview-block">
      <header class="preview-block__head">
        <h4>随机抽题规则</h4>
        <span class="preview-block__count">共 {{ drawRules.length }} 条</span>
      </header>
      <div class="rule-grid">
        <article v-for="rule in drawRules" :key="rule.sortOrder" class="rule-card">
          <span class="rule-card__no">规则 {{ rule.sortOrder }}</span>
          <p class="rule-card__bank">{{ bankName(rule.questionBankId) }}</p>
          <div class="rule-card__metrics">
            <span>{{ getExamEnumLabel(rule.questionType, QUESTION_TYPE_OPTIONS) }}</span>
            <span>抽 {{ rule.drawCount }} 题</span>
            <span>每题 {{ rule.scorePerQuestion }} 分</span>
          </div>
        </article>
      </div>
    </section>
  </template>
</template>

<style scoped lang="less">
.preview-summary {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: 12px;
}

.preview-summary__item {
  display: flex;
  flex-direction: column;
  gap: 6px;
  padding: 14px 16px;
  border: 1px solid var(--color-border);
  border-radius: 10px;
  background: var(--color-bg-subtle);
}

.preview-summary__label {
  color: var(--color-text-secondary);
  font-size: 12px;
}

.preview-summary__value {
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

.preview-summary__value--text {
  font-size: 16px;
}

.preview-block {
  margin-top: 20px;
}

.preview-block__head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 12px;

  h4 {
    position: relative;
    margin: 0;
    padding-left: 10px;
    color: var(--color-text-primary);
    font-size: 15px;
    font-weight: 600;

    &::before {
      position: absolute;
      top: 50%;
      left: 0;
      width: 3px;
      height: 14px;
      border-radius: 2px;
      background: var(--color-primary);
      content: '';
      transform: translateY(-50%);
    }
  }
}

.preview-block__count {
  color: var(--color-text-secondary);
  font-size: 12px;
}

.question-list {
  display: flex;
  margin: 0;
  padding: 0;
  flex-direction: column;
  gap: 12px;
  list-style: none;
}

.question-item {
  padding: 14px 16px;
  border: 1px solid var(--color-border);
  border-radius: var(--radius-card);
  background: var(--color-bg-surface);
  transition: border-color 0.2s, box-shadow 0.2s;

  &:hover {
    border-color: var(--color-primary);
    box-shadow: 0 4px 14px rgb(37 99 235 / 8%);
  }
}

.question-item__head {
  display: flex;
  align-items: center;
  gap: 8px;
}

.question-item__index {
  display: inline-flex;
  width: 22px;
  height: 22px;
  align-items: center;
  justify-content: center;
  border-radius: 6px;
  background: var(--color-primary-subtle);
  color: var(--color-primary);
  font-size: 12px;
  font-weight: 600;
}

.question-item__code {
  color: var(--color-text-secondary);
  font-family: ui-monospace, SFMono-Regular, Menlo, monospace;
  font-size: 12px;
}

.question-item__score {
  margin-left: auto;
  padding: 2px 8px;
  border-radius: 999px;
  background: var(--color-bg-subtle);
  color: var(--color-text-regular);
  font-size: 12px;
  font-weight: 600;
}

.question-item__stem {
  margin: 10px 0 0;
  color: var(--color-text-primary);
  font-size: 14px;
  line-height: 1.6;
}

.option-list {
  display: grid;
  margin: 10px 0 0;
  padding: 0;
  gap: 6px 16px;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  list-style: none;
}

.option-item {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 6px 10px;
  border: 1px solid var(--color-border);
  border-radius: 8px;
  color: var(--color-text-regular);
  font-size: 13px;

  &.is-correct {
    border-color: var(--color-primary);
    background: var(--color-primary-subtle);
    color: var(--color-text-primary);
  }
}

.option-item__key {
  display: inline-flex;
  width: 18px;
  height: 18px;
  flex: none;
  align-items: center;
  justify-content: center;
  border-radius: 50%;
  background: var(--color-bg-subtle);
  color: var(--color-text-secondary);
  font-size: 11px;

  .option-item.is-correct & {
    background: var(--color-primary);
    color: #fff;
  }
}

.option-item__text {
  overflow: hidden;
  flex: 1;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.option-item__flag {
  flex: none;
  color: var(--color-primary);
  font-size: 11px;
  font-weight: 600;
}

.question-item__foot {
  display: flex;
  margin-top: 10px;
  gap: 16px;
  color: var(--color-text-secondary);
  font-size: 12px;
}

.question-empty {
  padding: 8px 0 0;
}

.rule-grid {
  display: grid;
  gap: 12px;
  grid-template-columns: repeat(auto-fill, minmax(240px, 1fr));
}

.rule-card {
  padding: 14px 16px;
  border: 1px solid var(--color-border);
  border-radius: var(--radius-card);
  background: var(--color-bg-surface);
}

.rule-card__no {
  display: inline-block;
  padding: 2px 8px;
  border-radius: 999px;
  background: var(--color-primary-subtle);
  color: var(--color-primary);
  font-size: 12px;
  font-weight: 600;
}

.rule-card__bank {
  margin: 10px 0 6px;
  color: var(--color-text-primary);
  font-size: 14px;
  font-weight: 600;
}

.rule-card__metrics {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;

  span {
    padding: 2px 8px;
    border-radius: 6px;
    background: var(--color-bg-subtle);
    color: var(--color-text-secondary);
    font-size: 12px;
  }
}

@media (width <= 720px) {
  .preview-summary {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }

  .option-list {
    grid-template-columns: minmax(0, 1fr);
  }
}
</style>
