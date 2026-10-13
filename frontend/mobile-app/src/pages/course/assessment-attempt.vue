<script setup lang="ts">
import type { CourseAssessmentAttempt, LearnerExamOption, LearnerExamQuestion, SaveLearnerExamAnswersPayload } from '@shared/types';
import { buildWatermarkTile } from '@shared/utils/watermark';
import { onHide, onLoad, onUnload } from '@dcloudio/uni-app';
import { computed, ref } from 'vue';
import { getCourseAssessmentAttempt, getLearnerCourseDetail, saveCourseAssessmentAnswers, submitCourseAssessmentAttempt } from '@/api/learner-course';
import { recordExamAnticheatEvent } from '@/api/learner-exam';
import { COURSE_ASSESSMENT_ATTEMPT_STATUS, COURSE_ASSESSMENT_PAGE } from '@/constants';
import MobileBaseLayout from '@/layouts/MobileBaseLayout.vue';
import { useUserStore } from '@/stores';

const userStore = useUserStore();
/** 当前课程报名上下文中的作答快照与浏览题号。 */
const attempt = ref<CourseAssessmentAttempt | null>(null);
const questionIndex = ref(0);
const errorMessage = ref('');
const saving = ref(false);
const submitting = ref(false);
const savedAnswerSnapshot = ref('');
const courseItemTitle = ref('课程考核');
/** 课程级「启用学习防作弊」开关；未开启时不注册任何监听、不拦截复制粘贴。 */
const anticheatEnabled = ref(false);
/** 课程级「启用学习水印」开关；与防作弊同源，都取课程冻结快照。 */
const watermarkEnabled = ref(false);
/** 水印瓦片按当前登录用户名生成，1.5 倍换算 rpx 与学习页、考试页保持一致。 */
const watermarkStyle = computed<Record<string, string>>(() => {
  const tile = buildWatermarkTile(userStore.userInfo?.userName ?? userStore.userInfo?.userCode ?? '学员');
  return {
    '--course-watermark-image': tile.image,
    '--course-watermark-size': `${Math.round(tile.width * 1.5)}rpx ${Math.round(tile.height * 1.5)}rpx`
  };
});
let enrollmentId = 0;
let itemId = 0;
let attemptId = 0;
let clipboardListenersBound = false;
/** 每类异常行为的累计次数，随上报值送入后端便于管理端审计。 */
const anticheatEventCounts = new Map<string, number>();

/** 追加最小事件审计；上报失败只忽略，不阻断作答与交卷。 */
function recordAnticheatEvent(source: string, eventType = 'SWITCH_AWAY'): void {
  if (!anticheatEnabled.value || !editable.value) return;
  const eventCount = (anticheatEventCounts.get(eventType) ?? 0) + 1;
  anticheatEventCounts.set(eventType, eventCount);
  void recordExamAnticheatEvent(attemptId, {
    eventType,
    eventTime: new Date().toISOString(),
    eventCount,
    actionTaken: 'RECORD',
    clientPayloadJson: JSON.stringify({ source, assessmentKind: attempt.value?.assessmentKind ?? null })
  }).catch(() => undefined);
}

/** 复制、剪切、粘贴与长按菜单在开启防作弊时一律拦截并留痕。 */
function blockClipboard(event: Event): void {
  event.preventDefault();
  recordAnticheatEvent(`clipboard-${event.type}`, 'COPY');
  uni.showToast({ title: '本考核已禁止复制粘贴，本次操作已记录', icon: 'none' });
}

/** 仅 H5 存在浏览器剪贴板事件；其余端保留给原生容器能力。 */
function bindClipboardGuard(): void {
  // #ifdef H5
  if (!anticheatEnabled.value || clipboardListenersBound) return;
  globalThis.document.addEventListener('copy', blockClipboard);
  globalThis.document.addEventListener('cut', blockClipboard);
  globalThis.document.addEventListener('paste', blockClipboard);
  globalThis.document.addEventListener('contextmenu', blockClipboard);
  clipboardListenersBound = true;
  // #endif
}

