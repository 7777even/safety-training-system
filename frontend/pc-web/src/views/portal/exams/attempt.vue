<script setup lang="ts">
import type { LearnerExamAttempt, LearnerExamOption, LearnerExamQuestion } from '@shared/types';
import { buildWatermarkTile } from '@shared/utils/watermark';
import { computed, onBeforeUnmount, onMounted, ref } from 'vue';
import { useRoute, useRouter } from 'vue-router';
import { ElMessage, ElMessageBox } from 'element-plus';
import { getLearnerExamAttempt, recordExamAnticheatEvent, saveExamAnswers, submitExamAttempt, timeoutSubmitExamAttempt } from '@/api/learner-exam';
import { QUESTION_TYPE_OPTIONS } from '@/constants/exam-options';
import { useUserStore } from '@/stores/modules/user';
import { hasPermission } from '@/utils/permission';

const route = useRoute();
const router = useRouter();
const userStore = useUserStore();
const attempt = ref<LearnerExamAttempt | null>(null);
const currentIndex = ref(0);
const remainingSeconds = ref(0);
const saving = ref(false);
const submitting = ref(false);
const errorMessage = ref('');
/** 最近一次成功暂存的全量答案快照，用于识别本地未暂存修改。 */
const savedAnswerSnapshot = ref('');
let timer: number | undefined;
let runtimeListenersBound = false;
const anticheatEventCounts = new Map<string, number>();
const attemptId = Number(route.params.examAttemptId);
const examId = Number(route.params.examId);
/** 培训入口只携带 attempt ID，考试上下文在加载的作答快照中恢复。 */
const isTrainingAttempt = computed(() => route.path.startsWith('/portal/training/attempt/'));
/** 培训题库练习不计分，作答页不展示考试分值与交卷措辞。 */
const isTrainingPractice = computed(() => attempt.value?.sourceType === 'TRAINING_PRACTICE');
/** 仅服务端冻结策略允许时启用事件审计，避免页面配置变更影响进行中的作答。 */
const isAnticheatEnabled = computed(() => attempt.value?.isAnticheatEnabled === 1);
const isWatermarkEnabled = computed(() => attempt.value?.isWatermarkEnabled === 1);
const watermarkStyle = computed<Record<string, string>>(() => {
  const tile = buildWatermarkTile(userStore.userInfo?.userName ?? userStore.userInfo?.userCode ?? '学员');
  return { '--exam-watermark-image': tile.image, '--exam-watermark-size': `${tile.width}px ${tile.height}px` };
});
const question = computed<LearnerExamQuestion | null>(() => attempt.value?.questions[currentIndex.value] ?? null);
const unansweredCount = computed(() => attempt.value?.questions.filter((item) => item.answer?.isAnswered !== 1).length ?? 0);
const canSaveAnswer = computed(() => hasPermission(userStore.userInfo, 'exam:attempt:answer'));
const canSubmitAttempt = computed(() => hasPermission(userStore.userInfo, 'exam:attempt:submit'));
/** 已提交尝试不能继续停留在答题页，避免把历史截止时间展示成可操作倒计时。 */
function isTerminalAttempt(value: LearnerExamAttempt | null): boolean {
  return Boolean(value?.submittedTime || ['SUBMITTED', 'PENDING_REVIEW', 'PENDING_PUBLISH', 'PUBLISHED', 'RESULT_EFFECTIVE'].includes(value?.attemptStatus ?? ''));
}
const isValidDeadline = computed(() => getDeadlineTimestamp() !== undefined);
const isDeadlineExpired = computed(() => {
  const deadlineTimestamp = getDeadlineTimestamp();
  return deadlineTimestamp !== undefined && deadlineTimestamp <= Date.now();
});
const editable = computed(() =>
  Boolean(attempt.value && !attempt.value.submittedTime && attempt.value.attemptStatus === 'ANSWERING'
    && isValidDeadline.value && !isDeadlineExpired.value && canSaveAnswer.value)
);
/** 交卷时使用服务端已记录耗时与本地持续作答时长中的较大值。 */
const usedSeconds = computed(() => {
  if (!attempt.value) return 0;
  const startedTimestamp = Date.parse(attempt.value.startedTime);
  const elapsedSeconds = Number.isFinite(startedTimestamp) ? Math.max(0, Math.floor((Date.now() - startedTimestamp) / 1000)) : 0;
  return Math.max(attempt.value.usedSeconds, elapsedSeconds);
});
const formatSeconds = (value: number): string =>
  `${String(Math.max(0, Math.floor(value / 60))).padStart(2, '0')}:${String(Math.max(0, value % 60)).padStart(2, '0')}`;
