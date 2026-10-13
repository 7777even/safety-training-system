<script setup lang="ts">
import type { LearnerExamAttempt, LearnerExamQuestion } from '@shared/types';
import { onLoad } from '@dcloudio/uni-app';
import { computed, ref } from 'vue';
import { getLearnerExamAttempt } from '@/api/learner-exam';
import MobileBaseLayout from '@/layouts/MobileBaseLayout.vue';

/** 服务端返回的尝试结果；正式成绩可见性完全由 Contract 字段控制。 */
const attempt = ref<LearnerExamAttempt | null>(null);
const errorMessage = ref('');
const answeredCount = computed(() => attempt.value?.questions.filter((item) => item.answer?.isAnswered === 1).length ?? 0);
/** 培训题库练习不产生分数和及格结论，只展示即时学习反馈。 */
const isTrainingPractice = computed(() => attempt.value?.sourceType === 'TRAINING_PRACTICE');
const hasPracticeFeedback = computed(() => isTrainingPractice.value && Boolean(attempt.value?.submittedTime));
/** 独立题库练习交卷即生效；与 PC 一样兼容 EFFECTIVE，仍由服务端 scoreVisible 决定是否展示得分。 */
const showScoredResult = computed(
  () => !isTrainingPractice.value && attempt.value?.scoreVisible && ['PUBLISHED', 'EFFECTIVE'].includes(attempt.value.scorePublishStatus)
);

/** 将每题判分状态映射为可见中文文案，不推断正确答案。 */
function getQuestionResult(question: LearnerExamQuestion): string {
  if (question.answer?.evaluationResult === 'AUTO_CORRECT') return '回答正确';
  if (question.answer?.evaluationResult === 'AUTO_WRONG') return '回答错误';
  if (question.answer?.evaluationResult === 'NOT_EVALUATED') return '不判定（仅供学习对照）';
  if (question.answer?.isCorrect === 1) return '回答正确';
  if (question.answer?.isCorrect === 0) return '回答错误';
  return question.answer?.gradingStatus === 'PENDING' ? '等待批阅' : '已作答';
}

/** 每题状态对应的样式类，只使用 Contract 中已发布的判分结果。 */
function getQuestionResultClass(question: LearnerExamQuestion): string {
  if (question.answer?.evaluationResult === 'AUTO_CORRECT') return 'analysis__status--correct';
  if (question.answer?.evaluationResult === 'AUTO_WRONG') return 'analysis__status--wrong';
  if (question.answer?.isCorrect === 1) return 'analysis__status--correct';
  if (question.answer?.isCorrect === 0) return 'analysis__status--wrong';
  return 'analysis__status--pending';
}

/** 选择题优先显示正确选项及其快照内容，文本题保留 Contract 的标准答案。 */
function getStandardAnswer(question: LearnerExamQuestion): string {
  const correctOptions = question.options.filter((option) => option.isCorrectSnapshot === 1);
  if (correctOptions.length > 0) {
    return correctOptions.map((option) => `${option.optionKey}. ${option.optionContentSnapshot ?? option.optionContent ?? ''}`).join('、');
  }
  return question.standardAnswer || '未提供';
}

/** 服务端裁剪答案字段时，不渲染标准答案占位内容。 */
function hasStandardAnswer(question: LearnerExamQuestion): boolean {
  return Boolean(question.standardAnswer || question.options.some((option) => option.isCorrectSnapshot === 1));
}

/** 将考试题型转换为清晰的中文标签，避免回看时只显示笼统的“题目”。 */
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

/** 将结构化选择答案还原为题目选项文本，确保移动端回看与 PC 端一致。 */
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

/** 加载当前学员自己的尝试结果。 */
onLoad(async (query) => {
  try {
    attempt.value = await getLearnerExamAttempt(Number(query?.examAttemptId));
  } catch (error) {
    errorMessage.value = error instanceof Error ? error.message : '结果加载失败。';
  }
});
</script>

