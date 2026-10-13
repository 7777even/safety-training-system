<script setup lang="ts">
import { onShow } from '@dcloudio/uni-app';
import { onBeforeUnmount, ref } from 'vue';
import { getTrainingLearnerTasks, type TrainingLearnerTask } from '@/api/training-learner';
import MobileBaseLayout from '@/layouts/MobileBaseLayout.vue';
import { loadProtectedImageUrl, revokeProtectedImageUrl } from '@/libs/protected-image';
import UniIcons from '@dcloudio/uni-ui/lib/uni-icons/uni-icons.vue';
const tasks = ref<TrainingLearnerTask[]>([]);
const loading = ref(false);
const errorMessage = ref('');
const trainingCoverUrls = ref<Record<number, string | null>>({});
let trainingLoadVersion = 0;
function statusText(status: string): string {
  return { PENDING: '待开始', LEARNING: '进行中', COMPLETED: '已完成', FAILED: '未通过', EXPIRED: '已过期', WITHDRAWN: '已退出' }[status] ?? status;
}
/** 为个人培训进度选择语义颜色，帮助学员快速识别当前状态。 */
function statusTone(status: string): 'info' | 'primary' | 'success' | 'warning' | 'danger' {
  const statusTones = { PENDING: 'info', LEARNING: 'primary', COMPLETED: 'success', FAILED: 'danger', EXPIRED: 'warning', WITHDRAWN: 'info' } as const;
  return statusTones[status as keyof typeof statusTones] ?? 'info';
}
/** 学员列表说明未形成结果的范围；空值沿用结果待同步兜底。 */
function resultText(status?: TrainingLearnerTask['resultStatus']): string {
  return status ? ({ PASSED: '合格', FAILED: '不合格', INCOMPLETE: '有必修内容尚无最终结果' }[status] ?? status) : '结果待同步';
}
/** 已结束但结果未确定时，不把后台的 FAILED 过程值写成成绩未通过。 */
function isIncompleteEnded(task: TrainingLearnerTask): boolean {
  return task.publishStatus === 'ENDED' && task.resultStatus === 'INCOMPLETE';
}
/** 结果未同步时使用中性色，避免与个人进行中状态混淆。 */
function resultTone(status?: TrainingLearnerTask['resultStatus']): 'info' | 'success' | 'warning' | 'danger' {
  const resultTones = { PASSED: 'success', FAILED: 'danger', INCOMPLETE: 'warning' } as const;
  return status ? (resultTones[status as keyof typeof resultTones] ?? 'info') : 'info';
}
/** 培训计划已结束时使用中性色，进行中使用主色。 */
function planTone(status: TrainingLearnerTask['publishStatus']): 'info' | 'primary' {
  return status === 'RUNNING' ? 'primary' : 'info';
}
function planStatusText(status: TrainingLearnerTask['publishStatus']): string {
  return { RUNNING: '进行中', ENDED: '已结束' }[status];
}
/** 培训期间收敛为一条文案；两端都未设置时说明长期有效，避免输出「未设置 至 未设置」。 */
function getTrainingPeriodText(task: TrainingLearnerTask): string {
  const from = task.startTime?.slice(0, 10) ?? '';
  const to = task.endTime?.slice(0, 10) ?? '';
  if (!from && !to) return '长期有效';
  if (!from) return `截至 ${to}`;
  if (!to) return `${from} 起`;
  return `${from} ~ ${to}`;
}
/** 已结束计划只查看已有内容；缺少最终结果时改为查看完成情况。 */
function actionText(task: TrainingLearnerTask): string {
  if (isIncompleteEnded(task)) return '查看完成情况';
  return task.publishStatus === 'ENDED' ? '查看结果' : '查看并执行';
}
async function load(): Promise<void> {
  const loadVersion = ++trainingLoadVersion;
  loading.value = true;
  errorMessage.value = '';
  try {
    const taskList = await getTrainingLearnerTasks();
    if (loadVersion !== trainingLoadVersion) return;
    Object.values(trainingCoverUrls.value).forEach(revokeProtectedImageUrl);
    trainingCoverUrls.value = {};
    tasks.value = taskList;
    await loadTrainingCovers(taskList, loadVersion);
  } catch (error) {
    errorMessage.value = error instanceof Error ? error.message : '培训任务加载失败';
  } finally {
    loading.value = false;
  }
}

