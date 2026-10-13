<script setup lang="ts">
import type { LearnerExamAttempt, LearnerExamQuestion } from '@shared/types';
import { computed, onMounted, ref } from 'vue';
import { useRoute, useRouter } from 'vue-router';
import { getLearnerExamAttempt } from '@/api/learner-exam';

const route = useRoute();
const router = useRouter();
const attempt = ref<LearnerExamAttempt | null>(null);
const errorMessage = ref('');
/** 培训题库练习仅展示反馈，不沿用考试成绩与及格结论。 */
const isTrainingPractice = computed(() => attempt.value?.sourceType === 'TRAINING_PRACTICE');
const hasPracticeFeedback = computed(() => isTrainingPractice.value && Boolean(attempt.value?.submittedTime));
const showScoredResult = computed(() =>
  !isTrainingPractice.value && attempt.value?.scoreVisible && ['PUBLISHED', 'EFFECTIVE'].includes(attempt.value.scorePublishStatus)
);

/** 选择题优先显示正确选项及其快照内容，文本题保留 Contract 的标准答案。 */
function getStandardAnswer(question: LearnerExamQuestion): string {
  const correctOptions = question.options.filter((option) => option.isCorrectSnapshot === 1);
  if (correctOptions.length > 0) {
    return correctOptions.map((option) => `${option.optionKey}. ${option.optionContentSnapshot ?? option.optionContent ?? ''}`).join('\n');
  }
  return question.standardAnswer || '未提供';
}

/** 服务端裁剪答案字段时，不渲染标准答案占位内容。 */
function hasStandardAnswer(question: LearnerExamQuestion): boolean {
  return Boolean(question.standardAnswer || question.options.some((option) => option.isCorrectSnapshot === 1));
}

/** 只有服务端返回答案详情字段时才显示逐题复盘区域。 */
function hasAnswerReview(question: LearnerExamQuestion): boolean {
  return Boolean(
    hasStandardAnswer(question) ||
    question.analysisSnapshot ||
    question.answer?.evaluationResult ||
    (question.answer?.finalScore !== null && question.answer?.finalScore !== undefined) ||
    (question.answer?.isCorrect !== null && question.answer?.isCorrect !== undefined)
  );
}

/** 将结构化选择答案映射为题目快照中的选项文本，并按选项拆分为独立展示行。 */
function getLearnerAnswerLines(question: LearnerExamQuestion): string[] {
  const selectedKeys = question.answer?.items.map((item) => item.itemKey).filter(Boolean) ?? [];
  if (selectedKeys.length > 0) {
    return selectedKeys.map((key) => {
      const option = question.options.find((item) => item.optionKey === key);
      return option ? `${key}. ${option.optionContentSnapshot ?? option.optionContent ?? ''}` : key;
    });
  }
  return [question.answer?.answerText || '未作答'];
}

/** 门户结果页只在服务端 scoreVisible 与 PUBLISHED 同时成立时显示正式成绩和解析。 */
async function loadResult(): Promise<void> {
  try {
    attempt.value = await getLearnerExamAttempt(Number(route.params.examAttemptId));
  } catch (error) {
    errorMessage.value = error instanceof Error ? error.message : '结果加载失败。';
  }
}
/** 每题状态对应统一结果页颜色。 */
function getQuestionResultClass(question: LearnerExamQuestion): string {
  if (question.answer?.evaluationResult === 'AUTO_CORRECT') return 'analysis__status--correct';
  if (question.answer?.evaluationResult === 'AUTO_WRONG') return 'analysis__status--wrong';
  if (question.answer?.isCorrect === 1) return 'analysis__status--correct';
  if (question.answer?.isCorrect === 0) return 'analysis__status--wrong';
  return 'analysis__status--pending';
}
/** 题库练习以服务端逐题判定结果为准，主观题只记录答案供学习对照。 */
function getQuestionResult(question: LearnerExamQuestion): string {
  if (question.answer?.evaluationResult === 'AUTO_CORRECT') return '回答正确';
  if (question.answer?.evaluationResult === 'AUTO_WRONG') return '回答错误';
  if (question.answer?.evaluationResult === 'NOT_EVALUATED') return '不判定（仅供学习对照）';
  if (question.answer?.isCorrect === 1) return '回答正确';
  if (question.answer?.isCorrect === 0) return '回答有误';
  return '已作答';
}

/** 将考试题型映射为结果回看中的中文标签。 */
function getQuestionTypeText(question: LearnerExamQuestion): string {
  return (
    {
      SINGLE: '单选题',
      MULTIPLE: '多选题',
      JUDGE: '判断题',
      FILL: '填空题',
      ESSAY: '简答题',
      SINGLE_CHOICE: '单选题',
      MULTIPLE_CHOICE: '多选题',
      FILL_BLANK: '填空题'
    }[question.questionType] ?? '题目'
  );
}

