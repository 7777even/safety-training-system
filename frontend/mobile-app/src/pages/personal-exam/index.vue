<script setup lang="ts">
import { onShow } from '@dcloudio/uni-app';
import { ref } from 'vue';
import type { PersonalAssessmentAction, PersonalAssessmentTask, PersonalAssessmentHistory } from '@shared/types';
import { getExamTypeText } from '@shared/utils/exam';
import { getPersonalAssessments, getPersonalAttemptHistory, startPersonalCourseAttempt } from '@/api/learner-center';
import { LEARNER_CENTER_ROUTES, PERSONAL_ASSESSMENT_STATUSES, LEARNER_LIST_PAGE_SIZE } from '@/constants/learner-center';
import MobileBaseLayout from '@/layouts/MobileBaseLayout.vue';

/** 两类本人考核按移动任务卡片展示，保持原领域作答页面。 */
const rows = ref<PersonalAssessmentTask[]>([]); const page = ref(1); const total = ref(0); const pageSize = LEARNER_LIST_PAGE_SIZE;
const status = ref('ALL'); const error = ref(''); const busy = ref(false); const keyword = ref('');
const history = ref<PersonalAssessmentHistory[]>([]); const historyTask = ref<PersonalAssessmentTask>();
const statuses = PERSONAL_ASSESSMENT_STATUSES;
/** 主入口文案直接消费服务端动作，避免前端二次推断出与真实能力不符的入口。 */
const ACTION_LABELS: Record<PersonalAssessmentAction, string> = { START: '进入考核', CONTINUE: '继续作答', WAIT_REVIEW: '查看答题情况', VIEW_RESULT: '查看成绩' };
/** 状态中文取自筛选常量，避免卡片与页签两处文案漂移。 */
function statusLabel(value: PersonalAssessmentTask['status']): string { return statuses.find((s) => s.value === value)?.label ?? value; }
/** 状态配色：未完成待办用主色、进行中用警示色、已结束中性。 */
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
  return task.kind === 'COURSE_QUIZ' ? task.sourceName : task.examType ? getExamTypeText(task.examType) : null;
}
/** 按当前筛选加载本人真实数据，失败时保留提示。 */
async function load(): Promise<void> { busy.value = true; error.value = ''; try { const result = await getPersonalAssessments({ pageNum: page.value, pageSize, 'query.status': status.value, 'query.keyword': keyword.value }); rows.value = result.list; total.value = result.total; } catch (e) { error.value = e instanceof Error ? e.message : '加载失败'; } finally { busy.value = false; } }
/** 进入受控业务详情或作答页面；历史考卷用 resultId 直达结果。 */
async function open(task: PersonalAssessmentTask, resultId?: number): Promise<void> {
  try {
    // 线下考试的成绩由管理端录入，未录分前任何入口都不成立，这里兜住按钮之外的程序化调用。
    if (resultId === undefined && !task.available) {
      uni.showToast({ title: task.examType === 'OFFLINE' ? '线下考试成绩由考试管理方录入，录入后可查看结果。' : '当前暂无可进入的入口。', icon: 'none' });
      return;
    }
    if (task.kind === 'FORMAL_EXAM') {
      const viewable = task.action === 'WAIT_REVIEW' || task.action === 'VIEW_RESULT';
      const attemptId = resultId ?? (viewable ? task.attemptId ?? undefined : undefined);
      uni.navigateTo({ url: attemptId ? `/pages/exam/result?examId=${task.examId}&examAttemptId=${attemptId}` : `/pages/exam/notice?examId=${task.examId}&examCandidateId=${task.candidateId}` });
      return;
    }
    const attemptId = resultId ?? task.attemptId ?? await startPersonalCourseAttempt(task.enrollmentId!, task.itemId!);
    const viewing = resultId !== undefined || task.action === 'VIEW_RESULT' || task.action === 'WAIT_REVIEW';
    uni.navigateTo({ url: `/pages/course/${viewing ? 'assessment-result' : 'assessment-attempt'}?enrollmentId=${task.enrollmentId}&itemId=${task.itemId}&attemptId=${attemptId}&itemTitle=${encodeURIComponent(task.title)}` });
  } catch (e) { uni.showToast({ title: e instanceof Error ? e.message : '进入失败', icon: 'none' }); }
}
/** 读取本人允许回看的历史考卷。 */
async function showHistory(task: PersonalAssessmentTask): Promise<void> { try { history.value = await getPersonalAttemptHistory(task.kind === 'FORMAL_EXAM' ? { candidateId: task.candidateId } : { enrollmentId: task.enrollmentId, itemId: task.itemId }); historyTask.value = task; } catch (e) { uni.showToast({ title: e instanceof Error ? e.message : '加载失败', icon: 'none' }); } }
/** 进入独立理论模拟入口。 */
function simulate(): void { uni.navigateTo({ url: LEARNER_CENTER_ROUTES.practices }); }
onShow(load);
</script>
<template>
  <MobileBaseLayout>
    <view class="learner-page">
      <view class="learner-hero">
        <view class="learner-hero__deco" />
        <text class="learner-hero__eyebrow">MY EXAMS</text>
        <text class="learner-hero__title">考试与随堂测试</text>
        <text class="learner-hero__subtitle">正式考试与课程随堂测试统一入口</text>
      </view>
      <view class="learner-toolbar">
        <button class="learner-button learner-button--primary" @click="simulate">进入模拟考试</button>
      </view>
      <view class="segmented-filter">
        <text v-for="s in statuses" :key="s.value" class="segmented-filter__item" :class="{ 'segmented-filter__item--active': status === s.value }" @click="status = s.value; page = 1; load()">{{ s.label }}</text>
      </view>
      <view class="learner-toolbar">
        <input v-model="keyword" class="learner-input" placeholder="请输入考试或课程名称" @confirm="page = 1; load()">
        <button class="learner-button learner-button--primary" @click="page = 1; load()">查询</button>
      </view>
      <text v-if="error" class="learner-error">{{ error }}</text>
      <view v-for="t in rows" :key="t.key" class="learner-card exam-card" :class="`exam-card--${statusTone(t.status)}`">
        <view class="exam-card__head">
          <text class="learner-title exam-card__title">{{ t.title }}</text>
          <text class="exam-status" :class="`exam-status--${statusTone(t.status)}`">{{ statusLabel(t.status) }}</text>
        </view>
        <view class="exam-tags">
          <text class="exam-tag">{{ t.kind === 'COURSE_QUIZ' ? '课程随堂测试' : '正式考试' }}</text>
          <text v-if="secondaryTag(t)" class="exam-tag exam-tag--plain">{{ secondaryTag(t) }}</text>
        </view>
        <text class="exam-hint">{{ statusHint(t) }}</text>
        <view class="learner-actions learner-actions--split">
          <view class="learner-actions__group">
            <button class="learner-link" @click="showHistory(t)">历史考卷</button>
          </view>
          <button class="learner-button learner-button--primary" :disabled="!t.available || busy" @click="open(t)">{{ actionLabel(t) }}</button>
        </view>
      </view>
      <view v-if="!busy && !error && !total" class="learner-state">暂无考核任务</view>
      <view class="learner-toolbar learner-pager">
        <button class="learner-button" :disabled="page <= 1 || busy" @click="page--; load()">上一页</button>
        <text class="learner-meta">{{ page }}</text>
        <button class="learner-button" :disabled="page * pageSize >= total || busy" @click="page++; load()">下一页</button>
      </view>
      <view v-if="historyTask" class="learner-card">
        <text class="learner-title">历史考卷</text>
        <text v-if="!history.length" class="learner-meta">暂无允许查看的历史考卷</text>
        <button v-for="h in history" :key="h.attemptId" class="learner-button" @click="open(historyTask!, h.attemptId)">{{ h.submittedTime.replace('T', ' ') }} 查看考卷</button>
        <button class="learner-button" @click="historyTask = undefined">关闭</button>
      </view>
    </view>
  </MobileBaseLayout>
