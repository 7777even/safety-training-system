<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue';
import { ElMessage, ElMessageBox } from 'element-plus';
import { useRoute, useRouter } from 'vue-router';
import { uploadAssetFile } from '@/api/asset';
import {
  getKnowledgePointTree,
  getQuestionBankCategories,
  getQuestionBankPage,
  getQuestionDetail,
  type KnowledgePoint,
  type Question,
  type QuestionBank,
  type QuestionBankCategory
} from '@/api/question';
import {
  composePaper,
  createPaper,
  getPaper,
  getPaperVersion,
  getPaperVersions,
  previewPaper,
  publishPaper,
  updatePaper,
  type PaperComposePayload,
  type PaperDrawRulePayload,
  type PaperPreview,
  type PaperQuestionPayload,
  type PaperType
} from '@/api/paper';
import UnifiedFileUpload, { type UnifiedUploadedFile } from '@/components/file-upload/UnifiedFileUpload.vue';
import DurationInput from '@/components/duration/DurationInput.vue';
import {
  DRAW_DIFFICULTY_OPTIONS,
  MULTI_SELECT_SCORING_OPTIONS,
  PAPER_TYPE_OPTIONS,
  QUESTION_ORDER_MODE_OPTIONS,
  QUESTION_TYPE_OPTIONS
} from '@/constants/exam-options';
import QuestionBankPickerDialog from '../components/QuestionBankPickerDialog.vue';
import PaperPreviewContent from '../components/PaperPreviewContent.vue';
import PaperPreviewDialog from '../components/PaperPreviewDialog.vue';
import PaperBasicPanel from '../components/PaperBasicPanel.vue';
import PaperComposePanel from '../components/PaperComposePanel.vue';
import PaperPublishPanel from '../components/PaperPublishPanel.vue';
import {
  canEnterPaperWorkspaceStage,
  getPaperWorkspaceNextStage,
  getPaperWorkspaceStageAfterPreview,
  type PaperWorkspacePreviewAction,
  type PaperWorkspaceStage
} from './paper-workspace-state.js';

const route = useRoute();
const router = useRouter();
const paperId = ref(Number(route.params.paperId) || 0);
/** 新建试卷会因路由参数变化重建组件实例，用一次性会话标记把目标阶段交接给新实例。 */
const WORKSPACE_STAGE_HANDOFF_KEY = 'paper-workspace-pending-stage';

/** 读取并立即清除交接阶段，避免刷新或再次进入工作台时误用上一次的目标阶段。 */
function consumeStageHandoff(): PaperWorkspaceStage {
  const value = globalThis.sessionStorage.getItem(WORKSPACE_STAGE_HANDOFF_KEY);
  globalThis.sessionStorage.removeItem(WORKSPACE_STAGE_HANDOFF_KEY);
  return value === 'compose' || value === 'publish' ? value : 'basic';
}