/** 仅按任务返回的受控路径读取培训封面；无封面或读取失败均保留占位。 */
async function loadTrainingCovers(items: TrainingLearnerTask[], loadVersion: number): Promise<void> {
  const entries = await Promise.all(
    items.map(async (task) => {
      if (!task.coverUrl) return [task.trainingPlanId, null] as const;
      try {
        return [task.trainingPlanId, await loadProtectedImageUrl(task.coverUrl)] as const;
      } catch {
        return [task.trainingPlanId, null] as const;
      }
    })
  );
  if (loadVersion === trainingLoadVersion) trainingCoverUrls.value = Object.fromEntries(entries);
}
function openTask(task: TrainingLearnerTask): void {
  uni.navigateTo({ url: `/pages/training/detail?trainingParticipantId=${task.trainingParticipantId}` });
}
onShow(load);
onBeforeUnmount(() => Object.values(trainingCoverUrls.value).forEach(revokeProtectedImageUrl));
</script>
<template>
  <MobileBaseLayout>
    <view class="page">
      <view class="page-hero">
        <view class="page-hero__deco" />
        <text class="page-hero__eyebrow">MY TRAINING</text>
        <text class="page-hero__title">我的培训</text>
        <text class="page-hero__subtitle">完成课程、考试、练习与线下任务</text>
      </view>

      <view v-if="loading" class="state-panel"><text>正在加载培训任务…</text></view>
      <view v-else-if="errorMessage" class="state-panel">
        <text>{{ errorMessage }}</text>
        <button class="state-panel__button" @click="load">重新加载</button>
      </view>
      <view v-else-if="!tasks.length" class="state-panel"><text>暂无培训任务</text></view>
      <view v-else class="training-list">
        <view v-for="task in tasks" :key="task.trainingParticipantId" class="training-card" @click="openTask(task)">
          <view class="training-card__top">
            <image
              v-if="trainingCoverUrls[task.trainingPlanId]"
              class="training-card__cover"
              :src="trainingCoverUrls[task.trainingPlanId] || ''"
              mode="aspectFill"
            />
            <view v-else class="training-card__cover training-card__cover--placeholder">
              <UniIcons type="image" size="30" color="#2563eb" />
            </view>
            <view class="training-card__identity">
              <text class="plan-name">{{ task.planName }}</text>
              <text class="plan-hint">{{ isIncompleteEnded(task) ? '已结束，可查看各项状态' : task.publishStatus === 'ENDED' ? '培训计划已结束，可回看结果' : '培训计划执行中' }}</text>
              <view class="training-card__chips">
                <text class="status-chip" :class="`status-chip--${isIncompleteEnded(task) ? 'warning' : statusTone(task.participantStatus)}`">
                  {{ isIncompleteEnded(task) ? '未完成' : statusText(task.participantStatus) }}
                </text>
              </view>
            </view>
          </view>

          <view class="training-card__facts">
            <text class="training-card__fact">培训期间 {{ getTrainingPeriodText(task) }}</text>
          </view>

          <view class="status-summary">
            <text v-if="task.publishStatus === 'ENDED'" class="status-chip status-chip--plain" :class="`status-chip--${resultTone(task.resultStatus)}`">
              结果：{{ resultText(task.resultStatus) }}
            </text>
            <text class="status-chip status-chip--plain" :class="`status-chip--${planTone(task.publishStatus)}`">
              计划状态 {{ planStatusText(task.publishStatus) }}
            </text>
          </view>

          <view class="training-card__actions">
            <button class="training-card__button" @click.stop="openTask(task)">{{ actionText(task) }}</button>
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

