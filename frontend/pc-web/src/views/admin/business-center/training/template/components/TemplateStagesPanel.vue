<script setup lang="ts">
import { computed, ref } from 'vue';
import type { TrainingTemplateContentType, TrainingTemplateStagePayload } from '@/api/training-template';
import type { TrainingPlanContent } from '@/api/training-plan';
import type { SystemUser } from '@/api/system-user';
import TrainingExecutionConfigDialog from '../../components/TrainingExecutionConfigDialog.vue';
import { isAssessmentTrainingContent } from '@/libs/training-composition';
interface Stage extends Omit<TrainingTemplateStagePayload, 'contents'> {
  contents: NonNullable<TrainingTemplateStagePayload['contents']>;
}
interface Option {
  label: string;
  value: string | number;
}
interface ResourceOption {
  value: number;
  name: string;
}
interface CompositionMetric {
  label: string;
  current: string;
  target: string;
  satisfied: boolean;
}
interface RuleHint {
  key: string;
  label: string;
  description: string;
  statusText: string;
  statusType: 'success' | 'warning' | 'info';
  ruleType: 'TOTAL_HOURS' | 'ONLINE_RATIO' | 'COURSE' | 'EXAM' | 'OFFLINE';
  resourceId?: number;
  resourceVersionId?: number;
  contentName?: string;
  requiredValue?: number;
}
const props = defineProps<{
  stages: Stage[];
  readonly: boolean;
  canEdit: boolean;
  saving: boolean;
  canNext: boolean;
  compositionSummary: {
    title: string;
    metrics: CompositionMetric[];
  };
  ruleHints: RuleHint[];
  teacherOptions: SystemUser[];
  contentTypeOptions: Option[];
  stageTypeOptions: Option[];
  resourceOptions: (type: TrainingTemplateContentType) => ResourceOption[];
}>();
const emit = defineEmits<{
  addStage: [];
  removeStage: [index: number];
  addContent: [stage: Stage];
  removeContent: [stage: Stage, index: number];
  'content-type-change': [content: Stage['contents'][number]];
  'resource-change': [content: Stage['contents'][number], resourceId: number];
  addRequiredContent: [hint: RuleHint, stageIndex: number];
  back: [];
  save: [];
  next: [];
}>();
const executionDialogVisible = ref(false);
const editingContent = ref<Stage['contents'][number]>();
const editingContentForDialog = computed(() => editingContent.value as unknown as TrainingPlanContent | undefined);
const requiredContentDialogVisible = ref(false);
const pendingRequiredContent = ref<RuleHint>();
const pendingStageIndex = ref(0);

/** 打开阶段选择，避免快捷添加时默认落入用户未预期的阶段。 */
function openRequiredContentDialog(hint: RuleHint): void {
  pendingRequiredContent.value = hint;
  pendingStageIndex.value = 0;
  if (!props.stages.length) {
    emit('addRequiredContent', hint, 0);
    return;
  }
  requiredContentDialogVisible.value = true;
}

/** 将指定标准内容添加到用户选择的阶段。 */
function confirmRequiredContent(): void {
  if (!pendingRequiredContent.value) return;
  emit('addRequiredContent', pendingRequiredContent.value, pendingStageIndex.value);
  requiredContentDialogVisible.value = false;
}

/** 打开与计划端一致的执行配置弹窗，模板保存时保留配置供计划带入。 */
function openExecutionConfig(content: Stage['contents'][number]): void {
  editingContent.value = content;
  executionDialogVisible.value = true;
}

/** 将通用执行配置弹窗的确认结果写回模板内容草稿。 */
function confirmExecutionConfig(content: TrainingPlanContent): void {
  if (!editingContent.value) return;
  Object.assign(editingContent.value, {
    assessmentMode: content.assessmentMode,
    executionConfig: content.executionConfig
  });
}

/** 模板执行配置是可选默认值；计划复制后仍可补齐本期配置。 */
function isExecutionConfigured(content: Stage['contents'][number]): boolean {
  if (content.contentType === 'COURSE') return Boolean(content.executionConfig?.completionRule);
  if (content.contentType === 'EXAM') return Boolean(content.assessmentMode);
  const config = content.executionConfig;
  if (content.contentType === 'QUESTION_BANK')
    return Boolean(config?.durationSeconds && config.maxAttempts && config.drawRules?.length);
  return Boolean(
    config?.teacherUserId &&
    config.location &&
    config.startTime &&
    config.endTime &&
    config.passScore !== undefined
  );
}
function asContent(row: unknown): Stage['contents'][number] {
  return row as Stage['contents'][number];
}

