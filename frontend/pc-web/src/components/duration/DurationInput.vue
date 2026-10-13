<script setup lang="ts">
import { computed } from 'vue';
import { formatDuration } from '@/utils/duration';

const durationSeconds = defineModel<number | undefined>({ required: true });
const emit = defineEmits<{ change: [] }>();
const props = withDefaults(defineProps<{ disabled?: boolean }>(), { disabled: false });

/** 当前输入的总秒数，未设置时以零拆分，便于用户从时、分开始填写。 */
const normalizedSeconds = computed(() => Math.max(0, Math.floor(durationSeconds.value ?? 0)));
const hours = computed({
  get: () => Math.floor(normalizedSeconds.value / 3600),
  set: (value: number) => updateDuration(value, minutes.value, seconds.value)
});
const minutes = computed({
  get: () => Math.floor((normalizedSeconds.value % 3600) / 60),
  set: (value: number) => updateDuration(hours.value, value, seconds.value)
});
const seconds = computed({
  get: () => normalizedSeconds.value % 60,
  set: (value: number) => updateDuration(hours.value, minutes.value, value)
});

/** 将时、分、秒控件的值合并回接口契约仍要求的总秒数。 */
function updateDuration(hourValue: number, minuteValue: number, secondValue: number): void {
  durationSeconds.value = Math.max(0, Math.floor(hourValue || 0) * 3600 + Math.floor(minuteValue || 0) * 60 + Math.floor(secondValue || 0));
  emit('change');
}
</script>

<template>
  <div class="duration-input">
    <div class="duration-input__fields">
      <ElInputNumber v-model="hours" :disabled="props.disabled" :min="0" :precision="0" controls-position="right" aria-label="小时" />
      <span class="duration-input__unit">小时</span>
      <ElInputNumber v-model="minutes" :disabled="props.disabled" :min="0" :max="59" :precision="0" controls-position="right" aria-label="分钟" />
      <span class="duration-input__unit">分</span>
      <ElInputNumber v-model="seconds" :disabled="props.disabled" :min="0" :max="59" :precision="0" controls-position="right" aria-label="秒" />
      <span class="duration-input__unit">秒</span>
    </div>
    <p class="duration-input__hint">当前时长：{{ formatDuration(normalizedSeconds) }}（共 {{ normalizedSeconds }} 秒）</p>
  </div>
</template>

<style scoped lang="less">
.duration-input {
  width: 100%;
  min-width: 0;
}

/* 时 / 分 / 秒三格等分伸缩：容器变窄时按比例收窄，不再固定 96px 溢出裁切数字。 */
.duration-input__fields {
  display: flex;
  width: 100%;
  align-items: center;
  gap: 6px;
}

.duration-input__fields :deep(.el-input-number) {
  flex: 1 1 0;
  width: auto;
  min-width: 0;
  max-width: 120px;
}

/* 右侧步进器默认左右各留 15px / 42px，窄栏下压缩内边距与按钮宽度以免数字被截断。 */
.duration-input__fields :deep(.el-input-number.is-controls-right .el-input__wrapper) {
  padding-left: 8px;
  padding-right: 26px;
}

.duration-input__fields :deep(.el-input-number.is-controls-right .el-input-number__increase),
.duration-input__fields :deep(.el-input-number.is-controls-right .el-input-number__decrease) {
  width: 20px;
}

.duration-input__unit {
  flex: none;
  color: var(--color-text-secondary);
  font-size: 12px;
  white-space: nowrap;
}

.duration-input__hint {
  margin: 4px 0 0;
  color: var(--color-text-secondary);
  font-size: 12px;
  line-height: 18px;
}
</style>
