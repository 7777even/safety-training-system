<script setup lang="ts">
import { computed, ref, watch } from 'vue';
import { ElMessage } from 'element-plus';
import {
  getQuestionBankCategories,
  getQuestionBankPage,
  getQuestionDetail,
  getQuestionPage,
  type Question,
  type QuestionBank,
  type QuestionBankCategory,
  type QuestionDifficulty,
  type QuestionType
} from '@/api/question';
import { getExamEnumLabel as getEnumLabel, QUESTION_DIFFICULTY_OPTIONS, QUESTION_TYPE_OPTIONS } from '@/constants/exam-options';
import { resolveSelectedQuestions } from './question-selection';

/** 选择器需要一次性列出可选题目，使用独立常量命名，避免与列表分页混用。 */
const PICKER_OPTION_PAGE_SIZE = 200;

const props = defineProps<{
  modelValue: boolean;
  selectedQuestionIds: number[];
  defaultBankCategoryId?: number;
}>();
const emit = defineEmits<{ 'update:modelValue': [value: boolean]; confirm: [questions: Question[]] }>();

const banks = ref<QuestionBank[]>([]);
const categories = ref<QuestionBankCategory[]>([]);
const currentCategoryId = ref<number>();
const currentBankId = ref<number>();
const questions = ref<Question[]>([]);
const bankKeyword = ref('');
const questionKeyword = ref('');
const banksLoading = ref(false);
const questionsLoading = ref(false);
/** 跨题库累计选择结果，按题目 ID 保存详情，避免切换题库后丢失已勾选题目。 */
const pickedQuestions = ref<Map<number, Question>>(new Map());
/** 按题库缓存题目列表，切换回已浏览过的题库时不再重复请求。 */
const questionCache = new Map<number, Question[]>();

/** 题库名称在服务端分类筛选结果上再做一次本地关键字过滤，切换分类后自动收敛。 */
const filteredBanks = computed(() => {
  const keyword = bankKeyword.value.trim().toLowerCase();
  if (!keyword) return banks.value;
  return banks.value.filter((bank) => bank.bankName.toLowerCase().includes(keyword));
});

/** 题目关键字同时匹配题干与题目编码，未返回 currentVersion 时按空串降级。 */
const filteredQuestions = computed(() => {
  const keyword = questionKeyword.value.trim().toLowerCase();
  if (!keyword) return questions.value;
  return questions.value.filter(
    (question) => question.questionCode.toLowerCase().includes(keyword) || (question.currentVersion?.stem ?? '').toLowerCase().includes(keyword)
  );
});

const pickedCount = computed(() => pickedQuestions.value.size);
const currentBankName = computed(() => banks.value.find((bank) => bank.questionBankId === currentBankId.value)?.bankName ?? '');
const alreadyInPaperCount = computed(() => props.selectedQuestionIds.length);

/** 判断题目是否已存在于试卷中，这类题目在弹窗内不可重复选择。 */
function isAlreadyInPaper(questionId: number): boolean {
  return props.selectedQuestionIds.includes(questionId);
}

function isPicked(questionId: number): boolean {
  return pickedQuestions.value.has(questionId);
}

/** 勾选与取消勾选只改动本地累计结果，确认时才回写试卷题目列表。 */
function togglePick(question: Question): void {
  const next = new Map(pickedQuestions.value);
  if (next.has(question.questionId)) {
    next.delete(question.questionId);
  } else {
    next.set(question.questionId, question);
  }
  pickedQuestions.value = next;
}

/** 按当前筛选结果批量勾选，已存在于试卷中的题目自动跳过。 */
function pickAllVisible(): void {
  const next = new Map(pickedQuestions.value);
  filteredQuestions.value.forEach((question) => {
    if (!isAlreadyInPaper(question.questionId)) next.set(question.questionId, question);
  });
  pickedQuestions.value = next;
}

function clearPicked(): void {
  pickedQuestions.value = new Map();
}

