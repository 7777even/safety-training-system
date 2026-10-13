<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue';
import { useRoute, useRouter } from 'vue-router';
import { ElMessage, ElMessageBox, type FormRules } from 'element-plus';
import {
  assignTrainingPlanParticipant,
  applyTrainingPlanTemplate,
  bindTrainingPlanParticipantSelection,
  bindTrainingPlanEvaluationForms,
  cancelTrainingPlan,
  createTrainingPlan,
  endTrainingPlan,
  generateTrainingPlanExecutions,
  getTrainingParticipantResults,
  getTrainingPlan,
  getTrainingPlanExecutionLinks,
  getTrainingPlanParticipants,
  precheckTrainingPlanPublish,
  publishTrainingPlan,
  replaceTrainingPlanStages,
  startTrainingPlan,
  type TrainingExecutionGenerationResult,
  TrainingPlanApiError,
  type TrainingPublishIssue,
  type TrainingQuestionBankDrawRule,
  updateTrainingPlan,
  type TrainingNature,
  type TrainingParticipant,
  type TrainingParticipantResults,
  type TrainingPlan,
  type TrainingPlanContent,
  type TrainingPlanEvaluationForm,
  type TrainingPlanStage,
  type TrainingPlanPublishStatus
} from '@/api/training-plan';
import { createTrainingPlanFromMatrix, getTrainingMatrices, getTrainingMatrixSource, type TrainingMatrix } from '@/api/training-matrix';
import type { PersonSelectionRequest } from '@/api/person-selection';
import { getTrainingTypePage, type TrainingType } from '@/api/training-type';
import { getTrainingStandardDetail, getTrainingStandardPage, type TrainingStandard } from '@/api/training-standard';
import { getTrainingTemplatePage, type TrainingTemplate } from '@/api/training-template';
import {
  getEvaluationFormPage,
  getEvaluationFormVersionDetail,
  getEvaluationFormVersions,
  type EvaluationFormVersionDetail,
  type EvaluationFormVersionSummary
} from '@/api/evaluation-form';
import { getTrainingReferenceResourcePage, type TrainingReferenceResource } from '@/api/training-reference';
import {
  getTrainingAttachmentPage,
  getTrainingOfflineSessionPage,
  getTrainingRewardPenaltyPage,
  type TrainingAttachment,
  type TrainingOfflineSession,
  type TrainingRewardPenalty
} from '@/api/training-process';
import { getUserPage, type SystemUser } from '@/api/system-user';
import { hasPermission } from '@/utils/permission';
import PlanBasicPanel from '../components/PlanBasicPanel.vue';
import PlanApprovalPanel from '../components/PlanApprovalPanel.vue';
import PlanChangePanel from '../components/PlanChangePanel.vue';
import PlanEvaluationPanel from '../components/PlanEvaluationPanel.vue';
import PlanExpertInspectionPanel from '../components/PlanExpertInspectionPanel.vue';
import PlanExpertInspectionExecutionPanel from '../components/PlanExpertInspectionExecutionPanel.vue';
import PlanExecutionPanel from '../components/PlanExecutionPanel.vue';
import PlanProcessResultsPanel from '../components/PlanProcessResultsPanel.vue';
import PlanStagesPanel from '../components/PlanStagesPanel.vue';
import { useUserStore } from '@/stores';
import {
  normalizeAssessmentRequiredHours,
  normalizeTrainingRatio,
  summarizeStandardRuleCoverage,
  summarizeTrainingComposition
} from '@/libs/training-composition';
import { collectCompositeBindingIssues, resolvePrecheckTargetStep } from './composite-binding-precheck';
import type { CompositeBindingIssue } from './composite-binding-precheck';

const route = useRoute();
const router = useRouter();
const userStore = useUserStore();
const loading = ref(false);
const saving = ref(false);
const activeStep = ref(0);
/** 工作台步骤统一使用可点击导航，顺序与详情卡片保持一致。 */
const workspaceSteps = [
  { title: '基本信息', description: '计划范围与培训性质' },
  { title: '阶段与内容', description: '编排培训内容' },
  { title: '评价表单', description: '绑定已发布版本' },
  { title: '人员与执行', description: '发布、指派与执行' },
  { title: '过程与结果', description: '查看过程记录与结果概览' }
];
/** 计划状态完全按 OpenAPI 枚举映射，避免执行中等状态被误显示为草稿。 */
const PLAN_STATUS_META: Record<TrainingPlanPublishStatus, { label: string; type: 'info' | 'success' | 'primary' | 'warning' | 'danger' }> = {
  DRAFT: { label: '草稿', type: 'warning' },
  PUBLISHED: { label: '已发布', type: 'success' },
  RUNNING: { label: '执行中', type: 'primary' },
  ENDED: { label: '已结束', type: 'info' },
  CANCELLED: { label: '已取消', type: 'danger' },
  ARCHIVED: { label: '已归档', type: 'info' }
};
const plan = ref<TrainingPlan>();
/** 创建入口传入矩阵要求；只允许合法正整数，服务端再次校验归属。 */
const matrixIds = String(route.query.matrixIds || '').split(',').filter(Boolean).map(Number);
const matrixSource = ref<TrainingMatrix[]>([]);
/** 草稿创建前展示选中要求，创建后展示不可变来源。 */
async function loadMatrixSource(): Promise<void> {
  if (plan.value) matrixSource.value = await getTrainingMatrixSource(plan.value.trainingPlanId);
  else if (matrixIds.length) {
    if (matrixIds.some(id => !Number.isSafeInteger(id) || id <= 0)) throw new Error('矩阵选择无效，请返回重新选择');
    matrixSource.value = (await getTrainingMatrices()).filter(row => matrixIds.includes(row.matrixId));
  }
}
const trainingTypes = ref<TrainingType[]>([]);
const trainingStandards = ref<TrainingStandard[]>([]);
const trainingTemplates = ref<TrainingTemplate[]>([]);
const stages = ref<TrainingPlanStage[]>([]);
/** 最近一次真实保存的基础信息快照，用于控制阶段步骤入口。 */
const savedBasicFingerprint = ref<string>();
/** 最近一次真实保存的阶段与内容快照，避免临时编辑永久锁定后续步骤。 */
const savedStagesFingerprint = ref<string>();
const participants = ref<TrainingParticipant[]>([]);
const participantLoading = ref(false);
const userOptions = ref<SystemUser[]>([]);
const selectedUserId = ref<number>();
const formVersions = ref<EvaluationFormVersionSummary[]>([]);
/** 专家检查版本独立筛选，不能混进综合评价下拉。 */
const expertInspectionVersions = ref<EvaluationFormVersionSummary[]>([]);
const selectedEvaluationForms = ref<TrainingPlanEvaluationForm[]>([]);
const resources = ref<TrainingReferenceResource[]>([]);
const executionLinks = ref<Array<{ planContentId: number; executionType: string; executionId: number }>>([]);
const generationResult = ref<
  TrainingExecutionGenerationResult & {
    items: Array<{
      planContentId: number;
      contentName: string;
      contentType: string;
      status: 'SUCCESS' | 'EXISTING' | 'FAILED';
      code?: number;
      offeringId?: number | null;
      message?: string;
    }>;
  }