onMounted(() => void loadResult());
</script>
<template>
  <section class="result-page">
    <header class="page-heading">
      <div>
        <p class="page-heading__eyebrow">MY EXAMS</p>
        <strong>{{ isTrainingPractice ? '题库练习反馈' : '考试结果' }}</strong>
      </div>
      <ElButton @click="router.back()">返回</ElButton>
    </header>
    <ElAlert v-if="errorMessage" :title="errorMessage" type="error" show-icon :closable="false" />
    <ElCard v-else-if="attempt" class="result-card" shadow="never">
      <template #header>
        <div style="display: flex; justify-content: space-between; align-items: center">
          <strong>{{ isTrainingPractice ? '练习反馈' : showScoredResult ? '成绩概览' : '答题回看' }}</strong>
        </div>
      </template>
      <template v-if="hasPracticeFeedback">
        <ElAlert title="本次题库练习不判定及格；客观题自动得分，简答题只记录作答。" type="info" :closable="false" />
        <div class="practice-stats">
          <div><strong>{{ attempt.questions.length }}</strong><span>已作答</span></div>
          <div><strong>{{ attempt.evaluatedCount ?? 0 }}</strong><span>可判定</span></div>
          <div><strong>{{ attempt.correctCount ?? 0 }}</strong><span>答对</span></div>
          <div><strong>{{ attempt.wrongCount ?? 0 }}</strong><span>答错</span></div>
          <div><strong>{{ attempt.unassessedCount ?? 0 }}</strong><span>不判定</span></div>
          <div><strong>{{ attempt.accuracyRate == null ? '—' : `${attempt.accuracyRate}%` }}</strong><span>正确率</span></div>
        </div>
        <ElDivider>题目解析</ElDivider>
        <div v-for="(question, index) in attempt.questions" :key="question.attemptQuestionId" class="analysis">
          <div class="analysis__heading">
            <span class="analysis__index">{{ index + 1 }}</span>
            <ElTag class="analysis__type" type="info" effect="plain">{{ getQuestionTypeText(question) }}</ElTag>
            <ElTag
              class="analysis__status-tag"
              :type="getQuestionResultClass(question) === 'analysis__status--correct' ? 'success' : getQuestionResultClass(question) === 'analysis__status--wrong' ? 'danger' : 'info'"
            >
              {{ getQuestionResult(question) }}
            </ElTag>
          </div>
          <strong class="analysis__stem">{{ question.stemSnapshot }}</strong>
          <div class="answer-block">
            <p class="answer-block__label">我的答案</p>
            <div class="answer-block__content">
              <p v-for="(answer, answerIndex) in getLearnerAnswerLines(question)" :key="`${question.attemptQuestionId}-${answerIndex}`" class="answer-block__line">
                {{ answer }}
              </p>
            </div>
          </div>
          <div v-if="hasStandardAnswer(question)" class="answer-block answer-block--standard">
            <p class="answer-block__label">标准答案</p>
            <p class="answer-block__content answer-block__line">{{ getStandardAnswer(question) }}</p>
          </div>
          <ElAlert v-if="question.analysisSnapshot" class="analysis-detail" :title="question.analysisSnapshot" type="info" :closable="false" />
        </div>
      </template>
      <template v-else-if="showScoredResult">
        <div class="score" :class="attempt.isPassed === 1 ? 'passed' : 'failed'">
          {{ attempt.finalScore ?? '-' }}
          <small>分</small>
        </div>
        <p>{{ attempt.isPassed == null ? '练习完成 · 不判定及格' : attempt.isPassed === 1 ? '已及格' : '未及格' }} · 答对 {{ attempt.correctCount ?? '-' }} 题，答错 {{ attempt.wrongCount ?? '-' }} 题</p>
        <ElDivider>题目解析</ElDivider>
        <div v-for="(question, index) in attempt.questions" :key="question.attemptQuestionId" class="analysis">
          <div class="analysis__heading">
            <span class="analysis__index">{{ index + 1 }}</span>
            <ElTag class="analysis__type" type="info" effect="plain">{{ getQuestionTypeText(question) }}</ElTag>
            <ElTag
              v-if="question.answer?.isCorrect !== null && question.answer?.isCorrect !== undefined"
              class="analysis__status-tag"
              :type="question.answer.isCorrect === 1 ? 'success' : 'danger'"
            >
              {{ question.answer.isCorrect === 1 ? '回答正确' : '回答有误' }}
            </ElTag>
          </div>
          <strong class="analysis__stem">{{ question.stemSnapshot }}</strong>
          <div class="answer-block">
            <p class="answer-block__label">我的答案</p>
            <div class="answer-block__content">
              <p
                v-for="(answer, answerIndex) in getLearnerAnswerLines(question)"
                :key="`${question.attemptQuestionId}-${answerIndex}`"
                class="answer-block__line"
              >
                {{ answer }}
              </p>
            </div>
          </div>
          <template v-if="hasAnswerReview(question)">
            <div v-if="hasStandardAnswer(question)" class="answer-block answer-block--standard">
              <p class="answer-block__label">标准答案</p>
              <p class="answer-block__content answer-block__line">{{ getStandardAnswer(question) }}</p>
            </div>
            <p v-if="question.answer?.finalScore !== null && question.answer?.finalScore !== undefined">
              本题得分：
              <text :class="[getQuestionResultClass(question)]">{{ question.answer.finalScore }} / {{ question.score }}</text>
            </p>
            <!-- <ElAlert
              v-if="question.answer?.reviewComment"
              class="analysis-detail"
              :title="question.answer.reviewComment"
              type="info"
              :closable="false"
            /> -->
            <ElAlert v-if="question.analysisSnapshot" class="analysis-detail" :title="question.analysisSnapshot" type="info" :closable="false" />
          </template>
        </div>
      </template>
      <template v-else>
        <p class="review-notice">{{ attempt.scorePublishStatus === 'PUBLISHED' ? '成绩已发布；本次考试设置不展示成绩。可回看已提交答案。' : '试卷已提交，等待成绩发布；当前可回看已提交答案。' }}</p>
        <ElDivider>已提交答案</ElDivider>
        <div v-for="(question, index) in attempt.questions" :key="question.attemptQuestionId" class="analysis">
          <div class="analysis__heading">
            <span class="analysis__index">{{ index + 1 }}</span>
            <ElTag class="analysis__type" type="info" effect="plain">{{ getQuestionTypeText(question) }}</ElTag>
          </div>
          <strong class="analysis__stem">{{ question.stemSnapshot }}</strong>
          <div class="answer-block">
            <p class="answer-block__label">我的答案</p>
            <div class="answer-block__content">
              <p
                v-for="(answer, answerIndex) in getLearnerAnswerLines(question)"
                :key="`${question.attemptQuestionId}-${answerIndex}`"
                class="answer-block__line"
              >
                {{ answer }}
              </p>
            </div>
          </div>
        </div>
      </template>
    </ElCard>
  </section>
