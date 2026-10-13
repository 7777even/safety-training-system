<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue';
import { ElMessage, type FormInstance, type FormRules } from 'element-plus';
import { useRouter } from 'vue-router';
import { uploadAssetFile } from '@/api/asset';
import {
  createQuestionBank,
  createKnowledgePoint,
  createQuestionBankCategory,
  getKnowledgePointTree,
  getQuestionBankCategories,
  getQuestionBankPage,
  getQuestionDetail,
  getQuestionPage,
  importQuestions,
  updateQuestionBank,
  type KnowledgePoint,
  type QuestionBankCategory,
  type Question,
  type QuestionBank,
  type QuestionDifficulty,
  type QuestionStatus
} from '@/api/question';
import { DEFAULT_LIST_PAGE_SIZE, LIST_PAGE_SIZES } from '@/constants/pagination';
import UnifiedFileUpload, { type UnifiedUploadedFile } from '@/components/file-upload/UnifiedFileUpload.vue';
import { getExamEnumLabel, PUBLISH_STATUS_OPTIONS, QUESTION_DIFFICULTY_OPTIONS, QUESTION_TYPE_OPTIONS } from '@/constants/exam-options';
import { useUserStore } from '@/stores';
import { hasPermission } from '@/utils/permission';

const router = useRouter();
const userStore = useUserStore();
/** 根据 profile 按钮权限控制题库中心的操作入口。 */
const can = (permission: string): boolean => hasPermission(userStore.userInfo, permission);

const loading = ref(false);
const questionLoading = ref(false);
const bankRows = ref<QuestionBank[]>([]);
const questionRows = ref<Question[]>([]);
const total = ref(0);
const questionTotal = ref(0);
const pageNum = ref(1);
const questionPageNum = ref(1);
const pageSize = ref(DEFAULT_LIST_PAGE_SIZE);
const questionPageSize = ref(DEFAULT_LIST_PAGE_SIZE);
const selectedBankId = ref<number>();
const selectedQuestionId = ref<number>();
const selectedQuestion = ref<Question>();
const selectedQuestionLoading = ref(false);
const selectedQuestionError = ref('');
const previewDialogVisible = ref(false);
const knowledgePoints = ref<KnowledgePoint[]>([]);
const bankCategories = ref<QuestionBankCategory[]>([]);
const selectedKnowledgePointId = ref<number>();
const selectedCategoryId = ref<number>();
const categoryBankRows = ref<QuestionBank[]>([]);
const bankDialogVisible = ref(false);
const editingBankId = ref<number>();
const importDialogVisible = ref(false);
const importUploadVisible = ref(false);
const knowledgeDialogVisible = ref(false);
const categoryDialogVisible = ref(false);
const knowledgeFormRef = ref<FormInstance>();
const categoryFormRef = ref<FormInstance>();
const bankFormRef = ref<FormInstance>();
const bankName = ref('');
const importRowsText = ref('[]');
const importSourceFileId = ref<number>();
const importSourceFileName = ref('');
const bankForm = reactive({
  bankCode: '',
  bankName: '',
  bankCategoryId: undefined as number | undefined,
  difficulty: 'EASY' as QuestionDifficulty,
  questionOrderMode: 'TYPE',
  status: 1 as QuestionStatus,
  version: 0,
  knowledgePointIds: [] as number[]
});
const knowledgeForm = reactive({
  parentId: 0,
  knowledgeCode: '',
  knowledgeName: '',
  sortOrder: 1,
  status: 1 as const
});
const categoryForm = reactive({
  categoryName: '',
  description: '',
  sortOrder: 1,
  status: 1 as const
});
/** 知识点新增必填规则，与 KnowledgePointCreateRequest.required 保持一致。 */
const knowledgeRules: FormRules<typeof knowledgeForm> = {
  knowledgeCode: [{ required: true, message: '请输入知识点编码', trigger: 'blur' }],
  knowledgeName: [{ required: true, message: '请输入知识点名称', trigger: 'blur' }]
};
/** 题库分类 Contract 暂无分类编码，校验已定义且必填的名称与描述。 */
const categoryRules: FormRules<typeof categoryForm> = {
  categoryName: [{ required: true, message: '请输入分类名称', trigger: 'blur' }],
  description: [{ required: true, message: '请输入分类描述', trigger: 'blur' }]
};
/** 题库编码仅在新增时必填；编辑时编码由服务端保持不可变，因此不参与校验。 */
const bankRules = computed<FormRules<typeof bankForm>>(() => ({
  bankCode: editingBankId.value ? [] : [{ required: true, whitespace: true, message: '请输入题库编码', trigger: 'blur' }],
  bankName: [{ required: true, whitespace: true, message: '请输入题库名称', trigger: 'blur' }]
}));
function errorMessage(error: unknown): string {
  return error instanceof Error ? error.message : '请求失败，请稍后重试。';
}

