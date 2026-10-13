<script setup lang="ts">
import type { CourseAssessmentAttempt, LearnerExamOption, LearnerExamQuestion } from '@shared/types';
import { buildWatermarkTile } from '@shared/utils/watermark';
import { computed, onBeforeUnmount, onMounted, ref } from 'vue';
import { ElMessage, ElMessageBox } from 'element-plus';
import { useRoute, useRouter } from 'vue-router';
import { getCourseAssessmentAttempt, getLearnerCourseDetail, saveCourseAssessmentAnswers, submitCourseAssessmentAttempt } from '@/api/learner-course';
import { recordExamAnticheatEvent } from '@/api/learner-exam';
import { QUESTION_TYPE_OPTIONS } from '@/constants/exam-options';
import { useUserStore } from '@/stores/modules/user';

const route = useRoute();
const router = useRouter();
const userStore = useUserStore();
const attempt = ref<CourseAssessmentAttempt>();
const currentIndex = ref(0);
const loading = ref(true);
const saving = ref(false);
const submitting = ref(false);
const errorMessage = ref('');
const enrollmentId = Number(route.params.enrollmentId);
const itemId = Number(route.params.itemId);
const attemptId = Number(route.params.attemptId);
const question = computed<LearnerExamQuestion | null>(() => attempt.value?.questions[currentIndex.value] ?? null);
const editable = computed(() => attempt.value?.attemptStatus === 'ANSWERING');
const savedAnswerSnapshot = ref('');
/** 课程级「启用学习防作弊」开关；未开启时不注册任何监听、不拦截复制粘贴。 */
const anticheatEnabled = ref(false);
/** 课程级「启用学习水印」开关；与防作弊同源，都取课程冻结快照。 */
const watermarkEnabled = ref(false);
/** 水印瓦片按当前登录用户名生成，未取到时回退账号编码，与学习页、考试页取值一致。 */
const watermarkStyle = computed<Record<string, string>>(() => {
  const tile = buildWatermarkTile(userStore.userInfo?.userName ?? userStore.userInfo?.userCode ?? '学员');
  return {
    '--course-watermark-image': tile.image,
    '--course-watermark-size': `${tile.width}px ${tile.height}px`
  };
});
let runtimeListenersBound = false;

/** 将课程考核类型转换为学员可理解的页面标题。 */
const assessmentTitle = computed(() => (attempt.value?.assessmentKind === 'BANK_PRACTICE' ? '题库练习' : '课程测验'));
/** 当前课程内容项名称由学习页路由透传，资源名称仅作为直达链接的兜底展示。 */
const courseItemTitle = computed(() => String(route.query.itemTitle ?? attempt.value?.resourceName ?? assessmentTitle.value));
const isMultiple = (item: LearnerExamQuestion): boolean => item.questionType === 'MULTIPLE';
const isText = (item: LearnerExamQuestion): boolean => ['FILL', 'ESSAY'].includes(item.questionType);
const getOptionContent = (option: LearnerExamOption): string => option.optionContentSnapshot ?? option.optionContent ?? '';
const getQuestionTypeText = (item: LearnerExamQuestion): string =>
  QUESTION_TYPE_OPTIONS.find((option) => option.value === item.questionType)?.label ?? '题目';

/** 为填空题和主观题创建可编辑答案对象。 */
function ensureTextAnswer(item: LearnerExamQuestion): void {
  if (!item.answer) item.answer = { attemptQuestionId: item.attemptQuestionId, answerText: '', items: [], isAnswered: 0 };
}

/** 将选择题结果同步为课程作答 Contract 所需的答案结构。 */
function updateChoice(item: LearnerExamQuestion, keys: string | string[]): void {
  const selected = Array.isArray(keys) ? keys : [keys];
  item.answer = {
    attemptQuestionId: item.attemptQuestionId,
    answerText: selected.join(','),
    items: selected.map((key, index) => ({ itemType: 'OPTION', itemKey: key, itemValue: key, sortOrder: index + 1 })),
    isAnswered: selected.length ? 1 : 0
  };
}

/** 生成课程域暂存接口的全量答案快照。 */
function answerPayload() {
  return {
    answers: (attempt.value?.questions ?? []).map((item) => ({
      attemptQuestionId: item.attemptQuestionId,
      answerText: item.answer?.answerText ?? null,
      items: item.answer?.items ?? []
    }))
  };
}

/** 比较当前编辑值与最近一次成功暂存的快照，避免提交遗漏修改。 */
function answerSnapshot(): string {
  return JSON.stringify(answerPayload());
}

const hasUnsavedAnswers = computed(() => editable.value && answerSnapshot() !== savedAnswerSnapshot.value);

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

