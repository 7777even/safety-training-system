<script setup lang="ts">
import { computed, onMounted, reactive, ref, watch } from 'vue';
import { useRoute, useRouter } from 'vue-router';
import { ElMessage, ElMessageBox, type FormRules } from 'element-plus';
import { getTrainingReferenceResourcePage } from '@/api/training-reference';
import {
  createTrainingStandard,
  getTrainingStandardDetail,
  publishTrainingStandard,
  replaceTrainingStandardRules,
  TrainingStandardApiError,
  updateTrainingStandard
} from '@/api/training-standard';
import { getTrainingTypePage, type TrainingType } from '@/api/training-type';
import { useUserStore } from '@/stores';
import { hasPermission } from '@/utils/permission';
import StandardBasicPanel from '../components/StandardBasicPanel.vue';
import StandardPublishPanel from '../components/StandardPublishPanel.vue';
import StandardRulesPanel from '../components/StandardRulesPanel.vue';
import { normalizeTrainingRatio, toTrainingRatio, toTrainingRatioPercent } from '@/libs/training-composition';

type RuleType = 'TOTAL_HOURS' | 'ONLINE_RATIO' | 'COURSE' | 'EXAM' | 'OFFLINE';
type DefaultTrainingNature = 'INITIAL' | 'REVIEW';

/** 标准规则编辑行，与 TrainingStandardRuleRequest 保持一致。 */
interface RuleForm {
  ruleType: RuleType;
  requiredValue?: number;
  unit?: string;
  resourceType?: string;
  resourceId?: number;
  sortOrder: number;
  status: 0 | 1;
  description: string;
}

/** 课程或考试资源选择项，界面只展示可识别名称。 */
interface ResourceOption {
  value: number;
  label: string;
  versionId?: number | null;
}

const route = useRoute();
const router = useRouter();
const userStore = useUserStore();
const loading = ref(false);
const saving = ref(false);
const activeStep = ref(0);
/** 仅已校验并保存的步骤才允许从导航或底部按钮继续。 */
const basicStepCompleted = ref(false);
const rulesStepCompleted = ref(false);
/** 工作台步骤统一使用可点击导航，顺序与详情卡片保持一致。 */
const workspaceSteps = [
  { title: '基础信息', description: '标准范围与有效期' },
  { title: '规则编排', description: '学时、课程与考试要求' },
  { title: '发布确认', description: '冻结当前版本' }
];
const standardId = computed(() => Number(route.params.trainingStandardId) || undefined);
const isCreate = computed(() => !standardId.value);
const readonly = computed(() => form.publishStatus !== 'DRAFT');
const form = reactive({
  standardCode: '',
  standardName: '',
  trainingTypeId: undefined as number | undefined,
  /** 仅用于新建标准时选择默认带入哪一种培训类型学时，不写入标准字段。 */
  defaultTrainingNature: 'INITIAL' as DefaultTrainingNature,
  standardVersion: '',
  validRange: [] as string[],
  status: 1 as 0 | 1,
  publishStatus: 'DRAFT',
  version: 0
});
const rules = ref<RuleForm[]>([]);
/** 最近一次从服务端读取或成功保存的规则快照，用于判断用户是否已撤销编辑。 */
const savedRulesFingerprint = ref('');
const trainingTypeOptions = ref<TrainingType[]>([]);
const courseOptions = ref<ResourceOption[]>([]);
const examOptions = ref<ResourceOption[]>([]);
const formRules: FormRules = {
  standardCode: [{ required: true, message: '请输入标准编码', trigger: 'blur' }],
  standardName: [{ required: true, message: '请输入标准名称', trigger: 'blur' }],
  standardVersion: [{ required: true, message: '请输入标准版本', trigger: 'blur' }],
  trainingTypeId: [{ required: true, message: '请选择培训类型', trigger: 'change' }]
};
const ruleTypeOptions: Array<{ label: string; value: RuleType }> = [
  { label: '总学时', value: 'TOTAL_HOURS' },
  { label: '线上学时占比', value: 'ONLINE_RATIO' },
  { label: '指定课程', value: 'COURSE' },
  { label: '指定考试', value: 'EXAM' },
  { label: '线下培训', value: 'OFFLINE' }
];
/** 当前类型线上占比上限的页面百分数，用于输入限制与保存前提示。 */
const onlineRatioMaxPercent = computed(() => {
  const type = trainingTypeOptions.value.find((item) => item.trainingTypeId === form.trainingTypeId);
  return type?.onlineMaxRatio === undefined ? undefined : toTrainingRatioPercent(type.onlineMaxRatio);
});
/** 所选类型提供的不可降低基线，用于解释标准规则而非替代后端校验。 */
const trainingTypeBaseline = computed(() => {
  const type = trainingTypeOptions.value.find((item) => item.trainingTypeId === form.trainingTypeId);
  return type
    ? `初培最低 ${type.initialHours} 学时，复审最低 ${type.reviewHours} 学时，线上学时占比最高 ${(normalizeTrainingRatio(type.onlineMaxRatio) * 100).toFixed(0)}%`
    : '请选择培训类型后查看学时基线。';
});
/** 汇总当前启用规则，帮助管理员理解标准是否在类型基线之上加严。 */
const ruleComplianceSummary = computed(() => {
  const enabled = rules.value.filter((item) => item.status === 1);
  const totalHours = Math.max(0, ...enabled.filter((item) => item.ruleType === 'TOTAL_HOURS').map((item) => Number(item.requiredValue ?? 0)));
  const ratio = enabled.find((item) => item.ruleType === 'ONLINE_RATIO')?.requiredValue;
  const resources = enabled.filter((item) => ['COURSE', 'EXAM', 'OFFLINE'].includes(item.ruleType)).length;
  return `标准总学时 ${totalHours || '未设置'}；线上占比 ${ratio === undefined ? '未设置' : `${(normalizeTrainingRatio(Number(ratio)) * 100).toFixed(0)}%`}；指定资源规则 ${resources} 条。`;
});

