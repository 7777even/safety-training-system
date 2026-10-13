<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue';
import { useRoute, useRouter } from 'vue-router';
import { ElMessage, ElMessageBox } from 'element-plus';
import {
  cancelTeacherOfflineSession,
  completeTeacherOfflineSession,
  getTeacherTaskDetail,
  startTeacherOfflineSession,
  upsertOfflineScore,
  type TrainingTeacherRosterItem,
  type TrainingTeacherTaskDetail
} from '@/api/training-teacher-task';
import { getRecordScoreUnavailableMessage, getTeacherTaskLoadErrorMessage } from './teacher-task-detail-state';

const OFFLINE_SCORE_NAME = '线下培训成绩';
const route = useRoute();
const router = useRouter();
const sessionId = Number(route.params.offlineSessionId);
const loading = ref(false);
const saving = ref(false);
const transitioningAction = ref('');
const detail = ref<TrainingTeacherTaskDetail>();
const loadErrorMessage = ref('');
const scoreDialogVisible = ref(false);
const selectedParticipantId = ref<number>();
const scoreForm = reactive({ score: 0, maxScore: 100 });

/** 后端仅在场次可录分时返回 RECORD_SCORE，页面不自行放开写入。 */
const canRecordScore = computed(() => detail.value?.availableActions.includes('RECORD_SCORE') ?? false);
/** 场次状态操作严格由后端 availableActions 控制，避免前端自行推导状态。 */
const canStart = computed(() => detail.value?.availableActions.includes('START') ?? false);
const canComplete = computed(() => detail.value?.availableActions.includes('COMPLETE') ?? false);
const canCancel = computed(() => detail.value?.availableActions.includes('CANCEL') ?? false);
/** 不改变后端授权，仅将已返回的状态翻译为教师可理解的不可录分原因。 */
const recordScoreUnavailableMessage = computed(() =>
  detail.value ? getRecordScoreUnavailableMessage(detail.value.sessionStatus, canRecordScore.value) : ''
);

/** 教师名单中的成绩状态仅用于展示，完成判断由后端投影决定。 */
function resultStatusText(status?: string | null): string {
  return { PENDING: '待评分', IN_PROGRESS: '评分中', COMPLETED: '已完成', FAILED: '未通过' }[status ?? ''] ?? '待评分';
}

/** 将场次枚举转换为教师任务页面的中文状态。 */
function sessionStatusText(status?: string): string {
  return { DRAFT: '草稿', SCHEDULED: '已安排', IN_PROGRESS: '进行中', COMPLETED: '已结束', CANCELLED: '已取消' }[status ?? ''] ?? '状态未知';
}

/** 后端接收本地日期时间格式，避免 UTC 时间被直接当作本地评分时间。 */
function nowLocalDateTime(): string {
  const now = new Date();
  const pad = (value: number): string => String(value).padStart(2, '0');
  return `${now.getFullYear()}-${pad(now.getMonth() + 1)}-${pad(now.getDate())}T${pad(now.getHours())}:${pad(now.getMinutes())}:${pad(now.getSeconds())}`;
}

async function load(): Promise<void> {
  loading.value = true;
  loadErrorMessage.value = '';
  try {
    detail.value = await getTeacherTaskDetail(sessionId);
  } catch (error) {
    loadErrorMessage.value = getTeacherTaskLoadErrorMessage(error);
    ElMessage.error(loadErrorMessage.value);
  } finally {
    loading.value = false;
  }
}

/** 从后端名单选择参训人，已录分时默认回显真实得分和满分。 */
function openScoreDialog(row: TrainingTeacherRosterItem): void {
  selectedParticipantId.value = row.trainingParticipantId;
  scoreForm.score = row.score ?? 0;
  scoreForm.maxScore = row.maxScore ?? 100;
  scoreDialogVisible.value = true;
}

/** Element Plus 表格行在模板层是通用类型，进入业务函数前恢复为名单项。 */
function asRosterItem(row: unknown): TrainingTeacherRosterItem {
  return row as TrainingTeacherRosterItem;
}

async function saveScore(): Promise<void> {
  if (!selectedParticipantId.value || scoreForm.maxScore <= 0 || scoreForm.score < 0 || scoreForm.score > scoreForm.maxScore) {
    ElMessage.warning('请填写大于 0 的满分，且得分不得小于 0 或大于满分。');
    return;
  }
  saving.value = true;
  try {
    await upsertOfflineScore({
      trainingParticipantId: selectedParticipantId.value,
      offlineSessionId: sessionId,
      scoreName: OFFLINE_SCORE_NAME,
      score: scoreForm.score,
      maxScore: scoreForm.maxScore,
      scoreSource: 'MANUAL',
      scoredTime: nowLocalDateTime()
    });
    scoreDialogVisible.value = false;
    await load();
    ElMessage.success('线下成绩已保存，培训结果已刷新。');
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '线下成绩保存失败，请稍后重试。');
  } finally {
    saving.value = false;
  }
}

