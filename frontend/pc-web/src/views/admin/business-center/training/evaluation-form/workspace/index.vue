<script setup lang="ts">
import { computed, onMounted, reactive, ref, watch } from 'vue';
import { useRoute, useRouter } from 'vue-router';
import { ElMessage, ElMessageBox, type FormRules } from 'element-plus';
import {
  createEvaluationForm,
  getEvaluationFormDetail,
  getEvaluationFormVersions,
  publishEvaluationForm,
  updateEvaluationForm,
  type EvaluationCalculationType,
  type EvaluationFormType,
  type EvaluationFormVersionSummary,
  type EvaluationItemRequest,
  type EvaluationItemType,
  type EvaluationScoreRuleRequest,
  type EvaluationSourceType
} from '@/api/evaluation-form';
import { useUserNameResolver } from '@/composables/use-user-name';
import { useUserStore } from '@/stores';
import { hasPermission } from '@/utils/permission';
import EvaluationFormBasicPanel from '../components/EvaluationFormBasicPanel.vue';
import EvaluationFormItemsPanel from '../components/EvaluationFormItemsPanel.vue';
import EvaluationFormPublishPanel from '../components/EvaluationFormPublishPanel.vue';

const route = useRoute();
const router = useRouter();
const userStore = useUserStore();
const { loadUserNames, userLabel } = useUserNameResolver();
const evaluationFormId = ref(Number(route.params.evaluationFormId) || 0);
const EVALUATION_COMPOSITION_DRAFT_PREFIX = 'evaluation-form-composition-draft:';
const activeStep = ref(0);
/** 工作台步骤统一使用可点击导航，顺序与详情卡片保持一致。 */
const workspaceSteps = [
  { title: '基础信息', description: '明确用途与启用状态' },
  { title: '项目与规则', description: '编排项目与发布规则' },
  { title: '确认发布', description: '核对快照并生成版本' }
];
const loading = ref(false);
const saving = ref(false);
const publishing = ref(false);
const versions = ref<EvaluationFormVersionSummary[]>([]);

const basic = reactive({
  formCode: '',
  formName: '',
  formType: 'COMPOSITE' as EvaluationFormType,
  status: 1 as 0 | 1,
  description: '',
  version: 0
});
const items = ref<EvaluationItemRequest[]>([]);
const scoreRules = ref<EvaluationScoreRuleRequest[]>([]);
/** 点击下一步后保留当前错误，便于用户在同一位置修正配置。 */
const compositionValidationMessage = ref('');
/** 最近一次成功保存的基础信息快照，用于判断能否进入项目编排。 */
const savedBasicFingerprint = ref<string>();
/** 最近一次用户确认的项目与规则快照；该步骤暂无独立草稿保存接口。 */
const savedCompositionFingerprint = ref<string>();
const publishMeta = reactive({
  totalScore: 100,
  versionNote: ''
});
interface EvaluationCompositionDraft {
  items: EvaluationItemRequest[];
  scoreRules: EvaluationScoreRuleRequest[];
  confirmedFingerprint?: string;
}
const basicFormRules: FormRules = {
  formCode: [{ required: true, message: '请输入表单编码', trigger: 'blur' }],
  formName: [{ required: true, message: '请输入表单名称', trigger: 'blur' }],
  formType: [{ required: true, message: '请选择表单类型', trigger: 'change' }],
  description: [{ required: true, message: '请输入用途说明', trigger: 'blur' }]
};
const publishFormRules: FormRules = {
  totalScore: [{ required: true, message: '请输入版本总分', trigger: 'change' }]
};

