<script setup lang="ts">
import type { CourseAssessmentAttempt, LearnerExamQuestion } from '@shared/types';
import { computed, onMounted, ref } from 'vue';
import { useRoute, useRouter } from 'vue-router';
import { getCourseAssessmentAttempt } from '@/api/learner-course';

const route = useRoute();
const router = useRouter();
const attempt = ref<CourseAssessmentAttempt>();
const errorMessage = ref('');
/** 当前课程内容项名称由课程学习页透传，兼容刷新或直达时回退到资源名称。 */
const courseItemTitle = computed(() => String(route.query.itemTitle ?? attempt.value?.resourceName ?? '课程考核'));

/** 返回选择题正确选项及其快照内容；文本题使用服务端冻结标准答案。 */
function getStandardAnswer(question: LearnerExamQuestion): string {
  const correctOptions = question.options.filter((option) => option.isCorrectSnapshot === 1);
  if (correctOptions.length > 0) {
    return correctOptions.map((option) => `${option.optionKey}. ${option.optionContentSnapshot ?? option.optionContent ?? ''}`).join('、');
  }
  return question.standardAnswer || '未提供';
}

/** 将选择题作答键转换为题目快照中的选项文本。 */
function getLearnerAnswerLines(question: LearnerExamQuestion): string[] {
  const selectedKeys = question.answer?.items.map((item) => item.itemKey).filter(Boolean) ?? [];
  if (selectedKeys.length) {
    return selectedKeys.map((key) => {
      const option = question.options.find((item) => item.optionKey === key);
      return option ? `${key}. ${option.optionContentSnapshot ?? option.optionContent ?? ''}` : key;
    });
  }
  return [question.answer?.answerText || '未作答'];
}

/** 读取当前报名和课程内容项下的作答，保持与独立考试结果域隔离。 */
async function loadResult(): Promise<void> {
  const enrollmentId = Number(route.params.enrollmentId);
  const itemId = Number(route.params.itemId);
  const attemptId = Number(route.params.attemptId);
  if (!enrollmentId || !itemId || !attemptId) {
    errorMessage.value = '课程考核参数无效。';
    return;
  }
  try {
    attempt.value = await getCourseAssessmentAttempt(enrollmentId, itemId, attemptId);
  } catch (error) {
    errorMessage.value = error instanceof Error ? error.message : '课程考核结果加载失败。';
  }
}
/** 每题状态对应统一结果页颜色。 */
function getQuestionResultClass(question: LearnerExamQuestion): string {
  if (question.answer?.isCorrect === 1) return 'analysis__status--correct';
  if (question.answer?.isCorrect === 0) return 'analysis__status--wrong';
  return 'analysis__status--pending';
}

onMounted(() => void loadResult());
</script>