/** 当前工作台阶段，仅由用户导航或已成功完成的业务动作更新。 */
const stage = ref<PaperWorkspaceStage>(consumeStageHandoff());
const saving = ref(false);
const composing = ref(false);
const preview = ref<PaperPreview>();
const previewVisible = ref(false);
const questionPickerVisible = ref(false);
const coverUploadVisible = ref(false);
const coverFileName = ref('');
const bankCategories = ref<QuestionBankCategory[]>([]);
const questionBanks = ref<QuestionBank[]>([]);
const knowledgePoints = ref<KnowledgePoint[]>([]);
const selectedQuestionDetails = ref<Record<number, Question>>({});
const selectedQuestionIds = computed(() => compose.questions.map((item) => item.questionId));
/** 工作台阶段与新增课程保持同样的顺序导航，避免创建流程分散在多个页面。 */
const stages: Array<{ name: PaperWorkspaceStage; title: string; description: string }> = [
  { name: 'basic', title: '基础信息', description: '试卷资料与组卷类型' },
  { name: 'compose', title: '组卷配置', description: '题目、规则与计分' },
  { name: 'publish', title: '发布', description: '预览确认并固化版本' }
];
/** 基础信息严格对应 PaperCreateRequest 与 PaperUpdateRequest。 */
const basic = reactive({
  paperCode: '',
  paperName: '',
  paperType: 'FIXED' as PaperType,
  paperCategoryId: undefined as number | undefined,
  coverFileId: undefined as number | undefined,
  status: 1 as 0 | 1,
  version: 0
});
/** 组卷信息严格对应 PaperComposeRequest；总分与及格分由题目分值和比例只读计算。 */
const compose = reactive({
  durationSeconds: 3600,
  passRatio: 0.6,
  questionOrderMode: 'IMPORT',
  multiSelectScoring: 'ALL_OR_NOTHING',
  versionNote: '',
  randomSeed: undefined as number | undefined,
  questions: [] as PaperQuestionPayload[],
  drawRules: [] as PaperDrawRulePayload[]
});
const currentType = computed(() => PAPER_TYPE_OPTIONS.find((item) => item.value === basic.paperType)!);
const totalScore = computed(
  () =>
    compose.questions.reduce((total, item) => total + item.score, 0) +
    compose.drawRules.reduce((total, item) => total + item.drawCount * item.scorePerQuestion, 0)
);
const passScore = computed(() => totalScore.value * compose.passRatio);
const payload = computed<PaperComposePayload>(() => ({
  ...compose,
  drawSettingMode: basic.paperType,
  questions: basic.paperType === 'RANDOM' ? [] : compose.questions,
  drawRules: basic.paperType === 'FIXED' ? [] : compose.drawRules
}));

function invalidatePreview(): void {
  preview.value = undefined;
  previewVisible.value = false;
}

/** 未创建试卷时只能停留在基础信息阶段，保证后续请求始终使用真实 paperId。 */
function navigate(stageName: PaperWorkspaceStage): void {
  if (!canEnterPaperWorkspaceStage(paperId.value, stageName)) {
    ElMessage.warning('请先保存试卷基础信息');
    return;
  }
  stage.value = stageName;
}

/** 将弹窗选择的已发布题目加入固定题列表，并携带当前题目版本快照标识。 */
function addSelectedQuestions(questions: Question[]): void {
  for (const question of questions) {
    selectedQuestionDetails.value[question.questionId] = question;
    if (!compose.questions.some((item) => item.questionId === question.questionId) && question.currentVersion)
      compose.questions.push({
        questionId: question.questionId,
        questionVersionId: question.currentVersion.questionVersionId,
        score: 10,
        sortOrder: compose.questions.length + 1
      });
  }
  invalidatePreview();
}

/** 读取组卷页面使用的题库分类、题库和知识点选择项。 */
async function loadQuestionReferences(): Promise<void> {
  const [categories, banks, points] = await Promise.all([
    getQuestionBankCategories(),
    getQuestionBankPage({
      pageNum: 1,
      pageSize: 100,
      'query.status': 1
    }),
    getKnowledgePointTree()
  ]);
  bankCategories.value = categories;
  questionBanks.value = banks.list;
  knowledgePoints.value = points;
}

/** 将通用上传结果的 fileInfoId 写入试卷封面字段。 */
function handleCoverUploadSuccess(files: UnifiedUploadedFile[]): void {
  const fileInfo = files[0];
  if (!fileInfo) return;
  basic.coverFileId = fileInfo.fileInfoId;
  coverFileName.value = fileInfo.fileName;
  coverUploadVisible.value = false;
}

function requestCoverUpload(file: globalThis.File, onProgress: (percent: number) => void): Promise<UnifiedUploadedFile> {
  return uploadAssetFile(file, onProgress, 'exam-covers');
}

function selectedQuestion(questionId: number): Question | undefined {
  return selectedQuestionDetails.value[questionId];
}

/** 复用系统文件上传 Contract 上传考试域封面。 */
function addRule(): void {
  compose.drawRules.push({
    questionBankId: undefined!,
    questionType: 'SINGLE',
    difficulty: undefined,
    knowledgePointId: undefined,
    drawCount: 1,
    scorePerQuestion: 10,
    bankWeight: undefined,
    sortOrder: compose.drawRules.length + 1
  });
  invalidatePreview();
}