<template>
  <MobileBaseLayout>
    <view class="exam-result-page">
      <view v-if="errorMessage" class="state-card">
        <text>{{ errorMessage }}</text>
      </view>
      <template v-else-if="attempt">
        <template v-if="hasPracticeFeedback">
          <view class="practice-notice">
            <text class="practice-notice__title">练习反馈</text>
            <text>本次题库练习不计分、不判定及格；正确率仅基于可自动判断题。</text>
          </view>
          <view class="result-stats result-stats--practice">
            <view><text class="result-stats__number">{{ answeredCount }}</text><text>已作答</text></view>
            <view><text class="result-stats__number">{{ attempt.evaluatedCount ?? 0 }}</text><text>可判定</text></view>
            <view><text class="result-stats__number result-stats__number--correct">{{ attempt.correctCount ?? 0 }}</text><text>答对</text></view>
            <view><text class="result-stats__number result-stats__number--wrong">{{ attempt.wrongCount ?? 0 }}</text><text>答错</text></view>
            <view><text class="result-stats__number">{{ attempt.unassessedCount ?? 0 }}</text><text>不判定</text></view>
            <view><text class="result-stats__number">{{ attempt.accuracyRate == null ? '—' : `${attempt.accuracyRate}%` }}</text><text>正确率</text></view>
          </view>
          <view class="analysis-panel">
            <view class="analysis-panel__header"><text>题目解析</text></view>
            <view v-for="(question, index) in attempt.questions" :key="question.attemptQuestionId" class="analysis">
              <view class="analysis__heading">
                <text class="analysis__index">{{ index + 1 }}</text>
                <text class="analysis__type">{{ getQuestionTypeText(question) }}</text>
                <text :class="['analysis__title', getQuestionResultClass(question)]">{{ getQuestionResult(question) }}</text>
              </view>
              <text class="analysis__stem">{{ question.stemSnapshot }}</text>
              <view class="analysis__answer">
                <text class="analysis__answer-label">我的答案</text>
                <text v-for="(answer, answerIndex) in getLearnerAnswerLines(question)" :key="`${question.attemptQuestionId}-${answerIndex}`" class="analysis__answer-line">{{ answer }}</text>
              </view>
              <text v-if="hasStandardAnswer(question)" class="analysis__text">标准答案：{{ getStandardAnswer(question) }}</text>
              <text v-if="question.analysisSnapshot" class="analysis__explanation">{{ question.analysisSnapshot }}</text>
            </view>
          </view>
        </template>
        <template v-else-if="showScoredResult">
          <view :class="['result-hero', attempt.isPassed === 1 ? 'result-hero--passed' : 'result-hero--failed']">
            <text class="result-hero__caption">本次考试成绩</text>
            <text class="result-hero__score">{{ attempt.finalScore ?? '-' }}</text>
            <text class="result-hero__unit">分</text>
            <text class="result-hero__status">{{ attempt.isPassed === 1 ? '已及格' : '未及格' }}</text>
          </view>

          <view class="result-stats">
            <view>
              <text class="result-stats__number">{{ answeredCount }}</text>
              <text>已作答</text>
            </view>
            <view>
              <text class="result-stats__number result-stats__number--correct">{{ attempt.correctCount ?? '-' }}</text>
              <text>答对题数</text>
            </view>
            <view>
              <text class="result-stats__number result-stats__number--wrong">{{ attempt.wrongCount ?? '-' }}</text>
              <text>答错题数</text>
            </view>
          </view>

          <view class="analysis-panel">
            <view class="analysis-panel__header"><text>题目解析</text></view>
            <view v-for="(question, index) in attempt.questions" :key="question.attemptQuestionId" class="analysis">
              <view class="analysis__heading">
                <text class="analysis__index">{{ index + 1 }}</text>
                <text class="analysis__type">{{ getQuestionTypeText(question) }}</text>
                <text :class="['analysis__title', getQuestionResultClass(question)]">{{ getQuestionResult(question) }}</text>
                <text :class="['analysis__status', getQuestionResultClass(question)]">
                  {{ question.answer?.finalScore ?? '-' }} / {{ question.score }}
                </text>
              </view>
              <text class="analysis__stem">{{ question.stemSnapshot }}</text>
              <view class="analysis__answer">
                <text class="analysis__answer-label">我的答案</text>
                <text v-for="(answer, answerIndex) in getLearnerAnswerLines(question)" :key="`${question.attemptQuestionId}-${answerIndex}`" class="analysis__answer-line">{{ answer }}</text>
              </view>
              <text v-if="hasStandardAnswer(question)" class="analysis__text">标准答案：{{ getStandardAnswer(question) }}</text>
              <text v-if="question.analysisSnapshot" class="analysis__explanation">{{ question.analysisSnapshot }}</text>
            </view>
          </view>
        </template>
        <template v-else>
          <view class="pending-result-hero">
            <view class="pending-result-hero__top">
              <view class="pending-result-hero__icon">✓</view>
              <text class="pending-result-hero__tag">{{ attempt.scorePublishStatus === 'PUBLISHED' ? '成绩已发布' : '等待成绩发布' }}</text>
            </view>
            <text class="pending-result-hero__title">{{ attempt.scorePublishStatus === 'PUBLISHED' ? '本次考试设置不展示成绩' : '答卷已提交' }}</text>
            <text class="pending-result-hero__copy">{{ attempt.scorePublishStatus === 'PUBLISHED' ? '可回看已提交答案。' : '等待成绩发布，当前可回看已提交答案。' }}</text>
            <view class="pending-result-hero__stats">
              <view class="pending-result-hero__stat">
                <text class="pending-result-hero__number">{{ answeredCount }}</text>
                <text class="pending-result-hero__label">已作答</text>
              </view>
              <view class="pending-result-hero__divider" />
              <view class="pending-result-hero__stat">
                <text class="pending-result-hero__number">{{ attempt.questions.length }}</text>
                <text class="pending-result-hero__label">题目总数</text>
              </view>
            </view>
          </view>
          <view class="analysis-panel submitted-answers">
            <view class="submitted-answers__summary">
              <view class="submitted-answers__heading">
                <text class="analysis-panel__title">答题回看</text>
                <text class="submitted-answers__hint">当前仅展示已提交内容</text>
              </view>
              <text class="submitted-answers__count">{{ answeredCount }}/{{ attempt.questions.length }}</text>
            </view>
            <view v-for="(question, index) in attempt.questions" :key="question.attemptQuestionId" class="analysis analysis--submitted">
              <view class="analysis__heading">
                <text class="analysis__index">{{ index + 1 }}</text>
                <text class="analysis__type">{{ getQuestionTypeText(question) }}</text>
              </view>
              <text class="analysis__submitted-stem">{{ question.stemSnapshot }}</text>
              <view class="analysis__answer">
                <text class="analysis__answer-label">我的答案</text>
                <text v-for="(answer, answerIndex) in getLearnerAnswerLines(question)" :key="`${question.attemptQuestionId}-${answerIndex}`" class="analysis__answer-line">{{ answer }}</text>
              </view>
            </view>
          </view>
        </template>
      </template>
    </view>
  </MobileBaseLayout>
