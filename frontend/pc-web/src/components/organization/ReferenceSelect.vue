<script setup lang="ts">
import { onMounted, ref, watch } from 'vue';
import { getReferenceOptions, type ReferenceKind, type ReferenceOption } from '@/api/reference-options';

const model = defineModel<number | undefined>({ required: true });
const props = withDefaults(
  defineProps<{ kind: ReferenceKind; organizationId?: number; disabled?: boolean; clearable?: boolean }>(),
  { organizationId: undefined, disabled: false, clearable: false }
);
const options = ref<ReferenceOption[]>([]);
const loading = ref(false);

/** 按主体类型和机构加载真实选项，机构或类型变化时重新读取。 */
async function loadOptions(): Promise<void> {
  options.value = [];
  if (props.kind !== 'DEPT' && !props.organizationId) {
    return;
  }

  loading.value = true;
  try {
    options.value = await getReferenceOptions(props.kind, props.organizationId);
  } finally {
    loading.value = false;
  }
}

watch(() => [props.kind, props.organizationId], loadOptions);
onMounted(loadOptions);
</script>

<template>
  <ElSelect v-model="model" filterable :loading="loading" :disabled="disabled" :clearable="clearable" placeholder="请选择关联数据">
    <ElOption v-for="item in options" :key="item.id" :label="item.name" :value="item.id" />
  </ElSelect>
</template>

<style scoped lang="less">
:deep(.el-select) {
  width: 240px;
}
</style>