/** 根据用户在新建页面选择的依据带入学时，培训标准本身不保存培训性质。 */
function getDefaultTrainingHours(type: TrainingType): { hours: number; natureText: string } {
  return form.defaultTrainingNature === 'REVIEW' ? { hours: type.reviewHours, natureText: '复审' } : { hours: type.initialHours, natureText: '初培' };
}

/** 新建草稿若尚无规则，则从关联培训类型生成可编辑基线规则，用户保存后才写入后端。 */
function applyTrainingTypeDefaultRules(): void {
  if (readonly.value || rules.value.length > 0) return;
  const type = trainingTypeOptions.value.find((item) => item.trainingTypeId === form.trainingTypeId);
  if (!type) return;
  const defaultHours = getDefaultTrainingHours(type);
  rules.value = [
    {
      ruleType: 'TOTAL_HOURS',
      requiredValue: defaultHours.hours,
      unit: '学时',
      sortOrder: 1,
      status: 1,
      description: `由培训类型${defaultHours.natureText}最低学时带入，可按标准要求加严。`
    },
    {
      ruleType: 'ONLINE_RATIO',
      requiredValue: toTrainingRatioPercent(type.onlineMaxRatio),
      unit: '%',
      sortOrder: 2,
      status: 1,
      description: '由培训类型线上占比上限带入，可按标准要求加严。'
    }
  ];
  refreshRulesStepCompletion();
}

/** 总学时和线上占比是标准级唯一规则，新增时优先使用尚未配置的基础规则类型。 */
function addRule(): void {
  const usedTypes = new Set(rules.value.map((rule) => rule.ruleType));
  const ruleType = (['TOTAL_HOURS', 'ONLINE_RATIO', 'COURSE', 'EXAM', 'OFFLINE'] as RuleType[]).find(
    (type) => !['TOTAL_HOURS', 'ONLINE_RATIO'].includes(type) || !usedTypes.has(type)
  );
  if (!ruleType) {
    ElMessage.warning('总学时和线上学时占比规则各只能配置一条，请先调整已有规则。');
    return;
  }
  rules.value.push({
    ruleType,
    requiredValue: ruleType === 'COURSE' || ruleType === 'EXAM' ? undefined : 0,
    unit: ruleType === 'ONLINE_RATIO' ? '%' : ruleType === 'COURSE' || ruleType === 'EXAM' ? undefined : '学时',
    sortOrder: rules.value.length + 1,
    status: 1,
    description: ''
  });
  refreshRulesStepCompletion();
}