</template>

<style scoped lang="less">
.exam-result-page {
  min-height: 100%;
  padding: 32rpx 0 72rpx;
}

.state-card,
.analysis-panel {
  padding: 32rpx;
  border-radius: 20rpx;
  background: #fff;
  box-shadow: 0 8rpx 24rpx rgb(38 39 45 / 5%);
  color: #475569;
  font-size: 27rpx;
  line-height: 1.7;
}

.result-hero {
  display: flex;
  padding: 44rpx 32rpx;
  align-items: center;
  flex-direction: column;
  border-radius: 22rpx;
  color: #fff;
}

.result-hero--passed {
  background: #16a34a;
  box-shadow: 0 14rpx 30rpx rgb(22 163 74 / 18%);
}

.result-hero--failed {
  background: #ef4444;
  box-shadow: 0 14rpx 30rpx rgb(239 68 68 / 18%);
}

.result-hero__caption {
  font-size: 26rpx;
  opacity: 0.88;
}

.result-hero__score {
  margin-top: 6rpx;
  font-size: 92rpx;
  font-weight: 800;
  line-height: 1.15;
}

.result-hero__unit {
  margin-top: -14rpx;
  font-size: 26rpx;
}

.result-hero__status {
  margin-top: 18rpx;
  padding: 8rpx 24rpx;
  border: 1px solid rgb(255 255 255 / 50%);
  border-radius: 999rpx;
  font-size: 28rpx;
  font-weight: 700;
}

