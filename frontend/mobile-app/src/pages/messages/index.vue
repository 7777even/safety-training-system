<script setup lang="ts">
import { onShow } from '@dcloudio/uni-app';
import { ref, watch } from 'vue';
import type { LearnerMessage } from '@shared/types';
import type { LearnerExamTask } from '@shared/types';
import { loadMessageExams, messageExamInfo } from '@shared/libs/message-exams';
import { LEARNER_KIND_LABELS } from '@shared/utils/learner-home';
import { getLearnerMessages, getLearnerTarget, getMessageExamTasks } from '@/api/learner-home';
import { LEARNER_LIST_PAGE_SIZE } from '@/constants/learner-center';
import { useLearnerMessageStore } from '@/stores/modules/learner-messages';
import MobileBaseLayout from '@/layouts/MobileBaseLayout.vue';

/** 本人持久化消息，按窄屏卡片展示。 */
const rows = ref<LearnerMessage[]>([]),
  page = ref(1),
  total = ref(0),
  busy = ref(false),
  error = ref('');
const messages = useLearnerMessageStore();
/** 本人考试类型和当前动作与消息正文分开，线下成绩未开放时不提供作答入口。 */
const examTasks = ref<Record<string, LearnerExamTask>>({});
const examInfo = (message: LearnerMessage) => messageExamInfo(message, examTasks.value);
/** 请求中收到多次事件合并为一次补读，避免丢失最新通知。 */
let refreshPending = false;
/** 前台恢复与在线消息变化都重新读取真实接口。 */
async function load(): Promise<void> {
  if (busy.value) {
    refreshPending = true;
    return;
  }
  busy.value = true;
  error.value = '';
  examTasks.value = {};
  try {
    const result = await getLearnerMessages({ pageNum: page.value, pageSize: LEARNER_LIST_PAGE_SIZE });
    rows.value = result.list;
    total.value = result.total;
    if (rows.value.some(message => message.kind === 'FORMAL_EXAM')) {
      examTasks.value = await loadMessageExams(rows.value, getMessageExamTasks);
    }
  } catch (e) {
    error.value = e instanceof Error ? e.message : '消息加载失败';
  } finally {
    busy.value = false;
    if (refreshPending) {
      refreshPending = false;
      void load();
    }
  }
}
/** 每次打开旧消息都重新检查当前业务访问。 */
async function open(message: LearnerMessage): Promise<void> {
  if (!examInfo(message).canOpen) return;
  try {
    uni.navigateTo({ url: await getLearnerTarget(message) });
  } catch (e) {
    uni.showToast({ title: e instanceof Error ? e.message : '该业务已失效或无权访问', icon: 'none' });
  }
}
watch(() => messages.revision, load);
onShow(load);
</script>

<template>
  <MobileBaseLayout>
    <view class="learner-page">
      <view class="learner-hero">
        <view class="learner-hero__deco" />
        <text class="learner-hero__eyebrow">MY MESSAGES</text>
        <text class="learner-hero__title">消息中心</text>
        <text class="learner-hero__subtitle">课程、考试与培训提醒</text>
        <view class="hero-actions">
          <text class="hero-action hero-action--static">共 {{ total }} 条</text>
          <view class="hero-action" @click="!busy && load()">刷新消息</view>
        </view>
      </view>

      <text v-if="error || messages.error" class="learner-error">{{ error || messages.error }}</text>

      <view v-if="!busy && !rows.length" class="learner-state">暂无消息</view>

      <view v-for="message in rows" :key="message.recipientId" class="learner-card">
        <view class="learner-card__head">
          <text class="learner-title">{{ message.title }}</text>
          <text class="learner-badge">{{ LEARNER_KIND_LABELS[message.kind] }}</text>
        </view>
        <text class="learner-meta">{{ message.content }}</text>
        <text v-if="examInfo(message).typeLabel" class="learner-meta">{{ examInfo(message).typeLabel }}</text>
        <text v-if="examInfo(message).hint" class="learner-meta">{{ examInfo(message).hint }}</text>
        <text class="learner-meta">{{ message.createTime.replace('T', ' ') }}</text>
        <view class="learner-actions">
          <button class="learner-button learner-button--primary" :disabled="!examInfo(message).canOpen" @click="open(message)">{{ examInfo(message).actionLabel }}</button>
        </view>
      </view>

      <view v-if="total" class="learner-actions">
        <button class="learner-button" :disabled="page <= 1 || busy" @click="page--; load()">上一页</button>
        <text class="learner-meta">第 {{ page }} 页 · 共 {{ total }} 条</text>
        <button class="learner-button" :disabled="page * LEARNER_LIST_PAGE_SIZE >= total || busy" @click="page++; load()">下一页</button>
      </view>
    </view>
  </MobileBaseLayout>
</template>

<style scoped lang="less">
/* 卡片头部与业务类别标签走全局 `.learner-card__head` / `.learner-badge`，本页不再自造。 */
.learner-title,
.learner-meta {
  overflow-wrap: anywhere;
}
</style>
