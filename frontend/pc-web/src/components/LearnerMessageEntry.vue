<script setup lang="ts">
import { onUnmounted, watch } from 'vue';
import { useRouter } from 'vue-router';
import { subscribeLearnerMessages } from '@shared/libs/learner-messages';
import { getLearnerMessages } from '@/api/learner-home';
import { useUserStore } from '@/stores';
import { useLearnerMessageStore } from '@/stores/modules/learner-messages';

/** 门户内保持本人实时连接，退出或离开门户时立即释放。 */
const user = useUserStore();
const messages = useLearnerMessageStore();
const router = useRouter();
let stop: (() => void) | undefined;
watch(
  () => user.token,
  (token) => {
    stop?.();
    stop = undefined;
    messages.$reset();
    if (!token) return;
    stop = subscribeLearnerMessages(
      `${import.meta.env.VITE_API_BASE_URL ?? ''}/api/learner/messages/stream`,
      token,
      () => {
        void getLearnerMessages({ pageNum: 1, pageSize: 1 })
          .then((result) => {
            if (user.token === token) messages.changed(result.total);
          })
          .catch((e) => {
            if (user.token === token) messages.error = e instanceof Error ? e.message : '消息加载失败';
          });
      },
      (message) => {
        if (user.token === token) messages.error = message;
      }
    );
  },
  { immediate: true }
);
onUnmounted(() => stop?.());
</script>

<template>
  <ElButton link @click="router.push('/user-center/messages')">
    消息中心
    <span v-if="messages.total">（{{ messages.total }}）</span>
  </ElButton>
</template>

<style scoped lang="less">
span {
  margin-left: 4px;
}
</style>
