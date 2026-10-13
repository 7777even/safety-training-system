<script setup lang="ts">
import type { LearnerExamTask, LearnerExamTaskQuery, LearnerExamTaskStatus } from '@shared/types';
import { computed, onBeforeUnmount, onMounted, reactive, ref } from 'vue';
import { useRouter } from 'vue-router';
import { ElMessage } from 'element-plus';
import { ArrowRight, Document, Medal, Tickets } from '@element-plus/icons-vue';
import { getExam, type ExamItem } from '@/api/exam';
import { getLearnerExamCover, getLearnerExamTaskPage } from '@/api/learner-exam';
import { DEFAULT_LIST_PAGE_SIZE, LIST_PAGE_SIZES } from '@/constants/pagination';
import {
  LEARNER_EXAM_ACTION,
  LEARNER_TASK_STATUS_OPTIONS,
  getExamEnumLabel,
  getExamTypeLabel,
  getCandidateStatusLabel,
  getLearnerTaskTagType
} from '@/constants/exam-options';
import { useUserStore } from '@/stores/modules/user';
import { hasPermission } from '@/utils/permission';
import { formatDuration } from '@/utils/duration';

const router = useRouter();
const userStore = useUserStore();
const tasks = ref<LearnerExamTask[]>([]);
const total = ref(0);
const loading = ref(false);
const errorMessage = ref('');
/** 考试封面对象地址，仅通过学员受控封面接口读取。 */
const examCoverUrls = ref<Record<number, string>>({});
/** 当前在右侧抽屉中查看的考试任务。 */
const activeTask = ref<LearnerExamTask | null>(null);
/** 考试定义详情，用于补充卡片未展示的及格分与考试须知。 */
const examDetail = ref<ExamItem | null>(null);
const detailLoading = ref(false);
const detailDrawerVisible = ref(false);
const query = reactive({
  pageNum: 1,
  pageSize: DEFAULT_LIST_PAGE_SIZE,
  taskStatus: undefined as LearnerExamTaskStatus | undefined,
  examName: ''
});

/** 开考后必须进入内部作答页，因此同时要求开始和作答查看权限。 */
const canStartExam = (): boolean =>
  hasPermission(userStore.userInfo, 'exam:attempt:start') && hasPermission(userStore.userInfo, 'exam:learner:attempt:view');
const canViewAttempt = (): boolean => hasPermission(userStore.userInfo, 'exam:learner:attempt:view');
const taskQuery = computed<LearnerExamTaskQuery>(() => ({
  pageNum: query.pageNum,
  pageSize: query.pageSize,
  ...(query.taskStatus ? { 'query.taskStatus': query.taskStatus } : {}),
  ...(query.examName.trim() ? { 'query.examName': query.examName.trim() } : {})
}));
/** 门户考试状态筛选项，与学员任务 Contract 枚举保持一致。 */
const taskStatusTabs: Array<{ label: string; value: LearnerExamTaskStatus | undefined }> = [
  { label: '全部', value: undefined },
  ...LEARNER_TASK_STATUS_OPTIONS
];

/** 加载当前登录学员任务；后端基于会话身份确定用户，不传其他用户 ID。 */
async function loadTasks(): Promise<void> {
  loading.value = true;
  errorMessage.value = '';
  try {
    const page = await getLearnerExamTaskPage(taskQuery.value);
    tasks.value = page.list;
    total.value = page.total;
    await loadExamCovers(page.list);
  } catch (error) {
    errorMessage.value = error instanceof Error ? error.message : '考试任务加载失败，请稍后重试。';
  } finally {
    loading.value = false;
  }
}

/** 释放对象地址，避免筛选或分页切换后遗留浏览器内存。 */
function clearExamCoverUrls(): void {
  Object.values(examCoverUrls.value).forEach((url) => globalThis.URL.revokeObjectURL(url));
  examCoverUrls.value = {};
}

/** 依据 examId 读取受控封面；无封面与读取失败均保留统一占位。 */
async function loadExamCovers(examTasks: LearnerExamTask[]): Promise<void> {
  clearExamCoverUrls();
  const coverEntries = await Promise.all(
    [...new Map(examTasks.filter((task) => task.coverUrl).map((task) => [task.examId, task])).values()].map(async (task) => {
      try {
        return [task.examId, globalThis.URL.createObjectURL(await getLearnerExamCover(task.coverUrl!))] as const;
      } catch {
        return null;
      }
    })
  );
  examCoverUrls.value = Object.fromEntries(coverEntries.filter((entry): entry is readonly [number, string] => entry !== null));
}