>();
const generationLoading = ref(false);
/** 最近一次发布预检的完整阻塞项，供管理员逐条处理而非只看一条提示。 */
const publishIssues = ref<TrainingPublishIssue[]>([]);
const publishIssuesVisible = ref(false);
const focusedPrecheckContentId = ref<number>();
const selectedParticipantId = ref<number>();
const participantResults = ref<TrainingParticipantResults>();
const offlineSessions = ref<TrainingOfflineSession[]>([]);
/** 结束前检查只使用当前页面已有读取接口，不取代后端结束门禁。 */
const endPrecheckLoading = ref(false);
/** 阻塞项带参训人归属时按人折叠展示；title 只写相对说明，参训人由分组标题承载。 */
const endPrecheckBlockers = ref<Array<{ title: string; description: string; participantKey?: string; participantName?: string }>>([]);
const endPrecheckError = ref<string>();
/** 专家查课按需在弹窗加载，避免执行工作区常驻课程记录表。 */
const expertInspectionVisible = ref(false);
const attachments = ref<TrainingAttachment[]>([]);
const rewardPenalties = ref<TrainingRewardPenalty[]>([]);
/** 将本计划参训人关联到当前已加载的用户姓名，避免在奖惩表单手输人员编号。 */
const participantNames = computed(() => Object.fromEntries(participants.value.map((participant) => {
  const user = userOptions.value.find((item) => item.userId === participant.userId);
  return [participant.trainingParticipantId, user?.userName || user?.userCode || `参训人 ${participant.trainingParticipantId}`];
})));
const selectedStandard = ref<TrainingStandard>();
const evaluationVersionDetails = ref<Record<number, EvaluationFormVersionDetail>>({});
/** 正在请求详情的版本号：预检据此区分「读取中」与「确实缺失」，避免刚选完版本就提示重新选择。 */
const evaluationContextLoadingIds = ref<number[]>([]);
/** 最近一次被确认的上游来源，用于用户取消变更时恢复草稿。 */
const acceptedSources = reactive({
  trainingTypeId: undefined as number | undefined,
  trainingStandardId: undefined as number | undefined,
  sourceTemplateId: undefined as number | undefined
});
const planId = computed(() => Number(route.params.trainingPlanId) || undefined);
// 审批读取失败或待审时所有草稿面板保持只读。
const approvalPending = ref(true);
const approvalReady = ref(false);
const readonly = computed(() => Boolean(plan.value && (plan.value.publishStatus !== 'DRAFT' || approvalPending.value)));
/** 当前计划的状态标签只负责展示，计划操作仍由对应执行面板和后端校验。 */
const planStatusMeta = computed(() => (plan.value ? PLAN_STATUS_META[plan.value.publishStatus] : undefined));
/** 将类型、标准和当前阶段转换为可读的编排摘要，不替代服务端发布预检。 */
const complianceSummary = computed(() => {
  const composition = summarizeTrainingComposition(stages.value);
  const type = trainingTypes.value.find((item) => item.trainingTypeId === form.trainingTypeId);
  const typeHours = form.trainingNature === 'INITIAL' ? type?.initialHours : type?.reviewHours;
  const rules = selectedStandard.value?.rules ?? [];
  const standardHours = Math.max(
    0,
    ...rules.filter((item) => item.status === 1 && item.ruleType === 'TOTAL_HOURS').map((item) => Number(item.requiredValue ?? 0))
  );
  const requiredHours = Math.max(Number(typeHours ?? 0), standardHours);
  const standardRatio = rules.find((item) => item.status === 1 && item.ruleType === 'ONLINE_RATIO')?.requiredValue;
  const maxRatio = Math.min(
    normalizeTrainingRatio(type?.onlineMaxRatio ?? 1),
    standardRatio === undefined ? 1 : normalizeTrainingRatio(Number(standardRatio))
  );
  const requiredContents = rules
    .filter((item) => item.status === 1 && ['COURSE', 'EXAM', 'OFFLINE'].includes(item.ruleType))
    .map((rule) => {
      const resource = rule.resourceId ? resources.value.find((item) => item.resourceId === rule.resourceId) : undefined;
      return {
        ruleType: rule.ruleType as 'COURSE' | 'EXAM' | 'OFFLINE',
        resourceId: rule.resourceId,
        resourceVersionId: resource?.versionId ?? undefined,
        name: resource?.resourceName ?? (rule.ruleType === 'OFFLINE' ? rule.description : '指定资源未找到'),
        description: rule.description,
        satisfied: summarizeStandardRuleCoverage(stages.value, [rule])[0]?.satisfied ?? false
      };
    });
  return {
    sourceTemplateText:
      trainingTemplates.value.find((item) => item.trainingTemplateId === form.sourceTemplateId)?.templateName ?? '手工编排（未引用模板）',
    typeText: type
      ? `${form.trainingNature === 'INITIAL' ? '初培' : '复审'}最低 ${typeHours ?? '-'} 学时，线上最高 ${(normalizeTrainingRatio(type.onlineMaxRatio) * 100).toFixed(0)}%`
      : '请先选择培训类型',
    totalText: `当前 ${composition.totalHours} / 最低 ${requiredHours || '-'} 学时`,
    ratioText: `当前线上 ${(composition.onlineRatio * 100).toFixed(0)}% / 上限 ${(maxRatio * 100).toFixed(0)}%`,
    metrics: [
      {
        label: '模板来源',
        current: trainingTemplates.value.find((item) => item.trainingTemplateId === form.sourceTemplateId)?.templateName ?? '手工编排',
        target: '编排依据',
        satisfied: true
      },
      {
        label: '类型基线',
        current: type ? `${form.trainingNature === 'INITIAL' ? '初培' : '复审'}最低 ${typeHours ?? '-'} 学时` : '未选择类型',
        target: `线上最高 ${(normalizeTrainingRatio(type?.onlineMaxRatio ?? 1) * 100).toFixed(0)}%`,
        satisfied: Boolean(type)
      },
      {
        label: '总学时',
        current: `${composition.totalHours} 学时`,
        target: requiredHours ? `最低 ${requiredHours} 学时` : '未设置目标',
        satisfied: requiredHours ? composition.totalHours >= requiredHours : true
      },
      {
        label: '线上占比',
        current: `${(composition.onlineRatio * 100).toFixed(0)}%`,
        target: `上限 ${(maxRatio * 100).toFixed(0)}%`,
        satisfied: composition.onlineRatio <= maxRatio
      }
    ],
    ruleCoverage: summarizeStandardRuleCoverage(stages.value, rules),
    requiredContents
  };
});
/**
 * 已选评价版本的来源只作可用性提示，正式计算仍由评价域完成。
 * 可用性判据必须与后端绑定/发布校验一致：要求计划中存在计入综合成绩的同类别内容，
 * 仅"存在该类型内容"不足以参与综合成绩计算。
 */
const evaluationContextHints = computed(() => {
  const scoredContentTypes = new Set(
    stages.value
      .flatMap((stage) => stage.contents ?? [])
      .filter((content) => content.isScored === 1 && content.contentType !== 'QUESTION_BANK')
      .map((content) => content.contentType)
  );
  return selectedEvaluationForms.value.flatMap(({ formVersionId }) => {
    const versionDetail = evaluationVersionDetails.value[formVersionId];
    return (versionDetail?.scoreRules ?? []).map((rule) => {
      const item = versionDetail?.items.find((current) => current.evaluationItemId === rule.evaluationItemId);
      return {
        formVersionId,
        sourceType: rule.sourceType,
        available: rule.sourceType === 'REWARD_PENALTY' || rule.sourceType === 'MANUAL' ? true : scoredContentTypes.has(rule.sourceType),
        weight: rule.weight,
        calculationType: rule.calculationType,
        itemName: item?.itemName,
        itemCode: item?.itemCode,
        maxScore: item?.maxScore,
        isRequired: item?.isRequired
      };
    });
  });
});
/**
 * 绑定评价表单的前端预检。
 * 后端在保存绑定时按同一组判据整单回滚（8128），这里提前算出来，
 * 让管理员在点击保存前就知道差在哪、该回哪一步修改。
 */
const compositeBindingIssues = computed<CompositeBindingIssue[]>(() =>
  collectCompositeBindingIssues({
    stagesSaved: stagesStepCompleted.value,
    bindings: selectedEvaluationForms.value,
    stages: stages.value,
    versionDetails: evaluationVersionDetails.value,
    loadingVersionIds: evaluationContextLoadingIds.value
  })
);
/** 面板只展示会阻断保存的问题，预检通过时保持界面安静。 */
const blockingBindingIssues = computed(() => compositeBindingIssues.value.filter((issue) => issue.blocked));
/** 专家检查记录只读取专家用途绑定，不参与综合成绩计算。 */
const expertInspectionBinding = computed(() =>
  selectedEvaluationForms.value.find((item) => item.usageType === 'EXPERT_INSPECTION' && item.status === 1)
);
/** 结束门禁按计划是否曾绑定综合表单判定；此处与后端绑定记录口径保持一致。 */
const hasCompositeEvaluationBinding = computed(() => selectedEvaluationForms.value.some((item) => item.usageType === 'COMPOSITE'));
const form = reactive({
  planCode: '',
  planName: '',
  trainingTypeId: undefined as number | undefined,
  trainingNature: 'INITIAL' as Exclude<TrainingNature, 'UNSPECIFIED'>,
  trainingStandardId: undefined as number | undefined,
  sourceTemplateId: undefined as number | undefined,
  managerUserId: undefined as number | undefined,
  startTime: '',
  endTime: '',
  location: '',
  description: '',
  status: 1 as 0 | 1
});
const rules: FormRules = {
  planCode: [{ required: true, message: '请输入计划编码', trigger: 'blur' }],
  planName: [{ required: true, message: '请输入计划名称', trigger: 'blur' }],
  trainingTypeId: [{ required: true, message: '请选择培训类型', trigger: 'change' }],
  startTime: [{ required: true, message: '请选择开始时间', trigger: 'change' }],
  endTime: [{ required: true, message: '请选择结束时间', trigger: 'change' }]
};

function canOperate(permission: string): boolean {
  return hasPermission(userStore.userInfo, permission);
}