async function saveBasic(): Promise<void> {
  if (!basic.paperCode.trim() || !basic.paperName.trim()) {
    ElMessage.warning('请填写试卷编码和名称');
    return;
  }
  saving.value = true;
  try {
    if (!paperId.value) {
      paperId.value = await createPaper({
        paperCode: basic.paperCode,
        paperName: basic.paperName,
        paperType: basic.paperType,
        paperCategoryId: basic.paperCategoryId,
        coverFileId: basic.coverFileId,
        status: basic.status
      });
      // 路由参数变化会重建本页面，先登记目标阶段再跳转，保证新实例落在组卷配置。
      const nextStage = getPaperWorkspaceNextStage('basic-saved');
      globalThis.sessionStorage.setItem(WORKSPACE_STAGE_HANDOFF_KEY, nextStage);
      await router.replace(`/admin/business-center/exams/paper/workspace/${paperId.value}`);
      stage.value = nextStage;
      ElMessage.success('试卷已创建，请继续配置组卷规则');
      return;
    }
    await updatePaper(paperId.value, {
      paperName: basic.paperName,
      paperCategoryId: basic.paperCategoryId,
      coverFileId: basic.coverFileId,
      status: basic.status,
      version: basic.version
    });
    basic.version += 1;
    ElMessage.success('试卷基础信息已保存，可点击“下一步”进入组卷配置');
  } finally {
    saving.value = false;
  }
}

/** 基础信息阶段的“下一步”：未创建试卷时先创建并在创建流程中进入组卷配置。 */
async function goToCompose(): Promise<void> {
  if (!paperId.value) {
    await saveBasic();
    return;
  }
  navigate('compose');
}

async function saveCompose(): Promise<void> {
  if (!paperId.value) {
    ElMessage.warning('请先保存试卷基础信息');
    return;
  }
  composing.value = true;
  try {
    await composePaper(paperId.value, payload.value);
    ElMessage.success('组卷草稿已保存');
  } finally {
    composing.value = false;
  }
}

/** 预览当前组卷配置；仅预览时打开弹窗，进入发布时改为内嵌展示。 */
async function previewPaperFor(action: PaperWorkspacePreviewAction): Promise<void> {
  try {
    await saveCompose();
    if (paperId.value) {
      preview.value = await previewPaper(paperId.value);
      const missingIds = (preview.value.questions ?? [])
        .map((item) => item.questionId)
        .filter((questionId) => !selectedQuestionDetails.value[questionId]);
      if (missingIds.length) {
        const details = await Promise.all(missingIds.map(getQuestionDetail));
        selectedQuestionDetails.value = {
          ...selectedQuestionDetails.value,
          ...Object.fromEntries(details.map((item) => [item.questionId, item]))
        };
      }
      stage.value = getPaperWorkspaceStageAfterPreview(stage.value, action);
      previewVisible.value = action === 'preview-only';
    }
  } catch {
    preview.value = undefined;
  }
}

/** 打开当前组卷配置的独立预览弹窗。 */
async function showPreview(): Promise<void> {
  await previewPaperFor('preview-only');
}

/** 成功预览后切换到发布确认阶段，普通预览仅保留在组卷配置。 */
async function previewAndEnterPublish(): Promise<void> {
  await previewPaperFor('preview-and-publish');
}

async function publish(): Promise<void> {
  if (!preview.value || !paperId.value) {
    ElMessage.warning('请先成功预览当前配置');
    return;
  }
  await ElMessageBox.confirm('发布后将固定当前题目版本，确定继续吗？', '发布试卷版本', { type: 'warning' });
  const version = await publishPaper(paperId.value, payload.value);
  await router.replace(`/admin/business-center/exams/paper/${paperId.value}/version/${version.paperVersionId}`);
}