/** 更改每页数量时回到第一页，避免保留已失效的页码。 */
async function changePageSize(): Promise<void> {
  query.pageNum = 1;
  await loadTasks();
}

/** 切换考试状态后返回第一页，避免沿用已失效的分页位置。 */
async function changeTaskStatus(status: LearnerExamTaskStatus | undefined): Promise<void> {
  if (query.taskStatus === status) return;
  query.taskStatus = status;
  query.pageNum = 1;
  await loadTasks();
}

/** 打开考试详情抽屉；卡片只保留概览，详细字段延后按需读取。 */
async function openTaskDetail(task: LearnerExamTask): Promise<void> {
  activeTask.value = task;
  examDetail.value = null;
  detailDrawerVisible.value = true;
  detailLoading.value = true;
  try {
    examDetail.value = await getExam(task.examId);
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '考试详情加载失败，请稍后重试。');
  } finally {
    detailLoading.value = false;
  }
}

/** 根据 Contract 建议操作续答、查看结果或创建新的独立考试尝试。 */
async function handleTask(task: LearnerExamTask): Promise<void> {
  // 线下考试的成绩由管理端录入；旧数据即使误传 START 等动作，也不能进入线上作答。
  if (isOfflineExam(task) && task.availableAction !== LEARNER_EXAM_ACTION.VIEW_RESULT) {
    ElMessage.info('线下考试成绩由考试管理方录入，录入后可查看结果。');
    return;
  }
  if (task.availableAction === LEARNER_EXAM_ACTION.CONTINUE && task.currentAttemptId) {
    if (!canViewAttempt()) return;
    await router.push(`/portal/exams/${task.examId}/attempt/${task.currentAttemptId}`);
    return;
  }
  if (
    (task.availableAction === LEARNER_EXAM_ACTION.VIEW_RESULT || task.availableAction === LEARNER_EXAM_ACTION.WAIT_REVIEW) &&
    task.currentAttemptId
  ) {
    if (!canViewAttempt()) return;
    await router.push(`/portal/exams/${task.examId}/result/${task.currentAttemptId}`);
    return;
  }
  if (task.availableAction !== LEARNER_EXAM_ACTION.START) {
    ElMessage.warning('当前考试状态已变化，请刷新后重试。');
    await loadTasks();
    return;
  }
  await router.push({
    name: 'PortalExamNotice',
    params: { examId: task.examId, examCandidateId: task.examCandidateId }
  });
}

/** 线下考试只允许在管理端已录入成绩后查看结果，绝不创建或续答线上试卷。 */
function isOfflineExam(task: LearnerExamTask): boolean {
  return task.examType === 'OFFLINE';
}

/** Contract 建议动作的统一中文文案；线下考试未录分时没有建议动作。 */
function actionText(task: LearnerExamTask): string {
  if (!task.availableAction) return '待出成绩';
  if (task.availableAction === LEARNER_EXAM_ACTION.VIEW_RESULT && task.scorePublishStatus === 'PUBLISHED' && !task.scoreVisible)
    return '查看答题情况';
  return (
    {
      [LEARNER_EXAM_ACTION.START]: '开始考试',
      [LEARNER_EXAM_ACTION.CONTINUE]: '继续作答',
      [LEARNER_EXAM_ACTION.WAIT_REVIEW]: '查看答题情况',
      [LEARNER_EXAM_ACTION.VIEW_RESULT]: '查看结果'
    }[task.availableAction] ?? '查看任务'
  );
}

/** 抽屉中的开始操作先进入须知页，正式开考仍由须知页确认。 */
function drawerActionText(task: LearnerExamTask): string {
  return task.availableAction === LEARNER_EXAM_ACTION.START ? '查看考生须知' : actionText(task);
}

