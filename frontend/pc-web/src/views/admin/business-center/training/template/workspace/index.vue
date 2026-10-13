<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue';
import { useRoute, useRouter } from 'vue-router';
import { ElMessage, ElMessageBox, type FormRules } from 'element-plus';
import { getTrainingReferenceResourcePage, type TrainingReferenceResource } from '@/api/training-reference';
import { getTrainingStandardDetail, getTrainingStandardPage, type TrainingStandard, type TrainingStandardRuleType } from '@/api/training-standard';
import {
  createTrainingTemplate,
  getTrainingTemplate,
  getTrainingTemplateCategoryTree,
  publishTrainingTemplate,
  replaceTrainingTemplateStages,
  updateTrainingTemplate,
  type TrainingTemplate,
  type TrainingTemplateCategoryTreeNode,
  type TrainingTemplateContentType,
  type TrainingTemplateStagePayload,
  type TrainingTemplateStageType
} from '@/api/training-template';
import { getTrainingTypePage, type TrainingType } from '@/api/training-type';
import { getUserPage, type SystemUser } from '@/api/system-user';
import { useUserStore } from '@/stores';
import { hasPermission } from '@/utils/permission';
import TemplateBasicPanel from '../components/TemplateBasicPanel.vue';
import TemplatePublishPanel from '../components/TemplatePublishPanel.vue';
import TemplateStagesPanel from '../components/TemplateStagesPanel.vue';
import {
  normalizeAssessmentRequiredHours,
  normalizeTrainingRatio,
  summarizeStandardRuleCoverage,
  summarizeTrainingComposition
} from '@/libs/training-composition';

/** 工作台阶段内容始终保留数组，便于集中编排。 */
interface WorkbenchStage extends Omit<TrainingTemplateStagePayload, 'contents'> {
  /** 阶段内的培训内容。 */
  contents: NonNullable<TrainingTemplateStagePayload['contents']>;
}

/** 模板基础表单。 */
interface TemplateBasicForm {
  /** 模板编码。 */
  templateCode: string;
  /** 模板名称。 */
  templateName: string;
  /** 模板分类 ID。 */
  templateCategoryId: number | undefined;
  /** 培训类型 ID。 */
  trainingTypeId: number | undefined;
  /** 培训标准 ID。 */
  trainingStandardId: number | undefined;
  /** 模板说明。 */
  description: string;
  /** 启停状态。 */
  status: 0 | 1;
}

/** OpenAPI 列表接口转换后的统一选择项。 */
interface SelectionOption {
  /** Contract 要求提交的资源 ID。 */
  value: number;
  /** 用户可识别的资源名称。 */
  name: string;
  /** 当前可用状态中文说明。 */
  statusLabel: string;
  /** 可执行版本 ID；题库按契约为空。 */
  versionId?: number | null;
}

/** 阶段编排页中由培训标准转换出的规则提示。 */
interface TemplateStandardRuleHint {
  /** 列表渲染使用的稳定键。 */
  key: string;
  /** 规则名称。 */
  label: string;
  /** 规则目标和当前差异说明。 */
  description: string;
  /** 状态短文案。 */
  statusText: string;
  /** Element Plus 标签状态。 */
  statusType: 'success' | 'warning' | 'info';
  /** 原始标准规则类型。 */
  ruleType: TrainingStandardRuleType;
  /** 指定课程或考试资源 ID。 */
  resourceId?: number;
  /** 指定课程或考试对应的可执行资源版本。 */
  resourceVersionId?: number;
  /** 快捷添加时写入阶段内容的名称。 */
  contentName?: string;
  /** 学时或比例要求值。 */
  requiredValue?: number;
}

/** 内容类型中文选项。 */
const CONTENT_TYPE_OPTIONS: Array<{ label: string; value: TrainingTemplateContentType }> = [
  { label: '课程', value: 'COURSE' },
  { label: '考试', value: 'EXAM' },
  { label: '题库练习', value: 'QUESTION_BANK' },
  { label: '线下活动', value: 'OFFLINE' }
];

/** 阶段类型中文选项。 */
const STAGE_TYPE_OPTIONS: Array<{ label: string; value: TrainingTemplateStageType }> = [
  { label: '线上', value: 'ONLINE' },
  { label: '线下', value: 'OFFLINE' },
  { label: '混合', value: 'MIXED' }
];