/** 浏览器失焦或页面进入后台时记录本次切屏。 */
function handleSwitchAway(source: string): void {
  recordAnticheatEvent(source);
  ElMessage.warning('检测到离开答题页面，已记录本次异常行为。');
}

/** 复制、剪切、粘贴与右键菜单在开启防作弊时一律拦截并留痕。 */
function blockClipboard(event: globalThis.Event): void {
  event.preventDefault();
  recordAnticheatEvent(`clipboard-${event.type}`, 'COPY');
  ElMessage.warning('本考核已禁止复制粘贴，本次操作已记录。');
}

function handleWindowBlur(): void {
  handleSwitchAway('window-blur');
}

function handleVisibilityChange(): void {
  if (globalThis.document.visibilityState === 'hidden') handleSwitchAway('page-hidden');
}

/** 按课程开关注册或解绑答题期监听，关闭时保持普通作答体验。 */
function syncAnticheatListeners(enabled: boolean): void {
  anticheatEnabled.value = enabled;
  if (!enabled || runtimeListenersBound) return;
  globalThis.addEventListener('blur', handleWindowBlur);
  globalThis.document.addEventListener('visibilitychange', handleVisibilityChange);
  globalThis.document.addEventListener('copy', blockClipboard);
  globalThis.document.addEventListener('cut', blockClipboard);
  globalThis.document.addEventListener('paste', blockClipboard);
  globalThis.document.addEventListener('contextmenu', blockClipboard);
  runtimeListenersBound = true;
}

/** 页面卸载时统一解绑，避免离开答题后仍拦截其它页面的复制粘贴。 */
function releaseAnticheatListeners(): void {
  if (!runtimeListenersBound) return;
  globalThis.removeEventListener('blur', handleWindowBlur);
  globalThis.document.removeEventListener('visibilitychange', handleVisibilityChange);
  globalThis.document.removeEventListener('copy', blockClipboard);
  globalThis.document.removeEventListener('cut', blockClipboard);
  globalThis.document.removeEventListener('paste', blockClipboard);
  globalThis.document.removeEventListener('contextmenu', blockClipboard);
  runtimeListenersBound = false;
}

/** 加载课程上下文内的作答，不读取独立考试接口。 */
async function loadAttempt(): Promise<void> {
  if (!enrollmentId || !itemId || !attemptId) {
    errorMessage.value = '课程作答参数无效。';
    loading.value = false;
    return;
  }
  try {
    const [loadedAttempt, courseDetail] = await Promise.all([
      getCourseAssessmentAttempt(enrollmentId, itemId, attemptId),
      // 防作弊开关只存在于课程快照，随作答一起读取，失败时按未开启处理。
      getLearnerCourseDetail(enrollmentId).catch(() => null)
    ]);
    attempt.value = loadedAttempt;
    attempt.value.questions.forEach(ensureTextAnswer);
    savedAnswerSnapshot.value = answerSnapshot();
    syncAnticheatListeners(courseDetail?.course?.isStudyAnticheatEnabled === 1);
    // 学习水印与防作弊同源，都取课程冻结快照；读取失败时按未开启处理。
    watermarkEnabled.value = courseDetail?.course?.isWatermarkEnabled === 1;
  } catch (error) {
    errorMessage.value = error instanceof Error ? error.message : '课程作答加载失败。';
  } finally {
    loading.value = false;
  }
}

/** 暂存课程题库练习或课程测验答案。 */
async function save(): Promise<boolean> {
  if (!editable.value) return true;
  if (saving.value) return false;
  saving.value = true;
  try {
    await saveCourseAssessmentAnswers(enrollmentId, itemId, attemptId, answerPayload());
    savedAnswerSnapshot.value = answerSnapshot();
    ElMessage.success('答案已暂存');
    return true;
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '答案暂存失败。');
    return false;
  } finally {
    saving.value = false;
  }
}

/** 提交或返回前确保本次编辑已经写入服务端，防止本地答案丢失。 */
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

/** 手动确认后交卷；课程作答页不设置独立考试的本地倒计时自动交卷。 */
async function submit(): Promise<void> {
  if (!editable.value || submitting.value) return;
  if (!(await ensureAnswersSaved('提交'))) return;
  submitting.value = true;
  try {
    await submitCourseAssessmentAttempt(enrollmentId, itemId, attemptId, { submitReason: 'MANUAL' });
    // 交卷后立即停止采集：跳转结果页会触发失焦，不应记成学员切屏。
    anticheatEnabled.value = false;
    releaseAnticheatListeners();
    await router.replace({ name: 'PortalCourseAssessmentResult', params: { enrollmentId, itemId, attemptId }, query: route.query });
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '课程考核提交失败。');
  } finally {
    submitting.value = false;
  }
}

