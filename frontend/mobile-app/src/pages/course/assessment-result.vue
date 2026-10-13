<script setup lang="ts">
import type { CourseAssessmentAttempt, LearnerExamQuestion } from '@shared/types';
import { onLoad } from '@dcloudio/uni-app';
import { computed, ref } from 'vue';
import { getCourseAssessmentAttempt } from '@/api/learner-course';
import MobileBaseLayout from '@/layouts/MobileBaseLayout.vue';

/** 当前课程考核结果；成绩是否显示完全由课程 Contract 决定。 */
const attempt = ref<CourseAssessmentAttempt | null>(null);
const errorMessage = ref('');
const courseItemTitle = ref('课程考核');
let enrollmentId = 0;
let itemId = 0;
let attemptId = 0;

const answeredCount = computed(() => attempt.value?.questions.filter((item) => item.answer?.isAnswered === 1).length ?? 0);
const correctCount = computed(() => attempt.value?.questions.filter((item) => item.answer?.isCorrect === 1).length ?? 0);
const wrongCount = computed(() => attempt.value?.questions.filter((item) => item.answer?.isCorrect === 0).length ?? 0);
const resultVisible = computed(() => attempt.value?.scoreVisible && attempt.value.scorePublishStatus === 'EFFECTIVE');

/** 每题判分状态使用课程自动生效后的数据，不推断被裁剪的答案。 */
function getQuestionResult(question: LearnerExamQuestion): string {
  if (question.answer?.isCorrect === 1) return '回答正确';
  if (question.answer?.isCorrect === 0) return '回答错误';
  return '已作答';
}

/** 每题状态对应统一结果页颜色。 */
function getQuestionResultClass(question: LearnerExamQuestion): string {
  if (question.answer?.isCorrect === 1) return 'analysis__status--correct';
  if (question.answer?.isCorrect === 0) return 'analysis__status--wrong';
  return 'analysis__status--pending';
}

/** 将题型转换为中文标签，回看时避免所有题目都只显示序号。 */
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

/** 将结构化选择答案还原为选项文本，与 PC 端答题回看保持同一口径。 */
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

/** 选择题优先展示正确选项及其快照内容，文本题保留 Contract 的标准答案。 */
function getStandardAnswer(question: LearnerExamQuestion): string {
  const correctOptions = question.options.filter((option) => option.isCorrectSnapshot === 1);
  if (correctOptions.length > 0) {
    return correctOptions.map((option) => `${option.optionKey}. ${option.optionContentSnapshot ?? option.optionContent ?? ''}`).join('、');
  }
  return question.standardAnswer || '未提供';
}

/** 仅在后端结果已生效且返回答案时展示标准答案区域。 */
function hasStandardAnswer(question: LearnerExamQuestion): boolean {
  return Boolean(question.standardAnswer || question.options.some((option) => option.isCorrectSnapshot === 1));
}

/** 返回课程学习页；父页面 onShow 时刷新任务快照同步完成状态。 */
function goBackToCourse(): void {
  uni.navigateBack();
}

