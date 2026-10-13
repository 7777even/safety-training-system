<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue';
import { ElMessage, ElMessageBox } from 'element-plus';
import { useRoute, useRouter } from 'vue-router';
import {
  createAsset,
  getAssetDetail,
  uploadAssetFile,
  type AssetListItem,
  type AssetType
} from '@/api/asset';
import {
  getQuestionDetail,
  publishQuestion,
  replaceQuestionAssets,
  updateQuestion,
  type Question,
  type QuestionAsset,
  type QuestionBlankAnswerPayload,
  type QuestionDifficulty,
  type QuestionGradingMode,
  type QuestionKeywordPayload,
  type QuestionOptionPayload,
  type QuestionType
} from '@/api/question';
import {
  ASSET_UPLOAD_TYPE_CONFIG,
  ASSET_UPLOAD_TYPE_OPTIONS,
  ASSET_UPLOAD_TYPES,
  getAssetTypeLabel
} from '@/constants/asset';
import {
  getExamEnumLabel as getEnumLabel,
  PUBLISH_STATUS_OPTIONS,
  QUESTION_DIFFICULTY_OPTIONS,
  QUESTION_TYPE_OPTIONS
} from '@/constants/exam-options';
import { Collection, Delete, Upload } from '@element-plus/icons-vue';
import UnifiedFileUpload, { type UnifiedUploadedFile } from '@/components/file-upload/UnifiedFileUpload.vue';
import AssetPickerDialog from '@/components/asset-picker/AssetPickerDialog.vue';

const route = useRoute();
const router = useRouter();
const questionId = Number(route.params.questionId);
const loading = ref(false);
const saving = ref(false);
const question = ref<Question>();
const options = ref<QuestionOptionPayload[]>([]);
/** 题干素材行；素材被删除或停用时保留占位行，避免保存关联时静默丢失。 */
interface StemAssetRow extends AssetListItem {
  isUnavailable?: boolean;
}
/** 已关联的题干素材，含名称与类型，供列表展示和保存时重排 sortOrder。 */
const linkedAssets = ref<StemAssetRow[]>([]);
const assetUploadVisible = ref(false);
const assetPickerVisible = ref(false);
const assetSaving = ref(false);
const uploadAssetType = ref<AssetType>('DOCUMENT');
const selectedAssetIds = computed(() => linkedAssets.value.map((asset) => asset.assetId));
/** 题干素材允许引用的素材类型，与课程内容素材一致，排除直播等无法离线引用的类型。 */
const STEM_ASSET_ALLOWED_TYPES: AssetType[] = ['DOCUMENT', 'IMAGE', 'AUDIO', 'VIDEO', 'RICH_TEXT', 'LINK'];
/** 上传弹窗的素材类型候选与白名单/体积上限统一取自素材域常量，页面不自定义限制。 */
const stemAssetUploadTypeOptions = ASSET_UPLOAD_TYPE_OPTIONS;
const stemAssetUploadConfig = ASSET_UPLOAD_TYPE_CONFIG;
/** 填空题答案按空位逐行编辑，序号即 blankNo，避免管理员手工对齐行号。 */
const blankAnswers = ref<Array<{ answerText: string }>>([{ answerText: '' }]);
/** 判分关键词以标签形式维护，避免逗号分隔文本难以校对。 */
const keywords = ref<string[]>([]);
const form = reactive({
  questionType: 'SINGLE' as QuestionType,
  stem: '',
  analysis: '',
  standardAnswer: '',
  difficulty: 'EASY' as QuestionDifficulty,
  gradingMode: 'AUTO' as QuestionGradingMode,
  versionNote: '',
  version: 0,
  knowledgePointIds: [] as number[],
  isUnorderedAnswerAllowed: 0 as 0 | 1,
  isCaseIgnored: 0 as 0 | 1,
  isFullHalfWidthIgnored: 0 as 0 | 1
});

function isPublished(): boolean {
  return question.value?.currentVersion?.publishedStatus === 'PUBLISHED';
}

function isOptionQuestion(): boolean {
  return ['SINGLE', 'MULTIPLE', 'JUDGE'].includes(form.questionType);
}

/** 判断题只有两个固定选项，其余选择题按需增减。 */
function isJudgeQuestion(): boolean {
  return form.questionType === 'JUDGE';
}

const canRemoveOption = computed(() => options.value.length > (isJudgeQuestion() ? 2 : 1));
const correctOptionCount = computed(() => options.value.filter((option) => option.isCorrect === 1).length);
const gradingModeText = computed(() => (form.gradingMode === 'AUTO' ? '自动判分' : '人工判分'));