/** 仅包含计划基础信息中的可编辑字段，服务端计算字段不参与步骤判定。 */
function basicFingerprint(): string {
  return JSON.stringify({
    planCode: form.planCode,
    planName: form.planName,
    trainingTypeId: form.trainingTypeId,
    trainingNature: form.trainingNature,
    trainingStandardId: form.trainingStandardId,
    sourceTemplateId: form.sourceTemplateId,
    managerUserId: form.managerUserId,
    startTime: form.startTime,
    endTime: form.endTime,
    location: form.location,
    description: form.description,
    status: form.status
  });
}
/** 将阶段和内容完整序列化，支持新增后取消时自动恢复步骤可用状态。 */
function stagesFingerprint(): string {
  return JSON.stringify(stages.value);
}
const basicStepCompleted = computed(() => savedBasicFingerprint.value !== undefined && savedBasicFingerprint.value === basicFingerprint());
const stagesStepCompleted = computed(() => savedStagesFingerprint.value !== undefined && savedStagesFingerprint.value === stagesFingerprint());
/** 统计必修内容及已成功安排的必修内容，前端只改善按钮提示，最终仍以后端校验为准。 */
const requiredContentCount = computed(
  () => stages.value.flatMap((stage) => stage.contents ?? []).filter((content) => content.isRequired === 1).length
);
const readyRequiredContentCount = computed(() => {
  const readyContentIds = new Set([
    ...executionLinks.value.map((link) => link.planContentId),
    ...offlineSessions.value.map((session) => session.planContentId)
  ]);
  return stages.value
    .flatMap((stage) => stage.contents ?? [])
    .filter((content) => content.isRequired === 1 && content.planContentId && readyContentIds.has(content.planContentId)).length;
});
/** 统计计划内容的安排完成情况，供执行区决定是否继续显示“安排培训内容”。 */
const contentCount = computed(() => stages.value.flatMap((stage) => stage.contents ?? []).filter((content) => content.planContentId).length);
const arrangedContentCount = computed(() => {
  const readyContentIds = new Set([
    ...executionLinks.value.map((link) => link.planContentId),
    ...offlineSessions.value.map((session) => session.planContentId)
  ]);
  return stages.value
    .flatMap((stage) => stage.contents ?? [])
    .filter((content) => content.planContentId && readyContentIds.has(content.planContentId)).length;
});

/** 后续步骤必须以前一步最近一次真实保存的内容为准。 */
function isStepDisabled(step: number): boolean {
  if (readonly.value) return false;
  if (step === 1) return !basicStepCompleted.value;
  if (step >= 2) return !basicStepCompleted.value || !stagesStepCompleted.value;
  return false;
}

/** 切换工作台步骤，并为未保存的新计划给出明确反馈。 */
function goStep(step: number): void {
  if (isStepDisabled(step)) {
    ElMessage.warning(step === 1 ? '请先保存基础信息。' : '请先保存阶段与内容。');
    return;
  }
  activeStep.value = step;
}
async function loadTypes(): Promise<void> {
  trainingTypes.value = (await getTrainingTypePage({ pageNum: 1, pageSize: 100, 'query.status': 1 })).list;
}
/** 标准与模板均只提供已发布、启用的当前机构记录作为计划基础信息选项。 */
async function loadPlanReferenceOptions(): Promise<void> {
  const [standardPage, templatePage] = await Promise.all([
    getTrainingStandardPage({ pageNum: 1, pageSize: 100, 'query.status': 1, 'query.publishStatus': 'PUBLISHED' }),
    getTrainingTemplatePage({ pageNum: 1, pageSize: 100, 'query.status': 1, 'query.publishStatus': 'PUBLISHED' })
  ]);
  trainingStandards.value = standardPage.list;
  trainingTemplates.value = templatePage.list;
}
/** 经确认清空阶段，避免新依据与旧编排静默混用。 */
async function confirmClearStages(message: string): Promise<boolean> {
  if (!stages.value.length) return true;
  try {
    await ElMessageBox.confirm(message, '变更编排依据', { type: 'warning' });
    stages.value = [];
    savedStagesFingerprint.value = undefined;
    return true;
  } catch {
    return false;
  }
}
/** 培训类型变化后，旧标准、模板和阶段均可能不再适用。 */
async function handleTrainingTypeChange(): Promise<void> {
  if (!(await confirmClearStages('更换培训类型会清空当前阶段内容，是否继续？'))) {
    form.trainingTypeId = acceptedSources.trainingTypeId;
    return;
  }
  acceptedSources.trainingTypeId = form.trainingTypeId;
  acceptedSources.trainingStandardId = undefined;
  acceptedSources.sourceTemplateId = undefined;
  form.trainingStandardId = undefined;
  form.sourceTemplateId = undefined;
  selectedStandard.value = undefined;
}
/** 切换标准只更换发布检查口径，计划已有课表不应被标准规则静默改写。 */
async function handleTrainingStandardChange(): Promise<void> {
  acceptedSources.trainingStandardId = form.trainingStandardId;
  selectedStandard.value = form.trainingStandardId ? await getTrainingStandardDetail(form.trainingStandardId) : undefined;
}
/** 草稿计划选择模板时由服务端原子覆盖复制；新建计划在创建请求中一次复制。 */
async function handleTrainingTemplateChange(): Promise<void> {
  const previousTemplateId = acceptedSources.sourceTemplateId;
  if (!plan.value) {
    acceptedSources.sourceTemplateId = form.sourceTemplateId;
    return;
  }
  if (!form.sourceTemplateId) {
    acceptedSources.sourceTemplateId = undefined;
    ElMessage.info('已更新草稿模板来源，保存基础信息后生效；不会复制或清空当前课表。');
    return;
  }
  try {
    await ElMessageBox.confirm('从模板复制会覆盖当前阶段与内容，确认继续吗？', '覆盖复制课表', { type: 'warning' });
    await applyTrainingPlanTemplate(plan.value.trainingPlanId, { version: plan.value.version, sourceTemplateId: form.sourceTemplateId });
    acceptedSources.sourceTemplateId = form.sourceTemplateId;
    await loadPlan(plan.value.trainingPlanId);
    ElMessage.success('已从模板覆盖复制课表，请在计划中补全本期执行配置。');
  } catch (error) {
    form.sourceTemplateId = previousTemplateId;
    acceptedSources.sourceTemplateId = previousTemplateId;
    if (error instanceof TrainingPlanApiError && error.code === 8158) ElMessage.error('仅草稿计划可以从模板覆盖复制课表。');
    else if (error instanceof TrainingPlanApiError && error.code === 8157) ElMessage.error('所选模板未发布、类型不一致或不属于当前机构。');
    else if (error !== 'cancel' && error !== 'close') throw error;
  }
}
async function loadPlan(targetPlanId = planId.value): Promise<void> {
  if (!targetPlanId) return;
  loading.value = true;
  approvalPending.value = true;
  approvalReady.value = false;
  try {
    const data = await getTrainingPlan(targetPlanId);
    plan.value = data;
    stages.value = (data.stages ?? []).map((stage) => ({ ...stage, contents: [...(stage.contents ?? [])] }));
    normalizeAssessmentRequiredHours(stages.value);
    selectedEvaluationForms.value = data.evaluationForms ?? [];
    // 已有绑定不会触发下拉选择事件，需主动读取冻结规则以呈现来源可用性。
    await loadEvaluationContext(selectedEvaluationForms.value.map((item) => item.formVersionId));
    Object.assign(form, data, { trainingNature: data.trainingNature === 'UNSPECIFIED' ? 'INITIAL' : data.trainingNature });
    Object.assign(acceptedSources, {
      trainingTypeId: form.trainingTypeId,
      trainingStandardId: form.trainingStandardId,
      sourceTemplateId: form.sourceTemplateId
    });
    savedBasicFingerprint.value = basicFingerprint();
    savedStagesFingerprint.value = stagesFingerprint();
  } finally {
    loading.value = false;
  }
}
/** 计划详情之外的过程事实按独立端点加载，避免把事实伪造到草稿模型中。 */
async function loadExecutionContext(): Promise<void> {
  if (!planId.value) return;
  participantLoading.value = true;
  try {
    const [participantData, linkData] = await Promise.all([getTrainingPlanParticipants(planId.value), getTrainingPlanExecutionLinks(planId.value)]);
    participants.value = participantData;
    executionLinks.value = linkData;
  } finally {
    participantLoading.value = false;
  }
}
/** 过程事实始终从独立真实接口读取，不混入计划草稿或 M3 结果快照。 */
async function loadProcessFacts(): Promise<void> {
  if (!planId.value) return;
  const attachmentPage = canOperate('training:attachment:list')
    ? getTrainingAttachmentPage({ pageNum: 1, pageSize: 100, 'query.trainingPlanId': planId.value })
    : Promise.resolve({ list: [], total: 0 });
  const rewardPenaltyPage = canOperate('training:reward:list')
    ? loadAllRewardPenalties(planId.value)
    : Promise.resolve([] as TrainingRewardPenalty[]);
  const [sessionPage, attachmentData, rewardPenaltyData] = await Promise.all([
    getTrainingOfflineSessionPage({ pageNum: 1, pageSize: 100, 'query.trainingPlanId': planId.value }),
    attachmentPage,
    rewardPenaltyPage
  ]);
  offlineSessions.value = sessionPage.list;
  attachments.value = attachmentData.list;
  rewardPenalties.value = rewardPenaltyData;
}
/** 奖惩表不静默截断在第一页，保证本期记录可完整回看。 */
async function loadAllRewardPenalties(trainingPlanId: number): Promise<TrainingRewardPenalty[]> {
  const records: TrainingRewardPenalty[] = [];
  for (let pageNum = 1; ; pageNum++) {
    const page = await getTrainingRewardPenaltyPage({ pageNum, pageSize: 100, 'query.trainingPlanId': trainingPlanId });
    records.push(...page.list);
    if (records.length >= page.total || page.list.length === 0) return records;
  }
}
/** 奖惩或培训资料变更后回读服务端事实、计划版本和当前查看的个人结果。 */
async function handleProcessFactsChanged(): Promise<void> {
  try {
    await Promise.all([loadProcessFacts(), loadPlan()]);
    if (planId.value && selectedParticipantId.value) {
      participantResults.value = await getTrainingParticipantResults(planId.value, selectedParticipantId.value);
    }
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '奖惩保存成功，但结果刷新失败，请重新进入计划。');
  }
}
/**
 * 复用执行、线下场次和逐人结果事实生成结束前提示；未知或读取失败不伪造后端判定。
 */