/** 读取本人课程内容项作答结果，不读取独立考试结果接口。 */
onLoad(async (query) => {
  enrollmentId = Number(query?.enrollmentId);
  itemId = Number(query?.itemId);
  attemptId = Number(query?.attemptId);
  courseItemTitle.value = String(query?.itemTitle || '课程考核');
  if (!enrollmentId || !itemId || !attemptId) {
    errorMessage.value = '课程结果参数无效。';
    return;
  }
  try {
    attempt.value = await getCourseAssessmentAttempt(enrollmentId, itemId, attemptId);
  } catch (error) {
    errorMessage.value = error instanceof Error ? error.message : '课程结果加载失败。';
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
        <template v-if="resultVisible">
          <view :class="['result-hero', attempt.isPassed === 1 ? 'result-hero--passed' : 'result-hero--failed']">
            <text class="result-hero__caption">{{ courseItemTitle }}</text>
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
              <text class="result-stats__number result-stats__number--correct">{{ correctCount }}</text>
              <text>答对题数</text>
            </view>
            <view>
              <text class="result-stats__number result-stats__number--wrong">{{ wrongCount }}</text>
              <text>答错题数</text>
            </view>
          </view>
          <view class="analysis-panel">
            <view class="analysis-panel__header"><text>题目解析</text></view>
            <view v-for="(question, index) in attempt.questions" :key="question.attemptQuestionId" class="analysis">
              <view class="analysis__heading">
                <text class="analysis__index">{{ index + 1 }}</text>
                <text class="analysis__title">{{ getQuestionResult(question) }}</text>
                <text :class="['analysis__status', getQuestionResultClass(question)]">
                  {{ question.answer?.finalScore ?? '-' }} / {{ question.score }}
                </text>
              </view>
              <text class="analysis__stem">{{ question.stemSnapshot }}</text>
              <view class="answer-block">
                <text class="answer-block__label">我的答案</text>
                <view class="answer-block__body">
                  <text
                    v-for="(line, lineIndex) in getLearnerAnswerLines(question)"
                    :key="`${question.attemptQuestionId}-${lineIndex}`"
                    class="answer-block__content"
                  >{{ line }}</text>
                </view>
              </view>
              <view v-if="hasStandardAnswer(question)" class="answer-block answer-block--standard">
                <text class="answer-block__label">标准答案</text>
                <text class="answer-block__content">{{ getStandardAnswer(question) }}</text>
              </view>
              <text v-if="question.analysisSnapshot" class="analysis__explanation">{{ question.analysisSnapshot }}</text>
            </view>
          </view>
        </template>
        <template v-else>
          <view class="pending-result-hero">
            <view class="pending-result-hero__top">
              <view class="pending-result-hero__icon">✓</view>
              <text class="pending-result-hero__tag">等待成绩发布</text>
            </view>
            <text class="pending-result-hero__title">{{ courseItemTitle }}已提交</text>
            <text class="pending-result-hero__copy">教师批阅并发布成绩后，即可查看最终成绩、标准答案与题目解析。当前可先回看本人已提交的答案。</text>
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
                <text class="submitted-answers__title">答题回看</text>
                <text class="submitted-answers__hint">当前仅展示本人已提交内容</text>
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
                <text
                  v-for="(line, lineIndex) in getLearnerAnswerLines(question)"
                  :key="`${question.attemptQuestionId}-${lineIndex}`"
                  class="analysis__answer-line"
                >{{ line }}</text>
              </view>
            </view>
          </view>
        </template>
        <button class="back-button" @tap="goBackToCourse">返回课程学习</button>
      </template>
    </view>
  </MobileBaseLayout>
</template>

<style scoped lang="less">
.exam-result-page {
  padding: 32rpx 0 52rpx;
}
.state-card,
.analysis-panel {
  padding: 32rpx;
  border-radius: 20rpx;
  background: #fff;
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
}
.result-hero--failed {
  background: #ef4444;
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
.analysis-panel {
  margin-top: 20rpx;
  padding: 0 26rpx;
}
.analysis-panel__header {
  display: flex;
  padding: 24rpx 0;
  color: #64748b;
  font-size: 23rpx;
}
.analysis {
  display: flex;
  padding: 24rpx 0;
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
  flex: 1;
  color: #172033;
  font-size: 26rpx;
  font-weight: 700;
}
.analysis__status {
  font-size: 24rpx;
  font-weight: 700;
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
.analysis__stem {
  color: #172033;
  font-size: 27rpx;
  line-height: 1.6;
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
.analysis--submitted {
  gap: 16rpx;
}
.analysis__submitted-stem {
  color: #172033;
  font-size: 28rpx;
  font-weight: 600;
  line-height: 1.65;
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
.answer-block__body {
  display: flex;
  min-width: 0;
  flex-direction: column;
}
.answer-block {
  display: grid;
  grid-template-columns: 112rpx minmax(0, 1fr);
  gap: 16rpx;
  padding: 18rpx;
  border-radius: 12rpx;
  background: #f8fafc;
}
.answer-block--standard {
  background: #eff6ff;
}
.answer-block__label {
  color: #64748b;
  font-size: 23rpx;
  font-weight: 600;
  line-height: 1.65;
}
.answer-block__content {
  min-width: 0;
  color: #475569;
  font-size: 24rpx;
  line-height: 1.65;
  white-space: pre-wrap;
  word-break: break-word;
}
.answer-block__content + .answer-block__content {
  margin-top: 6rpx;
}
.analysis__explanation {
  padding: 16rpx;
  border-radius: 12rpx;
  background: #eff6ff;
  color: #1d4ed8;
  font-size: 24rpx;
  line-height: 1.6;
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
  padding: 0 26rpx;
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
.submitted-answers__title {
  color: #172033;
  font-size: 28rpx;
  font-weight: 700;
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
.back-button {
  display: block;
  width: 100%;
  margin-top: 20rpx;
  border: 1px solid #cbd5e1;
  border-radius: 12rpx;
  background: #fff;
  color: #475569;
  font-size: 26rpx;
  line-height: 72rpx;
}
</style>