async function loadDetail(): Promise<void> {
  if (!Number.isFinite(questionId)) {
    ElMessage.error('题目编号无效');
    return;
  }
  loading.value = true;
  try {
    question.value = await getQuestionDetail(questionId);
    const version = question.value.currentVersion;
    if (!version) throw new Error('题目当前版本不存在');
    Object.assign(form, {
      questionType: version.questionType,
      stem: version.stem,
      analysis: version.analysis ?? '',
      standardAnswer: version.standardAnswer ?? '',
      difficulty: version.difficulty,
      gradingMode: version.gradingMode,
      versionNote: version.versionNote ?? '',
      version: question.value.version ?? 0,
      knowledgePointIds: [...(question.value.knowledgePointIds ?? [])],
      isUnorderedAnswerAllowed: version.isUnorderedAnswerAllowed ?? 0,
      isCaseIgnored: version.isCaseIgnored ?? 0,
      isFullHalfWidthIgnored: version.isFullHalfWidthIgnored ?? 0
    });
    // 详情返回的填空答案与判分关键词按 sortOrder 回显，保持与后端一致的展示顺序。
    const blankList = [...(version.blankAnswers ?? [])].sort((left, right) => left.sortOrder - right.sortOrder);
    blankAnswers.value = blankList.length ? blankList.map((item) => ({ answerText: item.answerText })) : [{ answerText: '' }];
    keywords.value = [...(version.keywords ?? [])].sort((left, right) => left.sortOrder - right.sortOrder).map((item) => item.keyword);
    options.value =
      version.options?.map(({ optionKey, optionContent, isCorrect, sortOrder }) => ({
        optionKey,
        optionContent,
        isCorrect,
        sortOrder
      })) ?? [];
    await loadStemAssets(version.assets ?? []);
  } finally {
    loading.value = false;
  }
}

/** 题干素材按 sortOrder 回显名称与类型；素材已删除或停用时保留占位行，避免保存关联时被静默移除。 */
async function loadStemAssets(assets: QuestionAsset[]): Promise<void> {
  const stemAssets = assets
    .filter((asset) => asset.assetRole === 'STEM')
    .sort((left, right) => left.sortOrder - right.sortOrder);
  linkedAssets.value = await Promise.all(
    stemAssets.map(async (asset) => {
      try {
        return await getAssetDetail(asset.assetId);
      } catch {
        return {
          assetId: asset.assetId,
          assetCode: '—',
          assetName: `素材 #${asset.assetId}`,
          assetType: 'DOCUMENT' as AssetType,
          processStatus: 'FAILED' as const,
          status: 0 as const,
          isUnavailable: true
        };
      }
    })
  );
}

/** 将逐空答案转换为 Contract 定义的答案序号结构。 */
function buildBlankAnswers(payload: Array<{ answerText: string }>): QuestionBlankAnswerPayload[] {
  return payload
    .map((item) => item.answerText.trim())
    .filter(Boolean)
    .map((answerText, index) => ({ blankNo: index + 1, answerGroupNo: 1, answerText, sortOrder: index + 1 }));
}

/** 将关键词标签转换为 Contract 定义的关键词列表。 */
function buildKeywords(payload: string[]): QuestionKeywordPayload[] {
  return payload
    .map((keyword) => keyword.trim())
    .filter(Boolean)
    .map((keyword, index) => ({ keyword, weight: 1, sortOrder: index + 1 }));
}

function addBlankAnswer(): void {
  blankAnswers.value.push({ answerText: '' });
}

function removeBlankAnswer(index: number): void {
  if (blankAnswers.value.length <= 1) return;
  blankAnswers.value.splice(index, 1);
}

function addOption(): void {
  options.value.push({
    optionKey: String.fromCharCode(65 + options.value.length),
    optionContent: '',
    isCorrect: 0,
    sortOrder: options.value.length + 1
  });
}

/** 删除选项后重排字母与序号，避免出现 A、C 这类跳号。 */
function removeOption(index: number): void {
  if (!canRemoveOption.value) return;
  options.value.splice(index, 1);
  options.value.forEach((option, optionIndex) => {
    option.optionKey = String.fromCharCode(65 + optionIndex);
    option.sortOrder = optionIndex + 1;
  });
}

/** 单选题与判断题只能有一个正确项，多选题允许多选。 */
function toggleCorrectOption(index: number): void {
  if (form.questionType === 'MULTIPLE') {
    options.value[index].isCorrect = options.value[index].isCorrect === 1 ? 0 : 1;
    return;
  }
  options.value.forEach((option, optionIndex) => (option.isCorrect = optionIndex === index ? 1 : 0));
}

/** 切换题型后清理不适用于新题型的专属数据，避免隐藏字段随请求错误提交。 */
function handleQuestionTypeChange(): void {
  if (isOptionQuestion()) {
    if (!options.value.length) addOption();
  } else {
    options.value = [];
  }
  if (form.questionType !== 'FILL') {
    blankAnswers.value = [{ answerText: '' }];
    form.isUnorderedAnswerAllowed = 0;
    form.isCaseIgnored = 0;
    form.isFullHalfWidthIgnored = 0;
  }
  if (form.questionType !== 'ESSAY') {
    keywords.value = [];
    form.standardAnswer = '';
    form.gradingMode = 'AUTO';
  }
}

