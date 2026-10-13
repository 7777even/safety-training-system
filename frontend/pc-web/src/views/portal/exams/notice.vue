<script setup lang="ts">
import { computed, onMounted, ref } from 'vue';
import { isNavigationFailure, useRoute, useRouter } from 'vue-router';
import { Calendar, Checked, Clock, Document, Medal, Reading } from '@element-plus/icons-vue';
import type { ExamItem } from '@/api/exam';
import { startExamAttempt, getLearnerExamNotice } from '@/api/learner-exam';
import { startCourseExamAttempt } from '@/api/learner-course';
import { enterTrainingContent, getTrainingLearnerExamNotice } from '@/api/training-learner';
import { getExamTypeLabel } from '@/constants/exam-options';
import { useUserStore } from '@/stores/modules/user';
import { formatDuration } from '@/utils/duration';

const route = useRoute();
const router = useRouter();
const userStore = useUserStore();
/** 须知页只消费考试基本信息；培训快照允许部分规则字段为空。 */
interface ExamNotice {
  examId: number;
  examName: string;
  examType: string;
  openStartTime?: string | null;
  openEndTime?: string | null;
  durationSeconds?: number | null;
  passRatio?: number | null;
  passScore?: number | null;
  isNoticeEnabled?: ExamItem['isNoticeEnabled'] | 0 | 1 | null;
  noticeContent?: string | null;
}
const exam = ref<ExamNotice | null>(null);
const loading = ref(false);
const starting = ref(false);
const errorMessage = ref('');
const BROWSER_SUMMARY_MAX_LENGTH = 64;
const examId = Number(route.params.examId);
const examCandidateId = Number(route.params.examCandidateId);
const courseEnrollmentId = computed(() => Number(route.query.enrollmentId));
const courseItemId = computed(() => Number(route.query.courseItemId));
const trainingParticipantId = computed(() => Number(route.params.trainingParticipantId));
const trainingPlanContentId = computed(() => Number(route.params.planContentId));
const isTrainingNotice = computed(() => route.name === 'PortalTrainingExamNotice');
const sourceType = computed(() => isTrainingNotice.value ? 'training' : String(route.query.source ?? 'standalone'));
/** 线下考试没有在线作答环节，成绩由管理端录入；须知页不得给出开始答题入口。 */
const isOfflineExam = computed(() => exam.value?.examType === 'OFFLINE');

/** 独立考试幂等令牌只在正式点击开始答题时创建，不在须知页加载阶段占用作答状态。 */
function createLearnerAttemptToken(examId: number, userId: number): string {
  const storageKey = `learner-exam-attempt-${examId}-${userId}`;
  const existingToken = globalThis.sessionStorage.getItem(storageKey);
  if (existingToken) return existingToken;
  const token = globalThis.crypto?.randomUUID?.() ?? `pc-${examId}-${userId}-${Date.now()}`;
  globalThis.sessionStorage.setItem(storageKey, token);
  return token;
}

