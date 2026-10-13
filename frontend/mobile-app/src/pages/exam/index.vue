<script setup lang="ts">
import type { LearnerExamTask, LearnerExamTaskStatus } from '@shared/types';
import { getExamTypeText } from '@shared/utils/exam';
import { onShow } from '@dcloudio/uni-app';
import { onBeforeUnmount, ref } from 'vue';
import { getLearnerExamTaskPage } from '@/api/learner-exam';
import MobileBaseLayout from '@/layouts/MobileBaseLayout.vue';
import { loadProtectedImageUrl, revokeProtectedImageUrl } from '@/libs/protected-image';
import UniIcons from '@dcloudio/uni-ui/lib/uni-icons/uni-icons.vue';
const tasks = ref<LearnerExamTask[]>([]);
const errorMessage = ref('');
const examCoverUrls = ref<Record<number, string | null>>({});
/** 当前选中的任务状态；未选择时展示全部考试任务。 */
const activeTaskStatus = ref<LearnerExamTaskStatus | undefined>(undefined);
let examLoadVersion = 0;

/** 任务状态筛选项，与学员考试任务 Contract 枚举保持一致。 */
const taskStatusTabs: Array<{ label: string; value: LearnerExamTaskStatus | undefined }> = [
  { label: '全部', value: undefined },
  { label: '未开始', value: 'NOT_STARTED' },
  { label: '作答中', value: 'IN_PROGRESS' },
  { label: '等待成绩', value: 'WAITING_RESULT' },
  { label: '成绩已发布', value: 'RESULT_PUBLISHED' }
];

/** 加载当前登录学员的考试任务，状态筛选与 PC 考试中心使用同一查询参数。 */
async function loadTasks(): Promise<void> {
  const loadVersion = ++examLoadVersion;
  try {
    const page = await getLearnerExamTaskPage({
      pageNum: 1,
      pageSize: 20,
      ...(activeTaskStatus.value ? { 'query.taskStatus': activeTaskStatus.value } : {})
    });
    if (loadVersion !== examLoadVersion) return;
    Object.values(examCoverUrls.value).forEach(revokeProtectedImageUrl);
    examCoverUrls.value = {};
    tasks.value = page.list;
    await loadExamCovers(tasks.value, loadVersion);
  } catch (error) {
    errorMessage.value = error instanceof Error ? error.message : '考试任务加载失败。';
  }
}

/** 加载学员可见的考试封面；没有封面时使用文字占位，任务操作保持可用。 */
async function loadExamCovers(items: LearnerExamTask[], loadVersion: number): Promise<void> {
  const results = await Promise.all(
    items.map(async (task) => {
      if (!task.coverUrl) return [task.examId, null] as const;
      try {
        return [task.examId, await loadProtectedImageUrl(task.coverUrl)] as const;
      } catch {
        return [task.examId, null] as const;
      }
    })
  );
  if (loadVersion === examLoadVersion) examCoverUrls.value = Object.fromEntries(results);
}

/** 切换任务状态筛选后重新查询，避免沿用上一状态的列表。 */
async function changeTaskStatus(status: LearnerExamTaskStatus | undefined): Promise<void> {
  if (activeTaskStatus.value === status) return;
  activeTaskStatus.value = status;
  await loadTasks();
}

/** 根据服务端建议操作进入开始、续答、结果或等待状态。 */
async function handleTask(task: LearnerExamTask): Promise<void> {
  // 线下考试不允许在移动端创建线上作答；即使旧数据误带建议动作也必须保持与 PC 一致的等待结果语义。
  if (isOfflineExam(task)) {
    uni.showToast({ title: '线下成绩由考试管理方录入，录入后可查看结果', icon: 'none' });
    return;
  }
  if (!task.availableAction) return;
  if (task.currentAttemptId && task.availableAction === 'CONTINUE') {
    uni.navigateTo({ url: `/pages/exam/attempt?examId=${task.examId}&examAttemptId=${task.currentAttemptId}` });
    return;
  }
  // 与 PC 保持一致：已交卷但等待正式成绩时，也允许学员回看自己的答题内容。
  if (task.currentAttemptId && (task.availableAction === 'VIEW_RESULT' || task.availableAction === 'WAIT_REVIEW')) {
    uni.navigateTo({ url: `/pages/exam/result?examAttemptId=${task.currentAttemptId}` });
    return;
  }
  if (task.availableAction === 'START') uni.navigateTo({ url: `/pages/exam/notice?examId=${task.examId}` });
}

