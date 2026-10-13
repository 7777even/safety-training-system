<script setup lang="ts">
import type { LearnerExamQuestion, LearnerExamAttempt, LearnerExamOption, SaveLearnerExamAnswersPayload } from '@shared/types';
import { buildWatermarkTile } from '@shared/utils/watermark';
import { onHide, onLoad, onShow, onUnload } from '@dcloudio/uni-app';
import { computed, ref } from 'vue';
import { getLearnerExamAttempt, recordExamAnticheatEvent, saveExamAnswers, submitExamAttempt, timeoutSubmitExamAttempt } from '@/api/learner-exam';
import MobileBaseLayout from '@/layouts/MobileBaseLayout.vue';
import { useUserStore } from '@/stores';

/** 当前作答快照与正在浏览的题目序号。 */
const attempt = ref<LearnerExamAttempt | null>(null);
const questionIndex = ref(0);
const remainingSeconds = ref(0);
const errorMessage = ref('');
const saving = ref(false);
const savedAnswerSnapshot = ref('');
let examAttemptId = 0;
let timer: number | undefined;
let clipboardListenersBound = false;
const anticheatEventCounts = new Map<string, number>();
const userStore = useUserStore();

/** 已交卷或进入阅卷、发布阶段的作答不可继续停留在答题页。 */
function isTerminalAttempt(value: LearnerExamAttempt | null): boolean {
  return Boolean(
    value?.submittedTime || ['SUBMITTED', 'PENDING_REVIEW', 'PENDING_PUBLISH', 'PUBLISHED', 'RESULT_EFFECTIVE'].includes(value?.attemptStatus ?? '')
  );
}
/** 仅接受服务端下发的有效截止时间，避免异常值被误判为 00:00。 */
function getDeadlineTimestamp(value: LearnerExamAttempt | null): number | null {
  const timestamp = value?.deadlineTime ? new Date(value.deadlineTime).getTime() : Number.NaN;
  return Number.isFinite(timestamp) ? timestamp : null;
}
function isDeadlineExpired(value: LearnerExamAttempt | null): boolean {
  const timestamp = getDeadlineTimestamp(value);
  return timestamp != null && timestamp <= Date.now();
}
function goResult(): void {
  uni.redirectTo({ url: `/pages/exam/result?examAttemptId=${examAttemptId}` });
}

/** 当前显示题目，始终从后端返回的作答快照中读取。 */
const currentQuestion = computed<LearnerExamQuestion | null>(() => attempt.value?.questions[questionIndex.value] ?? null);
/** 培训题库练习不计分，作答页使用练习语义并要求全部题目作答。 */
const isTrainingPractice = computed(() => attempt.value?.sourceType === 'TRAINING_PRACTICE');
const isAnticheatEnabled = computed(() => attempt.value?.isAnticheatEnabled === 1);
const isWatermarkEnabled = computed(() => attempt.value?.isWatermarkEnabled === 1);
const watermarkStyle = computed<Record<string, string>>(() => {
  const tile = buildWatermarkTile(userStore.userInfo?.userName ?? userStore.userInfo?.userCode ?? '学员');
  return {
    '--exam-watermark-image': tile.image,
    '--exam-watermark-size': `${Math.round(tile.width * 1.5)}rpx ${Math.round(tile.height * 1.5)}rpx`
  };
});
/** 仅答题中的尝试允许编辑或交卷。 */
const editable = computed(() =>
  Boolean(attempt.value && !isTerminalAttempt(attempt.value) && attempt.value.attemptStatus === 'ANSWERING' && !isDeadlineExpired(attempt.value))
);
/** 已作答数量用于顶部进度反馈。 */
const answeredCount = computed(() => attempt.value?.questions.filter((item) => item.answer?.isAnswered === 1).length ?? 0);
const questionCount = computed(() => attempt.value?.questions.length ?? 0);
const unansweredCount = computed(() => questionCount.value - answeredCount.value);
const progressPercent = computed(() => (questionCount.value ? Math.round((answeredCount.value / questionCount.value) * 100) : 0));
const timeText = computed(() => {
  if (getDeadlineTimestamp(attempt.value) == null) return '--:--';
  return `${String(Math.max(0, Math.floor(remainingSeconds.value / 60))).padStart(2, '0')}:${String(Math.max(0, remainingSeconds.value % 60)).padStart(2, '0')}`;
});
/** 兼容 OpenAPI 题目快照字段，并保留现有 Mock 的展示字段。 */
const getOptionContent = (option: LearnerExamOption): string => option.optionContentSnapshot ?? option.optionContent ?? '';