/** 切换判分方式时清理另一种判分方式的专属输入。 */
function handleGradingModeChange(): void {
  if (form.gradingMode === 'AUTO') {
    form.standardAnswer = '';
    return;
  }
  keywords.value = [];
}

async function save(): Promise<void> {
  if (!form.stem.trim()) {
    ElMessage.warning('请输入题干');
    return;
  }
  if (
    isOptionQuestion() &&
    (!options.value.every((option) => option.optionContent.trim()) || !options.value.some((option) => option.isCorrect === 1))
  ) {
    ElMessage.warning('选项题至少需要一个内容完整且被标记为正确的选项');
    return;
  }
  if (form.questionType === 'FILL' && buildBlankAnswers(blankAnswers.value).length === 0) {
    ElMessage.warning('请至少填写一个填空题标准答案');
    return;
  }
  if (form.questionType === 'ESSAY' && form.gradingMode === 'AUTO' && buildKeywords(keywords.value).length === 0) {
    ElMessage.warning('自动判分的简答题请至少填写一个判分关键词');
    return;
  }
  if (form.questionType === 'ESSAY' && form.gradingMode === 'MANUAL' && !form.standardAnswer.trim()) {
    ElMessage.warning('人工判分的简答题请填写评分参考');
    return;
  }
  const createDraftFromPublished = isPublished();
  saving.value = true;
  try {
    await updateQuestion(questionId, {
      questionType: form.questionType,
      stem: form.stem.trim(),
      difficulty: form.difficulty,
      gradingMode: form.questionType === 'ESSAY' ? form.gradingMode : 'AUTO',
      version: form.version,
      ...(form.analysis.trim() ? { analysis: form.analysis.trim() } : {}),
      ...(form.versionNote.trim() ? { versionNote: form.versionNote.trim() } : {}),
      ...(isOptionQuestion() ? { options: options.value } : {}),
      ...(form.questionType === 'FILL'
        ? {
            blankAnswers: buildBlankAnswers(blankAnswers.value),
            isUnorderedAnswerAllowed: form.isUnorderedAnswerAllowed,
            isCaseIgnored: form.isCaseIgnored,
            isFullHalfWidthIgnored: form.isFullHalfWidthIgnored
          }
        : {}),
      ...(form.questionType === 'ESSAY' && form.gradingMode === 'AUTO' ? { keywords: buildKeywords(keywords.value) } : {}),
      ...(form.questionType === 'ESSAY' && form.gradingMode === 'MANUAL' ? { standardAnswer: form.standardAnswer.trim() } : {})
    });
    ElMessage.success(createDraftFromPublished ? '已基于已发布版本创建新草稿' : '题目草稿已保存');
    await loadDetail();
  } finally {
    saving.value = false;
  }
}

async function publish(): Promise<void> {
  await ElMessageBox.confirm('发布后将固定当前题目版本，确定继续吗？', '发布题目版本', { type: 'warning' });
  await publishQuestion(questionId);
  ElMessage.success('题目版本已发布');
  await loadDetail();
}

/** 保存题干素材关联；素材实体与可用性仍由后端 Contract 校验。 */
async function saveAssets(): Promise<void> {
  if (isPublished()) {
    ElMessage.warning('已发布版本不可编辑');
    return;
  }
  assetSaving.value = true;
  try {
    await replaceQuestionAssets(
      questionId,
      linkedAssets.value.map((asset, index) => ({ assetId: asset.assetId, assetRole: 'STEM', optionKey: '', sortOrder: index + 1 }))
    );
    ElMessage.success('题干素材关联已保存');
    await loadDetail();
  } finally {
    assetSaving.value = false;
  }
}

/** 打开本地上传弹窗，默认从文档开始选择，避免沿用上一次会话的类型。 */
function openStemAssetUpload(): void {
  uploadAssetType.value = 'DOCUMENT';
  assetUploadVisible.value = true;
}

/** 记录上传弹窗中选定的素材类型，用于为新建素材定档。 */
function handleStemUploadTypeChange(assetType: string): void {
  if (ASSET_UPLOAD_TYPES.includes(assetType as AssetType)) uploadAssetType.value = assetType as AssetType;
}

/** 将通用上传组件的请求桥接到系统文件上传 Contract。 */
function requestStemAssetUpload(
  file: globalThis.File,
  onProgress: (percent: number) => void,
  signal: globalThis.AbortSignal
): Promise<UnifiedUploadedFile> {
  return uploadAssetFile(file, onProgress, 'asset', signal);
}