async function loadEndPrecheck(): Promise<void> {
  if (!plan.value || plan.value.publishStatus !== 'RUNNING') {
    endPrecheckBlockers.value = [];
    endPrecheckError.value = undefined;
    return;
  }
  endPrecheckLoading.value = true;
  endPrecheckError.value = undefined;
  try {
    const blockers: Array<{ title: string; description: string; participantKey?: string; participantName?: string }> = [];
    const contents = stages.value.flatMap((stage) => stage.contents ?? []);
    const arrangedContentIds = new Set([
      ...executionLinks.value.map((link) => link.planContentId),
      ...offlineSessions.value.map((session) => session.planContentId)
    ]);
    const unarrangedContents = contents.filter((content) => content.planContentId && !arrangedContentIds.has(content.planContentId));
    if (unarrangedContents.length) {
      blockers.push({
        title: `仍有 ${unarrangedContents.length} 项培训内容未安排`,
        description: `请先安排：${unarrangedContents.map((content) => content.contentName).join('、')}。`
      });
    }
    offlineSessions.value
      .filter((session) => session.sessionStatus === 'IN_PROGRESS')
      .forEach((session) => {
        blockers.push({
          title: `线下场次“${session.sessionName}”仍在进行中`,
          description: '请在“教师任务”中结束线下场次，录入成绩后再刷新本检查。'
        });
      });

    const resultPairs = await Promise.all(
      participants.value.map(async (participant) => ({
        participant,
        result: await getTrainingParticipantResults(plan.value!.trainingPlanId, participant.trainingParticipantId)
      }))
    );
    const terminalResultStatuses = new Set(['COMPLETED', 'FAILED', 'EXPIRED']);
    const requiredContents = contents.filter((content) => content.isRequired === 1 && content.planContentId);
    const userNameById = new Map(userOptions.value.map((user) => [user.userId, user.userName || user.userCode]));
    resultPairs.forEach(({ participant, result }) => {
      const participantName = userNameById.get(participant.userId) ?? `参训人 ${participant.trainingParticipantId}`;
      // 参训人归属单独成字段，检查面板据此折叠；title 不再重复参训人，避免多人时成倍堆叠。
      const participantScope = { participantKey: String(participant.trainingParticipantId), participantName };
      const resultByContentId = new Map((result.items ?? []).map((item) => [item.planContentId, item]));
      (result.items ?? [])
        .filter((item) => item.resultStatus === 'IN_PROGRESS')
        .forEach((item) => {
          blockers.push({
            ...participantScope,
            title: `“${item.contentName}”仍在进行中`,
            description: '请等待课程学习或考试作答结束后，再刷新本检查。'
          });
        });
      requiredContents.forEach((content) => {
        const item = resultByContentId.get(content.planContentId!);
        if (!item || !terminalResultStatuses.has(item.resultStatus ?? '')) {
          blockers.push({
            ...participantScope,
            title: `尚未完成必修内容“${content.contentName}”`,
            description: '请完成对应课程、考试、题库练习或线下活动，并等待其结果进入最终状态。'
          });
        }
      });
      if (hasCompositeEvaluationBinding.value && result.compositeScore == null) {
        blockers.push({
          ...participantScope,
          title: '尚未产生综合成绩',
          description: '请检查课程、考试和线下正式成绩是否齐全，并等待系统自动汇总后再刷新本检查。'
        });
      }
    });
    endPrecheckBlockers.value = blockers;
  } catch {
    endPrecheckBlockers.value = [];
    endPrecheckError.value = '无法完整核对结束条件，请刷新后重试；最终仍以后端结束校验结果为准。';
  } finally {
    endPrecheckLoading.value = false;
  }
}
/** 刷新执行事实后同步更新结束前检查，避免页面展示旧的线下场次或参训结果。 */
async function refreshExecutionFacts(): Promise<void> {
  await Promise.all([loadExecutionContext(), loadProcessFacts()]);
  await loadEndPrecheck();
}
/** 从系统用户接口加载候选人员，页面不允许手输 userId。 */
async function loadParticipantOptions(): Promise<void> {
  userOptions.value = (await getUserPage({ pageNum: 1, pageSize: 100, 'query.status': 1 })).list;
}
async function loadEvaluationFormVersions(): Promise<void> {
  const forms = await getEvaluationFormPage({ pageNum: 1, pageSize: 100, 'query.status': 1 });
  const formNameById = new Map(forms.list.map((item) => [item.evaluationFormId, item.formName]));
  const versions = (await Promise.all(forms.list.map((item) => getEvaluationFormVersions(item.evaluationFormId))))
    .flat()
    .map((item) => ({ ...item, formName: formNameById.get(item.evaluationFormId) ?? `评价表单 ${item.evaluationFormId}` }));
  formVersions.value = versions.filter(
    (item) =>
      item.publishedStatus === 'PUBLISHED' &&
      forms.list.some((form) => form.evaluationFormId === item.evaluationFormId && form.formType === 'COMPOSITE')
  );
  expertInspectionVersions.value = versions.filter(
    (item) =>
      item.publishedStatus === 'PUBLISHED' &&
      forms.list.some((form) => form.evaluationFormId === item.evaluationFormId && form.formType === 'EXPERT_INSPECTION')
  );
}
/** 仅在选择版本后读取其冻结规则，避免列表加载时放大请求。 */
async function loadEvaluationContext(ids: number[]): Promise<void> {
  const missing = ids.filter((id) => !evaluationVersionDetails.value[id]);
  if (!missing.length) return;
  evaluationContextLoadingIds.value = [...new Set([...evaluationContextLoadingIds.value, ...missing])];
  try {
    const details = await Promise.all(missing.map((id) => getEvaluationFormVersionDetail(id)));
    evaluationVersionDetails.value = { ...evaluationVersionDetails.value, ...Object.fromEntries(details.map((item) => [item.formVersionId, item])) };
  } finally {
    evaluationContextLoadingIds.value = evaluationContextLoadingIds.value.filter((id) => !missing.includes(id));
  }
}
/** 评价表单绑定按 OpenAPI 子项保存，选择变化时保留已配置的必填和启用状态。 */
async function handleEvaluationSelection(ids: number[]): Promise<void> {
  const previous = new Map(selectedEvaluationForms.value.map((item) => [item.formVersionId, item]));
  const otherBindings = selectedEvaluationForms.value.filter((item) => item.usageType !== 'COMPOSITE');
  selectedEvaluationForms.value = [
    ...otherBindings,
    ...ids.map(
      (formVersionId) =>
        previous.get(formVersionId) ?? {
          formVersionId,
          usageType: 'COMPOSITE' as const,
          isRequired: 0 as const,
          status: 1 as const
        }
    )
  ];
  await loadEvaluationContext(ids);
}
/** 专家检查绑定仅替换专家用途，保留综合评价绑定与其来源配置。 */
async function handleExpertInspectionSelection(formVersionId?: number): Promise<void> {
  const otherBindings = selectedEvaluationForms.value.filter((item) => item.usageType !== 'EXPERT_INSPECTION');
  selectedEvaluationForms.value = formVersionId
    ? [...otherBindings, { formVersionId, usageType: 'EXPERT_INSPECTION' as const, isRequired: 0 as const, status: 1 as const }]
    : otherBindings;
  if (formVersionId) await loadEvaluationContext([formVersionId]);
}
async function loadResources(): Promise<void> {
  resources.value = (
    await Promise.all(
      ['COURSE', 'EXAM', 'QUESTION_BANK'].map((type) =>
        getTrainingReferenceResourcePage({ pageNum: 1, pageSize: 100, 'query.resourceType': type as 'COURSE' | 'EXAM' | 'QUESTION_BANK' })
      )
    )
  ).flatMap((page) => page.list);
}
async function save(): Promise<void> {
  saving.value = true;
  try {
    const payload = {
      ...form,
      trainingTypeId: form.trainingTypeId as number,
      location: form.location || undefined,
      description: form.description || undefined
    };
    let targetPlanId = plan.value?.trainingPlanId;
    let created = false;
    if (plan.value) {
      await updateTrainingPlan(plan.value.trainingPlanId, {
        ...payload,
        // 清空选择必须显式传 null；省略字段会被后端按“保持原关联”处理。
        trainingStandardId: form.trainingStandardId ?? null,
        sourceTemplateId: form.sourceTemplateId ?? null,
        version: plan.value.version
      });
    } else {
      const id = matrixIds.length ? await createTrainingPlanFromMatrix(payload, matrixIds) : await createTrainingPlan(payload);
      targetPlanId = id;
      created = true;
    }
    await loadPlan(targetPlanId);
    await loadMatrixSource();
    // 创建接口已在同一事务复制模板课表，详情只读取服务端结果。
    if (created) await router.replace(`/admin/business-center/training/plan/${targetPlanId}`);
    ElMessage.success('计划基础信息已保存');
  } finally {
    saving.value = false;
  }
}
async function run(action: 'publish' | 'generate' | 'start' | 'end' | 'cancel'): Promise<void> {
  if (!plan.value) return;
  if (action === 'publish') {
    // 发布前刷新计划，确保预检和发布使用服务端最新的版本号。
    await loadPlan(plan.value.trainingPlanId);
    if (!plan.value) return;
    if (participants.value.length <= 0) {
      activeStep.value = 3;
      ElMessage.warning('发布前请先配置至少一名参训人员');
      return;
    }
    const precheck = await precheckTrainingPlanPublish(plan.value.trainingPlanId);
    if (!precheck.ready) {
      publishIssues.value = precheck.issues ?? [];
      publishIssuesVisible.value = true;
      ElMessage.warning('发布预检未通过，请逐项处理后重新预检');
      return;
    }
    await publishTrainingPlan(plan.value.trainingPlanId, plan.value.version);
  }
  if (action === 'generate') {
    generationLoading.value = true;
    try {
      const result = await generateTrainingPlanExecutions(plan.value.trainingPlanId);
      await refreshExecutionFacts();
      const readyContentIds = new Set([
        ...executionLinks.value.map((link) => link.planContentId),
        ...offlineSessions.value.map((session) => session.planContentId)
      ]);
      const failureByContentId = new Map(result.failures.map((failure) => [failure.planContentId, failure]));
      generationResult.value = {
        ...result,
        failures: result.failures.map((failure) => ({
          ...failure,
          contentName:
            stages.value.flatMap((stage) => stage.contents ?? []).find((content) => content.planContentId === failure.planContentId)?.contentName ??
            `内容项 ${failure.planContentId}`
        })),
        items: stages.value
          .flatMap((stage) => stage.contents ?? [])
          .map((content) => {
            const failure = content.planContentId ? failureByContentId.get(content.planContentId) : undefined;
            return {
              planContentId: content.planContentId ?? 0,
              contentName: content.contentName,
              contentType: content.contentType,
              status: failure || !readyContentIds.has(content.planContentId ?? 0) ? 'FAILED' : 'SUCCESS',
              code: failure?.code,
              offeringId: failure?.offeringId,
              message: failure?.message ?? (readyContentIds.has(content.planContentId ?? 0) ? '已建立或继续使用对应培训安排' : '未找到对应培训安排')
            };
          })
      };
    } finally {
      generationLoading.value = false;
    }
  }
  if (action === 'start') await startTrainingPlan(plan.value.trainingPlanId, plan.value.version);
  if (action === 'end') await endTrainingPlan(plan.value.trainingPlanId, plan.value.version);
  if (action === 'cancel') {
    await ElMessageBox.confirm('取消后保留已有事实且不会产生通过结论，确认继续吗？', '取消计划', { type: 'warning' });
    await cancelTrainingPlan(plan.value.trainingPlanId, plan.value.version);
  }
  await loadPlan();
  await refreshExecutionFacts();
  ElMessage.success('操作已提交');
}
/** 将发布预检问题翻译为用户能直接处理的调整建议，不展示接口码和内部术语。 */
function formatPublishIssues(issues: TrainingPublishIssue[]): string {
  const contentName = (planContentId?: number | null): string | undefined =>
    planContentId
      ? stages.value.flatMap((stage) => stage.contents ?? []).find((content) => content.planContentId === planContentId)?.contentName
      : undefined;
  const messages = issues.map((item) => {
    const name = contentName(item.planContentId);
    if (item.code === 8115) return `${name ? `${name}：` : ''}考试没有可用的冻结试卷版本，请回到“阶段与内容”重新选择已发布且可用的考试。`;
    if (item.code === 8121)
      return `线上内容占比超过允许上限（${complianceSummary.value.ratioText}），请回到“阶段与内容”减少线上内容，或补充线下培训内容。`;
    if (item.code === 8156) return '当前计划缺少培训标准指定的课程、考试或线下内容，请回到“阶段与内容”补充。';
    return `${name ? `${name}：` : ''}${item.message || '当前编排还不满足发布条件，请检查阶段与内容。'}`;
  });
  return `发布前还需要调整：${messages.join('；') || '请检查计划编排'}`;
}

