<script setup lang="ts">
import { onShow } from '@dcloudio/uni-app';
import { computed, ref } from 'vue';
import { getTrainingLearnerTasks, type TrainingLearnerTask } from '@/api/training-learner';
import MobileBaseLayout from '@/layouts/MobileBaseLayout.vue';
import UniIcons from '@dcloudio/uni-ui/lib/uni-icons/uni-icons.vue';

const tasks = ref<TrainingLearnerTask[]>([]);
const loading = ref(false);
const errorMessage = ref('');

/** 仅已结束培训展示正式结果；进行中的实时投影尚未结算。 */
function resultText(status?: TrainingLearnerTask['resultStatus']): string {
  return status ? ({ PASSED: '合格', FAILED: '不合格', INCOMPLETE: '结果待补全' }[status] ?? status) : '结果待同步';
}

function resultTone(status?: TrainingLearnerTask['resultStatus']): 'info' | 'success' | 'warning' | 'danger' {
  const tones = { PASSED: 'success', FAILED: 'danger', INCOMPLETE: 'warning' } as const;
  return status ? (tones[status as keyof typeof tones] ?? 'info') : 'info';
}

function statusText(status: string): string {
  return { PENDING: '待开始', LEARNING: '进行中', COMPLETED: '已完成', FAILED: '未通过', EXPIRED: '已过期', WITHDRAWN: '已退出' }[status] ?? status;
}

/** 类型名称缺失不等于计划未分类，提示管理端核对类型主数据。 */
function typeNameText(task: TrainingLearnerTask): string {
  return task.trainingTypeName || '类型名称待补全';
}

/** 按培训类型分组，组内任务按计划名稳定排序。 */
const groups = computed(() => {
  const map = new Map<string, TrainingLearnerTask[]>();
  for (const task of tasks.value) {
    const key = typeNameText(task);
    const list = map.get(key) ?? [];
    list.push(task);
    map.set(key, list);
  }
  return Array.from(map.entries())
    .sort(([a], [b]) => a.localeCompare(b, 'zh'))
    .map(([name, list]) => ({ name, count: list.length, tasks: list }));
});

async function load(): Promise<void> {
  loading.value = true;
  errorMessage.value = '';
  try {
    tasks.value = await getTrainingLearnerTasks();
  } catch (error) {
    errorMessage.value = error instanceof Error ? error.message : '培训档案加载失败，请稍后重试。';
  } finally {
    loading.value = false;
  }
}

function openTask(task: TrainingLearnerTask): void {
  uni.navigateTo({ url: `/pages/training/detail?trainingParticipantId=${task.trainingParticipantId}` });
}

onShow(() => {
  void load();
});
</script>

<template>
  <MobileBaseLayout>
    <view class="page">
      <view class="page-hero">
        <view class="page-hero__deco" />
        <text class="page-hero__eyebrow">MY ARCHIVE</text>
        <text class="page-hero__title">我的培训档案</text>
        <text class="page-hero__subtitle">按培训类型汇总各期培训结果</text>
      </view>

      <view v-if="loading" class="state-panel"><text>正在加载培训档案…</text></view>

      <view v-else-if="errorMessage" class="state-panel">
        <text>{{ errorMessage }}</text>
        <button class="state-panel__button" @click="load">重新加载</button>
      </view>

      <view v-else-if="!groups.length" class="state-panel">
        <UniIcons type="paperclip" size="48" color="#cbd5e1" />
        <text>暂无培训档案</text>
      </view>

      <view v-else class="group-list">
        <view v-for="group in groups" :key="group.name" class="group-card">
          <view class="group-card__head">
            <text class="group-card__name">{{ group.name }}</text>
            <text class="group-card__count">共 {{ group.count }} 期</text>
          </view>
          <view v-for="task in group.tasks" :key="task.trainingParticipantId" class="task-row" @click="openTask(task)">
            <view class="task-row__main">
              <text class="task-row__plan">{{ task.planName }}</text>
              <text class="task-row__status">{{ statusText(task.participantStatus) }}</text>
            </view>
            <view class="task-row__side">
              <text v-if="task.publishStatus === 'ENDED'" class="status-chip" :class="`status-chip--${resultTone(task.resultStatus)}`">
                {{ resultText(task.resultStatus) }}
              </text>
              <UniIcons type="right" size="14" color="#94a3b8" />
            </view>
          </view>
        </view>
      </view>
    </view>
  </MobileBaseLayout>
</template>

<style scoped lang="less">
.page {
  padding: 24rpx 0 64rpx;
}

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
  margin-top: 8rpx;
  color: #fff;
  font-size: 42rpx;
  font-weight: 700;
}

.page-hero__subtitle {
  margin-top: 8rpx;
  color: rgb(255 255 255 / 78%);
  font-size: 24rpx;
}

.state-panel {
  display: flex;
  margin-top: 80rpx;
  flex-direction: column;
  align-items: center;
  gap: 18rpx;
  color: var(--color-text-secondary);
  font-size: 28rpx;
}

.state-panel__button {
  min-height: 72rpx;
  padding: 0 44rpx;
  border-radius: 999rpx;
  background: #2563eb;
  color: #fff;
  font-size: 26rpx;
  line-height: 72rpx;
}

.state-panel__button::after {
  border: 0;
}

.group-list {
  display: flex;
  margin-top: 26rpx;
  flex-direction: column;
  gap: 24rpx;
}

.group-card {
  padding: 28rpx;
  border-radius: 24rpx;
  background: var(--color-bg-surface);
  box-shadow: 0 8rpx 24rpx rgb(38 39 45 / 6%);
}

.group-card__head {
  display: flex;
  align-items: baseline;
  justify-content: space-between;
}

.group-card__name {
  color: var(--color-text-primary);
  font-size: 30rpx;
  font-weight: 700;
}

.group-card__count {
  color: var(--color-text-secondary);
  font-size: 22rpx;
}

.task-row {
  display: flex;
  margin-top: 10rpx;
  padding: 20rpx 0;
  align-items: center;
  gap: 20rpx;
}

.task-row + .task-row {
  border-top: 1rpx solid #f1f1f3;
}

.task-row__main {
  display: flex;
  flex: 1;
  min-width: 0;
  flex-direction: column;
}

.task-row__plan {
  overflow: hidden;
  color: var(--color-text-primary);
  font-size: 27rpx;
  font-weight: 500;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.task-row__status {
  margin-top: 6rpx;
  color: var(--color-text-secondary);
  font-size: 22rpx;
}

.task-row__side {
  display: flex;
  flex: none;
  align-items: center;
  gap: 12rpx;
}

.status-chip {
  padding: 6rpx 16rpx;
  border-radius: 999rpx;
  font-size: 21rpx;
  font-weight: 600;
}

.status-chip--success {
  background: rgb(22 163 74 / 12%);
  color: #16a34a;
}

.status-chip--danger {
  background: rgb(220 38 38 / 10%);
  color: #dc2626;
}

.status-chip--warning {
  background: rgb(217 119 6 / 12%);
  color: #d97706;
}

.status-chip--info {
  background: var(--color-primary-subtle);
  color: #2563eb;
}
</style>
