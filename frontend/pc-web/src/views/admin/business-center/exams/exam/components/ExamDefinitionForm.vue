<script setup lang="ts">
import { computed, ref, watch } from 'vue';
import { uploadAssetFile } from '@/api/asset';
import DurationInput from '@/components/duration/DurationInput.vue';
import { type ExamCreatePayload, type ExamDrawRulePayload, type ExamPolicyRequest } from '@/api/exam';
import type { PaperVersionOption } from '@/api/paper';
import { type QuestionBank, type QuestionBankCategory } from '@/api/question';
import ExamDrawRuleDialog from './ExamDrawRuleDialog.vue';
import UnifiedFileUpload, { type UnifiedUploadedFile } from '@/components/file-upload/UnifiedFileUpload.vue';
import PersonPickerDialog from '@/components/organization/PersonPickerDialog.vue';
import { Picture } from '@element-plus/icons-vue';
import { getPersonPickerPage, type PersonPickerItem } from '@/api/person-picker';
import { useUserStore } from '@/stores';
import {
  ANSWER_DETAIL_MODE_OPTIONS,
  EXAM_TYPE_OPTIONS,
  MULTI_SELECT_SCORING_OPTIONS,
  QUESTION_ORDER_MODE_OPTIONS,
  RETAKE_SCOPE_OPTIONS,
  RETAKE_SCORE_MODE_OPTIONS
} from '@/constants/exam-options';

const form = defineModel<ExamCreatePayload>({ required: true });
const userStore = useUserStore();
const props = defineProps<{
  editing?: boolean;
  createMode?: boolean;
  paperOptions: PaperVersionOption[];
  categoryOptions: QuestionBankCategory[];
  questionBankOptions: QuestionBank[];
}>();
const emit = defineEmits<{
  paperVersionSelected: [paperVersionId: number | undefined];
  ruleOverrideChanged: [field: 'durationSeconds' | 'passRatio' | 'passScore' | 'questionOrderMode' | 'multiSelectScoring'];
}>();
const responsiblePickerVisible = ref(false);
const responsibleUser = ref<PersonPickerItem>();
const coverUploadVisible = ref(false);
const coverFileName = ref('');
const coverPreviewUrl = ref('');
const drawRuleDialogVisible = ref(false);
const needsPaperVersion = computed(() => form.value.examType === 'ONLINE' || form.value.examType === 'SPECIAL_PRACTICE');
const needsQuestionBank = computed(() => form.value.examType === 'BANK_PRACTICE');
const hasPaperVersion = computed(() => needsPaperVersion.value && form.value.paperVersionId !== undefined && form.value.paperVersionId !== null);
const needsManualRules = computed(() => !hasPaperVersion.value && !needsQuestionBank.value);
/** 题库练习不产生及格结论，不展示及格比例和及格分数。 */
const needsPassingRules = computed(() => !needsQuestionBank.value);
/** 将考试开放时间范围映射回既有考试请求字段。 */
const openTimeRange = computed({
  get: (): [string, string] => [form.value.openStartTime ?? '', form.value.openEndTime ?? ''],
  set: (value: [string, string] | null) => {
    const [openStartTime, openEndTime] = value ?? [];
    form.value.openStartTime = openStartTime || undefined;
    form.value.openEndTime = openEndTime || undefined;
  }
});
/** 将补考时间范围映射回嵌套策略字段，避免改变策略请求结构。 */
const retakeTimeRange = computed({
  get: (): [string, string] => [ensurePolicy().retakeStartTime ?? '', ensurePolicy().retakeEndTime ?? ''],
  set: (value: [string, string] | null) => {
    const [retakeStartTime, retakeEndTime] = value ?? [];
    ensurePolicy().retakeStartTime = retakeStartTime || undefined;
    ensurePolicy().retakeEndTime = retakeEndTime || undefined;
  }
});

/** 初始化可选策略，并补齐考试创建时管理端需要展示的默认开关。 */
function ensurePolicy(): ExamPolicyRequest {
  const policy = form.value.policy ?? (form.value.policy = {});
  if (policy.isPcAllowed === undefined) policy.isPcAllowed = 1;
  if (policy.isAppAllowed === undefined) policy.isAppAllowed = 0;
  if (policy.isH5Allowed === undefined) policy.isH5Allowed = 1;
  if (policy.isAnalysisVisible === undefined) policy.isAnalysisVisible = 1;
  if (policy.isScoreVisible === undefined) policy.isScoreVisible = 1;
  if (policy.isRetakeEnabled === undefined) policy.isRetakeEnabled = 1;
  if (policy.retakeMaxCount === undefined) policy.retakeMaxCount = 3;
  if (policy.answerDetailMode === undefined) policy.answerDetailMode = 'PUBLISHED';
  return policy;
}