/** 按内容类型检查发布所需的配置，避免无效阶段内容进入服务端。 */
function validatePlanContent(content: TrainingPlanContent): string | undefined {
  if (!content.contentName.trim()) return '请填写内容名称。';
  if (content.contentType !== 'OFFLINE' && !content.resourceId) return '请选择内容资源。';
  if (content.contentType === 'EXAM' && !resources.value.some((item) => item.resourceId === content.resourceId && item.versionId != null)) {
    return '考试必须选择已有可用试卷版本的资源。';
  }
  const config = content.executionConfig;
  if (content.contentType === 'COURSE' && !['ALL_REQUIRED_ITEMS', 'ALL_ITEMS'].includes(config?.completionRule ?? '')) return '请选择课程完成规则。';
  if (content.contentType === 'EXAM' && !['QUIZ', 'FORMAL_EXAM'].includes(content.assessmentMode ?? '')) return '请选择考试模式。';
  if (content.contentType === 'QUESTION_BANK') {
    if (!config?.durationSeconds || config.durationSeconds <= 0) return '请输入大于 0 的练习时长。';
    if (!config.maxAttempts || config.maxAttempts <= 0) return '请输入大于 0 的最大作答次数。';
    if (!config.drawRules?.length) return '请至少添加一条抽题规则。';
    const invalidRule = config.drawRules.find((rule: TrainingQuestionBankDrawRule) => !rule.questionType || !rule.drawCount || rule.drawCount <= 0);
    if (invalidRule) return '请完善抽题规则。';
  }
  if (content.contentType === 'OFFLINE') {
    if (!config?.teacherUserId) return '请选择授课教师。';
    if (!config.location?.trim()) return '请输入培训地点。';
    if (!config.startTime || !config.endTime) return '请选择授课开始和结束时间。';
    if (config.passScore == null || config.passScore < 0) return '请输入有效的及格分数。';
  }
  return undefined;
}

/** 将内容类型契约枚举转换为管理员可理解的校验文案。 */
function scoreContentTypeText(contentType: string): string {
  return { COURSE: '课程', EXAM: '考试', OFFLINE: '线下培训' }[contentType] ?? '当前类别';
}

/** 检查阶段、内容和规则摘要，只有合规草稿才能覆盖保存。 */
function validatePlanStages(): string | undefined {
  const invalidMetric = complianceSummary.value.metrics.find((item) => !item.satisfied);
  if (invalidMetric)
    return `阶段内容不符合要求：${invalidMetric.label}当前为${invalidMetric.current}，目标为${invalidMetric.target}，请调整后再保存。`;
  const invalidRule = complianceSummary.value.ruleCoverage.find((item) => !item.satisfied);
  if (invalidRule) return `阶段内容不符合要求：${invalidRule.label}尚未加入当前阶段内容，请补充或调整后再保存。`;
  if (!stages.value.length) return '阶段内容不符合要求：请至少新增一个阶段。';
  for (const [stageIndex, stage] of stages.value.entries()) {
    if (!stage.stageName.trim()) return `阶段内容不符合要求：请填写第 ${stageIndex + 1} 个阶段名称。`;
    if (!stage.contents?.length) return `阶段内容不符合要求：请为“${stage.stageName}”添加至少一项培训内容。`;
    const issue = stage.contents.map(validatePlanContent).find(Boolean);
    if (issue) return `阶段“${stage.stageName}”的内容配置不完整：${issue}`;
  }
  const scoredByType = stages.value
    .flatMap((stage) => stage.contents ?? [])
    .filter((content) => content.isScored === 1 && content.contentType !== 'QUESTION_BANK')
    .reduce<Record<string, TrainingPlanContent[]>>((groups, content) => {
      (groups[content.contentType] ??= []).push(content);
      return groups;
    }, {});
  for (const [contentType, contents] of Object.entries(scoredByType)) {
    const contentTypeText = scoreContentTypeText(contentType);
    const modes = new Set(contents.map((content) => content.scoreAggregationMode));
    if (modes.size !== 1 || modes.has(undefined)) return `${contentTypeText}类计分内容必须统一选择“算术平均”或“加权平均”。`;
    // 与后端判据一致：算术平均不允许保留内容权重，否则内容能存下，却在绑定评价表单时被拒。
    if (modes.has('AVERAGE') && contents.some((content) => content.scoreWeight != null))
      return `${contentTypeText}类已选择“算术平均”，不能再填写内容权重，请清空权重后再保存。`;
    if (modes.has('WEIGHTED_AVERAGE')) {
      const total = contents.reduce((sum, content) => sum + (content.scoreWeight ?? 0), 0);
      if (contents.some((content) => !content.scoreWeight || content.scoreWeight <= 0))
        return `${contentTypeText}类已选择“加权平均”，请为每一项计分内容填写大于 0 的内容权重，并使合计为 1。`;
      if (Math.abs(total - 1) > 0.000001) return `${contentTypeText}类加权平均的内容权重当前合计为 ${total.toFixed(2)}，请调整为 1。`;
    }
  }
  return undefined;
}

