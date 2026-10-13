<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue';
import { ElMessage } from 'element-plus';
import { useRouter } from 'vue-router';
import {
  createQuestion,
  getQuestionBankPage,
  type QuestionBank,
  type QuestionBlankAnswerPayload,
  type QuestionDifficulty,
  type QuestionGradingMode,
  type QuestionKeywordPayload,
  type QuestionOptionPayload,
  type QuestionType
} from '@/api/question';
import { QUESTION_DIFFICULTY_OPTIONS, QUESTION_TYPE_OPTIONS } from '@/constants/exam-options';

const router = useRouter();
const loading = ref(false);
const banks = ref<QuestionBank[]>([]);
const options = ref<QuestionOptionPayload[]>([{ optionKey: 'A', optionContent: '', isCorrect: 1, sortOrder: 1 }]);
/** 填空题答案按空位逐行编辑，序号即 blankNo。 */
const blankAnswers = ref<Array<{ answerText: string }>>([{ answerText: '' }]);
/** 判分关键词以标签形式维护，输入回车即可添加。 */
const keywords = ref<string[]>([]);
const form = reactive({
  questionBankId: undefined as number | undefined,
  questionCode: '',
  questionType: 'SINGLE' as QuestionType,
  stem: '',
  analysis: '',
  standardAnswer: '',
  difficulty: 'EASY' as QuestionDifficulty,
  gradingMode: 'AUTO' as QuestionGradingMode,
  versionNote: '',
  isUnorderedAnswerAllowed: 0 as 0 | 1,
  isCaseIgnored: 0 as 0 | 1,
  isFullHalfWidthIgnored: 0 as 0 | 1
});

function isOptionQuestion(): boolean {
  return ['SINGLE', 'MULTIPLE', 'JUDGE'].includes(form.questionType);
}

const canRemoveOption = computed(() => options.value.length > 1);
const correctOptionCount = computed(() => options.value.filter((option) => option.isCorrect === 1).length);

/** 为进入选项题时准备一条默认选项，离开选项题后不保留无效选项。 */
function resetOptions(): void {
  options.value = [{ optionKey: 'A', optionContent: '', isCorrect: 1, sortOrder: 1 }];
}

/** 切换题型后清理其他题型专属数据，避免隐藏字段随请求错误提交。 */
function handleQuestionTypeChange(): void {
  if (isOptionQuestion()) {
    if (options.value.length === 0) resetOptions();
  } else {
    options.value = [];
  }

  if (form.questionType !== 'FILL') {
    blankAnswers.value = [{ answerText: '' }];
    form.isUnorderedAnswerAllowed = 0;
    form.isCaseIgnored = 0;
    form.isFullHalfWidthIgnored = 0;
  } else {
    form.gradingMode = 'AUTO';
  }

  if (form.questionType !== 'ESSAY') {
    keywords.value = [];
    form.standardAnswer = '';
    form.gradingMode = 'AUTO';
  }
}

/** 简答题改变判分方式时同步清理另一种判分方式的专属输入。 */
function handleGradingModeChange(): void {
  if (form.gradingMode === 'AUTO') {
    form.standardAnswer = '';
    return;
  }
  keywords.value = [];
}

async function loadBanks(): Promise<void> {
  const page = await getQuestionBankPage({ pageNum: 1, pageSize: 100 });
  banks.value = page.list;
}

function addOption(): void {
  options.value.push({
    optionKey: String.fromCharCode(65 + options.value.length),
    optionContent: '',
    isCorrect: 0,
    sortOrder: options.value.length + 1
  });
}

/** 删除选项后重排字母与序号，避免出现跳号影响学员阅读。 */
function removeOption(index: number): void {
  if (!canRemoveOption.value) return;
  options.value.splice(index, 1);
  options.value.forEach((option, optionIndex) => {
    option.optionKey = String.fromCharCode(65 + optionIndex);
    option.sortOrder = optionIndex + 1;
  });
}

function addBlankAnswer(): void {
  blankAnswers.value.push({ answerText: '' });
}

function removeBlankAnswer(index: number): void {
  if (blankAnswers.value.length <= 1) return;
  blankAnswers.value.splice(index, 1);
}

