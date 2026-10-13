<script setup lang="ts">
import { ref, watch } from 'vue';
import type { TrainingPlanContent, TrainingPlanStage } from '@/api/training-plan';
import type { TrainingReferenceResource } from '@/api/training-reference';
import type { SystemUser } from '@/api/system-user';
import { isAssessmentTrainingContent } from '@/libs/training-composition';
import TrainingExecutionConfigDialog from '../../components/TrainingExecutionConfigDialog.vue';

interface SummaryMetric {
  label: string;
  current: string;
  target: string;
  satisfied: boolean;
}
interface ComplianceSummary {
  sourceTemplateText: string;
  typeText: string;
  totalText: string;
  ratioText: string;
  metrics: SummaryMetric[];
  ruleCoverage: Array<{ label: string; satisfied: boolean }>;
  requiredContents: Array<{
    ruleType: 'COURSE' | 'EXAM' | 'OFFLINE';
    resourceId?: number;
    resourceVersionId?: number;
    name: string;
    description: string;
    satisfied: boolean;
  }>;
}
const props = defineProps<{
  stages: TrainingPlanStage[];
  resources: TrainingReferenceResource[];
  teacherOptions: SystemUser[];
  readonly: boolean;
  canNext: boolean;
  /** 发布预检点击“前往调整”时，要直接打开的内容执行配置。 */
  focusContentId?: number;
  complianceSummary: ComplianceSummary;
  /** 变更编辑只展示编排，符合性由批准生效时校验。 */
  showCompliance?: boolean;
}>();

const executionDialogVisible = ref(false);
const editingContent = ref<TrainingPlanContent>();
const requiredContentDialogVisible = ref(false);
const pendingRequiredContent = ref<ComplianceSummary['requiredContents'][number]>();
const pendingStageIndex = ref(0);
/** 多个同类计分内容先在计划内汇总，再交由综合评价表单计算。 */
const SCORE_AGGREGATION_OPTIONS = [
  { label: '算术平均', value: 'AVERAGE' },
  { label: '加权平均', value: 'WEIGHTED_AVERAGE' }
] as const;
const emit = defineEmits<{
  addStage: [];
  removeStage: [index: number];
  addContent: [stage: TrainingPlanStage];
  removeContent: [stage: TrainingPlanStage, index: number];
  contentTypeChange: [content: TrainingPlanContent];
  save: [];
  back: [];
  next: [];
  resourceChange: [content: TrainingPlanContent, resourceId: number];
  addRequiredContent: [content: ComplianceSummary['requiredContents'][number], stageIndex: number];
  precheckFocusConsumed: [];
}>();

/** 打开阶段选择，避免快捷添加时默认落入用户未预期的阶段。 */
function openRequiredContentDialog(content: ComplianceSummary['requiredContents'][number]): void {
  pendingRequiredContent.value = content;
  pendingStageIndex.value = 0;
  if (!props.stages.length) {
    emit('addRequiredContent', content, 0);
    return;
  }
  requiredContentDialogVisible.value = true;
}

/** 将指定内容添加到用户选择的阶段。 */
function confirmRequiredContent(): void {
  if (!pendingRequiredContent.value) return;
  emit('addRequiredContent', pendingRequiredContent.value, pendingStageIndex.value);
  requiredContentDialogVisible.value = false;
}

/** 将表格行收窄为培训计划内容，保证事件的业务类型明确。 */
function asContent(row: unknown): TrainingPlanContent {
  return row as TrainingPlanContent;
}

/** 考试和题库练习不填写安排学时。 */
function isAssessmentContent(contentType: TrainingPlanContent['contentType']): boolean {
  return isAssessmentTrainingContent(contentType);
}

/** 题库练习只提供练习反馈，固定不参与培训或综合成绩。 */
function isQuestionBankContent(contentType: TrainingPlanContent['contentType']): boolean {
  return contentType === 'QUESTION_BANK';
}