const route = useRoute();
const router = useRouter();
const userStore = useUserStore();
const loading = ref(false);
const saving = ref(false);
const activeStep = ref(0);
/** 基础信息仅在成功提交后允许进入阶段编排。 */
const basicStepCompleted = ref(false);
/** 最近一次成功保存的阶段编排快照，用于判断当前草稿是否仍可进入发布确认。 */
const savedStagesFingerprint = ref<string>();
/** 工作台步骤统一使用可点击导航，顺序与详情卡片保持一致。 */
const workspaceSteps = [
  { title: '基础信息', description: '分类、类型与标准' },
  { title: '阶段内容', description: '统一覆盖保存编排' },
  { title: '发布确认', description: '检查后冻结模板' }
];
const detail = ref<TrainingTemplate>();
const categoryTree = ref<TrainingTemplateCategoryTreeNode[]>([]);
/** 保留类型原始字段，供编排步骤展示学时与线上比例基线。 */
const trainingTypes = ref<TrainingType[]>([]);
const trainingTypeOptions = ref<SelectionOption[]>([]);
const trainingStandardOptions = ref<SelectionOption[]>([]);
const courseResourceOptions = ref<SelectionOption[]>([]);
const examResourceOptions = ref<SelectionOption[]>([]);
const questionBankResourceOptions = ref<SelectionOption[]>([]);
const categoryProps = {
  label: 'categoryName',
  value: 'templateCategoryId',
  children: 'children'
};
const basicForm = reactive<TemplateBasicForm>(emptyBasicForm());
const stages = ref<WorkbenchStage[]>([]);
/** 线下执行配置的授课教师候选集。 */
const userOptions = ref<SystemUser[]>([]);
/** 仅在选择标准后读取完整规则，用于当前模板编排的辅助提示。 */
const selectedStandard = ref<TrainingStandard>();
/** 当前阶段编排的稳定序列化结果；新增后删除会恢复为原快照。 */
function stagesFingerprint(): string {
  return JSON.stringify(stages.value);
}
/** 仅当当前编排与最近一次成功保存的内容完全一致时，才允许进入发布确认。 */
const stagesStepCompleted = computed(() => savedStagesFingerprint.value !== undefined && savedStagesFingerprint.value === stagesFingerprint());
const routeTemplateId = computed(() => {
  const value = route.params.trainingTemplateId;
  return value && value !== 'create' ? Number(value) : 0;
});
const isCreated = computed(() => Boolean(detail.value?.trainingTemplateId || routeTemplateId.value));
const readonly = computed(() => Boolean(detail.value && detail.value.publishStatus !== 'DRAFT'));
const canEdit = computed(() => !readonly.value && canOperate('training:template:edit'));
const canPublish = computed(() => !readonly.value && canOperate('training:template:publish'));
const pageTitle = computed(() => detail.value?.templateName || '新增培训模板');
const publishStatusText = computed(() => {
  if (detail.value?.publishStatus === 'PUBLISHED') return '已发布';
  if (detail.value?.publishStatus === 'ARCHIVED') return '已归档';
  return '草稿';
});
/** 模板阶段编排的实时合规摘要，最终发布由计划服务端预检决定。 */
const trainingCompositionSummary = computed(() => {
  const composition = summarizeTrainingComposition(stages.value);
  const type = trainingTypes.value.find((item) => item.trainingTypeId === basicForm.trainingTypeId);
  const rules = selectedStandard.value?.rules ?? [];
  const requiredHours = Math.max(0, ...rules.filter((item) => item.status === 1 && item.ruleType === 'TOTAL_HOURS').map((item) => Number(item.requiredValue ?? 0)));
  const ratio = rules.find((item) => item.status === 1 && item.ruleType === 'ONLINE_RATIO')?.requiredValue;
  const targetRatio = ratio === undefined ? undefined : normalizeTrainingRatio(Number(ratio));
  const typeRatio = normalizeTrainingRatio(type?.onlineMaxRatio);
  return {
    title: `培训类型：${type?.typeName ?? '未选择'}（初培最低 ${type?.initialHours ?? '-'} 学时，复审最低 ${type?.reviewHours ?? '-'} 学时，线上最高 ${(normalizeTrainingRatio(type?.onlineMaxRatio) * 100).toFixed(0)}%）；当前总学时 ${composition.totalHours} / 标准 ${requiredHours || '未设置'}；线上 ${(composition.onlineRatio * 100).toFixed(0)}%${ratio === undefined ? '' : ` / 标准 ${(normalizeTrainingRatio(Number(ratio)) * 100).toFixed(0)}%`}`,
    metrics: [
      {
        label: '类型初培最低',
        current: `${type?.initialHours ?? '-'} 学时`,
        target: '培训类型基线',
        satisfied: true
      },
      {
        label: '总学时',
        current: `${composition.totalHours} 学时`,
        target: requiredHours ? `${requiredHours} 学时` : '标准未设置',
        satisfied: !requiredHours || composition.totalHours >= requiredHours
      },
      {
        label: '线上占比',
        current: `${(composition.onlineRatio * 100).toFixed(0)}%`,
        target: targetRatio === undefined ? `${(typeRatio * 100).toFixed(0)}%` : `${(targetRatio * 100).toFixed(0)}%`,
        satisfied: targetRatio === undefined ? true : composition.onlineRatio <= targetRatio
      },
      {
        label: '线上学时',
        current: `${composition.onlineHours} 学时`,
        target: '课程/考试/题库合计',
        satisfied: true
      }
    ],
    coverage: summarizeStandardRuleCoverage(stages.value, rules)
  };
});
/** 按已选培训标准生成可见、可补齐的阶段编排规则。 */
const standardRuleHints = computed<TemplateStandardRuleHint[]>(() => {
  const rules = (selectedStandard.value?.rules ?? []).filter((item) => item.status === 1);
  const composition = summarizeTrainingComposition(stages.value);
  const contents = stages.value.flatMap((stage) => stage.contents);
  const offlineHours = contents
    .filter((item) => item.contentType === 'OFFLINE')
    .reduce((total, item) => total + Number(item.requiredHours ?? 0), 0);
  return rules.map((rule, index) => {
    const requiredValue = Number(rule.requiredValue ?? 0);
    const base = {
      key: `${rule.ruleType}-${rule.resourceId ?? index}`,
      ruleType: rule.ruleType,
      resourceId: rule.resourceId,
      requiredValue,
      contentName: rule.ruleType === 'OFFLINE' ? rule.description || '线下培训' : undefined,
      resourceVersionId: undefined as number | undefined
    };
    if (rule.ruleType === 'TOTAL_HOURS') {
      const satisfied = composition.totalHours >= requiredValue;
      return {
        ...base,
        label: '总学时要求',
        description: `标准要求不少于 ${requiredValue} 学时，当前 ${composition.totalHours} 学时。`,
        statusText: satisfied ? '已满足' : `差 ${Math.max(requiredValue - composition.totalHours, 0)} 学时`,
        statusType: satisfied ? 'success' : 'warning'
      };
    }
    if (rule.ruleType === 'ONLINE_RATIO') {
      const targetRatio = normalizeTrainingRatio(requiredValue);
      const satisfied = composition.totalHours === 0 || composition.onlineRatio <= targetRatio;
      return {
        ...base,
        label: '线上占比要求',
        description: `标准要求线上占比不高于 ${(targetRatio * 100).toFixed(0)}%，当前 ${(composition.onlineRatio * 100).toFixed(0)}%。`,
        statusText: satisfied ? '已满足' : '需降低线上占比',
        statusType: satisfied ? 'success' : 'warning'
      };
    }
    if (rule.ruleType === 'OFFLINE') {
      const satisfied = requiredValue ? offlineHours >= requiredValue : contents.some((item) => item.contentType === 'OFFLINE');
      return {
        ...base,
        label: '线下培训要求',
        description: requiredValue ? `标准要求线下不少于 ${requiredValue} 学时，当前 ${offlineHours} 学时。` : '标准要求包含线下培训内容。',
        statusText: satisfied ? '已满足' : '待补充',
        statusType: satisfied ? 'success' : 'warning'
      };
    }
    const resourceName = findRuleResourceName(rule.ruleType, rule.resourceId);
    const resource = resourceOptions(rule.ruleType as TrainingTemplateContentType).find((item) => item.value === rule.resourceId);
    const satisfied = contents.some((item) => item.contentType === rule.ruleType && item.resourceId === rule.resourceId);
    return {
      ...base,
      label: rule.ruleType === 'COURSE' ? '指定课程' : '指定考试',
      description: `标准要求包含 ${resourceName}。`,
      contentName: resource?.name ?? resourceName,
      resourceVersionId: resource?.versionId ?? undefined,
      statusText: satisfied ? '已满足' : '待补充',
      statusType: satisfied ? 'success' : 'warning'
    };
  });
});
const basicFormRules: FormRules<TemplateBasicForm> = {
  templateCode: [{ required: true, message: '请输入模板编码', trigger: 'blur' }],
  templateName: [{ required: true, message: '请输入模板名称', trigger: 'blur' }],
  trainingTypeId: [{ required: true, message: '请选择培训类型', trigger: 'change' }]
};