/** 读取指定题库的已发布、启用题目，并提供本地缓存避免重复请求。 */
async function selectBank(questionBankId: number): Promise<void> {
  currentBankId.value = questionBankId;
  const cached = questionCache.get(questionBankId);
  if (cached) {
    questions.value = cached;
    return;
  }
  questionsLoading.value = true;
  try {
    questions.value = (
      await getQuestionPage({
        pageNum: 1,
        pageSize: PICKER_OPTION_PAGE_SIZE,
        'query.questionBankId': questionBankId,
        'query.publishedStatus': 'PUBLISHED',
        'query.status': 1
      })
    ).list;
    questionCache.set(questionBankId, questions.value);
  } catch (error) {
    questions.value = [];
    ElMessage.error(error instanceof Error ? error.message : '题目加载失败，请稍后重试。');
  } finally {
    questionsLoading.value = false;
  }
}

/** 按当前分类重新加载题库，并自动定位到过滤后的第一个题库。 */
async function loadBanks(): Promise<void> {
  banksLoading.value = true;
  try {
    banks.value = (
      await getQuestionBankPage({
        pageNum: 1,
        pageSize: PICKER_OPTION_PAGE_SIZE,
        ...(currentCategoryId.value ? { 'query.bankCategoryId': currentCategoryId.value } : {}),
        'query.status': 1
      })
    ).list;
  } catch (error) {
    banks.value = [];
    ElMessage.error(error instanceof Error ? error.message : '题库加载失败，请稍后重试。');
  } finally {
    banksLoading.value = false;
  }
  questionCache.clear();
  questions.value = [];
  currentBankId.value = undefined;
  const firstBank = filteredBanks.value[0];
  if (firstBank) await selectBank(firstBank.questionBankId);
}

/** 题库分类变化时清空题库关键字并重新加载，避免旧关键字过滤掉新分类的题库。 */
async function changeCategory(): Promise<void> {
  bankKeyword.value = '';
  await loadBanks();
}

/** 只需切换右侧题目列表，不重复请求题库列表。 */
async function changeBank(questionBankId: number): Promise<void> {
  await selectBank(questionBankId);
}

/** 题库名称关键字变化后，若当前题库已被过滤掉则回落到首个可见题库。 */
async function handleBankKeywordChange(): Promise<void> {
  if (filteredBanks.value.some((bank) => bank.questionBankId === currentBankId.value)) return;
  const firstBank = filteredBanks.value[0];
  if (firstBank) {
    await selectBank(firstBank.questionBankId);
    return;
  }
  currentBankId.value = undefined;
  questions.value = [];
}

/** 重新加载分类与题库，并把试卷分类作为默认筛选条件。 */
async function loadReferenceData(): Promise<void> {
  currentCategoryId.value = props.defaultBankCategoryId;
  bankKeyword.value = '';
  questionKeyword.value = '';
  clearPicked();
  try {
    categories.value = await getQuestionBankCategories();
  } catch (error) {
    categories.value = [];
    ElMessage.error(error instanceof Error ? error.message : '题库分类加载失败，请稍后重试。');
  }
  await loadBanks();
}

/** 确认前补齐列表未返回的当前版本，确保编辑器可写入 questionVersionId。 */
async function confirm(): Promise<void> {
  const picked = [...pickedQuestions.value.values()];
  if (!picked.length) {
    ElMessage.warning('请至少选择一道题目');
    return;
  }
  emit(
    'confirm',
    await resolveSelectedQuestions(
      picked,
      picked.map((item) => item.questionId),
      getQuestionDetail
    )
  );
  emit('update:modelValue', false);
}

watch(
  () => props.modelValue,
  (visible) => {
    if (visible) void loadReferenceData();
  },
  { immediate: true }
);
</script>