async function load(): Promise<void> {
  if (paperId.value) {
    const paper = await getPaper(paperId.value);
    Object.assign(basic, paper);
    await loadQuestionReferences();
    const versions = await getPaperVersions(paperId.value);
    const current = versions.find((item) => item.versionNo === paper.currentVersionNo);
    if (current) {
      const version = await getPaperVersion(current.paperVersionId);
      Object.assign(compose, {
        durationSeconds: version.durationSeconds,
        passRatio: version.passRatio,
        questionOrderMode: version.questionOrderMode,
        multiSelectScoring: version.multiSelectScoring,
        versionNote: version.versionNote ?? '',
        randomSeed: version.randomSeed,
        questions:
          version.questions?.map(({ questionId, questionVersionId, score, sortOrder }) => ({
            questionId,
            questionVersionId,
            score,
            sortOrder
          })) ?? [],
        drawRules:
          version.drawRules?.map(
            ({ questionBankId, questionType, difficulty, knowledgePointId, drawCount, scorePerQuestion, bankWeight, sortOrder }) => ({
              questionBankId,
              questionType,
              difficulty,
              knowledgePointId,
              drawCount,
              scorePerQuestion,
              bankWeight,
              sortOrder
            })
          ) ?? []
      });
      const details = await Promise.all(compose.questions.map((item) => getQuestionDetail(item.questionId)));
      selectedQuestionDetails.value = Object.fromEntries(details.map((item) => [item.questionId, item]));
    }
  } else {
    await loadQuestionReferences();
  }
}

onMounted(() => {
  void load();
});
</script>