/** 切换规则类型时同步资源枚举并清除已失效选择。 */
function handleRuleTypeChange(row: RuleForm): void {
  row.resourceId = undefined;
  row.resourceType = row.ruleType === 'COURSE' || row.ruleType === 'EXAM' ? row.ruleType : undefined;
  row.requiredValue = row.resourceType ? undefined : 0;
  row.unit = row.ruleType === 'ONLINE_RATIO' ? '%' : row.resourceType ? undefined : '学时';
  refreshRulesStepCompletion();
}

/** 规则保存与发布前使用同一套字段语义校验。 */
function getInvalidRuleIndex(): number {
  if (rules.value.length === 0) return 0;
  const duplicateType = getDuplicateRuleType();
  if (duplicateType) return rules.value.findIndex((rule) => rule.ruleType === duplicateType);
  return rules.value.findIndex((rule) => {
    const needsResource = rule.ruleType === 'COURSE' || rule.ruleType === 'EXAM';
    const needsValue = !needsResource;
    return (
      !rule.description.trim() ||
      (needsResource && !rule.resourceId) ||
      (needsValue && (!Number.isFinite(rule.requiredValue) || Number(rule.requiredValue) < 0)) ||
      (rule.ruleType === 'ONLINE_RATIO' && Number(rule.requiredValue) > 100)
    );
  });
}

/** 查找重复配置的标准级基础规则，指定课程/考试/线下规则仍允许配置多条。 */
function getDuplicateRuleType(): RuleType | undefined {
  for (const ruleType of ['TOTAL_HOURS', 'ONLINE_RATIO'] as RuleType[]) {
    if (rules.value.filter((rule) => rule.ruleType === ruleType).length > 1) return ruleType;
  }
  return undefined;
}

/** 仅比较影响规则保存的字段，避免服务端只读字段干扰步骤状态。 */
function getRulesFingerprint(): string {
  return JSON.stringify(
    rules.value.map(({ ruleType, requiredValue, unit, resourceType, resourceId, sortOrder, status, description }) => ({
      ruleType,
      requiredValue,
      unit,
      resourceType,
      resourceId,
      sortOrder,
      status,
      description
    }))
  );
}

/** 用户撤销临时编辑并恢复到已保存快照时，重新开放下一步。 */
function refreshRulesStepCompletion(): void {
  rulesStepCompleted.value = readonly.value || (getInvalidRuleIndex() < 0 && getRulesFingerprint() === savedRulesFingerprint.value);
}

/** 加载当前机构下的启用培训类型，避免用户手工填写关联主键。 */
async function loadTrainingTypes(): Promise<void> {
  const data = await getTrainingTypePage({
    pageNum: 1,
    pageSize: 100,
    'query.status': 1
  });
  trainingTypeOptions.value = data.list;
}

/** 加载当前机构可执行的课程和考试，考试必须已有可冻结试卷版本。 */
async function loadResourceOptions(): Promise<void> {
  const [courses, exams] = await Promise.all([
    getTrainingReferenceResourcePage({ pageNum: 1, pageSize: 100, 'query.resourceType': 'COURSE' }),
    getTrainingReferenceResourcePage({ pageNum: 1, pageSize: 100, 'query.resourceType': 'EXAM' })
  ]);
  courseOptions.value = courses.list.map((item) => ({ value: item.resourceId, label: item.resourceName, versionId: item.versionId }));
  examOptions.value = exams.list
    .filter((item) => item.versionId !== null && item.versionId !== undefined)
    .map((item) => ({ value: item.resourceId, label: item.resourceName, versionId: item.versionId }));
}

/** 根据后端权限决定编辑和发布入口是否展示。 */
function canOperate(permission: string): boolean {
  return hasPermission(userStore.userInfo, permission);
}

/** 新建标准未落库前不能切换到依赖标准 ID 的后续步骤。 */
function isStepDisabled(step: number): boolean {
  if (step === 1) return !basicStepCompleted.value;
  if (step === 2) return !rulesStepCompleted.value;
  return false;
}

