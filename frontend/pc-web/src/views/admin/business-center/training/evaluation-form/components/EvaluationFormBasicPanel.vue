<script setup lang="ts">
/* eslint-disable vue/no-mutating-props -- 父工作台持有评价表单草稿。 */
import { ref } from 'vue';
import type { FormInstance, FormRules } from 'element-plus';
interface BasicForm {
  formCode: string;
  formName: string;
  formType: string;
  status: 0 | 1;
  description: string;
}
interface TypeOption {
  label: string;
  value: string;
  description: string;
}
const props = defineProps<{
  model: BasicForm;
  rules: FormRules;
  isCreated: boolean;
  canSave: boolean;
  canNext: boolean;
  saving: boolean;
  formTypes: TypeOption[];
  formTypeDescription: string;
}>();
const emit = defineEmits<{ save: []; next: [] }>();
const formRef = ref<FormInstance>();
/** 校验通过后通知父工作台提交真实接口请求。 */
async function submit(): Promise<void> {
  if (await formRef.value?.validate().catch(() => false)) emit('save');
}
</script>
<template>
  <ElCard shadow="never" class="admin-detail-page__card">
    <template #header>
      <div class="card-header">
        <div>
          <h2>基础信息</h2>
          <p>填写表单标识、适用范围和启用状态；保存后可继续项目编排。</p>
        </div>
      </div>
    </template>
    <div class="admin-detail-page__form-scroll">
      <ElForm ref="formRef" :model="props.model" :rules="props.rules" label-width="120px" class="admin-detail-form">
        <ElRow :gutter="24">
          <ElCol :span="12">
            <ElFormItem label="表单编码" prop="formCode" required>
              <ElInput v-model="props.model.formCode" :disabled="props.isCreated" placeholder="例如 FORM-COMPOSITE-001" />
            </ElFormItem>
          </ElCol>
          <ElCol :span="12">
            <ElFormItem label="表单名称" prop="formName" required>
              <ElInput v-model="props.model.formName" placeholder="请输入便于识别的表单名称" />
            </ElFormItem>
          </ElCol>
          <ElCol :span="12">
            <ElFormItem label="表单类型" prop="formType" required>
              <div class="form-type-field">
                <ElRadioGroup v-model="props.model.formType">
                  <ElRadioButton v-for="item in props.formTypes" :key="item.value" :value="item.value">{{ item.label }}</ElRadioButton>
                </ElRadioGroup>
                <div class="field-hint">
                  <span class="field-hint__label">当前类型说明</span>
                  <span>{{ props.formTypeDescription }}</span>
                </div>
              </div>
            </ElFormItem>
          </ElCol>
          <ElCol :span="12">
            <ElFormItem label="启用状态"><ElSwitch v-model="props.model.status" :active-value="1" :inactive-value="0" /></ElFormItem>
          </ElCol>
        </ElRow>
        <ElFormItem label="用途说明" prop="description" class="admin-detail-form__wide" required>
          <ElInput
            v-model="props.model.description"
            type="textarea"
            :rows="3"
            maxlength="1000"
            show-word-limit
            placeholder="说明该表单适用的培训场景和评价目的"
          />
        </ElFormItem>
      </ElForm>
    </div>
    <footer class="sticky-actions">
      <ElButton v-if="props.canSave" :type="props.canNext ? 'default' : 'primary'" :loading="props.saving" @click="submit">
        {{ props.isCreated ? '保存基础信息' : '创建表单' }}
      </ElButton>
      <ElButton type="primary" :disabled="!props.canNext" @click="emit('next')">下一步：项目与规则</ElButton>
    </footer>
  </ElCard>
</template>

<style scoped lang="less">
.form-type-field {
  width: 100%;
}

.field-hint {
  display: flex;
  align-items: flex-start;
  gap: 8px;
  box-sizing: border-box;
  width: min(100%, 400px);
  margin: 10px 0 0;
  padding: 8px 10px;
  color: var(--color-text-secondary);
  font-size: 13px;
  line-height: 20px;
  background: var(--color-primary-subtle);
  border-radius: 6px;
}

.field-hint__label {
  flex: 0 0 auto;
  color: var(--color-primary);
  font-weight: 600;
}
</style>