.result-stats {
  display: grid;
  margin-top: 20rpx;
  grid-template-columns: repeat(3, 1fr);
  border-radius: 18rpx;
  background: #fff;
}

.result-stats--practice {
  grid-template-columns: repeat(3, 1fr);
}

.result-stats view {
  display: flex;
  padding: 24rpx 8rpx;
  align-items: center;
  flex-direction: column;
  gap: 8rpx;
  color: #64748b;
  font-size: 22rpx;
}

.result-stats view + view {
  border-left: 1px solid #e2e8f0;
}

.result-stats__number {
  color: #172033;
  font-size: 36rpx;
  font-weight: 700;
}

.result-stats__number--correct {
  color: #16a34a;
}

.result-stats__number--wrong {
  color: #ef4444;
}

.practice-notice {
  display: flex;
  padding: 30rpx 32rpx;
  flex-direction: column;
  border-left: 8rpx solid #2563eb;
  border-radius: 20rpx;
  background: #eff6ff;
  color: #31537d;
  font-size: 25rpx;
  line-height: 1.7;
}

.practice-notice__title {
  margin-bottom: 10rpx;
  color: #1d4ed8;
  font-size: 32rpx;
  font-weight: 700;
}

.analysis-panel {
  margin-top: 20rpx;
  padding: 0 30rpx;
}

.analysis-panel__header {
  display: flex;
  padding: 24rpx 0;
  align-items: center;
  justify-content: space-between;
}

.analysis-panel__header {
  color: #64748b;
  font-size: 23rpx;
}

.analysis {
  display: flex;
  padding: 28rpx 0;
  flex-direction: column;
  gap: 12rpx;
  border-top: 1px solid #edf0f5;
}

.analysis__heading {
  display: flex;
  align-items: center;
  gap: 14rpx;
}

.analysis__index {
  display: inline-flex;
  width: 42rpx;
  height: 42rpx;
  align-items: center;
  justify-content: center;
  border-radius: 50%;
  background: #f1f5f9;
  color: #475569;
  font-size: 22rpx;
  font-weight: 700;
}