/** 页面卸载时统一解绑，避免离开答题后仍拦截其它页面的复制粘贴。 */
function releaseClipboardGuard(): void {
  // #ifdef H5
  if (!clipboardListenersBound) return;
  globalThis.document.removeEventListener('copy', blockClipboard);
  globalThis.document.removeEventListener('cut', blockClipboard);
  globalThis.document.removeEventListener('paste', blockClipboard);
  globalThis.document.removeEventListener('contextmenu', blockClipboard);
  clipboardListenersBound = false;
  // #endif
}

/** 当前显示题目只读取课程域返回的冻结作答快照。 */
const currentQuestion = computed<LearnerExamQuestion | null>(() => attempt.value?.questions[questionIndex.value] ?? null);
/** 仅作答中的课程考核可编辑，待批阅与已生效内容保持只读。 */
const editable = computed(() => attempt.value?.attemptStatus === COURSE_ASSESSMENT_ATTEMPT_STATUS.ANSWERING);
const answeredCount = computed(() => attempt.value?.questions.filter((item) => item.answer?.isAnswered === 1).length ?? 0);
const questionCount = computed(() => attempt.value?.questions.length ?? 0);
const progressPercent = computed(() => (questionCount.value ? Math.round((answeredCount.value / questionCount.value) * 100) : 0));
const assessmentLabel = computed(() => (attempt.value?.assessmentKind === 'BANK_PRACTICE' ? '题库练习' : '课程测验'));

/** 兼容题目选项的快照字段。 */
function getOptionContent(option: LearnerExamOption): string {
  return option.optionContentSnapshot ?? option.optionContent ?? '';
}

/** 为文本题创建可编辑答案对象。 */
function ensureAnswer(question: LearnerExamQuestion): void {
  if (!question.answer) {
    question.answer = { attemptQuestionId: question.attemptQuestionId, answerText: '', items: [], isAnswered: 0 };
  }
}

/** 将单选或多选结果转换为课程答案 Contract 的文本与结构化答案项。 */
function setChoices(question: LearnerExamQuestion, keys: string[]): void {
  question.answer = {
    attemptQuestionId: question.attemptQuestionId,
    answerText: keys.join(','),
    items: keys.map((key, index) => ({ itemType: 'OPTION', itemKey: key, itemValue: key, sortOrder: index + 1 })),
    isAnswered: keys.length ? 1 : 0
  };
}

/** 更新填空或简答题的作答状态。 */
function updateTextAnswer(question: LearnerExamQuestion, value: string): void {
  ensureAnswer(question);
  question.answer!.answerText = value;
  question.answer!.isAnswered = value.trim() ? 1 : 0;
}

/** 读取指定填空的已保存值，并兼容历史单空答案文本。 */
function getFillBlankValue(question: LearnerExamQuestion, blankNo: number): string {
  const item = question.answer?.items.find((answerItem) => answerItem.itemType === 'BLANK' && answerItem.itemKey === String(blankNo));
  return item?.itemValue ?? (blankNo === 1 ? question.answer?.answerText ?? '' : '');
}

/** 按题目空位序号保存填空值；多空题必须全部填写才算已答。 */
function updateFillBlankAnswer(question: LearnerExamQuestion, blankNo: number, value: string): void {
  ensureAnswer(question);
  const values = new Map<number, string>();
  for (let index = 1; index <= (question.blankCount ?? 1); index++) values.set(index, getFillBlankValue(question, index));
  values.set(blankNo, value);
  const items = [...values.entries()]
    .filter(([, text]) => text.trim())
    .map(([index, text]) => ({ itemType: 'BLANK', itemKey: String(index), itemValue: text, sortOrder: index }));
  question.answer = {
    attemptQuestionId: question.attemptQuestionId,
    answerText: null,
    items,
    isAnswered: items.length === (question.blankCount ?? 1) ? 1 : 0
  };
}

/** 适配 uni-app 输入事件，避免模板将空位序号误推断为事件对象。 */
function updateFillBlankAnswerEvent(question: LearnerExamQuestion, blankNo: number, event: unknown): void {
  const value = (event as { detail?: { value?: string } }).detail?.value ?? '';
  updateFillBlankAnswer(question, blankNo, value);
}