/** 使用详情返回的 version 触发场次状态流转，成功后统一重新读取后端事实。 */
async function transitionSession(action: 'START' | 'COMPLETE' | 'CANCEL'): Promise<void> {
  if (!detail.value) return;
  const actionText = { START: '开始', COMPLETE: '结束', CANCEL: '取消' }[action];
  try {
    await ElMessageBox.confirm(`确认${actionText}该线下场次吗？`, `${actionText}场次`, {
      confirmButtonText: `确认${actionText}`,
      cancelButtonText: '暂不操作',
      type: action === 'CANCEL' ? 'warning' : 'info'
    });
  } catch {
    return;
  }
  transitioningAction.value = action;
  try {
    const request = {
      START: startTeacherOfflineSession,
      COMPLETE: completeTeacherOfflineSession,
      CANCEL: cancelTeacherOfflineSession
    }[action];
    await request(sessionId, detail.value.version);
    await load();
    ElMessage.success(`场次已${actionText}。`);
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : `场次${actionText}失败，请稍后重试。`);
  } finally {
    transitioningAction.value = '';
  }
}

onMounted(load);
</script>

<template>
  <section v-loading="loading" class="admin-detail-page">
    <header class="admin-detail-page__header teacher-task-hero">
      <div>
        <h1>{{ detail?.sessionName ?? '线下场次处理' }}</h1>
        <p>请按参训名单逐人录入线下成绩，成绩保存后会自动刷新培训结果。</p>
      </div>
      <div class="header-actions">
        <ElButton v-if="canStart" type="primary" :loading="transitioningAction === 'START'" @click="transitionSession('START')">开始场次</ElButton>
        <ElButton v-if="canComplete" type="primary" :loading="transitioningAction === 'COMPLETE'" @click="transitionSession('COMPLETE')">
          结束场次
        </ElButton>
        <ElButton v-if="canCancel" type="danger" plain :loading="transitioningAction === 'CANCEL'" @click="transitionSession('CANCEL')">
          取消场次
        </ElButton>
        <ElButton @click="router.push('/admin/business-center/training/teacher-tasks')">返回任务列表</ElButton>
      </div>
    </header>

    <ElAlert title="本期线下培训由教师录入参训人员成绩，成绩将作为培训完成依据。" type="info" show-icon :closable="false" class="teacher-task-notice" />

    <ElAlert v-if="loadErrorMessage" :title="loadErrorMessage" type="error" show-icon :closable="false" />

    <section v-if="detail" class="summary-grid teacher-task-metrics">
      <div class="teacher-task-metric teacher-task-metric--location">
        <span>培训地点</span>
        <strong>{{ detail?.location || '待通知' }}</strong>
      </div>
      <div class="teacher-task-metric">
        <span>场次状态</span>
        <strong class="teacher-task-status">{{ sessionStatusText(detail?.sessionStatus) }}</strong>
      </div>
      <div class="teacher-task-metric">
        <span>应到人数</span>
        <strong>{{ detail?.participantCount ?? 0 }}</strong>
      </div>
      <div class="teacher-task-metric teacher-task-metric--scored">
        <span>已评分人数</span>
        <strong>{{ detail?.scoredCount ?? 0 }}</strong>
      </div>
      <div class="teacher-task-metric teacher-task-metric--pending">
        <span>待评分人数</span>
        <strong>{{ detail?.pendingScoreCount ?? 0 }}</strong>
      </div>
    </section>

    <ElCard v-if="detail" class="admin-detail-page__card teacher-task-roster" shadow="never">
      <template #header>
        <div class="card-heading">
          <div>
            <strong>参训人名单与成绩</strong>
            <span>每名参训人仅维护一条线下培训成绩。</span>
          </div>
          <ElTag class="score-tag" :type="canRecordScore ? 'success' : 'info'">{{ canRecordScore ? '可录入成绩' : '当前不可录入' }}</ElTag>
        </div>
      </template>
      <ElAlert
        v-if="recordScoreUnavailableMessage"
        :title="recordScoreUnavailableMessage"
        type="warning"
        show-icon
        :closable="false"
        class="record-score-alert"
      />
      <ElTable :data="detail?.roster ?? []" border>
        <ElTableColumn label="学员" min-width="160">
          <template #default="{ row }">
            <div>{{ row.userName || '未获取姓名' }}</div>
            <!-- <small>用户 ID：{{ row.userId }}</small> -->
          </template>
        </ElTableColumn>
        <ElTableColumn label="成绩" min-width="120">
          <template #default="{ row }">{{ row.score ?? '-' }}</template>
        </ElTableColumn>
        <ElTableColumn label="满分" min-width="100">
          <template #default="{ row }">{{ row.maxScore ?? '-' }}</template>
        </ElTableColumn>
        <ElTableColumn prop="scoredTime" label="评分时间" min-width="180">
          <template #default="{ row }">{{ row.scoredTime ?? '-' }}</template>
        </ElTableColumn>
        <ElTableColumn label="完成状态" min-width="120">
          <template #default="{ row }">
            <ElTag :type="row.resultStatus === 'COMPLETED' ? 'success' : row.resultStatus === 'FAILED' ? 'danger' : 'info'">
              {{ resultStatusText(row.resultStatus) }}
            </ElTag>
          </template>
        </ElTableColumn>
        <ElTableColumn label="操作" width="120">
          <template #default="{ row }">
            <ElButton v-if="canRecordScore" link type="primary" @click="openScoreDialog(asRosterItem(row))">
              {{ row.score != null ? '修改成绩' : '录入成绩' }}
            </ElButton>
          </template>
        </ElTableColumn>
        <template #empty><ElEmpty description="当前场次暂无参训人" /></template>
      </ElTable>
    </ElCard>

    <ElDialog v-model="scoreDialogVisible" title="录入线下培训成绩" width="420px">
      <ElForm label-width="80px">
        <ElFormItem label="成绩项">
          <span>{{ OFFLINE_SCORE_NAME }}</span>
        </ElFormItem>
        <ElFormItem label="得分" required><ElInputNumber v-model="scoreForm.score" :min="0" :max="scoreForm.maxScore" /></ElFormItem>
        <ElFormItem label="满分" required><ElInputNumber v-model="scoreForm.maxScore" :min="1" /></ElFormItem>
      </ElForm>
      <template #footer>
        <ElButton @click="scoreDialogVisible = false">取消</ElButton>
        <ElButton type="primary" :loading="saving" @click="saveScore">保存</ElButton>
      </template>
    </ElDialog>
  </section>