<template>
  <ElDialog
    :model-value="modelValue"
    title="从题库添加题目"
    width="1120px"
    top="6vh"
    destroy-on-close
    :lock-scroll="false"
    class="question-picker"
    @update:model-value="emit('update:modelValue', $event)"
  >
    <div class="picker-filter">
      <ElSelect v-model="currentCategoryId" class="picker-filter__select" clearable placeholder="全部题库分类" @change="changeCategory">
        <ElOption v-for="item in categories" :key="item.bankCategoryId" :label="item.categoryName" :value="item.bankCategoryId" />
      </ElSelect>
      <ElInput v-model="bankKeyword" class="picker-filter__select" clearable placeholder="按题库名称筛选" @input="handleBankKeywordChange" />
      <ElInput v-model="questionKeyword" class="picker-filter__select" clearable placeholder="按题目编码或题干筛选" />
      <span class="picker-filter__summary">
        共
        <b>{{ filteredBanks.length }}</b>
        个题库 · 当前题库
        <b>{{ filteredQuestions.length }}</b>
        道可选题目
      </span>
    </div>

    <div class="picker">
      <aside class="picker__banks">
        <div class="picker__pane-heading">
          <span>题库（{{ filteredBanks.length }}）</span>
          <ElButton link type="primary" :disabled="!filteredBanks.length" @click="handleBankKeywordChange">定位</ElButton>
        </div>
        <div v-loading="banksLoading" class="picker__bank-list">
          <button
            v-for="bank in filteredBanks"
            :key="bank.questionBankId"
            type="button"
            class="bank-item"
            :class="{ 'is-active': bank.questionBankId === currentBankId }"
            @click="changeBank(bank.questionBankId)"
          >
            <span class="bank-item__name">{{ bank.bankName }}</span>
            <small>{{ bank.bankCode || '—' }}</small>
          </button>
          <ElEmpty v-if="!banksLoading && !filteredBanks.length" description="当前筛选条件下没有题库" :image-size="56" />
        </div>
      </aside>

      <main class="picker__questions">
        <div class="picker__pane-heading">
          <span>{{ currentBankName ? `${currentBankName} 的题目` : '题目' }}</span>
          <div class="picker__pane-actions">
            <ElButton link type="primary" :disabled="!filteredQuestions.length" @click="pickAllVisible">全选当前列表</ElButton>
            <ElButton link :disabled="!pickedCount" @click="clearPicked">清空已选</ElButton>
          </div>
        </div>
        <ElTable v-loading="questionsLoading" :data="filteredQuestions" height="100%" class="picker__table">
          <ElTableColumn width="52" align="center">
            <template #default="{ row }">
              <ElCheckbox
                :model-value="isPicked(row.questionId)"
                :disabled="isAlreadyInPaper(row.questionId)"
                @change="togglePick(row as Question)"
              />
            </template>
          </ElTableColumn>
          <ElTableColumn label="题目编码" width="150">
            <template #default="{ row }">
              <span class="picker__code">{{ row.questionCode }}</span>
              <ElTag v-if="isAlreadyInPaper(row.questionId)" size="small" type="info" effect="plain" class="picker__flag">已在试卷中</ElTag>
            </template>
          </ElTableColumn>
          <ElTableColumn label="题干" min-width="320" show-overflow-tooltip>
            <template #default="{ row }">{{ row.currentVersion?.stem || '—' }}</template>
          </ElTableColumn>
          <ElTableColumn label="题型" width="100">
            <template #default="{ row }">{{ getEnumLabel(row.currentVersion?.questionType as QuestionType, QUESTION_TYPE_OPTIONS) }}</template>
          </ElTableColumn>
          <ElTableColumn label="难度" width="90">
            <template #default="{ row }">
              {{ getEnumLabel(row.currentVersion?.difficulty as QuestionDifficulty, QUESTION_DIFFICULTY_OPTIONS) }}
            </template>
          </ElTableColumn>
        </ElTable>
      </main>
    </div>

    <template #footer>
      <div class="picker-footer">
        <span class="picker-footer__summary">
          已选
          <b>{{ pickedCount }}</b>
          道题目
          <template v-if="alreadyInPaperCount">（试卷中已有 {{ alreadyInPaperCount }} 道，不重复添加）</template>
        </span>
        <div class="picker-footer__actions">
          <ElButton @click="emit('update:modelValue', false)">取消</ElButton>
          <ElButton type="primary" :disabled="!pickedCount" @click="confirm">添加已选题目</ElButton>
        </div>
      </div>
    </template>
  </ElDialog>