<template>
  <section class="course-result-page">
    <header class="page-heading">
      <div>
        <p>课程学习</p>
        <strong>{{ courseItemTitle }}</strong>
        <span>课程考核结果</span>
      </div>
      <ElButton @click="router.back()">返回课程</ElButton>
    </header>
    <ElAlert v-if="errorMessage" :title="errorMessage" type="error" show-icon :closable="false" />
    <ElCard v-else-if="attempt" class="result-card" shadow="never">
      <template #header>
        <strong>{{ attempt.scoreVisible && attempt.scorePublishStatus === 'EFFECTIVE' ? '成绩概览' : '答题回看' }}</strong>
      </template>
      <template v-if="attempt.scoreVisible && attempt.scorePublishStatus === 'EFFECTIVE'">
        <div class="score" :class="attempt.isPassed === 1 ? 'passed' : 'failed'">
          {{ attempt.finalScore ?? '-' }}
          <small>分</small>
        </div>
        <p class="score-summary">{{ attempt.isPassed === 1 ? '已及格' : '未及格' }} · 结果已自动生效</p>
        <ElDivider>查看答案和解析</ElDivider>
      </template>
      <template v-else>
        <p class="review-notice">作答已提交，主观题批阅完成后将自动生效成绩和解析。</p>
        <ElDivider>已提交答案</ElDivider>
      </template>
      <article v-for="(question, index) in attempt.questions" :key="question.attemptQuestionId" class="question-review">
        <div class="question-review__stem">
          <span class="question-review__number">{{ index + 1 }}</span>
          <strong>{{ question.stemSnapshot }}</strong>
        </div>
        <section class="answer-block">
          <b class="answer-block__label">我的答案</b>
          <div class="answer-block__content">
            <p v-for="(answerLine, answerIndex) in getLearnerAnswerLines(question)" :key="answerIndex" class="answer-block__line">
              {{ answerLine }}
            </p>
          </div>
        </section>
        <template v-if="attempt.scoreVisible && attempt.scorePublishStatus === 'EFFECTIVE'">
          <section class="answer-block answer-block--standard">
            <b class="answer-block__label">标准答案</b>
            <p class="answer-block__content answer-block__line">{{ getStandardAnswer(question) }}</p>
          </section>
          <p v-if="question.answer?.finalScore != null" class="question-score">
            本题得分：
            <b :class="[getQuestionResultClass(question)]">{{ question.answer.finalScore }} / {{ question.score }}</b>
          </p>
          <ElAlert v-if="question.analysisSnapshot" :title="question.analysisSnapshot" type="info" :closable="false" style="margin-top: 14px;"/>
        </template>
      </article>
    </ElCard>
  </section>
</template>

<style scoped lang="less">
.course-result-page {
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
  align-items: center;
  justify-content: space-between;
  padding: 16px 28px;
  background: #fff;
  box-shadow: 0 1px 5px #dbe2ee;
}
.page-heading p {
  margin: 0 0 4px;
  color: #2d6cdf;
  font-size: 12px;
  font-weight: 700;
}
.page-heading strong,
.page-heading span {
  display: block;
}
.page-heading span {
  margin-top: 4px;
  color: #64748b;
  font-size: 13px;
}
.result-card {
  flex: 1;
  gap: 20px;
  width: 60%;
  margin: 24px auto;
  overflow: auto;
}
.score,
.score-summary {
  text-align: center;
}
.score {
  font-size: 64px;
  font-weight: 700;
}
.score small {
  margin-left: 4px;
  font-size: 20px;
}
.passed {
  color: #16a34a;
}
.failed {
  color: #ef4444;
}
.score-summary,
.review-notice {
  color: #64748b;
  text-align: center;
}
.question-review {
  padding: 18px;
  border: 1px solid var(--color-border);
  border-radius: 8px;
  background: var(--color-bg-surface);
}
.question-review + .question-review {
  margin-top: 12px;
}
.question-review__stem {
  display: flex;
  align-items: flex-start;
  gap: 10px;
  color: var(--color-text-primary);
  line-height: 1.65;
}
.question-review__number {
  display: inline-flex;
  flex: 0 0 auto;
  align-items: center;
  justify-content: center;
  width: 24px;
  height: 24px;
  border-radius: 50%;
  background: var(--color-primary-subtle);
  color: var(--color-primary);
  font-size: 12px;
  font-weight: 600;
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
  color: var(--color-text-secondary);
  font-size: 13px;
  line-height: 22px;
}
.answer-block__content {
  min-width: 0;
  color: var(--color-text-regular);
  line-height: 22px;
}
.answer-block__line {
  margin: 0;
  white-space: pre-wrap;
  word-break: break-word;
}
.answer-block__line + .answer-block__line {
  margin-top: 6px;
}
.question-score {
  margin: 12px 0;
  color: var(--color-text-regular);
  .analysis__status--correct {
    color: #16a34a;
  }
  .analysis__status--wrong {
    color: #ef4444;
  }
  .analysis__status--pending {
    color: #64748b;
  }
}
@media (max-width: 640px) {
  .course-result-page {
    padding: 16px;
  }
  .answer-block {
    grid-template-columns: 1fr;
    gap: 4px;
  }
}
</style>
