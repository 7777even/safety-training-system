<script setup lang="ts">
import { ref, watch } from 'vue';
import { getOrganizationPage, type Organization } from '@/api/organization';

/** 机构选择项通过统一机构分页 API 加载，Mock 与真实接口在 API 层切换。 */
const model = defineModel<number | undefined>({ required: true });
withDefaults(defineProps<{ disabled?: boolean; clearable?: boolean; placeholder?: string }>(), {
  disabled: false,
  clearable: true,
  placeholder: '请选择所属机构'
});
const organizationOptions = ref<Organization[]>([]);
const loading = ref(false);

/** 根据输入关键字加载启用机构选项。 */
async function load(keyword = ''): Promise<void> {
  loading.value = true;
  try {
    organizationOptions.value = (
      await getOrganizationPage({ pageNum: 1, pageSize: 100, 'query.keyword': keyword || undefined, 'query.status': 1 })
    ).list;
  } finally {
    loading.value = false;
  }
}

watch(
  model,
  () => {
    if (!organizationOptions.value.length) void load();
  },
  { immediate: true }
);
</script>

<template>
  <ElSelect
    v-model="model"
    filterable
    remote
    :remote-method="load"
    :loading="loading"
    :disabled="disabled"
    :clearable="clearable"
    :placeholder="placeholder"
  >
    <ElOption v-for="item in organizationOptions" :key="item.organizationId" :label="item.orgName" :value="item.organizationId" />
  </ElSelect>
</template>

<style scoped lang="less">
:deep(.el-select) {
  width: 240px;
}
</style>