const FORM_TYPE_OPTIONS: Array<{ label: string; value: EvaluationFormType; description: string }> = [
  { label: '综合评价', value: 'COMPOSITE', description: '汇总课程、考试、线下和奖惩来源成绩。' },
  { label: '专家检查', value: 'EXPERT_INSPECTION', description: '用于专家现场打分和检查记录。' }
];
const ITEM_TYPE_OPTIONS: Array<{ label: string; value: EvaluationItemType }> = [
  { label: '分数项', value: 'SCORE' },
  { label: '文本项', value: 'TEXT' },
  { label: '选择项', value: 'CHOICE' }
];
const CALCULATION_OPTIONS: Array<{ label: string; value: EvaluationCalculationType }> = [
  { label: '权重折算', value: 'WEIGHT' },
  { label: '加减分调整', value: 'ADJUSTMENT' }
];
const SOURCE_OPTIONS: Array<{ label: string; value: EvaluationSourceType }> = [
  { label: '课程成绩', value: 'COURSE' },
  { label: '考试成绩', value: 'EXAM' },
  { label: '线下成绩', value: 'OFFLINE' },
  { label: '奖惩记录', value: 'REWARD_PENALTY' },
  { label: '人工录入', value: 'MANUAL' }
];
type CompositeSourceType = 'COURSE' | 'EXAM' | 'OFFLINE';

/** 缩小综合评价允许来源的联合类型，避免将其他契约枚举写入固定来源配置。 */
function isCompositeSourceType(sourceType: EvaluationSourceType): sourceType is CompositeSourceType {
  return sourceType === 'COURSE' || sourceType === 'EXAM' || sourceType === 'OFFLINE';
}

const isCreated = computed(() => evaluationFormId.value > 0);
/** 专家检查与综合评价共用版本模型，但项目编排和执行语义必须分支。 */
const isExpertInspection = computed(() => basic.formType === 'EXPERT_INSPECTION');
const canSave = computed(() => hasPermission(userStore.userInfo, isCreated.value ? 'evaluation:form:edit' : 'evaluation:form:add'));
const canPublish = computed(() => hasPermission(userStore.userInfo, 'evaluation:form:publish'));
const currentFormType = computed(() => FORM_TYPE_OPTIONS.find((item) => item.value === basic.formType) ?? FORM_TYPE_OPTIONS[0]);
/** 综合评价只能选择培训计划可产生正式成绩的三类来源。 */
const availableCalculationOptions = computed(() => basic.formType === 'COMPOSITE' ? CALCULATION_OPTIONS.filter((item) => item.value === 'WEIGHT') : CALCULATION_OPTIONS);
const availableSourceOptions = computed(() => basic.formType === 'COMPOSITE' ? SOURCE_OPTIONS.filter((item) =>
  item.value === 'COURSE' || item.value === 'EXAM' || item.value === 'OFFLINE') : SOURCE_OPTIONS);
/** 仅比较可编辑字段，避免服务端补充字段影响步骤状态。 */
function basicFingerprint(): string {
  return JSON.stringify({
    formCode: basic.formCode,
    formName: basic.formName,
    formType: basic.formType,
    status: basic.status,
    description: basic.description
  });
}
/** 将当前项目与规则序列化为可恢复比较的前端确认快照。 */
function compositionFingerprint(): string {
  return JSON.stringify({ items: items.value, scoreRules: scoreRules.value });
}
const basicStepCompleted = computed(() => savedBasicFingerprint.value !== undefined && savedBasicFingerprint.value === basicFingerprint());
const compositionStepCompleted = computed(
  () => savedCompositionFingerprint.value !== undefined && savedCompositionFingerprint.value === compositionFingerprint()
);

/** 只有当前步骤仍与最近保存或确认的快照一致时，才能进入后续步骤。 */
function isStepDisabled(step: number): boolean {
  if (step === 1) return !basicStepCompleted.value;
  if (step === 2) return !basicStepCompleted.value || !compositionStepCompleted.value;
  return false;
}

/** 切换工作台步骤，并为未保存的新表单给出明确反馈。 */
function goStep(step: number): void {
  if (isStepDisabled(step)) {
    ElMessage.warning(step === 1 ? '请先保存基础信息。' : '请先确认当前项目与规则。');
    return;
  }
  activeStep.value = step;
}

function itemTypeText(type: EvaluationItemType): string {
  return ITEM_TYPE_OPTIONS.find((item) => item.value === type)?.label ?? type;
}