/** 创建空模板表单。 */
function emptyBasicForm(): TemplateBasicForm {
  return {
    templateCode: '',
    templateName: '',
    templateCategoryId: undefined,
    trainingTypeId: undefined,
    trainingStandardId: undefined,
    description: '',
    status: 1
  };
}

/** 按服务端下发的按钮权限控制工作台动作。 */
function canOperate(permission: string): boolean {
  return hasPermission(userStore.userInfo, permission);
}

/** 工作台导航严格对应当前已确认保存的步骤。 */
function isStepDisabled(step: number): boolean {
  if (readonly.value) return false;
  if (step === 1) return !basicStepCompleted.value;
  if (step === 2) return !stagesStepCompleted.value;
  return false;
}

/** 切换工作台步骤，并为未保存的当前内容给出明确反馈。 */
function goStep(step: number): void {
  if (isStepDisabled(step)) {
    ElMessage.warning(step === 1 ? '请先保存基础信息。' : '请先保存阶段与内容编排。');
    return;
  }
  activeStep.value = step;
}

/** 基础信息发生变化后，必须重新保存才能进入下一步。 */
function markBasicDirty(): void {
  if (!readonly.value) basicStepCompleted.value = false;
}

/** 根据内容类型返回对应的可用资源。 */
function resourceOptions(contentType: TrainingTemplateContentType): SelectionOption[] {
  if (contentType === 'COURSE') return courseResourceOptions.value;
  if (contentType === 'EXAM') return examResourceOptions.value;
  if (contentType === 'QUESTION_BANK') return questionBankResourceOptions.value;
  return [];
}

