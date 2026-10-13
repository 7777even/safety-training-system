<script setup lang="ts">
import { onHide, onShow } from '@dcloudio/uni-app';
import { watch } from 'vue';
import { useUserStore } from '@/stores';
import { useLearnerMessageStore } from '@/stores/modules/learner-messages';
import { getLearnerMessages } from '@/api/learner-home';
import { getBaseURL } from '@/api/request';
import { subscribeLearnerMessages } from '@shared/libs/learner-messages';

/** H5前台保持本人实时连接；原生端不提前实现系统推送。 */
const user = useUserStore();
const messages = useLearnerMessageStore();
let stop: (() => void) | undefined;
let visible = false;
/** 切换账号或进入后台立即结束旧会话的消息连接。 */
function connect(): void {
  stop?.();
  stop = undefined;
  // #ifdef H5
  const token = user.token;
  if (visible && token)
    stop = subscribeLearnerMessages(
      `${getBaseURL()}/api/learner/messages/stream`,
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
  // #endif
}
watch(
  () => user.token,
  () => {
    messages.$reset();
    connect();
  }
);
onShow(() => {
  visible = true;
  connect();
});
onHide(() => {
  visible = false;
  stop?.();
  stop = undefined;
});
</script>