/** 只有计入综合成绩的课程、考试、线下内容需要类别内汇总配置。 */
function needsScoreAggregation(content: TrainingPlanContent): boolean {
  return !isQuestionBankContent(content.contentType) && content.isScored === 1;
}

/** 算术平均没有内容权重，切换时立即清除遗留值。 */
function changeScoreAggregation(content: TrainingPlanContent): void {
  if (content.scoreAggregationMode !== 'WEIGHTED_AVERAGE') content.scoreWeight = undefined;
}

/** 返回与当前内容类型匹配的已发布资源。 */
function resourceOptions(type: TrainingPlanContent['contentType']): TrainingReferenceResource[] {
  return props.resources.filter((item) => item.resourceType === type);
}

/** 阶段类型在计划和模板中共用同一组中文展示。 */
function stageTypeText(type: TrainingPlanStage['stageType']): string {
  return { ONLINE: '线上', OFFLINE: '线下', MIXED: '混合' }[type] ?? type;
}

/** 深拷贝配置草稿，确保关闭或取消弹窗不会污染阶段内容。 */
function openExecutionConfig(content: TrainingPlanContent): void {
  editingContent.value = content;
  executionDialogVisible.value = true;
}

/** 消费预检定位请求，直接打开对应内容的配置抽屉。 */
watch(
  () => props.focusContentId,
  (contentId) => {
    if (!contentId || props.readonly) return;
    const content = props.stages.flatMap((stage) => stage.contents ?? []).find((item) => item.planContentId === contentId);
    if (content) openExecutionConfig(content);
    emit('precheckFocusConsumed');
  },
  { flush: 'post' }
);

/** 按后端发布预检规则检查当前内容的执行配置完整性。 */
function validateExecutionDraft(content: TrainingPlanContent): string[] {
  const config = content.executionConfig;
  if (content.contentType === 'COURSE') {
    return config?.completionRule === 'ALL_REQUIRED_ITEMS' || config?.completionRule === 'ALL_ITEMS' ? [] : ['请选择课程完成规则'];
  }
  if (content.contentType === 'EXAM') {
    return content.assessmentMode === 'QUIZ' || content.assessmentMode === 'FORMAL_EXAM' ? [] : ['请选择考试模式'];
  }
  if (content.contentType === 'QUESTION_BANK') {
    const issues: string[] = [];
    if (!config?.durationSeconds || config.durationSeconds <= 0) issues.push('请输入大于 0 的练习时长');
    if (!config?.maxAttempts || config.maxAttempts <= 0) issues.push('请输入大于 0 的最大作答次数');
    if (!config?.drawRules?.length) issues.push('请至少添加一条抽题规则');
    return issues;
  }
  const issues: string[] = [];
  if (!config?.teacherUserId) issues.push('请选择授课教师');
  if (!config?.location?.trim()) issues.push('请输入培训地点');
  if (!config?.startTime || !config.endTime) issues.push('请选择授课开始和结束时间');
  if (config?.passScore === undefined || config.passScore === null || config.passScore < 0) issues.push('请输入有效的及格分数');
  return issues;
}

/** 内容行直接展示执行配置是否满足发布必填项。 */
function isExecutionConfigured(content: TrainingPlanContent): boolean {
  return validateExecutionDraft(content).length === 0;
}

