<script setup lang="ts">
import { onMounted, ref, watch } from 'vue';
import { useRouter } from 'vue-router';
import { ElMessage } from 'element-plus';
import { Bell } from '@element-plus/icons-vue';
import type { LearnerMessage } from '@shared/types';
import type { LearnerExamTask } from '@shared/types';
import { loadMessageExams, messageExamInfo } from '@shared/libs/message-exams';
import { LEARNER_KIND_LABELS } from '@shared/utils/learner-home';
import { getLearnerMessages, getLearnerTarget, getMessageExamTasks } from '@/api/learner-home';
import { DEFAULT_LIST_PAGE_SIZE, LIST_PAGE_SIZES } from '@/constants/pagination';
import { useLearnerMessageStore } from '@/stores/modules/learner-messages';

/** 本人持久化消息，不添加已读/未读语义。 */
const rows = ref<LearnerMessage[]>([]),
  page = ref(1),
  total = ref(0),
  busy = ref(false),
  error = ref('');
const router = useRouter();
const messages = useLearnerMessageStore();
/** 当前考试类型与动作由本人考试任务接口提供，不改写持久化通知正文。 */
const examTasks = ref<Record<string, LearnerExamTask>>({});
const examInfo = (message: LearnerMessage) => messageExamInfo(message, examTasks.value);
/** 请求中收到多次事件合并为一次补读，避免丢失最新通知。 */
let refreshPending = false;
/** 在线收到变化时刷新当前页；失败保留中文提示。 */
async function load(): Promise<void> {
  if (busy.value) {
    refreshPending = true;
    return;
  }
  busy.value = true;
  error.value = '';
  examTasks.value = {};
  try {
    const result = await getLearnerMessages({ pageNum: page.value, pageSize: DEFAULT_LIST_PAGE_SIZE });
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
/** 服务端重新校验当前动作，消息失效仍保留在列表。 */
async function open(message: LearnerMessage): Promise<void> {
  if (!examInfo(message).canOpen) return;
  try {
    await router.push(await getLearnerTarget(message));
  } catch (e) {
    ElMessage.warning(e instanceof Error ? e.message : '该业务已失效或无权访问');
  }
}
watch(() => messages.revision, load);
onMounted(load);
</script>

<template>
  <section class="portal-list-page">
    <header class="portal-list-page__heading">
      <div>
        <p class="portal-list-page__eyebrow">MY MESSAGES</p>
        <h1>消息中心</h1>
        <p>课程、考试和培训提醒，点击进入对应业务。</p>
      </div>
      <div class="portal-heading__actions">
        <ElButton :loading="busy" @click="load">刷新消息</ElButton>
      </div>
    </header>

    <ElAlert v-if="error" type="error" :title="error" :closable="false" />
    <ElAlert v-if="messages.error" type="warning" :title="messages.error" :closable="false" />

    <div class="portal-section-head">
      <h2 class="portal-section-title">全部消息</h2>
      <span class="portal-section-hint">共 {{ total }} 条</span>
    </div>

    <section v-if="!busy && !error && !rows.length" class="portal-list-state">
      <ElEmpty description="暂无消息" />
    </section>
    <div v-else class="portal-list-grid">
      <ElCard v-for="message in rows" :key="message.recipientId" class="portal-list-card" shadow="never">
        <div class="portal-list-card__cover portal-cover">
          <span class="portal-cover__icon"><ElIcon><Bell /></ElIcon></span>
          <span class="portal-cover__text">
            <strong>{{ LEARNER_KIND_LABELS[message.kind] }}</strong>
            <em>{{ message.createTime.replace('T', ' ') }}</em>
          </span>
          <ElIcon class="portal-cover__mark"><Bell /></ElIcon>
        </div>
        <div class="portal-list-card__content">
          <div class="portal-list-card__header">
            <h3>{{ message.title }}</h3>
            <span class="portal-chip portal-chip--plain">{{ LEARNER_KIND_LABELS[message.kind] }}</span>
          </div>
          <p class="portal-list-card__meta">{{ message.content }}</p>
          <p v-if="examInfo(message).typeLabel" class="portal-list-card__meta">{{ examInfo(message).typeLabel }}</p>
          <p v-if="examInfo(message).hint" class="portal-list-card__meta">{{ examInfo(message).hint }}</p>
          <div class="portal-list-card__actions">
            <ElButton type="primary" :disabled="!examInfo(message).canOpen" @click="open(message)">{{ examInfo(message).actionLabel }}</ElButton>
          </div>
        </div>
      </ElCard>
    </div>

    <div v-if="total" class="portal-list-page__pagination">
      <ElPagination
        v-model:current-page="page"
        :page-size="DEFAULT_LIST_PAGE_SIZE"
        :page-sizes="LIST_PAGE_SIZES"
        :total="total"
        layout="total, prev, pager, next"
        @current-change="load"
      />
    </div>
  </section>
</template>

<style scoped lang="less">
h3 {
  overflow-wrap: anywhere;
}

.portal-list-card__meta {
  overflow-wrap: anywhere;
}
</style>