/** 开启补考时补齐契约约定的默认补考范围，避免保存空规则。 */
function ensureRetakeScope(enabled: string | number | boolean): void {
  if (enabled === 1 && !ensurePolicy().retakeScope) ensurePolicy().retakeScope = 'FAILED';
}

/** 当前发布版本就是创建考试所需的 paperVersionId。 */
function paperLabel(paper: PaperVersionOption): string {
  return `${paper.paperName}（版本 ${paper.versionNo}）`;
}

/** 负责人只取当前所属机构内的一个在职人员。 */
function selectResponsible(items: PersonPickerItem[]): void {
  const [person] = items;
  if (!person) return;
  form.value.responsibleUserId = person.userId;
  responsibleUser.value = person;
}

/** 编辑既有考试时按负责人 ID 回读姓名，避免信息页仅显示无法识别的内部编号。 */
async function loadResponsibleUser(): Promise<void> {
  const responsibleUserId = form.value.responsibleUserId;
  const organizationId = userStore.userInfo?.organizationId;
  if (!responsibleUserId || !organizationId) return;
  const page = await getPersonPickerPage({ pageNum: 1, pageSize: 100, 'query.organizationId': organizationId, 'query.status': 1 });
  responsibleUser.value = page.list.find((item) => item.userId === responsibleUserId);
}

/** 选择试卷版本时只通知上层读取规则预览；分类和封面由创建接口按契约处理。 */
function selectPaperVersion(paperVersionId?: number): void {
  coverFileName.value = '';
  emit('paperVersionSelected', paperVersionId);
}

/** 考试类型切换时清理失效资源，避免线上考试带题库或题库练习带试卷。 */
function handleExamTypeChange(): void {
  if (!needsPaperVersion.value && form.value.paperVersionId !== undefined && form.value.paperVersionId !== null) {
    form.value.paperVersionId = undefined;
    selectPaperVersion(undefined);
  }
  if (!needsQuestionBank.value) {
    form.value.questionBankId = undefined;
    form.value.drawRules = undefined;
  }
}

/** 题库练习只需核对抽题数量，不计算正式分数。 */
const drawRuleCount = computed(() => (form.value.drawRules ?? []).reduce((total, rule) => total + (rule.drawCount || 0), 0));

/** 确认弹窗草稿后才写回表单，取消配置不会污染已保存的规则。 */
function confirmDrawRules(rules: ExamDrawRulePayload[]): void {
  form.value.drawRules = rules;
}

/** 无题库时不允许打开规则弹窗，避免用户在未知候选池上配置。 */
function openDrawRuleDialog(): void {
  if (!form.value.questionBankId) return;
  drawRuleDialogVisible.value = true;
}

/** 标记管理员主动覆盖的试卷规则，保存时才将对应字段发送给后端。 */
function markRuleOverride(field: 'durationSeconds' | 'passRatio' | 'passScore' | 'questionOrderMode' | 'multiSelectScoring'): void {
  emit('ruleOverrideChanged', field);
}

/** 复用统一文件上传 Contract 上传考试封面。 */
function requestCoverUpload(file: globalThis.File, onProgress: (percent: number) => void): Promise<UnifiedUploadedFile> {
  return uploadAssetFile(file, onProgress, 'exam-covers');
}

/** 将上传结果的 fileInfoId 写入考试封面字段。 */
function handleCoverUploadSuccess(files: UnifiedUploadedFile[]): void {
  const fileInfo = files[0];
  if (!fileInfo) return;
  form.value.coverFileId = fileInfo.fileInfoId;
  coverFileName.value = fileInfo.fileName;
  // 使用本地文件生成即时预览，避免额外请求文件地址。
  if (coverPreviewUrl.value.startsWith('blob:')) globalThis.URL.revokeObjectURL(coverPreviewUrl.value);
  coverPreviewUrl.value = fileInfo.sourceFile ? globalThis.URL.createObjectURL(fileInfo.sourceFile) : '';
  coverUploadVisible.value = false;
}