/** 判断指定选项是否已选择。 */
function isOptionSelected(question: LearnerExamQuestion, optionKey: string): boolean {
  return question.answer?.items.some((item) => item.itemKey === optionKey) ?? false;
}

/** 课程 Contract 的四种题型映射为移动端文案。 */
function getQuestionTypeText(question: LearnerExamQuestion): string {
  return { SINGLE: '单选题', MULTIPLE: '多选题', JUDGE: '判断题', FILL: '填空题', ESSAY: '简答题' }[question.questionType] ?? '题目';
}

/** 生成课程域暂存接口需要的全量答案快照。 */
function buildSavePayload(): SaveLearnerExamAnswersPayload {
  return {
    answers: (attempt.value?.questions ?? []).map((item) => ({
      attemptQuestionId: item.attemptQuestionId,
      answerText: item.answer?.answerText ?? null,
      items: item.answer?.items ?? []
    }))
  };
}

/** 保存成功快照用于交卷和返回前判断本地编辑是否遗漏。 */
function answerSnapshot(): string {
  return JSON.stringify(buildSavePayload());
}

/** 暂存课程考核答案。 */
async function saveAnswers(showSuccess = true): Promise<boolean> {
  if (!editable.value || saving.value) return !saving.value;
  saving.value = true;
  try {
    await saveCourseAssessmentAnswers(enrollmentId, itemId, attemptId, buildSavePayload());
    savedAnswerSnapshot.value = answerSnapshot();
    if (showSuccess) uni.showToast({ title: '答案已暂存', icon: 'success' });
    return true;
  } catch (error) {
    uni.showToast({ title: error instanceof Error ? error.message : '答案暂存失败', icon: 'none' });
    return false;
  } finally {
    saving.value = false;
  }
}

/** 提交或返回前保存未暂存的编辑，避免本地答案丢失。 */
async function ensureAnswersSaved(action: '交卷' | '返回'): Promise<boolean> {
  if (!editable.value || answerSnapshot() === savedAnswerSnapshot.value) return true;
  const result = await uni.showModal({
    title: '暂存答案',
    content: `当前答案尚未暂存，是否先暂存再${action}？`,
    confirmText: '暂存并继续',
    cancelText: '留在当前页',
    confirmColor: '#2563EB'
  });
  return result.confirm ? saveAnswers(false) : false;
}

/** 交卷前显示未答数量，提交成功后只跳转课程结果页。 */
async function confirmSubmit(): Promise<void> {
  if (!editable.value || submitting.value || !(await ensureAnswersSaved('交卷'))) return;
  const unanswered = questionCount.value - answeredCount.value;
  const result = await uni.showModal({
    title: '确认交卷',
    content: unanswered ? `还有 ${unanswered} 题未作答，确认交卷吗？` : '所有题目已作答，确认交卷吗？',
    confirmText: '确认交卷',
    confirmColor: '#2563EB'
  });
  if (!result.confirm) return;
  submitting.value = true;
  try {
    await submitCourseAssessmentAttempt(enrollmentId, itemId, attemptId, { submitReason: 'MANUAL' });
    // 交卷后立即停止采集：跳转结果页会触发页面隐藏，不应记成学员切屏。
    anticheatEnabled.value = false;
    releaseClipboardGuard();
    uni.redirectTo({
      url: `/pages/course/${COURSE_ASSESSMENT_PAGE.RESULT}?enrollmentId=${enrollmentId}&itemId=${itemId}&attemptId=${attemptId}&itemTitle=${encodeURIComponent(courseItemTitle.value)}`
    });
  } catch (error) {
    uni.showToast({ title: error instanceof Error ? error.message : '课程考核提交失败', icon: 'none' });
  } finally {
    submitting.value = false;
  }
}

/** 返回学习页前保护未暂存答案。 */
async function goBack(): Promise<void> {
  if (await ensureAnswersSaved('返回')) uni.navigateBack();
}