/** 为未回答题初始化可编辑的答案对象。 */
function ensureAnswer(question: LearnerExamQuestion): void {
  if (!question.answer)
    question.answer = {
      attemptQuestionId: question.attemptQuestionId,
      answerText: '',
      items: [],
      isAnswered: 0
    };
}

/** 记录单选或多选题的已选项，并同步答案文本和答案项。 */
function setChoices(question: LearnerExamQuestion, keys: string[]): void {
  question.answer = {
    attemptQuestionId: question.attemptQuestionId,
    answerText: keys.join(','),
    items: keys.map((key, index) => ({ itemType: 'OPTION', itemKey: key, itemValue: key, sortOrder: index + 1 })),
    isAnswered: keys.length ? 1 : 0
  };
}

/** 更新填空或简答答案的已回答状态。 */
function updateTextAnswer(question: LearnerExamQuestion, value: string): void {
  ensureAnswer(question);
  question.answer!.answerText = value;
  question.answer!.isAnswered = value.trim() ? 1 : 0;
}

/** 判断选项是否已被当前题目选中。 */
function isOptionSelected(question: LearnerExamQuestion, optionKey: string): boolean {
  return question.answer?.items.some((item) => item.itemKey === optionKey) ?? false;
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

/** 兼容当前契约与历史快照的多选题类型，确保使用 checkbox 组件。 */
function isMultipleQuestion(question: LearnerExamQuestion): boolean {
  return ['MULTIPLE', 'MULTIPLE_CHOICE'].includes(question.questionType);
}

/** 返回问题类型的移动端简短标签。 */
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

/** 将本地答案快照转换为保存接口需要的最小载荷。 */
function buildSavePayload(): SaveLearnerExamAnswersPayload {
  return {
    answers: (attempt.value?.questions ?? []).map((item) => ({
      attemptQuestionId: item.attemptQuestionId,
      answerText: item.answer?.answerText ?? null,
      items: item.answer?.items ?? []
    }))
  };
}

/** 序列化当前答案，用于判断用户编辑后是否需要在离开前再次暂存。 */
function answerSnapshot(): string {
  return JSON.stringify(buildSavePayload());
}

/** 暂存整份答案快照，避免切题时产生额外网络请求。 */
async function saveAnswers(showSuccess = true): Promise<boolean> {
  if (!editable.value || saving.value) return !saving.value;
  try {
    saving.value = true;
    await saveExamAnswers(examAttemptId, buildSavePayload());
    savedAnswerSnapshot.value = answerSnapshot();
    if (showSuccess) uni.showToast({ title: '答案已暂存', icon: 'success' });
    return true;
  } catch (error) {
    uni.showToast({ title: error instanceof Error ? error.message : '暂存失败', icon: 'none' });
    return false;
  } finally {
    saving.value = false;
  }
}

/** 交卷或返回前确保本地编辑已写入服务端，暂存失败时保留在当前页。 */
async function ensureAnswersSaved(): Promise<boolean> {
  if (!editable.value || answerSnapshot() === savedAnswerSnapshot.value) return true;
  return saveAnswers(false);
}

/** 仅追加最小事件审计；采集失败不阻断移动端作答或交卷。 */
function recordAnticheatEvent(source: string, eventType = 'SWITCH_AWAY', requiresAnticheat = true): void {
  if (!editable.value || (requiresAnticheat && !isAnticheatEnabled.value)) return;
  const eventCount = (anticheatEventCounts.get(eventType) ?? 0) + 1;
  anticheatEventCounts.set(eventType, eventCount);
  void recordExamAnticheatEvent(examAttemptId, {
    eventType,
    eventTime: new Date().toISOString(),
    eventCount,
    actionTaken: 'RECORD',
    clientPayloadJson: JSON.stringify({ source })
  }).catch(() => undefined);
}

/** 防作弊开启时阻止剪贴板与右键菜单操作，并记录可审计的最小事件。 */
function blockClipboard(event: Event): void {
  event.preventDefault();
  recordAnticheatEvent(`clipboard-${event.type}`, 'COPY');
  uni.showToast({ title: '本考试已禁止复制粘贴，本次操作已记录', icon: 'none' });
}

/** 浏览器剪贴板事件仅在 H5 有效，原生端仍由系统容器处理。 */
function bindClipboardGuard(): void {
  // #ifdef H5
  if (!isAnticheatEnabled.value || clipboardListenersBound) return;
  globalThis.document.addEventListener('copy', blockClipboard);
  globalThis.document.addEventListener('cut', blockClipboard);
  globalThis.document.addEventListener('paste', blockClipboard);
  globalThis.document.addEventListener('contextmenu', blockClipboard);
  clipboardListenersBound = true;
  // #endif
}

/** 页面离开后释放 H5 监听，避免影响其它移动页面。 */
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

/** 提交当前作答；超时时由计时器调用同一条 Contract 路径。 */
async function submitAttempt(timeout = false): Promise<void> {
  if (!attempt.value || (!timeout && !editable.value)) return;
  if (!timeout && !(await ensureAnswersSaved())) return;
  try {
    const data = { submitReason: timeout ? 'TIMEOUT' : 'MANUAL', usedSeconds: attempt.value?.usedSeconds ?? 0 };
    if (timeout) {
      recordAnticheatEvent('timeout-submit', 'TIMEOUT_SUBMIT', false);
      await timeoutSubmitExamAttempt(examAttemptId, data);
    } else await submitExamAttempt(examAttemptId, data);
    releaseClipboardGuard();
    goResult();
  } catch (error) {
    uni.showToast({ title: error instanceof Error ? error.message : '交卷失败', icon: 'none' });
  }
}

/** 交卷前提示未答数量，降低误交卷风险。 */
async function confirmSubmit(): Promise<void> {
  if (!(await ensureAnswersSaved())) return;
  if (isTrainingPractice.value && unansweredCount.value > 0) {
    uni.showToast({ title: `题库练习需完成全部题目后提交，当前还有 ${unansweredCount.value} 题未作答`, icon: 'none' });
    return;
  }
  const result = await uni.showModal({
    title: isTrainingPractice.value ? '确认提交练习' : '确认交卷',
    content: isTrainingPractice.value
      ? '提交后将进入练习反馈页。'
      : unansweredCount.value
        ? `还有 ${unansweredCount.value} 题未作答，确认交卷吗？`
        : '所有题目已作答，确认交卷吗？',
    confirmText: isTrainingPractice.value ? '提交练习' : '确认交卷',
    confirmColor: '#2563EB'
  });
  if (result.confirm) await submitAttempt();
}

/** 返回考试列表前先暂存未保存答案，避免手势返回或按钮离开导致答案丢失。 */
async function goBack(): Promise<void> {
  if (await ensureAnswersSaved()) uni.navigateBack();
}

/** 切换题目时只改变本地浏览状态，答案由显式暂存或交卷提交。 */
function goToQuestion(nextIndex: number): void {
  if (nextIndex >= 0 && nextIndex < questionCount.value) questionIndex.value = nextIndex;
}

/** 以服务端截止时刻重算剩余时间，不因退出页面暂停倒计时。 */
function refreshRemainingSeconds(): void {
  const deadlineTimestamp = getDeadlineTimestamp(attempt.value);
  remainingSeconds.value = deadlineTimestamp == null ? 0 : Math.max(0, Math.floor((deadlineTimestamp - Date.now()) / 1000));
}

/** 加载后恢复服务端作答快照，终态直接查看结果。 */
onLoad(async (query) => {
  examAttemptId = Number(query?.examAttemptId);
  try {
    attempt.value = await getLearnerExamAttempt(examAttemptId);
    if (isTerminalAttempt(attempt.value)) {
      goResult();
      return;
    }
    attempt.value.questions.forEach(ensureAnswer);
    savedAnswerSnapshot.value = answerSnapshot();
    if (getDeadlineTimestamp(attempt.value) == null) {
      errorMessage.value = '作答截止时间异常，请返回培训详情后重试。';
      return;
    }
    refreshRemainingSeconds();
    if (isDeadlineExpired(attempt.value)) {
      await submitAttempt(true);
      return;
    }
    bindClipboardGuard();
    timer = globalThis.setInterval(() => {
      refreshRemainingSeconds();
      if (isDeadlineExpired(attempt.value) && timer) {
        globalThis.clearInterval(timer);
        void submitAttempt(true);
      }
    }, 1000) as unknown as number;
  } catch (error) {
    errorMessage.value = error instanceof Error ? error.message : '作答加载失败。';
  }
});

onUnload(() => {
  if (timer) globalThis.clearInterval(timer);
  releaseClipboardGuard();
});

onShow(() => {
  if (!attempt.value || isTerminalAttempt(attempt.value)) return;
  refreshRemainingSeconds();
  if (isDeadlineExpired(attempt.value) && timer) {
    globalThis.clearInterval(timer);
    void submitAttempt(true);
  }
});

/** 移动端切入后台时按 Contract 记录切离考试页面。 */
onHide(() => recordAnticheatEvent('page-hide'));
</script>

<template>
  <MobileBaseLayout>
    <view class="exam-attempt-page" :style="isWatermarkEnabled ? watermarkStyle : undefined">
      <view class="exam-topbar">
        <view>
          <text class="exam-topbar__caption">已答 {{ answeredCount }}/{{ questionCount }}</text>
          <text class="exam-topbar__time">剩余 {{ timeText }}</text>
        </view>
        <button class="exam-topbar__submit" :disabled="!editable" @click="confirmSubmit">{{ isTrainingPractice ? '提交练习' : '交卷' }}</button>
      </view>

      <view v-if="errorMessage" class="state-card">
        <text>{{ errorMessage }}</text>
      </view>
      <template v-else-if="currentQuestion">
        <view v-if="isAnticheatEnabled" class="anticheat-notice">
          本考试已开启防作弊：离开答题页面、复制粘贴与右键菜单都会被记录。
        </view>
        <view class="progress-panel">
          <view class="progress-panel__line">
            <view class="progress-panel__value" :style="{ width: `${progressPercent}%` }" />
          </view>
          <text>{{ progressPercent }}% 已完成</text>
        </view>

        <scroll-view class="question-nav" scroll-x>
          <view class="question-nav__list">
            <button
              v-for="(item, itemIndex) in attempt?.questions"
              :key="item.attemptQuestionId"
              :class="[
                'question-nav__item',
                { 'question-nav__item--active': itemIndex === questionIndex, 'question-nav__item--done': item.answer?.isAnswered === 1 }
              ]"
              @click="goToQuestion(itemIndex)"
            >
              {{ itemIndex + 1 }}
            </button>
          </view>
        </scroll-view>

        <view class="question-card" :class="{ 'no-copy': isAnticheatEnabled }">
          <view class="question-card__meta">
            <text>{{ getQuestionTypeText(currentQuestion) }}</text>
            <text>{{ isTrainingPractice ? '完成后查看反馈' : `${currentQuestion.score} 分` }}</text>
          </view>
          <text class="question-card__stem">{{ questionIndex + 1 }}. {{ currentQuestion.stemSnapshot }}</text>

          <radio-group
            v-if="!isMultipleQuestion(currentQuestion) && currentQuestion.options.length"
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

          <checkbox-group v-else-if="isMultipleQuestion(currentQuestion) && currentQuestion.options.length" @change="setChoices(currentQuestion, $event.detail.value)">
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
              >
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
          <button class="answer-actions__secondary" :disabled="questionIndex === 0" @click="goToQuestion(questionIndex - 1)">上一题</button>
          <button class="answer-actions__secondary" :disabled="questionIndex === questionCount - 1" @click="goToQuestion(questionIndex + 1)">
            下一题
          </button>
          <button class="answer-actions__save" :loading="saving" :disabled="!editable" @click="() => void saveAnswers()">暂存</button>
        </view>
        <button class="back-button" @click="goBack">返回我的考试</button>
      </template>
    </view>
  </MobileBaseLayout>