/** 返回课程前同样保护未暂存的本地作答。 */
async function backToCourse(): Promise<void> {
  if (await ensureAnswersSaved('返回')) await router.back();
}

/** 交卷前明确提示不可再编辑。 */
async function confirmSubmit(): Promise<void> {
  try {
    await ElMessageBox.confirm('交卷后将无法继续编辑答案，确认提交吗？', '确认交卷', { type: 'warning' });
    await submit();
  } catch {
    // 用户取消确认时保持当前作答页。
  }
}

onMounted(loadAttempt);
onBeforeUnmount(releaseAnticheatListeners);
</script>

<template>
  <section class="attempt-page" :style="watermarkEnabled ? watermarkStyle : undefined">
    <header class="attempt-page__header">
      <div>
        <p>MY COURSE</p>
        <strong>{{ courseItemTitle }}</strong>
        <span>{{ assessmentTitle }}</span>
      </div>
      <div>
        <ElButton type="primary" :loading="saving" :disabled="!editable" @click="save">暂存答案</ElButton>
        <ElButton type="danger" :loading="submitting" :disabled="!editable" @click="confirmSubmit">提交</ElButton>
        <ElButton @click="backToCourse">返回</ElButton>
      </div>
    </header>
    <ElAlert
      v-if="anticheatEnabled"
      class="anticheat-notice"
      title="本考核已开启学习防作弊：离开答题页面、复制粘贴与右键菜单都会被记录。"
      type="warning"
      :closable="false"
      show-icon
    />
    <ElAlert v-if="errorMessage" :title="errorMessage" type="error" :closable="false" show-icon />
    <ElSkeleton v-else-if="loading" :rows="8" animated />
    <ElEmpty v-else-if="!attempt?.questions.length" description="当前课程考核暂无题目" />
    <main v-else-if="question && attempt" class="attempt-page__body">
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
      <article :class="{ 'no-copy': anticheatEnabled }">
        <h3>{{ currentIndex + 1 }}. {{ getQuestionTypeText(question) }}（{{ question.score }}分）</h3>
        <p>{{ question.stemSnapshot }}</p>
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
        <ElInput
          v-else
          v-model="question.answer!.answerText"
          :type="question.questionType === 'ESSAY' ? 'textarea' : 'text'"
          :autosize="{ minRows: 4 }"
          :disabled="!editable"
          placeholder="请输入答案"
          @input="question.answer!.isAnswered = question.answer!.answerText ? 1 : 0"
        />
        <footer>
          <ElButton :disabled="currentIndex === 0" @click="currentIndex--">上一题</ElButton>
          <ElButton :disabled="currentIndex === attempt.questions.length - 1" @click="currentIndex++">下一题</ElButton>
        </footer>
      </article>
    </main>
  </section>
</template>

<style scoped lang="less">
.attempt-page {
  height: 100%;
  background-image: var(--course-watermark-image, none);
  background-repeat: repeat;
  background-size: var(--course-watermark-size, auto);
  background-color: #f5f7fb;
}
.attempt-page__header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 16px 28px;
  background: #fff;
  box-shadow: 0 1px 5px #dbe2ee;
}
.attempt-page__header p {
  margin: 0 0 4px;
  color: #2d6cdf;
  font-size: 11px;
  font-weight: 700;
  letter-spacing: 0.12em;
}
.attempt-page__header strong,
.attempt-page__header span {
  display: block;
}
.attempt-page__header span {
  margin-top: 4px;
  color: #64748b;
  font-size: 13px;
}
.attempt-page__body {
  display: grid;
  grid-template-columns: 190px 1fr;
  gap: 20px;
  width: 1200px;
  margin: 24px auto;
}
.attempt-page__body aside,
.attempt-page__body article {
  padding: 24px;
  border-radius: 12px;
  background: #fff;
}
.attempt-page__body aside button {
  width: 36px;
  height: 36px;
  margin: 5px;
  border: 1px solid #d0d7e2;
  border-radius: 6px;
  background: #fff;
}
.attempt-page__body aside button.active {
  border-color: #2563eb;
}
.attempt-page__body aside button.answered {
  background: #16a34a;
  color: #fff;
}
.el-radio-group {
  display: flex;
  flex-direction: column;
  align-items: flex-start;
}
/* 开启防作弊后题干与选项不可选中复制，输入框仍保留正常选择与输入能力。 */
.no-copy {
  user-select: none;
}
.no-copy :deep(textarea),
.no-copy :deep(input) {
  user-select: text;
}
.anticheat-notice {
  width: 98%;
  margin: 16px 28px 0;
}
.el-radio,
.el-checkbox {
  display: flex;
}
footer {
  display: flex;
  gap: 12px;
  margin-top: 32px;
}
</style>