/** 接收通用执行配置弹窗结果，只写回计划内容实际使用的字段。 */
function applyExecutionConfig(draft: TrainingPlanContent): void {
  const target = editingContent.value;
  if (!draft || !target) return;
  let executionConfig = draft.executionConfig;
  if (draft.contentType === 'COURSE') {
    executionConfig = { completionRule: draft.executionConfig?.completionRule };
  } else if (draft.contentType === 'QUESTION_BANK') {
    // 题库练习不再提交历史分值字段，保持与当前题库练习契约一致。
    executionConfig = {
      durationSeconds: draft.executionConfig?.durationSeconds,
      maxAttempts: draft.executionConfig?.maxAttempts,
      drawRules: draft.executionConfig?.drawRules?.map((rule, index) => {
        const { scorePerQuestion, ...ruleWithoutScore } = rule;
        void scorePerQuestion;
        return { ...ruleWithoutScore, sortOrder: index + 1 };
      })
    };
  } else if (draft.contentType === 'OFFLINE') {
    executionConfig = {
      teacherUserId: draft.executionConfig?.teacherUserId,
      location: draft.executionConfig?.location?.trim(),
      startTime: draft.executionConfig?.startTime,
      endTime: draft.executionConfig?.endTime,
      passScore: draft.executionConfig?.passScore
    };
  }
  target.assessmentMode = draft.contentType === 'EXAM' ? draft.assessmentMode : undefined;
  target.executionConfig = executionConfig;
}
</script>

