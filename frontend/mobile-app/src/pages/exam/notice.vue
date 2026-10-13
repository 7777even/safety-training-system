<script setup lang="ts">
import type { LearnerExamNotice } from '@/api/learner-exam';
import { getExamTypeText } from '@shared/utils/exam';
import { onLoad } from '@dcloudio/uni-app';
import { computed, ref } from 'vue';
import { getLearnerExamNotice, startExamAttempt } from '@/api/learner-exam';
import { enterTrainingContent, getTrainingLearnerExamNotice, type TrainingLearnerExamNotice } from '@/api/training-learner';
import MobileBaseLayout from '@/layouts/MobileBaseLayout.vue';
import { useUserStore } from '@/stores/modules/user';

const userStore = useUserStore();
const exam = ref<LearnerExamNotice | TrainingLearnerExamNotice | null>(null);
const errorMessage = ref('');
const loading = ref(false);
const starting = ref(false);
let examId = 0;
let trainingParticipantId = 0;
let planContentId = 0;
const isTrainingExam = computed(() => Boolean(trainingParticipantId && planContentId));

/** 将 Contract 时间转为移动端紧凑展示格式。 */
function formatDateTime(value: string | null | undefined): string {
  return value ? value.replace('T', ' ').slice(0, 16) : '未设置';
}

/** 按 Contract 的小数及格比例反推试卷总分。 */
const totalScoreText = computed(() => {
  if (!exam.value?.passScore || !exam.value.passRatio) return '未设置';
  return `${Math.round((exam.value.passScore / exam.value.passRatio) * 100) / 100} 分`;
});
const openTimeText = computed(() => {
  if (!exam.value) return '未设置';
  return `${formatDateTime(exam.value.openStartTime)} 至 ${formatDateTime(exam.value.openEndTime)}`;
});
const durationText = computed(() => {
  const durationSeconds = exam.value?.durationSeconds;
  return durationSeconds ? `${Math.ceil(durationSeconds / 60)} 分钟` : '未设置';
});
const noticeText = computed(() =>
  exam.value?.isNoticeEnabled && exam.value.noticeContent ? exam.value.noticeContent : '请在规定时间内独立完成考试，交卷后将无法继续修改答案。'
);
/** 线下考试没有在线作答环节，成绩由管理端录入；须知页不得给出开始答题入口。 */
const isOfflineExam = computed(() => exam.value?.examType === 'OFFLINE');

/** 须知页只读取考试事实，不在加载或返回时创建作答。 */
async function loadExamNotice(): Promise<void> {
  loading.value = true;
  errorMessage.value = '';
  try {
    exam.value = isTrainingExam.value
      ? await getTrainingLearnerExamNotice(trainingParticipantId, planContentId)
      : await getLearnerExamNotice(examId);
  } catch (error) {
    errorMessage.value = error instanceof Error ? error.message : '考试信息加载失败，请稍后重试。';
  } finally {
    loading.value = false;
  }
}

/** 点击后才创建作答，并替换须知页，避免返回后重复开考。 */
async function startAnswering(): Promise<void> {
  const userId = userStore.userInfo?.userId;
  if (!exam.value || starting.value) return;
  // 双保险：按钮已对线下考试隐藏，直达链接也不得绕过。
  if (isOfflineExam.value) {
    uni.showToast({ title: '线下考试不支持在线作答，成绩由考试管理方录入', icon: 'none' });
    return;
  }
  if (!isTrainingExam.value && !userId) {
    uni.showToast({ title: '未读取到登录用户', icon: 'none' });
    return;
  }
  starting.value = true;
  try {
    if (isTrainingExam.value) {
      const entered = await enterTrainingContent(trainingParticipantId, planContentId, {
        clientAttemptToken: `mobile-training-notice-${trainingParticipantId}-${planContentId}-${Date.now()}`,
        platform: 'MOBILE'
      });
      if (!entered.examAttemptId) throw new Error('后端未返回考试作答。');
      uni.redirectTo({ url: `/pages/exam/attempt?examAttemptId=${entered.examAttemptId}` });
    } else {
      const attempt = await startExamAttempt({
        examId: exam.value.examId,
        userId: userId!,
        clientAttemptToken: `h5-notice-${exam.value.examId}-${userId}-${Date.now()}`,
        attemptType: 'FIRST',
        platform: 'H5'
      });
      uni.redirectTo({ url: `/pages/exam/attempt?examId=${attempt.examId}&examAttemptId=${attempt.examAttemptId}` });
    }
  } catch (error) {
    uni.showToast({ title: error instanceof Error ? error.message : '开始考试失败', icon: 'none' });
  } finally {
    starting.value = false;
  }
}

onLoad((query) => {
  trainingParticipantId = Number(query?.trainingParticipantId);
  planContentId = Number(query?.planContentId);
  examId = Number(query?.examId);
  if (!(trainingParticipantId && planContentId) && !examId) {
    errorMessage.value = '考试参数无效。';
    return;
  }
  void loadExamNotice();
});
</script>

