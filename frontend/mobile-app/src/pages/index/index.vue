<script setup lang="ts">
import MobileBaseLayout from '@/layouts/MobileBaseLayout.vue';
import { onShow } from '@dcloudio/uni-app';
import { ref, watch } from 'vue';
import type { LearnerHomeTask } from '@shared/types';
import { LEARNER_KIND_LABELS, learnerStatusLabel } from '@shared/utils/learner-home';
import { getLearnerHome, getLearnerTarget } from '@/api/learner-home';
import { useLearnerMessageStore } from '@/stores/modules/learner-messages';
import { LEARNER_LIST_PAGE_SIZE } from '@/constants/learner-center';
import { useUserStore } from '@/stores';

/** 移动首页以本人任务卡片呈现，保持原课程、考试与培训链路。 */
const user = useUserStore(); const messages = useLearnerMessageStore();
/** 慢查询期间的安排消息合并补读，首页不会停留在旧任务。 */
let refreshPending = false;
const rows = ref<LearnerHomeTask[]>([]), page = ref(1), total = ref(0), kind = ref('ALL'), busy = ref(false), error = ref('');
const kinds = [{ value: 'ALL', label: '全部' }, { value: 'COURSE', label: '课程' }, { value: 'FORMAL_EXAM', label: '考试' }, { value: 'COURSE_QUIZ', label: '测试' }, { value: 'TRAINING', label: '培训' }];
/** 状态标签只做展示着色，不据此推断访问权限。 */
function statusBadgeClass(status: string): string {
  if (status === 'COMPLETED') { return 'learner-badge--success'; }
  if (status === 'UNFINISHED') { return 'learner-badge--warning'; }
  return ['ENDED', 'EXPIRED', 'WITHDRAWN', 'CLEARED'].includes(status) ? 'learner-badge--plain' : '';
}
/** 类型与截止时间合并成一行，卡片只保留「标题 / 状态 / 类型与截止 / 主操作」四段。 */
function taskMeta(task: LearnerHomeTask): string {
  return task.endTime ? `${LEARNER_KIND_LABELS[task.kind]} · 截止 ${task.endTime.replace('T', ' ')}` : LEARNER_KIND_LABELS[task.kind];
}
/** 主操作文案由服务端下发的动作决定，不在前端推断可进入性。 */
function actionLabel(action: string): string {
  return action === 'VIEW_RESULT' ? '查看结果' : action === 'CONTINUE' ? '继续任务' : '进入任务';
}
/** 前台恢复读取本人真实任务；未登录只进入登录页。 */
async function load(): Promise<void> {
  if (!user.token) { uni.reLaunch({ url: '/pages/auth/LoginPage' }); return; }
  if (busy.value) { refreshPending = true; return; }
  busy.value = true; error.value = '';
  try { const result = await getLearnerHome({ pageNum: page.value, pageSize: LEARNER_LIST_PAGE_SIZE, 'query.kind': kind.value }); rows.value = result.list; total.value = result.total; }
  catch (e) { error.value = e instanceof Error ? e.message : '学习任务加载失败'; }
  finally { busy.value = false; if (refreshPending) { refreshPending = false; void load(); } }
}
/** 打开之前重新校验当前动作，失效的通知不会创造报名。 */
async function open(task: LearnerHomeTask): Promise<void> {
  try { uni.navigateTo({ url: await getLearnerTarget(task) }); }
  catch (e) { uni.showToast({ title: e instanceof Error ? e.message : '当前任务已失效或无权访问', icon: 'none' }); }
}
/** 消息中心使用普通页面压栈，返回时仍在首页。 */
function openMessages(): void { uni.navigateTo({ url: '/pages/messages/index' }); }
watch(() => messages.revision, load);
onShow(load);
</script>

<template>
  <MobileBaseLayout><view class="learner-page">
    <view class="learner-hero">
      <view class="learner-hero__deco" />
      <text class="learner-hero__eyebrow">MY LEARNING</text>
      <text class="learner-hero__title">我的学习任务</text>
      <text class="learner-hero__subtitle">已分配的课程、考试与培训</text>
      <view class="hero-actions">
        <view class="hero-action" @click="openMessages">消息中心<text v-if="messages.total">（{{ messages.total }}）</text> ›</view>
        <view class="hero-action" @click="!busy && load()">刷新任务</view>
      </view>
    </view>

    <view class="segmented-filter home-filter">
      <text
        v-for="item in kinds"
        :key="item.value"
        class="segmented-filter__item"
        :class="{ 'segmented-filter__item--active': kind === item.value }"
        @click="kind = item.value; page = 1; load()"
      >{{ item.label }}</text>
    </view>

    <text v-if="error" class="learner-error">{{ error }}</text>
    <view v-if="!busy && !error && !rows.length" class="learner-state">暂无已分配的学习任务</view>

    <view v-for="task in rows" :key="task.key" class="learner-card home-task">
      <view class="learner-card__head">
        <text class="learner-title">{{ task.title }}</text>
        <text class="learner-badge" :class="statusBadgeClass(task.status)">{{ learnerStatusLabel(task.status) }}</text>
      </view>
      <text class="learner-meta">{{ taskMeta(task) }}</text>
      <view v-if="task.action" class="learner-actions">
        <button class="learner-button learner-button--primary" @click="open(task)">{{ actionLabel(task.action) }}</button>
      </view>
      <text v-else class="learner-meta">当前尚无可进入的入口，请等待安排或成绩发布。</text>
    </view>

    <view v-if="total" class="learner-actions">
      <button class="learner-button" :disabled="page <= 1 || busy" @click="page--; load()">上一页</button>
      <text class="learner-meta">第 {{ page }} 页 · 共 {{ total }} 项</text>
      <button class="learner-button" :disabled="page * LEARNER_LIST_PAGE_SIZE >= total || busy" @click="page++; load()">下一页</button>
    </view>
  </view></MobileBaseLayout>
</template>

<style scoped lang="less">
/* 首页是 tabBar 页：顶部留白放宽到 48rpx，与卡片内边距形成统一节奏。 */
.learner-page {
  padding: 48rpx 0;

  /* 底部为固定 tabBar 预留安全距离，最后一张卡片不会被遮挡。 */
  padding-bottom: 64rpx;
}

/* 五项筛选条按移动端触控基准放开高度，长条过窄时不好点。 */
.home-filter .segmented-filter__item {
  min-height: 88rpx;
}

/* 卡片标题在窄屏统一用主文本色，长标题允许任意位置换行。 */
.home-task .learner-title {
  color: var(--color-text-primary);
  overflow-wrap: anywhere;
}

.home-task .learner-meta {
  overflow-wrap: anywhere;
}
</style>
