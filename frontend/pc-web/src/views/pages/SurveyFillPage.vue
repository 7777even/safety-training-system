<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue';
import { useRoute } from 'vue-router';
import { ElMessage } from 'element-plus';
import { CircleCheck, DocumentChecked, EditPen } from '@element-plus/icons-vue';
import SurveyStatsContent from '@/components/survey/SurveyStatsContent.vue';
import { getSurveyFill, getSurveyStats, submitSurvey, type SurveyFill, type SurveyQuestion, type SurveyStats } from '@/api/m4-outcome';

const route = useRoute();
const detail = ref<SurveyFill>();
const loading = ref(false);
const submitting = ref(false);
const submitted = ref(false);
const stats = ref<SurveyStats>();
const statsVisible = ref(false);
const singleAnswers = reactive<Record<number, number | undefined>>({});
const multipleAnswers = reactive<Record<number, number[] | undefined>>({});
const textAnswers = reactive<Record<number, string | undefined>>({});
/** 提交校验失败时高亮并滚动到第一道未答必填题，减少长问卷的来回查找。 */
const missingQuestionId = ref<number>();
/** 投放用途只展示中文口径，填写人不需要接触英文枚举。 */
const PURPOSE_LABELS: Record<SurveyFill['purpose'], string> = {
  TRAINING_SURVEY: '线下培训调查',
  ORG_EVALUATION: '培训机构评价'
};

