<script setup lang="ts">
import { onLoad, onShow } from '@dcloudio/uni-app';
import { ref } from 'vue';
import type { PracticeMode, PracticeTask, PracticeQuestion, PracticeSession } from '@shared/types';
import { practiceStandardAnswer, practiceRecordTime, practiceRecordDuration, practiceSourceLabel } from '@shared/utils/learner-practice';
import { getPracticeTasks, getWrongQuestions, getPracticeSessions, startPractice, setWrongDisposition } from '@/api/learner-practice';
import { LEARNER_CENTER_ROUTES, LEARNER_LIST_PAGE_SIZE } from '@/constants/learner-center';
import MobileBaseLayout from '@/layouts/MobileBaseLayout.vue';

/** 移动端练习与错题入口，以触控卡片呈现本人资源。 */
const wrongView = ref(false); const keyword = ref(''); const mode = ref<PracticeMode>('PRACTICE');
const tasks = ref<PracticeTask[]>([]); const wrongs = ref<PracticeQuestion[]>([]); const sessions = ref<PracticeSession[]>([]);
/** 本人练习/模拟历史独立分页。 */
const sessionPage = ref(1); const sessionTotal = ref(0);
const pageNum = ref(1); const pageSize = LEARNER_LIST_PAGE_SIZE; const total = ref(0); const busy = ref(false); const error = ref(''); const expanded = ref<number>();
/** 按当前筛选加载本人真实数据，失败时保留提示。 */
async function load(): Promise<void> {
  busy.value = true; error.value = '';
  try {
    const params = { pageNum: pageNum.value, pageSize, 'query.keyword': keyword.value };
    if (wrongView.value) { const page = await getWrongQuestions(params); wrongs.value = page.list; total.value = page.total; }
    else { const page = await getPracticeTasks(params); tasks.value = page.list; total.value = page.total; const history = await getPracticeSessions({ pageNum: sessionPage.value, pageSize }); sessions.value = history.list; sessionTotal.value = history.total; }
  } catch (e) { error.value = e instanceof Error ? e.message : '加载失败'; } finally { busy.value = false; }
}
/** 校验当前操作状态后启动独立练习会话。 */
async function start(candidateId?: number, questionId?: number): Promise<void> {
  if (busy.value) return; busy.value = true;
  try {
    const value = await startPractice({ candidateId, questionId, mode: mode.value, clientToken: (globalThis.crypto?.randomUUID?.() ?? `${Date.now()}-${Math.random()}`) });
    uni.navigateTo({ url: `${LEARNER_CENTER_ROUTES.attempt}?id=${value.sessionId}` });
  } catch (e) { uni.showToast({ title: e instanceof Error ? e.message : '启动失败', icon: 'none' }); } finally { busy.value = false; }
}
/** 提交错题处理状态后刷新列表。 */
async function handleWrong(id: number, disposition: 'DELETED' | 'MASTERED'): Promise<void> {
  try { await setWrongDisposition(id, disposition); await load(); }
  catch (e) { uni.showToast({ title: e instanceof Error ? e.message : '操作失败', icon: 'none' }); }
}
/** 进入本人已有练习或模拟记录。 */
function resume(id: number): void { uni.navigateTo({ url: `${LEARNER_CENTER_ROUTES.attempt}?id=${id}` }); }
onLoad(query => { wrongView.value = query?.tab === 'wrongs'; });
onShow(load);
</script>
<template>
  <MobileBaseLayout>
    <view class="learner-page">
      <view class="learner-hero">
        <view class="learner-hero__deco" />
        <text class="learner-hero__eyebrow">{{ wrongView ? 'WRONG QUESTIONS' : 'MY PRACTICE' }}</text>
        <text class="learner-hero__title">{{ wrongView ? '我的错题集' : '我的练习' }}</text>
        <text class="learner-hero__subtitle">{{ wrongView ? '只展示允许查看的正式考试和课程错题' : '练习与模拟不影响正式成绩和学时' }}</text>
      </view>
      <view class="learner-toolbar">
        <input v-model="keyword" class="learner-input" placeholder="请输入名称或题干" @confirm="pageNum = 1; load()">
        <button class="learner-button learner-button--primary" @click="pageNum = 1; load()">查询</button>
      </view>
      <view class="segmented-filter">
        <text class="segmented-filter__item" :class="{ 'segmented-filter__item--active': mode === 'PRACTICE' }" @click="mode = 'PRACTICE'">练习模式</text>
        <text class="segmented-filter__item" :class="{ 'segmented-filter__item--active': mode === 'SIMULATION' }" @click="mode = 'SIMULATION'">模拟模式</text>
      </view>
      <text v-if="error" class="learner-error">{{ error }}</text>
      <template v-if="wrongView">
        <view v-for="q in wrongs" :key="q.questionId" class="learner-card">
          <text class="learner-meta">{{ q.sourceType === 'COURSE_QUIZ' ? '课程测试' : '正式考试' }} · {{ q.sourceName }}</text>
          <text class="learner-card__text">{{ q.stem }}</text>
          <view class="learner-actions learner-actions--split">
            <view class="learner-actions__group">
              <text class="learner-link" @click="expanded = expanded === q.questionId ? undefined : q.questionId">{{ expanded === q.questionId ? '收起解析' : '查看解析' }}</text>
              <text class="learner-link" @click="handleWrong(q.questionId, 'MASTERED')">已掌握</text>
              <text class="learner-link learner-link--danger" @click="handleWrong(q.questionId, 'DELETED')">删除</text>
            </view>
            <button class="learner-button learner-button--primary" :disabled="busy" @click="start(undefined, q.questionId)">再练</button>
          </view>
          <view v-if="expanded === q.questionId" class="learner-answer">
            <text>标准答案：{{ practiceStandardAnswer(q.correctAnswerJson) }}</text>
            <text>解析：{{ q.analysis || '暂无可查看解析' }}</text>
          </view>
        </view>
      </template>
      <template v-else>
        <view v-for="t in tasks" :key="t.candidateId" class="learner-card">
          <text class="learner-title">{{ t.title }}</text>
          <text class="learner-meta">{{ t.kind === 'BANK_PRACTICE' ? '题库练习' : '专项训练' }}</text>
          <button class="learner-button learner-button--primary" :disabled="busy" @click="start(t.candidateId)">开始作答</button>
        </view>
      </template>
      <view v-if="!busy && !error && total === 0" class="learner-state">暂无记录</view>
      <view class="learner-toolbar learner-pager">
        <button class="learner-button" :disabled="pageNum <= 1 || busy" @click="pageNum--; load()">上一页</button>
        <text class="learner-meta">{{ pageNum }} / {{ Math.max(1, Math.ceil(total / pageSize)) }}</text>
        <button class="learner-button" :disabled="pageNum * pageSize >= total || busy" @click="pageNum++; load()">下一页</button>
      </view>
      <template v-if="!wrongView && sessions.length">
        <text class="learner-title">练习与模拟记录</text>
        <view v-for="s in sessions" :key="s.sessionId" class="learner-card" @click="resume(s.sessionId)">
          <text class="learner-card__text">{{ s.title }} · {{ s.mode === 'PRACTICE' ? '练习' : '模拟' }}</text>
          <text v-if="s.questionStem" class="learner-meta">题目：{{ s.questionStem }}</text>
          <text class="learner-meta">来源：{{ practiceSourceLabel(s.sourceType) }} · {{ s.sourceName || '原来源未记录' }}</text>
          <text class="learner-meta">开始：{{ practiceRecordTime(s.startedTime) }}</text>
          <text v-if="s.submittedTime" class="learner-meta">提交：{{ practiceRecordTime(s.submittedTime) }}</text>
          <text class="learner-meta">已答 {{ s.answeredCount }} / {{ s.totalCount }} 题 · 累计用时 {{ practiceRecordDuration(s.usedSeconds) }}</text>
          <text v-if="s.status === 'SUBMITTED'" class="learner-meta">正确 {{ s.correctCount }} · 错误 {{ s.wrongCount }} · 未判定 {{ s.unassessedCount }} · 正确率 {{ s.accuracyRate === null ? '暂无' : `${s.accuracyRate.toFixed(1)}%` }}</text>
          <text class="learner-meta">{{ s.status === 'ANSWERING' ? '继续作答' : '查看结果' }}</text>
        </view>
        <view class="learner-toolbar learner-pager">
          <button class="learner-button" :disabled="sessionPage <= 1 || busy" @click="sessionPage--; load()">上一页记录</button>
          <text class="learner-meta">{{ sessionPage }}</text>
          <button class="learner-button" :disabled="sessionPage * pageSize >= sessionTotal || busy" @click="sessionPage++; load()">下一页记录</button>
        </view>
      </template>
    </view>
  </MobileBaseLayout>
</template>
<style scoped lang="less">
.learner-pager {
  justify-content: center;
}

.learner-answer {
  padding: 20rpx;
  border-radius: 16rpx;
  background: var(--color-bg-subtle, #f9fafb);

  text {
    display: block;
    color: var(--color-text-regular, #374151);
    font-size: 24rpx;
    line-height: 1.6;
    white-space: pre-wrap;
  }
}
</style>