/** 独立考试任务根据所需按钮权限显示操作入口；线下考试仅在成绩可查看时开放结果页。 */
function canHandleTask(task: LearnerExamTask): boolean {
  if (isOfflineExam(task)) {
    return task.availableAction === LEARNER_EXAM_ACTION.VIEW_RESULT && Boolean(task.currentAttemptId) && canViewAttempt();
  }
  if (!task.availableAction) return false;
  return task.availableAction === LEARNER_EXAM_ACTION.START
    ? canStartExam()
    : ['CONTINUE', 'WAIT_REVIEW', 'VIEW_RESULT'].includes(task.availableAction) && Boolean(task.currentAttemptId)
      ? canViewAttempt()
      : false;
}

/** 成绩未发布时的状态说明，区分「线下待录入」与「线上待批阅发布」。 */
function pendingScoreText(task: LearnerExamTask): string {
  if (isOfflineExam(task)) return '等待管理端录入成绩';
  if (!task.availableAction) return '等待管理端录入成绩';
  return task.availableAction === LEARNER_EXAM_ACTION.WAIT_REVIEW ? '等待发布' : '未发布';
}

function getSourceText(task: LearnerExamTask): string {
  return task.sourceType === 'STANDALONE_PRACTICE' ? '专项练习' : '独立考试';
}

function formatDateTime(value: string | null): string {
  return value ? value.replace('T', ' ').slice(0, 16) : '未设置';
}

/** 卡片内的日期压缩为「月-日 时:分」，仅跨年时补全年份，避免一行塞进两个完整时间。 */
function compactDateTime(value: string | null): string {
  if (!value) return '';
  const [date, time = ''] = value.replace('T', ' ').split(' ');
  const [year, month, day] = date.split('-');
  const moment = time.slice(0, 5);
  const dayPart = Number(year) === new Date().getFullYear() ? `${month}-${day}` : date;
  return moment ? `${dayPart} ${moment}` : dayPart;
}

/** 开放区间收敛为一条可读文案；两端都未设置时直接说明不限时间，不再输出「未设置 至 未设置」。 */
function getOpenTimeText(task: LearnerExamTask): string {
  const start = compactDateTime(task.openStartTime);
  const end = compactDateTime(task.openEndTime);
  if (!start && !end) return '不限制开放时间';
  if (!start) return `截至 ${end}`;
  if (!end) return `${start} 起`;
  return `${start} ~ ${end}`;
}

/** 得分结论只保留三种语义强调色；未判定及格时使用中性色，不再沿用绿色。 */
function getOutcomeTone(task: LearnerExamTask): 'pass' | 'fail' | 'none' {
  if (task.isPassed === 1) return 'pass';
  if (task.isPassed === 0) return 'fail';
  return 'none';
}

/** 得分结论文案，未判定及格与未及格分开表达，避免误读为未通过。 */
function getOutcomeText(task: LearnerExamTask): string {
  if (task.isPassed === 1) return '已及格';
  if (task.isPassed === 0) return '未及格';
  return '不判定及格';
}

onMounted(() => void loadTasks());
onBeforeUnmount(clearExamCoverUrls);
</script>