/** 考试和题库练习不填写安排学时。 */
function isAssessmentContent(contentType: TrainingTemplateContentType): boolean {
  return isAssessmentTrainingContent(contentType);
}
</script>
<template>
  <ElCard shadow="never" class="admin-detail-page__card">
    <template #header>
      <div class="card-header">
        <div>
          <h2>阶段与内容编排</h2>
          <p>按执行顺序维护阶段与资源，保存确认后方可进入发布检查。</p>
        </div>
        <ElButton v-if="props.canEdit" type="primary" plain @click="emit('addStage')">新增阶段</ElButton>
      </div>
    </template>
    <div class="admin-detail-page__form-scroll stages-scroll">
      <section class="composition-board">
        <div class="composition-board__heading">
          <div>
            <h3>规则检查提示</h3>
            <p>{{ props.compositionSummary.title }}</p>
          </div>
          <ElTag :type="props.ruleHints.every((item) => item.statusType === 'success') ? 'success' : 'warning'">
            {{ props.ruleHints.every((item) => item.statusType === 'success') ? '规则已满足' : '仍需补齐' }}
          </ElTag>
        </div>
        <div class="metric-grid">
          <div
            v-for="metric in props.compositionSummary.metrics"
            :key="metric.label"
            class="metric-card"
            :class="{ 'is-warning': !metric.satisfied }"
          >
            <span>{{ metric.label }}</span>
            <strong>{{ metric.current }}</strong>
            <small>目标：{{ metric.target }}</small>
          </div>
        </div>
        <div class="rule-grid">
          <div v-for="hint in props.ruleHints" :key="hint.key" class="rule-item">
            <div>
              <strong>{{ hint.label }}</strong>
              <p>{{ hint.description }}</p>
            </div>
            <div class="rule-item__actions">
              <ElTag :type="hint.statusType">{{ hint.statusText }}</ElTag>
              <ElButton
                v-if="['COURSE', 'EXAM', 'OFFLINE'].includes(hint.ruleType) && hint.statusType !== 'success'"
                type="primary"
                link
                :disabled="props.readonly"
                @click="openRequiredContentDialog(hint)"
              >
                添加到阶段
              </ElButton>
            </div>
          </div>
          <div v-if="props.ruleHints.length === 0" class="rule-empty-state">
            <div class="rule-empty-state__icon" aria-hidden="true">自定义</div>
            <div class="rule-empty-state__body">
              <strong>当前为自定义编排</strong>
              <p>模板未关联培训标准，不影响继续配置阶段和内容；后续也可以返回基础信息补充标准。</p>
              <div class="rule-empty-state__guide">
                <span><i>1</i>新增阶段</span>
                <span><i>2</i>添加课程、考试或线下培训</span>
                <span><i>3</i>保存后发布确认</span>
              </div>
            </div>
            <div class="rule-empty-state__actions">
              <ElButton link type="primary" @click="emit('back')">返回关联培训标准</ElButton>
              <ElButton v-if="props.canEdit" type="primary" plain @click="emit('addStage')">新增阶段</ElButton>
            </div>
          </div>
        </div>
      </section>

      <ElEmpty v-if="props.stages.length === 0" description="暂无阶段，请新增后开始编排" class="stages-empty">
        <ElButton v-if="props.canEdit" type="primary" @click="emit('addStage')">新增第一个阶段</ElButton>
      </ElEmpty>
      <article v-for="(stage, stageIndex) in props.stages" :key="stageIndex" class="stage-card">
        <header class="stage-card__header">
          <div class="stage-card__index">{{ String(stageIndex + 1).padStart(2, '0') }}</div>
          <div>
            <h3>{{ stage.stageName || `阶段 ${stageIndex + 1}` }}</h3>
            <p>{{ stage.stageType }} · 阶段要求 {{ stage.requiredHours ?? 0 }} 学时 · 内容 {{ stage.contents.length }} 项</p>
          </div>
          <ElButton v-if="props.canEdit" link type="danger" @click="emit('removeStage', stageIndex)">删除阶段</ElButton>
        </header>
        <ElForm label-width="88px" class="admin-detail-form stage-form">
          <ElRow :gutter="24">
            <ElCol :span="8">
              <ElFormItem label="阶段名称" required>
                <ElInput v-model="stage.stageName" :disabled="props.readonly" placeholder="请输入阶段名称" />
              </ElFormItem>
            </ElCol>
            <ElCol :span="8">
              <ElFormItem label="阶段类型" required>
                <ElSelect v-model="stage.stageType" :disabled="props.readonly" placeholder="请选择阶段类型">
                  <ElOption v-for="item in props.stageTypeOptions" :key="item.value" :label="item.label" :value="item.value" />
                </ElSelect>
              </ElFormItem>
            </ElCol>
            <ElCol :span="8">
              <ElFormItem label="要求学时">
                <ElInputNumber v-model="stage.requiredHours" :disabled="props.readonly" :min="0" :precision="1" />
              </ElFormItem>
            </ElCol>
            <ElCol :span="12">
              <ElFormItem label="启用状态">
                <ElRadioGroup v-model="stage.status" :disabled="props.readonly">
                  <ElRadio :value="1">启用</ElRadio>
                  <ElRadio :value="0">停用</ElRadio>
                </ElRadioGroup>
              </ElFormItem>
            </ElCol>
          </ElRow>
        </ElForm>
        <div class="content-heading">
          <div>
            <h4>阶段内容</h4>
            <span>资源选择与模板内容将一并覆盖保存。</span>
          </div>
          <ElButton v-if="props.canEdit" link type="primary" @click="emit('addContent', stage)">新增内容</ElButton>
        </div>
        <ElTable :data="stage.contents" class="content-table" border>
          <ElTableColumn min-width="100">
            <template #header>内容类型 <i class="required-asterisk" aria-hidden="true">*</i></template>
            <template #default="{ row }">
              <ElSelect
                v-model="row.contentType"
                :disabled="props.readonly"
                placeholder="请选择内容类型"
                @change="emit('content-type-change', asContent(row))"
              >
                <ElOption v-for="item in props.contentTypeOptions" :key="item.value" :label="item.label" :value="item.value" />
              </ElSelect>
            </template>
          </ElTableColumn>
          <ElTableColumn min-width="200">
            <template #header>内容名称 <i class="required-asterisk" aria-hidden="true">*</i></template>
            <template #default="{ row }">
              <ElInput v-model="row.contentName" :disabled="props.readonly" placeholder="请输入内容名称" />
            </template>
          </ElTableColumn>
          <ElTableColumn label="内容资源（线上内容必填）" min-width="200">
            <template #default="{ row }">
              <ElTag v-if="row.contentType === 'OFFLINE'" type="info">线下内容无需绑定资源</ElTag>
              <ElSelect
                v-else
                v-model="row.resourceId"
                :disabled="props.readonly"
                placeholder="请选择已发布且可用的资源"
                @change="emit('resource-change', asContent(row), Number($event))"
              >
                <ElOption
                  v-for="item in props.resourceOptions(asContent(row).contentType)"
                  :key="item.value"
                  :label="item.name"
                  :value="item.value"
                />
              </ElSelect>
            </template>
          </ElTableColumn>
          <ElTableColumn label="必修" width="60">
            <template #default="{ row }">
              <ElSwitch v-model="row.isRequired" :disabled="props.readonly" :active-value="1" :inactive-value="0" />
            </template>
          </ElTableColumn>
          <ElTableColumn label="学时" width="160">
            <template #default="{ row }">
              <span v-if="isAssessmentContent(row.contentType)" class="content-hours-note">0 学时（不计入）</span>
              <ElInputNumber v-else v-model="row.requiredHours" :disabled="props.readonly" :min="0" :precision="1" />
            </template>
          </ElTableColumn>
          <ElTableColumn width="190">
            <template #header>执行配置（可选默认值）</template>
            <template #default="{ row }">
              <div class="execution-config-cell">
                <ElTag :type="isExecutionConfigured(asContent(row)) ? 'success' : 'warning'">
                  {{ isExecutionConfigured(asContent(row)) ? '已配置默认值' : '计划中补充' }}
                </ElTag>
                <ElButton link type="primary" @click="openExecutionConfig(asContent(row))">{{ props.readonly ? '查看默认值' : '配置默认值' }}</ElButton>
              </div>
            </template>
          </ElTableColumn>
          <ElTableColumn v-if="props.canEdit" label="操作" width="80">
            <template #default="{ $index }"><ElButton link type="danger" @click="emit('removeContent', stage, $index)">删除</ElButton></template>
          </ElTableColumn>
        </ElTable>
      </article>
    </div>
    <ElDialog v-model="requiredContentDialogVisible" title="选择添加阶段" width="420px" append-to-body>
      <ElForm label-position="top">
        <ElFormItem label="目标阶段" required>
          <ElSelect v-model="pendingStageIndex" class="required-content-stage-select" placeholder="请选择目标阶段">
            <ElOption
              v-for="(stage, index) in props.stages"
              :key="index"
              :label="`${String(index + 1).padStart(2, '0')} · ${stage.stageName || `阶段 ${index + 1}`}`"
              :value="index"
            />
          </ElSelect>
        </ElFormItem>
      </ElForm>
      <template #footer>
        <ElButton @click="requiredContentDialogVisible = false">取消</ElButton>
        <ElButton type="primary" @click="confirmRequiredContent">确认添加</ElButton>
      </template>
    </ElDialog>
    <footer class="sticky-actions">
      <ElButton @click="emit('back')">上一步</ElButton>
      <ElButton v-if="props.canEdit" :type="props.canNext ? 'default' : 'primary'" :loading="props.saving" @click="emit('save')">
        保存阶段内容
      </ElButton>
      <ElButton type="primary" :disabled="!props.canNext" @click="emit('next')">下一步：发布确认</ElButton>
    </footer>
    <TrainingExecutionConfigDialog
      v-model:visible="executionDialogVisible"
      :content="editingContentForDialog"
      :teacher-options="props.teacherOptions"
      :readonly="props.readonly"
      @confirm="confirmExecutionConfig"
    />
  </ElCard>
