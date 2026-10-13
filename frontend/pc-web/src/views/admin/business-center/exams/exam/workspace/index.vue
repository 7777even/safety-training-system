<script setup lang="ts">
import { computed, reactive, ref, watch } from 'vue';
import { ElMessage, ElMessageBox } from 'element-plus';
import { useRoute, useRouter } from 'vue-router';
import {
  createExam,
  EXAM_REFERENCE_PAGE_SIZE,
  getExam,
  publishExam,
  updateExam,
  type ExamCreatePayload,
  type ExamItem,
  type ExamPolicyRequest,
  type ExamUpdatePayload
} from '@/api/exam';
import { getPaperPage, getPaperVersion, getPaperVersions, type PaperVersionOption } from '@/api/paper';
import { getQuestionBankCategories, getQuestionBankPage, type QuestionBank, type QuestionBankCategory } from '@/api/question';
import ExamAssignmentPanel from '../components/ExamAssignmentPanel.vue';
import ExamAttemptManagementPanel from '../components/ExamAttemptManagementPanel.vue';
import ExamInformationPanel from '../components/ExamInformationPanel.vue';
import ExamPublishCheck from '../components/ExamPublishCheck.vue';

type ExamWorkspaceStage = 'information' | 'assignment' | 'publish' | 'attempts';
type ExamRuleOverrideField = 'durationSeconds' | 'passRatio' | 'passScore' | 'questionOrderMode' | 'multiSelectScoring';
const RULE_OVERRIDE_FIELDS: ExamRuleOverrideField[] = ['durationSeconds', 'passRatio', 'passScore', 'questionOrderMode', 'multiSelectScoring'];
const route = useRoute();
const router = useRouter();
const exam = ref<ExamItem>();
const loading = ref(false);
const saving = ref(false);
const publishing = ref(false);
const paperOptions = ref<PaperVersionOption[]>([]);
const categoryOptions = ref<QuestionBankCategory[]>([]);
const questionBankOptions = ref<QuestionBank[]>([]);
const candidateCount = ref(0);
const reviewerCount = ref(0);

/** 生成新增考试的默认表单，路由从详情切到新增时必须得到全新对象。 */
function createDefaultExamForm(): ExamCreatePayload {
  return {
    examCode: '',
    examName: '',
    examType: 'ONLINE',
    paperVersionId: undefined,
    durationSeconds: undefined,
    passRatio: undefined,
    passScore: undefined,
    credit: 1,
    questionOrderMode: undefined,
    multiSelectScoring: undefined,
    isNoticeEnabled: 0,
    status: 1,
    policy: { retakeScope: 'FAILED' } as ExamPolicyRequest
  };
}

const form = reactive<ExamCreatePayload>(createDefaultExamForm());
/** 仅记录管理员实际覆盖的试卷规则，避免预览值被误作为草稿覆盖提交。 */
const ruleOverrides = reactive<Record<ExamRuleOverrideField, boolean>>({
  durationSeconds: false,
  passRatio: false,
  passScore: false,
  questionOrderMode: false,
  multiSelectScoring: false
});
/** 草稿是否显式换卷或解绑；用于重置试卷规则覆盖标记。 */
const paperVersionChanged = ref(false);

/** 工作台仅在已创建考试后开放人员配置和发布，避免用临时 ID 发送请求。 */
const examId = computed(() => Number(route.params.examId) || 0);
const isCreateMode = computed(() => !examId.value);
const editable = computed(() => isCreateMode.value || exam.value?.publishStatus === 'DRAFT');
/** 考试情况仅面向已发布考试开放，草稿考试不展示作答、批阅和成绩入口。 */
const isPublished = computed(() => exam.value?.publishStatus === 'PUBLISHED');
const stage = computed<ExamWorkspaceStage>(() => {
  const value = route.query.stage;
  if (isCreateMode.value && value !== undefined) return 'information';
  if (value === 'attempts' && !isPublished.value) return 'publish';
  return value === 'assignment' || value === 'publish' || value === 'attempts' ? value : 'information';
});
const stages: Array<{ name: ExamWorkspaceStage; title: string; description: string }> = [
  { name: 'information', title: '设置考试信息', description: '考试规则与开放时间' },
  { name: 'assignment', title: '人员配置', description: '考生范围与批阅人' },
  { name: 'publish', title: '发布考试', description: '发布前核查' },
  { name: 'attempts', title: '考试情况', description: '作答、批阅与成绩' }
];
/** 草稿阶段隐藏考试情况步骤，避免暴露尚未生效的考试数据入口。 */
const visibleStages = computed(() => stages.filter((item) => item.name !== 'attempts' || isPublished.value));
/** 只有会产生人工批阅任务的考试才把批阅人作为发布门禁。 */
function requiresReviewerForExam(currentExam: ExamItem): boolean {
  return currentExam.examType === 'ONLINE' || currentExam.examType === 'SPECIAL_PRACTICE';
}

