<script setup lang="ts">
import { onMounted, ref } from 'vue';
import { useRouter } from 'vue-router';
import { ElMessage } from 'element-plus';
import type { PersonalAssessmentAction, PersonalAssessmentHistory, PersonalAssessmentTask } from '@shared/types';
import { getPersonalAssessments, getPersonalAttemptHistory, startPersonalCourseAttempt } from '@/api/learner-center';
import { getExamTypeLabel } from '@/constants/exam-options';
import { DEFAULT_LIST_PAGE_SIZE, LIST_PAGE_SIZES } from '@/constants/pagination';
import { LEARNER_CENTER_ROUTES, PERSONAL_ASSESSMENT_STATUSES } from '@/constants/learner-center';

/** 两类考核统一展示，跳转保留正式考试与课程报名领域的差异。 */
const router = useRouter(); const rows = ref<PersonalAssessmentTask[]>([]); const page = ref(1); const total = ref(0);
const status = ref('ALL'); const keyword = ref(''); const error = ref(''); const busy = ref(false);
const history = ref<PersonalAssessmentHistory[]>([]); const historyTask = ref<PersonalAssessmentTask>();
const statuses = PERSONAL_ASSESSMENT_STATUSES;
/** 主入口文案直接消费服务端动作，避免前端二次推断出与真实能力不符的入口。 */
const ACTION_LABELS: Record<PersonalAssessmentAction, string> = { START: '进入考核', CONTINUE: '继续作答', WAIT_REVIEW: '查看答题情况', VIEW_RESULT: '查看成绩' };
/** 状态中文取自筛选常量，避免卡片与页签两处文案漂移。 */
function statusLabel(value: PersonalAssessmentTask['status']): string { return statuses.find((s) => s.value === value)?.label ?? value; }
/** 卡片状态配色：未完成待办用主色、进行中用警示色、已结束中性。 */
function statusTone(value: PersonalAssessmentTask['status']): 'primary' | 'warning' | 'info' {
  if (value === 'IN_PROGRESS') return 'warning';
  return value === 'UNFINISHED' ? 'primary' : 'info';
}
/** 无入口时与考试中心同口径显示「待出成绩」：含线下待录分与线上待批阅。 */
function actionLabel(task: PersonalAssessmentTask): string { return task.action ? ACTION_LABELS[task.action] : '待出成绩'; }
/** 状态说明：把「未完成/进行中/已结束」的差别说清楚，同时解释按钮为何可用或不可用。 */
function statusHint(task: PersonalAssessmentTask): string {
  if (task.examType === 'OFFLINE') return task.available ? '线下考试，成绩已录入，可查看结果' : '线下考试，等待管理方录入成绩';
  if (task.status === 'IN_PROGRESS') return '作答中，可继续作答';
  if (task.status === 'UNFINISHED') return '尚未开始作答，请在开放时间内进入';
  if (task.action === 'VIEW_RESULT') return '考核已结束，可查看成绩';
  if (task.action === 'WAIT_REVIEW') return '考核已结束，可查看答题情况';
  return '考核已结束，等待成绩发布';
}
/** 课程随堂测试的第二枚标签是所属课程，与考试类型的元信息位置保持一致。 */
function secondaryTag(task: PersonalAssessmentTask): string | null {
  return task.kind === 'COURSE_QUIZ' ? task.sourceName : task.examType ? getExamTypeLabel(task.examType) : null;
}
/** 按当前筛选加载本人真实数据，失败时保留提示。 */
async function load(): Promise<void> { busy.value = true; error.value = ''; try { const result = await getPersonalAssessments({ pageNum: page.value, pageSize: DEFAULT_LIST_PAGE_SIZE, 'query.status': status.value, 'query.keyword': keyword.value }); rows.value = result.list; total.value = result.total; } catch (e) { error.value = e instanceof Error ? e.message : '加载失败'; } finally { busy.value = false; } }
/** 切换状态页签后回到第一页重新查询。 */
function changeStatus(value: string): void { status.value = value; page.value = 1; void load(); }
/** 进入受控业务详情或作答页面；历史考卷用 resultId 直达结果。 */
async function open(task: PersonalAssessmentTask, resultId?: number): Promise<void> {
  try {
    // 线下考试的成绩由管理端录入，未录分前任何入口都不成立，这里兜住按钮之外的程序化调用。
    if (resultId === undefined && !task.available) {
      ElMessage.info(task.examType === 'OFFLINE' ? '线下考试成绩由考试管理方录入，录入后可查看结果。' : '当前暂无可进入的入口。');
      return;
    }
    if (task.kind === 'FORMAL_EXAM') {
      const viewable = task.action === 'WAIT_REVIEW' || task.action === 'VIEW_RESULT';
      const attemptId = resultId ?? (viewable ? task.attemptId ?? undefined : undefined);
      await router.push(attemptId ? `/portal/exams/${task.examId}/result/${attemptId}` : `/portal/exams/${task.examId}/notice/${task.candidateId}`);
      return;
    }
    const attemptId = resultId ?? task.attemptId ?? await startPersonalCourseAttempt(task.enrollmentId!, task.itemId!);
    const viewing = resultId !== undefined || task.action === 'VIEW_RESULT' || task.action === 'WAIT_REVIEW';
    await router.push({ name: viewing ? 'PortalCourseAssessmentResult' : 'PortalCourseAssessmentAttempt', params: { enrollmentId: task.enrollmentId!, itemId: task.itemId!, attemptId }, query: { itemTitle: task.title } });
  } catch (e) { ElMessage.error(e instanceof Error ? e.message : '进入失败'); }
}
/** 读取本人允许回看的历史考卷。 */
async function showHistory(task: PersonalAssessmentTask): Promise<void> { try { history.value = await getPersonalAttemptHistory(task.kind === 'FORMAL_EXAM' ? { candidateId: task.candidateId } : { enrollmentId: task.enrollmentId, itemId: task.itemId }); historyTask.value = task; } catch (e) { ElMessage.error(e instanceof Error ? e.message : '读取失败'); } }
onMounted(load);
</script>

