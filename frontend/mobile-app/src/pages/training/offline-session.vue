<script setup lang="ts">
import { onLoad } from '@dcloudio/uni-app';
import { ref } from 'vue';
import { getTrainingLearnerOfflineSession, type TrainingLearnerOfflineSession } from '@/api/training-learner';
import MobileBaseLayout from '@/layouts/MobileBaseLayout.vue';

const loading = ref(false);
const errorMessage = ref('');
const session = ref<TrainingLearnerOfflineSession>();
let offlineSessionId = 0;

/** 将场次生命周期状态转换为学员可理解的中文。 */
function sessionStatusText(status?: string | null): string {
  return {
    DRAFT: '草稿',
    SCHEDULED: '已安排',
    IN_PROGRESS: '进行中',
    COMPLETED: '已结束',
    CANCELLED: '已取消'
  }[status ?? ''] ?? '待确认';
}

/** 将本人线下培训结果状态转换为学员可理解的中文。 */
function resultStatusText(status?: string | null): string {
  return {
    PENDING: '待开始',
    COMPLETED: '已完成',
    FAILED: '未通过'
  }[status ?? ''] ?? '待确认';
}

/** 返回状态对应的视觉色调，仅影响页面展示，不改变业务状态语义。 */
function statusTone(status?: string | null): string {
  if (status === 'COMPLETED') return 'green';
  if (status === 'IN_PROGRESS') return 'blue';
  if (status === 'SCHEDULED' || status === 'PENDING') return 'amber';
  if (status === 'CANCELLED' || status === 'FAILED') return 'red';
  return 'slate';
}

/** 线下获得学时由服务端投影返回，零值同样需要展示。 */
function earnedHoursText(seconds?: number): string {
  if (seconds == null) return '-';
  return `${Math.round((seconds / 3600) * 100) / 100} 学时`;
}

/** 统一展示本地日期时间，避免直接暴露 ISO 分隔符。 */
function dateTimeText(value?: string | null): string {
  return value ? value.replace('T', ' ').slice(0, 16) : '待通知';
}

async function load(): Promise<void> {
  loading.value = true;
  errorMessage.value = '';
  try {
    session.value = await getTrainingLearnerOfflineSession(offlineSessionId);
  } catch (error) {
    errorMessage.value = error instanceof Error ? error.message : '线下场次加载失败';
  } finally {
    loading.value = false;
  }
}

onLoad((query) => {
  offlineSessionId = Number(query?.offlineSessionId);
  if (offlineSessionId) void load();
  else errorMessage.value = '线下场次参数无效';
});
</script>

<template>
  <MobileBaseLayout>
    <view class="page">
      <view v-if="loading" class="state"><text>正在加载场次信息…</text></view>
      <view v-else-if="errorMessage" class="state">
        <text>{{ errorMessage }}</text>
        <button v-if="offlineSessionId" @click="load">重新加载</button>
      </view>
      <template v-else-if="session">
        <view class="offline-page">
          <view class="offline-hero">
            <view class="offline-hero__top">
              <text class="offline-hero__eyebrow">线下培训</text>
              <text :class="['status-tag', `status-tag--${statusTone(session.sessionStatus)}`]">{{ sessionStatusText(session.sessionStatus) }}</text>
            </view>
            <text class="offline-hero__title">{{ session.sessionName }}</text>
            <text class="offline-hero__hint">本次培训的场地与时间安排如下，请按通知到场参与。</text>
          </view>

          <view class="offline-card offline-schedule">
            <view class="offline-card__heading">
              <text>培训安排</text>
              <text class="offline-card__caption">线下场次</text>
            </view>
            <view class="offline-info-row">
              <text class="offline-info-row__icon">地</text>
              <view class="offline-info-row__body">
                <text class="offline-info-row__label">培训地点</text>
                <text class="offline-info-row__value">{{ session.location || '待通知' }}</text>
              </view>
            </view>
            <view class="offline-info-row">
              <text class="offline-info-row__icon">时</text>
              <view class="offline-info-row__body">
                <text class="offline-info-row__label">培训时间</text>
                <text class="offline-info-row__value">{{ dateTimeText(session.startTime) }} 至 {{ dateTimeText(session.endTime) }}</text>
              </view>
            </view>
          </view>

          <view class="offline-status-grid">
            <view class="offline-status-item">
              <text class="offline-status-item__label">场次状态</text>
              <text :class="['status-tag', `status-tag--${statusTone(session.sessionStatus)}`]">{{ sessionStatusText(session.sessionStatus) }}</text>
            </view>
            <view class="offline-status-item">
              <text class="offline-status-item__label">完成状态</text>
              <text :class="['status-tag', `status-tag--${statusTone(session.resultStatus)}`]">{{ resultStatusText(session.resultStatus) }}</text>
            </view>
          </view>

          <view class="offline-card">
            <view class="offline-card__heading">
              <text>学习成果</text>
              <text class="offline-card__caption">以教师确认结果为准</text>
            </view>
            <view class="offline-metric-grid">
              <view class="offline-metric">
                <text class="offline-metric__label">我的成绩</text>
                <view class="offline-metric__value"><text>{{ session.score ?? '-' }}</text><text v-if="session.score != null" class="offline-metric__unit">分</text></view>
              </view>
              <view class="offline-metric">
                <text class="offline-metric__label">获得学时</text>
                <text class="offline-metric__value offline-metric__value--hours">{{ earnedHoursText(session.effectiveDurationSeconds) }}</text>
              </view>
            </view>
          </view>
        </view>
      </template>
    </view>
  </MobileBaseLayout>