/** 新建考试尚未取得考试 ID 时，后续步骤不能提交或读取关联数据。 */
function isStageDisabled(target: ExamWorkspaceStage): boolean {
  return isCreateMode.value && target !== 'information';
}

/** 加载每份试卷最新的已发布版本，供新建和草稿换卷使用。 */
async function loadPaperVersions(): Promise<void> {
  const page = await getPaperPage({ pageNum: 1, pageSize: EXAM_REFERENCE_PAGE_SIZE, 'query.hasPublishedVersion': true });
  const groups = await Promise.all(
    page.list.map(async (paper) =>
      (await getPaperVersions(paper.paperId))
        .filter((version) => version.publishedStatus === 'PUBLISHED')
        .sort((left, right) => right.versionNo - left.versionNo)
        .slice(0, 1)
        .map((version) => ({ ...version, paperName: paper.paperName, paperCategoryId: paper.paperCategoryId, coverFileId: paper.coverFileId }))
    )
  );
  paperOptions.value = groups.flat();
}

/** 考试分类名称在草稿之外（已发布、进行中、已结束）的只读态也要回显，因此分类选项独立加载。 */
async function loadCategoryOptions(): Promise<void> {
  categoryOptions.value = await getQuestionBankCategories();
}

/** 工作台统一加载考试信息页与发布核查共用的引用数据；分类选项单独加载，只读态也要复用。 */
async function loadReferenceOptions(): Promise<void> {
  const [banks] = await Promise.all([
    getQuestionBankPage({ pageNum: 1, pageSize: EXAM_REFERENCE_PAGE_SIZE, 'query.status': 1 }),
    loadPaperVersions()
  ]);
  questionBankOptions.value = banks.list;
}

/** 编辑旧草稿时只补查其已绑定的非最新版本，避免为标签回显重复遍历全部版本。 */
async function ensureSelectedPaperVersion(): Promise<void> {
  const paperVersionId = exam.value?.paperVersionId;
  if (!paperVersionId || paperOptions.value.some((item) => item.paperVersionId === paperVersionId)) return;
  const version = await getPaperVersion(paperVersionId);
  const paper = paperOptions.value.find((item) => item.paperId === version.paperId);
  if (!paper || version.publishedStatus !== 'PUBLISHED') return;
  paperOptions.value.push({
    paperVersionId: version.paperVersionId,
    paperId: version.paperId,
    versionNo: version.versionNo,
    publishedStatus: version.publishedStatus,
    publishedBy: version.publishedBy,
    publishedTime: version.publishedTime,
    paperName: paper.paperName,
    paperCategoryId: paper.paperCategoryId,
    coverFileId: paper.coverFileId
  });
}

/** 清除上一次试卷预览产生的覆盖标记，新的未编辑值由后端按契约处理。 */
function resetRuleOverrides(): void {
  RULE_OVERRIDE_FIELDS.forEach((field) => {
    ruleOverrides[field] = false;
  });
}

/** 恢复新增考试工作台状态，防止复用详情组件实例时残留上一场考试数据。 */
function resetCreateWorkspace(): void {
  exam.value = undefined;
  candidateCount.value = 0;
  reviewerCount.value = 0;
  paperVersionChanged.value = false;
  resetRuleOverrides();
  Object.assign(form, createDefaultExamForm());
}

/** 管理员修改某项规则时，保存请求才包含该项本场覆盖。 */
function markRuleOverride(field: ExamRuleOverrideField): void {
  ruleOverrides[field] = true;
}

/** 选择、换绑或清空试卷时重置规则覆盖状态，使省略语义以当前试卷为准。 */
function handlePaperVersionSelected(): void {
  paperVersionChanged.value = true;
  resetRuleOverrides();
}

/** 接收人员配置或发布核查返回的真实人数，避免工作台重复读取相同接口。 */
function updateCheckSummary(nextCandidateCount: number, nextReviewerCount: number): void {
  candidateCount.value = nextCandidateCount;
  reviewerCount.value = nextReviewerCount;
}

async function load(): Promise<void> {
  if (isCreateMode.value) {
    resetCreateWorkspace();
    await Promise.all([loadCategoryOptions(), loadReferenceOptions()]);
    return;
  }
  loading.value = true;
  try {
    // 分类名称在任意发布状态下都要回显，与详情并行读取，避免只读态先渲染出内部 ID。
    // 分类读取失败不阻断详情展示，标签退回内部 ID 仍可读。
    const [detail] = await Promise.all([getExam(examId.value), loadCategoryOptions().catch(() => undefined)]);
    exam.value = detail;
    Object.assign(form, detail);
    paperVersionChanged.value = false;
    resetRuleOverrides();
    if (detail.publishStatus === 'DRAFT') {
      await loadReferenceOptions();
      await ensureSelectedPaperVersion();
    }
  } finally {
    loading.value = false;
  }
}

