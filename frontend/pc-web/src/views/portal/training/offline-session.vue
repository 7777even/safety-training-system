<script setup lang="ts">
import { computed, onMounted, ref } from 'vue';
import { useRoute, useRouter } from 'vue-router';
import { ElMessage } from 'element-plus';
import { Clock, Location } from '@element-plus/icons-vue';
import { getTrainingLearnerOfflineSession, type TrainingLearnerOfflineSession } from '@/api/training-learner';
import { getLearnerContentDisplayStatus } from './learner-training-status';

const route = useRoute();
const router = useRouter();
const loading = ref(false);
const errorMessage = ref('');
const session = ref<TrainingLearnerOfflineSession>();
const offlineSessionId = computed(() => Number(route.params.offlineSessionId));

/** 将后端结果状态转换为学员可理解的中文文案。 */
function resultStatusText(status?: string): string {
  return { PENDING: '待评分', IN_PROGRESS: '评分中', COMPLETED: '已完成', FAILED: '未通过' }[status ?? ''] ?? '待评分';
}

/** 将教师维护的场次状态转换为学员可理解的进度文案。 */
function sessionStatusText(status?: string | null): string {
  return { DRAFT: '待安排', SCHEDULED: '待开始', IN_PROGRESS: '进行中', COMPLETED: '已结束', CANCELLED: '已取消' }[status ?? ''] ?? '待开始';
}

/** 场次详情与培训内容列表统一展示学员个人完成结果，避免场次仍进行中覆盖已完成结果。 */
function displaySessionStatusText(currentSession: TrainingLearnerOfflineSession): string {
  const displayStatus = getLearnerContentDisplayStatus('OFFLINE', currentSession.resultStatus, undefined, currentSession.sessionStatus);
  if (displayStatus === 'COMPLETED') return '已完成';
  if (displayStatus === 'FAILED') return '未通过';
  return sessionStatusText(displayStatus);
}

/** 获得学时由后端以秒返回，页面仅负责换算显示且保留 0。 */
function earnedHoursText(seconds?: number): string {
  return `${Math.round(((seconds ?? 0) / 3600) * 100) / 100} 学时`;
}

/** 统一格式化场次时间，避免在学员界面直接展示 ISO 分隔符和秒数。 */
function dateTimeText(value?: string | null): string {
  return value ? value.replace('T', ' ').slice(0, 16) : '待通知';
}

/** 根据 Contract 状态返回 Element Plus 的语义色，不在前端推断业务结果。 */
function statusTagType(status?: string | null): 'primary' | 'success' | 'warning' | 'danger' | 'info' {
  if (status === 'COMPLETED') return 'success';
  if (status === 'IN_PROGRESS') return 'primary';
  if (status === 'SCHEDULED' || status === 'PENDING' || status === 'DRAFT') return 'warning';
  if (status === 'CANCELLED' || status === 'FAILED') return 'danger';
  return 'info';
}

async function load(): Promise<void> {
  loading.value = true;
  errorMessage.value = '';
  try {
    session.value = await getTrainingLearnerOfflineSession(offlineSessionId.value);
  } catch (error) {
    errorMessage.value = error instanceof Error ? error.message : '线下场次加载失败，请稍后重试。';
    ElMessage.error(errorMessage.value);
  } finally {
    loading.value = false;
  }
}

onMounted(load);
</script>

<template>
  <section v-loading="loading" class="learner-offline-session">
    <header class="portal-list-page__heading offline-session-hero">
      <div>
        <p class="portal-list-page__eyebrow">OFFLINE TRAINING</p>
        <h1>{{ session?.sessionName ?? '线下培训场次' }}</h1>
        <p>请按场次通知到场，成绩由授课教师录入后同步展示。</p>
      </div>
      <div class="offline-session-hero__actions">
        <ElTag v-if="session" :type="statusTagType(session.resultStatus)" effect="dark" round>
          {{ displaySessionStatusText(session) }}
        </ElTag>
        <ElButton @click="router.back()">返回培训详情</ElButton>
      </div>
    </header>

    <section v-if="errorMessage" class="state-panel">
      <p>{{ errorMessage }}</p>
      <ElButton type="primary" @click="load">重新加载</ElButton>
    </section>
    <template v-else-if="session">
      <ElCard class="offline-session__schedule" shadow="never">
        <template #header>
          <div class="offline-session__card-heading">
            <span>培训安排</span>
            <span>线下场次</span>
          </div>
        </template>
        <div class="offline-session__schedule-grid">
          <div class="offline-session__schedule-item">
            <span class="offline-session__schedule-icon">
              <ElIcon><Location /></ElIcon>
            </span>
            <div>
              <p>培训地点</p>
              <strong>{{ session.location || '待通知' }}</strong>
            </div>
          </div>
          <div class="offline-session__schedule-item">
            <span class="offline-session__schedule-icon">
              <ElIcon><Clock /></ElIcon>
            </span>
            <div>
              <p>培训时间</p>
              <strong>{{ dateTimeText(session.startTime) }} 至 {{ dateTimeText(session.endTime) }}</strong>
            </div>
          </div>
        </div>
      </ElCard>

      <section class="offline-session__status" aria-label="培训状态">
        <div class="offline-session__status-item">
          <span>场次状态</span>
          <ElTag :type="statusTagType(session.sessionStatus)" effect="light" round style="color:var(--el-tag-text-color)">{{ sessionStatusText(session.sessionStatus) }}</ElTag>
        </div>
        <div class="offline-session__status-item">
          <span>完成状态</span>
          <ElTag :type="statusTagType(session.resultStatus)" effect="light" round style="color:var(--el-tag-text-color)">{{ resultStatusText(session.resultStatus) }}</ElTag>
        </div>
      </section>

      <ElCard class="offline-session__metrics" shadow="never">
        <template #header>
          <div class="offline-session__card-heading">
            <span>学习成果</span>
            <span>以教师确认结果为准</span>
          </div>
        </template>
        <div class="offline-session__metrics-grid">
          <div class="offline-session__metric">
            <span>本人成绩</span>
            <strong>
              {{ session.score ?? '—' }}
              <small v-if="session.score != null">分</small>
            </strong>
          </div>
          <div class="offline-session__metric offline-session__metric--hours">
            <span>获得学时</span>
            <strong>{{ earnedHoursText(session.effectiveDurationSeconds) }}</strong>
          </div>
        </div>
      </ElCard>
    </template>
  </section>