/** 本地上传的题干素材由前端生成唯一编码，避免要求管理员为临时素材手工编码。 */
function buildStemAssetCode(): string {
  return `QSTEM-${Date.now().toString(36).toUpperCase()}-${Math.random().toString(36).slice(2, 6).toUpperCase()}`;
}

/** 上传成功后立即在素材库建档，使文件可被题目版本引用；建档失败不进入已关联列表。 */
async function handleStemAssetUploaded(files: UnifiedUploadedFile[], assetType?: string): Promise<void> {
  if (isPublished() || files.length === 0) return;
  const targetType = assetType && ASSET_UPLOAD_TYPES.includes(assetType as AssetType)
    ? (assetType as AssetType)
    : uploadAssetType.value;
  try {
    const created = await Promise.all(
      files.map(async (fileInfo) => {
        const assetId = await createAsset({
          assetCode: buildStemAssetCode(),
          assetName: fileInfo.fileName.slice(0, 255),
          assetType: targetType,
          sourceType: 'UPLOAD',
          fileInfoId: fileInfo.fileInfoId,
          fileSize: fileInfo.fileSize,
          ...(fileInfo.sourceFile?.type ? { mimeType: fileInfo.sourceFile.type } : {}),
          status: 1
        });
        try {
          return await getAssetDetail(assetId);
        } catch {
          // 建档已成功，详情读取失败时用上传结果占位，保存关联只依赖 assetId。
          return {
            assetId,
            assetCode: `#${assetId}`,
            assetName: fileInfo.fileName,
            assetType: targetType,
            processStatus: 'UPLOADED' as const,
            status: 1 as const
          };
        }
      })
    );
    // 本地上传只做追加，不会覆盖素材库弹窗维护的既有选择。
    appendStemAssets(created);
    ElMessage.success(`已上传 ${created.length} 个素材，点击“保存素材关联”后生效`);
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '素材建档失败，请稍后在素材库中重试');
  }
}

/** 追加素材到已关联列表，按主键去重并保留既有顺序。 */
function appendStemAssets(assets: AssetListItem[]): void {
  const merged = new Map<number, StemAssetRow>(linkedAssets.value.map((asset) => [asset.assetId, asset]));
  assets.forEach((asset) => merged.set(asset.assetId, asset));
  linkedAssets.value = Array.from(merged.values());
}

/**
 * 素材库弹窗以「当前已关联集合」回显，确认后按库中勾选结果重建关联，取消勾选即视为移除。
 * 已删除或停用的历史素材不在弹窗可选范围内，需原样保留，避免保存时被静默移除。
 */
function chooseStemAssets(assets: AssetListItem[]): void {
  const rebuilt = new Map<number, StemAssetRow>(assets.map((asset) => [asset.assetId, asset]));
  linkedAssets.value
    .filter((asset) => asset.isUnavailable && !rebuilt.has(asset.assetId))
    .forEach((asset) => rebuilt.set(asset.assetId, asset));
  linkedAssets.value = Array.from(rebuilt.values());
}

function removeStemAsset(assetId: number): void {
  linkedAssets.value = linkedAssets.value.filter((asset) => asset.assetId !== assetId);
}

onMounted(() => {
  void loadDetail();
});
</script>