function goStage(next: ExamWorkspaceStage): void {
  if (isStageDisabled(next)) {
    ElMessage.warning('请先保存考试信息。');
    return;
  }
  if (next === 'attempts' && !isPublished.value) {
    ElMessage.warning('考试发布后可查看考试情况。');
    return;
  }
  void router.replace({ query: next === 'information' ? {} : { stage: next } });
}

/** 判断无试卷场景的规则是否已完整填写，0 与空字符串的业务语义需分别处理。 */
function hasManualRules(): boolean {
  if (form.examType === 'BANK_PRACTICE') {
    return form.durationSeconds !== undefined
      && Boolean(form.questionOrderMode)
      && Boolean(form.multiSelectScoring);
  }
  return (
    form.durationSeconds !== undefined &&
    form.passRatio !== undefined &&
    form.passScore !== undefined &&
    Boolean(form.questionOrderMode) &&
    Boolean(form.multiSelectScoring)
  );
}

/** 在线考试与专项练习的执行对象必须以已发布试卷版本作为资源快照。 */
function requiresPaperVersion(examType: string): boolean {
  return examType === 'ONLINE' || examType === 'SPECIAL_PRACTICE';
}

/** 将规则字段按是否绑定试卷写入请求：绑定时仅提交管理员覆盖，无卷时全部必填。 */
function appendRulePayload(payload: ExamCreatePayload | ExamUpdatePayload, hasBoundPaper: boolean): boolean {
  if (hasBoundPaper) {
    RULE_OVERRIDE_FIELDS.forEach((field) => {
      if (!ruleOverrides[field]) return;
      if (field === 'durationSeconds') payload.durationSeconds = form.durationSeconds;
      if (field === 'passRatio') payload.passRatio = form.passRatio;
      if (field === 'passScore') payload.passScore = form.passScore;
      if (field === 'questionOrderMode') payload.questionOrderMode = form.questionOrderMode;
      if (field === 'multiSelectScoring') payload.multiSelectScoring = form.multiSelectScoring;
    });
    return true;
  }
  if (!hasManualRules()) {
    ElMessage.warning(
      form.examType === 'BANK_PRACTICE'
        ? '题库练习请填写考试时长、题目顺序和多选题计分。'
        : '未绑定试卷时，请填写考试时长、及格比例、及格分数、题目顺序和多选题计分。'
    );
    return false;
  }
  payload.durationSeconds = form.durationSeconds;
  if (form.examType !== 'BANK_PRACTICE') {
    payload.passRatio = form.passRatio;
    payload.passScore = form.passScore;
  }
  payload.questionOrderMode = form.questionOrderMode;
  payload.multiSelectScoring = form.multiSelectScoring;
  return true;
}

/** 创建与更新共享的可编辑考试字段，不混入详情专用字段。 */
function createBasePayload(): Omit<ExamCreatePayload, 'examCode' | 'examType' | 'paperVersionId' | ExamRuleOverrideField> {
  return {
    examName: form.examName,
    examCategoryId: form.examCategoryId,
    coverFileId: form.coverFileId,
    questionBankId: form.questionBankId,
    // 非题库练习统一提交空数组，保证草稿不会残留失效的抽题规则。
    drawRules: form.drawRules ?? [],
    responsibleUserId: form.responsibleUserId,
    openStartTime: form.openStartTime,
    openEndTime: form.openEndTime,
    credit: form.credit,
    isNoticeEnabled: form.isNoticeEnabled,
    noticeContent: form.noticeContent,
    status: form.status,
    policy: form.policy
  };
}

/** 题库练习必须配置抽题规则；其结果只提供自动反馈，不计算及格分。 */
function validateDrawRules(): boolean {
  if (form.examType !== 'BANK_PRACTICE') return true;
  const rules = form.drawRules ?? [];
  if (!rules.length || rules.some((rule) => !rule.questionType || !rule.drawCount)) {
    ElMessage.warning('题库练习请至少配置一条完整的抽题规则（含题型与抽题数量）。');
    return false;
  }
  return true;
}