/** 覆盖保存阶段和内容；资源主键由后续资源选择器步骤提供，不允许手工输入。 */
async function saveStages(): Promise<void> {
  if (!plan.value) return;
  normalizeAssessmentRequiredHours(stages.value);
  const issue = validatePlanStages();
  if (issue) {
    ElMessage.warning(issue);
    return;
  }
  await replaceTrainingPlanStages(plan.value.trainingPlanId, { version: plan.value.version, stages: stages.value });
  await loadPlan();
  ElMessage.success('阶段内容已保存');
}
function addStage(): void {
  stages.value.push({ stageName: '', stageType: 'ONLINE', sortOrder: stages.value.length + 1, contents: [] });
}
/** 新增内容默认从课程开始，用户可按当前契约切换为其他类型。 */
function addContent(stage: TrainingPlanStage): void {
  (stage.contents ??= []).push({
    contentType: 'COURSE',
    contentName: '',
    isRequired: 1,
    isScored: 1,
    scoreAggregationMode: 'AVERAGE',
    requiredHours: 0,
    sortOrder: (stage.contents?.length ?? 0) + 1
  });
}
/** 删除阶段后重新生成顺序，避免向后端提交断裂的 sortOrder。 */
function removeStage(index: number): void {
  stages.value.splice(index, 1);
  stages.value.forEach((stage, stageIndex) => {
    stage.sortOrder = stageIndex + 1;
  });
}
/** 删除内容后重新生成本阶段顺序，保持覆盖保存的数据连续。 */
function removeContent(stage: TrainingPlanStage, index: number): void {
  stage.contents?.splice(index, 1);
  stage.contents?.forEach((content, contentIndex) => {
    content.sortOrder = contentIndex + 1;
  });
}
/** 切换内容类型必须清空旧资源，避免不同资源类型错误复用同一主键。 */
function changeContentType(content: TrainingPlanContent): void {
  Object.assign(content, {
    resourceId: undefined,
    resourceVersionId: undefined,
    assessmentMode: undefined,
    executionConfig: undefined,
    contentName: '',
    isScored: content.contentType === 'QUESTION_BANK' ? 0 : 1,
    scoreAggregationMode: content.contentType === 'QUESTION_BANK' ? undefined : 'AVERAGE',
    scoreWeight: undefined
  });
  if (content.contentType === 'EXAM' || content.contentType === 'QUESTION_BANK') content.requiredHours = 0;
}

/** 预检问题的补充定位信息，规则级问题直接展示后端已计算的真实候选数量。 */
function publishIssueDetails(issue: TrainingPublishIssue): string[] {
  const details: string[] = [];
  if (issue.stageName) details.push(`阶段：${issue.stageName}`);
  if (issue.contentName || issue.resourceName) details.push(`内容：${issue.contentName ?? issue.resourceName}`);
  if (issue.ruleIndex) details.push(`抽题规则：第 ${issue.ruleIndex} 条`);
  if (issue.questionType)
    details.push(
      `题型：${({ SINGLE: '单选题', MULTIPLE: '多选题', JUDGE: '判断题', FILL: '填空题', ESSAY: '简答题' } as Record<string, string>)[issue.questionType] ?? issue.questionType}`
    );
  if (issue.requiredCount != null || issue.availableCount != null)
    details.push(`需要/可用：${issue.requiredCount ?? 0}/${issue.availableCount ?? 0} 题`);
  return details;
}

/** 从预检明细进入对应内容的执行配置；结构级问题仅跳转到阶段与内容。 */
function handlePublishIssue(issue: TrainingPublishIssue): void {
  publishIssuesVisible.value = false;
  activeStep.value = 1;
  focusedPrecheckContentId.value = issue.planContentId ?? undefined;
}
/** 将标准指定内容直接加入首个阶段，已存在或无可用资源时不重复添加。 */
function addRequiredContent(
  requiredContent: {
    ruleType: 'COURSE' | 'EXAM' | 'OFFLINE';
    resourceId?: number;
    resourceVersionId?: number;
    name: string;
  },
  stageIndex = 0
): void {
  let stage = stages.value[stageIndex];
  if (!stage) {
    addStage();
    stage = stages.value[stageIndex] ?? stages.value[0];
  }
  if (!stage) return;
  const exists = stages.value.some((item) =>
    (item.contents ?? []).some(
      (content) =>
        content.contentType === requiredContent.ruleType &&
        (requiredContent.ruleType === 'OFFLINE' || content.resourceId === requiredContent.resourceId)
    )
  );
  if (exists) {
    ElMessage.info('该指定内容已添加到阶段内容。');
    return;
  }
  (stage.contents ??= []).push({
    contentType: requiredContent.ruleType,
    resourceId: requiredContent.resourceId,
    resourceVersionId: requiredContent.resourceVersionId,
    contentName: requiredContent.name,
    isRequired: 1,
    isScored: 1,
    scoreAggregationMode: 'AVERAGE',
    requiredHours: 0,
    sortOrder: (stage.contents?.length ?? 0) + 1
  });
  ElMessage.success(`已添加“${requiredContent.name}”，请继续完善执行配置。`);
}
function selectContentResource(content: TrainingPlanContent, resourceId: number): void {
  const resource = resources.value.find((item) => item.resourceId === resourceId && item.resourceType === content.contentType);
  if (!resource) return;
  Object.assign(content, {
    resourceId: resource.resourceId,
    resourceVersionId: resource.versionId ?? undefined,
    contentName: resource.resourceName,
    contentType: resource.resourceType
  });
}
/** 将评价表单绑定校验失败翻译为管理员可以直接处理的调整提示。 */
function formatEvaluationBindingError(error: unknown): string | undefined {
  if (!(error instanceof TrainingPlanApiError)) return undefined;
  if (error.code === 8104) return '培训计划已被其他操作更新，请刷新页面后重新保存评价表单。';
  if (error.code === 8110) return '所选评价表单版本未发布或不存在，请刷新列表后重新选择。';
  // 8128 的原始文案只带英文来源枚举，这里补上处理动作，具体缺失项由绑定前检查列出。
  if (error.code === 8128) return `${error.message}；请按“绑定前检查”的提示回到步骤 2 补齐计分内容。`;
  if (error.code === 8133) return '同一评价用途只能绑定一个评价表单，请保留一个版本后再保存。';
  if (error.code === 8134) return '所选评价表单不属于当前机构或已停用，请刷新列表后重新选择。';
  if (error.code === 8135) return '评价表单类型与绑定用途不匹配，请重新选择与用途一致的版本。';
  return undefined;
}

/**
 * 保存绑定前先跑前端预检。
 * 后端对同一组判据整单回滚，先在界面上说清原因并回到需要修改的步骤，避免"点了保存才知道不行"。
 * 预检只拦必然被后端拒绝的情况；通过后仍以后端返回为准。
 */
async function confirmBindingPrecheck(): Promise<boolean> {
  const issues = blockingBindingIssues.value;
  if (!issues.length) return true;
  const first = issues[0];
  const extra = issues.length > 1 ? `（另有 ${issues.length - 1} 项待处理，详见“绑定前检查”）` : '';
  await ElMessageBox.alert(`${first.message}${extra}`, '暂不能保存评价表单', {
    type: 'warning',
    confirmButtonText: '去处理',
    showClose: false
  }).catch(() => undefined);
  activeStep.value = resolvePrecheckTargetStep(issues);
  return false;
}

async function saveEvaluationForms(): Promise<void> {
  if (!plan.value) return;
  if (!(await confirmBindingPrecheck())) return;
  try {
    await bindTrainingPlanEvaluationForms(plan.value.trainingPlanId, {
      version: plan.value.version,
      forms: selectedEvaluationForms.value.map(({ formVersionId, usageType, isRequired, status }) => ({
        formVersionId,
        usageType,
        isRequired,
        status
      }))
    });
    await loadPlan(plan.value.trainingPlanId);
    ElMessage.success('评价表单版本已保存');
  } catch (error) {
    const message = formatEvaluationBindingError(error);
    if (!message) throw error;
    ElMessage.error(message);
  }
}
async function loadParticipantResults(trainingParticipantId: number): Promise<void> {
  if (!plan.value) return;
  selectedParticipantId.value = trainingParticipantId;
  participantResults.value = await getTrainingParticipantResults(plan.value.trainingPlanId, trainingParticipantId);
}
async function assignParticipant(): Promise<void> {
  if (!plan.value || !selectedUserId.value) return;
  await assignTrainingPlanParticipant(plan.value.trainingPlanId, selectedUserId.value);
  await loadPlan();
  selectedUserId.value = undefined;
  await refreshExecutionFacts();
  ElMessage.success('参训人员已指派');
}
/** 使用统一人员选择条件批量绑定参训人，名单仍以服务端展开后的结果为准。 */
async function handleParticipantSelection(selection: PersonSelectionRequest): Promise<void> {
  if (!plan.value) return;
  await bindTrainingPlanParticipantSelection(plan.value.trainingPlanId, selection);
  await loadPlan();
  await refreshExecutionFacts();
  ElMessage.success('参训人员已绑定');
}
onMounted(async () => {
  await Promise.all([loadTypes(), loadPlanReferenceOptions(), loadPlan(), loadParticipantOptions(), loadEvaluationFormVersions(), loadResources()]);
  selectedStandard.value = form.trainingStandardId ? await getTrainingStandardDetail(form.trainingStandardId) : undefined;
  await refreshExecutionFacts();
  await loadMatrixSource();
});
</script>