function calculationText(type: EvaluationCalculationType): string {
  return CALCULATION_OPTIONS.find((item) => item.value === type)?.label ?? type;
}

function sourceText(type: EvaluationSourceType): string {
  return SOURCE_OPTIONS.find((item) => item.value === type)?.label ?? type;
}

function compositionDraftKey(): string {
  return evaluationFormId.value ? `${EVALUATION_COMPOSITION_DRAFT_PREFIX}${evaluationFormId.value}` : '';
}

/** 后端当前没有项目与规则草稿保存接口，发布前用本地缓存保护刷新场景。 */
function persistCompositionDraft(): void {
  const key = compositionDraftKey();
  if (!key) return;
  globalThis.localStorage.setItem(
    key,
    JSON.stringify({
      items: items.value,
      scoreRules: scoreRules.value,
      confirmedFingerprint: savedCompositionFingerprint.value
    })
  );
}

/** 读取本地草稿时只接受数组结构，避免异常缓存污染工作台。 */
function restoreCompositionDraft(): void {
  const key = compositionDraftKey();
  if (!key) return;
  try {
    const draft = JSON.parse(globalThis.localStorage.getItem(key) || 'null') as EvaluationCompositionDraft | null;
    if (!draft || !Array.isArray(draft.items) || !Array.isArray(draft.scoreRules)) return;
    items.value = draft.items;
    scoreRules.value = draft.scoreRules;
    if (draft.confirmedFingerprint === compositionFingerprint()) {
      savedCompositionFingerprint.value = draft.confirmedFingerprint;
    }
  } catch {
    globalThis.localStorage.removeItem(key);
  }
}

function clearCompositionDraft(): void {
  const key = compositionDraftKey();
  if (key) globalThis.localStorage.removeItem(key);
}

/** 重新读取主体与版本历史，保持乐观锁版本最新。 */
async function loadWorkspace(): Promise<void> {
  if (!evaluationFormId.value) return;
  loading.value = true;
  try {
    const [form, history] = await Promise.all([getEvaluationFormDetail(evaluationFormId.value), getEvaluationFormVersions(evaluationFormId.value)]);
    Object.assign(basic, form);
    savedBasicFingerprint.value = basicFingerprint();
    versions.value = history;
    await loadUserNames(history.map((item) => item.publishedBy));
  } finally {
    loading.value = false;
  }
}

/** 保存主体；是否进入下一步由用户在当前步骤明确确认。 */
async function saveBasic(): Promise<void> {
  if (!canSave.value) return;
  saving.value = true;
  try {
    if (!evaluationFormId.value) {
      evaluationFormId.value = await createEvaluationForm({
        formCode: basic.formCode,
        formName: basic.formName,
        formType: basic.formType,
        status: basic.status,
        description: basic.description
      });
      await router.replace(`/admin/business-center/training/evaluation-form/workspace/${evaluationFormId.value}`);
    } else {
      await updateEvaluationForm(evaluationFormId.value, {
        version: basic.version,
        formName: basic.formName,
        formType: basic.formType,
        status: basic.status,
        description: basic.description
      });
    }
    await loadWorkspace();
    ElMessage.success('基础信息已保存');
  } finally {
    saving.value = false;
  }
}

function addItem(): void {
  items.value.push({
    itemCode: `ITEM_${items.value.length + 1}`,
    itemName: '',
    itemType: 'SCORE',
    maxScore: isExpertInspection.value ? null : 100,
    weight: null,
    sortOrder: items.value.length + 1,
    isRequired: 1
  });
}

function removeItem(index: number): void {
  const code = items.value[index]?.itemCode;
  items.value.splice(index, 1);
  scoreRules.value = scoreRules.value.filter((rule) => rule.evaluationItemCode !== code);
}