</template>
<style scoped lang="less">
.learner-pager {
  justify-content: center;
}

/* 状态色带：让「未完成/进行中/已结束」在扫视卡片时先于文字被读到。 */
.exam-card {
  position: relative;
  overflow: hidden;
}

.exam-card::before {
  position: absolute;
  top: 0;
  left: 0;
  width: 8rpx;
  height: 100%;
  background: #909399;
  content: '';
}

.exam-card--primary::before {
  background: #2563eb;
}

.exam-card--warning::before {
  background: #e6a23c;
}

.exam-card--info::before {
  background: #909399;
}

.exam-card__head {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 16rpx;
}

.exam-card__title {
  flex: 1;
  min-width: 0;
}

.exam-status {
  display: flex;
  flex: none;
  padding: 4rpx 20rpx;
  align-items: center;
  gap: 10rpx;
  border-radius: 999rpx;
  background: var(--color-bg-subtle, #f9fafb);
  color: var(--color-text-secondary, #64748b);
  font-size: 26rpx;
  font-weight: 600;
  line-height: 1.9;
}

/* 状态色点：与文字同色，强化「未完成/进行中/已结束」的区分。 */
.exam-status::before {
  width: 12rpx;
  height: 12rpx;
  border-radius: 50%;
  background: currentcolor;
  content: '';
}

.exam-status--primary {
  background: var(--color-primary-subtle, #eff6ff);
  color: var(--color-primary, #2563eb);
}

.exam-status--warning {
  background: #fdf6ec;
  color: #b7791f;
}

.exam-hint {
  display: block;
  color: var(--color-text-secondary, #64748b);
  font-size: 24rpx;
  line-height: 1.6;
}

.exam-tags {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 12rpx;
}

.exam-tag {
  padding: 2rpx 18rpx;
  border-radius: 999rpx;
  background: var(--color-primary-subtle, #eff6ff);
  color: var(--color-primary, #2563eb);
  font-size: 22rpx;
  font-weight: 500;
  line-height: 2;
}

/* 考试类型是中性元信息，与作为类别的蓝色标签拉开层次。 */
.exam-tag--plain {
  background: var(--color-bg-subtle, #f9fafb);
  color: var(--color-text-regular, #374151);
}
</style>