/** 标准指定资源转成用户可读名称，避免规则卡只显示 ID。 */
function findRuleResourceName(ruleType: TrainingStandardRuleType, resourceId?: number): string {
  if (!resourceId) return '未指定资源';
  const options = ruleType === 'COURSE' ? courseResourceOptions.value : ruleType === 'EXAM' ? examResourceOptions.value : [];
  return options.find((item) => item.value === resourceId)?.name ?? `资源 ID ${resourceId}`;
}

/** 将当前机构可引用资源转换为统一下拉项。 */
function toResourceOption(item: TrainingReferenceResource): SelectionOption {
  return {
    value: item.resourceId,
    name: `${item.resourceName}（${item.resourceCode}）`,
    statusLabel: '可执行',
    versionId: item.versionId
  };
}

/** 从培训类型列表接口加载当前机构启用类型。 */
async function loadTrainingTypes(): Promise<void> {
  const types = await getTrainingTypePage({
    pageNum: 1,
    pageSize: 100,
    'query.status': 1
  });
  trainingTypes.value = types.list;
  trainingTypeOptions.value = types.list.map((item) => ({
    value: item.trainingTypeId,
    name: item.typeName,
    statusLabel: '启用'
  }));
}

/** 从培训标准列表接口加载当前类型下已发布标准。 */
async function loadTrainingStandards(): Promise<void> {
  const standards = await getTrainingStandardPage({
    pageNum: 1,
    pageSize: 100,
    ...(basicForm.trainingTypeId ? { 'query.trainingTypeId': basicForm.trainingTypeId } : {}),
    'query.publishStatus': 'PUBLISHED',
    'query.status': 1
  });
  trainingStandardOptions.value = standards.list.map((item) => ({
    value: item.trainingStandardId,
    name: `${item.standardName} ${item.standardVersion}`,
    statusLabel: '已发布'
  }));
}