/** 切换工作台步骤，并为未保存的新标准给出明确反馈。 */
function goStep(step: number): void {
  if (isStepDisabled(step)) {
    ElMessage.warning(step === 1 ? '请先完整填写并保存基础信息。' : '请先完整填写并保存规则编排。');
    return;
  }
  activeStep.value = step;
}

/** 加载标准详情及其规则。 */
async function loadDetail(targetStandardId = standardId.value): Promise<void> {
  if (!targetStandardId) return;
  loading.value = true;
  try {
    const detail = await getTrainingStandardDetail(targetStandardId);
    Object.assign(form, detail, {
      validRange: [detail.validFrom, detail.validTo].filter(Boolean)
    });
    rules.value = (detail.rules ?? []).map((rule: RuleForm) => ({
      ...rule,
      requiredValue: rule.ruleType === 'ONLINE_RATIO' ? toTrainingRatioPercent(rule.requiredValue) : rule.requiredValue
    }));
    basicStepCompleted.value = true;
    savedRulesFingerprint.value = getRulesFingerprint();
    await Promise.all([loadTrainingTypes(), loadResourceOptions()]);
    applyTrainingTypeDefaultRules();
    refreshRulesStepCompletion();
  } finally {
    loading.value = false;
  }
}

/** 保存基础信息；首次保存后切换到带 ID 的工作台地址。 */
async function saveBasic(): Promise<void> {
  saving.value = true;
  try {
    const payload = {
      standardName: form.standardName,
      trainingTypeId: form.trainingTypeId as number,
      standardVersion: form.standardVersion,
      validFrom: form.validRange[0],
      validTo: form.validRange[1],
      status: form.status
    };
    if (standardId.value) {
      await updateTrainingStandard(standardId.value, { ...payload, version: form.version });
      await loadDetail();
    } else {
      const id = await createTrainingStandard({
        ...payload,
        standardCode: form.standardCode
      });
      await router.replace(`/admin/business-center/training/standard/${id}`);
      await loadDetail(id);
      activeStep.value = 1;
    }
    basicStepCompleted.value = true;
    ElMessage.success('基础信息已保存');
  } finally {
    saving.value = false;
  }
}

/** 删除临时规则后根据已保存快照恢复或锁定下一步。 */
function removeRule(index: number): void {
  rules.value.splice(index, 1);
  refreshRulesStepCompletion();
}

/** 覆盖保存规则并携带当前版本。 */
async function saveRules(): Promise<void> {
  if (!standardId.value) return;
  const invalidIndex = getInvalidRuleIndex();
  if (invalidIndex >= 0) {
    const duplicateType = getDuplicateRuleType();
    if (duplicateType === 'TOTAL_HOURS') {
      ElMessage.warning('总学时规则只能配置一条，请合并或删除重复规则后再保存。');
      return;
    }
    if (duplicateType === 'ONLINE_RATIO') {
      ElMessage.warning('线上学时占比规则只能配置一条，请合并或删除重复规则后再保存。');
      return;
    }
    ElMessage.warning(rules.value.length ? `请完整填写第 ${invalidIndex + 1} 条规则的要求、资源和说明` : '请至少添加一条规则。');
    return;
  }
  const onlineRatioRule = rules.value.find((rule) => rule.ruleType === 'ONLINE_RATIO');
  if (onlineRatioRule && onlineRatioMaxPercent.value !== undefined && Number(onlineRatioRule.requiredValue) > onlineRatioMaxPercent.value) {
    ElMessage.warning(`线上学时占比不得超过培训类型上限 ${onlineRatioMaxPercent.value}%，请调整后再保存。`);
    return;
  }
  saving.value = true;
  try {
    await replaceTrainingStandardRules(standardId.value, {
      version: form.version,
      rules: rules.value.map((rule) => ({
        ...rule,
        requiredValue: rule.ruleType === 'ONLINE_RATIO' ? toTrainingRatio(rule.requiredValue) : rule.requiredValue
      }))
    });
    await loadDetail();
    ElMessage.success('标准规则已保存');
  } catch (error) {
    if (error instanceof TrainingStandardApiError && error.code === 8016) {
      ElMessage.error('总学时和线上学时占比规则各只能配置一条，请合并重复规则后再保存。');
      return;
    }
    if (error instanceof TrainingStandardApiError && error.code === 8018) {
      ElMessage.error(`线上学时占比不得超过培训类型上限 ${onlineRatioMaxPercent.value ?? ''}%，请调整后再保存。`);
      return;
    }
    throw error;
  } finally {
    saving.value = false;
  }
}