/** 创建与更新共享一份表单；草稿更新始终提交当前绑卷状态。 */
async function saveInformation(nextStage?: ExamWorkspaceStage): Promise<void> {
  if (!form.examCode.trim() || !form.examName.trim()) return void ElMessage.warning('请填写考试编码和考试名称。');
  if (requiresPaperVersion(form.examType) && !form.paperVersionId) {
    return void ElMessage.warning('请先选择已发布试卷版本，再配置考生。');
  }
  if (!validateDrawRules()) return;
  saving.value = true;
  try {
    if (isCreateMode.value) {
      const payload: ExamCreatePayload = {
        ...createBasePayload(),
        examCode: form.examCode,
        examType: form.examType
      };
      if (form.paperVersionId !== undefined && form.paperVersionId !== null) payload.paperVersionId = form.paperVersionId;
      if (!appendRulePayload(payload, payload.paperVersionId !== undefined)) return;
      const createdId = await createExam(payload);
      ElMessage.success('考试草稿已创建。');
      await router.replace({ path: `/admin/business-center/exams/exam/${createdId}`, query: nextStage ? { stage: nextStage } : {} });
      return;
    }
    const payload: ExamUpdatePayload = { ...createBasePayload(), version: exam.value!.version };
    payload.paperVersionId = form.paperVersionId ?? null;
    if (
      !appendRulePayload(
        payload,
        form.paperVersionId !== undefined && form.paperVersionId !== null
      )
    )
      return;
    await updateExam(examId.value, payload);
    ElMessage.success('考试草稿已保存。');
    await load();
    if (nextStage) goStage(nextStage);
  } finally {
    saving.value = false;
  }
}

/** 发布前保留确认，并始终由服务端完成最终业务校验与状态锁定。 */
async function publish(): Promise<void> {
  if (!exam.value || !editable.value || !candidateCount.value || (requiresReviewerForExam(exam.value) && !reviewerCount.value)) return;
  try {
    await ElMessageBox.confirm('发布后该考试将锁定，确定继续吗？', '发布考试', { type: 'warning' });
    publishing.value = true;
    await publishExam(examId.value);
    ElMessage.success('考试已发布。');
    await load();
    await router.replace({ query: { stage: 'publish' } });
  } finally {
    publishing.value = false;
  }
}

watch(examId, () => void load(), { immediate: true });
</script>

<template>
  <section v-loading="loading" class="admin-detail-page">
    <header class="admin-detail-page__header">
      <div>
        <h1>考试工作台</h1>
        <p>
          依次设置考试信息、人员配置并核查发布。
          <ElTag v-if="exam" :type="exam.publishStatus === 'PUBLISHED' ? 'success' : 'warning'" size="small" effect="light">
            {{ exam.publishStatus === 'PUBLISHED' ? '已发布' : '草稿' }}
          </ElTag>
        </p>
      </div>
      <ElButton @click="router.push('/admin/business-center/exams/exam')">返回列表</ElButton>
    </header>
    <nav class="stage-nav admin-detail-page__steps" aria-label="考试工作台步骤">
      <button
        v-for="(item, index) in visibleStages"
        :key="item.name"
        type="button"
        class="stage-item"
        :class="{ 'is-active': stage === item.name, 'is-completed': !editable }"
        :disabled="isStageDisabled(item.name)"
        @click="goStage(item.name)"
      >
        <span class="stage-index">{{ index + 1 }}</span>
        <span>
          <b>{{ item.title }}</b>
          <small>{{ item.description }}</small>
        </span>
      </button>
    </nav>
    <main class="admin-detail-page__surface">
      <ExamInformationPanel
        v-if="stage === 'information'"
        v-model="form"
        :exam="exam"
        :editing="editable"
        :create-mode="isCreateMode"
        :saving="saving"
        :paper-options="paperOptions"
        :category-options="categoryOptions"
        :question-bank-options="questionBankOptions"
        @cancel="router.push('/admin/business-center/exams/exam')"
        @save="saveInformation()"
        @next="saveInformation('assignment')"
        @paper-version-selected="handlePaperVersionSelected"
        @rule-override-changed="markRuleOverride"
      />
      <ExamAssignmentPanel
        v-else-if="stage === 'assignment' && exam"
        :exam-id="exam.examId"
        :organization-id="exam.organizationId"
        :exam-type="exam.examType"
        :read-only="!editable"
        @saved="updateCheckSummary"
        @next="goStage('publish')"
      />
      <ExamPublishCheck
        v-else-if="stage === 'publish' && exam"
        :exam="exam"
        :candidate-count="candidateCount"
        :reviewer-count="reviewerCount"
        :publishing="publishing"
        :published="!editable"
        :paper-options="paperOptions"
        :category-options="categoryOptions"
        :question-bank-options="questionBankOptions"
        @publish="publish"
        @summary-loaded="updateCheckSummary"
      />
      <ExamAttemptManagementPanel v-else-if="stage === 'attempts' && examId" :exam-id="examId" :exam-type="exam?.examType" :pass-score="exam?.passScore" :open-start-time="exam?.openStartTime" />
    </main>
  </section>
</template>

<style scoped lang="less">
@media (max-width: 900px) {
  .stage-item small {
    display: none;
  }
}
</style>