const questions = computed<SurveyQuestion[]>(() => detail.value?.questions ?? []);
/** 填空题按去掉首尾空格判断已答，与提交时的必填校验保持一致。 */
function isAnswered(question: SurveyQuestion): boolean {
  const questionId = question.surveyQuestionId!;
  if (question.questionType === 'TEXT') return Boolean(textAnswers[questionId]?.trim());
  if (question.questionType === 'SINGLE') return singleAnswers[questionId] !== undefined;
  return Boolean(multipleAnswers[questionId]?.length);
}
const answeredCount = computed(() => questions.value.filter(isAnswered).length);
const requiredCount = computed(() => questions.value.filter((question) => question.isRequired === 1).length);
const progress = computed(() => (questions.value.length ? Math.round((answeredCount.value / questions.value.length) * 100) : 0));
/** 提交后按投放的可见性说明统计入口，避免填写人反复寻找。 */
const visibilityHint = computed(() =>
  detail.value?.resultVisibility === 'SUBMITTER_AFTER_SUBMIT'
    ? '本次投放允许填写人查看统计，提交后即可查看汇总结果。'
    : '本次投放的统计结果仅管理员可见。'
);
async function load(): Promise<void> {
  loading.value = true;
  try {
    detail.value = await getSurveyFill(Number(route.params.surveyInstanceId));
    submitted.value = detail.value.submitted;
  } finally {
    loading.value = false;
  }
}
async function submit(): Promise<void> {
  if (!detail.value || submitting.value) return;
  const firstMissing = detail.value.questions.find((question) => question.isRequired === 1 && !isAnswered(question));
  if (firstMissing) {
    missingQuestionId.value = firstMissing.surveyQuestionId;
    ElMessage.warning('请完成所有必填题后再提交。');
    globalThis.document
      .getElementById(`survey-question-${firstMissing.surveyQuestionId}`)
      ?.scrollIntoView({ behavior: 'smooth', block: 'center' });
    return;
  }
  missingQuestionId.value = undefined;
  const payload = detail.value.questions.map((question) => ({
    surveyQuestionId: question.surveyQuestionId,
    optionIds: question.questionType === 'TEXT' ? undefined : question.questionType === 'SINGLE' ? [singleAnswers[question.surveyQuestionId!]].filter((item): item is number => item !== undefined) : (multipleAnswers[question.surveyQuestionId!] ?? []),
    answerText: question.questionType === 'TEXT' ? textAnswers[question.surveyQuestionId!] : undefined
  }));
  submitting.value = true;
  try {
    await submitSurvey(detail.value.surveyInstanceId, payload);
    submitted.value = true;
    ElMessage.success('问卷已提交。');
  } finally {
    submitting.value = false;
  }
}
/** 服务端按投放可见性和本人提交事实再次校验统计权限。 */
async function showStats(): Promise<void> {
  if (!detail.value || !submitted.value || detail.value.resultVisibility !== 'SUBMITTER_AFTER_SUBMIT') return;
  stats.value = await getSurveyStats(detail.value.surveyInstanceId);
  statsVisible.value = true;
}
onMounted(load);
</script>
<template>
  <main v-loading="loading" class="survey-fill">
    <div class="fill-shell">
      <header class="fill-hero">
        <p class="fill-hero__eyebrow">{{ detail ? PURPOSE_LABELS[detail.purpose] : 'SURVEY' }}</p>
        <h1>{{ detail?.instanceName || '问卷填写' }}</h1>
        <p class="fill-hero__name">{{ detail?.surveyName }}</p>
        <div v-if="detail && !submitted" class="ws-bar fill-progress">
          <div class="ws-bar__track">
            <div class="ws-bar__fill" :style="{ width: `${progress}%` }" />
          </div>
          <span class="ws-bar__legend">已答 {{ answeredCount }} / {{ questions.length }} 题（必填 {{ requiredCount }} 题）</span>
        </div>
      </header>

      <ElCard v-if="submitted" class="fill-card" shadow="never">
        <ElResult icon="success" title="已完成填写" sub-title="感谢您的反馈，问卷已提交成功。">
          <template #extra>
            <div class="fill-done">
              <p class="fill-done__hint">{{ visibilityHint }}</p>
              <ElButton
                v-if="detail?.resultVisibility === 'SUBMITTER_AFTER_SUBMIT'"
                type="primary"
                :icon="CircleCheck"
                @click="showStats"
              >
                查看问卷统计
              </ElButton>
            </div>
          </template>
        </ElResult>
      </ElCard>

      <ElForm v-else-if="detail" class="fill-form" label-position="top">
        <section
          v-for="(question, index) in detail.questions"
          :id="`survey-question-${question.surveyQuestionId}`"
          :key="question.surveyQuestionId"
          class="fill-question"
          :class="{ 'fill-question--missing': missingQuestionId === question.surveyQuestionId }"
        >
          <header class="fill-question__head">
            <span class="ws-index">Q{{ index + 1 }}</span>
            <h2>
              {{ question.questionText }}
              <em v-if="question.isRequired === 1">必填</em>
            </h2>
          </header>
          <ElFormItem :prop="`q-${question.surveyQuestionId}`" :show-message="false">
            <ElRadioGroup
              v-if="question.questionType === 'SINGLE'"
              v-model="singleAnswers[question.surveyQuestionId!]"
              class="fill-options"
            >
              <ElRadio v-for="option in question.options" :key="option.surveyOptionId" :value="option.surveyOptionId">
                {{ option.optionContent }}
              </ElRadio>
            </ElRadioGroup>
            <ElCheckboxGroup
              v-else-if="question.questionType === 'MULTIPLE'"
              v-model="multipleAnswers[question.surveyQuestionId!]"
              class="fill-options"
            >
              <ElCheckbox v-for="option in question.options" :key="option.surveyOptionId" :value="option.surveyOptionId">
                {{ option.optionContent }}
              </ElCheckbox>
            </ElCheckboxGroup>
            <ElInput
              v-else
              v-model="textAnswers[question.surveyQuestionId!]"
              type="textarea"
              :rows="4"
              maxlength="500"
              show-word-limit
              placeholder="请输入您的回答"
            />
          </ElFormItem>
        </section>

        <footer class="fill-actions">
          <span class="fill-actions__hint">
            <ElIcon><EditPen /></ElIcon>
            共 {{ questions.length }} 题，已答 {{ answeredCount }} 题
          </span>
          <ElButton type="primary" :loading="submitting" :icon="DocumentChecked" @click="submit">提交问卷</ElButton>
        </footer>
      </ElForm>

      <ElCard v-else class="fill-card" shadow="never">
        <ElResult icon="warning" title="问卷不可填写" sub-title="投放可能已结束或链接不正确，请联系投放管理员确认。" />
      </ElCard>
    </div>

    <ElDialog v-model="statsVisible" title="问卷统计" width="720px" top="6vh">
      <SurveyStatsContent :stats="stats" />
    </ElDialog>
  </main>
</template>
<style scoped lang="less">
.survey-fill {
  min-height: 100vh;
  padding: 40px 24px 56px;
  background: var(--color-bg-page);
}