/** Contract 建议动作的中文文案；线下待录分时没有建议动作，仅展示等待提示。 */
const actionText = (task: LearnerExamTask): string => {
  if (!task.availableAction) return '待出成绩';
  if (task.availableAction === 'VIEW_RESULT' && task.scorePublishStatus === 'PUBLISHED' && !task.scoreVisible)
    return '查看答题情况';
  return {
    START: '开始考试',
    CONTINUE: '继续作答',
    WAIT_REVIEW: '查看答题情况',
    VIEW_RESULT: '查看结果'
  }[task.availableAction];
};

/** 线下考试由管理端录分，学员端只可在成绩生成后查看结果。 */
const isOfflineExam = (task: LearnerExamTask): boolean => task.examType === 'OFFLINE';

/** 独立题库练习交卷即产生可回看的学习结果；兼容服务端尚未将旧记录收敛为 RESULT_PUBLISHED 的返回。 */
const hasBankPracticeResult = (task: LearnerExamTask): boolean =>
  task.examType === 'BANK_PRACTICE' && task.currentAttemptId !== null && ['WAITING_RESULT', 'RESULT_PUBLISHED'].includes(task.taskStatus);

/** 题库练习的有效结果与正式成绩均可在任务卡中展示；不以真值判断，确保 0 分正常显示。 */
const isResultVisible = (task: LearnerExamTask): boolean => task.scoreVisible || hasBankPracticeResult(task);

/** 题库练习不产生正式成绩，卡片用练习得分与 PC 端保持同一业务口径。 */
const scoreLabel = (task: LearnerExamTask): string => (task.examType === 'BANK_PRACTICE' ? '练习得分' : '正式成绩');

/** 线下待录分卡片不能显示任何作答入口，避免误入考生须知。 */
const canHandleTask = (task: LearnerExamTask): boolean => !isOfflineExam(task) && Boolean(task.availableAction);

/** 卡片开放时间沿用 PC 的日期压缩口径：同年只留「月-日 时:分」，一行即可容纳完整区间。 */
const compactDateTime = (value: string | null): string => {
  if (!value) return '';
  const [date, time = ''] = value.replace('T', ' ').split(' ');
  const [year, month, day] = date.split('-');
  const moment = time.slice(0, 5);
  const dayPart = Number(year) === new Date().getFullYear() ? `${month}-${day}` : date;
  return moment ? `${dayPart} ${moment}` : dayPart;
};

/** 汇总考试开放区间；两端都未设置时说明不限时间，避免输出「未设置 至 未设置」。 */
const getOpenTimeText = (task: LearnerExamTask): string => {
  const start = compactDateTime(task.openStartTime);
  const end = compactDateTime(task.openEndTime);
  if (!start && !end) return '不限';
  if (!start) return `截至 ${end}`;
  if (!end) return `${start} 起`;
  return `${start} ~ ${end}`;
};

/** 题库练习可能先有可回看的结果、后回填分数；分数缺失时按待出成绩展示，避免出现「- 分」。 */
const hasVisibleScore = (task: LearnerExamTask): boolean => isResultVisible(task) && task.finalScore !== null;

/** 底部只补充状态标签未说明的结果信息；发布事实与学员可见性分别判断。 */
const scoreStatusText = (task: LearnerExamTask): string => {
  if (task.scorePublishStatus === 'PUBLISHED' && !task.scoreVisible) return '本次考试设置不展示成绩';
  if (isResultVisible(task) || task.taskStatus !== 'WAITING_RESULT') return '';
  return isOfflineExam(task) ? '等待管理端录入成绩' : '已交卷，等待成绩发布';
};