</template>
<style scoped lang="less">
.result-page {
  display: flex;
  height: 100%;
  background: var(--color-bg-page);
  flex-direction: column;
}
.page-heading {
  position: sticky;
  top: 0;
  z-index: 2;
  display: flex;
  padding: 16px 28px;
  align-items: center;
  justify-content: space-between;
  background: #fff;
  box-shadow: 0 1px 5px #dbe2ee;
}
.page-heading__eyebrow {
  margin: 0 5px 4px 0;
  color: #2d6cdf;
  font-size: 11px;
  font-weight: 700;
  letter-spacing: 0.12em;
}

.result-card {
  flex: 1;
  gap: 20px;
  width: 60%;
  margin: 24px auto;
  text-align: center;
  overflow: auto;
}

.score {
  font-size: 72px;
  font-weight: 700;
}

.score small {
  font-size: 22px;
}

.passed {
  color: #16a34a;
}

.failed {
  color: #ef4444;
}

.practice-stats {
  display: grid;
  grid-template-columns: repeat(3, 1fr);
  gap: 12px;
  margin-top: 18px;
}

.practice-stats div {
  display: flex;
  min-height: 76px;
  align-items: center;
  justify-content: center;
  flex-direction: column;
  border-radius: 8px;
  background: var(--color-bg-subtle);
}

.practice-stats strong {
  color: var(--color-text-primary);
  font-size: 22px;
}

.practice-stats span {
  margin-top: 5px;
  color: var(--color-text-secondary);
  font-size: 12px;
}

.analysis {
  padding: 18px;
  text-align: left;
  border-bottom: 1px solid #edf0f5;
}

.analysis__heading {
  display: flex;
  align-items: center;
  gap: 8px;
}

.analysis__index {
  display: inline-flex;
  width: 24px;
  height: 24px;
  align-items: center;
  justify-content: center;
  border-radius: 50%;
  background: #eff6ff;
  color: #2563eb;
  font-size: 12px;
  font-weight: 700;
}

.analysis__type {
  flex: none;
}

.analysis__status-tag {
  margin-left: auto;
}

.analysis__stem {
  display: block;
  margin-top: 12px;
  color: var(--color-text-primary);
  line-height: 1.65;
}

.analysis p {
  color: #64748b;
}

.answer-block {
  display: grid;
  grid-template-columns: 88px minmax(0, 1fr);
  gap: 12px;
  margin-top: 14px;
  padding: 12px;
  border-radius: 6px;
  background: var(--color-bg-subtle);
}

.answer-block--standard {
  background: var(--color-primary-subtle);
}

.answer-block__label {
  margin: 0;
  color: var(--color-text-secondary);
  font-size: 13px;
  line-height: 22px;
}

.answer-block__content {
  min-width: 0;
  color: var(--color-text-regular);
  line-height: 22px;
  white-space: pre-wrap;
}

.answer-block__line {
  margin: 0;
  white-space: pre-wrap;
  word-break: break-word;
}

.answer-block__line + .answer-block__line {
  margin-top: 6px;
}
.analysis__status--correct {
  color: #16a34a;
}
.analysis__status--wrong {
  color: #ef4444;
}
.analysis__status--pending {
  color: #64748b;
}

.analysis-detail {
  margin-top: 12px;
}
</style>