<template>
  <section class="page admin-detail-page">
    <header class="admin-detail-page__header">
      <div>
        <h1>试卷工作台</h1>
        <p>
          在同一页完成基础信息、题库选题、组卷、预览与发布。
          <ElTag v-if="paperId" :type="basic.status ? 'success' : 'info'" size="small" effect="light">{{ basic.status ? '启用' : '停用' }}</ElTag>
        </p>
      </div>
      <ElButton @click="router.back()">返回列表</ElButton>
    </header>
    <nav class="stage-nav admin-detail-page__steps" aria-label="试卷创建阶段">
      <button
        v-for="(item, index) in stages"
        :key="item.name"
        type="button"
        class="stage-item"
        :class="{ 'is-active': stage === item.name }"
        :disabled="!canEnterPaperWorkspaceStage(paperId, item.name)"
        @click="navigate(item.name)"
      >
        <span class="stage-index">{{ index + 1 }}</span>
        <span>
          <b>{{ item.title }}</b>
          <small>{{ item.description }}</small>
        </span>
      </button>
    </nav>
    <main class="admin-detail-page__surface">
      <PaperBasicPanel
        v-if="stage === 'basic'"
        :paper-id="paperId"
        v-model:basic="basic"
        :bank-categories="bankCategories"
        :saving="saving"
        :cover-file-name="coverFileName"
        @invalidate-preview="invalidatePreview"
        @cover-uploaded="handleCoverUploadSuccess"
        @save="saveBasic"
        @next="goToCompose"
      />
      <PaperComposePanel
        v-else-if="stage === 'compose'"
        v-model:preview-visible="previewVisible"
        :paper-type="basic.paperType"
        :paper-category-id="basic.paperCategoryId"
        v-model:compose="compose"
        :composing="composing"
        :total-score="totalScore"
        :pass-score="passScore"
        :question-banks="questionBanks"
        :knowledge-points="knowledgePoints"
        :question-details="selectedQuestionDetails"
        :preview="preview"
        @invalidate-preview="invalidatePreview"
        @questions-selected="addSelectedQuestions"
        @add-rule="addRule"
        @save="saveCompose"
        @preview="showPreview"
        @preview-and-publish="previewAndEnterPublish"
      />
      <PaperPublishPanel
        v-else
        :preview="preview"
        :draw-rules="compose.drawRules"
        :question-details="selectedQuestionDetails"
        :question-banks="questionBanks"
        @compose="navigate('compose')"
        @publish="publish"
      />
      <template v-if="false">
        <ElCard v-if="stage === 'basic'" shadow="never" class="admin-detail-page__card">
          <template #header>基础信息</template>
          <div class="admin-detail-page__form-scroll">
            <ElForm label-width="120px" class="admin-detail-form">
              <ElRow :gutter="24">
                <ElCol :span="12">
                  <ElFormItem label="试卷编码" required>
                    <ElInput v-model="basic.paperCode" placeholder="请输入试卷编码" :disabled="Boolean(paperId)" />
                  </ElFormItem>
                </ElCol>
                <ElCol :span="12">
                  <ElFormItem label="试卷名称" required><ElInput v-model="basic.paperName" placeholder="请输入试卷名称" /></ElFormItem>
                </ElCol>
                <ElCol :span="12">
                  <ElFormItem label="组卷类型">
                    <ElRadioGroup v-model="basic.paperType" :disabled="Boolean(paperId)" @change="invalidatePreview">
                      <ElRadioButton v-for="item in PAPER_TYPE_OPTIONS" :key="item.value" :value="item.value">{{ item.label }}</ElRadioButton>
                    </ElRadioGroup>
                    <ElText class="hint">{{ currentType.description }}</ElText>
                  </ElFormItem>
                </ElCol>
                <ElCol :span="12">
                  <ElFormItem label="试卷分类">
                    <ElSelect v-model="basic.paperCategoryId" clearable placeholder="请选择题库分类">
                      <ElOption v-for="item in bankCategories" :key="item.bankCategoryId" :label="item.categoryName" :value="item.bankCategoryId" />
                    </ElSelect>
                  </ElFormItem>
                </ElCol>
                <ElCol :span="12">
                  <ElFormItem label="封面文件">
                    <ElButton @click="coverUploadVisible = true">{{ basic.coverFileId ? '重新上传封面' : '选择并上传封面' }}</ElButton>
                    <ElText v-if="basic.coverFileId" class="hint">{{ coverFileName || `已选择文件 #${basic.coverFileId}` }}</ElText>
                  </ElFormItem>
                </ElCol>
                <ElCol :span="12">
                  <ElFormItem label="启用状态"><ElSwitch v-model="basic.status" :active-value="1" :inactive-value="0" /></ElFormItem>
                </ElCol>
              </ElRow>
            </ElForm>
          </div>
          <footer class="sticky-actions">
            <ElButton :loading="saving" type="primary" @click="saveBasic">{{ paperId ? '保存基础信息' : '创建试卷并继续配置' }}</ElButton>
          </footer>
        </ElCard>
        <ElCard v-else-if="stage === 'compose'" shadow="never" class="admin-detail-page__card">
          <template #header>组卷配置</template>
          <div class="admin-detail-page__form-scroll">
            <ElDescriptions :column="3" border class="score-summary">
              <ElDescriptionsItem label="总分">{{ totalScore }}</ElDescriptionsItem>
              <ElDescriptionsItem label="及格比例">{{ compose.passRatio }}</ElDescriptionsItem>
              <ElDescriptionsItem label="及格分">{{ passScore }}</ElDescriptionsItem>
            </ElDescriptions>
            <ElForm label-position="top" class="compose-form">
              <ElFormItem label="考试时长">
                <DurationInput v-model="compose.durationSeconds" @change="invalidatePreview" />
              </ElFormItem>
              <ElFormItem label="及格比例">
                <ElInputNumber v-model="compose.passRatio" :min="0" :max="1" :step="0.1" @change="invalidatePreview" />
              </ElFormItem>
              <ElFormItem label="题目顺序">
                <ElSelect v-model="compose.questionOrderMode" placeholder="请选择题目顺序" @change="invalidatePreview">
                  <ElOption v-for="item in QUESTION_ORDER_MODE_OPTIONS" :key="item.value" :label="item.label" :value="item.value" />
                </ElSelect>
              </ElFormItem>
              <ElFormItem label="多选题计分">
                <ElSelect v-model="compose.multiSelectScoring" placeholder="请选择多选题计分方式" @change="invalidatePreview">
                  <ElOption v-for="item in MULTI_SELECT_SCORING_OPTIONS" :key="item.value" :label="item.label" :value="item.value" />
                </ElSelect>
              </ElFormItem>
              <ElFormItem label="随机种子">
                <ElInputNumber v-model="compose.randomSeed" :min="0" @change="invalidatePreview" />
              </ElFormItem>
              <ElFormItem label="版本说明" class="compose-form__wide">
                <ElInput v-model="compose.versionNote" placeholder="请输入版本说明" @input="invalidatePreview" />
              </ElFormItem>
            </ElForm>
            <ElDivider v-if="basic.paperType !== 'RANDOM'">试卷题目</ElDivider>
            <template v-if="basic.paperType !== 'RANDOM'">
              <ElButton plain type="primary" @click="questionPickerVisible = true">添加题目</ElButton>
              <ElTable :data="compose.questions" class="selected-table">
                <!-- 序号 -->
                <ElTableColumn type="index" width="60" label="序号" />
                <ElTableColumn label="题目编码" width="150">
                  <template #default="{ row }">{{ selectedQuestion(row.questionId)?.questionCode ?? `#${row.questionId}` }}</template>
                </ElTableColumn>
                <ElTableColumn label="题干" min-width="260" show-overflow-tooltip>
                  <template #default="{ row }">{{ selectedQuestion(row.questionId)?.currentVersion?.stem ?? '加载中' }}</template>
                </ElTableColumn>
                <ElTableColumn label="题目版本">
                  <template #default="{ row }">{{ row.questionVersionId }}</template>
                </ElTableColumn>
                <ElTableColumn label="分值" width="180">
                  <template #default="{ row }">
                    <ElInputNumber v-model="row.score" :min="1" @change="invalidatePreview" />
                  </template>
                </ElTableColumn>
                <ElTableColumn label="排序" width="180">
                  <template #default="{ row }">
                    <ElInputNumber v-model="row.sortOrder" :min="1" @change="invalidatePreview" />
                  </template>
                </ElTableColumn>
                <ElTableColumn label="操作" width="80">
                  <template #default="{ $index }">
                    <ElButton
                      link
                      type="danger"
                      @click="
                        compose.questions.splice($index, 1);
                        invalidatePreview();
                      "
                    >
                      删除
                    </ElButton>
                  </template>
                </ElTableColumn>
              </ElTable>
            </template>
            <ElDivider v-if="basic.paperType !== 'FIXED'">随机抽题规则</ElDivider>
            <template v-if="basic.paperType !== 'FIXED'">
              <ElButton plain @click="addRule">新增随机规则</ElButton>
              <div class="draw-rules-table">
                <ElTable :data="compose.drawRules" class="selected-table">
                  <ElTableColumn label="题库" width="240">
                    <template #default="{ row }">
                      <ElSelect v-model="row.questionBankId" placeholder="请选择题库" @change="invalidatePreview">
                        <ElOption v-for="bank in questionBanks" :key="bank.questionBankId" :label="bank.bankName" :value="bank.questionBankId" />
                      </ElSelect>
                    </template>
                  </ElTableColumn>
                  <ElTableColumn label="题型" width="140">
                    <template #default="{ row }">
                      <ElSelect v-model="row.questionType" placeholder="请选择题型" @change="invalidatePreview">
                        <ElOption v-for="item in QUESTION_TYPE_OPTIONS" :key="item.value" :label="item.label" :value="item.value" />
                      </ElSelect>
                    </template>
                  </ElTableColumn>
                  <ElTableColumn label="难度" width="140">
                    <template #default="{ row }">
                      <ElSelect v-model="row.difficulty" clearable placeholder="请选择难度" @change="invalidatePreview">
                        <ElOption v-for="item in DRAW_DIFFICULTY_OPTIONS" :key="item.value" :label="item.label" :value="item.value" />
                      </ElSelect>
                    </template>
                  </ElTableColumn>
                  <ElTableColumn label="知识点" width="240">
                    <template #default="{ row }">
                      <ElTreeSelect
                        v-model="row.knowledgePointId"
                        :data="knowledgePoints"
                        node-key="knowledgePointId"
                        clearable
                        check-strictly
                        :props="{ label: 'knowledgeName', children: 'children' }"
                        placeholder="请选择知识点"
                        @change="invalidatePreview"
                      />
                    </template>
                  </ElTableColumn>
                  <ElTableColumn label="抽题数量" width="180">
                    <template #default="{ row }">
                      <ElInputNumber v-model="row.drawCount" :min="1" @change="invalidatePreview" />
                    </template>
                  </ElTableColumn>
                  <ElTableColumn label="每题分值" width="180">
                    <template #default="{ row }">
                      <ElInputNumber v-model="row.scorePerQuestion" :min="1" @change="invalidatePreview" />
                    </template>
                  </ElTableColumn>
                  <ElTableColumn label="权重" width="180">
                    <template #default="{ row }">
                      <ElInputNumber v-model="row.bankWeight" :min="0" @change="invalidatePreview" />
                    </template>
                  </ElTableColumn>
                  <ElTableColumn label="排序" width="180">
                    <template #default="{ row }">
                      <ElInputNumber v-model="row.sortOrder" :min="1" @change="invalidatePreview" />
                    </template>
                  </ElTableColumn>
                  <ElTableColumn label="操作" width="80">
                    <template #default="{ $index }">
                      <ElButton
                        link
                        type="danger"
                        @click="
                          compose.drawRules.splice($index, 1);
                          invalidatePreview();
                        "
                      >
                        删除
                      </ElButton>
                    </template>
                  </ElTableColumn>
                </ElTable>
              </div>
            </template>
          </div>
          <footer class="sticky-actions">
            <ElButton :loading="composing" @click="saveCompose">保存组卷草稿</ElButton>
            <ElButton @click="showPreview">预览试卷</ElButton>
            <ElButton type="primary" @click="previewAndEnterPublish">预览并进入发布</ElButton>
          </footer>
        </ElCard>
        <ElCard v-else shadow="never" class="publish-stage admin-detail-page__card">
          <template #header>发布确认</template>
          <ElResult v-if="!preview" icon="info" title="当前阶段尚未完成预览" sub-title="请返回组卷配置，保存草稿并成功预览后再发布试卷版本。">
            <template #extra><ElButton type="primary" @click="navigate('compose')">返回组卷配置</ElButton></template>
          </ElResult>
          <template v-else>
            <div class="admin-detail-page__form-scroll">
              <ElDescriptions :column="3" border>
                <ElDescriptionsItem label="试卷题数">{{ preview?.questionCount }}</ElDescriptionsItem>
                <ElDescriptionsItem label="总分">{{ preview?.totalScore }}</ElDescriptionsItem>
                <ElDescriptionsItem label="及格分">{{ preview?.passScore }}</ElDescriptionsItem>
              </ElDescriptions>
              <!-- <ElDivider>试卷预览</ElDivider> -->
              <PaperPreviewContent
                :preview="preview"
                :show-summary="false"
                :draw-rules="compose.drawRules"
                :question-details="selectedQuestionDetails"
                :question-banks="questionBanks"
              />
              <ElAlert title="发布后将固定当前题目版本，已发布版本仅供查看。" type="warning" :closable="false" />
            </div>
            <footer class="sticky-actions">
              <ElButton @click="previewVisible = true">查看预览</ElButton>
              <ElButton type="primary" @click="publish">确认发布版本</ElButton>
            </footer>
          </template>
        </ElCard>
      </template>
    </main>
    <QuestionBankPickerDialog
      v-model="questionPickerVisible"
      :selected-question-ids="selectedQuestionIds"
      :default-bank-category-id="basic.paperCategoryId"
      @confirm="addSelectedQuestions"
    />
    <PaperPreviewDialog
      v-if="false"
      v-model="previewVisible"
      :preview="preview"
      :draw-rules="compose.drawRules"
      :question-details="selectedQuestionDetails"
      :question-banks="questionBanks"
    />
    <UnifiedFileUpload
      v-model="coverUploadVisible"
      mode="dialog"
      accept="image/*"
      :multiple="false"
      :limit="1"
      :upload-request="requestCoverUpload"
      @success="handleCoverUploadSuccess"
    />
  </section>
</template>

<style scoped lang="less">
p,
.hint {
  margin-left: 12px;
  color: var(--color-text-secondary);
}

.compose-form {
  display: grid;
  margin-top: 20px;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 16px;
}

.compose-form :deep(.el-form-item) {
  margin-bottom: 0;
}

.compose-form :deep(.el-input-number),
.compose-form :deep(.el-select) {
  width: 100%;
}

.compose-form__wide {
  grid-column: 1 / -1;
}

.score-summary {
  margin-top: 4px;
}

.selected-table {
  margin-top: 12px;
}

.draw-rules-table {
  min-width: 0;
  overflow-x: auto;
}

.draw-rules-table :deep(.el-table) {
  min-width: 1560px;
}

.publish-stage :deep(.el-alert) {
  margin-top: 16px;
}

@media (max-width: 1100px) {
  .stage-item small {
    display: none;
  }
}
</style>