<template>
  <section v-loading="loading" class="question-detail-page admin-detail-page">
    <header class="admin-detail-page__header">
      <div class="question-heading">
        <h1>题目详情</h1>
        <p>
          {{ question?.questionCode ?? '—' }} · 当前版本 V{{ question?.currentVersionNo ?? '—' }} ·
          {{ isPublished() ? '已发布版本需先创建新草稿再修改' : '草稿可继续完善并发布' }}
        </p>
      </div>
      <div class="question-heading__actions">
        <ElTag :type="isPublished() ? 'success' : 'warning'" effect="light" round>
          {{ getEnumLabel(question?.currentVersion?.publishedStatus, PUBLISH_STATUS_OPTIONS) }}
        </ElTag>
        <ElButton @click="router.back()">返回题库</ElButton>
      </div>
    </header>

    <ElCard v-if="question" shadow="never" class="admin-detail-page__card">
      <div class="detail-body">
        <div class="admin-detail-page__form-scroll detail-main">
          <section class="form-section">
            <header class="form-section__header">
              <strong>基础信息</strong>
              <span>题型、难度与判分方式</span>
            </header>
            <div class="form-section__body">
              <ElForm label-position="top" class="detail-form">
                <ElFormItem label="题型">
                  <ElRadioGroup v-model="form.questionType" :disabled="isPublished()" @change="handleQuestionTypeChange">
                    <ElRadioButton v-for="item in QUESTION_TYPE_OPTIONS" :key="item.value" :value="item.value" :disabled="isPublished()">
                      {{ item.label }}
                    </ElRadioButton>
                  </ElRadioGroup>
                </ElFormItem>
                <ElRow :gutter="16">
                  <ElCol :span="12">
                    <ElFormItem label="难度">
                      <ElSelect v-model="form.difficulty" :disabled="isPublished()" placeholder="请选择难度">
                        <ElOption v-for="item in QUESTION_DIFFICULTY_OPTIONS" :key="item.value" :label="item.label" :value="item.value" />
                      </ElSelect>
                    </ElFormItem>
                  </ElCol>
                  <ElCol v-if="form.questionType === 'ESSAY'" :span="12">
                    <ElFormItem label="判分方式">
                      <ElRadioGroup v-model="form.gradingMode" :disabled="isPublished()" @change="handleGradingModeChange">
                        <ElRadio value="AUTO" :disabled="isPublished()">自动判分</ElRadio>
                        <ElRadio value="MANUAL" :disabled="isPublished()">人工判分</ElRadio>
                      </ElRadioGroup>
                    </ElFormItem>
                  </ElCol>
                </ElRow>
              </ElForm>
            </div>
          </section>

          <section class="form-section">
            <header class="form-section__header">
              <strong>题干与素材</strong>
              <span>题干支持换行；素材可本地上传，也可引用素材库</span>
            </header>
            <div class="form-section__body">
              <ElForm label-position="top" class="detail-form">
                <ElFormItem label="题干" required>
                  <ElInput
                    v-model="form.stem"
                    type="textarea"
                    :rows="4"
                    :disabled="isPublished()"
                    maxlength="2000"
                    show-word-limit
                    placeholder="请输入题干内容"
                  />
                </ElFormItem>
                <ElFormItem label="题干素材">
                  <div class="asset-block">
                    <div class="asset-block__toolbar">
                      <div class="asset-block__actions">
                        <ElButton v-if="!isPublished()" type="primary" plain :icon="Upload" @click="openStemAssetUpload">本地上传</ElButton>
                        <ElButton v-if="!isPublished()" plain :icon="Collection" @click="assetPickerVisible = true">从素材库选择</ElButton>
                        <span v-if="isPublished()" class="asset-block__frozen">已发布版本的素材关联已冻结</span>
                      </div>
                      <span class="asset-block__count">已关联 {{ linkedAssets.length }} 个</span>
                    </div>

                    <ul v-if="linkedAssets.length" class="asset-list">
                      <li
                        v-for="(asset, index) in linkedAssets"
                        :key="asset.assetId"
                        class="asset-item"
                        :class="{ 'is-unavailable': asset.isUnavailable }"
                      >
                        <span class="asset-item__index">{{ index + 1 }}</span>
                        <div class="asset-item__main">
                          <strong>{{ asset.assetName }}</strong>
                          <span class="asset-item__meta">
                            <ElTag size="small" effect="plain" :type="asset.isUnavailable ? 'danger' : 'primary'">
                              {{ asset.isUnavailable ? '已不可用' : getAssetTypeLabel(asset.assetType) }}
                            </ElTag>
                            <em>{{ asset.assetCode }}</em>
                          </span>
                        </div>
                        <ElButton v-if="!isPublished()" link type="danger" :icon="Delete" @click="removeStemAsset(asset.assetId)">移除</ElButton>
                      </li>
                    </ul>
                    <p v-else class="asset-block__empty">暂未关联题干素材，可从本地上传，或引用素材库中已完成处理的素材。</p>

                    <div class="asset-block__footer">
                      <ElButton v-if="!isPublished()" type="primary" :loading="assetSaving" @click="saveAssets">保存素材关联</ElButton>
                      <span class="asset-block__tip">
                        {{
                          isPublished()
                            ? '当前版本已发布，素材关联已冻结；创建新草稿后可重新调整。'
                            : '新增或移除素材后需点击“保存素材关联”，该关联不随下方表单一起提交。'
                        }}
                      </span>
                    </div>
                  </div>
                </ElFormItem>
              </ElForm>
            </div>
          </section>

          <section class="form-section">
            <header class="form-section__header">
              <strong>{{ isOptionQuestion() ? '选项与正确答案' : form.questionType === 'FILL' ? '填空答案' : '判分设置' }}</strong>
              <span v-if="isOptionQuestion()">共 {{ options.length }} 个选项 · 已标记 {{ correctOptionCount }} 个正确答案</span>
              <span v-else-if="form.questionType === 'FILL'">共 {{ blankAnswers.length }} 个空</span>
              <span v-else>{{ gradingModeText }}</span>
            </header>
            <div class="form-section__body">
              <template v-if="isOptionQuestion()">
                <div class="option-list">
                  <div v-for="(option, index) in options" :key="option.optionKey" class="option-item" :class="{ 'is-correct': option.isCorrect === 1 }">
                    <span class="option-item__key">{{ option.optionKey }}</span>
                    <ElInput v-model="option.optionContent" :disabled="isPublished()" :placeholder="`请输入 ${option.optionKey} 选项内容`" />
                    <ElButton
                      class="option-item__correct"
                      :type="option.isCorrect === 1 ? 'primary' : 'default'"
                      :plain="option.isCorrect !== 1"
                      :disabled="isPublished()"
                      @click="toggleCorrectOption(index)"
                    >
                      {{ option.isCorrect === 1 ? '正确答案' : '设为正确' }}
                    </ElButton>
                    <ElButton link type="danger" :disabled="isPublished() || !canRemoveOption" @click="removeOption(index)">删除</ElButton>
                  </div>
                </div>
                <div class="option-actions">
                  <ElButton v-if="!isPublished()" plain type="primary" @click="addOption">新增选项</ElButton>
                  <span class="field-hint">
                    {{ form.questionType === 'MULTIPLE' ? '多选题可标记多个正确答案。' : '单选题与判断题只能标记一个正确答案。' }}
                  </span>
                </div>
              </template>

              <template v-else-if="form.questionType === 'FILL'">
                <div class="blank-list">
                  <div v-for="(blank, index) in blankAnswers" :key="index" class="blank-item">
                    <span class="blank-item__key">第 {{ index + 1 }} 空</span>
                    <ElInput v-model="blank.answerText" :disabled="isPublished()" placeholder="请输入该空的标准答案" />
                    <ElButton link type="danger" :disabled="isPublished() || blankAnswers.length <= 1" @click="removeBlankAnswer(index)">
                      删除
                    </ElButton>
                  </div>
                </div>
                <div class="option-actions">
                  <ElButton v-if="!isPublished()" plain type="primary" @click="addBlankAnswer">新增空位</ElButton>
                </div>
                <ElDivider />
                <ElForm label-position="top" class="detail-form">
                  <ElFormItem label="答案匹配规则">
                    <ElCheckbox v-model="form.isUnorderedAnswerAllowed" :true-value="1" :false-value="0" :disabled="isPublished()">
                      允许答案无序
                    </ElCheckbox>
                    <ElCheckbox v-model="form.isCaseIgnored" :true-value="1" :false-value="0" :disabled="isPublished()">忽略大小写</ElCheckbox>
                    <ElCheckbox v-model="form.isFullHalfWidthIgnored" :true-value="1" :false-value="0" :disabled="isPublished()">
                      忽略全半角
                    </ElCheckbox>
                    <p class="field-hint">规则在学员作答自动判分时生效，可同时启用多项。</p>
                  </ElFormItem>
                </ElForm>
              </template>

              <template v-else>
                <ElForm label-position="top" class="detail-form">
                  <ElFormItem v-if="form.gradingMode === 'AUTO'" label="判分关键词">
                    <ElSelect
                      v-model="keywords"
                      class="keyword-editor"
                      multiple
                      filterable
                      allow-create
                      default-first-option
                      :reserve-keyword="false"
                      :disabled="isPublished()"
                      placeholder="输入关键词后回车即可添加"
                    />
                    <p class="field-hint">学员答案命中关键词即得分，可添加多个关键词。</p>
                  </ElFormItem>
                  <ElFormItem v-else label="评分参考">
                    <ElInput
                      v-model="form.standardAnswer"
                      type="textarea"
                      :rows="3"
                      :disabled="isPublished()"
                      placeholder="请输入人工判分时参考的评分要点"
                    />
                    <p class="field-hint">人工判分不参与自动评分，仅作为阅卷人评分参考。</p>
                  </ElFormItem>
                </ElForm>
              </template>
            </div>
          </section>

          <section class="form-section">
            <header class="form-section__header">
              <strong>补充信息</strong>
              <span>题目解析与版本说明为可选项</span>
            </header>
            <div class="form-section__body">
              <ElForm label-position="top" class="detail-form">
                <ElFormItem label="题目解析">
                  <ElInput v-model="form.analysis" type="textarea" :rows="3" :disabled="isPublished()" placeholder="请输入题目解析" />
                </ElFormItem>
                <ElFormItem label="版本说明">
                  <ElInput v-model="form.versionNote" :disabled="isPublished()" placeholder="请输入本次版本说明" />
                </ElFormItem>
              </ElForm>
            </div>
          </section>
        </div>

        <aside class="detail-aside">
          <div class="aside-card">
            <div class="aside-card__title">版本状态</div>
            <ul class="aside-meta">
              <li>
                <span>题目编码</span>
                <b>{{ question.questionCode }}</b>
              </li>
              <li>
                <span>当前版本</span>
                <b>V{{ question.currentVersionNo ?? '—' }}</b>
              </li>
              <li>
                <span>发布状态</span>
                <b :class="isPublished() ? 'is-success' : 'is-warning'">
                  {{ getEnumLabel(question.currentVersion?.publishedStatus, PUBLISH_STATUS_OPTIONS) }}
                </b>
              </li>
              <li>
                <span>发布时间</span>
                <b>{{ question.currentVersion?.publishedTime || '未发布' }}</b>
              </li>
              <li>
                <span>判分方式</span>
                <b>{{ form.questionType === 'ESSAY' ? gradingModeText : '自动判分' }}</b>
              </li>
              <li>
                <span>关联素材</span>
                <b>{{ selectedAssetIds.length }} 个</b>
              </li>
            </ul>
          </div>
          <ElAlert
            class="aside-tip"
            :title="isPublished() ? '当前版本已发布' : '当前为草稿'"
            :description="
              isPublished()
                ? '已发布内容不可直接改写；点击“创建新草稿”后会生成新的草稿版本。'
                : '请检查题干、答案和判分规则，确认无误后再发布版本。'
            "
            :type="isPublished() ? 'success' : 'warning'"
            show-icon
            :closable="false"
          />
        </aside>
      </div>

      <footer class="admin-detail-action-bar">
        <ElButton @click="router.back()">返回</ElButton>
        <ElButton v-if="!isPublished()" :loading="saving" @click="save">保存草稿</ElButton>
        <ElButton v-if="!isPublished()" type="primary" @click="publish">发布版本</ElButton>
        <ElButton v-if="isPublished()" type="primary" :loading="saving" @click="save">创建新草稿</ElButton>
      </footer>
    </ElCard>

    <UnifiedFileUpload
      v-model="assetUploadVisible"
      mode="dialog"
      :type-options="stemAssetUploadTypeOptions"
      initial-type="DOCUMENT"
      :type-upload-config="stemAssetUploadConfig"
      :multiple="true"
      :limit="10"
      :upload-request="requestStemAssetUpload"
      @type-change="handleStemUploadTypeChange"
      @success="handleStemAssetUploaded"
    />
    <AssetPickerDialog
      v-model="assetPickerVisible"
      title="选择题干素材"
      selection-mode="multiple"
      :selected-ids="selectedAssetIds"
      :allowed-types="STEM_ASSET_ALLOWED_TYPES"
      @confirm="chooseStemAssets"
    />
  </section>