<template>
  <ElCard shadow="never" class="admin-detail-page__card">
    <template #header>
      <div class="card-header">
        <div>
          <h2>阶段内容编排</h2>
          <p>按执行顺序维护阶段和培训内容，保存后方可进入评价表单。</p>
        </div>
        <ElButton v-if="!props.readonly" type="primary" plain @click="emit('addStage')">新增阶段</ElButton>
      </div>
    </template>
    <div class="admin-detail-page__form-scroll stages-scroll">
      <ElAlert v-if="props.readonly" title="计划发布后阶段与内容已冻结，只读展示后端快照。" type="info" :closable="false" class="readonly-alert" />
      <section v-if="props.showCompliance !== false" class="composition-board">
        <div class="composition-board__heading">
          <div>
            <h3>编排依据</h3>
            <p>
              {{ props.complianceSummary.sourceTemplateText }}；{{ props.complianceSummary.typeText }}；{{ props.complianceSummary.totalText }}；{{
                props.complianceSummary.ratioText
              }}
            </p>
          </div>
          <ElTag :type="props.complianceSummary.ruleCoverage.every((item) => item.satisfied) ? 'success' : 'warning'">
            {{ props.complianceSummary.ruleCoverage.every((item) => item.satisfied) ? '规则已满足' : '仍需补齐' }}
          </ElTag>
        </div>
        <div class="metric-grid">
          <div v-for="metric in props.complianceSummary.metrics" :key="metric.label" class="metric-card" :class="{ 'is-warning': !metric.satisfied }">
            <span>{{ metric.label }}</span>
            <strong>{{ metric.current }}</strong>
            <small>目标：{{ metric.target }}</small>
          </div>
        </div>
        <div class="required-content-list">
          <div class="required-content-list__heading">
            <div>
              <strong>标准指定内容</strong>
              <span>标准要求的课程、考试和线下培训集中展示，可添加到指定阶段。</span>
            </div>
          </div>
          <div v-if="props.complianceSummary.requiredContents.length" class="required-content-grid">
            <div
              v-for="item in props.complianceSummary.requiredContents"
              :key="`${item.ruleType}-${item.resourceId ?? item.name}`"
              class="required-content-item"
            >
              <div class="required-content-item__body">
                <ElTag size="small" effect="plain">{{ item.ruleType === 'COURSE' ? '课程' : item.ruleType === 'EXAM' ? '考试' : '线下培训' }}</ElTag>
                <strong :title="item.name">{{ item.name }}</strong>
                <span v-if="item.description !== item.name" :title="item.description">{{ item.description }}</span>
              </div>
              <ElTag v-if="item.satisfied" type="success" size="small">已添加</ElTag>
              <ElButton v-else type="primary" link :disabled="props.readonly" @click="openRequiredContentDialog(item)">添加到阶段</ElButton>
            </div>
          </div>
          <div v-else class="rule-empty-state">
            <div class="rule-empty-state__icon" aria-hidden="true">自定义</div>
            <div class="rule-empty-state__body">
              <strong>当前为自定义编排</strong>
              <p>当前计划未配置标准指定内容，不影响继续维护阶段和培训内容；后续也可以返回基础信息补充培训标准。</p>
              <div class="rule-empty-state__guide">
                <span><i>1</i>新增阶段</span>
                <span><i>2</i>添加课程、考试或线下培训</span>
                <span><i>3</i>保存后进入下一步</span>
              </div>
            </div>
            <div class="rule-empty-state__actions">
              <ElButton link type="primary" :disabled="props.readonly" @click="emit('back')">返回关联培训标准</ElButton>
              <ElButton v-if="!props.readonly" type="primary" plain @click="emit('addStage')">新增阶段</ElButton>
            </div>
          </div>
        </div>
      </section>
      <ElEmpty v-if="props.stages.length === 0" description="暂无阶段，请新增后开始编排" class="stages-empty">
        <ElButton v-if="!props.readonly" type="primary" @click="emit('addStage')">新增第一个阶段</ElButton>
      </ElEmpty>
      <article v-for="(stage, stageIndex) in props.stages" :key="stage.planStageId ?? stageIndex" class="stage-card">
        <header class="stage-card__header">
          <div class="stage-card__index">{{ String(stageIndex + 1).padStart(2, '0') }}</div>
          <div>
            <h3>{{ stage.stageName || `阶段 ${stageIndex + 1}` }}</h3>
            <p>{{ stageTypeText(stage.stageType) }} · 阶段要求 {{ stage.requiredHours ?? 0 }} 学时 · 内容 {{ stage.contents?.length ?? 0 }} 项</p>
          </div>
          <ElButton v-if="!props.readonly" link type="danger" @click="emit('removeStage', stageIndex)">删除阶段</ElButton>
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
                  <ElOption label="线上" value="ONLINE" />
                  <ElOption label="线下" value="OFFLINE" />
                  <ElOption label="混合" value="MIXED" />
                </ElSelect>
              </ElFormItem>
            </ElCol>
            <ElCol :span="8">
              <ElFormItem label="要求学时">
                <ElInputNumber v-model="stage.requiredHours" :disabled="props.readonly" :min="0" :precision="1" />
              </ElFormItem>
            </ElCol>
          </ElRow>
        </ElForm>
        <div class="content-heading">
          <div>
            <h4>阶段内容</h4>
            <span>资源与内容属性将随阶段一并覆盖保存。</span>
          </div>
          <ElButton v-if="!props.readonly" link type="primary" @click="emit('addContent', stage)">新增内容</ElButton>
        </div>
        <ElTable :data="stage.contents ?? []" class="content-table" border>
          <ElTableColumn min-width="140">
            <template #header>
              内容类型
              <i class="required-asterisk" aria-hidden="true">*</i>
            </template>
            <template #default="{ row }">
              <ElSelect
                v-model="row.contentType"
                :disabled="props.readonly"
                placeholder="请选择内容类型"
                @change="emit('contentTypeChange', asContent(row))"
              >
                <ElOption label="课程" value="COURSE" />
                <ElOption label="考试" value="EXAM" />
                <ElOption label="题库练习" value="QUESTION_BANK" />
                <ElOption label="线下培训" value="OFFLINE" />
              </ElSelect>
            </template>
          </ElTableColumn>
          <ElTableColumn min-width="220">
            <template #header>
              内容名称
              <i class="required-asterisk" aria-hidden="true">*</i>
            </template>
            <template #default="{ row }">
              <ElInput
                v-model="row.contentName"
                :disabled="props.readonly || row.contentType !== 'OFFLINE'"
                :placeholder="row.contentType === 'OFFLINE' ? '请输入线下内容名称' : '请选择内容资源'"
              />
            </template>
          </ElTableColumn>
          <ElTableColumn label="内容资源（线上内容必填）" min-width="240">
            <template #default="{ row }">
              <ElTag v-if="row.contentType === 'OFFLINE'" type="info">线下内容无需绑定资源</ElTag>
              <ElSelect
                v-else
                v-model="row.resourceId"
                :disabled="props.readonly"
                filterable
                placeholder="请选择已发布且可用的资源"
                @change="emit('resourceChange', asContent(row), Number($event))"
              >
                <ElOption
                  v-for="resource in resourceOptions(asContent(row).contentType)"
                  :key="resource.resourceId"
                  :label="resource.resourceName"
                  :value="resource.resourceId"
                />
              </ElSelect>
            </template>
          </ElTableColumn>
          <ElTableColumn label="必修" width="80">
            <template #default="{ row }">
              <ElSwitch v-model="row.isRequired" :disabled="props.readonly" :active-value="1" :inactive-value="0" />
            </template>
          </ElTableColumn>
          <ElTableColumn label="计入综合成绩" width="200">
            <template #default="{ row }">
              <ElTag v-if="isQuestionBankContent(row.contentType)" type="info">题库练习不计分</ElTag>
              <ElSwitch
                v-else
                v-model="row.isScored"
                class="score-switch"
                :disabled="props.readonly"
                :active-value="1"
                :inactive-value="0"
                active-text="计入"
                inactive-text="不计入"
              />
            </template>
          </ElTableColumn>
          <ElTableColumn label="同类成绩汇总" min-width="300">
            <template #default="{ row }">
              <span v-if="!needsScoreAggregation(asContent(row))" class="content-hours-note">不参与</span>
              <div v-else class="score-aggregation-cell">
                <ElSelect
                  v-model="row.scoreAggregationMode"
                  :disabled="props.readonly"
                  placeholder="请选择汇总方式"
                  @change="changeScoreAggregation(asContent(row))"
                >
                  <ElOption v-for="option in SCORE_AGGREGATION_OPTIONS" :key="option.value" :label="option.label" :value="option.value" />
                </ElSelect>
                <div v-if="row.scoreAggregationMode === 'WEIGHTED_AVERAGE'" class="score-weight-field">
                  <span class="score-weight-field__label">内容权重</span>
                  <ElInputNumber
                    v-model="row.scoreWeight"
                    :disabled="props.readonly"
                    :min="0.01"
                    :max="1"
                    :step="0.1"
                    aria-label="内容权重"
                  />
                </div>
              </div>
            </template>
          </ElTableColumn>
          <ElTableColumn label="学时" width="160">
            <template #default="{ row }">
              <span v-if="isAssessmentContent(row.contentType)" class="content-hours-note">0 学时（不计入）</span>
              <ElInputNumber v-else v-model="row.requiredHours" :disabled="props.readonly" :min="0" :precision="1" />
            </template>
          </ElTableColumn>
          <ElTableColumn width="190">
            <template #header>
              执行配置
              <i class="required-asterisk" aria-hidden="true">*</i>
            </template>
            <template #default="{ row }">
              <div class="execution-config-cell">
                <ElTag :type="isExecutionConfigured(asContent(row)) ? 'success' : 'warning'">
                  {{ isExecutionConfigured(asContent(row)) ? '已配置' : '待配置' }}
                </ElTag>
                <ElButton link type="primary" @click="openExecutionConfig(asContent(row))">
                  {{ props.readonly ? '查看配置' : '配置' }}
                </ElButton>
              </div>
            </template>
          </ElTableColumn>
          <ElTableColumn v-if="!props.readonly" label="操作" width="80">
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
              :key="stage.planStageId ?? index"
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
    <footer v-if="!props.readonly" class="sticky-actions">
      <ElButton @click="emit('back')">上一步</ElButton>
      <ElButton :type="props.canNext ? 'default' : 'primary'" @click="emit('save')">保存阶段内容</ElButton>
      <ElButton type="primary" :disabled="!props.canNext" @click="emit('next')">下一步：评价表单</ElButton>
    </footer>

    <TrainingExecutionConfigDialog
      v-model:visible="executionDialogVisible"
      :content="editingContent"
      :teacher-options="props.teacherOptions"
      :readonly="props.readonly"
      @confirm="applyExecutionConfig"
    />
  </ElCard>