/* 页头 hero：与学习、考试两页共用同一条蓝色渐变横幅，保证三端视觉一致。 */
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

.training-list {
  display: flex;
  margin-top: 26rpx;
  flex-direction: column;
  gap: 22rpx;
}

.training-card {
  display: flex;
  padding: 32rpx;
  flex-direction: column;
  border-radius: 24rpx;
  background: var(--color-bg-surface, #fff);
  box-shadow: 0 8rpx 24rpx rgb(38 39 45 / 6%);
}

.training-card__top {
  display: flex;
  min-width: 0;
  align-items: flex-start;
  gap: 24rpx;
}

.training-card__cover {
  width: 176rpx;
  height: 176rpx;
  flex: none;
  border-radius: 18rpx;
  background: var(--color-primary-subtle, #eff6ff);
}

.training-card__cover--placeholder {
  display: flex;
  align-items: center;
  justify-content: center;
}

.training-card__identity {
  display: flex;
  min-width: 0;
  flex: 1;
  flex-direction: column;
}

.plan-name,
.plan-hint {
  overflow: hidden;
  text-overflow: ellipsis;
}

.plan-name {
  display: -webkit-box;
  overflow: hidden;
  -webkit-line-clamp: 2;
  -webkit-box-orient: vertical;
  color: var(--color-text-primary, #0f172a);
  font-size: 33rpx;
  font-weight: 700;
  line-height: 1.4;
}

.plan-hint {
  margin-top: 10rpx;
  color: var(--color-text-secondary, #64748b);
  font-size: 24rpx;
  white-space: nowrap;
}

.training-card__chips {
  display: flex;
  margin-top: 18rpx;
  flex-wrap: wrap;
  gap: 12rpx;
}

.status-chip {
  padding: 6rpx 16rpx;
  border-radius: 999rpx;
  font-size: 21rpx;
  line-height: 1.4;
  white-space: nowrap;
}

/* 结果与计划状态为并列的只读说明，用浅描边区分于个人培训状态标签。 */
.status-chip--plain {
  border: 1rpx solid rgb(15 23 42 / 6%);
}

.status-chip--info {
  background: #f1f5f9;
  color: #64748b;
}

.status-chip--primary {
  background: #dbeafe;
  color: #2563eb;
}

.status-chip--success {
  background: #dcfce7;
  color: #15803d;
}

.status-chip--warning {
  background: #fef3c7;
  color: #b45309;
}

.status-chip--danger {
  background: #fee2e2;
  color: #dc2626;
}

/* 字段区按「标签 取值」成行对齐；标签均为四个汉字，取值自然对齐成一列。 */
.training-card__facts {
  display: flex;
  margin-top: 24rpx;
  padding: 20rpx 24rpx;
  flex-direction: column;
  gap: 12rpx;
  border-radius: 16rpx;
  background: var(--color-bg-subtle, #f9fafb);
}

.training-card__fact {
  color: var(--color-text-regular, #374151);
  font-size: 24rpx;
  word-spacing: 10rpx;
}

.status-summary {
  display: flex;
  margin-top: 24rpx;
  padding-top: 24rpx;
  flex-wrap: wrap;
  gap: 12rpx;
  border-top: 1rpx solid #f1f1f3;
}

.training-card__actions {
  display: flex;
  margin-top: 26rpx;
  justify-content: flex-end;
}

.training-card__button,
.state-panel__button {
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

.training-card__button::after,
.state-panel__button::after {
  border: 0;
}

.state-panel {
  display: flex;
  margin-top: 26rpx;
  min-height: 240rpx;
  padding: 48rpx 28rpx;
  align-items: center;
  justify-content: center;
  flex-direction: column;
  gap: 26rpx;
  border-radius: 24rpx;
  background: var(--color-bg-surface, #fff);
  color: var(--color-text-secondary, #64748b);
  font-size: 26rpx;
  text-align: center;
}
</style>
