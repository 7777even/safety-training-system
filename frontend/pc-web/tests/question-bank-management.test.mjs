import assert from 'node:assert/strict';
import {readFile} from 'node:fs/promises';
import test from 'node:test';

const pageUrl = new URL('../src/views/admin/business-center/exams/question/index.vue', import.meta.url);

test('题库管理页提供真实题库、题目、发布与导入操作', async () => {
  const page = await readFile(pageUrl, 'utf8');

  assert.match(page, /getQuestionBankPage/);
  assert.match(page, /getQuestionPage/);
  assert.match(page, /getKnowledgePointTree/);
  assert.match(page, /createKnowledgePoint/);
  assert.match(page, /getQuestionBankCategories/);
  assert.match(page, /createQuestionBankCategory/);
  assert.match(page, /createQuestionBank/);
  assert.match(page, /createQuestion/);
  assert.match(page, /publishQuestion/);
  assert.match(page, /importQuestions/);
  assert.match(page, /题库管理/);
  assert.match(page, /新增题库/);
  assert.match(page, /发布版本/);
  assert.match(page, /批量导入/);
  assert.match(page, /知识点树/);
  assert.match(page, /题库分类/);
  assert.match(page, /编辑详情/);
  assert.doesNotMatch(page, /暂未开放|占位组件/);
});

test('题库、知识点与题库分类均采用左侧选择、右侧展示对应数据的主从布局', async () => {
  const page = await readFile(pageUrl, 'utf8');

  assert.match(page, /master-detail-layout/);
  assert.match(page, /selectedBankId\.value = data\.list\[0\]\?\.questionBankId/);
  assert.match(page, /selectedKnowledgePointId/);
  assert.match(page, /selectedCategoryId/);
  assert.match(page, /categoryBankRows/);
  assert.match(page, /bankCategoryId/);
  assert.match(page, /knowledgePointIds/);
});

test('知识点和题库分类新增表单校验必填字段，并可选择父级知识点', async () => {
  const page = await readFile(pageUrl, 'utf8');

  assert.match(page, /knowledgeFormRef/);
  assert.match(page, /knowledgeRules/);
  assert.match(page, /请输入知识点编码/);
  assert.match(page, /请输入知识点名称/);
  assert.match(page, /v-model="knowledgeForm\.parentId"/);
  assert.match(page, /请选择父级知识点/);
  assert.match(page, /categoryFormRef/);
  assert.match(page, /categoryRules/);
  assert.match(page, /请输入分类名称/);
  assert.match(page, /请输入分类描述/);
  assert.match(page, /await knowledgeFormRef\.value\?\.validate/);
  assert.match(page, /await categoryFormRef\.value\?\.validate/);
  assert.match(page, /changeKnowledgeOrganization/);
  assert.match(page, /getKnowledgePointTree\(knowledgeForm\.organizationId\)/);
  assert.match(page, /knowledgeForm\.parentId = 0/);
  assert.match(page, /changeBankOrganization/);
  assert.match(page, /getQuestionBankCategories\(bankForm\.organizationId\)/);
  assert.match(page, /请选择并上传导入文件/);
  assert.match(page, /description:\s*\[\{required:\s*true/);
});
