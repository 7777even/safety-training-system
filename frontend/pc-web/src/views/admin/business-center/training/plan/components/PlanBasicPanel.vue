<script setup lang="ts">
/* eslint-disable vue/no-mutating-props -- 父工作台持有草稿状态，步骤组件只编辑该草稿。 */
import { computed, ref } from 'vue';
import type { FormInstance, FormRules } from 'element-plus';
interface PlanForm {
  planCode: string;
  planName: string;
  trainingTypeId?: number;
  trainingNature: 'INITIAL' | 'REVIEW';
  trainingStandardId?: number;
  sourceTemplateId?: number;
  managerUserId?: number;
  startTime: string;
  endTime: string;
  location: string;
  description: string;
}
interface TrainingType {
  trainingTypeId: number;
  typeName: string;
}
interface TrainingStandard {
  trainingStandardId: number;
  standardName: string;
  trainingTypeId: number;
}
interface TrainingTemplate {
  trainingTemplateId: number;
  templateName: string;
  trainingTypeId: number;
  trainingStandardId?: number;
}
interface ManagerUser {
  userId: number;
  userName: string;
}
const props = defineProps<{
  model: PlanForm;
  rules: FormRules;
  readonly: boolean;
  created: boolean;
  saving: boolean;
  canSave: boolean;
  canNext: boolean;
  applicableRequiredHours?: number;
  historicalNature: boolean;
  trainingTypes: TrainingType[];
  trainingStandards: TrainingStandard[];
  trainingTemplates: TrainingTemplate[];
  managerUsers: ManagerUser[];
}>();
const emit = defineEmits<{ save: []; next: []; trainingTypeChange: []; trainingStandardChange: []; trainingTemplateChange: [] }>();
const formRef = ref<FormInstance>();
/** 将范围控件的双值映射到培训计划 Contract 的开始、结束字段。 */
const planTimeRange = computed({
  get: (): [string, string] => [props.model.startTime, props.model.endTime],
  set: (value: [string, string] | null) => {
    const [startTime = '', endTime = ''] = value ?? [];
    props.model.startTime = startTime;
    props.model.endTime = endTime;
  }
});
/** 基础信息通过本步骤校验后，再由父工作台提交真实接口。 */
async function save(): Promise<void> {
  if (await formRef.value?.validate().catch(() => false)) emit('save');
}
</script>
<template>
  <ElCard shadow="never" class="admin-detail-page__card">
    <template #header>
      <div class="card-header">
        <div>
          <h2>基本信息</h2>
          <p>计划编码、培训范围与责任人；保存后才能进入阶段内容编排。</p>
        </div>
        <ElTag v-if="props.created" effect="plain" size="small" type="info">已创建</ElTag>
      </div>
    </template>
    <div class="admin-detail-page__form-scroll">
      <ElForm ref="formRef" :model="props.model" :rules="props.rules" label-width="110px" :disabled="props.readonly">
        <div class="form-grid">
          <ElFormItem label="计划编码" prop="planCode" required>
            <ElInput v-model="props.model.planCode" :disabled="props.created" placeholder="请输入计划编码" />
          </ElFormItem>
          <ElFormItem label="计划名称" prop="planName" required><ElInput v-model="props.model.planName" placeholder="请输入计划名称" /></ElFormItem>
          <ElFormItem label="培训类型" prop="trainingTypeId" required>
            <ElSelect v-model="props.model.trainingTypeId" placeholder="请选择培训类型" @change="emit('trainingTypeChange')">
              <ElOption v-for="item in props.trainingTypes" :key="item.trainingTypeId" :label="item.typeName" :value="item.trainingTypeId" />
            </ElSelect>
          </ElFormItem>
          <ElFormItem label="培训性质" required>
            <ElSelect v-model="props.model.trainingNature" :disabled="props.readonly || props.historicalNature">
              <ElOption label="初培" value="INITIAL" />
              <ElOption label="复审" value="REVIEW" />
            </ElSelect>
            <ElText v-if="props.historicalNature" type="info">历史未标明</ElText>
          </ElFormItem>
          <ElFormItem label="培训标准" prop="trainingStandardId">
            <ElSelect v-model="props.model.trainingStandardId" clearable placeholder="请选择培训标准" @change="emit('trainingStandardChange')">
              <ElOption
                v-for="item in props.trainingStandards.filter((standard) => standard.trainingTypeId === props.model.trainingTypeId)"
                :key="item.trainingStandardId"
                :label="item.standardName"
                :value="item.trainingStandardId"
              />
            </ElSelect>
          </ElFormItem>
          <ElFormItem label="培训模板" prop="sourceTemplateId">
            <ElSelect v-model="props.model.sourceTemplateId" clearable placeholder="请选择来源培训模板" @change="emit('trainingTemplateChange')">
              <ElOption
                v-for="item in props.trainingTemplates.filter((template) => template.trainingTypeId === props.model.trainingTypeId)"
                :key="item.trainingTemplateId"
                :label="item.templateName"
                :value="item.trainingTemplateId"
              />
            </ElSelect>
          </ElFormItem>
          <ElFormItem label="计划负责人" prop="managerUserId">
            <ElSelect v-model="props.model.managerUserId" clearable filterable placeholder="请选择计划负责人">
              <ElOption v-for="item in props.managerUsers" :key="item.userId" :label="item.userName" :value="item.userId" />
            </ElSelect>
          </ElFormItem>
          <ElFormItem label="培训时间" prop="endTime" required>
            <ElDatePicker
              v-model="planTimeRange"
              type="datetimerange"
              value-format="YYYY-MM-DDTHH:mm:ss"
              range-separator="至"
              start-placeholder="开始时间"
              end-placeholder="结束时间"
            />
          </ElFormItem>
          <ElFormItem label="适用最低学时">
            <ElText>{{ props.applicableRequiredHours ?? '发布后计算' }}</ElText>
          </ElFormItem>
          <ElFormItem label="培训地点">
            <ElInput v-model="props.model.location" placeholder="请输入培训地点" />
          </ElFormItem>
          <ElFormItem label="说明" class="form-grid__full-row">
            <ElInput v-model="props.model.description" type="textarea" placeholder="请输入计划说明" />
          </ElFormItem>
        </div>
      </ElForm>
    </div>
    <footer class="sticky-actions">
      <ElButton v-if="props.canSave" :type="props.canNext ? 'default' : 'primary'" :loading="props.saving" @click="save">保存基础信息</ElButton>
      <ElButton type="primary" :disabled="!props.canNext" @click="emit('next')">下一步：阶段与内容</ElButton>
    </footer>
  </ElCard>
</template>

<style scoped lang="less">
.form-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 0 16px;

  :deep(.el-select),
  :deep(.el-date-editor) {
    width: 100%;
  }
}

.form-grid__full-row {
  grid-column: 1 / -1;
}

@media (max-width: 768px) {
  .form-grid {
    grid-template-columns: minmax(0, 1fr);
  }

  .form-grid__full-row {
    grid-column: auto;
  }
}
</style>