</template>

<style scoped lang="less">
.question-detail-page :deep(.el-card__body) {
  display: flex;
  flex-direction: column;
  overflow: hidden;
}

.question-heading p {
  margin: var(--space-2) 0 0 var(--space-4);
  color: var(--color-text-secondary);
  font-size: 13px;
}

.question-heading__actions {
  display: flex;
  align-items: center;
  gap: var(--space-3);
}

/* 主表单与右侧版本信息两栏布局，避免单列长表单造成的视觉疲劳。 */
.detail-body {
  display: grid;
  min-height: 0;
  flex: 1;
  grid-template-columns: minmax(0, 1fr) 292px;
  gap: var(--space-5);
  padding: var(--space-5) var(--space-6) 0;
  overflow: hidden;
}

.detail-main {
  padding-right: 6px;
  padding-bottom: var(--space-5);
}

.detail-aside {
  min-height: 0;
  padding-bottom: var(--space-5);
  overflow: auto;
}

.form-section {
  border: 1px solid var(--color-border);
  border-radius: var(--radius-card);
  background: var(--color-bg-surface);
}

.form-section + .form-section {
  margin-top: var(--space-4);
}

.form-section__header {
  display: flex;
  min-height: 44px;
  align-items: center;
  justify-content: space-between;
  gap: var(--space-3);
  padding: 0 var(--space-4);
  border-bottom: 1px solid var(--color-border);
  border-radius: var(--radius-card) var(--radius-card) 0 0;
  background: var(--color-bg-subtle);
}