/** 将题目启停状态转换为中文，未知 Contract 枚举保留原值便于联调识别。 */
function questionStatusText(status?: number): string {
  if (status === 1) return '启用';
  if (status === 0) return '停用';
  return '—';
}

/** 将题目归档状态转换为中文，避免直接向用户展示 Contract 枚举值。 */
function questionArchiveStatusText(status?: string): string {
  if (!status) return '—';
  if (status === 'ACTIVE') return '正常';
  if (status === 'ARCHIVED') return '已归档';
  return status;
}

/** 当前左侧选中的知识点及其直接下级，供右侧详情区展示。 */
const selectedKnowledgePoint = computed(() => findKnowledgePoint(knowledgePoints.value, selectedKnowledgePointId.value));
const selectedKnowledgeChildren = computed(() => selectedKnowledgePoint.value?.children ?? []);
/** 增加“顶层知识点”虚拟节点，使根节点和任意父节点都可明确选择。 */
const knowledgeParentOptions = computed(() => [{ knowledgePointId: 0, knowledgeName: '顶层知识点', children: knowledgePoints.value }]);

async function loadBanks(): Promise<void> {
  loading.value = true;
  try {
    const data = await getQuestionBankPage({
      pageNum: pageNum.value,
      pageSize: pageSize.value,
      ...(bankName.value ? { 'query.bankName': bankName.value } : {})
    });
    bankRows.value = data.list;
    total.value = data.total;
    if (!selectedBankId.value && data.list[0]) {
      selectedBankId.value = data.list[0]?.questionBankId;
      await loadQuestions();
    }
  } finally {
    loading.value = false;
  }
}

async function loadReferenceData(): Promise<void> {
  [knowledgePoints.value, bankCategories.value] = await Promise.all([getKnowledgePointTree(), getQuestionBankCategories()]);
  selectedKnowledgePointId.value ??= knowledgePoints.value[0]?.knowledgePointId;
  selectedCategoryId.value ??= bankCategories.value[0]?.bankCategoryId;
  await loadCategoryBanks();
}

/** 在树中按 ID 查找节点，避免模板递归处理。 */
function findKnowledgePoint(nodes: KnowledgePoint[], knowledgePointId?: number): KnowledgePoint | undefined {
  for (const node of nodes) {
    if (node.knowledgePointId === knowledgePointId) return node;
    const matched = findKnowledgePoint(node.children ?? [], knowledgePointId);
    if (matched) return matched;
  }
  return undefined;
}

function selectKnowledgePoint(node: KnowledgePoint): void {
  selectedKnowledgePointId.value = node.knowledgePointId;
}

async function loadCategoryBanks(): Promise<void> {
  if (!selectedCategoryId.value) {
    categoryBankRows.value = [];
    return;
  }
  const data = await getQuestionBankPage({
    pageNum: 1,
    pageSize: DEFAULT_LIST_PAGE_SIZE,
    'query.bankCategoryId': selectedCategoryId.value
  });
  categoryBankRows.value = data.list;
}

async function selectCategory(category: QuestionBankCategory | null): Promise<void> {
  if (!category) return;
  selectedCategoryId.value = category.bankCategoryId;
  await loadCategoryBanks();
}

function openKnowledgeDialog(): void {
  Object.assign(knowledgeForm, {
    parentId: selectedKnowledgePointId.value ?? 0,
    knowledgeCode: '',
    knowledgeName: '',
    sortOrder: 1,
    status: 1
  });
  knowledgeDialogVisible.value = true;
}

async function submitKnowledge(): Promise<void> {
  await knowledgeFormRef.value?.validate();
  await createKnowledgePoint(knowledgeForm);
  ElMessage.success('知识点已创建');
  knowledgeDialogVisible.value = false;
  await loadReferenceData();
}