/** 单选题与判断题只能有一个正确项，多选题允许多选。 */
function toggleCorrectOption(index: number): void {
  if (form.questionType === 'MULTIPLE') {
    options.value[index].isCorrect = options.value[index].isCorrect === 1 ? 0 : 1;
    return;
  }
  options.value.forEach((option, optionIndex) => (option.isCorrect = optionIndex === index ? 1 : 0));
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

async function submit(): Promise<void> {
  if (!form.questionBankId || !form.questionCode.trim() || !form.stem.trim()) {
    ElMessage.warning('请填写所属题库、题目编码和题干');
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
  loading.value = true;
  try {
    const questionId = await createQuestion({
      questionBankId: form.questionBankId,
      questionCode: form.questionCode.trim(),
      questionType: form.questionType,
      stem: form.stem.trim(),
      difficulty: form.difficulty,
      gradingMode: form.questionType === 'ESSAY' ? form.gradingMode : 'AUTO',
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
    ElMessage.success('题目已创建，请在详情页完善并发布版本');
    await router.replace(`/admin/business-center/exams/question/${questionId}`);
  } finally {
    loading.value = false;
  }
}

onMounted(loadBanks);
</script>

<template>
  <section class="question-create-page admin-detail-page">
    <header class="admin-detail-page__header">
      <div class="question-heading">
        <h1>新建题目</h1>
        <p>创建后进入题目详情继续完善；发布版本后仅可只读引用。</p>
      </div>
      <ElButton @click="router.back()">返回题库</ElButton>
    </header>

    <ElCard shadow="never" class="admin-detail-page__card">
      <div class="admin-detail-page__form-scroll create-body">
        <section class="form-section">
          <header class="form-section__header">
            <strong>基础信息</strong>
            <span>所属题库与题目编码</span>
          </header>
          <div class="form-section__body">
            <ElForm label-position="top" class="create-form">
              <ElRow :gutter="24">
                <ElCol :span="12">
                  <ElFormItem label="所属题库" required>
                    <ElSelect v-model="form.questionBankId" placeholder="请选择所属题库" filterable>
                      <ElOption v-for="bank in banks" :key="bank.questionBankId" :label="bank.bankName" :value="bank.questionBankId" />
                    </ElSelect>
                  </ElFormItem>
                </ElCol>
                <ElCol :span="12">
                  <ElFormItem label="题目编码" required>
                    <ElInput v-model="form.questionCode" placeholder="请输入题目编码，创建后不可修改" maxlength="40" show-word-limit />
                  </ElFormItem>
                </ElCol>
              </ElRow>
            </ElForm>
          </div>
        </section>

        <section class="form-section">
          <header class="form-section__header">
            <strong>题型与题干</strong>
            <span>题型决定下方需要填写的答案结构</span>
          </header>
          <div class="form-section__body">
            <ElForm label-position="top" class="create-form">
              <ElFormItem label="题型" required>
                <ElRadioGroup v-model="form.questionType" @change="handleQuestionTypeChange">
                  <ElRadioButton v-for="item in QUESTION_TYPE_OPTIONS" :key="item.value" :value="item.value">{{ item.label }}</ElRadioButton>
                </ElRadioGroup>
              </ElFormItem>
              <ElFormItem label="题干" required>
                <ElInput
                  v-model="form.stem"
                  type="textarea"
                  :rows="4"
                  maxlength="2000"
                  show-word-limit
                  placeholder="请输入题干内容"
                />
              </ElFormItem>
              <ElRow :gutter="24">
                <ElCol :span="12">
                  <ElFormItem label="难度">
                    <ElSelect v-model="form.difficulty" placeholder="请选择难度">
                      <ElOption v-for="item in QUESTION_DIFFICULTY_OPTIONS" :key="item.value" :label="item.label" :value="item.value" />
                    </ElSelect>
                  </ElFormItem>
                </ElCol>
                <ElCol v-if="form.questionType === 'ESSAY'" :span="12">
                  <ElFormItem label="判分方式">
                    <ElRadioGroup v-model="form.gradingMode" @change="handleGradingModeChange">
                      <ElRadio value="AUTO">自动判分</ElRadio>
                      <ElRadio value="MANUAL">人工判分</ElRadio>
                    </ElRadioGroup>
                  </ElFormItem>
                </ElCol>
              </ElRow>
            </ElForm>
          </div>
        </section>

        <section class="form-section">
          <header class="form-section__header">
            <strong>{{ isOptionQuestion() ? '选项与正确答案' : form.questionType === 'FILL' ? '填空答案' : '判分设置' }}</strong>
            <span v-if="isOptionQuestion()">共 {{ options.length }} 个选项 · 已标记 {{ correctOptionCount }} 个正确答案</span>
            <span v-else-if="form.questionType === 'FILL'">共 {{ blankAnswers.length }} 个空</span>
            <span v-else>{{ form.gradingMode === 'AUTO' ? '自动判分' : '人工判分' }}</span>
          </header>
          <div class="form-section__body">
            <template v-if="isOptionQuestion()">
              <div class="option-list">
                <div v-for="(option, index) in options" :key="option.optionKey" class="option-item" :class="{ 'is-correct': option.isCorrect === 1 }">
                  <span class="option-item__key">{{ option.optionKey }}</span>
                  <ElInput v-model="option.optionContent" :placeholder="`请输入 ${option.optionKey} 选项内容`" />
                  <ElButton
                    class="option-item__correct"
                    :type="option.isCorrect === 1 ? 'primary' : 'default'"
                    :plain="option.isCorrect !== 1"
                    @click="toggleCorrectOption(index)"
                  >
                    {{ option.isCorrect === 1 ? '正确答案' : '设为正确' }}
                  </ElButton>
                  <ElButton link type="danger" :disabled="!canRemoveOption" @click="removeOption(index)">删除</ElButton>
                </div>
              </div>
              <div class="option-actions">
                <ElButton plain type="primary" @click="addOption">新增选项</ElButton>
                <span class="field-hint">
                  {{ form.questionType === 'MULTIPLE' ? '多选题可标记多个正确答案。' : '单选题与判断题只能标记一个正确答案。' }}
                </span>
              </div>
            </template>

            <template v-else-if="form.questionType === 'FILL'">
              <div class="blank-list">
                <div v-for="(blank, index) in blankAnswers" :key="index" class="blank-item">
                  <span class="blank-item__key">第 {{ index + 1 }} 空</span>
                  <ElInput v-model="blank.answerText" placeholder="请输入该空的标准答案" />
                  <ElButton link type="danger" :disabled="blankAnswers.length <= 1" @click="removeBlankAnswer(index)">删除</ElButton>
                </div>
              </div>
              <div class="option-actions">
                <ElButton plain type="primary" @click="addBlankAnswer">新增空位</ElButton>
              </div>
              <ElDivider />
              <ElForm label-position="top" class="create-form">
                <ElFormItem label="答案匹配规则">
                  <ElCheckbox v-model="form.isUnorderedAnswerAllowed" :true-value="1" :false-value="0">允许答案无序</ElCheckbox>
                  <ElCheckbox v-model="form.isCaseIgnored" :true-value="1" :false-value="0">忽略大小写</ElCheckbox>
                  <ElCheckbox v-model="form.isFullHalfWidthIgnored" :true-value="1" :false-value="0">忽略全半角</ElCheckbox>
                  <p class="field-hint">规则在学员作答自动判分时生效，可同时启用多项。</p>
                </ElFormItem>
              </ElForm>
            </template>

            <template v-else>
              <ElForm label-position="top" class="create-form">
                <ElFormItem v-if="form.gradingMode === 'AUTO'" label="判分关键词">
                  <ElSelect
                    v-model="keywords"
                    class="keyword-editor"
                    multiple
                    filterable
                    allow-create
                    default-first-option
                    :reserve-keyword="false"
                    placeholder="输入关键词后回车即可添加"
                  />
                  <p class="field-hint">学员答案命中关键词即得分，可添加多个关键词。</p>
                </ElFormItem>
                <ElFormItem v-else label="评分参考" required>
                  <ElInput v-model="form.standardAnswer" type="textarea" :rows="3" placeholder="请输入人工判分时参考的评分要点" />
                  <p class="field-hint">人工判分不参与自动评分，仅作为阅卷人评分参考。</p>
                </ElFormItem>
              </ElForm>
            </template>
          </div>
        </section>

        <section class="form-section">
          <header class="form-section__header">
            <strong>补充信息</strong>
            <span>可选项，可稍后在详情页继续完善</span>
          </header>
          <div class="form-section__body">
            <ElForm label-position="top" class="create-form">
              <ElFormItem label="题目解析">
                <ElInput v-model="form.analysis" type="textarea" :rows="3" placeholder="请输入题目解析" />
              </ElFormItem>
              <ElFormItem label="版本说明">
                <ElInput v-model="form.versionNote" placeholder="请输入本次版本说明" />
              </ElFormItem>
            </ElForm>
          </div>
        </section>
      </div>

      <footer class="admin-detail-action-bar">
        <ElButton @click="router.back()">取消</ElButton>
        <ElButton type="primary" :loading="loading" @click="submit">创建并继续编辑</ElButton>
      </footer>
    </ElCard>
  </section>
</template>

<style scoped lang="less">
.question-create-page :deep(.el-card__body) {
  display: flex;
  flex-direction: column;
  overflow: hidden;
}

.question-heading p {
  margin: var(--space-2) 0 0 var(--space-4);
  color: var(--color-text-secondary);
  font-size: 13px;
}

.create-body {
  max-width: 960px;
  margin: 0 auto;
  padding: var(--space-5) var(--space-6);
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

.create-form :deep(.el-form-item) {
  margin-bottom: var(--space-4);
}

.create-form :deep(.el-form-item:last-child) {
  margin-bottom: 0;
}

.create-form :deep(.el-select),
.create-form :deep(.el-input-number),
.create-form :deep(.el-tree-select) {
  width: 100%;
}

.field-hint {
  margin-left:10px;
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
  height: 32px;
  place-items: center;
  border: 1px solid var(--color-border);
  border-radius: var(--radius-control);
  background: var(--color-bg-subtle);
  color: var(--color-text-regular);
  font-size: 12px;
  font-weight: 600;
}

.option-item.is-correct .option-item__key {
  border-color: var(--color-primary);
  background: var(--color-primary);
  color: #fff;
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

@media (max-width: 900px) {
  .option-item {
    grid-template-columns: 32px minmax(0, 1fr);
  }
}
</style>
