<script setup lang="ts">
import { computed, onMounted, ref } from 'vue';
import { useRouter } from 'vue-router';
import { getTeacherTaskPage, type TrainingTeacherTask } from '@/api/training-teacher-task';

/** 场次状态筛选项与后端枚举保持一致。 */
const SESSION_STATUS_OPTIONS = [
  { label: '草稿', value: 'DRAFT' },
  { label: '已安排', value: 'SCHEDULED' },
  { label: '进行中', value: 'IN_PROGRESS' },
  { label: '已结束', value: 'COMPLETED' },
  { label: '已取消', value: 'CANCELLED' },
] as const;

/** 同一培训下的教师线下场次展示分组。 */
interface TrainingTaskGroup {
  trainingPlanId: number;
  trainingPlanName: string;
  sessions: TrainingTeacherTask[];
  pendingScoreCount: number;
}

const router = useRouter();
const loading = ref(false);
const rows = ref<TrainingTeacherTask[]>([]);
const keyword = ref('');
const sessionStatus = ref('');
const expandedPlanIds = ref<string[]>([]);

/** 教师任务列表只展示后端场次状态的中文含义。 */
function sessionStatusText(status: string): string {
  return { DRAFT: '草稿', SCHEDULED: '已安排', IN_PROGRESS: '进行中', COMPLETED: '已结束', CANCELLED: '已取消' }[status] ?? '状态未知';
}

/** 按培训计划归集场次，避免同一培训的任务在列表中分散显示。 */
const taskGroups = computed<TrainingTaskGroup[]>(() => {
  const normalizedKeyword = keyword.value.trim().toLowerCase();
  const groups = new Map<number, TrainingTaskGroup>();

  rows.value
    .filter((task) => {
      const matchesStatus = !sessionStatus.value || task.sessionStatus === sessionStatus.value;
      const matchesKeyword = !normalizedKeyword || [task.trainingPlanName, task.sessionName, task.location]
        .some((value) => value?.toLowerCase().includes(normalizedKeyword));
      return matchesStatus && matchesKeyword;
    })
    .forEach((task) => {
      const group = groups.get(task.trainingPlanId) ?? {
        trainingPlanId: task.trainingPlanId,
        trainingPlanName: task.trainingPlanName || '培训名称未获取',
        sessions: [],
        pendingScoreCount: 0,
      };
      group.sessions.push(task);
      group.pendingScoreCount += task.pendingScoreCount;
      groups.set(task.trainingPlanId, group);
    });

  return [...groups.values()];
});

async function load(): Promise<void> {
  loading.value = true;
  try {
    rows.value = (await getTeacherTaskPage({ pageNum: 1, pageSize: 100 })).list;
    // 首次加载默认展开，保证改版后仍能直接看到原有全部任务。
    expandedPlanIds.value = [...new Set(rows.value.map((task) => String(task.trainingPlanId)))];
  } finally {
    loading.value = false;
  }
}
onMounted(load);
</script>
<template>
  <section class="admin-business-page">
    <ElCard class="admin-business-page__table" shadow="never">
      <template #header>
        <div class="admin-business-page__table-heading">
          <strong>我的线下教学任务</strong>
          <span class="teacher-task-list__hint">按所属培训归集场次，可展开查看和处理</span>
        </div>
      </template>
      <div v-loading="loading" class="teacher-task-list">
        <div class="teacher-task-list__filters">
          <ElInput v-model="keyword" clearable placeholder="搜索培训、场次或地点" class="teacher-task-list__keyword" />
          <ElSelect v-model="sessionStatus" clearable placeholder="全部场次状态" class="teacher-task-list__status">
            <ElOption v-for="option in SESSION_STATUS_OPTIONS" :key="option.value" :label="option.label" :value="option.value" />
          </ElSelect>
        </div>
        <ElCollapse v-if="taskGroups.length" v-model="expandedPlanIds" class="teacher-task-list__groups">
          <ElCollapseItem v-for="group in taskGroups" :key="group.trainingPlanId" :name="String(group.trainingPlanId)">
            <template #title>
              <div class="teacher-task-list__group-title">
                <span class="teacher-task-list__plan-name">{{ group.trainingPlanName }}</span>
                <ElTag size="small" effect="plain">{{ group.sessions.length }} 个线下场次</ElTag>
                <ElTag v-if="group.pendingScoreCount" size="small" type="warning" effect="plain">
                  待评分 {{ group.pendingScoreCount }} 人
                </ElTag>
              </div>
            </template>
            <ElTable :data="group.sessions" row-key="offlineSessionId" class="teacher-task-list__session-table">
              <ElTableColumn label="序号" width="64" align="center">
                <template #default="{ $index }">{{ $index + 1 }}</template>
              </ElTableColumn>
              <ElTableColumn prop="sessionName" label="线下培训场次" min-width="220" show-overflow-tooltip />
              <ElTableColumn prop="location" label="地点" min-width="140" show-overflow-tooltip />
              <ElTableColumn prop="startTime" label="开始时间" min-width="180" />
              <ElTableColumn label="场次状态" width="110">
                <template #default="{ row }">
                  <ElTag size="small" effect="plain">{{ sessionStatusText(row.sessionStatus) }}</ElTag>
                </template>
              </ElTableColumn>
              <ElTableColumn prop="scoredCount" label="已评分" width="90" />
              <ElTableColumn prop="pendingScoreCount" label="待评分" width="90" />
              <ElTableColumn label="操作" width="110" fixed="right">
                <template #default="{ row }">
                  <ElButton link type="primary" @click="router.push(`/admin/business-center/training/teacher-tasks/${row.offlineSessionId}`)">
                    处理任务
                  </ElButton>
                </template>
              </ElTableColumn>
            </ElTable>
          </ElCollapseItem>
        </ElCollapse>
        <ElEmpty v-else-if="!loading" description="暂无符合条件的教师任务" />
      </div>
    </ElCard>
  </section>
</template>

<style scoped lang="less">
.teacher-task-list {
  &__hint {
    margin-left: 12px;
    color: var(--el-text-color-secondary);
    font-size: 13px;
    font-weight: normal;
  }

  &__filters {
    display: flex;
    gap: 12px;
    margin-bottom: 16px;
  }

  &__keyword { width: 280px; }
  &__status { width: 160px; }

  &__groups { border-top: 1px solid var(--el-border-color-lighter); }

  :deep(.el-collapse-item__header) {
    height: 56px;
    padding: 0 16px;
    background: var(--el-fill-color-lighter);
    border-bottom: 1px solid var(--el-border-color-lighter);
  }

  :deep(.el-collapse-item__wrap) { border-bottom: 1px solid var(--el-border-color-lighter); }
  :deep(.el-collapse-item__content) { padding: 0 16px 16px; }

  &__group-title {
    display: flex;
    align-items: center;
    min-width: 0;
    gap: 8px;
  }

  &__plan-name {
    overflow: hidden;
    color: var(--el-text-color-primary);
    font-weight: 600;
    text-overflow: ellipsis;
    white-space: nowrap;
  }

  &__session-table { width: 100%; }
}
</style>