<template>
  <section v-loading="loading" class="admin-detail-page">
    <header class="admin-detail-page__header">
      <div>
        <h1 class="plan-title">
          <span>{{ plan?.planName || '新增培训计划' }}</span>
          <ElTag v-if="planStatusMeta" class="plan-title__status" :type="planStatusMeta.type">
            {{ planStatusMeta.label }}
          </ElTag>
        </h1>
        <p>发布后结构、名单与快照均只读。</p>
      </div>
      <div class="plan-actions">
        <!-- 正式结果必须从已结束计划进入，避免脱离计划上下文访问无参页面。 -->
        <ElButton
          v-if="plan?.publishStatus === 'ENDED' && canOperate('training:result:list')"
          type="primary"
          plain
          @click="router.push(`/admin/business-center/training/plan/${plan.trainingPlanId}/ended-result`)"
        >
          查看培训成果
        </ElButton>
        <ElButton
          v-if="plan?.publishStatus === 'ENDED' && canOperate('archive:training:list')"
          plain
          @click="router.push(`/admin/business-center/training/plan/${plan.trainingPlanId}/archive`)"
        >
          查看培训档案
        </ElButton>
        <ElButton @click="router.push('/admin/business-center/training/plan')">返回计划列表</ElButton>
      </div>
    </header>
    <nav class="stage-nav admin-detail-page__steps" aria-label="培训计划工作台步骤">
      <button
        v-for="(item, index) in workspaceSteps"
        :key="item.title"
        type="button"
        class="stage-item"
        :class="{ 'is-active': activeStep === index, 'is-completed': readonly }"
        :disabled="isStepDisabled(index)"
        @click="goStep(index)"
      >
        <span class="stage-index">{{ index + 1 }}</span>
        <span>
          <b>{{ item.title }}</b>
          <small>{{ item.description }}</small>
        </span>
      </button>
    </nav>
    <main class="admin-detail-page__surface">
      <!-- 步骤 1：基础信息。矩阵来源作为只读依据贴在表单上方，主卡仍撑满剩余高度。 -->
      <section v-show="activeStep === 0" class="plan-step plan-step--single">
        <el-alert v-if="matrixSource.length" title="岗位矩阵来源：相同课程版本取最大学时，保存后请核对课表。已有来源不会随矩阵编辑变化。" type="info" :closable="false" />
        <el-table v-if="matrixSource.length" :data="matrixSource" size="small">
          <el-table-column prop="positionName" label="来源岗位" />
          <el-table-column prop="contentName" label="培训内容" />
          <el-table-column prop="courseName" label="课程" />
          <el-table-column prop="requiredHours" label="要求学时" />
        </el-table>
        <PlanBasicPanel
          :model="form"
          :rules="rules"
          :readonly="readonly"
          :created="Boolean(plan)"
          :saving="saving"
          :can-save="!readonly && canOperate('training:plan:add')"
          :can-next="basicStepCompleted"
          :applicable-required-hours="plan?.applicableRequiredHours"
          :historical-nature="plan?.trainingNature === 'UNSPECIFIED'"
          :training-types="trainingTypes"
          :training-standards="trainingStandards"
          :training-templates="trainingTemplates"
          :manager-users="userOptions"
          @save="save"
          @training-type-change="handleTrainingTypeChange"
          @training-standard-change="handleTrainingStandardChange"
          @training-template-change="handleTrainingTemplateChange"
          @next="activeStep = 1"
        />
      </section>

      <!-- 步骤 2：阶段与内容。单主卡，编排依据与课表在同一张卡内滚动。 -->
      <section v-show="activeStep === 1" class="plan-step plan-step--single">
        <PlanStagesPanel
          v-if="plan"
          :stages="stages"
          :resources="resources"
          :teacher-options="userOptions"
          :readonly="readonly"
          :can-next="stagesStepCompleted"
          :focus-content-id="focusedPrecheckContentId"
          :compliance-summary="complianceSummary"
          @add-stage="addStage"
          @add-content="addContent"
          @remove-stage="removeStage"
          @remove-content="removeContent"
          @content-type-change="changeContentType"
          @save="saveStages"
          @back="activeStep = 0"
          @next="activeStep = 2"
          @resource-change="selectContentResource"
          @add-required-content="addRequiredContent"
          @precheck-focus-consumed="focusedPrecheckContentId = undefined"
        />
      </section>

      <!-- 步骤 3：评价表单。综合评价与专家检查左右并列，底部固定保存。 -->
      <section v-show="activeStep === 2" class="plan-step plan-step--single">
        <ElCard v-if="plan" class="admin-detail-page__card">
          <template #header>
            <div class="card-header">
              <div>
                <h2>绑定评价表单</h2>
                <p>综合评价与专家检查独立配置，保存时统一替换当前计划绑定。</p>
              </div>
              <ElTag effect="plain" type="info">已发布版本</ElTag>
            </div>
          </template>

          <!-- 与阶段内容编排一致：仅中间配置区滚动，标题与保存操作始终可见。 -->
          <div class="admin-detail-page__form-scroll evaluation-binding-layout__panels">
            <PlanEvaluationPanel
              :selected-ids="selectedEvaluationForms.filter((item) => item.usageType === 'COMPOSITE').map((item) => item.formVersionId)"
              :bindings="selectedEvaluationForms"
              :versions="formVersions"
              :readonly="readonly"
              :context-hints="evaluationContextHints"
              :precheck-issues="blockingBindingIssues"
              @selection-change="handleEvaluationSelection"
            />
            <PlanExpertInspectionPanel
              :versions="expertInspectionVersions"
              :bindings="selectedEvaluationForms"
              :details="evaluationVersionDetails"
              :readonly="readonly"
              @selection-change="handleExpertInspectionSelection"
            />
          </div>
          <footer v-if="!readonly" class="evaluation-binding-layout__footer">
            <span>保存后，两个评价用途的绑定将同时生效。</span>
            <ElButton type="primary" @click="saveEvaluationForms">保存评价表单</ElButton>
          </footer>
        </ElCard>
      </section>

      <!-- 步骤 4：人员与执行。主栏只放参训名单（这一步的阅读主体，独占整列高度）；
           侧栏承载发布执行、审批与变更三类计划级事务。 -->
      <section v-show="activeStep === 3" class="plan-step plan-step--split">
        <div class="plan-step__main">
          <div class="plan-step__block plan-step__block--grow">
            <PlanExecutionPanel
              v-if="plan"
              mode="participants"
              :training-plan-id="planId"
              :publish-status="plan.publishStatus"
              :can-assign="!approvalPending && canOperate('training:participant:assign')"
              :selected-user-id="selectedUserId"
              :user-options="userOptions"
              :execution-count="arrangedContentCount"
              :content-count="contentCount"
              :arranged-content-count="arrangedContentCount"
              :required-content-count="requiredContentCount"
              :ready-required-content-count="readyRequiredContentCount"
              :stages="stages"
              :participants="participants"
              :participant-loading="participantLoading"
              :results="participantResults"
              @update:selected-user-id="selectedUserId = $event"
              @assign="assignParticipant"
              @bind-selection="handleParticipantSelection"
              @refresh-participants="refreshExecutionFacts"
              @view-result="loadParticipantResults"
            />
          </div>
        </div>
        <aside class="plan-step__aside">
          <div class="plan-step__block">
            <PlanExecutionPanel
              v-if="plan"
              mode="lifecycle"
              :can-publish="approvalReady"
              :publish-status="plan.publishStatus"
              :can-assign="false"
              :user-options="userOptions"
              :execution-count="arrangedContentCount"
              :content-count="contentCount"
              :arranged-content-count="arrangedContentCount"
              :required-content-count="requiredContentCount"
              :ready-required-content-count="readyRequiredContentCount"
              :generation-result="generationResult"
              :generation-loading="generationLoading"
              :participants="participants"
              :end-precheck-loading="endPrecheckLoading"
              :end-precheck-blockers="endPrecheckBlockers"
              :end-precheck-error="endPrecheckError"
              :has-expert-inspection="Boolean(expertInspectionBinding)"
              :can-view-expert-inspection="canOperate('evaluation:record:list')"
              @action="run"
              @refresh-end-precheck="loadEndPrecheck"
              @view-expert-inspection="expertInspectionVisible = true"
            />
          </div>
          <div class="plan-step__block">
            <PlanApprovalPanel
              v-if="plan"
              :plan="plan"
              @state="(pending, approved) => { approvalPending = pending; approvalReady = approved; }"
              @changed="loadPlan()"
            />
          </div>
          <div class="plan-step__block">
            <PlanChangePanel
              v-if="plan && ['PUBLISHED','RUNNING','ENDED','CANCELLED','ARCHIVED'].includes(plan.publishStatus)"
              :plan="plan"
              :participants="participants"
              :training-types="trainingTypes"
              :training-standards="trainingStandards"
              :training-templates="trainingTemplates"
              :resources="resources"
              :users="userOptions"
              :form-versions="[...formVersions.map(v => ({...v, formType: 'COMPOSITE' as const})),...expertInspectionVersions.map(v => ({...v, formType: 'EXPERT_INSPECTION' as const}))]"
              @changed="refreshExecutionFacts(); loadPlan()"
            />
          </div>
        </aside>
      </section>

      <!-- 步骤 5：过程与结果。只在第五步挂载，避免把过程事实带进前四步。 -->
      <section v-show="activeStep === 4" class="plan-step plan-step--single">
        <PlanProcessResultsPanel
          v-if="plan && activeStep === 4"
          :offline-sessions="offlineSessions"
          :attachments="attachments"
          :reward-penalties="rewardPenalties"
          :participants="participants"
          :participant-names="participantNames"
          :stages="stages"
          :plan-id="plan.trainingPlanId"
          :plan-status="plan.publishStatus"
          :show-attachments="canOperate('training:attachment:list')"
          :show-rewards="canOperate('training:reward:list')"
          :can-add-reward="canOperate('training:reward:add')"
          :can-edit-reward="canOperate('training:reward:edit')"
          :can-delete-reward="canOperate('training:reward:delete')"
          :can-add-material="canOperate('training:attachment:add')"
          :can-edit-material="canOperate('training:attachment:edit')"
          :can-delete-material="canOperate('training:attachment:delete')"
          @reward-changed="handleProcessFactsChanged"
          @material-changed="handleProcessFactsChanged"
        />
      </section>
    </main>
    <ElDialog v-model="publishIssuesVisible" title="发布预检问题明细" width="760px" append-to-body>
      <ElAlert title="题库练习不评分：全部题目作答并提交即完成；简答题只记录作答，不进入批阅或正确率。" type="info" :closable="false" />
      <div class="publish-issues">
        <section
          v-for="(issue, index) in publishIssues"
          :key="`${issue.code}-${issue.planContentId}-${issue.ruleIndex}-${index}`"
          class="publish-issue"
        >
          <div>
            <strong>{{ issue.contentName || `问题 ${index + 1}` }}</strong>
            <p>{{ formatPublishIssues([issue]).replace('发布前还需要调整：', '') }}</p>
            <div v-if="publishIssueDetails(issue).length" class="publish-issue__details">
              <ElTag v-for="detail in publishIssueDetails(issue)" :key="detail" effect="plain">{{ detail }}</ElTag>
            </div>
            <small v-if="issue.suggestion">处理建议：{{ issue.suggestion }}</small>
          </div>
          <ElButton type="primary" plain @click="handlePublishIssue(issue)">前往调整</ElButton>
        </section>
      </div>
      <template #footer><ElButton @click="publishIssuesVisible = false">关闭</ElButton></template>
    </ElDialog>
    <ElDialog v-model="expertInspectionVisible" title="专家查课" width="980px" append-to-body destroy-on-close>
      <PlanExpertInspectionExecutionPanel
        v-if="plan && expertInspectionBinding && expertInspectionVisible"
        :training-plan-id="plan.trainingPlanId"
        :publish-status="plan.publishStatus"
        :form-version-id="expertInspectionBinding.formVersionId"
        :form-name="expertInspectionBinding.formName"
        :detail="evaluationVersionDetails[expertInspectionBinding.formVersionId]"
        :can-submit="canOperate('evaluation:record:add') && canOperate('evaluation:record:submit')"
      />
    </ElDialog>
  </section>