function openCategoryDialog(): void {
  Object.assign(categoryForm, { categoryName: '', description: '', sortOrder: 1, status: 1 });
  categoryDialogVisible.value = true;
}

async function submitCategory(): Promise<void> {
  await categoryFormRef.value?.validate();
  await createQuestionBankCategory(categoryForm);
  ElMessage.success('题库分类已创建');
  categoryDialogVisible.value = false;
  await loadReferenceData();
}

async function loadQuestions(): Promise<void> {
  selectedQuestionId.value = undefined;
  selectedQuestion.value = undefined;
  selectedQuestionError.value = '';
  previewDialogVisible.value = false;
  if (!selectedBankId.value) {
    questionRows.value = [];
    questionTotal.value = 0;
    return;
  }
  questionLoading.value = true;
  try {
    const data = await getQuestionPage({
      pageNum: questionPageNum.value,
      pageSize: questionPageSize.value,
      'query.questionBankId': selectedBankId.value
    });
    questionRows.value = data.list;
    questionTotal.value = data.total;
  } finally {
    questionLoading.value = false;
  }
}

function asQuestionBank(row: unknown): QuestionBank {
  return row as QuestionBank;
}

function asQuestion(row: unknown): Question {
  return row as Question;
}

/** 仅记录列表选中项，避免用户浏览列表时为整页题目逐条预加载详情。 */
function selectQuestionRow(question: Question | null): void {
  selectedQuestionId.value = question?.questionId;
}

/** 用户点击预览后才获取详情，并在独立弹窗中展示完整内容。 */
async function selectQuestion(question: Question | null): Promise<void> {
  if (!question) return;
  selectedQuestionId.value = question.questionId;
  selectedQuestion.value = undefined;
  selectedQuestionLoading.value = true;
  selectedQuestionError.value = '';
  previewDialogVisible.value = true;
  try {
    selectedQuestion.value = await getQuestionDetail(question.questionId);
  } catch (error) {
    selectedQuestion.value = undefined;
    selectedQuestionError.value = errorMessage(error);
  } finally {
    selectedQuestionLoading.value = false;
  }
}

async function selectBank(bank: QuestionBank | null): Promise<void> {
  if (!bank) return;
  selectedBankId.value = bank.questionBankId;
  questionPageNum.value = 1;
  await loadQuestions();
}

function openBankDialog(): void {
  editingBankId.value = undefined;
  Object.assign(bankForm, {
    bankCode: '',
    bankName: '',
    bankCategoryId: undefined,
    difficulty: 'EASY',
    questionOrderMode: 'TYPE',
    status: 1,
    version: 0,
    knowledgePointIds: []
  });
  bankDialogVisible.value = true;
}

/** 打开编辑题库弹窗，回显题库分类、默认难度与已关联知识点，供管理员重新关联。 */
function openEditBankDialog(bank: QuestionBank): void {
  editingBankId.value = bank.questionBankId;
  Object.assign(bankForm, {
    bankCode: bank.bankCode ?? '',
    bankName: bank.bankName,
    bankCategoryId: bank.bankCategoryId,
    difficulty: bank.difficulty ?? 'EASY',
    questionOrderMode: bank.questionOrderMode ?? 'TYPE',
    status: bank.status ?? 1,
    version: bank.version ?? 0,
    knowledgePointIds: [...(bank.knowledgePointIds ?? [])]
  });
  bankDialogVisible.value = true;
}

async function submitBank(): Promise<void> {
  await bankFormRef.value?.validate();
  if (editingBankId.value) {
    await updateQuestionBank(editingBankId.value, {
      bankName: bankForm.bankName.trim(),
      bankCategoryId: bankForm.bankCategoryId,
      difficulty: bankForm.difficulty,
      questionOrderMode: bankForm.questionOrderMode,
      status: bankForm.status,
      version: bankForm.version,
      knowledgePointIds: [...bankForm.knowledgePointIds]
    });
    ElMessage.success('题库已更新');
  } else {
    await createQuestionBank({
      bankCode: bankForm.bankCode.trim(),
      bankName: bankForm.bankName.trim(),
      bankCategoryId: bankForm.bankCategoryId,
      difficulty: bankForm.difficulty,
      questionOrderMode: bankForm.questionOrderMode,
      status: bankForm.status,
      knowledgePointIds: [...bankForm.knowledgePointIds]
    });
    ElMessage.success('题库已创建');
  }
  bankDialogVisible.value = false;
  await loadBanks();
}