const remainingTimeText = computed(() => isValidDeadline.value ? formatSeconds(remainingSeconds.value) : '--:--');
const isMultiple = (item: LearnerExamQuestion): boolean => item.questionType === 'MULTIPLE';
const isText = (item: LearnerExamQuestion): boolean => ['FILL', 'ESSAY'].includes(item.questionType);
const isFill = (item: LearnerExamQuestion): boolean => item.questionType === 'FILL';
/** 读取题目快照中的选项文本。 */
const getOptionContent = (option: LearnerExamOption): string => option.optionContentSnapshot ?? option.optionContent ?? '';

/** 按作答截止时间实时计算剩余秒数。 */
function getRemainingSeconds(): number {
  const deadlineTimestamp = getDeadlineTimestamp();
  return deadlineTimestamp === undefined ? 0 : Math.max(0, Math.floor((deadlineTimestamp - Date.now()) / 1000));
}
function getDeadlineTimestamp(): number | undefined {
  const deadlineTime = attempt.value?.deadlineTime;
  if (!deadlineTime) return undefined;
  const deadlineTimestamp = Date.parse(deadlineTime);
  return Number.isFinite(deadlineTimestamp) ? deadlineTimestamp : undefined;
}

/** 把当前题目快照转换为 Contract 暂存 payload。 */
function answerPayload() {
  return {
    answers: (attempt.value?.questions ?? []).map((item) => ({
      attemptQuestionId: item.attemptQuestionId,
      answerText: item.answer?.answerText ?? null,
      items: item.answer?.items ?? []
    }))
  };
}

/** 将暂存请求内容序列化为可比较快照，比较范围与后端保存范围保持一致。 */
function answerSnapshot(): string {
  return JSON.stringify(answerPayload());
}

const hasUnsavedAnswers = computed(() => editable.value && answerSnapshot() !== savedAnswerSnapshot.value);

function updateChoice(item: LearnerExamQuestion, keys: string | string[]): void {
  const selected = Array.isArray(keys) ? keys : [keys];
  item.answer = {
    attemptQuestionId: item.attemptQuestionId,
    answerText: selected.join(','),
    items: selected.map((key, index) => ({ itemType: 'OPTION', itemKey: key, itemValue: key, sortOrder: index + 1 })),
    isAnswered: selected.length ? 1 : 0
  };
}

/** 为文本题初始化可编辑答案对象，避免空快照无法绑定输入框。 */
function ensureTextAnswer(item: LearnerExamQuestion): void {
  if (!item.answer) item.answer = { attemptQuestionId: item.attemptQuestionId, answerText: '', items: [], isAnswered: 0 };
}

/** 返回指定填空的已暂存内容；兼容历史单空答案文本的回填。 */
function getFillBlankValue(item: LearnerExamQuestion, blankNo: number): string {
  const saved = item.answer?.items.find((answerItem) => answerItem.itemType === 'BLANK' && answerItem.itemKey === String(blankNo));
  return saved?.itemValue ?? (blankNo === 1 ? item.answer?.answerText ?? '' : '');
}

/** 按空位序号保存填空答案，所有空填写后才将题目标记为已答。 */
function updateFillBlankAnswer(item: LearnerExamQuestion, blankNo: number, value: string): void {
  ensureTextAnswer(item);
  const values = new Map<number, string>();
  for (let index = 1; index <= (item.blankCount ?? 1); index++) values.set(index, getFillBlankValue(item, index));
  values.set(blankNo, value);
  const items = [...values.entries()]
    .filter(([, text]) => text.trim())
    .map(([index, text]) => ({ itemType: 'BLANK', itemKey: String(index), itemValue: text, sortOrder: index }));
  item.answer = {
    attemptQuestionId: item.attemptQuestionId,
    answerText: null,
    items,
    isAnswered: items.length === (item.blankCount ?? 1) ? 1 : 0
  };
}

async function loadAttempt(): Promise<void> {
  try {
    attempt.value = await getLearnerExamAttempt(attemptId);
    remainingSeconds.value = getRemainingSeconds();
    if (attempt.value.attemptStatus === 'ANSWERING' && !isValidDeadline.value) {
      errorMessage.value = '作答截止时间异常，请返回培训详情后重试。';
    }
  } catch (error) {
    errorMessage.value = error instanceof Error ? error.message : '作答加载失败。';
  }
}