/** 从可引用资源接口分别加载课程、考试和题库。 */
async function loadContentResourceOptions(): Promise<void> {
  const [courses, exams, questionBanks] = await Promise.all([
    getTrainingReferenceResourcePage({
      pageNum: 1,
      pageSize: 100,
      'query.resourceType': 'COURSE'
    }),
    getTrainingReferenceResourcePage({
      pageNum: 1,
      pageSize: 100,
      'query.resourceType': 'EXAM'
    }),
    getTrainingReferenceResourcePage({
      pageNum: 1,
      pageSize: 100,
      'query.resourceType': 'QUESTION_BANK'
    })
  ]);
  courseResourceOptions.value = courses.list.map(toResourceOption);
  examResourceOptions.value = exams.list.map(toResourceOption);
  questionBankResourceOptions.value = questionBanks.list.map(toResourceOption);
}

/** 首次进入工作台时集中加载培训依据和阶段内容资源。 */
async function loadReferenceOptions(): Promise<void> {
  await Promise.all([loadTrainingTypes(), loadTrainingStandards(), loadContentResourceOptions(), loadUserOptions()]);
}

/** 复用系统用户列表，禁止在线下配置中手输教师 ID。 */
async function loadUserOptions(): Promise<void> {
  userOptions.value = (await getUserPage({ pageNum: 1, pageSize: 100, 'query.status': 1 })).list;
}

/** 培训类型变化后仅清空并重载依赖的已发布标准。 */
async function handleTrainingTypeChange(): Promise<void> {
  markBasicDirty();
  basicForm.trainingStandardId = undefined;
  selectedStandard.value = undefined;
  await loadTrainingStandards();
}
/** 标准变化后读取规则详情，供阶段编排显示可见的引用提示。 */
async function handleTrainingStandardChange(): Promise<void> {
  markBasicDirty();
  selectedStandard.value = basicForm.trainingStandardId ? await getTrainingStandardDetail(basicForm.trainingStandardId) : undefined;
}

/** 切换内容类型时清理不兼容资源；线下内容明确不绑定资源。 */
function handleContentTypeChange(content: WorkbenchStage['contents'][number]): void {
  content.resourceId = undefined;
  content.resourceVersionId = undefined;
  content.assessmentMode = undefined;
  content.executionConfig = undefined;
  if (content.contentType === 'EXAM' || content.contentType === 'QUESTION_BANK') content.requiredHours = 0;
  if (content.contentType === 'OFFLINE') return;
  content.contentName = '';
}

/** 选择线上资源时同步 Contract 需要提交的内容名称快照。 */
function selectContentResource(content: WorkbenchStage['contents'][number], resourceId: number): void {
  const selected = resourceOptions(content.contentType).find((item) => item.value === resourceId);
  if (!selected) return;
  Object.assign(content, {
    contentName: selected.name,
    resourceVersionId: selected.versionId ?? undefined
  });
}

/** 返回模板列表。 */
async function backToList(): Promise<void> {
  await router.push('/admin/business-center/training/template');
}

/** 加载模板详情并同步三个步骤的编辑状态。 */
async function loadDetail(): Promise<void> {
  if (!routeTemplateId.value) {
    detail.value = undefined;
    Object.assign(basicForm, emptyBasicForm());
    stages.value = [];
    basicStepCompleted.value = false;
    savedStagesFingerprint.value = undefined;
    return;
  }
  loading.value = true;
  try {
    const data = await getTrainingTemplate(routeTemplateId.value);
    detail.value = data;
    Object.assign(basicForm, {
      templateCode: data.templateCode,
      templateName: data.templateName,
      templateCategoryId: data.templateCategoryId,
      trainingTypeId: data.trainingTypeId,
      trainingStandardId: data.trainingStandardId,
      description: data.description ?? '',
      status: data.status
    });
    stages.value = (data.stages ?? []).map((stage) => ({
      stageName: stage.stageName,
      stageType: stage.stageType,
      requiredHours: stage.requiredHours,
      sortOrder: stage.sortOrder,
      status: stage.status,
      contents: (stage.contents ?? []).map((content) => ({
        contentType: content.contentType,
        resourceId: content.resourceId,
        resourceVersionId: content.resourceVersionId,
        contentName: content.contentName,
        isRequired: content.isRequired,
        requiredHours: content.requiredHours,
        sortOrder: content.sortOrder,
        assessmentMode: content.assessmentMode,
        executionConfig: content.executionConfig ? JSON.parse(JSON.stringify(content.executionConfig)) : undefined,
        status: content.status
      }))
    }));
    normalizeAssessmentRequiredHours(stages.value);
    basicStepCompleted.value = true;
    savedStagesFingerprint.value = stagesFingerprint();
    await Promise.all([loadCategoryTree(), loadReferenceOptions()]);
    selectedStandard.value = basicForm.trainingStandardId ? await getTrainingStandardDetail(basicForm.trainingStandardId) : undefined;
  } finally {
    loading.value = false;
  }
}