<template>
  <MobileBaseLayout>
    <view class="exam-notice-page">
      <view v-if="loading" class="state-card"><text>正在加载考试须知…</text></view>
      <view v-else-if="errorMessage" class="state-card">
        <text>{{ errorMessage }}</text>
        <button @click="loadExamNotice">重新加载</button>
      </view>
      <template v-else-if="exam">
        <view class="notice-hero">
          <text class="notice-hero__eyebrow">EXAM NOTICE</text>
          <text class="notice-hero__title">考生须知</text>
          <text class="notice-hero__name">{{ exam.examName }}</text>
        </view>

        <view class="summary-card">
          <text class="section-title">考试概要</text>
          <view class="summary-grid">
            <view>
              <text>考试类型</text>
              <strong>{{ getExamTypeText(exam.examType) }}</strong>
            </view>
            <view>
              <text>考试时长</text>
              <strong>{{ durationText }}</strong>
            </view>
            <view>
              <text>考试总分</text>
              <strong>{{ totalScoreText }}</strong>
            </view>
            <view>
              <text>及格分数</text>
              <strong>{{ exam.passScore || '未设置' }}{{ exam.passScore ? ' 分' : '' }}</strong>
            </view>
          </view>
          <view class="summary-time">
            <text>开放时间</text>
            <text>{{ openTimeText }}</text>
          </view>
        </view>

        <view class="rules-card">
          <text class="section-title">考试须知</text>
          <text class="rules-card__content">{{ noticeText }}</text>
          <template v-if="!isOfflineExam">
            <view class="rules-card__divider" />
            <text class="section-title">作答规则</text>
            <view class="rules-list">
              <text>1. 请在规定的开放时间内完成考试。</text>
              <text>2. 考试开始后将进入倒计时，请合理安排答题时间。</text>
              <text>3. 请独立作答；交卷后不能继续修改答案。</text>
            </view>
          </template>
        </view>

        <view v-if="isOfflineExam" class="offline-tip">
          <text class="offline-tip__title">线下考试无需在线作答</text>
          <text class="offline-tip__text">成绩由考试管理方录入，录入后可在「考试与随堂测试」中查看结果。</text>
        </view>
        <template v-else>
          <button class="start-button" :loading="starting" @click="startAnswering">开始答题</button>
          <text class="start-tip">点击后即正式开考，请确认已做好准备。</text>
        </template>
      </template>
    </view>
  </MobileBaseLayout>
</template>

<style scoped lang="less">
.exam-notice-page {
  padding: 32rpx 0 56rpx;
}
.notice-hero,
.summary-card,
.rules-card,
.state-card {
  border-radius: 20rpx;
  background: #fff;
}
.notice-hero {
  display: flex;
  padding: 38rpx 32rpx;
  flex-direction: column;
  background: linear-gradient(135deg, #2563eb, #1d4ed8);
  color: #fff;
}
.notice-hero__eyebrow {
  font-size: 22rpx;
  font-weight: 700;
  letter-spacing: 3rpx;
  opacity: 0.78;
}
.notice-hero__title {
  margin-top: 14rpx;
  font-size: 42rpx;
  font-weight: 800;
}
.notice-hero__name {
  margin-top: 18rpx;
  font-size: 27rpx;
  line-height: 1.55;
  opacity: 0.92;
}
.summary-card,
.rules-card,
.state-card {
  margin-top: 20rpx;
  padding: 30rpx;
}
.section-title {
  display: block;
  color: #172033;
  font-size: 30rpx;
  font-weight: 700;
}
.summary-grid {
  display: grid;
  margin-top: 24rpx;
  grid-template-columns: 1fr 1fr;
  gap: 22rpx 16rpx;
}
.summary-grid view {
  display: flex;
  min-width: 0;
  flex-direction: column;
  gap: 8rpx;
}
.summary-grid text,
.summary-time text:first-child {
  color: #64748b;
  font-size: 23rpx;
}
.summary-grid strong {
  color: #172033;
  font-size: 27rpx;
  line-height: 1.4;
}
.summary-time {
  display: flex;
  margin-top: 26rpx;
  padding-top: 22rpx;
  flex-direction: column;
  gap: 10rpx;
  border-top: 1px solid #e2e8f0;
}
.summary-time text:last-child {
  color: #334155;
  font-size: 25rpx;
  line-height: 1.5;
}
.rules-card {
  display: flex;
  flex-direction: column;
  gap: 16rpx;
}
.rules-card__content,
.rules-list text {
  color: #475569;
  font-size: 26rpx;
  line-height: 1.7;
  white-space: pre-wrap;
}
.rules-card__divider {
  height: 1px;
  margin: 6rpx 0;
  background: #e2e8f0;
}
.rules-list {
  display: flex;
  flex-direction: column;
  gap: 10rpx;
}
.start-button {
  display: block;
  width: 100%;
  margin-top: 28rpx;
  border: 0;
  border-radius: 14rpx;
  background: #2563eb;
  color: #fff;
  font-size: 29rpx;
  font-weight: 700;
  line-height: 84rpx;
}
.start-tip {
  display: block;
  margin-top: 16rpx;
  color: #64748b;
  font-size: 23rpx;
  text-align: center;
}
/* 线下考试用提示替代开考按钮，占用同一位置以免页面重心跳动。 */
.offline-tip {
  display: flex;
  margin-top: 28rpx;
  padding: 28rpx 32rpx;
  flex-direction: column;
  gap: 10rpx;
  border-radius: 20rpx;
  background: #eff6ff;
}
.offline-tip__title {
  color: #1d4ed8;
  font-size: 27rpx;
  font-weight: 700;
}
.offline-tip__text {
  color: #475569;
  font-size: 24rpx;
  line-height: 1.6;
}
.state-card {
  display: flex;
  color: #475569;
  font-size: 27rpx;
  line-height: 1.6;
  flex-direction: column;
  gap: 20rpx;
}
.state-card button {
  width: 180rpx;
  margin: 0;
  border: 1px solid #bfdbfe;
  border-radius: 10rpx;
  background: #eff6ff;
  color: #2563eb;
  font-size: 24rpx;
  line-height: 60rpx;
}
</style>