.form-section__header strong {
  color: var(--color-text-primary);
  font-size: 14px;
  font-weight: 600;
}

.form-section__header span {
  color: var(--color-text-secondary);
  font-size: 12px;
}

.form-section__body {
  padding: var(--space-4);
}

.detail-form :deep(.el-form-item) {
  margin-bottom: var(--space-4);
}

.detail-form :deep(.el-form-item:last-child) {
  margin-bottom: 0;
}

.detail-form :deep(.el-select),
.detail-form :deep(.el-input-number),
.detail-form :deep(.el-tree-select) {
  width: 100%;
}

.field-hint {
  margin: var(--space-2) 0 0;
  color: var(--color-text-secondary);
  font-size: 12px;
  line-height: 18px;
}

/* 题干素材区：工具条 + 已关联列表 + 保存动作，替代原先单行下拉的窄交互。 */
.asset-block {
  display: grid;
  width: 100%;
  gap: var(--space-3);
}

.asset-block__toolbar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: var(--space-3);
  padding: var(--space-3) var(--space-4);
  border: 1px solid var(--color-border);
  border-radius: var(--radius-control);
  background: var(--color-bg-subtle);
}

.asset-block__actions {
  display: flex;
  align-items: center;
  gap: var(--space-2);
}

.asset-block__frozen,
.asset-block__count {
  color: var(--color-text-secondary);
  font-size: 12px;
}