</template>

<style scoped lang="less">
.page { padding: 28rpx 0 64rpx; }
.offline-page { display: flex; flex-direction: column; gap: 22rpx; }
.offline-hero { display: flex; min-height: 214rpx; padding: 30rpx; flex-direction: column; justify-content: space-between; border-radius: 28rpx; background: linear-gradient(135deg, #0f4cdd 0%, #2563eb 58%, #38bdf8 100%); box-shadow: 0 18rpx 36rpx rgb(37 99 235 / 18%); color: #fff; }
.offline-hero__top, .offline-card__heading, .offline-info-row, .offline-metric__value { display: flex; align-items: center; }
.offline-hero__top, .offline-card__heading { justify-content: space-between; }
.offline-hero__eyebrow { color: rgb(219 234 254 / 92%); font-size: 22rpx; font-weight: 700; letter-spacing: 2rpx; }
.offline-hero__title { margin-top: 24rpx; color: #fff; font-size: 40rpx; font-weight: 700; line-height: 1.35; }
.offline-hero__hint { margin-top: 14rpx; color: rgb(239 246 255 / 90%); font-size: 24rpx; line-height: 1.6; }
.offline-card { padding: 28rpx; border-radius: 24rpx; background: #fff; box-shadow: 0 10rpx 28rpx rgb(15 23 42 / 5%); }
.offline-card__heading { padding-bottom: 22rpx; border-bottom: 1rpx solid #edf1f7; color: #172033; font-size: 28rpx; font-weight: 700; }
.offline-card__caption { color: #94a3b8; font-size: 21rpx; font-weight: 400; }
.offline-schedule { padding-bottom: 12rpx; }
.offline-info-row { padding: 22rpx 0; gap: 18rpx; }
.offline-info-row + .offline-info-row { border-top: 1rpx solid #f1f5f9; }
.offline-info-row__icon { display: flex; width: 46rpx; height: 46rpx; align-items: center; justify-content: center; border-radius: 14rpx; background: #eff6ff; color: #2563eb; font-size: 23rpx; font-weight: 700; }
.offline-info-row__body { display: flex; min-width: 0; flex: 1; flex-direction: column; gap: 6rpx; }
.offline-info-row__label, .offline-status-item__label, .offline-metric__label { color: #94a3b8; font-size: 22rpx; }
.offline-info-row__value { color: #475569; font-size: 26rpx; line-height: 1.5; }
.offline-status-grid, .offline-metric-grid { display: grid; grid-template-columns: repeat(2, minmax(0, 1fr)); gap: 18rpx; }
.offline-status-item { display: flex; min-height: 116rpx; padding: 22rpx; align-items: flex-start; justify-content: space-between; flex-direction: column; border-radius: 22rpx; background: #fff; box-shadow: 0 8rpx 22rpx rgb(15 23 42 / 4%); }
.status-tag { padding: 6rpx 13rpx; border-radius: 999rpx; font-size: 21rpx; line-height: 1.3; }
.status-tag--blue { background: #dbeafe; color: #2563eb; }
.status-tag--green { background: #dcfce7; color: #16a34a; }
.status-tag--amber { background: #fef3c7; color: #d97706; }
.status-tag--red { background: #fee2e2; color: #dc2626; }
.status-tag--slate { background: #f1f5f9; color: #64748b; }
.offline-metric-grid { margin-top: 24rpx; }
.offline-metric { display: flex; min-height: 136rpx; padding: 22rpx; justify-content: space-between; flex-direction: column; border-radius: 20rpx; background: linear-gradient(135deg, #f8fbff 0%, #eff6ff 100%); }
.offline-metric__value { gap: 8rpx; color: #1d4ed8; font-size: 38rpx; font-weight: 700; }
.offline-metric__unit { color: #64748b; font-size: 22rpx; font-weight: 400; }
.offline-metric__value--hours { color: #0f766e; font-size: 30rpx; }
.state { display: flex; min-height: 250rpx; padding: 32rpx; align-items: center; justify-content: center; flex-direction: column; gap: 24rpx; border-radius: 24rpx; background: #fff; color: #777981; font-size: 26rpx; }
.state button { margin: 0; background: #2563eb; color: #fff; font-size: 23rpx; }
</style>