<template>
  <section v-loading="busy" class="portal-list-page">
    <header class="portal-list-page__heading">
      <div>
        <p class="portal-list-page__eyebrow">MY EXAMS</p>
        <h1>我的考试</h1>
        <p>正式考试与课程随堂测试；理论模拟在我的练习中独立作答。</p>
      </div>
      <ElButton class="portal-heading__button" @click="router.push(LEARNER_CENTER_ROUTES.practices)">进入模拟考试</ElButton>
    </header>
    <nav class="portal-list-page__tabs" aria-label="考试状态筛选">
      <button v-for="s in statuses" :key="s.value" type="button" class="portal-list-page__tab" :class="{ 'portal-list-page__tab--active': status === s.value }" @click="changeStatus(s.value)">{{ s.label }}</button>
    </nav>
    <div class="portal-toolbar">
      <ElInput v-model="keyword" placeholder="请输入考试或课程名称" clearable @keyup.enter="page = 1; load()" />
      <ElButton type="primary" @click="page = 1; load()">查询</ElButton>
    </div>
    <ElAlert v-if="error" type="error" :title="error" :closable="false" />
    <section v-if="!busy && !error && !total" class="portal-list-state">
      <ElEmpty description="暂无考核任务" />
    </section>
    <div v-else class="portal-list-grid">
      <ElCard v-for="t in rows" :key="t.key" class="portal-list-card exam-card" :class="`exam-card--${statusTone(t.status)}`" shadow="never">
        <div class="portal-list-card__content exam-card__content">
          <div class="portal-list-card__header">
            <h3>{{ t.title }}</h3>
            <span class="exam-card__status" :class="`exam-card__status--${statusTone(t.status)}`">{{ statusLabel(t.status) }}</span>
          </div>
          <div class="portal-list-card__tags">
            <span class="portal-chip">{{ t.kind === 'COURSE_QUIZ' ? '课程随堂测试' : '正式考试' }}</span>
            <span v-if="secondaryTag(t)" class="portal-chip portal-chip--plain">{{ secondaryTag(t) }}</span>
          </div>
          <p class="exam-card__hint">{{ statusHint(t) }}</p>
          <div class="portal-list-card__actions portal-list-card__actions--split">
            <span class="portal-list-card__actions-group">
              <ElButton link @click="showHistory(t)">历史考卷</ElButton>
            </span>
            <ElButton type="primary" :disabled="!t.available" @click="open(t)">{{ actionLabel(t) }}</ElButton>
          </div>
        </div>
      </ElCard>
    </div>
    <div class="portal-list-page__pagination">
      <ElPagination v-model:current-page="page" :page-size="DEFAULT_LIST_PAGE_SIZE" :page-sizes="LIST_PAGE_SIZES" layout="total, sizes, prev, pager, next" :total="total" @current-change="load" />
    </div>
    <ElDrawer :model-value="Boolean(historyTask)" title="已完成考卷" size="420px" @close="historyTask = undefined">
      <ElEmpty v-if="!history.length" description="暂无允许查看的历史考卷" />
      <p v-for="h in history" :key="h.attemptId" class="history-row">
        <span>{{ h.submittedTime.replace('T', ' ') }}</span>
        <ElButton link type="primary" @click="open(historyTask!, h.attemptId)">查看考卷</ElButton>
      </p>
    </ElDrawer>
  </section>
</template>

<style scoped lang="less">
.portal-heading__button {
  flex: none;
}

.portal-toolbar {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: var(--space-3);

  .el-input {
    width: 260px;
  }
}

.exam-card {
  position: relative;
}

/* 状态色带：让「未完成/进行中/已结束」在卡片扫视时先于文字被读到，色相与状态标签保持一致。 */
.exam-card::before {
  position: absolute;
  top: 0;
  right: 0;
  left: 0;
  height: 3px;
  background: var(--color-border);
  content: '';
}

.exam-card--primary::before {
  background: var(--color-primary);
}

.exam-card--warning::before {
  background: #e6a23c;
}

.exam-card--info::before {
  background: #909399;
}

.exam-card__content {
  min-height: 150px;
  gap: var(--space-3);
}

/* 状态胶囊：未完成/进行中/已结束各用一档色相 + 色点，扫视时先于文字被读到。 */
.exam-card__status {
  display: inline-flex;
  height: 28px;
  flex: none;
  align-items: center;
  gap: 6px;
  padding: 0 12px;
  border-radius: 999px;
  font-size: 14px;
  font-weight: 600;
}

.exam-card__status::before {
  width: 6px;
  height: 6px;
  border-radius: 50%;
  background: currentcolor;
  content: '';
}

.exam-card__status--primary {
  background: var(--color-primary-subtle);
  color: var(--color-primary);
}

.exam-card__status--warning {
  background: #fdf6ec;
  color: #b7791f;
}

.exam-card__status--info {
  background: var(--color-bg-subtle);
  color: var(--color-text-secondary);
}

.exam-card__hint {
  margin: 0;
  color: var(--color-text-secondary);
  font-size: 13px;
  line-height: 20px;
}

.history-row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: var(--space-3);
  margin: 0 0 var(--space-2);
  color: var(--color-text-regular);
}
</style>