/** 题库分类名称用于列表回显，分类缺失时保持占位而不显示主键。 */
function bankCategoryName(bankCategoryId?: number): string {
  if (!bankCategoryId) return '未分类';
  return bankCategories.value.find((item) => item.bankCategoryId === bankCategoryId)?.categoryName ?? '—';
}

function openCreateQuestionPage(): void {
  if (!selectedBankId.value) {
    ElMessage.warning('请先选择题库');
    return;
  }
  void router.push('/admin/business-center/exams/question/create');
}

function openQuestionDetail(row: Question): void {
  void router.push(`/admin/business-center/exams/question/${row.questionId}`);
}

function openImportDialog(): void {
  if (!selectedBankId.value) {
    ElMessage.warning('请先选择题库');
    return;
  }
  importRowsText.value = '[]';
  importSourceFileId.value = undefined;
  importSourceFileName.value = '';
  importDialogVisible.value = true;
}

/** 上传题目导入源文件，并保存 Contract 所需的 fileInfoId。 */
function requestImportFileUpload(file: globalThis.File, onProgress: (percent: number) => void): Promise<UnifiedUploadedFile> {
  return uploadAssetFile(file, onProgress, 'question-imports');
}

/** 把统一上传结果映射到 sourceFileId，禁止手工录入文件主键。 */
function handleImportFileSuccess(files: UnifiedUploadedFile[]): void {
  const fileInfo = files[0];
  if (!fileInfo) return;
  importSourceFileId.value = fileInfo.fileInfoId;
  importSourceFileName.value = fileInfo.fileName;
  importUploadVisible.value = false;
}

async function submitImport(): Promise<void> {
  if (!selectedBankId.value) return;
  if (!importSourceFileId.value) {
    ElMessage.warning('请选择并上传导入文件');
    return;
  }
  let rows: unknown;
  try {
    rows = JSON.parse(importRowsText.value);
  } catch {
    return;
  }
  if (!Array.isArray(rows)) {
    ElMessage.error('导入题目失败：导入内容必须是题目数组');
    return;
  }
  const result = await importQuestions(selectedBankId.value, {
    sourceFileId: importSourceFileId.value,
    rows: rows as []
  });
  ElMessage.success(`导入完成：成功 ${result.successRows} 行，失败 ${result.failureRows} 行`);
  importDialogVisible.value = false;
  await loadQuestions();
}

async function searchBanks(): Promise<void> {
  pageNum.value = 1;
  await loadBanks();
}

onMounted(async () => {
  await Promise.all([loadBanks(), loadReferenceData()]);
});
</script>