</template>

<style scoped lang="less">
.required-asterisk {
  color: var(--el-color-danger);
  font-style: normal;
  font-weight: 600;
}
.card-header,
.content-heading {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
}
.card-header h2,
.stage-card h3,
.content-heading h4,
.composition-board h3 {
  margin: 0;
  color: var(--color-text-primary);
  font-weight: 600;
}
.card-header p,
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
.readonly-alert,
.composition-board {
  margin-bottom: 16px;
}
.composition-board {
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
.required-content-list {
  margin-top: 14px;
  padding-top: 14px;
  border-top: 1px solid var(--color-border);
}
.required-content-list__heading {
  display: flex;
  justify-content: space-between;
  margin-bottom: 10px;
}
.required-content-list__heading strong,
.required-content-list__heading span {
  display: block;
}
.required-content-list__heading strong {
  color: var(--color-text-primary);
  font-size: 13px;
  line-height: 18px;
}
.required-content-list__heading span {
  margin-top: 2px;
  color: var(--color-text-secondary);
  font-size: 12px;
  line-height: 18px;
}
.required-content-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 8px;
}
.rule-empty-state {
  display: flex;
  align-items: center;
  gap: 16px;
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
.required-content-stage-select {
  width: 100%;
}
.required-content-item {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 10px;
  min-width: 0;
  padding: 9px 10px;
  border: 1px solid var(--color-border);
  border-radius: 6px;
  background: var(--color-bg-subtle);
}
.required-content-item__body {
  display: flex;
  align-items: center;
  gap: 8px;
  min-width: 0;
}
.required-content-item__body strong,
.required-content-item__body span {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.required-content-item__body strong {
  color: var(--color-text-primary);
  font-size: 13px;
}
.required-content-item__body span {
  color: var(--color-text-secondary);
  font-size: 12px;
}
.stage-card {
  margin-bottom: 16px;
  overflow: hidden;
  border: 1px solid var(--color-border);
  border-radius: var(--radius-card);
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
.stage-form {
  padding: 20px 18px 4px;
}
.stage-form :deep(.el-select),
.stage-form :deep(.el-input-number),
.content-table :deep(.el-select),
.content-table :deep(.el-input-number) {
  width: 100%;
}
.content-heading {
  padding: 14px 18px;
  border-top: 1px solid var(--color-border);
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
.score-aggregation-cell {
  display: grid;
  min-width: 264px;
  gap: 8px;
}
.score-weight-field {
  display: grid;
  grid-template-columns: auto minmax(0, 1fr);
  align-items: center;
  gap: 10px;
  padding: 6px 8px 6px 10px;
  border: 1px solid var(--color-border);
  border-radius: 6px;
  background: var(--color-bg-subtle);
}
.score-weight-field__label {
  color: var(--color-text-secondary);
  font-size: 12px;
  white-space: nowrap;
}
.score-weight-field :deep(.el-input-number) {
  width: 100%;
}
@media (max-width: 900px) {
  .stage-card__header {
    grid-template-columns: 36px minmax(0, 1fr);
  }
  .stage-card__header > .el-button {
    grid-column: 2;
    justify-self: start;
  }
  .composition-board__heading {
    align-items: stretch;
    flex-direction: column;
  }
  .metric-grid,
  .required-content-grid {
    grid-template-columns: 1fr;
  }
  .rule-empty-state {
    align-items: stretch;
    flex-direction: column;
  }
  .rule-empty-state__actions {
    flex-wrap: wrap;
  }
  .content-table {
    min-width: 760px;
  }
  .stages-scroll {
    overflow-x: auto;
  }
}
/* “计入 / 不计入”开关文案保持单行，避免窄列下被折行。 */
.score-switch :deep(.el-switch__label) {
  white-space: nowrap;
}
</style>