/** 加载当前登录机构可用的模板分类树。 */
async function loadCategoryTree(): Promise<void> {
  categoryTree.value = await getTrainingTemplateCategoryTree();
}

/** 保存模板基础信息；首次保存后替换到真实模板地址。 */
async function saveBasic(): Promise<void> {
  saving.value = true;
  try {
    if (detail.value) {
      await updateTrainingTemplate(detail.value.trainingTemplateId, {
        version: detail.value.version,
        templateName: basicForm.templateName,
        templateCategoryId: basicForm.templateCategoryId,
        trainingTypeId: basicForm.trainingTypeId as number,
        trainingStandardId: basicForm.trainingStandardId,
        description: basicForm.description || undefined,
        status: basicForm.status
      });
      ElMessage.success('模板基础信息已保存');
      await loadDetail();
    } else {
      const trainingTemplateId = await createTrainingTemplate({
        templateCode: basicForm.templateCode,
        templateName: basicForm.templateName,
        templateCategoryId: basicForm.templateCategoryId,
        trainingTypeId: basicForm.trainingTypeId as number,
        trainingStandardId: basicForm.trainingStandardId,
        description: basicForm.description || undefined,
        status: basicForm.status
      });
      ElMessage.success('培训模板已创建');
      await router.replace(`/admin/business-center/training/template/${trainingTemplateId}`);
      detail.value = await getTrainingTemplate(trainingTemplateId);
    }
    basicStepCompleted.value = true;
  } finally {
    saving.value = false;
  }
}

/** 新增一个空阶段，并保持排序连续。 */
function addStage(): void {
  stages.value.push({
    stageName: '',
    stageType: 'ONLINE',
    requiredHours: 0,
    sortOrder: stages.value.length + 1,
    status: 1,
    contents: []
  });
}

/** 删除指定阶段并重排。 */
function removeStage(stageIndex: number): void {
  stages.value.splice(stageIndex, 1);
  stages.value.forEach((stage, index) => {
    stage.sortOrder = index + 1;
  });
}

/** 向阶段中新增一项内容。 */
function addContent(stage: WorkbenchStage): void {
  stage.contents.push({
    contentType: 'COURSE',
    contentName: '',
    isRequired: 1,
    requiredHours: 0,
    sortOrder: stage.contents.length + 1,
    status: 1
  });
}

/** 将标准指定内容添加到选择的模板阶段，规则只提供快捷入口，不自动改写课表。 */
function addRequiredContent(hint: TemplateStandardRuleHint, stageIndex = 0): void {
  if (!['COURSE', 'EXAM', 'OFFLINE'].includes(hint.ruleType)) return;
  const contentType = hint.ruleType as Extract<TrainingTemplateContentType, 'COURSE' | 'EXAM' | 'OFFLINE'>;
  let stage = stages.value[stageIndex];
  if (!stage) {
    addStage();
    stage = stages.value[stageIndex] ?? stages.value[0];
  }
  if (!stage) return;
  const exists = stages.value.some((item) =>
    item.contents.some(
      (content) => content.contentType === contentType && (contentType === 'OFFLINE' || content.resourceId === hint.resourceId)
    )
  );
  if (exists) {
    ElMessage.info('该指定内容已添加到阶段内容。');
    return;
  }
  stage.contents.push({
    contentType,
    resourceId: hint.resourceId,
    resourceVersionId: hint.resourceVersionId,
    contentName: hint.contentName ?? hint.description,
    isRequired: 1,
    requiredHours: 0,
    sortOrder: stage.contents.length + 1,
    status: 1
  });
  ElMessage.success(`已添加“${hint.contentName ?? hint.description}”，请继续完善阶段内容。`);
}