.asset-list {
  display: grid;
  margin: 0;
  padding: 0;
  gap: var(--space-2);
  list-style: none;
}

.asset-item {
  display: grid;
  align-items: center;
  gap: var(--space-3);
  grid-template-columns: 28px minmax(0, 1fr) auto;
  padding: var(--space-3);
  border: 1px solid var(--color-border);
  border-radius: var(--radius-control);
  background: var(--color-bg-surface);
  transition: border-color 0.2s, background-color 0.2s;
}

.asset-item:hover {
  border-color: var(--color-primary);
}

.asset-item.is-unavailable {
  border-color: var(--color-danger);
  background: var(--color-bg-subtle);
}

.asset-item__index {
  display: grid;
  height: 28px;
  place-items: center;
  border: 1px solid var(--color-border);
  border-radius: var(--radius-control);
  background: var(--color-bg-subtle);
  color: var(--color-text-regular);
  font-size: 12px;
  font-weight: 600;
}

.asset-item__main {
  display: grid;
  min-width: 0;
  gap: 4px;
}

.asset-item__main strong {
  overflow: hidden;
  color: var(--color-text-primary);
  font-size: 13px;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.asset-item__meta {
  display: flex;
  min-width: 0;
  align-items: center;
  gap: var(--space-2);
  color: var(--color-text-secondary);
  font-size: 12px;
}

.asset-item__meta em {
  overflow: hidden;
  font-style: normal;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.asset-block__empty {
  margin: 0;
  padding: var(--space-4);
  border: 1px dashed var(--color-border);
  border-radius: var(--radius-control);
  background: var(--color-bg-subtle);
  color: var(--color-text-secondary);
  font-size: 13px;
  text-align: center;
}

.asset-block__footer {
  display: flex;
  align-items: center;
  gap: var(--space-3);
}

.asset-block__tip {
  color: var(--color-text-secondary);
  font-size: 12px;
  line-height: 18px;
}

.option-list,
.blank-list {
  display: grid;
  gap: var(--space-3);
}

.option-item,
.blank-item {
  display: grid;
  align-items: center;
  gap: var(--space-3);
  padding: var(--space-3);
  border: 1px solid var(--color-border);
  border-radius: var(--radius-control);
  background: var(--color-bg-surface);
  transition: border-color 0.2s, background-color 0.2s;
}

.option-item {
  grid-template-columns: 32px minmax(0, 1fr) auto auto;
}

.option-item.is-correct {
  border-color: var(--color-primary);
  background: var(--color-primary-subtle);
}

.blank-item {
  grid-template-columns: 72px minmax(0, 1fr) auto;
}

.option-item__key,
.blank-item__key {
  display: grid;
  place-items: center;
  border-radius: var(--radius-control);
  background: var(--color-bg-subtle);
  color: var(--color-text-regular);
  font-size: 12px;
  font-weight: 600;
}

.option-item__key {
  height: 32px;
  border: 1px solid var(--color-border);
}

.option-item.is-correct .option-item__key {
  border-color: var(--color-primary);
  background: var(--color-primary);
  color: #fff;
}

.blank-item__key {
  height: 32px;
  border: 1px solid var(--color-border);
}

.option-item__correct {
  min-width: 92px;
}

.option-actions {
  display: flex;
  align-items: center;
  gap: var(--space-3);
  margin-top: var(--space-3);
}

.keyword-editor {
  width: 100%;
}

.aside-card {
  border: 1px solid var(--color-border);
  border-radius: var(--radius-card);
  background: var(--color-bg-surface);
  overflow: hidden;
}

.aside-card__title {
  padding: var(--space-3) var(--space-4);
  border-bottom: 1px solid var(--color-border);
  background: var(--color-bg-subtle);
  color: var(--color-text-primary);
  font-size: 14px;
  font-weight: 600;
}

.aside-meta {
  margin: 0;
  padding: var(--space-2) var(--space-4) var(--space-4);
  list-style: none;
}

.aside-meta li {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: var(--space-3);
  padding: var(--space-3) 0;
  border-bottom: 1px dashed var(--color-border);
  font-size: 13px;
}

.aside-meta li:last-child {
  border-bottom: 0;
}

.aside-meta span {
  flex: 0 0 auto;
  color: var(--color-text-secondary);
}

.aside-meta b {
  min-width: 0;
  color: var(--color-text-primary);
  font-weight: 600;
  text-align: right;
  word-break: break-all;
}

.aside-meta b.is-success {
  color: #16a34a;
}

.aside-meta b.is-warning {
  color: #d97706;
}

.aside-tip {
  margin-top: var(--space-4);
}

@media (max-width: 1280px) {
  .detail-body {
    grid-template-columns: minmax(0, 1fr);
  }

  .detail-aside {
    padding-bottom: var(--space-5);
  }
}
</style>