function addScoreRuleForItem(itemCode: string): void {
  if (!itemCode.trim()) {
    ElMessage.warning('请先填写项目编码，再设置评分规则');
    return;
  }
  const item = items.value.find((current) => current.itemCode === itemCode);
  scoreRules.value.push({
    evaluationItemCode: itemCode,
    calculationType: 'WEIGHT',
    sourceType: 'EXAM',
    scoreChange: null,
    weight: item?.itemType === 'SCORE' ? item.weight : null,
    sortOrder: scoreRules.value.length + 1,
    ruleDescription: ''
  });
}

/** 综合评价从固定来源一次性生成匹配的项目和规则，消除双表重复配置。 */
function addCompositeSource(sourceType: EvaluationSourceType): void {
  if (scoreRules.value.some((rule) => rule.sourceType === sourceType)) return;
  const sourceMeta: Record<CompositeSourceType, { code: string; name: string; description: string }> = {
    COURSE: { code: 'COURSE_SCORE', name: '课程类别成绩', description: '课程成绩按权重折算计入综合成绩' },
    EXAM: { code: 'EXAM_SCORE', name: '考试类别成绩', description: '考试成绩按权重折算计入综合成绩' },
    OFFLINE: { code: 'OFFLINE_SCORE', name: '线下类别成绩', description: '线下成绩按权重折算计入综合成绩' }
  };
  if (!isCompositeSourceType(sourceType)) return;
  const meta = sourceMeta[sourceType];
  items.value.push({
    itemCode: meta.code,
    itemName: meta.name,
    itemType: 'SCORE',
    maxScore: 100,
    weight: 1,
    sortOrder: items.value.length + 1,
    isRequired: 1
  });
  scoreRules.value.push({
    evaluationItemCode: meta.code,
    calculationType: 'WEIGHT',
    sourceType,
    scoreChange: null,
    weight: 1,
    sortOrder: scoreRules.value.length + 1,
    ruleDescription: meta.description
  });
}

/** 移除来源时同步删除其自动生成的项目，确保不会留下孤立项目。 */
function removeCompositeSource(sourceType: EvaluationSourceType): void {
  const rule = scoreRules.value.find((current) => current.sourceType === sourceType);
  if (!rule) return;
  scoreRules.value = scoreRules.value.filter((current) => current !== rule);
  items.value = items.value.filter((item) => item.itemCode !== rule.evaluationItemCode);
}

