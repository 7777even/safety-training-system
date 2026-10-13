import assert from 'node:assert/strict';
import {readFile} from 'node:fs/promises';
import test from 'node:test';

const createPageUrl = new URL('../src/views/admin/business-center/exams/question/create/index.vue', import.meta.url);
const detailPageUrl = new URL('../src/views/admin/business-center/exams/question/detail/index.vue', import.meta.url);

test('题目新建和编辑版本使用独立页面，而非 Dialog 或 Drawer', async () => {
  const [createPage, detailPage] = await Promise.all([readFile(createPageUrl, 'utf8'), readFile(detailPageUrl, 'utf8')]);

  assert.match(createPage, /createQuestion/);
  assert.match(createPage, /新建题目/);
  assert.doesNotMatch(createPage, /ElDialog|ElDrawer/);
  assert.match(detailPage, /updateQuestion/);
  assert.match(detailPage, /publishQuestion/);
  assert.match(detailPage, /replaceQuestionAssets/);
  assert.match(detailPage, /题目版本/);
  assert.doesNotMatch(detailPage, /ElDialog|ElDrawer/);
});

test('题目详情使用统一字典 label 回显题型和难度', async () => {
  const detailPage = await readFile(detailPageUrl, 'utf8');

  assert.match(detailPage, /QUESTION_TYPE_OPTIONS/);
  assert.match(detailPage, /QUESTION_DIFFICULTY_OPTIONS/);
  assert.match(detailPage, /getEnumLabel/);
  assert.match(detailPage, /getEnumLabel\(form\.questionType, QUESTION_TYPE_OPTIONS\)/);
  assert.match(detailPage, /getEnumLabel\(form\.difficulty, QUESTION_DIFFICULTY_OPTIONS\)/);
  assert.doesNotMatch(detailPage, /:model-value="form\.questionType"/);
});

test('题库新增表单为关键业务字段提供明确必填校验', async () => {
  const bankPage = await readFile(new URL('../src/views/admin/business-center/exams/question/index.vue', import.meta.url), 'utf8');

  assert.match(bankPage, /bankFormRef/);
  assert.match(bankPage, /bankRules/);
  assert.match(bankPage, /bankCode: \[\{ required: true/);
  assert.match(bankPage, /bankName: \[\{ required: true/);
  assert.match(bankPage, /<ElForm ref="bankFormRef" :model="bankForm" :rules="bankRules"/);
  assert.match(bankPage, /<ElFormItem label="题库编码" prop="bankCode"/);
  assert.match(bankPage, /<ElFormItem label="题库名称" prop="bankName"/);
  assert.match(bankPage, /await bankFormRef\.value\?\.validate\(\)/);
});

test('题目列表仅展示分页契约字段，选择题目后以弹窗加载详情预览', async () => {
  const bankPage = await readFile(new URL('../src/views/admin/business-center/exams/question/index.vue', import.meta.url), 'utf8');

  assert.match(bankPage, /getQuestionDetail/);
  assert.match(bankPage, /selectedQuestion/);
  assert.match(bankPage, /async function selectQuestion/);
  assert.match(bankPage, /await getQuestionDetail\(question\.questionId\)/);
  assert.match(bankPage, /@current-change="selectQuestionRow"/);
  assert.match(bankPage, /previewDialogVisible/);
  assert.match(bankPage, /<ElDialog v-model="previewDialogVisible" title="题目预览"/);
  assert.match(bankPage, /题目预览/);
  assert.match(bankPage, /label="归档状态"/);
  assert.match(bankPage, /label="启用状态"/);
  assert.doesNotMatch(bankPage, /prop="questionId" label="题目ID"/);
  assert.doesNotMatch(bankPage, /questionStem\(asQuestion\(row\)\)/);
  assert.doesNotMatch(bankPage, /questionType\(asQuestion\(row\)\)/);
  assert.doesNotMatch(bankPage, /publishQuestion/);
  assert.doesNotMatch(bankPage, /<section class="question-preview"/);
});

test('新建题目按题型显示字段并净化不适用提交数据', async () => {
  const createPage = await readFile(createPageUrl, 'utf8');

  assert.match(createPage, /function handleQuestionTypeChange/);
  assert.match(createPage, /@change="handleQuestionTypeChange"/);
  assert.match(createPage, /v-if="isOptionQuestion\(\)"/);
  assert.match(createPage, /v-if="form\.questionType === 'FILL'"/);
  assert.match(createPage, /v-if="form\.questionType === 'ESSAY' && form\.gradingMode === 'AUTO'"/);
  assert.match(createPage, /v-if="form\.questionType === 'ESSAY' && form\.gradingMode === 'MANUAL'"/);
  assert.match(createPage, /form\.blankAnswersText = ''/);
  assert.match(createPage, /form\.keywordsText = ''/);
  assert.match(createPage, /form\.standardAnswer = ''/);
  assert.match(createPage, /\.\.\.\(isOptionQuestion\(\) \? \{ options: options\.value \} : \{\}\)/);
  assert.match(createPage, /\.\.\.\(form\.questionType === 'FILL'/);
  assert.match(createPage, /\.\.\.\(form\.questionType === 'ESSAY' && form\.gradingMode === 'AUTO'/);
  assert.doesNotMatch(createPage, /const \{ blankAnswersText, keywordsText, \.\.\.payload \} = form/);
});

test('题目详情完整回显规则并引导草稿与发布状态流转', async () => {
  const detailPage = await readFile(detailPageUrl, 'utf8');

  assert.match(detailPage, /blankAnswersText/);
  assert.match(detailPage, /keywordsText/);
  assert.match(detailPage, /isUnorderedAnswerAllowed/);
  assert.match(detailPage, /version\.blankAnswers/);
  assert.match(detailPage, /version\.keywords/);
  assert.match(detailPage, /当前为草稿/);
  assert.match(detailPage, /当前版本已发布/);
  assert.match(detailPage, /已基于已发布版本创建新草稿/);
  assert.match(detailPage, /v-if="form\.questionType === 'FILL'"/);
  assert.match(detailPage, /v-if="form\.questionType === 'ESSAY' && form\.gradingMode === 'AUTO'"/);
  assert.match(detailPage, /v-if="form\.questionType === 'ESSAY' && form\.gradingMode === 'MANUAL'"/);
});