/** 发布标准并使工作台进入只读状态。 */
async function publish(): Promise<void> {
  if (!standardId.value) return;
  await ElMessageBox.confirm('发布后标准及规则将只读，确认发布吗？', '发布确认', { type: 'warning' });
  await publishTrainingStandard(standardId.value, form.version);
  await loadDetail();
  ElMessage.success('培训标准已发布');
}

onMounted(async () => {
  if (standardId.value) await loadDetail();
  else await Promise.all([loadTrainingTypes(), loadResourceOptions()]);
});

watch(standardId, async (id, previousId) => {
  if (id && id !== previousId) await loadDetail(id);
});
</script>

<template>
  <section v-loading="loading" class="admin-detail-page">
    <header class="admin-detail-page__header">
      <div>
        <h1>
          {{ isCreate ? '新增培训标准' : form.standardName }}
          <ElTag :type="form.publishStatus === 'ARCHIVED' ? 'info' : readonly ? 'success' : 'warning'">
            {{ form.publishStatus === 'ARCHIVED' ? '已归档，只读' : readonly ? '已发布，只读' : '草稿' }}
          </ElTag>
        </h1>
        <p>按基础信息、规则编排、发布确认三个阶段顺序完成配置。</p>
      </div>
      <ElButton @click="router.push('/admin/business-center/training/standard')">返回标准列表</ElButton>
    </header>

    <nav class="stage-nav admin-detail-page__steps" aria-label="培训标准工作台步骤">
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
      <StandardBasicPanel
        v-show="activeStep === 0"
        :model="form"
        :rules="formRules"
        :is-create="isCreate"
        :readonly="readonly"
        :can-next="basicStepCompleted"
        :training-type-options="trainingTypeOptions"
        :can-save="!readonly && canOperate(isCreate ? 'training:standard:add' : 'training:standard:edit')"
        :saving="saving"
        @save="saveBasic"
        @dirty="basicStepCompleted = false"
        @next="goStep(1)"
      />

      <StandardRulesPanel
        v-show="activeStep === 1"
        :rules="rules"
        :readonly="readonly"
        :can-edit="Boolean(standardId) && !readonly && canOperate('training:standard:edit')"
        :saving="saving"
        :can-next="rulesStepCompleted"
        :baseline-text="trainingTypeBaseline"
        :summary-text="ruleComplianceSummary"
        :online-ratio-max-percent="onlineRatioMaxPercent"
        :rule-type-options="ruleTypeOptions"
        :course-options="courseOptions"
        :exam-options="examOptions"
        @add="addRule"
        @remove="removeRule"
        @rule-type-change="handleRuleTypeChange"
        @dirty="refreshRulesStepCompletion"
        @save="saveRules"
        @next="goStep(2)"
      />
      <StandardPublishPanel
        v-if="standardId"
        v-show="activeStep === 2"
        :readonly="readonly"
        :can-publish="canOperate('training:standard:publish')"
        :standard="form"
        :training-type-name="trainingTypeOptions.find((item) => item.trainingTypeId === form.trainingTypeId)?.typeName || '-'"
        :rules="rules"
        :course-options="courseOptions"
        :exam-options="examOptions"
        @back="goStep(1)"
        @publish="publish"
      />
    </main>
  </section>
</template>

<style scoped lang="less">
.workspace-heading,
.card-header {
  display: flex;
  align-items: center;
  justify-content: space-between;

  h1 {
    margin: 8px 0;
    color: #172033;
    font-size: 26px;
  }

  p {
    margin: 0;
    color: #667085;
  }
}

.form-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 4px 20px;
}

.sticky-actions {
  display: flex;
  justify-content: flex-end;
  gap: 8px;
  margin-top: 16px;
}

.panel-actions {
  display: flex;
  justify-content: flex-end;
  gap: 10px;
  margin-top: 18px;
}
</style>