function validateComposition(): boolean {
  compositionValidationMessage.value = '';
  if (!items.value.length) {
    compositionValidationMessage.value = basic.formType === 'COMPOSITE' ? '请选择至少一项成绩来源。' : '至少配置一个评价项目。';
    ElMessage.warning(compositionValidationMessage.value);
    return false;
  }
  const codes = items.value.map((item) => item.itemCode.trim());
  if (codes.some((code) => !code) || new Set(codes).size !== codes.length) {
    compositionValidationMessage.value = '项目编码不能为空且不可重复。';
    ElMessage.warning(compositionValidationMessage.value);
    return false;
  }
  if (items.value.some((item) => !item.itemName.trim())) {
    compositionValidationMessage.value = '请填写所有项目名称。';
    ElMessage.warning(compositionValidationMessage.value);
    return false;
  }
  if (items.value.some((item) => item.itemType !== 'TEXT' && item.maxScore == null)) {
    compositionValidationMessage.value = '分数项必须填写满分。';
    ElMessage.warning(compositionValidationMessage.value);
    return false;
  }
  if (isExpertInspection.value) {
    const scoreItems = items.value.filter((item) => item.itemType === 'SCORE');
    const totalScore = scoreItems.reduce((total, item) => total + Number(item.maxScore ?? 0), 0);
    if (items.value.some((item) => !['SCORE', 'TEXT'].includes(item.itemType))) {
      compositionValidationMessage.value = '专家检查仅支持分数项和文本意见项。';
    } else if (items.value.some((item) => item.itemType === 'TEXT' && (item.maxScore != null || item.weight != null))) {
      compositionValidationMessage.value = '文本意见项不应填写满分或权重。';
    } else if (scoreItems.some((item) => Number(item.maxScore) <= 0)) {
      compositionValidationMessage.value = '专家检查分数项的满分必须大于 0。';
    } else if (Math.abs(totalScore - 100) > 0.000001) {
      compositionValidationMessage.value = `专家检查分数项满分合计必须为 100，当前为 ${totalScore}。`;
    } else if (scoreRules.value.length) {
      compositionValidationMessage.value = '专家检查不允许配置自动评分规则。';
    }
    if (compositionValidationMessage.value) {
      ElMessage.warning(compositionValidationMessage.value);
      return false;
    }
    publishMeta.totalScore = 100;
    return true;
  }
  if (scoreRules.value.some((rule) => !codes.includes(rule.evaluationItemCode))) {
    compositionValidationMessage.value = '每条评分规则都必须关联当前评价项目。';
    ElMessage.warning(compositionValidationMessage.value);
    return false;
  }
  if (scoreRules.value.some((rule) => !rule.ruleDescription.trim())) {
    compositionValidationMessage.value = '请填写每条评分规则的规则说明。';
    ElMessage.warning(compositionValidationMessage.value);
    return false;
  }
  if (scoreRules.value.some((rule) => rule.calculationType === 'WEIGHT' && (rule.weight == null || rule.weight <= 0))) {
    compositionValidationMessage.value = '权重折算规则必须填写大于 0 的权重。';
    ElMessage.warning(compositionValidationMessage.value);
    return false;
  }
  if (scoreRules.value.some((rule) => rule.calculationType === 'ADJUSTMENT' && rule.scoreChange == null)) {
    compositionValidationMessage.value = '加减分调整规则必须填写调整分值。';
    ElMessage.warning(compositionValidationMessage.value);
    return false;
  }
  if (basic.formType === 'COMPOSITE') {
    const totalWeight = scoreRules.value.reduce((total, rule) => total + (rule.weight ?? 0), 0);
    const sources = scoreRules.value.map((rule) => rule.sourceType);
    const itemByCode = new Map(items.value.map((item) => [item.itemCode, item]));
    if (new Set(sources).size !== sources.length) {
      compositionValidationMessage.value = '同一成绩来源只能纳入一次，请移除重复来源。';
    } else if (scoreRules.value.some((rule) => rule.calculationType !== 'WEIGHT' || !['COURSE', 'EXAM', 'OFFLINE'].includes(rule.sourceType))) {
      compositionValidationMessage.value = '综合评价仅支持课程、考试、线下三类来源，并且只能使用权重折算。';
    } else if (Math.abs(totalWeight - 1) > 0.000001) {
      compositionValidationMessage.value = `综合成绩权重合计必须为 1，当前为 ${Number(totalWeight.toFixed(2))}。`;
    } else if (scoreRules.value.some((rule) => {
      const item = itemByCode.get(rule.evaluationItemCode);
      return !item || item.itemType !== 'SCORE' || item.maxScore !== 100 || item.isRequired !== 1 || item.weight !== rule.weight;
    })) {
      compositionValidationMessage.value = '综合评价项目必须为满分 100 的必填分数项，并与来源权重保持一致。';
    }
    if (compositionValidationMessage.value) {
      ElMessage.warning(compositionValidationMessage.value);
      return false;
    }
  }
  return true;
}

function reviewBeforePublish(): void {
  if (!isCreated.value) {
    ElMessage.warning('请先保存基础信息');
    activeStep.value = 0;
    return;
  }
  if (validateComposition()) {
    savedCompositionFingerprint.value = compositionFingerprint();
    activeStep.value = 2;
  }
}