watch(
  () => form.value.responsibleUserId,
  () => void loadResponsibleUser(),
  { immediate: true }
);

</script>

<template>
  <ElForm label-width="150px" class="admin-detail-form">
    <section class="ws-form-section">
      <div class="ws-form-section__head">
        <span class="ws-form-section__index">01</span>
        <div>
          <strong>基础信息与考试规则</strong>
          <p>考试身份、资源绑定、开放时间与作答规则</p>
        </div>
      </div>
      <ElRow :gutter="24">
      <ElCol :span="12">
        <ElFormItem label="考试编码" required>
          <ElInput v-model="form.examCode" :disabled="editing" placeholder="请输入考试编码" />
        </ElFormItem>
      </ElCol>
      <ElCol :span="12">
        <ElFormItem label="考试名称" required>
          <ElInput v-model="form.examName" placeholder="请输入考试名称" />
        </ElFormItem>
      </ElCol>
      <ElCol :span="12">
        <ElFormItem label="考试类型" required>
          <ElSelect v-model="form.examType" :disabled="editing" placeholder="请选择考试类型" @change="handleExamTypeChange">
            <ElOption v-for="item in EXAM_TYPE_OPTIONS" :key="item.value" :label="item.label" :value="item.value" />
          </ElSelect>
        </ElFormItem>
      </ElCol>
      <ElCol v-if="needsPaperVersion" :span="12">
        <ElFormItem label="试卷版本">
          <ElSelect v-model="form.paperVersionId" :disabled="false" clearable placeholder="请选择已发布试卷版本" @change="selectPaperVersion">
            <ElOption v-for="paper in paperOptions" :key="paper.paperVersionId" :label="paperLabel(paper)" :value="paper.paperVersionId" />
          </ElSelect>
        </ElFormItem>
      </ElCol>
      <ElCol :span="12">
        <ElFormItem label="考试分类">
          <ElSelect v-model="form.examCategoryId" clearable placeholder="请选择题库分类">
            <ElOption v-for="item in props.categoryOptions" :key="item.bankCategoryId" :label="item.categoryName" :value="item.bankCategoryId" />
          </ElSelect>
        </ElFormItem>
      </ElCol>
      <ElCol :span="12">
        <ElFormItem label="考试封面" class="exam-cover-form-item">
          <div class="exam-cover-field">
            <ElImage
              v-if="coverPreviewUrl"
              :src="coverPreviewUrl"
              :preview-src-list="[coverPreviewUrl]"
              fit="cover"
              class="exam-cover-preview"
              preview-teleported
            />
            <div v-else class="exam-cover-placeholder">
              <ElIcon><Picture /></ElIcon>
              <span>暂无封面</span>
            </div>
            <div class="exam-cover-actions">
              <ElButton type="primary" plain @click="coverUploadVisible = true">
                {{ form.coverFileId ? '更换封面' : '上传封面' }}
              </ElButton>
              <span v-if="form.coverFileId" class="exam-cover-name" :title="coverFileName || '已设置考试封面'">
                {{ coverFileName || '已设置考试封面' }}
              </span>
              <span v-else class="exam-cover-tip">JPG、PNG、WebP，最大 10 MiB</span>
              <span v-if="coverPreviewUrl" class="exam-cover-tip">点击图片可放大预览</span>
            </div>
          </div>
        </ElFormItem>
      </ElCol>
      <ElCol v-if="needsQuestionBank" :span="12">
        <ElFormItem label="题库">
          <ElSelect v-model="form.questionBankId" clearable filterable placeholder="请选择题库">
            <ElOption v-for="bank in props.questionBankOptions" :key="bank.questionBankId" :label="bank.bankName" :value="bank.questionBankId" />
          </ElSelect>
        </ElFormItem>
      </ElCol>
      <ElCol v-if="needsQuestionBank" :span="24">
        <ElFormItem label="抽题规则">
          <div class="draw-rule-editor">
            <div class="draw-rule-editor__head">
              <span>{{ form.drawRules?.length ?? 0 }} 条规则 · 合计抽取 {{ drawRuleCount }} 题</span>
              <ElButton type="primary" plain size="small" :disabled="!form.questionBankId" @click="openDrawRuleDialog">{{ form.drawRules?.length ? '查看/修改规则' : '设置抽题规则' }}</ElButton>
            </div>
            <p class="draw-rule-editor__tip">请先选择题库，再在弹窗中按可用题量设置规则；开考时以当次抽题结果冻结题目。</p>
            <ElAlert
              v-if="!(form.drawRules?.length)"
              type="warning"
              show-icon
              :closable="false"
              title="尚未配置抽题规则，题库练习至少需要一条规则，否则发布会被拒绝。"
            />
          </div>
        </ElFormItem>
      </ElCol>
      <ElCol :span="12">
        <ElFormItem label="开放时间">
          <ElDatePicker
            v-model="openTimeRange"
            type="datetimerange"
            value-format="YYYY-MM-DDTHH:mm:ss"
            range-separator="至"
            start-placeholder="开始时间"
            end-placeholder="结束时间"
          />
        </ElFormItem>
      </ElCol>
      <ElCol :span="12">
        <ElFormItem label="考试负责人">
          <ElButton @click="responsiblePickerVisible = true">
            {{ responsibleUser ? `已选：${responsibleUser.userName}` : '选择机构人员' }}
          </ElButton>
          <ElText class="hint">负责人是考试归属人，不等同于组织确认人。</ElText>
        </ElFormItem>
      </ElCol>
      <ElCol :span="12">
        <ElFormItem label="学分" required>
          <ElInputNumber v-model="form.credit" :min="0" />
        </ElFormItem>
      </ElCol>
      <template v-if="hasPaperVersion">
        <ElCol :span="12">
          <ElFormItem label="考试时长">
            <DurationInput v-model="form.durationSeconds" @change="markRuleOverride('durationSeconds')" />
            <ElText class="hint">默认考试时长由所选试卷版本带入，可按需调整。</ElText>
          </ElFormItem>
        </ElCol>
        <ElCol :span="12">
          <ElFormItem label="及格比例">
            <ElInputNumber v-model="form.passRatio" :min="0" :max="1" :step="0.1" @change="markRuleOverride('passRatio')" />
          </ElFormItem>
        </ElCol>
        <ElCol :span="12">
          <ElFormItem label="及格分数">
            <ElInputNumber v-model="form.passScore" :min="0" @change="markRuleOverride('passScore')" />
          </ElFormItem>
        </ElCol>
        <ElCol :span="12">
          <ElFormItem label="题目顺序">
            <ElSelect v-model="form.questionOrderMode" placeholder="请选择题目顺序" @change="markRuleOverride('questionOrderMode')">
              <ElOption v-for="item in QUESTION_ORDER_MODE_OPTIONS" :key="item.value" :label="item.label" :value="item.value" />
            </ElSelect>
          </ElFormItem>
        </ElCol>
        <ElCol :span="12">
          <ElFormItem label="多选题计分">
            <ElSelect v-model="form.multiSelectScoring" placeholder="请选择多选题计分方式" @change="markRuleOverride('multiSelectScoring')">
              <ElOption v-for="item in MULTI_SELECT_SCORING_OPTIONS" :key="item.value" :label="item.label" :value="item.value" />
            </ElSelect>
          </ElFormItem>
        </ElCol>
      </template>
      <template v-else>
        <ElCol :span="12">
          <ElFormItem label="考试时长" :required="needsManualRules">
            <DurationInput v-model="form.durationSeconds" @change="markRuleOverride('durationSeconds')" />
          </ElFormItem>
        </ElCol>
        <ElCol v-if="needsPassingRules" :span="12">
          <ElFormItem label="及格比例" :required="needsManualRules">
            <ElInputNumber v-model="form.passRatio" :min="0" :max="1" :step="0.1" @change="markRuleOverride('passRatio')" />
          </ElFormItem>
        </ElCol>
        <ElCol v-if="needsPassingRules" :span="12">
          <ElFormItem label="及格分数" :required="needsManualRules">
            <ElInputNumber v-model="form.passScore" :min="0" />
          </ElFormItem>
        </ElCol>
        <ElCol :span="12">
          <ElFormItem label="题目顺序" :required="needsManualRules">
            <ElSelect v-model="form.questionOrderMode" placeholder="请选择题目顺序" @change="markRuleOverride('questionOrderMode')">
              <ElOption v-for="item in QUESTION_ORDER_MODE_OPTIONS" :key="item.value" :label="item.label" :value="item.value" />
            </ElSelect>
          </ElFormItem>
        </ElCol>
        <ElCol :span="12">
          <ElFormItem label="多选题计分" :required="needsManualRules">
            <ElSelect v-model="form.multiSelectScoring" placeholder="请选择多选题计分方式" @change="markRuleOverride('multiSelectScoring')">
              <ElOption v-for="item in MULTI_SELECT_SCORING_OPTIONS" :key="item.value" :label="item.label" :value="item.value" />
            </ElSelect>
          </ElFormItem>
        </ElCol>
      </template>
      <ElCol :span="12">
        <ElFormItem label="考试须知">
          <ElSwitch v-model="form.isNoticeEnabled" :active-value="1" :inactive-value="0" />
        </ElFormItem>
      </ElCol>
      <ElCol v-if="form.isNoticeEnabled" :span="24">
        <ElFormItem label="须知内容" class="admin-detail-form__wide">
          <ElInput v-model="form.noticeContent" type="textarea" :rows="3" placeholder="请输入考试须知" />
        </ElFormItem>
      </ElCol>
      <ElCol :span="12">
        <ElFormItem label="启用状态" required>
          <ElSwitch v-model="form.status" :active-value="1" :inactive-value="0" />
        </ElFormItem>
      </ElCol>
    </ElRow>
    </section>

    <section class="ws-form-section">
      <div class="ws-form-section__head">
        <span class="ws-form-section__index">02</span>
        <div>
          <strong>考试策略</strong>
          <p>作答终端、补考与成绩展示口径</p>
        </div>
      </div>
      <ElRow :gutter="24">
      <ElCol :span="12">
        <ElFormItem label="允许作答端">
          <ElCheckbox v-model="ensurePolicy().isPcAllowed" :true-value="1" :false-value="0">PC</ElCheckbox>
          <ElCheckbox v-model="ensurePolicy().isAppAllowed" :true-value="1" :false-value="0">App</ElCheckbox>
          <ElCheckbox v-model="ensurePolicy().isH5Allowed" :true-value="1" :false-value="0">H5</ElCheckbox>
        </ElFormItem>
      </ElCol>
      <ElCol :span="12">
        <ElFormItem label="允许补考">
          <ElSwitch v-model="ensurePolicy().isRetakeEnabled" :active-value="1" :inactive-value="0" @change="ensureRetakeScope" />
        </ElFormItem>
      </ElCol>
      <template v-if="ensurePolicy().isRetakeEnabled">
        <ElCol :span="12">
          <ElFormItem label="补考范围">
            <ElSelect v-model="ensurePolicy().retakeScope" placeholder="请选择补考范围">
              <ElOption v-for="item in RETAKE_SCOPE_OPTIONS" :key="item.value" :label="item.label" :value="item.value" />
            </ElSelect>
            <ElText class="hint">默认仅不及格人员可补考。</ElText>
          </ElFormItem>
        </ElCol>
        <ElCol :span="12">
          <ElFormItem label="补考时间">
            <ElDatePicker
              v-model="retakeTimeRange"
              type="datetimerange"
              value-format="YYYY-MM-DDTHH:mm:ss"
              range-separator="至"
              start-placeholder="开始时间"
              end-placeholder="结束时间"
            />
          </ElFormItem>
        </ElCol>
        <ElCol :span="12">
          <ElFormItem label="最大补考次数">
            <ElInputNumber v-model="ensurePolicy().retakeMaxCount" :min="0" />
            <ElText class="hint">不含首次考试。</ElText>
          </ElFormItem>
        </ElCol>
        <ElCol :span="12">
          <ElFormItem label="补考成绩方式">
            <ElSelect v-model="ensurePolicy().retakeScoreMode" clearable placeholder="请选择补考成绩方式">
              <ElOption v-for="item in RETAKE_SCORE_MODE_OPTIONS" :key="item.value" :label="item.label" :value="item.value" />
            </ElSelect>
          </ElFormItem>
        </ElCol>
      </template>
      <ElCol :span="12">
        <ElFormItem label="答案详情展示方式">
          <ElSelect v-model="ensurePolicy().answerDetailMode" clearable placeholder="请选择答案详情展示方式">
            <ElOption v-for="item in ANSWER_DETAIL_MODE_OPTIONS" :key="item.value" :label="item.label" :value="item.value" />
          </ElSelect>
        </ElFormItem>
      </ElCol>
      <ElCol :span="12">
        <ElFormItem label="展示试题解析">
          <ElSwitch v-model="ensurePolicy().isAnalysisVisible" :active-value="1" :inactive-value="0" />
        </ElFormItem>
      </ElCol>
      <ElCol :span="12">
        <ElFormItem label="展示成绩">
          <ElSwitch v-model="ensurePolicy().isScoreVisible" :active-value="1" :inactive-value="0" />
        </ElFormItem>
      </ElCol>
    </ElRow>
    </section>

    <section class="ws-form-section">
      <div class="ws-form-section__head">
        <span class="ws-form-section__index">03</span>
        <div>
          <strong>考试防作弊</strong>
          <p>开启后记录切离答题页事件；不自动交卷</p>
        </div>
      </div>
      <ElRow :gutter="24">
        <ElCol :span="12">
          <ElFormItem label="启用考试防作弊">
            <ElSwitch v-model="ensurePolicy().isAnticheatEnabled" :active-value="1" :inactive-value="0" />
          </ElFormItem>
        </ElCol>
        <ElCol :span="12">
          <ElFormItem label="水印">
            <ElSwitch v-model="ensurePolicy().isWatermarkEnabled" :active-value="1" :inactive-value="0" />
          </ElFormItem>
        </ElCol>
      </ElRow>
    </section>
  </ElForm>
  <PersonPickerDialog
    v-model:visible="responsiblePickerVisible"
    :organization-id="userStore.userInfo?.organizationId"
    title="选择考试负责人"
    :preselected="responsibleUser ? [responsibleUser] : []"
    @confirm="selectResponsible"
  />
  <UnifiedFileUpload
    v-model="coverUploadVisible"
    mode="dialog"
    accept="image/jpeg,image/png,image/webp"
    :multiple="false"
    :limit="1"
    :max-file-size="10 * 1024 * 1024"
    size-limit-hint="考试封面图片不能超过 10 MiB"
    :upload-request="requestCoverUpload"
    @success="handleCoverUploadSuccess"
  />
  <ExamDrawRuleDialog v-model:visible="drawRuleDialogVisible" :question-bank-id="form.questionBankId" :rules="form.drawRules ?? []" @confirm="confirmDrawRules" />