.analysis__title {
  min-width: 0;
  overflow: hidden;
  color: #334155;
  font-size: 27rpx;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.analysis__question-title {
  flex: 1;
  min-width: 0;
  color: #172033;
  font-size: 27rpx;
  font-weight: 600;
  line-height: 1.55;
}

.analysis__type {
  padding: 6rpx 12rpx;
  border-radius: 8rpx;
  background: #eff6ff;
  color: #2563eb;
  font-size: 21rpx;
  font-weight: 600;
  white-space: nowrap;
}

.analysis__status {
  margin-left: auto;
  font-size: 25rpx;
  font-weight: 700;
}

.analysis__status--correct {
  color: #16a34a;
}

.analysis__status--wrong {
  color: #ef4444;
}

.analysis__status--pending {
  color: #f59e0b;
}

.analysis__stem {
  margin-top: 8rpx;
  color: #172033;
  font-size: 27rpx;
  font-weight: 600;
  line-height: 1.55;
}

.analysis__answer {
  display: flex;
  margin-top: 12rpx;
  padding: 16rpx 18rpx;
  align-items: flex-start;
  flex-direction: column;
  border-radius: 12rpx;
  background: #f8fafc;
}

.analysis__answer-label {
  color: #64748b;
  font-size: 23rpx;
  font-weight: 600;
}

.analysis__answer-line {
  margin-top: 6rpx;
  color: #2563eb;
  font-size: 25rpx;
  line-height: 1.55;
  word-break: break-all;
}

.analysis__text {
  color: #475569;
  font-size: 25rpx;
}

.analysis__explanation {
  padding: 18rpx;
  border-radius: 12rpx;
  background: #eff6ff;
  color: #31537d;
  font-size: 24rpx;
}

.pending-result-hero {
  display: flex;
  flex-direction: column;
  padding: 34rpx 32rpx 30rpx;
  border-radius: 28rpx;
  background: linear-gradient(135deg, #1d4ed8 0%, #2563eb 55%, #38bdf8 100%);
  box-shadow: 0 18rpx 36rpx rgb(37 99 235 / 20%);
  color: #fff;
}

.pending-result-hero__top,
.pending-result-hero__stats,
.submitted-answers__summary,
.submitted-answers__heading {
  display: flex;
  align-items: center;
}

.pending-result-hero__top {
  width: 100%;
  justify-content: space-between;
}

.pending-result-hero__icon {
  display: flex;
  width: 56rpx;
  height: 56rpx;
  align-items: center;
  justify-content: center;
  border-radius: 18rpx;
  background: rgb(255 255 255 / 18%);
  color: #fff;
  font-size: 32rpx;
  font-weight: 700;
}

.pending-result-hero__tag {
  padding: 8rpx 16rpx;
  border: 1rpx solid rgb(255 255 255 / 30%);
  border-radius: 999rpx;
  background: rgb(255 255 255 / 14%);
  color: rgb(255 255 255 / 92%);
  font-size: 22rpx;
}

.pending-result-hero__title {
  display: block;
  width: 100%;
  margin-top: 28rpx;
  color: #fff;
  font-size: 38rpx;
  font-weight: 700;
}

.pending-result-hero__copy {
  display: block;
  margin-top: 12rpx;
  color: rgb(239 246 255 / 92%);
  font-size: 25rpx;
  line-height: 1.7;
}

.pending-result-hero__stats {
  width: 100%;
  margin-top: 30rpx;
  padding-top: 24rpx;
  border-top: 1rpx solid rgb(255 255 255 / 20%);
}

.pending-result-hero__stat {
  display: flex;
  flex: 1;
  align-items: baseline;
  justify-content: center;
  gap: 8rpx;
}

.pending-result-hero__number {
  color: #fff;
  font-size: 38rpx;
  font-weight: 700;
}

.pending-result-hero__label {
  color: rgb(239 246 255 / 82%);
  font-size: 22rpx;
}

.pending-result-hero__divider {
  width: 1rpx;
  height: 30rpx;
  background: rgb(255 255 255 / 30%);
}

.submitted-answers {
  margin-top: 20rpx;
}

.submitted-answers__summary {
  justify-content: space-between;
  padding: 26rpx 0 22rpx;
}

.submitted-answers__heading {
  flex-direction: column;
  align-items: flex-start;
  gap: 6rpx;
}

.submitted-answers__hint {
  color: #94a3b8;
  font-size: 21rpx;
}

.submitted-answers__count {
  padding: 8rpx 14rpx;
  border-radius: 999rpx;
  background: #eff6ff;
  color: #2563eb;
  font-size: 23rpx;
  font-weight: 700;
}

.analysis--submitted {
  gap: 16rpx;
}

.analysis__submitted-stem {
  color: #172033;
  font-size: 28rpx;
  font-weight: 600;
  line-height: 1.65;
}
</style>