<template>
  <section class="admin-business-page question-bank-page">
    <div class="master-detail-layout admin-business-page__table">
      <ElCard shadow="never" class="admin-business-page__table master-panel">
        <template #header>
          <div class="admin-business-page__table-heading">
            <strong>题库列表</strong>
            <div class="admin-business-page__table-actions">
              <ElButton v-if="can('question:knowledge:list')" @click="openKnowledgeDialog">知识点树</ElButton>
              <ElButton v-if="can('question:bank:list')" @click="openCategoryDialog">题库分类</ElButton>
              <ElButton v-if="can('question:bank:add')" type="primary" @click="openBankDialog">新增题库</ElButton>
            </div>
          </div>
        </template>
        <ElForm inline>
          <ElFormItem label="题库名称">
            <ElInput v-model="bankName" placeholder="请输入题库名称" clearable @keyup.enter="searchBanks" />
          </ElFormItem>
          <ElFormItem>
            <ElButton type="primary" @click="searchBanks">查询</ElButton>
            <ElButton
              @click="
                bankName = '';
                searchBanks();
              "
            >
              重置
            </ElButton>
          </ElFormItem>
        </ElForm>
        <ElTable
          v-loading="loading"
          :data="bankRows"
          row-key="questionBankId"
          height="calc(100% - 105px)"
          :current-row-key="selectedBankId"
          highlight-current-row
        >
          <ElTableColumn prop="bankCode" label="题库编码" min-width="130" />
          <ElTableColumn prop="bankName" label="题库名称" min-width="180" />
          <ElTableColumn label="题库分类" min-width="120">
            <template #default="{ row }">{{ bankCategoryName(asQuestionBank(row).bankCategoryId) }}</template>
          </ElTableColumn>
          <ElTableColumn label="关联知识点" width="110" align="center">
            <template #default="{ row }">{{ asQuestionBank(row).knowledgePointIds?.length ?? 0 }} 个</template>
          </ElTableColumn>
          <ElTableColumn label="难度" width="80">
            <template #default="{ row }">
              {{ getExamEnumLabel(row.difficulty as QuestionDifficulty, QUESTION_DIFFICULTY_OPTIONS) }}
            </template>
          </ElTableColumn>
          <ElTableColumn label="操作" width="120" align="center" fixed="right">
            <template #default="{ row }">
              <ElButton link type="primary" @click.stop="selectBank(asQuestionBank(row))">查看题目</ElButton>
              <ElButton v-if="can('question:bank:edit')" link type="primary" @click.stop="openEditBankDialog(asQuestionBank(row))">
                编辑
              </ElButton>
            </template>
          </ElTableColumn>
        </ElTable>
        <div class="admin-business-page__pagination">
          <ElPagination
            v-model:current-page="pageNum"
            v-model:page-size="pageSize"
            :page-sizes="LIST_PAGE_SIZES"
            :total="total"
            layout="total, sizes, prev, pager, next"
            @current-change="loadBanks"
            @size-change="searchBanks"
          />
        </div>
      </ElCard>

      <ElCard shadow="never" class="detail-panel">
        <template #header>
          <div class="admin-business-page__table-heading">
            <strong>题目列表</strong>
            <div class="admin-business-page__table-actions">
              <ElButton v-if="can('question:import')" :disabled="!selectedBankId" @click="openImportDialog">批量导入</ElButton>
              <ElButton v-if="can('question:question:add')" type="primary" :disabled="!selectedBankId" @click="openCreateQuestionPage">
                新增题目
              </ElButton>
            </div>
          </div>
        </template>
        <ElEmpty v-if="!selectedBankId" description="请先从上方选择题库" />
        <template v-else>
          <ElTable
            v-loading="questionLoading"
            :data="questionRows"
            row-key="questionId"
            :current-row-key="selectedQuestionId"
            highlight-current-row
            @current-change="selectQuestionRow"
            :height="`calc(100% - 52px)`"
          >
            <ElTableColumn label="序号" width="55" align="center">
              <template #default="{ $index }">
                {{ (questionPageNum - 1) * questionPageSize + $index + 1 }}
              </template>
            </ElTableColumn>
            <ElTableColumn prop="questionCode" label="题目编码" width="90" show-overflow-tooltip/>
            <ElTableColumn label="题干" min-width="300" show-overflow-tooltip>
              <template #default="{ row }">{{ asQuestion(row).currentVersion?.stem || '—' }}</template>
            </ElTableColumn>
            <ElTableColumn label="题型" width="110">
              <template #default="{ row }">
                {{ getExamEnumLabel(asQuestion(row).currentVersion?.questionType, QUESTION_TYPE_OPTIONS) }}
              </template>
            </ElTableColumn>
            <ElTableColumn label="归档状态" width="100">
              <template #default="{ row }">
                <ElTag type="success">{{ questionArchiveStatusText(asQuestion(row).archiveStatus) }}</ElTag>
              </template>
            </ElTableColumn>
            <ElTableColumn label="启用状态" width="100">
              <template #default="{ row }">
                <ElTag :type="asQuestion(row).status === 1 ? 'success' : 'info'">
                  {{ questionStatusText(asQuestion(row).status) }}
                </ElTag>
              </template>
            </ElTableColumn>
            <ElTableColumn label="操作" width="160">
              <template #default="{ row }">
                <ElButton v-if="can('question:question:list')" link type="primary" @click="selectQuestion(asQuestion(row))">查看预览</ElButton>
                <ElButton v-if="can('question:question:list')" link type="primary" @click="openQuestionDetail(asQuestion(row))">编辑详情</ElButton>
              </template>
            </ElTableColumn>
          </ElTable>
          <div class="admin-business-page__pagination">
            <ElPagination
              v-model:current-page="questionPageNum"
              v-model:page-size="questionPageSize"
              :page-sizes="LIST_PAGE_SIZES"
              :total="questionTotal"
              layout="total, sizes, prev, pager, next"
              @current-change="loadQuestions"
              @size-change="loadQuestions"
            />
          </div>
        </template>
      </ElCard>
    </div>

    <ElDialog v-model="previewDialogVisible" title="题目预览" width="760px" destroy-on-close :lock-scroll="false">
      <ElSkeleton v-if="selectedQuestionLoading" :rows="6" animated />
      <ElAlert
        v-else-if="selectedQuestionError"
        title="题目详情加载失败"
        :description="selectedQuestionError"
        type="error"
        show-icon
        :closable="false"
      />
      <ElEmpty v-else-if="!selectedQuestion" description="暂无可预览的题目详情" :image-size="72" />
      <template v-else>
        <ElDescriptions :column="3" border size="small">
          <ElDescriptionsItem label="题目编码">{{ selectedQuestion.questionCode }}</ElDescriptionsItem>
          <ElDescriptionsItem label="当前版本">V{{ selectedQuestion.currentVersionNo ?? '—' }}</ElDescriptionsItem>
          <ElDescriptionsItem label="发布状态">
            {{ getExamEnumLabel(selectedQuestion.currentVersion?.publishedStatus, PUBLISH_STATUS_OPTIONS) }}
          </ElDescriptionsItem>
          <ElDescriptionsItem label="题型">
            {{ getExamEnumLabel(selectedQuestion.currentVersion?.questionType, QUESTION_TYPE_OPTIONS) }}
          </ElDescriptionsItem>
          <ElDescriptionsItem label="难度">
            {{ getExamEnumLabel(selectedQuestion.currentVersion?.difficulty, QUESTION_DIFFICULTY_OPTIONS) }}
          </ElDescriptionsItem>
          <ElDescriptionsItem label="启用状态">{{ questionStatusText(selectedQuestion.status) }}</ElDescriptionsItem>
        </ElDescriptions>
        <div class="question-preview__stem">
          <span>题干</span>
          <p>{{ selectedQuestion.currentVersion?.stem || '当前版本未返回题干。' }}</p>
        </div>
      </template>
      <template #footer>
        <ElButton @click="previewDialogVisible = false">关闭</ElButton>
        <ElButton v-if="selectedQuestion && can('question:question:list')" type="primary" @click="openQuestionDetail(selectedQuestion)">
          {{ selectedQuestion.currentVersion?.publishedStatus === 'PUBLISHED' ? '查看详情' : '继续编辑' }}
        </ElButton>
      </template>
    </ElDialog>

    <ElDialog
      v-model="bankDialogVisible"
      :title="editingBankId ? '编辑题库' : '新增题库'"
      width="560px"
      destroy-on-close
      :lock-scroll="false"
    >
      <p class="bank-dialog-tip">题库编码在创建后不可修改；题库分类与关联知识点可随时重新关联。</p>
      <ElForm ref="bankFormRef" :model="bankForm" :rules="bankRules" label-width="100px" class="bank-form">
        <ElFormItem label="题库编码" prop="bankCode">
          <ElInput
            v-model="bankForm.bankCode"
            placeholder="请输入题库编码"
            :disabled="Boolean(editingBankId)"
            :title="editingBankId ? '题库编码在创建后不可修改' : undefined"
          />
        </ElFormItem>
        <ElFormItem label="题库名称" prop="bankName">
          <ElInput v-model="bankForm.bankName" placeholder="请输入题库名称" maxlength="60" show-word-limit />
        </ElFormItem>
        <ElFormItem label="题库分类">
          <ElSelect v-model="bankForm.bankCategoryId" placeholder="请选择题库分类（可选）" class="bank-reference-select" clearable>
            <ElOption v-for="item in bankCategories" :key="item.bankCategoryId" :label="item.categoryName" :value="item.bankCategoryId" />
          </ElSelect>
        </ElFormItem>
        <ElFormItem label="关联知识点">
          <ElTreeSelect
            v-model="bankForm.knowledgePointIds"
            :data="knowledgePoints"
            node-key="knowledgePointId"
            multiple
            show-checkbox
            check-strictly
            :render-after-expand="false"
            :props="{ label: 'knowledgeName', children: 'children' }"
            placeholder="请选择关联知识点（可选）"
            class="bank-reference-select"
          />
        </ElFormItem>
        <ElFormItem label="默认难度">
          <ElSelect v-model="bankForm.difficulty" placeholder="请选择难度" class="bank-reference-select">
            <ElOption v-for="item in QUESTION_DIFFICULTY_OPTIONS" :key="item.value" :label="item.label" :value="item.value" />
          </ElSelect>
        </ElFormItem>
        <ElFormItem label="启用状态">
          <ElSwitch v-model="bankForm.status" :active-value="1" :inactive-value="0" active-text="启用" inactive-text="停用" />
        </ElFormItem>
      </ElForm>
      <template #footer>
        <ElButton @click="bankDialogVisible = false">取消</ElButton>
        <ElButton type="primary" @click="submitBank">{{ editingBankId ? '保存修改' : '确定创建' }}</ElButton>
      </template>
    </ElDialog>
    <ElDialog v-model="importDialogVisible" title="批量导入" width="680px" :lock-scroll="false">
      <p class="dialog-tip">请按示例格式输入 JSON 题目数组；导入结果将保留供后续查看。</p>
      <div class="import-file-row">
        <ElButton @click="importUploadVisible = true">
          {{ importSourceFileId ? '重新上传导入文件' : '选择并上传导入文件' }}
        </ElButton>
        <ElText v-if="importSourceFileId">{{ importSourceFileName || `文件 #${importSourceFileId}` }}</ElText>
      </div>
      <ElInput v-model="importRowsText" type="textarea" :rows="10" placeholder="请输入题目 JSON 数组" />
      <template #footer>
        <ElButton @click="importDialogVisible = false">取消</ElButton>
        <ElButton type="primary" @click="submitImport">提交导入</ElButton>
      </template>
    </ElDialog>
    <UnifiedFileUpload
      v-model="importUploadVisible"
      mode="dialog"
      :multiple="false"
      :limit="1"
      :upload-request="requestImportFileUpload"
      @success="handleImportFileSuccess"
    />
    <ElDialog v-model="knowledgeDialogVisible" title="知识点树" width="700px" :lock-scroll="false">
      <div class="dialog-master-detail">
        <aside class="dialog-master-panel">
          <ElTree
            :data="knowledgePoints"
            node-key="knowledgePointId"
            highlight-current
            :current-node-key="selectedKnowledgePointId"
            :props="{ label: 'knowledgeName', children: 'children' }"
            @node-click="selectKnowledgePoint"
          />
        </aside>
        <section class="dialog-detail-panel">
          <template v-if="selectedKnowledgePoint">
            <h3>{{ selectedKnowledgePoint.knowledgeName }}</h3>
            <p>编码：{{ selectedKnowledgePoint.knowledgeCode }}</p>
            <p>下级知识点：{{ selectedKnowledgeChildren.length }} 个</p>
            <ElTable v-if="selectedKnowledgeChildren.length" :data="selectedKnowledgeChildren" size="small">
              <ElTableColumn prop="knowledgeName" label="下级知识点" />
              <ElTableColumn prop="knowledgeCode" label="编码" />
            </ElTable>
          </template>
          <ElEmpty v-else description="暂无知识点" :image-size="72" />
          <ElDivider />
          <ElForm ref="knowledgeFormRef" :model="knowledgeForm" :rules="knowledgeRules" label-width="110px">
            <ElFormItem label="父级知识点">
              <ElTreeSelect
                v-model="knowledgeForm.parentId"
                :data="knowledgeParentOptions"
                node-key="knowledgePointId"
                check-strictly
                :render-after-expand="false"
                :props="{ label: 'knowledgeName', children: 'children' }"
                placeholder="请选择父级知识点"
                class="bank-reference-select"
              />
            </ElFormItem>
            <ElFormItem label="知识点编码" prop="knowledgeCode" required>
              <ElInput v-model="knowledgeForm.knowledgeCode" placeholder="请输入知识点编码" />
            </ElFormItem>
            <ElFormItem label="知识点名称" prop="knowledgeName" required>
              <ElInput v-model="knowledgeForm.knowledgeName" placeholder="请输入知识点名称" />
            </ElFormItem>
          </ElForm>
        </section>
      </div>
      <template #footer>
        <ElButton @click="knowledgeDialogVisible = false">取消</ElButton>
        <ElButton type="primary" @click="submitKnowledge">新增知识点</ElButton>
      </template>
    </ElDialog>
    <ElDialog v-model="categoryDialogVisible" title="题库分类" width="700px" :lock-scroll="false">
      <div class="dialog-master-detail">
        <aside class="dialog-master-panel">
          <ElTable
            :data="bankCategories"
            row-key="bankCategoryId"
            :current-row-key="selectedCategoryId"
            highlight-current-row
            @current-change="selectCategory"
          >
            <ElTableColumn prop="categoryName" label="分类名称" />
          </ElTable>
        </aside>
        <section class="dialog-detail-panel">
          <h3>{{ bankCategories.find((item) => item.bankCategoryId === selectedCategoryId)?.categoryName ?? '请选择题库分类' }}</h3>
          <p>{{ bankCategories.find((item) => item.bankCategoryId === selectedCategoryId)?.description ?? '选择左侧分类后查看所属题库。' }}</p>
          <ElTable :data="categoryBankRows" size="small">
            <ElTableColumn prop="bankCode" label="题库编码" />
            <ElTableColumn prop="bankName" label="所属题库" />
          </ElTable>
          <ElDivider />
          <ElForm ref="categoryFormRef" :model="categoryForm" :rules="categoryRules" label-width="90px">
            <ElFormItem label="分类名称" prop="categoryName" required>
              <ElInput v-model="categoryForm.categoryName" placeholder="请输入分类名称" />
            </ElFormItem>
            <ElFormItem label="描述" prop="description" required>
              <ElInput v-model="categoryForm.description" placeholder="请输入分类描述" />
            </ElFormItem>
          </ElForm>
        </section>
      </div>
      <template #footer>
        <ElButton @click="categoryDialogVisible = false">取消</ElButton>
        <ElButton type="primary" @click="submitCategory">新增分类</ElButton>
      </template>
    </ElDialog>
  </section>
