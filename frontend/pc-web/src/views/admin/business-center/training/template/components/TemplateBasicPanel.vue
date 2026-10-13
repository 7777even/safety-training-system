<script setup lang="ts">
/* eslint-disable vue/no-mutating-props -- 父工作台持有模板基础信息草稿。 */
import { ref } from 'vue';
import type { FormInstance, FormRules } from 'element-plus';

/** 模板基础信息表单。 */
interface TemplateForm {
  templateCode: string;
  templateName: string;
  templateCategoryId?: number;
  trainingTypeId?: number;
  trainingStandardId?: number;
  description: string;
  status: 0 | 1;
}
/** 培训类型与标准下拉选项。 */
interface Option {
  value: number;
  name: string;
  statusLabel: string;
}
const props = defineProps<{
  model: TemplateForm;
  rules: FormRules;
  readonly: boolean;
  isCreated: boolean;
  canNext: boolean;
  canEdit: boolean;
  saving: boolean;
  categoryTree: Array<{ templateCategoryId: number; categoryName: string; children?: unknown[] }>;
  categoryProps: Record<string, string>;
  trainingTypeOptions: Option[];
  trainingStandardOptions: Option[];
}>();
const emit = defineEmits<{
  save: [];
  next: [];
  dirty: [];
  'training-type-change': [];
  'training-standard-change': [];
}>();
const formRef = ref<FormInstance>();
/** 在组件内先校验基础信息，再交由工作台保存。 */
async function save(): Promise<void> {
  if (await formRef.value?.validate().catch(() => false)) emit('save');
}
</script>

<template>
  <ElCard shadow="never" class="admin-detail-page__card">
    <template #header><span>基础信息</span></template>
    <div class="admin-detail-page__form-scroll">
      <section class="form-panel">
        <div class="panel-heading">
          <div>
            <h2>模板基础信息</h2>
            <p>模板编码创建后不可修改，保存后可进入阶段内容编排。</p>
          </div>
        </div>
        <ElForm ref="formRef" :model="props.model" :rules="props.rules" label-width="112px" :disabled="props.readonly" class="admin-detail-form">
          <ElRow :gutter="24">
            <ElCol :span="12">
              <ElFormItem label="模板编码" prop="templateCode" required>
                <ElInput v-model="props.model.templateCode" :disabled="props.isCreated" placeholder="请输入模板编码" @input="emit('dirty')" />
              </ElFormItem>
            </ElCol>
            <ElCol :span="12">
              <ElFormItem label="模板名称" prop="templateName" required>
                <ElInput v-model="props.model.templateName" placeholder="请输入模板名称" @input="emit('dirty')" />
              </ElFormItem>
            </ElCol>
            <ElCol :span="12">
              <ElFormItem label="模板分类">
                <ElTreeSelect
                  v-model="props.model.templateCategoryId"
                  :data="props.categoryTree"
                  :props="props.categoryProps"
                  check-strictly
                  clearable
                  placeholder="请选择模板分类"
                  @change="emit('dirty')"
                />
              </ElFormItem>
            </ElCol>
            <ElCol :span="12">
              <ElFormItem label="培训类型" prop="trainingTypeId" required>
                <ElSelect v-model="props.model.trainingTypeId" filterable placeholder="请选择启用的培训类型" @change="emit('training-type-change')">
                  <ElOption v-for="item in props.trainingTypeOptions" :key="item.value" :label="item.name" :value="item.value" />
                </ElSelect>
              </ElFormItem>
            </ElCol>
            <ElCol :span="12">
              <ElFormItem label="培训标准">
                <ElSelect
                  v-model="props.model.trainingStandardId"
                  clearable
                  placeholder="请选择已发布的培训标准"
                  @change="emit('training-standard-change')"
                >
                  <ElOption v-for="item in props.trainingStandardOptions" :key="item.value" :label="item.name" :value="item.value" />
                </ElSelect>
              </ElFormItem>
            </ElCol>
            <ElCol :span="12">
              <ElFormItem label="启用状态">
                <ElRadioGroup v-model="props.model.status" @change="emit('dirty')">
                  <ElRadio :value="1">启用</ElRadio>
                  <ElRadio :value="0">停用</ElRadio>
                </ElRadioGroup>
              </ElFormItem>
            </ElCol>
            <ElCol :span="24">
              <ElFormItem label="模板说明" class="admin-detail-form__wide">
                <ElInput
                  v-model="props.model.description"
                  type="textarea"
                  :rows="4"
                  placeholder="请输入模板适用范围与编排说明"
                  @input="emit('dirty')"
                />
              </ElFormItem>
            </ElCol>
          </ElRow>
        </ElForm>
      </section>
    </div>
    <footer class="sticky-actions">
      <ElButton
        v-if="!props.readonly && (props.canEdit || !props.isCreated)"
        :type="props.canNext ? 'default' : 'primary'"
        :loading="props.saving"
        @click="save"
      >
        保存基础信息
      </ElButton>
      <ElButton type="primary" :disabled="!props.canNext" @click="emit('next')">下一步：阶段内容</ElButton>
    </footer>
  </ElCard>
</template>

<style scoped lang="less">
.form-panel {
  min-width: 0;
  padding: 5px 20px;
}
.panel-heading {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 16px;
  margin-bottom: 10px;
}
.panel-heading h2 {
  margin: 0;
  color: var(--color-text-primary);
  font-size: 16px;
  font-weight: 600;
  line-height: 24px;
}
.panel-heading p {
  margin: 4px 0 0;
  color: var(--color-text-secondary);
  font-size: 12px;
  line-height: 20px;
}
</style>