/** 切换题目只更新本地浏览位置，答案通过显式暂存或交卷写入。 */
function goToQuestion(nextIndex: number): void {
  if (nextIndex >= 0 && nextIndex < questionCount.value) questionIndex.value = nextIndex;
}

/** 加载当前学员自己的课程考核作答，不调用独立考试接口。 */
onLoad(async (query) => {
  enrollmentId = Number(query?.enrollmentId);
  itemId = Number(query?.itemId);
  attemptId = Number(query?.attemptId);
  courseItemTitle.value = String(query?.itemTitle || '课程考核');
  if (!enrollmentId || !itemId || !attemptId) {
    errorMessage.value = '课程作答参数无效。';
    return;
  }
  try {
    const [loadedAttempt, courseDetail] = await Promise.all([
      getCourseAssessmentAttempt(enrollmentId, itemId, attemptId),
      // 防作弊开关只存在于课程快照，随作答一起读取，失败时按未开启处理。
      getLearnerCourseDetail(enrollmentId).catch(() => null)
    ]);
    attempt.value = loadedAttempt;
    attempt.value.questions.forEach(ensureAnswer);
    savedAnswerSnapshot.value = answerSnapshot();
    anticheatEnabled.value = courseDetail?.course?.isStudyAnticheatEnabled === 1;
    // 学习水印与防作弊同源，都取课程冻结快照；读取失败时按未开启处理。
    watermarkEnabled.value = courseDetail?.course?.isWatermarkEnabled === 1;
    bindClipboardGuard();
  } catch (error) {
    errorMessage.value = error instanceof Error ? error.message : '课程作答加载失败。';
  }
});

/** 移动端切后台即视为切屏，切屏次数在管理端可审计。 */
onHide(() => {
  recordAnticheatEvent('page-hide');
});

onUnload(releaseClipboardGuard);
</script>

<template>
  <MobileBaseLayout>
    <view class="exam-attempt-page" :style="watermarkEnabled ? watermarkStyle : undefined">
      <view class="exam-topbar">
        <view>
          <text class="exam-topbar__caption">{{ assessmentLabel }} · 已答 {{ answeredCount }}/{{ questionCount }}</text>
          <text class="exam-topbar__title">{{ courseItemTitle }}</text>
        </view>
        <button class="exam-topbar__submit" :loading="submitting" :disabled="!editable" @tap="confirmSubmit">交卷</button>
      </view>

      <view v-if="errorMessage" class="state-card">
        <text>{{ errorMessage }}</text>
      </view>
      <template v-else-if="currentQuestion">
        <view class="progress-panel">
          <view class="progress-panel__line"><view class="progress-panel__value" :style="{ width: `${progressPercent}%` }" /></view>
          <text>{{ progressPercent }}% 已完成</text>
        </view>
        <scroll-view class="question-nav" scroll-x>
          <view class="question-nav__list">
            <button
              v-for="(item, index) in attempt?.questions"
              :key="item.attemptQuestionId"
              :class="[
                'question-nav__item',
                { 'question-nav__item--active': index === questionIndex, 'question-nav__item--done': item.answer?.isAnswered === 1 }
              ]"
              @tap="goToQuestion(index)"
            >
              {{ index + 1 }}
            </button>
          </view>
        </scroll-view>
        <view class="question-card" :class="{ 'no-copy': anticheatEnabled }">
          <view class="question-card__meta">
            <text>{{ getQuestionTypeText(currentQuestion) }}</text>
            <text>{{ currentQuestion.score }} 分</text>
          </view>
          <text class="question-card__stem">{{ questionIndex + 1 }}. {{ currentQuestion.stemSnapshot }}</text>
          <radio-group
            v-if="currentQuestion.questionType === 'SINGLE' || currentQuestion.questionType === 'JUDGE'"
            @change="setChoices(currentQuestion, [$event.detail.value])"
          >
            <label
              v-for="option in currentQuestion.options"
              :key="option.optionKey"
              :class="['answer-option', { 'answer-option--selected': isOptionSelected(currentQuestion, option.optionKey) }]"
            >
              <radio :value="option.optionKey" :checked="isOptionSelected(currentQuestion, option.optionKey)" :disabled="!editable" color="#2563EB" />
              <text class="answer-option__key">{{ option.optionKey }}</text>
              <text>{{ getOptionContent(option) }}</text>
            </label>
          </radio-group>
          <checkbox-group v-else-if="currentQuestion.questionType === 'MULTIPLE'" @change="setChoices(currentQuestion, $event.detail.value)">
            <label
              v-for="option in currentQuestion.options"
              :key="option.optionKey"
              :class="['answer-option', { 'answer-option--selected': isOptionSelected(currentQuestion, option.optionKey) }]"
            >
              <checkbox
                :value="option.optionKey"
                :checked="isOptionSelected(currentQuestion, option.optionKey)"
                :disabled="!editable"
                color="#2563EB"
              />
              <text class="answer-option__key">{{ option.optionKey }}</text>
              <text>{{ getOptionContent(option) }}</text>
            </label>
          </checkbox-group>
          <view v-else-if="currentQuestion.questionType === 'FILL'" class="fill-blank-inputs">
            <view v-for="blankNo in currentQuestion.blankCount ?? 1" :key="blankNo" class="fill-blank-inputs__item">
              <text>第 {{ blankNo }} 空</text>
              <input
                :value="getFillBlankValue(currentQuestion, blankNo)"
                :disabled="!editable"
                :placeholder="`请输入第 ${blankNo} 空答案`"
                @input="updateFillBlankAnswerEvent(currentQuestion, blankNo, $event)"
              />
            </view>
          </view>
          <textarea
            v-else
            v-model="currentQuestion.answer!.answerText"
            :disabled="!editable"
            placeholder="请输入答案"
            maxlength="5000"
            @input="updateTextAnswer(currentQuestion, currentQuestion.answer?.answerText ?? '')"
          />
        </view>
        <view class="answer-actions">
          <button class="answer-actions__secondary" :disabled="questionIndex === 0" @tap="goToQuestion(questionIndex - 1)">上一题</button>
          <button class="answer-actions__secondary" :disabled="questionIndex === questionCount - 1" @tap="goToQuestion(questionIndex + 1)">
            下一题
          </button>
          <button class="answer-actions__save" :loading="saving" :disabled="!editable" @tap="saveAnswers">暂存</button>
        </view>
        <button class="back-button" @tap="goBack">返回课程学习</button>
      </template>
    </view>
  </MobileBaseLayout>