/** 暂存当前答案；只有服务端成功后才更新本地已保存基线。 */
async function save(): Promise<boolean> {
  if (!editable.value) return true;
  if (saving.value) return false;
  const payload = answerPayload();
  const savedSnapshot = JSON.stringify(payload);
  saving.value = true;
  try {
    await saveExamAnswers(attemptId, payload);
    savedAnswerSnapshot.value = savedSnapshot;
    ElMessage.success('答案已暂存');
    return true;
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '答案暂存失败。');
    return false;
  } finally {
    saving.value = false;
  }
}

/** 手动提交或返回前先保护尚未暂存的本地答案。 */
async function ensureAnswersSaved(action: '提交' | '返回'): Promise<boolean> {
  if (!hasUnsavedAnswers.value) return true;
  try {
    await ElMessageBox.confirm(`当前答案尚未暂存，是否先暂存答案再${action}？`, '暂存答案', {
      type: 'warning',
      confirmButtonText: '暂存并继续',
      cancelButtonText: '留在当前页'
    });
  } catch {
    return false;
  }
  return save();
}

/** 仅追加最小事件审计；采集失败不阻断作答、暂存或交卷。 */
function recordAnticheatEvent(source: string, eventType = 'SWITCH_AWAY', requiresAnticheat = true): void {
  if (!editable.value || (requiresAnticheat && !isAnticheatEnabled.value)) return;
  const eventCount = (anticheatEventCounts.get(eventType) ?? 0) + 1;
  anticheatEventCounts.set(eventType, eventCount);
  void recordExamAnticheatEvent(attemptId, {
    eventType,
    eventTime: new Date().toISOString(),
    eventCount,
    actionTaken: 'RECORD',
    clientPayloadJson: JSON.stringify({ source })
  }).catch(() => undefined);
}

/** 记录切离答题页并向学员反馈，明确说明异常行为已进入审计。 */
function handleSwitchAway(source: string): void {
  recordAnticheatEvent(source);
  ElMessage.warning('检测到离开答题页面，已记录本次异常行为。');
}

/** 防作弊开启时阻止浏览器剪贴板与右键菜单操作，并保留最小审计事件。 */
function blockClipboard(event: globalThis.Event): void {
  event.preventDefault();
  recordAnticheatEvent(`clipboard-${event.type}`, 'COPY');
  ElMessage.warning('本考试已禁止复制粘贴，本次操作已记录。');
}

/** 浏览器失焦时记录当前作答切离页面。 */
function handleWindowBlur(): void {
  handleSwitchAway('window-blur');
}

/** 页面进入后台时记录当前作答切离页面。 */
function handleVisibilityChange(): void {
  if (globalThis.document.visibilityState === 'hidden') handleSwitchAway('page-hidden');
}

/** 作答页只承接已创建 attempt，加载后立即启动倒计时与反作弊监听。 */
function startAttemptRuntime(): void {
  if (timer) globalThis.clearInterval(timer);
  timer = globalThis.setInterval(() => {
    if (!editable.value) return;
    remainingSeconds.value = getRemainingSeconds();
    if (remainingSeconds.value <= 0) {
      globalThis.clearInterval(timer);
      void finish(true);
    }
  }, 1000);
  if (runtimeListenersBound) return;
  if (isAnticheatEnabled.value) {
    globalThis.addEventListener('blur', handleWindowBlur);
    globalThis.document.addEventListener('visibilitychange', handleVisibilityChange);
    globalThis.document.addEventListener('copy', blockClipboard);
    globalThis.document.addEventListener('cut', blockClipboard);
    globalThis.document.addEventListener('paste', blockClipboard);
    globalThis.document.addEventListener('contextmenu', blockClipboard);
    runtimeListenersBound = true;
  }
}

/** 清理答题运行期资源，页面卸载或异常交卷后都应停止监听。 */
function stopAttemptRuntime(): void {
  if (timer) {
    globalThis.clearInterval(timer);
    timer = undefined;
  }
  if (!runtimeListenersBound) return;
  globalThis.removeEventListener('blur', handleWindowBlur);
  globalThis.document.removeEventListener('visibilitychange', handleVisibilityChange);
  globalThis.document.removeEventListener('copy', blockClipboard);
  globalThis.document.removeEventListener('cut', blockClipboard);
  globalThis.document.removeEventListener('paste', blockClipboard);
  globalThis.document.removeEventListener('contextmenu', blockClipboard);
  runtimeListenersBound = false;
}