</template>

<style scoped lang="less">
.hint {
  margin-left: 10px;
  color: var(--color-text-secondary);
}

/* 封面预览与占位图保持同一尺寸，避免上传前后字段高度跳动。 */
.exam-cover-preview {
  width: 144px;
  height: 81px;
  overflow: hidden;
  border: 1px solid var(--color-border);
  border-radius: 8px;
  box-shadow: var(--shadow-card);
  cursor: zoom-in;
}

.exam-cover-field {
  display: flex;
  align-items: center;
  gap: 12px;
  min-height: 81px;
}

.exam-cover-placeholder {
  display: flex;
  flex: 0 0 144px;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  width: 144px;
  height: 81px;
  color: var(--el-text-color-secondary);
  font-size: 12px;
  background: var(--color-bg-subtle);
  border: 1px dashed var(--color-border);
  border-radius: 8px;
}

.exam-cover-placeholder .el-icon {
  margin-bottom: 6px;
  font-size: 22px;
}

.exam-cover-actions {
  display: flex;
  flex-direction: column;
  align-items: flex-start;
  gap: 6px;
  min-width: 0;
}

.exam-cover-name,
.exam-cover-tip {
  max-width: 150px;
  overflow: hidden;
  color: var(--el-text-color-secondary);
  font-size: 12px;
  line-height: 18px;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.exam-cover-tip {
  color: var(--el-text-color-placeholder);
}

/* 抽题规则以浅底卡聚合数量、入口与说明，避免散落在字段流中。 */
.draw-rule-editor {
  display: flex;
  flex-direction: column;
  gap: 10px;
  width: 100%;
  padding: 12px 14px;
  border: 1px solid var(--color-border);
  border-radius: 10px;
  background: var(--color-bg-subtle);
}

.draw-rule-editor__head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  color: var(--color-text-regular);
  font-size: 13px;
  font-variant-numeric: tabular-nums;
}

.draw-rule-editor__tip {
  margin: 0;
  color: var(--color-text-secondary);
  font-size: 12px;
  line-height: 1.6;
}
</style>