</template>

<style scoped lang="less">
.exam-attempt-page {
  padding-bottom: 48rpx;
  background-image: var(--course-watermark-image, none);
  background-repeat: repeat;
  background-size: var(--course-watermark-size, auto);
}
.exam-topbar {
  position: sticky;
  top: 0;
  z-index: 4;
  display: flex;
  padding: 20rpx 28rpx;
  align-items: center;
  justify-content: space-between;
  background: #fff;
  box-shadow: 0 4rpx 14rpx rgb(15 23 42 / 6%);
}
.exam-topbar__caption,
.exam-topbar__title {
  display: block;
}
.exam-topbar__caption {
  color: #64748b;
  font-size: 24rpx;
}
.exam-topbar__title {
  margin-top: 4rpx;
  color: #172033;
  font-size: 28rpx;
  font-weight: 700;
}
.exam-topbar__submit,
.answer-actions button,
.back-button {
  margin: 0;
  border: 0;
  border-radius: 12rpx;
  font-size: 26rpx;
  font-weight: 600;
}
.exam-topbar__submit {
  padding: 0 28rpx;
  background: #2563eb;
  color: #fff;
  line-height: 64rpx;
}
.progress-panel,
.question-card,
.state-card {
  margin-top: 20rpx;
  padding: 26rpx 28rpx;
  border-radius: 18rpx;
  background: #fff;
}

.anticheat-notice {
  margin-top: 20rpx;
  padding: 22rpx 26rpx;
  border: 1rpx solid #fde68a;
  border-radius: 18rpx;
  background: #fffbeb;
  color: #b45309;
  font-size: 24rpx;
  line-height: 1.6;
}

/* 开启防作弊后题干与选项不可选中复制，输入框仍保留正常选择与输入能力。 */
.no-copy {
  user-select: none;
}