</template>

<style scoped lang="less">
.summary-grid {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: 12px;
}
.summary-grid > div {
  padding: 14px 16px;
  border: 1px solid var(--color-border);
  border-radius: var(--radius-card);
  background: var(--color-bg-surface);
}

.teacher-task-hero {
  position: relative;
  padding: 4px 0 8px;
}

.teacher-task-notice {
  border-radius: 10px;
}

.teacher-task-metrics {
  grid-template-columns: 1.35fr repeat(4, minmax(0, 1fr));
}

.summary-grid > .teacher-task-metric {
  min-height: 76px;
  padding: 13px 16px;
  background: linear-gradient(145deg, var(--color-bg-surface), var(--color-bg-subtle));
}

.teacher-task-metric--location {
  border-color: color-mix(in srgb, var(--color-primary) 24%, var(--color-border));
}

.teacher-task-metric--scored strong {
  color: var(--color-success, #67c23a);
}

.teacher-task-metric--pending strong {
  color: var(--color-warning, #e6a23c);
}

.teacher-task-status {
  color: var(--color-primary);
}
.summary-grid span,
.summary-grid strong,
.card-heading strong,
.card-heading span {
  display: block;
}
.summary-grid span,
.card-heading span {
  color: var(--color-text-secondary);
  font-size: 13px;
}
.card-heading {
  .score-tag {
    color: var(--el-tag-text-color);
    line-height: 24px;
  }
}

.teacher-task-roster {
  overflow: hidden;
}

.teacher-task-roster :deep(.el-card__header) {
  padding: 14px 18px;
  background: var(--color-bg-subtle);
}

.teacher-task-roster :deep(.el-card__body) {
  padding: 14px 18px 18px;
}
.summary-grid strong {
  margin-top: 4px;
  font-size: 20px;
}
.card-heading {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
}
.record-score-alert {
  margin-bottom: 12px;
}
.header-actions {
  display: flex;
  gap: 8px;
}
@media (max-width: 900px) {
  .summary-grid,
  .teacher-task-metrics {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }
}
</style>