/** 删除阶段内容并重排。 */
function removeContent(stage: WorkbenchStage, contentIndex: number): void {
  stage.contents.splice(contentIndex, 1);
  stage.contents.forEach((content, index) => {
    content.sortOrder = index + 1;
  });
}

/** 覆盖保存全部阶段和内容，避免局部保存造成节奏割裂。 */
async function saveStages(): Promise<void> {
  if (!detail.value) return;
  normalizeAssessmentRequiredHours(stages.value);
  const invalidStageIndex = stages.value.findIndex((stage) => !stage.stageName.trim());
  if (invalidStageIndex >= 0) {
    ElMessage.warning(`请输入第 ${invalidStageIndex + 1} 个阶段的名称`);
    return;
  }
  for (const [stageIndex, stage] of stages.value.entries()) {
    const invalidContentIndex = stage.contents.findIndex(
      (content) => !content.contentName.trim() || (content.contentType !== 'OFFLINE' && !content.resourceId)
    );
    if (invalidContentIndex >= 0) {
      ElMessage.warning(`请完整选择第 ${stageIndex + 1} 个阶段的第 ${invalidContentIndex + 1} 项内容`);
      return;
    }
  }
  saving.value = true;
  try {
    await replaceTrainingTemplateStages(detail.value.trainingTemplateId, {
      version: detail.value.version,
      stages: stages.value
    });
    ElMessage.success('阶段与内容已保存');
    await loadDetail();
  } finally {
    saving.value = false;
  }
}

/** 发布前确认不可编辑影响，并携带最新乐观锁版本。 */
async function publish(): Promise<void> {
  if (!detail.value) return;
  try {
    await ElMessageBox.confirm(`发布后“${detail.value.templateName}”及其阶段内容将只读，确认继续吗？`, '发布确认', { type: 'warning' });
    saving.value = true;
    await publishTrainingTemplate(detail.value.trainingTemplateId, { version: detail.value.version });
    ElMessage.success('培训模板已发布');
    await loadDetail();
  } finally {
    saving.value = false;
  }
}

/** 从任意层级的分类树中读取模板当前所属分类名称。 */
function findCategoryName(nodes: TrainingTemplateCategoryTreeNode[], templateCategoryId?: number): string | undefined {
  for (const node of nodes) {
    if (node.templateCategoryId === templateCategoryId) return node.categoryName;
    const childName = findCategoryName(node.children ?? [], templateCategoryId);
    if (childName) return childName;
  }
  return undefined;
}

onMounted(async () => {
  await loadDetail();
  if (!routeTemplateId.value) {
    await Promise.all([loadCategoryTree(), loadReferenceOptions()]);
  }
});
</script>