</template>

<style scoped lang="less">
.exam-attempt-page {
  min-height: 100vh;
  margin: 0 -32rpx;
  padding: 0 32rpx 48rpx;
  box-sizing: border-box;
  background-image: var(--exam-watermark-image, none);
  background-repeat: repeat;
  background-size: var(--exam-watermark-size, auto);
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
.exam-topbar__time {
  display: block;
}

.exam-topbar__caption {
  color: #64748b;
  font-size: 24rpx;
}

.exam-topbar__time {
  margin-top: 4rpx;
  color: #ef4444;
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
  gap: 14rpx;
  display: flex;
  flex-wrap: wrap;
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

/* 仅限制题干与选项复制，文本答案输入保留正常编辑体验。 */
.no-copy {
  user-select: none;
}

.no-copy textarea,
.no-copy input {
  user-select: text;
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
  background: #fff;
  color: #475569;
  border: 1px solid #cbd5e1 !important;
}

.answer-actions__save {
  background: #2563eb;
  color: #fff;
}

.back-button {
  display: block;
  width: 100%;
  margin-top: 20rpx;
  border: 1px solid #cbd5e1 !important;
  background: #fff;
  color: #475569;
  line-height: 72rpx;
}

.answer-actions button[disabled],
.exam-topbar__submit[disabled] {
  opacity: 0.45;
}
</style>
