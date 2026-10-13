<script setup lang="ts">
import { computed, ref, watch } from 'vue';
import { ElMessage, ElMessageBox } from 'element-plus';
import { useUserStore } from '@/stores';
import { hasPermission } from '@/utils/permission';
import type { TrainingPlan, TrainingPlanStage, TrainingPlanContent, TrainingParticipant } from '@/api/training-plan';
import type { TrainingType } from '@/api/training-type';
import type { TrainingStandard } from '@/api/training-standard';
import type { TrainingTemplate } from '@/api/training-template';
import type { TrainingReferenceResource } from '@/api/training-reference';
import type { SystemUser } from '@/api/system-user';
import type { EvaluationFormVersionSummary } from '@/api/evaluation-form';
import type { PersonSelectionRequest } from '@/api/person-selection';
import { getPlanApprovers, getAnnualPlans, type PlanApprover, type AnnualPlan } from '@/api/training-approval';
import { getPlanChanges, savePlanChange, submitPlanChange, decidePlanChange, type PlanChange, type PlanChangePayload } from '@/api/training-change';
import PersonSelectionDialog from '@/components/organization/PersonSelectionDialog.vue';
import PlanBasicPanel from './PlanBasicPanel.vue';
import PlanStagesPanel from './PlanStagesPanel.vue';
/** 独立变更编辑与原计划工作台互不写入；批准后父工作台回读生效数据。 */
const props = defineProps<{
  plan: TrainingPlan;
  participants: TrainingParticipant[];
  trainingTypes: TrainingType[];
  trainingStandards: TrainingStandard[];
  trainingTemplates: TrainingTemplate[];
  resources: TrainingReferenceResource[];
  users: SystemUser[];
  formVersions: Array<EvaluationFormVersionSummary & { formType: 'COMPOSITE' | 'EXPERT_INSPECTION'; formName?: string }>;
}>();
const emit = defineEmits<{ changed: [] }>();
const user = useUserStore();
const rows = ref<PlanChange[]>([]);
const loading = ref(false);
const saving = ref(false);
const error = ref('');
const editorVisible = ref(false);
const editorReadonly = ref(false);
const tab = ref('basic');
const editing = ref<PlanChange>();
const payload = ref<PlanChangePayload>();
const approvers = ref<PlanApprover[]>([]);
const annuals = ref<AnnualPlan[]>([]);
const submitVisible = ref(false);
const submitting = ref<PlanChange>();
const approverId = ref<number>();
const selectionVisible = ref(false);
/** 中文状态和人员范围与契约枚举一一对应。 */
const STATUS_LABELS = { DRAFT: '草稿', PENDING: '待审批', APPROVED: '已批准生效', REJECTED: '已驳回' } as const;
const SELECTION_LABELS = { ALL: '全部人员', DEPT: '部门', USER: '指定人员', POSITION: '岗位', GROUP: '用户组' } as const;
const canChange = computed(() => hasPermission(user.userInfo, 'training:plan:change'));
const basicAllowed = computed(() => ['PUBLISHED', 'RUNNING'].includes(props.plan.publishStatus));
const full = computed(() => payload.value?.full);
const basic = computed(() => payload.value?.basic);
/** 既有基础组件要求显示编码，该字段仅供显示，服务端不会修改编码。 */
const fullModel = computed(() =>
  full.value
    ? { ...full.value.plan, planCode: props.plan.planCode, location: full.value.plan.location || '', description: full.value.plan.description || '' }
    : undefined
);
const editableFullModel = ref<{
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
}>();
const stageSummary = {
  sourceTemplateText: '变更草稿',
  typeText: '批准前原计划继续有效',
  totalText: '请核对全部阶段和要求学时',
  ratioText: '生效时执行服务端校验',
  metrics: [],
  ruleCoverage: [],
  requiredContents: []
};
const basicRules = {
  planName: [{ required: true, message: '请输入计划名称', trigger: 'blur' }],
  trainingTypeId: [{ required: true, message: '请选择培训类型', trigger: 'change' }]
};
const annualChanged = ref(false);
const annualId = ref<number>();
let sequence = 0;
/** 乱序请求不能覆盖另一计划的变更列表。 */
async function load(): Promise<void> {
  const requestId = ++sequence;
  loading.value = true;
  error.value = '';
  try {
    const [changes, people, files] = await Promise.all([
      getPlanChanges(props.plan.trainingPlanId),
      canChange.value ? getPlanApprovers() : Promise.resolve([]),
      hasPermission(user.userInfo, 'training:annual:list') ? getAnnualPlans() : Promise.resolve([])
    ]);
    if (requestId !== sequence) return;
    rows.value = changes;
    approvers.value = people;
    annuals.value = files;
  } catch (e) {
    if (requestId === sequence) {
      rows.value = [];
      error.value = e instanceof Error ? e.message : '变更列表读取失败';
    }
  } finally {
    if (requestId === sequence) loading.value = false;
  }
}
/** 从生效计划或已保存草稿创建深拷贝，不让弹窗编辑污染原对象。 */
function open(type: 'BASIC' | 'FULL', row?: PlanChange, readonly = false): void {
  editing.value = row;
  editorReadonly.value = readonly;
  tab.value = 'basic';
  annualChanged.value = false;
  annualId.value = undefined;
  payload.value = row ? JSON.parse(JSON.stringify(row.request)) : { baseVersion: props.plan.version, changeType: type };
  if (row) payload.value!.version = row.version;
  else if (type === 'BASIC')
    payload.value!.basic = {
      planName: props.plan.planName,
      managerUserId: props.plan.managerUserId,
      location: props.plan.location || '',
      description: props.plan.description || ''
    };
  else
    payload.value!.full = {
      plan: { ...props.plan, trainingNature: props.plan.trainingNature === 'UNSPECIFIED' ? 'INITIAL' : props.plan.trainingNature },
      stages: JSON.parse(JSON.stringify(props.plan.stages || [])),
      forms: JSON.parse(JSON.stringify(props.plan.evaluationForms || [])),
      participantSelection: { selectionType: 'USER', subjectIds: props.participants.map((p) => p.userId) }
    };
  if (fullModel.value)
    editableFullModel.value = {
      ...fullModel.value,
      trainingStandardId: fullModel.value.trainingStandardId ?? undefined,
      sourceTemplateId: fullModel.value.sourceTemplateId ?? undefined
    };
  if (full.value?.annual) {
    annualChanged.value = true;
    annualId.value = full.value.annual.annualPlanId ?? undefined;
  }
  editorVisible.value = true;
}
/** 每次保存完整请求都进入独立草稿端点，绝不调用原计划编辑/发布。 */
async function save(): Promise<void> {
  if (!payload.value || editorReadonly.value) return;
  saving.value = true;
  try {
    if (full.value && editableFullModel.value) {
      Object.assign(full.value.plan, editableFullModel.value, {
        trainingStandardId: editableFullModel.value.trainingStandardId ?? null,
        sourceTemplateId: editableFullModel.value.sourceTemplateId ?? null
      });
      full.value.annual = annualChanged.value ? { version: props.plan.version, annualPlanId: annualId.value ?? null } : undefined;
    }
    await savePlanChange(props.plan.trainingPlanId, payload.value, editing.value?.changeId);
    ElMessage.success('变更草稿已保存，原计划继续有效');
    editorVisible.value = false;
    await load();
  } catch (e) {
    ElMessage.error(e instanceof Error ? e.message : '保存变更失败');
  } finally {
    saving.value = false;
  }
}
/** 打开指定审批人弹窗，清空上一次选择。 */
function startSubmit(row: PlanChange): void {
  submitting.value = row;
  approverId.value = undefined;
  submitVisible.value = true;
}
/** 提交当前变更版本，成功后回读审批记录。 */
async function submit(): Promise<void> {
  if (!submitting.value || !approverId.value) return;
  saving.value = true;
  try {
    await submitPlanChange(props.plan.trainingPlanId, submitting.value.changeId, submitting.value.version, approverId.value);
    ElMessage.success('变更已提交审批，原计划继续有效');
    submitVisible.value = false;
    await load();
  } catch (e) {
    ElMessage.error(e instanceof Error ? e.message : '变更提交失败');
  } finally {
    saving.value = false;
  }
}
/** 只有申请人可维护草稿或驳回记录。 */
function canEdit(row: PlanChange): boolean {
  return canChange.value && row.submitterUserId === user.userInfo?.userId && ['DRAFT', 'REJECTED'].includes(row.changeStatus);
}
/** 仅指定且拥有审批权限的人员可作出结论。 */
function canDecide(row: PlanChange): boolean {
  return hasPermission(user.userInfo, 'training:plan:approve') && row.changeStatus === 'PENDING' && row.approverUserId === user.userInfo?.userId;
}
/** 批准失败不会关闭原计划或伪报生效；驳回原因必须非空。 */
async function decide(row: PlanChange, allow: boolean): Promise<void> {
  try {
    const result = await ElMessageBox.prompt(allow ? '批准后立即生效，请先查看变更详情' : '请输入驳回原因', allow ? '批准变更' : '驳回变更', {
      inputValidator: (value) => allow || Boolean(value?.trim()) || '请输入驳回原因'
    });
    saving.value = true;
    await decidePlanChange(props.plan.trainingPlanId, row.changeId, allow, result.value || '');
    ElMessage.success(allow ? '变更已批准生效' : '变更已驳回');
    emit('changed');
    await load();
  } catch (e) {
    if (e !== 'cancel' && e !== 'close') ElMessage.error(e instanceof Error ? e.message : '审批失败');
  } finally {
    saving.value = false;
  }
}
/** 复用课表组件，顺序始终连续。 */
/** 在独立变更草稿追加阶段。 */
function addStage(): void {
  full.value?.stages.push({ stageName: '', stageType: 'ONLINE', sortOrder: full.value.stages.length + 1, contents: [] });
}
/** 删除草稿阶段并同步排序。 */
function removeStage(index: number): void {
  full.value?.stages.splice(index, 1);
  full.value?.stages.forEach((s, i) => (s.sortOrder = i + 1));
}
/** 追加可编辑内容，复用课程编排默认值。 */
function addContent(stage: TrainingPlanStage): void {
  (stage.contents ??= []).push({
    contentType: 'COURSE',
    contentName: '',
    isRequired: 1,
    isScored: 1,
    scoreAggregationMode: 'AVERAGE',
    requiredHours: 0,
    sortOrder: stage.contents.length + 1
  });
}
/** 删除草稿内容并同步排序。 */
function removeContent(stage: TrainingPlanStage, index: number): void {
  stage.contents?.splice(index, 1);
  stage.contents?.forEach((c, i) => (c.sortOrder = i + 1));
}
/** 切换类型时清理不适用的资源与执行配置。 */
function changeType(content: TrainingPlanContent): void {
  Object.assign(content, {
    resourceId: undefined,
    resourceVersionId: undefined,
    assessmentMode: undefined,
    executionConfig: undefined,
    contentName: '',
    scoreWeight: undefined,
    isScored: content.contentType === 'QUESTION_BANK' ? 0 : 1,
    scoreAggregationMode: content.contentType === 'QUESTION_BANK' ? undefined : 'AVERAGE'
  });
  if (['EXAM', 'QUESTION_BANK'].includes(content.contentType)) content.requiredHours = 0;
}
/** 选择资源时冻结明确课程版本及显示名称。 */
function resourceChange(content: TrainingPlanContent, id: number): void {
  const r = props.resources.find((item) => item.resourceId === id && item.resourceType === content.contentType);
  if (r) Object.assign(content, { resourceId: r.resourceId, resourceVersionId: r.versionId, contentName: r.resourceName });
}
/** 保存拟变更人员条件，服务端在提交时展开。 */
function setSelection(selection: PersonSelectionRequest): void {
  if (full.value) full.value.participantSelection = selection;
}
/** 评价用途使用已有已发布版本下拉，避免手输版本编号。 */
function setForm(usage: 'COMPOSITE' | 'EXPERT_INSPECTION', versionId?: number): void {
  if (!full.value) return;
  full.value.forms = full.value.forms.filter((f) => f.usageType !== usage);
  if (versionId) full.value.forms.push({ formVersionId: versionId, usageType: usage, isRequired: 1, status: 1 });
}
/** 表格行来自变更契约，收窄通用行类型。 */
function asChange(row: unknown): PlanChange {
  return row as PlanChange;
}
watch(
  () => [props.plan.trainingPlanId, props.plan.version],
  () => void load(),
  { immediate: true }
);
/** 变更状态与类型只在面板内映射，避免把后端枚举原值直接渲染给用户。 */
function changeStatusText(status: string): string {
  return STATUS_LABELS[status as keyof typeof STATUS_LABELS] ?? '未知状态';
}
function changeStatusTagType(status: string): 'info' | 'warning' | 'success' | 'danger' {
  if (status === 'APPROVED') return 'success';
  if (status === 'REJECTED') return 'danger';
  if (status === 'PENDING') return 'warning';
  return 'info';
}
function changeTypeText(type: string): string {
  return type === 'FULL' ? '完整变更' : '基础信息变更';
}
</script>
<template>
  <ElCard v-loading="loading || saving" shadow="never" class="admin-detail-page__card change-panel">
    <template #header>
      <div class="card-header change-panel__header">
        <div>
          <h2>计划变更审批</h2>
          <p>批准前原计划继续有效，审批记录单独保存。</p>
        </div>
        <div v-if="canChange && basicAllowed && !error" class="change-panel__actions">
          <ElButton size="small" @click="open('BASIC')">申请基础信息变更</ElButton>
          <ElButton v-if="plan.publishStatus === 'PUBLISHED'" size="small" type="primary" plain @click="open('FULL')">申请完整变更</ElButton>
        </div>
      </div>
    </template>
    <div class="admin-detail-page__form-scroll change-panel__body">
      <ElAlert
        title="批准前原计划继续有效；进行中仅允许名称、负责人、地点、说明。已有安排或事实无法安全同步时完整变更会被拒绝。"
        type="info"
        :closable="false"
        show-icon
      />
      <ElAlert v-if="error" :title="error" type="error" :closable="false" />
      <ElButton v-if="error" @click="load">重新读取</ElButton>
      <!-- 变更申请用列表承载：侧栏窄列里每行先给结论（类型 + 状态），操作按钮独立成行。 -->
      <ul v-if="rows.length" class="plan-records">
        <li v-for="row in rows" :key="row.changeId" class="plan-record">
          <div class="plan-record__head">
            <strong>{{ changeTypeText(row.request.changeType) }} · 基准版本 V{{ row.baseVersion }}</strong>
            <ElTag :type="changeStatusTagType(row.changeStatus)" effect="plain" size="small">{{ changeStatusText(row.changeStatus) }}</ElTag>
          </div>
          <p v-if="row.decisionComment" class="plan-record__comment">审批意见：{{ row.decisionComment }}</p>
          <div class="change-record__actions">
            <ElButton link type="primary" @click="open(row.request.changeType, asChange(row), true)">查看内容</ElButton>
            <ElButton v-if="canEdit(asChange(row))" link type="primary" @click="open(row.request.changeType, asChange(row))">编辑</ElButton>
            <ElButton v-if="canEdit(asChange(row)) && row.changeStatus === 'DRAFT'" link type="primary" @click="startSubmit(asChange(row))">
              提交审批
            </ElButton>
            <ElButton v-if="canDecide(asChange(row))" link type="success" @click="decide(asChange(row), true)">批准</ElButton>
            <ElButton v-if="canDecide(asChange(row))" link type="danger" @click="decide(asChange(row), false)">驳回</ElButton>
          </div>
        </li>
      </ul>
      <ElEmpty v-else description="暂无变更申请" :image-size="56" />
    </div>
  </ElCard>
  <ElDialog v-model="editorVisible" :title="editorReadonly ? '查看变更内容' : '编辑独立变更草稿'" width="min(1200px, 94vw)" destroy-on-close>
    <ElAlert v-if="editing" :title="`原计划：${editing.before.planName}；基准版本：${editing.baseVersion}`" type="info" :closable="false" />
    <ElCollapse v-if="editing" style="margin: 16px 0">
      <ElCollapseItem title="查看原计划与名单快照" name="before">
        <ElDescriptions :column="2" border>
          <ElDescriptionsItem label="计划名称">{{ editing.before.planName }}</ElDescriptionsItem>
          <ElDescriptionsItem label="负责人">
            {{ users.find((u) => u.userId === editing?.before.managerUserId)?.userName || '未指定' }}
          </ElDescriptionsItem>
          <ElDescriptionsItem label="起止时间">{{ editing.before.startTime }} 至 {{ editing.before.endTime }}</ElDescriptionsItem>
          <ElDescriptionsItem label="地点">{{ editing.before.location || '未填写' }}</ElDescriptionsItem>
          <ElDescriptionsItem label="说明">{{ editing.before.description || '未填写' }}</ElDescriptionsItem>
        </ElDescriptions>
        <p>
          原参训名单：{{ editing.beforeParticipants.map((p) => users.find((u) => u.userId === p.userId)?.userName || `人员 ${p.userId}`).join('、') }}
        </p>
        <ElTable
          :data="(editing.before.stages || []).flatMap((s) => (s.contents || []).map((c) => ({ ...c, stageName: s.stageName })))"
          empty-text="原计划无编排"
        >
          <ElTableColumn prop="stageName" label="阶段" />
          <ElTableColumn prop="contentName" label="内容" />
          <ElTableColumn prop="resourceVersionId" label="资源版本" />
          <ElTableColumn prop="requiredHours" label="要求学时" />
        </ElTable>
      </ElCollapseItem>
    </ElCollapse>
    <ElForm v-if="basic" label-width="100px" :disabled="editorReadonly">
      <ElFormItem label="计划名称"><ElInput v-model="basic.planName" maxlength="150" placeholder="请输入计划名称" /></ElFormItem>
      <ElFormItem label="负责人">
        <ElSelect v-model="basic.managerUserId" clearable filterable placeholder="请选择负责人" style="width: 320px">
          <ElOption v-for="u in users" :key="u.userId" :value="u.userId" :label="u.userName" />
        </ElSelect>
      </ElFormItem>
      <ElFormItem label="地点"><ElInput v-model="basic.location" maxlength="255" placeholder="请输入培训地点" /></ElFormItem>
      <ElFormItem label="说明"><ElInput v-model="basic.description" type="textarea" maxlength="10000" placeholder="请输入变更说明" /></ElFormItem>
    </ElForm>
    <ElTabs v-if="full && editableFullModel" v-model="tab">
      <ElTabPane label="基础范围" name="basic">
        <PlanBasicPanel
          :model="editableFullModel"
          :rules="basicRules"
          :readonly="editorReadonly"
          :created="true"
          :saving="saving"
          :can-save="false"
          :can-next="true"
          :historical-nature="false"
          :training-types="trainingTypes"
          :training-standards="trainingStandards"
          :training-templates="trainingTemplates"
          :manager-users="users"
          @next="tab = 'stages'"
        />
      </ElTabPane>
      <ElTabPane label="阶段与内容" name="stages">
        <PlanStagesPanel
          :stages="full.stages"
          :resources="resources"
          :teacher-options="users"
          :readonly="editorReadonly"
          :can-next="true"
          :show-compliance="false"
          :compliance-summary="stageSummary"
          @add-stage="addStage"
          @remove-stage="removeStage"
          @add-content="addContent"
          @remove-content="removeContent"
          @content-type-change="changeType"
          @resource-change="resourceChange"
          @save="save"
          @back="tab = 'basic'"
          @next="tab = 'people'"
        />
      </ElTabPane>
      <ElTabPane label="人员、评价与年度依据" name="people">
        <p v-if="full.annualSnapshot">
          年度审批依据：{{ full.annualSnapshot.planYear }} · {{ full.annualSnapshot.planName }} · {{ full.annualSnapshot.fileName }}
        </p>
        <p>人员范围：{{ SELECTION_LABELS[full.participantSelection.selectionType] }}（提交时展开并冻结）</p>
        <p v-if="full.participantUserIds">
          提交时名单：{{ full.participantUserIds.map((id) => users.find((u) => u.userId === id)?.userName || `人员 ${id}`).join('、') }}
        </p>
        <ElButton v-if="!editorReadonly" @click="selectionVisible = true">重新选择参训范围</ElButton>
        <ElForm label-width="110px" :disabled="editorReadonly" style="margin-top: 16px">
          <ElFormItem
            v-for="usage in ['COMPOSITE', 'EXPERT_INSPECTION'] as const"
            :key="usage"
            :label="usage === 'COMPOSITE' ? '综合评价' : '专家检查'"
          >
            <ElSelect
              :model-value="full.forms.find((f) => f.usageType === usage)?.formVersionId"
              clearable
              placeholder="请选择已发布表单版本"
              style="width: 360px"
              @change="setForm(usage, $event)"
            >
              <ElOption
                v-for="f in formVersions.filter((item) => item.formType === usage)"
                :key="f.formVersionId"
                :value="f.formVersionId"
                :label="`${f.formName || `评价表单 ${f.evaluationFormId}`} · V${f.versionNo}`"
              />
            </ElSelect>
          </ElFormItem>
          <ElFormItem label="更新年度依据"><ElSwitch v-model="annualChanged" /></ElFormItem>
          <ElFormItem v-if="annualChanged" label="年度文件">
            <ElSelect v-model="annualId" clearable placeholder="选择年度文件，清空为解除关联" style="width: 360px">
              <ElOption v-for="a in annuals" :key="a.annualPlanId" :value="a.annualPlanId" :label="`${a.planYear} · ${a.planName}`" />
            </ElSelect>
          </ElFormItem>
        </ElForm>
      </ElTabPane>
    </ElTabs>
    <template #footer>
      <ElButton @click="editorVisible = false">关闭</ElButton>
      <ElButton v-if="!editorReadonly" type="primary" :loading="saving" @click="save">保存变更草稿</ElButton>
    </template>
  </ElDialog>
  <ElDialog v-model="submitVisible" title="提交变更审批" width="480px">
    <ElSelect v-model="approverId" filterable placeholder="请选择另一位有审批权限的人员" style="width: 100%">
      <ElOption v-for="u in approvers" :key="u.userId" :value="u.userId" :label="u.userName" />
    </ElSelect>
    <template #footer>
      <ElButton @click="submitVisible = false">取消</ElButton>
      <ElButton type="primary" :disabled="!approverId" :loading="saving" @click="submit">提交</ElButton>
    </template>
  </ElDialog>
  <PersonSelectionDialog
    v-if="full"
    v-model:visible="selectionVisible"
    title="完整变更参训范围"
    :model-value="full.participantSelection"
    @confirm="setSelection"
  />