</template>

<style scoped lang="less">
.picker-filter {
  display: grid;
  align-items: center;
  grid-template-columns: 220px 240px minmax(240px, 1fr) auto;
  gap: var(--space-3);
  padding: var(--space-3) var(--space-4);
  border: 1px solid var(--color-border);
  border-radius: var(--radius-card);
  background: var(--color-bg-subtle);
}

.picker-filter__select {
  width: 100%;
}

.picker-filter__summary {
  color: var(--color-text-secondary);
  font-size: 12px;
  white-space: nowrap;
}

.picker-filter__summary b {
  color: var(--color-primary);
  font-size: 14px;
}

.picker {
  display: grid;
  height: 460px;
  margin-top: var(--space-3);
  overflow: auto;
  grid-template-columns: 258px minmax(0, 1fr);
  border: 1px solid var(--color-border);
  border-radius: var(--radius-card);
  background: var(--color-bg-surface);
}

.picker__banks {
  display: flex;
  min-width: 0;
  flex-direction: column;
  border-right: 1px solid var(--color-border);
  background: var(--color-bg-subtle);
}

.picker__questions {
  display: flex;
  min-width: 0;
  flex-direction: column;
}

.picker__pane-heading {
  display: flex;
  min-height: 42px;
  align-items: center;
  justify-content: space-between;
  gap: var(--space-3);
  padding: 0 var(--space-4);
  border-bottom: 1px solid var(--color-border);
  color: var(--color-text-primary);
  font-size: 13px;
  font-weight: 600;
}

.picker__pane-actions {
  display: flex;
  align-items: center;
  gap: var(--space-2);
}

.picker__bank-list {
  min-height: 0;
  flex: 1;
  padding: var(--space-2);
  overflow: auto;
}

.bank-item {
  display: flex;
  width: 100%;
  align-items: center;
  justify-content: space-between;
  gap: var(--space-2);
  margin-bottom: 6px;
  padding: 10px 12px;
  border: 1px solid transparent;
  border-radius: var(--radius-control);
  background: transparent;
  color: var(--color-text-regular);
  cursor: pointer;
  text-align: left;
  transition:
    border-color 0.2s,
    background-color 0.2s;
}

.bank-item:hover {
  background: var(--color-bg-surface);
}

.bank-item.is-active {
  border-color: #bfdbfe;
  background: var(--color-primary-subtle);
  color: var(--color-primary);
}

.bank-item__name {
  min-width: 0;
  overflow: hidden;
  font-size: 13px;
  font-weight: 600;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.bank-item small {
  flex: 0 0 auto;
  color: var(--color-text-secondary);
  font-size: 11px;
}

.picker__table {
  min-height: 0;
  flex: 1;
}

.picker__code {
  display: block;
  color: var(--color-text-regular);
  font-family: Menlo, Consolas, monospace;
  font-size: 12px;
  overflow: hidden;
  white-space: nowrap;
  text-overflow: ellipsis;
}

.picker__flag {
  margin-left: 5px;
}

.picker-footer {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: var(--space-3);
}

.picker-footer__summary {
  color: var(--color-text-secondary);
  font-size: 13px;
}

.picker-footer__summary b {
  color: var(--color-primary);
  font-size: 15px;
}

.picker-footer__actions {
  display: flex;
  align-items: center;
  gap: var(--space-2);
}

@media (max-width: 1200px) {
  .picker-filter {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }

  .picker-filter__summary {
    grid-column: 1 / -1;
  }
}
</style>