</template>
<style scoped lang="less">
.required-asterisk {
  color: var(--el-color-danger);
  font-style: normal;
  font-weight: 600;
}
.stage-card h3,
.content-heading h4,
.composition-board h3 {
  margin: 0;
  color: var(--color-text-primary);
  font-weight: 600;
}
.stage-card p,
.content-heading span,
.composition-board p {
  margin: 4px 0 0;
  color: var(--color-text-secondary);
  font-size: 12px;
  line-height: 20px;
}
.stages-scroll {
  padding: 16px 18px 20px;
  background: var(--color-bg-subtle);
}
.stages-empty {
  min-height: 300px;
}
.composition-board {
  margin-bottom: 16px;
  padding: 16px;
  border: 1px solid var(--color-border);
  border-radius: 8px;
  background: var(--color-bg-surface);
}
.composition-board__heading {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 16px;
  margin-bottom: 14px;
}
.composition-board h3 {
  font-size: 15px;
  line-height: 20px;
}
.metric-grid {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: 10px;
  margin-bottom: 12px;
}
.metric-card {
  min-width: 0;
  padding: 10px 12px;
  border: 1px solid var(--color-border);
  border-radius: 8px;
  background: var(--color-bg-subtle);
}
.metric-card.is-warning {
  border-color: var(--el-color-warning-light-5);
  background: var(--el-color-warning-light-9);
}
.metric-card span,
.metric-card small {
  display: block;
  overflow: hidden;
  color: var(--color-text-secondary);
  font-size: 12px;
  line-height: 18px;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.metric-card strong {
  display: block;
  margin: 4px 0 2px;
  overflow: hidden;
  color: var(--color-text-primary);
  font-size: 18px;
  line-height: 24px;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.rule-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 10px;
  min-height: 0;
  align-content: center;
}
.rule-empty-state {
  display: flex;
  align-items: center;
  gap: 16px;
  grid-column: 1 / -1;
  padding: 18px 20px;
  border: 1px dashed var(--color-border);
  border-radius: 8px;
  background: var(--color-bg-subtle);
}
.rule-empty-state__icon {
  display: grid;
  width: 48px;
  height: 48px;
  flex: 0 0 auto;
  place-items: center;
  border-radius: 12px;
  background: var(--color-primary-subtle);
  color: var(--color-primary);
  font-size: 12px;
  font-weight: 600;
}
.rule-empty-state__body {
  min-width: 0;
  flex: 1;
}
.rule-empty-state__body strong {
  color: var(--color-text-primary);
  font-size: 14px;
}
.rule-empty-state__body p {
  margin-top: 4px;
}
.rule-empty-state__guide {
  display: flex;
  flex-wrap: wrap;
  gap: 8px 18px;
  margin-top: 10px;
  color: var(--color-text-secondary);
  font-size: 12px;
}
.rule-empty-state__guide span {
  display: inline-flex;
  align-items: center;
  gap: 5px;
}
.rule-empty-state__guide i {
  display: inline-grid;
  width: 18px;
  height: 18px;
  place-items: center;
  border-radius: 50%;
  background: var(--color-primary);
  color: #fff;
  font-style: normal;
  font-size: 11px;
}
.rule-empty-state__actions {
  display: flex;
  flex: 0 0 auto;
  align-items: center;
  gap: 8px;
}
.rule-item {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  min-width: 0;
  padding: 10px 12px;
  border: 1px solid var(--color-border);
  border-radius: 8px;
  background: #fff;
}
.rule-item strong {
  display: block;
  color: var(--color-text-primary);
  font-size: 13px;
  line-height: 18px;
}
.rule-item p {
  overflow: hidden;
  max-width: 520px;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.rule-item__actions {
  display: flex;
  flex: 0 0 auto;
  align-items: center;
  gap: 8px;
}
.required-content-stage-select {
  width: 100%;
}
/* 规则提示为空时占满整行。 */
.rule-grid :deep(.el-empty),
.rule-grid > .el-empty {
  grid-column: 1 / -1;
  width: 100%;
  display: flex;
  justify-content: center;
  align-items: center;
}
.stage-card {
  margin-bottom: 16px;
  overflow: hidden;
  border: 1px solid var(--color-border);
  border-radius: 8px;
  background: var(--color-bg-surface);
  box-shadow: var(--shadow-sm);
}
.stage-card:last-child {
  margin-bottom: 0;
}
.stage-card__header {
  display: grid;
  grid-template-columns: 36px minmax(0, 1fr) auto;
  align-items: center;
  gap: 12px;
  padding: 14px 18px;
  border-bottom: 1px solid var(--color-border);
  background: var(--color-bg-subtle);
}
.stage-card__index {
  display: grid;
  width: 32px;
  height: 32px;
  place-items: center;
  border-radius: 50%;
  background: var(--color-primary-subtle);
  color: var(--color-primary);
  font-size: 12px;
  font-weight: 600;
}
.stage-card h3 {
  font-size: 15px;
  line-height: 20px;
}
.stage-card p {
  margin-top: 2px;
}
.stage-form {
  padding: 20px 18px 4px;
}
.content-heading {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
  padding: 14px 18px;
  border-top: 1px solid var(--color-border);
}
.content-heading h4 {
  font-size: 14px;
  line-height: 20px;
}
.content-heading span {
  display: block;
  margin-top: 2px;
}
.content-table {
  margin: 0 18px 18px;
  width: calc(100% - 36px);
}
.execution-config-cell {
  display: flex;
  align-items: center;
  gap: 8px;
}
@media (max-width: 1200px) {
  .metric-grid,
  .rule-grid {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }
}
@media (max-width: 900px) {
  .composition-board__heading,
  .rule-item {
    align-items: stretch;
    flex-direction: column;
  }
  .metric-grid,
  .rule-grid {
    grid-template-columns: 1fr;
  }
  .rule-item__actions {
    justify-content: space-between;
  }
  .rule-empty-state {
    align-items: flex-start;
    flex-direction: column;
  }
  .rule-empty-state__actions {
    width: 100%;
    justify-content: flex-end;
  }
  .stage-card__header {
    grid-template-columns: 36px minmax(0, 1fr);
  }
  .stage-card__header > .el-button {
    grid-column: 2;
    justify-self: start;
  }
  .content-table {
    min-width: 760px;
  }
  .stages-scroll {
    overflow-x: auto;
  }
}
</style>