/** 按入口上下文进入对应的只读结果页。 */
async function goResult(): Promise<void> {
  await router.replace(isTrainingAttempt.value
    ? `/portal/training/attempt/${attemptId}/result`
    : `/portal/exams/${attempt.value?.examId ?? examId}/result/${attemptId}`);
}

async function finish(timeout = false): Promise<void> {
  if (!canSubmitAttempt.value || attempt.value?.submittedTime || submitting.value) return;
  if (!timeout && !(await ensureAnswersSaved('提交'))) return;
  submitting.value = true;
  try {
    const payload = { submitReason: timeout ? 'TIMEOUT' : 'MANUAL', usedSeconds: usedSeconds.value };
    if (timeout) {
      recordAnticheatEvent('timeout-submit', 'TIMEOUT_SUBMIT', false);
      await timeoutSubmitExamAttempt(attemptId, payload);
    } else await submitExamAttempt(attemptId, payload);
    stopAttemptRuntime();
    await goResult();
  } finally {
    submitting.value = false;
  }
}

async function confirmSubmit(): Promise<void> {
  if (isTrainingPractice.value && unansweredCount.value > 0) {
    ElMessage.warning(`题库练习需完成全部题目后提交，当前还有 ${unansweredCount.value} 题未作答。`);
    return;
  }
  try {
    await ElMessageBox.confirm(
      isTrainingPractice.value ? '提交后将进入练习反馈页，确认提交吗？' : '交卷后将无法继续编辑答案，确认提交吗？',
      isTrainingPractice.value ? '确认提交练习' : '确认交卷',
      { type: 'warning' }
    );
    await finish();
  } catch {
    return;
  }
}

/** 返回考试中心前同样保护未暂存的本地作答。 */
async function backToExam(): Promise<void> {
  if (await ensureAnswersSaved('返回')) await router.back();
}

/** 返回与后台题库一致的题型中文标签。 */
function getQuestionTypeText(question: LearnerExamQuestion): string {
  return QUESTION_TYPE_OPTIONS.find((item) => item.value === question.questionType)?.label ?? '题目';
}

onMounted(async () => {
  await loadAttempt();
  if (isTerminalAttempt(attempt.value)) {
    await goResult();
    return;
  }
  attempt.value?.questions.forEach(ensureTextAnswer);
  savedAnswerSnapshot.value = answerSnapshot();
  if (attempt.value?.attemptStatus === 'ANSWERING' && isDeadlineExpired.value) {
    await finish(true);
    return;
  }
  if (editable.value) startAttemptRuntime();
});
onBeforeUnmount(() => {
  stopAttemptRuntime();
});
</script>

