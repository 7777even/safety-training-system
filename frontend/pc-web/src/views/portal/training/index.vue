<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, ref } from 'vue';
import { useRouter } from 'vue-router';
import { getTrainingLearnerCover, getTrainingLearnerTasks, type TrainingLearnerTask } from '@/api/training-learner';
import { SetUp } from '@element-plus/icons-vue';
const router = useRouter();
const tasks = ref<TrainingLearnerTask[]>([]);
const loading = ref(false);
const errorMessage = ref('');
/** 培训计划授权范围内的封面对象地址。 */
const trainingCoverUrls = ref<Record<number, string>>({});
const hasTasks = computed(() => tasks.value.length > 0);
/** 将后端培训状态转换为中文，不在前端推断结果。 */
function statusText(status: string): string {
  return { PENDING: '待开始', LEARNING: '进行中', COMPLETED: '已完成', FAILED: '未通过', EXPIRED: '已过期', WITHDRAWN: '已退出' }[status] ?? status;
}
function statusTagType(status: string): 'info' | 'primary' | 'success' | 'warning' | 'danger' {
  const statusTypes = {
    PENDING: 'info',
    LEARNING: 'primary',
    COMPLETED: 'success',
    FAILED: 'danger',
    EXPIRED: 'warning',
    WITHDRAWN: 'info'
  } as const;
  return statusTypes[status as keyof typeof statusTypes] ?? 'info';
}
function resultText(status?: string): string {
  return status ? ({ PASSED: '合格', FAILED: '不合格', INCOMPLETE: '有必修内容尚无最终结果' }[status] ?? status) : '结果待同步';
}
/** 计划结束时，未形成最终结果与成绩不合格不同，列表按完成情况提示。 */
function isIncompleteEnded(task: TrainingLearnerTask): boolean {
  return task.publishStatus === 'ENDED' && task.resultStatus === 'INCOMPLETE';
}

/** 培训期间收敛为一条文案，两端都未设置时直接说明长期有效。 */
function getTrainingPeriodText(task: TrainingLearnerTask): string {
  const from = task.startTime?.slice(0, 10) ?? '';
  const to = task.endTime?.slice(0, 10) ?? '';
  if (!from && !to) return '长期有效';
  if (!from) return `截至 ${to}`;
  if (!to) return `${from} 起`;
  return `${from} ~ ${to}`;
}
function resultTagType(status?: string): 'info' | 'success' | 'warning' | 'danger' {
  const resultTypes = { PASSED: 'success', FAILED: 'danger', INCOMPLETE: 'warning' } as const;
  return status ? (resultTypes[status as keyof typeof resultTypes] ?? 'info') : 'info';
}
function planTagType(status: TrainingLearnerTask['publishStatus']): 'info' | 'primary' {
  return status === 'RUNNING' ? 'primary' : 'info';
}
function planStatusText(status: TrainingLearnerTask['publishStatus']): string {
  return { RUNNING: '进行中', ENDED: '已结束' }[status];
}
async function loadTasks(): Promise<void> {
  loading.value = true;
  errorMessage.value = '';
  try {
    tasks.value = await getTrainingLearnerTasks();
    await loadTrainingCovers(tasks.value);
  } catch (error) {
    errorMessage.value = error instanceof Error ? error.message : '培训任务加载失败，请稍后重试。';
  } finally {
    loading.value = false;
  }
}
/** 清理受控封面对象地址，避免刷新列表后残留浏览器内存。 */
function clearTrainingCoverUrls(): void {
  Object.values(trainingCoverUrls.value).forEach((url) => globalThis.URL.revokeObjectURL(url));
  trainingCoverUrls.value = {};
}
/** 只读取任务返回的计划级封面路径，空路径直接使用卡片占位。 */
async function loadTrainingCovers(items: TrainingLearnerTask[]): Promise<void> {
  clearTrainingCoverUrls();
  const entries = await Promise.all(
    items
      .filter((item) => item.coverUrl)
      .map(async (item) => {
        try {
          return [item.trainingPlanId, globalThis.URL.createObjectURL(await getTrainingLearnerCover(item.coverUrl!))] as const;
        } catch {
          return null;
        }
      })
  );
  trainingCoverUrls.value = Object.fromEntries(entries.filter((entry): entry is readonly [number, string] => entry !== null));
}
function openTask(task: TrainingLearnerTask): void {
  void router.push(`/portal/training/${task.trainingParticipantId}`);
}
onMounted(loadTasks);
onBeforeUnmount(clearTrainingCoverUrls);
</script>
<template>
  <section v-loading="loading" class="portal-list-page learner-training-page">
    <header class="portal-list-page__heading">
      <div>
        <p class="portal-list-page__eyebrow">MY TRAINING</p>
        <h1>我的培训</h1>
        <p>集中完成课程、考试、练习与线下培训任务。</p>
      </div>
      <ElButton :loading="loading" @click="loadTasks">刷新</ElButton>
    </header>
    <section v-if="errorMessage" class="portal-list-state state-panel">
      <p>{{ errorMessage }}</p>
      <ElButton type="primary" @click="loadTasks">重新加载</ElButton>
    </section>
    <section v-else-if="!hasTasks && !loading" class="portal-list-state state-panel"><ElEmpty description="暂无培训任务" /></section>
    <div v-else class="portal-list-grid training-grid">
      <ElCard v-for="task in tasks" :key="task.trainingParticipantId" class="portal-list-card training-card" shadow="never">
        <div class="portal-list-card__cover">
          <div v-if="trainingCoverUrls[task.trainingPlanId]" class="training-card__cover-image">
            <img :src="trainingCoverUrls[task.trainingPlanId]" :alt="`${task.planName}培训封面`" />
          </div>
          <div v-else class="portal-list-card__cover-placeholder" aria-label="未设置培训封面">
            <ElIcon><SetUp /></ElIcon>
          </div>
        </div>
        <div class="portal-list-card__content training-card__content">
          <div class="portal-list-card__header training-card__header">
            <div class="training-card__identity">
              <h2>{{ task.planName }}</h2>
            </div>
            <ElTooltip :content="`培训状态：${isIncompleteEnded(task) ? '未完成' : statusText(task.participantStatus)}`" placement="top" effect="light">
              <ElTag :type="isIncompleteEnded(task) ? 'warning' : statusTagType(task.participantStatus)" size="small">
                {{ isIncompleteEnded(task) ? '未完成' : statusText(task.participantStatus) }}
              </ElTag>
            </ElTooltip>
          </div>
          <dl class="training-card__facts">
            <div>
              <dt>培训期间</dt>
              <dd>{{ getTrainingPeriodText(task) }}</dd>
            </div>
          </dl>
          <div class="portal-list-card__meta training-card__meta training-card__status-summary">
            <ElTooltip v-if="task.publishStatus === 'ENDED'" :content="isIncompleteEnded(task) ? '有必修内容尚无最终结果，可在详情中查看各项状态' : `结果状态：${resultText(task.resultStatus)}`" placement="top" effect="light">
              <ElTag :type="resultTagType(task.resultStatus)" effect="plain" size="small">{{ resultText(task.resultStatus) }}</ElTag>
            </ElTooltip>
            <ElTooltip :content="`计划状态：${planStatusText(task.publishStatus)}`" placement="top" effect="light">
              <ElTag :type="planTagType(task.publishStatus)" effect="plain" size="small">{{ planStatusText(task.publishStatus) }}</ElTag>
            </ElTooltip>
          </div>
          <div class="portal-list-card__actions training-card__actions">
            <ElButton type="primary" @click="openTask(task)">{{ isIncompleteEnded(task) ? '查看完成情况' : task.publishStatus === 'ENDED' ? '查看结果' : '查看并执行' }}</ElButton>
          </div>
        </div>
      </ElCard>
    </div>
  </section>