/** 发布时提交完整快照，评分规则只通过 evaluationItemCode 关联同批次项目。 */
async function publishVersion(): Promise<void> {
  if (!evaluationFormId.value || !canPublish.value || !validateComposition()) return;
  try {
    await ElMessageBox.confirm('发布后版本内容不可修改，后续调整将产生新版本。确认发布当前配置吗？', '发布评价表单', { type: 'warning' });
    publishing.value = true;
    const version = await publishEvaluationForm(evaluationFormId.value, {
      version: basic.version,
      totalScore: publishMeta.totalScore,
      versionNote: publishMeta.versionNote || null,
      items: items.value.map((item) => ({ ...item, itemCode: item.itemCode.trim() })),
    scoreRules: isExpertInspection.value ? [] : scoreRules.value.map((rule) => ({ ...rule }))
    });
    clearCompositionDraft();
    ElMessage.success(`评价表单 V${version.versionNo} 已发布`);
    await router.replace(`/admin/business-center/training/evaluation-form/version/${version.formVersionId}`);
  } finally {
    publishing.value = false;
  }
}

function openVersion(formVersionId: number): void {
  void router.push(`/admin/business-center/training/evaluation-form/version/${formVersionId}`);
}

/** 切换到专家检查时仅执行一次归一化，不能在项目深度监听中反复重置规则数组。 */
watch(() => basic.formType, (formType) => {
  if (formType !== 'EXPERT_INSPECTION') return;
  if (scoreRules.value.length) scoreRules.value = [];
  items.value.forEach((item) => {
    item.weight = null;
    if (item.itemType === 'TEXT') item.maxScore = null;
  });
  publishMeta.totalScore = 100;
});
/** 综合评价才需要把来源规则权重同步到自动生成的项目。 */
watch([items, scoreRules], () => {
  compositionValidationMessage.value = '';
  if (basic.formType !== 'COMPOSITE') return;
  scoreRules.value.forEach((rule) => {
    const item = items.value.find((current) => current.itemCode === rule.evaluationItemCode);
    if (item) item.weight = rule.weight;
  });
}, { deep: true });
watch([items, scoreRules, savedCompositionFingerprint], persistCompositionDraft, { deep: true });

onMounted(async () => {
  await loadWorkspace();
  restoreCompositionDraft();
});
</script>

<template>
  <section v-loading="loading" class="admin-detail-page">
    <header class="admin-detail-page__header">
      <div>
        <h1>{{ isCreated ? basic.formName || '编辑评价表单' : '新建评价表单' }}</h1>
        <p>
          按顺序完成主体、项目规则和发布确认；发布后版本内容不可修改。
          <ElTag v-if="isCreated" :type="basic.status ? 'success' : 'info'" size="small" effect="light">{{ basic.status ? '启用' : '停用' }}</ElTag>
        </p>
      </div>
      <ElButton @click="router.push('/admin/business-center/training/evaluation-form')">返回列表</ElButton>
    </header>

    <nav class="stage-nav admin-detail-page__steps" aria-label="评价表单工作台步骤">
      <button
        v-for="(item, index) in workspaceSteps"
        :key="item.title"
        type="button"
        class="stage-item"
        :class="{ 'is-active': activeStep === index }"
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
      <EvaluationFormBasicPanel
        v-show="activeStep === 0"
        :model="basic"
        :rules="basicFormRules"
        :is-created="isCreated"
        :can-save="canSave"
        :can-next="basicStepCompleted"
        :saving="saving"
        :form-types="FORM_TYPE_OPTIONS"
        :form-type-description="currentFormType.description"
        @save="saveBasic"
        @next="activeStep = 1"
      />

      <EvaluationFormItemsPanel
        v-show="activeStep === 1"
        :items="items"
        :score-rules="scoreRules"
        :item-type-options="ITEM_TYPE_OPTIONS"
        :calculation-options="availableCalculationOptions"
        :source-options="availableSourceOptions"
        :is-composite="basic.formType === 'COMPOSITE'"
        :is-expert-inspection="isExpertInspection"
        :validation-message="compositionValidationMessage"
        @add-item="addItem"
        @remove-item="removeItem"
        @add-rule-for-item="addScoreRuleForItem"
        @remove-rule="scoreRules.splice($event, 1)"
        @add-composite-source="addCompositeSource"
        @remove-composite-source="removeCompositeSource"
        @back="activeStep = 0"
        @next="reviewBeforePublish"
      />

      <EvaluationFormPublishPanel
        v-show="activeStep === 2"
        :form-name="basic.formName"
        :items="items"
        :score-rules="scoreRules"
        :meta="publishMeta"
        :rules="publishFormRules"
        :can-publish="canPublish"
        :publishing="publishing"
        :item-type-text="itemTypeText"
        :calculation-text="calculationText"
        :source-text="sourceText"
        @back="activeStep = 1"
        @publish="publishVersion"
      />

      <ElCard v-if="versions.length" shadow="never" class="history-version-card">
        <template #header>
          <div class="card-heading">
            <div>
              <h2>历史版本</h2>
              <p>已发布版本始终只读</p>
            </div>
            <span class="history-version-count">共 {{ versions.length }} 个版本</span>
          </div>
        </template>
        <div class="history-version-list">
          <article v-for="version in versions" :key="version.formVersionId" class="history-version-item">
            <div class="history-version-item__main">
              <span class="history-version-item__number">V{{ version.versionNo }}</span>
              <span class="history-version-item__time">{{ version.publishedTime || '发布时间未知' }}</span>
            </div>
            <p class="history-version-item__note">{{ version.versionNote || '未填写版本说明' }}</p>
            <div class="history-version-item__footer">
              <span>发布人：{{ userLabel(version.publishedBy) }}</span>
              <ElButton
                v-if="hasPermission(userStore.userInfo, 'evaluation:form:list')"
                link
                type="primary"
                @click="openVersion(version.formVersionId)"
              >
                查看快照
              </ElButton>
            </div>
          </article>
        </div>
      </ElCard>
    </main>
  </section>