/** 任务状态标签文案；不直接回显 Contract 枚举码。 */
const taskStatusText = (task: LearnerExamTask): string =>
  ({ NOT_STARTED: '未开始', IN_PROGRESS: '作答中', WAITING_RESULT: '等待成绩', RESULT_PUBLISHED: '成绩已发布' })[task.taskStatus];

/** 任务状态标签配色：已发布用成功色、等待成绩用警示色、其余用主色。 */
const taskStatusTone = (task: LearnerExamTask): 'primary' | 'warning' | 'success' => {
  if (task.taskStatus === 'RESULT_PUBLISHED') return 'success';
  if (task.taskStatus === 'WAITING_RESULT') return 'warning';
  return 'primary';
};

/** 独立考试与专项练习的来源文案，与 PC 考试中心同一口径。 */
const getSourceText = (task: LearnerExamTask): string => (task.sourceType === 'STANDALONE_PRACTICE' ? '专项练习' : '独立考试');

/** 得分结论只保留三种语义强调色；未判定及格时使用中性色，避免误读为未通过。 */
const getOutcomeTone = (task: LearnerExamTask): 'pass' | 'fail' | 'none' => {
  if (task.isPassed === 1) return 'pass';
  if (task.isPassed === 0) return 'fail';
  return 'none';
};

/** 得分结论文案，未判定及格与未及格分开表达。 */
const getOutcomeText = (task: LearnerExamTask): string => {
  if (task.isPassed === 1) return '已及格';
  if (task.isPassed === 0) return '未及格';
  return '不判定及格';
};

onShow(() => void loadTasks());
onBeforeUnmount(() => Object.values(examCoverUrls.value).forEach(revokeProtectedImageUrl));
</script>
<template>
  <MobileBaseLayout>
    <view class="page">
      <view class="page-hero">
        <view class="page-hero__deco" />
        <text class="page-hero__eyebrow">MY EXAMS</text>
        <text class="page-hero__title">我的考试</text>
        <text class="page-hero__subtitle">按要求完成安全教育考试任务</text>
        <view class="hero-actions">
          <navigator class="hero-action" url="/pages/personal-exam/index">考试与随堂测试 ›</navigator>
        </view>
      </view>

      <view class="segmented-filter">
        <text
          v-for="tab in taskStatusTabs"
          :key="tab.value || 'ALL'"
          class="segmented-filter__item"
          :class="{ 'segmented-filter__item--active': activeTaskStatus === tab.value }"
          @click="changeTaskStatus(tab.value)"
        >
          {{ tab.label }}
        </text>
      </view>

      <view v-if="errorMessage" class="state-panel">
        <text>{{ errorMessage }}</text>
        <button class="state-panel__button" @click="loadTasks">重新加载</button>
      </view>
      <view v-else class="exam-list">
        <view v-for="task in tasks" :key="task.examCandidateId" class="exam-card">
          <view class="exam-card__top">
            <image
              v-if="examCoverUrls[task.examId]"
              class="exam-card__cover"
              :src="examCoverUrls[task.examId] || ''"
              mode="aspectFill"
            />
            <view v-else class="exam-card__cover exam-card__cover--placeholder">
              <UniIcons type="image" size="30" color="#2563eb" />
            </view>
            <view class="exam-card__identity">
              <text class="exam-name">{{ task.examName }}</text>
              <text class="exam-source">{{ getSourceText(task) }}</text>
              <view class="exam-card__chips">
                <text class="exam-type">{{ getExamTypeText(task.examType) }}</text>
                <text class="status-chip" :class="`status-chip--${taskStatusTone(task)}`">{{ taskStatusText(task) }}</text>
              </view>
            </view>
          </view>

          <view class="exam-card__facts">
            <text class="exam-card__fact">开放时间 {{ getOpenTimeText(task) }}</text>
            <text class="exam-card__fact">考试时长 {{ Math.ceil(task.durationSeconds / 60) }} 分钟</text>
            <text class="exam-card__fact">作答次数 {{ task.attemptCount }} 次</text>
          </view>

          <view v-if="hasVisibleScore(task)" class="exam-card__outcome" :class="`exam-card__outcome--${getOutcomeTone(task)}`">
            <text class="exam-card__outcome-label">{{ scoreLabel(task) }}</text>
            <text class="exam-card__outcome-value">{{ task.finalScore ?? '-' }} 分</text>
            <text class="exam-card__outcome-flag">{{ getOutcomeText(task) }}</text>
          </view>

          <view class="exam-card__actions">
            <text v-if="scoreStatusText(task)" class="exam-card__status-note">{{ scoreStatusText(task) }}</text>
            <button v-if="canHandleTask(task)" class="exam-card__button" @click="handleTask(task)">{{ actionText(task) }}</button>
          </view>
        </view>
        <view v-if="!tasks.length" class="state-panel">
          <text>{{ activeTaskStatus ? '该状态下暂无考试任务' : '暂无考试任务' }}</text>
        </view>
      </view>
    </view>
  </MobileBaseLayout>