<template>
  <section class="portal-list-page learner-exam-page">
    <header class="portal-list-page__heading">
      <div>
        <p class="portal-list-page__eyebrow">MY EXAMS</p>
        <h1>我的考试</h1>
        <p>查看本人考试任务，并在开放时间内完成作答。</p>
      </div>
      <div class="portal-heading__actions">
        <RouterLink class="portal-entry-link" to="/user-center/exams">
          <ElIcon><Tickets /></ElIcon>查看正式考试与所有课程随堂测试<ElIcon><ArrowRight /></ElIcon>
        </RouterLink>
        <ElButton :loading="loading" @click="loadTasks">刷新</ElButton>
      </div>
    </header>

    <nav class="portal-list-page__tabs exam-status-tabs" aria-label="考试任务状态筛选">
      <button
        v-for="tab in taskStatusTabs"
        :key="tab.value ?? 'ALL'"
        type="button"
        class="portal-list-page__tab exam-status-tabs__item"
        :class="{ 'portal-list-page__tab--active exam-status-tabs__item--active': query.taskStatus === tab.value }"
        @click="changeTaskStatus(tab.value)"
      >
        {{ tab.label }}
      </button>
    </nav>

    <ElAlert v-if="errorMessage" :title="errorMessage" type="error" show-icon :closable="false" class="error-alert" />
    <div
      v-loading="loading"
      :class="['portal-list-grid', 'exam-grid', { 'portal-list-grid--empty': !tasks.length, 'exam-grid-unEmpty': tasks.length }]"
    >
      <ElEmpty v-if="!loading && !tasks.length" description="暂无符合条件的考试任务" />
      <ElCard
        v-for="task in tasks"
        :key="task.examCandidateId"
        class="portal-list-card exam-card"
        shadow="never"
        role="button"
        tabindex="0"
        @click="openTaskDetail(task)"
        @keydown.enter="openTaskDetail(task)"
      >
        <div class="portal-list-card__cover">
          <img v-if="examCoverUrls[task.examId]" :src="examCoverUrls[task.examId]" :alt="`${task.examName}考试封面`" />
          <div v-else class="portal-list-card__cover-placeholder" aria-label="未设置考试封面">
            <ElIcon><Document /></ElIcon>
          </div>
          <span class="exam-card__badge">{{ getExamTypeLabel(task.examType) }}</span>
        </div>
        <div class="portal-list-card__content exam-card__content">
          <div class="portal-list-card__header exam-card__header">
            <div class="exam-card__identity">
              <h2>{{ task.examName }}</h2>
              <p>{{ getSourceText(task) }}</p>
            </div>
            <ElTag :type="getLearnerTaskTagType(task.taskStatus)" size="small">
              {{ getExamEnumLabel(task.taskStatus, LEARNER_TASK_STATUS_OPTIONS) }}
            </ElTag>
          </div>
          <dl class="exam-card__facts">
            <div>
              <dt>开放时间</dt>
              <dd>{{ getOpenTimeText(task) }}</dd>
            </div>
            <div>
              <dt>考试时长</dt>
              <dd>{{ formatDuration(task.durationSeconds) }}</dd>
            </div>
            <div>
              <dt>作答次数</dt>
              <dd>{{ task.attemptCount }} 次</dd>
            </div>
          </dl>
          <div
            v-if="task.scoreVisible"
            class="exam-card__outcome"
            :class="`exam-card__outcome--${getOutcomeTone(task)}`"
          >
            <span class="exam-card__outcome-label">{{ task.examType === 'BANK_PRACTICE' ? '练习得分' : '正式成绩' }}</span>
            <span class="exam-card__outcome-score">
              {{ task.finalScore ?? '-' }}<em>分</em>
            </span>
            <span class="exam-card__outcome-flag">{{ getOutcomeText(task) }}</span>
          </div>
        </div>
      </ElCard>
    </div>
    <ElPagination
      v-if="total > 0"
      v-model:current-page="query.pageNum"
      v-model:page-size="query.pageSize"
      class="portal-list-page__pagination exam-pagination"
      background
      layout="total, sizes, prev, pager, next"
      :page-sizes="LIST_PAGE_SIZES"
      :total="total"
      @current-change="loadTasks"
      @size-change="changePageSize"
    />

    <ElDrawer v-model="detailDrawerVisible" :title="activeTask?.examName ?? '考试详情'" size="520px" :lock-scroll="false">
      <div v-loading="detailLoading" class="exam-detail-drawer">
        <template v-if="activeTask">
          <div class="exam-detail-drawer__summary">
            <ElIcon><Document /></ElIcon>
            <div>
              <h2>{{ activeTask.examName }}</h2>
              <p>{{ getSourceText(activeTask) }} · {{ getExamTypeLabel(activeTask.examType) }}</p>
            </div>
          </div>
          <dl class="exam-detail-drawer__fields">
            <div>
              <dt>当前状态</dt>
              <dd>{{ getExamEnumLabel(activeTask.taskStatus, LEARNER_TASK_STATUS_OPTIONS) }}</dd>
            </div>
            <div>
              <dt>开放时间</dt>
              <dd>{{ getOpenTimeText(activeTask) }}</dd>
            </div>
            <div>
              <dt>考试时长</dt>
              <dd>{{ formatDuration(activeTask.durationSeconds) }}</dd>
            </div>
            <div>
              <dt>作答情况</dt>
              <dd>{{ getCandidateStatusLabel(activeTask.candidateStatus) }} · 已作答 {{ activeTask.attemptCount }} 次</dd>
            </div>
            <div v-if="activeTask.deadlineTime">
              <dt>当前作答截止</dt>
              <dd>{{ formatDateTime(activeTask.deadlineTime) }}</dd>
            </div>
                <div v-if="activeTask.scoreVisible">
                  <dt>{{ activeTask.examType === 'BANK_PRACTICE' ? '练习得分' : '正式成绩' }}</dt>
              <dd>{{ activeTask.finalScore ?? '-' }} 分 · {{ activeTask.isPassed == null ? '不判定及格' : activeTask.isPassed === 1 ? '已及格' : '未及格' }}</dd>
            </div>
            <div v-else-if="activeTask.scorePublishStatus === 'PUBLISHED'">
              <dt>成绩状态</dt>
              <dd>成绩已发布；本次考试设置不展示成绩</dd>
            </div>
            <div v-else>
              <dt>成绩状态</dt>
              <dd>{{ pendingScoreText(activeTask) }}</dd>
            </div>
            <div v-if="examDetail && examDetail.examType !== 'BANK_PRACTICE'">
              <dt>及格分数</dt>
              <dd>{{ examDetail.passScore ?? '未设置' }} 分</dd>
            </div>
          </dl>
          <section v-if="examDetail?.noticeContent" class="exam-detail-drawer__notice">
            <div>
              <ElIcon><Medal /></ElIcon>
              <h3>考试须知</h3>
            </div>
            <p style="white-space: pre-wrap">{{ examDetail.noticeContent }}</p>
          </section>
          <div class="exam-detail-drawer__actions">
            <ElButton v-if="canHandleTask(activeTask)" type="primary" @click="handleTask(activeTask)">
              {{ drawerActionText(activeTask) }}
            </ElButton>
            <ElTag v-else-if="isOfflineExam(activeTask) || !activeTask.availableAction" type="info">待出成绩</ElTag>
            <ElTag v-else-if="activeTask.availableAction === LEARNER_EXAM_ACTION.WAIT_REVIEW" type="info">等待成绩发布</ElTag>
            <span v-else class="permission-tip">暂无操作权限</span>
          </div>
        </template>
      </div>
    </ElDrawer>
  </section>