</template>

<style scoped lang="less">
.section-heading,
.action-bar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 20px;
}

.field-hint {
  margin: 6px 0 0;
  color: var(--color-text-secondary);
}
.card-heading {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 24px;
  min-height: 48px;

  h2 {
    margin: 0 0 6px;
    color: var(--color-text-primary);
    font-size: 16px;
    font-weight: 600;
    line-height: 22px;
  }

  p {
    margin: 0;
    color: var(--color-text-secondary);
    font-size: 13px;
    line-height: 20px;
  }
}

.eyebrow {
  color: var(--color-primary) !important;
  font-size: 13px;
  font-weight: 600;
}

.step-card {
  padding: 4px 0;
}

.basic-form {
  max-width: 984px;
}

.field-hint {
  width: 100%;
  font-size: 13px;
}

.section-heading h2 {
  margin: 0;
  font-size: 18px;
}

.action-bar {
  justify-content: flex-end;
  margin-top: 24px;
}

.action-bar--split {
  justify-content: space-between;
}

.summary,
.publish-form,
.summary-table {
  margin-top: 18px;
}

.history-version-card {
  min-width: 0;

  :deep(.el-card__header) {
    padding: 14px 16px;
    background: var(--color-bg-subtle);
    border-bottom-color: var(--color-border);
  }

  :deep(.el-card__body) {
    padding: 12px;
  }

  .card-heading {
    align-items: flex-start;
  }
}

.history-version-count {
  flex: 0 0 auto;
  margin: 0 !important;
  padding-top: 3px;
  font-size: 12px;
}

.history-version-list {
  display: grid;
  gap: 10px;
}

.history-version-item {
  padding: 12px;
  background: var(--color-bg-surface);
  border: 1px solid var(--color-border);
  border-radius: 8px;
}

.history-version-item__main,
.history-version-item__footer {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 8px;
}

.history-version-item__number {
  color: var(--color-primary);
  font-size: 15px;
  font-weight: 600;
}

.history-version-item__time,
.history-version-item__footer {
  color: var(--color-text-secondary);
  font-size: 12px;
}

.history-version-item__note {
  display: -webkit-box;
  margin: 8px 0;
  overflow: hidden;
  color: var(--color-text-regular);
  font-size: 13px;
  line-height: 20px;
  -webkit-box-orient: vertical;
  -webkit-line-clamp: 2;
}
</style>