</template>
<style scoped lang="less">
.page {
  padding: 24rpx 0 64rpx;
}

/* 页头 hero：与学习、培训两页共用同一条蓝色渐变横幅，保证三端视觉一致。 */
.page-hero {
  position: relative;
  display: flex;
  overflow: hidden;
  padding: 40rpx 36rpx;
  flex-direction: column;
  border-radius: 28rpx;
  background: linear-gradient(135deg, #155eef 0%, #4f8bff 100%);
  box-shadow: 0 16rpx 32rpx rgb(21 94 239 / 18%);
}

.page-hero__deco {
  position: absolute;
  top: -90rpx;
  right: -70rpx;
  width: 280rpx;
  height: 280rpx;
  border-radius: 50%;
  background: rgb(255 255 255 / 14%);
}

.page-hero__eyebrow,
.page-hero__title,
.page-hero__subtitle {
  position: relative;
  z-index: 1;
}

.page-hero__eyebrow {
  color: rgb(255 255 255 / 82%);
  font-size: 21rpx;
  font-weight: 700;
  letter-spacing: 3rpx;
}

.page-hero__title {
  margin-top: 14rpx;
  color: #fff;
  font-size: 48rpx;
  font-weight: 700;
}

.page-hero__subtitle {
  margin-top: 12rpx;
  color: rgb(255 255 255 / 78%);
  font-size: 25rpx;
  line-height: 1.5;
}

/* 状态筛选复用全局分段控件：与学习页保持同一条不换行的筛选栏。 */
.exam-list {
  display: flex;
  margin-top: 26rpx;
  flex-direction: column;
  gap: 22rpx;
}

.exam-card {
  display: flex;
  padding: 32rpx;
  flex-direction: column;
  border-radius: 24rpx;
  background: var(--color-bg-surface, #fff);
  box-shadow: 0 8rpx 24rpx rgb(38 39 45 / 6%);
}

.exam-card__top {
  display: flex;
  min-width: 0;
  align-items: flex-start;
  gap: 24rpx;
}

.exam-card__cover {
  width: 176rpx;
  height: 176rpx;
  flex: none;
  border-radius: 18rpx;
  background: var(--color-primary-subtle, #eff6ff);
}

.exam-card__cover--placeholder {
  display: flex;
  align-items: center;
  justify-content: center;
}

.exam-card__identity {
  display: flex;
  min-width: 0;
  flex: 1;
  flex-direction: column;
}

.exam-name,
.exam-source {
  overflow: hidden;
  text-overflow: ellipsis;
}

.exam-name {
  display: -webkit-box;
  overflow: hidden;
  -webkit-line-clamp: 2;
  -webkit-box-orient: vertical;
  color: var(--color-text-primary, #0f172a);
  font-size: 33rpx;
  font-weight: 700;
  line-height: 1.4;
}

.exam-source {
  margin-top: 10rpx;
  color: var(--color-text-secondary, #64748b);
  font-size: 24rpx;
  white-space: nowrap;
}

.exam-card__chips {
  display: flex;
  margin-top: 18rpx;
  flex-wrap: wrap;
  gap: 12rpx;
}

.exam-type,
.status-chip {
  padding: 6rpx 16rpx;
  border-radius: 8rpx;
  font-size: 21rpx;
  line-height: 1.4;
  white-space: nowrap;
}

.exam-type {
  background: var(--color-primary-subtle, #eff6ff);
  color: var(--color-primary, #2563eb);
}

.status-chip {
  border-radius: 999rpx;
}

.status-chip--primary {
  background: #dbeafe;
  color: #2563eb;
}

.status-chip--warning {
  background: #fef3c7;
  color: #b45309;
}

.status-chip--success {
  background: #dcfce7;
  color: #15803d;
}

/* 字段区按「标签 取值」成行对齐；标签均为四个汉字，取值自然对齐成一列。 */
.exam-card__facts {
  display: flex;
  margin-top: 24rpx;
  padding: 20rpx 24rpx;
  flex-direction: column;
  gap: 12rpx;
  border-radius: 16rpx;
  background: var(--color-bg-subtle, #f9fafb);
}

.exam-card__fact {
  color: var(--color-text-regular, #374151);
  font-size: 24rpx;
  word-spacing: 10rpx;
}

/* 得分结论独立成条：三态配色只做语义强调，未判定及格保持中性灰。 */
.exam-card__outcome {
  display: flex;
  margin-top: 24rpx;
  padding: 20rpx 24rpx;
  align-items: center;
  gap: 16rpx;
  border-radius: 16rpx;
}

.exam-card__outcome-label {
  color: var(--color-text-secondary, #64748b);
  font-size: 23rpx;
}

.exam-card__outcome-value {
  font-size: 34rpx;
  font-weight: 700;
}

.exam-card__outcome-flag {
  margin-left: auto;
  padding: 6rpx 18rpx;
  border-radius: 999rpx;
  font-size: 21rpx;
  white-space: nowrap;
}

.exam-card__outcome--pass {
  background: #ecfdf5;

  .exam-card__outcome-value {
    color: #15803d;
  }

  .exam-card__outcome-flag {
    background: #d1fae5;
    color: #15803d;
  }
}

.exam-card__outcome--fail {
  background: #fef2f2;

  .exam-card__outcome-value {
    color: #dc2626;
  }

  .exam-card__outcome-flag {
    background: #fee2e2;
    color: #dc2626;
  }
}

.exam-card__outcome--none {
  background: var(--color-bg-subtle, #f9fafb);

  .exam-card__outcome-value {
    color: var(--color-text-primary, #0f172a);
  }

  .exam-card__outcome-flag {
    background: #e2e8f0;
    color: #64748b;
  }
}

.exam-card__actions {
  display: flex;
  margin-top: 26rpx;
  align-items: center;
  justify-content: space-between;
  gap: 20rpx;
}

.exam-card__status-note {
  flex: 1;
  min-width: 0;
  color: var(--color-text-secondary, #64748b);
  font-size: 23rpx;
}

.exam-card__button,
.state-panel__button {
  flex: none;
  min-width: 180rpx;
  margin: 0;
  padding: 0 34rpx;
  border: 0;
  border-radius: 999rpx;
  background: linear-gradient(135deg, #2563eb 0%, #4f8bff 100%);
  color: #fff;
  font-size: 26rpx;
  line-height: 68rpx;
  text-align: center;
  box-shadow: 0 10rpx 20rpx rgb(37 99 235 / 22%);
}

.exam-card__button::after,
.state-panel__button::after {
  border: 0;
}

.exam-card__button {
  margin-left: auto;
}

.state-panel {
  display: flex;
  margin-top: 26rpx;
  padding: 72rpx 28rpx;
  align-items: center;
  flex-direction: column;
  gap: 26rpx;
  border-radius: 24rpx;
  background: var(--color-bg-surface, #fff);
  color: var(--color-text-secondary, #64748b);
  font-size: 26rpx;
  text-align: center;
}
</style>