</template>

<style scoped lang="less">
.permission-tip {
  color: var(--color-text-secondary);
}
.error-alert {
  margin: -10px 0;
}
.exam-card {
  display: flex;
  flex-direction: column;
  cursor: pointer;
}
.exam-card:focus-visible {
  outline: 2px solid var(--color-primary);
  outline-offset: 2px;
}
/* 封面统一按比例裁切，避免不同尺寸封面被拉伸变形。 */
.exam-card .portal-list-card__cover img {
  object-fit: cover;
}
/* 空封面补上类型角标与居中图标，不再是一块只放小图标的浅蓝空板。 */
.exam-card .portal-list-card__cover-placeholder {
  background: linear-gradient(135deg, #eef4ff, #dbeafe);
}
.exam-card .portal-list-card__cover-placeholder .el-icon {
  display: grid;
  width: 46px;
  height: 46px;
  place-items: center;
  border-radius: 50%;
  background: rgb(255 255 255 / 82%);
  box-shadow: 0 2px 10px rgb(37 99 235 / 14%);
  font-size: 22px;
}
.exam-card__badge {
  position: absolute;
  top: 10px;
  left: 10px;
  padding: 2px 10px;
  border: 1px solid rgb(255 255 255 / 76%);
  border-radius: 999px;
  background: rgb(255 255 255 / 88%);
  box-shadow: 0 1px 4px rgb(15 23 42 / 12%);
  color: var(--color-primary);
  font-size: 12px;
  font-weight: 500;
  line-height: 20px;
}
.exam-card__content {
  gap: 14px;
}
.exam-card__header {
  align-items: flex-start;
  gap: 10px;
}
.exam-card__identity {
  min-width: 0;
}
.exam-card__header :deep(.el-tag) {
  flex: 0 0 auto;
  border: 0;
}
/* 关键事实按「标签列 + 取值列」对齐，替代原先两端对不齐的散装字段。 */
.exam-card__facts {
  display: grid;
  gap: 8px;
  margin: 0;
  padding: 12px 14px;
  border-radius: 10px;
  background: var(--color-bg-subtle);
}
.exam-card__facts div {
  display: grid;
  align-items: baseline;
  gap: 12px;
  grid-template-columns: 60px minmax(0, 1fr);
}
.exam-card__facts dt {
  color: var(--color-text-secondary);
  font-size: 12px;
  line-height: 20px;
}
.exam-card__facts dd {
  margin: 0;
  color: var(--color-text-regular);
  font-size: 13px;
  font-variant-numeric: tabular-nums;
  line-height: 20px;
  overflow-wrap: anywhere;
  white-space: nowrap;
}
/* 得分结论独立成条：分数右对齐突出，及格结论用柔和药丸底，不再使用原始 green/red 文字色。 */
.exam-card__outcome {
  display: flex;
  margin-top: auto;
  padding-top: 12px;
  align-items: baseline;
  gap: 10px;
  border-top: 1px dashed var(--color-border);
}
.exam-card__outcome-label {
  color: var(--color-text-secondary);
  font-size: 12px;
}
.exam-card__outcome-score {
  margin-left: auto;
  color: var(--color-text-primary);
  font-size: 18px;
  font-variant-numeric: tabular-nums;
  font-weight: 600;
  line-height: 24px;
}
.exam-card__outcome-score em {
  margin-left: 2px;
  color: var(--color-text-secondary);
  font-size: 12px;
  font-style: normal;
  font-weight: 400;
}
.exam-card__outcome-flag {
  padding: 2px 9px;
  border-radius: 999px;
  font-size: 12px;
  line-height: 18px;
}
.exam-card__outcome--pass .exam-card__outcome-flag {
  background: #dcfce7;
  color: #15803d;
}
.exam-card__outcome--fail .exam-card__outcome-flag {
  background: #fee2e2;
  color: var(--color-danger);
}
.exam-card__outcome--none .exam-card__outcome-flag {
  background: var(--color-bg-subtle);
  color: var(--color-text-secondary);
}
.exam-pagination {
  justify-content: flex-end;
  margin-top: -8px;
}
.exam-detail-drawer {
  display: flex;
  flex-direction: column;
  gap: 24px;
  min-height: 240px;
}
.exam-detail-drawer__summary {
  display: flex;
  align-items: center;
  gap: 12px;
  padding-bottom: 20px;
  border-bottom: 1px solid var(--color-border);
}
.exam-detail-drawer__summary > .el-icon {
  display: grid;
  width: 40px;
  height: 40px;
  place-items: center;
  border-radius: 8px;
  color: var(--color-primary);
  background: var(--color-primary-subtle);
  font-size: 20px;
}
.exam-detail-drawer__summary h2,
.exam-detail-drawer__summary p,
.exam-detail-drawer__notice h3,
.exam-detail-drawer__notice p {
  margin: 0;
}
.exam-detail-drawer__summary h2 {
  color: var(--color-text-primary);
  font-size: 18px;
  line-height: 26px;
}
.exam-detail-drawer__summary p,
.exam-detail-drawer__notice p {
  margin-top: 4px;
  color: var(--color-text-secondary);
  font-size: 13px;
  line-height: 1.7;
}
.exam-detail-drawer__fields {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 16px;
  margin: 0;
}
.exam-detail-drawer__fields div {
  min-width: 0;
}
.exam-detail-drawer__fields dt {
  margin-bottom: 6px;
  color: var(--color-text-secondary);
  font-size: 12px;
}
.exam-detail-drawer__fields dd {
  margin: 0;
  color: var(--color-text-regular);
  font-size: 14px;
  line-height: 1.6;
  word-break: break-word;
}
.exam-detail-drawer__notice {
  padding: 16px;
  border-radius: 8px;
  background: var(--color-bg-subtle);
}
.exam-detail-drawer__notice > div {
  display: flex;
  align-items: center;
  gap: 8px;
  color: var(--color-text-primary);
}
.exam-detail-drawer__notice .el-icon {
  color: var(--color-primary);
}
.exam-detail-drawer__notice h3 {
  font-size: 14px;
}
.exam-detail-drawer__actions {
  display: flex;
  align-items: center;
  gap: 12px;
  padding-top: 4px;
}
@media (max-width: 768px) {
  .exam-detail-drawer__fields {
    grid-template-columns: 1fr;
  }
}
</style>