</template>

<style scoped lang="less">
.sticky-actions {
  display: flex;
  justify-content: flex-end;
  gap: 8px;
  margin-top: 16px;
}
.plan-title {
  display: flex;
  align-items: center;
  gap: 8px;
}
.plan-title__status {
  flex: 0 0 auto;
}
.plan-actions {
  display: flex;
  gap: 8px;
}
/*
 * 工作台步骤容器。
 * 主内容区是 flex 行容器，早前把多张卡片直接并列摆放会被压成一条竖缝；
 * 这里改为「一步骤一容器」：单栏步骤让主卡撑满剩余高度，两栏步骤由容器自己分栏。
 */
.plan-step {
  display: flex;
  min-width: 0;
  min-height: 0;
  flex: 1 1 auto;
  gap: 12px;
  padding: 12px;
}
.plan-step--single {
  flex-direction: column;
  overflow: hidden;
}
/* 矩阵来源属于步骤内的只读依据，不参与主卡高度分配。 */
.plan-step--single > .el-alert,
.plan-step--single > .el-table {
  flex: 0 0 auto;
}
.plan-step--split {
  flex-direction: row;
  overflow: hidden;
}
.plan-step__main,
.plan-step__aside {
  display: flex;
  min-height: 0;
  flex-direction: column;
  gap: 12px;
  overflow-x: hidden;
  overflow-y: auto;
}
.plan-step__main {
  min-width: 0;
  flex: 1 1 auto;
}
/* 侧栏宽度固定，审批与变更这类事务面板在窄列里按列表排版。 */
.plan-step__aside {
  width: 420px;
  flex: 0 0 420px;
}
/* 每块包含一张完整业务卡；卡片按内容高度排列，由主栏/侧栏统一滚动。 */
.plan-step__block {
  display: flex;
  min-width: 0;
  flex: 0 0 auto;
  flex-direction: column;
}
.plan-step__block > .admin-detail-page__card {
  flex: 0 0 auto;
}
/* 参训名单是这一步的唯一主体：独占主栏整列高度，表格在自己的卡内滚动。
   主栏高度不足时给出 320px 下限，由主栏整体滚动兜底。 */
.plan-step__block--grow {
  min-height: 320px;
  flex: 1 1 auto;
}
/* 侧栏是窄列：状态流由四列压成两列，避免每格只剩一个编号。 */
.plan-step__aside :deep(.status-flow) {
  grid-template-columns: repeat(2, minmax(0, 1fr));
}
/* 指标带三格在窄列里仍排一行，别竖着堆成三块。 */
.plan-step__aside :deep(.summary-grid) {
  flex-wrap: nowrap;
}
.plan-step__aside :deep(.summary-grid > *) {
  flex: 1 1 0;
  padding: 6px 10px;
}
.plan-step__block--grow > .admin-detail-page__card {
  min-height: 0;
  flex: 1 1 auto;
}
@media (max-width: 1280px) {
  .plan-step--split {
    flex-direction: column;
    overflow-y: auto;
  }
  .plan-step__main,
  .plan-step__aside {
    overflow: visible;
  }
  .plan-step__aside {
    width: auto;
    flex: 0 0 auto;
  }
}
.stage-card {
  margin-top: 12px;
  padding: 12px;
  border: 1px solid var(--color-border);
  border-radius: var(--radius-card);
}
.participant-table,
.result-summary {
  margin-top: 16px;
}
.participant-assign {
  display: flex;
  gap: 8px;
  margin-bottom: 16px;
}
.process-facts {
  margin-top: 16px;
}
.evaluation-binding-layout {
  display: flex;
  width: 100%;
  min-height: 0;
  flex: 1;
  flex-direction: column;
  overflow: hidden;
  border: 1px solid var(--color-border);
  border-radius: 10px;
  background: var(--color-bg-surface);
}
.evaluation-binding-layout__footer {
  flex: 0 0 auto;
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
  padding: 16px 20px;
}
.evaluation-binding-layout__footer span {
  margin: 3px 0 0;
  color: var(--color-text-secondary);
  font-size: 12px;
  line-height: 18px;
}
.evaluation-binding-layout__panels {
  display: grid;
  align-content: start;
  grid-template-columns: minmax(0, 1fr) minmax(0, 1fr);
  gap: 16px;
  padding: 9px;
  background: var(--color-bg-subtle);
}
.evaluation-binding-layout__footer {
  border-top: 1px solid var(--color-border);
  background: var(--color-bg-surface);
}
@media (max-width: 1120px) {
  .evaluation-binding-layout {
    border-radius: 8px;
  }
  .evaluation-binding-layout__panels {
    grid-template-columns: 1fr;
  }
}
.publish-issues {
  display: grid;
  gap: 10px;
  margin-top: 14px;
}
.publish-issue {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 16px;
  padding: 12px;
  border: 1px solid var(--el-border-color-light);
  border-radius: 6px;
}
.publish-issue strong,
.publish-issue p,
.publish-issue small {
  display: block;
}
.publish-issue p {
  margin: 6px 0;
  color: var(--el-text-color-regular);
}
.publish-issue small {
  color: var(--el-text-color-secondary);
}
.publish-issue__details {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
  margin: 6px 0;
}
</style>