</template>

<style scoped lang="less">
:deep(.el-card__header) {
  padding: calc(var(--el-card-padding) - 2px) var(--el-card-padding) !important;
}
:deep(.el-card__body) {
  padding: var(--el-card-padding) !important;
}
.learner-offline-session,
.state-panel {
  display: flex;
  flex-direction: column;
  gap: 18px;
}

.offline-session-hero {
  align-items: flex-start;
}

.offline-session-hero__actions {
  display: flex;
  align-items: center;
  gap: 12px;
}

.offline-session-hero :deep(.el-tag) {
  border-color: rgb(255 255 255 / 30%);
}

.offline-session__schedule,
.offline-session__metrics {
  border-radius: var(--radius-card);
  border: 1px solid var(--el-border-color-lighter);
}

.offline-session__card-heading {
  display: flex;
  align-items: center;
  justify-content: space-between;
  color: var(--el-text-color-primary);
  font-size: 16px;
  font-weight: 600;
}

.offline-session__card-heading span:last-child {
  color: var(--el-text-color-secondary);
  font-size: 12px;
  font-weight: 400;
}

.offline-session__schedule-grid,
.offline-session__metrics-grid,
.offline-session__status {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 16px;
}

.offline-session__schedule-item {
  display: flex;
  min-height: 92px;
  padding: 18px;
  align-items: center;
  gap: 14px;
  border-radius: 12px;
  background: #f8fbff;
}

.offline-session__schedule-icon {
  display: inline-flex;
  width: 40px;
  height: 40px;
  align-items: center;
  justify-content: center;
  border-radius: 12px;
  background: #e8f1ff;
  color: #2563eb;
  font-size: 20px;
}

.offline-session__schedule-item div {
  min-width: 0;
}

.offline-session__schedule-item p,
.offline-session__metric > span,
.offline-session__status-item > span {
  margin: 0 0 6px;
  color: var(--el-text-color-secondary);
  font-size: 12px;
}

.offline-session__schedule-item strong {
  display: block;
  overflow: hidden;
  color: var(--el-text-color-primary);
  font-size: 14px;
  font-weight: 500;
  line-height: 1.55;
  text-overflow: ellipsis;
}

.offline-session__status-item {
  display: flex;
  min-height: 92px;
  padding: 18px;
  align-items: flex-start;
  justify-content: space-between;
  flex-direction: column;
  border: 1px solid var(--el-border-color-lighter);
  border-radius: 12px;
  background: var(--el-bg-color);
  box-shadow: 0 8px 20px rgb(15 23 42 / 3%);
}

.offline-session__metric {
  display: flex;
  min-height: 124px;
  padding: 20px;
  justify-content: space-between;
  flex-direction: column;
  border-radius: 12px;
  background: linear-gradient(135deg, #f8fbff 0%, #eef5ff 100%);
}

.offline-session__metric strong {
  color: #1d4ed8;
  font-size: 34px;
  line-height: 1;
}

.offline-session__metric small {
  margin-left: 6px;
  color: var(--el-text-color-secondary);
  font-size: 13px;
  font-weight: 400;
}

.offline-session__metric--hours {
  background: linear-gradient(135deg, #f3fbfa 0%, #e8f8f5 100%);
}

.offline-session__metric--hours strong {
  color: #0f766e;
  font-size: 28px;
}

.state-panel {
  min-height: 220px;
  align-items: center;
  justify-content: center;
}

@media (max-width: 768px) {
  .offline-session__schedule-grid,
  .offline-session__metrics-grid,
  .offline-session__status {
    grid-template-columns: 1fr;
  }
}
</style>