</template>
<style scoped lang="less">
/* 培训卡片与课程、考试中心保持同一套信息呈现结构：封面裁切、对齐事实、底部状态与动作。 */
.training-card__content {
  gap: 14px;
}
.training-card .portal-list-card__cover img {
  object-fit: cover;
}
.training-card .portal-list-card__cover-placeholder {
  background: linear-gradient(135deg, #eef4ff, #dbeafe);
}
.training-card .portal-list-card__cover-placeholder .el-icon {
  display: grid;
  width: 46px;
  height: 46px;
  place-items: center;
  border-radius: 50%;
  background: rgb(255 255 255 / 82%);
  box-shadow: 0 2px 10px rgb(37 99 235 / 14%);
  font-size: 22px;
}
.training-card__meta {
  margin-top: 0;
}
.training-card__facts {
  display: grid;
  gap: 8px;
  margin: 0;
  padding: 12px 14px;
  border-radius: 10px;
  background: var(--color-bg-subtle);
}
.training-card__facts div {
  display: grid;
  align-items: baseline;
  gap: 12px;
  grid-template-columns: 60px minmax(0, 1fr);
}
.training-card__facts dt {
  color: var(--color-text-secondary);
  font-size: 12px;
  line-height: 20px;
}
.training-card__facts dd {
  margin: 0;
  color: var(--color-text-regular);
  font-size: 13px;
  font-variant-numeric: tabular-nums;
  line-height: 20px;
  overflow-wrap: anywhere;
      white-space: nowrap;
}
.training-card__status-summary {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 8px;
  padding-top: 12px;
  border-top: 1px dashed var(--color-border);
}
.training-card__header {
  align-items: flex-start;
  justify-content: space-between;
  gap: 12px;
}
.training-card__identity,
.training-card__header > div:first-child {
  min-width: 0;
}
.training-card__identity h2 {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.training-card__header :deep(.el-tag) {
  flex: 0 0 auto;
  border: 0;
}
.training-card__cover-image {
  height: 100%;
}
.training-card__cover-image img {
  display: block;
  width: 100%;
  height: 100%;
  object-fit: cover;
}
.training-card__actions {
  justify-content: flex-end;
}
</style>