</template>
<style scoped lang="less">
.change-panel__header {
  flex-wrap: wrap;
  row-gap: 8px;
}
.change-panel__actions {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
}
.change-panel__body {
  display: flex;
  min-height: 0;
  flex-direction: column;
  gap: 10px;
  padding: 12px 0 16px;
}
/* 变更/审批记录统一列表行，窄栏里不产生横向滚动。 */
.plan-records {
  display: grid;
  gap: 8px;
  margin: 0;
  padding: 0;
  list-style: none;
}
.plan-record {
  display: grid;
  gap: 6px;
  padding: 10px 12px;
  border: 1px solid var(--color-border);
  border-radius: 10px;
  background: var(--color-bg-subtle);
}
.plan-record__head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 8px;
}
.plan-record__head strong {
  min-width: 0;
  overflow: hidden;
  color: var(--color-text-primary);
  font-size: 13px;
  line-height: 18px;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.plan-record__comment {
  margin: 0;
  color: var(--color-text-regular);
  font-size: 12px;
  line-height: 18px;
}
.change-record__actions {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 4px 12px;
}
/* 操作按钮之间只用 grid gap 控制间距，避免叠加 Element Plus 默认外边距。 */
.change-record__actions :deep(.el-button + .el-button) {
  margin-left: 0;
}
/* 侧栏里的空状态只需一句提示，收回 Element Plus 默认的 40px 上下留白。 */
.change-panel__body :deep(.el-empty) {
  padding: 8px 0 2px;
}
</style>