<template>
  <section class="attempt-page" :style="isWatermarkEnabled ? watermarkStyle : undefined">
    <header class="exam-top">
      <div class="page-heading">
        <p class="page-heading__eyebrow">MY EXAMS</p>
        <strong>{{ isTrainingPractice ? '题库练习' : '在线考试' }}</strong>
      </div>
      <span>
        剩余时间：
        <b>{{ remainingTimeText }}</b>
      </span>
      <div>
        <ElButton v-if="canSubmitAttempt" type="danger" :loading="submitting" :disabled="Boolean(attempt?.submittedTime)" @click="confirmSubmit">
          {{ isTrainingPractice ? '提交练习' : '提交试卷' }}
        </ElButton>
        <ElButton @click="backToExam">返回</ElButton>
      </div>
    </header>
    <ElAlert v-if="errorMessage" :title="errorMessage" type="error" show-icon :closable="false" />
    <ElAlert
      v-if="isAnticheatEnabled"
      class="anticheat-notice"
      title="本考试已开启防作弊：离开答题页面、复制粘贴与右键菜单都会被记录。"
      type="warning"
      show-icon
      :closable="false"
    />
    <main v-if="attempt && question" class="exam-body">
      <aside>
        <h3>答题卡</h3>
        <button
          v-for="(item, index) in attempt.questions"
          :key="item.attemptQuestionId"
          :class="{ active: index === currentIndex, answered: item.answer?.isAnswered === 1 }"
          @click="currentIndex = index"
        >
          {{ index + 1 }}
        </button>
      </aside>
      <article class="question-card" :class="{ 'no-copy': isAnticheatEnabled }">
        <div class="question-title">
          {{ currentIndex + 1 }}. {{ getQuestionTypeText(question) }}
          <template v-if="!isTrainingPractice">（{{ question.score }}分）</template>
          <template v-else>（完成后查看练习反馈）</template>
        </div>
        <div style="margin: 5px 0">{{ question.stemSnapshot }}</div>
        <ElRadioGroup
          v-if="!isText(question) && !isMultiple(question)"
          :model-value="question.answer?.answerText ?? undefined"
          :disabled="!editable"
          @update:model-value="updateChoice(question, String($event))"
        >
          <ElRadio v-for="option in question.options" :key="option.optionKey" :value="option.optionKey">
            {{ option.optionKey }}. {{ getOptionContent(option) }}
          </ElRadio>
        </ElRadioGroup>
        <ElCheckboxGroup
          v-else-if="isMultiple(question)"
          :model-value="question.answer?.items.map((item) => item.itemKey) ?? []"
          :disabled="!editable"
          @update:model-value="updateChoice(question, $event as string[])"
        >
          <ElCheckbox v-for="option in question.options" :key="option.optionKey" :value="option.optionKey">
            {{ option.optionKey }}. {{ getOptionContent(option) }}
          </ElCheckbox>
        </ElCheckboxGroup>
        <div v-else-if="isFill(question)" class="fill-blank-inputs">
          <ElInput
            v-for="blankNo in question.blankCount ?? 1"
            :key="blankNo"
            :model-value="getFillBlankValue(question, blankNo)"
            :disabled="!editable"
            :placeholder="`请输入第 ${blankNo} 空答案`"
            @update:model-value="updateFillBlankAnswer(question, blankNo, String($event))"
          >
            <template #prepend>第 {{ blankNo }} 空</template>
          </ElInput>
        </div>
        <ElInput
          v-else
          v-model="question.answer!.answerText"
          type="textarea"
          :autosize="{ minRows: 4 }"
          :disabled="!editable"
          placeholder="请输入答案"
          @input="question.answer!.isAnswered = question.answer!.answerText ? 1 : 0"
        />
        <footer>
          <ElButton :disabled="currentIndex === 0" @click="currentIndex--">上一题</ElButton>
          <ElButton :disabled="currentIndex === attempt.questions.length - 1" @click="currentIndex++">下一题</ElButton>
          <ElButton type="primary" :loading="saving" :disabled="!editable" @click="save">暂存答案</ElButton>
        </footer>
      </article>
    </main>
  </section>
</template>
<style scoped lang="less">
.attempt-page {
  height: 100%;
  background-image: var(--exam-watermark-image, none);
  background-repeat: repeat;
  background-size: var(--exam-watermark-size, auto);
  background-color: var(--color-bg-page);
}

.exam-top {
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

.exam-top b {
  color: #f04452;
}

.page-heading__eyebrow {
  margin: 0 5px 4px 0;
  color: #2d6cdf;
  font-size: 11px;
  font-weight: 700;
  letter-spacing: 0.12em;
}

.exam-body {
  display: grid;
  grid-template-columns: 190px 1fr;
  gap: 20px;
  width: 1200px;
  margin: 24px auto;
}

.exam-body aside,
.question-card {
  padding: 24px;
  border-radius: 12px;
  background: #fff;
}

.exam-body aside button {
  width: 36px;
  height: 36px;
  margin: 5px;
  border: 1px solid #d0d7e2;
  border-radius: 6px;
  background: #fff;
}

.exam-body aside button.active {
  border-color: #2563eb;
}

.exam-body aside button.answered {
  background: #16a34a;
  color: #fff;
}

.question-title {
  margin-bottom: 28px;
  font-weight: 700;
}

.el-radio-group {
  display: flex;
  flex-direction: column;
  align-items: flex-start;
}

.el-radio,
.el-checkbox {
  display: flex;
}

/** 与课程考核保持一致，突出当前考试已启用的行为审计。 */
.anticheat-notice {
  width: 98%;
  margin: 16px 28px 0;
}

/* 防作弊时仅限制题干和选项复制，文本答案输入仍保持正常编辑。 */
.no-copy {
  user-select: none;
}

.no-copy :deep(textarea),
.no-copy :deep(input) {
  user-select: text;
}

.fill-blank-inputs {
  display: grid;
  gap: 12px;
}

.question-card footer {
  display: flex;
  gap: 12px;
  margin-top: 32px;
}
</style>