/** 将浏览器 User-Agent 归一为数据库可存储摘要，不上传完整原始指纹。 */
function getBrowserSummary(): string {
  const userAgent = globalThis.navigator.userAgent;
  const browserSignatures: Array<[RegExp, string]> = [
    [/Edg\/([\d.]+)/, 'Edge'],
    [/OPR\/([\d.]+)/, 'Opera'],
    [/Firefox\/([\d.]+)/, 'Firefox'],
    [/Chrome\/([\d.]+)/, 'Chrome'],
    [/Version\/([\d.]+).*Safari\//, 'Safari']
  ];
  const matchedBrowser = browserSignatures.find(([pattern]) => pattern.test(userAgent));
  if (!matchedBrowser) return '未知浏览器';
  const version = userAgent.match(matchedBrowser[0])?.[1];
  const browserSummary = version ? `${matchedBrowser[1]} ${version}` : matchedBrowser[1];
  return browserSummary.slice(0, BROWSER_SUMMARY_MAX_LENGTH);
}

function formatDateTime(value: string | null | undefined): string {
  return value ? value.replace('T', ' ').slice(0, 16) : '未设置';
}

// function getResponsibleText(): string {
//   return exam.value?.responsibleUserId ? `用户 #${exam.value.responsibleUserId}` : '未设置';
// }

function getOpenTimeText(): string {
  const startTime = formatDateTime(exam.value?.openStartTime);
  const endTime = formatDateTime(exam.value?.openEndTime);
  return startTime === '未设置' && endTime === '未设置' ? '未设置' : `${startTime} / ${endTime}`;
}

/** ExamVO 当前未直接返回总分，按及格分和及格比例反推，缺字段时展示空态。 */
function getTotalScoreText(): string {
  if (!exam.value?.passScore || !exam.value.passRatio) return '—';
  return `${Math.round((exam.value.passScore / exam.value.passRatio) * 100) / 100} 分`;
}

async function loadExam(): Promise<void> {
  loading.value = true;
  errorMessage.value = '';
  try {
    exam.value = isTrainingNotice.value
      ? await getTrainingLearnerExamNotice(trainingParticipantId.value, trainingPlanContentId.value)
      : await getLearnerExamNotice(examId);
  } catch (error) {
    errorMessage.value = error instanceof Error ? error.message : '考试信息加载失败，请稍后重试。';
  } finally {
    loading.value = false;
  }
}

/** 跳转作答页后检查路由结果，避免作答已创建但页面仍停留在须知页。 */
async function goAttempt(attemptExamId: number, examAttemptId: number): Promise<void> {
  const navigationFailure = await router.replace({
    name: sourceType.value === 'training' ? 'PortalTrainingExamAttempt' : 'PortalExamAttempt',
    params: sourceType.value === 'training' ? { examAttemptId } : { examId: attemptExamId, examAttemptId }
  });
  const expectedRouteName = sourceType.value === 'training' ? 'PortalTrainingExamAttempt' : 'PortalExamAttempt';
  if (isNavigationFailure(navigationFailure) || router.currentRoute.value.name !== expectedRouteName) {
    throw new Error('作答已创建，但无法进入考试详情，请刷新页面后继续作答。');
  }
}

/** 点击开始答题才正式调用开考接口，考试状态从未开始进入作答中。 */
async function startAnswering(): Promise<void> {
  if (!exam.value || starting.value) return;
  // 双保险：按钮已对线下考试隐藏，直达链接也不得绕过。
  if (isOfflineExam.value) {
    errorMessage.value = '线下考试不支持在线作答，成绩由考试管理方录入。';
    return;
  }
  starting.value = true;
  try {
    if (sourceType.value === 'course') {
      if (!courseEnrollmentId.value || !courseItemId.value) throw new Error('课程考试上下文缺失，请回到课程学习页重新进入。');
      const attempt = await startCourseExamAttempt(courseEnrollmentId.value, courseItemId.value, {
        clientAttemptToken: `pc-course-${courseEnrollmentId.value}-${courseItemId.value}-${Date.now()}`,
        platform: 'PC'
      });
      await goAttempt(0, attempt.examAttemptId);
      return;
    }

    if (isTrainingNotice.value) {
      if (!trainingParticipantId.value || !trainingPlanContentId.value) throw new Error('培训考试上下文缺失，请回到培训详情重新进入。');
      const attempt = await enterTrainingContent(trainingParticipantId.value, trainingPlanContentId.value, {
        clientAttemptToken: `pc-training-${trainingParticipantId.value}-${trainingPlanContentId.value}-${Date.now()}`,
        platform: 'PC',
        operatingSystem: globalThis.navigator.platform,
        browser: getBrowserSummary()
      });
      if (!attempt.examAttemptId) throw new Error('考试已开始，但未返回作答信息，请刷新培训详情后继续。');
      await goAttempt(0, attempt.examAttemptId);
      return;
    }

    const userId = userStore.userInfo?.userId;
    if (!userId || !examCandidateId) throw new Error('未读取到当前考生信息，暂不能开始考试。');
    const storageKey = `learner-exam-attempt-${exam.value.examId}-${userId}`;
    const attempt = await startExamAttempt({
      examId: exam.value.examId,
      userId,
      clientAttemptToken: createLearnerAttemptToken(exam.value.examId, userId),
      attemptType: 'FIRST',
      platform: 'PC',
      operatingSystem: globalThis.navigator.platform,
      browser: getBrowserSummary()
    });
    await goAttempt(attempt.examId, attempt.examAttemptId);
    globalThis.sessionStorage.removeItem(storageKey);
  } finally {
    starting.value = false;
  }
}

onMounted(loadExam);
</script>

<template>
  <section class="exam-notice-page">
    <header class="exam-notice-top">
      <div>
        <p class="page-heading__eyebrow">MY EXAMS</p>
        <strong>考生须知</strong>
      </div>
      <ElButton @click="router.back()">返回</ElButton>
    </header>
    <ElAlert v-if="errorMessage" :title="errorMessage" type="error" show-icon :closable="false" />
    <main v-loading="loading" class="exam-notice">
      <template v-if="exam">
        <p class="exam-notice__label">考试名称</p>
        <h1>{{ exam.examName }}</h1>
        <section class="exam-notice__summary" aria-label="考试概要">
          <div class="exam-notice__column">
            <div class="summary-item">
              <ElIcon><Medal /></ElIcon>
              <span>总分：</span>
              <strong>{{ getTotalScoreText() }}</strong>
            </div>
            <div v-if="exam.examType !== 'BANK_PRACTICE'" class="summary-item">
              <ElIcon><Checked /></ElIcon>
              <span>及格分数：</span>
              <strong>{{ exam.passScore ?? '—' }} 分</strong>
            </div>
            <div class="summary-item">
              <ElIcon><Document /></ElIcon>
              <span>考试类型：</span>
              <strong>{{ getExamTypeLabel(exam.examType) }}</strong>
            </div>
          </div>
          <div class="exam-notice__column">
            <div class="summary-item">
              <ElIcon><Clock /></ElIcon>
              <span>考试时长：</span>
              <strong>{{ formatDuration(exam.durationSeconds) }}</strong>
            </div>
            <!-- <div class="summary-item">
              <ElIcon><User /></ElIcon>
              <span>负责人：</span>
              <strong>{{ getResponsibleText() }}</strong>
            </div> -->
            <div class="summary-item">
              <ElIcon><Calendar /></ElIcon>
              <span>开放时间：</span>
              <strong>{{ getOpenTimeText() }}</strong>
            </div>
            <div class="summary-item">
              <ElIcon><Reading /></ElIcon>
              <span>考试须知：</span>
              <strong>{{ exam.isNoticeEnabled === 1 ? '已启用' : '通用规则' }}</strong>
            </div>
          </div>
        </section>
        <section class="exam-notice__rules">
          <h2>考试须知</h2>
          <p>{{ exam.noticeContent || (isOfflineExam ? '本场为线下考试，请在考试管理方安排的时间与场地参加。' : '请在规定时间内独立完成考试，交卷后将无法继续修改答案。') }}</p>
          <template v-if="!isOfflineExam">
            <h2>通用考试规则</h2>
            <ol>
              <li>考试过程中请保持诚信作答，禁止替考、抄袭、传播试题或使用未经允许的辅助工具。</li>
              <li>请认真阅读题目并按要求作答，选择不确定的试题可先暂存答案，交卷前再次核对。</li>
              <li>进入答题后系统将记录页面切离等必要反作弊事件，请勿频繁切换窗口或离开考试页面。</li>
              <li>倒计时结束后系统将自动交卷，请合理安排答题时间。</li>
            </ol>
          </template>
        </section>
        <ElAlert
          v-if="isOfflineExam"
          class="exam-notice__offline"
          type="info"
          show-icon
          :closable="false"
          title="线下考试无需在线作答；成绩由考试管理方录入，录入后可查看结果。"
        />
        <ElButton v-else class="exam-notice__start" type="primary" size="large" :loading="starting" @click="startAnswering">开始答题</ElButton>
      </template>
    </main>
  </section>
</template>

<style scoped lang="less">
.exam-notice-page {
  min-height: 83vh;
  background: #f5f7fb;
}

.exam-notice-top {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin: 16px;
  padding: 18px 28px;
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

.exam-notice {
  max-width: 980px;
  min-height: 640px;
  margin: 0 auto;
  padding: 40px 24px 48px;
  text-align: center;
  background: #fff;
}

.exam-notice h1 {
  max-width: 760px;
  margin: 4px auto 34px;
  color: #1f2937;
  font-size: 24px;
  font-weight: 600;
  line-height: 1.45;
}

.exam-notice__label {
  margin: 0;
  color: #9ca3af;
  font-size: 13px;
}

.exam-notice__summary {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  max-width: 760px;
  margin: 0 auto 24px;
  background: #fafafa;
}

.exam-notice__column {
  display: grid;
  gap: 28px;
  padding: 34px 52px;
  text-align: left;
}

.exam-notice__column + .exam-notice__column {
  border-left: 1px solid #edf0f5;
}

.summary-item {
  display: grid;
  grid-template-columns: 24px max-content minmax(0, 1fr);
  gap: 10px;
  align-items: center;
  color: #9ca3af;
  font-size: 16px;
}

.summary-item .el-icon {
  color: #2d8cff;
  font-size: 18px;
}

.summary-item strong {
  min-width: 0;
  color: #111827;
  font-weight: 500;
  word-break: break-word;
}

.exam-notice__rules {
  max-width: 760px;
  margin: 0 auto;
  color: #6b7280;
  text-align: left;
}

.exam-notice__rules h2 {
  margin: 18px 0 10px;
  color: #374151;
  font-size: 14px;
  font-weight: 700;
}

.exam-notice__rules p,
.exam-notice__rules ol {
  margin: 0;
  line-height: 1.8;
}

.exam-notice__rules ol {
  padding-left: 22px;
}

.exam-notice__start {
  min-width: 120px;
  margin-top: 28px;
}

/* 线下考试用提示替代开考按钮，位置与按钮一致以免页面重心跳动。 */
.exam-notice__offline {
  max-width: 560px;
  margin: 28px auto 0;
  text-align: left;
}

@media (max-width: 780px) {
  .exam-notice__summary {
    grid-template-columns: 1fr;
  }

  .exam-notice__column {
    padding: 28px;
  }

  .exam-notice__column + .exam-notice__column {
    border-top: 1px solid #edf0f5;
    border-left: 0;
  }
}
</style>