<template>
  <section v-loading="loading" class="admin-detail-page">
    <header class="admin-detail-page__header">
      <div>
        <h1>
          {{ pageTitle }}
          <ElTag v-if="detail" :type="detail.publishStatus === 'PUBLISHED' ? 'success' : detail.publishStatus === 'ARCHIVED' ? 'info' : 'warning'">
            {{ publishStatusText }}
          </ElTag>
        </h1>
        <p>
          {{ isCreated ? `模板 ID：${detail?.trainingTemplateId ?? routeTemplateId}` : '先保存基础信息，再进行阶段编排与发布' }}
        </p>
      </div>
      <ElButton plain @click="backToList">返回模板列表</ElButton>
    </header>

    <nav class="stage-nav admin-detail-page__steps" aria-label="培训模板工作台步骤">
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
      <TemplateBasicPanel
        v-show="activeStep === 0"
        :model="basicForm"
        :rules="basicFormRules"
        :readonly="readonly"
        :is-created="isCreated"
        :can-next="basicStepCompleted"
        :can-edit="canEdit"
        :saving="saving"
        :category-tree="categoryTree"
        :category-props="categoryProps"
        :training-type-options="trainingTypeOptions"
        :training-standard-options="trainingStandardOptions"
        @save="saveBasic"
        @next="activeStep = 1"
        @dirty="markBasicDirty"
        @training-type-change="handleTrainingTypeChange"
        @training-standard-change="handleTrainingStandardChange"
      />

      <TemplateStagesPanel
        v-show="activeStep === 1"
        :stages="stages"
        :readonly="readonly"
        :can-edit="canEdit"
        :saving="saving"
        :can-next="stagesStepCompleted"
        :composition-summary="trainingCompositionSummary"
        :rule-hints="standardRuleHints"
        :teacher-options="userOptions"
        :content-type-options="CONTENT_TYPE_OPTIONS"
        :stage-type-options="STAGE_TYPE_OPTIONS"
        :resource-options="resourceOptions"
        @add-stage="addStage"
        @remove-stage="removeStage"
        @add-content="addContent"
        @remove-content="removeContent"
        @content-type-change="handleContentTypeChange"
        @resource-change="selectContentResource"
        @add-required-content="addRequiredContent"
        @back="activeStep = 0"
        @save="saveStages"
        @next="activeStep = 2"
      />

      <TemplatePublishPanel
        v-show="activeStep === 2"
        :template-name="basicForm.templateName"
        :template-code="basicForm.templateCode"
        :category-name="findCategoryName(categoryTree, basicForm.templateCategoryId)"
        :training-type-name="trainingTypeOptions.find((item) => item.value === basicForm.trainingTypeId)?.name"
        :training-standard-name="trainingStandardOptions.find((item) => item.value === basicForm.trainingStandardId)?.name"
        :description="basicForm.description"
        :status="basicForm.status"
        :stages="stages"
        :stage-count="stages.length"
        :content-count="stages.reduce((total, stage) => total + stage.contents.length, 0)"
        :readonly="readonly"
        :archived="detail?.publishStatus === 'ARCHIVED'"
        :can-publish="canPublish"
        :saving="saving"
        :created="Boolean(detail)"
        :course-options="courseResourceOptions"
        :exam-options="examResourceOptions"
        :teacher-options="userOptions"
        @back="activeStep = 1"
        @publish="publish"
      />
    </main>

  </section>
</template>

<style scoped lang="less">
.section-heading,
.stage-card > header,
.content-heading,
.step-actions,
.category-actions,
.publish-summary {
  display: flex;
  align-items: center;
}

.section-heading,
.stage-card > header,
.content-heading {
  justify-content: space-between;
}

.option-status {
  float: right;
  margin-top: 3px;
}

.form-panel,
.stage-panel,
.publish-panel {
  padding: 16px;
}

.section-heading {
  margin-bottom: 18px;

  h2 {
    margin: 0;
    color: var(--color-text-primary);
    font-size: 16px;
  }

  p {
    margin: 5px 0 0;
    color: var(--color-text-secondary);
    font-size: 12px;
  }
}

.category-actions {
  gap: 6px;
  margin-bottom: 14px;
}

.form-grid,
.stage-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 480px));
  gap: 0 18px;
}

.form-grid {
  max-width: 984px;

  :deep(.el-input),
  :deep(.el-select),
  :deep(.el-tree-select) {
    width: 320px;
  }
}

.stage-card {
  margin-bottom: 16px;
  padding: 18px;
  border: 1px solid var(--color-border);
  border-radius: var(--radius-card);
  background: var(--color-bg-surface);

  > header {
    margin-bottom: 16px;
  }
}

.content-heading {
  margin: 4px 0 10px;
  padding-top: 14px;
  border-top: 1px solid var(--color-border);
  color: var(--color-text-regular);
  font-weight: 600;
}

.step-actions {
  justify-content: flex-end;
  gap: 8px;
  margin-top: 20px;
  padding-top: 18px;
  border-top: 1px solid var(--color-border);
}

.publish-summary {
  justify-content: center;
  gap: 40px;
  margin: 0 auto 20px;

  span,
  strong {
    display: block;
  }

  span {
    color: var(--color-text-secondary);
    font-size: 12px;
  }

  strong {
    margin-top: 6px;
    color: var(--color-text-primary);
    font-size: 18px;
  }
}

@media (max-width: 1000px) {
  .form-grid,
  .stage-grid {
    grid-template-columns: 1fr;
  }
}
</style>