.no-copy textarea {
  user-select: text;
}
.progress-panel {
  display: flex;
  align-items: center;
  gap: 16rpx;
  color: #64748b;
  font-size: 23rpx;
}
.progress-panel__line {
  overflow: hidden;
  flex: 1;
  height: 10rpx;
  border-radius: 999rpx;
  background: #e2e8f0;
}
.progress-panel__value {
  height: 100%;
  border-radius: inherit;
  background: #2563eb;
  transition: width 0.2s ease;
}
.question-nav {
  margin-top: 18rpx;
  white-space: nowrap;
}
.question-nav__list {
  display: flex;
  flex-wrap: wrap;
  gap: 14rpx;
}
.question-nav__item {
  width: 62rpx;
  min-width: 62rpx;
  height: 62rpx;
  margin: 0;
  padding: 0;
  border: 1px solid #cbd5e1;
  border-radius: 14rpx;
  background: #fff;
  color: #64748b;
  font-size: 25rpx;
  line-height: 60rpx;
}
.question-nav__item--done {
  border-color: #bfdbfe;
  background: #eff6ff;
  color: #2563eb;
}
.question-nav__item--active {
  border-color: #2563eb;
  background: #2563eb;
  color: #fff;
}
.question-card__meta {
  display: flex;
  align-items: center;
  justify-content: space-between;
  color: #2563eb;
  font-size: 24rpx;
  font-weight: 600;
}
.question-card__stem {
  display: block;
  margin: 22rpx 0 30rpx;
  color: #172033;
  font-size: 31rpx;
  font-weight: 700;
  line-height: 1.65;
}
.answer-option {
  display: flex;
  margin-top: 18rpx;
  padding: 24rpx;
  align-items: center;
  gap: 14rpx;
  border: 1px solid #e2e8f0;
  border-radius: 14rpx;
  background: #f8fafc;
  color: #334155;
  font-size: 27rpx;
  line-height: 1.5;
}
.answer-option--selected {
  border-color: #93c5fd;
  background: #eff6ff;
  color: #1d4ed8;
}
.answer-option__key {
  display: inline-flex;
  width: 34rpx;
  height: 34rpx;
  align-items: center;
  justify-content: center;
  border-radius: 50%;
  background: #e2e8f0;
  color: #475569;
  font-size: 22rpx;
  font-weight: 700;
}
.answer-option--selected .answer-option__key {
  background: #2563eb;
  color: #fff;
}
textarea {
  width: 100%;
  min-height: 280rpx;
  padding: 22rpx;
  box-sizing: border-box;
  border: 1px solid #cbd5e1;
  border-radius: 14rpx;
  background: #f8fafc;
  color: #172033;
  font-size: 28rpx;
  line-height: 1.6;
}

.fill-blank-inputs {
  display: grid;
  gap: 18rpx;
}

.fill-blank-inputs__item text {
  display: block;
  margin-bottom: 10rpx;
  color: #475569;
  font-size: 25rpx;
}

.fill-blank-inputs__item input {
  width: 100%;
  height: 76rpx;
  padding: 0 22rpx;
  box-sizing: border-box;
  border: 1px solid #cbd5e1;
  border-radius: 14rpx;
  background: #f8fafc;
  color: #172033;
  font-size: 28rpx;
}
.answer-actions {
  display: grid;
  margin-top: 22rpx;
  grid-template-columns: 1fr 1fr 1.1fr;
  gap: 16rpx;
}
.answer-actions button {
  padding: 0;
  line-height: 72rpx;
}
.answer-actions__secondary {
  border: 1px solid #cbd5e1 !important;
  background: #fff;
  color: #475569;
}
.answer-actions__save {
  background: #2563eb;
  color: #fff;
}
.back-button {
  display: block;
  width: 100%;
  margin-top: 20rpx;
  border: 1px solid #cbd5e1;
  background: #fff;
  color: #475569;
  line-height: 72rpx;
}
.answer-actions button[disabled],
.exam-topbar__submit[disabled] {
  opacity: 0.45;
}
</style>