</template>

<style scoped lang="less">
.master-detail-layout,
.dialog-master-detail {
  display: grid;
  grid-template-columns: minmax(280px, 32%) minmax(0, 1fr);
  gap: 16px;
}

.master-panel,
.dialog-master-panel {
  min-width: 0;
}

.detail-panel,
.dialog-detail-panel {
  min-width: 0;
}
.question-preview__stem {
  margin: 16px 0;
}

.question-preview__stem span {
  color: var(--color-text-secondary);
  font-size: 13px;
}

.question-preview__stem p {
  margin: 6px 0 0;
  white-space: pre-wrap;
  line-height: 1.7;
  color: var(--color-text-primary);
}

.dialog-master-panel {
  max-height: 520px;
  overflow: auto;
  padding-right: 12px;
  border-right: 1px solid var(--color-border);
}

.dialog-detail-panel h3 {
  margin: 0;
  color: var(--color-text-primary);
}

.dialog-detail-panel p {
  color: var(--color-text-secondary);
  font-size: 13px;
}

.dialog-tip {
  margin: 6px 0 0 16px;
  color: var(--color-text-secondary);
  font-size: 13px;
}

.pagination {
  display: flex;
  justify-content: flex-end;
  margin-top: var(--space-2);
}

.import-file-row {
  display: flex;
  align-items: center;
  gap: 12px;
  margin-bottom: 12px;
}

.bank-reference-select {
  width: 100%;
}

/* 新增与编辑题库共用同一弹窗，说明文字与表单保持统一的垂直节奏。 */
.bank-dialog-tip {
  margin: 0 0 var(--space-4);
  padding: 10px 12px;
  border: 1px solid var(--color-border);
  border-radius: var(--radius-control);
  background: var(--color-bg-subtle);
  color: var(--color-text-secondary);
  font-size: 13px;
  line-height: 20px;
}

.bank-form :deep(.el-form-item:last-child) {
  margin-bottom: 0;
}

@media (max-width: 960px) {
  .master-detail-layout,
  .dialog-master-detail {
    grid-template-columns: 1fr;
  }

  .dialog-master-panel {
    max-height: 240px;
    padding-right: 0;
    padding-bottom: 12px;
    border-right: 0;
    border-bottom: 1px solid var(--color-border);
  }
}
</style>