.fill-shell {
  display: grid;
  gap: var(--space-4);
  width: 100%;
  max-width: 760px;
  margin: 0 auto;
}

/* 顶部身份区块沿用工作台的渐变口径，让公开填写页与后台投放信息保持一致。 */
.fill-hero {
  display: grid;
  gap: var(--space-2);
  padding: 24px;
  border: 1px solid #dbeafe;
  border-radius: var(--radius-card);
  background: linear-gradient(135deg, var(--color-primary-subtle), var(--color-bg-surface) 68%);
}

.fill-hero__eyebrow {
  margin: 0;
  color: var(--color-primary);
  font-size: 11px;
  font-weight: 700;
  letter-spacing: 0.12em;
}

.fill-hero h1 {
  margin: 0;
  color: var(--color-text-primary);
  font-size: 22px;
  font-weight: 600;
  line-height: 30px;
  overflow-wrap: anywhere;
}

.fill-hero__name {
  margin: 0;
  color: var(--color-text-secondary);
  font-size: 13px;
  line-height: 20px;
}

/* 外壳来自全局 ws-bar，此处只保留与身份区块的间距。 */
.fill-progress {
  margin-top: var(--space-2);
}

.fill-card {
  border-color: var(--color-border);
  border-radius: var(--radius-card);
}

.fill-form {
  display: grid;
  gap: var(--space-3);
}

.fill-question {
  display: grid;
  gap: var(--space-2);
  padding: 18px 20px;
  border: 1px solid var(--color-border);
  border-radius: var(--radius-card);
  background: var(--color-bg-surface);
  transition: border-color 0.2s ease, box-shadow 0.2s ease;
}

/* 校验失败时高亮第一道未答必填题，配合滚动定位使用。 */
.fill-question--missing {
  border-color: var(--color-danger);
  box-shadow: 0 0 0 3px rgb(220 38 38 / 10%);
}

.fill-question__head {
  display: flex;
  min-width: 0;
  gap: var(--space-2);
}

.fill-question__head h2 {
  margin: 0;
  color: var(--color-text-primary);
  font-size: 15px;
  font-weight: 600;
  line-height: 22px;
  overflow-wrap: anywhere;
}

.fill-question__head em {
  margin-left: var(--space-2);
  color: var(--color-danger);
  font-size: 12px;
  font-style: normal;
  font-weight: 400;
}

.fill-question :deep(.el-form-item) {
  margin-bottom: 0;
}

.fill-options {
  display: grid;
  gap: var(--space-1);
}

.fill-options :deep(.el-radio),
.fill-options :deep(.el-checkbox) {
  height: auto;
  min-height: 34px;
  margin-right: 0;
  padding: 6px 10px;
  border-radius: var(--radius-control);
}

.fill-options :deep(.el-radio:hover),
.fill-options :deep(.el-checkbox:hover) {
  background: var(--color-primary-subtle);
}

.fill-options :deep(.el-radio__label),
.fill-options :deep(.el-checkbox__label) {
  line-height: 22px;
  white-space: normal;
  overflow-wrap: anywhere;
}

.fill-actions {
  position: sticky;
  bottom: 0;
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  justify-content: space-between;
  gap: var(--space-3);
  padding: 12px 16px;
  border: 1px solid var(--color-border);
  border-radius: var(--radius-card);
  background: rgb(255 255 255 / 96%);
  box-shadow: var(--shadow-card);
  backdrop-filter: blur(4px);
}

.fill-actions__hint {
  display: flex;
  align-items: center;
  gap: 6px;
  color: var(--color-text-secondary);
  font-size: 12px;
  font-variant-numeric: tabular-nums;
}

.fill-done {
  display: grid;
  justify-items: center;
  gap: var(--space-3);
}

.fill-done__hint {
  margin: 0;
  color: var(--color-text-secondary);
  font-size: 13px;
  line-height: 20px;
}

/* 扫码填写以手机为主，窄屏收窄留白并让主操作占满宽度。 */
@media (max-width: 640px) {
  .survey-fill {
    padding: 16px 12px 32px;
  }

  .fill-hero {
    padding: 18px;
  }

  .fill-question {
    padding: 14px 16px;
  }

  .fill-actions {
    padding: 10px 12px;
  }

  .fill-actions :deep(.el-button) {
    width: 100%;
  }
}
</style>
