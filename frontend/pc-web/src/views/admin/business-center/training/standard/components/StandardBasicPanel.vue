<script setup lang="ts">
/* eslint-disable vue/no-mutating-props -- 父工作台持有培训标准基础信息草稿。 */
import { ref } from 'vue';
import type { FormInstance, FormRules } from 'element-plus';

/** 培训标准基础信息的最小表单模型。 */
interface StandardBasicModel {
  standardCode: string;
  standardName: string;
  standardVersion: string;
  trainingTypeId?: number;
  validRange: string[];
  defaultTrainingNature: 'INITIAL' | 'REVIEW';
}
/** 培训类型下拉选项。 */
interface TrainingTypeOption {
  trainingTypeId: number;
  typeName: string;
  typeCode: string;
}
const props = defineProps<{
  model: StandardBasicModel;
  rules: FormRules;
  isCreate: boolean;
  readonly: boolean;
  canNext: boolean;
  trainingTypeOptions: TrainingTypeOption[];
  canSave: boolean;
  saving: boolean;
}>();
const emit = defineEmits<{ save: []; next: []; dirty: [] }>();
const formRef = ref<FormInstance>();
/** 在步骤组件内完成表单校验，再通知父工作台执行真实保存调用。 */
async function submit(): Promise<void> {
  const valid = await formRef.value?.validate().catch(() => false);
  if (valid) emit('save');
}
</script>
<template>
  <ElCard shadow="never" class="admin-detail-page__card">
    <template #header><span>基础信息</span></template>
    <div class="admin-detail-page__form-scroll">
      <ElForm ref="formRef" :model="props.model" :rules="props.rules" label-width="110px" :disabled="props.readonly" class="admin-detail-form">
        <ElRow :gutter="24">
          <ElCol :span="12">
            <ElFormItem label="标准编码" prop="standardCode" required>
              <ElInput v-model="props.model.standardCode" :disabled="!props.isCreate" placeholder="请输入标准编码" @input="emit('dirty')" />
            </ElFormItem>
          </ElCol>
          <ElCol :span="12">
            <ElFormItem label="标准名称" prop="standardName" required>
              <ElInput v-model="props.model.standardName" placeholder="请输入标准名称" @input="emit('dirty')" />
            </ElFormItem>
          </ElCol>
          <ElCol :span="12">
            <ElFormItem label="标准版本" prop="standardVersion" required>
              <ElInput v-model="props.model.standardVersion" placeholder="请输入标准版本" @input="emit('dirty')" />
            </ElFormItem>
          </ElCol>
          <ElCol :span="12">
            <ElFormItem label="培训类型" prop="trainingTypeId" required>
              <ElSelect v-model="props.model.trainingTypeId" filterable placeholder="请选择培训类型" @change="emit('dirty')">
                <ElOption
                  v-for="item in props.trainingTypeOptions"
                  :key="item.trainingTypeId"
                  :label="`${item.typeName}`"
                  :value="item.trainingTypeId"
                />
              </ElSelect>
            </ElFormItem>
          </ElCol>
          <ElCol v-if="props.isCreate" :span="12">
            <ElFormItem label="默认带入学时" required>
              <ElRadioGroup v-model="props.model.defaultTrainingNature" @change="emit('dirty')">
                <ElRadio value="INITIAL">按初培带入</ElRadio>
                <ElRadio value="REVIEW">按复审带入</ElRadio>
              </ElRadioGroup>
            </ElFormItem>
          </ElCol>
          <ElCol :span="12">
            <ElFormItem label="有效期">
              <ElDatePicker
                v-model="props.model.validRange"
                type="daterange"
                value-format="YYYY-MM-DD"
                start-placeholder="开始日期"
                end-placeholder="结束日期"
                @change="emit('dirty')"
              />
            </ElFormItem>
          </ElCol>
        </ElRow>
      </ElForm>
    </div>
    <footer class="sticky-actions">
      <ElButton v-if="props.canSave" type="primary" :loading="props.saving" @click="submit">保存并继续</ElButton>
      <ElButton type="primary" :disabled="!props.canNext" @click="emit('next')">下一步：规则编排</ElButton>
    </footer>
  </ElCard>
</template>
